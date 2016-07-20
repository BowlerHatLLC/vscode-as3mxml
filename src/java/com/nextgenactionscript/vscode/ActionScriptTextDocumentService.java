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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.apache.flex.compiler.clients.problems.CompilerProblemCategorizer;
import org.apache.flex.compiler.common.ISourceLocation;
import org.apache.flex.compiler.config.Configurator;
import org.apache.flex.compiler.config.ICompilerSettingsConstants;
import org.apache.flex.compiler.constants.IASKeywordConstants;
import org.apache.flex.compiler.constants.IASLanguageConstants;
import org.apache.flex.compiler.definitions.IAccessorDefinition;
import org.apache.flex.compiler.definitions.IClassDefinition;
import org.apache.flex.compiler.definitions.IConstantDefinition;
import org.apache.flex.compiler.definitions.IDefinition;
import org.apache.flex.compiler.definitions.IFunctionDefinition;
import org.apache.flex.compiler.definitions.IInterfaceDefinition;
import org.apache.flex.compiler.definitions.IPackageDefinition;
import org.apache.flex.compiler.definitions.IParameterDefinition;
import org.apache.flex.compiler.definitions.ITypeDefinition;
import org.apache.flex.compiler.definitions.IVariableDefinition;
import org.apache.flex.compiler.internal.driver.js.goog.JSGoogConfiguration;
import org.apache.flex.compiler.internal.parsing.as.ASParser;
import org.apache.flex.compiler.internal.parsing.as.ASToken;
import org.apache.flex.compiler.internal.parsing.as.RepairingTokenBuffer;
import org.apache.flex.compiler.internal.parsing.as.StreamingASTokenizer;
import org.apache.flex.compiler.internal.projects.CompilerProject;
import org.apache.flex.compiler.internal.projects.FlexJSProject;
import org.apache.flex.compiler.internal.tree.as.FileNode;
import org.apache.flex.compiler.internal.units.SWCCompilationUnit;
import org.apache.flex.compiler.internal.workspaces.Workspace;
import org.apache.flex.compiler.problems.CompilerProblemSeverity;
import org.apache.flex.compiler.problems.ICompilerProblem;
import org.apache.flex.compiler.projects.IFlexProject;
import org.apache.flex.compiler.scopes.IASScope;
import org.apache.flex.compiler.targets.ITarget;
import org.apache.flex.compiler.targets.ITargetSettings;
import org.apache.flex.compiler.tree.as.IASNode;
import org.apache.flex.compiler.tree.as.IBlockNode;
import org.apache.flex.compiler.tree.as.IContainerNode;
import org.apache.flex.compiler.tree.as.IExpressionNode;
import org.apache.flex.compiler.tree.as.IFileNode;
import org.apache.flex.compiler.tree.as.IFunctionCallNode;
import org.apache.flex.compiler.tree.as.IFunctionNode;
import org.apache.flex.compiler.tree.as.IIdentifierNode;
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
import io.typefox.lsapi.CodeActionParams;
import io.typefox.lsapi.CodeLens;
import io.typefox.lsapi.CodeLensImpl;
import io.typefox.lsapi.CodeLensParams;
import io.typefox.lsapi.Command;
import io.typefox.lsapi.CommandImpl;
import io.typefox.lsapi.CompletionItem;
import io.typefox.lsapi.CompletionItemImpl;
import io.typefox.lsapi.CompletionItemKind;
import io.typefox.lsapi.CompletionList;
import io.typefox.lsapi.CompletionListImpl;
import io.typefox.lsapi.Diagnostic;
import io.typefox.lsapi.DiagnosticImpl;
import io.typefox.lsapi.DiagnosticSeverity;
import io.typefox.lsapi.DidChangeTextDocumentParams;
import io.typefox.lsapi.DidChangeWatchedFilesParams;
import io.typefox.lsapi.DidCloseTextDocumentParams;
import io.typefox.lsapi.DidOpenTextDocumentParams;
import io.typefox.lsapi.DidSaveTextDocumentParams;
import io.typefox.lsapi.DocumentFormattingParams;
import io.typefox.lsapi.DocumentHighlight;
import io.typefox.lsapi.DocumentHighlightImpl;
import io.typefox.lsapi.DocumentOnTypeFormattingParams;
import io.typefox.lsapi.DocumentRangeFormattingParams;
import io.typefox.lsapi.DocumentSymbolParams;
import io.typefox.lsapi.FileEvent;
import io.typefox.lsapi.Hover;
import io.typefox.lsapi.HoverImpl;
import io.typefox.lsapi.Location;
import io.typefox.lsapi.LocationImpl;
import io.typefox.lsapi.MarkedStringImpl;
import io.typefox.lsapi.ParameterInformationImpl;
import io.typefox.lsapi.Position;
import io.typefox.lsapi.PositionImpl;
import io.typefox.lsapi.PublishDiagnosticsParams;
import io.typefox.lsapi.PublishDiagnosticsParamsImpl;
import io.typefox.lsapi.Range;
import io.typefox.lsapi.RangeImpl;
import io.typefox.lsapi.ReferenceParams;
import io.typefox.lsapi.RenameParams;
import io.typefox.lsapi.SignatureHelp;
import io.typefox.lsapi.SignatureHelpImpl;
import io.typefox.lsapi.SignatureInformationImpl;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.SymbolInformationImpl;
import io.typefox.lsapi.SymbolKind;
import io.typefox.lsapi.TextDocumentContentChangeEvent;
import io.typefox.lsapi.TextDocumentIdentifier;
import io.typefox.lsapi.TextDocumentItem;
import io.typefox.lsapi.TextDocumentPositionParams;
import io.typefox.lsapi.TextEdit;
import io.typefox.lsapi.TextEditImpl;
import io.typefox.lsapi.VersionedTextDocumentIdentifier;
import io.typefox.lsapi.WorkspaceEdit;
import io.typefox.lsapi.WorkspaceEditImpl;
import io.typefox.lsapi.WorkspaceSymbolParams;
import io.typefox.lsapi.services.TextDocumentService;
import io.typefox.lsapi.util.LsapiFactories;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public class ActionScriptTextDocumentService implements TextDocumentService
{
    private Path workspaceRoot;
    private boolean asconfigFileChanged = true;
    private Map<Path, String> sourceByPath = new HashMap<>();
    private Collection<ICompilationUnit> compilationUnits;
    private ArrayList<IInvisibleCompilationUnit> invisibleUnits = new ArrayList<>();
    private ICompilationUnit currentUnit;
    private IFlexProject currentProject;
    private IWorkspace currentWorkspace;
    private ASConfigOptions currentOptions;
    private LanguageServerFileSpecGetter fileSpecGetter;
    private Consumer<PublishDiagnosticsParams> publishDiagnostics = p ->
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

    @Override
    public CompletableFuture<CompletionList> completion(TextDocumentPositionParams position)
    {
        IASNode offsetNode = getOffsetNode(position);
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return CompletableFuture.completedFuture(new CompletionListImpl());
        }
        IASNode parentNode = offsetNode.getParent();

        CompletionListImpl result = new CompletionListImpl();
        result.setIncomplete(false);
        result.setItems(new ArrayList<>());

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
            if (offsetNode == memberAccessNode.getRightOperandNode())
            {
                autoCompleteMemberAccess(memberAccessNode, result);
                return CompletableFuture.completedFuture(result);
            }
        }

        if (offsetNode instanceof IFileNode)
        {
            //a package may only be defined at the file level
            addKeywordAutoComplete(IASKeywordConstants.PACKAGE, result);
        }
        IScopedNode containingScope = offsetNode.getContainingScope();
        if (containingScope != null)
        {
            IASScope scope = containingScope.getScope();
            if (offsetNode instanceof IScopedNode)
            {
                IScopedNode scopedNode = (IScopedNode) offsetNode;
                scope = scopedNode.getScope();
            }
            IDefinition scopeDefinition = scope.getDefinition();
            if (!(scopeDefinition instanceof ITypeDefinition || scopeDefinition instanceof IFunctionDefinition))
            {
                //you can't nest types inside other types or inside functions
                addKeywordAutoComplete(IASKeywordConstants.CLASS, result);
                addKeywordAutoComplete(IASKeywordConstants.INTERFACE, result);
            }
        }
        if (offsetNode instanceof IBlockNode)
        {
            addKeywordAutoComplete(IASKeywordConstants.VAR, result);
            addKeywordAutoComplete(IASKeywordConstants.CONST, result);
            addKeywordAutoComplete(IASKeywordConstants.FUNCTION, result);
            addKeywordAutoComplete(IASKeywordConstants.NAMESPACE, result);
            addKeywordAutoComplete(IASKeywordConstants.IMPORT, result);
            addKeywordAutoComplete(IASKeywordConstants.BREAK, result);
            addKeywordAutoComplete(IASKeywordConstants.CONTINUE, result);
            addKeywordAutoComplete(IASKeywordConstants.IF, result);
            addKeywordAutoComplete(IASKeywordConstants.ELSE, result);
            addKeywordAutoComplete(IASKeywordConstants.TRY, result);
            addKeywordAutoComplete(IASKeywordConstants.CATCH, result);
        }

        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved)
    {
        return CompletableFuture.completedFuture(new CompletionItemImpl());
    }

    @Override
    public CompletableFuture<Hover> hover(TextDocumentPositionParams position)
    {
        IASNode offsetNode = getOffsetNode(position);
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return CompletableFuture.completedFuture(LsapiFactories.emptyHover());
        }

        //INamespaceDecorationNode extends IIdentifierNode, but we don't want
        //any hover information for it.
        if (offsetNode instanceof IIdentifierNode
                && !(offsetNode instanceof INamespaceDecorationNode))
        {
            IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
            IDefinition definition = identifierNode.resolve(currentUnit.getProject());
            if (definition == null)
            {
                return null;
            }
            HoverImpl result = new HoverImpl();
            String detail = getDefinitionDetail(definition);
            List<MarkedStringImpl> contents = new ArrayList<>();
            contents.add(markedString(detail));
            result.setContents(contents);
            return CompletableFuture.completedFuture(result);
        }

        return CompletableFuture.completedFuture(LsapiFactories.emptyHover());
    }

    @Override
    public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position)
    {
        IASNode offsetNode = getOffsetNode(position);
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return CompletableFuture.completedFuture(LsapiFactories.emptySignatureHelp());
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
        return CompletableFuture.completedFuture(LsapiFactories.emptySignatureHelp());
    }

    @Override
    public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position)
    {
        IASNode offsetNode = getOffsetNode(position);
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        if (offsetNode instanceof IIdentifierNode)
        {
            List<Location> result = new ArrayList<>();
            IIdentifierNode expressionNode = (IIdentifierNode) offsetNode;
            resolveExpression(expressionNode, result);
            return CompletableFuture.completedFuture(result);
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params)
    {
        IASNode offsetNode = getOffsetNode(params.getTextDocument(), params.getPosition());
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
            for (ICompilationUnit compilationUnit : compilationUnits)
            {
                if (compilationUnit instanceof SWCCompilationUnit)
                {
                    continue;
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
                findIdentifiers(ast, resolved, identifiers);
                for (IIdentifierNode otherNode : identifiers)
                {
                    Path otherNodePath = Paths.get(otherNode.getSourcePath());
                    LocationImpl location = new LocationImpl();
                    location.setUri(otherNodePath.toUri().toString());
                    PositionImpl start = new PositionImpl();
                    start.setLine(otherNode.getLine());
                    start.setCharacter(otherNode.getColumn());
                    PositionImpl end = new PositionImpl();
                    end.setLine(otherNode.getEndLine());
                    end.setCharacter(otherNode.getEndColumn());
                    RangeImpl range = new RangeImpl();
                    range.setStart(start);
                    range.setEnd(end);
                    location.setRange(range);
                    result.add(location);
                }
            }
            return CompletableFuture.completedFuture(result);
        }

        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<DocumentHighlight> documentHighlight(TextDocumentPositionParams position)
    {
        return CompletableFuture.completedFuture(new DocumentHighlightImpl());
    }

    public CompletableFuture<List<? extends SymbolInformation>> workspaceSymbol(WorkspaceSymbolParams params)
    {
        ICompilationUnit mainUnit = getMainCompilationUnit();
        if (mainUnit == null)
        {
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

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params)
    {
        TextDocumentIdentifier textDocument = params.getTextDocument();
        URI uri = URI.create(textDocument.getUri());
        Optional<Path> optionalPath = getFilePath(uri);
        if (!optionalPath.isPresent())
        {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        Path path = optionalPath.get();
        ICompilationUnit unit = getCompilationUnit(path, false);
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

    @Override
    public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams params)
    {
        List<? extends Diagnostic> diagnostics = params.getContext().getDiagnostics();
        TextDocumentIdentifier document = params.getTextDocument();
        URI uri = URI.create(document.getUri());
        Optional<Path> optionalPath = getFilePath(uri);
        if (!optionalPath.isPresent())
        {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        Path path = optionalPath.get();
        if (!sourceByPath.containsKey(path))
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
                case "1046": //UnknownTypeProblem
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
                                    if (typeDefinition.getBaseName().equals(typeString))
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
                    break;
                }
            }
        }
        return CompletableFuture.completedFuture(commands);
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params)
    {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved)
    {
        return CompletableFuture.completedFuture(new CodeLensImpl());
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params)
    {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params)
    {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params)
    {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params)
    {
        IASNode offsetNode = getOffsetNode(params.getTextDocument(), params.getPosition());
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return null;
        }

        if (offsetNode instanceof IIdentifierNode)
        {
            IIdentifierNode expressionNode = (IIdentifierNode) offsetNode;
            WorkspaceEditImpl result = new WorkspaceEditImpl();
            renameIdentifier(expressionNode, params.getNewName(), result);
            return CompletableFuture.completedFuture(result);
        }

        return null;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params)
    {
        TextDocumentItem document = params.getTextDocument();
        URI uri = URI.create(document.getUri());
        Optional<Path> optionalPath = getFilePath(uri);

        if (optionalPath.isPresent())
        {
            Path path = optionalPath.get();

            String text = document.getText();
            sourceByPath.put(path, text);

            checkFilePathForProblems(path);
        }
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params)
    {
        VersionedTextDocumentIdentifier document = params.getTextDocument();
        URI uri = URI.create(document.getUri());
        Optional<Path> optionalPath = getFilePath(uri);

        if (optionalPath.isPresent())
        {
            Path path = optionalPath.get();
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
            checkFilePathForProblems(path);
        }
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params)
    {
        TextDocumentIdentifier document = params.getTextDocument();
        URI uri = URI.create(document.getUri());
        Optional<Path> path = getFilePath(uri);

        if (path.isPresent())
        {
            // Remove from source cache
            sourceByPath.remove(path.get());
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params)
    {
        //as long as we're checking on change, we shouldn't need to do anything
        //on save
    }

    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params)
    {
        for (FileEvent event : params.getChanges())
        {
            URI uri = URI.create(event.getUri());
            Optional<Path> optionalPath = getFilePath(uri);
            if (!optionalPath.isPresent())
            {
                continue;
            }
            Path path = optionalPath.get();
            File file = path.toFile();
            if (file.getName().equals("asconfig.json"))
            {
                asconfigFileChanged = true;
            }
        }
        if (asconfigFileChanged)
        {
            Path path = getMainCompilationUnitPath();
            if (path != null)
            {
                checkFilePathForProblems(path);
            }
        }
    }

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
        Path asconfigPath = workspaceRoot.resolve("asconfig.json");
        File asconfigFile = new File(asconfigPath.toUri());
        if (!asconfigFile.exists())
        {
            return null;
        }
        return asconfigFile;
    }

    private void loadASConfigFile()
    {
        if (!asconfigFileChanged && currentOptions != null)
        {
            //the options are fully up-to-date
            return;
        }
        currentOptions = null;
        asconfigFileChanged = true;
        File asconfigFile = getASConfigFile();
        if (asconfigFile == null)
        {
            return;
        }
        ProjectType type = ProjectType.APP;
        String config = null;
        String[] files = null;
        CompilerOptions compilerOptions = new CompilerOptions();
        try (InputStream schemaInputStream = getClass().getResourceAsStream("/schemas/asconfig.schema.json"))
        {
            JSONObject rawJsonSchema = new JSONObject(new JSONTokener(schemaInputStream));
            Schema schema = SchemaLoader.load(rawJsonSchema);
            String contents = FileUtils.readFileToString(asconfigFile);
            JSONObject json = new JSONObject(contents);
            schema.validate(json);
            if (json.has("type")) //optional, defaults to "app"
            {
                String typeString = json.getString("type");
                type = ProjectType.fromToken(typeString);
            }
            config = json.getString("config");
            if (json.has("files")) //optional
            {
                JSONArray jsonFiles = json.getJSONArray("files");
                int fileCount = jsonFiles.length();
                files = new String[fileCount];
                for (int i = 0; i < fileCount; i++)
                {
                    String pathString = jsonFiles.getString(i);
                    Path filePath = workspaceRoot.resolve(pathString);
                    files[i] = filePath.toString();
                }
            }
            if (json.has("compilerOptions")) //optional
            {
                JSONObject jsonCompilerOptions = json.getJSONObject("compilerOptions");
                if (jsonCompilerOptions.has("debug"))
                {
                    compilerOptions.debug = jsonCompilerOptions.getBoolean("debug");
                }
                if (jsonCompilerOptions.has("external-library-path"))
                {
                    JSONArray jsonExternalLibraryPath = jsonCompilerOptions.getJSONArray("external-library-path");
                    ArrayList<File> externalLibraryPath = new ArrayList<>();
                    for (int i = 0, count = jsonExternalLibraryPath.length(); i < count; i++)
                    {
                        String pathString = jsonExternalLibraryPath.getString(i);
                        Path filePath = workspaceRoot.resolve(pathString);
                        externalLibraryPath.add(filePath.toFile());
                    }
                    compilerOptions.externalLibraryPath = externalLibraryPath;
                }
                if (jsonCompilerOptions.has("library-path"))
                {
                    JSONArray jsonLibraryPath = jsonCompilerOptions.getJSONArray("library-path");
                    ArrayList<File> libraryPath = new ArrayList<>();
                    for (int i = 0, count = jsonLibraryPath.length(); i < count; i++)
                    {
                        String pathString = jsonLibraryPath.getString(i);
                        Path filePath = workspaceRoot.resolve(pathString);
                        libraryPath.add(filePath.toFile());
                    }
                    compilerOptions.libraryPath = libraryPath;
                }
                //skipping sourceMap
                if (jsonCompilerOptions.has("source-path"))
                {
                    JSONArray jsonSourcePath = jsonCompilerOptions.getJSONArray("source-path");
                    ArrayList<File> sourcePath = new ArrayList<>();
                    for (int i = 0, count = jsonSourcePath.length(); i < count; i++)
                    {
                        String pathString = jsonSourcePath.getString(i);
                        Path filePath = workspaceRoot.resolve(pathString);
                        sourcePath.add(filePath.toFile());
                    }
                    compilerOptions.libraryPath = sourcePath;
                }
                if (jsonCompilerOptions.has("warnings"))
                {
                    compilerOptions.warnings = jsonCompilerOptions.getBoolean("warnings");
                }
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
        ASConfigOptions options = new ASConfigOptions();
        options.type = type;
        options.config = config;
        options.files = files;
        options.compilerOptions = compilerOptions;
        currentOptions = options;
    }

    private void autoCompleteTypes(CompletionListImpl result)
    {
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
                    ITypeDefinition typeDefinition = (ITypeDefinition) definition;
                    addDefinitionAutoComplete(typeDefinition, result);
                }
            }
        }
        CompletionItemImpl item = new CompletionItemImpl();
        item.setKind(CompletionItemKind.Class);
        item.setLabel("void");
        result.getItems().add(item);
    }

    private void autoCompleteMemberAccess(IMemberAccessExpressionNode node, CompletionListImpl result)
    {
        IExpressionNode leftOperand = node.getLeftOperandNode();
        IDefinition leftDefinition = leftOperand.resolve(currentProject);
        if (leftDefinition != null && leftDefinition instanceof ITypeDefinition)
        {
            ITypeDefinition typeDefinition = (ITypeDefinition) leftDefinition;
            addDefinitionsInScopeToAutoComplete(typeDefinition, true, result);
            return;
        }
        ITypeDefinition leftType = leftOperand.resolveType(currentProject);
        if (leftType != null)
        {
            addDefinitionsInScopeToAutoComplete(leftType, false, result);
            return;
        }
    }

    private void addDefinitionsInScopeToAutoComplete(ITypeDefinition typeDefinition, boolean isStatic, CompletionListImpl result)
    {
        IASScope scope = typeDefinition.getContainedScope();
        if (scope != null)
        {
            for (IDefinition localDefinition : scope.getAllLocalDefinitions())
            {
                if (localDefinition.isImplicit())
                {
                    //skip things like super and this
                    continue;
                }
                if (isStatic && !localDefinition.isStatic())
                {
                    continue;
                }
                if (!isStatic && localDefinition.isStatic())
                {
                    continue;
                }
                addDefinitionAutoComplete(localDefinition, result);
            }
        }
        if (isStatic)
        {
            //nothing from the base class or interfaces matter
            return;
        }
        if (typeDefinition instanceof IClassDefinition)
        {
            IClassDefinition classDefinition = (IClassDefinition) typeDefinition;
            IClassDefinition baseClassDefinition = classDefinition.resolveBaseClass(currentProject);
            if (baseClassDefinition != null)
            {
                addDefinitionsInScopeToAutoComplete(baseClassDefinition, isStatic, result);
            }
        }
        else if (typeDefinition instanceof IInterfaceDefinition)
        {
            IInterfaceDefinition interfaceDefinition = (IInterfaceDefinition) typeDefinition;
            IInterfaceDefinition[] interfaceDefinitions = interfaceDefinition.resolveExtendedInterfaces(currentProject);
            for (IInterfaceDefinition extendedInterfaceDefinition : interfaceDefinitions)
            {
                addDefinitionsInScopeToAutoComplete(extendedInterfaceDefinition, isStatic, result);
            }
        }
    }

    private void addDefinitionAutoComplete(IDefinition definition, CompletionListImpl result)
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
        result.getItems().add(item);
    }

    private void addKeywordAutoComplete(String keyword, CompletionListImpl result)
    {
        CompletionItemImpl item = new CompletionItemImpl();
        item.setKind(CompletionItemKind.Keyword);
        item.setLabel(keyword);
        result.getItems().add(item);
    }

    private void resolveExpression(IExpressionNode node, List<Location> result)
    {
        IDefinition resolved = node.resolve(currentProject);
        if (resolved == null)
        {
            return;
        }
        String sourcePath = resolved.getSourcePath();
        if (sourcePath == null)
        {
            //if it's in a SWC or something, the source path might be null
            return;
        }
        Path resolvedPath = Paths.get(resolved.getSourcePath());
        LocationImpl location = new LocationImpl();
        location.setUri(resolvedPath.toUri().toString());
        PositionImpl start = new PositionImpl();
        start.setLine(resolved.getNameLine());
        start.setCharacter(resolved.getNameColumn());
        PositionImpl end = new PositionImpl();
        end.setLine(resolved.getNameLine());
        end.setCharacter(resolved.getNameColumn() + resolved.getNameEnd() - resolved.getNameStart());
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

    private void renameIdentifier(IIdentifierNode node, String newName, WorkspaceEditImpl result)
    {
        IDefinition resolved = node.resolve(currentProject);
        if (resolved == null)
        {
            return;
        }
        Map<String, TextEditImpl[]> changes = new HashMap<>();
        for (ICompilationUnit compilationUnit : compilationUnits)
        {
            if (compilationUnit instanceof SWCCompilationUnit)
            {
                continue;
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
            findIdentifiers(ast, resolved, identifiers);
            ArrayList<TextEdit> textEdits = new ArrayList<>();
            for (IIdentifierNode identifierNode : identifiers)
            {
                TextEditImpl textEdit = new TextEditImpl();
                textEdit.setNewText(newName);

                PositionImpl start = new PositionImpl();
                start.setLine(identifierNode.getLine());
                start.setCharacter(identifierNode.getColumn());
                PositionImpl end = new PositionImpl();
                end.setLine(identifierNode.getEndLine());
                end.setCharacter(identifierNode.getEndColumn());
                RangeImpl range = new RangeImpl();
                range.setStart(start);
                range.setEnd(end);
                textEdit.setRange(range);
                textEdits.add(textEdit);
            }
            TextEditImpl[] editsArray = textEdits.toArray(new TextEditImpl[textEdits.size()]);
            changes.put(compilationUnit.getAbsoluteFilename(), editsArray);
        }
        //result.setChanges(changes);
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
        publish.setDiagnostics(new ArrayList<>());
        publish.setUri(uri.toString());

        String code = sourceByPath.get(path);
        StringReader reader = new StringReader(code);
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
            return;
        }
        for (ICompilerProblem problem : parser.getSyntaxProblems())
        {
            addCompilerProblem(problem, publish);
        }
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
        if (currentOptions.files.length == 0)
        {
            return null;
        }
        String lastFilePath = currentOptions.files[currentOptions.files.length - 1];
        return Paths.get(lastFilePath);
    }

    private ICompilationUnit getMainCompilationUnit()
    {
        Path path = getMainCompilationUnitPath();
        if (path == null)
        {
            return null;
        }
        return getCompilationUnit(path, false);
    }

    private ICompilationUnit getCompilationUnit(Path path, boolean fileChanged)
    {
        String absolutePath = path.toAbsolutePath().toString();
        currentUnit = null;
        currentProject = getProject();
        if (currentProject == null)
        {
            return null;
        }
        currentWorkspace = currentProject.getWorkspace();
        if (fileChanged)
        {
            currentWorkspace.fileChanged(fileSpecGetter.getFileSpecification(path.toAbsolutePath().toString()));
        }
        String[] files = currentOptions.files;
        for (int i = files.length - 1; i >= 0; i--)
        {
            String file = files[i];
            ICompilationUnit existingUnit = findCompilationUnit(file);
            if (existingUnit != null)
            {
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
        compilationUnits = currentProject.getCompilationUnits();
        if (currentUnit == null)
        {
            for (ICompilationUnit unit : compilationUnits)
            {
                if (unit.getAbsoluteFilename().equals(absolutePath))
                {
                    currentUnit = unit;
                    break;
                }
            }
        }

        return currentUnit;
    }

    private void clearInvisibleCompilationUnits()
    {
        for (IInvisibleCompilationUnit unit : invisibleUnits)
        {
            //it's not enough to simply call remove() on the unit, we also need
            //to remove the file spec from the workspace. otherwise, an
            //exception will be thrown when calling fileChanged()
            currentWorkspace.fileRemoved(fileSpecGetter.getFileSpecification(unit.getAbsoluteFilename()));
            //...we also need to do it before calling remove() for some reason
            unit.remove();
        }
        invisibleUnits.clear();
    }

    private IFlexProject getProject()
    {
        clearInvisibleCompilationUnits();
        loadASConfigFile();
        if (currentOptions == null)
        {
            currentWorkspace = null;
            currentProject = null;
            fileSpecGetter = null;
            compilationUnits = null;
            return null;
        }
        if (asconfigFileChanged)
        {
            asconfigFileChanged = false;

            //start fresh if the asconfig.json file changed
            currentWorkspace = null;
            currentProject = null;
            fileSpecGetter = null;
            compilationUnits = null;
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
        JSGoogConfiguration configuration = new JSGoogConfiguration();
        Configurator configurator = new Configurator(JSGoogConfiguration.class);
        configurator.setToken("configname", currentOptions.config);
        if (currentOptions.files != null)
        {
            if (currentOptions.type.equals(ProjectType.LIB))
            {
                ArrayList<File> files = new ArrayList<>();
                for (String filePath : currentOptions.files)
                {
                    File file = new File(filePath);
                    files.add(file);
                }
                configurator.setIncludeSources(files);
                configurator.setConfiguration(null, ICompilerSettingsConstants.INCLUDE_CLASSES_VAR, false);
            }
            else //app
            {
                configurator.setConfiguration(currentOptions.files, ICompilerSettingsConstants.FILE_SPECS_VAR);
            }
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

    private void checkFilePathForProblems(Path path)
    {
        currentUnit = null;
        compilationUnits = null;
        if (!checkCompilationUnitForProblems(path))
        {
            checkFilePathForSyntaxProblems(path);
        }
    }

    private boolean checkCompilationUnitForProblems(Path path)
    {
        ICompilationUnit mainUnit = getCompilationUnit(path, true);
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
            PublishDiagnosticsParamsImpl publish = new PublishDiagnosticsParamsImpl();
            publish.setDiagnostics(new ArrayList<>());
            publish.setUri(path.toUri().toString());
            for (ICompilerProblem problem : fatalProblems)
            {
                addCompilerProblem(problem, publish);
            }
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
            for (ICompilationUnit unit : compilationUnits)
            {
                if (unit instanceof SWCCompilationUnit)
                {
                    //compiled compilation units won't have problems
                    continue;
                }
                URI uri = Paths.get(unit.getAbsoluteFilename()).toUri();
                PublishDiagnosticsParamsImpl publish = new PublishDiagnosticsParamsImpl();
                publish.setDiagnostics(new ArrayList<>());
                publish.setUri(uri.toString());
                files.put(uri, publish);
                ArrayList<ICompilerProblem> problems = new ArrayList<>();
                try
                {
                    unit.waitForBuildFinish(problems, ITarget.TargetType.SWF);
                }
                catch (Exception e)
                {
                    return false;
                }
                for (ICompilerProblem problem : problems)
                {
                    String sourcePath = problem.getSourcePath();
                    if (sourcePath == null)
                    {
                        //if the problem is not associated with a file, we'll skip it
                        System.err.println("Internal ActionScript compiler problem:");
                        System.err.println(problem);
                        continue;
                    }
                    addCompilerProblem(problem, publish);
                }
            }
        }
        catch (Exception e)
        {
            System.err.println("test: " + e);
            return false;
        }
        files.values().forEach(publishDiagnostics::accept);
        return true;
    }

    private IASNode getOffsetNode(TextDocumentPositionParams position)
    {
        return getOffsetNode(position.getTextDocument(), position.getPosition());
    }

    private IASNode getOffsetNode(TextDocumentIdentifier textDocument, Position position)
    {
        URI uri = URI.create(textDocument.getUri());
        Optional<Path> optionalPath = getFilePath(uri);
        if (!optionalPath.isPresent())
        {
            System.err.println("Could not find URI " + uri);
            return null;
        }
        Path path = optionalPath.get();
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

        ICompilationUnit unit = getCompilationUnit(path, false);
        if (unit == null)
        {
            return null;
        }
        IASNode ast = null;
        try
        {
            ast = unit.getSyntaxTreeRequest().get().getAST();
        }
        catch (InterruptedException e)
        {
            System.err.println("Interrupted while getting AST");
            return null;
        }
        if (ast == null)
        {
            //we couldn't find the root node for this file
            System.err.println("Could not find AST");
            return null;
        }

        int offset = lineAndCharacterToOffset(new StringReader(code),
                position.getLine(),
                position.getCharacter());
        if (offset == -1)
        {
            System.err.println("Could not find code at position " + position.getLine() + ":" + position.getCharacter());
            return null;
        }
        return ast.getContainingNode(offset);
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
            detailBuilder.append(IASKeywordConstants.CLASS);
            detailBuilder.append(" ");
            detailBuilder.append(classDefinition.getQualifiedName());
            IClassDefinition baseClassDefinition = classDefinition.resolveBaseClass(currentProject);
            if (baseClassDefinition != null)
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

    private void querySymbolsInScope(String query, IASScope scope, List<SymbolInformationImpl> result)
    {
        Collection<IDefinition> definitions = scope.getAllLocalDefinitions();
        for (IDefinition definition : definitions)
        {
            if (definition.isImplicit())
            {
                continue;
            }
            if (definition instanceof IPackageDefinition)
            {
                IPackageDefinition packageDefinition = (IPackageDefinition) definition;
                IASScope packageScope = packageDefinition.getContainedScope();
                querySymbolsInScope(query, packageScope, result);
            }
            else if (definition instanceof ITypeDefinition)
            {
                ITypeDefinition typeDefinition = (ITypeDefinition) definition;
                if (typeDefinition.getBaseName().startsWith(query) ||
                        typeDefinition.getQualifiedName().startsWith(query))
                {
                    SymbolInformationImpl symbol = definitionToSymbol(typeDefinition);
                    result.add(symbol);
                }
                IASScope typeScope = typeDefinition.getContainedScope();
                querySymbolsInScope(query, typeScope, result);
            }
            else if (definition instanceof IFunctionDefinition)
            {
                IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
                if (functionDefinition.getBaseName().startsWith(query) ||
                        functionDefinition.getQualifiedName().startsWith(query))
                {
                    SymbolInformationImpl symbol = definitionToSymbol(functionDefinition);
                    result.add(symbol);
                }
                IASScope functionScope = functionDefinition.getContainedScope();
                querySymbolsInScope(query, functionScope, result);
            }
            else if (definition instanceof IVariableDefinition)
            {
                IVariableDefinition variableDefinition = (IVariableDefinition) definition;
                if (variableDefinition.getBaseName().startsWith(query) ||
                        variableDefinition.getQualifiedName().startsWith(query))
                {
                    SymbolInformationImpl symbol = definitionToSymbol(variableDefinition);
                    result.add(symbol);
                }
            }
        }
    }

    private void scopeToSymbols(IASScope scope, List<SymbolInformationImpl> result)
    {
        Collection<IDefinition> definitions = scope.getAllLocalDefinitions();
        for (IDefinition definition : definitions)
        {
            if (definition.isImplicit())
            {
                continue;
            }
            if (definition instanceof IPackageDefinition)
            {
                IPackageDefinition packageDefinition = (IPackageDefinition) definition;
                IASScope packageScope = packageDefinition.getContainedScope();
                scopeToSymbols(packageScope, result);
            }
            else if (definition instanceof ITypeDefinition)
            {
                ITypeDefinition typeDefinition = (ITypeDefinition) definition;
                SymbolInformationImpl symbol = definitionToSymbol(typeDefinition);
                result.add(symbol);
                IASScope typeScope = typeDefinition.getContainedScope();
                scopeToSymbols(typeScope, result);
            }
            else if (definition instanceof IFunctionDefinition || definition instanceof IVariableDefinition)
            {
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
        Path definitionPath = Paths.get(definition.getSourcePath());
        location.setUri(definitionPath.toUri().toString());
        PositionImpl start = new PositionImpl();
        start.setLine(definition.getLine());
        start.setCharacter(definition.getColumn());
        PositionImpl end = new PositionImpl();
        end.setLine(definition.getLine());
        end.setCharacter(definition.getColumn());
        RangeImpl range = new RangeImpl();
        range.setStart(start);
        range.setEnd(end);
        location.setRange(range);
        symbol.setLocation(location);
        return symbol;
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
