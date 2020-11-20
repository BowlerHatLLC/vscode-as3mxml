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
package com.as3mxml.vscode.providers;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.as3mxml.asconfigc.compiler.ProjectType;
import com.as3mxml.vscode.commands.ICommandConstants;
import com.as3mxml.vscode.project.ILspProject;
import com.as3mxml.vscode.project.ActionScriptProjectData;
import com.as3mxml.vscode.services.ActionScriptLanguageClient;
import com.as3mxml.vscode.utils.ASTUtils;
import com.as3mxml.vscode.utils.CodeActionsUtils;
import com.as3mxml.vscode.utils.CompilerProjectUtils;
import com.as3mxml.vscode.utils.FileTracker;
import com.as3mxml.vscode.utils.ImportRange;
import com.as3mxml.vscode.utils.ImportTextEditUtils;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;
import com.as3mxml.vscode.utils.MXMLDataUtils;
import com.as3mxml.vscode.utils.ActionScriptProjectManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.filespecs.IFileSpecification;
import org.apache.royale.compiler.internal.mxml.MXMLData;
import org.apache.royale.compiler.internal.workspaces.Workspace;
import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IImportNode;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.apache.royale.utils.FilenameNormalization;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;

public class ExecuteCommandProvider {
    private static final String FILE_EXTENSION_MXML = ".mxml";
    private static final String FILE_EXTENSION_AS = ".as";

    private ActionScriptProjectManager actionScriptProjectManager;
    private FileTracker fileTracker;
    private Workspace compilerWorkspace;
    private ActionScriptLanguageClient languageClient;
    private boolean concurrentRequests;

    public ExecuteCommandProvider(ActionScriptProjectManager actionScriptProjectManager, FileTracker fileTracker,
            Workspace compilerWorkspace, ActionScriptLanguageClient languageClient, boolean concurrentRequests) {
        this.actionScriptProjectManager = actionScriptProjectManager;
        this.fileTracker = fileTracker;
        this.compilerWorkspace = compilerWorkspace;
        this.languageClient = languageClient;
        this.concurrentRequests = concurrentRequests;
    }

    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        switch (params.getCommand()) {
            case ICommandConstants.ADD_IMPORT: {
                return executeAddImportCommand(params);
            }
            case ICommandConstants.ADD_MXML_NAMESPACE: {
                return executeAddMXMLNamespaceCommand(params);
            }
            case ICommandConstants.ORGANIZE_IMPORTS_IN_URI: {
                return executeOrganizeImportsInUriCommand(params);
            }
            case ICommandConstants.ORGANIZE_IMPORTS_IN_DIRECTORY: {
                return executeOrganizeImportsInDirectoryCommand(params);
            }
            case ICommandConstants.GET_ACTIVE_PROJECT_URIS: {
                return executeGetActiveProjectUrisCommand(params);
            }
            default: {
                System.err.println("Unknown command: " + params.getCommand());
                return CompletableFuture.completedFuture(new Object());
            }
        }
    }

    private CompletableFuture<Object> executeOrganizeImportsInDirectoryCommand(ExecuteCommandParams params) {
        if (!concurrentRequests) {
            return CompletableFuture.completedFuture(executeOrganizeImportsInDirectoryCommand2(params, null));
        }
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken -> {
            cancelToken.checkCanceled();
            return executeOrganizeImportsInDirectoryCommand2(params, cancelToken);
        });
    }

    private Object executeOrganizeImportsInDirectoryCommand2(ExecuteCommandParams params, CancelChecker cancelToken) {
        List<Object> args = params.getArguments();
        JsonObject uriObject = (JsonObject) args.get(0);
        String directoryURI = uriObject.get("external").getAsString();

        Path directoryPath = LanguageServerCompilerUtils.getPathFromLanguageServerURI(directoryURI);
        if (directoryPath == null) {
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return new Object();
        }

        File directoryFile = directoryPath.toFile();
        if (!directoryFile.isDirectory()) {
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return new Object();
        }

        List<Path> filesToClose = new ArrayList<>();
        List<String> fileURIs = new ArrayList<>();
        List<File> directories = new ArrayList<>();
        directories.add(directoryFile);
        for (int i = 0; i < directories.size(); i++) {
            File currentDir = directories.get(i);
            File[] files = currentDir.listFiles();
            for (File file : files) {
                if (file.isDirectory()) {
                    //add this directory to the list to search
                    directories.add(file);
                    continue;
                }
                if (!file.getName().endsWith(FILE_EXTENSION_AS) && !file.getName().endsWith(FILE_EXTENSION_MXML)) {
                    continue;
                }
                fileURIs.add(file.toURI().toString());
                Path filePath = file.toPath();
                if (!fileTracker.isOpen(filePath)) {
                    filesToClose.add(file.toPath());
                    openFileForOrganizeImports(filePath);
                }
            }
        }
        if (fileURIs.size() == 0) {
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return new Object();
        }

        compilerWorkspace.startBuilding();
        ApplyWorkspaceEditParams editParams = null;
        try {
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            Map<String, List<TextEdit>> changes = new HashMap<>();
            for (String fileURI : fileURIs) {
                organizeImportsInUri(fileURI, changes);
            }

            if (changes.keySet().size() > 0) {
                editParams = new ApplyWorkspaceEditParams();
                WorkspaceEdit workspaceEdit = new WorkspaceEdit();
                workspaceEdit.setChanges(changes);
                editParams.setEdit(workspaceEdit);
            }
        } finally {
            compilerWorkspace.doneBuilding();
            for (Path filePath : filesToClose) {
                fileTracker.closeFile(filePath);
            }
        }
        if (cancelToken != null) {
            cancelToken.checkCanceled();
        }
        if (editParams != null) {
            languageClient.applyEdit(editParams);
        }
        return new Object();
    }

    private CompletableFuture<Object> executeOrganizeImportsInUriCommand(ExecuteCommandParams params) {
        if (!concurrentRequests) {
            return CompletableFuture.completedFuture(executeOrganizeImportsInUriCommand2(params, null));
        }
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken -> {
            cancelToken.checkCanceled();
            return executeOrganizeImportsInUriCommand2(params, cancelToken);
        });
    }

    private Object executeOrganizeImportsInUriCommand2(ExecuteCommandParams params, CancelChecker cancelToken) {
        List<Object> args = params.getArguments();
        JsonObject uriObject = (JsonObject) args.get(0);
        String uri = uriObject.get("external").getAsString();

        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(uri);
        if (path == null) {
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return new Object();
        }

        boolean isOpen = fileTracker.isOpen(path);
        if (!isOpen) {
            openFileForOrganizeImports(path);
        }

        compilerWorkspace.startBuilding();
        ApplyWorkspaceEditParams editParams = null;
        try {
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }

            Map<String, List<TextEdit>> changes = new HashMap<>();
            organizeImportsInUri(uri, changes);

            if (changes.keySet().size() > 0) {
                editParams = new ApplyWorkspaceEditParams();
                WorkspaceEdit workspaceEdit = new WorkspaceEdit();
                workspaceEdit.setChanges(changes);
                editParams.setEdit(workspaceEdit);
            }
        } finally {
            compilerWorkspace.doneBuilding();
        }
        if (!isOpen) {
            fileTracker.closeFile(path);
        }
        if (cancelToken != null) {
            cancelToken.checkCanceled();
        }
        if (editParams != null) {
            languageClient.applyEdit(editParams);
        }
        return new Object();
    }

    private void openFileForOrganizeImports(Path path) {
        if (fileTracker.isOpen(path)) {
            //already opened
            return;
        }

        //if the file isn't open in an editor, we need to read it from the
        //file system instead.
        String text = fileTracker.getText(path);
        if (text == null) {
            return;
        }

        //for some reason, the full AST is not populated if the file is not
        //already open in the editor. we use a similar workaround to didOpen
        //to force the AST to be populated.

        //we'll clear this out later before we return from this function
        fileTracker.openFile(path, text);

        //notify the workspace that it should read the file from memory
        //instead of loading from the file system
        String normalizedPath = FilenameNormalization.normalize(path.toAbsolutePath().toString());
        IFileSpecification fileSpec = fileTracker.getFileSpecification(normalizedPath);
        compilerWorkspace.fileChanged(fileSpec);
    }

    private void organizeImportsInUri(String uri, Map<String, List<TextEdit>> changes) {
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(uri);
        if (path == null) {
            return;
        }
        ActionScriptProjectData projectData = actionScriptProjectManager.getProjectDataForSourceFile(path);
        if (projectData == null || projectData.project == null) {
            return;
        }
        ILspProject project = projectData.project;

        ICompilationUnit unit = CompilerProjectUtils.findCompilationUnit(path, project);
        if (unit == null) {
            return;
        }

        String text = fileTracker.getText(path);
        if (text == null) {
            return;
        }

        Set<String> missingNames = null;
        Set<String> importsToAdd = null;
        List<IImportNode> importsToRemove = null;
        IASNode ast = ASTUtils.getCompilationUnitAST(unit);
        if (ast != null) {
            missingNames = ASTUtils.findUnresolvedIdentifiersToImport(ast, project);
            Set<String> requiredImports = project.getQNamesOfDependencies(unit);
            importsToRemove = ASTUtils.findImportNodesToRemove(ast, requiredImports);
        }
        if (missingNames != null) {
            importsToAdd = new HashSet<>();
            Collection<ICompilationUnit> units = project.getCompilationUnits();
            for (String missingName : missingNames) {
                List<IDefinition> types = ASTUtils.findTypesThatMatchName(missingName, units);
                if (types.size() == 1) {
                    //add an import only if exactly one type is found
                    importsToAdd.add(types.get(0).getQualifiedName());
                }
            }
        }
        List<TextEdit> edits = ImportTextEditUtils.organizeImports(text, importsToRemove, importsToAdd);
        if (edits == null || edits.size() == 0) {
            //no edit required
            return;
        }
        changes.put(uri, edits);
    }

    private CompletableFuture<Object> executeAddImportCommand(ExecuteCommandParams params) {
        if (!concurrentRequests) {
            return CompletableFuture.completedFuture(executeAddImportCommand2(params, null));
        }
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken -> {
            cancelToken.checkCanceled();
            return executeAddImportCommand2(params, cancelToken);
        });
    }

    private Object executeAddImportCommand2(ExecuteCommandParams params, CancelChecker cancelToken) {

        compilerWorkspace.startBuilding();
        try {
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            List<Object> args = params.getArguments();
            String qualifiedName = ((JsonPrimitive) args.get(0)).getAsString();
            String uri = ((JsonPrimitive) args.get(1)).getAsString();
            int line = ((JsonPrimitive) args.get(2)).getAsInt();
            int character = ((JsonPrimitive) args.get(3)).getAsInt();
            if (qualifiedName == null) {
                if (cancelToken != null) {
                    cancelToken.checkCanceled();
                }
                return new Object();
            }
            Path pathForImport = LanguageServerCompilerUtils.getPathFromLanguageServerURI(uri);
            if (pathForImport == null) {
                if (cancelToken != null) {
                    cancelToken.checkCanceled();
                }
                return new Object();
            }
            ActionScriptProjectData projectData = actionScriptProjectManager.getProjectDataForSourceFile(pathForImport);
            if (projectData == null || projectData.project == null) {
                if (cancelToken != null) {
                    cancelToken.checkCanceled();
                }
                return new Object();
            }
            String text = fileTracker.getText(pathForImport);
            if (text == null) {
                if (cancelToken != null) {
                    cancelToken.checkCanceled();
                }
                return new Object();
            }
            int currentOffset = LanguageServerCompilerUtils.getOffsetFromPosition(new StringReader(text),
                    new Position(line, character));
            ImportRange importRange = null;
            if (uri.endsWith(FILE_EXTENSION_MXML)) {
                MXMLData mxmlData = actionScriptProjectManager.getMXMLDataForPath(pathForImport, projectData);
                IMXMLTagData offsetTag = MXMLDataUtils.getOffsetMXMLTag(mxmlData, currentOffset);
                importRange = ImportRange.fromOffsetTag(offsetTag, currentOffset);
            } else {
                IASNode offsetNode = actionScriptProjectManager.getOffsetNode(pathForImport, currentOffset,
                        projectData);
                importRange = ImportRange.fromOffsetNode(offsetNode);
            }
            WorkspaceEdit workspaceEdit = CodeActionsUtils.createWorkspaceEditForAddImport(qualifiedName, text, uri,
                    importRange);
            if (workspaceEdit == null) {
                //no edit required
                return new Object();
            }

            ApplyWorkspaceEditParams editParams = new ApplyWorkspaceEditParams();
            editParams.setEdit(workspaceEdit);

            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            languageClient.applyEdit(editParams);
            return new Object();
        } finally {
            compilerWorkspace.doneBuilding();
        }
    }

    private CompletableFuture<Object> executeAddMXMLNamespaceCommand(ExecuteCommandParams params) {
        if (!concurrentRequests) {
            return CompletableFuture.completedFuture(executeAddMXMLNamespaceCommand2(params, null));
        }
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken -> {
            cancelToken.checkCanceled();
            return executeAddMXMLNamespaceCommand2(params, cancelToken);
        });
    }

    private Object executeAddMXMLNamespaceCommand2(ExecuteCommandParams params, CancelChecker cancelToken) {
        compilerWorkspace.startBuilding();
        try {
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            List<Object> args = params.getArguments();
            String nsPrefix = ((JsonPrimitive) args.get(0)).getAsString();
            String nsUri = ((JsonPrimitive) args.get(1)).getAsString();
            String uri = ((JsonPrimitive) args.get(2)).getAsString();
            int startIndex = ((JsonPrimitive) args.get(3)).getAsInt();
            int endIndex = ((JsonPrimitive) args.get(4)).getAsInt();
            if (nsPrefix == null || nsUri == null) {
                if (cancelToken != null) {
                    cancelToken.checkCanceled();
                }
                return new Object();
            }
            Path pathForImport = LanguageServerCompilerUtils.getPathFromLanguageServerURI(uri);
            if (pathForImport == null) {
                if (cancelToken != null) {
                    cancelToken.checkCanceled();
                }
                return new Object();
            }
            String text = fileTracker.getText(pathForImport);
            if (text == null) {
                if (cancelToken != null) {
                    cancelToken.checkCanceled();
                }
                return new Object();
            }
            WorkspaceEdit workspaceEdit = CodeActionsUtils.createWorkspaceEditForAddMXMLNamespace(nsPrefix, nsUri, text,
                    uri, startIndex, endIndex);
            if (workspaceEdit == null) {
                if (cancelToken != null) {
                    cancelToken.checkCanceled();
                }
                //no edit required
                return new Object();
            }

            ApplyWorkspaceEditParams editParams = new ApplyWorkspaceEditParams();
            editParams.setEdit(workspaceEdit);

            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            languageClient.applyEdit(editParams);
            return new Object();
        } finally {
            compilerWorkspace.doneBuilding();
        }
    }

    private CompletableFuture<Object> executeGetActiveProjectUrisCommand(ExecuteCommandParams params) {
        List<Object> args = params.getArguments();
        final boolean appsOnly = args.size() > 0 && ((JsonPrimitive) args.get(0)).getAsBoolean();
        List<String> result = actionScriptProjectManager.getAllProjectData().stream()
                .filter(projectData -> appsOnly ? ProjectType.APP.equals(projectData.options.type) : true)
                .map(projectData -> projectData.projectRoot.toUri().toString()).collect(Collectors.toList());
        return CompletableFuture.completedFuture(result);
    }
}