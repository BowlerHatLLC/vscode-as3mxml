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
package com.as3mxml.vscode;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.as3mxml.asconfigc.compiler.ProjectType;
import com.as3mxml.vscode.asdoc.VSCodeASDocDelegate;
import com.as3mxml.vscode.compiler.problems.SyntaxFallbackProblem;
import com.as3mxml.vscode.project.IProjectConfigStrategy;
import com.as3mxml.vscode.project.IProjectConfigStrategyFactory;
import com.as3mxml.vscode.project.ProjectOptions;
import com.as3mxml.vscode.project.SimpleProjectConfigStrategy;
import com.as3mxml.vscode.project.WorkspaceFolderData;
import com.as3mxml.vscode.providers.CodeActionProvider;
import com.as3mxml.vscode.providers.CompletionProvider;
import com.as3mxml.vscode.providers.DefinitionProvider;
import com.as3mxml.vscode.providers.DocumentSymbolProvider;
import com.as3mxml.vscode.providers.ExecuteCommandProvider;
import com.as3mxml.vscode.providers.HoverProvider;
import com.as3mxml.vscode.providers.ImplementationProvider;
import com.as3mxml.vscode.providers.ReferencesProvider;
import com.as3mxml.vscode.providers.RenameProvider;
import com.as3mxml.vscode.providers.SignatureHelpProvider;
import com.as3mxml.vscode.providers.TypeDefinitionProvider;
import com.as3mxml.vscode.providers.WorkspaceSymbolProvider;
import com.as3mxml.vscode.services.ActionScriptLanguageClient;
import com.as3mxml.vscode.utils.ActionScriptSDKUtils;
import com.as3mxml.vscode.utils.CompilationUnitUtils;
import com.as3mxml.vscode.utils.CompilationUnitUtils.IncludeFileData;
import com.as3mxml.vscode.utils.CompilerProblemFilter;
import com.as3mxml.vscode.utils.CompilerProjectUtils;
import com.as3mxml.vscode.utils.FileTracker;
import com.as3mxml.vscode.utils.LSPUtils;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;
import com.as3mxml.vscode.utils.ProblemTracker;
import com.as3mxml.vscode.utils.SourcePathUtils;
import com.as3mxml.vscode.utils.WaitForBuildFinishRunner;
import com.as3mxml.vscode.utils.WorkspaceFolderManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.royale.compiler.clients.problems.ProblemQuery;
import org.apache.royale.compiler.config.CommandLineConfigurator;
import org.apache.royale.compiler.config.Configuration;
import org.apache.royale.compiler.config.ICompilerProblemSettings;
import org.apache.royale.compiler.filespecs.IFileSpecification;
import org.apache.royale.compiler.internal.parsing.as.ASParser;
import org.apache.royale.compiler.internal.parsing.as.ASToken;
import org.apache.royale.compiler.internal.parsing.as.RepairingTokenBuffer;
import org.apache.royale.compiler.internal.parsing.as.StreamingASTokenizer;
import org.apache.royale.compiler.internal.projects.CompilerProject;
import org.apache.royale.compiler.internal.projects.RoyaleProject;
import org.apache.royale.compiler.internal.projects.RoyaleProjectConfigurator;
import org.apache.royale.compiler.internal.tree.as.FileNode;
import org.apache.royale.compiler.internal.units.ResourceBundleCompilationUnit;
import org.apache.royale.compiler.internal.units.SWCCompilationUnit;
import org.apache.royale.compiler.internal.workspaces.Workspace;
import org.apache.royale.compiler.problems.ICompilerProblem;
import org.apache.royale.compiler.problems.InternalCompilerProblem;
import org.apache.royale.compiler.targets.ITarget;
import org.apache.royale.compiler.targets.ITargetSettings;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.apache.royale.compiler.workspaces.IWorkspace;
import org.apache.royale.utils.FilenameNormalization;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionCapabilities;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemCapabilities;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * Handles requests from Visual Studio Code that are at the document level,
 * including things like API completion, function signature help, and find all
 * references. Calls APIs on the Apache Royale compiler to get data for the
 * responses to return to VSCode.
 */
public class ActionScriptServices implements TextDocumentService, WorkspaceService
{
    private static final String MXML_EXTENSION = ".mxml";
    private static final String AS_EXTENSION = ".as";
    private static final String SWC_EXTENSION = ".swc";
    private static final String PROPERTY_FRAMEWORK_LIB = "royalelib";
    private static final String ROYALE_ASJS_RELATIVE_PATH_CHILD = "./royale-asjs";
    private static final String FRAMEWORKS_RELATIVE_PATH_CHILD = "./frameworks";

    private ActionScriptLanguageClient languageClient;
    private IProjectConfigStrategyFactory projectConfigStrategyFactory;
    private String oldFrameworkSDKPath;
    private Workspace compilerWorkspace;
    private WorkspaceFolderManager workspaceFolderManager;
    private WatchService sourcePathWatcher;
    private Thread sourcePathWatcherThread;
    private ClientCapabilities clientCapabilities;
    private boolean completionSupportsSnippets = false;
    private FileTracker fileTracker;
    private CompilerProblemFilter compilerProblemFilter = new CompilerProblemFilter();
    private boolean initialized = false;
    private boolean frameworkSDKIsRoyale = false;
    private WaitForBuildFinishRunner waitForBuildFinishRunner;
    private Set<URI> notOnSourcePathSet = new HashSet<>();
    private boolean realTimeProblems = true;
    private SimpleProjectConfigStrategy fallbackConfig;

    public ActionScriptServices()
    {
        compilerWorkspace = new Workspace();
        compilerWorkspace.setASDocDelegate(new VSCodeASDocDelegate());
        fileTracker = new FileTracker(compilerWorkspace);
        workspaceFolderManager = new WorkspaceFolderManager(fileTracker);
        updateFrameworkSDK();
    }

    public IProjectConfigStrategyFactory getProjectConfigStrategyFactory()
    {
        return projectConfigStrategyFactory;
    }

    public void setProjectConfigStrategyFactory(IProjectConfigStrategyFactory value)
    {
        projectConfigStrategyFactory = value;
    }

    public List<WorkspaceFolder> getWorkspaceFolders()
    {
        return workspaceFolderManager.getWorkspaceFolders();
    }

    public WorkspaceFolderData getWorkspaceFolderData(WorkspaceFolder folder)
    {
        return workspaceFolderManager.getWorkspaceFolderData(folder);
    }

    public void addWorkspaceFolder(WorkspaceFolder folder)
    {
        IProjectConfigStrategy config = projectConfigStrategyFactory.create(folder);
        WorkspaceFolderData folderData = workspaceFolderManager.addWorkspaceFolder(folder, config);
        folderData.codeProblemTracker.setLanguageClient(languageClient);
        folderData.configProblemTracker.setLanguageClient(languageClient);
        
        //let's get the code intelligence up and running!
        Path path = getMainCompilationUnitPath(folderData);
        if (path != null)
        {
            String normalizedPath = FilenameNormalization.normalize(path.toAbsolutePath().toString());
            IFileSpecification fileSpec = fileTracker.getFileSpecification(normalizedPath);
            compilerWorkspace.fileChanged(fileSpec);
        }

        checkProjectForProblems(folderData);
    }

    public void removeWorkspaceFolder(WorkspaceFolder folder)
    {
        workspaceFolderManager.removeWorkspaceFolder(folder);
    }

    public ClientCapabilities getClientCapabilities()
    {
        return clientCapabilities;
    }

    public void setClientCapabilities(ClientCapabilities value)
    {
        completionSupportsSnippets = false;

        clientCapabilities = value;
        TextDocumentClientCapabilities textDocument = clientCapabilities.getTextDocument();
        if(textDocument != null)
        {
            CompletionCapabilities completion = textDocument.getCompletion();
            if(completion != null)
            {
                CompletionItemCapabilities completionItem = completion.getCompletionItem();
                if(completionItem != null)
                {
                    completionSupportsSnippets = completionItem.getSnippetSupport();
                }
            }
        }
    }

    public void setLanguageClient(ActionScriptLanguageClient value)
    {
        languageClient = value;
    }

    /**
     * Returns a list of all items to display in the completion list at a
     * specific position in a document. Called automatically by VSCode as the
     * user types, and may not necessarily be triggered only on "." or ":".
     */
    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params)
    {
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken ->
        {
            cancelToken.checkCanceled();

            compilerWorkspace.startBuilding();
            try
            {
                CompletionProvider provider = new CompletionProvider(workspaceFolderManager,
                        fileTracker, completionSupportsSnippets, frameworkSDKIsRoyale);
                return provider.completion(params, cancelToken);
            }
            finally
            {
                compilerWorkspace.doneBuilding();
            }
        });
    }

    /**
     * This function is never called. We resolve completion items immediately
     * in completion() instead of requiring a separate step.
     */
    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved)
    {
        return CompletableFuture.completedFuture(new CompletionItem());
    }

    /**
     * Returns information to display in a tooltip when the mouse hovers over
     * something in a text document.
     */
    @Override
    public CompletableFuture<Hover> hover(TextDocumentPositionParams params)
    {
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken ->
        {
            cancelToken.checkCanceled();

            compilerWorkspace.startBuilding();
            try
            {
                HoverProvider provider = new HoverProvider(workspaceFolderManager, fileTracker);
                return provider.hover(params, cancelToken);
            }
            finally
            {
                compilerWorkspace.doneBuilding();
            }
        });
    }

    /**
     * Displays a function's parameters, including which one is currently
     * active. Called automatically by VSCode any time that the user types "(",
     * so be sure to check that a function call is actually happening at the
     * current position.
     */
    @Override
    public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams params)
    {
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken ->
        {
            cancelToken.checkCanceled();

            compilerWorkspace.startBuilding();
            try
            {
                SignatureHelpProvider provider = new SignatureHelpProvider(workspaceFolderManager, fileTracker);
                return provider.signatureHelp(params, cancelToken);
            }
            finally
            {
                compilerWorkspace.doneBuilding();
            }
        });
    }

    /**
     * Finds where the definition referenced at the current position in a text
     * document is defined.
     */
    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(TextDocumentPositionParams params)
    {
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken ->
        {
            cancelToken.checkCanceled();

            compilerWorkspace.startBuilding();
            try
            {
                DefinitionProvider provider = new DefinitionProvider(workspaceFolderManager, fileTracker);
                return provider.definition(params, cancelToken);
            }
            finally
            {
                compilerWorkspace.doneBuilding();
            }
        });
    }

    /**
     * Finds where the type of the definition referenced at the current position
     * in a text document is defined.
     */
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> typeDefinition(TextDocumentPositionParams params)
    {
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken ->
        {
            cancelToken.checkCanceled();

            compilerWorkspace.startBuilding();
            try
            {
                TypeDefinitionProvider provider = new TypeDefinitionProvider(workspaceFolderManager, fileTracker);
                return provider.typeDefinition(params, cancelToken);
            }
            finally
            {
                compilerWorkspace.doneBuilding();
            }
        });
    }

    /**
     * Finds all implemenations of an interface.
     */
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(TextDocumentPositionParams params)
    {
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken ->
        {
            cancelToken.checkCanceled();

            compilerWorkspace.startBuilding();
            try
            {
                ImplementationProvider provider = new ImplementationProvider(workspaceFolderManager, fileTracker);
                return provider.implementation(params, cancelToken);
            }
            finally
            {
                compilerWorkspace.doneBuilding();
            }
        });
    }

    /**
     * Finds all references of the definition referenced at the current position
     * in a text document. Does not necessarily get called where a definition is
     * defined, but may be at one of the references.
     */
    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params)
    {
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken ->
        {
            cancelToken.checkCanceled();

            compilerWorkspace.startBuilding();
            try
            {
                ReferencesProvider provider = new ReferencesProvider(workspaceFolderManager, fileTracker);
                return provider.references(params, cancelToken);
            }
            finally
            {
                compilerWorkspace.doneBuilding();
            }
        });
    }

    /**
     * This feature is not implemented at this time.
     */
    @Override
    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams params)
    {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    /**
     * Searches by name for a symbol in the workspace.
     */
    public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params)
    {
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken ->
        {
            cancelToken.checkCanceled();

            compilerWorkspace.startBuilding();
            try
            {
                WorkspaceSymbolProvider provider = new WorkspaceSymbolProvider(workspaceFolderManager);
                return provider.workspaceSymbol(params, cancelToken);
            }
            finally
            {
                compilerWorkspace.doneBuilding();
            }
        });
    }

    /**
     * Searches by name for a symbol in a specific document (not the whole
     * workspace)
     */
    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params)
    {
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken ->
        {
            cancelToken.checkCanceled();
            
            compilerWorkspace.startBuilding();
            try
            {
                boolean hierarchicalDocumentSymbolSupport = clientCapabilities.getTextDocument().getDocumentSymbol().getHierarchicalDocumentSymbolSupport();
                DocumentSymbolProvider provider = new DocumentSymbolProvider(workspaceFolderManager, hierarchicalDocumentSymbolSupport);
                return provider.documentSymbol(params, cancelToken);
            }
            finally
            {
                compilerWorkspace.doneBuilding();
            }
        });
    }

    /**
     * Can be used to "quick fix" an error or warning.
     */
    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params)
    {
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken ->
        {
            cancelToken.checkCanceled();

            compilerWorkspace.startBuilding();
            try
            {
                CodeActionProvider provider = new CodeActionProvider(workspaceFolderManager, fileTracker);
                return provider.codeAction(params, cancelToken);
            }
            finally
            {
                compilerWorkspace.doneBuilding();
            }
        });
    }

    /**
     * This feature is not implemented at this time.
     */
    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params)
    {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    /**
     * This feature is not implemented at this time.
     */
    @Override
    public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved)
    {
        return CompletableFuture.completedFuture(new CodeLens());
    }

    /**
     * This feature is not implemented at this time.
     */
    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params)
    {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    /**
     * This feature is not implemented at this time.
     */
    @Override
    public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params)
    {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    /**
     * This feature is not implemented at this time.
     */
    @Override
    public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params)
    {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    /**
     * Renames a symbol at the specified document position.
     */
    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params)
    {
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken ->
        {
            cancelToken.checkCanceled();

            compilerWorkspace.startBuilding();
            try
            {
                RenameProvider provider = new RenameProvider(workspaceFolderManager, fileTracker);
                WorkspaceEdit result = provider.rename(params, cancelToken);
                if(result == null)
                {
                    if (languageClient != null)
                    {
                        MessageParams message = new MessageParams();
                        message.setType(MessageType.Info);
                        message.setMessage("You cannot rename this element.");
                        languageClient.showMessage(message);
                    }
                    return new WorkspaceEdit(new HashMap<>());
                }
                return result;
            }
            finally
            {
                compilerWorkspace.doneBuilding();
            }
        });
    }

    /**
     * Called when one of the commands registered in ActionScriptLanguageServer
     * is executed.
     */
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params)
    {
        ExecuteCommandProvider provider = new ExecuteCommandProvider(workspaceFolderManager,
                fileTracker, compilerWorkspace, languageClient);
        return provider.executeCommand(params);
    }

    /**
     * Called whan a file is opened for editing in Visual Studio Code. We store
     * the file's contents in a String since any changes that have been made to
     * it may not have been saved yet. This method will not be called again if
     * the user simply switches to a different tab for another file and then
     * switched back to this one, without every closing it completely. In
     * other words, the language server does not usually know which file is
     * currently visible to the user in VSCode.
     */
    @Override
    public void didOpen(DidOpenTextDocumentParams params)
    {
        TextDocumentItem textDocument = params.getTextDocument();
        String textDocumentUri = textDocument.getUri();
        if (!textDocumentUri.endsWith(AS_EXTENSION)
                && !textDocumentUri.endsWith(MXML_EXTENSION))
        {
            return;
        }
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocumentUri);
        if (path == null)
        {
            return;
        }

        //even if it's not in a workspace folder right now, store it just in
        //case we need it later. example: added to source-path compiler option.
        String text = textDocument.getText();
        fileTracker.openFile(path, text);

        WorkspaceFolderData folderData = workspaceFolderManager.getWorkspaceFolderDataForSourceFile(path);
        if (folderData == null)
        {
            //this file isn't in any of the workspace folders
            publishDiagnosticForFileOutsideSourcePath(path);
            return;
        }

        if (fallbackConfig != null && folderData.equals(workspaceFolderManager.getFallbackFolderData()))
        {
            fallbackConfig.didOpen(path);
        }

        getProject(folderData);
        RoyaleProject project = folderData.project;
        if (project == null)
        {
            //something went wrong while creating the project
            return;
        }

        //notify the workspace that it should read the file from memory
        //instead of loading from the file system
        String normalizedPath = FilenameNormalization.normalize(path.toAbsolutePath().toString());
        IFileSpecification fileSpec = fileTracker.getFileSpecification(normalizedPath);
        compilerWorkspace.fileChanged(fileSpec);

        //if it's an included file, switch to the parent file
        IncludeFileData includeFileData = folderData.includedFiles.get(path.toString());
        if (includeFileData != null)
        {
            path = Paths.get(includeFileData.parentPath);
        }

        //we need to check for problems when opening a new file because it
        //may not have been in the workspace before.
        checkFilePathForProblems(path, folderData, true);
    }

    /**
     * Called when a change is made to a file open for editing in Visual Studio
     * Code. Receives incremental changes that need to be applied to the
     * in-memory String that we store for this file.
     */
    @Override
    public void didChange(DidChangeTextDocumentParams params)
    {
        VersionedTextDocumentIdentifier textDocument = params.getTextDocument();
        String textDocumentUri = textDocument.getUri();
        if (!textDocumentUri.endsWith(AS_EXTENSION)
                && !textDocumentUri.endsWith(MXML_EXTENSION))
        {
            return;
        }
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocumentUri);
        if (path == null)
        {
            return;
        }
        fileTracker.changeFile(path, params.getContentChanges());

        WorkspaceFolderData folderData = workspaceFolderManager.getWorkspaceFolderDataForSourceFile(path);
        if (folderData == null)
        {
            //this file isn't in any of the workspace folders
            publishDiagnosticForFileOutsideSourcePath(path);
            return;
        }

        getProject(folderData);
        RoyaleProject project = folderData.project;
        if (project == null)
        {
            //something went wrong while creating the project
            return;
        }

        String normalizedChangedPathAsString = path.toString();

        ICompilationUnit unit = null;
        compilerWorkspace.startBuilding();
        try
        {
            unit = CompilerProjectUtils.findCompilationUnit(path, project);
            if(unit != null)
            {
                //windows drive letter may not match, even after normalization,
                //so it's better to use the unit's path, if available.
                normalizedChangedPathAsString = unit.getAbsoluteFilename();
            }
        }
        finally
        {
            compilerWorkspace.doneBuilding();
        }

        IFileSpecification fileSpec = fileTracker.getFileSpecification(normalizedChangedPathAsString);
        compilerWorkspace.fileChanged(fileSpec);

        compilerWorkspace.startBuilding();
        try
        {
            //if it's an included file, switch to the parent file
            IncludeFileData includeFileData = folderData.includedFiles.get(path.toString());
            if (includeFileData != null)
            {
                path = Paths.get(includeFileData.parentPath);
                unit = CompilerProjectUtils.findCompilationUnit(path, project);
            }
        }
        finally
        {
            compilerWorkspace.doneBuilding();
        }

        if(folderData.equals(workspaceFolderManager.getFallbackFolderData()))
        {
            //don't check for errors with the fallback folder data
            return;
        }

        if(unit == null)
        {
            //this file doesn't have a compilation unit yet, so we'll fall back
            //to simple syntax checking for now
            checkFilePathForProblems(path, folderData, true);
        }
        else if(realTimeProblems)
        {
            //try to keep using the existing instance, if possible
            if(waitForBuildFinishRunner == null
                    || !waitForBuildFinishRunner.isRunning()
                    || !unit.equals(waitForBuildFinishRunner.getCompilationUnit()))
            {
                waitForBuildFinishRunner = new WaitForBuildFinishRunner(unit, folderData, languageClient, compilerProblemFilter);
                compilerWorkspace.getExecutorService().submit(waitForBuildFinishRunner);
            }
            else
            {
                waitForBuildFinishRunner.setChanged();
            }
        }
        else if(waitForBuildFinishRunner != null)
        {
            waitForBuildFinishRunner.setCancelled();
            waitForBuildFinishRunner = null;
        }
    }

    /**
     * Called when a file is closed in Visual Studio Code. We should no longer
     * store the file as a String, and we can load the contents from the file
     * system.
     */
    @Override
    public void didClose(DidCloseTextDocumentParams params)
    {
        TextDocumentIdentifier textDocument = params.getTextDocument();
        String textDocumentUri = textDocument.getUri();
        if (!textDocumentUri.endsWith(AS_EXTENSION)
                && !textDocumentUri.endsWith(MXML_EXTENSION))
        {
            return;
        }
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocumentUri);
        if (path == null)
        {
            return;
        }

        fileTracker.closeFile(path);

        boolean clearProblems = false;

        WorkspaceFolderData folderData = workspaceFolderManager.getWorkspaceFolderDataForSourceFile(path);
        if (folderData == null)
        {
            //if we can't figure out which workspace the file is in, then clear
            //the problems completely because we want to display problems only
            //while it is open
            clearProblems = true;
        }
        else
        {
            if (fallbackConfig != null && folderData.equals(workspaceFolderManager.getFallbackFolderData()))
            {
                fallbackConfig.didClose(path);
            }

            getProject(folderData);
            RoyaleProject project = folderData.project;
            URI uri = path.toUri();
            if(project == null)
            {
                //if the current project isn't properly configured, we want to
                //display problems only while a file is open
                clearProblems = true;
            }
            else if(notOnSourcePathSet.contains(uri))
            {
                //if the file is outside of the project's source path, we want
                //to display problems only while it is open
                clearProblems = true;
                notOnSourcePathSet.remove(uri);
            }

            //if it's an included file, switch to the parent file
            IncludeFileData includeFileData = folderData.includedFiles.get(path.toString());
            if (includeFileData != null)
            {
                path = Paths.get(includeFileData.parentPath);
            }
        }

        if (clearProblems)
        {
            //immediately clear any diagnostics published for this file
            URI uri = path.toUri();
            PublishDiagnosticsParams publish = new PublishDiagnosticsParams();
            ArrayList<Diagnostic> diagnostics = new ArrayList<>();
            publish.setDiagnostics(diagnostics);
            publish.setUri(uri.toString());
            if (languageClient != null)
            {
                languageClient.publishDiagnostics(publish);
            }
            return;
        }

        //the contents of the file may have been reverted without saving
        //changes, so check for errors with the file system version
        checkFilePathForProblems(path, folderData, true);
    }

    /**
     * Called when a file being edited is saved.
     */
    @Override
    public void didSave(DidSaveTextDocumentParams params)
    {
        if(realTimeProblems)
        {
            //as long as we're checking on change, we shouldn't need to do anything
            //on save
            return;
        }

        TextDocumentIdentifier textDocument = params.getTextDocument();
        String textDocumentUri = textDocument.getUri();
        if (!textDocumentUri.endsWith(AS_EXTENSION)
                && !textDocumentUri.endsWith(MXML_EXTENSION))
        {
            return;
        }
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocumentUri);
        if (path == null)
        {
            return;
        }
        WorkspaceFolderData folderData = workspaceFolderManager.getWorkspaceFolderDataForSourceFile(path);
        if (folderData == null)
        {
            //this file isn't in any of the workspace folders
            publishDiagnosticForFileOutsideSourcePath(path);
            return;
        }
        getProject(folderData);
        RoyaleProject project = folderData.project;
        if (project == null)
        {
            //something went wrong while creating the project
            return;
        }

        //if it's an included file, switch to the parent file
        IncludeFileData includeFileData = folderData.includedFiles.get(path.toString());
        if (includeFileData != null)
        {
            path = Paths.get(includeFileData.parentPath);
        }
        
        checkFilePathForProblems(path, folderData, true);
    }

    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params)
    {
        didChangeWatchedFiles(params, true);
    }

    /**
     * Called when certain files in the workspace are added, removed, or
     * changed, even if they are not considered open for editing. Also checks if
     * the project configuration strategy has changed. If it has, checks for
     * errors on the whole project.
     */
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params, boolean checkForProblems)
    {
        Set<WorkspaceFolderData> foldersToCheck = new HashSet<>();

        for (FileEvent event : params.getChanges())
        {
            Path changedPath = LanguageServerCompilerUtils.getPathFromLanguageServerURI(event.getUri());
            if (changedPath == null)
            {
                continue;
            }

            //first check if any project's config file has changed
            for (WorkspaceFolder folder : workspaceFolderManager.getWorkspaceFolders())
            {
                WorkspaceFolderData folderData = workspaceFolderManager.getWorkspaceFolderData(folder);
                IProjectConfigStrategy config = folderData.config;
                if(changedPath.equals(config.getConfigFilePath()))
                {
                    config.forceChanged();
                    foldersToCheck.add(folderData);
                }
            }

            //then, check if source or library files have changed
            FileChangeType changeType = event.getType();
            String normalizedChangedPathAsString = FilenameNormalization.normalize(changedPath.toString());
            if (normalizedChangedPathAsString.endsWith(SWC_EXTENSION))
            {
                Path normalizedChangedPath = Paths.get(normalizedChangedPathAsString);
                List<WorkspaceFolderData> allFolderData = workspaceFolderManager.getAllWorkspaceFolderDataForSWCFile(normalizedChangedPath);
                if (allFolderData.size() > 0)
                {
                    compilerWorkspace.startBuilding();
                    ICompilationUnit changedUnit = null;
                    try
                    {
                        changedUnit = workspaceFolderManager.findCompilationUnit(normalizedChangedPath);
                    }
                    finally
                    {
                        compilerWorkspace.doneBuilding();
                    }
                    if (changedUnit != null)
                    {
                        //windows drive letter may not match, even after normalization,
                        //so it's better to use the unit's path, if available.
                        normalizedChangedPathAsString = changedUnit.getAbsoluteFilename();
                    }

                    boolean swcConfigChanged = false;
                    IFileSpecification swcFileSpec = fileTracker.getFileSpecification(normalizedChangedPathAsString);
                    if (changeType.equals(FileChangeType.Deleted))
                    {
                        swcConfigChanged = true;
                        compilerWorkspace.fileRemoved(swcFileSpec);
                    }
                    else if (changeType.equals(FileChangeType.Created))
                    {
                        swcConfigChanged = true;
                        compilerWorkspace.fileAdded(swcFileSpec);
                    }
                    else if (changeType.equals(FileChangeType.Changed))
                    {
                        compilerWorkspace.fileChanged(swcFileSpec);
                    }
                    if(swcConfigChanged)
                    {
                        //for some reason, simply calling fileAdded() or
                        //fileRemoved() is not enough for SWC files.
                        //changing the project configuration will force the
                        //change to be detected, so let's do that manually.
                        for (WorkspaceFolderData folderData : allFolderData)
                        {
                            folderData.config.forceChanged();
                        }
                    }
                    foldersToCheck.addAll(allFolderData);
                }
            }
            else if (normalizedChangedPathAsString.endsWith(AS_EXTENSION) || normalizedChangedPathAsString.endsWith(MXML_EXTENSION))
            {
                Path normalizedChangedPath = Paths.get(normalizedChangedPathAsString);
                compilerWorkspace.startBuilding();
                ICompilationUnit changedUnit = null;
                try
                {
                    changedUnit = workspaceFolderManager.findCompilationUnit(normalizedChangedPath);
                }
                finally
                {
                    compilerWorkspace.doneBuilding();
                }

                if (changedUnit != null)
                {
                    //windows drive letter may not match, even after normalization,
                    //so it's better to use the unit's path, if available.
                    normalizedChangedPathAsString = changedUnit.getAbsoluteFilename();
                }
                List<WorkspaceFolderData> allFolderData = workspaceFolderManager.getAllWorkspaceFolderDataForSourceFile(changedPath);
                if (changeType.equals(FileChangeType.Deleted) ||

                    //this is weird, but it's possible for a renamed file to
                    //result in a Changed event, but not a Deleted event
                    (changeType.equals(FileChangeType.Changed) && !java.nio.file.Files.exists(changedPath))
                )
                {
                    IFileSpecification fileSpec = fileTracker.getFileSpecification(normalizedChangedPathAsString);
                    compilerWorkspace.fileRemoved(fileSpec);
                    //deleting a file may change errors in other existing files,
                    //so we need to do a full check
                    foldersToCheck.addAll(allFolderData);
                }
                else if (event.getType().equals(FileChangeType.Created))
                {
                    IFileSpecification fileSpec = fileTracker.getFileSpecification(normalizedChangedPathAsString);
                    compilerWorkspace.fileAdded(fileSpec);
                    //creating a file may change errors in other existing files,
                    //so we need to do a full check
                    foldersToCheck.addAll(allFolderData);
                }
                else if (changeType.equals(FileChangeType.Changed))
                {
                    IFileSpecification fileSpec = fileTracker.getFileSpecification(normalizedChangedPathAsString);
                    compilerWorkspace.fileChanged(fileSpec);
                    foldersToCheck.addAll(allFolderData);
                }
            }
            else if (changeType.equals(FileChangeType.Created) && java.nio.file.Files.isDirectory(changedPath))
            {
                try
                {
                    java.nio.file.Files.walkFileTree(changedPath, new SimpleFileVisitor<Path>()
                    {
                        @Override
                        public FileVisitResult visitFile(Path subPath, BasicFileAttributes attrs)
                        {
                            String normalizedSubPath = FilenameNormalization.normalize(subPath.toString());
                            if (normalizedSubPath.endsWith(AS_EXTENSION) || normalizedSubPath.endsWith(MXML_EXTENSION))
                            {
                                IFileSpecification fileSpec = fileTracker.getFileSpecification(normalizedSubPath);
                                compilerWorkspace.fileAdded(fileSpec);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
                catch (IOException e)
                {
                    System.err.println("Failed to walk added path: " + changedPath.toString());
                    e.printStackTrace(System.err);
                }
            }
            else if (changeType.equals(FileChangeType.Deleted))
            {
                //we don't get separate didChangeWatchedFiles notifications for
                //each .as and .mxml in a directory when the directory is
                //deleted. with that in mind, we need to manually check if any
                //compilation units were in the directory that was deleted.
                String deletedFilePath = normalizedChangedPathAsString + File.separator;
                Set<String> filesToRemove = new HashSet<>();
                
                for (WorkspaceFolder folder : workspaceFolderManager.getWorkspaceFolders())
                {
                    WorkspaceFolderData folderData = workspaceFolderManager.getWorkspaceFolderData(folder);
                    RoyaleProject project = folderData.project;
                    if (project == null)
                    {
                        continue;
                    }
                    compilerWorkspace.startBuilding();
                    try
                    {
                        for (ICompilationUnit unit : project.getCompilationUnits())
                        {
                            String unitFileName = unit.getAbsoluteFilename();
                            if (unitFileName.startsWith(deletedFilePath)
                                    && (unitFileName.endsWith(AS_EXTENSION)
                                            || unitFileName.endsWith(MXML_EXTENSION)
                                            || unitFileName.endsWith(SWC_EXTENSION)))
                            {
                                //if we call fileRemoved() here, it will change the
                                //compilationUnits collection and throw an exception
                                //so just save the paths to be removed after this loop.
                                filesToRemove.add(unitFileName);

                                //deleting a file may change errors in other existing files,
                                //so we need to do a full check
                                foldersToCheck.add(folderData);

                                if (unitFileName.endsWith(SWC_EXTENSION))
                                {
                                    folderData.config.forceChanged();
                                }
                            }
                        }
                    }
                    finally
                    {
                        compilerWorkspace.doneBuilding();
                    }
                }
                for (String fileToRemove : filesToRemove)
                {
                    Path pathToRemove = Paths.get(fileToRemove);
                    compilerWorkspace.startBuilding();
                    ICompilationUnit unit = null;
                    try
                    {
                        unit = workspaceFolderManager.findCompilationUnit(pathToRemove);
                    }
                    finally
                    {
                        compilerWorkspace.doneBuilding();
                    }
                    if (unit != null)
                    {
                        fileToRemove = unit.getAbsoluteFilename();
                    }
                    IFileSpecification fileSpec = fileTracker.getFileSpecification(fileToRemove);
                    compilerWorkspace.fileRemoved(fileSpec);
                }
            }
        }
        if (checkForProblems)
        {
            for (WorkspaceFolderData folderData : foldersToCheck)
            {
                checkProjectForProblems(folderData);
            }
        }
    }

	@Override
	public void didChangeConfiguration(DidChangeConfigurationParams params)
	{
		if(!(params.getSettings() instanceof JsonObject))
		{
			return;
		}
		JsonObject settings = (JsonObject) params.getSettings();
		this.updateSDK(settings);
		this.updateRealTimeProblems(settings);
	}

	@Override
	public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params)
	{
		for(WorkspaceFolder folder : params.getEvent().getRemoved())
		{
			removeWorkspaceFolder(folder);
		}
		for(WorkspaceFolder folder : params.getEvent().getAdded())
		{
			addWorkspaceFolder(folder);
		}
	}
     
	@JsonNotification("$/setTraceNotification")
	public void setTraceNotification(Object params)
	{
		//this may be ignored. see: eclipse/lsp4j#22
	}

    public void setInitialized()
    {
        if(initialized)
        {
            return;
        }
        initialized = true;
        //this is the first time that we can notify the client about any
        //diagnostics
        for (WorkspaceFolder folder : workspaceFolderManager.getWorkspaceFolders())
        {
            WorkspaceFolderData folderData = workspaceFolderManager.getWorkspaceFolderData(folder);
            checkProjectForProblems(folderData);
        }
    }

    /**
     * Called if something in the configuration has changed.
     */
    public void checkForProblemsNow()
    {
        updateFrameworkSDK();
        for (WorkspaceFolder folder : workspaceFolderManager.getWorkspaceFolders())
        {
            WorkspaceFolderData folderData = workspaceFolderManager.getWorkspaceFolderData(folder);
            IProjectConfigStrategy config = folderData.config;
            config.forceChanged();
            checkProjectForProblems(folderData);
        }
    }

    private void updateFrameworkSDK()
    {
        String frameworkSDKPath = System.getProperty(PROPERTY_FRAMEWORK_LIB);
        if(frameworkSDKPath.equals(oldFrameworkSDKPath))
        {
            return;
        }

        oldFrameworkSDKPath = frameworkSDKPath;

        //if the framework SDK doesn't include the Falcon compiler, we can
        //ignore certain errors from the editor SDK, which includes Falcon.
        Path frameworkPath = Paths.get(frameworkSDKPath);
        Path compilerPath = frameworkPath.resolve("../lib/falcon-mxmlc.jar");
        compilerProblemFilter.royaleProblems = compilerPath.toFile().exists();

        frameworkSDKIsRoyale = ActionScriptSDKUtils.isRoyaleFramework(frameworkPath);

        updateFrameworkWorkspaceFolder();
    }

    private void updateFrameworkWorkspaceFolder()
    {
        if(oldFrameworkSDKPath == null)
        {
            return;
        }
        WorkspaceFolder folder = new WorkspaceFolder(Paths.get(oldFrameworkSDKPath).toUri().toString());
        fallbackConfig = new SimpleProjectConfigStrategy(folder);
        workspaceFolderManager.setFallbackFolderData(folder, fallbackConfig);
    }

    private void watchNewSourceOrLibraryPath(Path sourceOrLibraryPath, WorkspaceFolderData folderData)
    {
        try
        {
            java.nio.file.Files.walkFileTree(sourceOrLibraryPath, new SimpleFileVisitor<Path>()
            {
                @Override
                public FileVisitResult preVisitDirectory(Path subPath, BasicFileAttributes attrs) throws IOException
                {
                    WatchKey watchKey = subPath.register(sourcePathWatcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
                    folderData.sourceOrLibraryPathWatchKeys.put(watchKey, subPath);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException e)
        {
            System.err.println("Failed to watch source or library path: " + sourceOrLibraryPath.toString());
            e.printStackTrace(System.err);
        }
    }

    private void prepareNewProject(WorkspaceFolderData folderData)
    {
        RoyaleProject project = folderData.project;
        if (project == null)
        {
            return;
        }
        if (sourcePathWatcher == null)
        {
            createSourcePathWatcher();
        }
        String workspaceFolderUri = folderData.folder.getUri();
        Path workspaceFolderPath = LanguageServerCompilerUtils.getPathFromLanguageServerURI(workspaceFolderUri);
        if (workspaceFolderPath == null)
        {
            return;
        }
        boolean dynamicDidChangeWatchedFiles = clientCapabilities.getWorkspace().getDidChangeWatchedFiles().getDynamicRegistration();
        for (File sourcePathFile : project.getSourcePath())
        {
            Path sourcePath = sourcePathFile.toPath();
            try
            {
                sourcePath = sourcePath.toRealPath();
            }
            catch (IOException e)
            {
            }
            boolean shouldWatch = true;
            if (dynamicDidChangeWatchedFiles)
            {
                //we need to check if the source path is inside any of the
                //workspace folders. not just the current one.
                for (WorkspaceFolder workspaceFolder : workspaceFolderManager.getWorkspaceFolders())
                {
                    String uri = workspaceFolder.getUri();
                    Path folderPath = LanguageServerCompilerUtils.getPathFromLanguageServerURI(uri);
                    if (sourcePath.startsWith(folderPath))
                    {
                        //if we're already watching for changes in the
                        //workspace, and we need to avoid so that the compiler
                        //doesn't get confused by duplicates that might have
                        //slightly different capitalization because the language
                        //server protocol and Java file watchers don't
                        //necessarily match
                        shouldWatch = false;
                        break;
                    }
                }
            }
            if (shouldWatch)
            {
                watchNewSourceOrLibraryPath(sourcePath, folderData);
            }
        }
        for (String libraryPathString : project.getCompilerLibraryPath(folderData.configurator.getConfiguration()))
        {
            Path libraryPath = Paths.get(libraryPathString);
            try
            {
                libraryPath = libraryPath.toRealPath();
            }
            catch (IOException e)
            {
            }
            boolean shouldWatch = true;
            if (dynamicDidChangeWatchedFiles)
            {
                //we need to check if the source path is inside any of the
                //workspace folders. not just the current one.
                for (WorkspaceFolder workspaceFolder : workspaceFolderManager.getWorkspaceFolders())
                {
                    String uri = workspaceFolder.getUri();
                    Path folderPath = LanguageServerCompilerUtils.getPathFromLanguageServerURI(uri);
                    if (libraryPath.startsWith(folderPath))
                    {
                        //if we're already watching for changes in the
                        //workspace, and we need to avoid so that the compiler
                        //doesn't get confused by duplicates that might have
                        //slightly different capitalization because the language
                        //server protocol and Java file watchers don't
                        //necessarily match
                        shouldWatch = false;
                        break;
                    }
                }
            }
            if (shouldWatch)
            {
                watchNewSourceOrLibraryPath(libraryPath, folderData);
            }
        }
        for (String externalLibraryPathString : project.getCompilerExternalLibraryPath(folderData.configurator.getConfiguration()))
        {
            Path externalLibraryPath = Paths.get(externalLibraryPathString);
            try
            {
                externalLibraryPath = externalLibraryPath.toRealPath();
            }
            catch (IOException e)
            {
            }
            boolean shouldWatch = true;
            if (dynamicDidChangeWatchedFiles)
            {
                //we need to check if the source path is inside any of the
                //workspace folders. not just the current one.
                for (WorkspaceFolder workspaceFolder : workspaceFolderManager.getWorkspaceFolders())
                {
                    String uri = workspaceFolder.getUri();
                    Path folderPath = LanguageServerCompilerUtils.getPathFromLanguageServerURI(uri);
                    if (externalLibraryPath.startsWith(folderPath))
                    {
                        //if we're already watching for changes in the
                        //workspace, and we need to avoid so that the compiler
                        //doesn't get confused by duplicates that might have
                        //slightly different capitalization because the language
                        //server protocol and Java file watchers don't
                        //necessarily match
                        shouldWatch = false;
                        break;
                    }
                }
            }
            if (shouldWatch)
            {
                watchNewSourceOrLibraryPath(externalLibraryPath, folderData);
            }
        }
    }

    private void createSourcePathWatcher()
    {
        try
        {
            sourcePathWatcher = FileSystems.getDefault().newWatchService();
        }
        catch (IOException e)
        {
            System.err.println("Failed to get watch service for source paths.");
            e.printStackTrace(System.err);
        }
        sourcePathWatcherThread = new Thread()
        {
            public void run()
            {
                while(true)
                {
                    WatchKey watchKey = null;
                    try
                    {
                        //pause the thread while there are no changes pending,
                        //for better performance
                        watchKey = sourcePathWatcher.take();
                    }
                    catch(InterruptedException e)
                    {
                        return;
                    }
                    Set<WorkspaceFolderData> foldersToCheckForProblems = new HashSet<WorkspaceFolderData>();
                    while (watchKey != null)
                    {
                        for (WorkspaceFolder folder : workspaceFolderManager.getWorkspaceFolders())
                        {
                            WorkspaceFolderData folderData = workspaceFolderManager.getWorkspaceFolderData(folder);
                            if(!folderData.sourceOrLibraryPathWatchKeys.containsKey(watchKey))
                            {
                                continue;
                            }
                            foldersToCheckForProblems.add(folderData);
                            List<FileEvent> changes = new ArrayList<>();
                            Path path = folderData.sourceOrLibraryPathWatchKeys.get(watchKey);
                            for (WatchEvent<?> event : watchKey.pollEvents())
                            {
                                WatchEvent.Kind<?> kind = event.kind();
                                Path childPath = (Path) event.context();
                                childPath = path.resolve(childPath);
                                if(java.nio.file.Files.isDirectory(childPath))
                                {
                                    if(kind.equals(StandardWatchEventKinds.ENTRY_CREATE))
                                    {
                                        //if a new directory has been created under
                                        //an existing that we're already watching,
                                        //then start watching the new one too.
                                        watchNewSourceOrLibraryPath(childPath, folderData);
                                    }
                                }
                                FileChangeType changeType = FileChangeType.Changed;
                                if(kind.equals(StandardWatchEventKinds.ENTRY_CREATE))
                                {
                                    changeType = FileChangeType.Created;
                                }
                                else if(kind.equals(StandardWatchEventKinds.ENTRY_DELETE))
                                {
                                    changeType = FileChangeType.Deleted;
                                }
                                changes.add(new FileEvent(childPath.toUri().toString(), changeType));
                            }
                            boolean valid = watchKey.reset();
                            if (!valid)
                            {
                                folderData.sourceOrLibraryPathWatchKeys.remove(watchKey);
                            }
                            //convert to DidChangeWatchedFilesParams and pass
                            //to didChangeWatchedFiles, as if a notification
                            //had been sent from the client.
                            DidChangeWatchedFilesParams params = new DidChangeWatchedFilesParams();
                            params.setChanges(changes);
                            didChangeWatchedFiles(params, false);
                        }
                        //keep handling new changes until we run out
                        watchKey = sourcePathWatcher.poll();
                    }
                    //if we get here, watchKey is null, so there are no more
                    //pending changes. now, we can check for problems.
                    for (WorkspaceFolderData folderData : foldersToCheckForProblems)
                    {
                        checkProjectForProblems(folderData);
                    }
                    foldersToCheckForProblems.clear();
                }
            }
        };
        sourcePathWatcherThread.start();
    }

    private void refreshProjectOptions(WorkspaceFolderData folderData)
    {
        IProjectConfigStrategy currentConfig = folderData.config;
        ProjectOptions projectOptions = folderData.options;
        if (!currentConfig.getChanged() && projectOptions != null)
        {
            //the options are fully up-to-date
            return;
        }
        //if the configuration changed, start fresh with a whole new project
        folderData.cleanup();
        folderData.options = currentConfig.getOptions();
    }

    private void addCompilerProblem(ICompilerProblem problem, PublishDiagnosticsParams publish)
    {
        if (!compilerProblemFilter.isAllowed(problem))
        {
            return;
        }
        Diagnostic diagnostic = LanguageServerCompilerUtils.getDiagnosticFromCompilerProblem(problem);
        List<Diagnostic> diagnostics = publish.getDiagnostics();
        diagnostics.add(diagnostic);
    }

    private void checkFilePathForSyntaxProblems(Path path, WorkspaceFolderData folderData, ProblemQuery problemQuery)
    {
        ASParser parser = null;
        Reader reader = fileTracker.getReader(path);
        if (reader != null)
        {
            StreamingASTokenizer tokenizer = null;
            ASToken[] tokens = null;
            try
            {
                tokenizer = StreamingASTokenizer.createForRepairingASTokenizer(reader, path.toString(), null);
                tokens = tokenizer.getTokens(reader);
            }
            finally
            {
                try
                {
                    reader.close();
                }
                catch(IOException e) {}
            }
            if (tokenizer.hasTokenizationProblems())
            {
                problemQuery.addAll(tokenizer.getTokenizationProblems());
            }
            RepairingTokenBuffer buffer = new RepairingTokenBuffer(tokens);

            Workspace workspace = new Workspace();
            workspace.endRequest();
            parser = new ASParser(workspace, buffer);
            FileNode node = new FileNode(workspace);
            try
            {
                parser.file(node);
            }
            catch (Exception e)
            {
                parser = null;
                System.err.println("Failed to parse file (" + path.toString() + "): " + e);
                e.printStackTrace(System.err);
            }
            //if an error occurred above, parser will be null
            if (parser != null)
            {
                problemQuery.addAll(parser.getSyntaxProblems());
            }
        }

        ProjectOptions projectOptions = folderData.options;
        SyntaxFallbackProblem syntaxProblem = null;
        if (reader == null)
        {
            //the file does not exist
            syntaxProblem = new SyntaxFallbackProblem(path.toString(), "File not found: " + path.toAbsolutePath().toString() + ". Error checking has been disabled.");
        }
        else if (parser == null && projectOptions == null)
        {
            //we couldn't load the project configuration and we couldn't parse
            //the file. we can't provide any information here.
            syntaxProblem = new SyntaxFallbackProblem(path.toString(), "Failed to load project configuration options. Error checking has been disabled.");
        }
        else if (parser == null)
        {
            //something terrible happened, and this is the best we can do
            syntaxProblem = new SyntaxFallbackProblem(path.toString(), "A fatal error occurred while checking for simple syntax problems.");
        }
        else if (projectOptions == null)
        {
            //something went wrong while attempting to load and parse the
            //project configuration, but we could successfully parse the syntax
            //tree.
            syntaxProblem = new SyntaxFallbackProblem(path.toString(), "Failed to load project configuration options. Error checking has been disabled, except for simple syntax problems.");
        }
        else
        {
            //we seem to have loaded the project configuration and we could
            //parse the file, but something still went wrong.
            syntaxProblem = new SyntaxFallbackProblem(path.toString(), "A fatal error occurred. Error checking has been disabled, except for simple syntax problems.");
        }
        problemQuery.add(syntaxProblem);
        reader = null;
    }

    private Path getMainCompilationUnitPath(WorkspaceFolderData folderData)
    {
        refreshProjectOptions(folderData);
        ProjectOptions projectOptions = folderData.options;
        if (projectOptions == null)
        {
            return null;
        }
        String[] files = projectOptions.files;
        if (files == null || files.length == 0)
        {
            return null;
        }
        String lastFilePath = files[files.length - 1];
        return Paths.get(lastFilePath);
    }

    /**
     * Returns the project associated with a workspace folder. If it has already
     * been created, returns the existing project *unless* the configuration has
     * changed. When the configuration has changed, destroys the old project and
     * creates a new one.
     */
    private RoyaleProject getProject(WorkspaceFolderData folderData)
    {
        if(folderData == null)
        {
            System.err.println("Cannot find workspace for project.");
            return null;
        }
        refreshProjectOptions(folderData);
        RoyaleProject project = folderData.project;
        ProjectOptions projectOptions = folderData.options;
        if (projectOptions == null)
        {
            folderData.cleanup();
            //if there are existing configuration problems, they should no
            //longer be considered valid
            publishDiagnosticsForProblemQuery(new ProblemQuery(), folderData.configProblemTracker, folderData, true);
            return null;
        }
        if (project != null)
        {   
            //clear all old problems because they won't be cleared automatically
            project.getProblems().clear();
            return project;
        }

        String oldUserDir = System.getProperty("user.dir");
        List<ICompilerProblem> configProblems = new ArrayList<>();

        RoyaleProjectConfigurator configurator = null;
        compilerWorkspace.startIdleState();
        try
        {
            URI rootURI = URI.create(folderData.folder.getUri());
            Path rootPath = Paths.get(rootURI);
            System.setProperty("user.dir", rootPath.toString());
            project = CompilerProjectUtils.createProject(projectOptions, compilerWorkspace);
            configurator = CompilerProjectUtils.createConfigurator(project, projectOptions);
        }
        finally
        {
            compilerWorkspace.endIdleState(IWorkspace.NIL_COMPILATIONUNITS_TO_UPDATE);
        }

        //this is not wrapped in startIdleState() or startBuilding()
        //because applyToProject() could trigger both, depending on context!
        if(configurator != null)
        {
            boolean result = configurator.applyToProject(project);
            Configuration configuration = configurator.getConfiguration();
            //it's possible for the configuration to be null when parsing
            //certain values in additionalOptions in asconfig.json
            if(configuration != null)
            {
                configProblems.addAll(configurator.getConfigurationProblems());
            }
            if (!result)
            {
                configurator = null;
            }
        }

        compilerWorkspace.startIdleState();
        try
        {
            if(configurator != null)
            {
                ITarget.TargetType targetType = ITarget.TargetType.SWF;
                if (projectOptions.type.equals(ProjectType.LIB))
                {
                    targetType = ITarget.TargetType.SWC;
                }
                ITargetSettings targetSettings = configurator.getTargetSettings(targetType);
                if (targetSettings == null)
                {
                    System.err.println("Failed to get compile settings for +configname=" + projectOptions.config + ".");
                    configurator = null;
                }
                else
                {
                    project.setTargetSettings(targetSettings);
                }
            }

            if (configurator == null)
            {
                project.delete();
                project = null;
            }

            System.setProperty("user.dir", oldUserDir);

            ICompilerProblemSettings compilerProblemSettings = null;
            if (configurator != null)
            {
                compilerProblemSettings = configurator.getCompilerProblemSettings();
            }
            ProblemQuery problemQuery = new ProblemQuery(compilerProblemSettings);
            problemQuery.addAll(configProblems);
            publishDiagnosticsForProblemQuery(problemQuery, folderData.configProblemTracker, folderData, true);

            folderData.project = project;
            folderData.configurator = configurator;
            prepareNewProject(folderData);
        }
        finally
        {
            compilerWorkspace.endIdleState(IWorkspace.NIL_COMPILATIONUNITS_TO_UPDATE);
        }
        return project;
    }

    private void checkProjectForProblems(WorkspaceFolderData folderData)
    {
        getProject(folderData);
        ProjectOptions projectOptions = folderData.options;
        if (projectOptions == null || projectOptions.type.equals(ProjectType.LIB))
        {
            ProblemQuery problemQuery = workspaceFolderDataToProblemQuery(folderData);
            for(Path filePath : fileTracker.getOpenFiles())
            {
                WorkspaceFolderData otherFolderData = workspaceFolderManager.getWorkspaceFolderDataForSourceFile(filePath);
                if (!folderData.equals(otherFolderData))
                {
                    //don't check files from other projects!
                    continue;
                }
                checkFilePathForProblems(filePath, problemQuery, folderData, true);
            }
            publishDiagnosticsForProblemQuery(problemQuery, folderData.codeProblemTracker, folderData, true);
        }
        else //app
        {
            Path path = getMainCompilationUnitPath(folderData);
            if (path != null)
            {
                checkFilePathForProblems(path, folderData, false);
            }
        }
    }

    private void publishDiagnosticsForProblemQuery(ProblemQuery problemQuery, ProblemTracker problemTracker, WorkspaceFolderData folderData, boolean releaseStale)
    {
        Map<URI, PublishDiagnosticsParams> filesMap = new HashMap<>();
        for (ICompilerProblem problem : problemQuery.getFilteredProblems())
        {
            String problemSourcePath = problem.getSourcePath();
            IncludeFileData includedFile = folderData.includedFiles.get(problemSourcePath);
            if (includedFile != null && !includedFile.parentPath.equals(problemSourcePath))
            {
                //skip files that are included in other files
                continue;
            }
            if (problemSourcePath == null)
            {
                Path folderPath = LanguageServerCompilerUtils.getPathFromLanguageServerURI(folderData.folder.getUri());
                problemSourcePath = folderPath.toString();
            }
            if (CommandLineConfigurator.SOURCE_COMMAND_LINE.equals(problemSourcePath))
            {
                //for configuration problems that point to the command line, the
                //best default location to send the user is probably to the
                //config file (like asconfig.json in Visual Studio Code)
                problemSourcePath = folderData.config.getDefaultConfigurationProblemPath();
            }
            URI uri = Paths.get(problemSourcePath).toUri();
            if (!filesMap.containsKey(uri))
            {
                PublishDiagnosticsParams params = new PublishDiagnosticsParams();
                params.setUri(uri.toString());
                params.setDiagnostics(new ArrayList<>());
                filesMap.put(uri, params);
            }
            PublishDiagnosticsParams params = filesMap.get(uri);
            problemTracker.trackFileWithProblems(uri);
            addCompilerProblem(problem, params);
        }
        if (releaseStale)
        {
            problemTracker.releaseStale();
        }
        else
        {
            problemTracker.makeStale();
        }
        if (languageClient != null)
        {
            filesMap.values().forEach(languageClient::publishDiagnostics);
        }
    }

    private ProblemQuery workspaceFolderDataToProblemQuery(WorkspaceFolderData folderData)
    {
        ICompilerProblemSettings compilerProblemSettings = null;
        if (folderData.configurator != null)
        {
            compilerProblemSettings = folderData.configurator.getCompilerProblemSettings();
        }
        return new ProblemQuery(compilerProblemSettings);
    }

    private void checkFilePathForProblems(Path path, WorkspaceFolderData folderData, boolean quick)
    {
        if(folderData.equals(workspaceFolderManager.getFallbackFolderData()))
        {
            compilerWorkspace.startBuilding();
            try
            {
                ICompilationUnit unitForPath = CompilerProjectUtils.findCompilationUnit(path, folderData.project);
                if (unitForPath != null)
                {
                    CompilationUnitUtils.findIncludedFiles(unitForPath, folderData.includedFiles);
                }
            }
            finally
            {
                compilerWorkspace.doneBuilding();
            }
            return;
        }
        ProblemQuery problemQuery = workspaceFolderDataToProblemQuery(folderData);
        checkFilePathForProblems(path, problemQuery, folderData, quick);
        publishDiagnosticsForProblemQuery(problemQuery, folderData.codeProblemTracker, folderData, !quick);
    }

    private void checkFilePathForProblems(Path path, ProblemQuery problemQuery, WorkspaceFolderData folderData, boolean quick)
    {
        if(folderData.equals(workspaceFolderManager.getFallbackFolderData()))
        {
            return;
        }
        RoyaleProject project = folderData.project;
        if (project != null && !SourcePathUtils.isInProjectSourcePath(path, project, folderData.configurator))
        {
            publishDiagnosticForFileOutsideSourcePath(path);
            return;
        }
        
        URI uri = path.toUri();
        if(notOnSourcePathSet.contains(uri))
        {
            //if the file was not on the project's source path, clear out any
            //errors that might have existed previously
            notOnSourcePathSet.remove(uri);

            PublishDiagnosticsParams publish = new PublishDiagnosticsParams();
            publish.setDiagnostics(new ArrayList<>());
            publish.setUri(uri.toString());
            if (languageClient != null)
            {
                languageClient.publishDiagnostics(publish);
            }
        }
        
        ProjectOptions projectOptions = folderData.options;
        if (projectOptions == null || !checkFilePathForAllProblems(path, problemQuery, folderData, false))
        {
            checkFilePathForSyntaxProblems(path, folderData, problemQuery);
        }
    }

    private void publishDiagnosticForFileOutsideSourcePath(Path path)
    {
        URI uri = path.toUri();
        PublishDiagnosticsParams publish = new PublishDiagnosticsParams();
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        publish.setDiagnostics(diagnostics);
        publish.setUri(uri.toString());

        Diagnostic diagnostic = LSPUtils.createDiagnosticWithoutRange();
        diagnostic.setSeverity(DiagnosticSeverity.Information);
        if(workspaceFolderManager.getWorkspaceFolders().size() == 0)
        {
            diagnostic.setMessage("Open a workspace folder to enable all ActionScript & MXML language features.");
        }
        else
        {
            diagnostic.setMessage(path.getFileName() + " is not located in the project's source path. Code intelligence will not be available for this file.");
        }
        diagnostics.add(diagnostic);

        notOnSourcePathSet.add(uri);

        if (languageClient != null)
        {
            languageClient.publishDiagnostics(publish);
        }
    }

    private boolean checkFilePathForAllProblems(Path path, ProblemQuery problemQuery, WorkspaceFolderData folderData, boolean quick)
    {
        compilerWorkspace.startBuilding();
        try
        {
            ICompilationUnit unitForPath = CompilerProjectUtils.findCompilationUnit(path, folderData.project);
            if (unitForPath == null)
            {
                //fall back to the syntax check instead
                return false;
            }
            if (waitForBuildFinishRunner != null
                && unitForPath.equals(waitForBuildFinishRunner.getCompilationUnit())
                && waitForBuildFinishRunner.isRunning())
            {
                //take precedence over the real time problem checker
                waitForBuildFinishRunner.setCancelled();
            }
            CompilerProject project = (CompilerProject) unitForPath.getProject();
            Collection<ICompilerProblem> fatalProblems = project.getFatalProblems();
            if (fatalProblems == null || fatalProblems.size() == 0)
            {
                fatalProblems = project.getProblems();
            }
            problemQuery.addAll(fatalProblems);
            if (fatalProblems != null && fatalProblems.size() > 0)
            {
                //since we found some problems, we'll skip the syntax check fallback
                return true;
            }
            try
            {
                if (quick)
                {
                    checkCompilationUnitForAllProblems(unitForPath, problemQuery);
                    CompilationUnitUtils.findIncludedFiles(unitForPath, folderData.includedFiles);
                    return true;
                }
                //start fresh when checking all compilation units
                folderData.includedFiles.clear();
                boolean continueCheckingForErrors = true;
                while (continueCheckingForErrors)
                {
                    try
                    {
                        for (ICompilationUnit unit : project.getCompilationUnits())
                        {
                            if (unit == null
                                    || unit instanceof SWCCompilationUnit
                                    || unit instanceof ResourceBundleCompilationUnit)
                            {
                                //compiled compilation units won't have problems
                                continue;
                            }
                            checkCompilationUnitForAllProblems(unit, problemQuery);
                        }
                        if (initialized)
                        {
                            //if not initialized, do this later
                            for (ICompilationUnit unit : project.getCompilationUnits())
                            {
                                if (unit == null
                                        || unit instanceof SWCCompilationUnit
                                        || unit instanceof ResourceBundleCompilationUnit)
                                {
                                    continue;
                                }
                                //just to be safe, find all of the included files
                                //after we've checked for problems
                                CompilationUnitUtils.findIncludedFiles(unit, folderData.includedFiles);
                            }
                        }
                        continueCheckingForErrors = false;
                    }
                    catch (ConcurrentModificationException e)
                    {
                        //when we finished building one of the compilation
                        //units, more were added to the collection, so we need
                        //to start over because we can't iterate over a modified
                        //collection.
                    }
                }
            }
            catch (Exception e)
            {
                System.err.println("Exception during build: " + e);
                e.printStackTrace(System.err);
                return false;
            }
            return true;
        }
        finally
        {
            compilerWorkspace.doneBuilding();
        }
    }

    private void checkCompilationUnitForAllProblems(ICompilationUnit unit, ProblemQuery problemQuery)
    {
        ArrayList<ICompilerProblem> problems = new ArrayList<>();
        try
        {
            if(initialized)
            {
                //if we pass in null, it's desigfned to ignore certain errors that
                //don't matter for IDE code intelligence.
                unit.waitForBuildFinish(problems, null);
                problemQuery.addAll(problems);
            }
            else
            {
                //we can't publish diagnostics yet, but we can start the build
                //process in the background so that it's faster when we're ready
                //to publish diagnostics after initialization
                unit.getSyntaxTreeRequest();
                unit.getFileScopeRequest();
                unit.getOutgoingDependenciesRequest();
                unit.getABCBytesRequest();
            }
        }
        catch (Exception e)
        {
            System.err.println("Exception during waitForBuildFinish(): " + e);
            e.printStackTrace(System.err);

            InternalCompilerProblem problem = new InternalCompilerProblem(e);
            problemQuery.add(problem);
        }
    }

	private void updateSDK(JsonObject settings)
	{
		if (!settings.has("as3mxml"))
		{
			return;
		}
		JsonObject as3mxml = settings.get("as3mxml").getAsJsonObject();
		if (!as3mxml.has("sdk"))
		{
			return;
		}
		JsonObject sdk = as3mxml.get("sdk").getAsJsonObject();
		String frameworkSDK = null;
		if (sdk.has("framework"))
		{
			JsonElement frameworkValue = sdk.get("framework");
			if (!frameworkValue.isJsonNull())
			{
				frameworkSDK = frameworkValue.getAsString();
			}
		}
		if (frameworkSDK == null && sdk.has("editor"))
		{
			//for legacy reasons, we fall back to the editor SDK
			JsonElement editorValue = sdk.get("editor");
			if (!editorValue.isJsonNull())
			{
				frameworkSDK = editorValue.getAsString();
			}
		}
		if (frameworkSDK == null)
		{
			//keep using the existing framework for now
			return;
		}
		String frameworkLib = null;
		Path frameworkLibPath = Paths.get(frameworkSDK).resolve(FRAMEWORKS_RELATIVE_PATH_CHILD).toAbsolutePath().normalize();
		if (frameworkLibPath.toFile().exists())
		{
			//if the frameworks directory exists, use it!
			frameworkLib = frameworkLibPath.toString();
		}
		else 
		{
			//if the frameworks directory doesn't exist, we also
			//need to check for Apache Royale's unique layout
			//with the royale-asjs directory
			Path royalePath = Paths.get(frameworkSDK).resolve(ROYALE_ASJS_RELATIVE_PATH_CHILD).resolve(FRAMEWORKS_RELATIVE_PATH_CHILD).toAbsolutePath().normalize();
			if(royalePath.toFile().exists())
			{
				frameworkLib = royalePath.toString();
			}
		}
		if (frameworkLib == null)
		{
			//keep using the existing framework for now
			return;
		}
		String oldFrameworkLib = System.getProperty(PROPERTY_FRAMEWORK_LIB);
		if (oldFrameworkLib.equals(frameworkLib))
		{
			//frameworks library has not changed
			return;
		}
		System.setProperty(PROPERTY_FRAMEWORK_LIB, frameworkLib);
		checkForProblemsNow();
	}

	private void updateRealTimeProblems(JsonObject settings)
	{
		if (!settings.has("as3mxml"))
		{
			return;
		}
		JsonObject as3mxml = settings.get("as3mxml").getAsJsonObject();
		if (!as3mxml.has("problems"))
		{
			return;
		}
		JsonObject problems = as3mxml.get("problems").getAsJsonObject();
		if (!problems.has("realTime"))
		{
			return;
		}
		boolean newRealTimeProblems = problems.get("realTime").getAsBoolean();
        if(realTimeProblems == newRealTimeProblems)
        {
            return;
        }
        realTimeProblems = newRealTimeProblems;
        if(newRealTimeProblems)
        {
            checkForProblemsNow();
        }
	}
}
