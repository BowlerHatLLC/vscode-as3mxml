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
import org.apache.flex.compiler.config.Configurator;
import org.apache.flex.compiler.config.ICompilerSettingsConstants;
import org.apache.flex.compiler.constants.IASKeywordConstants;
import org.apache.flex.compiler.constants.IASLanguageConstants;
import org.apache.flex.compiler.definitions.IAccessorDefinition;
import org.apache.flex.compiler.definitions.IClassDefinition;
import org.apache.flex.compiler.definitions.IConstantDefinition;
import org.apache.flex.compiler.definitions.IDefinition;
import org.apache.flex.compiler.definitions.IFunctionDefinition;
import org.apache.flex.compiler.definitions.IGetterDefinition;
import org.apache.flex.compiler.definitions.IInterfaceDefinition;
import org.apache.flex.compiler.definitions.INamespaceDefinition;
import org.apache.flex.compiler.definitions.IPackageDefinition;
import org.apache.flex.compiler.definitions.IParameterDefinition;
import org.apache.flex.compiler.definitions.ISetterDefinition;
import org.apache.flex.compiler.definitions.ITypeDefinition;
import org.apache.flex.compiler.definitions.IVariableDefinition;
import org.apache.flex.compiler.definitions.metadata.IMetaTag;
import org.apache.flex.compiler.filespecs.IFileSpecification;
import org.apache.flex.compiler.internal.driver.js.goog.JSGoogConfiguration;
import org.apache.flex.compiler.internal.mxml.MXMLNamespaceMapping;
import org.apache.flex.compiler.internal.parsing.as.ASParser;
import org.apache.flex.compiler.internal.parsing.as.ASToken;
import org.apache.flex.compiler.internal.parsing.as.RepairingTokenBuffer;
import org.apache.flex.compiler.internal.parsing.as.StreamingASTokenizer;
import org.apache.flex.compiler.internal.projects.CompilerProject;
import org.apache.flex.compiler.internal.projects.FlexJSProject;
import org.apache.flex.compiler.internal.scopes.ASScope;
import org.apache.flex.compiler.internal.scopes.TypeScope;
import org.apache.flex.compiler.internal.tree.as.FileNode;
import org.apache.flex.compiler.internal.tree.as.FullNameNode;
import org.apache.flex.compiler.internal.units.SWCCompilationUnit;
import org.apache.flex.compiler.internal.workspaces.Workspace;
import org.apache.flex.compiler.problems.CompilerProblemSeverity;
import org.apache.flex.compiler.problems.ICompilerProblem;
import org.apache.flex.compiler.projects.IFlexProject;
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

public class ActionScriptTextDocumentService implements TextDocumentService
{
    private Boolean asconfigChanged = true;
    private Path workspaceRoot;
    private Map<Path, String> sourceByPath = new HashMap<>();
    private Collection<ICompilationUnit> compilationUnits;
    private ArrayList<IInvisibleCompilationUnit> invisibleUnits = new ArrayList<>();
    private ICompilationUnit currentUnit;
    private IFlexProject currentProject;
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
        IASNode nodeAtPreviousOffset = null;
        if (parentNode != null)
        {
            nodeAtPreviousOffset = parentNode.getContainingNode(currentOffset - 1);
        }

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
            return CompletableFuture.completedFuture(new HoverImpl(Collections.emptyList(), null));
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
                //note: keywords won't resolve to anything
                return CompletableFuture.completedFuture(new HoverImpl(Collections.emptyList(), null));
            }
            HoverImpl result = new HoverImpl();
            String detail = getDefinitionDetail(definition);
            List<MarkedStringImpl> contents = new ArrayList<>();
            contents.add(markedString(detail));
            result.setContents(contents);
            return CompletableFuture.completedFuture(result);
        }

        return CompletableFuture.completedFuture(new HoverImpl(Collections.emptyList(), null));
    }

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
                if (identifierNode.getName().equals("super"))
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
        if (compilationUnits == null)
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
                case "1120": //AccessUndefinedPropertyProblem
                {
                    createCodeActionsForImport(diagnostic, commands);
                    break;
                }
                case "1046": //UnknownTypeProblem
                {
                    createCodeActionsForImport(diagnostic, commands);
                    break;
                }
                case "1178": //InaccessiblePropertyReferenceProblem
                {
                    createCodeActionsForImport(diagnostic, commands);
                    break;
                }
                case "1180": //CallUndefinedMethodProblem
                {
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
            return CompletableFuture.completedFuture(new WorkspaceEditImpl(new HashMap<>()));
        }

        if (offsetNode instanceof IDefinitionNode)
        {
            IDefinitionNode definitionNode = (IDefinitionNode) offsetNode;
            IExpressionNode expressionNode = definitionNode.getNameExpressionNode();
            WorkspaceEditImpl result = renameExpression(expressionNode, params.getNewName());
            return CompletableFuture.completedFuture(result);
        }
        if (offsetNode instanceof IIdentifierNode)
        {
            IIdentifierNode expressionNode = (IIdentifierNode) offsetNode;
            WorkspaceEditImpl result = renameExpression(expressionNode, params.getNewName());
            return CompletableFuture.completedFuture(result);
        }

        MessageParamsImpl message = new MessageParamsImpl();
        message.setType(MessageType.Info);
        message.setMessage("You cannot rename this element.");
        showMessageCallback.accept(message);

        return CompletableFuture.completedFuture(new WorkspaceEditImpl(new HashMap<>()));
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

            //we need to check for problems when opening a new file because it
            //may not have been in the workspace before.
            checkFilePathForProblems(path, false);
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

    @Override
    public void didClose(DidCloseTextDocumentParams params)
    {
        TextDocumentIdentifier document = params.getTextDocument();
        URI uri = URI.create(document.getUri());
        Optional<Path> optionalPath = getFilePath(uri);

        if (optionalPath.isPresent())
        {
            Path path = optionalPath.get();
            sourceByPath.remove(path);
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
        boolean needsFullCheck = false;
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
                //compiler settings may have changed, which means we should
                //start fresh
                asconfigChanged = true;
                needsFullCheck = true;
            }
            else if (file.getName().endsWith(".as") && currentWorkspace != null)
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

    private void autoCompleteTypes(CompletionListImpl result)
    {
        autoCompleteDefinitions(result, true, null, null);
    }

    private void autoCompleteDefinitions(CompletionListImpl result, boolean typesOnly,
                                         String packageName, IDefinition definitionToSkip)
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
                if (!typesOnly || definition instanceof ITypeDefinition)
                {
                    if (packageName == null || definition.getPackageName().equals(packageName))
                    {
                        if (skipQualifiedName != null
                                && skipQualifiedName.equals(definition.getQualifiedName()))
                        {
                            continue;
                        }
                        addDefinitionAutoComplete(definition, result);
                    }
                }
            }
        }
        if (packageName == null || packageName.equals(""))
        {
            CompletionItemImpl item = new CompletionItemImpl();
            item.setKind(CompletionItemKind.Class);
            item.setLabel("void");
            result.getItems().add(item);
        }
    }

    private void autoCompleteScope(IScopedNode node, CompletionListImpl result)
    {
        IASScope scope = node.getScope();
        IDefinition definitionToSkip = scope.getDefinition();
        //include definitions in the top-level package
        autoCompleteDefinitions(result, false, "", definitionToSkip);
        //include definitions in the same package
        String packageName = node.getPackageName();
        if (packageName != null && packageName.length() > 0)
        {
            autoCompleteDefinitions(result, false, packageName, definitionToSkip);
        }
        //include definitions that are imported from other packages
        ArrayList<IImportNode> importNodes = new ArrayList<>();
        node.getAllImportNodes(importNodes);
        for (IImportNode importNode : importNodes)
        {
            IDefinition importDefinition = importNode.resolveImport(currentProject);
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

    private void addDefinitionsInTypeScopeToAutoComplete(TypeScope typeScope, ASScope otherScope, boolean isStatic, CompletionListImpl result)
    {
        IMetaTag[] excludeMetaTags = typeScope.getDefinition().getMetaTagsByName(IASLanguageConstants.EXCLUDE_META_TAG);
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
                    String excludeName = excludeMetaTag.getAttributeValue(IASLanguageConstants.EXCLUDE_META_TAG_NAME);
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
            if (localDefinition instanceof ISetterDefinition)
            {
                ISetterDefinition setter = (ISetterDefinition) localDefinition;
                IGetterDefinition getter = setter.resolveGetter(currentProject);
                if (getter != null)
                {
                    //skip the setter if there's also a getter because it would
                    //add a duplicate entry
                    continue;
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
            addDefinitionAutoComplete(localDefinition, result);
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

    private WorkspaceEditImpl renameExpression(IExpressionNode node, String newName)
    {
        WorkspaceEditImpl result = new WorkspaceEditImpl();
        Map<String, List<TextEditImpl>> changes = new HashMap<>();
        result.setChanges(changes);
        IDefinition resolved = node.resolve(currentProject);
        if (resolved == null)
        {
            MessageParamsImpl message = new MessageParamsImpl();
            message.setType(MessageType.Info);
            message.setMessage("You cannot rename this element.");
            showMessageCallback.accept(message);
            return result;
        }
        if (resolved.getContainingFilePath().endsWith(".swc"))
        {
            MessageParamsImpl message = new MessageParamsImpl();
            message.setType(MessageType.Info);
            message.setMessage("You cannot rename an element defined in a SWC file.");
            showMessageCallback.accept(message);
            return result;
        }
        if (resolved instanceof IPackageDefinition)
        {
            MessageParamsImpl message = new MessageParamsImpl();
            message.setType(MessageType.Info);
            message.setMessage("You cannot rename a package.");
            showMessageCallback.accept(message);
            return result;
        }
        IDefinition parentDefinition = resolved.getParent();
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
            ArrayList<TextEditImpl> textEdits = new ArrayList<>();
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
        //always do this before returning!
        compilationUnits = currentProject.getCompilationUnits();
        if (currentUnit != null)
        {
            return currentUnit;
        }

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
        //for some reason, function nodes may not be populated if the
        //compilation unit isn't one of the main files, so force it to populate
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

    private IFlexProject getProject()
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

    private IASNode getOffsetNode(TextDocumentPositionParams position)
    {
        return getOffsetNode(position.getTextDocument(), position.getPosition());
    }

    private IASNode getOffsetNode(TextDocumentIdentifier textDocument, Position position)
    {
        currentOffset = -1;
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

        ICompilationUnit unit = getCompilationUnit(path);
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
            if (baseClassDefinition != null && !baseClassDefinition.getQualifiedName().equals("Object"))
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
