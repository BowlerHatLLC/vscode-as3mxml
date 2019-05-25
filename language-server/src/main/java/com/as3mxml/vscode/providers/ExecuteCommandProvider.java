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
package com.as3mxml.vscode.providers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.as3mxml.asconfigc.ASConfigC;
import com.as3mxml.asconfigc.ASConfigCException;
import com.as3mxml.asconfigc.ASConfigCOptions;
import com.as3mxml.vscode.commands.ICommandConstants;
import com.as3mxml.vscode.compiler.CompilerShell;
import com.as3mxml.vscode.project.WorkspaceFolderData;
import com.as3mxml.vscode.services.ActionScriptLanguageClient;
import com.as3mxml.vscode.utils.ASTUtils;
import com.as3mxml.vscode.utils.CodeActionsUtils;
import com.as3mxml.vscode.utils.CompilerProjectUtils;
import com.as3mxml.vscode.utils.ImportRange;
import com.as3mxml.vscode.utils.ImportTextEditUtils;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;
import com.as3mxml.vscode.utils.MXMLDataUtils;
import com.as3mxml.vscode.utils.WorkspaceFolderManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.filespecs.IFileSpecification;
import org.apache.royale.compiler.internal.mxml.MXMLData;
import org.apache.royale.compiler.internal.projects.RoyaleProject;
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
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;

public class ExecuteCommandProvider
{
    private static final String MXML_EXTENSION = ".mxml";
    private static final String AS_EXTENSION = ".as";
    private static final String PROPERTY_FRAMEWORK_LIB = "royalelib";

	private WorkspaceFolderManager workspaceFolderManager;
	private Workspace compilerWorkspace;
	private ActionScriptLanguageClient languageClient;
    private CompilerShell compilerShell;

	public ExecuteCommandProvider(WorkspaceFolderManager workspaceFolderManager, Workspace compilerWorkspace, ActionScriptLanguageClient languageClient)
	{
		this.workspaceFolderManager = workspaceFolderManager;
		this.compilerWorkspace = compilerWorkspace;
		this.languageClient = languageClient;
	}

	public CompletableFuture<Object> executeCommand(ExecuteCommandParams params)
	{
        switch(params.getCommand())
        {
            case ICommandConstants.QUICK_COMPILE:
            {
                return executeQuickCompileCommand(params);
            }
            case ICommandConstants.ADD_IMPORT:
            {
                return executeAddImportCommand(params);
            }
            case ICommandConstants.ADD_MXML_NAMESPACE:
            {
                return executeAddMXMLNamespaceCommand(params);
            }
            case ICommandConstants.ORGANIZE_IMPORTS_IN_URI:
            {
                return executeOrganizeImportsInUriCommand(params);
            }
            case ICommandConstants.ORGANIZE_IMPORTS_IN_DIRECTORY:
            {
                return executeOrganizeImportsInDirectoryCommand(params);
            }
            default:
            {
                System.err.println("Unknown command: " + params.getCommand());
                return CompletableFuture.completedFuture(new Object());
            }
        }
	}

    private CompletableFuture<Object> executeOrganizeImportsInDirectoryCommand(ExecuteCommandParams params)
    {
        List<Object> args = params.getArguments();
        JsonObject uriObject = (JsonObject) args.get(0);
        String directoryURI = uriObject.get("external").getAsString();

        Path directoryPath = LanguageServerCompilerUtils.getPathFromLanguageServerURI(directoryURI);
        if (directoryPath == null)
        {
            return CompletableFuture.completedFuture(new Object());
        }

        File directoryFile = directoryPath.toFile();
        if (!directoryFile.isDirectory())
        {
            return CompletableFuture.completedFuture(new Object());
        }

        List<Path> filesToClose = new ArrayList<>();
        List<String> fileURIs = new ArrayList<>();
        List<File> directories = new ArrayList<>();
        directories.add(directoryFile);
        for(int i = 0; i < directories.size(); i++)
        {
            File currentDir = directories.get(i);
            File[] files = currentDir.listFiles();
            for (File file : files)
            {
                if (file.isDirectory())
                {
                    //add this directory to the list to search
                    directories.add(file);
                    continue;
                }
                if (!file.getName().endsWith(AS_EXTENSION) && !file.getName().endsWith(MXML_EXTENSION))
                {
                    continue;
                }
                fileURIs.add(file.toURI().toString());
                Path filePath = file.toPath();
                if(!workspaceFolderManager.sourceByPath.containsKey(filePath))
                {
                    filesToClose.add(file.toPath());
                    openFileForOrganizeImports(filePath);
                }
            }
        }
        if (fileURIs.size() == 0)
        {
            return CompletableFuture.completedFuture(new Object());
        }

        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken ->
        {
            cancelToken.checkCanceled();

            compilerWorkspace.startBuilding();
            ApplyWorkspaceEditParams editParams = null;
            try
            {
                cancelToken.checkCanceled();
                Map<String,List<TextEdit>> changes = new HashMap<>();
                for(String fileURI : fileURIs)
                {
                    organizeImportsInUri(fileURI, changes);
                }
                
                if(changes.keySet().size() > 0)
                {
                    editParams = new ApplyWorkspaceEditParams();
                    WorkspaceEdit workspaceEdit = new WorkspaceEdit();
                    workspaceEdit.setChanges(changes);
                    editParams.setEdit(workspaceEdit);
                }
            }
            finally
            {
                compilerWorkspace.doneBuilding();
            }
            for(Path filePath : filesToClose)
            {
                workspaceFolderManager.sourceByPath.remove(filePath);
            }
            if(editParams != null)
            {
                languageClient.applyEdit(editParams);
            }
            return new Object();
        });
    }
    
    private CompletableFuture<Object> executeOrganizeImportsInUriCommand(ExecuteCommandParams params)
    {
        List<Object> args = params.getArguments();
        JsonObject uriObject = (JsonObject) args.get(0);
        String uri = uriObject.get("external").getAsString();

        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(uri);
        if (path == null)
        {
            return CompletableFuture.completedFuture(new Object());
        }

        boolean isOpen = workspaceFolderManager.sourceByPath.containsKey(path);
        if(!isOpen)
        {
            openFileForOrganizeImports(path);
        }
        
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken ->
        {
            cancelToken.checkCanceled();

            compilerWorkspace.startBuilding();
            ApplyWorkspaceEditParams editParams = null;
            try
            {
                cancelToken.checkCanceled();
                
                Map<String,List<TextEdit>> changes = new HashMap<>();
                organizeImportsInUri(uri, changes);

                if(changes.keySet().size() > 0)
                {
                    editParams = new ApplyWorkspaceEditParams();
                    WorkspaceEdit workspaceEdit = new WorkspaceEdit();
                    workspaceEdit.setChanges(changes);
                    editParams.setEdit(workspaceEdit);
                }
            }
            finally
            {
                compilerWorkspace.doneBuilding();
            }
            if(!isOpen)
            {
                workspaceFolderManager.sourceByPath.remove(path);
            }
            if(editParams != null)
            {
                languageClient.applyEdit(editParams);
            }
            return new Object();
        });
    }

    private void openFileForOrganizeImports(Path path)
    {
        if(workspaceFolderManager.sourceByPath.containsKey(path))
        {
            //already opened
            return;
        }

        //if the file isn't open in an editor, we need to read it from the
        //file system instead.
        String text = workspaceFolderManager.getFileTextForPath(path);
        if(text == null)
        {
            return;
        }

        //for some reason, the full AST is not populated if the file is not
        //already open in the editor. we use a similar workaround to didOpen
        //to force the AST to be populated.

        //we'll clear this out later before we return from this function
        workspaceFolderManager.sourceByPath.put(path, text);

        //notify the workspace that it should read the file from memory
        //instead of loading from the file system
        String normalizedPath = FilenameNormalization.normalize(path.toAbsolutePath().toString());
        IFileSpecification fileSpec = workspaceFolderManager.getFileSpecification(normalizedPath);
        compilerWorkspace.fileChanged(fileSpec);
    }

    private void organizeImportsInUri(String uri, Map<String,List<TextEdit>> changes)
    {
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(uri);
        if(path == null)
        {
            return;
        }
        WorkspaceFolderData folderData = workspaceFolderManager.getWorkspaceFolderDataForSourceFile(path);
        if(folderData == null || folderData.project == null)
        {
            return;
        }
        RoyaleProject project = folderData.project;
        
        ICompilationUnit unit = CompilerProjectUtils.findCompilationUnit(path, project);
        if(unit == null)
        {
            return;
        }

        String text = workspaceFolderManager.getFileTextForPath(path);
        if(text == null)
        {
            return;
        }

        Set<String> missingNames = null;
        Set<String> importsToAdd = null;
        Set<IImportNode> importsToRemove = null;
        IASNode ast = ASTUtils.getCompilationUnitAST(unit);
        if (ast != null)
        {
            missingNames = ASTUtils.findUnresolvedIdentifiersToImport(ast, project);
            importsToRemove = ASTUtils.findImportNodesToRemove(ast, project);
        }
        if (missingNames != null)
        {
            importsToAdd = new HashSet<>();
            Collection<ICompilationUnit> units = project.getCompilationUnits();
            for (String missingName : missingNames)
            {
                List<IDefinition> types = ASTUtils.findTypesThatMatchName(missingName, units);
                if (types.size() == 1)
                {
                    //add an import only if exactly one type is found
                    importsToAdd.add(types.get(0).getQualifiedName());
                }
            }
        }
        List<TextEdit> edits = ImportTextEditUtils.organizeImports(text, importsToRemove, importsToAdd);
        if(edits == null || edits.size() == 0)
        {
            //no edit required
            return;
        }
        changes.put(uri, edits);
    }
    
    private CompletableFuture<Object> executeAddImportCommand(ExecuteCommandParams params)
    {
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken ->
        {
            cancelToken.checkCanceled();

            compilerWorkspace.startBuilding();
            try
            {
                cancelToken.checkCanceled();
                List<Object> args = params.getArguments();
                String qualifiedName = ((JsonPrimitive) args.get(0)).getAsString();
                String uri = ((JsonPrimitive) args.get(1)).getAsString();
                int line = ((JsonPrimitive) args.get(2)).getAsInt();
                int character = ((JsonPrimitive) args.get(3)).getAsInt();
                if(qualifiedName == null)
                {
                    return new Object();
                }
                Path pathForImport = LanguageServerCompilerUtils.getPathFromLanguageServerURI(uri);
                if(pathForImport == null)
                {
                    return new Object();
                }
                WorkspaceFolderData folderData = workspaceFolderManager.getWorkspaceFolderDataForSourceFile(pathForImport);
                if(folderData == null || folderData.project == null)
                {
                    return new Object();
                }
                String text = workspaceFolderManager.getFileTextForPath(pathForImport);
                if(text == null)
                {
                    return new Object();
                }
                int currentOffset = LanguageServerCompilerUtils.getOffsetFromPosition(new StringReader(text), new Position(line, character));
                ImportRange importRange = null;
                if(uri.endsWith(MXML_EXTENSION))
                {
                    MXMLData mxmlData = workspaceFolderManager.getMXMLDataForPath(pathForImport, folderData);
                    IMXMLTagData offsetTag = MXMLDataUtils.getOffsetMXMLTag(mxmlData, currentOffset);
                    importRange = ImportRange.fromOffsetTag(offsetTag, currentOffset);
                }
                else
                {
                    IASNode offsetNode = workspaceFolderManager.getOffsetNode(pathForImport, currentOffset, folderData);
                    importRange = ImportRange.fromOffsetNode(offsetNode);
                }
                WorkspaceEdit workspaceEdit = CodeActionsUtils.createWorkspaceEditForAddImport(
                    qualifiedName, text, uri, importRange);
                if(workspaceEdit == null)
                {
                    //no edit required
                    return new Object();
                }

                ApplyWorkspaceEditParams editParams = new ApplyWorkspaceEditParams();
                editParams.setEdit(workspaceEdit);

                languageClient.applyEdit(editParams);
                return new Object();
            }
            finally
            {
                compilerWorkspace.doneBuilding();
            }
        });
    }
    
    private CompletableFuture<Object> executeAddMXMLNamespaceCommand(ExecuteCommandParams params)
    {
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken ->
        {
            cancelToken.checkCanceled();

            compilerWorkspace.startBuilding();
            try
            {
                cancelToken.checkCanceled();
                List<Object> args = params.getArguments();
                String nsPrefix = ((JsonPrimitive) args.get(0)).getAsString();
                String nsUri = ((JsonPrimitive) args.get(1)).getAsString();
                String uri = ((JsonPrimitive) args.get(2)).getAsString();
                int startIndex = ((JsonPrimitive) args.get(3)).getAsInt();
                int endIndex = ((JsonPrimitive) args.get(4)).getAsInt();
                if(nsPrefix == null || nsUri == null)
                {
                    return new Object();
                }
                Path pathForImport = LanguageServerCompilerUtils.getPathFromLanguageServerURI(uri);
                if(pathForImport == null)
                {
                    return new Object();
                }
                String text = workspaceFolderManager.getFileTextForPath(pathForImport);
                if(text == null)
                {
                    return new Object();
                }
                WorkspaceEdit workspaceEdit = CodeActionsUtils.createWorkspaceEditForAddMXMLNamespace(nsPrefix, nsUri, text, uri, startIndex, endIndex);
                if(workspaceEdit == null)
                {
                    //no edit required
                    return new Object();
                }

                ApplyWorkspaceEditParams editParams = new ApplyWorkspaceEditParams();
                editParams.setEdit(workspaceEdit);

                languageClient.applyEdit(editParams);
                return new Object();
            }
            finally
            {
                compilerWorkspace.doneBuilding();
            }
        });
    }

    private CompletableFuture<Object> executeQuickCompileCommand(ExecuteCommandParams params)
    {
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken ->
        {
            List<Object> args = params.getArguments();
            String uri = ((JsonPrimitive) args.get(0)).getAsString();
            boolean debug = ((JsonPrimitive) args.get(1)).getAsBoolean();
            boolean success = false;
            try
            {
                if (compilerShell == null)
                {
                    compilerShell = new CompilerShell(languageClient);
                }
                String frameworkLib = System.getProperty(PROPERTY_FRAMEWORK_LIB);
                Path frameworkSDKHome = Paths.get(frameworkLib, "..");
                Path workspaceRootPath = LanguageServerCompilerUtils.getPathFromLanguageServerURI(uri);
                ASConfigCOptions options = new ASConfigCOptions(workspaceRootPath.toString(), frameworkSDKHome.toString(), debug, null, null, true, compilerShell);
                try
                {
                    new ASConfigC(options);
                    success = true;
                }
                catch(ASConfigCException e)
                {
                    //this is a message intended for the user
                    languageClient.logCompilerShellOutput("\n" + e.getMessage());
                    success = false;
                }
            }
            catch(Exception e)
            {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                e.printStackTrace(new PrintStream(buffer));
                languageClient.logCompilerShellOutput("Exception in compiler shell: " + buffer.toString());
            }
            return success;
        });
    }
}