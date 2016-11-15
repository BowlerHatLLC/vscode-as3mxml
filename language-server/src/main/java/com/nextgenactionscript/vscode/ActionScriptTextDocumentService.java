/*
Copyright 2016 Bowler Hat LLC

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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.apache.flex.compiler.clients.problems.CompilerProblemCategorizer;
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
import org.apache.flex.compiler.definitions.metadata.IMetaTag;
import org.apache.flex.compiler.filespecs.IFileSpecification;
import org.apache.flex.compiler.internal.driver.js.goog.JSGoogConfiguration;
import org.apache.flex.compiler.internal.mxml.MXMLData;
import org.apache.flex.compiler.internal.mxml.MXMLNamespaceMapping;
import org.apache.flex.compiler.internal.parsing.as.ASParser;
import org.apache.flex.compiler.internal.parsing.as.ASToken;
import org.apache.flex.compiler.internal.parsing.as.RepairingTokenBuffer;
import org.apache.flex.compiler.internal.parsing.as.StreamingASTokenizer;
import org.apache.flex.compiler.internal.projects.CompilerProject;
import org.apache.flex.compiler.internal.projects.FlexJSProject;
import org.apache.flex.compiler.internal.projects.FlexProject;
import org.apache.flex.compiler.internal.scopes.ASScope;
import org.apache.flex.compiler.internal.scopes.TypeScope;
import org.apache.flex.compiler.internal.tree.as.FileNode;
import org.apache.flex.compiler.internal.tree.as.FullNameNode;
import org.apache.flex.compiler.internal.units.SWCCompilationUnit;
import org.apache.flex.compiler.internal.workspaces.Workspace;
import org.apache.flex.compiler.mxml.IMXMLDataManager;
import org.apache.flex.compiler.mxml.IMXMLLanguageConstants;
import org.apache.flex.compiler.mxml.IMXMLTagAttributeData;
import org.apache.flex.compiler.mxml.IMXMLTagData;
import org.apache.flex.compiler.problems.CompilerProblemSeverity;
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
import org.apache.flex.compiler.tree.as.IMemberAccessExpressionNode;
import org.apache.flex.compiler.tree.as.INamespaceDecorationNode;
import org.apache.flex.compiler.tree.as.IScopedDefinitionNode;
import org.apache.flex.compiler.tree.as.IScopedNode;
import org.apache.flex.compiler.tree.as.IVariableNode;
import org.apache.flex.compiler.units.ICompilationUnit;
import org.apache.flex.compiler.units.IInvisibleCompilationUnit;
import org.apache.flex.compiler.workspaces.IWorkspace;

import com.nextgenactionscript.vscode.asconfig.ASConfigOptions;
import com.nextgenactionscript.vscode.asconfig.CompilerOptions;
import com.nextgenactionscript.vscode.asconfig.ProjectType;
import com.nextgenactionscript.vscode.mxml.IMXMLLibraryConstants;
import io.typefox.lsapi.CodeActionParams;
import io.typefox.lsapi.CodeLens;
import io.typefox.lsapi.CodeLensParams;
import io.typefox.lsapi.Command;
import io.typefox.lsapi.CompletionItem;
import io.typefox.lsapi.CompletionItemKind;
import io.typefox.lsapi.CompletionList;
import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.DiagnosticSeverity;
import io.typefox.lsapi.DidChangeTextDocumentParams;
import io.typefox.lsapi.DidChangeWatchedFilesParams;
import io.typefox.lsapi.DidCloseTextDocumentParams;
import io.typefox.lsapi.DidOpenTextDocumentParams;
import io.typefox.lsapi.DidSaveTextDocumentParams;
import io.typefox.lsapi.DocumentFormattingParams;
import io.typefox.lsapi.DocumentHighlight;
import io.typefox.lsapi.DocumentOnTypeFormattingParams;
import io.typefox.lsapi.DocumentRangeFormattingParams;
import io.typefox.lsapi.DocumentSymbolParams;
import io.typefox.lsapi.FileChangeType;
import io.typefox.lsapi.FileEvent;
import io.typefox.lsapi.Hover;
import io.typefox.lsapi.Location;
import io.typefox.lsapi.MessageParams;
import io.typefox.lsapi.MessageType;
import io.typefox.lsapi.Position;
import io.typefox.lsapi.PublishDiagnosticsParams;
import io.typefox.lsapi.Range;
import io.typefox.lsapi.ReferenceParams;
import io.typefox.lsapi.RenameParams;
import io.typefox.lsapi.SignatureHelp;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.SymbolKind;
import io.typefox.lsapi.TextDocumentContentChangeEvent;
import io.typefox.lsapi.TextDocumentIdentifier;
import io.typefox.lsapi.TextDocumentItem;
import io.typefox.lsapi.TextDocumentPositionParams;
import io.typefox.lsapi.TextEdit;
import io.typefox.lsapi.VersionedTextDocumentIdentifier;
import io.typefox.lsapi.WorkspaceEdit;
import io.typefox.lsapi.WorkspaceSymbolParams;
import io.typefox.lsapi.impl.CodeLensImpl;
import io.typefox.lsapi.impl.CommandImpl;
import io.typefox.lsapi.impl.CompletionItemImpl;
import io.typefox.lsapi.impl.CompletionListImpl;
import io.typefox.lsapi.impl.DiagnosticImpl;
import io.typefox.lsapi.impl.DocumentHighlightImpl;
import io.typefox.lsapi.impl.HoverImpl;
import io.typefox.lsapi.impl.LocationImpl;
import io.typefox.lsapi.impl.MarkedStringImpl;
import io.typefox.lsapi.impl.MessageParamsImpl;
import io.typefox.lsapi.impl.ParameterInformationImpl;
import io.typefox.lsapi.impl.PositionImpl;
import io.typefox.lsapi.impl.PublishDiagnosticsParamsImpl;
import io.typefox.lsapi.impl.RangeImpl;
import io.typefox.lsapi.impl.SignatureHelpImpl;
import io.typefox.lsapi.impl.SignatureInformationImpl;
import io.typefox.lsapi.impl.SymbolInformationImpl;
import io.typefox.lsapi.impl.TextEditImpl;
import io.typefox.lsapi.impl.WorkspaceEditImpl;
import io.typefox.lsapi.services.TextDocumentService;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

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
    private static final String ASCONFIG_JSON = "asconfig.json";
    private static final String DEFAULT_NS_PREFIX = "ns";
    private static final String DOT_STAR = ".*";

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

    private Boolean asconfigChanged = true;
    private Path workspaceRoot;
    private Map<Path, String> sourceByPath = new HashMap<>();
    private Collection<ICompilationUnit> compilationUnits;
    private ArrayList<IInvisibleCompilationUnit> invisibleUnits = new ArrayList<>();
    private ICompilationUnit currentUnit;
    private FlexProject currentProject;
    private IWorkspace currentWorkspace;
    private ASConfigOptions currentOptions;
    private int currentOffset = -1;
    private LanguageServerFileSpecGetter fileSpecGetter;
    private HashSet<URI> newFilesWithErrors = new HashSet<>();
    private HashSet<URI> oldFilesWithErrors = new HashSet<>();
    private Consumer<PublishDiagnosticsParams> publishDiagnostics = p ->
    {
    };
    public Consumer<MessageParams> showMessageCallback = m ->
    {
    };

    public ActionScriptTextDocumentService()
    {
    }

    public Path getWorkspaceRoot()
    {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(Path value)
    {
        workspaceRoot = value;
    }

    /**
     * Returns a list of all items to display in the completion list at a
     * specific position in a document. Called automatically by VSCode as the
     * user types, and may not necessarily be triggered only on "." or ":".
     */
    @Override
    public CompletableFuture<CompletionList> completion(TextDocumentPositionParams position)
    {
        IMXMLTagData offsetTag = getOffsetMXMLTag(position);
        //if we're inside an <fx:Script> tag, we want ActionScript completion,
        //so that's why we call isMXMLTagValidForCompletion()
        if (offsetTag != null && isMXMLTagValidForCompletion(offsetTag))
        {
            return mxmlCompletion(position, offsetTag);
        }
        return actionScriptCompletion(position);
    }

    /**
     * This function is never called. We resolve completion items immediately
     * in completion() instead of requiring a separate step.
     */
    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved)
    {
        return CompletableFuture.completedFuture(new CompletionItemImpl());
    }

    /**
     * Returns information to display in a tooltip when the mouse hovers over
     * something in a text document.
     */
    @Override
    public CompletableFuture<Hover> hover(TextDocumentPositionParams position)
    {
        IMXMLTagData offsetTag = getOffsetMXMLTag(position);
        //if we're inside an <fx:Script> tag, we want ActionScript hover,
        //so that's why we call isMXMLTagValidForCompletion()
        if (offsetTag != null && isMXMLTagValidForCompletion(offsetTag))
        {
            return mxmlHover(position, offsetTag);
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
        IASNode offsetNode = getOffsetNode(position);
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return CompletableFuture.completedFuture(new SignatureHelpImpl(Collections.emptyList(), -1, -1));
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
                        functionDefinition = classDefinitionToConstructor(classDefinition);
                    }
                }
            }
        }
        if (functionDefinition != null)
        {
            SignatureHelpImpl result = new SignatureHelpImpl();
            List<SignatureInformationImpl> signatures = new ArrayList<>();

            SignatureInformationImpl signatureInfo = new SignatureInformationImpl();
            signatureInfo.setLabel(getSignatureLabel(functionDefinition));

            List<ParameterInformationImpl> parameters = new ArrayList<>();
            for (IParameterDefinition param : functionDefinition.getParameters())
            {
                ParameterInformationImpl paramInfo = new ParameterInformationImpl();
                paramInfo.setLabel(param.getBaseName());
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
        return CompletableFuture.completedFuture(new SignatureHelpImpl(Collections.emptyList(), -1, -1));
    }

    /**
     * Finds where the definition referenced at the current position in a text
     * document is defined.
     */
    @Override
    public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position)
    {
        IMXMLTagData offsetTag = getOffsetMXMLTag(position);
        //if we're inside an <fx:Script> tag, we want ActionScript lookup,
        //so that's why we call isMXMLTagValidForCompletion()
        if (offsetTag != null && isMXMLTagValidForCompletion(offsetTag))
        {
            return mxmlDefinition(position, offsetTag);
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
        IMXMLTagData offsetTag = getOffsetMXMLTag(params);
        //if we're inside an <fx:Script> tag, we want ActionScript lookup,
        //so that's why we call isMXMLTagValidForCompletion()
        if (offsetTag != null && isMXMLTagValidForCompletion(offsetTag))
        {
            return mxmlReferences(params, offsetTag);
        }
        return actionScriptReferences(params);
    }

    /**
     * This feature is implemented at this time.
     */
    @Override
    public CompletableFuture<DocumentHighlight> documentHighlight(TextDocumentPositionParams position)
    {
        return CompletableFuture.completedFuture(new DocumentHighlightImpl());
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
        List<SymbolInformationImpl> result = new ArrayList<>();
        String query = params.getQuery();
        for (ICompilationUnit unit : compilationUnits)
        {
            if (unit instanceof SWCCompilationUnit)
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
        Path path = getPathFromLsapiURI(textDocument.getUri());
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
        List<SymbolInformationImpl> result = new ArrayList<>();
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
        Path path = getPathFromLsapiURI(textDocument.getUri());
        if (path == null || !sourceByPath.containsKey(path))
        {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        ArrayList<CommandImpl> commands = new ArrayList<>();
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
                    createCodeActionsForImport(diagnostic, commands);
                    break;
                }
                case "1046": //UnknownTypeProblem
                {
                    //see if there's anything we can import
                    createCodeActionsForImport(diagnostic, commands);
                    break;
                }
                case "1178": //InaccessiblePropertyReferenceProblem
                {
                    //see if there's anything we can import
                    createCodeActionsForImport(diagnostic, commands);
                    break;
                }
                case "1180": //CallUndefinedMethodProblem
                {
                    //see if there's anything we can import
                    createCodeActionsForImport(diagnostic, commands);
                    break;
                }
            }
        }
        return CompletableFuture.completedFuture(commands);
    }

    private void createCodeActionsForImport(Diagnostic diagnostic, List<CommandImpl> commands)
    {
        String message = diagnostic.getMessage();
        int start = message.lastIndexOf(" ") + 1;
        int end = message.length() - 1;
        String typeString = message.substring(start, end);

        ArrayList<String> types = new ArrayList<>();
        for (ICompilationUnit unit : compilationUnits)
        {
            try
            {
                Collection<IDefinition> definitions = unit.getFileScopeRequest().get().getExternallyVisibleDefinitions();
                if (definitions == null)
                {
                    continue;
                }
                for (IDefinition definition : definitions)
                {
                    if (definition instanceof ITypeDefinition)
                    {
                        ITypeDefinition typeDefinition = (ITypeDefinition) definition;
                        String baseName = typeDefinition.getBaseName();
                        if (typeDefinition.getQualifiedName().equals(baseName))
                        {
                            //this definition is top-level. no import required.
                            continue;
                        }
                        if (baseName.equals(typeString))
                        {
                            types.add(typeDefinition.getQualifiedName());
                        }
                    }
                }
            }
            catch (Exception e)
            {
                //safe to ignore
            }
        }
        for (String qualifiedName : types)
        {
            CommandImpl command = new CommandImpl();
            command.setCommand("nextgenas.addImport");
            command.setTitle("Import " + qualifiedName);
            command.setArguments(Collections.singletonList(qualifiedName));
            commands.add(command);
        }
    }

    /**
     * This feature is implemented at this time.
     */
    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params)
    {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    /**
     * This feature is implemented at this time.
     */
    @Override
    public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved)
    {
        return CompletableFuture.completedFuture(new CodeLensImpl());
    }

    /**
     * This feature is implemented at this time.
     */
    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params)
    {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    /**
     * This feature is implemented at this time.
     */
    @Override
    public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params)
    {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    /**
     * This feature is implemented at this time.
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
        IMXMLTagData offsetTag = getOffsetMXMLTag(params.getTextDocument(), params.getPosition());
        //if we're inside an <fx:Script> tag, we want ActionScript rename,
        //so that's why we call isMXMLTagValidForCompletion()
        if (offsetTag != null && isMXMLTagValidForCompletion(offsetTag))
        {
            return mxmlRename(params, offsetTag);
        }
        return actionScriptRename(params);
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
        Path path = getPathFromLsapiURI(textDocument.getUri());
        if (path != null)
        {
            String text = textDocument.getText();
            sourceByPath.put(path, text);

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
        Path path = getPathFromLsapiURI(textDocument.getUri());
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
        Path path = getPathFromLsapiURI(textDocument.getUri());
        if (path != null)
        {
            sourceByPath.remove(path);
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
     * changed, even if they are not considered open for editing. This includes
     * the asconfig.json file that holds compiler configuration settings, and
     * any ActionScript file in the workspace.
     */
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params)
    {
        boolean needsFullCheck = false;
        for (FileEvent event : params.getChanges())
        {
            Path path = getPathFromLsapiURI(event.getUri());
            if (path == null)
            {
                continue;
            }
            File file = path.toFile();
            String fileName = file.getName();
            if (fileName.equals(ASCONFIG_JSON))
            {
                //compiler settings may have changed, which means we should
                //start fresh
                asconfigChanged = true;
                needsFullCheck = true;
            }
            else if ((fileName.endsWith(AS_EXTENSION) || fileName.endsWith(MXML_EXTENSION))
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
        if (needsFullCheck)
        {
            if (currentOptions != null && currentOptions.type.equals(ProjectType.LIB))
            {
                Set<Path> filePaths = this.sourceByPath.keySet();
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
    }

    /**
     * Receives a callback to inform Visual Studio Code about "problems" (like
     * compiler errors or warnings) that need to be displayed to the user for
     * a specific file.
     */
    @Override
    public void onPublishDiagnostics(Consumer<PublishDiagnosticsParams> callback)
    {
        publishDiagnostics = callback;
    }

    private File getASConfigFile()
    {
        if (workspaceRoot == null)
        {
            return null;
        }
        Path asconfigPath = workspaceRoot.resolve(ASCONFIG_JSON);
        File asconfigFile = new File(asconfigPath.toUri());
        if (!asconfigFile.exists())
        {
            return null;
        }
        return asconfigFile;
    }

    private void loadASConfigFile()
    {
        if (!asconfigChanged && currentOptions != null)
        {
            //the options are fully up-to-date
            return;
        }
        //if the configuration changed, start fresh with a whole new workspace
        currentOptions = null;
        currentWorkspace = null;
        currentProject = null;
        fileSpecGetter = null;
        compilationUnits = null;
        File asconfigFile = getASConfigFile();
        if (asconfigFile == null)
        {
            return;
        }
        asconfigChanged = false;
        ProjectType type = ProjectType.APP;
        String config = null;
        String[] files = null;
        String additionalOptions = null;
        CompilerOptions compilerOptions = new CompilerOptions();
        try (InputStream schemaInputStream = getClass().getResourceAsStream("/schemas/asconfig.schema.json"))
        {
            JSONObject rawJsonSchema = new JSONObject(new JSONTokener(schemaInputStream));
            Schema schema = SchemaLoader.load(rawJsonSchema);
            String contents = FileUtils.readFileToString(asconfigFile);
            JSONObject json = new JSONObject(contents);
            schema.validate(json);
            if (json.has(ASConfigOptions.TYPE)) //optional, defaults to "app"
            {
                String typeString = json.getString(ASConfigOptions.TYPE);
                type = ProjectType.fromToken(typeString);
            }
            config = json.getString(ASConfigOptions.CONFIG);
            if (json.has(ASConfigOptions.FILES)) //optional
            {
                JSONArray jsonFiles = json.getJSONArray(ASConfigOptions.FILES);
                int fileCount = jsonFiles.length();
                files = new String[fileCount];
                for (int i = 0; i < fileCount; i++)
                {
                    String pathString = jsonFiles.getString(i);
                    Path filePath = workspaceRoot.resolve(pathString);
                    files[i] = filePath.toString();
                }
            }
            if (json.has(ASConfigOptions.COMPILER_OPTIONS)) //optional
            {
                JSONObject jsonCompilerOptions = json.getJSONObject(ASConfigOptions.COMPILER_OPTIONS);
                if (jsonCompilerOptions.has(CompilerOptions.DEBUG))
                {
                    compilerOptions.debug = jsonCompilerOptions.getBoolean(CompilerOptions.DEBUG);
                }
                if (jsonCompilerOptions.has(CompilerOptions.DEFINE))
                {
                    HashMap<String, String> defines = new HashMap<>();
                    JSONArray jsonDefine = jsonCompilerOptions.getJSONArray(CompilerOptions.DEFINE);
                    for (int i = 0, count = jsonDefine.length(); i < count; i++)
                    {
                        JSONObject jsonNamespace = jsonDefine.getJSONObject(i);
                        String name = jsonNamespace.getString(CompilerOptions.DEFINE_NAME);
                        Object value = jsonNamespace.get(CompilerOptions.DEFINE_VALUE);
                        if (value instanceof String)
                        {
                            value = "\"" + value + "\"";
                        }
                        defines.put(name, value.toString());
                    }
                    compilerOptions.defines = defines;
                }
                if (jsonCompilerOptions.has(CompilerOptions.EXTERNAL_LIBRARY_PATH))
                {
                    JSONArray jsonExternalLibraryPath = jsonCompilerOptions.getJSONArray(CompilerOptions.EXTERNAL_LIBRARY_PATH);
                    ArrayList<File> externalLibraryPath = new ArrayList<>();
                    for (int i = 0, count = jsonExternalLibraryPath.length(); i < count; i++)
                    {
                        String pathString = jsonExternalLibraryPath.getString(i);
                        Path filePath = workspaceRoot.resolve(pathString);
                        externalLibraryPath.add(filePath.toFile());
                    }
                    compilerOptions.externalLibraryPath = externalLibraryPath;
                }
                if (jsonCompilerOptions.has(CompilerOptions.INCLUDE_CLASSES))
                {
                    JSONArray jsonIncludeClasses = jsonCompilerOptions.getJSONArray(CompilerOptions.INCLUDE_CLASSES);
                    ArrayList<String> includeClasses = new ArrayList<>();
                    for (int i = 0, count = jsonIncludeClasses.length(); i < count; i++)
                    {
                        String qualifiedName = jsonIncludeClasses.getString(i);
                        includeClasses.add(qualifiedName);
                    }
                    compilerOptions.includeClasses = includeClasses;
                }
                if (jsonCompilerOptions.has(CompilerOptions.INCLUDE_NAMESPACES))
                {
                    JSONArray jsonIncludeNamespaces = jsonCompilerOptions.getJSONArray(CompilerOptions.INCLUDE_NAMESPACES);
                    ArrayList<String> includeNamespaces = new ArrayList<>();
                    for (int i = 0, count = jsonIncludeNamespaces.length(); i < count; i++)
                    {
                        String namespaceURI = jsonIncludeNamespaces.getString(i);
                        includeNamespaces.add(namespaceURI);
                    }
                    compilerOptions.includeNamespaces = includeNamespaces;
                }
                if (jsonCompilerOptions.has(CompilerOptions.INCLUDE_SOURCES))
                {
                    JSONArray jsonIncludeSources = jsonCompilerOptions.getJSONArray(CompilerOptions.INCLUDE_SOURCES);
                    ArrayList<File> includeSources = new ArrayList<>();
                    for (int i = 0, count = jsonIncludeSources.length(); i < count; i++)
                    {
                        String pathString = jsonIncludeSources.getString(i);
                        Path filePath = workspaceRoot.resolve(pathString);
                        includeSources.add(filePath.toFile());
                    }
                    compilerOptions.includeSources = includeSources;
                }
                if (jsonCompilerOptions.has(CompilerOptions.NAMESPACE))
                {
                    JSONArray jsonLibraryPath = jsonCompilerOptions.getJSONArray(CompilerOptions.NAMESPACE);
                    ArrayList<MXMLNamespaceMapping> namespaceMappings = new ArrayList<>();
                    for (int i = 0, count = jsonLibraryPath.length(); i < count; i++)
                    {
                        JSONObject jsonNamespace = jsonLibraryPath.getJSONObject(i);
                        String uri = jsonNamespace.getString(CompilerOptions.NAMESPACE_URI);
                        String manifest = jsonNamespace.getString(CompilerOptions.NAMESPACE_MANIFEST);
                        MXMLNamespaceMapping mapping = new MXMLNamespaceMapping(uri, manifest);
                        namespaceMappings.add(mapping);
                    }
                    compilerOptions.namespaceMappings = namespaceMappings;
                }
                if (jsonCompilerOptions.has(CompilerOptions.LIBRARY_PATH))
                {
                    JSONArray jsonLibraryPath = jsonCompilerOptions.getJSONArray(CompilerOptions.LIBRARY_PATH);
                    ArrayList<File> libraryPath = new ArrayList<>();
                    for (int i = 0, count = jsonLibraryPath.length(); i < count; i++)
                    {
                        String pathString = jsonLibraryPath.getString(i);
                        Path filePath = workspaceRoot.resolve(pathString);
                        libraryPath.add(filePath.toFile());
                    }
                    compilerOptions.libraryPath = libraryPath;
                }
                if (jsonCompilerOptions.has(CompilerOptions.SOURCE_PATH))
                {
                    JSONArray jsonSourcePath = jsonCompilerOptions.getJSONArray(CompilerOptions.SOURCE_PATH);
                    ArrayList<File> sourcePath = new ArrayList<>();
                    for (int i = 0, count = jsonSourcePath.length(); i < count; i++)
                    {
                        String pathString = jsonSourcePath.getString(i);
                        Path filePath = workspaceRoot.resolve(pathString);
                        sourcePath.add(filePath.toFile());
                    }
                    compilerOptions.sourcePath = sourcePath;
                }
                if (jsonCompilerOptions.has(CompilerOptions.WARNINGS))
                {
                    compilerOptions.warnings = jsonCompilerOptions.getBoolean(CompilerOptions.WARNINGS);
                }
            }
            //these options are formatted as if sent in through the command line
            if (json.has(ASConfigOptions.ADDITIONAL_OPTIONS)) //optional
            {
                additionalOptions = json.getString(ASConfigOptions.ADDITIONAL_OPTIONS);
            }
        }
        catch (ValidationException e)
        {
            System.err.println("Failed to validate asconfig.json: " + e);
            return;
        }
        catch (Exception e)
        {
            System.err.println("Failed to parse asconfig.json: " + e);
            e.printStackTrace();
            return;
        }
        //in a library project, the files field will be treated the same as the
        //include-sources compiler option
        if (type == ProjectType.LIB && files != null)
        {
            if (compilerOptions.includeSources == null)
            {
                compilerOptions.includeSources = new ArrayList<>();
            }
            for (int i = 0, count = files.length; i < count; i++)
            {
                String filePath = files[i];
                compilerOptions.includeSources.add(new File(filePath));
            }
            files = null;
        }
        ASConfigOptions options = new ASConfigOptions();
        options.type = type;
        options.config = config;
        options.files = files;
        options.compilerOptions = compilerOptions;
        options.additionalOptions = additionalOptions;
        currentOptions = options;
    }

    private CompletableFuture<CompletionList> actionScriptCompletion(TextDocumentPositionParams position)
    {
        CompletionListImpl result = new CompletionListImpl();
        result.setIncomplete(false);
        result.setItems(new ArrayList<>());

        //ActionScript completion
        IASNode offsetNode = getOffsetNode(position);
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return CompletableFuture.completedFuture(new CompletionListImpl());
        }
        IASNode parentNode = offsetNode.getParent();
        IASNode nodeAtPreviousOffset = null;
        if (parentNode != null)
        {
            nodeAtPreviousOffset = parentNode.getContainingNode(currentOffset - 1);
        }

        if (isInActionScriptComment(position))
        {
            //if we're inside a comment, no completion!
            return CompletableFuture.completedFuture(new CompletionListImpl());
        }

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
                    autoCompleteTypes(result);
                }
                return CompletableFuture.completedFuture(result);
            }
        }
        if (parentNode != null
                && parentNode instanceof IVariableNode)
        {
            IVariableNode variableNode = (IVariableNode) parentNode;
            if (offsetNode == variableNode.getVariableTypeNode())
            {
                autoCompleteTypes(result);
                return CompletableFuture.completedFuture(result);
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
                    autoCompleteTypes(result);
                    return CompletableFuture.completedFuture(result);
                }
            }
        }
        if (parentNode != null
                && parentNode instanceof IFunctionNode)
        {
            IFunctionNode functionNode = (IFunctionNode) parentNode;
            if (offsetNode == functionNode.getReturnTypeNode())
            {
                autoCompleteTypes(result);
                return CompletableFuture.completedFuture(result);
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
                autoCompleteTypes(result);
                return CompletableFuture.completedFuture(result);
            }
        }
        if (nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IKeywordNode
                && nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordNewID)
        {
            autoCompleteTypes(result);
            return CompletableFuture.completedFuture(result);
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
                autoCompleteTypes(result);
                return CompletableFuture.completedFuture(result);
            }
        }
        if (nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IBinaryOperatorNode
                && (nodeAtPreviousOffset.getNodeID() == ASTNodeID.Op_AsID
                || nodeAtPreviousOffset.getNodeID() == ASTNodeID.Op_IsID))
        {
            autoCompleteTypes(result);
            return CompletableFuture.completedFuture(result);
        }
        //class extends keyword
        if (offsetNode instanceof IClassNode
                && nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IKeywordNode
                && nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordExtendsID)
        {
            autoCompleteTypes(result);
            return CompletableFuture.completedFuture(result);
        }
        //class implements keyword
        if (offsetNode instanceof IClassNode
                && nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IKeywordNode
                && nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordImplementsID)
        {
            autoCompleteTypes(result);
            return CompletableFuture.completedFuture(result);
        }
        //interface extends keyword
        if (offsetNode instanceof IInterfaceNode
                && nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IKeywordNode
                && nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordExtendsID)
        {
            autoCompleteTypes(result);
            return CompletableFuture.completedFuture(result);
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
                return CompletableFuture.completedFuture(result);
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
                    return CompletableFuture.completedFuture(result);
                }
            }
        }
        if (nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IImportNode)
        {
            autoCompleteImport("", result);
            return CompletableFuture.completedFuture(result);
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
                    return CompletableFuture.completedFuture(result);
                }
            }
        }
        if (parentNode != null
                && parentNode instanceof IMemberAccessExpressionNode)
        {
            IMemberAccessExpressionNode memberAccessNode = (IMemberAccessExpressionNode) parentNode;
            //you would expect that the offset node could only be the right
            //operand, but it's actually possible for it to be the left operand,
            //even if the . has been typed!
            if (offsetNode == memberAccessNode.getRightOperandNode()
                    || offsetNode == memberAccessNode.getLeftOperandNode())
            {
                autoCompleteMemberAccess(memberAccessNode, result);
                return CompletableFuture.completedFuture(result);
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
                    return CompletableFuture.completedFuture(result);
                }
            }
        }

        //local scope
        do
        {
            //just keep traversing up until we get a scoped node or run out of
            //nodes to check
            if (offsetNode instanceof IScopedNode)
            {
                IScopedNode scopedNode = (IScopedNode) offsetNode;
                autoCompleteScope(scopedNode, result);
                return CompletableFuture.completedFuture(result);
            }
            offsetNode = offsetNode.getParent();
        }
        while (offsetNode != null);

        return CompletableFuture.completedFuture(result);
    }

    private CompletableFuture<CompletionList> mxmlCompletion(TextDocumentPositionParams position, IMXMLTagData offsetTag)
    {
        if (isInXMLComment(position))
        {
            //if we're inside a comment, no completion!
            return CompletableFuture.completedFuture(new CompletionListImpl());
        }
        CompletionListImpl result = new CompletionListImpl();
        result.setIncomplete(false);
        result.setItems(new ArrayList<>());

        IMXMLTagData parentTag = offsetTag.getParentTag();

        boolean isAttribute = offsetTag.isOffsetInAttributeList(currentOffset);
        boolean isChildOfOffsetTag = false;
        if (!isAttribute && !offsetTag.isCloseTag())
        {
            IMXMLTagData firstChild = offsetTag.getFirstChild(true);
            if (firstChild != null && currentOffset > firstChild.getAbsoluteStart())
            {
                isChildOfOffsetTag = true;
            }
        }

        //inside <fx:Declarations>
        if (isDeclarationsTag(offsetTag))
        {
            if (isChildOfOffsetTag)
            {
                autoCompleteTypesForMXML(result);
            }
            return CompletableFuture.completedFuture(result);
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
                        //tags can't appear in attributes, so skip types
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
                return CompletableFuture.completedFuture(result);
            }
            else if (isDeclarationsTag(parentTag))
            {
                autoCompleteTypesForMXMLFromExistingTag(result, offsetTag);
                return CompletableFuture.completedFuture(result);
            }
            return CompletableFuture.completedFuture(result);
        }
        if (offsetDefinition instanceof IClassDefinition)
        {
            IClassDefinition classDefinition = (IClassDefinition) offsetDefinition;
            addMembersForMXMLTypeToAutoComplete(classDefinition, offsetTag, !isAttribute, result);
            String defaultPropertyName = classDefinition.getDefaultPropertyName(currentProject);
            if (defaultPropertyName != null && !isAttribute)
            {
                //if [DefaultProperty] is set, then we can instantiate
                //types as child elements
                //but we don't want to do that when in an attribute
                autoCompleteTypesForMXML(result);
            }
            return CompletableFuture.completedFuture(result);
        }
        if (offsetDefinition instanceof IVariableDefinition && !isAttribute)
        {
            autoCompleteTypesForMXML(result);
            return CompletableFuture.completedFuture(result);
        }
        System.err.println("Unknown definition for MXML completion: " + offsetDefinition.getClass());
        return CompletableFuture.completedFuture(result);
    }

    private CompletableFuture<Hover> actionScriptHover(TextDocumentPositionParams position)
    {
        IDefinition definition = null;
        IASNode offsetNode = getOffsetNode(position);
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return CompletableFuture.completedFuture(new HoverImpl(Collections.emptyList(), null));
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
            return CompletableFuture.completedFuture(new HoverImpl(Collections.emptyList(), null));
        }

        HoverImpl result = new HoverImpl();
        String detail = getDefinitionDetail(definition);
        List<MarkedStringImpl> contents = new ArrayList<>();
        contents.add(markedString(detail));
        result.setContents(contents);
        return CompletableFuture.completedFuture(result);
    }

    private CompletableFuture<Hover> mxmlHover(TextDocumentPositionParams position, IMXMLTagData offsetTag)
    {
        IDefinition definition = getDefinitionForMXMLAtOffset(offsetTag, currentOffset);
        if (definition == null)
        {
            return CompletableFuture.completedFuture(new HoverImpl(Collections.emptyList(), null));
        }

        if (isInsideTagPrefix(offsetTag, currentOffset))
        {
            //inside the prefix
            String prefix = offsetTag.getPrefix();
            HoverImpl result = new HoverImpl();
            List<MarkedStringImpl> contents = new ArrayList<>();
            String detail = null;
            if (prefix.length() > 0)
            {
                detail = "xmlns:" + prefix + "=\"" + offsetTag.getURI() + "\"";
            }
            else
            {
                detail = "xmlns=\"" + offsetTag.getURI() + "\"";
            }
            contents.add(mxmlMarkedString(detail));
            result.setContents(contents);
            return CompletableFuture.completedFuture(result);
        }

        HoverImpl result = new HoverImpl();
        String detail = getDefinitionDetail(definition);
        List<MarkedStringImpl> contents = new ArrayList<>();
        contents.add(markedString(detail));
        result.setContents(contents);
        return CompletableFuture.completedFuture(result);
    }

    private CompletableFuture<List<? extends Location>> actionScriptDefinition(TextDocumentPositionParams position)
    {
        IASNode offsetNode = getOffsetNode(position);
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
        IDefinition definition = getDefinitionForMXMLAtOffset(offsetTag, currentOffset);
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
        IDefinition definition = getDefinitionForMXMLAtOffset(offsetTag, currentOffset);
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
        Path path = getPathFromLsapiURI(params.getTextDocument().getUri());
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
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return CompletableFuture.completedFuture(new WorkspaceEditImpl(new HashMap<>()));
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
            MessageParamsImpl message = new MessageParamsImpl();
            message.setType(MessageType.Info);
            message.setMessage("You cannot rename this element.");
            showMessageCallback.accept(message);
            return CompletableFuture.completedFuture(new WorkspaceEditImpl(new HashMap<>()));
        }

        WorkspaceEditImpl result = renameDefinition(definition, params.getNewName());
        return CompletableFuture.completedFuture(result);
    }

    private CompletableFuture<WorkspaceEdit> mxmlRename(RenameParams params, IMXMLTagData offsetTag)
    {
        IDefinition definition = getDefinitionForMXMLAtOffset(offsetTag, currentOffset);
        if (definition != null)
        {
            if (isInsideTagPrefix(offsetTag, currentOffset))
            {
                //ignore the tag's prefix
                return CompletableFuture.completedFuture(new WorkspaceEditImpl(new HashMap<>()));
            }
            WorkspaceEditImpl result = renameDefinition(definition, params.getNewName());
            return CompletableFuture.completedFuture(result);
        }

        MessageParamsImpl message = new MessageParamsImpl();
        message.setType(MessageType.Info);
        message.setMessage("You cannot rename this element.");
        showMessageCallback.accept(message);
        return CompletableFuture.completedFuture(new WorkspaceEditImpl(new HashMap<>()));
    }

    private void referencesForDefinition(IDefinition definition, List<Location> result)
    {
        for (ICompilationUnit compilationUnit : compilationUnits)
        {
            if (compilationUnit instanceof SWCCompilationUnit)
            {
                continue;
            }
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
                        LocationImpl location = sourceLocationToLocationImpl(otherUnit);
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
                continue;
            }
            ArrayList<IIdentifierNode> identifiers = new ArrayList<>();
            findIdentifiers(ast, definition, identifiers);
            for (IIdentifierNode otherNode : identifiers)
            {
                LocationImpl location = sourceLocationToLocationImpl(otherNode);
                if (location == null)
                {
                    continue;
                }
                result.add(location);
            }
        }
    }

    private RangeImpl sourceLocationToRangeImpl(ISourceLocation sourceLocation)
    {
        int line = sourceLocation.getLine();
        int column = sourceLocation.getColumn();
        if (line == -1 || column == -1)
        {
            //this is probably generated by the compiler somehow
            return null;
        }
        PositionImpl start = new PositionImpl();
        start.setLine(line);
        start.setCharacter(column);

        int endLine = sourceLocation.getEndLine();
        int endColumn = sourceLocation.getEndColumn();
        if (endLine == -1 || endColumn == -1)
        {
            endLine = line;
            endColumn = column;
        }
        PositionImpl end = new PositionImpl();
        end.setLine(endLine);
        end.setCharacter(endColumn);

        RangeImpl range = new RangeImpl();
        range.setStart(start);
        range.setEnd(end);

        return range;
    }

    private LocationImpl sourceLocationToLocationImpl(ISourceLocation sourceLocation)
    {
        Path sourceLocationPath = Paths.get(sourceLocation.getSourcePath());
        LocationImpl location = new LocationImpl();
        location.setUri(sourceLocationPath.toUri().toString());

        RangeImpl range = sourceLocationToRangeImpl(sourceLocation);
        if (range == null)
        {
            //this is probably generated by the compiler somehow
            return null;
        }
        location.setRange(range);

        return location;
    }

    private String getMXMLPrefixForDefinition(IDefinition definition, MXMLData mxmlData)
    {
        PrefixMap prefixMap = mxmlData.getRootTagPrefixMap();
        Collection<XMLName> tagNames = currentProject.getTagNamesForClass(definition.getQualifiedName());
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
            if (prefixes.length > 0)
            {
                return prefixes[0];
            }
        }
        return null;
    }

    private void autoCompleteTypes(CompletionListImpl result)
    {
        autoCompleteDefinitions(result, false, true, null, null);
    }

    private void autoCompleteTypesForMXML(CompletionListImpl result)
    {
        autoCompleteDefinitions(result, true, true, null, null);
    }

    /**
     * Using an existing tag, that may already have a prefix or short name,
     * populate the completion list.
     */
    private void autoCompleteTypesForMXMLFromExistingTag(CompletionListImpl result, IMXMLTagData offsetTag)
    {
        IMXMLDataManager mxmlDataManager = currentWorkspace.getMXMLDataManager();
        MXMLData mxmlData = (MXMLData) mxmlDataManager.get(fileSpecGetter.getFileSpecification(currentUnit.getAbsoluteFilename()));
        String tagStartShortName = offsetTag.getShortName();
        String tagPrefix = offsetTag.getPrefix();
        PrefixMap prefixMap = mxmlData.getRootTagPrefixMap();

        for (ICompilationUnit unit : compilationUnits)
        {
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

                //first check that the tag either doesn't have a short name yet
                //or that the definition's base name matches the short name 
                if (tagStartShortName.length() == 0 || definition.getBaseName().startsWith(tagStartShortName))
                {
                    //if a prefix already exists, make sure the definition is
                    //in a namespace with that prefix
                    if (tagPrefix.length() > 0)
                    {
                        Collection<XMLName> tagNames = currentProject.getTagNamesForClass(definition.getQualifiedName());
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
                                    addDefinitionAutoComplete(definition, result);
                                }
                            }
                        }
                    }
                    else
                    {
                        //no prefix, so complete the definition with a prefix
                        String prefix = getMXMLPrefixForDefinition(definition, mxmlData);
                        if (prefix == null)
                        {
                            prefix = getNumberedNamespacePrefix(DEFAULT_NS_PREFIX, prefixMap);
                        }
                        addDefinitionAutoComplete(definition,
                                prefix + IMXMLCoreConstants.colon,
                                result);
                    }
                }
            }
        }
    }

    private void autoCompleteDefinitions(CompletionListImpl result, boolean forMXML,
                                         boolean typesOnly, String packageName,
                                         IDefinition definitionToSkip)
    {
        String skipQualifiedName = null;
        if (definitionToSkip != null)
        {
            skipQualifiedName = definitionToSkip.getQualifiedName();
        }
        for (ICompilationUnit unit : compilationUnits)
        {
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
                    if (packageName == null || definition.getPackageName().equals(packageName))
                    {
                        if (skipQualifiedName != null
                                && skipQualifiedName.equals(definition.getQualifiedName()))
                        {
                            continue;
                        }
                        if (forMXML && isType)
                        {
                            ITypeDefinition typeDefinition = (ITypeDefinition) definition;
                            addMXMLTypeDefinitionAutoComplete(typeDefinition, result);
                        }
                        else
                        {
                            addDefinitionAutoComplete(definition, result);
                        }
                    }
                }
            }
        }
        if (packageName == null || packageName.equals(""))
        {
            CompletionItemImpl item = new CompletionItemImpl();
            item.setKind(CompletionItemKind.Class);
            item.setLabel(IASKeywordConstants.VOID);
            result.getItems().add(item);
        }
    }

    private void autoCompleteScope(IScopedNode node, CompletionListImpl result)
    {
        IASScope scope = node.getScope();
        IDefinition definitionToSkip = scope.getDefinition();
        //include definitions in the top-level package
        autoCompleteDefinitions(result, false, false, "", definitionToSkip);
        //include definitions in the same package
        String packageName = node.getPackageName();
        if (packageName != null && packageName.length() > 0)
        {
            autoCompleteDefinitions(result, false, false, packageName, definitionToSkip);
        }
        //include definitions that are imported from other packages
        ArrayList<IImportNode> importNodes = new ArrayList<>();
        node.getAllImportNodes(importNodes);
        for (IImportNode importNode : importNodes)
        {
            IDefinition importDefinition = null;
            try
            {
                //this can throw a null pointer exception when parsing MXML, for
                //some reason
                importDefinition = importNode.resolveImport(currentProject);
            }
            catch (Exception e)
            {
                //do nothing
            }
            if (importDefinition != null)
            {
                addDefinitionAutoComplete(importDefinition, result);
            }
        }
        //include all members and local things that are in scope
        IScopedNode currentNode = node;
        while (currentNode != null)
        {
            IASScope currentScope = currentNode.getScope();
            boolean staticOnly = currentNode == node
                    && currentScope instanceof TypeScope;
            for (IDefinition localDefinition : currentScope.getAllLocalDefinitions())
            {
                if (localDefinition.getBaseName().length() == 0)
                {
                    continue;
                }
                if (!staticOnly || localDefinition.isStatic())
                {
                    addDefinitionAutoComplete(localDefinition, result);
                }
            }
            currentNode = currentNode.getContainingScope();
        }
    }

    private void autoCompleteMemberAccess(IMemberAccessExpressionNode node, CompletionListImpl result)
    {
        ASScope scope = (ASScope) node.getContainingScope().getScope();
        IExpressionNode leftOperand = node.getLeftOperandNode();
        IDefinition leftDefinition = leftOperand.resolve(currentProject);
        if (leftDefinition != null && leftDefinition instanceof ITypeDefinition)
        {
            ITypeDefinition typeDefinition = (ITypeDefinition) leftDefinition;
            TypeScope typeScope = (TypeScope) typeDefinition.getContainedScope();
            addDefinitionsInTypeScopeToAutoComplete(typeScope, scope, true, result);
            return;
        }
        ITypeDefinition leftType = leftOperand.resolveType(currentProject);
        if (leftType != null)
        {
            TypeScope typeScope = (TypeScope) leftType.getContainedScope();
            addDefinitionsInTypeScopeToAutoComplete(typeScope, scope, false, result);
            return;
        }
    }

    private void autoCompleteImport(String importName, CompletionListImpl result)
    {
        List<CompletionItemImpl> items = result.getItems();
        for (ICompilationUnit unit : compilationUnits)
        {
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
                        CompletionItemImpl item = new CompletionItemImpl();
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

    private void addMembersForMXMLTypeToAutoComplete(IClassDefinition definition, IMXMLTagData offsetTag, boolean includePrefix, CompletionListImpl result)
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
                    propertyElementPrefix = prefix + IMXMLCoreConstants.colon;
                }
            }
            TypeScope typeScope = (TypeScope) definition.getContainedScope();
            ASScope scope = (ASScope) scopes[0];
            addDefinitionsInTypeScopeToAutoComplete(typeScope, scope, false, true, propertyElementPrefix, result);
            addStyleMetadataToAutoComplete(typeScope, result);
            addEventMetadataToAutoComplete(typeScope, result);
        }
    }

    private void addDefinitionsInTypeScopeToAutoComplete(TypeScope typeScope, ASScope otherScope, boolean isStatic, CompletionListImpl result)
    {
        addDefinitionsInTypeScopeToAutoComplete(typeScope, otherScope, isStatic, false, null, result);
    }

    private void addDefinitionsInTypeScopeToAutoComplete(TypeScope typeScope, ASScope otherScope, boolean isStatic, boolean forMXML, String prefix, CompletionListImpl result)
    {
        IMetaTag[] excludeMetaTags = typeScope.getDefinition().getMetaTagsByName(IMetaAttributeConstants.ATTRIBUTE_EXCLUDE);
        ArrayList<IDefinition> memberAccessDefinitions = new ArrayList<>();
        Set<INamespaceDefinition> namespaceSet = otherScope.getNamespaceSet(currentProject);
        if (typeScope.getContainingDefinition() instanceof IInterfaceDefinition)
        {
            //interfaces have a special namespace that isn't actually the same
            //as public, but should be treated the same way
            namespaceSet.addAll(typeScope.getNamespaceSet(currentProject));
        }
        typeScope.getAllPropertiesForMemberAccess((CompilerProject) currentProject, memberAccessDefinitions, namespaceSet);
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
                if (localDefinition.getParent() != typeScope.getContainingDefinition())
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
            addDefinitionAutoComplete(localDefinition, prefix, result);
        }
    }

    private void addEventMetadataToAutoComplete(TypeScope typeScope, CompletionListImpl result)
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
                CompletionItemImpl item = new CompletionItemImpl();
                item.setKind(CompletionItemKind.Field);
                item.setLabel(eventName);
                item.setDetail(getDefinitionDetail(eventDefinition));
                result.getItems().add(item);
            }
            definition = classDefinition.resolveBaseClass(currentProject);
        }
    }

    private void addStyleMetadataToAutoComplete(TypeScope typeScope, CompletionListImpl result)
    {
        ArrayList<String> styleNames = new ArrayList<>();
        IDefinition definition = typeScope.getDefinition();
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
                CompletionItemImpl item = new CompletionItemImpl();
                item.setKind(CompletionItemKind.Field);
                item.setLabel(styleName);
                item.setDetail(getDefinitionDetail(styleDefinition));
                result.getItems().add(item);
            }
            definition = classDefinition.resolveBaseClass(currentProject);
        }
    }

    private void addMXMLTypeDefinitionAutoComplete(ITypeDefinition definition, CompletionListImpl result)
    {
        IMXMLDataManager mxmlDataManager = currentWorkspace.getMXMLDataManager();
        MXMLData mxmlData = (MXMLData) mxmlDataManager.get(fileSpecGetter.getFileSpecification(currentUnit.getAbsoluteFilename()));
        PrefixMap prefixMap = mxmlData.getRootTagPrefixMap();

        //first, try to use one of the existing prefixes in the document
        Collection<XMLName> tagNames = currentProject.getTagNamesForClass(definition.getQualifiedName());
        String discoveredPrefix = getMXMLPrefixForDefinition(definition, mxmlData);
        if (discoveredPrefix != null)
        {
            addDefinitionAutoComplete(definition, discoveredPrefix + IMXMLCoreConstants.colon, result);
            return;
        }
        String fallbackNamespace = null;
        if (tagNames.size() > 0 && fallbackNamespace == null)
        {
            XMLName tagName = tagNames.iterator().next();
            fallbackNamespace = tagName.getXMLNamespace();
        }

        if (fallbackNamespace != null)
        {
            //this type is in a namespace
            //let's try to figure out a nice prefix to use
            String prefix = null;

            //first, we'll check if the namespace comes from a known library
            //with a common prefix
            if (NAMESPACE_TO_PREFIX.containsKey(fallbackNamespace))
            {
                prefix = NAMESPACE_TO_PREFIX.get(fallbackNamespace);
                if (prefixMap.containsPrefix(prefix))
                {
                    //the prefix already exists, so we can't use it
                    prefix = null;
                }
                else
                {
                    prefix += IMXMLCoreConstants.colon;
                }
            }
            //if we can't find a known library, we'll generate a prefix
            if (prefix == null)
            {
                prefix = getNumberedNamespacePrefix(DEFAULT_NS_PREFIX, prefixMap) + IMXMLCoreConstants.colon;
            }
            addDefinitionAutoComplete(definition, prefix, result);
            return;
        }

        //try to find a prefix based on the package name
        String[] prefixes = prefixMap.getPrefixesForNamespace(definition.getPackageName() + DOT_STAR);
        if (prefixes.length > 0)
        {
            //use an existing prefix, if possible
            String prefix = prefixes[0] + IMXMLCoreConstants.colon;
            addDefinitionAutoComplete(definition, prefix, result);
            return;
        }
        //finally, our last option is to generate a prefix
        String prefix = getNumberedNamespacePrefix(DEFAULT_NS_PREFIX, prefixMap) + IMXMLCoreConstants.colon;
        addDefinitionAutoComplete(definition, prefix, result);
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

    private void addDefinitionAutoComplete(IDefinition definition, CompletionListImpl result)
    {
        addDefinitionAutoComplete(definition, null, result);
    }

    private void addDefinitionAutoComplete(IDefinition definition, String prefix, CompletionListImpl result)
    {
        CompletionItemImpl item = new CompletionItemImpl();
        if (definition instanceof IClassDefinition)
        {
            item.setKind(CompletionItemKind.Class);
        }
        else if (definition instanceof IInterfaceDefinition)
        {
            item.setKind(CompletionItemKind.Interface);
        }
        else if (definition instanceof IFunctionDefinition)
        {
            IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
            if (functionDefinition.isConstructor())
            {
                //ignore constructors
                return;
            }
            item.setKind(CompletionItemKind.Function);
        }
        else if (definition instanceof IVariableDefinition)
        {
            item.setKind(CompletionItemKind.Variable);
        }
        item.setDetail(getDefinitionDetail(definition));
        item.setLabel(definition.getBaseName());
        if (prefix != null)
        {
            item.setInsertText(prefix + definition.getBaseName());
        }
        result.getItems().add(item);
    }

    private void addKeywordAutoComplete(String keyword, CompletionListImpl result)
    {
        CompletionItemImpl item = new CompletionItemImpl();
        item.setKind(CompletionItemKind.Keyword);
        item.setLabel(keyword);
        result.getItems().add(item);
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
            //however, getContainingFilePath() also works for SWCs, so only
            //allow the files we support
            if (!definitionPath.endsWith(AS_EXTENSION)
                    && !definitionPath.endsWith(MXML_EXTENSION))
            {
                //if it's in a SWC or something, we don't know how to resolve
                return;
            }
        }

        Path resolvedPath = Paths.get(definitionPath);
        LocationImpl location = new LocationImpl();
        location.setUri(resolvedPath.toUri().toString());
        PositionImpl start = new PositionImpl();
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

            PositionImpl position = new PositionImpl();
            offsetToLineAndCharacter(reader, nameOffset, position);
            nameLine = position.getLine();
            nameColumn = position.getCharacter();
        }
        if (nameLine == -1 || nameColumn == -1)
        {
            //we can't find the name, so give up
            return;
        }
        start.setLine(nameLine);
        start.setCharacter(nameColumn);
        PositionImpl end = new PositionImpl();
        end.setLine(nameLine);
        end.setCharacter(nameColumn + definition.getNameEnd() - definition.getNameStart());
        RangeImpl range = new RangeImpl();
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

    private MarkedStringImpl markedString(String value)
    {
        MarkedStringImpl result = new MarkedStringImpl();

        result.setLanguage("nextgenas");
        result.setValue(value);

        return result;
    }

    private MarkedStringImpl mxmlMarkedString(String value)
    {
        MarkedStringImpl result = new MarkedStringImpl();

        result.setLanguage("mxml");
        result.setValue(value);

        return result;
    }

    private WorkspaceEditImpl renameDefinition(IDefinition definition, String newName)
    {
        if (definition == null)
        {
            MessageParamsImpl message = new MessageParamsImpl();
            message.setType(MessageType.Info);
            message.setMessage("You cannot rename this element.");
            showMessageCallback.accept(message);
            return new WorkspaceEditImpl(new HashMap<>());
        }
        WorkspaceEditImpl result = new WorkspaceEditImpl();
        Map<String, List<TextEditImpl>> changes = new HashMap<>();
        result.setChanges(changes);
        if (definition.getContainingFilePath().endsWith(SWC_EXTENSION))
        {
            MessageParamsImpl message = new MessageParamsImpl();
            message.setType(MessageType.Info);
            message.setMessage("You cannot rename an element defined in a SWC file.");
            showMessageCallback.accept(message);
            return result;
        }
        if (definition instanceof IPackageDefinition)
        {
            MessageParamsImpl message = new MessageParamsImpl();
            message.setType(MessageType.Info);
            message.setMessage("You cannot rename a package.");
            showMessageCallback.accept(message);
            return result;
        }
        IDefinition parentDefinition = definition.getParent();
        if (parentDefinition != null && parentDefinition instanceof IPackageDefinition)
        {
            MessageParamsImpl message = new MessageParamsImpl();
            message.setType(MessageType.Info);
            message.setMessage("You cannot rename this element.");
            showMessageCallback.accept(message);
            return result;
        }
        for (ICompilationUnit compilationUnit : compilationUnits)
        {
            if (compilationUnit instanceof SWCCompilationUnit)
            {
                continue;
            }
            ArrayList<TextEditImpl> textEdits = new ArrayList<>();
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
                        TextEditImpl textEdit = new TextEditImpl();
                        textEdit.setNewText(newName);

                        RangeImpl range = sourceLocationToRangeImpl(otherUnit);
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
                    TextEditImpl textEdit = new TextEditImpl();
                    textEdit.setNewText(newName);

                    RangeImpl range = sourceLocationToRangeImpl(identifierNode);
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
            changes.put(uri.toString(), textEdits);
        }
        return result;
    }

    private void findIdentifiers(IASNode node, IDefinition definition, List<IIdentifierNode> result)
    {
        if (node.isTerminal())
        {
            if (node instanceof IIdentifierNode)
            {
                IIdentifierNode identifierNode = (IIdentifierNode) node;
                if (identifierNode.resolve(currentProject) == definition)
                {
                    result.add(identifierNode);
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

    private DiagnosticSeverity getCompilerProblemSeverity(ICompilerProblem problem)
    {
        CompilerProblemCategorizer categorizer = new CompilerProblemCategorizer(null);
        CompilerProblemSeverity severity = categorizer.getProblemSeverity(problem);
        switch (severity)
        {
            case ERROR:
            {
                return DiagnosticSeverity.Error;
            }
            case WARNING:
            {
                return DiagnosticSeverity.Warning;
            }
            default:
            {
                return DiagnosticSeverity.Information;
            }
        }
    }

    private String patch(String sourceText, TextDocumentContentChangeEvent change)
    {
        try
        {
            Range range = change.getRange();
            BufferedReader reader = new BufferedReader(new StringReader(sourceText));
            StringWriter writer = new StringWriter();

            // Skip unchanged lines
            int line = 0;

            while (line < range.getStart().getLine())
            {
                writer.write(reader.readLine() + '\n');
                line++;
            }

            // Skip unchanged chars
            for (int character = 0; character < range.getStart().getCharacter(); character++)
            {
                writer.write(reader.read());
            }

            // Write replacement text
            writer.write(change.getText());

            // Skip replaced text
            reader.skip(change.getRangeLength());

            // Write remaining text
            while (true)
            {
                int next = reader.read();

                if (next == -1)
                    return writer.toString();
                else
                    writer.write(next);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private Optional<Path> getFilePath(URI uri)
    {
        if (!uri.getScheme().equals("file"))
        {
            return Optional.empty();
        }
        else
        {
            return Optional.of(Paths.get(uri));
        }
    }

    private RangeImpl getSourceLocationRange(ISourceLocation problem)
    {
        int line = problem.getLine();
        if (line < 0)
        {
            line = 0;
        }
        int column = problem.getColumn();
        if (column < 0)
        {
            column = 0;
        }
        int endLine = problem.getEndLine();
        if (endLine < 0)
        {
            endLine = line;
        }
        int endColumn = problem.getEndColumn();
        if (endColumn < 0)
        {
            endColumn = column;
        }
        PositionImpl start = new PositionImpl();
        start.setLine(line);
        start.setCharacter(column);

        PositionImpl end = new PositionImpl();
        end.setLine(endLine);
        end.setCharacter(endColumn);

        RangeImpl range = new RangeImpl();
        range.setStart(start);
        range.setEnd(end);

        return range;
    }

    private void addCompilerProblem(ICompilerProblem problem, PublishDiagnosticsParams publish)
    {
        DiagnosticImpl diagnostic = new DiagnosticImpl();

        DiagnosticSeverity severity = getCompilerProblemSeverity(problem);
        diagnostic.setSeverity(severity);

        RangeImpl range = getSourceLocationRange(problem);
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

        List diagnostics = publish.getDiagnostics();
        diagnostics.add(diagnostic);
    }

    private void checkFilePathForSyntaxProblems(Path path)
    {
        URI uri = path.toUri();
        PublishDiagnosticsParamsImpl publish = new PublishDiagnosticsParamsImpl();
        ArrayList<DiagnosticImpl> diagnostics = new ArrayList<>();
        publish.setDiagnostics(diagnostics);
        publish.setUri(uri.toString());
        trackFileWithErrors(uri);

        Reader reader = getReaderForPath(path);
        if (reader == null)
        {
            //we can't get the code at all
            System.err.println("Cannot create Reader for path: " + path.toAbsolutePath().toString());
            return;
        }
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
        ASParser parser = new ASParser(workspace, buffer);
        FileNode node = new FileNode(workspace);
        try
        {
            parser.file(node);
        }
        catch (Exception e)
        {
            parser = null;
            System.err.println("Failed to parse file (" + path.toString() + "): " + e);
            e.printStackTrace();
        }
        //if an error occurred above, parser will be null
        if (parser != null)
        {
            for (ICompilerProblem problem : parser.getSyntaxProblems())
            {
                addCompilerProblem(problem, publish);
            }
        }

        DiagnosticImpl diagnostic = new DiagnosticImpl();
        diagnostic.setSeverity(DiagnosticSeverity.Information);

        File asconfigFile = getASConfigFile();
        if (parser == null)
        {
            //something terrible happened, and this is the best we can do
            diagnostic.setMessage("A fatal error occurred while checking for simple syntax problems.");
        }
        else if (currentOptions == null)
        {
            if (asconfigFile == null)
            {
                diagnostic.setMessage("Cannot find asconfig.json. Error checking disabled, except for simple syntax problems.");
            }
            else
            {
                //we found asconfig.json, but something went wrong while
                //attempting to parse it
                diagnostic.setMessage("Failed to parse asconfig.json. Error checking disabled, except for simple syntax problems.");
            }
        }
        else
        {
            //we loaded and parsed asconfig.json, so something went wrong while
            //checking for errors.
            diagnostic.setMessage("A fatal error occurred while checking for errors. Error checking disabled, except for simple syntax problems.");
        }

        RangeImpl range = new RangeImpl();
        range.setStart(new PositionImpl());
        range.setEnd(new PositionImpl());
        diagnostic.setRange(range);
        diagnostics.add(diagnostic);

        cleanUpStaleErrors();
        publishDiagnostics.accept(publish);
    }

    private ICompilationUnit findCompilationUnit(String absoluteFileName)
    {
        if (compilationUnits == null)
        {
            return null;
        }
        for (ICompilationUnit unit : compilationUnits)
        {
            if (unit.getAbsoluteFilename().equals(absoluteFileName))
            {
                return unit;
            }
        }
        return null;
    }

    private Path getMainCompilationUnitPath()
    {
        loadASConfigFile();
        if (currentOptions == null)
        {
            return null;
        }
        String[] files = currentOptions.files;
        if (files == null || files.length == 0)
        {
            return null;
        }
        String lastFilePath = files[files.length - 1];
        return Paths.get(lastFilePath);
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
        String[] files = currentOptions.files;
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
                IInvisibleCompilationUnit unit = currentProject.createInvisibleCompilationUnit(file, fileSpecGetter);
                if (unit == null)
                {
                    System.err.println("Could not create compilation unit for file: " + file);
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
                if (unit.getAbsoluteFilename().equals(absolutePath))
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
                System.err.println("Could not create compilation unit for file: " + absolutePath);
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
        if (ast instanceof IFileNode)
        {
            IFileNode fileNode = (IFileNode) ast;
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

    private FlexProject getProject()
    {
        clearInvisibleCompilationUnits();
        loadASConfigFile();
        if (currentOptions == null)
        {
            return null;
        }
        FlexJSProject project = null;
        if (currentWorkspace == null)
        {
            currentWorkspace = new Workspace();
            project = new FlexJSProject((Workspace) currentWorkspace);
            fileSpecGetter = new LanguageServerFileSpecGetter(currentWorkspace, sourceByPath);
        }
        else
        {
            return currentProject;
        }
        CompilerOptions compilerOptions = currentOptions.compilerOptions;
        Configurator configurator = new Configurator(JSGoogConfiguration.class);
        configurator.setToken("configname", currentOptions.config);
        ProjectType type = currentOptions.type;
        String[] files = currentOptions.files;
        String additionalOptions = currentOptions.additionalOptions;
        ArrayList<String> combinedOptions = new ArrayList<>();
        if (additionalOptions != null)
        {
            String[] splitOptions = additionalOptions.split("\\s+");
            combinedOptions.addAll(Arrays.asList(splitOptions));
        }
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
        boolean result = configurator.applyToProject(project);
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
        if (currentOptions.type.equals(ProjectType.LIB))
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
        if (!result)
        {
            return null;
        }
        ITarget.TargetType targetType = ITarget.TargetType.SWF;
        if (currentOptions.type.equals(ProjectType.LIB))
        {
            targetType = ITarget.TargetType.SWC;
        }
        ITargetSettings targetSettings = configurator.getTargetSettings(targetType);
        if (targetSettings == null)
        {
            System.err.println("Failed to get compile settings for +configname=" + currentOptions.config + ".");
            return null;
        }
        project.setTargetSettings(targetSettings);
        return project;
    }

    private void cleanUpStaleErrors()
    {
        //if any files have been removed, they will still appear in this set, so
        //clear the errors so that they don't persist
        for (URI uri : oldFilesWithErrors)
        {
            PublishDiagnosticsParamsImpl publish = new PublishDiagnosticsParamsImpl();
            publish.setDiagnostics(new ArrayList<>());
            publish.setUri(uri.toString());
            publishDiagnostics.accept(publish);
        }
        oldFilesWithErrors.clear();
        HashSet<URI> temp = newFilesWithErrors;
        newFilesWithErrors = oldFilesWithErrors;
        oldFilesWithErrors = temp;
    }

    private void trackFileWithErrors(URI uri)
    {
        newFilesWithErrors.add(uri);
        oldFilesWithErrors.remove(uri);
    }

    private void checkFilePathForProblems(Path path, Boolean quick)
    {
        currentUnit = null;
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
            PublishDiagnosticsParamsImpl publish = new PublishDiagnosticsParamsImpl();
            publish.setDiagnostics(new ArrayList<>());
            publish.setUri(uri.toString());
            trackFileWithErrors(uri);
            for (ICompilerProblem problem : fatalProblems)
            {
                addCompilerProblem(problem, publish);
            }
            cleanUpStaleErrors();
            publishDiagnostics.accept(publish);
            return true;
        }
        IASNode ast = null;
        try
        {
            ast = mainUnit.getSyntaxTreeRequest().get().getAST();
        }
        catch (Exception e)
        {
            return false;
        }
        if (ast == null)
        {
            return false;
        }
        Map<URI, PublishDiagnosticsParamsImpl> files = new HashMap<>();
        try
        {
            if (quick)
            {
                PublishDiagnosticsParamsImpl params = checkCompilationUnitForAllProblems(mainUnit);
                if (params == null)
                {
                    return false;
                }
                URI uri = Paths.get(mainUnit.getAbsoluteFilename()).toUri();
                files.put(uri, params);
            }
            else
            {
                for (ICompilationUnit unit : compilationUnits)
                {
                    if (unit instanceof SWCCompilationUnit)
                    {
                        //compiled compilation units won't have problems
                        continue;
                    }
                    PublishDiagnosticsParamsImpl params = checkCompilationUnitForAllProblems(unit);
                    if (params == null)
                    {
                        return false;
                    }
                    URI uri = Paths.get(unit.getAbsoluteFilename()).toUri();
                    files.put(uri, params);
                }
                //only clean up stale errors on a full check
                cleanUpStaleErrors();
            }
        }
        catch (Exception e)
        {
            System.err.println("Exception during build: " + e);
            e.printStackTrace();
            return false;
        }
        files.values().forEach(publishDiagnostics::accept);
        return true;
    }

    private PublishDiagnosticsParamsImpl checkCompilationUnitForAllProblems(ICompilationUnit unit)
    {
        URI uri = Paths.get(unit.getAbsoluteFilename()).toUri();
        PublishDiagnosticsParamsImpl publish = new PublishDiagnosticsParamsImpl();
        publish.setDiagnostics(new ArrayList<>());
        publish.setUri(uri.toString());
        trackFileWithErrors(uri);
        ArrayList<ICompilerProblem> problems = new ArrayList<>();
        try
        {
            unit.waitForBuildFinish(problems, ITarget.TargetType.SWF);
        }
        catch (Exception e)
        {
            System.err.println("Exception during waitForBuildFinish(): " + e);
            e.printStackTrace();
            return null;
        }
        for (ICompilerProblem problem : problems)
        {
            addCompilerProblem(problem, publish);
        }
        return publish;
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
            //if the file is not open, read it from the file system
            try
            {
                reader = new FileReader(path.toAbsolutePath().toString());
            }
            catch (FileNotFoundException e)
            {
                //do nothing
            }
        }
        return reader;
    }

    private Path getPathFromLsapiURI(String apiURI)
    {
        URI uri = URI.create(apiURI);
        Optional<Path> optionalPath = getFilePath(uri);
        if (!optionalPath.isPresent())
        {
            System.err.println("Could not find URI " + uri);
            return null;
        }
        return optionalPath.get();
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
        String uri = textDocument.getUri();
        if (!uri.endsWith(MXML_EXTENSION))
        {
            // don't try to parse ActionScript files as MXML
            return null;
        }
        currentOffset = -1;
        Path path = getPathFromLsapiURI(uri);
        if (path == null)
        {
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

        currentOffset = lineAndCharacterToOffset(new StringReader(code),
                position.getLine(),
                position.getCharacter());
        if (currentOffset == -1)
        {
            System.err.println("Could not find code at position " + position.getLine() + ":" + position.getCharacter() + " in file " + path.toAbsolutePath().toString());
            return null;
        }

        IMXMLTagData tagData = mxmlData.findTagOrSurroundingTagContainingOffset(currentOffset);
        if (tagData != null)
        {
            return tagData;
        }
        return mxmlData.findTagOrSurroundingTagContainingOffset(currentOffset - 1);
    }

    private IASNode getOffsetNode(TextDocumentPositionParams position)
    {
        return getOffsetNode(position.getTextDocument(), position.getPosition());
    }

    private IASNode getOffsetNode(TextDocumentIdentifier textDocument, Position position)
    {
        currentOffset = -1;
        Path path = getPathFromLsapiURI(textDocument.getUri());
        if (path == null)
        {
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

        currentOffset = lineAndCharacterToOffset(new StringReader(code),
                position.getLine(),
                position.getCharacter());
        if (currentOffset == -1)
        {
            System.err.println("Could not find code at position " + position.getLine() + ":" + position.getCharacter() + " in file " + path.toAbsolutePath().toString());
            return null;
        }
        return ast.getContainingNode(currentOffset);
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

    private IDefinition getDefinitionForMXMLAtOffset(IMXMLTagData tag, int offset)
    {
        if (tag.isOffsetInAttributeList(offset))
        {
            return getDefinitionForMXMLTagAttribute(tag, offset);
        }
        return getDefinitionForMXMLTag(tag);
    }

    private IMXMLTagAttributeData getMXMLTagAttributeAtOffset(IMXMLTagData tag, int offset)
    {
        IMXMLTagAttributeData[] attributes = tag.getAttributeDatas();
        for (IMXMLTagAttributeData attributeData : attributes)
        {
            if (offset >= attributeData.getAbsoluteStart()
                    && offset < attributeData.getAbsoluteEnd())
            {
                return attributeData;
            }
        }
        return null;
    }

    private IMXMLTagAttributeData getMXMLTagAttributeWithValueAtOffset(IMXMLTagData tag, int offset)
    {
        IMXMLTagAttributeData[] attributes = tag.getAttributeDatas();
        for (IMXMLTagAttributeData attributeData : attributes)
        {
            if (offset >= attributeData.getValueStart()
                    && offset < attributeData.getValueEnd())
            {
                return attributeData;
            }
        }
        return null;
    }

    private IDefinition getDefinitionForMXMLTagAttribute(IMXMLTagData tag, int offset)
    {
        IMXMLTagAttributeData attributeData = getMXMLTagAttributeAtOffset(tag, offset);
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
            detailBuilder.append(classDefinition.getQualifiedName());
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
                //an IAccessoryDefinition actually extends both
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

    private boolean isInActionScriptComment(TextDocumentPositionParams params)
    {
        TextDocumentIdentifier textDocument = params.getTextDocument();
        Path path = getPathFromLsapiURI(textDocument.getUri());
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
        Path path = getPathFromLsapiURI(textDocument.getUri());
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
        return tag != null && tag.getShortName().equals(IMXMLLanguageConstants.DECLARATIONS)
                && tag.getURI().equals(IMXMLLanguageConstants.NAMESPACE_MXML_2009);
    }

    private void querySymbolsInScope(String query, IASScope scope, List<SymbolInformationImpl> result)
    {
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
                        && (typeDefinition.getBaseName().startsWith(query)
                        || typeDefinition.getQualifiedName().startsWith(query)))
                {
                    SymbolInformationImpl symbol = definitionToSymbol(typeDefinition);
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
                if (functionDefinition.getBaseName().startsWith(query)
                        || functionDefinition.getQualifiedName().startsWith(query))
                {
                    SymbolInformationImpl symbol = definitionToSymbol(functionDefinition);
                    result.add(symbol);
                }
                IASScope functionScope = functionDefinition.getContainedScope();
                querySymbolsInScope(query, functionScope, result);
            }
            else if (definition instanceof IVariableDefinition)
            {
                if (definition.isImplicit())
                {
                    continue;
                }
                IVariableDefinition variableDefinition = (IVariableDefinition) definition;
                if (variableDefinition.getBaseName().startsWith(query)
                        || variableDefinition.getQualifiedName().startsWith(query))
                {
                    SymbolInformationImpl symbol = definitionToSymbol(variableDefinition);
                    result.add(symbol);
                }
            }
        }
    }

    private IFunctionDefinition classDefinitionToConstructor(IClassDefinition definition)
    {
        IASScope scope = definition.getContainedScope();
        Collection<IDefinition> definitions = scope.getAllLocalDefinitions();
        for (IDefinition localDefinition : definitions)
        {
            if (localDefinition instanceof IFunctionDefinition)
            {
                IFunctionDefinition functionDefinition = (IFunctionDefinition) localDefinition;
                if (functionDefinition.isConstructor())
                {
                    return functionDefinition;
                }
            }
        }
        return null;
    }

    private void scopeToSymbols(IASScope scope, List<SymbolInformationImpl> result)
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
                    SymbolInformationImpl typeSymbol = definitionToSymbol(typeDefinition);
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
                SymbolInformationImpl localSymbol = definitionToSymbol(definition);
                result.add(localSymbol);
            }
        }
    }

    private SymbolInformationImpl definitionToSymbol(IDefinition definition)
    {
        SymbolInformationImpl symbol = new SymbolInformationImpl();
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
        symbol.setName(definition.getQualifiedName());
        LocationImpl location = new LocationImpl();
        String sourcePath = definition.getSourcePath();
        if (sourcePath == null)
        {
            //I'm not sure why getSourcePath() can sometimes return null, but
            //getContainingFilePath() seems to work as a fallback -JT
            sourcePath = definition.getContainingFilePath();
        }
        Path definitionPath = Paths.get(sourcePath);
        location.setUri(definitionPath.toUri().toString());
        PositionImpl start = new PositionImpl();
        PositionImpl end = new PositionImpl();
        int line = definition.getLine();
        int column = definition.getColumn();
        if (line < 0 || column < 0)
        {
            //this is not ideal, but MXML variable definitions may not have a
            //node associated with them, so we need to figure this out from the
            //offset instead of a pre-calculated line and column -JT
            String code = sourceByPath.get(Paths.get(sourcePath));
            offsetToLineAndCharacter(new StringReader(code), definition.getNameStart(), start);
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
        RangeImpl range = new RangeImpl();
        range.setStart(start);
        range.setEnd(end);
        location.setRange(range);
        symbol.setLocation(location);
        return symbol;
    }

    private static void offsetToLineAndCharacter(Reader in, int targetOffset, PositionImpl result)
    {
        try
        {
            int offset = 0;
            int line = 0;
            int character = 0;

            while (offset < targetOffset)
            {
                int next = in.read();

                if (next < 0)
                {
                    result.setLine(line);
                    result.setCharacter(line);
                    return;
                }
                else
                {
                    offset++;
                    character++;

                    if (next == '\n')
                    {
                        line++;
                        character = 0;
                    }
                }
            }

            result.setLine(line);
            result.setCharacter(character);
        }
        catch (IOException e)
        {
            result.setLine(-1);
            result.setCharacter(-1);
        }
    }

    private static int lineAndCharacterToOffset(Reader in, int targetLine, int targetCharacter)
    {
        try
        {
            int offset = 0;
            int line = 0;
            int character = 0;

            while (line < targetLine)
            {
                int next = in.read();

                if (next < 0)
                {
                    return offset;
                }
                else
                {
                    offset++;

                    if (next == '\n')
                    {
                        line++;
                    }
                }
            }

            while (character < targetCharacter)
            {
                int next = in.read();

                if (next < 0)
                {
                    return offset;
                }
                else
                {
                    offset++;
                    character++;
                }
            }

            return offset;
        }
        catch (IOException e)
        {
            return -1;
        }
    }
}
