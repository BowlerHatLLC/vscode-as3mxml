/*
Copyright 2016-2019 Bowler Hat LLC

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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.as3mxml.vscode.asdoc.VSCodeASDocDelegate;
import com.as3mxml.vscode.project.IProjectConfigStrategy;
import com.as3mxml.vscode.project.WorkspaceFolderData;
import com.as3mxml.vscode.utils.CompilationUnitUtils.IncludeFileData;

import org.apache.commons.io.IOUtils;
import org.apache.royale.compiler.config.Configuration;
import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IEventDefinition;
import org.apache.royale.compiler.filespecs.IFileSpecification;
import org.apache.royale.compiler.internal.mxml.MXMLData;
import org.apache.royale.compiler.internal.parsing.as.OffsetCue;
import org.apache.royale.compiler.internal.projects.RoyaleProject;
import org.apache.royale.compiler.internal.workspaces.Workspace;
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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.WorkspaceFolder;

public class WorkspaceFolderManager
{
    private static final String MXML_EXTENSION = ".mxml";

    public Workspace compilerWorkspace;
    public Map<Path, String> sourceByPath = new HashMap<>();
    public LanguageServerFileSpecGetter fileSpecGetter;
    private List<WorkspaceFolder> workspaceFolders = new ArrayList<>();
    private Map<WorkspaceFolder, WorkspaceFolderData> workspaceFolderToData = new HashMap<>();
    
    public WorkspaceFolderManager()
    {
        compilerWorkspace = new Workspace();
        compilerWorkspace.setASDocDelegate(new VSCodeASDocDelegate());
        fileSpecGetter = new LanguageServerFileSpecGetter(compilerWorkspace, sourceByPath);
    }

    public List<WorkspaceFolder> getWorkspaceFolders()
    {
        return workspaceFolders;
    }

    public WorkspaceFolderData getWorkspaceFolderData(WorkspaceFolder folder)
    {
        return workspaceFolderToData.get(folder);
    }

    public WorkspaceFolderData addWorkspaceFolder(WorkspaceFolder folder, IProjectConfigStrategy config)
    {
        workspaceFolders.add(folder);
        WorkspaceFolderData folderData = new WorkspaceFolderData(folder, config);
        workspaceFolderToData.put(folder, folderData);
        return folderData;
    }

    public void removeWorkspaceFolder(WorkspaceFolder folder)
    {
        if(!workspaceFolderToData.containsKey(folder))
        {
            return;
        }
        workspaceFolders.remove(folder);
        WorkspaceFolderData folderData = workspaceFolderToData.get(folder);
        workspaceFolderToData.remove(folder);
        folderData.cleanup();
    }

    public IASNode getOffsetNode(Path path, int currentOffset, WorkspaceFolderData folderData)
    {
        IncludeFileData includeFileData = folderData.includedFiles.get(path.toString());
        if(includeFileData != null)
        {
            path = Paths.get(includeFileData.parentPath);
        }
        RoyaleProject project = folderData.project;
        if (!SourcePathUtils.isInProjectSourcePath(path, project, folderData.configurator))
        {
            //the path must be in the workspace or source-path
            return null;
        }

        ICompilationUnit unit = CompilerProjectUtils.findCompilationUnit(path, project);
        if (unit == null)
        {
            //the path must be in the workspace or source-path
            return null;
        }

        IASNode ast = ASTUtils.getCompilationUnitAST(unit);
        if (ast == null)
        {
            return null;
        }

        return ASTUtils.getContainingNodeIncludingStart(ast, currentOffset);
    }

    public WorkspaceFolderData getWorkspaceFolderDataForSourceFile(Path path)
    {
        //first try to find the path in an existing project
        WorkspaceFolderData fallback = null;
        for (WorkspaceFolderData folderData : workspaceFolderToData.values())
        {
            RoyaleProject project = folderData.project;
            if (project == null)
            {
                continue;
            }
            String uri = folderData.folder.getUri();
            Path workspacePath = LanguageServerCompilerUtils.getPathFromLanguageServerURI(uri);
            if (workspacePath != null && SourcePathUtils.isInProjectSourcePath(path, project, folderData.configurator))
            {
                if(path.startsWith(workspacePath))
                {
                    //if the source path is inside the workspace folder, it's a
                    //perfect match
                    return folderData;
                }
                //if path is in the source path, but not inside the workspace
                //folder, save it as possible result for later. in other words,
                //we always prefer a workspace that contains the file, so we'll
                //check the other workspaces before using the fallback.
                if (fallback == null)
                {
                    fallback = folderData;
                }
            }
        }
        //we found the path in a project's source path, but not inside any the
        //workspace folders
        if (fallback != null)
        {
            return fallback;

        }
        //if none of the existing projects worked, try a folder where a project
        //hasn't been created yet
        for (WorkspaceFolderData folderData : workspaceFolderToData.values())
        {
            String uri = folderData.folder.getUri();
            Path workspacePath = LanguageServerCompilerUtils.getPathFromLanguageServerURI(uri);
            if (workspacePath == null)
            {
                continue;
            }
			if (path.startsWith(workspacePath))
			{
                return folderData;
			}
        }
        return null;
    }

    public IASNode getEmbeddedActionScriptNodeInMXMLTag(IMXMLTagData tag, Path path, int currentOffset, WorkspaceFolderData folderData)
    {
        RoyaleProject project = folderData.project;
        IMXMLTagAttributeData attributeData = MXMLDataUtils.getMXMLTagAttributeWithValueAtOffset(tag, currentOffset);
        if (attributeData != null)
        {
            //some attributes can have ActionScript completion, such as
            //events and properties with data binding

            IDefinition resolvedDefinition = project.resolveXMLNameToDefinition(tag.getXMLName(), tag.getMXMLDialect());
            //prominic/Moonshine-IDE#/203: don't allow interface definitions because
            //we cannot resolve specifiers. <fx:Component> resolves to an interface
            //definition, and it can have an id attribute.
            if (resolvedDefinition == null || !(resolvedDefinition instanceof IClassDefinition))
            {
                //we can't figure out which class the tag represents!
                //maybe the user hasn't defined the tag's namespace or something
                return null;
            }
            IClassDefinition tagDefinition = (IClassDefinition) resolvedDefinition;
            IDefinition attributeDefinition = project.resolveSpecifier(tagDefinition, attributeData.getShortName());
            if (attributeDefinition instanceof IEventDefinition)
            {
                IASNode offsetNode = getOffsetNode(path, currentOffset, folderData);
                if (offsetNode instanceof IMXMLClassReferenceNode)
                {
                    IMXMLClassReferenceNode mxmlNode = (IMXMLClassReferenceNode) offsetNode;
                    IMXMLEventSpecifierNode eventNode = mxmlNode.getEventSpecifierNode(attributeData.getShortName());
                    //the event node might be null if the MXML document isn't in a
                    //fully valid state (unclosed tags, for instance)
                    if (eventNode != null)
                    {
                        for (IASNode asNode : eventNode.getASNodes())
                        {
                            IASNode containingNode = ASTUtils.getContainingNodeIncludingStart(asNode, currentOffset);
                            if (containingNode != null)
                            {
                                return containingNode;
                            }
                        }
                    }
                    return eventNode;
                }
            }
            else
            {
                IASNode offsetNode = getOffsetNode(path, currentOffset, folderData);
                if (offsetNode instanceof IMXMLClassReferenceNode)
                {
                    IMXMLClassReferenceNode mxmlNode = (IMXMLClassReferenceNode) offsetNode;
                    IMXMLPropertySpecifierNode propertyNode = mxmlNode.getPropertySpecifierNode(attributeData.getShortName());
                    if (propertyNode != null)
                    {
                        for (int i = 0, count = propertyNode.getChildCount(); i < count; i++)
                        {
                            IMXMLNode propertyChild = (IMXMLNode) propertyNode.getChild(i);
                            if (propertyChild instanceof IMXMLConcatenatedDataBindingNode)
                            {
                                IMXMLConcatenatedDataBindingNode dataBinding = (IMXMLConcatenatedDataBindingNode) propertyChild;
                                for (int j = 0, childCount = dataBinding.getChildCount(); j < childCount; j++)
                                {
                                    IASNode dataBindingChild = dataBinding.getChild(i);
                                    if (dataBindingChild.contains(currentOffset)
                                            && dataBindingChild instanceof IMXMLSingleDataBindingNode)
                                    {
                                        //we'll parse this in a moment, as if it were
                                        //a direct child of the property specifier
                                        propertyChild = (IMXMLSingleDataBindingNode) dataBindingChild;
                                        break;
                                    }
                                }
                            }
                            if (propertyChild instanceof IMXMLSingleDataBindingNode)
                            {
                                IMXMLSingleDataBindingNode dataBinding = (IMXMLSingleDataBindingNode) propertyChild;
                                IASNode containingNode = dataBinding.getExpressionNode().getContainingNode(currentOffset);
                                if (containingNode == null)
                                {
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

    public MXMLData getMXMLDataForPath(Path path, WorkspaceFolderData folderData)
    {
        IncludeFileData includeFileData = folderData.includedFiles.get(path.toString());
        if(includeFileData != null)
        {
            path = Paths.get(includeFileData.parentPath);
        }
        if (!path.toString().endsWith(MXML_EXTENSION))
        {
            // don't try to parse ActionScript files as MXML
            return null;
        }
        RoyaleProject project = folderData.project;
        if (!SourcePathUtils.isInProjectSourcePath(path, project, folderData.configurator))
        {
            //the path must be in the workspace or source-path
            return null;
        }

        //need to ensure that the compilation unit exists, even though we don't
        //use it directly
        ICompilationUnit unit = CompilerProjectUtils.findCompilationUnit(path, project);
        if (unit == null)
        {
            //no need to log this case because it can happen for reasons that
            //should have been logged already
            return null;
        }
        IMXMLDataManager mxmlDataManager = compilerWorkspace.getMXMLDataManager();
        String normalizedPath = FilenameNormalization.normalize(path.toAbsolutePath().toString());
        IFileSpecification fileSpecification = fileSpecGetter.getFileSpecification(normalizedPath);
        return (MXMLData) mxmlDataManager.get(fileSpecification);
    }

    public ICompilationUnit findCompilationUnit(Path pathToFind)
    {
        for (WorkspaceFolderData folderData : workspaceFolderToData.values())
        {
            RoyaleProject project = folderData.project;
            if (project == null)
            {
                continue;
            }
            ICompilationUnit result = CompilerProjectUtils.findCompilationUnit(pathToFind, project);
            if(result != null)
            {
                return result;
            }
        }
        return null;
    }

    public int getOffsetFromPathAndPosition(Path path, Position position, WorkspaceFolderData folderData)
    {
        int offset = 0;
        Reader reader = getReaderForPath(path);
        try
        {
            offset = LanguageServerCompilerUtils.getOffsetFromPosition(reader, position);
        }
        finally
        {
            try
            {
                reader.close();
            }
            catch(IOException e) {}
        }
 
        IncludeFileData includeFileData = folderData.includedFiles.get(path.toString());
        if(includeFileData != null)
        {
            int originalOffset = offset;
            //we're actually going to use the offset from the file that includes
            //this one
            for(OffsetCue offsetCue : includeFileData.getOffsetCues())
            {
                if(originalOffset >= offsetCue.local)
                {
                    offset = originalOffset + offsetCue.adjustment;
                }
            }
        }
        return offset;
    }

    public Reader getReaderForPath(Path path)
    {
        if(path == null)
        {
            return null;
        }
        Reader reader = null;
        if (sourceByPath.containsKey(path))
        {
            //if the file is open, use the edited code
            String code = sourceByPath.get(path);
            reader = new StringReader(code);
        }
        else
        {
            File file = new File(path.toAbsolutePath().toString());
            if (!file.exists())
            {
                return null;
            }
            //if the file is not open, read it from the file system
            try
            {
                reader = new FileReader(file);
            }
            catch (FileNotFoundException e)
            {
                //do nothing
            }
        }
        return reader;
    }

    public String getFileTextForPath(Path path)
    {
        if(sourceByPath.containsKey(path))
        {
            return sourceByPath.get(path);
        }
        Reader reader = getReaderForPath(path);
        if(reader == null)
        {
            return null;
        }
        try
        {
            return IOUtils.toString(reader);
        }
        catch (IOException e)
        {
            return null;
        }
        finally
        {
            try
            {
                reader.close();
            }
            catch(IOException e) {}
        }
    }

    public List<WorkspaceFolderData> getAllWorkspaceFolderDataForSourceFile(Path path)
    {
        List<WorkspaceFolderData> result = new ArrayList<>();
        for (WorkspaceFolderData folderData : workspaceFolderToData.values())
        {
            RoyaleProject project = folderData.project;
            if (project == null)
            {
                String folderUri = folderData.folder.getUri();
                Path workspacePath = LanguageServerCompilerUtils.getPathFromLanguageServerURI(folderUri);
                if (path.startsWith(workspacePath))
                {
                    result.add(folderData);
                }
            }
            else if (SourcePathUtils.isInProjectSourcePath(path, project, folderData.configurator))
            {
                result.add(folderData);
            }
        }
        return result;
    }

    public List<WorkspaceFolderData> getAllWorkspaceFolderDataForSWCFile(Path path)
    {
        List<WorkspaceFolderData> result = new ArrayList<>();
        for (WorkspaceFolderData folderData : workspaceFolderToData.values())
        {
            RoyaleProject project = folderData.project;
            if (project == null)
            {
                String folderUri = folderData.folder.getUri();
                Path workspacePath = LanguageServerCompilerUtils.getPathFromLanguageServerURI(folderUri);
                if (path.startsWith(workspacePath))
                {
                    result.add(folderData);
                }
            }
            else
            {
                Configuration configuration = folderData.configurator.getConfiguration();
                if (SourcePathUtils.isInProjectLibraryPathOrExternalLibraryPath(path, project, configuration))
                {
                    result.add(folderData);
                }
            }
        }
        return result;
    }

    public boolean isInActionScriptComment(Path path, int currentOffset, int minCommentStartIndex)
    {
        if (path == null || !sourceByPath.containsKey(path))
        {
            return false;
        }
        String code = sourceByPath.get(path);
        int startComment = code.lastIndexOf("/*", currentOffset - 1);
        if (startComment != -1 && startComment >= minCommentStartIndex)
        {
            int endComment = code.indexOf("*/", startComment);
            if (endComment > currentOffset)
            {
                return true;
            }
        }
        int startLine = code.lastIndexOf('\n', currentOffset - 1);
        if (startLine == -1)
        {
            //we're on the first line
            startLine = 0;
        }
        //we need to stop searching after the end of the current line
        int endLine = code.indexOf('\n', currentOffset);
        do
        {
            //we need to check this in a loop because it's possible for
            //the start of a single line comment to appear inside multiple
            //MXML attributes on the same line
            startComment = code.indexOf("//", startLine);
            if(startComment != -1 && currentOffset > startComment && startComment >= minCommentStartIndex)
            {
                return true;
            }
            startLine = startComment + 2;
        }
        while(startComment != -1 && startLine < endLine);
        return false;
    }

    public boolean isInXMLComment(Path path, int currentOffset)
    {
        if (!sourceByPath.containsKey(path))
        {
            return false;
        }
        String code = sourceByPath.get(path);
        int startComment = code.lastIndexOf("<!--", currentOffset - 1);
        if (startComment == -1)
        {
            return false;
        }
        int endComment = code.indexOf("-->", startComment);
        return endComment > currentOffset;
    }
}