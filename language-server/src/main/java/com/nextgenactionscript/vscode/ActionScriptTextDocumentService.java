/*
Copyright 2016-2017 Bowler Hat LLC

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
package com.nextgenactionscript.vscode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.flex.abc.ABCConstants;
import org.apache.flex.abc.ABCParser;
import org.apache.flex.abc.Pool;
import org.apache.flex.abc.PoolingABCVisitor;
import org.apache.flex.compiler.asdoc.IASDocTag;
import org.apache.flex.compiler.clients.MXMLJSC;
import org.apache.flex.compiler.common.ASModifier;
import org.apache.flex.compiler.common.ISourceLocation;
import org.apache.flex.compiler.common.PrefixMap;
import org.apache.flex.compiler.common.XMLName;
import org.apache.flex.compiler.config.Configurator;
import org.apache.flex.compiler.config.ICompilerSettingsConstants;
import org.apache.flex.compiler.constants.IASKeywordConstants;
import org.apache.flex.compiler.constants.IASLanguageConstants;
import org.apache.flex.compiler.constants.IMXMLCoreConstants;
import org.apache.flex.compiler.constants.IMetaAttributeConstants;
import org.apache.flex.compiler.definitions.IAccessorDefinition;
import org.apache.flex.compiler.definitions.IClassDefinition;
import org.apache.flex.compiler.definitions.IConstantDefinition;
import org.apache.flex.compiler.definitions.IDefinition;
import org.apache.flex.compiler.definitions.IDocumentableDefinition;
import org.apache.flex.compiler.definitions.IEventDefinition;
import org.apache.flex.compiler.definitions.IFunctionDefinition;
import org.apache.flex.compiler.definitions.IGetterDefinition;
import org.apache.flex.compiler.definitions.IInterfaceDefinition;
import org.apache.flex.compiler.definitions.INamespaceDefinition;
import org.apache.flex.compiler.definitions.IPackageDefinition;
import org.apache.flex.compiler.definitions.IParameterDefinition;
import org.apache.flex.compiler.definitions.ISetterDefinition;
import org.apache.flex.compiler.definitions.IStyleDefinition;
import org.apache.flex.compiler.definitions.ITypeDefinition;
import org.apache.flex.compiler.definitions.IVariableDefinition;
import org.apache.flex.compiler.definitions.IVariableDefinition.VariableClassification;
import org.apache.flex.compiler.definitions.metadata.IMetaTag;
import org.apache.flex.compiler.driver.IBackend;
import org.apache.flex.compiler.filespecs.IFileSpecification;
import org.apache.flex.compiler.internal.driver.js.flexjs.FlexJSBackend;
import org.apache.flex.compiler.internal.driver.js.goog.JSGoogConfiguration;
import org.apache.flex.compiler.internal.driver.js.jsc.JSCBackend;
import org.apache.flex.compiler.internal.driver.js.node.NodeBackend;
import org.apache.flex.compiler.internal.driver.js.node.NodeModuleBackend;
import org.apache.flex.compiler.internal.mxml.MXMLData;
import org.apache.flex.compiler.internal.parsing.as.ASParser;
import org.apache.flex.compiler.internal.parsing.as.ASToken;
import org.apache.flex.compiler.internal.parsing.as.RepairingTokenBuffer;
import org.apache.flex.compiler.internal.parsing.as.StreamingASTokenizer;
import org.apache.flex.compiler.internal.projects.CompilerProject;
import org.apache.flex.compiler.internal.projects.FlexJSProject;
import org.apache.flex.compiler.internal.projects.FlexProject;
import org.apache.flex.compiler.internal.scopes.ASScope;
import org.apache.flex.compiler.internal.scopes.TypeScope;
import org.apache.flex.compiler.internal.scopes.ASProjectScope.DefinitionPromise;
import org.apache.flex.compiler.internal.tree.as.FileNode;
import org.apache.flex.compiler.internal.tree.as.FullNameNode;
import org.apache.flex.compiler.internal.units.SWCCompilationUnit;
import org.apache.flex.compiler.internal.workspaces.Workspace;
import org.apache.flex.compiler.mxml.IMXMLData;
import org.apache.flex.compiler.mxml.IMXMLDataManager;
import org.apache.flex.compiler.mxml.IMXMLLanguageConstants;
import org.apache.flex.compiler.mxml.IMXMLTagAttributeData;
import org.apache.flex.compiler.mxml.IMXMLTagData;
import org.apache.flex.compiler.mxml.IMXMLTextData;
import org.apache.flex.compiler.mxml.IMXMLUnitData;
import org.apache.flex.compiler.problems.FontEmbeddingNotSupported;
import org.apache.flex.compiler.problems.ICompilerProblem;
import org.apache.flex.compiler.scopes.IASScope;
import org.apache.flex.compiler.targets.ITarget;
import org.apache.flex.compiler.targets.ITargetSettings;
import org.apache.flex.compiler.tree.ASTNodeID;
import org.apache.flex.compiler.tree.as.IASNode;
import org.apache.flex.compiler.tree.as.IBinaryOperatorNode;
import org.apache.flex.compiler.tree.as.IClassNode;
import org.apache.flex.compiler.tree.as.IContainerNode;
import org.apache.flex.compiler.tree.as.IDefinitionNode;
import org.apache.flex.compiler.tree.as.IExpressionNode;
import org.apache.flex.compiler.tree.as.IFileNode;
import org.apache.flex.compiler.tree.as.IFunctionCallNode;
import org.apache.flex.compiler.tree.as.IFunctionNode;
import org.apache.flex.compiler.tree.as.IIdentifierNode;
import org.apache.flex.compiler.tree.as.IImportNode;
import org.apache.flex.compiler.tree.as.IInterfaceNode;
import org.apache.flex.compiler.tree.as.IKeywordNode;
import org.apache.flex.compiler.tree.as.ILanguageIdentifierNode;
import org.apache.flex.compiler.tree.as.IMemberAccessExpressionNode;
import org.apache.flex.compiler.tree.as.INamespaceDecorationNode;
import org.apache.flex.compiler.tree.as.IPackageNode;
import org.apache.flex.compiler.tree.as.IScopedDefinitionNode;
import org.apache.flex.compiler.tree.as.IScopedNode;
import org.apache.flex.compiler.tree.as.ITypeNode;
import org.apache.flex.compiler.tree.as.IVariableNode;
import org.apache.flex.compiler.tree.mxml.IMXMLClassReferenceNode;
import org.apache.flex.compiler.tree.mxml.IMXMLConcatenatedDataBindingNode;
import org.apache.flex.compiler.tree.mxml.IMXMLEventSpecifierNode;
import org.apache.flex.compiler.tree.mxml.IMXMLNode;
import org.apache.flex.compiler.tree.mxml.IMXMLPropertySpecifierNode;
import org.apache.flex.compiler.tree.mxml.IMXMLSingleDataBindingNode;
import org.apache.flex.compiler.units.ICompilationUnit;
import org.apache.flex.compiler.units.IInvisibleCompilationUnit;
import org.apache.flex.compiler.workspaces.IWorkspace;

import com.google.common.io.Files;
import com.google.gson.internal.LinkedTreeMap;
import com.nextgenactionscript.vscode.asdoc.VSCodeASDocComment;
import com.nextgenactionscript.vscode.asdoc.VSCodeASDocDelegate;
import com.nextgenactionscript.vscode.commands.ICommandConstants;
import com.nextgenactionscript.vscode.mxml.IMXMLLibraryConstants;
import com.nextgenactionscript.vscode.project.CompilerOptions;
import com.nextgenactionscript.vscode.project.IProjectConfigStrategy;
import com.nextgenactionscript.vscode.project.ProjectOptions;
import com.nextgenactionscript.vscode.project.ProjectType;
import com.nextgenactionscript.vscode.project.VSCodeConfiguration;
import com.nextgenactionscript.vscode.utils.ASTUtils;
import com.nextgenactionscript.vscode.utils.ImportTextEditUtils;
import com.nextgenactionscript.vscode.utils.LSPUtils;
import com.nextgenactionscript.vscode.utils.LanguageServerCompilerUtils;
import com.nextgenactionscript.vscode.utils.ProblemTracker;

import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
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
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

/**
 * Handles requests from Visual Studio Code that are at the document level,
 * including things like API completion, function signature help, and find all
 * references. Calls APIs on the Apache FlexJS compiler to get data for the
 * responses to return to VSCode.
 */
public class ActionScriptTextDocumentService implements TextDocumentService
{
    private static final String MXML_EXTENSION = ".mxml";
    private static final String AS_EXTENSION = ".as";
    private static final String SWC_EXTENSION = ".swc";
    private static final String DEFAULT_NS_PREFIX = "ns";
    private static final String STAR = "*";
    private static final String DOT_STAR = ".*";
    private static final String MARKDOWN_CODE_BLOCK_NEXTGENAS_START = "```nextgenas\n";
    private static final String MARKDOWN_CODE_BLOCK_MXML_START = "```mxml\n";
    private static final String MARKDOWN_CODE_BLOCK_END = "\n```";
    private static final String TOKEN_CONFIGNAME = "configname";
    private static final String CONFIG_JS = "js";
    private static final String CONFIG_NODE = "node";
    private static final String SDK_FRAMEWORKS_PATH_SIGNATURE = "/frameworks/";
    private static final String SDK_LIBRARY_PATH_SIGNATURE_UNIX = "/frameworks/libs/";
    private static final String SDK_LIBRARY_PATH_SIGNATURE_WINDOWS = "\\frameworks\\libs\\";
    private static final String SDK_SOURCE_PATH_SIGNATURE_UNIX = "/frameworks/projects/";
    private static final String SDK_SOURCE_PATH_SIGNATURE_WINDOWS = "\\frameworks\\projects\\";
    private static final String FLEXLIB = "flexlib";
    private static final String UNDERSCORE_UNDERSCORE_AS3_PACKAGE = "__AS3__.";
    private static final String VECTOR_HIDDEN_PREFIX = "Vector$";
    private static final String ASDOC_TAG_PARAM = "param";

    private static final String[] LANGUAGE_TYPE_NAMES =
            {
                    IMXMLLanguageConstants.ARRAY,
                    IMXMLLanguageConstants.BOOLEAN,
                    IMXMLLanguageConstants.CLASS,
                    IMXMLLanguageConstants.DATE,
                    IMXMLLanguageConstants.FUNCTION,
                    IMXMLLanguageConstants.INT,
                    IMXMLLanguageConstants.NUMBER,
                    IMXMLLanguageConstants.OBJECT,
                    IMXMLLanguageConstants.STRING,
                    IMXMLLanguageConstants.XML,
                    IMXMLLanguageConstants.XML_LIST,
                    IMXMLLanguageConstants.UINT
            };

    private static final HashMap<String, String> NAMESPACE_TO_PREFIX = new HashMap<>();

    {
        //MXML language
        NAMESPACE_TO_PREFIX.put(IMXMLLanguageConstants.NAMESPACE_MXML_2006, "mx");
        NAMESPACE_TO_PREFIX.put(IMXMLLanguageConstants.NAMESPACE_MXML_2009, "fx");

        //Flex
        NAMESPACE_TO_PREFIX.put(IMXMLLibraryConstants.MX, "mx");
        NAMESPACE_TO_PREFIX.put(IMXMLLibraryConstants.SPARK, "s");

        //FlexJS
        NAMESPACE_TO_PREFIX.put(IMXMLLibraryConstants.BASIC, "js");

        //Feathers
        NAMESPACE_TO_PREFIX.put(IMXMLLibraryConstants.FEATHERS, "f");
    }

    private static boolean isWindows;

    private LanguageClient languageClient;
    private IProjectConfigStrategy projectConfigStrategy;
    private String oldFrameworkSDKPath;
    private Path workspaceRoot;
    private Map<Path, String> sourceByPath = new HashMap<>();
    private Map<Path, List<SavedCodeAction>> codeActionsByPath = new HashMap<>();
    private List<String> completionTypes = new ArrayList<>();
    private Collection<ICompilationUnit> compilationUnits;
    private ArrayList<IInvisibleCompilationUnit> invisibleUnits = new ArrayList<>();
    private ICompilationUnit currentUnit;
    private FlexProject currentProject;
    private IWorkspace currentWorkspace;
    private ProjectOptions currentProjectOptions;
    private int currentOffset = -1;
    private ImportRange importRange = new ImportRange();
    private int namespaceStartIndex = -1;
    private int namespaceEndIndex = -1;
    private String namespaceUri;
    private LanguageServerFileSpecGetter fileSpecGetter;
    private boolean flexLibSDKContainsFalconCompiler = false;
    private boolean flexLibSDKIsFlexJS = false;
    private ProblemTracker codeProblemTracker = new ProblemTracker();
    private ProblemTracker configProblemTracker = new ProblemTracker();
    private Pattern additionalOptionsPattern = Pattern.compile("[^\\s]*'([^'])*?'|[^\\s]*\"([^\"])*?\"|[^\\s]+");

    private class MXMLNamespace
    {
        public MXMLNamespace(String prefix, String uri)
        {
            this.prefix = prefix;
            this.uri = uri;
        }

        public String prefix;
        public String uri;
    }

    private class SavedCodeAction
    {
        public SavedCodeAction(Command command, Range range)
        {
            this.command = command;
            this.range = range;
        }
        
        public Command command;
        public Range range;
    }

    public ActionScriptTextDocumentService()
    {
        updateFrameworkSDK();
        isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
    }

    public IProjectConfigStrategy getProjectConfigStrategy()
    {
        return projectConfigStrategy;
    }

    public void setProjectConfigStrategy(IProjectConfigStrategy value)
    {
        projectConfigStrategy = value;
    }

    public Path getWorkspaceRoot()
    {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(Path value)
    {
        workspaceRoot = value;
        if (workspaceRoot != null)
        {
            checkProjectForProblems();
        }
    }

    public void setLanguageClient(LanguageClient value)
    {
        languageClient = value;
        codeProblemTracker.setLanguageClient(value);
        configProblemTracker.setLanguageClient(value);
    }

    /**
     * Returns a list of all items to display in the completion list at a
     * specific position in a document. Called automatically by VSCode as the
     * user types, and may not necessarily be triggered only on "." or ":".
     */
    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(TextDocumentPositionParams position)
    {
        //this shouldn't be necessary, but if we ever forget to do this
        //somewhere, completion results might be missing items.
        completionTypes.clear();
        String textDocumentUri = position.getTextDocument().getUri();
        if (!textDocumentUri.endsWith(AS_EXTENSION)
                && !textDocumentUri.endsWith(MXML_EXTENSION))
        {
            CompletionList result = new CompletionList();
            result.setIsIncomplete(false);
            result.setItems(new ArrayList<>());
            return CompletableFuture.completedFuture(Either.forRight(result));
        }
        IMXMLTagData offsetTag = getOffsetMXMLTag(position);
        if (offsetTag != null)
        {
            IASNode embeddedNode = getEmbeddedActionScriptNodeInMXMLTag(offsetTag, currentOffset, position);
            if (embeddedNode != null)
            {
                CompletionList result = actionScriptCompletionWithNode(position, embeddedNode);
                completionTypes.clear();
                return CompletableFuture.completedFuture(Either.forRight(result));
            }
            //if we're inside an <fx:Script> tag, we want ActionScript completion,
            //so that's why we call isMXMLTagValidForCompletion()
            if (isMXMLTagValidForCompletion(offsetTag))
            {
                CompletionList result = mxmlCompletion(position, offsetTag);
                completionTypes.clear();
                return CompletableFuture.completedFuture(Either.forRight(result));
            }
        }
        if (offsetTag == null && position.getTextDocument().getUri().endsWith(MXML_EXTENSION))
        {
            //it's possible for the offset tag to be null in an MXML file, but
            //we don't want to trigger ActionScript completion.
            //for some reason, the offset tag will be null if completion is
            //triggered at the asterisk:
            //<fx:Declarations>*
            CompletionList result = new CompletionList();
            result.setIsIncomplete(false);
            result.setItems(new ArrayList<>());
            return CompletableFuture.completedFuture(Either.forRight(result));
        }
        CompletionList result = actionScriptCompletion(position);
        completionTypes.clear();
        return CompletableFuture.completedFuture(Either.forRight(result));
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
    public CompletableFuture<Hover> hover(TextDocumentPositionParams position)
    {
        String textDocumentUri = position.getTextDocument().getUri();
        if (!textDocumentUri.endsWith(AS_EXTENSION)
                && !textDocumentUri.endsWith(MXML_EXTENSION))
        {
            return CompletableFuture.completedFuture(new Hover(Collections.emptyList(), null));
        }
        IMXMLTagData offsetTag = getOffsetMXMLTag(position);
        if (offsetTag != null)
        {
            IASNode embeddedNode = getEmbeddedActionScriptNodeInMXMLTag(offsetTag, currentOffset, position);
            if (embeddedNode != null)
            {
                return actionScriptHoverWithNode(position, embeddedNode);
            }
            //if we're inside an <fx:Script> tag, we want ActionScript hover,
            //so that's why we call isMXMLTagValidForCompletion()
            if (isMXMLTagValidForCompletion(offsetTag))
            {
                return mxmlHover(position, offsetTag);
            }
        }
        return actionScriptHover(position);
    }

    /**
     * Displays a function's parameters, including which one is currently
     * active. Called automatically by VSCode any time that the user types "(",
     * so be sure to check that a function call is actually happening at the
     * current position.
     */
    @Override
    public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position)
    {
        String textDocumentUri = position.getTextDocument().getUri();
        if (!textDocumentUri.endsWith(AS_EXTENSION)
                && !textDocumentUri.endsWith(MXML_EXTENSION))
        {
            //we couldn't find a node at the specified location
            return CompletableFuture.completedFuture(new SignatureHelp(Collections.emptyList(), -1, -1));
        }
        IASNode offsetNode = null;
        IMXMLTagData offsetTag = getOffsetMXMLTag(position);
        if (offsetTag != null)
        {
            IMXMLTagAttributeData attributeData = getMXMLTagAttributeWithValueAtOffset(offsetTag, currentOffset);
            if (attributeData != null)
            {
                //some attributes can have ActionScript completion, such as
                //events and properties with data binding
                IClassDefinition tagDefinition = (IClassDefinition) currentProject.resolveXMLNameToDefinition(offsetTag.getXMLName(), offsetTag.getMXMLDialect());
                IDefinition attributeDefinition = currentProject.resolveSpecifier(tagDefinition, attributeData.getShortName());
                if (attributeDefinition instanceof IEventDefinition)
                {
                    IMXMLClassReferenceNode mxmlNode = (IMXMLClassReferenceNode) getOffsetNode(position);
                    IMXMLEventSpecifierNode eventNode = mxmlNode.getEventSpecifierNode(attributeData.getShortName());
                    for (IASNode asNode : eventNode.getASNodes())
                    {
                        IASNode containingNode = getContainingNodeIncludingStart(asNode, currentOffset);
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
        if (offsetNode == null)
        {
            offsetNode = getOffsetNode(position);
        }
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return CompletableFuture.completedFuture(new SignatureHelp(Collections.emptyList(), -1, -1));
        }

        IFunctionCallNode functionCallNode = getAncestorFunctionCallNode(offsetNode);
        IFunctionDefinition functionDefinition = null;
        if (functionCallNode != null)
        {
            IExpressionNode nameNode = functionCallNode.getNameNode();
            IDefinition definition = nameNode.resolve(currentUnit.getProject());
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
                    ITypeDefinition typeDefinition = nameNode.resolveType(currentProject);
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
            signatureInfo.setLabel(getSignatureLabel(functionDefinition));
            String docs = getDocumentationForDefinition(functionDefinition, true);
            if (docs != null)
            {
                signatureInfo.setDocumentation(docs);
            }

            List<ParameterInformation> parameters = new ArrayList<>();
            for (IParameterDefinition param : functionDefinition.getParameters())
            {
                ParameterInformation paramInfo = new ParameterInformation();
                paramInfo.setLabel(param.getBaseName());
                String paramDocs = getDocumentationForParameter(param, true);
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
            IParameterDefinition[] params = functionDefinition.getParameters();
            int paramCount = params.length;
            if (paramCount > 0 && index >= paramCount)
            {
                if (index >= paramCount)
                {
                    IParameterDefinition lastParam = params[paramCount - 1];
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
            return CompletableFuture.completedFuture(result);
        }
        return CompletableFuture.completedFuture(new SignatureHelp(Collections.emptyList(), -1, -1));
    }

    /**
     * Finds where the definition referenced at the current position in a text
     * document is defined.
     */
    @Override
    public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position)
    {
        String textDocumentUri = position.getTextDocument().getUri();
        if (!textDocumentUri.endsWith(AS_EXTENSION)
                && !textDocumentUri.endsWith(MXML_EXTENSION))
        {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        IMXMLTagData offsetTag = getOffsetMXMLTag(position);
        if (offsetTag != null)
        {
            IASNode embeddedNode = getEmbeddedActionScriptNodeInMXMLTag(offsetTag, currentOffset, position);
            if (embeddedNode != null)
            {
                return actionScriptDefinitionWithNode(position, embeddedNode);
            }
            //if we're inside an <fx:Script> tag, we want ActionScript lookup,
            //so that's why we call isMXMLTagValidForCompletion()
            if (isMXMLTagValidForCompletion(offsetTag))
            {
                return mxmlDefinition(position, offsetTag);
            }
        }
        return actionScriptDefinition(position);
    }

    /**
     * Finds all references of the definition referenced at the current position
     * in a text document. Does not necessarily get called where a definition is
     * defined, but may be at one of the references.
     */
    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params)
    {
        String textDocumentUri = params.getTextDocument().getUri();
        if (!textDocumentUri.endsWith(AS_EXTENSION)
                && !textDocumentUri.endsWith(MXML_EXTENSION))
        {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        IMXMLTagData offsetTag = getOffsetMXMLTag(params);
        if (offsetTag != null)
        {
            IASNode embeddedNode = getEmbeddedActionScriptNodeInMXMLTag(offsetTag, currentOffset, params.getTextDocument(), params.getPosition());
            if (embeddedNode != null)
            {
                return actionScriptReferencesWithNode(params, embeddedNode);
            }
            //if we're inside an <fx:Script> tag, we want ActionScript lookup,
            //so that's why we call isMXMLTagValidForCompletion()
            if (isMXMLTagValidForCompletion(offsetTag))
            {
                return mxmlReferences(params, offsetTag);
            }
        }
        return actionScriptReferences(params);
    }

    /**
     * This feature is not implemented at this time.
     */
    @Override
    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position)
    {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    /**
     * Searches by name for a symbol in the workspace.
     */
    public CompletableFuture<List<? extends SymbolInformation>> workspaceSymbol(WorkspaceSymbolParams params)
    {
        if (compilationUnits == null)
        {
            //if we haven't successfully compiled the project, we can't do this
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        List<SymbolInformation> result = new ArrayList<>();
        String query = params.getQuery();
        for (ICompilationUnit unit : compilationUnits)
        {
            if (unit == null || unit instanceof SWCCompilationUnit)
            {
                continue;
            }
            IASScope[] scopes;
            try
            {
                scopes = unit.getFileScopeRequest().get().getScopes();
            }
            catch (Exception e)
            {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }
            for (IASScope scope : scopes)
            {
                querySymbolsInScope(query, scope, result);
            }
        }
        return CompletableFuture.completedFuture(result);
    }

    /**
     * Searches by name for a symbol in a specific document (not the whole
     * workspace)
     */
    @Override
    public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params)
    {
        TextDocumentIdentifier textDocument = params.getTextDocument();
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
        if (path == null)
        {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        ICompilationUnit unit = getCompilationUnit(path);
        if (unit == null)
        {
            //we couldn't find a compilation unit with the specified path
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        IASScope[] scopes;
        try
        {
            scopes = unit.getFileScopeRequest().get().getScopes();
        }
        catch (Exception e)
        {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        List<SymbolInformation> result = new ArrayList<>();
        for (IASScope scope : scopes)
        {
            scopeToSymbols(scope, result);
        }
        return CompletableFuture.completedFuture(result);
    }

    /**
     * Can be used to "quick fix" an error or warning.
     */
    @Override
    public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams params)
    {
        List<? extends Diagnostic> diagnostics = params.getContext().getDiagnostics();
        TextDocumentIdentifier textDocument = params.getTextDocument();
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
        if (path == null || !sourceByPath.containsKey(path))
        {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        ArrayList<Command> commands = new ArrayList<>();
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
                    createCodeActionsForImport(textDocument, diagnostic, commands);
                    createCodeActionForMissingLocalVariable(textDocument, diagnostic, commands);
                    createCodeActionForMissingField(textDocument, diagnostic, commands);
                    break;
                }
                case "1046": //UnknownTypeProblem
                {
                    //see if there's anything we can import
                    createCodeActionsForImport(textDocument, diagnostic, commands);
                    break;
                }
                case "1017": //UnknownSuperclassProblem
                {
                    //see if there's anything we can import
                    createCodeActionsForImport(textDocument, diagnostic, commands);
                    break;
                }
                case "1045": //UnknownInterfaceProblem
                {
                    //see if there's anything we can import
                    createCodeActionsForImport(textDocument, diagnostic, commands);
                    break;
                }
                case "1061": //StrictUndefinedMethodProblem
                {
                    createCodeActionForMissingMethod(textDocument, diagnostic, commands);
                    break;
                }
                case "1119": //AccessUndefinedMemberProblem
                {
                    createCodeActionForMissingField(textDocument, diagnostic, commands);
                    break;
                }
                case "1178": //InaccessiblePropertyReferenceProblem
                {
                    //see if there's anything we can import
                    createCodeActionsForImport(textDocument, diagnostic, commands);
                    break;
                }
                case "1180": //CallUndefinedMethodProblem
                {
                    //see if there's anything we can import
                    createCodeActionsForImport(textDocument, diagnostic, commands);
                    createCodeActionForMissingMethod(textDocument, diagnostic, commands);
                    break;
                }
            }
        }
        if (codeActionsByPath.containsKey(path))
        {
            List<SavedCodeAction> codeActionsForPath = codeActionsByPath.get(path);
            for (SavedCodeAction codeAction : codeActionsForPath)
            {
                Range savedRange = codeAction.range;
                Range paramRange = params.getRange();
                if (LSPUtils.rangesIntersect(savedRange, paramRange))
                {
                    commands.add(codeAction.command);
                }
            }
        }
        return CompletableFuture.completedFuture(commands);
    }

    private void createCodeActionForMissingField(TextDocumentIdentifier textDocument, Diagnostic diagnostic, List<Command> commands)
    {
        IASNode offsetNode = getOffsetNode(textDocument, diagnostic.getRange().getStart());
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

        String identifierName = identifierNode.getName();
        Command generateVariableCommand = new Command();
        generateVariableCommand.setTitle("Generate Field Variable");
        generateVariableCommand.setCommand(ICommandConstants.GENERATE_FIELD_VARIABLE);
        generateVariableCommand.setArguments(Arrays.asList(
            textDocument.getUri(),
            diagnostic.getRange().getStart().getLine(),
            diagnostic.getRange().getStart().getCharacter(),
            diagnostic.getRange().getEnd().getLine(),
            diagnostic.getRange().getEnd().getCharacter(),
            identifierName
        ));
        commands.add(generateVariableCommand);
    }
    
    private void createCodeActionForMissingLocalVariable(TextDocumentIdentifier textDocument, Diagnostic diagnostic, List<Command> commands)
    {
        IASNode offsetNode = getOffsetNode(textDocument, diagnostic.getRange().getStart());
        IIdentifierNode identifierNode = null;
        if (offsetNode instanceof IIdentifierNode)
        {
            identifierNode = (IIdentifierNode) offsetNode;
        }
        if (identifierNode == null)
        {
            return;
        }

        String identifierName = identifierNode.getName();
        Command generateVariableCommand = new Command();
        generateVariableCommand.setTitle("Generate Local Variable");
        generateVariableCommand.setCommand(ICommandConstants.GENERATE_LOCAL_VARIABLE);
        generateVariableCommand.setArguments(Arrays.asList(
            textDocument.getUri(),
            diagnostic.getRange().getStart().getLine(),
            diagnostic.getRange().getStart().getCharacter(),
            diagnostic.getRange().getEnd().getLine(),
            diagnostic.getRange().getEnd().getCharacter(),
            identifierName
        ));
        commands.add(generateVariableCommand);
    }

    private void createCodeActionForMissingMethod(TextDocumentIdentifier textDocument, Diagnostic diagnostic, List<Command> commands)
    {
        IASNode offsetNode = getOffsetNode(textDocument, diagnostic.getRange().getStart());
        IASNode parentNode = offsetNode.getParent();
        IFunctionCallNode functionCallNode = null;
        String functionName = null;
        if (offsetNode instanceof IFunctionCallNode)
        {
            functionCallNode = (IFunctionCallNode) offsetNode;
            functionName = functionCallNode.getFunctionName();
        }
        else if (parentNode instanceof IFunctionCallNode)
        {
            functionCallNode = (IFunctionCallNode) offsetNode.getParent();
            functionName = functionCallNode.getFunctionName();
        }
        else if(offsetNode instanceof IIdentifierNode
                && parentNode instanceof IMemberAccessExpressionNode)
        {
            IMemberAccessExpressionNode memberAccessExpressionNode = (IMemberAccessExpressionNode) offsetNode.getParent();
            IExpressionNode leftOperandNode = memberAccessExpressionNode.getLeftOperandNode();
            if (leftOperandNode instanceof ILanguageIdentifierNode)
            {
                ILanguageIdentifierNode leftIdentifierNode = (ILanguageIdentifierNode) leftOperandNode;
                IASNode gpNode = parentNode.getParent();
                if (leftIdentifierNode.getKind() == ILanguageIdentifierNode.LanguageIdentifierKind.THIS
                        && gpNode instanceof IFunctionCallNode)
                {
                    functionCallNode = (IFunctionCallNode) gpNode;
                    IIdentifierNode rightIdentifierNode = (IIdentifierNode) offsetNode;
                    functionName = rightIdentifierNode.getName();
                }
            }
        }
        if (functionCallNode == null || functionName == null || functionName.length() == 0)
        {
            return;
        }

        ArrayList<String> argTypes = new ArrayList<>();
        for (IExpressionNode arg : functionCallNode.getArgumentNodes())
        {
            ITypeDefinition typeDefinition = arg.resolveType(currentProject);
            if (typeDefinition != null)
            {
                argTypes.add(typeDefinition.getQualifiedName());
            }
            else
            {
                argTypes.add(IASLanguageConstants.Object);
            }
        }

        Command generateMethodCommand = new Command();
        generateMethodCommand.setTitle("Generate Method");
        generateMethodCommand.setCommand(ICommandConstants.GENERATE_METHOD);
        generateMethodCommand.setArguments(Arrays.asList(
            textDocument.getUri(),
            diagnostic.getRange().getStart().getLine(),
            diagnostic.getRange().getStart().getCharacter(),
            diagnostic.getRange().getEnd().getLine(),
            diagnostic.getRange().getEnd().getCharacter(),
            functionName,
            argTypes.toArray()
        ));
        commands.add(generateMethodCommand);
    }

    private void createCodeActionsForImport(TextDocumentIdentifier textDocument, Diagnostic diagnostic, List<Command> commands)
    {
        IASNode offsetNode = getOffsetNode(textDocument, diagnostic.getRange().getStart());
        if (offsetNode == null || !(offsetNode instanceof IIdentifierNode))
        {
            return;
        }
        IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
        String typeString = identifierNode.getName();

        List<IDefinition> types = ASTUtils.findTypesThatMatchName(typeString, compilationUnits);
        for (IDefinition definitionToImport : types)
        {
            Command command = createImportCommand(definitionToImport);
            if (command != null)
            {
                commands.add(command);
            }
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
        String textDocumentUri = params.getTextDocument().getUri();
        if (!textDocumentUri.endsWith(AS_EXTENSION)
                && !textDocumentUri.endsWith(MXML_EXTENSION))
        {
            return CompletableFuture.completedFuture(new WorkspaceEdit(new HashMap<>()));
        }
        IMXMLTagData offsetTag = getOffsetMXMLTag(params.getTextDocument(), params.getPosition());
        if (offsetTag != null)
        {
            IASNode embeddedNode = getEmbeddedActionScriptNodeInMXMLTag(offsetTag, currentOffset, params.getTextDocument(), params.getPosition());
            if (embeddedNode != null)
            {
                return actionScriptRenameWithNode(params, embeddedNode);
            }
            //if we're inside an <fx:Script> tag, we want ActionScript rename,
            //so that's why we call isMXMLTagValidForCompletion()
            if (isMXMLTagValidForCompletion(offsetTag))
            {
                return mxmlRename(params, offsetTag);
            }
        }
        return actionScriptRename(params);
    }

    /**
     * Called when one of the commands registered in ActionScriptLanguageServer
     * is executed.
     */
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params)
    {
        switch(params.getCommand())
        {
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
            case ICommandConstants.GENERATE_GETTER_AND_SETTER:
            {
                return executeGenerateGetterAndSetterCommand(params, true, true);
            }
            case ICommandConstants.GENERATE_GETTER:
            {
                return executeGenerateGetterAndSetterCommand(params, true, false);
            }
            case ICommandConstants.GENERATE_SETTER:
            {
                return executeGenerateGetterAndSetterCommand(params, false, true);
            }
            case ICommandConstants.GENERATE_LOCAL_VARIABLE:
            {
                return executeGenerateLocalVariableCommand(params);
            }
            case ICommandConstants.GENERATE_FIELD_VARIABLE:
            {
                return executeGenerateFieldVariableCommand(params);
            }
            case ICommandConstants.GENERATE_METHOD:
            {
                return executeGenerateMethodCommand(params);
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
        if (path != null)
        {
            String text = textDocument.getText();
            sourceByPath.put(path, text);
            codeActionsByPath.put(path, new ArrayList<>());

            if (currentWorkspace != null)
            {
                //if the compiler was using the file system version, switch to
                //the in-memory version
                IFileSpecification fileSpec = fileSpecGetter.getFileSpecification(path.toAbsolutePath().toString());
                currentWorkspace.fileChanged(fileSpec);
            }
            //we need to check for problems when opening a new file because it
            //may not have been in the workspace before.
            checkFilePathForProblems(path, false);
        }
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
        if (path != null)
        {
            for (TextDocumentContentChangeEvent change : params.getContentChanges())
            {
                if (change.getRange() == null)
                {
                    sourceByPath.put(path, change.getText());
                }
                else
                {
                    String existingText = sourceByPath.get(path);
                    String newText = patch(existingText, change);
                    sourceByPath.put(path, newText);
                }
            }
            if (currentWorkspace != null)
            {
                IFileSpecification fileSpec = fileSpecGetter.getFileSpecification(path.toAbsolutePath().toString());
                currentWorkspace.fileChanged(fileSpec);
            }
            //we do a quick check of the current file on change for better
            //performance while typing. we'll do a full check when we save the
            //file later
            checkFilePathForProblems(path, true);
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
        if (path != null)
        {
            sourceByPath.remove(path);
            codeActionsByPath.remove(path);
        }
    }

    /**
     * Called when a file being edited is saved.
     */
    @Override
    public void didSave(DidSaveTextDocumentParams params)
    {
        //as long as we're checking on change, we shouldn't need to do anything
        //on save
    }

    /**
     * Called when certain files in the workspace are added, removed, or
     * changed, even if they are not considered open for editing. Also checks if
     * the project configuration strategy has changed. If it has, checks for
     * errors on the whole project.
     */
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params)
    {
        boolean needsFullCheck = false;
        for (FileEvent event : params.getChanges())
        {
            Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(event.getUri());
            if (path == null)
            {
                continue;
            }
            File file = path.toFile();
            String fileName = file.getName();
            if ((fileName.endsWith(AS_EXTENSION) || fileName.endsWith(MXML_EXTENSION))
                    && currentWorkspace != null)
            {
                if (event.getType().equals(FileChangeType.Deleted))
                {
                    IFileSpecification fileSpec = fileSpecGetter.getFileSpecification(file.getAbsolutePath());
                    currentWorkspace.fileRemoved(fileSpec);
                    needsFullCheck = true;
                }
                else if (event.getType().equals(FileChangeType.Created))
                {
                    IFileSpecification fileSpec = fileSpecGetter.getFileSpecification(file.getAbsolutePath());
                    currentWorkspace.fileAdded(fileSpec);
                }
                else if (event.getType().equals(FileChangeType.Changed))
                {
                    IFileSpecification fileSpec = fileSpecGetter.getFileSpecification(file.getAbsolutePath());
                    currentWorkspace.fileChanged(fileSpec);
                    checkFilePathForProblems(path, false);
                }
            }
        }
        if (needsFullCheck || projectConfigStrategy.getChanged())
        {
            checkProjectForProblems();
        }
    }

    /**
     * Called if something in the configuration has changed.
     */
    public void checkForProblemsNow()
    {
        updateFrameworkSDK();
        if (projectConfigStrategy.getChanged())
        {
            checkProjectForProblems();
        }
    }

    private void updateFrameworkSDK()
    {
        String frameworkSDKPath = System.getProperty(FLEXLIB);
        if(frameworkSDKPath.equals(oldFrameworkSDKPath))
        {
            return;
        }
        oldFrameworkSDKPath = frameworkSDKPath;
        //if the framework SDK doesn't include the Falcon compiler, we can
        //ignore certain errors from the editor SDK, which includes Falcon.
        Path sdkPath = Paths.get(frameworkSDKPath);
        sdkPath = sdkPath.resolve("../lib/falcon-mxmlc.jar");
        flexLibSDKContainsFalconCompiler = sdkPath.toFile().exists();
        sdkPath = Paths.get(System.getProperty(FLEXLIB));
        sdkPath = sdkPath.resolve("../js/bin/asjsc");
        flexLibSDKIsFlexJS = sdkPath.toFile().exists();
    }

    private void cleanupCurrentProject()
    {
        currentWorkspace = null;
        currentProject = null;
        fileSpecGetter = null;
        compilationUnits = null;
    }

    private void refreshProjectOptions()
    {
        if (!projectConfigStrategy.getChanged() && currentProjectOptions != null)
        {
            //the options are fully up-to-date
            return;
        }
        //if the configuration changed, start fresh with a whole new workspace
        cleanupCurrentProject();
        currentProjectOptions = projectConfigStrategy.getOptions();
    }

    private String nodeToContainingPackageName(IASNode node)
    {
        IASNode currentNode = node;
        String containingPackageName = null;
        while(currentNode != null && containingPackageName == null)
        {
            containingPackageName = currentNode.getPackageName();
            currentNode = currentNode.getParent();
        }
        return containingPackageName;
    }

    private CompletionList actionScriptCompletion(TextDocumentPositionParams position)
    {
        IASNode offsetNode = getOffsetNode(position);
        return actionScriptCompletionWithNode(position, offsetNode);
    }

    private CompletionList actionScriptCompletionWithNode(TextDocumentPositionParams position, IASNode offsetNode)
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

        if (!isActionScriptCompletionAllowedInNode(position, offsetNode))
        {
            //if we're inside a node that shouldn't have completion!
            return new CompletionList();
        }

        String containingPackageName = nodeToContainingPackageName(offsetNode);

        //variable types
        if (offsetNode instanceof IVariableNode)
        {
            IVariableNode variableNode = (IVariableNode) offsetNode;
            IExpressionNode nameExpression = variableNode.getNameExpressionNode();
            IExpressionNode typeNode = variableNode.getVariableTypeNode();
            int line = position.getPosition().getLine();
            int column = position.getPosition().getCharacter();
            if (line >= nameExpression.getEndLine() && line <= typeNode.getLine())
            {
                if ((line != nameExpression.getEndLine() && line != typeNode.getLine())
                        || (line == nameExpression.getEndLine() && column > nameExpression.getEndColumn())
                        || (line == typeNode.getLine() && column <= typeNode.getColumn()))
                {
                    autoCompleteTypes(offsetNode, containingPackageName, result);
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
                autoCompleteTypes(parentNode, containingPackageName, result);
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
                int line = position.getPosition().getLine();
                int column = position.getPosition().getCharacter();
                if (line >= parameters.getEndLine()
                        && column > parameters.getEndColumn()
                        && line <= typeNode.getLine()
                        && column <= typeNode.getColumn())
                {
                    autoCompleteTypes(offsetNode, containingPackageName, result);
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
                autoCompleteTypes(parentNode, containingPackageName, result);
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
                autoCompleteTypes(parentNode, containingPackageName, result);
                return result;
            }
        }
        if (nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IKeywordNode
                && nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordNewID)
        {
            autoCompleteTypes(nodeAtPreviousOffset, containingPackageName, result);
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
                autoCompleteTypes(parentNode, containingPackageName, result);
                return result;
            }
        }
        if (nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IBinaryOperatorNode
                && (nodeAtPreviousOffset.getNodeID() == ASTNodeID.Op_AsID
                || nodeAtPreviousOffset.getNodeID() == ASTNodeID.Op_IsID))
        {
            autoCompleteTypes(nodeAtPreviousOffset, containingPackageName, result);
            return result;
        }
        //class extends keyword
        if (offsetNode instanceof IClassNode
                && nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IKeywordNode
                && nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordExtendsID)
        {
            autoCompleteTypes(offsetNode, containingPackageName, result);
            return result;
        }
        //class implements keyword
        if (offsetNode instanceof IClassNode
                && nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IKeywordNode
                && nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordImplementsID)
        {
            autoCompleteTypes(offsetNode, containingPackageName, result);
            return result;
        }
        //interface extends keyword
        if (offsetNode instanceof IInterfaceNode
                && nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IKeywordNode
                && nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordExtendsID)
        {
            autoCompleteTypes(offsetNode, containingPackageName, result);
            return result;
        }

        //package (must be before member access)
        if (offsetNode instanceof IFileNode)
        {
            IFileNode fileNode = (IFileNode) offsetNode;
            if (fileNode.getChildCount() == 0 && fileNode.getAbsoluteEnd() == 0)
            {
                //the file is completely empty
                autoCompletePackageBlock(result);
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
                    autoCompletePackageBlock(result);
                    return result;
                }
            }
        }
        if (offsetNode instanceof IPackageNode)
        {
            IPackageNode packageNode = (IPackageNode) offsetNode;
            autoCompletePackageName(packageNode.getPackageName(), result);
            return result;
        }
        if (parentNode != null
                && parentNode instanceof FullNameNode)
        {
            IASNode gpNode = parentNode.getParent();
            if (gpNode != null && gpNode instanceof IPackageNode)
            {
                IPackageNode packageNode = (IPackageNode) gpNode;
                autoCompletePackageName(packageNode.getPackageName(), result);
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
                    autoCompletePackageBlock(result);
                }
                else
                {
                    autoCompletePackageName(packageNode.getPackageName(), result);
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
                importName = importName.substring(0, position.getPosition().getCharacter() - nameNode.getColumn());
                autoCompleteImport(importName, result);
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
                    importName = importName.substring(0, position.getPosition().getCharacter() - nameNode.getColumn());
                    autoCompleteImport(importName, result);
                    return result;
                }
            }
        }
        if (nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IImportNode)
        {
            autoCompleteImport("", result);
            return result;
        }

        //member access
        if (offsetNode instanceof IMemberAccessExpressionNode)
        {
            IMemberAccessExpressionNode memberAccessNode = (IMemberAccessExpressionNode) offsetNode;
            IExpressionNode leftOperand = memberAccessNode.getLeftOperandNode();
            IExpressionNode rightOperand = memberAccessNode.getRightOperandNode();
            int line = position.getPosition().getLine();
            int column = position.getPosition().getCharacter();
            if (line >= leftOperand.getEndLine() && line <= rightOperand.getLine())
            {
                if ((line != leftOperand.getEndLine() && line != rightOperand.getLine())
                        || (line == leftOperand.getEndLine() && column > leftOperand.getEndColumn())
                        || (line == rightOperand.getLine() && column <= rightOperand.getColumn()))
                {
                    autoCompleteMemberAccess(memberAccessNode, result);
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
                autoCompleteMemberAccess(memberAccessNode, result);
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
                    autoCompleteMemberAccess(memberAccessNode, result);
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
                    autoCompleteFunctionOverrides(functionNode, result);
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
                    autoCompleteFunctionOverrides(functionNode, result);
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
                autoCompleteScope(scopedNode, false, containingPackageName, result);

                //include all public definitions
                IASScope scope = scopedNode.getScope();
                IDefinition definitionToSkip = scope.getDefinition();
                autoCompleteDefinitions(result, false, false, null, definitionToSkip, containingPackageName);
                autoCompleteKeywords(scopedNode, result);
                return result;
            }
            currentNodeForScope = currentNodeForScope.getParent();
        }
        while (currentNodeForScope != null);

        return result;
    }

    private CompletionList mxmlCompletion(TextDocumentPositionParams position, IMXMLTagData offsetTag)
    {
        CompletionList result = new CompletionList();
        result.setIsIncomplete(false);
        result.setItems(new ArrayList<>());
        if (isInXMLComment(position))
        {
            //if we're inside a comment, no completion!
            return result;
        }

        IMXMLTagData parentTag = offsetTag.getParentTag();

        //for some reason, the attributes list includes the >, but that's not
        //what we want here, so check if currentOffset isn't the end of the tag!
        boolean isAttribute = offsetTag.isOffsetInAttributeList(currentOffset)
                && currentOffset < offsetTag.getAbsoluteEnd();
        if (isAttribute && offsetTag.isCloseTag())
        {
            return result;
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
        if (isDeclarationsTag(offsetTag))
        {
            if (!isAttribute)
            {
                autoCompleteTypesForMXML(result);
            }
            return result;
        }

        IDefinition offsetDefinition = getDefinitionForMXMLTag(offsetTag);
        if (offsetDefinition == null)
        {
            IDefinition parentDefinition = null;
            if (parentTag != null)
            {
                parentDefinition = getDefinitionForMXMLTag(parentTag);
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
                        addMembersForMXMLTypeToAutoComplete(classDefinition, parentTag, offsetPrefix.length() == 0, result);
                    }
                    if (!isAttribute)
                    {
                        MXMLNamespace fxNS = getMXMLLanguageNamespace();
                        IMXMLData mxmlParent = offsetTag.getParent();
                        if (mxmlParent != null && parentTag.equals(mxmlParent.getRootTag()))
                        {
                            if (offsetPrefix.length() == 0)
                            {
                                //this tag doesn't have a prefix
                                addRootMXMLLanguageTagsToAutoComplete(offsetTag, fxNS.prefix, true, result);
                            }
                            else if (offsetPrefix.equals(fxNS.prefix))
                            {
                                //this tag has a prefix
                                addRootMXMLLanguageTagsToAutoComplete(offsetTag, fxNS.prefix, false, result);
                            }
                        }
                        if (offsetPrefix.length() == 0)
                        {
                            //this tag doesn't have a prefix
                            addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.COMPONENT, fxNS.prefix, true, result);
                        }
                        else if (offsetPrefix.equals(fxNS.prefix))
                        {
                            //this tag has a prefix
                            addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.COMPONENT, fxNS.prefix, false, result);
                        }
                        String defaultPropertyName = classDefinition.getDefaultPropertyName(currentProject);
                        if (defaultPropertyName != null)
                        {
                            //only add types if the class defines [DefaultProperty]
                            //metadata
                            autoCompleteTypesForMXMLFromExistingTag(result, offsetTag);
                        }
                    }
                }
                else
                {
                    //the parent is something like a property, so matching the
                    //prefix is not required
                    autoCompleteTypesForMXMLFromExistingTag(result, offsetTag);
                }
                return result;
            }
            else if (isDeclarationsTag(parentTag))
            {
                autoCompleteTypesForMXMLFromExistingTag(result, offsetTag);
                return result;
            }
            return result;
        }
        if (offsetDefinition instanceof IClassDefinition)
        {
            IMXMLTagAttributeData attribute = getMXMLTagAttributeWithValueAtOffset(offsetTag, currentOffset);
            if (attribute != null)
            {
                return mxmlAttributeCompletion(offsetTag, result);
            }
            attribute = getMXMLTagAttributeWithNameAtOffset(offsetTag, currentOffset, true);
            if (attribute != null
                    && currentOffset > (attribute.getAbsoluteStart() + attribute.getXMLName().toString().length()))
            {
                return mxmlStatesCompletion(offsetTag, result);
            }

            IClassDefinition classDefinition = (IClassDefinition) offsetDefinition;
            addMembersForMXMLTypeToAutoComplete(classDefinition, offsetTag, !isAttribute, result);

            if (!isAttribute)
            {
                IMXMLData mxmlParent = offsetTag.getParent();
                MXMLNamespace fxNS = getMXMLLanguageNamespace();
                if (mxmlParent != null && offsetTag.equals(mxmlParent.getRootTag()))
                {
                    addRootMXMLLanguageTagsToAutoComplete(offsetTag, fxNS.prefix, true, result);
                }
                addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.COMPONENT, fxNS.prefix, true, result);
                String defaultPropertyName = classDefinition.getDefaultPropertyName(currentProject);
                if (defaultPropertyName != null)
                {
                    //if [DefaultProperty] is set, then we can instantiate
                    //types as child elements
                    //but we don't want to do that when in an attribute
                    autoCompleteTypesForMXML(result);
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
                autoCompleteTypesForMXML(result);
            }
            return result;
        }
        System.err.println("Unknown definition for MXML completion: " + offsetDefinition.getClass());
        return result;
    }

    private CompletionList mxmlStatesCompletion(IMXMLTagData offsetTag, CompletionList result)
    {
        List<IDefinition> definitions = currentUnit.getDefinitionPromises();
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
            return result;
        }
        return result;
    }

    private CompletionList mxmlAttributeCompletion(IMXMLTagData offsetTag, CompletionList result)
    {
        List<CompletionItem> items = result.getItems();
        IDefinition attributeDefinition = getDefinitionForMXMLTagAttribute(offsetTag, currentOffset, true);
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
        }
        return result;
    }

    private CompletableFuture<Hover> actionScriptHover(TextDocumentPositionParams position)
    {
        IASNode offsetNode = getOffsetNode(position);
        return actionScriptHoverWithNode(position, offsetNode);
    }

    private CompletableFuture<Hover> actionScriptHoverWithNode(TextDocumentPositionParams position, IASNode offsetNode)
    {
        IDefinition definition = null;
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return CompletableFuture.completedFuture(new Hover(Collections.emptyList(), null));
        }

        //INamespaceDecorationNode extends IIdentifierNode, but we don't want
        //any hover information for it.
        if (definition == null
                && offsetNode instanceof IIdentifierNode
                && !(offsetNode instanceof INamespaceDecorationNode))
        {
            IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
            definition = identifierNode.resolve(currentUnit.getProject());
        }

        if (definition == null)
        {
            return CompletableFuture.completedFuture(new Hover(Collections.emptyList(), null));
        }

        Hover result = new Hover();
        String detail = getDefinitionDetail(definition);
        List<Either<String,MarkedString>> contents = new ArrayList<>();
        contents.add(Either.forLeft(MARKDOWN_CODE_BLOCK_NEXTGENAS_START + detail + MARKDOWN_CODE_BLOCK_END));
        String docs = getDocumentationForDefinition(definition, true);
        if(docs != null)
        {
            contents.add(Either.forLeft(docs));
        }
        result.setContents(contents);
        return CompletableFuture.completedFuture(result);
    }

    private CompletableFuture<Hover> mxmlHover(TextDocumentPositionParams position, IMXMLTagData offsetTag)
    {
        IDefinition definition = getDefinitionForMXMLNameAtOffset(offsetTag, currentOffset);
        if (definition == null)
        {
            return CompletableFuture.completedFuture(new Hover(Collections.emptyList(), null));
        }

        if (isInsideTagPrefix(offsetTag, currentOffset))
        {
            //inside the prefix
            String prefix = offsetTag.getPrefix();
            Hover result = new Hover();
            List<Either<String,MarkedString>> contents = new ArrayList<>();
            StringBuilder detailBuilder = new StringBuilder();
            detailBuilder.append(MARKDOWN_CODE_BLOCK_MXML_START);
            if (prefix.length() > 0)
            {
                detailBuilder.append("xmlns:" + prefix + "=\"" + offsetTag.getURI() + "\"");
            }
            else
            {
                detailBuilder.append("xmlns=\"" + offsetTag.getURI() + "\"");
            }
            detailBuilder.append(MARKDOWN_CODE_BLOCK_END);
            contents.add(Either.forLeft(detailBuilder.toString()));
            result.setContents(contents);
            return CompletableFuture.completedFuture(result);
        }

        Hover result = new Hover();
        String detail = getDefinitionDetail(definition);
        List<Either<String,MarkedString>> contents = new ArrayList<>();
        contents.add(Either.forLeft(MARKDOWN_CODE_BLOCK_NEXTGENAS_START + detail + MARKDOWN_CODE_BLOCK_END));
        result.setContents(contents);
        return CompletableFuture.completedFuture(result);
    }

    private CompletableFuture<List<? extends Location>> actionScriptDefinition(TextDocumentPositionParams position)
    {
        IASNode offsetNode = getOffsetNode(position);
        return actionScriptDefinitionWithNode(position, offsetNode);
    }

    private CompletableFuture<List<? extends Location>> actionScriptDefinitionWithNode(TextDocumentPositionParams position, IASNode offsetNode)
    {
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        IDefinition definition = null;

        if (offsetNode instanceof IIdentifierNode)
        {
            IIdentifierNode expressionNode = (IIdentifierNode) offsetNode;
            definition = expressionNode.resolve(currentProject);

            if (definition == null)
            {
                if (expressionNode.getName().equals(IASKeywordConstants.SUPER))
                {
                    ITypeDefinition typeDefinition = expressionNode.resolveType(currentProject);
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
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        List<Location> result = new ArrayList<>();
        resolveDefinition(definition, result);
        return CompletableFuture.completedFuture(result);
    }

    private CompletableFuture<List<? extends Location>> mxmlDefinition(TextDocumentPositionParams position, IMXMLTagData offsetTag)
    {
        IDefinition definition = getDefinitionForMXMLNameAtOffset(offsetTag, currentOffset);
        if (definition == null)
        {
            //VSCode may call definition() when there isn't necessarily a
            //definition referenced at the current position.
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        if (isInsideTagPrefix(offsetTag, currentOffset))
        {
            //ignore the tag's prefix
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        List<Location> result = new ArrayList<>();
        resolveDefinition(definition, result);
        return CompletableFuture.completedFuture(result);
    }

    private CompletableFuture<List<? extends Location>> actionScriptReferences(ReferenceParams params)
    {
        IASNode offsetNode = getOffsetNode(params);
        return actionScriptReferencesWithNode(params, offsetNode);
    }

    private CompletableFuture<List<? extends Location>> actionScriptReferencesWithNode(ReferenceParams params, IASNode offsetNode)
    {
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        if (offsetNode instanceof IIdentifierNode)
        {
            IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
            IDefinition resolved = identifierNode.resolve(currentProject);
            if (resolved == null)
            {
                return CompletableFuture.completedFuture(Collections.emptyList());
            }
            List<Location> result = new ArrayList<>();
            referencesForDefinition(resolved, result);
            return CompletableFuture.completedFuture(result);
        }

        //VSCode may call definition() when there isn't necessarily a
        //definition referenced at the current position.
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    private CompletableFuture<List<? extends Location>> mxmlReferences(ReferenceParams params, IMXMLTagData offsetTag)
    {
        IDefinition definition = getDefinitionForMXMLNameAtOffset(offsetTag, currentOffset);
        if (definition != null)
        {
            if (isInsideTagPrefix(offsetTag, currentOffset))
            {
                //ignore the tag's prefix
                return CompletableFuture.completedFuture(Collections.emptyList());
            }
            ArrayList<Location> result = new ArrayList<>();
            referencesForDefinition(definition, result);
            return CompletableFuture.completedFuture(result);
        }

        //finally, check if we're looking for references to a tag's id
        IMXMLTagAttributeData attributeData = getMXMLTagAttributeWithValueAtOffset(offsetTag, currentOffset);
        if (attributeData == null || !attributeData.getName().equals(IMXMLLanguageConstants.ATTRIBUTE_ID))
        {
            //VSCode may call definition() when there isn't necessarily a
            //definition referenced at the current position.
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(params.getTextDocument().getUri());
        if (path == null)
        {
            //this probably shouldn't happen, but check just to be safe
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        ICompilationUnit unit = getCompilationUnit(path);
        Collection<IDefinition> definitions = null;
        try
        {
            definitions = unit.getFileScopeRequest().get().getExternallyVisibleDefinitions();
        }
        catch (Exception e)
        {
            //safe to ignore
        }
        if (definitions == null || definitions.size() == 0)
        {
            return CompletableFuture.completedFuture(Collections.emptyList());
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
            return CompletableFuture.completedFuture(Collections.emptyList());
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
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        ArrayList<Location> result = new ArrayList<>();
        referencesForDefinition(definition, result);
        return CompletableFuture.completedFuture(result);
    }

    private CompletableFuture<WorkspaceEdit> actionScriptRename(RenameParams params)
    {
        IASNode offsetNode = getOffsetNode(params.getTextDocument(), params.getPosition());
        return actionScriptRenameWithNode(params, offsetNode);
    }

    private CompletableFuture<WorkspaceEdit> actionScriptRenameWithNode(RenameParams params, IASNode offsetNode)
    {
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return CompletableFuture.completedFuture(new WorkspaceEdit(new HashMap<>()));
        }

        IDefinition definition = null;

        if (offsetNode instanceof IDefinitionNode)
        {
            IDefinitionNode definitionNode = (IDefinitionNode) offsetNode;
            IExpressionNode expressionNode = definitionNode.getNameExpressionNode();
            definition = expressionNode.resolve(currentProject);
        }
        else if (offsetNode instanceof IIdentifierNode)
        {
            IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
            definition = identifierNode.resolve(currentProject);
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
            return CompletableFuture.completedFuture(new WorkspaceEdit(new HashMap<>()));
        }

        WorkspaceEdit result = renameDefinition(definition, params.getNewName());
        return CompletableFuture.completedFuture(result);
    }

    private CompletableFuture<WorkspaceEdit> mxmlRename(RenameParams params, IMXMLTagData offsetTag)
    {
        IDefinition definition = getDefinitionForMXMLNameAtOffset(offsetTag, currentOffset);
        if (definition != null)
        {
            if (isInsideTagPrefix(offsetTag, currentOffset))
            {
                //ignore the tag's prefix
                return CompletableFuture.completedFuture(new WorkspaceEdit(new HashMap<>()));
            }
            WorkspaceEdit result = renameDefinition(definition, params.getNewName());
            return CompletableFuture.completedFuture(result);
        }

        if (languageClient != null)
        {
            MessageParams message = new MessageParams();
            message.setType(MessageType.Info);
            message.setMessage("You cannot rename this element.");
            languageClient.showMessage(message);
        }
        return CompletableFuture.completedFuture(new WorkspaceEdit(new HashMap<>()));
    }
    
    private void referencesForDefinitionInCompilationUnit(IDefinition definition, ICompilationUnit compilationUnit, List<Location> result)
    {
        if (compilationUnit.getAbsoluteFilename().endsWith(MXML_EXTENSION))
        {
            IMXMLDataManager mxmlDataManager = currentWorkspace.getMXMLDataManager();
            MXMLData mxmlData = (MXMLData) mxmlDataManager.get(fileSpecGetter.getFileSpecification(compilationUnit.getAbsoluteFilename()));
            IMXMLTagData rootTag = mxmlData.getRootTag();
            if (rootTag != null)
            {
                ArrayList<ISourceLocation> units = new ArrayList<>();
                findMXMLUnits(mxmlData.getRootTag(), definition, units);
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
        IASNode ast;
        try
        {
            ast = compilationUnit.getSyntaxTreeRequest().get().getAST();
        }
        catch (Exception e)
        {
            return;
        }
        ArrayList<IIdentifierNode> identifiers = new ArrayList<>();
        findIdentifiers(ast, definition, identifiers);
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

    private void referencesForDefinition(IDefinition definition, List<Location> result)
    {
        for (ICompilationUnit compilationUnit : compilationUnits)
        {
            if (compilationUnit == null || compilationUnit instanceof SWCCompilationUnit)
            {
                continue;
            }
            referencesForDefinitionInCompilationUnit(definition, compilationUnit, result);
        }
    }

    private MXMLNamespace getNamespaceFromURI(String uri, PrefixMap prefixMap)
    {
        String[] uriPrefixes = prefixMap.getPrefixesForNamespace(uri);
        if (uriPrefixes.length > 0)
        {
            return new MXMLNamespace(uriPrefixes[0], uri);
        }

        //we'll check if the namespace comes from a known library
        //with a common prefix
        if (NAMESPACE_TO_PREFIX.containsKey(uri))
        {
            String prefix = NAMESPACE_TO_PREFIX.get(uri);
            if (prefixMap.containsPrefix(prefix))
            {
                //the prefix already exists with a different URI, so we can't
                //use it for this URI
                prefix = null;
            }
            if (prefix != null)
            {
                return new MXMLNamespace(prefix, uri);
            }
        }
        return null;
    }

    private MXMLNamespace getMXMLLanguageNamespace()
    {
        IMXMLDataManager mxmlDataManager = currentWorkspace.getMXMLDataManager();
        MXMLData mxmlData = (MXMLData) mxmlDataManager.get(fileSpecGetter.getFileSpecification(currentUnit.getAbsoluteFilename()));
        PrefixMap prefixMap = mxmlData.getRootTagPrefixMap();
        String fxURI = mxmlData.getRootTag().getMXMLDialect().getLanguageNamespace();
        MXMLNamespace fxNS = getNamespaceFromURI(fxURI, prefixMap);
        return fxNS;
    }

    private MXMLNamespace getMXMLNamespaceForTypeDefinition(ITypeDefinition definition, MXMLData mxmlData)
    {
        PrefixMap prefixMap = mxmlData.getRootTagPrefixMap();
        Collection<XMLName> tagNames = currentProject.getTagNamesForClass(definition.getQualifiedName());

        //1. try to use an existing xmlns with an uri
        for (XMLName tagName : tagNames)
        {
            String tagNamespace = tagName.getXMLNamespace();
            //getTagNamesForClass() returns the 2006 namespace, even if that's
            //not what we're using in this file
            if (tagNamespace.equals(IMXMLLanguageConstants.NAMESPACE_MXML_2006))
            {
                //use the language namespace of the root tag instead
                tagNamespace = mxmlData.getRootTag().getMXMLDialect().getLanguageNamespace();
            }
            String[] uriPrefixes = prefixMap.getPrefixesForNamespace(tagNamespace);
            if (uriPrefixes.length > 0)
            {
                return new MXMLNamespace(uriPrefixes[0], tagNamespace);
            }
        }

        //2. try to use an existing xmlns with a package name
        String packageName = definition.getPackageName();
        String packageNamespace = getPackageNameMXMLNamespaceURI(packageName);
        String[] packagePrefixes = prefixMap.getPrefixesForNamespace(packageNamespace);
        if (packagePrefixes.length > 0)
        {
            return new MXMLNamespace(packagePrefixes[0], packageNamespace);
        }

        //3. try to create a new xmlns with a prefix and uri


        //special case for the __AS3__ package
        if (packageName != null && packageName.startsWith(UNDERSCORE_UNDERSCORE_AS3_PACKAGE))
        {
            //anything in this package is in the language namespace
            String fxNamespace = mxmlData.getRootTag().getMXMLDialect().getLanguageNamespace();
            MXMLNamespace resultNS = getNamespaceFromURI(fxNamespace, prefixMap);
            if (resultNS != null)
            {
                return resultNS;
            }
        }

        String fallbackNamespace = null;
        for (XMLName tagName : tagNames)
        {
            //we know this type is in one or more namespaces
            //let's try to figure out a nice prefix to use
            fallbackNamespace = tagName.getXMLNamespace();
            MXMLNamespace resultNS = getNamespaceFromURI(fallbackNamespace, prefixMap);
            if (resultNS != null)
            {
                return resultNS;
            }
        }
        if (fallbackNamespace != null)
        {
            //if we couldn't find a known prefix, use a numbered one
            String prefix = getNumberedNamespacePrefix(DEFAULT_NS_PREFIX, prefixMap);
            return new MXMLNamespace(prefix, fallbackNamespace);
        }

        //4. worse case: create a new xmlns with numbered prefix and package name
        String prefix = getNumberedNamespacePrefix(DEFAULT_NS_PREFIX, prefixMap);
        return new MXMLNamespace(prefix, packageNamespace);
    }

    private String getPackageNameMXMLNamespaceURI(String packageName)
    {
        if (packageName.length() > 0)
        {
            return packageName + DOT_STAR;
        }
        return STAR;
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

    private void autoCompleteTypes(IASNode withNode, String containingPackageName, CompletionList result)
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
                autoCompleteScope(scopedNode, true, containingPackageName, result);
                break;
            }
            node = node.getParent();
        }
        while (node != null);
        autoCompleteDefinitions(result, false, true, null, null, containingPackageName);
    }

    private void autoCompleteTypesForMXML(CompletionList result)
    {
        autoCompleteDefinitions(result, true, true, null, null, null);
    }

    /**
     * Using an existing tag, that may already have a prefix or short name,
     * populate the completion list.
     */
    private void autoCompleteTypesForMXMLFromExistingTag(CompletionList result, IMXMLTagData offsetTag)
    {
        IMXMLDataManager mxmlDataManager = currentWorkspace.getMXMLDataManager();
        MXMLData mxmlData = (MXMLData) mxmlDataManager.get(fileSpecGetter.getFileSpecification(currentUnit.getAbsoluteFilename()));
        String tagStartShortName = offsetTag.getShortName();
        String tagPrefix = offsetTag.getPrefix();
        PrefixMap prefixMap = mxmlData.getRootTagPrefixMap();

        for (ICompilationUnit unit : compilationUnits)
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

                //first check that the tag either doesn't have a short name yet
                //or that the definition's base name matches the short name 
                if (tagStartShortName.length() == 0 || typeDefinition.getBaseName().startsWith(tagStartShortName))
                {
                    //if a prefix already exists, make sure the definition is
                    //in a namespace with that prefix
                    if (tagPrefix.length() > 0)
                    {
                        Collection<XMLName> tagNames = currentProject.getTagNamesForClass(typeDefinition.getQualifiedName());
                        for (XMLName tagName : tagNames)
                        {
                            String tagNamespace = tagName.getXMLNamespace();
                            //getTagNamesForClass() returns the 2006 namespace, even if that's
                            //not what we're using in this file
                            if (tagNamespace.equals(IMXMLLanguageConstants.NAMESPACE_MXML_2006))
                            {
                                //use the language namespace of the root tag instead
                                tagNamespace = mxmlData.getRootTag().getMXMLDialect().getLanguageNamespace();
                            }
                            String[] prefixes = prefixMap.getPrefixesForNamespace(tagNamespace);
                            for (String otherPrefix : prefixes)
                            {
                                if (tagPrefix.equals(otherPrefix))
                                {
                                    addDefinitionAutoCompleteMXML(typeDefinition, null, null, result);
                                }
                            }
                        }
                    }
                    else
                    {
                        //no prefix yet, so complete the definition with a prefix
                        MXMLNamespace ns = getMXMLNamespaceForTypeDefinition(typeDefinition, mxmlData);
                        addDefinitionAutoCompleteMXML(typeDefinition, ns.prefix, ns.uri, result);
                    }
                }
            }
        }
    }

    private void autoCompleteDefinitions(CompletionList result, boolean forMXML,
                                         boolean typesOnly, String requiredPackageName,
                                         IDefinition definitionToSkip, String containingPackageName)
    {
        String skipQualifiedName = null;
        if (definitionToSkip != null)
        {
            skipQualifiedName = definitionToSkip.getQualifiedName();
        }
        for (ICompilationUnit unit : compilationUnits)
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
                        if (forMXML && isType)
                        {
                            ITypeDefinition typeDefinition = (ITypeDefinition) definition;
                            addMXMLTypeDefinitionAutoComplete(typeDefinition, result);
                        }
                        else
                        {
                            addDefinitionAutoCompleteActionScript(definition, containingPackageName, result);
                        }
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

    private void autoCompleteScope(IScopedNode node, boolean typesOnly, String containingPackageName, CompletionList result)
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
                addDefinitionsInTypeScopeToAutoComplete(typeScope, scope, true, true, false, null, result);
                if (!staticOnly)
                {
                    addDefinitionsInTypeScopeToAutoCompleteActionScript(typeScope, scope, false, result);
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
                            IGetterDefinition getter = setter.resolveGetter(currentProject);
                            if (getter != null)
                            {
                                //skip the setter if there's also a getter because
                                //it would add a duplicate entry
                                continue;
                            }
                        }
                        addDefinitionAutoCompleteActionScript(localDefinition, containingPackageName, result);
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

    private void autoCompleteFunctionOverrides(IFunctionNode node, CompletionList result)
    {
        String namespace = node.getNamespace();
        boolean isGetter = node.isGetter();
        boolean isSetter = node.isSetter();
        IClassNode classNode = (IClassNode) node.getAncestorOfType(IClassNode.class);
        IClassDefinition classDefinition = classNode.getDefinition();

        ArrayList<IDefinition> propertyDefinitions = new ArrayList<>();
        TypeScope typeScope = (TypeScope) classDefinition.getContainedScope();
        Set<INamespaceDefinition> namespaceSet = typeScope.getNamespaceSet(currentProject);
        do
        {
            classDefinition = classDefinition.resolveBaseClass(currentProject);
            if (classDefinition == null)
            {
                break;
            }
            typeScope = (TypeScope) classDefinition.getContainedScope();
            INamespaceDefinition protectedNamespace = classDefinition.getProtectedNamespaceReference();
            typeScope.getAllLocalProperties(currentProject, propertyDefinitions, namespaceSet, protectedNamespace);
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
            if (functionNames.contains(functionName))
            {
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
                    Object defaultValue = param.resolveDefaultValue(currentProject);
                    if (defaultValue instanceof String)
                    {
                        insertText.append("\"" + defaultValue + "\"");
                    }
                    else if(defaultValue == ABCConstants.UNDEFINED_VALUE)
                    {
                        insertText.append(IASLanguageConstants.UNDEFINED);
                    }
                    else if(defaultValue == ABCConstants.NULL_VALUE)
                    {
                        insertText.append(IASLanguageConstants.NULL);
                    }
                    else
                    {
                        insertText.append(defaultValue);
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

            CompletionItem item = new CompletionItem();
            item.setKind(getDefinitionKind(functionDefinition));
            item.setDetail(getDefinitionDetail(functionDefinition));
            item.setLabel(functionDefinition.getBaseName());
            item.setInsertText(insertText.toString());
            String docs = getDocumentationForDefinition(functionDefinition, false);
            if (docs != null)
            {
                item.setDocumentation(docs);
            }
            resultItems.add(item);
        }
    }

    private void autoCompleteMemberAccess(IMemberAccessExpressionNode node, CompletionList result)
    {
        ASScope scope = (ASScope) node.getContainingScope().getScope();
        IExpressionNode leftOperand = node.getLeftOperandNode();
        IDefinition leftDefinition = leftOperand.resolve(currentProject);
        if (leftDefinition != null && leftDefinition instanceof ITypeDefinition)
        {
            ITypeDefinition typeDefinition = (ITypeDefinition) leftDefinition;
            TypeScope typeScope = (TypeScope) typeDefinition.getContainedScope();
            addDefinitionsInTypeScopeToAutoCompleteActionScript(typeScope, scope, true, result);
            return;
        }
        ITypeDefinition leftType = leftOperand.resolveType(currentProject);
        if (leftType != null)
        {
            TypeScope typeScope = (TypeScope) leftType.getContainedScope();
            addDefinitionsInTypeScopeToAutoCompleteActionScript(typeScope, scope, false, result);
            return;
        }

        if (leftOperand instanceof IMemberAccessExpressionNode)
        {
            IMemberAccessExpressionNode memberAccess = (IMemberAccessExpressionNode) leftOperand;
            String packageName = memberAccessToPackageName(memberAccess);
            if (packageName != null)
            {
                autoCompleteDefinitions(result, false, false, packageName, null, null);
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

    private String getExpectedPackage(ICompilationUnit unit)
    {
        //we'll guess the package name based on path of the parent directory
        File unitFile = new File(unit.getAbsoluteFilename());
        unitFile = unitFile.getParentFile();

        //find the source path that the parent directory is inside
        //that way we can strip it down to just the package
        String basePath = null;
        for (File sourcePath : currentProject.getSourcePath())
        {
            if (unitFile.toPath().startsWith(sourcePath.toPath()))
            {
                basePath = sourcePath.toPath().toString();
                break;
            }
        }
        if (basePath == null)
        {
            //we couldn't find the source path!
            return "";
        }

        String expectedPackage = unitFile.getAbsolutePath().substring(basePath.length());
        //replace / in path on Unix
        expectedPackage = expectedPackage.replaceAll("/", ".");
        //replaces \ in path on Windows
        expectedPackage = expectedPackage.replaceAll("\\\\", ".");
        if (expectedPackage.startsWith("."))
        {
            expectedPackage = expectedPackage.substring(1);
        }
        return expectedPackage;
    }

    private void autoCompletePackageBlock(CompletionList result)
    {
        String expectedPackage = getExpectedPackage(currentUnit);
        StringBuilder builder = new StringBuilder();
        builder.append("package");
        if (expectedPackage.length() > 0)
        {
            builder.append(" ");
            builder.append(expectedPackage);
        }
        builder.append("\n{\n\t\n}");
        CompletionItem packageItem = new CompletionItem();
        packageItem.setLabel(builder.toString());
        packageItem.setKind(CompletionItemKind.Module);
        result.getItems().add(packageItem);
    }

    private void autoCompletePackageName(String partialPackageName, CompletionList result)
    {
        String expectedPackage = getExpectedPackage(currentUnit);
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

    private void autoCompleteImport(String importName, CompletionList result)
    {
        List<CompletionItem> items = result.getItems();
        for (ICompilationUnit unit : compilationUnits)
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

    private void addMXMLLanguageTagToAutoComplete(String tagName, String prefix, boolean includeOpenTagPrefix, CompletionList result)
    {
        List<CompletionItem> items = result.getItems();
        CompletionItem item = new CompletionItem();
        item.setKind(CompletionItemKind.Keyword);
        item.setLabel(tagName);
        StringBuilder builder = new StringBuilder();
        if (includeOpenTagPrefix)
        {
            builder.append(prefix);
            builder.append(IMXMLCoreConstants.colon);
        }
        builder.append(tagName);
        builder.append(">");
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

    private void addRootMXMLLanguageTagsToAutoComplete(IMXMLTagData offsetTag, String prefix, boolean includeOpenTagPrefix, CompletionList result)
    {
        List<CompletionItem> items = result.getItems();
                            
        CompletionItem item = new CompletionItem();
        item.setKind(CompletionItemKind.Keyword);
        item.setLabel(IMXMLLanguageConstants.SCRIPT);
        StringBuilder builder = new StringBuilder();
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

        addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.BINDING, prefix, includeOpenTagPrefix, result);
        addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.DECLARATIONS, prefix, includeOpenTagPrefix, result);
        addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.METADATA, prefix, includeOpenTagPrefix, result);
        addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.STYLE, prefix, includeOpenTagPrefix, result);
    }

    private void addMembersForMXMLTypeToAutoComplete(IClassDefinition definition, IMXMLTagData offsetTag, boolean includePrefix, CompletionList result)
    {
        ICompilationUnit unit = getCompilationUnit(Paths.get(offsetTag.getSourcePath()));
        if (unit == null)
        {
            return;
        }
        IASScope[] scopes;
        try
        {
            scopes = unit.getFileScopeRequest().get().getScopes();
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
            addDefinitionsInTypeScopeToAutoCompleteMXML(typeScope, scope, propertyElementPrefix, result);
            addStyleMetadataToAutoCompleteMXML(typeScope, propertyElementPrefix, result);
            addEventMetadataToAutoCompleteMXML(typeScope, propertyElementPrefix, result);
        }
    }

    private void addDefinitionsInTypeScopeToAutoCompleteActionScript(TypeScope typeScope, ASScope otherScope, boolean isStatic, CompletionList result)
    {
        addDefinitionsInTypeScopeToAutoComplete(typeScope, otherScope, isStatic, false, false, null, result);
    }

    private void addDefinitionsInTypeScopeToAutoCompleteMXML(TypeScope typeScope, ASScope otherScope, String prefix, CompletionList result)
    {
        addDefinitionsInTypeScopeToAutoComplete(typeScope, otherScope, false, false, true, prefix, result);
    }

    private void collectInterfaceNamespaces(IInterfaceDefinition interfaceDefinition, Set<INamespaceDefinition> namespaceSet)
    {
        TypeScope typeScope = (TypeScope) interfaceDefinition.getContainedScope();
        namespaceSet.addAll(typeScope.getNamespaceSet(currentProject));
        IInterfaceDefinition[] interfaceDefinitions = interfaceDefinition.resolveExtendedInterfaces(currentProject);
        for (IInterfaceDefinition extendedInterface : interfaceDefinitions)
        {
            collectInterfaceNamespaces(extendedInterface, namespaceSet);
        }
    }

    private void addDefinitionsInTypeScopeToAutoComplete(TypeScope typeScope, ASScope otherScope, boolean isStatic, boolean includeSuperStatics, boolean forMXML, String prefix, CompletionList result)
    {
        IMetaTag[] excludeMetaTags = typeScope.getDefinition().getMetaTagsByName(IMetaAttributeConstants.ATTRIBUTE_EXCLUDE);
        ArrayList<IDefinition> memberAccessDefinitions = new ArrayList<>();
        Set<INamespaceDefinition> namespaceSet = otherScope.getNamespaceSet(currentProject);
        if (typeScope.getContainingDefinition() instanceof IInterfaceDefinition)
        {
            //interfaces have a special namespace that isn't actually the same
            //as public, but should be treated the same way
            IInterfaceDefinition interfaceDefinition = (IInterfaceDefinition) typeScope.getContainingDefinition();
            collectInterfaceNamespaces(interfaceDefinition, namespaceSet);
        }
        IClassDefinition otherContainingClass = otherScope.getContainingClass();
        if (otherContainingClass != null)
        {
            IClassDefinition classDefinition = typeScope.getContainingClass();
            if (classDefinition != null)
            {
                boolean isSuperClass = Arrays.asList(otherContainingClass.resolveAncestry(currentProject)).contains(classDefinition);
                if (isSuperClass)
                {
                    //if the containing class of the type scope is a superclass
                    //of the other scope, we need to add the protected
                    //namespaces from the super classes
                    do
                    {
                        namespaceSet.add(classDefinition.getProtectedNamespaceReference());
                        classDefinition = classDefinition.resolveBaseClass(currentProject);
                    }
                    while (classDefinition instanceof IClassDefinition);
                }
            }
        }
        typeScope.getAllPropertiesForMemberAccess(currentProject, memberAccessDefinitions, namespaceSet);
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
                    IGetterDefinition getter = setter.resolveGetter(currentProject);
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
                addDefinitionAutoCompleteMXML(localDefinition, prefix, null, result);
            }
            else //actionscript
            {
                addDefinitionAutoCompleteActionScript(localDefinition, null, result);
            }
        }
    }

    private void addEventMetadataToAutoCompleteMXML(TypeScope typeScope, String prefix, CompletionList result)
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
                if (eventNames.contains(eventName))
                {
                    //avoid duplicates!
                    continue;
                }
                eventNames.add(eventName);
                IDefinition eventDefinition = currentProject.resolveSpecifier(classDefinition, eventName);
                if (eventDefinition == null)
                {
                    continue;
                }
                CompletionItem item = new CompletionItem();
                item.setKind(CompletionItemKind.Field);
                item.setLabel(eventName);
                if (prefix != null)
                {
                    item.setInsertText(prefix + IMXMLCoreConstants.colon + eventName);
                }
                item.setDetail(getDefinitionDetail(eventDefinition));
                result.getItems().add(item);
            }
            definition = classDefinition.resolveBaseClass(currentProject);
        }
    }

    private void addStyleMetadataToAutoCompleteMXML(TypeScope typeScope, String prefix, CompletionList result)
    {
        ArrayList<String> styleNames = new ArrayList<>();
        IDefinition definition = typeScope.getDefinition();
        List<CompletionItem> items = result.getItems();
        while (definition instanceof IClassDefinition)
        {
            IClassDefinition classDefinition = (IClassDefinition) definition;
            IMetaTag[] styleMetaTags = typeScope.getDefinition().getMetaTagsByName(IMetaAttributeConstants.ATTRIBUTE_STYLE);
            for (IMetaTag styleMetaTag : styleMetaTags)
            {
                String styleName = styleMetaTag.getAttributeValue(IMetaAttributeConstants.NAME_STYLE_NAME);
                if (styleNames.contains(styleName))
                {
                    //avoid duplicates!
                    continue;
                }
                styleNames.add(styleName);
                IDefinition styleDefinition = currentProject.resolveSpecifier(classDefinition, styleName);
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
                        //name. if there's a conflict, the compiler will know
                        //how to handle it.
                        foundExisting = true;
                        break;
                    }
                }
                if (foundExisting)
                {
                    break;
                }
                CompletionItem item = new CompletionItem();
                item.setKind(CompletionItemKind.Field);
                item.setLabel(styleName);
                if (prefix != null)
                {
                    item.setInsertText(prefix + IMXMLCoreConstants.colon + styleName);
                }
                item.setDetail(getDefinitionDetail(styleDefinition));
                items.add(item);
            }
            definition = classDefinition.resolveBaseClass(currentProject);
        }
    }

    private void addMXMLTypeDefinitionAutoComplete(ITypeDefinition definition, CompletionList result)
    {
        IMXMLDataManager mxmlDataManager = currentWorkspace.getMXMLDataManager();
        MXMLData mxmlData = (MXMLData) mxmlDataManager.get(fileSpecGetter.getFileSpecification(currentUnit.getAbsoluteFilename()));
        MXMLNamespace discoveredNS = getMXMLNamespaceForTypeDefinition(definition, mxmlData);
        addDefinitionAutoCompleteMXML(definition, discoveredNS.prefix, discoveredNS.uri, result);
    }

    private String getNumberedNamespacePrefix(String prefixPrefix, PrefixMap prefixMap)
    {
        //if all else fails, fall back to a generic namespace
        int count = 1;
        String prefix = null;
        do
        {
            prefix = prefixPrefix + count;
            if (prefixMap.containsPrefix(prefix))
            {
                prefix = null;
            }
            count++;
        }
        while (prefix == null);
        return prefix;
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

    private void addDefinitionAutoCompleteActionScript(IDefinition definition, String containingPackageName, CompletionList result)
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
        CompletionItem item = new CompletionItem();
        item.setKind(getDefinitionKind(definition));
        item.setDetail(getDefinitionDetail(definition));
        item.setLabel(definition.getBaseName());
        String docs = getDocumentationForDefinition(definition, false);
        if (docs != null)
        {
            item.setDocumentation(docs);
        }
        boolean isInPackage = !definition.getQualifiedName().equals(definition.getBaseName());
        if (isInPackage && (containingPackageName == null || !definition.getPackageName().equals(containingPackageName)))
        {
            Command command = createImportCommand(definition);
            if (command != null)
            {
                item.setCommand(command);
            }
        }
        result.getItems().add(item);
    }

    private void addDefinitionAutoCompleteMXML(IDefinition definition, String prefix, String uri, CompletionList result)
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
        CompletionItem item = new CompletionItem();
        item.setKind(getDefinitionKind(definition));
        item.setDetail(getDefinitionDetail(definition));
        item.setLabel(definition.getBaseName());
        String docs = getDocumentationForDefinition(definition, false);
        if (docs != null)
        {
            item.setDocumentation(docs);
        }
        if (prefix != null)
        {
            item.setInsertText(prefix + IMXMLCoreConstants.colon + definition.getBaseName());
            if (definition instanceof ITypeDefinition && uri != null)
            {
                item.setCommand(createMXMLNamespaceCommand(definition, prefix, uri));
            }
        }
        result.getItems().add(item);
    }
    
    private String getDocumentationForParameter(IParameterDefinition definition, boolean useMarkdown)
    {
        IDefinition parentDefinition = definition.getParent();
        if (!(parentDefinition instanceof IFunctionDefinition))
        {
            return null;
        }
        IFunctionDefinition functionDefinition = (IFunctionDefinition) parentDefinition;
        VSCodeASDocComment comment = (VSCodeASDocComment) functionDefinition.getExplicitSourceComment();
        if (comment == null)
        {
            return null;
        }
        comment.compile(useMarkdown);
        Collection<IASDocTag> paramTags = comment.getTagsByName(ASDOC_TAG_PARAM);
        if (paramTags == null)
        {
            return null;
        }
        String paramName = definition.getBaseName();
        for (IASDocTag paramTag : paramTags)
        {
            String description = paramTag.getDescription();
            if (description.startsWith(paramName + " "))
            {
                return description.substring(paramName.length() + 1);
            }
        }
        return null;
    }
    
    private String getDocumentationForDefinition(IDefinition definition, boolean useMarkdown)
    {
        if (!(definition instanceof IDocumentableDefinition))
        {
            return null;
        }
        IDocumentableDefinition documentableDefinition = (IDocumentableDefinition) definition;
        VSCodeASDocComment comment = (VSCodeASDocComment) documentableDefinition.getExplicitSourceComment();
        if (comment == null)
        {
            return null;
        }
        comment.compile(useMarkdown);
        String description = comment.getDescription();
        if (description == null)
        {
            return null;
        }
        return description;
    }

    private CompletionItemKind getDefinitionKind(IDefinition definition)
    {
        if (definition instanceof IClassDefinition)
        {
            return CompletionItemKind.Class;
        }
        else if (definition instanceof IInterfaceDefinition)
        {
            return CompletionItemKind.Interface;
        }
        else if (definition instanceof IFunctionDefinition)
        {
            IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
            if (functionDefinition.isConstructor())
            {
                return CompletionItemKind.Constructor;
            }
            return CompletionItemKind.Function;
        }
        else if (definition instanceof IVariableDefinition)
        {
            return CompletionItemKind.Variable;
        }
        return CompletionItemKind.Value;
    }

    private Command createImportCommand(IDefinition definition)
    {
        String packageName = definition.getPackageName();
        if (packageName == null
                || packageName.isEmpty()
                || packageName.startsWith(UNDERSCORE_UNDERSCORE_AS3_PACKAGE))
        {
            //don't even bother with these things that don't need importing
            return null;
        }
        String qualifiedName = definition.getQualifiedName();
        Command importCommand = new Command();
        importCommand.setTitle("Import " + qualifiedName);
        importCommand.setCommand(ICommandConstants.ADD_IMPORT);
        importCommand.setArguments(Arrays.asList(
            qualifiedName,
            importRange.uri,
            importRange.startIndex,
            importRange.endIndex
        ));
        return importCommand;
    }

    private Command createMXMLNamespaceCommand(IDefinition definition, String prefix, String uri)
    {
        Command xmlnsCommand = new Command();
        xmlnsCommand.setTitle("Add Namespace " + uri);
        xmlnsCommand.setCommand(ICommandConstants.ADD_MXML_NAMESPACE);
        xmlnsCommand.setArguments(Arrays.asList(
            prefix,
            uri,
            namespaceUri,
            namespaceStartIndex,
            namespaceEndIndex
        ));
        return xmlnsCommand;
    }

    private String transformDebugFilePath(String sourceFilePath)
    {
        int index = -1;
        if (isWindows)
        {
            //the debug file path divides directories with ; instead of slash in
            //a couple of places, but it's easy to fix
            sourceFilePath = sourceFilePath.replace(';', '\\');
            sourceFilePath = sourceFilePath.replace('/', '\\');
            index = sourceFilePath.indexOf(SDK_SOURCE_PATH_SIGNATURE_WINDOWS);
        }
        else
        {
            sourceFilePath = sourceFilePath.replace(';', '/');
            sourceFilePath = sourceFilePath.replace('\\', '/');
            index = sourceFilePath.indexOf(SDK_SOURCE_PATH_SIGNATURE_UNIX);
        }
        if (index == -1)
        {
            return sourceFilePath;
        }
        sourceFilePath = sourceFilePath.substring(index + SDK_FRAMEWORKS_PATH_SIGNATURE.length());
        Path frameworkPath = Paths.get(System.getProperty(FLEXLIB));
        Path transformedPath = frameworkPath.resolve(sourceFilePath);
        return transformedPath.toFile().getAbsolutePath();
    }

    private void resolveDefinition(IDefinition definition, List<Location> result)
    {
        String definitionPath = definition.getSourcePath();
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
                ICompilationUnit unit = currentProject.getScope().getCompilationUnitForDefinition(definition);
                try
                {
                    byte[] abcBytes = unit.getABCBytesRequest().get().getABCBytes();
                    ABCParser parser = new ABCParser(abcBytes);
                    PoolingABCVisitor visitor = new PoolingABCVisitor();
                    parser.parseABC(visitor);
                    Pool<String> pooledStrings = visitor.getStringPool();
                    for (String pooledString : pooledStrings.getValues())
                    {
                        if (pooledString.contains(SDK_SOURCE_PATH_SIGNATURE_UNIX)
                                || pooledString.contains(SDK_SOURCE_PATH_SIGNATURE_WINDOWS))
                        {
                            //just go with the first one that we find
                            definitionPath = transformDebugFilePath(pooledString);
                            break;
                        }
                    }
                }
                catch (InterruptedException e)
                {
                    //safe to ignore
                }
            }
            if (!definitionPath.endsWith(AS_EXTENSION)
                    && !definitionPath.endsWith(MXML_EXTENSION))
            {
                //if it's in a SWC or something, we don't know how to resolve
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

    private WorkspaceEdit renameDefinition(IDefinition definition, String newName)
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
        Map<String, List<TextEdit>> changes = new HashMap<>();
        result.setChanges(changes);
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
        for (ICompilationUnit compilationUnit : compilationUnits)
        {
            if (compilationUnit == null || compilationUnit instanceof SWCCompilationUnit)
            {
                continue;
            }
            ArrayList<TextEdit> textEdits = new ArrayList<>();
            if (compilationUnit.getAbsoluteFilename().endsWith(MXML_EXTENSION))
            {
                IMXMLDataManager mxmlDataManager = currentWorkspace.getMXMLDataManager();
                MXMLData mxmlData = (MXMLData) mxmlDataManager.get(fileSpecGetter.getFileSpecification(compilationUnit.getAbsoluteFilename()));
                IMXMLTagData rootTag = mxmlData.getRootTag();
                if (rootTag != null)
                {
                    ArrayList<ISourceLocation> units = new ArrayList<>();
                    findMXMLUnits(mxmlData.getRootTag(), definition, units);
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
            IASNode ast = null;
            try
            {
                ast = compilationUnit.getSyntaxTreeRequest().get().getAST();
            }
            catch (Exception e)
            {
                //safe to ignore
            }
            if (ast != null)
            {
                ArrayList<IIdentifierNode> identifiers = new ArrayList<>();
                findIdentifiers(ast, definition, identifiers);
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

            URI uri = Paths.get(compilationUnit.getAbsoluteFilename()).toUri();
            if (definitionIsMainDefinitionInCompilationUnit(compilationUnit, definition))
            {
                originalDefinitionFilePath = Paths.get(compilationUnit.getAbsoluteFilename());
                String newBaseName = newName + "." + Files.getFileExtension(originalDefinitionFilePath.toFile().getName());
                newDefinitionFilePath = originalDefinitionFilePath.getParent().resolve(newBaseName);
                uri = newDefinitionFilePath.toUri();
            }
            changes.put(uri.toString(), textEdits);
        }
        if (newDefinitionFilePath != null)
        {
            //wait to actually rename the file because we need to be sure
            //that finding the identifiers above still works with the old name
            try
            {
                java.nio.file.Files.move(originalDefinitionFilePath, newDefinitionFilePath, StandardCopyOption.ATOMIC_MOVE);
            }
            catch(IOException e)
            {
                System.err.println("could not move file for rename: " + newDefinitionFilePath.toUri().toString());
            }
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

    private void findIdentifiers(IASNode node, IDefinition definition, List<IIdentifierNode> result)
    {
        if (node.isTerminal())
        {
            if (node instanceof IIdentifierNode)
            {
                IIdentifierNode identifierNode = (IIdentifierNode) node;
                IDefinition resolvedDefinition = identifierNode.resolve(currentProject);
                if (resolvedDefinition == definition)
                {
                    result.add(identifierNode);
                }
                else if (resolvedDefinition instanceof IClassDefinition
                    && definition instanceof IFunctionDefinition
                    && ((IFunctionDefinition) definition).isConstructor())
                {
                    //if renaming the constructor, also rename the class
                    IClassDefinition classDefinition = (IClassDefinition) resolvedDefinition;
                    IFunctionDefinition constructorDefinition = classDefinition.getConstructor();
                    if (constructorDefinition != null && definition == constructorDefinition)
                    {
                        result.add(identifierNode);
                    }
                }
                else if (resolvedDefinition instanceof IFunctionDefinition
                    && ((IFunctionDefinition) resolvedDefinition).isConstructor()
                    && definition instanceof IClassDefinition)
                {
                    //if renaming the class, also rename the constructor
                    IClassDefinition classDefinition = (IClassDefinition) definition;
                    IFunctionDefinition constructorDefinition = classDefinition.getConstructor();
                    if (constructorDefinition != null && resolvedDefinition == constructorDefinition)
                    {
                        result.add(identifierNode);
                    }
                }
                else if (resolvedDefinition instanceof ISetterDefinition
                        && definition instanceof IGetterDefinition)
                {
                    //if renaming the getter, also rename the setter
                    IGetterDefinition getterDefinition = (IGetterDefinition) definition;
                    ISetterDefinition setterDefinition = getterDefinition.resolveSetter(currentProject);
                    if (setterDefinition != null && resolvedDefinition == setterDefinition)
                    {
                        result.add(identifierNode);
                    }
                }
                else if (resolvedDefinition instanceof IGetterDefinition
                        && definition instanceof ISetterDefinition)
                {
                    //if renaming the setter, also rename the getter
                    ISetterDefinition setterDefinition = (ISetterDefinition) definition;
                    IGetterDefinition getterDefinition = setterDefinition.resolveGetter(currentProject);
                    if (getterDefinition != null && resolvedDefinition == getterDefinition)
                    {
                        result.add(identifierNode);
                    }
                }
            }
            return;
        }
        for (int i = 0, count = node.getChildCount(); i < count; i++)
        {
            IASNode childNode = node.getChild(i);
            findIdentifiers(childNode, definition, result);
        }
    }

    private void findMXMLUnits(IMXMLTagData tagData, IDefinition definition, List<ISourceLocation> result)
    {
        IDefinition tagDefinition = currentProject.resolveXMLNameToDefinition(tagData.getXMLName(), tagData.getMXMLDialect());
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
                IDefinition attributeDefinition = currentProject.resolveSpecifier(classDefinition, attributeData.getShortName());
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
            findMXMLUnits(childTag, definition, result);
            childTag = childTag.getNextSibling(true);
        }
    }

    private String patch(String sourceText, TextDocumentContentChangeEvent change)
    {
        Range range = change.getRange();
        Position start = range.getStart();
        StringReader reader = new StringReader(sourceText);
        int offset = LanguageServerCompilerUtils.getOffsetFromPosition(reader, start);
        return sourceText.substring(0, offset) + change.getText() + sourceText.substring(offset + change.getRangeLength());
    }

    private void addCompilerProblem(ICompilerProblem problem, PublishDiagnosticsParams publish)
    {
        if (!flexLibSDKContainsFalconCompiler)
        {
            //the following errors get special treatment if the framework SDK's
            //compiler isn't Falcon

            if (problem.getClass().equals(FontEmbeddingNotSupported.class))
            {
                //ignore this error because the framework SDK can embed fonts
                return;
            }
        }
        Diagnostic diagnostic = new Diagnostic();

        DiagnosticSeverity severity = LanguageServerCompilerUtils.getDiagnosticSeverityFromCompilerProblem(problem);
        diagnostic.setSeverity(severity);

        Range range = LanguageServerCompilerUtils.getRangeFromSourceLocation(problem);
        if (range == null)
        {
            //fall back to an empty range
            range = new Range(new Position(), new Position());
        }
        diagnostic.setRange(range);

        diagnostic.setMessage(problem.toString());

        try
        {
            Field field = problem.getClass().getDeclaredField("errorCode");
            int errorCode = (int) field.get(problem);
            diagnostic.setCode(Integer.toString(errorCode));
        }
        catch (Exception e)
        {
            //skip it
        }

        List<Diagnostic> diagnostics = publish.getDiagnostics();
        diagnostics.add(diagnostic);
    }

    private void checkFilePathForSyntaxProblems(Path path)
    {
        URI uri = path.toUri();
        PublishDiagnosticsParams publish = new PublishDiagnosticsParams();
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        publish.setDiagnostics(diagnostics);
        publish.setUri(uri.toString());
        codeProblemTracker.trackFileWithProblems(uri);

        ASParser parser = null;
        Reader reader = getReaderForPath(path);
        if (reader != null)
        {
            StreamingASTokenizer tokenizer = StreamingASTokenizer.createForRepairingASTokenizer(reader, uri.toString(), null);
            ASToken[] tokens = tokenizer.getTokens(reader);
            if (tokenizer.hasTokenizationProblems())
            {
                for (ICompilerProblem problem : tokenizer.getTokenizationProblems())
                {
                    addCompilerProblem(problem, publish);
                }
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
                for (ICompilerProblem problem : parser.getSyntaxProblems())
                {
                    addCompilerProblem(problem, publish);
                }
            }
        }

        Diagnostic diagnostic = createDiagnosticWithoutRange();
        diagnostic.setSeverity(DiagnosticSeverity.Information);

        if (reader == null)
        {
            //the file does not exist
            diagnostic.setSeverity(DiagnosticSeverity.Error);
            diagnostic.setMessage("File not found: " + path.toAbsolutePath().toString() + ". Error checking disabled.");
        }
        else if (parser == null)
        {
            //something terrible happened, and this is the best we can do
            diagnostic.setSeverity(DiagnosticSeverity.Error);
            diagnostic.setMessage("A fatal error occurred while checking for simple syntax problems.");
        }
        else if (currentProjectOptions == null)
        {
            //something went wrong while attempting to load and parse the
            //project configuration.
            diagnostic.setMessage("Failed to load project configuration options. Error checking disabled, except for simple syntax problems.");
        }
        else
        {
            //we loaded and parsed the project configuration, so something went
            //wrong while checking for errors.
            diagnostic.setMessage("A fatal error occurred while checking for errors. Error checking disabled, except for simple syntax problems.");
        }

        diagnostics.add(diagnostic);

        codeProblemTracker.cleanUpStaleProblems();
        if (languageClient != null)
        {
            languageClient.publishDiagnostics(publish);
        }
    }

    private Diagnostic createDiagnosticWithoutRange()
    {
        Diagnostic diagnostic = new Diagnostic();
        Range range = new Range();
        range.setStart(new Position());
        range.setEnd(new Position());
        diagnostic.setRange(range);
        return diagnostic;
    }

    private ICompilationUnit findCompilationUnit(String absoluteFileName)
    {
        if (compilationUnits == null)
        {
            return null;
        }
        for (ICompilationUnit unit : compilationUnits)
        {
            //it's possible for the collection of compilation units to contain
            //null values, so be sure to check for null values before checking
            //the file name
            if (unit != null && unit.getAbsoluteFilename().equals(absoluteFileName))
            {
                return unit;
            }
        }
        return null;
    }

    private Path getMainCompilationUnitPath()
    {
        refreshProjectOptions();
        if (currentProjectOptions == null)
        {
            return null;
        }
        String[] files = currentProjectOptions.files;
        if (files == null || files.length == 0)
        {
            return null;
        }
        String lastFilePath = files[files.length - 1];
        return Paths.get(lastFilePath);
    }

    private IASNode getAST(Path path)
    {
        ICompilationUnit unit = getCompilationUnit(path);
        if (unit == null)
        {
            //no need to log this case because it can happen for reasons that
            //should have been logged already
            return null;
        }
        IASNode ast = null;
        try
        {
            ast = unit.getSyntaxTreeRequest().get().getAST();
        }
        catch (InterruptedException e)
        {
            System.err.println("Interrupted while getting AST: " + path.toAbsolutePath().toString());
            return null;
        }
        if (ast == null)
        {
            //we couldn't find the root node for this file
            System.err.println("Could not find AST: " + path.toAbsolutePath().toString());
            return null;
        }
        return ast;
    }

    private ICompilationUnit getCompilationUnit(Path path)
    {
        String absolutePath = path.toAbsolutePath().toString();
        currentUnit = null;
        currentProject = getProject();
        if (currentProject == null)
        {
            return null;
        }
        currentWorkspace = currentProject.getWorkspace();
        //we're going to start with the files passed into the compiler
        String[] files = currentProjectOptions.files;
        if (files != null)
        {
            for (int i = files.length - 1; i >= 0; i--)
            {
                String file = files[i];
                //a previous file may have created a compilation unit for the
                //current file, so use that, if available
                ICompilationUnit existingUnit = findCompilationUnit(file);
                if (existingUnit != null)
                {
                    if (file.equals(absolutePath))
                    {
                        currentUnit = existingUnit;
                    }
                    continue;
                }
                if (currentProject.getSourcePath().size() == 0
                        && i == (files.length - 1))
                {
                    //if the main file didn't exist at first, it's possible
                    //that the project won't have a source path yet. when the
                    //file is created later, the compilation unit can only be
                    //created successfully if we set the source path manually
                    File mainFile = new File(file);
                    ArrayList<File> sourcePaths = new ArrayList<>();
                    sourcePaths.add(mainFile.getParentFile());
                    currentProject.setSourcePath(sourcePaths);
                }
                IInvisibleCompilationUnit unit = currentProject.createInvisibleCompilationUnit(file, fileSpecGetter);
                if (unit == null)
                {
                    if (sourceByPath.containsKey(path) || (new File(absolutePath)).exists())
                    {
                        //only display an error if the compilation unit should definitely exist
                        System.err.println("Could not create compilation unit for file: " + file);
                    }
                    continue;
                }
                invisibleUnits.add(unit);
                if (file.equals(absolutePath))
                {
                    currentUnit = unit;
                }
            }
        }

        compilationUnits = currentProject.getCompilationUnits();

        //if we didn't find the unit already, search the complete set of units
        if (currentUnit == null)
        {
            //first, search the existing compilation units for the file because it
            //might already be created
            for (ICompilationUnit unit : compilationUnits)
            {
                if (unit != null && unit.getAbsoluteFilename().equals(absolutePath))
                {
                    currentUnit = unit;
                    break;
                }
            }
        }

        //if we still haven't found it, create it manually
        if (currentUnit == null)
        {
            //if all else fails, create the compilation unit manually
            IInvisibleCompilationUnit unit = currentProject.createInvisibleCompilationUnit(absolutePath, fileSpecGetter);
            if (unit == null)
            {
                if (sourceByPath.containsKey(path) || (new File(absolutePath)).exists())
                {
                    //only display an error if the compilation unit should definitely exist
                    System.err.println("Could not create compilation unit for file (final fallback): " + absolutePath);
                }
                return null;
            }
            invisibleUnits.add(unit);
            currentUnit = unit;
        }

        //for some reason, function nodes may not always be populated, but we
        //can force them to be populated
        IASNode ast = null;
        try
        {
            ast = currentUnit.getSyntaxTreeRequest().get().getAST();
        }
        catch (InterruptedException e)
        {
            System.err.println("Interrupted while getting AST");
        }
        if (ast instanceof FileNode)
        {
            FileNode fileNode = (FileNode) ast;
            fileNode.parseRequiredFunctionBodies();
        }
        else if (ast instanceof IFileNode)
        {
            IFileNode fileNode = (IFileNode) ast;
            //ideally, we'd use parseRequiredFunctionBodies(), but if we don't
            //necessarily know that it exists, this fallback is almost as good
            fileNode.populateFunctionNodes();
        }
        return currentUnit;
    }

    private void clearInvisibleCompilationUnits()
    {
        //invisible units may exist for new files that haven't been saved, so
        //they don't exist on the file system. the first compilation unit
        //created will be invisible too, at least to start out.
        //if needed, we'll recreate invisible compilation units later.
        for (IInvisibleCompilationUnit unit : invisibleUnits)
        {
            unit.remove();
        }
        invisibleUnits.clear();
    }

    private boolean isJSConfig(ProjectOptions projectOptions)
    {
        if(flexLibSDKIsFlexJS)
        {
            return true;
        }
        String config = projectOptions.config;
        if (config.equals(CONFIG_JS) || config.equals(CONFIG_NODE))
        {
            return true;
        }
        CompilerOptions compilerOptions = projectOptions.compilerOptions;
        if (compilerOptions.jsOutputType != null)
        {
            return true;
        }
        if (compilerOptions.targets != null
                && compilerOptions.targets.size() > 0)
        {
            return true;
        }
        return false;
    }

    private void appendPathCompilerOptions(String prefix, Collection<File> files, List<String> options)
    {
        for(File file : files)
        {
            String path = file.getAbsolutePath();
            if (path.indexOf(' ') != -1)
            {
                //wrap in quotes, if required
                path = "\"" + path + "\"";
            }
            options.add(prefix + file.getAbsolutePath());
        }
    }
    
    private void publishConfigurationProblems(Configurator configurator)
    {
        Collection<ICompilerProblem> problems = configurator.getConfigurationProblems();
        if (problems.size() > 0)
        {
            Map<URI, PublishDiagnosticsParams> filesMap = new HashMap<>();
            for (ICompilerProblem problem : problems)
            {
                URI uri = Paths.get(problem.getSourcePath()).toUri();
                configProblemTracker.trackFileWithProblems(uri);
                PublishDiagnosticsParams params = null;
                if (filesMap.containsKey(uri))
                {
                    params = filesMap.get(uri);
                }
                else
                {
                    params = new PublishDiagnosticsParams();
                    params.setUri(uri.toString());
                    params.setDiagnostics(new ArrayList<>());
                    filesMap.put(uri, params);
                }
                addCompilerProblem(problem, params);
            }
            if (languageClient != null)
            {
                filesMap.values().forEach(languageClient::publishDiagnostics);
            }
        }
    }

    private FlexProject getProject()
    {
        clearInvisibleCompilationUnits();
        refreshProjectOptions();
        if (currentProjectOptions == null)
        {
            cleanupCurrentProject();
            return null;
        }
        FlexProject project = null;
        if (currentProject == null)
        {
            //we're going to try to determine if we need a JS project or a SWF one
            IBackend backend = null;
            List<String> targets = currentProjectOptions.compilerOptions.targets;
            if (targets != null && targets.size() > 0)
            {
                //first, check if any targets are specified
                String firstTarget = targets.get(0);
                switch (MXMLJSC.JSTargetType.fromString(firstTarget))
                {
                    case SWF:
                    {
                        //no backend. fall back to FlexProject.
                        backend = null;
                        break;
                    }
                    case JS_NATIVE:
                    {
                        backend = new JSCBackend();
                        break;
                    }
                    case JS_NODE:
                    {
                        backend = new NodeBackend();
                        break;
                    }
                    case JS_NODE_MODULE:
                    {
                        backend = new NodeModuleBackend();
                        break;
                    }
                    default:
                    {
                        //it actually shouldn't matter too much which JS
                        //backend is used. we just want to rule out SWF.
                        backend = new FlexJSBackend();
                        break;
                    }
                }
            }
            //if no targets are specified, we can guess JS from some configs
            else if (currentProjectOptions.config.equals(CONFIG_JS))
            {
                backend = new JSCBackend();
            }
            else if (currentProjectOptions.config.equals(CONFIG_NODE))
            {
                backend = new NodeBackend();
            }
            if (backend != null)
            {
                //if we created a backend, it's a JS project
                project = new FlexJSProject(new Workspace(), backend);
            }
            if (project == null)
            {
                //if we haven't created the project yet, we default to SWF
                project = new FlexProject(new Workspace());
            }
            project.setProblems(new ArrayList<>());
            currentWorkspace = project.getWorkspace();
            currentWorkspace.setASDocDelegate(new VSCodeASDocDelegate());
            fileSpecGetter = new LanguageServerFileSpecGetter(currentWorkspace, sourceByPath);
        }
        else
        {
            //clear all old problems because they won't be cleared automatically
            currentProject.getProblems().clear();
            return currentProject;
        }
        CompilerOptions compilerOptions = currentProjectOptions.compilerOptions;
        Configurator configurator = null;
        if (isJSConfig(currentProjectOptions))
        {
            configurator = new Configurator(JSGoogConfiguration.class);
        }
        else //swf only
        {
            configurator = new Configurator(VSCodeConfiguration.class);
        }
        configurator.setToken(TOKEN_CONFIGNAME, currentProjectOptions.config);
        ProjectType type = currentProjectOptions.type;
        String[] files = currentProjectOptions.files;
        String additionalOptions = currentProjectOptions.additionalOptions;
        ArrayList<String> combinedOptions = new ArrayList<>();
        if (compilerOptions.swfExternalLibraryPath != null)
        {
            //this isn't available in the configurator, so add it like the additionalOptions
            appendPathCompilerOptions("--swf-external-library-path+=", compilerOptions.swfExternalLibraryPath, combinedOptions);
        }
        if (compilerOptions.swfLibraryPath != null)
        {
            appendPathCompilerOptions("--swf-library-path+=", compilerOptions.swfLibraryPath, combinedOptions);
        }
        if (compilerOptions.jsExternalLibraryPath != null)
        {
            appendPathCompilerOptions("--js-external-library-path+=", compilerOptions.jsExternalLibraryPath, combinedOptions);
        }
        if (compilerOptions.jsLibraryPath != null)
        {
            appendPathCompilerOptions("--js-library-path+=", compilerOptions.jsLibraryPath, combinedOptions);
        }
        if (compilerOptions.swfVersion != -1)
        {
            combinedOptions.add("--swf-version=" + compilerOptions.swfVersion);
        }
        if (compilerOptions.targetPlayer != null)
        {
            combinedOptions.add("--target-player=" + compilerOptions.targetPlayer);
        }
        if (additionalOptions != null)
        {
            //split the additionalOptions into separate values so that we can
            //pass them in as String[], as the compiler expects.
            Matcher matcher = additionalOptionsPattern.matcher(additionalOptions);
            while (matcher.find())
            {
                String option = matcher.group();
                combinedOptions.add(option);
            }
        }
        //not all framework SDKs support a theme (such as Adobe's AIR SDK), so
        //we clear it for the editor to avoid a missing spark.css file.
        combinedOptions.add("-theme=");
        if (type.equals(ProjectType.LIB))
        {
            configurator.setConfiguration(combinedOptions.toArray(new String[combinedOptions.size()]),
                    ICompilerSettingsConstants.INCLUDE_CLASSES_VAR, false);
        }
        else // app
        {
            combinedOptions.addAll(Arrays.asList(files));
            configurator.setConfiguration(combinedOptions.toArray(new String[combinedOptions.size()]),
                    ICompilerSettingsConstants.FILE_SPECS_VAR);
        }
        //this needs to be set before applyToProject() so that it's in the
        //configuration buffer before addExternalLibraryPath() is called
        configurator.setExcludeNativeJSLibraries(false);
        Path appendConfigPath = Paths.get(System.getProperty(FLEXLIB));
        appendConfigPath = appendConfigPath.resolve("../ide/vscode-nextgenas/vscode-nextgenas-config.xml");
        File appendConfigFile = appendConfigPath.toFile();
        if (appendConfigFile.exists())
        {
            configurator.addConfiguration(appendConfigFile);
        }
        boolean result = configurator.applyToProject(project);
        publishConfigurationProblems(configurator);
        configProblemTracker.cleanUpStaleProblems();
        if (!result)
        {
            return null;
        }
        //set things after the first applyToProject() so that cfgbuf is not null
        //because setting some values checks the cfgbuf
        if (compilerOptions.sourcePath != null)
        {
            configurator.addSourcePath(compilerOptions.sourcePath);
        }
        if (compilerOptions.libraryPath != null)
        {
            configurator.addLibraryPath(compilerOptions.libraryPath);
        }
        if (compilerOptions.externalLibraryPath != null)
        {
            configurator.addExternalLibraryPath(compilerOptions.externalLibraryPath);
        }
        if (compilerOptions.namespaceMappings != null)
        {
            configurator.setNamespaceMappings(compilerOptions.namespaceMappings);
        }
        if (compilerOptions.defines != null)
        {
            configurator.setDefineDirectives(compilerOptions.defines);
        }
        if (currentProjectOptions.type.equals(ProjectType.LIB))
        {
            if (compilerOptions.includeClasses != null)
            {
                configurator.setIncludeClasses(compilerOptions.includeClasses);
            }
            if (compilerOptions.includeNamespaces != null)
            {
                configurator.setIncludeNamespaces(compilerOptions.includeNamespaces);
            }
            if (compilerOptions.includeSources != null)
            {
                configurator.setIncludeSources(compilerOptions.includeSources);
            }
        }
        configurator.enableDebugging(compilerOptions.debug, null);
        configurator.showActionScriptWarnings(compilerOptions.warnings);
        result = configurator.applyToProject(project);
        publishConfigurationProblems(configurator);
        configProblemTracker.cleanUpStaleProblems();
        if (!result)
        {
            return null;
        }
        ITarget.TargetType targetType = ITarget.TargetType.SWF;
        if (currentProjectOptions.type.equals(ProjectType.LIB))
        {
            targetType = ITarget.TargetType.SWC;
        }
        ITargetSettings targetSettings = configurator.getTargetSettings(targetType);
        if (targetSettings == null)
        {
            System.err.println("Failed to get compile settings for +configname=" + currentProjectOptions.config + ".");
            return null;
        }
        project.setTargetSettings(targetSettings);
        return project;
    }

    private void checkProjectForProblems()
    {
        refreshProjectOptions();
        if (currentProjectOptions != null && currentProjectOptions.type.equals(ProjectType.LIB))
        {
            Set<Path> filePaths = sourceByPath.keySet();
            if (filePaths.size() > 0)
            {
                //it doesn't matter which file we pick here because we're
                //doing a full build
                Path path = filePaths.iterator().next();
                checkFilePathForProblems(path, false);
            }
        }
        else //app
        {
            Path path = getMainCompilationUnitPath();
            if (path != null)
            {
                checkFilePathForProblems(path, false);
            }
        }
    }

    private void checkFilePathForProblems(Path path, Boolean quick)
    {
        currentUnit = null;
        if (!isInWorkspaceOrSourcePath(path))
        {
            return;
        }
        if (!checkFilePathForAllProblems(path, quick))
        {
            checkFilePathForSyntaxProblems(path);
        }
    }

    private boolean checkFilePathForAllProblems(Path path, Boolean quick)
    {
        ICompilationUnit mainUnit = getCompilationUnit(path);
        if (mainUnit == null)
        {
            return false;
        }
        CompilerProject project = (CompilerProject) mainUnit.getProject();
        Collection<ICompilerProblem> fatalProblems = project.getFatalProblems();
        if (fatalProblems == null || fatalProblems.size() == 0)
        {
            fatalProblems = project.getProblems();
        }
        if (fatalProblems != null && fatalProblems.size() > 0)
        {
            URI uri = path.toUri();
            PublishDiagnosticsParams publish = new PublishDiagnosticsParams();
            publish.setDiagnostics(new ArrayList<>());
            publish.setUri(uri.toString());
            codeProblemTracker.trackFileWithProblems(uri);
            for (ICompilerProblem problem : fatalProblems)
            {
                addCompilerProblem(problem, publish);
            }
            codeProblemTracker.cleanUpStaleProblems();
            if (languageClient != null)
            {
                languageClient.publishDiagnostics(publish);
            }
            return true;
        }
        IASNode ast = null;
        try
        {
            ast = mainUnit.getSyntaxTreeRequest().get().getAST();
        }
        catch (Exception e)
        {
            System.err.println("Exception during build: " + e);
            return false;
        }
        if (ast == null)
        {
            return false;
        }
        Map<URI, PublishDiagnosticsParams> files = new HashMap<>();
        try
        {
            if (quick)
            {
                PublishDiagnosticsParams params = checkCompilationUnitForAllProblems(mainUnit);
                URI uri = Paths.get(mainUnit.getAbsoluteFilename()).toUri();
                files.put(uri, params);
            }
            else
            {
                boolean continueCheckingForErrors = true;
                while (continueCheckingForErrors)
                {
                    try
                    {
                        for (ICompilationUnit unit : compilationUnits)
                        {
                            if (unit == null || unit instanceof SWCCompilationUnit)
                            {
                                //compiled compilation units won't have problems
                                continue;
                            }
                            PublishDiagnosticsParams params = checkCompilationUnitForAllProblems(unit);
                            URI uri = Paths.get(unit.getAbsoluteFilename()).toUri();
                            files.put(uri, params);
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
                //only clean up stale errors on a full check
                codeProblemTracker.cleanUpStaleProblems();
            }
        }
        catch (Exception e)
        {
            System.err.println("Exception during build: " + e);
            e.printStackTrace(System.err);
            return false;
        }
        if (languageClient != null)
        {
            files.values().forEach(languageClient::publishDiagnostics);
        }
        return true;
    }

    private PublishDiagnosticsParams checkCompilationUnitForAllProblems(ICompilationUnit unit)
    {
        URI uri = Paths.get(unit.getAbsoluteFilename()).toUri();
        PublishDiagnosticsParams publish = new PublishDiagnosticsParams();
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        publish.setDiagnostics(diagnostics);
        publish.setUri(uri.toString());
        codeProblemTracker.trackFileWithProblems(uri);
        ArrayList<ICompilerProblem> problems = new ArrayList<>();
        try
        {
            //if we pass in null, it's designed to ignore certain errors that
            //don't matter for IDE code intelligence.
            unit.waitForBuildFinish(problems, null);
            for (ICompilerProblem problem : problems)
            {
                addCompilerProblem(problem, publish);
            }
        }
        catch (Exception e)
        {
            System.err.println("Exception during waitForBuildFinish(): " + e);
            e.printStackTrace(System.err);

            Diagnostic diagnostic = createDiagnosticWithoutRange();
            diagnostic.setSeverity(DiagnosticSeverity.Error);
            diagnostic.setMessage("A fatal error occurred while checking a file for problems: " + unit.getAbsoluteFilename());
            diagnostics.add(diagnostic);
        }

        Path unitPath = Paths.get(uri);
        if (sourceByPath.containsKey(unitPath) && codeActionsByPath.containsKey(unitPath))
        {
            IASNode ast = null;
            try
            {
                ast = unit.getSyntaxTreeRequest().get().getAST();
            }
            catch (Exception e)
            {
                //do nothing
            }
            if (ast != null)
            {
                List<SavedCodeAction> codeActions = codeActionsByPath.get(unitPath);
                codeActions.clear();
                saveCodeActionsForLater(ast, unitPath, codeActions);
            }
        }

        return publish;
    }

    private void saveCodeActionsForLater(IASNode node, Path path, List<SavedCodeAction> codeActions)
    {
        if (node instanceof IVariableNode)
        {
            IVariableNode variableNode = (IVariableNode) node;
            IExpressionNode expressionNode = variableNode.getNameExpressionNode();
            IDefinition definition = expressionNode.resolve(currentProject);
            if (definition instanceof IVariableDefinition
                && !(definition instanceof IConstantDefinition)
                && !(definition instanceof IAccessorDefinition))
            {
                //we want variables, but not constants or accessors
                IVariableDefinition variableDefinition = (IVariableDefinition) definition;
                if (variableDefinition.getVariableClassification().equals(VariableClassification.CLASS_MEMBER))
                {
                    createCommandsForGenerateGetterAndSetter(variableNode, path, codeActions);
                }
            }
        }
        if (node instanceof IFunctionNode)
        {
            //a member can't be the child of a function, so no need to continue
            return;
        }
        for (int i = 0, childCount = node.getChildCount(); i < childCount; i++)
        {
            IASNode child = node.getChild(i);
            saveCodeActionsForLater(child, path, codeActions);
        }
    }

    private void createCommandsForGenerateGetterAndSetter(IVariableNode variableNode, Path path, List<SavedCodeAction> codeActions)
    {
        Range range = LanguageServerCompilerUtils.getRangeFromSourceLocation(variableNode);
        IExpressionNode assignedValueNode = variableNode.getAssignedValueNode();
        String assignedValue = null;
        if (assignedValueNode != null)
        {
            String source = sourceByPath.get(path);
            assignedValue = source.substring(assignedValueNode.getAbsoluteStart(),
                assignedValueNode.getAbsoluteEnd());
        }
        Command generateGetterAndSetterCommand = new Command();
        generateGetterAndSetterCommand.setTitle("Generate Getter and Setter");
        generateGetterAndSetterCommand.setCommand(ICommandConstants.GENERATE_GETTER_AND_SETTER);
        generateGetterAndSetterCommand.setArguments(Arrays.asList(
            path.toUri().toString(),
            range.getStart().getLine(),
            range.getStart().getCharacter(),
            range.getEnd().getLine(),
            range.getEnd().getCharacter(),
            variableNode.getName(),
            variableNode.getNamespace(),
            variableNode.hasModifier(ASModifier.STATIC),
            variableNode.getVariableType(),
            assignedValue
        ));
        SavedCodeAction getterSetterCodeAction = new SavedCodeAction(generateGetterAndSetterCommand, range);
        codeActions.add(getterSetterCodeAction);
        
        Command generateGetterCommand = new Command();
        generateGetterCommand.setTitle("Generate Getter");
        generateGetterCommand.setCommand(ICommandConstants.GENERATE_GETTER);
        generateGetterCommand.setArguments(Arrays.asList(
            path.toUri().toString(),
            range.getStart().getLine(),
            range.getStart().getCharacter(),
            range.getEnd().getLine(),
            range.getEnd().getCharacter(),
            variableNode.getName(),
            variableNode.getNamespace(),
            variableNode.hasModifier(ASModifier.STATIC),
            variableNode.getVariableType(),
            assignedValue
        ));
        SavedCodeAction getterCodeAction = new SavedCodeAction(generateGetterCommand, range);
        codeActions.add(getterCodeAction);

        Command generateSetterCommand = new Command();
        generateSetterCommand.setTitle("Generate Setter");
        generateSetterCommand.setCommand(ICommandConstants.GENERATE_SETTER);
        generateSetterCommand.setArguments(Arrays.asList(
            path.toUri().toString(),
            range.getStart().getLine(),
            range.getStart().getCharacter(),
            range.getEnd().getLine(),
            range.getEnd().getCharacter(),
            variableNode.getName(),
            variableNode.getNamespace(),
            variableNode.hasModifier(ASModifier.STATIC),
            variableNode.getVariableType(),
            assignedValue
        ));
        SavedCodeAction setterCodeAction = new SavedCodeAction(generateSetterCommand, range);
        codeActions.add(setterCodeAction);
    }

    private Reader getReaderForPath(Path path)
    {
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

    private boolean isMXMLTagValidForCompletion(IMXMLTagData tag)
    {
        if (tag.getXMLName().equals(tag.getMXMLDialect().resolveScript()))
        {
            //inside an <fx:Script> tag
            return false;
        }
        return true;
    }

    private IMXMLTagData getOffsetMXMLTag(TextDocumentPositionParams position)
    {
        return getOffsetMXMLTag(position.getTextDocument(), position.getPosition());
    }

    private IMXMLTagData getOffsetMXMLTag(TextDocumentIdentifier textDocument, Position position)
    {
        namespaceStartIndex = -1;
        namespaceEndIndex = -1;
        String uri = textDocument.getUri();
        if (!uri.endsWith(MXML_EXTENSION))
        {
            // don't try to parse ActionScript files as MXML
            return null;
        }
        currentOffset = -1;
        importRange.uri = null;
        importRange.startIndex = -1;
        importRange.endIndex = -1;
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(uri);
        if (path == null)
        {
            return null;
        }
        if (!isInWorkspaceOrSourcePath(path))
        {
            //the path must be in the workspace or source-path
            return null;
        }
        String code;
        if (sourceByPath.containsKey(path))
        {
            code = sourceByPath.get(path);
        }
        else
        {
            System.err.println("Could not find source " + path.toAbsolutePath().toString());
            System.err.println(sourceByPath.keySet().size());
            return null;
        }

        //need to ensure that the compilation unit exists, even though we don't
        //use it directly
        ICompilationUnit unit = getCompilationUnit(path);
        if (unit == null)
        {
            //no need to log this case because it can happen for reasons that
            //should have been logged already
            return null;
        }
        IMXMLDataManager mxmlDataManager = currentWorkspace.getMXMLDataManager();
        MXMLData mxmlData = (MXMLData) mxmlDataManager.get(fileSpecGetter.getFileSpecification(path.toAbsolutePath().toString()));
        if (mxmlData == null)
        {
            return null;
        }

        currentOffset = LanguageServerCompilerUtils.getOffsetFromPosition(new StringReader(code), position);
        if (currentOffset == -1)
        {
            System.err.println("Could not find code at position " + position.getLine() + ":" + position.getCharacter() + " in file " + path.toAbsolutePath().toString());
            return null;
        }

        //calculate the location for automatically generated xmlns tags
        IMXMLTagData rootTag = mxmlData.getRootTag();
        if (rootTag == null)
        {
            return null;
        }
        IMXMLTagAttributeData[] attributeDatas = rootTag.getAttributeDatas();
        for (IMXMLTagAttributeData attributeData : attributeDatas)
        {
            if (!attributeData.getName().startsWith("xmlns"))
            {
                if (namespaceStartIndex == -1)
                {
                    namespaceStartIndex = attributeData.getStart();
                    namespaceEndIndex = namespaceStartIndex;
                }
                break;
            }
            int start = attributeData.getAbsoluteStart();
            int end = attributeData.getValueEnd() + 1;
            if (namespaceStartIndex == -1 || namespaceStartIndex > start)
            {
                namespaceStartIndex = start;
            }
            if (namespaceEndIndex == -1 || namespaceEndIndex < end)
            {
                namespaceEndIndex = end;
            }
        }
        namespaceUri = textDocument.getUri();

        IMXMLUnitData unitData = mxmlData.findContainmentReferenceUnit(currentOffset);
        IMXMLUnitData currentUnitData = unitData;
        while (currentUnitData != null)
        {
            if (currentUnitData instanceof IMXMLTagData)
            {
                IMXMLTagData tagData = (IMXMLTagData) currentUnitData;
                if (tagData.getXMLName().equals(tagData.getMXMLDialect().resolveScript()) &&
                        unitData instanceof IMXMLTextData)
                {
                    IMXMLTextData textUnitData = (IMXMLTextData) unitData;
                    if (textUnitData.getTextType() == IMXMLTextData.TextType.CDATA)
                    {
                        importRange.uri = Paths.get(textUnitData.getSourcePath()).toUri().toString();
                        importRange.startIndex = textUnitData.getCompilableTextStart();
                        importRange.endIndex = textUnitData.getCompilableTextEnd();
                    }
                }
                return tagData;
            }
            currentUnitData = currentUnitData.getParentUnitData();
        }
        return null;
    }

    private IASNode getOffsetNode(TextDocumentPositionParams position)
    {
        return getOffsetNode(position.getTextDocument(), position.getPosition());
    }

    private IASNode getOffsetNode(TextDocumentIdentifier textDocument, Position position)
    {
        currentOffset = -1;
        if (!textDocument.getUri().endsWith(MXML_EXTENSION))
        {
            //if we're in an <fx:Script> element, these will have been
            //previously calculated, so don't clear them
            importRange.uri = null;
            importRange.startIndex = -1;
            importRange.endIndex = -1;
        }
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
        if (path == null)
        {
            return null;
        }
        if (!isInWorkspaceOrSourcePath(path))
        {
            //the path must be in the workspace or source-path
            return null;
        }
        String code;
        if (sourceByPath.containsKey(path))
        {
            code = sourceByPath.get(path);
        }
        else
        {
            System.err.println("Could not find source " + path.toAbsolutePath().toString());
            System.err.println(sourceByPath.keySet().size());
            return null;
        }

        IASNode ast = getAST(path);
        if (ast == null)
        {
            return null;
        }

        currentOffset = LanguageServerCompilerUtils.getOffsetFromPosition(new StringReader(code), position);
        if (currentOffset == -1)
        {
            System.err.println("Could not find code at position " + position.getLine() + ":" + position.getCharacter() + " in file " + path.toAbsolutePath().toString());
            return null;
        }
        IASNode offsetNode = getContainingNodeIncludingStart(ast, currentOffset);
        if (!textDocument.getUri().endsWith(MXML_EXTENSION))
        {
            importRange = getImportRange(offsetNode);
        }
        return offsetNode;
    }
    
    private class ImportRange
    {
        public String uri = null;
        public int startIndex = -1;
        public int endIndex = -1;
    }
    
    private ImportRange getImportRange(IASNode offsetNode)
    {
        ImportRange range = new ImportRange();
        if (offsetNode != null)
        {
            //if we have an offset node, try to find where imports may be added
            IPackageNode packageNode = (IPackageNode) offsetNode.getAncestorOfType(IPackageNode.class);
            if (packageNode == null)
            {
                IFileNode fileNode = (IFileNode) offsetNode.getAncestorOfType(IFileNode.class);
                if (fileNode != null)
                {
                    boolean foundPackage = false;
                    for (int i = 0; i < fileNode.getChildCount(); i++)
                    {
                        IASNode childNode = fileNode.getChild(i);
                        if (foundPackage)
                        {
                            //this is the node following the package
                            range.startIndex = childNode.getAbsoluteStart();
                            break;
                        }
                        if (childNode instanceof IPackageNode)
                        {
                            //use the start of the the next node after the
                            //package as the place where the import can be added
                            foundPackage = true;
                        }
                    }
                }
            }
            else
            {
                range.endIndex = packageNode.getAbsoluteEnd();
            }
            range.uri = Paths.get(offsetNode.getSourcePath()).toUri().toString();
        }
        return range;
    }

    private boolean containsWithStart(IASNode node, int offset)
    {
        return offset >= node.getAbsoluteStart() && offset <= node.getAbsoluteEnd();
    }

    private IASNode getContainingNodeIncludingStart(IASNode node, int offset)
    {
        if (!containsWithStart(node, offset))
        {
            return null;
        }
        for (int i = 0, count = node.getChildCount(); i < count; i++)
        {
            IASNode child = node.getChild(i);
            IASNode result = getContainingNodeIncludingStart(child, offset);
            if (result != null)
            {
                return result;
            }
        }
        return node;
    }

    private boolean isInsideTagPrefix(IMXMLTagData tag, int offset)
    {
        //next, check that we're after the prefix
        //one extra for bracket
        int maxOffset = tag.getAbsoluteStart() + 1;
        String prefix = tag.getPrefix();
        int prefixLength = prefix.length();
        if (prefixLength > 0)
        {
            //one extra for colon
            maxOffset += prefixLength + 1;
        }
        return offset > tag.getAbsoluteStart() && offset < maxOffset;
    }

    private IDefinition getDefinitionForMXMLNameAtOffset(IMXMLTagData tag, int offset)
    {
        if (tag.isOffsetInAttributeList(offset))
        {
            return getDefinitionForMXMLTagAttribute(tag, offset, false);
        }
        return getDefinitionForMXMLTag(tag);
    }

    private IMXMLTagAttributeData getMXMLTagAttributeAtOffset(IMXMLTagData tag, int offset)
    {
        IMXMLTagAttributeData[] attributes = tag.getAttributeDatas();
        for (IMXMLTagAttributeData attributeData : attributes)
        {
            if (offset >= attributeData.getAbsoluteStart()
                    && offset <= attributeData.getValueEnd())
            {
                return attributeData;
            }
        }
        return null;
    }

    private IMXMLTagAttributeData getMXMLTagAttributeWithNameAtOffset(IMXMLTagData tag, int offset, boolean includeEnd)
    {
        IMXMLTagAttributeData[] attributes = tag.getAttributeDatas();
        for (IMXMLTagAttributeData attributeData : attributes)
        {
            if (offset >= attributeData.getAbsoluteStart())
            {
                if(includeEnd && offset <= attributeData.getAbsoluteEnd())
                {
                    return attributeData;
                }
                else if(offset < attributeData.getAbsoluteEnd())
                {
                    return attributeData;
                }
            }
        }
        return null;
    }

    private IASNode getEmbeddedActionScriptNodeInMXMLTag(IMXMLTagData tag, int offset, TextDocumentPositionParams position)
    {
        IMXMLTagAttributeData attributeData = getMXMLTagAttributeWithValueAtOffset(tag, currentOffset);
        if (attributeData != null)
        {
            //some attributes can have ActionScript completion, such as
            //events and properties with data binding
            IClassDefinition tagDefinition = (IClassDefinition) currentProject.resolveXMLNameToDefinition(tag.getXMLName(), tag.getMXMLDialect());
            if (tagDefinition == null)
            {
                //we can't figure out which class the tag represents!
                //maybe the user hasn't defined the tag's namespace or something
                return null;
            }
            IDefinition attributeDefinition = currentProject.resolveSpecifier(tagDefinition, attributeData.getShortName());
            if (attributeDefinition instanceof IEventDefinition)
            {
                IMXMLClassReferenceNode mxmlNode = (IMXMLClassReferenceNode) getOffsetNode(position);
                IMXMLEventSpecifierNode eventNode = mxmlNode.getEventSpecifierNode(attributeData.getShortName());
                for (IASNode asNode : eventNode.getASNodes())
                {
                    IASNode containingNode = getContainingNodeIncludingStart(asNode, currentOffset);
                    if (containingNode != null)
                    {
                        return containingNode;
                    }
                }
                return eventNode;
            }
            else
            {
                IASNode offsetNode = getOffsetNode(position);
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
                                //IMXMLSingleDataBindingNode dataBinding = (IMXMLSingleDataBindingNode) propertyChild;
                                //IASNode containingNode = dataBinding.getExpressionNode().getContainingNode(currentOffset);
                                //we found the correct expression, but due to a bug
                                //in the compiler its line and column positions
                                //will be wrong. the resulting completion is too
                                //quirky, so this feature will be completed later.
                                //return containingNode;
                            }
                        }
                    }
                }
                //nothing possible for this attribute
            }
        }
        return null;
    }

    private IASNode getEmbeddedActionScriptNodeInMXMLTag(IMXMLTagData tag, int offset, TextDocumentIdentifier textDocument, Position position)
    {
        TextDocumentPositionParams textDocumentPosition = new TextDocumentPositionParams();
        textDocumentPosition.setTextDocument(textDocument);
        textDocumentPosition.setPosition(position);
        return getEmbeddedActionScriptNodeInMXMLTag(tag, offset, textDocumentPosition);
    }

    private IMXMLTagAttributeData getMXMLTagAttributeWithValueAtOffset(IMXMLTagData tag, int offset)
    {
        IMXMLTagAttributeData[] attributes = tag.getAttributeDatas();
        for (IMXMLTagAttributeData attributeData : attributes)
        {
            if (offset >= attributeData.getValueStart()
                    && offset <= attributeData.getValueEnd())
            {
                return attributeData;
            }
        }
        return null;
    }

    private IDefinition getDefinitionForMXMLTagAttribute(IMXMLTagData tag, int offset, boolean includeValue)
    {
        IMXMLTagAttributeData attributeData = null;
        if (includeValue)
        {
            attributeData = getMXMLTagAttributeAtOffset(tag, offset);
        }
        else
        {
            attributeData = getMXMLTagAttributeWithNameAtOffset(tag, offset, false);
        }
        if (attributeData == null)
        {
            return null;
        }
        IDefinition tagDefinition = getDefinitionForMXMLTag(tag);
        if (tagDefinition != null
                && tagDefinition instanceof IClassDefinition)
        {
            IClassDefinition classDefinition = (IClassDefinition) tagDefinition;
            return currentProject.resolveSpecifier(classDefinition, attributeData.getShortName());
        }
        return null;
    }

    private IDefinition getDefinitionForMXMLTag(IMXMLTagData tag)
    {
        if (tag == null)
        {
            return null;
        }

        IDefinition offsetDefinition = currentProject.resolveXMLNameToDefinition(tag.getXMLName(), tag.getMXMLDialect());
        if (offsetDefinition != null)
        {
            return offsetDefinition;
        }
        if (tag.getXMLName().getXMLNamespace().equals(tag.getMXMLDialect().getLanguageNamespace()))
        {
            for (String typeName : LANGUAGE_TYPE_NAMES)
            {
                if (tag.getShortName().equals(typeName))
                {
                    return currentProject.resolveQNameToDefinition(typeName);
                }
            }
        }
        IMXMLTagData parentTag = tag.getParentTag();
        if (parentTag == null)
        {
            return null;
        }
        IDefinition parentDefinition = currentProject.resolveXMLNameToDefinition(parentTag.getXMLName(), parentTag.getMXMLDialect());
        if (parentDefinition == null || !(parentDefinition instanceof IClassDefinition))
        {
            return null;
        }
        IClassDefinition classDefinition = (IClassDefinition) parentDefinition;
        return currentProject.resolveSpecifier(classDefinition, tag.getShortName());
    }

    private void appendInterfaceNamesToDetail(StringBuilder detailBuilder, IInterfaceDefinition[] interfaceDefinitions)
    {
        for (int i = 0, count = interfaceDefinitions.length; i < count; i++)
        {
            if (i > 0)
            {
                detailBuilder.append(", ");
            }
            IInterfaceDefinition baseInterface = interfaceDefinitions[i];
            detailBuilder.append(baseInterface.getBaseName());
        }
    }

    private String getDefinitionDetail(IDefinition definition)
    {
        StringBuilder detailBuilder = new StringBuilder();
        if (definition instanceof IClassDefinition)
        {
            IClassDefinition classDefinition = (IClassDefinition) definition;
            if (classDefinition.isDynamic())
            {
                detailBuilder.append(IASKeywordConstants.DYNAMIC);
                detailBuilder.append(" ");
            }
            detailBuilder.append(IASKeywordConstants.CLASS);
            detailBuilder.append(" ");
            if (classDefinition.getPackageName().startsWith(UNDERSCORE_UNDERSCORE_AS3_PACKAGE))
            {
                //classes like __AS3__.vec.Vector should not include the
                //package name
                detailBuilder.append(classDefinition.getBaseName());
            }
            else
            {
                detailBuilder.append(classDefinition.getQualifiedName());
            }
            IClassDefinition baseClassDefinition = classDefinition.resolveBaseClass(currentProject);
            if (baseClassDefinition != null && !baseClassDefinition.getQualifiedName().equals(IASLanguageConstants.Object))
            {
                detailBuilder.append(" ");
                detailBuilder.append(IASKeywordConstants.EXTENDS);
                detailBuilder.append(" ");
                detailBuilder.append(baseClassDefinition.getBaseName());
            }
            IInterfaceDefinition[] interfaceDefinitions = classDefinition.resolveImplementedInterfaces(currentProject);
            if (interfaceDefinitions.length > 0)
            {
                detailBuilder.append(" ");
                detailBuilder.append(IASKeywordConstants.IMPLEMENTS);
                detailBuilder.append(" ");
                appendInterfaceNamesToDetail(detailBuilder, interfaceDefinitions);
            }
        }
        else if (definition instanceof IInterfaceDefinition)
        {
            IInterfaceDefinition interfaceDefinition = (IInterfaceDefinition) definition;
            detailBuilder.append(IASKeywordConstants.INTERFACE);
            detailBuilder.append(" ");
            detailBuilder.append(interfaceDefinition.getQualifiedName());
            IInterfaceDefinition[] interfaceDefinitions = interfaceDefinition.resolveExtendedInterfaces(currentProject);
            if (interfaceDefinitions.length > 0)
            {
                detailBuilder.append(" ");
                detailBuilder.append(IASKeywordConstants.EXTENDS);
                detailBuilder.append(" ");
                appendInterfaceNamesToDetail(detailBuilder, interfaceDefinitions);
            }
        }
        else if (definition instanceof IVariableDefinition)
        {
            IVariableDefinition variableDefinition = (IVariableDefinition) definition;
            IDefinition parentDefinition = variableDefinition.getParent();
            if (parentDefinition instanceof ITypeDefinition)
            {
                //an IAccessorDefinition actually extends both
                //IVariableDefinition and IFunctionDefinition 
                if (variableDefinition instanceof IAccessorDefinition)
                {
                    detailBuilder.append("(property) ");
                }
                else if (variableDefinition instanceof IConstantDefinition)
                {
                    detailBuilder.append("(const) ");
                }
                else
                {
                    detailBuilder.append("(variable) ");
                }
                detailBuilder.append(parentDefinition.getQualifiedName());
                detailBuilder.append(".");
            }
            else if (parentDefinition instanceof IFunctionDefinition)
            {
                if (variableDefinition instanceof IParameterDefinition)
                {
                    detailBuilder.append("(parameter) ");
                }
                else
                {
                    detailBuilder.append("(local ");
                    if (variableDefinition instanceof IConstantDefinition)
                    {
                        detailBuilder.append("const) ");
                    }
                    else
                    {
                        detailBuilder.append("var) ");
                    }
                }
            }
            else
            {
                if (variableDefinition instanceof IConstantDefinition)
                {
                    detailBuilder.append(IASKeywordConstants.CONST);
                }
                else
                {
                    detailBuilder.append(IASKeywordConstants.VAR);
                }
                detailBuilder.append(" ");
            }
            detailBuilder.append(variableDefinition.getBaseName());
            detailBuilder.append(":");
            detailBuilder.append(variableDefinition.getTypeAsDisplayString());
        }
        else if (definition instanceof IFunctionDefinition)
        {
            IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
            IDefinition parentDefinition = functionDefinition.getParent();
            if (parentDefinition instanceof ITypeDefinition)
            {
                if (functionDefinition.isConstructor())
                {
                    detailBuilder.append("(constructor) ");
                }
                else
                {
                    detailBuilder.append("(method) ");
                }
                detailBuilder.append(parentDefinition.getQualifiedName());
                detailBuilder.append(".");
            }
            else if (parentDefinition instanceof IFunctionDefinition)
            {
                detailBuilder.append("(local function) ");
            }
            else
            {
                detailBuilder.append(IASKeywordConstants.FUNCTION);
                detailBuilder.append(" ");
            }
            detailBuilder.append(getSignatureLabel(functionDefinition));
        }
        else if (definition instanceof IEventDefinition)
        {
            IEventDefinition eventDefinition = (IEventDefinition) definition;
            detailBuilder.append("(event) ");
            detailBuilder.append("[");
            detailBuilder.append(IMetaAttributeConstants.ATTRIBUTE_EVENT);
            detailBuilder.append("(");
            detailBuilder.append(IMetaAttributeConstants.NAME_EVENT_NAME);
            detailBuilder.append("=");
            detailBuilder.append("\"");
            detailBuilder.append(eventDefinition.getBaseName());
            detailBuilder.append("\"");
            detailBuilder.append(",");
            detailBuilder.append(IMetaAttributeConstants.NAME_EVENT_TYPE);
            detailBuilder.append("=");
            detailBuilder.append("\"");
            detailBuilder.append(eventDefinition.getTypeAsDisplayString());
            detailBuilder.append("\"");
            detailBuilder.append(")");
            detailBuilder.append("]");
        }
        else if (definition instanceof IStyleDefinition)
        {
            IStyleDefinition styleDefinition = (IStyleDefinition) definition;
            detailBuilder.append("(style) ");
            detailBuilder.append("[");
            detailBuilder.append(IMetaAttributeConstants.ATTRIBUTE_STYLE);
            detailBuilder.append("(");
            detailBuilder.append(IMetaAttributeConstants.NAME_STYLE_NAME);
            detailBuilder.append("=");
            detailBuilder.append("\"");
            detailBuilder.append(styleDefinition.getBaseName());
            detailBuilder.append("\"");
            detailBuilder.append(",");
            detailBuilder.append(IMetaAttributeConstants.NAME_STYLE_TYPE);
            detailBuilder.append("=");
            detailBuilder.append("\"");
            detailBuilder.append(styleDefinition.getTypeAsDisplayString());
            detailBuilder.append("\"");
            detailBuilder.append(")");
            detailBuilder.append("]");
        }
        return detailBuilder.toString();
    }

    private String getSignatureLabel(IFunctionDefinition functionDefinition)
    {
        StringBuilder labelBuilder = new StringBuilder();
        labelBuilder.append(functionDefinition.getBaseName());
        labelBuilder.append("(");
        IParameterDefinition[] parameters = functionDefinition.getParameters();
        for (int i = 0, count = parameters.length; i < count; i++)
        {
            if (i > 0)
            {
                labelBuilder.append(", ");
            }
            IParameterDefinition parameterDefinition = parameters[i];
            if (parameterDefinition.isRest())
            {
                labelBuilder.append(IASLanguageConstants.REST);
            }
            labelBuilder.append(parameterDefinition.getBaseName());
            labelBuilder.append(":");
            labelBuilder.append(parameterDefinition.getTypeAsDisplayString());
            if (parameterDefinition.hasDefaultValue())
            {
                labelBuilder.append(" = ");
                Object defaultValue = parameterDefinition.resolveDefaultValue(currentProject);
                if (defaultValue instanceof String)
                {
                    labelBuilder.append("\"");
                    labelBuilder.append(defaultValue);
                    labelBuilder.append("\"");
                }
                else if (defaultValue != null)
                {
                    if (defaultValue.getClass() == Object.class)
                    {
                        //for some reason, null is some strange random object
                        labelBuilder.append(IASLanguageConstants.NULL);
                    }
                    else
                    {
                        //numeric values and everything else should be okay
                        labelBuilder.append(defaultValue);
                    }
                }
                else
                {
                    //I don't know how this might happen, but this is probably
                    //a safe fallback value
                    labelBuilder.append(IASLanguageConstants.NULL);
                }
            }
        }
        labelBuilder.append(")");
        if (!functionDefinition.isConstructor())
        {
            labelBuilder.append(":");
            labelBuilder.append(functionDefinition.getReturnTypeAsDisplayString());
        }
        return labelBuilder.toString();
    }

    private boolean isActionScriptCompletionAllowedInNode(TextDocumentPositionParams params, IASNode offsetNode)
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
        return !isInActionScriptComment(params);
    }

    private boolean isInActionScriptComment(TextDocumentPositionParams params)
    {
        TextDocumentIdentifier textDocument = params.getTextDocument();
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
        if (path == null || !sourceByPath.containsKey(path))
        {
            return false;
        }
        String code = sourceByPath.get(path);
        int startComment = code.lastIndexOf("/*", currentOffset - 1);
        if (startComment >= 0)
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
        startComment = code.indexOf("//", startLine);
        return startComment != -1 && currentOffset > startComment;
    }

    private boolean isInXMLComment(TextDocumentPositionParams params)
    {
        TextDocumentIdentifier textDocument = params.getTextDocument();
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
        if (path == null || !sourceByPath.containsKey(path))
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

    private boolean isDeclarationsTag(IMXMLTagData tag)
    {
        if (tag == null)
        {
            return false;
        }
        String shortName = tag.getShortName();
        if (shortName == null || !shortName.equals(IMXMLLanguageConstants.DECLARATIONS))
        {
            return false;
        }
        String uri = tag.getURI();
        if (uri == null || !uri.equals(IMXMLLanguageConstants.NAMESPACE_MXML_2009))
        {
            return false;
        }
        return true;
    }

    private boolean isInWorkspaceOrSourcePath(Path path)
    {
        if (path.startsWith(workspaceRoot))
        {
            return true;
        }
        //if we haven't accessed a compilation unit yet, the project may be null
        currentProject = getProject();
        if (currentProjectOptions == null)
        {
            return false;
        }
        List<File> sourcePaths = currentProjectOptions.compilerOptions.sourcePath;
        if (sourcePaths != null)
        {
            for (File sourcePathFile : sourcePaths)
            {
                try
                {
                    Path sourcePathPath = sourcePathFile.getCanonicalFile().toPath();
                    if (path.startsWith(sourcePathPath))
                    {
                        return true;
                    }
                }
                catch (IOException e)
                {
                    //safe to ignore
                }
            }
        }
        return false;
    }

    private void querySymbolsInScope(String query, IASScope scope, List<SymbolInformation> result)
    {
        String lowerCaseQuery
         = query.toLowerCase();
        Collection<IDefinition> definitions = scope.getAllLocalDefinitions();
        for (IDefinition definition : definitions)
        {
            if (definition instanceof IPackageDefinition)
            {
                IPackageDefinition packageDefinition = (IPackageDefinition) definition;
                IASScope packageScope = packageDefinition.getContainedScope();
                querySymbolsInScope(query, packageScope, result);
            }
            else if (definition instanceof ITypeDefinition)
            {
                ITypeDefinition typeDefinition = (ITypeDefinition) definition;
                if (!definition.isImplicit()
                        && typeDefinition.getQualifiedName().toLowerCase().contains(lowerCaseQuery))
                {
                    SymbolInformation symbol = definitionToSymbol(typeDefinition);
                    result.add(symbol);
                }
                IASScope typeScope = typeDefinition.getContainedScope();
                querySymbolsInScope(query, typeScope, result);
            }
            else if (definition instanceof IFunctionDefinition)
            {
                if (definition.isImplicit())
                {
                    continue;
                }
                IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
                if (functionDefinition.getQualifiedName().toLowerCase().contains(lowerCaseQuery))
                {
                    SymbolInformation symbol = definitionToSymbol(functionDefinition);
                    result.add(symbol);
                }
            }
            else if (definition instanceof IVariableDefinition)
            {
                if (definition.isImplicit())
                {
                    continue;
                }
                IVariableDefinition variableDefinition = (IVariableDefinition) definition;
                if (variableDefinition.getQualifiedName().toLowerCase().contains(lowerCaseQuery))
                {
                    SymbolInformation symbol = definitionToSymbol(variableDefinition);
                    result.add(symbol);
                }
            }
        }
    }

    private void scopeToSymbols(IASScope scope, List<SymbolInformation> result)
    {
        Collection<IDefinition> definitions = scope.getAllLocalDefinitions();
        for (IDefinition definition : definitions)
        {
            if (definition instanceof IPackageDefinition)
            {
                IPackageDefinition packageDefinition = (IPackageDefinition) definition;
                IASScope packageScope = packageDefinition.getContainedScope();
                scopeToSymbols(packageScope, result);
            }
            else if (definition instanceof ITypeDefinition)
            {
                ITypeDefinition typeDefinition = (ITypeDefinition) definition;
                if (!definition.isImplicit())
                {
                    SymbolInformation typeSymbol = definitionToSymbol(typeDefinition);
                    result.add(typeSymbol);
                }
                IASScope typeScope = typeDefinition.getContainedScope();
                scopeToSymbols(typeScope, result);
            }
            else if (definition instanceof IFunctionDefinition
                    || definition instanceof IVariableDefinition)
            {
                if (definition.isImplicit())
                {
                    continue;
                }
                SymbolInformation localSymbol = definitionToSymbol(definition);
                result.add(localSymbol);
            }
        }
    }

    private SymbolInformation definitionToSymbol(IDefinition definition)
    {
        SymbolInformation symbol = new SymbolInformation();
        if (definition instanceof IClassDefinition)
        {
            symbol.setKind(SymbolKind.Class);
        }
        else if (definition instanceof IInterfaceDefinition)
        {
            symbol.setKind(SymbolKind.Interface);
        }
        else if (definition instanceof IFunctionDefinition)
        {
            IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
            if (functionDefinition.isConstructor())
            {
                symbol.setKind(SymbolKind.Constructor);
            }
            else
            {
                symbol.setKind(SymbolKind.Function);
            }
        }
        else if (definition instanceof IFunctionDefinition)
        {
            symbol.setKind(SymbolKind.Function);
        }
        else if (definition instanceof IConstantDefinition)
        {
            symbol.setKind(SymbolKind.Constant);
        }
        else
        {
            symbol.setKind(SymbolKind.Variable);
        }
        if (!definition.getQualifiedName().equals(definition.getBaseName()))
        {
            symbol.setContainerName(definition.getPackageName());
        }
        symbol.setName(definition.getBaseName());
        Location location = new Location();
        String sourcePath = definition.getSourcePath();
        if (sourcePath == null)
        {
            //I'm not sure why getSourcePath() can sometimes return null, but
            //getContainingFilePath() seems to work as a fallback -JT
            sourcePath = definition.getContainingFilePath();
        }
        Path definitionPath = Paths.get(sourcePath);
        location.setUri(definitionPath.toUri().toString());
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
            LanguageServerCompilerUtils.getPositionFromOffset(definitionReader, definition.getNameStart(), start);
            end.setLine(start.getLine());
            end.setCharacter(start.getCharacter());
        }
        else
        {
            start.setLine(line);
            start.setCharacter(column);
            end.setLine(line);
            end.setCharacter(column);
        }
        Range range = new Range();
        range.setStart(start);
        range.setEnd(end);
        location.setRange(range);
        symbol.setLocation(location);
        return symbol;
    }

    private CompletableFuture<Object> executeOrganizeImportsInDirectoryCommand(ExecuteCommandParams params)
    {
        List<Object> args = params.getArguments();
        Object uncastUri = args.get(0);
        LinkedTreeMap<?,?> encodedUri = null;
        if (uncastUri instanceof LinkedTreeMap<?,?>)
        {
            encodedUri = (LinkedTreeMap<?,?>) uncastUri;
        }
        String uri = (String) encodedUri.get("external");

        File rootDir = Paths.get(URI.create(uri)).toFile();
        if (!rootDir.isDirectory())
        {
            return CompletableFuture.completedFuture(new Object());
        }
        ArrayList<File> directories = new ArrayList<>();
        directories.add(rootDir);
        for(int i = 0, dirCount = 1; i < dirCount; i++)
        {
            File currentDir = directories.get(i);
            File[] files = currentDir.listFiles();
            for (File file : files)
            {
                if (file.isDirectory())
                {
                    //add this directory to the list to search
                    directories.add(file);
                    dirCount++;
                    continue;
                }
                if (!file.getName().endsWith(AS_EXTENSION) && !file.getName().endsWith(MXML_EXTENSION))
                {
                    continue;
                }
                organizeImportsInUri(file.toURI().toString());
            }
        }
        return CompletableFuture.completedFuture(new Object());
    }

    private void organizeImportsInUri(String uri)
    {
        Path pathForImport = Paths.get(URI.create(uri));
        Reader reader = getReaderForPath(pathForImport);
        String text = null;
        try
        {
            text = IOUtils.toString(reader);
        }
        catch (IOException e)
        {
            return;
        }

        Set<String> missingNames = null;
        Set<String> importsToAdd = null;
        Set<IImportNode> importsToRemove = null;
        IASNode ast = getAST(pathForImport);
        if (ast != null)
        {
            missingNames = ASTUtils.findUnresolvedIdentifiersToImport(ast, currentProject);
            importsToRemove = ASTUtils.findImportNodesToRemove(ast, currentProject);
        }
        if (missingNames != null)
        {
            importsToAdd = new HashSet<>();
            for (String missingName : missingNames)
            {
                List<IDefinition> types = ASTUtils.findTypesThatMatchName(missingName, compilationUnits);
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
        
        ApplyWorkspaceEditParams editParams = new ApplyWorkspaceEditParams();
        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        HashMap<String,List<TextEdit>> changes = new HashMap<>();
        changes.put(uri, edits);
        workspaceEdit.setChanges(changes);
        editParams.setEdit(workspaceEdit);

        languageClient.applyEdit(editParams);
    }
    
    private CompletableFuture<Object> executeOrganizeImportsInUriCommand(ExecuteCommandParams params)
    {
        List<Object> args = params.getArguments();
        Object uncastUri = args.get(0);
        LinkedTreeMap<?,?> encodedUri = null;
        if (uncastUri instanceof LinkedTreeMap<?,?>)
        {
            encodedUri = (LinkedTreeMap<?,?>) uncastUri;
        }

        String uri = (String) encodedUri.get("external");
        organizeImportsInUri(uri);

        return CompletableFuture.completedFuture(new Object());
    }
    
    private CompletableFuture<Object> executeAddImportCommand(ExecuteCommandParams params)
    {
        List<Object> args = params.getArguments();
        String qualifiedName = (String) args.get(0);
        String uri = (String) args.get(1);
        int startIndex = ((Double) args.get(2)).intValue();
        int endIndex = ((Double) args.get(3)).intValue();
        if(qualifiedName == null)
        {
            return CompletableFuture.completedFuture(new Object());
        }
        Path pathForImport = Paths.get(URI.create(uri));
        String text = sourceByPath.get(pathForImport);
        TextEdit edit = ImportTextEditUtils.createTextEditForImport(qualifiedName, text, startIndex, endIndex);
        if(edit == null)
        {
            //no edit required
            return CompletableFuture.completedFuture(new Object());
        }

        ApplyWorkspaceEditParams editParams = new ApplyWorkspaceEditParams();
        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        HashMap<String,List<TextEdit>> changes = new HashMap<>();
        List<TextEdit> edits = new ArrayList<>();
        edits.add(edit);
        changes.put(uri, edits);
        workspaceEdit.setChanges(changes);
        editParams.setEdit(workspaceEdit);

        languageClient.applyEdit(editParams);

        return CompletableFuture.completedFuture(new Object());
    }
    
    private CompletableFuture<Object> executeAddMXMLNamespaceCommand(ExecuteCommandParams params)
    {
        List<Object> args = params.getArguments();
        String nsPrefix = (String) args.get(0);
        String nsUri = (String) args.get(1);
        String uri = (String) args.get(2);
        int startIndex = ((Double) args.get(3)).intValue();
        int endIndex = ((Double) args.get(4)).intValue();
        if(nsPrefix == null || nsUri == null)
        {
            return CompletableFuture.completedFuture(new Object());
        }
        Path pathForImport = Paths.get(URI.create(uri));
        String text = sourceByPath.get(pathForImport);
        TextEdit edit = ImportTextEditUtils.createTextEditForMXMLNamespace(nsPrefix, nsUri, text, startIndex, endIndex);
        if(edit == null)
        {
            //no edit required
            return CompletableFuture.completedFuture(new Object());
        }

        ApplyWorkspaceEditParams editParams = new ApplyWorkspaceEditParams();
        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        HashMap<String,List<TextEdit>> changes = new HashMap<>();
        List<TextEdit> edits = new ArrayList<>();
        edits.add(edit);
        changes.put(uri, edits);
        workspaceEdit.setChanges(changes);
        editParams.setEdit(workspaceEdit);

        languageClient.applyEdit(editParams);

        return CompletableFuture.completedFuture(new Object());
    }
    
    private CompletableFuture<Object> executeGenerateLocalVariableCommand(ExecuteCommandParams params)
    {
        List<Object> args = params.getArguments();
        String uri = (String) args.get(0);
        int startLine = ((Double) args.get(1)).intValue();
        int startChar = ((Double) args.get(2)).intValue();
        //int endLine = ((Double) args.get(3)).intValue();
        //int endChar = ((Double) args.get(4)).intValue();
        String name = (String) args.get(5);

        TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
        Position position = new Position(startLine, startChar);
        IASNode offsetNode = getOffsetNode(identifier, position);
        if (offsetNode == null)
        {
            return CompletableFuture.completedFuture(new Object());
        }
        IFunctionNode functionNode = (IFunctionNode) offsetNode.getAncestorOfType(IFunctionNode.class);
        if (functionNode == null)
        {
            return CompletableFuture.completedFuture(new Object());
        }
        IScopedNode scopedNode = functionNode.getScopedNode();
        if (scopedNode == null)
        {
            return CompletableFuture.completedFuture(new Object());
        }

        String newLine = "\n";
        String indent = "\t\t\t";
        StringBuilder builder = new StringBuilder();
        builder.append(newLine);
        builder.append(indent);
        builder.append("var ");
        builder.append(name);
        builder.append(":");
        builder.append(IASLanguageConstants.Object);
        builder.append(";");
        
        ApplyWorkspaceEditParams editParams = new ApplyWorkspaceEditParams();
        
        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        editParams.setEdit(workspaceEdit);

        HashMap<String,List<TextEdit>> changes = new HashMap<>();
        workspaceEdit.setChanges(changes);

        List<TextEdit> edits = new ArrayList<>();
        changes.put(uri, edits);

        TextEdit edit = new TextEdit();
        edits.add(edit);

        edit.setNewText(builder.toString());
        Position editPosition = new Position(scopedNode.getLine(), scopedNode.getColumn() + 1);
        edit.setRange(new Range(editPosition, editPosition));

        languageClient.applyEdit(editParams);

        return CompletableFuture.completedFuture(new Object());
    }
    
    private CompletableFuture<Object> executeGenerateFieldVariableCommand(ExecuteCommandParams params)
    {
        List<Object> args = params.getArguments();
        String uri = (String) args.get(0);
        int startLine = ((Double) args.get(1)).intValue();
        int startChar = ((Double) args.get(2)).intValue();
        //int endLine = ((Double) args.get(3)).intValue();
        //int endChar = ((Double) args.get(4)).intValue();
        String name = (String) args.get(5);
        
        TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
        Position position = new Position(startLine, startChar);
        IASNode offsetNode = getOffsetNode(identifier, position);
        if (offsetNode == null)
        {
            return CompletableFuture.completedFuture(new Object());
        }
        IClassNode classNode = (IClassNode) offsetNode.getAncestorOfType(IClassNode.class);
        if (classNode == null)
        {
            return CompletableFuture.completedFuture(new Object());
        }
        IScopedNode scopedNode = classNode.getScopedNode();
        if (scopedNode == null)
        {
            return CompletableFuture.completedFuture(new Object());
        }

        String indent = "\t\t";
        String newLine = "\n";
        StringBuilder builder = new StringBuilder();
        builder.append(newLine);
        builder.append(indent);
        builder.append("public var ");
        builder.append(name);
        builder.append(":");
        builder.append(IASLanguageConstants.Object);
        builder.append(";");
        builder.append(newLine);
        
        ApplyWorkspaceEditParams editParams = new ApplyWorkspaceEditParams();
        
        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        editParams.setEdit(workspaceEdit);

        HashMap<String,List<TextEdit>> changes = new HashMap<>();
        workspaceEdit.setChanges(changes);

        List<TextEdit> edits = new ArrayList<>();
        changes.put(uri, edits);

        TextEdit edit = new TextEdit();
        edits.add(edit);

        edit.setNewText(builder.toString());
        Position editPosition = new Position(scopedNode.getEndLine(), 0);
        edit.setRange(new Range(editPosition, editPosition));

        languageClient.applyEdit(editParams);

        return CompletableFuture.completedFuture(new Object());
    }
    
    private CompletableFuture<Object> executeGenerateMethodCommand(ExecuteCommandParams params)
    {
        List<Object> args = params.getArguments();
        String uri = (String) args.get(0);
        int startLine = ((Double) args.get(1)).intValue();
        int startChar = ((Double) args.get(2)).intValue();
        //int endLine = ((Double) args.get(3)).intValue();
        //int endChar = ((Double) args.get(4)).intValue();
        String name = (String) args.get(5);
        Object uncastArgs = args.get(6);
        ArrayList<?> methodArgs = null;
        if (uncastArgs instanceof ArrayList<?>)
        {
            methodArgs = (ArrayList<?>) uncastArgs;
        }

        TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
        Position position = new Position(startLine, startChar);
        IASNode offsetNode = getOffsetNode(identifier, position);
        if (offsetNode == null)
        {
            return CompletableFuture.completedFuture(new Object());
        }
        IClassNode classNode = (IClassNode) offsetNode.getAncestorOfType(IClassNode.class);
        if (classNode == null)
        {
            return CompletableFuture.completedFuture(new Object());
        }
        IScopedNode scopedNode = classNode.getScopedNode();
        if (scopedNode == null)
        {
            return CompletableFuture.completedFuture(new Object());
        }
        
        ApplyWorkspaceEditParams editParams = new ApplyWorkspaceEditParams();
        
        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        editParams.setEdit(workspaceEdit);

        HashMap<String,List<TextEdit>> changes = new HashMap<>();
        workspaceEdit.setChanges(changes);

        List<TextEdit> edits = new ArrayList<>();
        changes.put(uri, edits);

        TextEdit edit = new TextEdit();
        edits.add(edit);

        String newLine = "\n";
        String indent = "\t\t";
        StringBuilder builder = new StringBuilder();
        builder.append(newLine);
        builder.append(indent);
        builder.append("private function ");
        builder.append(name);
        builder.append("(");
        ImportRange importRange = getImportRange(offsetNode);
        Path pathForImport = Paths.get(URI.create(uri));
        String fileText = sourceByPath.get(pathForImport);
        for (int i = 0, count = methodArgs.size(); i < count; i++)
        {
            if(i > 0)
            {
                builder.append(", ");
            }
            String type = (String) methodArgs.get(i);
            builder.append("param");
            builder.append(i);
            builder.append(":");
            int index = type.lastIndexOf(".");
            if (index == -1)
            {
                builder.append(type);
            }
            else
            {
                builder.append(type.substring(index + 1));
            }
            TextEdit importEdit = ImportTextEditUtils.createTextEditForImport(type, fileText, importRange.startIndex, importRange.endIndex);
            if (importEdit != null)
            {
                edits.add(importEdit);
            }
        }
        builder.append(")");
        builder.append(":");
        builder.append(IASLanguageConstants.void_);
        builder.append(newLine);
        builder.append(indent);
        builder.append("{");
        builder.append(newLine);
        builder.append(indent);
        builder.append("}");
        builder.append(newLine);

        edit.setNewText(builder.toString());
        Position editPosition = new Position(scopedNode.getEndLine(), 0);
        edit.setRange(new Range(editPosition, editPosition));

        languageClient.applyEdit(editParams);

        return CompletableFuture.completedFuture(new Object());
    }

    private CompletableFuture<Object> executeGenerateGetterAndSetterCommand(ExecuteCommandParams params, boolean generateGetter, boolean generateSetter)
    {
        List<Object> args = params.getArguments();
        String uri = (String) args.get(0);
        int startLine = ((Double) args.get(1)).intValue();
        int startChar = ((Double) args.get(2)).intValue();
        int endLine = ((Double) args.get(3)).intValue();
        int endChar = ((Double) args.get(4)).intValue();
        String name = (String) args.get(5);
        String namespace = (String) args.get(6);
        boolean isStatic = (Boolean) args.get(7);
        String type = (String) args.get(8);
        String assignedValue = (String) args.get(9);

        ApplyWorkspaceEditParams editParams = new ApplyWorkspaceEditParams();

        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        editParams.setEdit(workspaceEdit);

        HashMap<String,List<TextEdit>> changes = new HashMap<>();
        workspaceEdit.setChanges(changes);

        List<TextEdit> edits = new ArrayList<>();
        changes.put(uri, edits);

        TextEdit edit = new TextEdit();
        edits.add(edit);

        StringBuilder builder = new StringBuilder();
        builder.append("private ");
        if(isStatic)
        {
            builder.append("static ");
        }
        builder.append("var _" + name);
        if(type != null && type.length() > 0)
        {
            builder.append(":" + type);
        }
        if(assignedValue != null)
        {
            builder.append(" = " + assignedValue);
        }
        builder.append(";");
        if (generateGetter)
        {
            builder.append("\n\n");
            builder.append("\t\t" + namespace + " ");
            if(isStatic)
            {
                builder.append("static ");
            }
            builder.append("function get " + name + "()");
            if(type != null && type.length() > 0)
            {
                builder.append(":" + type);
            }
            builder.append("\n");
            builder.append("\t\t{\n");
            builder.append("\t\t\treturn _" + name +";\n");
            builder.append("\t\t}");
        }
        if (generateSetter)
        {
            builder.append("\n\n");
            builder.append("\t\t" + namespace + " ");
            if(isStatic)
            {
                builder.append("static ");
            }
            builder.append("function set " + name + "(value");
            if(type != null && type.length() > 0)
            {
                builder.append(":" + type);
            }
            builder.append("):void\n");
            builder.append("\t\t{\n");
            builder.append("\t\t\t_" + name + " = value;\n");
            builder.append("\t\t}");
        }
        edit.setNewText(builder.toString());

        Position startPosition = new Position(startLine, startChar);
        Position endPosition = new Position(endLine, endChar);

        //we may need to adjust the end position to include the semi-colon
        try
        {
            Path path = Paths.get(URI.create(uri));
            String text = IOUtils.toString(getReaderForPath(path));
            int offset = LanguageServerCompilerUtils.getOffsetFromPosition(new StringReader(text), endPosition);
            if (offset < text.length() && text.charAt(offset) == ';')
            {
                endPosition.setCharacter(endChar + 1);
            }
        }
        catch (IOException e)
        {
            //ignore
        }

        edit.setRange(new Range(startPosition, endPosition));

        languageClient.applyEdit(editParams);
        return CompletableFuture.completedFuture(new Object());
    }
}
