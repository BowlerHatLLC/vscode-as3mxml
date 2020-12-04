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
package com.as3mxml.vscode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import com.as3mxml.asconfigc.ASConfigC;
import com.as3mxml.asconfigc.ASConfigCException;
import com.as3mxml.asconfigc.ASConfigCOptions;
import com.as3mxml.asconfigc.compiler.ProjectType;
import com.as3mxml.vscode.asdoc.VSCodeASDocDelegate;
import com.as3mxml.vscode.commands.ICommandConstants;
import com.as3mxml.vscode.compiler.CompilerShell;
import com.as3mxml.vscode.compiler.problems.LSPFileNotFoundProblem;
import com.as3mxml.vscode.compiler.problems.SyntaxFallbackProblem;
import com.as3mxml.vscode.project.ILspProject;
import com.as3mxml.vscode.project.IProjectConfigStrategy;
import com.as3mxml.vscode.project.IProjectConfigStrategyFactory;
import com.as3mxml.vscode.project.ProjectOptions;
import com.as3mxml.vscode.project.SimpleProjectConfigStrategy;
import com.as3mxml.vscode.project.ActionScriptProjectData;
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
import com.as3mxml.vscode.utils.ASTUtils;
import com.as3mxml.vscode.utils.ActionScriptSDKUtils;
import com.as3mxml.vscode.utils.CompilationUnitUtils;
import com.as3mxml.vscode.utils.CompilationUnitUtils.IncludeFileData;
import com.as3mxml.vscode.utils.CompilerProblemFilter;
import com.as3mxml.vscode.utils.CompilerProjectUtils;
import com.as3mxml.vscode.utils.FileTracker;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;
import com.as3mxml.vscode.utils.ProblemTracker;
import com.as3mxml.vscode.utils.RealTimeProblemsChecker;
import com.as3mxml.vscode.utils.ActionScriptProjectManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.apache.royale.compiler.clients.problems.ProblemQuery;
import org.apache.royale.compiler.config.CommandLineConfigurator;
import org.apache.royale.compiler.config.Configuration;
import org.apache.royale.compiler.config.ICompilerProblemSettings;
import org.apache.royale.compiler.config.ICompilerSettingsConstants;
import org.apache.royale.compiler.exceptions.ConfigurationException;
import org.apache.royale.compiler.filespecs.IFileSpecification;
import org.apache.royale.compiler.internal.parsing.as.ASParser;
import org.apache.royale.compiler.internal.parsing.as.ASToken;
import org.apache.royale.compiler.internal.parsing.as.RepairingTokenBuffer;
import org.apache.royale.compiler.internal.parsing.as.StreamingASTokenizer;
import org.apache.royale.compiler.internal.projects.RoyaleProjectConfigurator;
import org.apache.royale.compiler.internal.targets.Target;
import org.apache.royale.compiler.internal.tree.as.FileNode;
import org.apache.royale.compiler.internal.workspaces.Workspace;
import org.apache.royale.compiler.problems.ConfigurationProblem;
import org.apache.royale.compiler.problems.FileNotFoundProblem;
import org.apache.royale.compiler.problems.ICompilerProblem;
import org.apache.royale.compiler.problems.InternalCompilerProblem;
import org.apache.royale.compiler.problems.MissingRequirementConfigurationProblem;
import org.apache.royale.compiler.targets.ITarget;
import org.apache.royale.compiler.targets.ITargetSettings;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.apache.royale.compiler.units.ICompilationUnit.UnitType;
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
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.ImplementationParams;
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
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.TypeDefinitionParams;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
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
public class ActionScriptServices implements TextDocumentService, WorkspaceService {
    private static final String FILE_EXTENSION_MXML = ".mxml";
    private static final String FILE_EXTENSION_AS = ".as";
    private static final String FILE_EXTENSION_SWC = ".swc";
    private static final String PROPERTY_FRAMEWORK_LIB = "royalelib";
    private static final String ROYALE_ASJS_RELATIVE_PATH_CHILD = "./royale-asjs";
    private static final String FRAMEWORKS_RELATIVE_PATH_CHILD = "./frameworks";
    private static final String SOURCE_DEFAULTS = "defaults";
    private static final String SOURCE_CONFIG = "config.as";

    private ActionScriptLanguageClient languageClient;
    private String oldFrameworkSDKPath;
    private Workspace compilerWorkspace;
    private ActionScriptProjectManager actionScriptProjectManager;
    private WatchService sourcePathWatcher;
    private Thread sourcePathWatcherThread;
    private ClientCapabilities clientCapabilities;
    private boolean completionSupportsSnippets = false;
    private FileTracker fileTracker;
    private CompilerProblemFilter compilerProblemFilter = new CompilerProblemFilter();
    private boolean initialized = false;
    private boolean frameworkSDKIsRoyale = false;
    private boolean frameworkSDKIsFallback = false;
    private RealTimeProblemsChecker realTimeProblemsChecker;
    private Future<?> realTimeProblemsFuture;
    private Set<URI> notOnSourcePathSet = new HashSet<>();
    private boolean realTimeProblems = true;
    private boolean showFileOutsideSourcePath = true;
    private boolean concurrentRequests = true;
    private SimpleProjectConfigStrategy fallbackConfig;
    private CompilerShell compilerShell;
    private String jvmargs;

    public ActionScriptServices(IProjectConfigStrategyFactory factory) {
        compilerWorkspace = new Workspace();
        compilerWorkspace.setASDocDelegate(new VSCodeASDocDelegate(compilerWorkspace));
        fileTracker = new FileTracker(compilerWorkspace);
        actionScriptProjectManager = new ActionScriptProjectManager(fileTracker, factory,
                (projectData) -> onAddProject(projectData), (projectData) -> onRemoveProject(projectData));
        updateFrameworkSDK();
    }

    public void addWorkspaceFolder(WorkspaceFolder folder) {
        actionScriptProjectManager.addWorkspaceFolder(folder);
    }

    private boolean onAddProject(ActionScriptProjectData projectData) {
        //let's get the code intelligence up and running!
        Path path = getMainCompilationUnitPath(projectData);
        if (path != null) {
            String normalizedPath = FilenameNormalization.normalize(path.toAbsolutePath().toString());
            IFileSpecification fileSpec = fileTracker.getFileSpecification(normalizedPath);
            compilerWorkspace.fileChanged(fileSpec);
        }

        checkProjectForProblems(projectData);
        return true;
    }

    private boolean onRemoveProject(ActionScriptProjectData projectData) {
        return true;
    }

    public void removeWorkspaceFolder(WorkspaceFolder folder) {
        actionScriptProjectManager.removeWorkspaceFolder(folder);
    }

    public ClientCapabilities getClientCapabilities() {
        return clientCapabilities;
    }

    public void setClientCapabilities(ClientCapabilities value) {
        completionSupportsSnippets = false;

        clientCapabilities = value;
        TextDocumentClientCapabilities textDocument = clientCapabilities.getTextDocument();
        if (textDocument != null) {
            CompletionCapabilities completion = textDocument.getCompletion();
            if (completion != null) {
                CompletionItemCapabilities completionItem = completion.getCompletionItem();
                if (completionItem != null) {
                    completionSupportsSnippets = completionItem.getSnippetSupport();
                }
            }
        }
    }

    public void setLanguageClient(ActionScriptLanguageClient value) {
        languageClient = value;
        actionScriptProjectManager.setLanguageClient(languageClient);
    }

    public void shutdown() {
        if (compilerShell != null) {
            compilerShell.dispose();
            compilerShell = null;
        }
        if (realTimeProblemsFuture != null) {
            realTimeProblemsChecker.clear();
            realTimeProblemsChecker = null;
            realTimeProblemsFuture.cancel(true);
            realTimeProblemsFuture = null;
        }
    }

    /**
     * Returns a list of all items to display in the completion list at a
     * specific position in a document. Called automatically by VSCode as the
     * user types, and may not necessarily be triggered only on "." or ":".
     */
    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        if (!concurrentRequests) {
            return CompletableFuture.completedFuture(completion2(params, null));
        }
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken -> {
            cancelToken.checkCanceled();
            return completion2(params, cancelToken);
        });
    }

    private Either<List<CompletionItem>, CompletionList> completion2(CompletionParams params,
            CancelChecker cancelToken) {
        //make sure that the latest changes have been passed to
        //workspace.fileChanged() before proceeding
        if (realTimeProblemsChecker != null) {
            realTimeProblemsChecker.updateNow();
        }

        compilerWorkspace.startBuilding();
        try {
            CompletionProvider provider = new CompletionProvider(actionScriptProjectManager, fileTracker,
                    completionSupportsSnippets, frameworkSDKIsRoyale);
            return provider.completion(params, cancelToken);
        } finally {
            compilerWorkspace.doneBuilding();
        }
    }

    /**
     * This function is never called. We resolve completion items immediately
     * in completion() instead of requiring a separate step.
     */
    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
        return CompletableFuture.completedFuture(new CompletionItem());
    }

    /**
     * Returns information to display in a tooltip when the mouse hovers over
     * something in a text document.
     */
    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        if (!concurrentRequests) {
            return CompletableFuture.completedFuture(hover2(params, null));
        }
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken -> {
            cancelToken.checkCanceled();
            return hover2(params, cancelToken);
        });
    }

    private Hover hover2(HoverParams params, CancelChecker cancelToken) {
        //make sure that the latest changes have been passed to
        //workspace.fileChanged() before proceeding
        if (realTimeProblemsChecker != null) {
            realTimeProblemsChecker.updateNow();
        }

        compilerWorkspace.startBuilding();
        try {
            HoverProvider provider = new HoverProvider(actionScriptProjectManager, fileTracker);
            return provider.hover(params, cancelToken);
        } finally {
            compilerWorkspace.doneBuilding();
        }
    }

    /**
     * Displays a function's parameters, including which one is currently
     * active. Called automatically by VSCode any time that the user types "(",
     * so be sure to check that a function call is actually happening at the
     * current position.
     */
    @Override
    public CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params) {
        if (!concurrentRequests) {
            return CompletableFuture.completedFuture(signatureHelp2(params, null));
        }
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken -> {
            cancelToken.checkCanceled();
            return signatureHelp2(params, cancelToken);
        });
    }

    private SignatureHelp signatureHelp2(SignatureHelpParams params, CancelChecker cancelToken) {
        //make sure that the latest changes have been passed to
        //workspace.fileChanged() before proceeding
        if (realTimeProblemsChecker != null) {
            realTimeProblemsChecker.updateNow();
        }

        compilerWorkspace.startBuilding();
        try {
            SignatureHelpProvider provider = new SignatureHelpProvider(actionScriptProjectManager, fileTracker);
            return provider.signatureHelp(params, cancelToken);
        } finally {
            compilerWorkspace.doneBuilding();
        }
    }

    /**
     * Finds where the definition referenced at the current position in a text
     * document is defined.
     */
    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
            DefinitionParams params) {
        if (!concurrentRequests) {
            return CompletableFuture.completedFuture(definition2(params, null));
        }
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken -> {
            cancelToken.checkCanceled();
            return definition2(params, cancelToken);
        });
    }

    private Either<List<? extends Location>, List<? extends LocationLink>> definition2(DefinitionParams params,
            CancelChecker cancelToken) {
        //make sure that the latest changes have been passed to
        //workspace.fileChanged() before proceeding
        if (realTimeProblemsChecker != null) {
            realTimeProblemsChecker.updateNow();
        }

        compilerWorkspace.startBuilding();
        try {
            DefinitionProvider provider = new DefinitionProvider(actionScriptProjectManager, fileTracker);
            return provider.definition(params, cancelToken);
        } finally {
            compilerWorkspace.doneBuilding();
        }
    }

    /**
     * Finds where the type of the definition referenced at the current position
     * in a text document is defined.
     */
    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> typeDefinition(
            TypeDefinitionParams params) {
        if (!concurrentRequests) {
            return CompletableFuture.completedFuture(typeDefinition2(params, null));
        }
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken -> {
            cancelToken.checkCanceled();
            return typeDefinition2(params, cancelToken);
        });
    }

    private Either<List<? extends Location>, List<? extends LocationLink>> typeDefinition2(TypeDefinitionParams params,
            CancelChecker cancelToken) {
        //make sure that the latest changes have been passed to
        //workspace.fileChanged() before proceeding
        if (realTimeProblemsChecker != null) {
            realTimeProblemsChecker.updateNow();
        }

        compilerWorkspace.startBuilding();
        try {
            TypeDefinitionProvider provider = new TypeDefinitionProvider(actionScriptProjectManager, fileTracker);
            return provider.typeDefinition(params, cancelToken);
        } finally {
            compilerWorkspace.doneBuilding();
        }
    }

    /**
     * Finds all implemenations of an interface.
     */
    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(
            ImplementationParams params) {
        if (!concurrentRequests) {
            return CompletableFuture.completedFuture(implementation2(params, null));
        }
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken -> {
            cancelToken.checkCanceled();
            return implementation2(params, cancelToken);
        });
    }

    private Either<List<? extends Location>, List<? extends LocationLink>> implementation2(ImplementationParams params,
            CancelChecker cancelToken) {
        //make sure that the latest changes have been passed to
        //workspace.fileChanged() before proceeding
        if (realTimeProblemsChecker != null) {
            realTimeProblemsChecker.updateNow();
        }

        compilerWorkspace.startBuilding();
        try {
            ImplementationProvider provider = new ImplementationProvider(actionScriptProjectManager, fileTracker);
            return provider.implementation(params, cancelToken);
        } finally {
            compilerWorkspace.doneBuilding();
        }
    }

    /**
     * Finds all references of the definition referenced at the current position
     * in a text document. Does not necessarily get called where a definition is
     * defined, but may be at one of the references.
     */
    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        if (!concurrentRequests) {
            return CompletableFuture.completedFuture(references2(params, null));
        }
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken -> {
            cancelToken.checkCanceled();
            return references2(params, cancelToken);
        });
    }

    private List<? extends Location> references2(ReferenceParams params, CancelChecker cancelToken) {
        //make sure that the latest changes have been passed to
        //workspace.fileChanged() before proceeding
        if (realTimeProblemsChecker != null) {
            realTimeProblemsChecker.updateNow();
        }

        compilerWorkspace.startBuilding();
        try {
            ReferencesProvider provider = new ReferencesProvider(actionScriptProjectManager, fileTracker);
            return provider.references(params, cancelToken);
        } finally {
            compilerWorkspace.doneBuilding();
        }
    }

    /**
     * This feature is not implemented at this time.
     */
    @Override
    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(DocumentHighlightParams params) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    /**
     * Searches by name for a symbol in the workspace.
     */
    @Override
    public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
        if (!concurrentRequests) {
            return CompletableFuture.completedFuture(symbol2(params, null));
        }
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken -> {
            cancelToken.checkCanceled();
            return symbol2(params, cancelToken);
        });
    }

    private List<? extends SymbolInformation> symbol2(WorkspaceSymbolParams params, CancelChecker cancelToken) {
        //make sure that the latest changes have been passed to
        //workspace.fileChanged() before proceeding
        if (realTimeProblemsChecker != null) {
            realTimeProblemsChecker.updateNow();
        }

        compilerWorkspace.startBuilding();
        try {
            WorkspaceSymbolProvider provider = new WorkspaceSymbolProvider(actionScriptProjectManager);
            return provider.workspaceSymbol(params, cancelToken);
        } finally {
            compilerWorkspace.doneBuilding();
        }
    }

    /**
     * Searches by name for a symbol in a specific document (not the whole
     * workspace)
     */
    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
            DocumentSymbolParams params) {
        if (!concurrentRequests) {
            return CompletableFuture.completedFuture(documentSymbol2(params, null));
        }
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken -> {
            cancelToken.checkCanceled();
            return documentSymbol2(params, cancelToken);
        });
    }

    private List<Either<SymbolInformation, DocumentSymbol>> documentSymbol2(DocumentSymbolParams params,
            CancelChecker cancelToken) {
        //make sure that the latest changes have been passed to
        //workspace.fileChanged() before proceeding
        if (realTimeProblemsChecker != null) {
            realTimeProblemsChecker.updateNow();
        }

        compilerWorkspace.startBuilding();
        try {
            boolean hierarchicalDocumentSymbolSupport = false;
            try {
                hierarchicalDocumentSymbolSupport = clientCapabilities.getTextDocument().getDocumentSymbol()
                        .getHierarchicalDocumentSymbolSupport();
            } catch (NullPointerException e) {
                //ignore
            }
            DocumentSymbolProvider provider = new DocumentSymbolProvider(actionScriptProjectManager,
                    hierarchicalDocumentSymbolSupport);
            return provider.documentSymbol(params, cancelToken);
        } finally {
            compilerWorkspace.doneBuilding();
        }
    }

    /**
     * Can be used to "quick fix" an error or warning.
     */
    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        if (!concurrentRequests) {
            return CompletableFuture.completedFuture(codeAction2(params, null));
        }
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken -> {
            cancelToken.checkCanceled();
            return codeAction2(params, cancelToken);
        });
    }

    private List<Either<Command, CodeAction>> codeAction2(CodeActionParams params, CancelChecker cancelToken) {
        //make sure that the latest changes have been passed to
        //workspace.fileChanged() before proceeding
        if (realTimeProblemsChecker != null) {
            realTimeProblemsChecker.updateNow();
        }

        compilerWorkspace.startBuilding();
        try {
            CodeActionProvider provider = new CodeActionProvider(actionScriptProjectManager, fileTracker);
            return provider.codeAction(params, cancelToken);
        } finally {
            compilerWorkspace.doneBuilding();
        }
    }

    /**
     * This feature is not implemented at this time.
     */
    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    /**
     * This feature is not implemented at this time.
     */
    @Override
    public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
        return CompletableFuture.completedFuture(new CodeLens());
    }

    /**
     * This feature is not implemented at this time.
     */
    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    /**
     * This feature is not implemented at this time.
     */
    @Override
    public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    /**
     * This feature is not implemented at this time.
     */
    @Override
    public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    /**
     * Renames a symbol at the specified document position.
     */
    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        if (!concurrentRequests) {
            return CompletableFuture.completedFuture(rename2(params, null));
        }
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken -> {
            cancelToken.checkCanceled();
            return rename2(params, cancelToken);
        });
    }

    private WorkspaceEdit rename2(RenameParams params, CancelChecker cancelToken) {
        //make sure that the latest changes have been passed to
        //workspace.fileChanged() before proceeding
        if (realTimeProblemsChecker != null) {
            realTimeProblemsChecker.updateNow();
        }

        compilerWorkspace.startBuilding();
        try {
            RenameProvider provider = new RenameProvider(actionScriptProjectManager, fileTracker);
            WorkspaceEdit result = provider.rename(params, cancelToken);
            if (result == null) {
                if (languageClient != null) {
                    MessageParams message = new MessageParams();
                    message.setType(MessageType.Info);
                    message.setMessage("You cannot rename this element.");
                    languageClient.showMessage(message);
                }
                return new WorkspaceEdit(new HashMap<>());
            }
            return result;
        } finally {
            compilerWorkspace.doneBuilding();
        }
    }

    /**
     * Called when one of the commands registered in ActionScriptLanguageServer
     * is executed.
     */
    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        if (params.getCommand().equals(ICommandConstants.QUICK_COMPILE)) {
            return executeQuickCompileCommand(params);
        }
        ExecuteCommandProvider provider = new ExecuteCommandProvider(actionScriptProjectManager, fileTracker,
                compilerWorkspace, languageClient, concurrentRequests);
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
    public void didOpen(DidOpenTextDocumentParams params) {
        TextDocumentItem textDocument = params.getTextDocument();
        String textDocumentUri = textDocument.getUri();
        if (!textDocumentUri.endsWith(FILE_EXTENSION_AS) && !textDocumentUri.endsWith(FILE_EXTENSION_MXML)) {
            //code intelligence is available only in .as and .mxml files
            //so we ignore other file extensions
            return;
        }
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocumentUri);
        if (path == null) {
            return;
        }

        //even if it's not in a workspace folder right now, store it just in
        //case we need it later.
        //example: if we modify to source-path compiler option
        String text = textDocument.getText();
        fileTracker.openFile(path, text);

        ActionScriptProjectData projectData = actionScriptProjectManager.getProjectDataForSourceFile(path);
        if (projectData == null) {
            return;
        }

        if (fallbackConfig != null && projectData.equals(actionScriptProjectManager.getFallbackProjectData())) {
            fallbackConfig.didOpen(path);
        }

        getProject(projectData);
        ILspProject project = projectData.project;
        if (project == null) {
            //something went wrong while creating the project
            return;
        }

        //notify the workspace that it should read the file from memory
        //instead of loading from the file system
        String normalizedPath = FilenameNormalization.normalize(path.toAbsolutePath().toString());
        IFileSpecification fileSpec = fileTracker.getFileSpecification(normalizedPath);
        compilerWorkspace.fileChanged(fileSpec);

        //if it's an included file, switch to the parent file
        IncludeFileData includeFileData = projectData.includedFiles.get(path.toString());
        if (includeFileData != null) {
            path = Paths.get(includeFileData.parentPath);
        }

        checkProjectForProblems(projectData);
    }

    /**
     * Called when a change is made to a file open for editing in Visual Studio
     * Code. Receives incremental changes that need to be applied to the
     * in-memory String that we store for this file.
     */
    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        VersionedTextDocumentIdentifier textDocument = params.getTextDocument();
        String textDocumentUri = textDocument.getUri();
        if (!textDocumentUri.endsWith(FILE_EXTENSION_AS) && !textDocumentUri.endsWith(FILE_EXTENSION_MXML)) {
            //code intelligence is available only in .as and .mxml files
            //so we ignore other file extensions
            return;
        }
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocumentUri);
        if (path == null) {
            return;
        }
        fileTracker.changeFile(path, params.getContentChanges());

        ActionScriptProjectData projectData = actionScriptProjectManager.getProjectDataForSourceFile(path);
        if (projectData == null) {
            return;
        }

        getProject(projectData);
        ILspProject project = projectData.project;
        if (project == null) {
            //something went wrong while creating the project
            return;
        }

        String normalizedChangedPathAsString = FilenameNormalization.normalize(path.toAbsolutePath().toString());
        IFileSpecification fileSpec = fileTracker.getFileSpecification(normalizedChangedPathAsString);

        //if we're checking a compilation unit for problems in real time, and
        //the path of this new change is the same, we'll re-check for problems
        //when its done
        //this is the fastest way to check for problems while the user is typing

        if (realTimeProblems && realTimeProblemsChecker != null) {
            synchronized (realTimeProblemsChecker) {
                IFileSpecification otherFileSpec = realTimeProblemsChecker.getFileSpecification();
                if (otherFileSpec != null && otherFileSpec.getPath().equals(normalizedChangedPathAsString)) {
                    realTimeProblemsChecker.setFileSpecification(fileSpec);
                    return;
                }
            }
        }

        ICompilationUnit unit = null;
        compilerWorkspace.startBuilding();
        try {
            //if it's an included file, switch to the parent file
            IncludeFileData includeFileData = projectData.includedFiles.get(path.toString());
            if (includeFileData != null) {
                path = Paths.get(includeFileData.parentPath);
            }

            //we need the compilation unit at this point
            unit = CompilerProjectUtils.findCompilationUnit(path, project);
        } finally {
            compilerWorkspace.doneBuilding();
        }

        compilerWorkspace.fileChanged(fileSpec);

        if (unit == null) {
            //we don't have a compilation unit for this yet, but if we check the
            //entire project, it should be created (or we'll fall back to simple
            //syntax checking)
            checkProjectForProblems(projectData);
        } else if (realTimeProblems) {
            if (realTimeProblemsChecker == null) {
                realTimeProblemsChecker = new RealTimeProblemsChecker(languageClient, compilerProblemFilter);
                realTimeProblemsFuture = compilerWorkspace.getExecutorService().submit(realTimeProblemsChecker);
            }
            if (projectData.equals(actionScriptProjectManager.getFallbackProjectData())) {
                realTimeProblemsChecker.clear();
            } else {
                realTimeProblemsChecker.setCompilationUnit(unit, fileSpec, projectData);
            }
        } else if (realTimeProblemsFuture != null) {
            realTimeProblemsChecker.clear();
            realTimeProblemsChecker = null;
            realTimeProblemsFuture.cancel(true);
            realTimeProblemsFuture = null;
        }
    }

    /**
     * Called when a file is closed in Visual Studio Code. We should no longer
     * store the file as a String, and we can load the contents from the file
     * system.
     */
    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        TextDocumentIdentifier textDocument = params.getTextDocument();
        String textDocumentUri = textDocument.getUri();
        if (!textDocumentUri.endsWith(FILE_EXTENSION_AS) && !textDocumentUri.endsWith(FILE_EXTENSION_MXML)) {
            //code intelligence is available only in .as and .mxml files
            //so we ignore other file extensions
            return;
        }
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocumentUri);
        if (path == null) {
            return;
        }

        fileTracker.closeFile(path);

        boolean clearProblems = false;

        ActionScriptProjectData projectData = actionScriptProjectManager.getProjectDataForSourceFile(path);
        if (projectData == null) {
            //if we can't figure out which workspace the file is in, then clear
            //the problems completely because we want to display problems only
            //while it is open
            clearProblems = true;
        } else {
            if (fallbackConfig != null && projectData.equals(actionScriptProjectManager.getFallbackProjectData())) {
                fallbackConfig.didClose(path);
                clearProblems = true;
            }

            getProject(projectData);
            ILspProject project = projectData.project;
            URI uri = path.toUri();
            if (project == null) {
                //if the current project isn't properly configured, we want to
                //display problems only while a file is open
                clearProblems = true;
            } else if (notOnSourcePathSet.contains(uri)) {
                //if the file is outside of the project's source path, we want
                //to display problems only while it is open
                clearProblems = true;
                notOnSourcePathSet.remove(uri);
            }

            //if it's an included file, switch to the parent file
            IncludeFileData includeFileData = projectData.includedFiles.get(path.toString());
            if (includeFileData != null) {
                path = Paths.get(includeFileData.parentPath);
            }
        }

        if (clearProblems) {
            //immediately clear any diagnostics published for this file
            clearProblemsForURI(path.toUri());
            return;
        }

        //the contents of the file may have been modified, and then reverted
        //without saving changes, so re-check for errors with the file system
        //version of the file
        checkProjectForProblems(projectData);
    }

    /**
     * Called when a file being edited is saved.
     */
    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        if (realTimeProblems) {
            //as long as we're checking on change, we shouldn't need to do
            //anything on save because we should already have the correct state
            return;
        }

        TextDocumentIdentifier textDocument = params.getTextDocument();
        String textDocumentUri = textDocument.getUri();
        if (!textDocumentUri.endsWith(FILE_EXTENSION_AS) && !textDocumentUri.endsWith(FILE_EXTENSION_MXML)) {
            //code intelligence is available only in .as and .mxml files
            //so we ignore other file extensions
            return;
        }
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocumentUri);
        if (path == null) {
            return;
        }
        ActionScriptProjectData projectData = actionScriptProjectManager.getProjectDataForSourceFile(path);
        if (projectData == null) {
            return;
        }
        getProject(projectData);
        ILspProject project = projectData.project;
        if (project == null) {
            //something went wrong while creating the project
            return;
        }

        //if it's an included file, switch to the parent file
        IncludeFileData includeFileData = projectData.includedFiles.get(path.toString());
        if (includeFileData != null) {
            path = Paths.get(includeFileData.parentPath);
        }

        checkProjectForProblems(projectData);
    }

    /**
     * Called when certain files in the workspace are added, removed, or
     * changed, even if they are not considered open for editing. Also checks if
     * the project configuration strategy has changed. If it has, checks for
     * errors on the whole project.
     */
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        Set<ActionScriptProjectData> foldersToCheck = new HashSet<>();

        for (FileEvent event : params.getChanges()) {
            Path changedPath = LanguageServerCompilerUtils.getPathFromLanguageServerURI(event.getUri());
            if (changedPath == null) {
                continue;
            }

            //first check if any project's config file has changed
            for (ActionScriptProjectData projectData : actionScriptProjectManager.getAllProjectData()) {
                IProjectConfigStrategy config = projectData.config;
                if (changedPath.equals(config.getConfigFilePath())) {
                    config.forceChanged();
                    foldersToCheck.add(projectData);
                }
            }

            //then, check if source or library files have changed
            FileChangeType changeType = event.getType();
            String normalizedChangedPathAsString = FilenameNormalization
                    .normalize(changedPath.toAbsolutePath().toString());
            if (normalizedChangedPathAsString.endsWith(FILE_EXTENSION_SWC)) {
                List<ActionScriptProjectData> allProjectData = actionScriptProjectManager
                        .getAllProjectDataForSWCFile(changedPath);
                if (allProjectData.size() > 0) {
                    //for some reason, simply calling fileAdded(),
                    //fileRemoved(), or fileChanged() doesn't work properly for
                    //SWC files.
                    //changing the project configuration will force the
                    //change to be detected, so let's do that manually.
                    for (ActionScriptProjectData projectData : allProjectData) {
                        projectData.config.forceChanged();
                    }
                    foldersToCheck.addAll(allProjectData);
                }
            } else if (normalizedChangedPathAsString.endsWith(FILE_EXTENSION_AS)
                    || normalizedChangedPathAsString.endsWith(FILE_EXTENSION_MXML)) {
                List<ActionScriptProjectData> allProjectData = actionScriptProjectManager
                        .getAllProjectDataForSourceFile(changedPath);
                if (changeType.equals(FileChangeType.Deleted) ||

                //this is weird, but it's possible for a renamed file to
                //result in a Changed event, but not a Deleted event
                        (changeType.equals(FileChangeType.Changed) && !changedPath.toFile().exists())) {
                    IFileSpecification fileSpec = fileTracker.getFileSpecification(normalizedChangedPathAsString);
                    compilerWorkspace.fileRemoved(fileSpec);
                    clearProblemsForURI(Paths.get(normalizedChangedPathAsString).toUri());
                    //deleting a file may change errors in other existing files,
                    //so we need to do a full check
                    foldersToCheck.addAll(allProjectData);
                } else if (event.getType().equals(FileChangeType.Created)) {
                    IFileSpecification fileSpec = fileTracker.getFileSpecification(normalizedChangedPathAsString);
                    compilerWorkspace.fileAdded(fileSpec);
                    //creating a file may change errors in other existing files,
                    //so we need to do a full check
                    foldersToCheck.addAll(allProjectData);
                } else if (changeType.equals(FileChangeType.Changed)) {
                    IFileSpecification fileSpec = fileTracker.getFileSpecification(normalizedChangedPathAsString);
                    compilerWorkspace.fileChanged(fileSpec);
                    foldersToCheck.addAll(allProjectData);
                }
            } else if (changeType.equals(FileChangeType.Created) && java.nio.file.Files.isDirectory(changedPath)) {
                try {
                    java.nio.file.Files.walkFileTree(changedPath, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path subPath, BasicFileAttributes attrs) {
                            String normalizedSubPath = FilenameNormalization
                                    .normalize(subPath.toAbsolutePath().toString());
                            if (normalizedSubPath.endsWith(FILE_EXTENSION_AS)
                                    || normalizedSubPath.endsWith(FILE_EXTENSION_MXML)) {
                                IFileSpecification fileSpec = fileTracker.getFileSpecification(normalizedSubPath);
                                compilerWorkspace.fileAdded(fileSpec);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException e) {
                    System.err.println("Failed to walk added path: " + changedPath.toString());
                    e.printStackTrace(System.err);
                }
            } else if (changeType.equals(FileChangeType.Deleted)) {
                //we don't get separate didChangeWatchedFiles notifications for
                //each .as and .mxml in a directory when the directory is
                //deleted. with that in mind, we need to manually check if any
                //compilation units were in the directory that was deleted.
                String deletedFilePath = normalizedChangedPathAsString + File.separator;
                Set<String> filesToRemove = new HashSet<>();

                for (ActionScriptProjectData projectData : actionScriptProjectManager.getAllProjectData()) {
                    ILspProject project = projectData.project;
                    if (project == null) {
                        continue;
                    }
                    compilerWorkspace.startBuilding();
                    try {
                        for (ICompilationUnit unit : project.getCompilationUnits()) {
                            if (unit == null) {
                                continue;
                            }
                            UnitType unitType = unit.getCompilationUnitType();
                            if (!UnitType.AS_UNIT.equals(unitType) && !UnitType.MXML_UNIT.equals(unitType)
                                    && !UnitType.SWC_UNIT.equals(unitType)) {
                                continue;
                            }
                            String unitFileName = unit.getAbsoluteFilename();
                            if (unitFileName.startsWith(deletedFilePath)) {
                                //if we call fileRemoved() here, it will change the
                                //compilationUnits collection and throw an exception
                                //so just save the paths to be removed after this loop.
                                filesToRemove.add(unitFileName);

                                //deleting a file may change errors in other existing files,
                                //so we need to do a full check
                                foldersToCheck.add(projectData);

                                if (UnitType.SWC_UNIT.equals(unitType)) {
                                    projectData.config.forceChanged();
                                }
                            }
                        }
                    } finally {
                        compilerWorkspace.doneBuilding();
                    }
                }
                for (String fileToRemove : filesToRemove) {
                    Path pathToRemove = Paths.get(fileToRemove);
                    fileToRemove = FilenameNormalization.normalize(pathToRemove.toAbsolutePath().toString());
                    IFileSpecification fileSpec = fileTracker.getFileSpecification(fileToRemove);
                    compilerWorkspace.fileRemoved(fileSpec);
                    clearProblemsForURI(pathToRemove.toUri());
                }
            }
        }
        for (ActionScriptProjectData projectData : foldersToCheck) {
            checkProjectForProblems(projectData);
        }
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        if (!(params.getSettings() instanceof JsonObject)) {
            return;
        }
        JsonObject settings = (JsonObject) params.getSettings();
        this.updateSDK(settings);
        this.updateRealTimeProblems(settings);
        this.updateSourcePathWarning(settings);
        this.updateJVMArgs(settings);
        this.updateConcurrentRequests(settings);
    }

    @Override
    public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
        for (WorkspaceFolder folder : params.getEvent().getRemoved()) {
            removeWorkspaceFolder(folder);
        }
        for (WorkspaceFolder folder : params.getEvent().getAdded()) {
            addWorkspaceFolder(folder);
        }
    }

    @JsonNotification("$/setTraceNotification")
    public void setTraceNotification(Object params) {
        //this may be ignored. see: eclipse/lsp4j#22
    }

    public void setInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;

        //this is the first time that we can notify the client about any
        //diagnostics
        checkForProblemsNow(false);
    }

    /**
     * Called if something in the configuration has changed.
     */
    public void checkForProblemsNow(boolean forceChange) {
        updateFrameworkSDK();
        for (ActionScriptProjectData projectData : actionScriptProjectManager.getAllProjectData()) {
            if (forceChange) {
                IProjectConfigStrategy config = projectData.config;
                config.forceChanged();
            }
            checkProjectForProblems(projectData);
        }
        if (fallbackConfig != null) {
            if (forceChange) {
                fallbackConfig.forceChanged();
            }
            ActionScriptProjectData projectData = actionScriptProjectManager.getFallbackProjectData();
            checkProjectForProblems(projectData);
        }
    }

    private void updateFrameworkSDK() {
        String frameworkSDKPath = System.getProperty(PROPERTY_FRAMEWORK_LIB);
        if (frameworkSDKPath == null || frameworkSDKPath.equals(oldFrameworkSDKPath)) {
            return;
        }

        oldFrameworkSDKPath = frameworkSDKPath;

        //if the framework SDK doesn't include the Falcon compiler, we can
        //ignore certain errors from the editor SDK, which includes Falcon.
        Path frameworkPath = Paths.get(frameworkSDKPath);
        Path compilerPath = frameworkPath.resolve("../lib/falcon-mxmlc.jar");
        compilerProblemFilter.royaleProblems = compilerPath.toFile().exists();

        frameworkSDKIsRoyale = ActionScriptSDKUtils.isRoyaleFramework(frameworkPath);
        frameworkSDKIsFallback = isFallbackFramework();

        updateFallbackProject();
    }

    private boolean isFallbackFramework() {
        String jarPath = null;
        try {
            URI uri = ActionScriptServices.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            jarPath = Paths.get(uri).normalize().toString();
        } catch (Exception e) {
            return false;
        }
        if (jarPath == null) {
            return false;
        }

        String frameworkSDKPath = System.getProperty(PROPERTY_FRAMEWORK_LIB);
        if (frameworkSDKPath == null) {
            return true;
        }
        return Paths.get(jarPath).getParent().getParent().resolve("frameworks").equals(Paths.get(frameworkSDKPath));
    }

    private void updateFallbackProject() {
        if (oldFrameworkSDKPath == null) {
            return;
        }
        Path projectRoot = Paths.get(oldFrameworkSDKPath);
        WorkspaceFolder folder = new WorkspaceFolder(projectRoot.toUri().toString());
        fallbackConfig = new SimpleProjectConfigStrategy(projectRoot, folder);
        ActionScriptProjectData fallbackProjectData = actionScriptProjectManager.setFallbackProjectData(projectRoot,
                folder, fallbackConfig);
        for (Path openFilePath : fileTracker.getOpenFiles()) {
            ActionScriptProjectData projectData = actionScriptProjectManager.getProjectDataForSourceFile(openFilePath);
            if (fallbackProjectData.equals(projectData)) {
                fallbackConfig.didOpen(openFilePath);
            }
        }
    }

    private void watchNewSourceOrLibraryPath(Path sourceOrLibraryPath, ActionScriptProjectData projectData) {
        try {
            java.nio.file.Files.walkFileTree(sourceOrLibraryPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path subPath, BasicFileAttributes attrs) throws IOException {
                    WatchKey watchKey = subPath.register(sourcePathWatcher, StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
                    projectData.sourceOrLibraryPathWatchKeys.put(watchKey, subPath);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            System.err.println("Failed to watch source or library path: " + sourceOrLibraryPath.toString());
            e.printStackTrace(System.err);
        }
    }

    private void prepareNewProject(ActionScriptProjectData projectData) {
        ILspProject project = projectData.project;
        if (project == null) {
            return;
        }
        if (sourcePathWatcher == null) {
            createSourcePathWatcher();
        }
        Path projectRoot = projectData.projectRoot;
        if (projectRoot == null) {
            return;
        }
        boolean dynamicDidChangeWatchedFiles = false;
        try {
            dynamicDidChangeWatchedFiles = clientCapabilities.getWorkspace().getDidChangeWatchedFiles()
                    .getDynamicRegistration();
        } catch (NullPointerException e) {
            //ignore
        }
        for (File sourcePathFile : project.getSourcePath()) {
            Path sourcePath = sourcePathFile.toPath();
            try {
                sourcePath = sourcePath.toRealPath();
            } catch (IOException e) {
            }
            boolean shouldWatch = true;
            if (dynamicDidChangeWatchedFiles) {
                //we need to check if the source path is inside any of the
                //workspace folders. not just the current one.
                for (ActionScriptProjectData otherProjectData : actionScriptProjectManager.getAllProjectData()) {
                    Path otherProjectRoot = otherProjectData.projectRoot;
                    if (sourcePath.startsWith(otherProjectRoot)) {
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
            if (shouldWatch) {
                watchNewSourceOrLibraryPath(sourcePath, projectData);
            }
        }
        for (String libraryPathString : project.getCompilerLibraryPath(projectData.configurator.getConfiguration())) {
            Path libraryPath = Paths.get(libraryPathString);
            try {
                libraryPath = libraryPath.toRealPath();
            } catch (IOException e) {
            }
            boolean shouldWatch = true;
            if (dynamicDidChangeWatchedFiles) {
                //we need to check if the source path is inside any of the
                //workspace folders. not just the current one.
                for (ActionScriptProjectData otherProjectData : actionScriptProjectManager.getAllProjectData()) {
                    Path otherProjectRoot = otherProjectData.projectRoot;
                    if (libraryPath.startsWith(otherProjectRoot)) {
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
            if (shouldWatch) {
                watchNewSourceOrLibraryPath(libraryPath, projectData);
            }
        }
        for (String externalLibraryPathString : project
                .getCompilerExternalLibraryPath(projectData.configurator.getConfiguration())) {
            Path externalLibraryPath = Paths.get(externalLibraryPathString);
            try {
                externalLibraryPath = externalLibraryPath.toRealPath();
            } catch (IOException e) {
            }
            boolean shouldWatch = true;
            if (dynamicDidChangeWatchedFiles) {
                //we need to check if the source path is inside any of the
                //workspace folders. not just the current one.
                for (ActionScriptProjectData otherProjectData : actionScriptProjectManager.getAllProjectData()) {
                    Path otherProjectRoot = otherProjectData.projectRoot;
                    if (externalLibraryPath.startsWith(otherProjectRoot)) {
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
            if (shouldWatch) {
                watchNewSourceOrLibraryPath(externalLibraryPath, projectData);
            }
        }
    }

    private void createSourcePathWatcher() {
        try {
            sourcePathWatcher = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            System.err.println("Failed to get watch service for source paths.");
            e.printStackTrace(System.err);
        }
        sourcePathWatcherThread = new Thread() {
            public void run() {
                while (true) {
                    WatchKey watchKey = null;
                    try {
                        //pause the thread while there are no changes pending,
                        //for better performance
                        watchKey = sourcePathWatcher.take();
                    } catch (InterruptedException e) {
                        return;
                    }
                    List<FileEvent> changes = new ArrayList<>();
                    while (watchKey != null) {
                        for (ActionScriptProjectData projectData : actionScriptProjectManager.getAllProjectData()) {
                            if (!projectData.sourceOrLibraryPathWatchKeys.containsKey(watchKey)) {
                                continue;
                            }
                            Path path = projectData.sourceOrLibraryPathWatchKeys.get(watchKey);
                            for (WatchEvent<?> event : watchKey.pollEvents()) {
                                WatchEvent.Kind<?> kind = event.kind();
                                Path childPath = (Path) event.context();
                                childPath = path.resolve(childPath);
                                if (java.nio.file.Files.isDirectory(childPath)) {
                                    if (kind.equals(StandardWatchEventKinds.ENTRY_CREATE)) {
                                        //if a new directory has been created under
                                        //an existing that we're already watching,
                                        //then start watching the new one too.
                                        watchNewSourceOrLibraryPath(childPath, projectData);
                                    }
                                }
                                FileChangeType changeType = FileChangeType.Changed;
                                if (kind.equals(StandardWatchEventKinds.ENTRY_CREATE)) {
                                    changeType = FileChangeType.Created;
                                } else if (kind.equals(StandardWatchEventKinds.ENTRY_DELETE)) {
                                    changeType = FileChangeType.Deleted;
                                }
                                changes.add(new FileEvent(childPath.toUri().toString(), changeType));
                            }
                            boolean valid = watchKey.reset();
                            if (!valid) {
                                projectData.sourceOrLibraryPathWatchKeys.remove(watchKey);
                            }
                        }
                        //keep handling new changes until we run out
                        watchKey = sourcePathWatcher.poll();
                    }
                    if (changes.size() > 0) {
                        //convert to DidChangeWatchedFilesParams and pass
                        //to didChangeWatchedFiles, as if a notification
                        //had been sent from the client.
                        DidChangeWatchedFilesParams params = new DidChangeWatchedFilesParams();
                        params.setChanges(changes);
                        didChangeWatchedFiles(params);
                    }
                }
            }
        };
        sourcePathWatcherThread.start();
    }

    private void refreshProjectOptions(ActionScriptProjectData projectData) {
        IProjectConfigStrategy currentConfig = projectData.config;
        ProjectOptions projectOptions = projectData.options;
        if (!currentConfig.getChanged() && projectOptions != null) {
            //the options are fully up-to-date
            return;
        }
        //if the configuration changed, start fresh with a whole new project
        projectData.cleanup();
        if (frameworkSDKIsFallback) {
            projectData.options = null;
        } else {
            projectData.options = currentConfig.getOptions();
        }
    }

    private void addCompilerProblem(ICompilerProblem problem, PublishDiagnosticsParams publish, boolean isConfigFile) {
        if (!compilerProblemFilter.isAllowed(problem)) {
            return;
        }
        Diagnostic diagnostic = LanguageServerCompilerUtils.getDiagnosticFromCompilerProblem(problem);
        if (isConfigFile) {
            //clear the range because it isn't relevant
            diagnostic.setRange(new Range(new Position(0, 0), new Position(0, 0)));
        }
        List<Diagnostic> diagnostics = publish.getDiagnostics();
        diagnostics.add(diagnostic);
    }

    private void checkFilePathForSyntaxProblems(Path path, ActionScriptProjectData projectData,
            ProblemQuery problemQuery) {
        ASParser parser = null;
        Reader reader = fileTracker.getReader(path);
        if (reader != null) {
            StreamingASTokenizer tokenizer = null;
            ASToken[] tokens = null;
            try {
                tokenizer = StreamingASTokenizer.createForRepairingASTokenizer(reader, path.toString(), null);
                tokens = tokenizer.getTokens(reader);
            } finally {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
            if (tokenizer.hasTokenizationProblems()) {
                problemQuery.addAll(tokenizer.getTokenizationProblems());
            }
            RepairingTokenBuffer buffer = new RepairingTokenBuffer(tokens);

            Workspace workspace = new Workspace();
            workspace.endRequest();
            parser = new ASParser(workspace, buffer);
            FileNode node = new FileNode(workspace);
            try {
                parser.file(node);
            } catch (Exception e) {
                parser = null;
                System.err.println("Failed to parse file (" + path.toString() + "): " + e);
                e.printStackTrace(System.err);
            }
            //if an error occurred above, parser will be null
            if (parser != null) {
                problemQuery.addAll(parser.getSyntaxProblems());
            }
        }

        ProjectOptions projectOptions = projectData.options;
        ICompilerProblem syntaxProblem = null;
        if (reader == null) {
            //the file does not exist
            syntaxProblem = new FileNotFoundProblem(path.toString());
        } else if (parser == null && projectOptions == null) {
            //we couldn't load the project configuration and we couldn't parse
            //the file. we can't provide any information here.
            syntaxProblem = new SyntaxFallbackProblem(path.toString(),
                    "Failed to load project configuration options. Error checking has been disabled.");
        } else if (parser == null) {
            //something terrible happened, and this is the best we can do
            syntaxProblem = new SyntaxFallbackProblem(path.toString(),
                    "A fatal error occurred while checking for simple syntax problems.");
        } else if (projectOptions == null) {
            //something went wrong while attempting to load and parse the
            //project configuration, but we could successfully parse the syntax
            //tree.
            syntaxProblem = new SyntaxFallbackProblem(path.toString(),
                    "Failed to load project configuration options. Error checking has been disabled, except for simple syntax problems.");
        } else {
            //we seem to have loaded the project configuration and we could
            //parse the file, but something still went wrong.
            syntaxProblem = new SyntaxFallbackProblem(path.toString(),
                    "A fatal error occurred. Error checking has been disabled, except for simple syntax problems.");
        }
        problemQuery.add(syntaxProblem);
        reader = null;
    }

    private Path getMainCompilationUnitPath(ActionScriptProjectData projectData) {
        refreshProjectOptions(projectData);
        ProjectOptions projectOptions = projectData.options;
        if (projectOptions == null) {
            return null;
        }
        String[] files = projectOptions.files;
        if (files == null || files.length == 0) {
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
    private ILspProject getProject(ActionScriptProjectData projectData) {
        if (projectData == null) {
            System.err.println("Cannot find workspace for project.");
            return null;
        }
        refreshProjectOptions(projectData);
        ILspProject project = projectData.project;
        ProjectOptions projectOptions = projectData.options;
        if (projectOptions == null) {
            projectData.cleanup();

            Path configFilePath = projectData.config.getConfigFilePath();
            if (frameworkSDKIsFallback) {
                Path problemPath = null;
                if (configFilePath != null && configFilePath.toFile().exists()) {
                    problemPath = configFilePath;
                } else {
                    //if there's no project file, just grab the first open file
                    for (Path openFile : fileTracker.getOpenFiles()) {
                        problemPath = openFile;
                        break;
                    }
                }
                if (problemPath != null) {
                    projectData.codeProblemTracker.trackFileWithProblems(problemPath.toUri());
                    ProblemQuery problemQuery = new ProblemQuery();
                    problemQuery.add(new SyntaxFallbackProblem(problemPath.toString(),
                            "ActionScript & MXML code intelligence disabled. SDK not found."));
                    publishDiagnosticsForProblemQuery(problemQuery, projectData.configProblemTracker, projectData,
                            true);
                }
            } else if (configFilePath != null && !configFilePath.toFile().exists()
                    && actionScriptProjectManager.hasOpenFilesForProject(projectData)) {
                //the config file is missing, and there are open files in this
                //project, so we should add a hint that suggests how to properly
                //configure the project for the full experience.
                projectData.codeProblemTracker.trackFileWithProblems(configFilePath.toUri());
                ProblemQuery problemQuery = new ProblemQuery();
                problemQuery.add(new SyntaxFallbackProblem(configFilePath.toString(),
                        "ActionScript & MXML code intelligence disabled. Create a file named '"
                                + configFilePath.getFileName() + "' to enable all features."));
                publishDiagnosticsForProblemQuery(problemQuery, projectData.configProblemTracker, projectData, true);
            } else {
                //if there are existing configuration problems, they should no
                //longer be considered valid
                publishDiagnosticsForProblemQuery(new ProblemQuery(), projectData.configProblemTracker, projectData,
                        true);
            }
            return null;
        }
        if (project != null) {
            //clear all old problems because they won't be cleared automatically
            project.getProblems().clear();
            return project;
        }

        String oldUserDir = System.getProperty("user.dir");
        List<ICompilerProblem> configProblems = new ArrayList<>();

        RoyaleProjectConfigurator configurator = null;
        compilerWorkspace.startIdleState();
        try {
            Path projectRoot = projectData.projectRoot;
            System.setProperty("user.dir", projectRoot.toString());
            project = CompilerProjectUtils.createProject(projectOptions, compilerWorkspace);
            configurator = CompilerProjectUtils.createConfigurator(project, projectOptions);
        } finally {
            compilerWorkspace.endIdleState(IWorkspace.NIL_COMPILATIONUNITS_TO_UPDATE);
        }

        //this is not wrapped in startIdleState() or startBuilding()
        //because applyToProject() could trigger both, depending on context!
        if (configurator != null) {
            boolean result = configurator.applyToProject(project);
            Configuration configuration = configurator.getConfiguration();
            //it's possible for the configuration to be null when parsing
            //certain values in additionalOptions in asconfig.json
            if (configuration != null) {
                //the configurator will throw a null reference exception if the
                //configuration is null
                configProblems.addAll(configurator.getConfigurationProblems());
                //add configurator problems before the custom problems below
                //because configurator problems are probably more important
                if (projectOptions.type.equals(ProjectType.LIB)) {
                    String output = configuration.getOutput();
                    if (output == null || output.length() == 0) {
                        result = false;
                        configProblems
                                .add(new MissingRequirementConfigurationProblem(ICompilerSettingsConstants.OUTPUT_VAR));
                    }
                } else //app
                {
                    //if there are no existing configurator problems, we need to
                    //report at least one problem so that the user knows
                    //something is wrong
                    if (configProblems.size() == 0 && configuration.getTargetFile() == null) {
                        result = false;

                        //fall back to the config file or the workspace folder
                        Path problemPath = projectData.projectRoot;
                        Path configFilePath = projectData.config.getConfigFilePath();
                        if (configFilePath != null) {
                            problemPath = configFilePath;
                        }

                        String[] files = projectOptions.files;
                        if (files != null && files.length > 0) {
                            //even if mainClass is set, an entry must be added
                            //to the files array
                            configProblems.add(new LSPFileNotFoundProblem(files[files.length - 1],
                                    problemPath != null ? problemPath.toString() : null));
                        } else {
                            ConfigurationException e = new ConfigurationException.MustSpecifyTarget(null,
                                    problemPath != null ? problemPath.toString() : null, -1);
                            configProblems.add(new ConfigurationProblem(e));
                        }
                    }
                }
            }
            if (!result) {
                configurator = null;
            }
        }

        compilerWorkspace.startIdleState();
        try {
            if (configurator != null) {
                ITarget.TargetType targetType = ITarget.TargetType.SWF;
                if (projectOptions.type.equals(ProjectType.LIB)) {
                    targetType = ITarget.TargetType.SWC;
                }
                ITargetSettings targetSettings = configurator.getTargetSettings(targetType);
                if (targetSettings == null) {
                    // calling getTargetSettings() can add more configuration
                    // problems that didn't exist above
                    configProblems.addAll(configurator.getConfigurationProblems());
                    configurator = null;
                } else {
                    project.setTargetSettings(targetSettings);
                }
            }

            if (configurator == null) {
                project.delete();
                project = null;
            }

            System.setProperty("user.dir", oldUserDir);

            ICompilerProblemSettings compilerProblemSettings = null;
            if (configurator != null) {
                compilerProblemSettings = configurator.getCompilerProblemSettings();
            }
            ProblemQuery problemQuery = new ProblemQuery(compilerProblemSettings);
            problemQuery.addAll(configProblems);
            publishDiagnosticsForProblemQuery(problemQuery, projectData.configProblemTracker, projectData, true);

            projectData.project = project;
            projectData.configurator = configurator;
            prepareNewProject(projectData);
        } finally {
            compilerWorkspace.endIdleState(IWorkspace.NIL_COMPILATIONUNITS_TO_UPDATE);
        }
        return project;
    }

    private void clearProblemsForURI(URI uri) {
        PublishDiagnosticsParams publish = new PublishDiagnosticsParams();
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        publish.setDiagnostics(diagnostics);
        publish.setUri(uri.toString());
        if (languageClient != null) {
            languageClient.publishDiagnostics(publish);
        }
    }

    private void checkProjectForProblems(ActionScriptProjectData projectData) {
        //make sure that the latest changes have been passed to
        //workspace.fileChanged() before proceeding
        if (realTimeProblemsChecker != null) {
            realTimeProblemsChecker.updateNow();
        }

        getProject(projectData);
        ILspProject project = projectData.project;
        ProjectOptions options = projectData.options;
        if (project == null || options == null) {
            //since we don't have a project, we don't have compilation units
            //any existing problems should be considered stale and won't be
            //updated until the configuration problems are fixed.
            projectData.codeProblemTracker.releaseStale();
            return;
        }

        ProblemQuery problemQuery = projectDataToProblemQuery(projectData);
        compilerWorkspace.startBuilding();
        try {
            //start by making sure that all of the project's compilation units
            //have been created. we'll check them for errors in a later step
            populateCompilationUnits(project);

            //don't check compilation units for problems if the project itself
            //has problems. the user should fix those first.
            Collection<ICompilerProblem> fatalProblems = project.getFatalProblems();
            if (fatalProblems != null) {
                problemQuery.addAll(fatalProblems);
            }

            problemQuery.addAll(project.getProblems());

            Collection<ICompilerProblem> collectedProblems = new ArrayList<>();
            project.collectProblems(collectedProblems);
            problemQuery.addAll(collectedProblems);

            if (!problemQuery.hasErrors()) {
                checkReachableCompilationUnitsForErrors(problemQuery, projectData);
            }
        } finally {
            compilerWorkspace.doneBuilding();
        }
        publishDiagnosticsForProblemQuery(problemQuery, projectData.codeProblemTracker, projectData, true);
    }

    private void publishDiagnosticsForProblemQuery(ProblemQuery problemQuery, ProblemTracker problemTracker,
            ActionScriptProjectData projectData, boolean releaseStale) {
        Path projectRoot = projectData.projectRoot;
        String defaultsPathString = projectRoot.resolve(SOURCE_DEFAULTS).toString();
        Path configPath = projectRoot.resolve(SOURCE_CONFIG);
        Path projectConfigPath = null;
        String defaultConfiguratonProblemPath = projectData.config.getDefaultConfigurationProblemPath();
        if (defaultConfiguratonProblemPath != null) {
            projectConfigPath = Paths.get(defaultConfiguratonProblemPath);
            if (!projectConfigPath.isAbsolute()) {
                projectConfigPath = projectRoot.resolve(projectConfigPath);
            }
        }
        Map<URI, PublishDiagnosticsParams> filesMap = new HashMap<>();
        for (ICompilerProblem problem : problemQuery.getFilteredProblems()) {
            String problemSourcePath = problem.getSourcePath();
            IncludeFileData includedFile = projectData.includedFiles.get(problemSourcePath);
            if (includedFile != null && !includedFile.parentPath.equals(problemSourcePath)) {
                //skip files that are included in other files
                continue;
            }
            boolean isConfigFile = false;
            if (problemSourcePath != null && (CommandLineConfigurator.SOURCE_COMMAND_LINE.equals(problemSourcePath)
                    || defaultsPathString.equals(problemSourcePath) || (problemSourcePath.endsWith(SOURCE_CONFIG)
                            && configPath.equals(Paths.get(problemSourcePath))))) {
                //for configuration problems that point to defaults, config.as,
                //or the command line, the best default location to send the
                //user is probably to the project's config file (like
                //asconfig.json in Visual Studio Code)
                if (projectConfigPath != null) {
                    problemSourcePath = projectConfigPath.toString();
                }
                isConfigFile = true;
            }
            if (problemSourcePath == null) {
                problemSourcePath = projectRoot.toString();
            }
            URI uri = Paths.get(problemSourcePath).toUri();
            if (!filesMap.containsKey(uri)) {
                PublishDiagnosticsParams params = new PublishDiagnosticsParams();
                params.setUri(uri.toString());
                params.setDiagnostics(new ArrayList<>());
                filesMap.put(uri, params);
            }
            PublishDiagnosticsParams params = filesMap.get(uri);
            problemTracker.trackFileWithProblems(uri);
            addCompilerProblem(problem, params, isConfigFile);
        }
        if (releaseStale) {
            problemTracker.releaseStale();
        } else {
            problemTracker.makeStale();
        }
        if (languageClient != null) {
            filesMap.values().forEach(languageClient::publishDiagnostics);
        }
    }

    private ProblemQuery projectDataToProblemQuery(ActionScriptProjectData projectData) {
        ICompilerProblemSettings compilerProblemSettings = null;
        if (projectData.configurator != null) {
            compilerProblemSettings = projectData.configurator.getCompilerProblemSettings();
        }
        return new ProblemQuery(compilerProblemSettings);
    }

    private void populateCompilationUnits(ILspProject project) {
        List<ICompilerProblem> problems = new ArrayList<>();
        boolean continueCheckingForErrors = true;
        while (continueCheckingForErrors) {
            try {
                //at this point, we want to build all compilation units,
                //including the ones that aren't considered reachable yet.
                //we'll filter out the unreachable units later
                for (ICompilationUnit unit : project.getCompilationUnits()) {
                    if (unit == null) {
                        continue;
                    }
                    UnitType unitType = unit.getCompilationUnitType();
                    if (!UnitType.AS_UNIT.equals(unitType) && !UnitType.MXML_UNIT.equals(unitType)) {
                        //compiled compilation units won't have problems
                        continue;
                    }
                    //reuse the existing list so that we don't allocate a list
                    //for every compilation unit
                    problems.clear();

                    checkCompilationUnitForAllProblems(unit, project, problems);
                    problems.clear();
                }
                continueCheckingForErrors = false;
            } catch (ConcurrentModificationException e) {
                //when we finished building one of the compilation
                //units, more were added to the collection, so we need
                //to start over because we can't iterate over a modified
                //collection.
            }
        }
    }

    private void checkReachableCompilationUnitsForErrors(ProblemQuery problemQuery,
            ActionScriptProjectData projectData) {
        if (!initialized) {
            //do this later because we can't publish diagnostics yet
            return;
        }

        ILspProject project = projectData.project;

        Set<ICompilationUnit> roots = new HashSet<>();
        try {
            if (projectData.options.type.equals(ProjectType.LIB)) {
                Target target = (Target) project.createSWCTarget(project.getTargetSettings(), null);
                roots.addAll(target.getRootedCompilationUnits().getUnits());
            } else //app
            {
                for (String file : projectData.options.files) {
                    String normalizedFile = FilenameNormalization.normalize(file);
                    Collection<ICompilationUnit> units = project.getCompilationUnits(normalizedFile);
                    if (units.size() == 0) {
                        //we couldn't find a compilation unit for this file, but
                        //we should provide some kind of fallback because it's
                        //one of our root files
                        checkFilePathForSyntaxProblems(Paths.get(normalizedFile), projectData, problemQuery);
                    } else {
                        roots.addAll(units);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Exception during createSWCTarget() or createSWFTarget(): " + e);
            e.printStackTrace(System.err);

            InternalCompilerProblem problem = new InternalCompilerProblem(e);
            problemQuery.add(problem);
            return;
        }

        //some additional files may be open in an editor, and we'll
        //want to check those for errors too, even if they aren't
        //referenced by one of the roots
        for (Path openFilePath : fileTracker.getOpenFiles()) {
            ActionScriptProjectData openFileProjectData = actionScriptProjectManager
                    .getProjectDataForSourceFile(openFilePath);
            if (!projectData.equals(openFileProjectData)) {
                //not in this project
                continue;
            }
            ICompilationUnit openUnit = CompilerProjectUtils.findCompilationUnit(openFilePath, project);
            if (openUnit == null) {
                //if there is no unit for this open file, check for simple
                //syntax problems instead
                checkFilePathForSyntaxProblems(openFilePath, projectData, problemQuery);
                continue;
            }
            roots.add(openUnit);
        }

        //start fresh when checking all compilation units
        projectData.includedFiles.clear();

        List<ICompilerProblem> problems = new ArrayList<>();
        List<ICompilationUnit> reachableUnits = new ArrayList<>();
        //there shouldn't be any concurrent modification exceptions when looping
        //over the reachable units, but to be safe, copy all of the compilation
        //units to a new collection
        reachableUnits.addAll(project.getReachableCompilationUnitsInSWFOrder(roots));
        for (ICompilationUnit unit : reachableUnits) {
            if (unit == null) {
                continue;
            }

            UnitType unitType = unit.getCompilationUnitType();
            if (!UnitType.AS_UNIT.equals(unitType) && !UnitType.MXML_UNIT.equals(unitType)) {
                //compiled compilation units won't have problems
                continue;
            }

            Path unitPath = Paths.get(unit.getAbsoluteFilename());
            URI unitUri = unitPath.toUri();
            if (notOnSourcePathSet.contains(unitUri)) {
                //if the file was not on the project's source path, clear out any
                //errors that might have existed previously
                notOnSourcePathSet.remove(unitUri);

                PublishDiagnosticsParams publish = new PublishDiagnosticsParams();
                publish.setDiagnostics(new ArrayList<>());
                publish.setUri(unitUri.toString());
                if (languageClient != null) {
                    languageClient.publishDiagnostics(publish);
                }
            }

            //we don't check for errors in the fallback project
            if (projectData.equals(actionScriptProjectManager.getFallbackProjectData())) {
                //normally, we look for included files after checking
                //for errors, but since we're not checking for errors
                //do it here instead
                CompilationUnitUtils.findIncludedFiles(unit, projectData.includedFiles);

                //there's a configuration setting that determines if we
                //warn the user that a file is outside of the project's
                //source path
                if (showFileOutsideSourcePath) {
                    notOnSourcePathSet.add(unitUri);
                    if (actionScriptProjectManager.getAllProjectData().size() == 0) {
                        SyntaxFallbackProblem problem = new SyntaxFallbackProblem(unitPath.toString(),
                                "Some code intelligence features are disabled for this file. Open a workspace folder to enable all ActionScript & MXML features.");
                        problemQuery.add(problem);
                    } else {
                        SyntaxFallbackProblem problem = new SyntaxFallbackProblem(unitPath.toString(), unitPath
                                .getFileName()
                                + " is not located in the workspace's source path. Some code intelligence features are disabled for this file.");
                        problemQuery.add(problem);
                    }
                }
                continue;
            }

            //reuse the existing list so that we don't allocate a list
            //for every compilation unit
            problems.clear();

            //we should have already built, so this will be fast
            //if we hadn't built, we would not have all of the roots
            checkCompilationUnitForAllProblems(unit, project, problems);
            problemQuery.addAll(problems);
            //clear for the next compilation unit
            problems.clear();

            //just to be safe, find all of the included files
            //after we've checked for problems
            CompilationUnitUtils.findIncludedFiles(unit, projectData.includedFiles);
        }
    }

    private void checkCompilationUnitForAllProblems(ICompilationUnit unit, ILspProject project,
            List<ICompilerProblem> problems) {
        try {
            if (initialized) {
                //if we pass in null, it's designed to ignore certain errors
                //that don't matter for IDE code intelligence.
                unit.waitForBuildFinish(problems, null);

                //note: we check for unused imports only for full builds because
                //it's a little too expensive to do it for real-time problems
                IASNode ast = ASTUtils.getCompilationUnitAST(unit);
                if (ast != null) {
                    Set<String> requiredImports = project.getQNamesOfDependencies(unit);
                    ASTUtils.findUnusedImportProblems(ast, requiredImports, problems);
                    ASTUtils.findDisabledConfigConditionBlockProblems(ast, problems);
                }
            } else {
                //we can't publish diagnostics yet, but we can start the build
                //process in the background so that it's faster when we're ready
                //to publish diagnostics after initialization
                unit.getSyntaxTreeRequest();
                unit.getFileScopeRequest();
                unit.getOutgoingDependenciesRequest();
                unit.getABCBytesRequest();
            }
        } catch (Exception e) {
            System.err.println("Exception during waitForBuildFinish(): " + e);
            e.printStackTrace(System.err);

            InternalCompilerProblem problem = new InternalCompilerProblem(e);
            problems.add(problem);
        }
    }

    private void updateSDK(JsonObject settings) {
        if (!settings.has("as3mxml")) {
            return;
        }
        JsonObject as3mxml = settings.get("as3mxml").getAsJsonObject();
        if (!as3mxml.has("sdk")) {
            return;
        }
        JsonObject sdk = as3mxml.get("sdk").getAsJsonObject();
        String frameworkSDK = null;
        if (sdk.has("framework")) {
            JsonElement frameworkValue = sdk.get("framework");
            if (!frameworkValue.isJsonNull()) {
                frameworkSDK = frameworkValue.getAsString();
            }
        }
        if (frameworkSDK == null && sdk.has("editor")) {
            //for legacy reasons, we fall back to the editor SDK
            JsonElement editorValue = sdk.get("editor");
            if (!editorValue.isJsonNull()) {
                frameworkSDK = editorValue.getAsString();
            }
        }
        if (frameworkSDK == null) {
            //keep using the existing framework for now
            return;
        }
        String frameworkLib = null;
        Path frameworkLibPath = Paths.get(frameworkSDK).resolve(FRAMEWORKS_RELATIVE_PATH_CHILD).toAbsolutePath()
                .normalize();
        if (frameworkLibPath.toFile().exists()) {
            //if the frameworks directory exists, use it!
            frameworkLib = frameworkLibPath.toString();
        } else {
            //if the frameworks directory doesn't exist, we also
            //need to check for Apache Royale's unique layout
            //with the royale-asjs directory
            Path royalePath = Paths.get(frameworkSDK).resolve(ROYALE_ASJS_RELATIVE_PATH_CHILD)
                    .resolve(FRAMEWORKS_RELATIVE_PATH_CHILD).toAbsolutePath().normalize();
            if (royalePath.toFile().exists()) {
                frameworkLib = royalePath.toString();
            }
        }
        if (frameworkLib == null) {
            //keep using the existing framework for now
            return;
        }
        String oldFrameworkLib = System.getProperty(PROPERTY_FRAMEWORK_LIB);
        if (oldFrameworkLib.equals(frameworkLib)) {
            //frameworks library has not changed
            return;
        }
        System.setProperty(PROPERTY_FRAMEWORK_LIB, frameworkLib);
        checkForProblemsNow(true);
    }

    private void updateRealTimeProblems(JsonObject settings) {
        if (!settings.has("as3mxml")) {
            return;
        }
        JsonObject as3mxml = settings.get("as3mxml").getAsJsonObject();
        if (!as3mxml.has("problems")) {
            return;
        }
        JsonObject problems = as3mxml.get("problems").getAsJsonObject();
        if (!problems.has("realTime")) {
            return;
        }
        boolean newRealTimeProblems = problems.get("realTime").getAsBoolean();
        if (realTimeProblems == newRealTimeProblems) {
            return;
        }
        realTimeProblems = newRealTimeProblems;
        if (newRealTimeProblems) {
            checkForProblemsNow(true);
        }
    }

    private void updateSourcePathWarning(JsonObject settings) {
        if (!settings.has("as3mxml")) {
            return;
        }
        JsonObject as3mxml = settings.get("as3mxml").getAsJsonObject();
        if (!as3mxml.has("problems")) {
            return;
        }
        JsonObject problems = as3mxml.get("problems").getAsJsonObject();
        if (!problems.has("showFileOutsideSourcePath")) {
            return;
        }
        boolean newShowFileOutsideSourcePath = problems.get("showFileOutsideSourcePath").getAsBoolean();
        if (showFileOutsideSourcePath == newShowFileOutsideSourcePath) {
            return;
        }
        showFileOutsideSourcePath = newShowFileOutsideSourcePath;
        checkForProblemsNow(true);
    }

    private void updateJVMArgs(JsonObject settings) {
        if (!settings.has("as3mxml")) {
            return;
        }
        JsonObject as3mxml = settings.get("as3mxml").getAsJsonObject();
        if (!as3mxml.has("asconfigc")) {
            return;
        }
        JsonObject asconfigc = as3mxml.get("asconfigc").getAsJsonObject();
        if (!asconfigc.has("jvmargs")) {
            return;
        }
        JsonElement jvmargsElement = asconfigc.get("jvmargs");
        String newJVMArgs = null;
        if (!jvmargsElement.isJsonNull()) {
            newJVMArgs = asconfigc.get("jvmargs").getAsString();
        }
        if (jvmargs == null && newJVMArgs == null) {
            return;
        }
        if (jvmargs != null && jvmargs.equals(newJVMArgs)) {
            return;
        }
        jvmargs = newJVMArgs;
        if (compilerShell != null) {
            compilerShell.dispose();
            compilerShell = null;
        }
    }

    private void updateConcurrentRequests(JsonObject settings) {
        if (!settings.has("as3mxml")) {
            return;
        }
        JsonObject as3mxml = settings.get("as3mxml").getAsJsonObject();
        if (!as3mxml.has("languageServer")) {
            return;
        }
        JsonObject languageServer = as3mxml.get("languageServer").getAsJsonObject();
        if (!languageServer.has("concurrentRequests")) {
            return;
        }
        boolean newConcurrentRequests = languageServer.get("concurrentRequests").getAsBoolean();
        if (concurrentRequests == newConcurrentRequests) {
            return;
        }
        concurrentRequests = newConcurrentRequests;
    }

    private CompletableFuture<Object> executeQuickCompileCommand(ExecuteCommandParams params) {
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken -> {
            List<Object> args = params.getArguments();
            String uri = ((JsonPrimitive) args.get(0)).getAsString();
            boolean debug = ((JsonPrimitive) args.get(1)).getAsBoolean();
            boolean success = false;
            try {
                if (compilerShell == null) {
                    List<String> argsList = null;
                    if (jvmargs != null) {
                        String[] argsArray = jvmargs.split(" ");
                        argsList = Arrays.stream(argsArray).collect(Collectors.toList());
                    }
                    compilerShell = new CompilerShell(languageClient, argsList);
                }
                String frameworkLib = System.getProperty(PROPERTY_FRAMEWORK_LIB);
                Path frameworkSDKHome = Paths.get(frameworkLib, "..");
                Path workspaceRootPath = LanguageServerCompilerUtils.getPathFromLanguageServerURI(uri);
                ASConfigCOptions options = new ASConfigCOptions(workspaceRootPath.toString(),
                        frameworkSDKHome.toString(), debug, null, null, true, compilerShell);
                try {
                    new ASConfigC(options);
                    success = true;
                } catch (ASConfigCException e) {
                    //this is a message intended for the user
                    languageClient.logCompilerShellOutput("\n" + e.getMessage());
                    success = false;
                }
            } catch (Exception e) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                e.printStackTrace(new PrintStream(buffer));
                languageClient.logCompilerShellOutput("Exception in compiler shell: " + buffer.toString());
            }
            return success;
        });
    }
}
