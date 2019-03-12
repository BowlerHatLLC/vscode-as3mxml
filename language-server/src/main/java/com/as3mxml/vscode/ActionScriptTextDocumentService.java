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
import java.io.FileNotFoundException;
import java.io.FileReader;
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.royale.compiler.clients.problems.ProblemQuery;
import org.apache.royale.compiler.common.ASModifier;
import org.apache.royale.compiler.common.ISourceLocation;
import org.apache.royale.compiler.common.PrefixMap;
import org.apache.royale.compiler.common.XMLName;
import org.apache.royale.compiler.config.CommandLineConfigurator;
import org.apache.royale.compiler.config.Configuration;
import org.apache.royale.compiler.config.ICompilerProblemSettings;
import org.apache.royale.compiler.constants.IASKeywordConstants;
import org.apache.royale.compiler.constants.IASLanguageConstants;
import org.apache.royale.compiler.constants.IMXMLCoreConstants;
import org.apache.royale.compiler.constants.IMetaAttributeConstants;
import org.apache.royale.compiler.definitions.IAccessorDefinition;
import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IEventDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IGetterDefinition;
import org.apache.royale.compiler.definitions.IInterfaceDefinition;
import org.apache.royale.compiler.definitions.INamespaceDefinition;
import org.apache.royale.compiler.definitions.IPackageDefinition;
import org.apache.royale.compiler.definitions.IParameterDefinition;
import org.apache.royale.compiler.definitions.ISetterDefinition;
import org.apache.royale.compiler.definitions.IStyleDefinition;
import org.apache.royale.compiler.definitions.ITypeDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition;
import org.apache.royale.compiler.definitions.metadata.IDeprecationInfo;
import org.apache.royale.compiler.definitions.metadata.IMetaTag;
import org.apache.royale.compiler.definitions.metadata.IMetaTagAttribute;
import org.apache.royale.compiler.filespecs.IFileSpecification;
import org.apache.royale.compiler.internal.mxml.MXMLData;
import org.apache.royale.compiler.internal.mxml.MXMLTagData;
import org.apache.royale.compiler.internal.parsing.as.ASParser;
import org.apache.royale.compiler.internal.parsing.as.ASToken;
import org.apache.royale.compiler.internal.parsing.as.OffsetCue;
import org.apache.royale.compiler.internal.parsing.as.RepairingTokenBuffer;
import org.apache.royale.compiler.internal.parsing.as.StreamingASTokenizer;
import org.apache.royale.compiler.internal.projects.CompilerProject;
import org.apache.royale.compiler.internal.projects.RoyaleProject;
import org.apache.royale.compiler.internal.projects.RoyaleProjectConfigurator;
import org.apache.royale.compiler.internal.scopes.ASScope;
import org.apache.royale.compiler.internal.scopes.TypeScope;
import org.apache.royale.compiler.internal.scopes.ASProjectScope.DefinitionPromise;
import org.apache.royale.compiler.internal.tree.as.FileNode;
import org.apache.royale.compiler.internal.tree.as.FullNameNode;
import org.apache.royale.compiler.internal.units.ResourceBundleCompilationUnit;
import org.apache.royale.compiler.internal.units.SWCCompilationUnit;
import org.apache.royale.compiler.internal.workspaces.Workspace;
import org.apache.royale.compiler.mxml.IMXMLData;
import org.apache.royale.compiler.mxml.IMXMLDataManager;
import org.apache.royale.compiler.mxml.IMXMLLanguageConstants;
import org.apache.royale.compiler.mxml.IMXMLTagAttributeData;
import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.mxml.IMXMLUnitData;
import org.apache.royale.compiler.problems.ICompilerProblem;
import org.apache.royale.compiler.problems.InternalCompilerProblem;
import org.apache.royale.compiler.scopes.IASScope;
import org.apache.royale.compiler.targets.ITarget;
import org.apache.royale.compiler.targets.ITargetSettings;
import org.apache.royale.compiler.tree.ASTNodeID;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IBinaryOperatorNode;
import org.apache.royale.compiler.tree.as.IClassNode;
import org.apache.royale.compiler.tree.as.IContainerNode;
import org.apache.royale.compiler.tree.as.IDefinitionNode;
import org.apache.royale.compiler.tree.as.IExpressionNode;
import org.apache.royale.compiler.tree.as.IFileNode;
import org.apache.royale.compiler.tree.as.IFunctionCallNode;
import org.apache.royale.compiler.tree.as.IFunctionNode;
import org.apache.royale.compiler.tree.as.IIdentifierNode;
import org.apache.royale.compiler.tree.as.IImportNode;
import org.apache.royale.compiler.tree.as.IInterfaceNode;
import org.apache.royale.compiler.tree.as.IKeywordNode;
import org.apache.royale.compiler.tree.as.ILanguageIdentifierNode;
import org.apache.royale.compiler.tree.as.IMemberAccessExpressionNode;
import org.apache.royale.compiler.tree.as.INamespaceDecorationNode;
import org.apache.royale.compiler.tree.as.IPackageNode;
import org.apache.royale.compiler.tree.as.IScopedDefinitionNode;
import org.apache.royale.compiler.tree.as.IScopedNode;
import org.apache.royale.compiler.tree.as.ITryNode;
import org.apache.royale.compiler.tree.as.ITypeNode;
import org.apache.royale.compiler.tree.as.IVariableNode;
import org.apache.royale.compiler.tree.mxml.IMXMLClassDefinitionNode;
import org.apache.royale.compiler.tree.mxml.IMXMLClassReferenceNode;
import org.apache.royale.compiler.tree.mxml.IMXMLConcatenatedDataBindingNode;
import org.apache.royale.compiler.tree.mxml.IMXMLEventSpecifierNode;
import org.apache.royale.compiler.tree.mxml.IMXMLInstanceNode;
import org.apache.royale.compiler.tree.mxml.IMXMLNode;
import org.apache.royale.compiler.tree.mxml.IMXMLPropertySpecifierNode;
import org.apache.royale.compiler.tree.mxml.IMXMLSingleDataBindingNode;
import org.apache.royale.compiler.tree.mxml.IMXMLSpecifierNode;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.apache.royale.compiler.workspaces.IWorkspace;
import org.apache.royale.utils.FilenameNormalization;

import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
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
import com.as3mxml.vscode.services.ActionScriptLanguageClient;
import com.as3mxml.vscode.utils.ASTUtils;
import com.as3mxml.vscode.utils.ActionScriptSDKUtils;
import com.as3mxml.vscode.utils.AddImportData;
import com.as3mxml.vscode.utils.CodeActionsUtils;
import com.as3mxml.vscode.utils.CompilationUnitUtils;
import com.as3mxml.vscode.utils.CompilerProblemFilter;
import com.as3mxml.vscode.utils.CompilerProjectUtils;
import com.as3mxml.vscode.utils.CompletionItemUtils;
import com.as3mxml.vscode.utils.DefinitionDocumentationUtils;
import com.as3mxml.vscode.utils.DefinitionTextUtils;
import com.as3mxml.vscode.utils.DefinitionUtils;
import com.as3mxml.vscode.utils.ImportRange;
import com.as3mxml.vscode.utils.ImportTextEditUtils;
import com.as3mxml.vscode.utils.LSPUtils;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;
import com.as3mxml.vscode.utils.LanguageServerFileSpecGetter;
import com.as3mxml.vscode.utils.MXMLDataUtils;
import com.as3mxml.vscode.utils.MXMLNamespace;
import com.as3mxml.vscode.utils.MXMLNamespaceUtils;
import com.as3mxml.vscode.utils.ProblemTracker;
import com.as3mxml.vscode.utils.ScopeUtils;
import com.as3mxml.vscode.utils.SourcePathUtils;
import com.as3mxml.vscode.utils.WaitForBuildFinishRunner;
import com.as3mxml.vscode.utils.XmlnsRange;
import com.as3mxml.vscode.utils.CompilationUnitUtils.IncludeFileData;
import com.as3mxml.vscode.utils.DefinitionTextUtils.DefinitionAsText;

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
import org.eclipse.lsp4j.CompletionItemKind;
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
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentEdit;
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
    private static final String MARKED_STRING_LANGUAGE_ACTIONSCRIPT = "nextgenas";
    private static final String MARKED_STRING_LANGUAGE_MXML = "mxml";
    private static final String SDK_LIBRARY_PATH_SIGNATURE_UNIX = "/frameworks/libs/";
    private static final String SDK_LIBRARY_PATH_SIGNATURE_WINDOWS = "\\frameworks\\libs\\";
    private static final String PROPERTY_FRAMEWORK_LIB = "royalelib";
    private static final String VECTOR_HIDDEN_PREFIX = "Vector$";

    private ActionScriptLanguageClient languageClient;
    private IProjectConfigStrategyFactory projectConfigStrategyFactory;
    private String oldFrameworkSDKPath;
    private Map<Path, String> sourceByPath = new HashMap<>();
    private List<String> completionTypes = new ArrayList<>();
    private Map<String,IncludeFileData> includedFiles = new HashMap<>();
    private Workspace compilerWorkspace;
    private List<WorkspaceFolder> workspaceFolders = new ArrayList<>();
    private Map<WorkspaceFolder, WorkspaceFolderData> workspaceFolderToData = new HashMap<>();
    private LanguageServerFileSpecGetter fileSpecGetter;
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
        updateFrameworkSDK();
        
        compilerWorkspace = new Workspace();
        compilerWorkspace.setASDocDelegate(new VSCodeASDocDelegate());
        fileSpecGetter = new LanguageServerFileSpecGetter(compilerWorkspace, sourceByPath);
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
        return workspaceFolders;
    }

    public WorkspaceFolderData getWorkspaceFolderData(WorkspaceFolder folder)
    {
        return workspaceFolderToData.get(folder);
    }

    public void addWorkspaceFolder(WorkspaceFolder folder)
    {
        workspaceFolders.add(folder);
        IProjectConfigStrategy config = projectConfigStrategyFactory.create(folder);
        WorkspaceFolderData folderData = new WorkspaceFolderData(folder, config);
        workspaceFolderToData.put(folder, folderData);
        folderData.codeProblemTracker.setLanguageClient(languageClient);
        folderData.configProblemTracker.setLanguageClient(languageClient);
        
        //let's get the code intelligence up and running!
        Path path = getMainCompilationUnitPath(folderData);
        if (path != null)
        {
            String normalizedPath = FilenameNormalization.normalize(path.toAbsolutePath().toString());
            IFileSpecification fileSpec = fileSpecGetter.getFileSpecification(normalizedPath);
            compilerWorkspace.fileChanged(fileSpec);
        }

        checkProjectForProblems(folderData);
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
                cancelToken.checkCanceled();

                //this shouldn't be necessary, but if we ever forget to do this
                //somewhere, completion results might be missing items.
                completionTypes.clear();
                
                TextDocumentIdentifier textDocument = params.getTextDocument();
                Position position = params.getPosition();
                Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
                if (path == null)
                {
                    CompletionList result = new CompletionList();
                    result.setIsIncomplete(false);
                    result.setItems(new ArrayList<>());
                    cancelToken.checkCanceled();
                    return Either.forRight(result);
                }
                WorkspaceFolderData folderData = getWorkspaceFolderDataForSourceFile(path);
                if(folderData == null || folderData.project == null)
                {
                    CompletionList result = new CompletionList();
                    result.setIsIncomplete(false);
                    result.setItems(new ArrayList<>());
                    cancelToken.checkCanceled();
                    return Either.forRight(result);
                }
                RoyaleProject project = folderData.project;

                int currentOffset = getOffsetFromPathAndPosition(path, position);
                if (currentOffset == -1)
                {
                    CompletionList result = new CompletionList();
                    result.setIsIncomplete(false);
                    result.setItems(new ArrayList<>());
                    cancelToken.checkCanceled();
                    return Either.forRight(result);
                }
                MXMLData mxmlData = getMXMLDataForPath(path, folderData);

                IMXMLTagData offsetTag = getOffsetMXMLTag(mxmlData, currentOffset);
                if (offsetTag != null)
                {
                    IASNode embeddedNode = getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path, currentOffset, folderData);
                    if (embeddedNode != null)
                    {
                        CompletionList result = actionScriptCompletion(embeddedNode, path, position, currentOffset, folderData);
                        cancelToken.checkCanceled();
                        return Either.forRight(result);
                    }
                    //if we're inside an <fx:Script> tag, we want ActionScript completion,
                    //so that's why we call isMXMLTagValidForCompletion()
                    if (MXMLDataUtils.isMXMLTagValidForCompletion(offsetTag))
                    {
                        ICompilationUnit offsetUnit = findCompilationUnit(path);
                        CompletionList result = mxmlCompletion(offsetTag, path, currentOffset, offsetUnit, project);
                        cancelToken.checkCanceled();
                        return Either.forRight(result);
                    }
                }
                else if(mxmlData != null && mxmlData.getRootTag() == null)
                {
                    ICompilationUnit offsetUnit = findCompilationUnit(path);
                    boolean tagsNeedOpenBracket = getTagsNeedOpenBracket(path, currentOffset);
                    CompletionList result = new CompletionList();
                    result.setIsIncomplete(false);
                    result.setItems(new ArrayList<>());
                    autoCompleteDefinitionsForMXML(result, project, offsetUnit, offsetTag, true, tagsNeedOpenBracket, null, null, null);
                    cancelToken.checkCanceled();
                    return Either.forRight(result);
                }
                if (offsetTag == null && params.getTextDocument().getUri().endsWith(MXML_EXTENSION))
                {
                    //it's possible for the offset tag to be null in an MXML file, but
                    //we don't want to trigger ActionScript completion.
                    //for some reason, the offset tag will be null if completion is
                    //triggered at the asterisk:
                    //<fx:Declarations>*
                    CompletionList result = new CompletionList();
                    result.setIsIncomplete(false);
                    result.setItems(new ArrayList<>());
                    cancelToken.checkCanceled();
                    return Either.forRight(result);
                }
                IASNode offsetNode = getOffsetNode(path, currentOffset, folderData);
                CompletionList result = actionScriptCompletion(offsetNode, path, position, currentOffset, folderData);
                cancelToken.checkCanceled();
                return Either.forRight(result);
            }
            finally
            {
                completionTypes.clear();
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
                cancelToken.checkCanceled();
                TextDocumentIdentifier textDocument = params.getTextDocument();
                Position position = params.getPosition();
                Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
                if (path == null)
                {
                    cancelToken.checkCanceled();
                    return new Hover(Collections.emptyList(), null);
                }
                WorkspaceFolderData folderData = getWorkspaceFolderDataForSourceFile(path);
                if(folderData == null || folderData.project == null)
                {
                    cancelToken.checkCanceled();
                    return new Hover(Collections.emptyList(), null);
                }
                RoyaleProject project = folderData.project;

                int currentOffset = getOffsetFromPathAndPosition(path, position);
                if (currentOffset == -1)
                {
                    cancelToken.checkCanceled();
                    return new Hover(Collections.emptyList(), null);
                }
                MXMLData mxmlData = getMXMLDataForPath(path, folderData);

                IMXMLTagData offsetTag = getOffsetMXMLTag(mxmlData, currentOffset);
                if (offsetTag != null)
                {
                    IASNode embeddedNode = getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path, currentOffset, folderData);
                    if (embeddedNode != null)
                    {
                        Hover result = actionScriptHover(embeddedNode, project);
                        cancelToken.checkCanceled();
                        return result;
                    }
                    //if we're inside an <fx:Script> tag, we want ActionScript hover,
                    //so that's why we call isMXMLTagValidForCompletion()
                    if (MXMLDataUtils.isMXMLTagValidForCompletion(offsetTag))
                    {
                        Hover result = mxmlHover(offsetTag, currentOffset, project);
                        cancelToken.checkCanceled();
                        return result;
                    }
                }
                IASNode offsetNode = getOffsetNode(path, currentOffset, folderData);
                Hover result = actionScriptHover(offsetNode, project);
                cancelToken.checkCanceled();
                return result;
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
                cancelToken.checkCanceled();
                TextDocumentIdentifier textDocument = params.getTextDocument();
                Position position = params.getPosition();
                Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
                if (path == null)
                {
                    cancelToken.checkCanceled();
                    return new SignatureHelp(Collections.emptyList(), -1, -1);
                }
                WorkspaceFolderData folderData = getWorkspaceFolderDataForSourceFile(path);
                if(folderData == null || folderData.project == null)
                {
                    cancelToken.checkCanceled();
                    return new SignatureHelp(Collections.emptyList(), -1, -1);
                }
                RoyaleProject project = folderData.project;

                int currentOffset = getOffsetFromPathAndPosition(path, position);
                if (currentOffset == -1)
                {
                    cancelToken.checkCanceled();
                    return new SignatureHelp(Collections.emptyList(), -1, -1);
                }
                MXMLData mxmlData = getMXMLDataForPath(path, folderData);

                IASNode offsetNode = null;
                IMXMLTagData offsetTag = getOffsetMXMLTag(mxmlData, currentOffset);
                if (offsetTag != null)
                {
                    IMXMLTagAttributeData attributeData = MXMLDataUtils.getMXMLTagAttributeWithValueAtOffset(offsetTag, currentOffset);
                    if (attributeData != null)
                    {
                        //some attributes can have ActionScript completion, such as
                        //events and properties with data binding
                        IClassDefinition tagDefinition = (IClassDefinition) project.resolveXMLNameToDefinition(offsetTag.getXMLName(), offsetTag.getMXMLDialect());
                        IDefinition attributeDefinition = project.resolveSpecifier(tagDefinition, attributeData.getShortName());
                        if (attributeDefinition instanceof IEventDefinition)
                        {
                            IASNode mxmlOffsetNode = getOffsetNode(path, currentOffset, folderData);
                            if (mxmlOffsetNode instanceof IMXMLClassReferenceNode)
                            {
                                IMXMLClassReferenceNode mxmlNode = (IMXMLClassReferenceNode) mxmlOffsetNode;
                                IMXMLEventSpecifierNode eventNode = mxmlNode.getEventSpecifierNode(attributeData.getShortName());
                                for (IASNode asNode : eventNode.getASNodes())
                                {
                                    IASNode containingNode = ASTUtils.getContainingNodeIncludingStart(asNode, currentOffset);
                                    if (containingNode != null)
                                    {
                                        offsetNode = containingNode;
                                    }
                                }
                                if (offsetNode == null)
                                {
                                    offsetNode = eventNode;
                                }
                            }
                        }
                    }
                }
                if (offsetNode == null)
                {
                    offsetNode = getOffsetNode(path, currentOffset, folderData);
                }
                if (offsetNode == null)
                {
                    cancelToken.checkCanceled();
                    //we couldn't find a node at the specified location
                    return new SignatureHelp(Collections.emptyList(), -1, -1);
                }

                IFunctionCallNode functionCallNode = getAncestorFunctionCallNode(offsetNode);
                IFunctionDefinition functionDefinition = null;
                if (functionCallNode != null)
                {
                    IExpressionNode nameNode = functionCallNode.getNameNode();
                    IDefinition definition = nameNode.resolve(project);
                    if (definition instanceof IFunctionDefinition)
                    {
                        functionDefinition = (IFunctionDefinition) definition;
                    }
                    else if (definition instanceof IClassDefinition)
                    {
                        IClassDefinition classDefinition = (IClassDefinition) definition;
                        functionDefinition = classDefinition.getConstructor();
                    }
                    else if (nameNode instanceof IIdentifierNode)
                    {
                        //special case for super()
                        IIdentifierNode identifierNode = (IIdentifierNode) nameNode;
                        if (identifierNode.getName().equals(IASKeywordConstants.SUPER))
                        {
                            ITypeDefinition typeDefinition = nameNode.resolveType(project);
                            if (typeDefinition instanceof IClassDefinition)
                            {
                                IClassDefinition classDefinition = (IClassDefinition) typeDefinition;
                                functionDefinition = classDefinition.getConstructor();
                            }
                        }
                    }
                }
                if (functionDefinition != null)
                {
                    SignatureHelp result = new SignatureHelp();
                    List<SignatureInformation> signatures = new ArrayList<>();

                    SignatureInformation signatureInfo = new SignatureInformation();
                    signatureInfo.setLabel(DefinitionTextUtils.functionDefinitionToSignature(functionDefinition, project));
                    String docs = DefinitionDocumentationUtils.getDocumentationForDefinition(functionDefinition, true);
                    if (docs != null)
                    {
                        signatureInfo.setDocumentation(docs);
                    }

                    List<ParameterInformation> parameters = new ArrayList<>();
                    for (IParameterDefinition param : functionDefinition.getParameters())
                    {
                        ParameterInformation paramInfo = new ParameterInformation();
                        paramInfo.setLabel(param.getBaseName());
                        String paramDocs = DefinitionDocumentationUtils.getDocumentationForParameter(param, true);
                        if (paramDocs != null)
                        {
                            paramInfo.setDocumentation(paramDocs);
                        }
                        parameters.add(paramInfo);
                    }
                    signatureInfo.setParameters(parameters);
                    signatures.add(signatureInfo);
                    result.setSignatures(signatures);
                    result.setActiveSignature(0);

                    int index = getFunctionCallNodeArgumentIndex(functionCallNode, offsetNode);
                    IParameterDefinition[] parameterDefs = functionDefinition.getParameters();
                    int paramCount = parameterDefs.length;
                    if (paramCount > 0 && index >= paramCount)
                    {
                        if (index >= paramCount)
                        {
                            IParameterDefinition lastParam = parameterDefs[paramCount - 1];
                            if (lastParam.isRest())
                            {
                                //functions with rest parameters may accept any
                                //number of arguments, so continue to make the rest
                                //parameter active
                                index = paramCount - 1;
                            }
                            else
                            {
                                //if there's no rest parameter, and we're beyond the
                                //final parameter, none should be active
                                index = -1;
                            }
                        }
                    }
                    if (index != -1)
                    {
                        result.setActiveParameter(index);
                    }
                    cancelToken.checkCanceled();
                    return result;
                }
                cancelToken.checkCanceled();
                return new SignatureHelp(Collections.emptyList(), -1, -1);
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
    public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams params)
    {
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken ->
        {
            cancelToken.checkCanceled();

            compilerWorkspace.startBuilding();
            try
            {
                cancelToken.checkCanceled();
                TextDocumentIdentifier textDocument = params.getTextDocument();
                Position position = params.getPosition();
                Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
                if (path == null)
                {
                    cancelToken.checkCanceled();
                    return Collections.emptyList();
                }
                WorkspaceFolderData folderData = getWorkspaceFolderDataForSourceFile(path);
                if(folderData == null || folderData.project == null)
                {
                    cancelToken.checkCanceled();
                    return Collections.emptyList();
                }
                RoyaleProject project = folderData.project;

                int currentOffset = getOffsetFromPathAndPosition(path, position);
                if (currentOffset == -1)
                {
                    cancelToken.checkCanceled();
                    return Collections.emptyList();
                }
                MXMLData mxmlData = getMXMLDataForPath(path, folderData);

                IMXMLTagData offsetTag = getOffsetMXMLTag(mxmlData, currentOffset);
                if (offsetTag != null)
                {
                    IASNode embeddedNode = getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path, currentOffset, folderData);
                    if (embeddedNode != null)
                    {
                        List<? extends Location> result = actionScriptDefinition(embeddedNode, project);
                        cancelToken.checkCanceled();
                        return result;
                    }
                    //if we're inside an <fx:Script> tag, we want ActionScript lookup,
                    //so that's why we call isMXMLTagValidForCompletion()
                    if (MXMLDataUtils.isMXMLTagValidForCompletion(offsetTag))
                    {
                        List<? extends Location> result = mxmlDefinition(offsetTag, currentOffset, project);
                        cancelToken.checkCanceled();
                        return result;
                    }
                }
                IASNode offsetNode = getOffsetNode(path, currentOffset, folderData);
                List<? extends Location> result = actionScriptDefinition(offsetNode, project);
                cancelToken.checkCanceled();
                return result;
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
    public CompletableFuture<List<? extends Location>> typeDefinition(TextDocumentPositionParams params)
    {
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken ->
        {
            cancelToken.checkCanceled();

            compilerWorkspace.startBuilding();
            try
            {
                cancelToken.checkCanceled();
                TextDocumentIdentifier textDocument = params.getTextDocument();
                Position position = params.getPosition();
                Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
                if (path == null)
                {
                    cancelToken.checkCanceled();
                    return Collections.emptyList();
                }
                WorkspaceFolderData folderData = getWorkspaceFolderDataForSourceFile(path);
                if(folderData == null || folderData.project == null)
                {
                    cancelToken.checkCanceled();
                    return Collections.emptyList();
                }
                RoyaleProject project = folderData.project;

                int currentOffset = getOffsetFromPathAndPosition(path, position);
                if (currentOffset == -1)
                {
                    cancelToken.checkCanceled();
                    return Collections.emptyList();
                }
                MXMLData mxmlData = getMXMLDataForPath(path, folderData);

                IMXMLTagData offsetTag = getOffsetMXMLTag(mxmlData, currentOffset);
                if (offsetTag != null)
                {
                    IASNode embeddedNode = getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path, currentOffset, folderData);
                    if (embeddedNode != null)
                    {
                        List<? extends Location> result = actionScriptTypeDefinition(embeddedNode, project);
                        cancelToken.checkCanceled();
                        return result;
                    }
                    //if we're inside an <fx:Script> tag, we want ActionScript lookup,
                    //so that's why we call isMXMLTagValidForCompletion()
                    if (MXMLDataUtils.isMXMLTagValidForCompletion(offsetTag))
                    {
                        List<? extends Location> result = mxmlTypeDefinition(offsetTag, currentOffset, project);
                        cancelToken.checkCanceled();
                        return result;
                    }
                }
                IASNode offsetNode = getOffsetNode(path, currentOffset, folderData);
                List<? extends Location> result = actionScriptTypeDefinition(offsetNode, project);
                cancelToken.checkCanceled();
                return result;
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
    public CompletableFuture<List<? extends Location>> implementation(TextDocumentPositionParams params)
    {
        return CompletableFutures.computeAsync(compilerWorkspace.getExecutorService(), cancelToken ->
        {
            cancelToken.checkCanceled();

            compilerWorkspace.startBuilding();
            try
            {
                cancelToken.checkCanceled();
                TextDocumentIdentifier textDocument = params.getTextDocument();
                Position position = params.getPosition();
                Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
                if (path == null)
                {
                    cancelToken.checkCanceled();
                    return Collections.emptyList();
                }
                WorkspaceFolderData folderData = getWorkspaceFolderDataForSourceFile(path);
                if(folderData == null || folderData.project == null)
                {
                    cancelToken.checkCanceled();
                    return Collections.emptyList();
                }
                RoyaleProject project = folderData.project;

                int currentOffset = getOffsetFromPathAndPosition(path, position);
                if (currentOffset == -1)
                {
                    cancelToken.checkCanceled();
                    return Collections.emptyList();
                }
                MXMLData mxmlData = getMXMLDataForPath(path, folderData);

                IMXMLTagData offsetTag = getOffsetMXMLTag(mxmlData, currentOffset);
                if (offsetTag != null)
                {
                    IASNode embeddedNode = getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path, currentOffset, folderData);
                    if (embeddedNode != null)
                    {
                        List<? extends Location> result = actionScriptImplementation(embeddedNode, project);
                        cancelToken.checkCanceled();
                        return result;
                    }
                }
                IASNode offsetNode = getOffsetNode(path, currentOffset, folderData);
                List<? extends Location> result = actionScriptImplementation(offsetNode, project);
                cancelToken.checkCanceled();
                return result;
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
                cancelToken.checkCanceled();
                TextDocumentIdentifier textDocument = params.getTextDocument();
                Position position = params.getPosition();
                Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
                if (path == null)
                {
                    cancelToken.checkCanceled();
                    return Collections.emptyList();
                }
                WorkspaceFolderData folderData = getWorkspaceFolderDataForSourceFile(path);
                if(folderData == null || folderData.project == null)
                {
                    cancelToken.checkCanceled();
                    return Collections.emptyList();
                }
                RoyaleProject project = folderData.project;

                int currentOffset = getOffsetFromPathAndPosition(path, position);
                if (currentOffset == -1)
                {
                    cancelToken.checkCanceled();
                    return Collections.emptyList();
                }
                MXMLData mxmlData = getMXMLDataForPath(path, folderData);

                IMXMLTagData offsetTag = getOffsetMXMLTag(mxmlData, currentOffset);
                if (offsetTag != null)
                {
                    IASNode embeddedNode = getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path, currentOffset, folderData);
                    if (embeddedNode != null)
                    {
                        List<? extends Location> result = actionScriptReferences(embeddedNode, project);
                        cancelToken.checkCanceled();
                        return result;
                    }
                    //if we're inside an <fx:Script> tag, we want ActionScript lookup,
                    //so that's why we call isMXMLTagValidForCompletion()
                    if (MXMLDataUtils.isMXMLTagValidForCompletion(offsetTag))
                    {
                        ICompilationUnit offsetUnit = findCompilationUnit(path);
                        List<? extends Location> result = mxmlReferences(offsetTag, currentOffset, offsetUnit, project);
                        cancelToken.checkCanceled();
                        return result;
                    }
                }
                IASNode offsetNode = getOffsetNode(path, currentOffset, folderData);
                List<? extends Location> result = actionScriptReferences(offsetNode, project);
                cancelToken.checkCanceled();
                return result;
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
                cancelToken.checkCanceled();
                Set<String> qualifiedNames = new HashSet<>();
                List<SymbolInformation> result = new ArrayList<>();
                String query = params.getQuery();
                StringBuilder currentQuery = new StringBuilder();
                List<String> queries = new ArrayList<>();
                for(int i = 0, length = query.length(); i < length; i++)
                {
                    String charAtI = query.substring(i, i + 1);
                    if(i > 0 && charAtI.toUpperCase().equals(charAtI))
                    {
                        queries.add(currentQuery.toString().toLowerCase());
                        currentQuery = new StringBuilder();
                    }
                    currentQuery.append(charAtI);
                }
                if(currentQuery.length() > 0)
                {
                    queries.add(currentQuery.toString().toLowerCase());
                }
                for (WorkspaceFolderData folderData : workspaceFolderToData.values())
                {
                    RoyaleProject project = folderData.project;
                    if (project == null)
                    {
                        continue;
                    }
                    for (ICompilationUnit unit : project.getCompilationUnits())
                    {
                        if (unit == null || unit instanceof ResourceBundleCompilationUnit)
                        {
                            continue;
                        }
                        if (unit instanceof SWCCompilationUnit)
                        {
                            List<IDefinition> definitions = unit.getDefinitionPromises();
                            for (IDefinition definition : definitions)
                            {
                                if (definition instanceof DefinitionPromise)
                                {
                                    //we won't be able to detect what type of definition
                                    //this is without getting the actual definition from the
                                    //promise.
                                    DefinitionPromise promise = (DefinitionPromise) definition;
                                    definition = promise.getActualDefinition();
                                }
                                if (definition.isImplicit())
                                {
                                    continue;
                                }
                                if (!matchesQueries(queries, definition.getQualifiedName()))
                                {
                                    continue;
                                }
                                String qualifiedName = definition.getQualifiedName();
                                if (qualifiedNames.contains(qualifiedName))
                                {
                                    //we've already added this symbol
                                    //this can happen when there are multiple root
                                    //folders in the workspace
                                    continue;
                                }
                                SymbolInformation symbol = definitionToSymbolInformation(definition, project);
                                if (symbol != null)
                                {
                                    qualifiedNames.add(qualifiedName);
                                    result.add(symbol);
                                }
                            }
                        }
                        else
                        {
                            IASScope[] scopes;
                            try
                            {
                                scopes = unit.getFileScopeRequest().get().getScopes();
                            }
                            catch (Exception e)
                            {
                                return Collections.emptyList();
                            }
                            for (IASScope scope : scopes)
                            {
                                querySymbolsInScope(queries, scope, qualifiedNames, project, result);
                            }
                        }
                    }
                }
                cancelToken.checkCanceled();
                return result;
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
                cancelToken.checkCanceled();
                TextDocumentIdentifier textDocument = params.getTextDocument();
                Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
                if (path == null)
                {
                    cancelToken.checkCanceled();
                    return Collections.emptyList();
                }
                WorkspaceFolderData folderData = getWorkspaceFolderDataForSourceFile(path);
                if(folderData == null || folderData.project == null)
                {
                    cancelToken.checkCanceled();
                    return Collections.emptyList();
                }
                RoyaleProject project = folderData.project;

                ICompilationUnit unit = findCompilationUnit(path, project);
                if (unit == null)
                {
                    cancelToken.checkCanceled();
                    //we couldn't find a compilation unit with the specified path
                    return Collections.emptyList();
                }

                IASScope[] scopes;
                try
                {
                    scopes = unit.getFileScopeRequest().get().getScopes();
                }
                catch (Exception e)
                {
                    cancelToken.checkCanceled();
                    return Collections.emptyList();
                }
                List<Either<SymbolInformation, DocumentSymbol>> result = new ArrayList<>();
                if (clientCapabilities.getTextDocument().getDocumentSymbol().getHierarchicalDocumentSymbolSupport())
                {
                    List<DocumentSymbol> symbols = new ArrayList<>();
                    for (IASScope scope : scopes)
                    {
                        scopeToDocumentSymbols(scope, project, symbols);
                    }
                    for (DocumentSymbol symbol : symbols)
                    {
                        result.add(Either.forRight(symbol));
                    }
                }
                else //fallback to non-hierarchical
                {
                    List<SymbolInformation> symbols = new ArrayList<>();
                    for (IASScope scope : scopes)
                    {
                        scopeToSymbolInformation(scope, project, symbols);
                    }
                    for (SymbolInformation symbol : symbols)
                    {
                        result.add(Either.forLeft(symbol));
                    }
                }
                cancelToken.checkCanceled();
                return result;
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
                if (!sourceByPath.containsKey(path))
                {
                    cancelToken.checkCanceled();
                    return Collections.emptyList();
                }
                WorkspaceFolderData folderData = getWorkspaceFolderDataForSourceFile(path);
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

                ICompilationUnit unit = findCompilationUnit(path, project);
                if (unit != null)
                {
                    IASNode ast = getAST(unit);
                    if (ast != null)
                    {
                        String fileText = sourceByPath.get(path);
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
            }
        }
    }

    private void createCodeActionForMissingField(Path path, Diagnostic diagnostic, WorkspaceFolderData folderData, List<Either<Command, CodeAction>> codeActions)
    {
        Position position = diagnostic.getRange().getStart();
        int currentOffset = getOffsetFromPathAndPosition(path, position);
        IASNode offsetNode = getOffsetNode(path, currentOffset, folderData);
        if (offsetNode instanceof IMXMLInstanceNode)
        {
            MXMLData mxmlData = getMXMLDataForPath(path, folderData);
            if (mxmlData != null)
            {
                IMXMLTagData offsetTag = getOffsetMXMLTag(mxmlData, currentOffset);
                //workaround for bug in Royale compiler
                Position newPosition = new Position(position.getLine(), position.getCharacter() + 1);
                int newOffset = getOffsetFromPathAndPosition(path, newPosition);
                offsetNode = getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path, newOffset, folderData);
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
        String fileText = getFileTextForPath(path);
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
        int currentOffset = getOffsetFromPathAndPosition(path, position);
        IASNode offsetNode = getOffsetNode(path, currentOffset, folderData);
        if (offsetNode instanceof IMXMLInstanceNode)
        {
            MXMLData mxmlData = getMXMLDataForPath(path, folderData);
            if (mxmlData != null)
            {
                IMXMLTagData offsetTag = getOffsetMXMLTag(mxmlData, currentOffset);
                //workaround for bug in Royale compiler
                Position newPosition = new Position(position.getLine(), position.getCharacter() + 1);
                int newOffset = getOffsetFromPathAndPosition(path, newPosition);
                offsetNode = getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path, newOffset, folderData);
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
        String fileText = getFileTextForPath(path);
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
        int currentOffset = getOffsetFromPathAndPosition(path, position);
        IASNode offsetNode = getOffsetNode(path, currentOffset, folderData);
        if(!(offsetNode instanceof ITryNode))
        {
            return;
        }
        ITryNode tryNode = (ITryNode) offsetNode;
        String fileText = getFileTextForPath(path);
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

    private void createCodeActionForMissingMethod(Path path, Diagnostic diagnostic, WorkspaceFolderData folderData, List<Either<Command, CodeAction>> codeActions)
    {
        RoyaleProject project = folderData.project;
        Position position = diagnostic.getRange().getStart();
        int currentOffset = getOffsetFromPathAndPosition(path, position);
        IASNode offsetNode = getOffsetNode(path, currentOffset, folderData);
        if (offsetNode == null)
        {
            return;
        }
        if (offsetNode instanceof IMXMLInstanceNode)
        {
            MXMLData mxmlData = getMXMLDataForPath(path, folderData);
            if (mxmlData != null)
            {
                IMXMLTagData offsetTag = getOffsetMXMLTag(mxmlData, currentOffset);
                //workaround for bug in Royale compiler
                Position newPosition = new Position(position.getLine(), position.getCharacter() + 1);
                int newOffset = getOffsetFromPathAndPosition(path, newPosition);
                offsetNode = getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path, newOffset, folderData);
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
        String fileText = getFileTextForPath(path);
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

    private void createCodeActionsForImport(Path path, Diagnostic diagnostic, WorkspaceFolderData folderData, List<Either<Command, CodeAction>> codeActions)
    {
        RoyaleProject project = folderData.project;
        Position position = diagnostic.getRange().getStart();
        int currentOffset = getOffsetFromPathAndPosition(path, position);
        IASNode offsetNode = getOffsetNode(path, currentOffset, folderData);
        IMXMLTagData offsetTag = null;
        boolean isMXML = path.toUri().toString().endsWith(MXML_EXTENSION);
        if (isMXML)
        {
            MXMLData mxmlData = getMXMLDataForPath(path, folderData);
            if (mxmlData != null)
            {
                offsetTag = getOffsetMXMLTag(mxmlData, currentOffset);
            }
        }
        if (offsetNode instanceof IMXMLInstanceNode && offsetTag != null)
        {
            //workaround for bug in Royale compiler
            Position newPosition = new Position(position.getLine(), position.getCharacter() + 1);
            int newOffset = getOffsetFromPathAndPosition(path, newPosition);
            offsetNode = getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path, newOffset, folderData);
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
        String fileText = getFileTextForPath(path);
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
                cancelToken.checkCanceled();
                TextDocumentIdentifier textDocument = params.getTextDocument();
                Position position = params.getPosition();
                Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
                if (path == null)
                {
                    cancelToken.checkCanceled();
                    return new WorkspaceEdit(new HashMap<>());
                }
                WorkspaceFolderData folderData = getWorkspaceFolderDataForSourceFile(path);
                if(folderData == null || folderData.project == null)
                {
                    cancelToken.checkCanceled();
                    return new WorkspaceEdit(new HashMap<>());
                }
                RoyaleProject project = folderData.project;

                int currentOffset = getOffsetFromPathAndPosition(path, position);
                if (currentOffset == -1)
                {
                    cancelToken.checkCanceled();
                    return new WorkspaceEdit(new HashMap<>());
                }

                MXMLData mxmlData = getMXMLDataForPath(path, folderData);
                IMXMLTagData offsetTag = getOffsetMXMLTag(mxmlData, currentOffset);
                if (offsetTag != null)
                {
                    IASNode embeddedNode = getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path, currentOffset, folderData);
                    if (embeddedNode != null)
                    {
                        WorkspaceEdit result = actionScriptRename(embeddedNode, params.getNewName(), project);
                        cancelToken.checkCanceled();
                        return result;
                    }
                    //if we're inside an <fx:Script> tag, we want ActionScript rename,
                    //so that's why we call isMXMLTagValidForCompletion()
                    if (MXMLDataUtils.isMXMLTagValidForCompletion(offsetTag))
                    {
                        WorkspaceEdit result = mxmlRename(offsetTag, currentOffset, params.getNewName(), project);
                        cancelToken.checkCanceled();
                        return result;
                    }
                }
                IASNode offsetNode = getOffsetNode(path, currentOffset, folderData);
                WorkspaceEdit result = actionScriptRename(offsetNode, params.getNewName(), project);
                cancelToken.checkCanceled();
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
        sourceByPath.put(path, text);

        WorkspaceFolderData folderData = getWorkspaceFolderDataForSourceFile(path);
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
        IFileSpecification fileSpec = fileSpecGetter.getFileSpecification(normalizedPath);
        compilerWorkspace.fileChanged(fileSpec);

        //if it's an included file, switch to the parent file
        IncludeFileData includeFileData = includedFiles.get(path.toString());
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
                sourceByPath.put(path, change.getText());
            }
            else if(sourceByPath.containsKey(path))
            {
                String existingText = sourceByPath.get(path);
                String newText = patch(existingText, change);
                sourceByPath.put(path, newText);
            }
            else
            {
                System.err.println("Failed to apply changes to code intelligence from URI: " + textDocumentUri);
            }
        }

        WorkspaceFolderData folderData = getWorkspaceFolderDataForSourceFile(path);
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
            unit = findCompilationUnit(path, project);
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

        IFileSpecification fileSpec = fileSpecGetter.getFileSpecification(normalizedChangedPathAsString);
        compilerWorkspace.fileChanged(fileSpec);

        compilerWorkspace.startBuilding();
        try
        {
            //if it's an included file, switch to the parent file
            IncludeFileData includeFileData = includedFiles.get(path.toString());
            if (includeFileData != null)
            {
                path = Paths.get(includeFileData.parentPath);
                unit = findCompilationUnit(path, project);
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

        sourceByPath.remove(path);

        boolean clearProblems = false;

        WorkspaceFolderData folderData = getWorkspaceFolderDataForSourceFile(path);
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
        IncludeFileData includeFileData = includedFiles.get(path.toString());
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
        WorkspaceFolderData folderData = getWorkspaceFolderDataForSourceFile(path);
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
        IncludeFileData includeFileData = includedFiles.get(path.toString());
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
            for (WorkspaceFolderData folderData : workspaceFolderToData.values())
            {
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
                List<WorkspaceFolderData> allFolderData = getAllWorkspaceFolderDataForSWCFile(Paths.get(normalizedChangedPathAsString));
                if (allFolderData.size() > 0)
                {
                    compilerWorkspace.startBuilding();
                    ICompilationUnit changedUnit = null;
                    try
                    {
                        changedUnit = findCompilationUnit(normalizedChangedPathAsString);
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
                    IFileSpecification swcFileSpec = fileSpecGetter.getFileSpecification(normalizedChangedPathAsString);
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
                compilerWorkspace.startBuilding();
                ICompilationUnit changedUnit = null;
                try
                {
                    changedUnit = findCompilationUnit(normalizedChangedPathAsString);
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
                List<WorkspaceFolderData> allFolderData = getAllWorkspaceFolderDataForSourceFile(changedPath);
                if (changeType.equals(FileChangeType.Deleted) ||

                    //this is weird, but it's possible for a renamed file to
                    //result in a Changed event, but not a Deleted event
                    (changeType.equals(FileChangeType.Changed) && !java.nio.file.Files.exists(changedPath))
                )
                {
                    IFileSpecification fileSpec = fileSpecGetter.getFileSpecification(normalizedChangedPathAsString);
                    compilerWorkspace.fileRemoved(fileSpec);
                    //deleting a file may change errors in other existing files,
                    //so we need to do a full check
                    foldersToCheck.addAll(allFolderData);
                }
                else if (event.getType().equals(FileChangeType.Created))
                {
                    IFileSpecification fileSpec = fileSpecGetter.getFileSpecification(normalizedChangedPathAsString);
                    compilerWorkspace.fileAdded(fileSpec);
                    //creating a file may change errors in other existing files,
                    //so we need to do a full check
                    foldersToCheck.addAll(allFolderData);
                }
                else if (changeType.equals(FileChangeType.Changed))
                {
                    IFileSpecification fileSpec = fileSpecGetter.getFileSpecification(normalizedChangedPathAsString);
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
                                IFileSpecification fileSpec = fileSpecGetter.getFileSpecification(normalizedSubPath);
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
                
                for (WorkspaceFolderData folderData : workspaceFolderToData.values())
                {
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
                    compilerWorkspace.startBuilding();
                    ICompilationUnit unit = null;
                    try
                    {
                        unit = findCompilationUnit(fileToRemove);
                    }
                    finally
                    {
                        compilerWorkspace.doneBuilding();
                    }
                    if (unit != null)
                    {
                        fileToRemove = unit.getAbsoluteFilename();
                    }
                    IFileSpecification fileSpec = fileSpecGetter.getFileSpecification(fileToRemove);
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
        for (WorkspaceFolderData folderData : workspaceFolderToData.values())
        {
            checkProjectForProblems(folderData);
        }
    }

    /**
     * Called if something in the configuration has changed.
     */
    public void checkForProblemsNow()
    {
        updateFrameworkSDK();
        for (WorkspaceFolderData folderData : workspaceFolderToData.values())
        {
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
                for (WorkspaceFolder workspaceFolder : workspaceFolders)
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
                for (WorkspaceFolder workspaceFolder : workspaceFolders)
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
                for (WorkspaceFolder workspaceFolder : workspaceFolders)
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
                        for (WorkspaceFolderData folderData : workspaceFolderToData.values())
                        {
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

    private CompletionList actionScriptCompletion(IASNode offsetNode, Path path, Position position, int currentOffset, WorkspaceFolderData folderData)
    {
        CompletionList result = new CompletionList();
        result.setIsIncomplete(false);
        result.setItems(new ArrayList<>());
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return result;
        }
        IASNode parentNode = offsetNode.getParent();
        IASNode nodeAtPreviousOffset = null;
        if (parentNode != null)
        {
            nodeAtPreviousOffset = parentNode.getContainingNode(currentOffset - 1);
        }

        if (!isActionScriptCompletionAllowedInNode(path, offsetNode, currentOffset))
        {
            //if we're inside a node that shouldn't have completion!
            return result;
        }
        boolean isMXML = path.toString().endsWith(MXML_EXTENSION);
        ImportRange importRange = ImportRange.fromOffsetNode(offsetNode);
        if (isMXML)
        {
            IMXMLTagData offsetTag = null;
            MXMLData mxmlData = getMXMLDataForPath(path, folderData);
            if (mxmlData != null)
            {
                offsetTag = getOffsetMXMLTag(mxmlData, currentOffset);
            }
            if (offsetTag != null)
            {
                importRange = ImportRange.fromOffsetTag(offsetTag, currentOffset);
            }
        }
        RoyaleProject project = folderData.project;
        String fileText = getFileTextForPath(path);
        AddImportData addImportData = CodeActionsUtils.findAddImportData(fileText, importRange);

        //variable types
        if (offsetNode instanceof IVariableNode)
        {
            IVariableNode variableNode = (IVariableNode) offsetNode;
            IExpressionNode nameExpression = variableNode.getNameExpressionNode();
            IExpressionNode typeNode = variableNode.getVariableTypeNode();
            int line = position.getLine();
            int column = position.getCharacter();
            if (line >= nameExpression.getEndLine() && line <= typeNode.getLine())
            {
                if ((line != nameExpression.getEndLine() && line != typeNode.getLine())
                        || (line == nameExpression.getEndLine() && column > nameExpression.getEndColumn())
                        || (line == typeNode.getLine() && column <= typeNode.getColumn()))
                {
                    autoCompleteTypes(offsetNode, addImportData, project, result);
                }
                return result;
            }
        }
        if (parentNode != null
                && parentNode instanceof IVariableNode)
        {
            IVariableNode variableNode = (IVariableNode) parentNode;
            if (offsetNode == variableNode.getVariableTypeNode())
            {
                autoCompleteTypes(parentNode, addImportData, project, result);
                return result;
            }
        }
        //function return types
        if (offsetNode instanceof IFunctionNode)
        {
            IFunctionNode functionNode = (IFunctionNode) offsetNode;
            IContainerNode parameters = functionNode.getParametersContainerNode();
            IExpressionNode typeNode = functionNode.getReturnTypeNode();
            if (typeNode != null)
            {
                int line = position.getLine();
                int column = position.getCharacter();
                if (line >= parameters.getEndLine()
                        && column > parameters.getEndColumn()
                        && line <= typeNode.getLine()
                        && column <= typeNode.getColumn())
                {
                    autoCompleteTypes(offsetNode, addImportData, project, result);
                    return result;
                }
            }
        }
        if (parentNode != null
                && parentNode instanceof IFunctionNode)
        {
            IFunctionNode functionNode = (IFunctionNode) parentNode;
            if (offsetNode == functionNode.getReturnTypeNode())
            {
                autoCompleteTypes(parentNode, addImportData, project, result);
                return result;
            }
        }
        //new keyword types
        if (parentNode != null
                && parentNode instanceof IFunctionCallNode)
        {
            IFunctionCallNode functionCallNode = (IFunctionCallNode) parentNode;
            if (functionCallNode.getNameNode() == offsetNode
                    && functionCallNode.isNewExpression())
            {
                autoCompleteTypes(parentNode, addImportData, project, result);
                return result;
            }
        }
        if (nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IKeywordNode
                && nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordNewID)
        {
            autoCompleteTypes(nodeAtPreviousOffset, addImportData, project, result);
            return result;
        }
        //as and is keyword types
        if (parentNode != null
                && parentNode instanceof IBinaryOperatorNode
                && (parentNode.getNodeID() == ASTNodeID.Op_AsID
                || parentNode.getNodeID() == ASTNodeID.Op_IsID))
        {
            IBinaryOperatorNode binaryOperatorNode = (IBinaryOperatorNode) parentNode;
            if (binaryOperatorNode.getRightOperandNode() == offsetNode)
            {
                autoCompleteTypes(parentNode, addImportData, project, result);
                return result;
            }
        }
        if (nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IBinaryOperatorNode
                && (nodeAtPreviousOffset.getNodeID() == ASTNodeID.Op_AsID
                || nodeAtPreviousOffset.getNodeID() == ASTNodeID.Op_IsID))
        {
            autoCompleteTypes(nodeAtPreviousOffset, addImportData, project, result);
            return result;
        }
        //class extends keyword
        if (offsetNode instanceof IClassNode
                && nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IKeywordNode
                && nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordExtendsID)
        {
            autoCompleteTypes(offsetNode, addImportData, project, result);
            return result;
        }
        //class implements keyword
        if (offsetNode instanceof IClassNode
                && nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IKeywordNode
                && nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordImplementsID)
        {
            autoCompleteTypes(offsetNode, addImportData, project, result);
            return result;
        }
        //interface extends keyword
        if (offsetNode instanceof IInterfaceNode
                && nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IKeywordNode
                && nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordExtendsID)
        {
            autoCompleteTypes(offsetNode, addImportData, project, result);
            return result;
        }

        //package (must be before member access)
        if (offsetNode instanceof IFileNode)
        {
            IFileNode fileNode = (IFileNode) offsetNode;
            if (fileNode.getChildCount() == 0 && fileNode.getAbsoluteEnd() == 0)
            {
                //the file is completely empty
                autoCompletePackageBlock(fileNode.getFileSpecification(), project, result);
                return result;
            }
        }
        if (parentNode != null
                && parentNode instanceof IFileNode)
        {
            IFileNode fileNode = (IFileNode) parentNode;
            if (fileNode.getChildCount() == 1 && offsetNode instanceof IIdentifierNode)
            {
                IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
                String identifier = identifierNode.getName();
                if (IASKeywordConstants.PACKAGE.startsWith(identifier))
                {
                    //the file contains only a substring of the package keyword
                    autoCompletePackageBlock(offsetNode.getFileSpecification(), project, result);
                    return result;
                }
            }
        }
        if (offsetNode instanceof IPackageNode)
        {
            IPackageNode packageNode = (IPackageNode) offsetNode;
            autoCompletePackageName(packageNode.getPackageName(), offsetNode.getFileSpecification(), project, result);
            return result;
        }
        if (parentNode != null
                && parentNode instanceof FullNameNode)
        {
            IASNode gpNode = parentNode.getParent();
            if (gpNode != null && gpNode instanceof IPackageNode)
            {
                IPackageNode packageNode = (IPackageNode) gpNode;
                autoCompletePackageName(packageNode.getPackageName(), offsetNode.getFileSpecification(), project, result);
            }
        }
        if (parentNode != null
                && parentNode instanceof IPackageNode)
        {
            //we'll get here if the last character in the package name is .
            IPackageNode packageNode = (IPackageNode) parentNode;
            IExpressionNode nameNode = packageNode.getNameExpressionNode();
            if (offsetNode == nameNode)
            {
                if (currentOffset == IASKeywordConstants.PACKAGE.length())
                {
                    autoCompletePackageBlock(offsetNode.getFileSpecification(), project, result);
                }
                else
                {
                    autoCompletePackageName(packageNode.getPackageName(), offsetNode.getFileSpecification(), project, result);
                }
                return result;
            }
        }

        //import (must be before member access)
        if (parentNode != null
                && parentNode instanceof IImportNode)
        {
            IImportNode importNode = (IImportNode) parentNode;
            IExpressionNode nameNode = importNode.getImportNameNode();
            if (offsetNode == nameNode)
            {
                String importName = importNode.getImportName();
                importName = importName.substring(0, position.getCharacter() - nameNode.getColumn());
                autoCompleteImport(importName, project, result);
                return result;
            }
        }
        if (parentNode != null
                && parentNode instanceof FullNameNode)
        {
            IASNode gpNode = parentNode.getParent();
            if (gpNode != null && gpNode instanceof IImportNode)
            {
                IImportNode importNode = (IImportNode) gpNode;
                IExpressionNode nameNode = importNode.getImportNameNode();
                if (parentNode == nameNode)
                {
                    String importName = importNode.getImportName();
                    importName = importName.substring(0, position.getCharacter() - nameNode.getColumn());
                    autoCompleteImport(importName, project, result);
                    return result;
                }
            }
        }
        if (nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IImportNode)
        {
            autoCompleteImport("", project, result);
            return result;
        }

        //member access
        if (offsetNode instanceof IMemberAccessExpressionNode)
        {
            IMemberAccessExpressionNode memberAccessNode = (IMemberAccessExpressionNode) offsetNode;
            IExpressionNode leftOperand = memberAccessNode.getLeftOperandNode();
            IExpressionNode rightOperand = memberAccessNode.getRightOperandNode();
            int line = position.getLine();
            int column = position.getCharacter();
            if (line >= leftOperand.getEndLine() && line <= rightOperand.getLine())
            {
                if ((line != leftOperand.getEndLine() && line != rightOperand.getLine())
                        || (line == leftOperand.getEndLine() && column > leftOperand.getEndColumn())
                        || (line == rightOperand.getLine() && column <= rightOperand.getColumn()))
                {
                    autoCompleteMemberAccess(memberAccessNode, addImportData, project, result);
                    return result;
                }
            }
        }
        if (parentNode != null
                && parentNode instanceof IMemberAccessExpressionNode)
        {
            IMemberAccessExpressionNode memberAccessNode = (IMemberAccessExpressionNode) parentNode;
            //you would expect that the offset node could only be the right
            //operand, but it's actually possible for it to be the left operand,
            //even if the . has been typed! only sometimes, though.
            boolean isValidLeft = true;
            if (offsetNode == memberAccessNode.getLeftOperandNode()
                && memberAccessNode.getRightOperandNode() instanceof IIdentifierNode)
            {
                //if the left and right operands both exist, then this is not
                //member access and we should skip ahead
                isValidLeft = false;
            }
            if (offsetNode == memberAccessNode.getRightOperandNode()
                    || isValidLeft)
            {
                autoCompleteMemberAccess(memberAccessNode, addImportData, project, result);
                return result;
            }
        }
        if (nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IMemberAccessExpressionNode)
        {
            //depending on the left operand, if a . is typed, the member access
            //may end up being the previous node instead of the parent or offset
            //node, so check if the right operand is empty
            IMemberAccessExpressionNode memberAccessNode = (IMemberAccessExpressionNode) nodeAtPreviousOffset;
            IExpressionNode rightOperandNode = memberAccessNode.getRightOperandNode();
            if (rightOperandNode instanceof IIdentifierNode)
            {
                IIdentifierNode identifierNode = (IIdentifierNode) rightOperandNode;
                if (identifierNode.getName().equals(""))
                {
                    autoCompleteMemberAccess(memberAccessNode, addImportData, project, result);
                    return result;
                }
            }
        }

        //function overrides
        if (parentNode != null
            && parentNode instanceof IFunctionNode
            && offsetNode instanceof IIdentifierNode)
        {
            IFunctionNode functionNode = (IFunctionNode) parentNode;
            if (offsetNode == functionNode.getNameExpressionNode())
            {
                if (functionNode.hasModifier(ASModifier.OVERRIDE)
                    && functionNode.getParametersContainerNode().getAbsoluteStart() == -1
                    && functionNode.getReturnTypeNode() == null)
                {
                    autoCompleteFunctionOverrides(functionNode, project, result);
                    return result;
                }
            }
        }
        if (nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IKeywordNode
                && (nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordFunctionID
                        || nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordGetID
                        || nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordSetID))
        {
            IASNode previousNodeParent = (IASNode) nodeAtPreviousOffset.getParent();
            if (previousNodeParent instanceof IFunctionNode)
            {
                IFunctionNode functionNode = (IFunctionNode) previousNodeParent;
                if (functionNode.hasModifier(ASModifier.OVERRIDE)
                        && functionNode.getParametersContainerNode().getAbsoluteStart() == -1
                        && functionNode.getReturnTypeNode() == null)
                {
                    autoCompleteFunctionOverrides(functionNode, project, result);
                    return result;
                }
            }
        }

        //local scope
        IASNode currentNodeForScope = offsetNode;
        do
        {
            //just keep traversing up until we get a scoped node or run out of
            //nodes to check
            if (currentNodeForScope instanceof IScopedNode)
            {
                IScopedNode scopedNode = (IScopedNode) currentNodeForScope;

                //include all members and local things that are in scope
                autoCompleteScope(scopedNode, false, addImportData, project, result);

                //include all public definitions
                IASScope scope = scopedNode.getScope();
                IDefinition definitionToSkip = scope.getDefinition();
                autoCompleteDefinitionsForActionScript(result, project, scopedNode, false, null, definitionToSkip, false, null, addImportData);
                autoCompleteKeywords(scopedNode, result);
                return result;
            }
            currentNodeForScope = currentNodeForScope.getParent();
        }
        while (currentNodeForScope != null);

        return result;
    }

    private boolean getTagsNeedOpenBracket(Path path, int currentOffset)
    {
        boolean tagsNeedOpenBracket = currentOffset == 0;
        if (currentOffset > 0)
        {
            Reader reader = getReaderForPath(path);
            if (reader != null)
            {
                try
                {
                    reader.skip(currentOffset - 1);
                    char prevChar = (char) reader.read();
                    tagsNeedOpenBracket = prevChar != '<';
                }
                catch(IOException e)
                {
                    //just ignore it
                }
                try
                {
                    reader.close();
                }
                catch(IOException e)
                {
                    //just ignore it
                }
            }
        }
        return tagsNeedOpenBracket;
    }

    private CompletionList mxmlCompletion(IMXMLTagData offsetTag, Path path, int currentOffset, ICompilationUnit offsetUnit, RoyaleProject project)
    {
        CompletionList result = new CompletionList();
        result.setIsIncomplete(false);
        result.setItems(new ArrayList<>());
        if (isInXMLComment(path, currentOffset))
        {
            //if we're inside a comment, no completion!
            return result;
        }

        ImportRange importRange = ImportRange.fromOffsetTag(offsetTag, currentOffset);
        String fileText = getFileTextForPath(path);
        AddImportData addImportData = CodeActionsUtils.findAddImportData(fileText, importRange);
        XmlnsRange xmlnsRange = XmlnsRange.fromOffsetTag(offsetTag, currentOffset);
        Position xmlnsPosition = null;
        if (xmlnsRange.endIndex >= 0)
        {
            xmlnsPosition = LanguageServerCompilerUtils.getPositionFromOffset(new StringReader(fileText), xmlnsRange.endIndex);
        }

        boolean tagsNeedOpenBracket = getTagsNeedOpenBracket(path, currentOffset);

        IMXMLTagData parentTag = offsetTag.getParentTag();

        //for some reason, the attributes list includes the >, but that's not
        //what we want here, so check if currentOffset isn't the end of the tag!
        boolean isAttribute = offsetTag.isOffsetInAttributeList(currentOffset)
                && currentOffset < offsetTag.getAbsoluteEnd();
        if (isAttribute && offsetTag.isCloseTag())
        {
            return result;
        }
        boolean isTagName = false;
        if(offsetTag instanceof MXMLTagData) //this shouldn't ever be false
        {
            MXMLTagData mxmlTagData = (MXMLTagData) offsetTag;
            //getNameStart() and getNameEnd() are not defined on IMXMLTagData
            isTagName = MXMLData.contains(mxmlTagData.getNameStart(), mxmlTagData.getNameEnd(), currentOffset);
        }

        //an implicit offset tag may mean that we're trying to close a tag
        if (parentTag != null && offsetTag.isImplicit())
        {
            IMXMLTagData nextTag = offsetTag.getNextTag();
            if (nextTag != null
                    && nextTag.isImplicit()
                    && nextTag.isCloseTag()
                    && nextTag.getName().equals(parentTag.getName())
                    && parentTag.getShortName().startsWith(offsetTag.getShortName()))
            {
                String closeTagText = "</" + nextTag.getName() + ">";
                CompletionItem closeTagItem = new CompletionItem();
                //display the full close tag
                closeTagItem.setLabel(closeTagText);
                //strip </ from the insert text
                String insertText = closeTagText.substring(2);
                int prefixLength = offsetTag.getPrefix().length();
                if (prefixLength > 0)
                {
                    //if the prefix already exists, strip it away so that the
                    //editor won't duplicate it.
                    insertText = insertText.substring(prefixLength + 1);
                }
                closeTagItem.setInsertText(insertText);
                closeTagItem.setSortText(offsetTag.getShortName());
                result.getItems().add(closeTagItem);
            }
        }

        //inside <fx:Declarations>
        if (MXMLDataUtils.isDeclarationsTag(offsetTag))
        {
            if (!isAttribute)
            {
                autoCompleteDefinitionsForMXML(result, project, offsetUnit, offsetTag, true, tagsNeedOpenBracket, null, addImportData, xmlnsPosition);
            }
            return result;
        }

        IDefinition offsetDefinition = MXMLDataUtils.getDefinitionForMXMLTag(offsetTag, project);
        if (offsetDefinition == null || isTagName)
        {
            IDefinition parentDefinition = null;
            if (parentTag != null)
            {
                parentDefinition = MXMLDataUtils.getDefinitionForMXMLTag(parentTag, project);
            }
            if (parentDefinition != null)
            {
                if (parentDefinition instanceof IClassDefinition)
                {
                    IClassDefinition classDefinition = (IClassDefinition) parentDefinition;
                    String offsetPrefix = offsetTag.getPrefix();
                    if (offsetPrefix.length() == 0 || parentTag.getPrefix().equals(offsetPrefix))
                    {
                        //only add members if the prefix is the same as the
                        //parent tag. members can't have different prefixes.
                        //also allow members when we don't have a prefix.
                        addMembersForMXMLTypeToAutoComplete(classDefinition, parentTag, offsetUnit, false, offsetPrefix.length() == 0, false, addImportData, xmlnsPosition, project, result);
                    }
                    if (!isAttribute)
                    {
                        IFileSpecification fileSpec = fileSpecGetter.getFileSpecification(offsetUnit.getAbsoluteFilename());
                        MXMLNamespace fxNS = MXMLNamespaceUtils.getMXMLLanguageNamespace(fileSpec, compilerWorkspace);
                        IMXMLData mxmlParent = offsetTag.getParent();
                        if (mxmlParent != null && parentTag.equals(mxmlParent.getRootTag()))
                        {
                            if (offsetPrefix.length() == 0)
                            {
                                //this tag doesn't have a prefix
                                addRootMXMLLanguageTagsToAutoComplete(offsetTag, fxNS.prefix, true, tagsNeedOpenBracket, result);
                            }
                            else if (offsetPrefix.equals(fxNS.prefix))
                            {
                                //this tag has a prefix
                                addRootMXMLLanguageTagsToAutoComplete(offsetTag, fxNS.prefix, false, false, result);
                            }
                        }
                        if (offsetPrefix.length() == 0)
                        {
                            //this tag doesn't have a prefix
                            addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.COMPONENT, fxNS.prefix, true, tagsNeedOpenBracket, result);
                        }
                        else if (offsetPrefix.equals(fxNS.prefix))
                        {
                            //this tag has a prefix
                            addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.COMPONENT, fxNS.prefix, false, false, result);
                        }
                        String defaultPropertyName = classDefinition.getDefaultPropertyName(project);
                        //if [DefaultProperty] is set, then we can instantiate
                        //types as child elements
                        //but we don't want to do that when in an attribute
                        boolean allowTypesAsChildren = defaultPropertyName != null;
                        if (!allowTypesAsChildren)
                        {
                            //similar to [DefaultProperty], if a component implements
                            //mx.core.IContainer, we can instantiate types as children
                            String containerInterface = project.getContainerInterface();
                            allowTypesAsChildren = classDefinition.isInstanceOf(containerInterface, project);
                        }
                        if (allowTypesAsChildren)
                        {
                            String typeFilter = null;
                            if (defaultPropertyName != null)
                            {
                                TypeScope typeScope = (TypeScope) classDefinition.getContainedScope();
                                Set<INamespaceDefinition> namespaceSet = ScopeUtils.getNamespaceSetForScopes(typeScope, typeScope, project);
                                List<IDefinition> propertiesByName = typeScope.getPropertiesByNameForMemberAccess(project, defaultPropertyName, namespaceSet);
                                if (propertiesByName.size() > 0)
                                {
                                    IDefinition propertyDefinition = propertiesByName.get(0);
                                    typeFilter = DefinitionUtils.getMXMLChildElementTypeForDefinition(propertyDefinition, project);
                                }
                            }
                            autoCompleteTypesForMXMLFromExistingTag(result, project, offsetUnit, offsetTag, typeFilter, xmlnsPosition);
                        }
                    }
                }
                else
                {
                    //the parent is something like a property, so matching the
                    //prefix is not required
                    autoCompleteTypesForMXMLFromExistingTag(result, project, offsetUnit, offsetTag, null, xmlnsPosition);
                }
                return result;
            }
            else if (MXMLDataUtils.isDeclarationsTag(parentTag))
            {
                autoCompleteTypesForMXMLFromExistingTag(result, project, offsetUnit, offsetTag, null, xmlnsPosition);
                return result;
            }
            else if (offsetTag.getParent().getRootTag().equals(offsetTag))
            {
                autoCompleteTypesForMXMLFromExistingTag(result, project, offsetUnit, offsetTag, null, xmlnsPosition);
            }
            return result;
        }
        if (offsetDefinition instanceof IClassDefinition)
        {
            IMXMLTagAttributeData attribute = MXMLDataUtils.getMXMLTagAttributeWithValueAtOffset(offsetTag, currentOffset);
            if (attribute != null)
            {
                return mxmlAttributeCompletion(offsetTag, currentOffset, project, result);
            }
            attribute = MXMLDataUtils.getMXMLTagAttributeWithNameAtOffset(offsetTag, currentOffset, true);
            if (attribute != null
                    && currentOffset > (attribute.getAbsoluteStart() + attribute.getXMLName().toString().length()))
            {
                return mxmlStatesCompletion(offsetUnit, result);
            }

            IClassDefinition classDefinition = (IClassDefinition) offsetDefinition;
            addMembersForMXMLTypeToAutoComplete(classDefinition, offsetTag, offsetUnit, isAttribute, !isAttribute, tagsNeedOpenBracket, addImportData, xmlnsPosition, project, result);

            if (!isAttribute)
            {
                IMXMLData mxmlParent = offsetTag.getParent();
                IFileSpecification fileSpec = fileSpecGetter.getFileSpecification(offsetUnit.getAbsoluteFilename());
                MXMLNamespace fxNS = MXMLNamespaceUtils.getMXMLLanguageNamespace(fileSpec, compilerWorkspace);
                if (mxmlParent != null && offsetTag.equals(mxmlParent.getRootTag()))
                {
                    addRootMXMLLanguageTagsToAutoComplete(offsetTag, fxNS.prefix, true, tagsNeedOpenBracket, result);
                }
                addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.COMPONENT, fxNS.prefix, true, tagsNeedOpenBracket, result);
                String defaultPropertyName = classDefinition.getDefaultPropertyName(project);
                //if [DefaultProperty] is set, then we can instantiate
                //types as child elements
                //but we don't want to do that when in an attribute
                boolean allowTypesAsChildren = defaultPropertyName != null;
                if (!allowTypesAsChildren)
                {
                    //similar to [DefaultProperty], if a component implements
                    //mx.core.IContainer, we can instantiate types as children
                    String containerInterface = project.getContainerInterface();
                    allowTypesAsChildren = classDefinition.isInstanceOf(containerInterface, project);
                }
                if (allowTypesAsChildren)
                {
                    String typeFilter = null;
                    if (defaultPropertyName != null)
                    {
                        TypeScope typeScope = (TypeScope) classDefinition.getContainedScope();
                        Set<INamespaceDefinition> namespaceSet = ScopeUtils.getNamespaceSetForScopes(typeScope, typeScope, project);
                        List<IDefinition> propertiesByName = typeScope.getPropertiesByNameForMemberAccess(project, defaultPropertyName, namespaceSet);
                        if (propertiesByName.size() > 0)
                        {
                            IDefinition propertyDefinition = propertiesByName.get(0);
                            typeFilter = DefinitionUtils.getMXMLChildElementTypeForDefinition(propertyDefinition, project);
                        }
                    }

                    autoCompleteDefinitionsForMXML(result, project, offsetUnit, offsetTag, true, tagsNeedOpenBracket, typeFilter, addImportData, xmlnsPosition);
                }
            }
            return result;
        }
        if (offsetDefinition instanceof IVariableDefinition
                || offsetDefinition instanceof IEventDefinition
                || offsetDefinition instanceof IStyleDefinition)
        {
            if (!isAttribute)
            {
                String typeFilter = DefinitionUtils.getMXMLChildElementTypeForDefinition(offsetDefinition, project);
                autoCompleteDefinitionsForMXML(result, project, offsetUnit, offsetTag, true, tagsNeedOpenBracket, typeFilter, addImportData, xmlnsPosition);
            }
            return result;
        }
        if (offsetDefinition instanceof IInterfaceDefinition)
        {
            //<fx:Component> resolves to an IInterfaceDefinition, but there's
            //nothing to add to the result, so return it as-is and skip the
            //warning below
            return result;
        }
        System.err.println("Unknown definition for MXML completion: " + offsetDefinition.getClass());
        return result;
    }

    private CompletionList mxmlStatesCompletion(ICompilationUnit unit, CompletionList result)
    {
        List<IDefinition> definitions = unit.getDefinitionPromises();
        if (definitions.size() == 0)
        {
            return result;
        }
        IDefinition definition = definitions.get(0);
        if (definition instanceof DefinitionPromise)
        {
            DefinitionPromise definitionPromise = (DefinitionPromise) definition;
            definition = definitionPromise.getActualDefinition();
        }
        if (definition instanceof IClassDefinition)
        {
            List<CompletionItem> items = result.getItems();
            IClassDefinition classDefinition = (IClassDefinition) definition;
            Set<String> stateNames = classDefinition.getStateNames();
            for (String stateName : stateNames)
            {
                CompletionItem stateItem = new CompletionItem();
                stateItem.setKind(CompletionItemKind.Field);
                stateItem.setLabel(stateName);
                items.add(stateItem);
            }
            ITypeNode typeNode = classDefinition.getNode();
            if(typeNode != null && typeNode instanceof IMXMLClassDefinitionNode)
            {
                IMXMLClassDefinitionNode mxmlClassNode = (IMXMLClassDefinitionNode) typeNode;
                Set<String> stateGroupNames = mxmlClassNode.getStateGroupNames();
                for (String stateGroupName : stateGroupNames)
                {
                    CompletionItem stateItem = new CompletionItem();
                    stateItem.setKind(CompletionItemKind.Field);
                    stateItem.setLabel(stateGroupName);
                    items.add(stateItem);
                }
            }
            return result;
        }
        return result;
    }

    private CompletionList mxmlAttributeCompletion(IMXMLTagData offsetTag, int currentOffset, RoyaleProject project, CompletionList result)
    {
        List<CompletionItem> items = result.getItems();
        IDefinition attributeDefinition = MXMLDataUtils.getDefinitionForMXMLTagAttribute(offsetTag, currentOffset, true, project);
        if (attributeDefinition instanceof IVariableDefinition)
        {
            IVariableDefinition variableDefinition = (IVariableDefinition) attributeDefinition;
            if (variableDefinition.getTypeAsDisplayString().equals(IASLanguageConstants.Boolean))
            {
                CompletionItem falseItem = new CompletionItem();
                falseItem.setKind(CompletionItemKind.Value);
                falseItem.setLabel(IASLanguageConstants.FALSE);
                items.add(falseItem);
                CompletionItem trueItem = new CompletionItem();
                trueItem.setKind(CompletionItemKind.Value);
                trueItem.setLabel(IASLanguageConstants.TRUE);
                items.add(trueItem);
                return result;
            }
            IMetaTag inspectableTag = variableDefinition.getMetaTagByName(IMetaAttributeConstants.ATTRIBUTE_INSPECTABLE);
            if (inspectableTag == null)
            {
                if (variableDefinition instanceof IAccessorDefinition)
                {
                    IAccessorDefinition accessorDefinition = (IAccessorDefinition) variableDefinition;
                    IAccessorDefinition otherAccessorDefinition = accessorDefinition.resolveCorrespondingAccessor(project);
                    if (otherAccessorDefinition != null)
                    {
                        inspectableTag = otherAccessorDefinition.getMetaTagByName(IMetaAttributeConstants.ATTRIBUTE_INSPECTABLE);
                    }
                }
            }
            if (inspectableTag != null)
            {
                IMetaTagAttribute enumAttribute = inspectableTag.getAttribute(IMetaAttributeConstants.NAME_INSPECTABLE_ENUMERATION);
                if (enumAttribute != null)
                {
                    String joinedValue = enumAttribute.getValue();
                    String[] values = joinedValue.split(",");
                    for (String value : values)
                    {
                        value = value.trim();
                        if (value.length() == 0)
                        {
                            //skip empty values
                            continue;
                        }
                        CompletionItem enumItem = new CompletionItem();
                        enumItem.setKind(CompletionItemKind.Value);
                        enumItem.setLabel(value);
                        items.add(enumItem);
                    }
                }
            }
        }
        if (attributeDefinition instanceof IStyleDefinition)
        {
            IStyleDefinition styleDefinition = (IStyleDefinition) attributeDefinition;
            for (String enumValue : styleDefinition.getEnumeration())
            {
                CompletionItem styleItem = new CompletionItem();
                styleItem.setKind(CompletionItemKind.Value);
                styleItem.setLabel(enumValue);
                items.add(styleItem);
            }
        }
        return result;
    }

    private Hover actionScriptHover(IASNode offsetNode, RoyaleProject project)
    {
        IDefinition definition = null;
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return new Hover(Collections.emptyList(), null);
        }

        //INamespaceDecorationNode extends IIdentifierNode, but we don't want
        //any hover information for it.
        if (definition == null
                && offsetNode instanceof IIdentifierNode
                && !(offsetNode instanceof INamespaceDecorationNode))
        {
            IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
            definition = identifierNode.resolve(project);
        }

        if (definition == null)
        {
            return new Hover(Collections.emptyList(), null);
        }

        IASNode parentNode = offsetNode.getParent();
        if (definition instanceof IClassDefinition
                && parentNode instanceof IFunctionCallNode)
        {
            IFunctionCallNode functionCallNode = (IFunctionCallNode) parentNode;
            if (functionCallNode.isNewExpression())
            {
                IClassDefinition classDefinition = (IClassDefinition) definition;
                //if it's a class in a new expression, use the constructor
                //definition instead
                IFunctionDefinition constructorDefinition = classDefinition.getConstructor();
                if (constructorDefinition != null)
                {
                    definition = constructorDefinition;
                }
            }
        }

        Hover result = new Hover();
        String detail = DefinitionTextUtils.definitionToDetail(definition, project);
        MarkedString markedDetail = new MarkedString(MARKED_STRING_LANGUAGE_ACTIONSCRIPT, detail);
        List<Either<String,MarkedString>> contents = new ArrayList<>();
        contents.add(Either.forRight(markedDetail));
        String docs = DefinitionDocumentationUtils.getDocumentationForDefinition(definition, true);
        if(docs != null)
        {
            contents.add(Either.forLeft(docs));
        }
        result.setContents(contents);
        return result;
    }

    private Hover mxmlHover(IMXMLTagData offsetTag, int currentOffset, RoyaleProject project)
    {
        IDefinition definition = MXMLDataUtils.getDefinitionForMXMLNameAtOffset(offsetTag, currentOffset, project);
        if (definition == null)
        {
            return new Hover(Collections.emptyList(), null);
        }

        if (MXMLDataUtils.isInsideTagPrefix(offsetTag, currentOffset))
        {
            //inside the prefix
            String prefix = offsetTag.getPrefix();
            Hover result = new Hover();
            List<Either<String,MarkedString>> contents = new ArrayList<>();
            StringBuilder detailBuilder = new StringBuilder();
            if (prefix.length() > 0)
            {
                detailBuilder.append("xmlns:" + prefix + "=\"" + offsetTag.getURI() + "\"");
            }
            else
            {
                detailBuilder.append("xmlns=\"" + offsetTag.getURI() + "\"");
            }
            MarkedString markedDetail = new MarkedString(MARKED_STRING_LANGUAGE_MXML, detailBuilder.toString());
            contents.add(Either.forRight(markedDetail));
            result.setContents(contents);
            return result;
        }

        Hover result = new Hover();
        String detail = DefinitionTextUtils.definitionToDetail(definition, project);
        MarkedString markedDetail = new MarkedString(MARKED_STRING_LANGUAGE_ACTIONSCRIPT, detail);
        List<Either<String,MarkedString>> contents = new ArrayList<>();
        contents.add(Either.forRight(markedDetail));
        result.setContents(contents);
        return result;
    }

    private List<? extends Location> actionScriptDefinition(IASNode offsetNode, RoyaleProject project)
    {
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return Collections.emptyList();
        }

        IDefinition definition = null;

        if (offsetNode instanceof IIdentifierNode)
        {
            IIdentifierNode expressionNode = (IIdentifierNode) offsetNode;
            definition = expressionNode.resolve(project);

            if (definition == null)
            {
                if (expressionNode.getName().equals(IASKeywordConstants.SUPER))
                {
                    ITypeDefinition typeDefinition = expressionNode.resolveType(project);
                    if (typeDefinition instanceof IClassDefinition)
                    {
                        IClassDefinition classDefinition = (IClassDefinition) typeDefinition;
                        definition = classDefinition.getConstructor();
                    }
                }
            }
        }

        if (definition == null)
        {
            //VSCode may call definition() when there isn't necessarily a
            //definition referenced at the current position.
            return Collections.emptyList();
        }
        
        IASNode parentNode = offsetNode.getParent();
        if (definition instanceof IClassDefinition
                && parentNode instanceof IFunctionCallNode)
        {
            IFunctionCallNode functionCallNode = (IFunctionCallNode) parentNode;
            if (functionCallNode.isNewExpression())
            {
                IClassDefinition classDefinition = (IClassDefinition) definition;
                //if it's a class in a new expression, use the constructor
                //definition instead
                IFunctionDefinition constructorDefinition = classDefinition.getConstructor();
                if (constructorDefinition != null)
                {
                    definition = constructorDefinition;
                }
            }
        }

        List<Location> result = new ArrayList<>();
        resolveDefinition(definition, project, result);
        return result;
    }

    private List<? extends Location> mxmlDefinition(IMXMLTagData offsetTag, int currentOffset, RoyaleProject project)
    {
        IDefinition definition = MXMLDataUtils.getDefinitionForMXMLNameAtOffset(offsetTag, currentOffset, project);
        if (definition == null)
        {
            //VSCode may call definition() when there isn't necessarily a
            //definition referenced at the current position.
            return Collections.emptyList();
        }

        if (MXMLDataUtils.isInsideTagPrefix(offsetTag, currentOffset))
        {
            //ignore the tag's prefix
            return Collections.emptyList();
        }

        List<Location> result = new ArrayList<>();
        resolveDefinition(definition, project, result);
        return result;
    }

    private List<? extends Location> actionScriptTypeDefinition(IASNode offsetNode, RoyaleProject project)
    {
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return Collections.emptyList();
        }

        IDefinition definition = null;

        if (offsetNode instanceof IIdentifierNode)
        {
            IIdentifierNode expressionNode = (IIdentifierNode) offsetNode;
            definition = expressionNode.resolveType(project);
        }

        if (definition == null)
        {
            //VSCode may call typeDefinition() when there isn't necessarily a
            //type definition referenced at the current position.
            return Collections.emptyList();
        }
        List<Location> result = new ArrayList<>();
        resolveDefinition(definition, project, result);
        return result;
    }

    private List<? extends Location> mxmlTypeDefinition(IMXMLTagData offsetTag, int currentOffset, RoyaleProject project)
    {
        IDefinition definition = MXMLDataUtils.getTypeDefinitionForMXMLNameAtOffset(offsetTag, currentOffset, project);
        if (definition == null)
        {
            //VSCode may call definition() when there isn't necessarily a
            //definition referenced at the current position.
            return Collections.emptyList();
        }

        if (MXMLDataUtils.isInsideTagPrefix(offsetTag, currentOffset))
        {
            //ignore the tag's prefix
            return Collections.emptyList();
        }

        List<Location> result = new ArrayList<>();
        resolveDefinition(definition, project, result);
        return result;
    }

    private List<? extends Location> actionScriptImplementation(IASNode offsetNode, RoyaleProject project)
    {
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return Collections.emptyList();
        }

        IInterfaceDefinition interfaceDefinition = null;

        if (offsetNode instanceof IIdentifierNode)
        {
            IIdentifierNode expressionNode = (IIdentifierNode) offsetNode;
            IDefinition resolvedDefinition = expressionNode.resolve(project);
            if (resolvedDefinition instanceof IInterfaceDefinition)
            {
                interfaceDefinition = (IInterfaceDefinition) resolvedDefinition;
            }
        }

        if (interfaceDefinition == null)
        {
            //VSCode may call typeDefinition() when there isn't necessarily a
            //type definition referenced at the current position.
            return Collections.emptyList();
        }
        
        List<Location> result = new ArrayList<>();
        for (ICompilationUnit unit : project.getCompilationUnits())
        {
            if (unit == null
                    || unit instanceof SWCCompilationUnit
                    || unit instanceof ResourceBundleCompilationUnit)
            {
                continue;
            }
            Collection<IDefinition> definitions = null;
            try
            {
                definitions = unit.getFileScopeRequest().get().getExternallyVisibleDefinitions();
            }
            catch (Exception e)
            {
                //safe to ignore
                continue;
            }

            for (IDefinition definition : definitions)
            {
                if (!(definition instanceof IClassDefinition))
                {
                    continue;
                }
                IClassDefinition classDefinition = (IClassDefinition) definition;
                if (DefinitionUtils.isImplementationOfInterface(classDefinition, interfaceDefinition, project))
                {
                    Location location = definitionToLocation(classDefinition, project);
                    if (location != null)
                    {
                        result.add(location);
                    }
                }
            }
        }
        return result;
    }

    private List<? extends Location> actionScriptReferences(IASNode offsetNode, RoyaleProject project)
    {
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return Collections.emptyList();
        }

        if (offsetNode instanceof IIdentifierNode)
        {
            IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
            IDefinition resolved = identifierNode.resolve(project);
            if (resolved == null)
            {
                return Collections.emptyList();
            }
            List<Location> result = new ArrayList<>();
            referencesForDefinition(resolved, project, result);
            return result;
        }

        //VSCode may call definition() when there isn't necessarily a
        //definition referenced at the current position.
        return Collections.emptyList();
    }

    private List<? extends Location> mxmlReferences(IMXMLTagData offsetTag, int currentOffset, ICompilationUnit offsetUnit, RoyaleProject project)
    {
        IDefinition definition = MXMLDataUtils.getDefinitionForMXMLNameAtOffset(offsetTag, currentOffset, project);
        if (definition != null)
        {
            if (MXMLDataUtils.isInsideTagPrefix(offsetTag, currentOffset))
            {
                //ignore the tag's prefix
                return Collections.emptyList();
            }
            ArrayList<Location> result = new ArrayList<>();
            referencesForDefinition(definition, project, result);
            return result;
        }

        //finally, check if we're looking for references to a tag's id
        IMXMLTagAttributeData attributeData = MXMLDataUtils.getMXMLTagAttributeWithValueAtOffset(offsetTag, currentOffset);
        if (attributeData == null || !attributeData.getName().equals(IMXMLLanguageConstants.ATTRIBUTE_ID))
        {
            //VSCode may call definition() when there isn't necessarily a
            //definition referenced at the current position.
            return Collections.emptyList();
        }
        Collection<IDefinition> definitions = null;
        try
        {
            definitions = offsetUnit.getFileScopeRequest().get().getExternallyVisibleDefinitions();
        }
        catch (Exception e)
        {
            //safe to ignore
        }
        if (definitions == null || definitions.size() == 0)
        {
            return Collections.emptyList();
        }
        IClassDefinition classDefinition = null;
        for (IDefinition currentDefinition : definitions)
        {
            if (currentDefinition instanceof IClassDefinition)
            {
                classDefinition = (IClassDefinition) currentDefinition;
                break;
            }
        }
        if (classDefinition == null)
        {
            //this probably shouldn't happen, but check just to be safe
            return Collections.emptyList();
        }
        IASScope scope = classDefinition.getContainedScope();
        for (IDefinition currentDefinition : scope.getAllLocalDefinitions())
        {
            if (currentDefinition.getBaseName().equals(attributeData.getRawValue()))
            {
                definition = currentDefinition;
                break;
            }
        }
        if (definition == null)
        {
            //VSCode may call definition() when there isn't necessarily a
            //definition referenced at the current position.
            return Collections.emptyList();
        }
        ArrayList<Location> result = new ArrayList<>();
        referencesForDefinition(definition, project, result);
        return result;
    }

    private WorkspaceEdit actionScriptRename(IASNode offsetNode, String newName, RoyaleProject project)
    {
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return new WorkspaceEdit(new HashMap<>());
        }

        IDefinition definition = null;

        if (offsetNode instanceof IDefinitionNode)
        {
            IDefinitionNode definitionNode = (IDefinitionNode) offsetNode;
            IExpressionNode expressionNode = definitionNode.getNameExpressionNode();
            definition = expressionNode.resolve(project);
        }
        else if (offsetNode instanceof IIdentifierNode)
        {
            IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
            definition = identifierNode.resolve(project);
        }

        if (definition == null)
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

        WorkspaceEdit result = renameDefinition(definition, newName, project);
        return result;
    }

    private WorkspaceEdit mxmlRename(IMXMLTagData offsetTag, int currentOffset, String newName, RoyaleProject project)
    {
        IDefinition definition = MXMLDataUtils.getDefinitionForMXMLNameAtOffset(offsetTag, currentOffset, project);
        if (definition != null)
        {
            if (MXMLDataUtils.isInsideTagPrefix(offsetTag, currentOffset))
            {
                //ignore the tag's prefix
                return new WorkspaceEdit(new HashMap<>());
            }
            WorkspaceEdit result = renameDefinition(definition, newName, project);
            return result;
        }

        if (languageClient != null)
        {
            MessageParams message = new MessageParams();
            message.setType(MessageType.Info);
            message.setMessage("You cannot rename this element.");
            languageClient.showMessage(message);
        }
        return new WorkspaceEdit(new HashMap<>());
    }
    
    private void referencesForDefinitionInCompilationUnit(IDefinition definition, ICompilationUnit compilationUnit, RoyaleProject project, List<Location> result)
    {
        if (compilationUnit.getAbsoluteFilename().endsWith(MXML_EXTENSION))
        {
            IMXMLDataManager mxmlDataManager = compilerWorkspace.getMXMLDataManager();
            MXMLData mxmlData = (MXMLData) mxmlDataManager.get(fileSpecGetter.getFileSpecification(compilationUnit.getAbsoluteFilename()));
            IMXMLTagData rootTag = mxmlData.getRootTag();
            if (rootTag != null)
            {
                ArrayList<ISourceLocation> units = new ArrayList<>();
                findMXMLUnits(mxmlData.getRootTag(), definition, project, units);
                for (ISourceLocation otherUnit : units)
                {
                    Location location = LanguageServerCompilerUtils.getLocationFromSourceLocation(otherUnit);
                    if (location == null)
                    {
                        continue;
                    }
                    result.add(location);
                }
            }
        }
        IASNode ast = getAST(compilationUnit);
        if(ast == null)
        {
            return;
        }
        ArrayList<IIdentifierNode> identifiers = new ArrayList<>();
        ASTUtils.findIdentifiersForDefinition(ast, definition, project, identifiers);
        for (IIdentifierNode otherNode : identifiers)
        {
            Location location = LanguageServerCompilerUtils.getLocationFromSourceLocation(otherNode);
            if (location == null)
            {
                continue;
            }
            result.add(location);
        }
    }

    private void referencesForDefinition(IDefinition definition, RoyaleProject project, List<Location> result)
    {
        for (ICompilationUnit compilationUnit : project.getCompilationUnits())
        {
            if (compilationUnit == null
                    || compilationUnit instanceof SWCCompilationUnit
                    || compilationUnit instanceof ResourceBundleCompilationUnit)
            {
                continue;
            }
            referencesForDefinitionInCompilationUnit(definition, compilationUnit, project, result);
        }
    }
    
    private void autoCompleteValue(String value, CompletionList result)
    {
        CompletionItem item = new CompletionItem();
        item.setKind(CompletionItemKind.Value);
        item.setLabel(value);
        result.getItems().add(item);
    }
    
    private void autoCompleteKeyword(String keyword, CompletionList result)
    {
        CompletionItem item = new CompletionItem();
        item.setKind(CompletionItemKind.Keyword);
        item.setLabel(keyword);
        result.getItems().add(item);
    }

    private void autoCompleteTypes(IASNode withNode, AddImportData addImportData, RoyaleProject project, CompletionList result)
    {
        //start by getting the types in scope
        IASNode node = withNode;
        do
        {
            //just keep traversing up until we get a scoped node or run out of
            //nodes to check
            if (node instanceof IScopedNode)
            {
                IScopedNode scopedNode = (IScopedNode) node;

                //include all members and local things that are in scope
                autoCompleteScope(scopedNode, true, addImportData, project, result);
                break;
            }
            node = node.getParent();
        }
        while (node != null);
        autoCompleteDefinitionsForActionScript(result, project, withNode, true, null, null, false, null, addImportData);
    }

    /**
     * Using an existing tag, that may already have a prefix or short name,
     * populate the completion list.
     */
    private void autoCompleteTypesForMXMLFromExistingTag(CompletionList result, RoyaleProject project, ICompilationUnit offsetUnit, IMXMLTagData offsetTag, String typeFilter, Position xmlnsPosition)
    {
        IMXMLDataManager mxmlDataManager = compilerWorkspace.getMXMLDataManager();
        MXMLData mxmlData = (MXMLData) mxmlDataManager.get(fileSpecGetter.getFileSpecification(offsetUnit.getAbsoluteFilename()));
        String tagStartShortNameForComparison = offsetTag.getShortName().toLowerCase();
        String tagPrefix = offsetTag.getPrefix();
        String tagNamespace = null;
        PrefixMap prefixMap = mxmlData.getRootTagPrefixMap();
        if (prefixMap != null)
        {
            //could be null if this is the root tag and no prefixes are defined
            tagNamespace = prefixMap.getNamespaceForPrefix(tagPrefix);
        }
        String tagNamespacePackage = null;
        if (tagNamespace != null && tagNamespace.endsWith("*"))
        {
            if (tagNamespace.length() > 1)
            {
                tagNamespacePackage = tagNamespace.substring(0, tagNamespace.length() - 2);
            }
            else //top level
            {
                tagNamespacePackage = "";
            }
        }

        for (ICompilationUnit unit : project.getCompilationUnits())
        {
            if (unit == null)
            {
                continue;
            }
            Collection<IDefinition> definitions = null;
            try
            {
                definitions = unit.getFileScopeRequest().get().getExternallyVisibleDefinitions();
            }
            catch (Exception e)
            {
                //safe to ignore
                continue;
            }

            for (IDefinition definition : definitions)
            {
                if (!(definition instanceof ITypeDefinition))
                {
                    continue;
                }
                ITypeDefinition typeDefinition = (ITypeDefinition) definition;
                if (typeFilter != null && !DefinitionUtils.extendsOrImplements(project, typeDefinition, typeFilter))
                {
                    continue;
                }

                //first check that the tag either doesn't have a short name yet
                //or that the definition's base name matches the short name 
                if (tagStartShortNameForComparison.length() == 0
                    || typeDefinition.getBaseName().toLowerCase().startsWith(tagStartShortNameForComparison))
                {
                    //if a prefix already exists, make sure the definition is
                    //in a namespace with that prefix
                    if (tagPrefix.length() > 0)
                    {
                        Collection<XMLName> tagNames = project.getTagNamesForClass(typeDefinition.getQualifiedName());
                        for (XMLName tagName : tagNames)
                        {
                            String tagNameNamespace = tagName.getXMLNamespace();
                            //getTagNamesForClass() returns the 2006 namespace, even if that's
                            //not what we're using in this file
                            if (tagNameNamespace.equals(IMXMLLanguageConstants.NAMESPACE_MXML_2006))
                            {
                                //use the language namespace of the root tag instead
                                tagNameNamespace = mxmlData.getRootTag().getMXMLDialect().getLanguageNamespace();
                            }
                            if (prefixMap != null)
                            {
                                String[] prefixes = prefixMap.getPrefixesForNamespace(tagNameNamespace);
                                for (String otherPrefix : prefixes)
                                {
                                    if (tagPrefix.equals(otherPrefix))
                                    {
                                        addDefinitionAutoCompleteMXML(typeDefinition, xmlnsPosition, false, null, null, false, offsetTag, project, result);
                                    }
                                }
                            }
                        }
                        if (tagNamespacePackage != null
                                && tagNamespacePackage.equals(typeDefinition.getPackageName()))
                        {
                            addDefinitionAutoCompleteMXML(typeDefinition, xmlnsPosition, false, null, null, false, offsetTag, project, result);
                        }
                    }
                    else
                    {
                        //no prefix yet, so complete the definition with a prefix
                        MXMLNamespace ns = MXMLNamespaceUtils.getMXMLNamespaceForTypeDefinition(typeDefinition, mxmlData, project);
                        addDefinitionAutoCompleteMXML(typeDefinition, xmlnsPosition, false, ns.prefix, ns.uri, false, offsetTag, project, result);
                    }
                }
            }
        }
    }

    private void autoCompleteDefinitionsForMXML(CompletionList result, RoyaleProject project, ICompilationUnit offsetUnit, IMXMLTagData offsetTag, boolean typesOnly, boolean tagsNeedOpenBracket, String typeFilter, AddImportData addImportData, Position xmlnsPosition)
    {
        for (ICompilationUnit unit : project.getCompilationUnits())
        {
            if (unit == null)
            {
                continue;
            }
            Collection<IDefinition> definitions = null;
            try
            {
                definitions = unit.getFileScopeRequest().get().getExternallyVisibleDefinitions();
            }
            catch (Exception e)
            {
                //safe to ignore
                continue;
            }
            for (IDefinition definition : definitions)
            {
                boolean isType = definition instanceof ITypeDefinition;
                if (!typesOnly || isType)
                {
                    if (isType)
                    {
                        IMetaTag excludeClassMetaTag = definition.getMetaTagByName(IMetaAttributeConstants.ATTRIBUTE_EXCLUDECLASS);
                        if (excludeClassMetaTag != null)
                        {
                            //skip types with [ExcludeClass] metadata
                            continue;
                        }
                    }
                    if (isType)
                    {
                        ITypeDefinition typeDefinition = (ITypeDefinition) definition;
                        if (typeFilter != null && !DefinitionUtils.extendsOrImplements(project, typeDefinition, typeFilter))
                        {
                            continue;
                        }

                        addMXMLTypeDefinitionAutoComplete(typeDefinition, xmlnsPosition, offsetUnit, offsetTag, tagsNeedOpenBracket, project, result);
                    }
                    else
                    {
                        addDefinitionAutoCompleteActionScript(definition, null, addImportData, project, result);
                    }
                }
            }
        }
    }

    private void autoCompleteDefinitionsForActionScript(CompletionList result,
            RoyaleProject project, IASNode offsetNode,
            boolean typesOnly, String requiredPackageName, IDefinition definitionToSkip,
            boolean tagsNeedOpenBracket, String typeFilter, AddImportData addImportData)
    {
        String skipQualifiedName = null;
        if (definitionToSkip != null)
        {
            skipQualifiedName = definitionToSkip.getQualifiedName();
        }
        for (ICompilationUnit unit : project.getCompilationUnits())
        {
            if (unit == null)
            {
                continue;
            }
            Collection<IDefinition> definitions = null;
            try
            {
                definitions = unit.getFileScopeRequest().get().getExternallyVisibleDefinitions();
            }
            catch (Exception e)
            {
                //safe to ignore
                continue;
            }
            for (IDefinition definition : definitions)
            {
                boolean isType = definition instanceof ITypeDefinition;
                if (!typesOnly || isType)
                {
                    if (requiredPackageName == null || definition.getPackageName().equals(requiredPackageName))
                    {
                        if (skipQualifiedName != null
                                && skipQualifiedName.equals(definition.getQualifiedName()))
                        {
                            continue;
                        }
                        if (isType)
                        {
                            IMetaTag excludeClassMetaTag = definition.getMetaTagByName(IMetaAttributeConstants.ATTRIBUTE_EXCLUDECLASS);
                            if (excludeClassMetaTag != null)
                            {
                                //skip types with [ExcludeClass] metadata
                                continue;
                            }
                        }
                        addDefinitionAutoCompleteActionScript(definition, offsetNode, addImportData, project, result);
                    }
                }
            }
        }
        if (requiredPackageName == null || requiredPackageName.equals(""))
        {
            CompletionItem item = new CompletionItem();
            item.setKind(CompletionItemKind.Class);
            item.setLabel(IASKeywordConstants.VOID);
            result.getItems().add(item);
        }
    }

    private void autoCompleteScope(IScopedNode node, boolean typesOnly, AddImportData addImportData, RoyaleProject project, CompletionList result)
    {
        IScopedNode currentNode = node;
        ASScope scope = (ASScope) node.getScope();
        while (currentNode != null)
        {
            IASScope currentScope = currentNode.getScope();
            boolean isType = currentScope instanceof TypeScope;
            boolean staticOnly = currentNode == node && isType;
            if (currentScope instanceof TypeScope && !typesOnly)
            {
                TypeScope typeScope = (TypeScope) currentScope;
                addDefinitionsInTypeScopeToAutoComplete(typeScope, scope, true, true, false, false, null, false, addImportData, null, null, project, result);
                if (!staticOnly)
                {
                    addDefinitionsInTypeScopeToAutoCompleteActionScript(typeScope, scope, false, addImportData, project, result);
                }
            }
            else
            {
                for (IDefinition localDefinition : currentScope.getAllLocalDefinitions())
                {
                    if (localDefinition.getBaseName().length() == 0)
                    {
                        continue;
                    }
                    if (typesOnly && !(localDefinition instanceof ITypeDefinition))
                    {
                        continue;
                    }
                    if (!staticOnly || localDefinition.isStatic())
                    {
                        if (localDefinition instanceof ISetterDefinition)
                        {
                            ISetterDefinition setter = (ISetterDefinition) localDefinition;
                            IGetterDefinition getter = setter.resolveGetter(project);
                            if (getter != null)
                            {
                                //skip the setter if there's also a getter because
                                //it would add a duplicate entry
                                continue;
                            }
                        }
                        addDefinitionAutoCompleteActionScript(localDefinition, currentNode, addImportData, project, result);
                    }
                }
            }
            currentNode = currentNode.getContainingScope();
        }
    }
    
    private void autoCompleteKeywords(IScopedNode node, CompletionList result)
    {
        boolean isInFunction = false;
        boolean isInClass = false;
        boolean isFileScope = false;
        boolean isTypeScope = false;
        boolean isClassScope = false;
        boolean isPackageScope = false;

        IASNode exactNode = node.getParent();
        IScopedNode currentNode = node;
        while (currentNode != null)
        {
            IASNode parentNode = currentNode.getParent();
            if (parentNode instanceof IFunctionNode)
            {
                isInFunction = true;
            }
            if (parentNode instanceof IClassNode)
            {
                if (parentNode == exactNode)
                {
                    isClassScope = true;
                }
                isInClass = true;
            }
            if (parentNode instanceof IFileNode && parentNode == exactNode)
            {
                isFileScope = true;
            }
            if (parentNode instanceof ITypeNode && parentNode == exactNode)
            {
                isTypeScope = true;
            }
            if (parentNode instanceof IPackageNode && parentNode == exactNode)
            {
                isPackageScope = true;
            }

            currentNode = currentNode.getContainingScope();
        }

        autoCompleteKeyword(IASKeywordConstants.AS, result);
        autoCompleteKeyword(IASKeywordConstants.BREAK, result);
        autoCompleteKeyword(IASKeywordConstants.CASE, result);
        autoCompleteKeyword(IASKeywordConstants.CATCH, result);
        if (isPackageScope || isFileScope)
        {
            autoCompleteKeyword(IASKeywordConstants.CLASS, result);
        }
        autoCompleteKeyword(IASKeywordConstants.CONST, result);
        autoCompleteKeyword(IASKeywordConstants.CONTINUE, result);
        autoCompleteKeyword(IASKeywordConstants.DEFAULT, result);
        autoCompleteKeyword(IASKeywordConstants.DELETE, result);
        autoCompleteKeyword(IASKeywordConstants.DO, result);
        if (isPackageScope || isFileScope)
        {
            autoCompleteKeyword(IASKeywordConstants.DYNAMIC, result);
        }
        autoCompleteKeyword(IASKeywordConstants.EACH, result);
        autoCompleteKeyword(IASKeywordConstants.ELSE, result);
        if (isPackageScope || isFileScope)
        {
            autoCompleteKeyword(IASKeywordConstants.EXTENDS, result);
            autoCompleteKeyword(IASKeywordConstants.FINAL, result);
        }
        autoCompleteKeyword(IASKeywordConstants.FINALLY, result);
        autoCompleteKeyword(IASKeywordConstants.FOR, result);
        autoCompleteKeyword(IASKeywordConstants.FUNCTION, result);
        if (isTypeScope)
        {
            //get keyword can only be used in a class/interface
            autoCompleteKeyword(IASKeywordConstants.GET, result);
        }
        autoCompleteKeyword(IASKeywordConstants.GOTO, result);
        autoCompleteKeyword(IASKeywordConstants.IF, result);
        if (isPackageScope || isFileScope)
        {
            autoCompleteKeyword(IASKeywordConstants.IMPLEMENTS, result);
        }
        autoCompleteKeyword(IASKeywordConstants.IMPORT, result);
        autoCompleteKeyword(IASKeywordConstants.IN, result);
        autoCompleteKeyword(IASKeywordConstants.INCLUDE, result);
        autoCompleteKeyword(IASKeywordConstants.INSTANCEOF, result);
        if (isPackageScope || isFileScope)
        {
            autoCompleteKeyword(IASKeywordConstants.INTERFACE, result);
        }
        if (!isInFunction)
        {
            //namespaces can't be in functions
            autoCompleteKeyword(IASKeywordConstants.INTERNAL, result);
        }
        autoCompleteKeyword(IASKeywordConstants.IS, result);
        autoCompleteKeyword(IASKeywordConstants.NAMESPACE, result);
        if (isClassScope)
        {
            //native keyword may only be used for class members
            autoCompleteKeyword(IASKeywordConstants.NATIVE, result);
        }
        autoCompleteKeyword(IASKeywordConstants.NEW, result);
        if (isClassScope)
        {
            //override keyword may only be used for class members
            autoCompleteKeyword(IASKeywordConstants.OVERRIDE, result);
        }
        if (isFileScope)
        {
            //a package can only be defined directly in a file
            autoCompleteKeyword(IASKeywordConstants.PACKAGE, result);
        }
        if (isPackageScope || isClassScope)
        {
            //namespaces can't be in functions
            autoCompleteKeyword(IASKeywordConstants.PRIVATE, result);
            autoCompleteKeyword(IASKeywordConstants.PROTECTED, result);
            autoCompleteKeyword(IASKeywordConstants.PUBLIC, result);
        }
        if (isInFunction)
        {
            //can only return from a function
            autoCompleteKeyword(IASKeywordConstants.RETURN, result);
        }
        if (isTypeScope)
        {
            //set keyword can only be used in a class/interface
            autoCompleteKeyword(IASKeywordConstants.SET, result);
        }
        if (isClassScope)
        {
            //static keyword may only be used for class members
            autoCompleteKeyword(IASKeywordConstants.STATIC, result);
        }
        if (isInFunction && isInClass)
        {
            //can only be used in functions that are in classes
            autoCompleteKeyword(IASKeywordConstants.SUPER, result);
        }
        autoCompleteKeyword(IASKeywordConstants.SWITCH, result);
        if (isInFunction)
        {
            //this should only be used in functions
            autoCompleteKeyword(IASKeywordConstants.THIS, result);
        }
        autoCompleteKeyword(IASKeywordConstants.THROW, result);
        autoCompleteKeyword(IASKeywordConstants.TRY, result);
        autoCompleteKeyword(IASKeywordConstants.TYPEOF, result);
        autoCompleteKeyword(IASKeywordConstants.VAR, result);
        autoCompleteKeyword(IASKeywordConstants.WHILE, result);
        autoCompleteKeyword(IASKeywordConstants.WITH, result);

        autoCompleteValue(IASKeywordConstants.TRUE, result);
        autoCompleteValue(IASKeywordConstants.FALSE, result);
        autoCompleteValue(IASKeywordConstants.NULL, result);
    }

    private void autoCompleteFunctionOverrides(IFunctionNode node, RoyaleProject project, CompletionList result)
    {
        String namespace = node.getNamespace();
        boolean isGetter = node.isGetter();
        boolean isSetter = node.isSetter();
        IClassNode classNode = (IClassNode) node.getAncestorOfType(IClassNode.class);
        IClassDefinition classDefinition = classNode.getDefinition();

        ArrayList<IDefinition> propertyDefinitions = new ArrayList<>();
        TypeScope typeScope = (TypeScope) classDefinition.getContainedScope();
        Set<INamespaceDefinition> namespaceSet = typeScope.getNamespaceSet(project);
        do
        {
            classDefinition = classDefinition.resolveBaseClass(project);
            if (classDefinition == null)
            {
                break;
            }
            typeScope = (TypeScope) classDefinition.getContainedScope();
            INamespaceDefinition protectedNamespace = classDefinition.getProtectedNamespaceReference();
            typeScope.getAllLocalProperties(project, propertyDefinitions, namespaceSet, protectedNamespace);
        }
        while (classDefinition instanceof IClassDefinition);

        List<CompletionItem> resultItems = result.getItems();
        ArrayList<String> functionNames = new ArrayList<>();
        for (IDefinition definition : propertyDefinitions)
        {
            if (!(definition instanceof IFunctionDefinition)
                    || definition.isStatic())
            {
                continue;
            }
            IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
            boolean otherIsGetter = functionDefinition instanceof IGetterDefinition;
            boolean otherIsSetter = functionDefinition instanceof ISetterDefinition;
            String otherNamespace = functionDefinition.getNamespaceReference().getBaseName();
            if (isGetter != otherIsGetter
                    || isSetter != otherIsSetter
                    || !namespace.equals(otherNamespace))
            {
                continue;
            }
            String functionName = functionDefinition.getBaseName();
            if (functionName.length() == 0)
            {
                //vscode expects all items to have a name
                continue;
            }
            if (functionNames.contains(functionName))
            {
                //avoid duplicates
                continue;
            }
            functionNames.add(functionName);

            StringBuilder insertText = new StringBuilder();
            insertText.append(functionName);
            insertText.append("(");
            IParameterDefinition[] params = functionDefinition.getParameters();
            for (int i = 0, length = params.length; i < length; i++)
            {
                if (i > 0)
                {
                    insertText.append(", ");
                }
                IParameterDefinition param = params[i];
                if (param.isRest())
                {
                    insertText.append(IASLanguageConstants.REST);
                }
                insertText.append(param.getBaseName());
                String paramType = param.getTypeAsDisplayString();
                if(paramType.length() != 0)
                {
                    insertText.append(":");
                    insertText.append(paramType);
                }
                if (param.hasDefaultValue())
                {
                    insertText.append(" = ");
                    Object defaultValue = param.resolveDefaultValue(project);
                    String valueAsString = DefinitionTextUtils.valueToString(defaultValue);
                    if (valueAsString != null)
                    {
                        insertText.append(valueAsString);
                    }
                }
            }
            insertText.append(")");
            String returnType = functionDefinition.getReturnTypeAsDisplayString();
            if(returnType.length() != 0)
            {
                insertText.append(":");
                insertText.append(returnType);
            }

            CompletionItem item = CompletionItemUtils.createDefinitionItem(functionDefinition, project);
		    item.setInsertText(insertText.toString());
            resultItems.add(item);
        }
    }

    private void autoCompleteMemberAccess(IMemberAccessExpressionNode node, AddImportData addImportData, RoyaleProject project, CompletionList result)
    {
        ASScope scope = (ASScope) node.getContainingScope().getScope();
        IExpressionNode leftOperand = node.getLeftOperandNode();
        IDefinition leftDefinition = leftOperand.resolve(project);
        if (leftDefinition != null && leftDefinition instanceof ITypeDefinition)
        {
            ITypeDefinition typeDefinition = (ITypeDefinition) leftDefinition;
            TypeScope typeScope = (TypeScope) typeDefinition.getContainedScope();
            addDefinitionsInTypeScopeToAutoCompleteActionScript(typeScope, scope, true, addImportData, project, result);
            return;
        }
        ITypeDefinition leftType = leftOperand.resolveType(project);
        if (leftType != null)
        {
            TypeScope typeScope = (TypeScope) leftType.getContainedScope();
            addDefinitionsInTypeScopeToAutoCompleteActionScript(typeScope, scope, false, addImportData, project, result);
            return;
        }

        if (leftOperand instanceof IMemberAccessExpressionNode)
        {
            IMemberAccessExpressionNode memberAccess = (IMemberAccessExpressionNode) leftOperand;
            String packageName = memberAccessToPackageName(memberAccess);
            if (packageName != null)
            {
                autoCompleteDefinitionsForActionScript(result, project, node, false, packageName, null, false, null, addImportData);
                return;
            }
        }
    }

    private String memberAccessToPackageName(IMemberAccessExpressionNode memberAccess)
    {
        String result = null;
        IExpressionNode rightOperand = memberAccess.getRightOperandNode();
        if(!(rightOperand instanceof IIdentifierNode))
        {
            return null;
        }
        IExpressionNode leftOperand = memberAccess.getLeftOperandNode();
        if (leftOperand instanceof IMemberAccessExpressionNode)
        {
            result = memberAccessToPackageName((IMemberAccessExpressionNode) leftOperand);
        }
        else if(leftOperand instanceof IIdentifierNode)
        {
            IIdentifierNode identifierNode = (IIdentifierNode) leftOperand;
            result = identifierNode.getName();
        }
        else
        {
            return null;
        }
        IIdentifierNode identifierNode = (IIdentifierNode) rightOperand;
        return result + "." + identifierNode.getName();
    }

    private void autoCompletePackageBlock(IFileSpecification fileSpec, RoyaleProject project, CompletionList result)
    {
        //we'll guess the package name based on path of the parent directory
        File unitFile = new File(fileSpec.getPath());
        unitFile = unitFile.getParentFile();
        String expectedPackage = SourcePathUtils.getPackageForDirectoryPath(unitFile.toPath(), project);
        CompletionItem packageItem = CompletionItemUtils.createPackageBlockItem(expectedPackage, completionSupportsSnippets);
        result.getItems().add(packageItem);
    }

    private void autoCompletePackageName(String partialPackageName, IFileSpecification fileSpec, RoyaleProject project, CompletionList result)
    {
        File unitFile = new File(fileSpec.getPath());
        unitFile = unitFile.getParentFile();
        String expectedPackage = SourcePathUtils.getPackageForDirectoryPath(unitFile.toPath(), project);
        if (expectedPackage.length() == 0)
        {
            //it's the top level package
            return;
        }
        if (partialPackageName.startsWith(expectedPackage))
        {
            //we already have the correct package, maybe with some extra
            return;
        }
        if (partialPackageName.contains(".")
                && expectedPackage.startsWith(partialPackageName))
        {
            int lastDot = partialPackageName.lastIndexOf('.');
            expectedPackage = expectedPackage.substring(lastDot + 1);
        }
        CompletionItem item = new CompletionItem();
        item.setKind(CompletionItemKind.Module);
        item.setLabel(expectedPackage);
        result.getItems().add(item);
    }

    private void autoCompleteImport(String importName, RoyaleProject project, CompletionList result)
    {
        List<CompletionItem> items = result.getItems();
        for (ICompilationUnit unit : project.getCompilationUnits())
        {
            if (unit == null)
            {
                continue;
            }
            Collection<IDefinition> definitions = null;
            try
            {
                definitions = unit.getFileScopeRequest().get().getExternallyVisibleDefinitions();
            }
            catch (Exception e)
            {
                //safe to ignore
                continue;
            }
            for (IDefinition definition : definitions)
            {
                if (definition instanceof ITypeDefinition)
                {
                    String qualifiedName = definition.getQualifiedName();
                    if (qualifiedName.equals(definition.getBaseName()))
                    {
                        //this definition is top-level. no import required.
                        continue;
                    }
                    if (qualifiedName.startsWith(importName))
                    {
                        int index = importName.lastIndexOf(".");
                        if (index != -1)
                        {
                            qualifiedName = qualifiedName.substring(index + 1);
                        }
                        index = qualifiedName.indexOf(".");
                        if (index > 0)
                        {
                            qualifiedName = qualifiedName.substring(0, index);
                        }
                        CompletionItem item = new CompletionItem();
                        item.setLabel(qualifiedName);
                        if (definition.getBaseName().equals(qualifiedName))
                        {
                            ITypeDefinition typeDefinition = (ITypeDefinition) definition;
                            if (typeDefinition instanceof IInterfaceDefinition)
                            {
                                item.setKind(CompletionItemKind.Interface);
                            }
                            else if (typeDefinition instanceof IClassDefinition)
                            {
                                item.setKind(CompletionItemKind.Class);
                            }
                            else
                            {
                                item.setKind(CompletionItemKind.Text);
                            }
                        }
                        else
                        {
                            item.setKind(CompletionItemKind.Text);
                        }
                        if (!items.contains(item))
                        {
                            items.add(item);
                        }
                    }
                }
            }
        }
    }

    private void addMXMLLanguageTagToAutoComplete(String tagName, String prefix, boolean includeOpenTagPrefix, boolean tagsNeedOpenBracket, CompletionList result)
    {
        List<CompletionItem> items = result.getItems();
        CompletionItem item = new CompletionItem();
        item.setKind(CompletionItemKind.Keyword);
        item.setLabel("fx:" + tagName);
        if (completionSupportsSnippets)
        {
            item.setInsertTextFormat(InsertTextFormat.Snippet);
        }
        item.setFilterText(tagName);
        item.setSortText(tagName);
        StringBuilder builder = new StringBuilder();
        if (tagsNeedOpenBracket)
        {
            builder.append("<");
        }
        if (includeOpenTagPrefix)
        {
            builder.append(prefix);
            builder.append(IMXMLCoreConstants.colon);
        }
        builder.append(tagName);
        builder.append(">");
        builder.append("\n");
        builder.append("\t");
        if (completionSupportsSnippets)
        {
            builder.append("$0");
        }
        builder.append("\n");
        builder.append("<");
        builder.append(IMXMLCoreConstants.slash);
        builder.append(prefix);
        builder.append(IMXMLCoreConstants.colon);
        builder.append(tagName);
        builder.append(">");
        item.setInsertText(builder.toString());
        items.add(item);
    }

    private void addRootMXMLLanguageTagsToAutoComplete(IMXMLTagData offsetTag, String prefix, boolean includeOpenTagPrefix, boolean tagsNeedOpenBracket, CompletionList result)
    {
        List<CompletionItem> items = result.getItems();
                            
        CompletionItem item = new CompletionItem();
        item.setKind(CompletionItemKind.Keyword);
        item.setLabel("fx:" + IMXMLLanguageConstants.SCRIPT);
        if (completionSupportsSnippets)
        {
            item.setInsertTextFormat(InsertTextFormat.Snippet);
        }
        item.setFilterText(IMXMLLanguageConstants.SCRIPT);
        item.setSortText(IMXMLLanguageConstants.SCRIPT);
        StringBuilder builder = new StringBuilder();
        if (tagsNeedOpenBracket)
        {
            builder.append("<");
        }
        if (includeOpenTagPrefix)
        {
            builder.append(prefix);
            builder.append(IMXMLCoreConstants.colon);
        }
        builder.append(IMXMLLanguageConstants.SCRIPT);
        builder.append(">");
        builder.append("\n");
        builder.append("\t");
        builder.append(IMXMLCoreConstants.cDataStart);
        builder.append("\n");
        builder.append("\t\t");
        if (completionSupportsSnippets)
        {
            builder.append("$0");
        }
        builder.append("\n");
        builder.append("\t");
        builder.append(IMXMLCoreConstants.cDataEnd);
        builder.append("\n");
        builder.append("<");
        builder.append(IMXMLCoreConstants.slash);
        builder.append(prefix);
        builder.append(IMXMLCoreConstants.colon);
        builder.append(IMXMLLanguageConstants.SCRIPT);
        builder.append(">");
        item.setInsertText(builder.toString());
        items.add(item);

        addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.BINDING, prefix, includeOpenTagPrefix, tagsNeedOpenBracket, result);
        addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.DECLARATIONS, prefix, includeOpenTagPrefix, tagsNeedOpenBracket, result);
        addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.METADATA, prefix, includeOpenTagPrefix, tagsNeedOpenBracket, result);
        addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.STYLE, prefix, includeOpenTagPrefix, tagsNeedOpenBracket, result);
    }

    private void addMembersForMXMLTypeToAutoComplete(IClassDefinition definition,
            IMXMLTagData offsetTag, ICompilationUnit offsetUnit, boolean isAttribute, boolean includePrefix, boolean tagsNeedOpenBracket,
            AddImportData addImportData, Position xmlnsPosition, RoyaleProject project, CompletionList result)
    {
        IASScope[] scopes;
        try
        {
            scopes = offsetUnit.getFileScopeRequest().get().getScopes();
        }
        catch (Exception e)
        {
            return;
        }
        if (scopes != null && scopes.length > 0)
        {
            String propertyElementPrefix = null;
            if (includePrefix)
            {
                String prefix = offsetTag.getPrefix();
                if (prefix.length() > 0)
                {
                    propertyElementPrefix = prefix;
                }
            }
            TypeScope typeScope = (TypeScope) definition.getContainedScope();
            ASScope scope = (ASScope) scopes[0];
            addDefinitionsInTypeScopeToAutoCompleteMXML(typeScope, scope, isAttribute, propertyElementPrefix, tagsNeedOpenBracket, addImportData, xmlnsPosition, offsetTag, project, result);
            addStyleMetadataToAutoCompleteMXML(typeScope, isAttribute, propertyElementPrefix, tagsNeedOpenBracket, project, result);
            addEventMetadataToAutoCompleteMXML(typeScope, isAttribute, propertyElementPrefix, tagsNeedOpenBracket, project, result);
            if(isAttribute)
            {
                addLanguageAttributesToAutoCompleteMXML(typeScope, scope, project, result);
            }
        }
    }

    private void addLanguageAttributesToAutoCompleteMXML(TypeScope typeScope, ASScope otherScope, RoyaleProject project, CompletionList result)
    {
        List<CompletionItem> items = result.getItems();

        CompletionItem includeInItem = new CompletionItem();
        includeInItem.setKind(CompletionItemKind.Keyword);
        includeInItem.setLabel(IMXMLLanguageConstants.ATTRIBUTE_INCLUDE_IN);
        if (completionSupportsSnippets)
        {
            includeInItem.setInsertTextFormat(InsertTextFormat.Snippet);
            includeInItem.setInsertText(IMXMLLanguageConstants.ATTRIBUTE_INCLUDE_IN + "=\"$0\"");
        }
        items.add(includeInItem);

        CompletionItem excludeFromItem = new CompletionItem();
        excludeFromItem.setKind(CompletionItemKind.Keyword);
        excludeFromItem.setLabel(IMXMLLanguageConstants.ATTRIBUTE_EXCLUDE_FROM);
        if (completionSupportsSnippets)
        {
            excludeFromItem.setInsertTextFormat(InsertTextFormat.Snippet);
            excludeFromItem.setInsertText(IMXMLLanguageConstants.ATTRIBUTE_EXCLUDE_FROM + "=\"$0\"");
        }
        items.add(excludeFromItem);

        Set<INamespaceDefinition> namespaceSet = ScopeUtils.getNamespaceSetForScopes(typeScope, otherScope, project);

        IDefinition idPropertyDefinition = typeScope.getPropertyByNameForMemberAccess(project, IMXMLLanguageConstants.ATTRIBUTE_ID, namespaceSet);
        if (idPropertyDefinition == null)
        {
            CompletionItem idItem = new CompletionItem();
            idItem.setKind(CompletionItemKind.Keyword);
            idItem.setLabel(IMXMLLanguageConstants.ATTRIBUTE_ID);
            if (completionSupportsSnippets)
            {
                idItem.setInsertTextFormat(InsertTextFormat.Snippet);
                idItem.setInsertText(IMXMLLanguageConstants.ATTRIBUTE_ID + "=\"$0\"");
            }
            items.add(idItem);
        }

        if (frameworkSDKIsRoyale)
        {
            IDefinition localIdPropertyDefinition = typeScope.getPropertyByNameForMemberAccess(project, IMXMLLanguageConstants.ATTRIBUTE_LOCAL_ID, namespaceSet);
            if (localIdPropertyDefinition == null)
            {
                CompletionItem localIdItem = new CompletionItem();
                localIdItem.setKind(CompletionItemKind.Keyword);
                localIdItem.setLabel(IMXMLLanguageConstants.ATTRIBUTE_LOCAL_ID);
                if (completionSupportsSnippets)
                {
                    localIdItem.setInsertTextFormat(InsertTextFormat.Snippet);
                    localIdItem.setInsertText(IMXMLLanguageConstants.ATTRIBUTE_LOCAL_ID + "=\"$0\"");
                }
                items.add(localIdItem);
            }
        }
    }

    private void addDefinitionsInTypeScopeToAutoCompleteActionScript(TypeScope typeScope, ASScope otherScope,
        boolean isStatic, AddImportData addImportData,
        RoyaleProject project, CompletionList result)
    {
        addDefinitionsInTypeScopeToAutoComplete(typeScope, otherScope, isStatic, false, false, false, null, false, addImportData, null, null, project, result);
    }

    private void addDefinitionsInTypeScopeToAutoCompleteMXML(TypeScope typeScope, ASScope otherScope,
        boolean isAttribute, String prefix, boolean tagsNeedOpenBracket,
        AddImportData addImportData, Position xmlnsPosition,
        IMXMLTagData offsetTag, RoyaleProject project, CompletionList result)
    {
        addDefinitionsInTypeScopeToAutoComplete(typeScope, otherScope, false, false, true, isAttribute, prefix, tagsNeedOpenBracket, addImportData, xmlnsPosition, offsetTag, project, result);
    }

    private void addDefinitionsInTypeScopeToAutoComplete(TypeScope typeScope, ASScope otherScope,
        boolean isStatic, boolean includeSuperStatics,
        boolean forMXML, boolean isAttribute, String prefix, boolean tagsNeedOpenBracket,
        AddImportData addImportData, Position xmlnsPosition,
        IMXMLTagData offsetTag, RoyaleProject project, CompletionList result)
    {
        IMetaTag[] excludeMetaTags = typeScope.getDefinition().getMetaTagsByName(IMetaAttributeConstants.ATTRIBUTE_EXCLUDE);
        ArrayList<IDefinition> memberAccessDefinitions = new ArrayList<>();
        Set<INamespaceDefinition> namespaceSet = ScopeUtils.getNamespaceSetForScopes(typeScope, otherScope, project);
        
        typeScope.getAllPropertiesForMemberAccess(project, memberAccessDefinitions, namespaceSet);
        for (IDefinition localDefinition : memberAccessDefinitions)
        {
            if (localDefinition.isOverride())
            {
                //overrides would add unnecessary duplicates to the list
                continue;
            }
            if (excludeMetaTags != null && excludeMetaTags.length > 0)
            {
                boolean exclude = false;
                for (IMetaTag excludeMetaTag : excludeMetaTags)
                {
                    String excludeName = excludeMetaTag.getAttributeValue(IMetaAttributeConstants.NAME_EXCLUDE_NAME);
                    if (excludeName.equals(localDefinition.getBaseName()))
                    {
                        exclude = true;
                        break;
                    }
                }
                if (exclude)
                {
                    continue;
                }
            }
            //there are some things that we need to skip in MXML
            if (forMXML)
            {
                if (localDefinition instanceof IGetterDefinition)
                {
                    //no getters because we can only set
                    continue;
                }
                else if (localDefinition instanceof IFunctionDefinition &&
                        !(localDefinition instanceof ISetterDefinition))
                {
                    //no calling functions, unless they're setters
                    continue;
                }
            }
            else //actionscript
            {
                if (localDefinition instanceof ISetterDefinition)
                {
                    ISetterDefinition setter = (ISetterDefinition) localDefinition;
                    IGetterDefinition getter = setter.resolveGetter(project);
                    if (getter != null)
                    {
                        //skip the setter if there's also a getter because it
                        //would add a duplicate entry
                        continue;
                    }
                }
            }
            if (isStatic)
            {
                if (!localDefinition.isStatic())
                {
                    //if we want static members, and the definition isn't
                    //static, skip it
                    continue;
                }
                if (!includeSuperStatics && localDefinition.getParent() != typeScope.getContainingDefinition())
                {
                    //if we want static members, then members from base classes
                    //aren't available with member access
                    continue;
                }
            }
            if (!isStatic && localDefinition.isStatic())
            {
                //if we want non-static members, and the definition is static,
                //skip it!
                continue;
            }
            if (forMXML)
            {
                addDefinitionAutoCompleteMXML(localDefinition, xmlnsPosition, isAttribute, prefix, null, tagsNeedOpenBracket, offsetTag, project, result);
            }
            else //actionscript
            {
                addDefinitionAutoCompleteActionScript(localDefinition, null, addImportData, project, result);
            }
        }
    }

    private void addEventMetadataToAutoCompleteMXML(TypeScope typeScope, boolean isAttribute, String prefix, boolean tagsNeedOpenBracket, RoyaleProject project, CompletionList result)
    {
        ArrayList<String> eventNames = new ArrayList<>();
        IDefinition definition = typeScope.getDefinition();
        while (definition instanceof IClassDefinition)
        {
            IClassDefinition classDefinition = (IClassDefinition) definition;
            IMetaTag[] eventMetaTags = definition.getMetaTagsByName(IMetaAttributeConstants.ATTRIBUTE_EVENT);
            for (IMetaTag eventMetaTag : eventMetaTags)
            {
                String eventName = eventMetaTag.getAttributeValue(IMetaAttributeConstants.NAME_EVENT_NAME);
                if (eventName == null || eventName.length() == 0)
                {
                    //vscode expects all items to have a name
                    continue;
                }
                if (eventNames.contains(eventName))
                {
                    //avoid duplicates!
                    continue;
                }
                eventNames.add(eventName);
                IDefinition eventDefinition = project.resolveSpecifier(classDefinition, eventName);
                if (eventDefinition == null)
                {
                    continue;
                }
                CompletionItem item = CompletionItemUtils.createDefinitionItem(eventDefinition, project);
                if (isAttribute && completionSupportsSnippets)
                {
                    item.setInsertTextFormat(InsertTextFormat.Snippet);
                    item.setInsertText(eventName + "=\"$0\"");
                }
                else if (!isAttribute)
                {
                    StringBuilder builder = new StringBuilder();
                    if (tagsNeedOpenBracket)
                    {
                        builder.append("<");
                    }
                    if(prefix != null)
                    {
                        builder.append(prefix);
                        builder.append(IMXMLCoreConstants.colon);
                    }
                    builder.append(eventName);
                    if (completionSupportsSnippets)
                    {
                        item.setInsertTextFormat(InsertTextFormat.Snippet);
                        builder.append(">");
                        builder.append("$0");
                        builder.append("</");
                        if(prefix != null)
                        {
                            builder.append(prefix);
                            builder.append(IMXMLCoreConstants.colon);
                        }
                        builder.append(eventName);
                        builder.append(">");
                    }
                    item.setInsertText(builder.toString());
                }
                result.getItems().add(item);
            }
            definition = classDefinition.resolveBaseClass(project);
        }
    }

    private void addStyleMetadataToAutoCompleteMXML(TypeScope typeScope, boolean isAttribute, String prefix, boolean tagsNeedOpenBracket, RoyaleProject project, CompletionList result)
    {
        ArrayList<String> styleNames = new ArrayList<>();
        IDefinition definition = typeScope.getDefinition();
        List<CompletionItem> items = result.getItems();
        while (definition instanceof IClassDefinition)
        {
            IClassDefinition classDefinition = (IClassDefinition) definition;
            IMetaTag[] styleMetaTags = definition.getMetaTagsByName(IMetaAttributeConstants.ATTRIBUTE_STYLE);
            for (IMetaTag styleMetaTag : styleMetaTags)
            {
                String styleName = styleMetaTag.getAttributeValue(IMetaAttributeConstants.NAME_STYLE_NAME);
                if (styleName == null || styleName.length() == 0)
                {
                    //vscode expects all items to have a name
                    continue;
                }
                if (styleNames.contains(styleName))
                {
                    //avoid duplicates!
                    continue;
                }
                styleNames.add(styleName);
                IDefinition styleDefinition = project.resolveSpecifier(classDefinition, styleName);
                if (styleDefinition == null)
                {
                    continue;
                }
                boolean foundExisting = false;
                for (CompletionItem item : items)
                {
                    if (item.getLabel().equals(styleName))
                    {
                        //we want to avoid adding a duplicate item with the same
                        //name. in flex, it's possible for a component to have
                        //a property and a style with the same name.
                        //if there's a conflict, the compiler will know how to handle it.
                        foundExisting = true;
                        break;
                    }
                }
                if (foundExisting)
                {
                    break;
                }
                CompletionItem item = CompletionItemUtils.createDefinitionItem(styleDefinition, project);
                if (isAttribute && completionSupportsSnippets)
                {
                    item.setInsertTextFormat(InsertTextFormat.Snippet);
                    item.setInsertText(styleName + "=\"$0\"");
                }
                else if (!isAttribute)
                {
                    StringBuilder builder = new StringBuilder();
                    if (tagsNeedOpenBracket)
                    {
                        builder.append("<");
                    }
                    if(prefix != null)
                    {
                        builder.append(prefix);
                        builder.append(IMXMLCoreConstants.colon);
                    }
                    builder.append(styleName);
                    if (completionSupportsSnippets)
                    {
                        item.setInsertTextFormat(InsertTextFormat.Snippet);
                        builder.append(">");
                        builder.append("$0");
                        builder.append("</");
                        if(prefix != null)
                        {
                            builder.append(prefix);
                            builder.append(IMXMLCoreConstants.colon);
                        }
                        builder.append(styleName);
                        builder.append(">");
                    }
                    item.setInsertText(builder.toString());
                }
                items.add(item);
            }
            definition = classDefinition.resolveBaseClass(project);
        }
    }

    private void addMXMLTypeDefinitionAutoComplete(ITypeDefinition definition, Position xmlnsPosition, ICompilationUnit offsetUnit, IMXMLTagData offsetTag, boolean tagsNeedOpenBracket, RoyaleProject project, CompletionList result)
    {
        IMXMLDataManager mxmlDataManager = compilerWorkspace.getMXMLDataManager();
        MXMLData mxmlData = (MXMLData) mxmlDataManager.get(fileSpecGetter.getFileSpecification(offsetUnit.getAbsoluteFilename()));
        MXMLNamespace discoveredNS = MXMLNamespaceUtils.getMXMLNamespaceForTypeDefinition(definition, mxmlData, project);
        addDefinitionAutoCompleteMXML(definition, xmlnsPosition, false, discoveredNS.prefix, discoveredNS.uri, tagsNeedOpenBracket, offsetTag, project, result);
    }

    private boolean isDuplicateTypeDefinition(IDefinition definition)
    {
        if (definition instanceof ITypeDefinition)
        {
            String qualifiedName = definition.getQualifiedName();
            return completionTypes.contains(qualifiedName);
        }
        return false;
    }

    private void addDefinitionAutoCompleteActionScript(IDefinition definition, IASNode offsetNode, AddImportData addImportData, RoyaleProject project, CompletionList result)
    {
        String definitionBaseName = definition.getBaseName();
        if (definitionBaseName.length() == 0)
        {
            //vscode expects all items to have a name
            return;
        }
        if (definitionBaseName.startsWith(VECTOR_HIDDEN_PREFIX))
        {
            return;
        }
        if (isDuplicateTypeDefinition(definition))
        {
            return;
        }
        if (definition instanceof ITypeDefinition)
        {
            String qualifiedName = definition.getQualifiedName();
            completionTypes.add(qualifiedName);
        }
        CompletionItem item = CompletionItemUtils.createDefinitionItem(definition, project);
        /*if (definition instanceof IFunctionDefinition
                && !(definition instanceof IAccessorDefinition)
                && completionSupportsSnippets)
        {
            IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
            if (functionDefinition.getParameters().length == 0)
            {
                item.setInsertText(definition.getBaseName() + "()");
            }
            else
            {
                item.setInsertTextFormat(InsertTextFormat.Snippet);
                item.setInsertText(definition.getBaseName() + "($0)");
                //TODO: manually activate signature help
            }
        }*/
        if (ASTUtils.needsImport(offsetNode, definition.getQualifiedName()))
        {
            TextEdit textEdit = CodeActionsUtils.createTextEditForAddImport(definition, addImportData);
            if(textEdit != null)
            {
                item.setAdditionalTextEdits(Collections.singletonList(textEdit));
            }
        }
        IDeprecationInfo deprecationInfo = definition.getDeprecationInfo();
        if (deprecationInfo != null)
        {
            item.setDeprecated(true);
        }
        result.getItems().add(item);
    }

    private void addDefinitionAutoCompleteMXML(IDefinition definition, Position xmlnsPosition, boolean isAttribute, String prefix, String uri, boolean tagsNeedOpenBracket, IMXMLTagData offsetTag, RoyaleProject project, CompletionList result)
    {
        if (definition.getBaseName().startsWith(VECTOR_HIDDEN_PREFIX))
        {
            return;
        }
        if (isDuplicateTypeDefinition(definition))
        {
            return;
        }
        if (definition instanceof ITypeDefinition)
        {
            String qualifiedName = definition.getQualifiedName();
            completionTypes.add(qualifiedName);
        }
        String definitionBaseName = definition.getBaseName();
        if (definitionBaseName.length() == 0)
        {
            //vscode expects all items to have a name
            return;
        }
        CompletionItem item = CompletionItemUtils.createDefinitionItem(definition, project);
        if (isAttribute && completionSupportsSnippets)
        {
            item.setInsertTextFormat(InsertTextFormat.Snippet);
            item.setInsertText(definitionBaseName + "=\"$0\"");
        }
        else if (!isAttribute)
        {
            if (definition instanceof ITypeDefinition && prefix != null)
            {
                StringBuilder labelBuilder = new StringBuilder();
                labelBuilder.append(prefix);
                labelBuilder.append(IMXMLCoreConstants.colon);
                labelBuilder.append(definitionBaseName);
                item.setLabel(labelBuilder.toString());
                item.setSortText(definitionBaseName);
                item.setFilterText(definitionBaseName);
            }
            StringBuilder insertTextBuilder = new StringBuilder();
            if (tagsNeedOpenBracket)
            {
                insertTextBuilder.append("<");
            }
            if(prefix != null)
            {
                insertTextBuilder.append(prefix);
                insertTextBuilder.append(IMXMLCoreConstants.colon);
            }
            insertTextBuilder.append(definitionBaseName);
            if (definition instanceof ITypeDefinition
                    && prefix != null
                    && (offsetTag == null || offsetTag.equals(offsetTag.getParent().getRootTag()))
                    && xmlnsPosition == null)
            {
                //if this is the root tag, we should add the XML namespace and
                //close the tag automatically
                insertTextBuilder.append(" ");
                if(!uri.equals(IMXMLLanguageConstants.NAMESPACE_MXML_2009))
                {
                    insertTextBuilder.append("xmlns");
                    insertTextBuilder.append(IMXMLCoreConstants.colon);
                    insertTextBuilder.append("fx=\"");
                    insertTextBuilder.append(IMXMLLanguageConstants.NAMESPACE_MXML_2009);
                    insertTextBuilder.append("\"\n\t");
                }
                insertTextBuilder.append("xmlns");
                insertTextBuilder.append(IMXMLCoreConstants.colon);
                insertTextBuilder.append(prefix);
                insertTextBuilder.append("=\"");
                insertTextBuilder.append(uri);
                insertTextBuilder.append("\">\n\t");
                if (completionSupportsSnippets)
                {
                    item.setInsertTextFormat(InsertTextFormat.Snippet);
                    insertTextBuilder.append("$0");
                }
                insertTextBuilder.append("\n</");
                insertTextBuilder.append(prefix);
                insertTextBuilder.append(IMXMLCoreConstants.colon);
                insertTextBuilder.append(definitionBaseName);
                insertTextBuilder.append(">");
            }
            if (completionSupportsSnippets && !(definition instanceof ITypeDefinition))
            {
                item.setInsertTextFormat(InsertTextFormat.Snippet);
                insertTextBuilder.append(">");
                insertTextBuilder.append("$0");
                insertTextBuilder.append("</");
                if(prefix != null)
                {
                    insertTextBuilder.append(prefix);
                    insertTextBuilder.append(IMXMLCoreConstants.colon);
                }
                insertTextBuilder.append(definitionBaseName);
                insertTextBuilder.append(">");
            }
            item.setInsertText(insertTextBuilder.toString());
            if (definition instanceof ITypeDefinition
                    && prefix != null && uri != null
                    && MXMLDataUtils.needsNamespace(offsetTag, prefix, uri)
                    && xmlnsPosition != null)
            {
                TextEdit textEdit = CodeActionsUtils.createTextEditForAddMXMLNamespace(prefix, uri, xmlnsPosition);
                if(textEdit != null)
                {
                    item.setAdditionalTextEdits(Collections.singletonList(textEdit));
                }
            }
        }
        IDeprecationInfo deprecationInfo = definition.getDeprecationInfo();
        if (deprecationInfo != null)
        {
            item.setDeprecated(true);
        }
        result.getItems().add(item);
    }

    private void resolveDefinition(IDefinition definition, RoyaleProject project, List<Location> result)
    {
        String definitionPath = definition.getSourcePath();
        String containingSourceFilePath = definition.getContainingSourceFilePath(project);
        if(includedFiles.containsKey(containingSourceFilePath))
        {
            definitionPath = containingSourceFilePath;
        }
        if (definitionPath == null)
        {
            //if the definition is in an MXML file, getSourcePath() may return
            //null, but getContainingFilePath() will return something
            definitionPath = definition.getContainingFilePath();
            if (definitionPath == null)
            {
                //if everything is null, there's nothing to do
                return;
            }
            //however, getContainingFilePath() also works for SWCs
            if (!definitionPath.endsWith(AS_EXTENSION)
                    && !definitionPath.endsWith(MXML_EXTENSION)
                    && (definitionPath.contains(SDK_LIBRARY_PATH_SIGNATURE_UNIX)
                    || definitionPath.contains(SDK_LIBRARY_PATH_SIGNATURE_WINDOWS)))
            {
                //if it's a framework SWC, we're going to attempt to resolve
                //the source file 
                String debugPath = DefinitionUtils.getDefinitionDebugSourceFilePath(definition, project);
                if (debugPath != null)
                {
                    definitionPath = debugPath;
                }
            }
            if (definitionPath.endsWith(SWC_EXTENSION))
            {
                DefinitionAsText definitionText = DefinitionTextUtils.definitionToTextDocument(definition, project);
                //may be null if definitionToTextDocument() doesn't know how
                //to parse that type of definition
                if (definitionText != null)
                {
                    //if we get here, we couldn't find a framework source file and
                    //the definition path still ends with .swc
                    //we're going to try our best to display "decompiled" content
                    result.add(definitionText.toLocation());
                }
                return;
            }
            if (!definitionPath.endsWith(AS_EXTENSION)
                    && !definitionPath.endsWith(MXML_EXTENSION))
            {
                //if it's anything else, we don't know how to resolve
                return;
            }
        }

        Path resolvedPath = Paths.get(definitionPath);
        Location location = new Location();
        location.setUri(resolvedPath.toUri().toString());
        int nameLine = definition.getNameLine();
        int nameColumn = definition.getNameColumn();
        if (nameLine == -1 || nameColumn == -1)
        {
            //getNameLine() and getNameColumn() will both return -1 for a
            //variable definition created by an MXML tag with an id.
            //so we need to figure them out from the offset instead.
            int nameOffset = definition.getNameStart();
            if (nameOffset == -1)
            {
                //we can't find the name, so give up
                return;
            }

            Reader reader = getReaderForPath(resolvedPath);
            if (reader == null)
            {
                //we can't get the code at all
                return;
            }

            Position position = LanguageServerCompilerUtils.getPositionFromOffset(reader, nameOffset);
            try
            {
                reader.close();
            }
            catch(IOException e) {}
            nameLine = position.getLine();
            nameColumn = position.getCharacter();
        }
        if (nameLine == -1 || nameColumn == -1)
        {
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

    private int getFunctionCallNodeArgumentIndex(IFunctionCallNode functionCallNode, IASNode offsetNode)
    {
        if (offsetNode == functionCallNode.getArgumentsNode()
                && offsetNode.getChildCount() == 0)
        {
            //there are no arguments yet
            return 0;
        }
        int indexToFind = offsetNode.getAbsoluteEnd();
        IExpressionNode[] argumentNodes = functionCallNode.getArgumentNodes();
        for (int i = argumentNodes.length - 1; i >= 0; i--)
        {
            IExpressionNode argumentNode = argumentNodes[i];
            if (indexToFind >= argumentNode.getAbsoluteStart())
            {
                return i;
            }
        }
        return -1;
    }

    private IFunctionCallNode getAncestorFunctionCallNode(IASNode offsetNode)
    {
        IASNode currentNode = offsetNode;
        do
        {
            if (currentNode instanceof IFunctionCallNode)
            {
                return (IFunctionCallNode) currentNode;
            }
            if (currentNode instanceof IScopedDefinitionNode)
            {
                return null;
            }
            currentNode = currentNode.getParent();
        }
        while (currentNode != null);
        return null;
    }

    private WorkspaceEdit renameDefinition(IDefinition definition, String newName, RoyaleProject project)
    {
        if (definition == null)
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
        WorkspaceEdit result = new WorkspaceEdit();
        List<Either<TextDocumentEdit, ResourceOperation>> documentChanges = new ArrayList<>();
        result.setDocumentChanges(documentChanges);
        if (definition.getContainingFilePath().endsWith(SWC_EXTENSION))
        {
            if (languageClient != null)
            {
                MessageParams message = new MessageParams();
                message.setType(MessageType.Info);
                message.setMessage("You cannot rename an element defined in a SWC file.");
                languageClient.showMessage(message);
            }
            return result;
        }
        if (definition instanceof IPackageDefinition)
        {
            if (languageClient != null)
            {
                MessageParams message = new MessageParams();
                message.setType(MessageType.Info);
                message.setMessage("You cannot rename a package.");
                languageClient.showMessage(message);
            }
            return result;
        }
        Path originalDefinitionFilePath = null;
        Path newDefinitionFilePath = null;
        for (ICompilationUnit compilationUnit : project.getCompilationUnits())
        {
            if (compilationUnit == null
                    || compilationUnit instanceof SWCCompilationUnit
                    || compilationUnit instanceof ResourceBundleCompilationUnit)
            {
                continue;
            }
            ArrayList<TextEdit> textEdits = new ArrayList<>();
            if (compilationUnit.getAbsoluteFilename().endsWith(MXML_EXTENSION))
            {
                IMXMLDataManager mxmlDataManager = compilerWorkspace.getMXMLDataManager();
                MXMLData mxmlData = (MXMLData) mxmlDataManager.get(fileSpecGetter.getFileSpecification(compilationUnit.getAbsoluteFilename()));
                IMXMLTagData rootTag = mxmlData.getRootTag();
                if (rootTag != null)
                {
                    ArrayList<ISourceLocation> units = new ArrayList<>();
                    findMXMLUnits(mxmlData.getRootTag(), definition, project, units);
                    for (ISourceLocation otherUnit : units)
                    {
                        TextEdit textEdit = new TextEdit();
                        textEdit.setNewText(newName);

                        Range range = LanguageServerCompilerUtils.getRangeFromSourceLocation(otherUnit);
                        if (range == null)
                        {
                            continue;
                        }
                        textEdit.setRange(range);

                        textEdits.add(textEdit);
                    }
                }
            }
            IASNode ast = getAST(compilationUnit);
            if (ast != null)
            {
                ArrayList<IIdentifierNode> identifiers = new ArrayList<>();
                ASTUtils.findIdentifiersForDefinition(ast, definition, project, identifiers);
                for (IIdentifierNode identifierNode : identifiers)
                {
                    TextEdit textEdit = new TextEdit();
                    textEdit.setNewText(newName);

                    Range range = LanguageServerCompilerUtils.getRangeFromSourceLocation(identifierNode);
                    if (range == null)
                    {
                        continue;
                    }
                    textEdit.setRange(range);

                    textEdits.add(textEdit);
                }
            }
            if (textEdits.size() == 0)
            {
                continue;
            }

            Path textDocumentPath = Paths.get(compilationUnit.getAbsoluteFilename());
            if (definitionIsMainDefinitionInCompilationUnit(compilationUnit, definition))
            {
                originalDefinitionFilePath = textDocumentPath;
                String newBaseName = newName + "." + Files.getFileExtension(originalDefinitionFilePath.toFile().getName());
                newDefinitionFilePath = originalDefinitionFilePath.getParent().resolve(newBaseName);
            }
            
            VersionedTextDocumentIdentifier versionedIdentifier =
                    new VersionedTextDocumentIdentifier(textDocumentPath.toUri().toString(), null);
            TextDocumentEdit textDocumentEdit = new TextDocumentEdit(versionedIdentifier, textEdits);
            documentChanges.add(Either.forLeft(textDocumentEdit));
        }
        if (newDefinitionFilePath != null)
        {
            RenameFile renameFile = new RenameFile();
            renameFile.setOldUri(originalDefinitionFilePath.toUri().toString());
            renameFile.setNewUri(newDefinitionFilePath.toUri().toString());
            documentChanges.add(Either.forRight(renameFile));
        }
        return result;
    }

    private boolean definitionIsMainDefinitionInCompilationUnit(ICompilationUnit unit, IDefinition definition)
    {
        IASScope[] scopes;
        try
        {
            scopes = unit.getFileScopeRequest().get().getScopes();
        }
        catch (Exception e)
        {
            return false;
        }
        for (IASScope scope : scopes)
        {
            for (IDefinition localDefinition : scope.getAllLocalDefinitions())
            {
                if (localDefinition instanceof IPackageDefinition)
                {
                    IPackageDefinition packageDefinition = (IPackageDefinition) localDefinition;
                    IASScope packageScope = packageDefinition.getContainedScope();
                    boolean mightBeConstructor = definition instanceof IFunctionDefinition;
                    for (IDefinition localDefinition2 : packageScope.getAllLocalDefinitions())
                    {
                        if(localDefinition2 == definition)
                        {
                            return true;
                        }
                        if(mightBeConstructor && localDefinition2 instanceof IClassDefinition)
                        {
                            IClassDefinition classDefinition = (IClassDefinition) localDefinition2;
                            if (classDefinition.getConstructor() == definition)
                            {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private void findMXMLUnits(IMXMLTagData tagData, IDefinition definition, RoyaleProject project, List<ISourceLocation> result)
    {
        IDefinition tagDefinition = project.resolveXMLNameToDefinition(tagData.getXMLName(), tagData.getMXMLDialect());
        if (tagDefinition != null && definition == tagDefinition)
        {
            result.add(tagData);
        }
        if (tagDefinition instanceof IClassDefinition)
        {
            IClassDefinition classDefinition = (IClassDefinition) tagDefinition;
            IMXMLTagAttributeData[] attributes = tagData.getAttributeDatas();
            for (IMXMLTagAttributeData attributeData : attributes)
            {
                IDefinition attributeDefinition = project.resolveSpecifier(classDefinition, attributeData.getShortName());
                if (attributeDefinition != null && definition == attributeDefinition)
                {
                    result.add(attributeData);
                }
            }
        }
        IMXMLTagData childTag = tagData.getFirstChild(true);
        while (childTag != null)
        {
            if (childTag.isCloseTag())
            {
                //only open tags matter
                continue;
            }
            findMXMLUnits(childTag, definition, project, result);
            childTag = childTag.getNextSibling(true);
        }
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
        Reader reader = getReaderForPath(path);
        if (reader != null)
        {
            StreamingASTokenizer tokenizer = StreamingASTokenizer.createForRepairingASTokenizer(reader, path.toString(), null);
            ASToken[] tokens = tokenizer.getTokens(reader);
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

        if (reader != null)
        {
            try
            {
                reader.close();
            }
            catch(IOException e) {}
            reader = null;
        }
    }

    private ICompilationUnit findCompilationUnit(Path pathToFind, RoyaleProject project)
    {
        if(project == null)
        {
            return null;
        }
        for (ICompilationUnit unit : project.getCompilationUnits())
        {
            //it's possible for the collection of compilation units to contain
            //null values, so be sure to check for null values before checking
            //the file name
            if (unit == null)
            {
                continue;
            }
            Path unitPath = Paths.get(unit.getAbsoluteFilename());
            if(unitPath.equals(pathToFind))
            {
                return unit;
            }
        }
        return null;
    }

    private ICompilationUnit findCompilationUnit(Path pathToFind)
    {
        for (WorkspaceFolderData folderData : workspaceFolderToData.values())
        {
            RoyaleProject project = folderData.project;
            if (project == null)
            {
                continue;
            }
            ICompilationUnit result = findCompilationUnit(pathToFind, project);
            if(result != null)
            {
                return result;
            }
        }
        return null;
    }

    private ICompilationUnit findCompilationUnit(String absoluteFileName)
    {
        Path pathToFind = Paths.get(absoluteFileName);
        return findCompilationUnit(pathToFind);
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

    private IASNode getAST(ICompilationUnit unit)
    {
        IASNode ast = null;
        try
        {
            ast = unit.getSyntaxTreeRequest().get().getAST();
        }
        catch (InterruptedException e)
        {
            System.err.println("Interrupted while getting AST: " + unit.getAbsoluteFilename());
            return null;
        }
        if (ast == null)
        {
            //we couldn't find the root node for this file
            System.err.println("Could not find AST: " + unit.getAbsoluteFilename());
            return null;
        }
        if (ast instanceof FileNode)
        {
            FileNode fileNode = (FileNode) ast;
            //seems to work better than populateFunctionNodes() alone
            fileNode.parseRequiredFunctionBodies();
        }
        if (ast instanceof IFileNode)
        {
            try
            {
                IFileNode fileNode = (IFileNode) ast;
                //call this in addition to parseRequiredFunctionBodies() because
                //functions in included files won't be populated without it
                fileNode.populateFunctionNodes();
            }
            catch(NullPointerException e)
            {
                //sometimes, a null pointer exception can be thrown inside
                //FunctionNode.parseFunctionBody(). seems like a Royale bug.
            }
        }
        return ast;
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
            configProblems.addAll(configurator.getConfigurationProblems());
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
            for(Path filePath : sourceByPath.keySet())
            {
                WorkspaceFolderData otherFolderData = getWorkspaceFolderDataForSourceFile(filePath);
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
            IncludeFileData includedFile = includedFiles.get(problemSourcePath);
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
            ICompilationUnit unitForPath = findCompilationUnit(path, folderData.project);
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
                    CompilationUnitUtils.findIncludedFiles(unitForPath, includedFiles);
                    return true;
                }
                //start fresh when checking all compilation units
                includedFiles.clear();
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
                            CompilationUnitUtils.findIncludedFiles(unit, includedFiles);
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

    private List<WorkspaceFolderData> getAllWorkspaceFolderDataForSourceFile(Path path)
    {
        List<WorkspaceFolderData> result = new ArrayList<>();
        for (WorkspaceFolder folder : workspaceFolderToData.keySet())
        {
            WorkspaceFolderData folderData = workspaceFolderToData.get(folder);
            RoyaleProject project = folderData.project;
            if (project == null)
            {
                String folderUri = folder.getUri();
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

    private List<WorkspaceFolderData> getAllWorkspaceFolderDataForSWCFile(Path path)
    {
        List<WorkspaceFolderData> result = new ArrayList<>();
        for (WorkspaceFolder folder : workspaceFolderToData.keySet())
        {
            WorkspaceFolderData folderData = workspaceFolderToData.get(folder);
            RoyaleProject project = folderData.project;
            if (project == null)
            {
                String folderUri = folder.getUri();
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

    private WorkspaceFolderData getWorkspaceFolderDataForSourceFile(Path path)
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

    private String getFileTextForPath(Path path)
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
        String text = null;
        try
        {
            text = IOUtils.toString(reader);
        }
        catch (IOException e) {}
        try
        {
            reader.close();
        }
        catch(IOException e) {}
        return text;
    }

    private Reader getReaderForPath(Path path)
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

    private MXMLData getMXMLDataForPath(Path path, WorkspaceFolderData folderData)
    {
        IncludeFileData includeFileData = includedFiles.get(path.toString());
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
        ICompilationUnit unit = findCompilationUnit(path, project);
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

    private IMXMLTagData getOffsetMXMLTag(MXMLData mxmlData, int currentOffset)
    {
        if (mxmlData == null)
        {
            return null;
        }
        IMXMLUnitData unitData = mxmlData.findContainmentReferenceUnit(currentOffset);
        IMXMLUnitData currentUnitData = unitData;
        while (currentUnitData != null)
        {
            if (currentUnitData instanceof IMXMLTagData)
            {
                IMXMLTagData tagData = (IMXMLTagData) currentUnitData;
                return tagData;
            }
            currentUnitData = currentUnitData.getParentUnitData();
        }
        return null;
    }

    private int getOffsetFromPathAndPosition(Path path, Position position)
    {
        Reader reader = getReaderForPath(path);
        int offset = LanguageServerCompilerUtils.getOffsetFromPosition(reader, position);
        try
        {
            reader.close();
        }
        catch(IOException e) {}
 
        IncludeFileData includeFileData = includedFiles.get(path.toString());
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

    private IASNode getOffsetNode(Path path, int currentOffset, WorkspaceFolderData folderData)
    {
        IncludeFileData includeFileData = includedFiles.get(path.toString());
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

        ICompilationUnit unit = findCompilationUnit(path, project);
        if (unit == null)
        {
            //the path must be in the workspace or source-path
            return null;
        }

        IASNode ast = getAST(unit);
        if (ast == null)
        {
            return null;
        }

        return ASTUtils.getContainingNodeIncludingStart(ast, currentOffset);
    }

    private IASNode getEmbeddedActionScriptNodeInMXMLTag(IMXMLTagData tag, Path path, int currentOffset, WorkspaceFolderData folderData)
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

    private boolean isActionScriptCompletionAllowedInNode(Path path, IASNode offsetNode, int currentOffset)
    {
        if (offsetNode != null)
        {
            if (offsetNode.getNodeID().equals(ASTNodeID.LiteralStringID))
            {
                return false;
            }
            if (offsetNode.getNodeID().equals(ASTNodeID.LiteralRegexID))
            {
                return false;
            }
        }
        int minCommentStartIndex = 0;
        if (offsetNode instanceof IMXMLSpecifierNode)
        {
            IMXMLSpecifierNode mxmlNode = (IMXMLSpecifierNode) offsetNode;
            //start in the current MXML node and ignore the start of comments
            //that appear in earlier MXML nodes
            minCommentStartIndex = mxmlNode.getAbsoluteStart();
        }
        return !isInActionScriptComment(path, currentOffset, minCommentStartIndex);
    }

    private boolean isInActionScriptComment(Path path, int currentOffset, int minCommentStartIndex)
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

    private boolean isInXMLComment(Path path, int currentOffset)
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

    private void querySymbolsInScope(List<String> queries, IASScope scope, Set<String> foundTypes, RoyaleProject project, Collection<SymbolInformation> result)
    {
        Collection<IDefinition> definitions = scope.getAllLocalDefinitions();
        for (IDefinition definition : definitions)
        {
            if (definition instanceof IPackageDefinition)
            {
                IPackageDefinition packageDefinition = (IPackageDefinition) definition;
                IASScope packageScope = packageDefinition.getContainedScope();
                querySymbolsInScope(queries, packageScope, foundTypes, project, result);
            }
            else if (definition instanceof ITypeDefinition)
            {
                String qualifiedName = definition.getQualifiedName();
                if (foundTypes.contains(qualifiedName))
                {
                    //skip types that we've already encountered because we don't
                    //want duplicates in the result
                    continue;
                }
                foundTypes.add(qualifiedName);
                ITypeDefinition typeDefinition = (ITypeDefinition) definition;
                if (!definition.isImplicit() && matchesQueries(queries, qualifiedName))
                {
                    SymbolInformation symbol = definitionToSymbolInformation(typeDefinition, project);
                    if (symbol != null)
                    {
                        result.add(symbol);
                    }
                }
                IASScope typeScope = typeDefinition.getContainedScope();
                querySymbolsInScope(queries, typeScope, foundTypes, project, result);
            }
            else if (definition instanceof IFunctionDefinition)
            {
                if (definition.isImplicit())
                {
                    continue;
                }
                if (!matchesQueries(queries, definition.getQualifiedName()))
                {
                    continue;
                }
                IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
                SymbolInformation symbol = definitionToSymbolInformation(functionDefinition, project);
                if (symbol != null)
                {
                    result.add(symbol);
                }
            }
            else if (definition instanceof IVariableDefinition)
            {
                if (definition.isImplicit())
                {
                    continue;
                }
                if (!matchesQueries(queries, definition.getQualifiedName()))
                {
                    continue;
                }
                IVariableDefinition variableDefinition = (IVariableDefinition) definition;
                SymbolInformation symbol = definitionToSymbolInformation(variableDefinition, project);
                if (symbol != null)
                {
                    result.add(symbol);
                }
            }
        }
    }

    private boolean matchesQueries(List<String> queries, String target)
    {
        String lowerCaseTarget = target.toLowerCase();
        int fromIndex = 0;
        for (String query : queries)
        {
            int index = lowerCaseTarget.indexOf(query, fromIndex);
            if (index == -1)
            {
                return false;
            }
            fromIndex = index + query.length();
        }
        return true;
    }

    private void scopeToSymbolInformation(IASScope scope, RoyaleProject project, List<SymbolInformation> result)
    {
        Collection<IDefinition> definitions = scope.getAllLocalDefinitions();
        for (IDefinition definition : definitions)
        {
            if (definition instanceof IPackageDefinition)
            {
                IPackageDefinition packageDefinition = (IPackageDefinition) definition;
                IASScope packageScope = packageDefinition.getContainedScope();
                scopeToSymbolInformation(packageScope, project, result);
            }
            else if (definition instanceof ITypeDefinition)
            {
                ITypeDefinition typeDefinition = (ITypeDefinition) definition;
                IASScope typeScope = typeDefinition.getContainedScope();
                if (!definition.isImplicit())
                {
                    SymbolInformation typeSymbol = definitionToSymbolInformation(typeDefinition, project);
                    result.add(typeSymbol);
                }
                scopeToSymbolInformation(typeScope, project, result);
                
            }
            else if (definition instanceof IFunctionDefinition
                    || definition instanceof IVariableDefinition)
            {
                if (definition.isImplicit())
                {
                    continue;
                }
                SymbolInformation localSymbol = definitionToSymbolInformation(definition, project);
                if (localSymbol != null)
                {
                    result.add(localSymbol);
                }
            }
        }
    }

    private void scopeToDocumentSymbols(IASScope scope, RoyaleProject project, List<DocumentSymbol> result)
    {
        Collection<IDefinition> definitions = scope.getAllLocalDefinitions();
        for (IDefinition definition : definitions)
        {
            if (definition instanceof IPackageDefinition)
            {
                IPackageDefinition packageDefinition = (IPackageDefinition) definition;
                IASScope packageScope = packageDefinition.getContainedScope();
                scopeToDocumentSymbols(packageScope, project, result);
            }
            else if (definition instanceof ITypeDefinition)
            {
                ITypeDefinition typeDefinition = (ITypeDefinition) definition;
                IASScope typeScope = typeDefinition.getContainedScope();
                List<DocumentSymbol> childSymbols = new ArrayList<>();
                scopeToDocumentSymbols(typeScope, project, childSymbols);

                if (definition.isImplicit())
                {
                    result.addAll(childSymbols);
                }
                else
                {
                    DocumentSymbol typeSymbol = definitionToDocumentSymbol(typeDefinition, project);
                    if (typeSymbol == null)
                    {
                        result.addAll(childSymbols);
                    }
                    else
                    {
                        typeSymbol.setChildren(childSymbols);
                        result.add(typeSymbol);
                    }
                }
                
            }
            else if (definition instanceof IFunctionDefinition
                    || definition instanceof IVariableDefinition)
            {
                if (definition.isImplicit())
                {
                    continue;
                }
                DocumentSymbol localSymbol = definitionToDocumentSymbol(definition, project);
                if (localSymbol != null)
                {
                    result.add(localSymbol);
                }
            }
        }
    }

    private SymbolInformation definitionToSymbolInformation(IDefinition definition, RoyaleProject project)
    {
        String definitionBaseName = definition.getBaseName();
        if (definitionBaseName.length() == 0)
        {
            //vscode expects all items to have a name
            return null;
        }

        Location location = definitionToLocation(definition, project);
        if (location == null)
        {
            //we can't find where the source code for this symbol is located
            return null;
        }

        SymbolInformation symbol = new SymbolInformation();
        symbol.setKind(LanguageServerCompilerUtils.getSymbolKindFromDefinition(definition));
        if (!definition.getQualifiedName().equals(definitionBaseName))
        {
            symbol.setContainerName(definition.getPackageName());
        }
        else if (definition instanceof ITypeDefinition)
        {
            symbol.setContainerName("No Package");
        }
        else
        {
            IDefinition parentDefinition = definition.getParent();
            if (parentDefinition != null)
            {
                symbol.setContainerName(parentDefinition.getQualifiedName());
            }
        }
        symbol.setName(definitionBaseName);

        symbol.setLocation(location);

        IDeprecationInfo deprecationInfo = definition.getDeprecationInfo();
        if (deprecationInfo != null)
        {
            symbol.setDeprecated(true);
        }

        return symbol;
    }

    private DocumentSymbol definitionToDocumentSymbol(IDefinition definition, RoyaleProject project)
    {
        String definitionBaseName = definition.getBaseName();
        if (definition instanceof IPackageDefinition)
        {
            definitionBaseName = "package " + definitionBaseName;
        }
        if (definitionBaseName.length() == 0)
        {
            //vscode expects all items to have a name
            return null;
        }

        Range range = definitionToRange(definition, project);
        if (range == null)
        {
            //we can't find where the source code for this symbol is located
            return null;
        }

        DocumentSymbol symbol = new DocumentSymbol();
        symbol.setKind(LanguageServerCompilerUtils.getSymbolKindFromDefinition(definition));
        symbol.setName(definitionBaseName);
        symbol.setRange(range);
        symbol.setSelectionRange(range);

        IDeprecationInfo deprecationInfo = definition.getDeprecationInfo();
        if (deprecationInfo != null)
        {
            symbol.setDeprecated(true);
        }

        return symbol;
    }

    private String definitionToSourcePath(IDefinition definition, RoyaleProject project)
    {
        String sourcePath = definition.getSourcePath();
        if (sourcePath == null)
        {
            //I'm not sure why getSourcePath() can sometimes return null, but
            //getContainingFilePath() seems to work as a fallback -JT
            sourcePath = definition.getContainingFilePath();
        }
        if (sourcePath == null)
        {
            return null;
        }
        if (!sourcePath.endsWith(AS_EXTENSION)
                && !sourcePath.endsWith(MXML_EXTENSION)
                && (sourcePath.contains(SDK_LIBRARY_PATH_SIGNATURE_UNIX)
                || sourcePath.contains(SDK_LIBRARY_PATH_SIGNATURE_WINDOWS)))
        {
            //if it's a framework SWC, we're going to attempt to resolve
            //the real source file 
            String debugPath = DefinitionUtils.getDefinitionDebugSourceFilePath(definition, project);
            if (debugPath != null)
            {
                //if we can't find the debug source file, keep the SWC extension
                sourcePath = debugPath;
            }
        }
        return sourcePath;
    }

    private Location definitionToLocation(IDefinition definition, RoyaleProject project)
    {
        String sourcePath = definitionToSourcePath(definition, project);
        if (sourcePath == null)
        {
            //we can't find where the source code for this symbol is located
            return null;
        }
        Location location = null;
        if (sourcePath.endsWith(SWC_EXTENSION))
        {
            DefinitionAsText definitionText = DefinitionTextUtils.definitionToTextDocument(definition, project);
            //may be null if definitionToTextDocument() doesn't know how
            //to parse that type of definition
            if (definitionText != null)
            {
                //if we get here, we couldn't find a framework source file and
                //the definition path still ends with .swc
                //we're going to try our best to display "decompiled" content
                location = definitionText.toLocation();
            }
        }
        if(location == null)
        {
            location = new Location();
            Path definitionPath = Paths.get(sourcePath);
            location.setUri(definitionPath.toUri().toString());
            Range range = definitionToRange(definition, project);
            if (range == null)
            {
                return null;
            }
            location.setRange(range);
        }
        return location;
    }

    private Range definitionToRange(IDefinition definition, RoyaleProject project)
    {
        String sourcePath = definitionToSourcePath(definition, project);
        if (sourcePath == null)
        {
            //we can't find where the source code for this symbol is located
            return null;
        }
        Range range = null;
        if (sourcePath.endsWith(SWC_EXTENSION))
        {
            DefinitionAsText definitionText = DefinitionTextUtils.definitionToTextDocument(definition, project);
            //may be null if definitionToTextDocument() doesn't know how
            //to parse that type of definition
            if (definitionText != null)
            {
                //if we get here, we couldn't find a framework source file and
                //the definition path still ends with .swc
                //we're going to try our best to display "decompiled" content
                range = definitionText.toRange();
            }
        }
        if (range == null)
        {
            Path definitionPath = Paths.get(sourcePath);
            Position start = new Position();
            Position end = new Position();
            //getLine() and getColumn() may include things like metadata, so it
            //makes more sense to jump to where the definition name starts
            int line = definition.getNameLine();
            int column = definition.getNameColumn();
            if (line < 0 || column < 0)
            {
                //this is not ideal, but MXML variable definitions may not have a
                //node associated with them, so we need to figure this out from the
                //offset instead of a pre-calculated line and column -JT
                Reader definitionReader = getReaderForPath(definitionPath);
                if (definitionReader == null)
                {
                    //we might get here if it's from a SWC, but the associated
                    //source file is missing.
                    return null;
                }
                else
                {
                    LanguageServerCompilerUtils.getPositionFromOffset(definitionReader, definition.getNameStart(), start);
                    end.setLine(start.getLine());
                    end.setCharacter(start.getCharacter());
                    try
                    {
                        definitionReader.close();
                    }
                    catch(IOException e) {}
                }
            }
            else
            {
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
                if(!sourceByPath.containsKey(filePath))
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
                sourceByPath.remove(filePath);
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

        boolean isOpen = sourceByPath.containsKey(path);
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
                sourceByPath.remove(path);
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
        if(sourceByPath.containsKey(path))
        {
            //already opened
            return;
        }

        //if the file isn't open in an editor, we need to read it from the
        //file system instead.
        String text = getFileTextForPath(path);
        if(text == null)
        {
            return;
        }

        //for some reason, the full AST is not populated if the file is not
        //already open in the editor. we use a similar workaround to didOpen
        //to force the AST to be populated.

        //we'll clear this out later before we return from this function
        sourceByPath.put(path, text);

        //notify the workspace that it should read the file from memory
        //instead of loading from the file system
        String normalizedPath = FilenameNormalization.normalize(path.toAbsolutePath().toString());
        IFileSpecification fileSpec = fileSpecGetter.getFileSpecification(normalizedPath);
        compilerWorkspace.fileChanged(fileSpec);
    }

    private void organizeImportsInUri(String uri, Map<String,List<TextEdit>> changes)
    {
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(uri);
        if(path == null)
        {
            return;
        }
        WorkspaceFolderData folderData = getWorkspaceFolderDataForSourceFile(path);
        if(folderData == null || folderData.project == null)
        {
            return;
        }
        RoyaleProject project = folderData.project;
        
        ICompilationUnit unit = findCompilationUnit(path, project);
        if(unit == null)
        {
            return;
        }

        String text = getFileTextForPath(path);
        if(text == null)
        {
            return;
        }

        Set<String> missingNames = null;
        Set<String> importsToAdd = null;
        Set<IImportNode> importsToRemove = null;
        IASNode ast = getAST(unit);
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
                WorkspaceFolderData folderData = getWorkspaceFolderDataForSourceFile(pathForImport);
                if(folderData == null || folderData.project == null)
                {
                    return new Object();
                }
                String text = getFileTextForPath(pathForImport);
                if(text == null)
                {
                    return new Object();
                }
                int currentOffset = LanguageServerCompilerUtils.getOffsetFromPosition(new StringReader(text), new Position(line, character));
                ImportRange importRange = null;
                if(uri.endsWith(MXML_EXTENSION))
                {
                    MXMLData mxmlData = getMXMLDataForPath(pathForImport, folderData);
                    IMXMLTagData offsetTag = getOffsetMXMLTag(mxmlData, currentOffset);
                    importRange = ImportRange.fromOffsetTag(offsetTag, currentOffset);
                }
                else
                {
                    IASNode offsetNode = getOffsetNode(pathForImport, currentOffset, folderData);
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
                String text = getFileTextForPath(pathForImport);
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
                ASConfigCOptions options = new ASConfigCOptions(workspaceRootPath.toString(), frameworkSDKHome.toString(), true, null, null, true, compilerShell);
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
