/*
Copyright 2016-2020 Bowler Hat LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.as3mxml.vscode.utils;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.as3mxml.vscode.project.ActionScriptProjectData;
import com.as3mxml.vscode.project.ILspProject;
import com.as3mxml.vscode.project.IProjectConfigStrategy;
import com.as3mxml.vscode.project.IProjectConfigStrategyFactory;
import com.as3mxml.vscode.utils.CompilationUnitUtils.IncludeFileData;
import com.as3mxml.vscode.utils.DefinitionTextUtils.DefinitionAsText;

import org.apache.royale.compiler.config.Configuration;
import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IEventDefinition;
import org.apache.royale.compiler.definitions.IPackageDefinition;
import org.apache.royale.compiler.definitions.ITypeDefinition;
import org.apache.royale.compiler.definitions.metadata.IDeprecationInfo;
import org.apache.royale.compiler.filespecs.IFileSpecification;
import org.apache.royale.compiler.internal.mxml.MXMLData;
import org.apache.royale.compiler.mxml.IMXMLDataManager;
import org.apache.royale.compiler.mxml.IMXMLTagAttributeData;
import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.mxml.IMXMLClassReferenceNode;
import org.apache.royale.compiler.tree.mxml.IMXMLConcatenatedDataBindingNode;
import org.apache.royale.compiler.tree.mxml.IMXMLEventSpecifierNode;
import org.apache.royale.compiler.tree.mxml.IMXMLNode;
import org.apache.royale.compiler.tree.mxml.IMXMLPropertySpecifierNode;
import org.apache.royale.compiler.tree.mxml.IMXMLSingleDataBindingNode;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.apache.royale.utils.FilenameNormalization;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;

public class ActionScriptProjectManager {
    private static final String FILE_EXTENSION_AS = ".as";
    private static final String FILE_EXTENSION_MXML = ".mxml";
    private static final String FILE_EXTENSION_SWC = ".swc";
    private static final String FILE_EXTENSION_ANE = ".ane";
    private static final String SDK_LIBRARY_PATH_SIGNATURE_UNIX = "/frameworks/libs/";
    private static final String SDK_LIBRARY_PATH_SIGNATURE_WINDOWS = "\\frameworks\\libs\\";

    private List<ActionScriptProjectData> allProjectData = new ArrayList<>();
    private List<WorkspaceFolder> workspaceFolders = new ArrayList<>();
    private FileTracker fileTracker;
    private IProjectConfigStrategyFactory projectConfigStrategyFactory;
    private ActionScriptProjectData fallbackProjectData;
    private LanguageClient languageClient;
    private Predicate<ActionScriptProjectData> addProjectCallback;
    private Predicate<ActionScriptProjectData> removeProjectCallback;

    public ActionScriptProjectManager(FileTracker fileTracker, IProjectConfigStrategyFactory factory,
            Predicate<ActionScriptProjectData> addProjectCallback,
            Predicate<ActionScriptProjectData> removeProjectCallback) {
        this.fileTracker = fileTracker;
        this.projectConfigStrategyFactory = factory;
        this.addProjectCallback = addProjectCallback;
        this.removeProjectCallback = removeProjectCallback;
    }

    public List<ActionScriptProjectData> getAllProjectData() {
        return allProjectData;
    }

    public void addWorkspaceFolder(WorkspaceFolder folder) {
        workspaceFolders.add(folder);
        Path projectRoot = Paths.get(URI.create(folder.getUri()));
        try {
            projectRoot = projectRoot.toRealPath();
        } catch (IOException e) {
            //didn't seem to work, for some reason
        }
        addProject(projectRoot, folder);
    }

    public void removeWorkspaceFolder(WorkspaceFolder folder) {
        if (!workspaceFolders.contains(folder)) {
            return;
        }
        workspaceFolders.remove(folder);
        for (ActionScriptProjectData projectData : allProjectData) {
            if (!folder.equals(projectData.folder)) {
                continue;
            }
            removeProject(projectData);
        }
    }

    public void setLanguageClient(LanguageClient value) {
        languageClient = value;
        for (ActionScriptProjectData projectData : allProjectData) {
            projectData.codeProblemTracker.setLanguageClient(value);
            projectData.configProblemTracker.setLanguageClient(value);
        }
        if (fallbackProjectData != null) {
            fallbackProjectData.codeProblemTracker.setLanguageClient(value);
            fallbackProjectData.configProblemTracker.setLanguageClient(value);
        }
    }

    public ActionScriptProjectData getFallbackProjectData() {
        return fallbackProjectData;
    }

    public ActionScriptProjectData setFallbackProjectData(Path projectRoot, WorkspaceFolder folder,
            IProjectConfigStrategy config) {
        if (fallbackProjectData != null) {
            if (fallbackProjectData.projectRoot.equals(projectRoot) && fallbackProjectData.folder.equals(folder)
                    && fallbackProjectData.config.equals(config)) {
                return fallbackProjectData;
            }
            fallbackProjectData.cleanup();
            fallbackProjectData = null;
        }
        fallbackProjectData = new ActionScriptProjectData(projectRoot, folder, config);
        fallbackProjectData.codeProblemTracker.setLanguageClient(languageClient);
        fallbackProjectData.configProblemTracker.setLanguageClient(languageClient);
        return fallbackProjectData;
    }

    public ActionScriptProjectData getProjectDataForSourceFile(Path path) {
        checkForMissingProjectsContainingSourceFile(path);

        //first try to find the path in an existing project
        ActionScriptProjectData bestMatch = null;
        ActionScriptProjectData fallback = null;
        for (ActionScriptProjectData projectData : allProjectData) {
            ILspProject project = projectData.project;
            if (project == null) {
                continue;
            }
            Path projectRoot = projectData.projectRoot;
            if (projectRoot == null) {
                continue;
            }
            if (bestMatch != null && bestMatch.projectDepth >= projectData.projectDepth) {
                //even if it's in the source path, it's not a better match
                continue;
            }
            if (SourcePathUtils.isInProjectSourcePath(path, project, projectData.configurator)) {
                if (path.startsWith(projectRoot)) {
                    //if the source path is inside the project root folder, then
                    //the project is a candidate (we'll compare depths later)
                    bestMatch = projectData;
                    continue;
                }
                //if path is in the source path, but not inside the workspace
                //folder, save it as possible result for later. in other words,
                //we always prefer a workspace that contains the file, so we'll
                //check the other workspaces before using the fallback.
                if (fallback == null) {
                    fallback = projectData;
                    continue;
                }
            }
        }
        if (bestMatch != null) {
            return bestMatch;
        }
        //we found the path in a project's source path, but not inside any the
        //workspace folders
        if (fallback != null) {
            return fallback;
        }
        //if none of the existing projects worked, try a folder where a project
        //hasn't been created yet
        for (ActionScriptProjectData projectData : allProjectData) {
            ILspProject project = projectData.project;
            if (project != null) {
                //if there's already a project, there's nothing to create later
                continue;
            }
            Path projectRoot = projectData.projectRoot;
            if (projectRoot == null) {
                continue;
            }
            if (path.startsWith(projectRoot)) {
                return projectData;
            }
        }
        //a project where "everything else" goes
        return fallbackProjectData;
    }

    public boolean hasOpenFilesForProject(ActionScriptProjectData project) {
        for (Path openFilePath : fileTracker.getOpenFiles()) {
            ActionScriptProjectData otherProject = getProjectDataForSourceFile(openFilePath);
            if (otherProject == project) {
                return true;
            }
        }
        return false;
    }

    public IASNode getOffsetNode(Path path, int currentOffset, ActionScriptProjectData projectData) {
        IncludeFileData includeFileData = projectData.includedFiles.get(path.toString());
        if (includeFileData != null) {
            path = Paths.get(includeFileData.parentPath);
        }
        ILspProject project = projectData.project;
        if (!SourcePathUtils.isInProjectSourcePath(path, project, projectData.configurator)) {
            //the path must be in the workspace or source-path
            return null;
        }

        ICompilationUnit unit = CompilerProjectUtils.findCompilationUnit(path, project);
        if (unit == null) {
            //the path must be in the workspace or source-path
            return null;
        }

        IASNode ast = ASTUtils.getCompilationUnitAST(unit);
        if (ast == null) {
            return null;
        }

        return ASTUtils.getContainingNodeIncludingStart(ast, currentOffset);
    }

    public IASNode getEmbeddedActionScriptNodeInMXMLTag(IMXMLTagData tag, Path path, int currentOffset,
            ActionScriptProjectData projectData) {
        ILspProject project = projectData.project;
        IMXMLTagAttributeData attributeData = MXMLDataUtils.getMXMLTagAttributeWithValueAtOffset(tag, currentOffset);
        if (attributeData != null) {
            //some attributes can have ActionScript completion, such as
            //events and properties with data binding

            IDefinition resolvedDefinition = project.resolveXMLNameToDefinition(tag.getXMLName(), tag.getMXMLDialect());
            //prominic/Moonshine-IDE#/203: don't allow interface definitions because
            //we cannot resolve specifiers. <fx:Component> resolves to an interface
            //definition, and it can have an id attribute.
            if (resolvedDefinition == null || !(resolvedDefinition instanceof IClassDefinition)) {
                //we can't figure out which class the tag represents!
                //maybe the user hasn't defined the tag's namespace or something
                return null;
            }
            IClassDefinition tagDefinition = (IClassDefinition) resolvedDefinition;
            IDefinition attributeDefinition = project.resolveSpecifier(tagDefinition, attributeData.getShortName());
            if (attributeDefinition instanceof IEventDefinition) {
                IASNode offsetNode = getOffsetNode(path, currentOffset, projectData);
                if (offsetNode instanceof IMXMLClassReferenceNode) {
                    IMXMLClassReferenceNode mxmlNode = (IMXMLClassReferenceNode) offsetNode;
                    IMXMLEventSpecifierNode eventNode = mxmlNode.getEventSpecifierNode(attributeData.getShortName());
                    //the event node might be null if the MXML document isn't in a
                    //fully valid state (unclosed tags, for instance)
                    if (eventNode != null) {
                        for (IASNode asNode : eventNode.getASNodes()) {
                            IASNode containingNode = ASTUtils.getContainingNodeIncludingStart(asNode, currentOffset);
                            if (containingNode != null) {
                                return containingNode;
                            }
                        }
                    }
                    return eventNode;
                }
            } else {
                IASNode offsetNode = getOffsetNode(path, currentOffset, projectData);
                if (offsetNode instanceof IMXMLClassReferenceNode) {
                    IMXMLClassReferenceNode mxmlNode = (IMXMLClassReferenceNode) offsetNode;
                    IMXMLPropertySpecifierNode propertyNode = mxmlNode
                            .getPropertySpecifierNode(attributeData.getShortName());
                    if (propertyNode != null) {
                        for (int i = 0, count = propertyNode.getChildCount(); i < count; i++) {
                            IMXMLNode propertyChild = (IMXMLNode) propertyNode.getChild(i);
                            if (propertyChild instanceof IMXMLConcatenatedDataBindingNode) {
                                IMXMLConcatenatedDataBindingNode dataBinding = (IMXMLConcatenatedDataBindingNode) propertyChild;
                                for (int j = 0, childCount = dataBinding.getChildCount(); j < childCount; j++) {
                                    IASNode dataBindingChild = dataBinding.getChild(i);
                                    if (dataBindingChild.contains(currentOffset)
                                            && dataBindingChild instanceof IMXMLSingleDataBindingNode) {
                                        //we'll parse this in a moment, as if it were
                                        //a direct child of the property specifier
                                        propertyChild = (IMXMLSingleDataBindingNode) dataBindingChild;
                                        break;
                                    }
                                }
                            }
                            if (propertyChild instanceof IMXMLSingleDataBindingNode) {
                                IMXMLSingleDataBindingNode dataBinding = (IMXMLSingleDataBindingNode) propertyChild;
                                IASNode containingNode = dataBinding.getExpressionNode()
                                        .getContainingNode(currentOffset);
                                if (containingNode == null) {
                                    return dataBinding;
                                }
                                return containingNode;
                            }
                        }
                    }
                }
                //nothing possible for this attribute
            }
        }
        return null;
    }

    public MXMLData getMXMLDataForPath(Path path, ActionScriptProjectData projectData) {
        IncludeFileData includeFileData = projectData.includedFiles.get(path.toString());
        if (includeFileData != null) {
            path = Paths.get(includeFileData.parentPath);
        }
        if (!path.toString().endsWith(FILE_EXTENSION_MXML)) {
            // don't try to parse ActionScript files as MXML
            return null;
        }
        ILspProject project = projectData.project;
        if (!SourcePathUtils.isInProjectSourcePath(path, project, projectData.configurator)) {
            //the path must be in the workspace or source-path
            return null;
        }

        //need to ensure that the compilation unit exists, even though we don't
        //use it directly
        ICompilationUnit unit = CompilerProjectUtils.findCompilationUnit(path, project);
        if (unit == null) {
            //no need to log this case because it can happen for reasons that
            //should have been logged already
            return null;
        }
        IMXMLDataManager mxmlDataManager = project.getWorkspace().getMXMLDataManager();
        String normalizedPath = FilenameNormalization.normalize(path.toAbsolutePath().toString());
        IFileSpecification fileSpecification = fileTracker.getFileSpecification(normalizedPath);
        return (MXMLData) mxmlDataManager.get(fileSpecification);
    }

    public ICompilationUnit findCompilationUnit(Path pathToFind) {
        for (ActionScriptProjectData projectData : allProjectData) {
            ILspProject project = projectData.project;
            if (project == null) {
                continue;
            }
            ICompilationUnit result = CompilerProjectUtils.findCompilationUnit(pathToFind, project);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public List<ActionScriptProjectData> getAllProjectDataForSourceFile(Path path) {
        List<ActionScriptProjectData> result = new ArrayList<>();
        for (ActionScriptProjectData projectData : allProjectData) {
            ILspProject project = projectData.project;
            if (project == null) {
                Path projectRoot = projectData.projectRoot;
                if (path.startsWith(projectRoot)) {
                    result.add(projectData);
                }
            } else if (SourcePathUtils.isInProjectSourcePath(path, project, projectData.configurator)) {
                result.add(projectData);
            }
        }
        return result;
    }

    public List<ActionScriptProjectData> getAllProjectDataForSWCFile(Path path) {
        List<ActionScriptProjectData> result = new ArrayList<>();
        for (ActionScriptProjectData projectData : allProjectData) {
            ILspProject project = projectData.project;
            if (project == null) {
                Path projectRoot = projectData.projectRoot;
                if (path.startsWith(projectRoot)) {
                    result.add(projectData);
                }
            } else {
                Configuration configuration = projectData.configurator.getConfiguration();
                if (SourcePathUtils.isInProjectLibraryPathOrExternalLibraryPath(path, project, configuration)) {
                    result.add(projectData);
                }
            }
        }
        return result;
    }

    public Location getLocationFromDefinition(IDefinition definition, ILspProject project) {
        String sourcePath = LanguageServerCompilerUtils.getSourcePathFromDefinition(definition, project);
        if (sourcePath == null) {
            //we can't find where the source code for this symbol is located
            return null;
        }
        Location location = null;
        if (sourcePath.endsWith(FILE_EXTENSION_SWC) || sourcePath.endsWith(FILE_EXTENSION_ANE)) {
            DefinitionAsText definitionText = DefinitionTextUtils.definitionToTextDocument(definition, project);
            //may be null if definitionToTextDocument() doesn't know how
            //to parse that type of definition
            if (definitionText != null) {
                //if we get here, we couldn't find a framework source file and
                //the definition path still ends with .swc
                //we're going to try our best to display "decompiled" content
                location = definitionText.toLocation();
            }
        }
        if (location == null) {
            location = new Location();
            Path definitionPath = Paths.get(sourcePath);
            location.setUri(definitionPath.toUri().toString());
            Range range = definitionToRange(definition, project);
            if (range == null) {
                return null;
            }
            location.setRange(range);
        }
        return location;
    }

    public Range definitionToRange(IDefinition definition, ILspProject project) {
        String sourcePath = LanguageServerCompilerUtils.getSourcePathFromDefinition(definition, project);
        if (sourcePath == null) {
            //we can't find where the source code for this symbol is located
            return null;
        }
        Range range = null;
        if (sourcePath.endsWith(FILE_EXTENSION_SWC) || sourcePath.endsWith(FILE_EXTENSION_ANE)) {
            DefinitionAsText definitionText = DefinitionTextUtils.definitionToTextDocument(definition, project);
            //may be null if definitionToTextDocument() doesn't know how
            //to parse that type of definition
            if (definitionText != null) {
                //if we get here, we couldn't find a framework source file and
                //the definition path still ends with .swc
                //we're going to try our best to display "decompiled" content
                range = definitionText.toRange();
            }
        }
        if (range == null) {
            Path definitionPath = Paths.get(sourcePath);
            Position start = new Position();
            Position end = new Position();
            //getLine() and getColumn() may include things like metadata, so it
            //makes more sense to jump to where the definition name starts
            int line = definition.getNameLine();
            int column = definition.getNameColumn();
            if (line < 0 || column < 0) {
                //this is not ideal, but MXML variable definitions may not have a
                //node associated with them, so we need to figure this out from the
                //offset instead of a pre-calculated line and column -JT
                Reader definitionReader = fileTracker.getReader(definitionPath);
                if (definitionReader == null) {
                    //we might get here if it's from a SWC, but the associated
                    //source file is missing.
                    return null;
                } else {
                    try {
                        LanguageServerCompilerUtils.getPositionFromOffset(definitionReader, definition.getNameStart(),
                                start);
                        end.setLine(start.getLine());
                        end.setCharacter(start.getCharacter());
                    } finally {
                        try {
                            definitionReader.close();
                        } catch (IOException e) {
                        }
                    }
                }
            } else {
                start.setLine(line);
                start.setCharacter(column);
                end.setLine(line);
                end.setCharacter(column);
            }
            range = new Range();
            range.setStart(start);
            range.setEnd(end);
        }
        return range;
    }

    public DocumentSymbol definitionToDocumentSymbol(IDefinition definition, ILspProject project) {
        String definitionBaseName = definition.getBaseName();
        if (definition instanceof IPackageDefinition) {
            definitionBaseName = "package " + definitionBaseName;
        }
        if (definitionBaseName.length() == 0) {
            //vscode expects all items to have a name
            return null;
        }

        Range range = definitionToRange(definition, project);
        if (range == null) {
            //we can't find where the source code for this symbol is located
            return null;
        }

        DocumentSymbol symbol = new DocumentSymbol();
        symbol.setKind(LanguageServerCompilerUtils.getSymbolKindFromDefinition(definition));
        symbol.setName(definitionBaseName);
        symbol.setRange(range);
        symbol.setSelectionRange(range);

        IDeprecationInfo deprecationInfo = definition.getDeprecationInfo();
        if (deprecationInfo != null) {
            symbol.setDeprecated(true);
        }

        return symbol;
    }

    public SymbolInformation definitionToSymbolInformation(IDefinition definition, ILspProject project) {
        String definitionBaseName = definition.getBaseName();
        if (definitionBaseName.length() == 0) {
            //vscode expects all items to have a name
            return null;
        }

        Location location = getLocationFromDefinition(definition, project);
        if (location == null) {
            //we can't find where the source code for this symbol is located
            return null;
        }

        SymbolInformation symbol = new SymbolInformation();
        symbol.setKind(LanguageServerCompilerUtils.getSymbolKindFromDefinition(definition));
        if (!definition.getQualifiedName().equals(definitionBaseName)) {
            symbol.setContainerName(definition.getPackageName());
        } else if (definition instanceof ITypeDefinition) {
            symbol.setContainerName("No Package");
        } else {
            IDefinition parentDefinition = definition.getParent();
            if (parentDefinition != null) {
                symbol.setContainerName(parentDefinition.getQualifiedName());
            }
        }
        symbol.setName(definitionBaseName);

        symbol.setLocation(location);

        IDeprecationInfo deprecationInfo = definition.getDeprecationInfo();
        if (deprecationInfo != null) {
            symbol.setDeprecated(true);
        }

        return symbol;
    }

    public void resolveDefinition(IDefinition definition, ActionScriptProjectData projectData, List<Location> result) {
        String definitionPath = definition.getSourcePath();
        String containingSourceFilePath = definition.getContainingSourceFilePath(projectData.project);
        if (projectData.includedFiles.containsKey(containingSourceFilePath)) {
            definitionPath = containingSourceFilePath;
        }
        if (definitionPath == null) {
            //if the definition is in an MXML file, getSourcePath() may return
            //null, but getContainingFilePath() will return something
            definitionPath = definition.getContainingFilePath();
            if (definitionPath == null) {
                //if everything is null, there's nothing to do
                return;
            }
            //however, getContainingFilePath() also works for SWCs
            if (!definitionPath.endsWith(FILE_EXTENSION_AS) && !definitionPath.endsWith(FILE_EXTENSION_MXML)
                    && (definitionPath.contains(SDK_LIBRARY_PATH_SIGNATURE_UNIX)
                            || definitionPath.contains(SDK_LIBRARY_PATH_SIGNATURE_WINDOWS))) {
                //if it's a framework SWC, we're going to attempt to resolve
                //the source file 
                String debugPath = DefinitionUtils.getDefinitionDebugSourceFilePath(definition, projectData.project);
                if (debugPath != null) {
                    definitionPath = debugPath;
                }
            }
            if (definitionPath.endsWith(FILE_EXTENSION_SWC) || definitionPath.endsWith(FILE_EXTENSION_ANE)) {
                DefinitionAsText definitionText = DefinitionTextUtils.definitionToTextDocument(definition,
                        projectData.project);
                //may be null if definitionToTextDocument() doesn't know how
                //to parse that type of definition
                if (definitionText != null) {
                    //if we get here, we couldn't find a framework source file and
                    //the definition path still ends with .swc
                    //we're going to try our best to display "decompiled" content
                    result.add(definitionText.toLocation());
                }
                return;
            }
            if (!definitionPath.endsWith(FILE_EXTENSION_AS) && !definitionPath.endsWith(FILE_EXTENSION_MXML)) {
                //if it's anything else, we don't know how to resolve
                return;
            }
        }

        Path resolvedPath = Paths.get(definitionPath);
        Location location = new Location();
        location.setUri(resolvedPath.toUri().toString());
        int nameLine = definition.getNameLine();
        int nameColumn = definition.getNameColumn();
        if (nameLine == -1 || nameColumn == -1) {
            //getNameLine() and getNameColumn() will both return -1 for a
            //variable definition created by an MXML tag with an id.
            //so we need to figure them out from the offset instead.
            int nameOffset = definition.getNameStart();
            if (nameOffset == -1) {
                //we can't find the name, so give up
                return;
            }

            Reader reader = fileTracker.getReader(resolvedPath);
            if (reader == null) {
                //we can't get the code at all
                return;
            }

            try {
                Position position = LanguageServerCompilerUtils.getPositionFromOffset(reader, nameOffset);
                nameLine = position.getLine();
                nameColumn = position.getCharacter();
            } finally {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
        if (nameLine == -1 || nameColumn == -1) {
            //we can't find the name, so give up
            return;
        }
        Position start = new Position();
        start.setLine(nameLine);
        start.setCharacter(nameColumn);
        Position end = new Position();
        end.setLine(nameLine);
        end.setCharacter(nameColumn + definition.getNameEnd() - definition.getNameStart());
        Range range = new Range();
        range.setStart(start);
        range.setEnd(end);
        location.setRange(range);
        result.add(location);
    }

    private ActionScriptProjectData addProject(Path projectRoot, WorkspaceFolder workspaceFolder) {
        IProjectConfigStrategy config = projectConfigStrategyFactory.create(projectRoot, workspaceFolder);
        ActionScriptProjectData projectData = new ActionScriptProjectData(projectRoot, workspaceFolder, config);
        projectData.codeProblemTracker.setLanguageClient(languageClient);
        projectData.configProblemTracker.setLanguageClient(languageClient);
        allProjectData.add(projectData);
        addProjectCallback.test(projectData);
        return projectData;
    }

    private void removeProject(ActionScriptProjectData projectData) {
        removeProjectCallback.test(projectData);
        allProjectData.remove(projectData);
        projectData.cleanup();
    }

    private void checkForMissingProjectsContainingSourceFile(Path path) {
        String configFileName = null;
        for (ActionScriptProjectData projectData : allProjectData) {
            if (projectData.config == null) {
                continue;
            }
            Path configFilePath = projectData.config.getConfigFilePath();
            if (configFilePath == null) {
                continue;
            }
            // this assumes that the configFileName is the same for all projects
            configFileName = configFilePath.getFileName().toString();
            break;
        }

        if (configFileName == null) {
            return;
        }

        for (WorkspaceFolder folder : workspaceFolders) {
            Path workspaceFolderPath = LanguageServerCompilerUtils.getPathFromLanguageServerURI(folder.getUri());
            if (workspaceFolderPath == null) {
                continue;
            }
            if (!path.startsWith(workspaceFolderPath)) {
                continue;
            }
            List<ActionScriptProjectData> workspaceProjects = getAllProjectDataForWorkspaceFolder(folder);
            Path currentPath = path.getParent();
            do {
                Path configFilePath = currentPath.resolve(configFileName);
                if (configFilePath.toFile().exists()) {
                    ActionScriptProjectData foundProjectData = null;
                    for (ActionScriptProjectData projectData : workspaceProjects) {
                        if (projectData.config == null) {
                            continue;
                        }

                        if (configFilePath.equals(projectData.config.getConfigFilePath())) {
                            foundProjectData = projectData;
                            break;
                        }
                    }
                    if (foundProjectData == null) {
                        addProject(currentPath, folder);
                    }
                }
                currentPath = currentPath.getParent();
            } while (currentPath != null && currentPath.startsWith(workspaceFolderPath));
        }
    }

    private List<ActionScriptProjectData> getAllProjectDataForWorkspaceFolder(WorkspaceFolder folder) {
        return allProjectData.stream().filter(projectData -> folder.equals(projectData.folder))
                .collect(Collectors.toList());
    }
}