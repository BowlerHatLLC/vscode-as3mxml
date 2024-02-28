/*
Copyright 2016-2024 Bowler Hat LLC

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
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.royale.compiler.asdoc.IASDocTag;
import org.apache.royale.compiler.common.ASModifier;
import org.apache.royale.compiler.common.ISourceLocation;
import org.apache.royale.compiler.common.PrefixMap;
import org.apache.royale.compiler.common.XMLName;
import org.apache.royale.compiler.constants.IASKeywordConstants;
import org.apache.royale.compiler.constants.IASLanguageConstants;
import org.apache.royale.compiler.constants.IMXMLCoreConstants;
import org.apache.royale.compiler.constants.IMetaAttributeConstants;
import org.apache.royale.compiler.definitions.IAccessorDefinition;
import org.apache.royale.compiler.definitions.IAppliedVectorDefinition;
import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IClassDefinition.IClassIterator;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IEventDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition.FunctionClassification;
import org.apache.royale.compiler.definitions.IGetterDefinition;
import org.apache.royale.compiler.definitions.IInterfaceDefinition;
import org.apache.royale.compiler.definitions.INamespaceDefinition;
import org.apache.royale.compiler.definitions.IParameterDefinition;
import org.apache.royale.compiler.definitions.ISetterDefinition;
import org.apache.royale.compiler.definitions.IStyleDefinition;
import org.apache.royale.compiler.definitions.ITypeDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition.VariableClassification;
import org.apache.royale.compiler.definitions.metadata.IMetaTag;
import org.apache.royale.compiler.definitions.metadata.IMetaTagAttribute;
import org.apache.royale.compiler.filespecs.IFileSpecification;
import org.apache.royale.compiler.internal.mxml.MXMLData;
import org.apache.royale.compiler.internal.mxml.MXMLTagData;
import org.apache.royale.compiler.internal.projects.CompilerProject;
import org.apache.royale.compiler.internal.scopes.ASProjectScope.DefinitionPromise;
import org.apache.royale.compiler.internal.scopes.ASScope;
import org.apache.royale.compiler.internal.scopes.TypeScope;
import org.apache.royale.compiler.internal.tree.as.FullNameNode;
import org.apache.royale.compiler.mxml.IMXMLData;
import org.apache.royale.compiler.mxml.IMXMLDataManager;
import org.apache.royale.compiler.mxml.IMXMLLanguageConstants;
import org.apache.royale.compiler.mxml.IMXMLTagAttributeData;
import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.scopes.IASScope;
import org.apache.royale.compiler.tree.ASTNodeID;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IAccessorNode;
import org.apache.royale.compiler.tree.as.IBinaryOperatorNode;
import org.apache.royale.compiler.tree.as.IBlockNode;
import org.apache.royale.compiler.tree.as.IClassNode;
import org.apache.royale.compiler.tree.as.IContainerNode;
import org.apache.royale.compiler.tree.as.IDefinitionNode;
import org.apache.royale.compiler.tree.as.IDynamicAccessNode;
import org.apache.royale.compiler.tree.as.IExpressionNode;
import org.apache.royale.compiler.tree.as.IFileNode;
import org.apache.royale.compiler.tree.as.IFunctionCallNode;
import org.apache.royale.compiler.tree.as.IFunctionNode;
import org.apache.royale.compiler.tree.as.IIdentifierNode;
import org.apache.royale.compiler.tree.as.IImportNode;
import org.apache.royale.compiler.tree.as.IInterfaceNode;
import org.apache.royale.compiler.tree.as.IKeywordNode;
import org.apache.royale.compiler.tree.as.ILanguageIdentifierNode;
import org.apache.royale.compiler.tree.as.ILanguageIdentifierNode.LanguageIdentifierKind;
import org.apache.royale.compiler.tree.as.IMemberAccessExpressionNode;
import org.apache.royale.compiler.tree.as.IModifierNode;
import org.apache.royale.compiler.tree.as.IOperatorNode.OperatorType;
import org.apache.royale.compiler.tree.as.IPackageNode;
import org.apache.royale.compiler.tree.as.IScopedNode;
import org.apache.royale.compiler.tree.as.ITypeNode;
import org.apache.royale.compiler.tree.as.IVariableNode;
import org.apache.royale.compiler.tree.mxml.IMXMLClassDefinitionNode;
import org.apache.royale.compiler.tree.mxml.IMXMLStyleNode;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.as3mxml.vscode.asdoc.IASDocTagConstants;
import com.as3mxml.vscode.asdoc.IRoyaleASDocTagConstants;
import com.as3mxml.vscode.asdoc.VSCodeASDocComment;
import com.as3mxml.vscode.asdoc.VSCodeASDocComment.VSCodeASDocTag;
import com.as3mxml.vscode.project.ActionScriptProjectData;
import com.as3mxml.vscode.project.ILspProject;
import com.as3mxml.vscode.utils.ASTUtils;
import com.as3mxml.vscode.utils.ActionScriptProjectManager;
import com.as3mxml.vscode.utils.AddImportData;
import com.as3mxml.vscode.utils.CodeActionsUtils;
import com.as3mxml.vscode.utils.CompilationUnitUtils.IncludeFileData;
import com.as3mxml.vscode.utils.CompilerProjectUtils;
import com.as3mxml.vscode.utils.CompletionItemUtils;
import com.as3mxml.vscode.utils.DefinitionTextUtils;
import com.as3mxml.vscode.utils.DefinitionUtils;
import com.as3mxml.vscode.utils.FileTracker;
import com.as3mxml.vscode.utils.ImportRange;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;
import com.as3mxml.vscode.utils.MXMLDataUtils;
import com.as3mxml.vscode.utils.MXMLNamespace;
import com.as3mxml.vscode.utils.MXMLNamespaceUtils;
import com.as3mxml.vscode.utils.ScopeUtils;
import com.as3mxml.vscode.utils.SourcePathUtils;
import com.as3mxml.vscode.utils.XmlnsRange;

public class CompletionProvider {
    private static final String FILE_EXTENSION_MXML = ".mxml";
    private static final String VECTOR_HIDDEN_PREFIX = "Vector$";
    private static final Pattern asdocTagAllowedPattern = Pattern.compile("^\\s*(\\*)\\s*(@.*)?$");

    private ActionScriptProjectManager actionScriptProjectManager;
    private FileTracker fileTracker;
    private boolean completionSupportsSnippets;
    private boolean completionSupportsSimpleSnippets;
    private boolean frameworkSDKIsRoyale;
    private List<String> completionTypes = new ArrayList<>();

    public CompletionProvider(ActionScriptProjectManager actionScriptProjectManager, FileTracker fileTracker,
            boolean completionSupportsSnippets, boolean completionSupportsSimpleSnippets,
            boolean frameworkSDKIsRoyale) {
        this.actionScriptProjectManager = actionScriptProjectManager;
        this.fileTracker = fileTracker;
        this.completionSupportsSnippets = completionSupportsSnippets;
        this.completionSupportsSimpleSnippets = completionSupportsSimpleSnippets;
        this.frameworkSDKIsRoyale = frameworkSDKIsRoyale;
    }

    public Either<List<CompletionItem>, CompletionList> completion(CompletionParams params, CancelChecker cancelToken) {
        try {
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }

            // this shouldn't be necessary, but if we ever forget to do this
            // somewhere, completion results might be missing items.
            completionTypes.clear();

            TextDocumentIdentifier textDocument = params.getTextDocument();
            Position position = params.getPosition();
            Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
            if (path == null) {
                CompletionList result = new CompletionList();
                result.setIsIncomplete(false);
                result.setItems(new ArrayList<>());
                if (cancelToken != null) {
                    cancelToken.checkCanceled();
                }
                return Either.forRight(result);
            }
            ActionScriptProjectData projectData = actionScriptProjectManager.getProjectDataForSourceFile(path);
            if (projectData == null || projectData.project == null) {
                CompletionList result = new CompletionList();
                result.setIsIncomplete(false);
                result.setItems(new ArrayList<>());
                if (cancelToken != null) {
                    cancelToken.checkCanceled();
                }
                return Either.forRight(result);
            }
            ILspProject project = projectData.project;

            IncludeFileData includeFileData = projectData.includedFiles.get(path.toString());
            int currentOffset = LanguageServerCompilerUtils.getOffsetFromPosition(fileTracker.getReader(path), position,
                    includeFileData);
            if (currentOffset == -1) {
                CompletionList result = new CompletionList();
                result.setIsIncomplete(false);
                result.setItems(new ArrayList<>());
                if (cancelToken != null) {
                    cancelToken.checkCanceled();
                }
                return Either.forRight(result);
            }
            boolean isMXML = textDocument.getUri().endsWith(FILE_EXTENSION_MXML);
            if (isMXML) {
                MXMLData mxmlData = actionScriptProjectManager.getMXMLDataForPath(path, projectData);
                IMXMLTagData offsetTag = MXMLDataUtils.getOffsetMXMLTag(mxmlData, currentOffset);
                if (offsetTag != null) {
                    IASNode embeddedNode = actionScriptProjectManager.getEmbeddedActionScriptNodeInMXMLTag(offsetTag,
                            path, currentOffset, projectData);
                    if (embeddedNode != null) {
                        CompletionList result = actionScriptCompletion(embeddedNode, path, position, currentOffset,
                                projectData);
                        if (cancelToken != null) {
                            cancelToken.checkCanceled();
                        }
                        return Either.forRight(result);
                    }
                    // if we're inside an <fx:Script> tag, we want ActionScript completion,
                    // so that's why we call isMXMLTagValidForCompletion()
                    if (MXMLDataUtils.isMXMLCodeIntelligenceAvailableForTag(offsetTag)) {
                        ICompilationUnit offsetUnit = CompilerProjectUtils.findCompilationUnit(path, project);
                        CompletionList result = mxmlCompletion(offsetTag, path, currentOffset, offsetUnit, project);
                        if (cancelToken != null) {
                            cancelToken.checkCanceled();
                        }
                        return Either.forRight(result);
                    }
                } else if (mxmlData != null && mxmlData.getRootTag() == null) {
                    ICompilationUnit offsetUnit = CompilerProjectUtils.findCompilationUnit(path, project);
                    boolean includeOpenTagBracket = getTagNeedsOpenBracket(path, currentOffset);
                    CompletionList result = new CompletionList();
                    result.setIsIncomplete(false);
                    result.setItems(new ArrayList<>());
                    autoCompleteDefinitionsForMXML(result, project, offsetUnit, offsetTag, true, includeOpenTagBracket,
                            (char) -1, null, null, null);
                    if (cancelToken != null) {
                        cancelToken.checkCanceled();
                    }
                    return Either.forRight(result);
                }
                if (offsetTag == null) {
                    // it's possible for the offset tag to be null in an MXML file, but
                    // we don't want to trigger ActionScript completion.
                    // for some reason, the offset tag will be null if completion is
                    // triggered at the asterisk:
                    // <fx:Declarations>*
                    CompletionList result = new CompletionList();
                    result.setIsIncomplete(false);
                    result.setItems(new ArrayList<>());
                    if (cancelToken != null) {
                        cancelToken.checkCanceled();
                    }
                    return Either.forRight(result);
                }
            }
            ISourceLocation offsetSourceLocation = actionScriptProjectManager
                    .getOffsetSourceLocation(path,
                            currentOffset, projectData);
            if (offsetSourceLocation instanceof IMXMLStyleNode) {
                // special case for <fx:Style>
                // because CSS shouldn't include ActionScript completion

                // IMXMLStyleNode styleNode = (IMXMLStyleNode) offsetSourceLocation;
                // ICSSDocument cssDocument = styleNode.getCSSDocument(new ArrayList<>());

                CompletionList result = new CompletionList();
                result.setIsIncomplete(false);
                result.setItems(new ArrayList<>());
                if (cancelToken != null) {
                    cancelToken.checkCanceled();
                }
                return Either.forRight(result);
            }
            if (offsetSourceLocation instanceof VSCodeASDocComment) {
                VSCodeASDocComment docComment = (VSCodeASDocComment) offsetSourceLocation;
                CompletionList result = asdocCompletion(docComment, path, position, currentOffset, projectData);
                if (cancelToken != null) {
                    cancelToken.checkCanceled();
                }
                return Either.forRight(result);
            }
            if (!(offsetSourceLocation instanceof IASNode)) {
                // we don't recognize what type this is, so don't try to treat
                // it as an IASNode
                offsetSourceLocation = null;
            }
            IASNode offsetNode = (IASNode) offsetSourceLocation;
            CompletionList result = actionScriptCompletion(offsetNode, path, position, currentOffset, projectData);
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return Either.forRight(result);
        } finally {
            completionTypes.clear();
        }
    }

    private CompletionList actionScriptCompletion(IASNode offsetNode, Path path, Position position, int currentOffset,
            ActionScriptProjectData projectData) {
        CompletionList result = new CompletionList();
        result.setIsIncomplete(false);
        result.setItems(new ArrayList<>());
        if (offsetNode == null) {
            // we couldn't find a node at the specified location
            return result;
        }
        IASNode parentNode = offsetNode.getParent();
        IASNode nodeAtPreviousOffset = null;
        if (parentNode != null) {
            nodeAtPreviousOffset = parentNode.getContainingNode(currentOffset - 1);
        } else if (offsetNode instanceof IFileNode) {
            // there is no parent, but we may still be able to detect
            // a child of the file node
            nodeAtPreviousOffset = offsetNode.getContainingNode(currentOffset - 1);
        }

        String fileText = fileTracker.getText(path);
        if (!ASTUtils.isActionScriptCompletionAllowedInNode(offsetNode, fileText, currentOffset)) {
            // if we're inside a node that shouldn't have completion!
            return result;
        }
        boolean isMXML = path.toString().endsWith(FILE_EXTENSION_MXML);
        ImportRange importRange = ImportRange.fromOffsetNode(offsetNode);
        if (isMXML) {
            IMXMLTagData offsetTag = null;
            MXMLData mxmlData = actionScriptProjectManager.getMXMLDataForPath(path, projectData);
            if (mxmlData != null) {
                offsetTag = MXMLDataUtils.getOffsetMXMLTag(mxmlData, currentOffset);
            }
            if (offsetTag != null) {
                importRange = ImportRange.fromOffsetTag(offsetTag, currentOffset);
            }
        }
        ILspProject project = projectData.project;
        AddImportData addImportData = CodeActionsUtils.findAddImportData(fileText, importRange);

        char nextChar = (char) -1;
        if (fileText.length() > currentOffset) {
            nextChar = fileText.charAt(currentOffset);
        }

        boolean isParamOfTypeFunction = ASTUtils.isOffsetNodeInsideParameterOfTypeFunction(offsetNode, fileText,
                currentOffset, project);

        // definition names
        if (nodeAtPreviousOffset != null && nodeAtPreviousOffset instanceof IKeywordNode) {
            IKeywordNode keywordNode = (IKeywordNode) nodeAtPreviousOffset;
            // no completion for the name of the symbol currently being declared
            if (ASTNodeID.KeywordFunctionID.equals(keywordNode.getNodeID())) {
                IASNode currentNodeForScope = offsetNode;
                do {
                    // just keep traversing up until we get a scoped node or we
                    // run out of nodes to check
                    if (currentNodeForScope instanceof IScopedNode) {
                        IScopedNode scopedNode = (IScopedNode) currentNodeForScope;

                        if (scopedNode.getScope() instanceof TypeScope) {
                            // get and set after function is allowed in type scope
                            autoCompleteKeyword(IASKeywordConstants.GET, result);
                            autoCompleteKeyword(IASKeywordConstants.SET, result);
                        }
                        break;
                    }
                    currentNodeForScope = currentNodeForScope.getParent();
                } while (currentNodeForScope != null);
                // don't return because the function might be an override
                // and the name can be completed
            }
            if (ASTNodeID.KeywordVarID.equals(keywordNode.getNodeID())
                    || ASTNodeID.KeywordConstID.equals(keywordNode.getNodeID())
                    || ASTNodeID.KeywordClassID.equals(keywordNode.getNodeID())
                    || ASTNodeID.KeywordInterfaceID.equals(keywordNode.getNodeID())) {
                return result;
            }
        }
        if (parentNode != null && parentNode instanceof IDefinitionNode) {
            IDefinitionNode defNode = (IDefinitionNode) parentNode;
            if (offsetNode == defNode.getNameExpressionNode()) {
                boolean skipCompletion = true;
                if (defNode instanceof IPackageNode) {
                    skipCompletion = false;
                } else if (defNode instanceof IFunctionNode) {
                    IFunctionNode funcNode = (IFunctionNode) defNode;
                    if (funcNode.hasModifier(ASModifier.OVERRIDE)) {
                        skipCompletion = false;
                    }
                }
                if (skipCompletion) {
                    // no completion for certain definition names where names
                    // shouldn't conflict with each other
                    return result;
                }
            }
        }

        // variable types (not getters or setters)
        if (offsetNode instanceof IVariableNode
                && !(offsetNode instanceof IAccessorNode)) {
            IVariableNode variableNode = (IVariableNode) offsetNode;
            IExpressionNode nameExprNode = variableNode.getNameExpressionNode();
            IExpressionNode typeNode = variableNode.getVariableTypeNode();
            if (nameExprNode != null && typeNode != null) {
                int line = position.getLine();
                int column = position.getCharacter();
                if ((line > nameExprNode.getLine()
                        || (line == nameExprNode.getLine() && column > nameExprNode.getEndColumn()))
                        && (line < typeNode.getLine()
                                || (line == typeNode.getLine() && column <= typeNode.getEndColumn()))) {
                    autoCompleteTypes(offsetNode, null, addImportData, project, result);
                    return result;
                }
            }
        }
        if (parentNode != null && parentNode instanceof IVariableNode
                && !(parentNode instanceof IAccessorNode)) {
            IVariableNode variableNode = (IVariableNode) parentNode;
            if (offsetNode == variableNode.getVariableTypeNode()) {
                autoCompleteTypes(parentNode, null, addImportData, project, result);
                return result;
            }
        }
        // function return types (including getters and setters)
        if (offsetNode instanceof IFunctionNode) {
            IFunctionNode functionNode = (IFunctionNode) offsetNode;
            IContainerNode parameters = functionNode.getParametersContainerNode();
            IExpressionNode typeNode = functionNode.getReturnTypeNode();
            if (typeNode != null) {
                int line = position.getLine();
                int column = position.getCharacter();
                if (line >= parameters.getEndLine() && column > parameters.getEndColumn() && line <= typeNode.getLine()
                        && column <= typeNode.getColumn()) {
                    autoCompleteTypes(offsetNode, null, addImportData, project, result);
                    return result;
                }
            }
        }
        if (parentNode != null && parentNode instanceof IFunctionNode) {
            IFunctionNode functionNode = (IFunctionNode) parentNode;
            if (offsetNode == functionNode.getReturnTypeNode()) {
                autoCompleteTypes(parentNode, null, addImportData, project, result);
                return result;
            }
        }
        // new keyword types
        IFunctionCallNode newExpressionCall = null;
        IDefinition priorityNewClass = null;
        if (parentNode != null && parentNode instanceof IFunctionCallNode) {
            IFunctionCallNode functionCallNode = (IFunctionCallNode) parentNode;
            if (functionCallNode.getNameNode() == offsetNode && functionCallNode.isNewExpression()) {
                newExpressionCall = functionCallNode;
                IASNode newParent = functionCallNode.getParent();
                if (newParent != null) {
                    if (newParent instanceof IVariableNode) {
                        IVariableNode variableNode = (IVariableNode) newParent;
                        IExpressionNode variableTypeNode = variableNode.getVariableTypeNode();
                        if (variableTypeNode != null) {
                            priorityNewClass = variableTypeNode.resolve(project);
                        }
                    } else if (newParent instanceof IBinaryOperatorNode) {
                        IBinaryOperatorNode binaryOpNode = (IBinaryOperatorNode) newParent;
                        if (OperatorType.ASSIGNMENT.equals(binaryOpNode.getOperator())
                                && functionCallNode.equals(binaryOpNode.getRightOperandNode())) {
                            IExpressionNode leftOperandNode = binaryOpNode.getLeftOperandNode();
                            if (leftOperandNode != null) {
                                priorityNewClass = leftOperandNode.resolveType(project);
                            }
                        }
                    }
                }
            }
        }
        if (newExpressionCall == null && nodeAtPreviousOffset != null && nodeAtPreviousOffset instanceof IKeywordNode
                && nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordNewID) {
            IASNode prevParent = nodeAtPreviousOffset.getParent();
            if (prevParent != null && prevParent instanceof IFunctionCallNode) {
                IFunctionCallNode functionCallNode = (IFunctionCallNode) prevParent;
                if (functionCallNode.isNewExpression()) {
                    newExpressionCall = functionCallNode;
                    IASNode newParent = functionCallNode.getParent();
                    if (newParent != null) {
                        if (newParent instanceof IVariableNode) {
                            IVariableNode variableNode = (IVariableNode) newParent;
                            IExpressionNode variableTypeNode = variableNode.getVariableTypeNode();
                            if (variableTypeNode != null) {
                                priorityNewClass = variableTypeNode.resolve(project);
                            }
                        } else {
                            IASNode newGP = newParent.getParent();
                            if (newGP != null && newGP instanceof IVariableNode) {
                                IVariableNode variableNode = (IVariableNode) newGP;
                                IExpressionNode assignedValueNode = variableNode.getAssignedValueNode();
                                if (assignedValueNode != null
                                        && ASTUtils.nodeContainsNode(assignedValueNode, nodeAtPreviousOffset)) {
                                    IExpressionNode variableTypeNode = variableNode.getVariableTypeNode();
                                    if (variableTypeNode != null) {
                                        priorityNewClass = variableTypeNode.resolve(project);
                                    }
                                }
                            }
                            if (priorityNewClass == null) {
                                if (newParent != null && newParent instanceof IBinaryOperatorNode) {
                                    IBinaryOperatorNode binaryOpNode = (IBinaryOperatorNode) newParent;
                                    if (OperatorType.ASSIGNMENT.equals(binaryOpNode.getOperator())
                                            && functionCallNode.equals(binaryOpNode.getRightOperandNode())) {
                                        IExpressionNode leftOperandNode = binaryOpNode.getLeftOperandNode();
                                        if (leftOperandNode != null) {
                                            priorityNewClass = leftOperandNode.resolveType(project);
                                        }
                                    }
                                }
                                if (priorityNewClass == null) {
                                    if (newGP != null && newGP instanceof IBinaryOperatorNode) {
                                        IBinaryOperatorNode binaryOpNode = (IBinaryOperatorNode) newGP;
                                        if (OperatorType.ASSIGNMENT.equals(binaryOpNode.getOperator())
                                                && newParent.equals(binaryOpNode.getRightOperandNode())) {
                                            IExpressionNode leftOperandNode = binaryOpNode.getLeftOperandNode();
                                            if (leftOperandNode != null) {
                                                priorityNewClass = leftOperandNode.resolveType(project);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (newExpressionCall != null) {
            autoCompleteTypes(parentNode, priorityNewClass, addImportData, project, result);
            return result;
        }
        // as and is keyword types
        if (parentNode != null && parentNode instanceof IBinaryOperatorNode
                && (parentNode.getNodeID() == ASTNodeID.Op_AsID || parentNode.getNodeID() == ASTNodeID.Op_IsID)) {
            IBinaryOperatorNode binaryOperatorNode = (IBinaryOperatorNode) parentNode;
            if (binaryOperatorNode.getRightOperandNode() == offsetNode) {
                autoCompleteTypes(parentNode, null, addImportData, project, result);
                return result;
            }
        }
        if (nodeAtPreviousOffset != null && nodeAtPreviousOffset instanceof IBinaryOperatorNode
                && (nodeAtPreviousOffset.getNodeID() == ASTNodeID.Op_AsID
                        || nodeAtPreviousOffset.getNodeID() == ASTNodeID.Op_IsID)) {
            autoCompleteTypes(nodeAtPreviousOffset, null, addImportData, project, result);
            return result;
        }
        // class extends keyword
        if (offsetNode instanceof IClassNode && nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IKeywordNode
                && nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordExtendsID) {
            autoCompleteTypes(offsetNode, null, addImportData, project, result);
            return result;
        }
        // class implements keyword
        if (offsetNode instanceof IClassNode && nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IKeywordNode
                && nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordImplementsID) {
            autoCompleteTypes(offsetNode, null, addImportData, project, result);
            return result;
        }
        // interface extends keyword
        if (offsetNode instanceof IInterfaceNode && nodeAtPreviousOffset != null
                && nodeAtPreviousOffset instanceof IKeywordNode
                && nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordExtendsID) {
            autoCompleteTypes(offsetNode, null, addImportData, project, result);
            return result;
        }

        // package (must be before member access)
        if (offsetNode instanceof IFileNode) {
            IFileNode fileNode = (IFileNode) offsetNode;
            if (fileNode.getChildCount() == 0 && fileNode.getAbsoluteEnd() == 0) {
                // the file is completely empty
                autoCompletePackageBlock(fileNode.getFileSpecification(), project, result);
                return result;
            }
        }
        if (parentNode != null && parentNode instanceof IFileNode) {
            IFileNode fileNode = (IFileNode) parentNode;
            if (fileNode.getChildCount() == 1 && offsetNode instanceof IIdentifierNode) {
                IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
                String identifier = identifierNode.getName();
                if (IASKeywordConstants.PACKAGE.startsWith(identifier)) {
                    // the file contains only a substring of the package keyword
                    autoCompletePackageBlock(offsetNode.getFileSpecification(), project, result);
                    return result;
                }
            }
        }
        if (offsetNode instanceof IPackageNode) {
            IPackageNode packageNode = (IPackageNode) offsetNode;
            autoCompletePackageName(packageNode.getPackageName(), offsetNode.getFileSpecification(), project, result);
            return result;
        }
        if (parentNode != null && parentNode instanceof FullNameNode) {
            IASNode gpNode = parentNode.getParent();
            if (gpNode != null && gpNode instanceof IPackageNode) {
                IPackageNode packageNode = (IPackageNode) gpNode;
                autoCompletePackageName(packageNode.getPackageName(), offsetNode.getFileSpecification(), project,
                        result);
            }
        }
        if (parentNode != null && parentNode instanceof IPackageNode) {
            // we'll get here if the last character in the package name is .
            IPackageNode packageNode = (IPackageNode) parentNode;
            IExpressionNode nameNode = packageNode.getNameExpressionNode();
            if (offsetNode == nameNode) {
                if (currentOffset == IASKeywordConstants.PACKAGE.length()) {
                    autoCompletePackageBlock(offsetNode.getFileSpecification(), project, result);
                } else {
                    autoCompletePackageName(packageNode.getPackageName(), offsetNode.getFileSpecification(), project,
                            result);
                }
                return result;
            }
        }

        // import (must be before member access)
        if (parentNode != null && parentNode instanceof IImportNode) {
            IImportNode importNode = (IImportNode) parentNode;
            IExpressionNode nameNode = importNode.getImportNameNode();
            if (offsetNode == nameNode) {
                String importName = importNode.getImportName();
                int endIndex = position.getCharacter() - nameNode.getColumn();
                if (importName.length() >= endIndex) {
                    importName = importName.substring(0, endIndex);
                    autoCompleteImport(importName, project, result);
                }
                return result;
            }
        }
        if (parentNode != null && parentNode instanceof FullNameNode) {
            IASNode gpNode = parentNode.getParent();
            if (gpNode != null && gpNode instanceof IImportNode) {
                IImportNode importNode = (IImportNode) gpNode;
                IExpressionNode nameNode = importNode.getImportNameNode();
                if (parentNode == nameNode) {
                    String importName = importNode.getImportName();
                    int endIndex = position.getCharacter() - nameNode.getColumn();
                    if (importName.length() >= endIndex) {
                        importName = importName.substring(0, endIndex);
                        autoCompleteImport(importName, project, result);
                    }
                    return result;
                }
            }
        }
        if (nodeAtPreviousOffset != null && nodeAtPreviousOffset instanceof IImportNode) {
            autoCompleteImport("", project, result);
            return result;
        }

        // member access
        if (offsetNode instanceof IMemberAccessExpressionNode) {
            IMemberAccessExpressionNode memberAccessNode = (IMemberAccessExpressionNode) offsetNode;
            IExpressionNode leftOperand = memberAccessNode.getLeftOperandNode();
            IExpressionNode rightOperand = memberAccessNode.getRightOperandNode();
            int line = position.getLine();
            int column = position.getCharacter();
            if (line >= leftOperand.getEndLine() && line <= rightOperand.getLine()) {
                if ((line != leftOperand.getEndLine() && line != rightOperand.getLine())
                        || (line == leftOperand.getEndLine() && column > leftOperand.getEndColumn())
                        || (line == rightOperand.getLine() && column <= rightOperand.getColumn())) {
                    autoCompleteMemberAccess(memberAccessNode, nextChar, isParamOfTypeFunction, addImportData, project,
                            result);
                    return result;
                }
            }
        }
        if (parentNode != null && parentNode instanceof IMemberAccessExpressionNode) {
            IMemberAccessExpressionNode memberAccessNode = (IMemberAccessExpressionNode) parentNode;
            // you would expect that the offset node could only be the right
            // operand, but it's actually possible for it to be the left operand,
            // even if the . has been typed! only sometimes, though.
            boolean isValidLeft = true;
            if (offsetNode == memberAccessNode.getLeftOperandNode()
                    && memberAccessNode.getRightOperandNode() instanceof IIdentifierNode) {
                // if the left and right operands both exist, then this is not
                // member access and we should skip ahead
                isValidLeft = false;
            }
            if (offsetNode == memberAccessNode.getRightOperandNode() || isValidLeft) {
                autoCompleteMemberAccess(memberAccessNode, nextChar, isParamOfTypeFunction, addImportData, project,
                        result);
                return result;
            }
        }
        if (nodeAtPreviousOffset != null && nodeAtPreviousOffset instanceof IMemberAccessExpressionNode) {
            // depending on the left operand, if a . is typed, the member access
            // may end up being the previous node instead of the parent or offset
            // node, so check if the right operand is empty
            IMemberAccessExpressionNode memberAccessNode = (IMemberAccessExpressionNode) nodeAtPreviousOffset;
            IExpressionNode rightOperandNode = memberAccessNode.getRightOperandNode();
            if (rightOperandNode instanceof IIdentifierNode) {
                IIdentifierNode identifierNode = (IIdentifierNode) rightOperandNode;
                if (identifierNode.getName().equals("")) {
                    autoCompleteMemberAccess(memberAccessNode, nextChar, isParamOfTypeFunction, addImportData, project,
                            result);
                    return result;
                }
            }
        }

        // function overrides
        if (nodeAtPreviousOffset instanceof IModifierNode) {
            IModifierNode modifierNode = (IModifierNode) nodeAtPreviousOffset;
            if (ASModifier.OVERRIDE.equals(modifierNode.getModifier())) {
                autoCompleteFunctionOverrides(modifierNode, addImportData, project, result);
                return result;
            }
        }
        if (offsetNode instanceof IScopedNode && offsetNode instanceof IBlockNode && parentNode instanceof IClassNode
                && nodeAtPreviousOffset instanceof IIdentifierNode) {
            IIdentifierNode previousIdentifier = (IIdentifierNode) nodeAtPreviousOffset;
            if (IASKeywordConstants.OVERRIDE.equals(previousIdentifier.getName())) {
                autoCompleteFunctionOverrides(previousIdentifier, addImportData, project, result);
                return result;
            }
        }
        if (parentNode != null && parentNode instanceof IFunctionNode && offsetNode instanceof IIdentifierNode) {
            IFunctionNode functionNode = (IFunctionNode) parentNode;
            if (offsetNode == functionNode.getNameExpressionNode()) {
                if (functionNode.hasModifier(ASModifier.OVERRIDE)
                        && functionNode.getParametersContainerNode().getAbsoluteStart() == -1
                        && functionNode.getReturnTypeNode() == null) {
                    autoCompleteFunctionOverrides(functionNode, addImportData, project, result);
                    return result;
                }
            }
        }
        if (nodeAtPreviousOffset != null && nodeAtPreviousOffset instanceof IKeywordNode
                && (nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordFunctionID
                        || nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordGetID
                        || nodeAtPreviousOffset.getNodeID() == ASTNodeID.KeywordSetID)) {
            IASNode previousNodeParent = (IASNode) nodeAtPreviousOffset.getParent();
            if (previousNodeParent instanceof IFunctionNode) {
                IFunctionNode functionNode = (IFunctionNode) previousNodeParent;
                if (functionNode.hasModifier(ASModifier.OVERRIDE)
                        && functionNode.getParametersContainerNode().getAbsoluteStart() == -1
                        && functionNode.getReturnTypeNode() == null) {
                    autoCompleteFunctionOverrides(functionNode, addImportData, project, result);
                    return result;
                }
            }
        }

        // local scope
        IASNode currentNodeForScope = offsetNode;
        do {
            // just keep traversing up until we get a scoped node or run out of
            // nodes to check
            if (currentNodeForScope instanceof IScopedNode) {
                IScopedNode scopedNode = (IScopedNode) currentNodeForScope;

                // include all members and local things that are in scope
                autoCompleteScope(scopedNode, false, nextChar, isParamOfTypeFunction, addImportData, project, result);

                // include all public definitions
                IASScope scope = scopedNode.getScope();
                IDefinition definitionToSkip = scope.getDefinition();
                autoCompleteDefinitionsForActionScript(result, project, scopedNode, false, null, definitionToSkip,
                        false, null, nextChar, null, isParamOfTypeFunction, addImportData);
                autoCompleteKeywords(scopedNode, result);
                return result;
            }
            currentNodeForScope = currentNodeForScope.getParent();
        } while (currentNodeForScope != null);

        return result;
    }

    private CompletionList mxmlCompletion(IMXMLTagData offsetTag, Path path, int currentOffset,
            ICompilationUnit offsetUnit, ILspProject project) {
        CompletionList result = new CompletionList();
        result.setIsIncomplete(false);
        result.setItems(new ArrayList<>());

        String fileText = fileTracker.getText(path);
        if (ASTUtils.isInXMLComment(fileText, currentOffset)) {
            // if we're inside a comment, no completion!
            return result;
        }

        ImportRange importRange = ImportRange.fromOffsetTag(offsetTag, currentOffset);
        AddImportData addImportData = CodeActionsUtils.findAddImportData(fileText, importRange);
        XmlnsRange xmlnsRange = XmlnsRange.fromOffsetTag(offsetTag, currentOffset);
        Position xmlnsPosition = null;
        if (xmlnsRange.endIndex >= 0) {
            xmlnsPosition = LanguageServerCompilerUtils.getPositionFromOffset(new StringReader(fileText),
                    xmlnsRange.endIndex);
        }

        boolean includeOpenTagBracket = getTagNeedsOpenBracket(path, currentOffset);

        char nextChar = (char) -1;
        if (fileText.length() > currentOffset) {
            nextChar = fileText.charAt(currentOffset);
        }

        IMXMLTagData parentTag = offsetTag.getParentTag();

        // for some reason, the attributes list includes the >, but that's not
        // what we want here, so check if currentOffset isn't the end of the tag!
        boolean isAttribute = offsetTag.isOffsetInAttributeList(currentOffset)
                && currentOffset < offsetTag.getAbsoluteEnd();
        if (isAttribute && offsetTag.isCloseTag()) {
            return result;
        }
        boolean isTagName = false;
        if (offsetTag instanceof MXMLTagData) // this shouldn't ever be false
        {
            MXMLTagData mxmlTagData = (MXMLTagData) offsetTag;
            // getNameStart() and getNameEnd() are not defined on IMXMLTagData
            isTagName = MXMLData.contains(mxmlTagData.getNameStart(), mxmlTagData.getNameEnd(), currentOffset);
        }

        // an implicit offset tag may mean that we're trying to close a tag
        if (parentTag != null && offsetTag.isImplicit()) {
            IMXMLTagData nextTag = offsetTag.getNextTag();
            if (nextTag != null && nextTag.isImplicit() && nextTag.isCloseTag()
                    && nextTag.getName().equals(parentTag.getName())
                    && parentTag.getShortName().startsWith(offsetTag.getShortName())) {
                String closeTagText = "</" + nextTag.getName() + ">";
                CompletionItem closeTagItem = new CompletionItem();
                // display the full close tag
                closeTagItem.setLabel(closeTagText);
                // strip </ from the insert text
                String insertText = closeTagText.substring(2);
                int prefixLength = offsetTag.getPrefix().length();
                if (prefixLength > 0) {
                    // if the prefix already exists, strip it away so that the
                    // editor won't duplicate it.
                    insertText = insertText.substring(prefixLength + 1);
                }
                closeTagItem.setInsertText(insertText);
                closeTagItem.setSortText(offsetTag.getShortName());
                result.getItems().add(closeTagItem);
            }
        }

        // inside <fx:Declarations>
        if (MXMLDataUtils.isDeclarationsTag(offsetTag)) {
            if (!isAttribute) {
                autoCompleteDefinitionsForMXML(result, project, offsetUnit, offsetTag, true, includeOpenTagBracket,
                        nextChar, null, addImportData, xmlnsPosition);
            }
            return result;
        }

        IDefinition offsetDefinition = MXMLDataUtils.getDefinitionForMXMLTag(offsetTag, project);
        if (offsetDefinition == null || isTagName) {
            IDefinition parentDefinition = null;
            if (parentTag != null) {
                parentDefinition = MXMLDataUtils.getDefinitionForMXMLTag(parentTag, project);
            }
            if (parentDefinition != null) {
                if (parentDefinition instanceof IClassDefinition) {
                    IClassDefinition classDefinition = (IClassDefinition) parentDefinition;
                    String offsetPrefix = offsetTag.getPrefix();
                    if (offsetPrefix.length() == 0 || parentTag.getPrefix().equals(offsetPrefix)) {
                        // only add members if the prefix is the same as the
                        // parent tag. members can't have different prefixes.
                        // also allow members when we don't have a prefix.
                        addMembersForMXMLTypeToAutoComplete(classDefinition, parentTag, offsetUnit, false, false,
                                offsetPrefix.length() == 0, nextChar, addImportData, xmlnsPosition, project, result);
                    }
                    if (!isAttribute) {
                        IFileSpecification fileSpec = fileTracker
                                .getFileSpecification(offsetUnit.getAbsoluteFilename());
                        MXMLNamespace fxNS = MXMLNamespaceUtils.getMXMLLanguageNamespace(fileSpec,
                                project.getWorkspace());
                        IMXMLData mxmlParent = offsetTag.getParent();
                        if (mxmlParent != null && parentTag.equals(mxmlParent.getRootTag())) {
                            if (offsetPrefix.length() == 0) {
                                // this tag doesn't have a prefix
                                addRootMXMLLanguageTagsToAutoComplete(offsetTag, fxNS.prefix, true,
                                        includeOpenTagBracket, result);
                            } else if (offsetPrefix.equals(fxNS.prefix)) {
                                // this tag has a prefix
                                addRootMXMLLanguageTagsToAutoComplete(offsetTag, fxNS.prefix, false, false, result);
                            }
                        }
                        if (offsetPrefix.length() == 0) {
                            // this tag doesn't have a prefix
                            addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.COMPONENT, fxNS.prefix,
                                    includeOpenTagBracket, true, result);
                        } else if (offsetPrefix.equals(fxNS.prefix)) {
                            // this tag has a prefix
                            addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.COMPONENT, fxNS.prefix, false,
                                    false, result);
                        }
                        String defaultPropertyName = classDefinition.getDefaultPropertyName(project);
                        // if [DefaultProperty] is set, then we can instantiate
                        // types as child elements
                        // but we don't want to do that when in an attribute
                        boolean allowTypesAsChildren = defaultPropertyName != null;
                        if (!allowTypesAsChildren) {
                            // similar to [DefaultProperty], if a component implements
                            // mx.core.IContainer, we can instantiate types as children
                            String containerInterface = project.getContainerInterface();
                            allowTypesAsChildren = classDefinition.isInstanceOf(containerInterface, project);
                        }
                        if (allowTypesAsChildren) {
                            String typeFilter = null;
                            if (defaultPropertyName != null) {
                                TypeScope typeScope = (TypeScope) classDefinition.getContainedScope();
                                Set<INamespaceDefinition> namespaceSet = ScopeUtils.getNamespaceSetForScopes(typeScope,
                                        typeScope, project);
                                List<IDefinition> propertiesByName = typeScope.getPropertiesByNameForMemberAccess(
                                        (CompilerProject) project, defaultPropertyName, namespaceSet);
                                if (propertiesByName.size() > 0) {
                                    IDefinition propertyDefinition = propertiesByName.get(0);
                                    typeFilter = DefinitionUtils
                                            .getMXMLChildElementTypeForDefinition(propertyDefinition, project);
                                }
                            }
                            autoCompleteTypesForMXMLFromExistingTag(result, project, offsetUnit, offsetTag, nextChar,
                                    typeFilter, xmlnsPosition);
                        }
                    }
                } else {
                    // the parent is something like a property, so matching the
                    // prefix is not required
                    autoCompleteTypesForMXMLFromExistingTag(result, project, offsetUnit, offsetTag, nextChar, null,
                            xmlnsPosition);
                }
                return result;
            } else if (MXMLDataUtils.isDeclarationsTag(parentTag)) {
                autoCompleteTypesForMXMLFromExistingTag(result, project, offsetUnit, offsetTag, nextChar, null,
                        xmlnsPosition);
                return result;
            } else if (offsetTag.getParent().getRootTag().equals(offsetTag)) {
                autoCompleteTypesForMXMLFromExistingTag(result, project, offsetUnit, offsetTag, nextChar, null,
                        xmlnsPosition);
            }
            return result;
        }
        if (offsetDefinition instanceof IClassDefinition) {
            IMXMLTagAttributeData attribute = MXMLDataUtils.getMXMLTagAttributeWithValueAtOffset(offsetTag,
                    currentOffset);
            if (attribute != null) {
                return mxmlAttributeCompletion(offsetTag, currentOffset, project, result);
            }
            attribute = MXMLDataUtils.getMXMLTagAttributeWithNameAtOffset(offsetTag, currentOffset, true);
            if (attribute != null
                    && currentOffset > (attribute.getAbsoluteStart() + attribute.getXMLName().toString().length())) {
                return mxmlStatesCompletion(offsetUnit, result);
            }

            IClassDefinition classDefinition = (IClassDefinition) offsetDefinition;
            addMembersForMXMLTypeToAutoComplete(classDefinition, offsetTag, offsetUnit, isAttribute,
                    includeOpenTagBracket, !isAttribute, nextChar, addImportData, xmlnsPosition, project, result);

            if (!isAttribute) {
                IMXMLData mxmlParent = offsetTag.getParent();
                IFileSpecification fileSpec = fileTracker.getFileSpecification(offsetUnit.getAbsoluteFilename());
                MXMLNamespace fxNS = MXMLNamespaceUtils.getMXMLLanguageNamespace(fileSpec, project.getWorkspace());
                if (mxmlParent != null && offsetTag.equals(mxmlParent.getRootTag())) {
                    addRootMXMLLanguageTagsToAutoComplete(offsetTag, fxNS.prefix, true, includeOpenTagBracket, result);
                }
                addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.COMPONENT, fxNS.prefix, includeOpenTagBracket,
                        true, result);
                String defaultPropertyName = classDefinition.getDefaultPropertyName(project);
                // if [DefaultProperty] is set, then we can instantiate
                // types as child elements
                // but we don't want to do that when in an attribute
                boolean allowTypesAsChildren = defaultPropertyName != null;
                if (!allowTypesAsChildren) {
                    // similar to [DefaultProperty], if a component implements
                    // mx.core.IContainer, we can instantiate types as children
                    String containerInterface = project.getContainerInterface();
                    allowTypesAsChildren = classDefinition.isInstanceOf(containerInterface, project);
                }
                if (allowTypesAsChildren) {
                    String typeFilter = null;
                    if (defaultPropertyName != null) {
                        TypeScope typeScope = (TypeScope) classDefinition.getContainedScope();
                        Set<INamespaceDefinition> namespaceSet = ScopeUtils.getNamespaceSetForScopes(typeScope,
                                typeScope, project);
                        List<IDefinition> propertiesByName = typeScope.getPropertiesByNameForMemberAccess(
                                (CompilerProject) project, defaultPropertyName, namespaceSet);
                        if (propertiesByName.size() > 0) {
                            IDefinition propertyDefinition = propertiesByName.get(0);
                            typeFilter = DefinitionUtils.getMXMLChildElementTypeForDefinition(propertyDefinition,
                                    project);
                        }
                    }

                    autoCompleteDefinitionsForMXML(result, project, offsetUnit, offsetTag, true, includeOpenTagBracket,
                            nextChar, typeFilter, addImportData, xmlnsPosition);
                }
            }
            return result;
        }
        if (offsetDefinition instanceof IVariableDefinition || offsetDefinition instanceof IEventDefinition
                || offsetDefinition instanceof IStyleDefinition) {
            if (!isAttribute) {
                String typeFilter = DefinitionUtils.getMXMLChildElementTypeForDefinition(offsetDefinition, project);
                autoCompleteDefinitionsForMXML(result, project, offsetUnit, offsetTag, true, includeOpenTagBracket,
                        nextChar, typeFilter, addImportData, xmlnsPosition);
            }
            return result;
        }
        if (offsetDefinition instanceof IInterfaceDefinition) {
            // <fx:Component> resolves to an IInterfaceDefinition, but there's
            // nothing to add to the result, so return it as-is and skip the
            // warning below
            return result;
        }
        System.err.println("Unknown definition for MXML completion: " + offsetDefinition.getClass());
        return result;
    }

    private CompletionList asdocCompletion(VSCodeASDocComment docComment, Path path, Position position,
            int currentOffset,
            ActionScriptProjectData projectData) {
        docComment.compile(false);
        VSCodeASDocTag offsetTag = null;
        for (String tagName : docComment.getTags().keySet()) {
            for (IASDocTag tag : docComment.getTags().get(tagName)) {
                if (!(tag instanceof VSCodeASDocTag)) {
                    continue;
                }
                VSCodeASDocTag docTag = (VSCodeASDocTag) tag;
                if (position.getLine() == docTag.getLine()
                        && position.getLine() == docTag.getEndLine()
                        && position.getCharacter() > docTag.getColumn()
                        && position.getCharacter() <= docTag.getEndColumn()) {
                    offsetTag = docTag;
                    break;
                }
            }
            if (offsetTag != null) {
                break;
            }
        }
        CompletionList result = new CompletionList();
        result.setIsIncomplete(false);
        result.setItems(new ArrayList<>());
        if (offsetTag == null) {
            int lineIndex = position.getLine() - docComment.getLine();
            String[] lines = docComment.getTokenText().split("\r?\n");
            if (lineIndex >= 0 && lineIndex < lines.length) {
                String line = lines[lineIndex];
                Matcher matcher = asdocTagAllowedPattern.matcher(line);
                // if there's no offset asdoc tag on the current line, then
                // check if the line contains any content that would prevent one
                // from being added. if the position is correct, include all
                // asdoc tags.
                if (matcher.matches()) {
                    String atGroup = matcher.group(2);
                    boolean hasAt = atGroup != null && atGroup.length() != 0;
                    if ((hasAt && position.getCharacter() == (matcher.start(2) + 1))
                            || (!hasAt && position.getCharacter() >= matcher.end(1))) {
                        autoCompleteASDocTags("", !hasAt, result);
                    }
                }
            }
        } else {
            int lineIndex = offsetTag.getLine() - docComment.getLine();
            String[] lines = docComment.getTokenText().split("\r?\n");
            if (lineIndex >= 0 && lineIndex < lines.length) {
                String line = lines[lineIndex];
                int tagStartIndex = line.indexOf('@');
                if (tagStartIndex != -1) {
                    int tagNameEndIndex = line.indexOf(' ', tagStartIndex + 1);
                    if (tagNameEndIndex == -1) {
                        tagNameEndIndex = line.length();
                    }
                    if (position.getCharacter() > tagStartIndex && position.getCharacter() <= tagNameEndIndex) {
                        String partialTagName = line.substring(tagStartIndex + 1, position.getCharacter());
                        autoCompleteASDocTags(partialTagName, false, result);
                    }
                }
            }
        }
        return result;
    }

    private void autoCompleteKeywords(IScopedNode node, CompletionList result) {
        boolean isInFunction = false;
        boolean isInClass = false;
        boolean isFileScope = false;
        boolean isTypeScope = false;
        boolean isClassScope = false;
        boolean isPackageScope = false;

        IASNode exactNode = node.getParent();
        IScopedNode currentNode = node;
        while (currentNode != null) {
            IASNode parentNode = currentNode.getParent();
            if (parentNode instanceof IFunctionNode) {
                isInFunction = true;
            }
            if (parentNode instanceof IClassNode) {
                if (parentNode == exactNode) {
                    isClassScope = true;
                }
                isInClass = true;
            }
            if (parentNode instanceof IFileNode && parentNode == exactNode) {
                isFileScope = true;
            }
            if (parentNode instanceof ITypeNode && parentNode == exactNode) {
                isTypeScope = true;
            }
            if (parentNode instanceof IPackageNode && parentNode == exactNode) {
                isPackageScope = true;
            }

            currentNode = currentNode.getContainingScope();
        }

        autoCompleteKeyword(IASKeywordConstants.AS, result);
        autoCompleteKeyword(IASKeywordConstants.BREAK, result);
        autoCompleteKeyword(IASKeywordConstants.CASE, result);
        autoCompleteKeyword(IASKeywordConstants.CATCH, result);
        if (isPackageScope || isFileScope) {
            autoCompleteKeyword(IASKeywordConstants.CLASS, result);
        }
        autoCompleteKeyword(IASKeywordConstants.CONST, result);
        autoCompleteKeyword(IASKeywordConstants.CONTINUE, result);
        autoCompleteKeyword(IASKeywordConstants.DEFAULT, result);
        autoCompleteKeyword(IASKeywordConstants.DELETE, result);
        autoCompleteKeyword(IASKeywordConstants.DO, result);
        if (isPackageScope || isFileScope) {
            autoCompleteKeyword(IASKeywordConstants.DYNAMIC, result);
        }
        autoCompleteKeyword(IASKeywordConstants.EACH, result);
        autoCompleteKeyword(IASKeywordConstants.ELSE, result);
        if (isPackageScope || isFileScope) {
            autoCompleteKeyword(IASKeywordConstants.EXTENDS, result);
            autoCompleteKeyword(IASKeywordConstants.FINAL, result);
        }
        autoCompleteKeyword(IASKeywordConstants.FINALLY, result);
        autoCompleteKeyword(IASKeywordConstants.FOR, result);
        autoCompleteKeyword(IASKeywordConstants.FUNCTION, result);
        if (isTypeScope) {
            // get keyword can only be used in a class/interface
            autoCompleteKeyword(IASKeywordConstants.GET, result);
        }
        autoCompleteKeyword(IASKeywordConstants.GOTO, result);
        autoCompleteKeyword(IASKeywordConstants.IF, result);
        if (isPackageScope || isFileScope) {
            autoCompleteKeyword(IASKeywordConstants.IMPLEMENTS, result);
        }
        autoCompleteKeyword(IASKeywordConstants.IMPORT, result);
        autoCompleteKeyword(IASKeywordConstants.IN, result);
        autoCompleteKeyword(IASKeywordConstants.INCLUDE, result);
        autoCompleteKeyword(IASKeywordConstants.INSTANCEOF, result);
        if (isPackageScope || isFileScope) {
            autoCompleteKeyword(IASKeywordConstants.INTERFACE, result);
        }
        if (!isInFunction) {
            // namespaces can't be in functions
            autoCompleteKeyword(IASKeywordConstants.INTERNAL, result);
        }
        autoCompleteKeyword(IASKeywordConstants.IS, result);
        autoCompleteKeyword(IASKeywordConstants.NAMESPACE, result);
        if (isClassScope) {
            // native keyword may only be used for class members
            autoCompleteKeyword(IASKeywordConstants.NATIVE, result);
        }
        autoCompleteKeyword(IASKeywordConstants.NEW, result);
        if (isClassScope) {
            // override keyword may only be used for class members
            autoCompleteKeyword(IASKeywordConstants.OVERRIDE, result);
        }
        if (isFileScope) {
            // a package can only be defined directly in a file
            autoCompleteKeyword(IASKeywordConstants.PACKAGE, result);
        }
        if (isPackageScope || isClassScope) {
            // namespaces can't be in functions
            autoCompleteKeyword(IASKeywordConstants.PRIVATE, result);
            autoCompleteKeyword(IASKeywordConstants.PROTECTED, result);
            autoCompleteKeyword(IASKeywordConstants.PUBLIC, result);
        }
        if (isInFunction) {
            // can only return from a function
            autoCompleteKeyword(IASKeywordConstants.RETURN, result);
        }
        if (isTypeScope) {
            // set keyword can only be used in a class/interface
            autoCompleteKeyword(IASKeywordConstants.SET, result);
        }
        if (isClassScope) {
            // static keyword may only be used for class members
            autoCompleteKeyword(IASKeywordConstants.STATIC, result);
        }
        if (isInFunction && isInClass) {
            // can only be used in functions that are in classes
            autoCompleteKeyword(IASKeywordConstants.SUPER, result);
        }
        autoCompleteKeyword(IASKeywordConstants.SWITCH, result);
        if (isInFunction) {
            // this should only be used in functions
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

    private void autoCompleteValue(String value, CompletionList result) {
        CompletionItem item = new CompletionItem();
        item.setKind(CompletionItemKind.Value);
        item.setLabel(value);
        result.getItems().add(item);
    }

    private void autoCompleteKeyword(String keyword, CompletionList result) {
        CompletionItem item = new CompletionItem();
        item.setKind(CompletionItemKind.Keyword);
        item.setLabel(keyword);
        result.getItems().add(item);
    }

    private void autoCompleteASDocTags(String partialTagName, boolean needsAt, CompletionList result) {
        for (String tag : IASDocTagConstants.TAGS) {
            if (partialTagName.length() == 0 || tag.startsWith(partialTagName)) {
                autoCompleteASDocTag(tag, needsAt, result);
            }
        }
        if (frameworkSDKIsRoyale) {
            for (String tag : IRoyaleASDocTagConstants.TAGS) {
                if (partialTagName.length() == 0 || tag.startsWith(partialTagName)) {
                    autoCompleteASDocTag(tag, needsAt, result);
                }
            }
        }
    }

    private void autoCompleteASDocTag(String tag, boolean needsAt, CompletionList result) {
        CompletionItem tagNameItem = new CompletionItem();
        // display the full close tag
        tagNameItem.setLabel("@" + tag);
        tagNameItem.setKind(CompletionItemKind.Property);
        if (!needsAt) {
            tagNameItem.setInsertText(tag);
        }
        tagNameItem.setFilterText(tag);
        result.getItems().add(tagNameItem);
    }

    private void autoCompleteTypes(IASNode withNode, IDefinition priorityNewClass, AddImportData addImportData,
            ILspProject project, CompletionList result) {
        // start by getting the types in scope
        IASNode node = withNode;
        do {
            // just keep traversing up until we get a scoped node or run out of
            // nodes to check
            if (node instanceof IScopedNode) {
                IScopedNode scopedNode = (IScopedNode) node;

                // include all members and local things that are in scope
                autoCompleteScope(scopedNode, true, (char) -1, false, addImportData, project, result);
                break;
            }
            node = node.getParent();
        } while (node != null);
        autoCompleteDefinitionsForActionScript(result, project, withNode, true, null, null, false, null, (char) -1,
                priorityNewClass, false, addImportData);
    }

    private void autoCompleteScope(IScopedNode scopedNode, boolean typesOnly, char nextChar,
            boolean isParamOfTypeFunction, AddImportData addImportData, ILspProject project, CompletionList result) {
        IScopedNode currentNode = scopedNode;
        ASScope scope = (ASScope) scopedNode.getScope();
        while (currentNode != null) {
            IASScope currentScope = currentNode.getScope();
            boolean isType = currentScope instanceof TypeScope;
            boolean staticOnly = currentNode == scopedNode && isType;
            if (currentScope instanceof TypeScope && !typesOnly) {
                TypeScope typeScope = (TypeScope) currentScope;
                addDefinitionsInTypeScopeToAutoComplete(typeScope, scope, scopedNode, true, true, false, false, null,
                        false, false, nextChar, null, isParamOfTypeFunction, addImportData, null, null, project,
                        result);
                if (!staticOnly) {
                    addDefinitionsInTypeScopeToAutoCompleteActionScript(typeScope, scope, scopedNode, false, nextChar,
                            null, isParamOfTypeFunction, addImportData, project, result);
                }
            } else {
                Collection<IDefinition> localDefs = new ArrayList<>(currentScope.getAllLocalDefinitions());
                for (IDefinition localDefinition : localDefs) {
                    if (localDefinition.getBaseName().length() == 0) {
                        continue;
                    }
                    if (typesOnly && !(localDefinition instanceof ITypeDefinition)) {
                        continue;
                    }
                    if (!staticOnly || localDefinition.isStatic()) {
                        if (localDefinition instanceof ISetterDefinition) {
                            ISetterDefinition setter = (ISetterDefinition) localDefinition;
                            IGetterDefinition getter = setter.resolveGetter(project);
                            if (getter != null) {
                                // skip the setter if there's also a getter because
                                // it would add a duplicate entry
                                continue;
                            }
                        }
                        addDefinitionAutoCompleteActionScript(localDefinition, scopedNode, nextChar, null,
                                null, isParamOfTypeFunction, addImportData, project, result);
                    }
                }
            }
            currentNode = currentNode.getContainingScope();
        }
    }

    private void autoCompleteFunctionOverrides(IASNode node, AddImportData addImportData, ILspProject project,
            CompletionList result) {
        boolean needsFunctionKeyword = true;
        String namespace = null;
        boolean isGetter = false;
        boolean isSetter = false;
        if (node instanceof IFunctionNode) {
            IFunctionNode functionNode = (IFunctionNode) node;
            namespace = functionNode.getNamespace();
            isGetter = functionNode.isGetter();
            isSetter = functionNode.isSetter();
            needsFunctionKeyword = false;
        }
        IClassNode classNode = (IClassNode) node.getAncestorOfType(IClassNode.class);
        if (classNode == null) {
            // this can happen in MXML files
            return;
        }

        IClassDefinition classDefinition = classNode.getDefinition();

        ArrayList<IDefinition> propertyDefinitions = new ArrayList<>();
        TypeScope typeScope = (TypeScope) classDefinition.getContainedScope();
        Set<INamespaceDefinition> namespaceSet = typeScope.getNamespaceSet(project);
        do {
            classDefinition = classDefinition.resolveBaseClass(project);
            if (classDefinition == null) {
                break;
            }
            typeScope = (TypeScope) classDefinition.getContainedScope();
            INamespaceDefinition protectedNamespace = classDefinition.getProtectedNamespaceReference();
            typeScope.getAllLocalProperties((CompilerProject) project, propertyDefinitions, namespaceSet,
                    protectedNamespace);
        } while (classDefinition instanceof IClassDefinition);

        List<CompletionItem> resultItems = result.getItems();
        ArrayList<String> functionNames = new ArrayList<>();
        for (IDefinition definition : propertyDefinitions) {
            if (!(definition instanceof IFunctionDefinition) || definition.isStatic()) {
                continue;
            }
            IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
            boolean otherIsGetter = functionDefinition instanceof IGetterDefinition;
            boolean otherIsSetter = functionDefinition instanceof ISetterDefinition;
            String otherNamespace = functionDefinition.getNamespaceReference().getBaseName();
            if ((isGetter && !otherIsGetter) || (isSetter && !otherIsSetter)
                    || (namespace != null && !namespace.equals(otherNamespace))) {
                continue;
            }
            String functionName = functionDefinition.getBaseName();
            if (functionName.length() == 0) {
                // vscode expects all items to have a name
                continue;
            }
            StringBuilder functionNameBuilder = new StringBuilder();
            functionNameBuilder.append(functionName);
            if (otherIsGetter) {
                functionNameBuilder.append(" (");
                functionNameBuilder.append(IASKeywordConstants.GET);
                functionNameBuilder.append(")");
            } else if (otherIsSetter) {
                functionNameBuilder.append(" (");
                functionNameBuilder.append(IASKeywordConstants.SET);
                functionNameBuilder.append(")");
            }
            String functionNameWithModifier = functionNameBuilder.toString();
            if (functionNames.contains(functionNameWithModifier)) {
                // avoid duplicates
                continue;
            }
            functionNames.add(functionNameWithModifier);

            Set<String> importedQualifiedNames = new HashSet<>();
            List<TextEdit> additionalTextEdits = new ArrayList<>();
            StringBuilder insertText = new StringBuilder();
            if (namespace == null) {
                insertText.append(otherNamespace);
                insertText.append(" ");
            }
            if (needsFunctionKeyword) {
                insertText.append(IASKeywordConstants.FUNCTION);
                insertText.append(" ");
            }
            if (!isGetter && otherIsGetter) {
                insertText.append(IASKeywordConstants.GET);
                insertText.append(" ");
            }
            if (!isSetter && otherIsSetter) {
                insertText.append(IASKeywordConstants.SET);
                insertText.append(" ");
            }
            insertText.append(functionName);
            insertText.append("(");
            IParameterDefinition[] params = functionDefinition.getParameters();
            for (int i = 0, length = params.length; i < length; i++) {
                if (i > 0) {
                    insertText.append(", ");
                }
                IParameterDefinition param = params[i];
                if (param.isRest()) {
                    insertText.append(IASLanguageConstants.REST);
                }
                insertText.append(param.getBaseName());
                ITypeDefinition paramTypeDefinition = param.resolveType(project);
                if (paramTypeDefinition != null) {
                    insertText.append(":");
                    insertText.append(paramTypeDefinition.getBaseName());

                    String paramQualifiedName = paramTypeDefinition.getQualifiedName();
                    if (!importedQualifiedNames.contains(paramQualifiedName)
                            && ASTUtils.needsImport(node, paramQualifiedName)) {
                        importedQualifiedNames.add(paramQualifiedName);
                        TextEdit textEdit = CodeActionsUtils.createTextEditForAddImport(paramTypeDefinition,
                                addImportData);
                        if (textEdit != null) {
                            additionalTextEdits.add(textEdit);
                        }
                    }
                }
                if (param.hasDefaultValue()) {
                    insertText.append(" = ");
                    Object defaultValue = param.resolveDefaultValue(project);
                    String valueAsString = DefinitionTextUtils.valueToString(defaultValue);
                    if (valueAsString != null) {
                        insertText.append(valueAsString);
                    } else {
                        // I don't know how this might happen, but this is probably
                        // a safe fallback value
                        insertText.append(IASLanguageConstants.NULL);
                    }
                }
            }
            insertText.append(")");

            ITypeDefinition returnTypeDefinition = functionDefinition.resolveReturnType(project);
            if (returnTypeDefinition != null) {
                insertText.append(":");
                insertText.append(returnTypeDefinition.getBaseName());

                String returnQualifiedName = returnTypeDefinition.getQualifiedName();
                if (!importedQualifiedNames.contains(returnQualifiedName)
                        && ASTUtils.needsImport(node, returnQualifiedName)) {
                    importedQualifiedNames.add(returnQualifiedName);
                    TextEdit textEdit = CodeActionsUtils.createTextEditForAddImport(returnTypeDefinition,
                            addImportData);
                    if (textEdit != null) {
                        additionalTextEdits.add(textEdit);
                    }
                }
            }

            CompletionItem item = CompletionItemUtils.createDefinitionItem(functionDefinition, project);
            item.setInsertText(insertText.toString());
            if ((!isGetter && otherIsGetter) || (!isSetter && otherIsSetter)) {
                item.setLabel(functionNameWithModifier);
            }
            if (additionalTextEdits.size() > 0) {
                item.setAdditionalTextEdits(additionalTextEdits);
            }
            resultItems.add(item);
        }
    }

    private void autoCompleteMemberAccess(IMemberAccessExpressionNode node, char nextChar,
            boolean isParamOfTypeFunction, AddImportData addImportData,
            ILspProject project, CompletionList result) {
        ASScope scope = (ASScope) node.getContainingScope().getScope();
        IExpressionNode leftOperand = node.getLeftOperandNode();
        IDefinition leftDefinition = leftOperand.resolve(project);
        if (leftDefinition != null && leftDefinition instanceof ITypeDefinition) {
            ITypeDefinition typeDefinition = (ITypeDefinition) leftDefinition;
            TypeScope typeScope = (TypeScope) typeDefinition.getContainedScope();
            addDefinitionsInTypeScopeToAutoCompleteActionScript(typeScope, scope, node, true, nextChar, null,
                    isParamOfTypeFunction, addImportData, project, result);
            return;
        }

        if (leftOperand instanceof IDynamicAccessNode) {
            IDynamicAccessNode dynamicAccess = (IDynamicAccessNode) leftOperand;
            IExpressionNode dynamicLeftOperandNode = dynamicAccess.getLeftOperandNode();
            ITypeDefinition leftType = dynamicLeftOperandNode.resolveType(project);
            if (leftType instanceof IAppliedVectorDefinition) {
                IAppliedVectorDefinition vectorDef = (IAppliedVectorDefinition) leftType;
                ITypeDefinition elementType = vectorDef.resolveElementType(project);
                if (elementType != null) {
                    TypeScope typeScope = (TypeScope) elementType.getContainedScope();
                    addDefinitionsInTypeScopeToAutoCompleteActionScript(typeScope, scope, node, false, nextChar,
                            null, isParamOfTypeFunction, addImportData, project, result);
                    return;
                }
            }
        }

        ITypeDefinition leftType = leftOperand.resolveType(project);
        if (leftType != null) {
            TypeScope typeScope = (TypeScope) leftType.getContainedScope();
            IFunctionNode prioritySuperFunction = null;
            if (leftOperand instanceof ILanguageIdentifierNode) {
                ILanguageIdentifierNode identifierNode = (ILanguageIdentifierNode) leftOperand;
                if (LanguageIdentifierKind.SUPER.equals(identifierNode.getKind())) {
                    IFunctionNode containingFunction = (IFunctionNode) node.getAncestorOfType(IFunctionNode.class);
                    if (containingFunction != null && containingFunction.hasModifier(ASModifier.OVERRIDE)
                            && FunctionClassification.CLASS_MEMBER
                                    .equals(containingFunction.getFunctionClassification())) {
                        prioritySuperFunction = containingFunction;
                    }
                }
            }

            addDefinitionsInTypeScopeToAutoCompleteActionScript(typeScope, scope, node, false, nextChar,
                    prioritySuperFunction, isParamOfTypeFunction, addImportData, project, result);
            return;
        }

        if (leftOperand instanceof IMemberAccessExpressionNode) {
            IMemberAccessExpressionNode memberAccess = (IMemberAccessExpressionNode) leftOperand;
            String packageName = ASTUtils.memberAccessToPackageName(memberAccess);
            if (packageName != null) {
                autoCompleteDefinitionsForActionScript(result, project, node, false, packageName, null, false, null,
                        nextChar, null, false, addImportData);
                return;
            }
        }
    }

    private void autoCompletePackageBlock(IFileSpecification fileSpec, ILspProject project, CompletionList result) {
        // we'll guess the package name based on path of the parent directory
        File unitFile = new File(fileSpec.getPath());
        unitFile = unitFile.getParentFile();
        String expectedPackage = SourcePathUtils.getPackageForDirectoryPath(unitFile.toPath(), project);
        CompletionItem packageItem = CompletionItemUtils.createPackageBlockItem(expectedPackage,
                completionSupportsSnippets || completionSupportsSimpleSnippets);
        result.getItems().add(packageItem);
    }

    private void autoCompletePackageName(String partialPackageName, IFileSpecification fileSpec, ILspProject project,
            CompletionList result) {
        File unitFile = new File(fileSpec.getPath());
        unitFile = unitFile.getParentFile();
        String expectedPackage = SourcePathUtils.getPackageForDirectoryPath(unitFile.toPath(), project);
        if (expectedPackage.length() == 0) {
            // it's the top level package
            return;
        }
        if (partialPackageName.startsWith(expectedPackage)) {
            // we already have the correct package, maybe with some extra
            return;
        }
        if (partialPackageName.contains(".") && expectedPackage.startsWith(partialPackageName)) {
            int lastDot = partialPackageName.lastIndexOf('.');
            expectedPackage = expectedPackage.substring(lastDot + 1);
        }
        CompletionItem item = new CompletionItem();
        item.setKind(CompletionItemKind.Module);
        item.setLabel(expectedPackage);
        result.getItems().add(item);
    }

    private void autoCompleteImport(String importName, ILspProject project, CompletionList result) {
        List<CompletionItem> items = result.getItems();
        for (ICompilationUnit unit : project.getCompilationUnits()) {
            if (unit == null) {
                continue;
            }
            Collection<IDefinition> definitions = null;
            try {
                definitions = unit.getFileScopeRequest().get().getExternallyVisibleDefinitions();
            } catch (Exception e) {
                // safe to ignore
                continue;
            }
            for (IDefinition definition : definitions) {
                String qualifiedName = definition.getQualifiedName();
                if (qualifiedName.equals(definition.getBaseName())) {
                    // this definition is top-level. no import required.
                    continue;
                }
                if (qualifiedName.startsWith(importName)) {
                    int index = importName.lastIndexOf(".");
                    if (index != -1) {
                        qualifiedName = qualifiedName.substring(index + 1);
                    }
                    index = qualifiedName.indexOf(".");
                    if (index > 0) {
                        qualifiedName = qualifiedName.substring(0, index);
                    }
                    CompletionItem item = new CompletionItem();
                    item.setLabel(qualifiedName);
                    if (definition.getBaseName().equals(qualifiedName)) {
                        item.setKind(LanguageServerCompilerUtils.getCompletionItemKindFromDefinition(definition));
                    } else {
                        item.setKind(CompletionItemKind.Module);
                    }
                    if (!items.contains(item)) {
                        items.add(item);
                    }
                }
            }
        }
    }

    private void addMXMLLanguageTagToAutoComplete(String tagName, String prefix, boolean includeOpenTagBracket,
            boolean includeOpenTagPrefix, CompletionList result) {
        List<CompletionItem> items = result.getItems();
        CompletionItem item = new CompletionItem();
        item.setKind(CompletionItemKind.Keyword);
        item.setLabel("fx:" + tagName);
        if (completionSupportsSnippets || completionSupportsSimpleSnippets) {
            item.setInsertTextFormat(InsertTextFormat.Snippet);
        }
        item.setFilterText(tagName);
        item.setSortText(tagName);
        StringBuilder insertTextBuilder = new StringBuilder();
        if (includeOpenTagBracket) {
            insertTextBuilder.append("<");
        }
        if (includeOpenTagPrefix) {
            insertTextBuilder.append(prefix);
            insertTextBuilder.append(IMXMLCoreConstants.colon);
        }
        String escapedTagName = tagName;
        if (completionSupportsSnippets || completionSupportsSimpleSnippets) {
            escapedTagName = tagName.replaceAll("\\$",
                    Matcher.quoteReplacement("\\$"));
        }
        insertTextBuilder.append(escapedTagName);
        insertTextBuilder.append(">");
        insertTextBuilder.append("\n");
        insertTextBuilder.append("\t");
        if (completionSupportsSnippets || completionSupportsSimpleSnippets) {
            insertTextBuilder.append("$0");
        }
        insertTextBuilder.append("\n");
        insertTextBuilder.append("<");
        insertTextBuilder.append(IMXMLCoreConstants.slash);
        insertTextBuilder.append(prefix);
        insertTextBuilder.append(IMXMLCoreConstants.colon);
        insertTextBuilder.append(escapedTagName);
        insertTextBuilder.append(">");
        item.setInsertText(insertTextBuilder.toString());
        items.add(item);
    }

    private void addRootMXMLLanguageTagsToAutoComplete(IMXMLTagData offsetTag, String prefix,
            boolean includeOpenTagPrefix, boolean includeOpenTagBracket, CompletionList result) {
        List<CompletionItem> items = result.getItems();

        CompletionItem item = new CompletionItem();
        item.setKind(CompletionItemKind.Keyword);
        item.setLabel("fx:" + IMXMLLanguageConstants.SCRIPT);
        if (completionSupportsSnippets || completionSupportsSimpleSnippets) {
            item.setInsertTextFormat(InsertTextFormat.Snippet);
        }
        item.setFilterText(IMXMLLanguageConstants.SCRIPT);
        item.setSortText(IMXMLLanguageConstants.SCRIPT);
        StringBuilder insertTextBuilder = new StringBuilder();
        if (includeOpenTagBracket) {
            insertTextBuilder.append("<");
        }
        if (includeOpenTagPrefix) {
            insertTextBuilder.append(prefix);
            insertTextBuilder.append(IMXMLCoreConstants.colon);
        }
        insertTextBuilder.append(IMXMLLanguageConstants.SCRIPT);
        insertTextBuilder.append(">");
        insertTextBuilder.append("\n");
        insertTextBuilder.append("\t");
        insertTextBuilder.append(IMXMLCoreConstants.cDataStart);
        insertTextBuilder.append("\n");
        insertTextBuilder.append("\t\t");
        if (completionSupportsSnippets || completionSupportsSimpleSnippets) {
            insertTextBuilder.append("$0");
        }
        insertTextBuilder.append("\n");
        insertTextBuilder.append("\t");
        insertTextBuilder.append(IMXMLCoreConstants.cDataEnd);
        insertTextBuilder.append("\n");
        insertTextBuilder.append("<");
        insertTextBuilder.append(IMXMLCoreConstants.slash);
        insertTextBuilder.append(prefix);
        insertTextBuilder.append(IMXMLCoreConstants.colon);
        insertTextBuilder.append(IMXMLLanguageConstants.SCRIPT);
        insertTextBuilder.append(">");
        item.setInsertText(insertTextBuilder.toString());
        items.add(item);

        addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.BINDING, prefix, includeOpenTagBracket,
                includeOpenTagPrefix, result);
        addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.DECLARATIONS, prefix, includeOpenTagBracket,
                includeOpenTagPrefix, result);
        addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.METADATA, prefix, includeOpenTagBracket,
                includeOpenTagPrefix, result);
        addMXMLLanguageTagToAutoComplete(IMXMLLanguageConstants.STYLE, prefix, includeOpenTagBracket,
                includeOpenTagPrefix, result);
    }

    private void addMembersForMXMLTypeToAutoComplete(IClassDefinition definition, IMXMLTagData offsetTag,
            ICompilationUnit offsetUnit, boolean isAttribute, boolean includeOpenTagBracket,
            boolean includeOpenTagPrefix, char nextChar, AddImportData addImportData, Position xmlnsPosition,
            ILspProject project, CompletionList result) {
        IASScope[] scopes;
        try {
            scopes = offsetUnit.getFileScopeRequest().get().getScopes();
        } catch (Exception e) {
            return;
        }
        if (scopes != null && scopes.length > 0) {
            String propertyElementPrefix = null;
            String prefix = offsetTag.getPrefix();
            if (prefix.length() > 0) {
                propertyElementPrefix = prefix;
            }
            TypeScope typeScope = (TypeScope) definition.getContainedScope();
            ASScope scope = (ASScope) scopes[0];
            addDefinitionsInTypeScopeToAutoCompleteMXML(typeScope, scope, isAttribute, propertyElementPrefix,
                    includeOpenTagBracket, includeOpenTagPrefix, addImportData, xmlnsPosition, offsetTag, project,
                    result);
            addStyleMetadataToAutoCompleteMXML(typeScope, isAttribute, propertyElementPrefix, includeOpenTagBracket,
                    includeOpenTagPrefix, nextChar, project, result);
            addEventMetadataToAutoCompleteMXML(typeScope, isAttribute, propertyElementPrefix, includeOpenTagBracket,
                    includeOpenTagPrefix, nextChar, project, result);
            if (isAttribute) {
                addLanguageAttributesToAutoCompleteMXML(typeScope, scope, nextChar, project, result);
            }
        }
    }

    private void addLanguageAttributesToAutoCompleteMXML(TypeScope typeScope, ASScope otherScope, char nextChar,
            ILspProject project, CompletionList result) {
        List<CompletionItem> items = result.getItems();

        CompletionItem includeInItem = new CompletionItem();
        includeInItem.setKind(CompletionItemKind.Keyword);
        includeInItem.setLabel(IMXMLLanguageConstants.ATTRIBUTE_INCLUDE_IN);
        if ((completionSupportsSnippets || completionSupportsSimpleSnippets) && nextChar != '=') {
            includeInItem.setInsertTextFormat(InsertTextFormat.Snippet);
            includeInItem.setInsertText(IMXMLLanguageConstants.ATTRIBUTE_INCLUDE_IN + "=\"$0\"");
        }
        items.add(includeInItem);

        CompletionItem excludeFromItem = new CompletionItem();
        excludeFromItem.setKind(CompletionItemKind.Keyword);
        excludeFromItem.setLabel(IMXMLLanguageConstants.ATTRIBUTE_EXCLUDE_FROM);
        if ((completionSupportsSnippets || completionSupportsSimpleSnippets) && nextChar != '=') {
            excludeFromItem.setInsertTextFormat(InsertTextFormat.Snippet);
            excludeFromItem.setInsertText(IMXMLLanguageConstants.ATTRIBUTE_EXCLUDE_FROM + "=\"$0\"");
        }
        items.add(excludeFromItem);

        Set<INamespaceDefinition> namespaceSet = ScopeUtils.getNamespaceSetForScopes(typeScope, otherScope, project);

        IDefinition idPropertyDefinition = typeScope.getPropertyByNameForMemberAccess((CompilerProject) project,
                IMXMLLanguageConstants.ATTRIBUTE_ID, namespaceSet);
        if (idPropertyDefinition == null) {
            CompletionItem idItem = new CompletionItem();
            idItem.setKind(CompletionItemKind.Keyword);
            idItem.setLabel(IMXMLLanguageConstants.ATTRIBUTE_ID);
            if ((completionSupportsSnippets || completionSupportsSimpleSnippets) && nextChar != '=') {
                idItem.setInsertTextFormat(InsertTextFormat.Snippet);
                idItem.setInsertText(IMXMLLanguageConstants.ATTRIBUTE_ID + "=\"$0\"");
            }
            items.add(idItem);
        }

        if (frameworkSDKIsRoyale) {
            IDefinition localIdPropertyDefinition = typeScope.getPropertyByNameForMemberAccess(
                    (CompilerProject) project, IMXMLLanguageConstants.ATTRIBUTE_LOCAL_ID, namespaceSet);
            if (localIdPropertyDefinition == null) {
                CompletionItem localIdItem = new CompletionItem();
                localIdItem.setKind(CompletionItemKind.Keyword);
                localIdItem.setLabel(IMXMLLanguageConstants.ATTRIBUTE_LOCAL_ID);
                if ((completionSupportsSnippets || completionSupportsSimpleSnippets) && nextChar != '=') {
                    localIdItem.setInsertTextFormat(InsertTextFormat.Snippet);
                    localIdItem.setInsertText(IMXMLLanguageConstants.ATTRIBUTE_LOCAL_ID + "=\"$0\"");
                }
                items.add(localIdItem);
            }
        }
    }

    private void addDefinitionsInTypeScopeToAutoCompleteActionScript(TypeScope typeScope, ASScope otherScope,
            IASNode offsetNode, boolean isStatic, char nextChar, IFunctionNode prioritySuperFunction,
            boolean isParamOfTypeFunction, AddImportData addImportData, ILspProject project, CompletionList result) {
        addDefinitionsInTypeScopeToAutoComplete(typeScope, otherScope, offsetNode, isStatic, false, false, false, null,
                false, false, nextChar, prioritySuperFunction, isParamOfTypeFunction, addImportData, null, null,
                project, result);
    }

    private void addDefinitionsInTypeScopeToAutoCompleteMXML(TypeScope typeScope, ASScope otherScope,
            boolean isAttribute, String prefix, boolean includeOpenTagBracket, boolean includeOpenTagPrefix,
            AddImportData addImportData, Position xmlnsPosition, IMXMLTagData offsetTag,
            ILspProject project, CompletionList result) {
        addDefinitionsInTypeScopeToAutoComplete(typeScope, otherScope, null, false, false, true, isAttribute,
                prefix, includeOpenTagBracket, includeOpenTagPrefix, (char) -1, null, false, addImportData,
                xmlnsPosition, offsetTag, project, result);
    }

    private void addDefinitionsInTypeScopeToAutoComplete(TypeScope typeScope, ASScope otherScope, IASNode offsetNode,
            boolean isStatic, boolean includeSuperStatics, boolean forMXML, boolean isAttribute,
            String prefix, boolean includeOpenTagBracket, boolean includeOpenTagPrefix, char nextChar,
            IFunctionNode prioritySuperFunction, boolean isParamOfTypeFunction, AddImportData addImportData,
            Position xmlnsPosition, IMXMLTagData offsetTag, ILspProject project, CompletionList result) {
        IMetaTag[] excludeMetaTags = typeScope.getDefinition()
                .getMetaTagsByName(IMetaAttributeConstants.ATTRIBUTE_EXCLUDE);
        ArrayList<IDefinition> memberAccessDefinitions = new ArrayList<>();
        Set<INamespaceDefinition> namespaceSet = ScopeUtils.getNamespaceSetForScopes(typeScope, otherScope, project);

        boolean isInterfaceScope = typeScope.getDefinition() instanceof IInterfaceDefinition;
        typeScope.getAllPropertiesForMemberAccess((CompilerProject) project, memberAccessDefinitions, namespaceSet);
        for (IDefinition localDefinition : memberAccessDefinitions) {
            if (localDefinition.isOverride()) {
                // overrides would add unnecessary duplicates to the list
                continue;
            }
            if (isInterfaceScope && localDefinition instanceof IVariableDefinition
                    && !(localDefinition instanceof IFunctionDefinition)) {
                // interfaces can't have variables, but technically, the
                // compiler will still add variables to the scope while
                // reporting an error. we might as well just skip them.
                continue;
            }
            if (excludeMetaTags != null && excludeMetaTags.length > 0) {
                boolean exclude = false;
                for (IMetaTag excludeMetaTag : excludeMetaTags) {
                    String excludeName = excludeMetaTag.getAttributeValue(IMetaAttributeConstants.NAME_EXCLUDE_NAME);
                    if (excludeName.equals(localDefinition.getBaseName())) {
                        exclude = true;
                        break;
                    }
                }
                if (exclude) {
                    continue;
                }
            }
            // there are some things that we need to skip in MXML
            if (forMXML) {
                if (localDefinition instanceof IGetterDefinition) {
                    // no getters because we can only set
                    continue;
                } else if (localDefinition instanceof IFunctionDefinition
                        && !(localDefinition instanceof ISetterDefinition)) {
                    // no calling functions, unless they're setters
                    continue;
                }
            } else // actionscript
            {
                if (localDefinition instanceof ISetterDefinition) {
                    ISetterDefinition setter = (ISetterDefinition) localDefinition;
                    IGetterDefinition getter = setter.resolveGetter(project);
                    if (getter != null) {
                        // skip the setter if there's also a getter because it
                        // would add a duplicate entry
                        continue;
                    }
                }
            }
            if (isStatic) {
                if (!localDefinition.isStatic()) {
                    // if we want static members, and the definition isn't
                    // static, skip it
                    continue;
                }
                if (!includeSuperStatics && localDefinition.getParent() != typeScope.getContainingDefinition()) {
                    // if we want static members, then members from base classes
                    // aren't available with member access
                    continue;
                }
            }
            if (!isStatic && localDefinition.isStatic()) {
                // if we want non-static members, and the definition is static,
                // skip it!
                continue;
            }
            if (forMXML) {
                addDefinitionAutoCompleteMXML(localDefinition, xmlnsPosition, isAttribute, prefix, null,
                        includeOpenTagBracket, includeOpenTagPrefix, nextChar, offsetTag, project, result);
            } else // actionscript
            {
                addDefinitionAutoCompleteActionScript(localDefinition, offsetNode, nextChar,
                        prioritySuperFunction, null, isParamOfTypeFunction, addImportData, project, result);
            }
        }
    }

    private void addEventMetadataToAutoCompleteMXML(TypeScope typeScope, boolean isAttribute, String prefix,
            boolean includeOpenTagBracket, boolean includeOpenTagPrefix, char nextChar, ILspProject project,
            CompletionList result) {
        ArrayList<String> eventNames = new ArrayList<>();
        IDefinition definition = typeScope.getDefinition();
        while (definition instanceof IClassDefinition) {
            IClassDefinition classDefinition = (IClassDefinition) definition;
            IMetaTag[] eventMetaTags = definition.getMetaTagsByName(IMetaAttributeConstants.ATTRIBUTE_EVENT);
            for (IMetaTag eventMetaTag : eventMetaTags) {
                String eventName = eventMetaTag.getAttributeValue(IMetaAttributeConstants.NAME_EVENT_NAME);
                if (eventName == null || eventName.length() == 0) {
                    // vscode expects all items to have a name
                    continue;
                }
                if (eventNames.contains(eventName)) {
                    // avoid duplicates!
                    continue;
                }
                eventNames.add(eventName);
                IDefinition eventDefinition = project.resolveSpecifier(classDefinition, eventName);
                if (eventDefinition == null) {
                    continue;
                }
                String escapedEventName = eventName;
                if (completionSupportsSnippets || completionSupportsSimpleSnippets) {
                    escapedEventName = eventName.replaceAll("\\$",
                            Matcher.quoteReplacement("\\$"));
                }
                CompletionItem item = CompletionItemUtils.createDefinitionItem(eventDefinition, project);
                if (isAttribute && (completionSupportsSnippets || completionSupportsSimpleSnippets)
                        && nextChar != '=') {
                    item.setInsertTextFormat(InsertTextFormat.Snippet);
                    item.setInsertText(escapedEventName + "=\"$0\"");
                } else if (!isAttribute) {
                    StringBuilder insertTextBuilder = new StringBuilder();
                    if (includeOpenTagBracket) {
                        insertTextBuilder.append("<");
                    }
                    if (includeOpenTagPrefix && prefix != null && prefix.length() > 0) {
                        insertTextBuilder.append(prefix);
                        insertTextBuilder.append(IMXMLCoreConstants.colon);
                    }
                    insertTextBuilder.append(escapedEventName);
                    if (completionSupportsSnippets || completionSupportsSimpleSnippets) {
                        item.setInsertTextFormat(InsertTextFormat.Snippet);
                        insertTextBuilder.append(">");
                        insertTextBuilder.append("$0");
                        insertTextBuilder.append("</");
                        if (prefix != null && prefix.length() > 0) {
                            insertTextBuilder.append(prefix);
                            insertTextBuilder.append(IMXMLCoreConstants.colon);
                        }
                        insertTextBuilder.append(escapedEventName);
                        insertTextBuilder.append(">");
                    }
                    item.setInsertText(insertTextBuilder.toString());
                }
                result.getItems().add(item);
            }
            definition = classDefinition.resolveBaseClass(project);
        }
    }

    private void addStyleMetadataToAutoCompleteMXML(TypeScope typeScope, boolean isAttribute, String prefix,
            boolean includeOpenTagBracket, boolean includeOpenTagPrefix, char nextChar, ILspProject project,
            CompletionList result) {
        ArrayList<String> styleNames = new ArrayList<>();
        IDefinition definition = typeScope.getDefinition();
        List<CompletionItem> items = result.getItems();
        while (definition instanceof IClassDefinition) {
            IClassDefinition classDefinition = (IClassDefinition) definition;
            IMetaTag[] styleMetaTags = definition.getMetaTagsByName(IMetaAttributeConstants.ATTRIBUTE_STYLE);
            for (IMetaTag styleMetaTag : styleMetaTags) {
                String styleName = styleMetaTag.getAttributeValue(IMetaAttributeConstants.NAME_STYLE_NAME);
                if (styleName == null || styleName.length() == 0) {
                    // vscode expects all items to have a name
                    continue;
                }
                if (styleNames.contains(styleName)) {
                    // avoid duplicates!
                    continue;
                }
                styleNames.add(styleName);
                IDefinition styleDefinition = project.resolveSpecifier(classDefinition, styleName);
                if (styleDefinition == null) {
                    continue;
                }
                boolean foundExisting = false;
                for (CompletionItem item : items) {
                    if (item.getLabel().equals(styleName)) {
                        // we want to avoid adding a duplicate item with the same
                        // name. in flex, it's possible for a component to have
                        // a property and a style with the same name.
                        // if there's a conflict, the compiler will know how to handle it.
                        foundExisting = true;
                        break;
                    }
                }
                if (foundExisting) {
                    break;
                }

                String escapedStyleName = styleName;
                if (completionSupportsSnippets || completionSupportsSimpleSnippets) {
                    escapedStyleName = styleName.replaceAll("\\$",
                            Matcher.quoteReplacement("\\$"));
                }
                CompletionItem item = CompletionItemUtils.createDefinitionItem(styleDefinition, project);
                if (isAttribute && (completionSupportsSnippets || completionSupportsSimpleSnippets)
                        && nextChar != '=') {
                    item.setInsertTextFormat(InsertTextFormat.Snippet);
                    item.setInsertText(escapedStyleName + "=\"$0\"");
                } else if (!isAttribute) {
                    StringBuilder insertTextBuilder = new StringBuilder();
                    if (includeOpenTagBracket) {
                        insertTextBuilder.append("<");
                    }
                    if (includeOpenTagPrefix && prefix != null && prefix.length() > 0) {
                        insertTextBuilder.append(prefix);
                        insertTextBuilder.append(IMXMLCoreConstants.colon);
                    }
                    insertTextBuilder.append(escapedStyleName);
                    if (completionSupportsSnippets || completionSupportsSimpleSnippets) {
                        item.setInsertTextFormat(InsertTextFormat.Snippet);
                        insertTextBuilder.append(">");
                        insertTextBuilder.append("$0");
                        insertTextBuilder.append("</");
                        if (prefix != null && prefix.length() > 0) {
                            insertTextBuilder.append(prefix);
                            insertTextBuilder.append(IMXMLCoreConstants.colon);
                        }
                        insertTextBuilder.append(escapedStyleName);
                        insertTextBuilder.append(">");
                    }
                    item.setInsertText(insertTextBuilder.toString());
                }
                items.add(item);
            }
            definition = classDefinition.resolveBaseClass(project);
        }
    }

    private void addMXMLTypeDefinitionAutoComplete(ITypeDefinition definition, Position xmlnsPosition,
            ICompilationUnit offsetUnit, IMXMLTagData offsetTag, boolean includeOpenTagBracket, char nextChar,
            ILspProject project, CompletionList result) {
        IMXMLDataManager mxmlDataManager = project.getWorkspace().getMXMLDataManager();
        MXMLData mxmlData = (MXMLData) mxmlDataManager
                .get(fileTracker.getFileSpecification(offsetUnit.getAbsoluteFilename()));
        MXMLNamespace discoveredNS = MXMLNamespaceUtils.getMXMLNamespaceForTypeDefinition(definition, mxmlData,
                project);
        addDefinitionAutoCompleteMXML(definition, xmlnsPosition, false, discoveredNS.prefix, discoveredNS.uri,
                includeOpenTagBracket, true, nextChar, offsetTag, project, result);
    }

    private void addDefinitionAutoCompleteActionScript(IDefinition definition, IASNode offsetNode,
            char nextChar, IFunctionNode prioritySuperFunction, IDefinition priorityNewClass,
            boolean isParamOfTypeFunction, AddImportData addImportData, ILspProject project, CompletionList result) {
        String definitionBaseName = definition.getBaseName();
        if (definitionBaseName.length() == 0) {
            // vscode expects all items to have a name
            return;
        }
        if (definitionBaseName.startsWith(VECTOR_HIDDEN_PREFIX)) {
            return;
        }
        if (isDuplicateTypeDefinition(definition)) {
            return;
        }
        int priority = 0;
        if (priorityNewClass != null && definition instanceof IClassDefinition) {
            IClassDefinition classDefinition = (IClassDefinition) definition;
            if (classDefinition.equals(priorityNewClass)) {
                // exact match has higher priority than subclasses
                priority += 2;
            } else {
                IClassIterator classIterator = classDefinition.classIterator(project, false);
                while (classIterator.hasNext()) {
                    IClassDefinition otherClass = classIterator.next();
                    if (otherClass.equals(priorityNewClass)) {
                        // subclasses have lower priority than exact match
                        priority += 1;
                    }
                }
            }
        }
        if (definition instanceof IFunctionDefinition) {
            IFunctionDefinition funcDef = (IFunctionDefinition) definition;
            FunctionClassification functionClassification = funcDef.getFunctionClassification();
            if (functionClassification != null) {
                switch (functionClassification) {
                    case INTERFACE_MEMBER:
                        priority += 1;
                        break;
                    case CLASS_MEMBER:
                        priority += 1;
                        if (prioritySuperFunction != null
                                && prioritySuperFunction.getName().equals(funcDef.getBaseName())) {
                            priority += 1;
                        }
                        break;
                    case LOCAL:
                        priority += 2;
                        break;
                    default:
                }
            }
        } else if (definition instanceof IVariableDefinition) {
            IVariableDefinition varDef = (IVariableDefinition) definition;
            VariableClassification variableClassification = varDef.getVariableClassification();
            if (variableClassification != null) {
                switch (varDef.getVariableClassification()) {
                    case INTERFACE_MEMBER:
                        priority += 1;
                        break;
                    case CLASS_MEMBER:
                        priority += 1;
                        break;
                    case PARAMETER:
                        priority += 2;
                        break;
                    case LOCAL:
                        priority += 2;
                        break;
                    default:
                }
            }

        } else if (definition instanceof ITypeDefinition) {
            ITypeDefinition typeDefinition = (ITypeDefinition) definition;
            String qualifiedName = typeDefinition.getQualifiedName();
            completionTypes.add(qualifiedName);

            if (ASTUtils.isExplicitlyImported(offsetNode, qualifiedName)) {
                priority += 1;
            }
        }

        CompletionItem item = CompletionItemUtils.createDefinitionItem(definition, project);
        if (priority > 0) {
            // if we ever target JDK 11, use repeat() instead
            item.setSortText(String.join("", Collections.nCopies(3 + priority, "0")) + definitionBaseName);
        }
        if (definition instanceof IFunctionDefinition && !(definition instanceof IAccessorDefinition) && nextChar != '('
                && (completionSupportsSnippets || completionSupportsSimpleSnippets) && !isParamOfTypeFunction) {
            IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
            String functionName = definition.getBaseName();

            if (functionDefinition.getParameters().length == 0) {
                item.setInsertText(functionName + "()");
            } else {
                item.setInsertTextFormat(InsertTextFormat.Snippet);
                String escapedFunctionName = functionName.replaceAll("\\$",
                        Matcher.quoteReplacement("\\$"));
                item.setInsertText(escapedFunctionName + "($0)");
                Command showParamsCommand = new Command();
                showParamsCommand.setTitle("Parameters");
                showParamsCommand.setCommand("editor.action.triggerParameterHints");
                item.setCommand(showParamsCommand);
            }
        }
        if (ASTUtils.needsImport(offsetNode, definition.getQualifiedName())) {
            TextEdit textEdit = CodeActionsUtils.createTextEditForAddImport(definition, addImportData);
            if (textEdit != null) {
                item.setAdditionalTextEdits(Collections.singletonList(textEdit));
            }
        }
        result.getItems().add(item);
    }

    private void addDefinitionAutoCompleteMXML(IDefinition definition, Position xmlnsPosition, boolean isAttribute,
            String prefix, String uri, boolean includeOpenTagBracket, boolean includeOpenTagPrefix, char nextChar,
            IMXMLTagData offsetTag, ILspProject project, CompletionList result) {
        if (definition.getBaseName().startsWith(VECTOR_HIDDEN_PREFIX)) {
            return;
        }
        if (isDuplicateTypeDefinition(definition)) {
            return;
        }
        if (definition instanceof ITypeDefinition) {
            String qualifiedName = definition.getQualifiedName();
            completionTypes.add(qualifiedName);
        }
        String definitionBaseName = definition.getBaseName();
        if (definitionBaseName.length() == 0) {
            // vscode expects all items to have a name
            return;
        }
        CompletionItem item = CompletionItemUtils.createDefinitionItem(definition, project);
        String escapedDefinitionBaseName = definitionBaseName;
        if (completionSupportsSnippets || completionSupportsSimpleSnippets) {
            escapedDefinitionBaseName = definitionBaseName.replaceAll("\\$",
                    Matcher.quoteReplacement("\\$"));
        }
        if (isAttribute && (completionSupportsSnippets || completionSupportsSimpleSnippets) && nextChar != '=') {
            item.setInsertTextFormat(InsertTextFormat.Snippet);
            item.setInsertText(escapedDefinitionBaseName + "=\"$0\"");
        } else if (!isAttribute) {
            if (definition instanceof IVariableDefinition) {
                // start with priority 2 to be higher than the extra boost that
                // types get from matching namespaces
                int priority = 2;
                IVariableDefinition varDef = (IVariableDefinition) definition;
                VariableClassification variableClassification = varDef.getVariableClassification();
                if (variableClassification != null) {
                    switch (varDef.getVariableClassification()) {
                        case INTERFACE_MEMBER:
                            priority += 1;
                            break;
                        case CLASS_MEMBER:
                            priority += 1;
                            break;
                        case PARAMETER:
                            priority += 2;
                            break;
                        case LOCAL:
                            priority += 2;
                            break;
                        default:
                    }
                }
                if (priority > 0) {
                    // if we ever target JDK 11, use repeat() instead
                    item.setSortText(String.join("", Collections.nCopies(3 + priority, "0")) + definitionBaseName);
                }
            } else if (definition instanceof ITypeDefinition) {
                if (includeOpenTagPrefix && prefix != null && prefix.length() > 0) {
                    StringBuilder labelBuilder = new StringBuilder();
                    labelBuilder.append(prefix);
                    labelBuilder.append(IMXMLCoreConstants.colon);
                    labelBuilder.append(definitionBaseName);
                    item.setLabel(labelBuilder.toString());
                    item.setFilterText(definitionBaseName);
                }

                int priority = 0;
                if (offsetTag != null && uri != null && prefix != null) {
                    PrefixMap compositePrefixMap = offsetTag.getCompositePrefixMap();
                    if (uri.equals(compositePrefixMap.getNamespaceForPrefix(prefix))) {
                        // prefer namespaces that are explicitly declared already
                        priority += 1;
                    }
                    IMXMLTagData rootTag = offsetTag.getParent().getRootTag();
                    if (rootTag != null && uri.equals(rootTag.getURI())) {
                        // prefer the root tag's namespace
                        // especially useful for Royale's many component sets,
                        // but also for Flex's Spark and MX
                        priority += 1;
                    }
                }
                if (priority > 0) {
                    // if we ever target JDK 11, use repeat() instead
                    item.setSortText(String.join("", Collections.nCopies(3 + priority, "0")) + definitionBaseName);
                } else {
                    item.setSortText(definitionBaseName);
                }
            }
            StringBuilder insertTextBuilder = new StringBuilder();
            if (includeOpenTagBracket) {
                insertTextBuilder.append("<");
            }
            if (includeOpenTagPrefix && prefix != null && prefix.length() > 0) {
                insertTextBuilder.append(prefix);
                insertTextBuilder.append(IMXMLCoreConstants.colon);
            }
            insertTextBuilder.append(escapedDefinitionBaseName);
            if (definition instanceof ITypeDefinition && prefix != null && prefix.length() > 0
                    && (offsetTag == null || offsetTag.equals(offsetTag.getParent().getRootTag()))
                    && xmlnsPosition == null) {
                // if this is the root tag, we should add the XML namespace and
                // close the tag automatically
                insertTextBuilder.append(" ");
                if (!uri.equals(IMXMLLanguageConstants.NAMESPACE_MXML_2009)
                        && !uri.equals(IMXMLLanguageConstants.NAMESPACE_MXML_2006)) {
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
                if (completionSupportsSnippets || completionSupportsSimpleSnippets) {
                    item.setInsertTextFormat(InsertTextFormat.Snippet);
                    insertTextBuilder.append("$0");
                }
                insertTextBuilder.append("\n</");
                insertTextBuilder.append(prefix);
                insertTextBuilder.append(IMXMLCoreConstants.colon);
                insertTextBuilder.append(escapedDefinitionBaseName);
                insertTextBuilder.append(">");
            }
            if ((completionSupportsSnippets || completionSupportsSimpleSnippets)
                    && !(definition instanceof ITypeDefinition)) {
                item.setInsertTextFormat(InsertTextFormat.Snippet);
                insertTextBuilder.append(">");
                insertTextBuilder.append("$0");
                insertTextBuilder.append("</");
                if (prefix != null && prefix.length() > 0) {
                    insertTextBuilder.append(prefix);
                    insertTextBuilder.append(IMXMLCoreConstants.colon);
                }
                insertTextBuilder.append(escapedDefinitionBaseName);
                insertTextBuilder.append(">");
            }
            item.setInsertText(insertTextBuilder.toString());
            if (definition instanceof ITypeDefinition && prefix != null && prefix.length() > 0 && uri != null
                    && MXMLDataUtils.needsNamespace(offsetTag, prefix, uri) && xmlnsPosition != null) {
                TextEdit textEdit = CodeActionsUtils.createTextEditForAddMXMLNamespace(prefix, uri, xmlnsPosition);
                if (textEdit != null) {
                    item.setAdditionalTextEdits(Collections.singletonList(textEdit));
                }
            }
        }
        result.getItems().add(item);
    }

    private boolean getTagNeedsOpenBracket(Path path, int currentOffset) {
        boolean tagNeedsOpenBracket = currentOffset == 0;
        if (currentOffset > 0) {
            Reader reader = fileTracker.getReader(path);
            if (reader != null) {
                try {
                    reader.skip(currentOffset - 1);
                    char prevChar = (char) reader.read();
                    tagNeedsOpenBracket = prevChar != '<';
                } catch (IOException e) {
                    // just ignore it
                } finally {
                    try {
                        reader.close();
                    } catch (IOException e) {
                    }
                }
            }
        }
        return tagNeedsOpenBracket;
    }

    private CompletionList mxmlStatesCompletion(ICompilationUnit unit, CompletionList result) {
        List<IDefinition> definitions = unit.getDefinitionPromises();
        if (definitions.size() == 0) {
            return result;
        }
        IDefinition definition = definitions.get(0);
        if (definition instanceof DefinitionPromise) {
            DefinitionPromise definitionPromise = (DefinitionPromise) definition;
            definition = definitionPromise.getActualDefinition();
        }
        if (definition instanceof IClassDefinition) {
            List<CompletionItem> items = result.getItems();
            IClassDefinition classDefinition = (IClassDefinition) definition;
            Set<String> stateNames = classDefinition.getStateNames();
            for (String stateName : stateNames) {
                CompletionItem stateItem = new CompletionItem();
                stateItem.setKind(CompletionItemKind.Field);
                stateItem.setLabel(stateName);
                items.add(stateItem);
            }
            ITypeNode typeNode = classDefinition.getNode();
            if (typeNode != null && typeNode instanceof IMXMLClassDefinitionNode) {
                IMXMLClassDefinitionNode mxmlClassNode = (IMXMLClassDefinitionNode) typeNode;
                Set<String> stateGroupNames = mxmlClassNode.getStateGroupNames();
                for (String stateGroupName : stateGroupNames) {
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

    private CompletionList mxmlAttributeCompletion(IMXMLTagData offsetTag, int currentOffset, ILspProject project,
            CompletionList result) {
        List<CompletionItem> items = result.getItems();
        IDefinition attributeDefinition = MXMLDataUtils.getDefinitionForMXMLTagAttribute(offsetTag, currentOffset, true,
                project);
        if (attributeDefinition instanceof IVariableDefinition) {
            IVariableDefinition variableDefinition = (IVariableDefinition) attributeDefinition;
            if (variableDefinition.getTypeAsDisplayString().equals(IASLanguageConstants.Boolean)) {
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
            IMetaTag inspectableTag = variableDefinition
                    .getMetaTagByName(IMetaAttributeConstants.ATTRIBUTE_INSPECTABLE);
            if (inspectableTag == null) {
                if (variableDefinition instanceof IAccessorDefinition) {
                    IAccessorDefinition accessorDefinition = (IAccessorDefinition) variableDefinition;
                    IAccessorDefinition otherAccessorDefinition = accessorDefinition
                            .resolveCorrespondingAccessor(project);
                    if (otherAccessorDefinition != null) {
                        inspectableTag = otherAccessorDefinition
                                .getMetaTagByName(IMetaAttributeConstants.ATTRIBUTE_INSPECTABLE);
                    }
                }
            }
            if (inspectableTag != null) {
                IMetaTagAttribute enumAttribute = inspectableTag
                        .getAttribute(IMetaAttributeConstants.NAME_INSPECTABLE_ENUMERATION);
                if (enumAttribute != null) {
                    String joinedValue = enumAttribute.getValue();
                    String[] values = joinedValue.split(",");
                    for (String value : values) {
                        value = value.trim();
                        if (value.length() == 0) {
                            // skip empty values
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
        if (attributeDefinition instanceof IStyleDefinition) {
            IStyleDefinition styleDefinition = (IStyleDefinition) attributeDefinition;
            for (String enumValue : styleDefinition.getEnumeration()) {
                CompletionItem styleItem = new CompletionItem();
                styleItem.setKind(CompletionItemKind.Value);
                styleItem.setLabel(enumValue);
                items.add(styleItem);
            }
        }
        return result;
    }

    /**
     * Using an existing tag, that may already have a prefix or short name, populate
     * the completion list.
     */
    private void autoCompleteTypesForMXMLFromExistingTag(CompletionList result, ILspProject project,
            ICompilationUnit offsetUnit, IMXMLTagData offsetTag, char nextChar, String typeFilter,
            Position xmlnsPosition) {
        IMXMLDataManager mxmlDataManager = project.getWorkspace().getMXMLDataManager();
        MXMLData mxmlData = (MXMLData) mxmlDataManager
                .get(fileTracker.getFileSpecification(offsetUnit.getAbsoluteFilename()));
        String tagStartShortNameForComparison = offsetTag.getShortName().toLowerCase();
        String tagPrefix = offsetTag.getPrefix();
        String tagNamespace = null;
        PrefixMap prefixMap = mxmlData.getRootTagPrefixMap();
        if (prefixMap != null) {
            // could be null if this is the root tag and no prefixes are defined
            tagNamespace = prefixMap.getNamespaceForPrefix(tagPrefix);
        }
        String tagNamespacePackage = null;
        if (tagNamespace != null && tagNamespace.endsWith("*")) {
            if (tagNamespace.length() > 1) {
                tagNamespacePackage = tagNamespace.substring(0, tagNamespace.length() - 2);
            } else // top level
            {
                tagNamespacePackage = "";
            }
        }

        for (ICompilationUnit unit : project.getCompilationUnits()) {
            if (unit == null) {
                continue;
            }
            Collection<IDefinition> definitions = null;
            try {
                definitions = unit.getFileScopeRequest().get().getExternallyVisibleDefinitions();
            } catch (Exception e) {
                // safe to ignore
                continue;
            }

            for (IDefinition definition : definitions) {
                if (!(definition instanceof ITypeDefinition)) {
                    continue;
                }
                ITypeDefinition typeDefinition = (ITypeDefinition) definition;
                if (typeFilter != null && !DefinitionUtils.extendsOrImplements(project, typeDefinition, typeFilter)) {
                    continue;
                }

                // first check that the tag either doesn't have a short name yet
                // or that the definition's base name matches the short name
                if (tagStartShortNameForComparison.length() == 0
                        || typeDefinition.getBaseName().toLowerCase().startsWith(tagStartShortNameForComparison)) {
                    // if a prefix already exists, make sure the definition is
                    // in a namespace with that prefix
                    if (tagPrefix.length() > 0) {
                        Collection<XMLName> tagNames = project.getTagNamesForClass(typeDefinition.getQualifiedName());
                        for (XMLName tagName : tagNames) {
                            String tagNameNamespace = tagName.getXMLNamespace();
                            // getTagNamesForClass() returns the 2006 namespace, even if that's
                            // not what we're using in this file
                            if (tagNameNamespace.equals(IMXMLLanguageConstants.NAMESPACE_MXML_2006)) {
                                // use the language namespace of the root tag instead
                                tagNameNamespace = mxmlData.getRootTag().getMXMLDialect().getLanguageNamespace();
                            }
                            if (prefixMap != null) {
                                String[] prefixes = prefixMap.getPrefixesForNamespace(tagNameNamespace);
                                for (String otherPrefix : prefixes) {
                                    if (tagPrefix.equals(otherPrefix)) {
                                        addDefinitionAutoCompleteMXML(typeDefinition, xmlnsPosition, false, null, null,
                                                false, false, nextChar, offsetTag, project, result);
                                    }
                                }
                            }
                        }
                        if (tagNamespacePackage != null
                                && tagNamespacePackage.equals(typeDefinition.getPackageName())) {
                            addDefinitionAutoCompleteMXML(typeDefinition, xmlnsPosition, false, null, null, false,
                                    false, nextChar, offsetTag, project, result);
                        }
                    } else {
                        // no prefix yet, so complete the definition with a prefix
                        MXMLNamespace ns = MXMLNamespaceUtils.getMXMLNamespaceForTypeDefinition(typeDefinition,
                                mxmlData, project);
                        addDefinitionAutoCompleteMXML(typeDefinition, xmlnsPosition, false, ns.prefix, ns.uri, false,
                                true, nextChar, offsetTag, project, result);
                    }
                }
            }
        }
    }

    private void autoCompleteDefinitionsForMXML(CompletionList result, ILspProject project, ICompilationUnit offsetUnit,
            IMXMLTagData offsetTag, boolean typesOnly, boolean includeOpenTagBracket, char nextChar, String typeFilter,
            AddImportData addImportData, Position xmlnsPosition) {
        for (ICompilationUnit unit : project.getCompilationUnits()) {
            if (unit == null) {
                continue;
            }
            Collection<IDefinition> definitions = null;
            try {
                definitions = unit.getFileScopeRequest().get().getExternallyVisibleDefinitions();
            } catch (Exception e) {
                // safe to ignore
                continue;
            }
            for (IDefinition definition : definitions) {
                boolean isType = definition instanceof ITypeDefinition;
                if (!typesOnly || isType) {
                    if (isType) {
                        IMetaTag excludeClassMetaTag = definition
                                .getMetaTagByName(IMetaAttributeConstants.ATTRIBUTE_EXCLUDECLASS);
                        if (excludeClassMetaTag != null) {
                            // skip types with [ExcludeClass] metadata
                            continue;
                        }
                    }
                    if (isType) {
                        ITypeDefinition typeDefinition = (ITypeDefinition) definition;
                        if (typeFilter != null
                                && !DefinitionUtils.extendsOrImplements(project, typeDefinition, typeFilter)) {
                            continue;
                        }

                        addMXMLTypeDefinitionAutoComplete(typeDefinition, xmlnsPosition, offsetUnit, offsetTag,
                                includeOpenTagBracket, nextChar, project, result);
                    } else {
                        addDefinitionAutoCompleteActionScript(definition, null, (char) -1, null, null,
                                false, addImportData, project, result);
                    }
                }
            }
        }
    }

    private void autoCompleteDefinitionsForActionScript(CompletionList result, ILspProject project, IASNode offsetNode,
            boolean typesOnly, String requiredPackageName, IDefinition definitionToSkip, boolean includeOpenTagBracket,
            String typeFilter, char nextChar, IDefinition priorityNewClass, boolean isParamOfTypeFunction,
            AddImportData addImportData) {
        String skipQualifiedName = null;
        if (definitionToSkip != null) {
            skipQualifiedName = definitionToSkip.getQualifiedName();
        }
        Collection<ICompilationUnit> units = new ArrayList<>(project.getCompilationUnits());
        for (ICompilationUnit unit : units) {
            if (unit == null) {
                continue;
            }
            Collection<IDefinition> definitions = null;
            try {
                definitions = unit.getFileScopeRequest().get().getExternallyVisibleDefinitions();
            } catch (Exception e) {
                // safe to ignore
                continue;
            }
            for (IDefinition definition : definitions) {
                boolean isType = definition instanceof ITypeDefinition;
                if (!typesOnly || isType) {
                    if (requiredPackageName == null || definition.getPackageName().equals(requiredPackageName)) {
                        if (skipQualifiedName != null && skipQualifiedName.equals(definition.getQualifiedName())) {
                            continue;
                        }
                        if (isType) {
                            IMetaTag excludeClassMetaTag = definition
                                    .getMetaTagByName(IMetaAttributeConstants.ATTRIBUTE_EXCLUDECLASS);
                            if (excludeClassMetaTag != null) {
                                // skip types with [ExcludeClass] metadata
                                continue;
                            }
                        }
                        addDefinitionAutoCompleteActionScript(definition, offsetNode, nextChar, null,
                                priorityNewClass, isParamOfTypeFunction, addImportData, project, result);
                    }
                }
            }
        }
        if (requiredPackageName == null || requiredPackageName.equals("")) {
            CompletionItem item = new CompletionItem();
            item.setKind(CompletionItemKind.Class);
            item.setLabel(IASKeywordConstants.VOID);
            result.getItems().add(item);
        }
    }

    private boolean isDuplicateTypeDefinition(IDefinition definition) {
        if (definition instanceof ITypeDefinition) {
            String qualifiedName = definition.getQualifiedName();
            return completionTypes.contains(qualifiedName);
        }
        return false;
    }
}