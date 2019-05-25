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
import java.io.PrintStream;
import java.io.Reader;
import java.io.StringReader;
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

import com.as3mxml.asconfigc.ASConfigC;
import com.as3mxml.asconfigc.ASConfigCException;
import com.as3mxml.asconfigc.ASConfigCOptions;
import com.as3mxml.asconfigc.compiler.ProjectType;
import com.as3mxml.vscode.asdoc.VSCodeASDocDelegate;
import com.as3mxml.vscode.commands.ICommandConstants;
import com.as3mxml.vscode.compiler.CompilerShell;
import com.as3mxml.vscode.compiler.problems.SyntaxFallbackProblem;
import com.as3mxml.vscode.project.IProjectConfigStrategy;
import com.as3mxml.vscode.project.IProjectConfigStrategyFactory;
import com.as3mxml.vscode.project.ProjectOptions;
import com.as3mxml.vscode.project.WorkspaceFolderData;
import com.as3mxml.vscode.providers.CompletionProvider;
import com.as3mxml.vscode.providers.DefinitionProvider;
import com.as3mxml.vscode.providers.DocumentSymbolProvider;
import com.as3mxml.vscode.providers.HoverProvider;
import com.as3mxml.vscode.providers.ImplementationProvider;
import com.as3mxml.vscode.providers.ReferencesProvider;
import com.as3mxml.vscode.providers.RenameProvider;
import com.as3mxml.vscode.providers.SignatureHelpProvider;
import com.as3mxml.vscode.providers.TypeDefinitionProvider;
import com.as3mxml.vscode.providers.WorkspaceSymbolProvider;
import com.as3mxml.vscode.services.ActionScriptLanguageClient;
import com.as3mxml.vscode.utils.ASTUtils;
import com.as3mxml.vscode.utils.ActionScriptSDKUtils;
import com.as3mxml.vscode.utils.CodeActionsUtils;
import com.as3mxml.vscode.utils.CompilationUnitUtils;
import com.as3mxml.vscode.utils.CompilationUnitUtils.IncludeFileData;
import com.as3mxml.vscode.utils.CompilerProblemFilter;
import com.as3mxml.vscode.utils.CompilerProjectUtils;
import com.as3mxml.vscode.utils.ImportRange;
import com.as3mxml.vscode.utils.ImportTextEditUtils;
import com.as3mxml.vscode.utils.LSPUtils;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;
import com.as3mxml.vscode.utils.MXMLDataUtils;
import com.as3mxml.vscode.utils.ProblemTracker;
import com.as3mxml.vscode.utils.SourcePathUtils;
import com.as3mxml.vscode.utils.WaitForBuildFinishRunner;
import com.as3mxml.vscode.utils.WorkspaceFolderManager;
import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.royale.compiler.clients.problems.ProblemQuery;
import org.apache.royale.compiler.config.CommandLineConfigurator;
import org.apache.royale.compiler.config.Configuration;
import org.apache.royale.compiler.config.ICompilerProblemSettings;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IInterfaceDefinition;
import org.apache.royale.compiler.filespecs.IFileSpecification;
import org.apache.royale.compiler.internal.mxml.MXMLData;
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
import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.problems.ICompilerProblem;
import org.apache.royale.compiler.problems.InternalCompilerProblem;
import org.apache.royale.compiler.targets.ITarget;
import org.apache.royale.compiler.targets.ITargetSettings;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IClassNode;
import org.apache.royale.compiler.tree.as.IContainerNode;
import org.apache.royale.compiler.tree.as.IExpressionNode;
import org.apache.royale.compiler.tree.as.IFunctionCallNode;
import org.apache.royale.compiler.tree.as.IIdentifierNode;
import org.apache.royale.compiler.tree.as.IImportNode;
import org.apache.royale.compiler.tree.as.ILanguageIdentifierNode;
import org.apache.royale.compiler.tree.as.IMemberAccessExpressionNode;
import org.apache.royale.compiler.tree.as.ITryNode;
import org.apache.royale.compiler.tree.mxml.IMXMLInstanceNode;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.apache.royale.compiler.workspaces.IWorkspace;
import org.apache.royale.utils.FilenameNormalization;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
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
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
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
import org.eclipse.lsp4j.services.TextDocumentService;

/**
 * Handles requests from Visual Studio Code that are at the document level,
 * including things like API completion, function signature help, and find all
 * references. Calls APIs on the Apache Royale compiler to get data for the
 * responses to return to VSCode.
 */
public class ActionScriptTextDocumentService implements TextDocumentService
{
    private static final String MXML_EXTENSION = ".mxml";
    private static final String AS_EXTENSION = ".as";
    private static final String SWC_EXTENSION = ".swc";
    private static final String PROPERTY_FRAMEWORK_LIB = "royalelib";

    private ActionScriptLanguageClient languageClient;
    private IProjectConfigStrategyFactory projectConfigStrategyFactory;
    private String oldFrameworkSDKPath;
    private Workspace compilerWorkspace;
    private WorkspaceFolderManager workspaceFolderManager;
    private WatchService sourcePathWatcher;
    private Thread sourcePathWatcherThread;
    private ClientCapabilities clientCapabilities;
    private boolean completionSupportsSnippets = false;
    private CompilerShell compilerShell;
    private CompilerProblemFilter compilerProblemFilter = new CompilerProblemFilter();
    private boolean initialized = false;
    private boolean frameworkSDKIsRoyale = false;
    private WaitForBuildFinishRunner waitForBuildFinishRunner;
    private Set<URI> notOnSourcePathSet = new HashSet<>();
    
    private boolean realTimeProblems = true;

    public boolean getRealTimeProblems()
    {
        return realTimeProblems;
    }

    public void setRealTimeProblems(boolean value)
    {
        if(realTimeProblems == value)
        {
            return;
        }
        realTimeProblems = value;
        if(value)
        {
            checkForProblemsNow();
        }
    }

    public ActionScriptTextDocumentService()
    {
        compilerWorkspace = new Workspace();
        compilerWorkspace.setASDocDelegate(new VSCodeASDocDelegate());
        workspaceFolderManager = new WorkspaceFolderManager(compilerWorkspace);
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
            IFileSpecification fileSpec = workspaceFolderManager.getFileSpecification(normalizedPath);
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
                CompletionProvider provider = new CompletionProvider(workspaceFolderManager, completionSupportsSnippets, frameworkSDKIsRoyale);
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
                HoverProvider provider = new HoverProvider(workspaceFolderManager);
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
                SignatureHelpProvider provider = new SignatureHelpProvider(workspaceFolderManager);
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
                DefinitionProvider provider = new DefinitionProvider(workspaceFolderManager);
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
                TypeDefinitionProvider provider = new TypeDefinitionProvider(workspaceFolderManager);
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
                ImplementationProvider provider = new ImplementationProvider(workspaceFolderManager);
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
                ReferencesProvider provider = new ReferencesProvider(workspaceFolderManager);
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
    public CompletableFuture<List<? extends SymbolInformation>> workspaceSymbol(WorkspaceSymbolParams params)
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
                cancelToken.checkCanceled();
                TextDocumentIdentifier textDocument = params.getTextDocument();
                Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
                if (path == null)
                {
                    cancelToken.checkCanceled();
                    return Collections.emptyList();
                }
                //we don't need to create code actions for non-open files
                if (!workspaceFolderManager.sourceByPath.containsKey(path))
                {
                    cancelToken.checkCanceled();
                    return Collections.emptyList();
                }
                WorkspaceFolderData folderData = workspaceFolderManager.getWorkspaceFolderDataForSourceFile(path);
                if (folderData == null || folderData.project == null)
                {
                    cancelToken.checkCanceled();
                    //the path must be in the workspace or source-path
                    return Collections.emptyList();
                }
                RoyaleProject project = folderData.project;

                if (project == null || !SourcePathUtils.isInProjectSourcePath(path, project, folderData.configurator))
                {
                    cancelToken.checkCanceled();
                    //the path must be in the workspace or source-path
                    return Collections.emptyList();
                }

                List<? extends Diagnostic> diagnostics = params.getContext().getDiagnostics();
                List<Either<Command, CodeAction>> codeActions = new ArrayList<>();
                findSourceActions(path, codeActions);
                findCodeActionsForDiagnostics(path, folderData, diagnostics, codeActions);

                ICompilationUnit unit = CompilerProjectUtils.findCompilationUnit(path, project);
                if (unit != null)
                {
                    IASNode ast = ASTUtils.getCompilationUnitAST(unit);
                    if (ast != null)
                    {
                        String fileText = workspaceFolderManager.sourceByPath.get(path);
                        CodeActionsUtils.findGetSetCodeActions(ast, project, textDocument.getUri(), fileText, params.getRange(), codeActions);
                    }
                }
                cancelToken.checkCanceled();
                return codeActions;
            }
            finally
            {
                compilerWorkspace.doneBuilding();
            }
        });
    }

    public void findSourceActions(Path path, List<Either<Command, CodeAction>> codeActions)
    {
        Command organizeCommand = new Command();
        organizeCommand.setTitle("Organize Imports");
        organizeCommand.setCommand(ICommandConstants.ORGANIZE_IMPORTS_IN_URI);
        JsonObject uri = new JsonObject();
        uri.addProperty("external", path.toUri().toString());
        organizeCommand.setArguments(Lists.newArrayList(
            uri
        ));
        CodeAction organizeImports = new CodeAction();
        organizeImports.setKind(CodeActionKind.SourceOrganizeImports);
        organizeImports.setTitle(organizeCommand.getTitle());
        organizeImports.setCommand(organizeCommand);
        codeActions.add(Either.forRight(organizeImports));
    }

    public void findCodeActionsForDiagnostics(Path path, WorkspaceFolderData folderData, List<? extends Diagnostic> diagnostics, List<Either<Command, CodeAction>> codeActions)
    {
        boolean handledUnimplementedMethods = false;
        for (Diagnostic diagnostic : diagnostics)
        {
            //I don't know why this can be null
            String code = diagnostic.getCode();
            if (code == null)
            {
                continue;
            }
            switch (code)
            {
                case "1120": //AccessUndefinedPropertyProblem
                {
                    //see if there's anything we can import
                    createCodeActionsForImport(path, diagnostic, folderData, codeActions);
                    createCodeActionForMissingLocalVariable(path, diagnostic, folderData, codeActions);
                    createCodeActionForMissingField(path, diagnostic, folderData, codeActions);
                    createCodeActionForMissingEventListener(path, diagnostic, folderData, codeActions);
                    break;
                }
                case "1046": //UnknownTypeProblem
                {
                    //see if there's anything we can import
                    createCodeActionsForImport(path, diagnostic, folderData, codeActions);
                    break;
                }
                case "1017": //UnknownSuperclassProblem
                {
                    //see if there's anything we can import
                    createCodeActionsForImport(path, diagnostic, folderData, codeActions);
                    break;
                }
                case "1045": //UnknownInterfaceProblem
                {
                    //see if there's anything we can import
                    createCodeActionsForImport(path, diagnostic, folderData, codeActions);
                    break;
                }
                case "1061": //StrictUndefinedMethodProblem
                {
                    createCodeActionForMissingMethod(path, diagnostic, folderData, codeActions);
                    break;
                }
                case "1073": //MissingCatchOrFinallyProblem
                {
                    createCodeActionForMissingCatchOrFinally(path, diagnostic, folderData, codeActions);
                    break;
                }
                case "1119": //AccessUndefinedMemberProblem
                {
                    createCodeActionForMissingField(path, diagnostic, folderData, codeActions);
                    createCodeActionForMissingEventListener(path, diagnostic, folderData, codeActions);
                    break;
                }
                case "1178": //InaccessiblePropertyReferenceProblem
                {
                    //see if there's anything we can import
                    createCodeActionsForImport(path, diagnostic, folderData, codeActions);
                    break;
                }
                case "1180": //CallUndefinedMethodProblem
                {
                    //see if there's anything we can import
                    createCodeActionsForImport(path, diagnostic, folderData, codeActions);
                    createCodeActionForMissingMethod(path, diagnostic, folderData, codeActions);
                    break;
                }
                case "1044": //UnimplementedInterfaceMethodProblem
                {
                    //only needs to be handled one time
                    if(!handledUnimplementedMethods)
                    {
                        handledUnimplementedMethods = true;
                        createCodeActionForUnimplementedMethods(path, diagnostic, folderData, codeActions);
                    }
                    break;
                }
            }
        }
    }

    private void createCodeActionForMissingField(Path path, Diagnostic diagnostic, WorkspaceFolderData folderData, List<Either<Command, CodeAction>> codeActions)
    {
        Position position = diagnostic.getRange().getStart();
        int currentOffset = workspaceFolderManager.getOffsetFromPathAndPosition(path, position, folderData);
        IASNode offsetNode = workspaceFolderManager.getOffsetNode(path, currentOffset, folderData);
        if (offsetNode instanceof IMXMLInstanceNode)
        {
            MXMLData mxmlData = workspaceFolderManager.getMXMLDataForPath(path, folderData);
            if (mxmlData != null)
            {
                IMXMLTagData offsetTag = MXMLDataUtils.getOffsetMXMLTag(mxmlData, currentOffset);
                //workaround for bug in Royale compiler
                Position newPosition = new Position(position.getLine(), position.getCharacter() + 1);
                int newOffset = workspaceFolderManager.getOffsetFromPathAndPosition(path, newPosition, folderData);
                offsetNode = workspaceFolderManager.getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path, newOffset, folderData);
            }
        }
        IIdentifierNode identifierNode = null;
        if (offsetNode instanceof IIdentifierNode)
        {
            IASNode parentNode = offsetNode.getParent();
            if (parentNode instanceof IMemberAccessExpressionNode)
            {
                IMemberAccessExpressionNode memberAccessExpressionNode = (IMemberAccessExpressionNode) offsetNode.getParent();
                IExpressionNode leftOperandNode = memberAccessExpressionNode.getLeftOperandNode();
                if (leftOperandNode instanceof ILanguageIdentifierNode)
                {
                    ILanguageIdentifierNode leftIdentifierNode = (ILanguageIdentifierNode) leftOperandNode;
                    if (leftIdentifierNode.getKind() == ILanguageIdentifierNode.LanguageIdentifierKind.THIS)
                    {
                        identifierNode = (IIdentifierNode) offsetNode;
                    }
                }
            }
            else //no member access
            {
                identifierNode = (IIdentifierNode) offsetNode;
            }
        }
        if (identifierNode == null)
        {
            return;
        }
        String fileText = workspaceFolderManager.getFileTextForPath(path);
        if(fileText == null)
        {
            return;
        }
        WorkspaceEdit edit = CodeActionsUtils.createWorkspaceEditForGenerateFieldVariable(
            identifierNode, path.toUri().toString(), fileText);
        if(edit == null)
        {
            return;
        }
        
        CodeAction codeAction = new CodeAction();
        codeAction.setDiagnostics(Collections.singletonList(diagnostic));
        codeAction.setTitle("Generate Field Variable");
        codeAction.setEdit(edit);
        codeAction.setKind(CodeActionKind.QuickFix);
        codeActions.add(Either.forRight(codeAction));
    }
    
    private void createCodeActionForMissingLocalVariable(Path path, Diagnostic diagnostic, WorkspaceFolderData folderData, List<Either<Command, CodeAction>> codeActions)
    {
        Position position = diagnostic.getRange().getStart();
        int currentOffset = workspaceFolderManager.getOffsetFromPathAndPosition(path, position, folderData);
        IASNode offsetNode = workspaceFolderManager.getOffsetNode(path, currentOffset, folderData);
        if (offsetNode instanceof IMXMLInstanceNode)
        {
            MXMLData mxmlData = workspaceFolderManager.getMXMLDataForPath(path, folderData);
            if (mxmlData != null)
            {
                IMXMLTagData offsetTag = MXMLDataUtils.getOffsetMXMLTag(mxmlData, currentOffset);
                //workaround for bug in Royale compiler
                Position newPosition = new Position(position.getLine(), position.getCharacter() + 1);
                int newOffset = workspaceFolderManager.getOffsetFromPathAndPosition(path, newPosition, folderData);
                offsetNode = workspaceFolderManager.getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path, newOffset, folderData);
            }
        }
        IIdentifierNode identifierNode = null;
        if (offsetNode instanceof IIdentifierNode)
        {
            identifierNode = (IIdentifierNode) offsetNode;
        }
        if (identifierNode == null)
        {
            return;
        }
        String fileText = workspaceFolderManager.getFileTextForPath(path);
        if(fileText == null)
        {
            return;
        }

        WorkspaceEdit edit = CodeActionsUtils.createWorkspaceEditForGenerateLocalVariable(
            identifierNode, path.toUri().toString(), fileText);
        if(edit == null)
        {
            return;
        }

        CodeAction codeAction = new CodeAction();
        codeAction.setDiagnostics(Collections.singletonList(diagnostic));
        codeAction.setTitle("Generate Local Variable");
        codeAction.setEdit(edit);
        codeAction.setKind(CodeActionKind.QuickFix);
        codeActions.add(Either.forRight(codeAction));
    }

    private void createCodeActionForMissingCatchOrFinally(Path path, Diagnostic diagnostic, WorkspaceFolderData folderData, List<Either<Command, CodeAction>> codeActions)
    {
        RoyaleProject project = folderData.project;
        Position position = diagnostic.getRange().getStart();
        int currentOffset = workspaceFolderManager.getOffsetFromPathAndPosition(path, position, folderData);
        IASNode offsetNode = workspaceFolderManager.getOffsetNode(path, currentOffset, folderData);
        if(!(offsetNode instanceof ITryNode))
        {
            return;
        }
        ITryNode tryNode = (ITryNode) offsetNode;
        String fileText = workspaceFolderManager.getFileTextForPath(path);
        if(fileText == null)
        {
            return;
        }

        WorkspaceEdit edit = CodeActionsUtils.createWorkspaceEditForGenerateCatch(
            tryNode, path.toUri().toString(), fileText, project);
        if(edit == null)
        {
            return;
        }

        CodeAction codeAction = new CodeAction();
        codeAction.setDiagnostics(Collections.singletonList(diagnostic));
        codeAction.setTitle("Generate catch");
        codeAction.setEdit(edit);
        codeAction.setKind(CodeActionKind.QuickFix);
        codeActions.add(Either.forRight(codeAction));
    }

    private void createCodeActionForUnimplementedMethods(Path path, Diagnostic diagnostic, WorkspaceFolderData folderData, List<Either<Command, CodeAction>> codeActions)
    {
        RoyaleProject project = folderData.project;
        Position position = diagnostic.getRange().getStart();
        int currentOffset = workspaceFolderManager.getOffsetFromPathAndPosition(path, position, folderData);
        IASNode offsetNode = workspaceFolderManager.getOffsetNode(path, currentOffset, folderData);
        if (offsetNode == null)
        {
            return;
        }

        IClassNode classNode = (IClassNode) offsetNode.getAncestorOfType(IClassNode.class);
        if (classNode == null)
        {
            return;
        }

        String fileText = workspaceFolderManager.getFileTextForPath(path);
        if(fileText == null)
        {
            return;
        }

        for (IExpressionNode exprNode : classNode.getImplementedInterfaceNodes())
        {
            IInterfaceDefinition interfaceDefinition = (IInterfaceDefinition) exprNode.resolve(project);
            if (interfaceDefinition == null)
            {
                continue;
            }
            WorkspaceEdit edit = CodeActionsUtils.createWorkspaceEditForImplementInterface(
                classNode, interfaceDefinition, path.toUri().toString(), fileText, project);
            if (edit == null)
            {
                continue;
            }

            CodeAction codeAction = new CodeAction();
            codeAction.setDiagnostics(Collections.singletonList(diagnostic));
            codeAction.setTitle("Implement interface '" + interfaceDefinition.getBaseName() + "'");
            codeAction.setEdit(edit);
            codeAction.setKind(CodeActionKind.QuickFix);
            codeActions.add(Either.forRight(codeAction));
        }
    }

    private void createCodeActionForMissingMethod(Path path, Diagnostic diagnostic, WorkspaceFolderData folderData, List<Either<Command, CodeAction>> codeActions)
    {
        RoyaleProject project = folderData.project;
        Position position = diagnostic.getRange().getStart();
        int currentOffset = workspaceFolderManager.getOffsetFromPathAndPosition(path, position, folderData);
        IASNode offsetNode = workspaceFolderManager.getOffsetNode(path, currentOffset, folderData);
        if (offsetNode == null)
        {
            return;
        }
        if (offsetNode instanceof IMXMLInstanceNode)
        {
            MXMLData mxmlData = workspaceFolderManager.getMXMLDataForPath(path, folderData);
            if (mxmlData != null)
            {
                IMXMLTagData offsetTag = MXMLDataUtils.getOffsetMXMLTag(mxmlData, currentOffset);
                //workaround for bug in Royale compiler
                Position newPosition = new Position(position.getLine(), position.getCharacter() + 1);
                int newOffset = workspaceFolderManager.getOffsetFromPathAndPosition(path, newPosition, folderData);
                offsetNode = workspaceFolderManager.getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path, newOffset, folderData);
            }
        }
        IASNode parentNode = offsetNode.getParent();

        IFunctionCallNode functionCallNode = null;
        if (offsetNode instanceof IFunctionCallNode)
        {
            functionCallNode = (IFunctionCallNode) offsetNode;
        }
        else if (parentNode instanceof IFunctionCallNode)
        {
            functionCallNode = (IFunctionCallNode) offsetNode.getParent();
        }
        else if(offsetNode instanceof IIdentifierNode
                && parentNode instanceof IMemberAccessExpressionNode)
        {
            IASNode gpNode = parentNode.getParent();
            if (gpNode instanceof IFunctionCallNode)
            {
                functionCallNode = (IFunctionCallNode) gpNode;
            }
        }
        if (functionCallNode == null)
        {
            return;
        }
        String fileText = workspaceFolderManager.getFileTextForPath(path);
        if(fileText == null)
        {
            return;
        }

        WorkspaceEdit edit = CodeActionsUtils.createWorkspaceEditForGenerateMethod(
            functionCallNode, path.toUri().toString(), fileText, project);
        if(edit == null)
        {
            return;
        }

        CodeAction codeAction = new CodeAction();
        codeAction.setDiagnostics(Collections.singletonList(diagnostic));
        codeAction.setTitle("Generate Method");
        codeAction.setEdit(edit);
        codeAction.setKind(CodeActionKind.QuickFix);
        codeActions.add(Either.forRight(codeAction));
    }

    private void createCodeActionForMissingEventListener(Path path, Diagnostic diagnostic, WorkspaceFolderData folderData, List<Either<Command, CodeAction>> codeActions)
    {
        RoyaleProject project = folderData.project;
        Position position = diagnostic.getRange().getStart();
        int currentOffset = workspaceFolderManager.getOffsetFromPathAndPosition(path, position, folderData);
        IASNode offsetNode = workspaceFolderManager.getOffsetNode(path, currentOffset, folderData);
        if (offsetNode == null)
        {
            return;
        }
        if (offsetNode instanceof IMXMLInstanceNode)
        {
            MXMLData mxmlData = workspaceFolderManager.getMXMLDataForPath(path, folderData);
            if (mxmlData != null)
            {
                IMXMLTagData offsetTag = MXMLDataUtils.getOffsetMXMLTag(mxmlData, currentOffset);
                //workaround for bug in Royale compiler
                Position newPosition = new Position(position.getLine(), position.getCharacter() + 1);
                int newOffset = workspaceFolderManager.getOffsetFromPathAndPosition(path, newPosition, folderData);
                offsetNode = workspaceFolderManager.getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path, newOffset, folderData);
            }
        }
        if (!(offsetNode instanceof IIdentifierNode))
        {
            return;
        }
        IASNode parentNode = offsetNode.getParent();
        if (parentNode instanceof IMemberAccessExpressionNode)
        {
            IMemberAccessExpressionNode memberAccessExpressionNode = (IMemberAccessExpressionNode) parentNode;
            IExpressionNode leftOperandNode = memberAccessExpressionNode.getLeftOperandNode();
            IExpressionNode rightOperandNode = memberAccessExpressionNode.getRightOperandNode();
            if (rightOperandNode instanceof IIdentifierNode
                    && leftOperandNode instanceof ILanguageIdentifierNode)
            {
                ILanguageIdentifierNode leftIdentifierNode = (ILanguageIdentifierNode) leftOperandNode;
                if (leftIdentifierNode.getKind() == ILanguageIdentifierNode.LanguageIdentifierKind.THIS)
                {
                    parentNode = parentNode.getParent();
                }
            }
        }
        if (!(parentNode instanceof IContainerNode))
        {
            return;
        }

        IASNode gpNode = parentNode.getParent();
        if (!(gpNode instanceof IFunctionCallNode))
        {
            return;
        }

        IFunctionCallNode functionCallNode = (IFunctionCallNode) gpNode;
        if(!ASTUtils.isFunctionCallWithName(functionCallNode, "addEventListener"))
        {
            return;
        }

        IExpressionNode[] args = functionCallNode.getArgumentNodes();
        if (args.length < 2 || (args[1] != offsetNode && args[1] != offsetNode.getParent()))
        {
            return;
        }

        String eventTypeClassName = ASTUtils.findEventClassNameFromAddEventListenerFunctionCall(functionCallNode, project);
        if (eventTypeClassName == null)
        {
            return;
        }

        IIdentifierNode functionIdentifier = (IIdentifierNode) offsetNode;
        String functionName = functionIdentifier.getName();
        if (functionName.length() == 0)
        {
            return;
        }

        String fileText = workspaceFolderManager.getFileTextForPath(path);
        if(fileText == null)
        {
            return;
        }

        WorkspaceEdit edit = CodeActionsUtils.createWorkspaceEditForGenerateEventListener(
            functionIdentifier, functionName, eventTypeClassName,
            path.toUri().toString(), fileText, project);
        if(edit == null)
        {
            return;
        }

        CodeAction codeAction = new CodeAction();
        codeAction.setDiagnostics(Collections.singletonList(diagnostic));
        codeAction.setTitle("Generate Event Listener");
        codeAction.setEdit(edit);
        codeAction.setKind(CodeActionKind.QuickFix);
        codeActions.add(Either.forRight(codeAction));
    }

    private void createCodeActionsForImport(Path path, Diagnostic diagnostic, WorkspaceFolderData folderData, List<Either<Command, CodeAction>> codeActions)
    {
        RoyaleProject project = folderData.project;
        Position position = diagnostic.getRange().getStart();
        int currentOffset = workspaceFolderManager.getOffsetFromPathAndPosition(path, position, folderData);
        IASNode offsetNode = workspaceFolderManager.getOffsetNode(path, currentOffset, folderData);
        IMXMLTagData offsetTag = null;
        boolean isMXML = path.toUri().toString().endsWith(MXML_EXTENSION);
        if (isMXML)
        {
            MXMLData mxmlData = workspaceFolderManager.getMXMLDataForPath(path, folderData);
            if (mxmlData != null)
            {
                offsetTag = MXMLDataUtils.getOffsetMXMLTag(mxmlData, currentOffset);
            }
        }
        if (offsetNode instanceof IMXMLInstanceNode && offsetTag != null)
        {
            //workaround for bug in Royale compiler
            Position newPosition = new Position(position.getLine(), position.getCharacter() + 1);
            int newOffset = workspaceFolderManager.getOffsetFromPathAndPosition(path, newPosition, folderData);
            offsetNode = workspaceFolderManager.getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path, newOffset, folderData);
        }
        if (offsetNode == null || !(offsetNode instanceof IIdentifierNode))
        {
            return;
        }
        ImportRange importRange = null;
        if (offsetTag != null)
        {
            importRange = ImportRange.fromOffsetTag(offsetTag, currentOffset);
        }
        else
        {
            importRange = ImportRange.fromOffsetNode(offsetNode);
        }
        String uri = importRange.uri;
        String fileText = workspaceFolderManager.getFileTextForPath(path);
        if(fileText == null)
        {
            return;
        }

        IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
        String typeString = identifierNode.getName();

        List<IDefinition> types = ASTUtils.findTypesThatMatchName(typeString, project.getCompilationUnits());
        for (IDefinition definitionToImport : types)
        {
            WorkspaceEdit edit = CodeActionsUtils.createWorkspaceEditForAddImport(definitionToImport, fileText, uri, importRange);
            if (edit == null)
            {
                continue;
            }
            CodeAction codeAction = new CodeAction();
            codeAction.setTitle("Import " + definitionToImport.getQualifiedName());
            codeAction.setEdit(edit);
            codeAction.setKind(CodeActionKind.QuickFix);
            codeAction.setDiagnostics(Collections.singletonList(diagnostic));
            codeActions.add(Either.forRight(codeAction));
        }
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
                RenameProvider provider = new RenameProvider(workspaceFolderManager);
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
        workspaceFolderManager.sourceByPath.put(path, text);

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

        //notify the workspace that it should read the file from memory
        //instead of loading from the file system
        String normalizedPath = FilenameNormalization.normalize(path.toAbsolutePath().toString());
        IFileSpecification fileSpec = workspaceFolderManager.getFileSpecification(normalizedPath);
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
        for (TextDocumentContentChangeEvent change : params.getContentChanges())
        {
            if (change.getRange() == null)
            {
                workspaceFolderManager.sourceByPath.put(path, change.getText());
            }
            else if(workspaceFolderManager.sourceByPath.containsKey(path))
            {
                String existingText = workspaceFolderManager.sourceByPath.get(path);
                String newText = patch(existingText, change);
                workspaceFolderManager.sourceByPath.put(path, newText);
            }
            else
            {
                System.err.println("Failed to apply changes to code intelligence from URI: " + textDocumentUri);
            }
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

        IFileSpecification fileSpec = workspaceFolderManager.getFileSpecification(normalizedChangedPathAsString);
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

        workspaceFolderManager.sourceByPath.remove(path);

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
        }

        //if it's an included file, switch to the parent file
        IncludeFileData includeFileData = folderData.includedFiles.get(path.toString());
        if (includeFileData != null)
        {
            path = Paths.get(includeFileData.parentPath);
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
                    IFileSpecification swcFileSpec = workspaceFolderManager.getFileSpecification(normalizedChangedPathAsString);
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
                    IFileSpecification fileSpec = workspaceFolderManager.getFileSpecification(normalizedChangedPathAsString);
                    compilerWorkspace.fileRemoved(fileSpec);
                    //deleting a file may change errors in other existing files,
                    //so we need to do a full check
                    foldersToCheck.addAll(allFolderData);
                }
                else if (event.getType().equals(FileChangeType.Created))
                {
                    IFileSpecification fileSpec = workspaceFolderManager.getFileSpecification(normalizedChangedPathAsString);
                    compilerWorkspace.fileAdded(fileSpec);
                    //creating a file may change errors in other existing files,
                    //so we need to do a full check
                    foldersToCheck.addAll(allFolderData);
                }
                else if (changeType.equals(FileChangeType.Changed))
                {
                    IFileSpecification fileSpec = workspaceFolderManager.getFileSpecification(normalizedChangedPathAsString);
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
                                IFileSpecification fileSpec = workspaceFolderManager.getFileSpecification(normalizedSubPath);
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
                    IFileSpecification fileSpec = workspaceFolderManager.getFileSpecification(fileToRemove);
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

    private String patch(String sourceText, TextDocumentContentChangeEvent change)
    {
        Range range = change.getRange();
        Position start = range.getStart();
        StringReader reader = new StringReader(sourceText);
        int offset = LanguageServerCompilerUtils.getOffsetFromPosition(reader, start);
        StringBuilder builder = new StringBuilder();
        builder.append(sourceText.substring(0, offset));
        builder.append(change.getText());
        builder.append(sourceText.substring(offset + change.getRangeLength()));
        return builder.toString();
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
        Reader reader = workspaceFolderManager.getReaderForPath(path);
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
            for(Path filePath : workspaceFolderManager.sourceByPath.keySet())
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
        ProblemQuery problemQuery = workspaceFolderDataToProblemQuery(folderData);
        checkFilePathForProblems(path, problemQuery, folderData, quick);
        publishDiagnosticsForProblemQuery(problemQuery, folderData.codeProblemTracker, folderData, !quick);
    }

    private void checkFilePathForProblems(Path path, ProblemQuery problemQuery, WorkspaceFolderData folderData, boolean quick)
    {
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
        diagnostic.setMessage(path.getFileName() + " is not located in the project's source path. Code intelligence will not be available for this file.");
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
