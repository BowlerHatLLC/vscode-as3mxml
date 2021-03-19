/*
Copyright 2016-2021 Bowler Hat LLC

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
package com.as3mxml.vscode.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.as3mxml.vscode.compiler.problems.DisabledConfigConditionBlockProblem;
import com.as3mxml.vscode.compiler.problems.UnusedImportProblem;

import org.apache.royale.compiler.constants.IASLanguageConstants;
import org.apache.royale.compiler.constants.IMetaAttributeConstants;
import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IClassDefinition.ClassClassification;
import org.apache.royale.compiler.definitions.IConstantDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition.FunctionClassification;
import org.apache.royale.compiler.definitions.IGetterDefinition;
import org.apache.royale.compiler.definitions.IInterfaceDefinition;
import org.apache.royale.compiler.definitions.IInterfaceDefinition.InterfaceClassification;
import org.apache.royale.compiler.definitions.ISetterDefinition;
import org.apache.royale.compiler.definitions.ITypeDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition.VariableClassification;
import org.apache.royale.compiler.definitions.metadata.IMetaTag;
import org.apache.royale.compiler.internal.tree.as.ConfigConditionBlockNode;
import org.apache.royale.compiler.internal.tree.as.FileNode;
import org.apache.royale.compiler.problems.ICompilerProblem;
import org.apache.royale.compiler.projects.ICompilerProject;
import org.apache.royale.compiler.tree.ASTNodeID;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IBinaryOperatorNode;
import org.apache.royale.compiler.tree.as.IBlockNode;
import org.apache.royale.compiler.tree.as.IClassNode;
import org.apache.royale.compiler.tree.as.IExpressionNode;
import org.apache.royale.compiler.tree.as.IFileNode;
import org.apache.royale.compiler.tree.as.IFunctionCallNode;
import org.apache.royale.compiler.tree.as.IFunctionNode;
import org.apache.royale.compiler.tree.as.IIdentifierNode;
import org.apache.royale.compiler.tree.as.IImportNode;
import org.apache.royale.compiler.tree.as.IInterfaceNode;
import org.apache.royale.compiler.tree.as.ILiteralContainerNode;
import org.apache.royale.compiler.tree.as.ILiteralNode;
import org.apache.royale.compiler.tree.as.IMemberAccessExpressionNode;
import org.apache.royale.compiler.tree.as.IPackageNode;
import org.apache.royale.compiler.tree.as.IScopedDefinitionNode;
import org.apache.royale.compiler.tree.as.IScopedNode;
import org.apache.royale.compiler.tree.as.ITransparentContainerNode;
import org.apache.royale.compiler.tree.as.IVariableNode;
import org.apache.royale.compiler.tree.mxml.IMXMLSpecifierNode;
import org.apache.royale.compiler.units.ICompilationUnit;

public class ASTUtils {
    private static final String DOT_STAR = ".*";
    private static final String UNDERSCORE_UNDERSCORE_AS3_PACKAGE = "__AS3__.";

    public static IASNode getCompilationUnitAST(ICompilationUnit unit) {
        IASNode ast = null;
        try {
            ast = unit.getSyntaxTreeRequest().get().getAST();
        } catch (InterruptedException e) {
            System.err.println("Interrupted while getting AST: " + unit.getAbsoluteFilename());
            return null;
        }
        if (ast == null) {
            //we couldn't find the root node for this file
            System.err.println("Could not find AST: " + unit.getAbsoluteFilename());
            return null;
        }
        if (ast instanceof FileNode) {
            FileNode fileNode = (FileNode) ast;
            //seems to work better than populateFunctionNodes() alone
            fileNode.parseRequiredFunctionBodies();
        }
        if (ast instanceof IFileNode) {
            try {
                IFileNode fileNode = (IFileNode) ast;
                //call this in addition to parseRequiredFunctionBodies() because
                //functions in included files won't be populated without it
                fileNode.populateFunctionNodes();
            } catch (NullPointerException e) {
                //sometimes, a null pointer exception can be thrown inside
                //FunctionNode.parseFunctionBody(). seems like a Royale bug.
            }
        }
        return ast;
    }

    public static boolean containsWithStart(IASNode node, int offset) {
        return offset >= node.getAbsoluteStart() && offset <= node.getAbsoluteEnd();
    }

    public static IASNode findFirstDescendantOfType(IASNode node, Class<? extends IASNode> classToFind) {
        for (int i = 0; i < node.getChildCount(); i++) {
            IASNode child = node.getChild(i);
            if (classToFind.isInstance(child)) {
                return child;
            }
            if (child.isTerminal()) {
                continue;
            }
            IASNode result = findFirstDescendantOfType(child, classToFind);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public static void findAllDescendantsOfType(IASNode node, Class<? extends IASNode> classToFind,
            List<IASNode> result) {
        for (int i = 0; i < node.getChildCount(); i++) {
            IASNode child = node.getChild(i);
            if (classToFind.isInstance(child)) {
                result.add(child);
            }
            if (child.isTerminal()) {
                continue;
            }
            findAllDescendantsOfType(child, classToFind, result);
        }
    }

    public static IASNode getContainingNodeIncludingStart(IASNode node, int offset) {
        if (!containsWithStart(node, offset)) {
            return null;
        }
        for (int i = 0, count = node.getChildCount(); i < count; i++) {
            IASNode child = node.getChild(i);
            if (child.getAbsoluteStart() == -1) {
                //the Royale compiler has a quirk where a node can have an
                //unknown offset, but its children have known offsets. this is
                //where we work around that...
                for (int j = 0, innerCount = child.getChildCount(); j < innerCount; j++) {
                    IASNode innerChild = child.getChild(j);
                    IASNode result = getContainingNodeIncludingStart(innerChild, offset);
                    if (result != null) {
                        return result;
                    }
                }
                continue;
            }
            IASNode result = getContainingNodeIncludingStart(child, offset);
            if (result != null) {
                return result;
            }
        }
        return node;
    }

    public static boolean needsImport(IASNode offsetNode, String qualifiedName) {
        int packageEndIndex = qualifiedName.lastIndexOf(".");
        if (packageEndIndex == -1) {
            //if it's not in a package, it doesn't need to be imported
            return false;
        }
        if (qualifiedName.startsWith(UNDERSCORE_UNDERSCORE_AS3_PACKAGE)) {
            //things in this package don't need to be imported
            return false;
        }
        IASNode node = offsetNode;
        while (node != null) {
            if (node instanceof IPackageNode) {
                String packageName = qualifiedName.substring(0, packageEndIndex);
                IPackageNode packageNode = (IPackageNode) node;
                if (packageName.equals(packageNode.getName())) {
                    //same package, so it doesn't need to be imported
                    return false;
                }
                //imports outside of the package node do not apply to a node
                //inside the package
                break;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                IASNode child = node.getChild(i);
                if (child instanceof IImportNode) {
                    IImportNode importNode = (IImportNode) child;
                    if (qualifiedName.equals(importNode.getImportName())) {
                        return false;
                    }
                }
            }
            node = node.getParent();
        }
        return true;
    }

    public static Set<String> findUnresolvedIdentifiersToImport(IASNode node, ICompilerProject project) {
        HashSet<String> importsToAdd = new HashSet<>();
        findUnresolvedIdentifiersToImport(node, project, importsToAdd);
        return importsToAdd;
    }

    public static List<IImportNode> findImportNodesToRemove(IASNode node, Set<String> requiredImports) {
        List<IImportNode> importsToRemove = new ArrayList<>();
        findImportNodesToRemove(node, requiredImports, importsToRemove);
        return importsToRemove;
    }

    public static void findUnusedImportProblems(IASNode ast, Set<String> requiredImports,
            List<ICompilerProblem> problems) {
        List<IImportNode> importsToRemove = findImportNodesToRemove(ast, requiredImports);
        for (IImportNode importNode : importsToRemove) {
            problems.add(new UnusedImportProblem(importNode));
        }
    }

    public static void findDisabledConfigConditionBlockProblems(IASNode ast, List<ICompilerProblem> problems) {
        List<ConfigConditionBlockNode> blocks = new ArrayList<>();
        findDisabledConfigConditionBlocks(ast, blocks);
        for (ConfigConditionBlockNode block : blocks) {
            problems.add(new DisabledConfigConditionBlockProblem(block));
        }
    }

    public static List<IDefinition> findDefinitionsThatMatchName(String nameToFind, boolean allowDuplicates,
            Collection<ICompilationUnit> compilationUnits) {

        ArrayList<IDefinition> result = new ArrayList<>();
        Set<String> qualifiedNames = allowDuplicates ? null : new HashSet<>();
        for (ICompilationUnit unit : compilationUnits) {
            if (unit == null) {
                continue;
            }
            try {
                Collection<IDefinition> definitions = unit.getFileScopeRequest().get()
                        .getExternallyVisibleDefinitions();
                if (definitions == null) {
                    continue;
                }
                for (IDefinition definition : definitions) {
                    if (definition.isImplicit()) {
                        continue;
                    }
                    if (definition instanceof IClassDefinition) {
                        IClassDefinition classDefinition = (IClassDefinition) definition;
                        if (!ClassClassification.PACKAGE_MEMBER
                                .equals(classDefinition.getClassClassification())) {
                            continue;
                        }
                    } else if (definition instanceof IInterfaceDefinition) {
                        IInterfaceDefinition interfaceDefinition = (IInterfaceDefinition) definition;
                        if (!InterfaceClassification.PACKAGE_MEMBER
                                .equals(interfaceDefinition.getInterfaceClassification())) {
                            continue;
                        }
                    } else if (definition instanceof IFunctionDefinition) {
                        IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
                        if (!FunctionClassification.PACKAGE_MEMBER
                                .equals(functionDefinition.getFunctionClassification())) {
                            continue;
                        }
                    } else if (definition instanceof IVariableDefinition) {
                        IVariableDefinition variableDefinition = (IVariableDefinition) definition;
                        if (!VariableClassification.PACKAGE_MEMBER
                                .equals(variableDefinition.getVariableClassification())) {
                            continue;
                        }
                    } else {
                        //unknown definition type
                        continue;
                    }
                    String baseName = definition.getBaseName();
                    if (!baseName.equals(nameToFind)) {
                        continue;
                    }
                    String qualifiedName = definition.getQualifiedName();
                    if (baseName.equals(qualifiedName)) {
                        //this definition is top-level. no import required.
                        continue;
                    }
                    if(!allowDuplicates) {
                        if(qualifiedNames.contains(qualifiedName)) {
                            continue;
                        }
                        qualifiedNames.add(qualifiedName);
                    }
                    result.add(definition);
                }
            } catch (Exception e) {
                //safe to ignore
            }
        }
        return result;
    }

    public static boolean isFunctionCallWithName(IFunctionCallNode functionCallNode, String functionName) {
        IExpressionNode nameNode = functionCallNode.getNameNode();
        if (nameNode instanceof IIdentifierNode) {
            IIdentifierNode nameIdentifierNode = (IIdentifierNode) nameNode;
            if (nameIdentifierNode.getName().equals(functionName)) {
                return true;
            }
        } else if (nameNode instanceof IMemberAccessExpressionNode) {
            IMemberAccessExpressionNode nameMemberAccess = (IMemberAccessExpressionNode) nameNode;
            IExpressionNode rightOperandNode = nameMemberAccess.getRightOperandNode();
            if (rightOperandNode instanceof IIdentifierNode) {
                IIdentifierNode rightIdentifierNode = (IIdentifierNode) rightOperandNode;
                if (rightIdentifierNode.getName().equals(functionName)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String findEventClassNameFromAddEventListenerFunctionCall(IFunctionCallNode functionCallNode,
            ICompilerProject project) {
        String eventType = null;
        IExpressionNode[] args = functionCallNode.getArgumentNodes();
        if (args.length < 1) {
            return null;
        }
        IExpressionNode eventTypeExpression = args[0];
        if (eventTypeExpression.getNodeID().equals(ASTNodeID.LiteralStringID)) {
            ILiteralNode literalNode = (ILiteralNode) eventTypeExpression;
            eventType = literalNode.getValue();
        } else {
            IDefinition resolvedDefinition = eventTypeExpression.resolve(project);
            if (resolvedDefinition instanceof IConstantDefinition) {
                IConstantDefinition constantDefinition = (IConstantDefinition) resolvedDefinition;
                Object initialValue = constantDefinition.resolveInitialValue(project);
                if (initialValue instanceof String) {
                    eventType = (String) initialValue;
                }
            }
        }

        if (eventType == null) {
            return null;
        }

        ITypeDefinition typeDefinition = null;
        IExpressionNode nameNode = functionCallNode.getNameNode();
        if (nameNode instanceof IIdentifierNode) {
            typeDefinition = ScopeUtils.getContainingTypeDefinitionForScope(functionCallNode.getContainingScope());
        } else if (nameNode instanceof IMemberAccessExpressionNode) {
            IMemberAccessExpressionNode memberAccessExpressionNode = (IMemberAccessExpressionNode) nameNode;
            IExpressionNode leftExpressionNode = memberAccessExpressionNode.getLeftOperandNode();
            typeDefinition = leftExpressionNode.resolveType(project);
        }
        if (typeDefinition == null) {
            return null;
        }

        String eventClassName = findEventClassFromTypeDefinitionMetadata(eventType, typeDefinition, project);
        if (eventClassName == null) {
            //lets use Object as the fallback if [Event] metadata is missing
            //we can't assume a specific class, like flash.events.Event
            //because Apache Royale or Starling objects might dispatch other
            //types of events.
            return IASLanguageConstants.Object;
        }
        return eventClassName;
    }

    public static String findEventClassFromTypeDefinitionMetadata(String eventName, ITypeDefinition typeDefinition,
            ICompilerProject project) {
        for (ITypeDefinition currentDefinition : typeDefinition.typeIteratable(project, false)) {
            IMetaTag[] eventMetaTags = currentDefinition.getMetaTagsByName(IMetaAttributeConstants.ATTRIBUTE_EVENT);
            for (IMetaTag metaTag : eventMetaTags) {
                String metaEventName = metaTag.getAttributeValue(IMetaAttributeConstants.NAME_EVENT_NAME);
                if (!eventName.equals(metaEventName)) {
                    continue;
                }
                String eventClassName = metaTag.getAttributeValue(IMetaAttributeConstants.NAME_EVENT_TYPE);
                if (eventClassName != null) {
                    return eventClassName;
                }
                break;
            }
        }
        return null;
    }

    public static void findIdentifiersForDefinition(IASNode node, IDefinition definition, ICompilerProject project,
            List<IIdentifierNode> result) {
        if (node.isTerminal()) {
            if (node instanceof IIdentifierNode) {
                IIdentifierNode identifierNode = (IIdentifierNode) node;
                IDefinition resolvedDefinition = identifierNode.resolve(project);
                if (resolvedDefinition == definition) {
                    result.add(identifierNode);
                } else if (resolvedDefinition instanceof IClassDefinition && definition instanceof IFunctionDefinition
                        && ((IFunctionDefinition) definition).isConstructor()) {
                    //if renaming the constructor, also rename the class
                    IClassDefinition classDefinition = (IClassDefinition) resolvedDefinition;
                    IFunctionDefinition constructorDefinition = classDefinition.getConstructor();
                    if (constructorDefinition != null && definition == constructorDefinition) {
                        result.add(identifierNode);
                    }
                } else if (resolvedDefinition instanceof IFunctionDefinition
                        && ((IFunctionDefinition) resolvedDefinition).isConstructor()
                        && definition instanceof IClassDefinition) {
                    //if renaming the class, also rename the constructor
                    IClassDefinition classDefinition = (IClassDefinition) definition;
                    IFunctionDefinition constructorDefinition = classDefinition.getConstructor();
                    if (constructorDefinition != null && resolvedDefinition == constructorDefinition) {
                        result.add(identifierNode);
                    }
                } else if (resolvedDefinition instanceof ISetterDefinition && definition instanceof IGetterDefinition) {
                    //if renaming the getter, also rename the setter
                    IGetterDefinition getterDefinition = (IGetterDefinition) definition;
                    ISetterDefinition setterDefinition = getterDefinition.resolveSetter(project);
                    if (setterDefinition != null && resolvedDefinition == setterDefinition) {
                        result.add(identifierNode);
                    }
                } else if (resolvedDefinition instanceof IGetterDefinition && definition instanceof ISetterDefinition) {
                    //if renaming the setter, also rename the getter
                    ISetterDefinition setterDefinition = (ISetterDefinition) definition;
                    IGetterDefinition getterDefinition = setterDefinition.resolveGetter(project);
                    if (getterDefinition != null && resolvedDefinition == getterDefinition) {
                        result.add(identifierNode);
                    }
                }
            }
            if (!(node instanceof ILiteralContainerNode)) {
                return;
            }
        }
        for (int i = 0, count = node.getChildCount(); i < count; i++) {
            IASNode childNode = node.getChild(i);
            findIdentifiersForDefinition(childNode, definition, project, result);
        }
    }

    protected static void findUnresolvedIdentifiersToImport(IASNode node, ICompilerProject project,
            Set<String> importsToAdd) {
        IASNode gp = node.getParent();
        for (int i = 0, count = node.getChildCount(); i < count; i++) {
            IASNode child = node.getChild(i);
            if (child instanceof IImportNode) {
                continue;
            }
            if (child instanceof IIdentifierNode) {
                IIdentifierNode identifierNode = (IIdentifierNode) child;
                String identifierName = identifierNode.getName();
                IDefinition definition = identifierNode.resolve(project);
                if (definition == null) {
                    if (node instanceof IFunctionCallNode) {
                        //new Identifier()
                        //x = Identifier(y)
                        IFunctionCallNode functionCallNode = (IFunctionCallNode) node;
                        if (functionCallNode.getNameNode().equals(identifierNode)) {
                            importsToAdd.add(identifierName);
                        }
                    } else if (node instanceof IVariableNode) {
                        //var x:Identifier
                        IVariableNode variableNode = (IVariableNode) node;
                        if (variableNode.getVariableTypeNode().equals(identifierNode)) {
                            importsToAdd.add(identifierName);
                        }
                    } else if (node instanceof IFunctionNode) {
                        //function():Identifier
                        IFunctionNode functionNode = (IFunctionNode) node;
                        if (functionNode.getReturnTypeNode().equals(identifierNode)) {
                            importsToAdd.add(identifierName);
                        }
                    } else if (node instanceof IClassNode) {
                        //class x extends Identifier
                        IClassNode classNode = (IClassNode) node;
                        if (classNode.getBaseClassExpressionNode().equals(identifierNode)) {
                            importsToAdd.add(identifierName);
                        }
                    } else if (node instanceof ITransparentContainerNode && gp instanceof IClassNode) {
                        //class x extends y implements Identifier
                        IClassNode classNode = (IClassNode) gp;
                        if (Arrays.asList(classNode.getImplementedInterfaceNodes()).contains(identifierNode)) {
                            importsToAdd.add(identifierName);
                        }
                    } else if (node instanceof ITransparentContainerNode && gp instanceof IInterfaceNode) {
                        //interface x extends Identifier
                        IInterfaceNode classNode = (IInterfaceNode) gp;
                        if (Arrays.asList(classNode.getExtendedInterfaceNodes()).contains(identifierNode)) {
                            importsToAdd.add(identifierName);
                        }
                    } else if (node instanceof IBinaryOperatorNode
                            && (node.getNodeID().equals(ASTNodeID.Op_IsID)
                                    || node.getNodeID().equals(ASTNodeID.Op_AsID))
                            && ((IBinaryOperatorNode) node).getRightOperandNode().equals(identifierNode)) {
                        //x is Identifier
                        //x as Identifier
                        importsToAdd.add(identifierName);
                    } else if (node instanceof IScopedNode && node instanceof IBlockNode) {
                        //{ Identifier; }
                        importsToAdd.add(identifierName);
                    }
                }
            }
            findUnresolvedIdentifiersToImport(child, project, importsToAdd);
        }
    }

    protected static void findImportNodesToRemove(IASNode node, Set<String> requiredImports,
            List<IImportNode> importsToRemove) {
        for (int i = 0, count = node.getChildCount(); i < count; i++) {
            IASNode child = node.getChild(i);
            if (child instanceof IImportNode) {
                IImportNode importNode = (IImportNode) child;
                String importName = importNode.getImportName();
                if (importName.endsWith(DOT_STAR)) {
                    String importPackage = importName.substring(0, importName.length() - 2);
                    if (containsReferenceForImportPackage(importPackage, requiredImports)) {
                        //this class is referenced by a wildcard import
                        continue;
                    }
                }
                if (!requiredImports.contains(importName)) {
                    importsToRemove.add(importNode);
                }
                //import nodes can't be references
                continue;
            }
            if (child.isTerminal()) {
                //terminal nodes don't have children, so don't bother checking
                continue;
            }
            findImportNodesToRemove(child, requiredImports, importsToRemove);
        }
    }

    private static boolean containsReferenceForImportPackage(String importPackage, Set<String> referencedDefinitions) {
        for (String reference : referencedDefinitions) {
            if (reference.startsWith(importPackage) && reference.indexOf('.', importPackage.length() + 1) == -1) {
                //an entire package is imported, so check if any
                //references are in that package
                return true;
            }
        }
        return false;
    }

    public static String getIndentBeforeNode(IASNode node, String fileText) {
        return getIndentFromOffsetAndColumn(node.getAbsoluteStart(), node.getColumn(), fileText);
    }

    public static String getIndentFromOffsetAndColumn(int offset, int column, String fileText) {
        int indentStart = offset - column;
        if (indentStart != -1 && column != -1) {
            int indentEnd = indentStart + column;
            if (indentEnd < fileText.length()) {
                return fileText.substring(indentStart, indentEnd);
            }
        }
        return "";
    }

    public static String nodeToContainingPackageName(IASNode node) {
        IASNode currentNode = node;
        String containingPackageName = null;
        while (currentNode != null && containingPackageName == null) {
            containingPackageName = currentNode.getPackageName();
            currentNode = currentNode.getParent();
        }
        return containingPackageName;
    }

    public static int getFunctionCallNodeArgumentIndex(IFunctionCallNode functionCallNode, IASNode offsetNode) {
        if (offsetNode == functionCallNode.getArgumentsNode() && offsetNode.getChildCount() == 0) {
            //there are no arguments yet
            return 0;
        }
        int indexToFind = offsetNode.getAbsoluteEnd();
        IExpressionNode[] argumentNodes = functionCallNode.getArgumentNodes();
        for (int i = argumentNodes.length - 1; i >= 0; i--) {
            IExpressionNode argumentNode = argumentNodes[i];
            if (indexToFind >= argumentNode.getAbsoluteStart()) {
                return i;
            }
        }
        return -1;
    }

    public static IFunctionCallNode getAncestorFunctionCallNode(IASNode offsetNode) {
        IASNode currentNode = offsetNode;
        do {
            if (currentNode instanceof IFunctionCallNode) {
                return (IFunctionCallNode) currentNode;
            }
            if (currentNode instanceof IScopedDefinitionNode) {
                return null;
            }
            currentNode = currentNode.getParent();
        } while (currentNode != null);
        return null;
    }

    public static String memberAccessToPackageName(IMemberAccessExpressionNode memberAccess) {
        String result = null;
        IExpressionNode rightOperand = memberAccess.getRightOperandNode();
        if (!(rightOperand instanceof IIdentifierNode)) {
            return null;
        }
        IExpressionNode leftOperand = memberAccess.getLeftOperandNode();
        if (leftOperand instanceof IMemberAccessExpressionNode) {
            result = memberAccessToPackageName((IMemberAccessExpressionNode) leftOperand);
        } else if (leftOperand instanceof IIdentifierNode) {
            IIdentifierNode identifierNode = (IIdentifierNode) leftOperand;
            result = identifierNode.getName();
        } else {
            return null;
        }
        IIdentifierNode identifierNode = (IIdentifierNode) rightOperand;
        return result + "." + identifierNode.getName();
    }

    private static boolean isInActionScriptComment(IASNode offsetNode, String code, int currentOffset,
            int minCommentStartIndex) {
        if (offsetNode != null && offsetNode.isTerminal()) {
            return false;
        }
        int startComment = code.lastIndexOf("/*", currentOffset - 1);
        if (startComment < minCommentStartIndex) {
            startComment = -1;
        }
        if (startComment != -1) {
            if (startComment > offsetNode.getAbsoluteStart()) {
                IASNode commentNode = getContainingNodeIncludingStart(offsetNode, startComment + 1);
                if (offsetNode.equals(commentNode)
                        && !isInSingleLineComment(code, startComment, minCommentStartIndex)) {
                    int endComment = code.indexOf("*/", startComment);
                    if (endComment == -1) {
                        endComment = code.length();
                    }
                    if (endComment < offsetNode.getAbsoluteEnd()) {
                        commentNode = getContainingNodeIncludingStart(offsetNode, endComment + 1);
                        if (offsetNode.equals(commentNode)
                                && !isInSingleLineComment(code, endComment, minCommentStartIndex)) {
                            //start and end are both the same node as the offset
                            //node, and neither is inside single line comments,
                            //so we're probably inside a multiline comment
                            return true;
                        }
                    }
                }
            }
        }
        return isInSingleLineComment(code, currentOffset, minCommentStartIndex);
    }

    private static boolean isInSingleLineComment(String code, int currentOffset, int minCommentStartIndex) {
        int startComment = -1;
        int startLine = code.lastIndexOf('\n', currentOffset - 1);
        if (startLine == -1) {
            //we're on the first line
            startLine = 0;
        }
        //we need to stop searching after the end of the current line
        int endLine = code.indexOf('\n', currentOffset);
        do {
            //we need to check this in a loop because it's possible for
            //the start of a single line comment to appear inside multiple
            //MXML attributes on the same line
            startComment = code.indexOf("//", startLine);
            if (startComment != -1 && currentOffset > startComment && startComment >= minCommentStartIndex) {
                return true;
            }
            startLine = startComment + 2;
        } while (startComment != -1 && startLine < endLine);
        return false;
    }

    public static boolean isInXMLComment(String code, int currentOffset) {
        int startComment = code.lastIndexOf("<!--", currentOffset - 1);
        if (startComment == -1) {
            return false;
        }
        int endComment = code.indexOf("-->", startComment);
        return endComment > currentOffset;
    }

    public static boolean isActionScriptCompletionAllowedInNode(IASNode offsetNode, String fileText,
            int currentOffset) {
        if (offsetNode != null) {
            if (offsetNode.getNodeID().equals(ASTNodeID.LiteralStringID)) {
                return false;
            }
            if (offsetNode.getNodeID().equals(ASTNodeID.LiteralRegexID)) {
                return false;
            }
        }
        int minCommentStartIndex = 0;
        IMXMLSpecifierNode mxmlNode = null;
        if (offsetNode instanceof IMXMLSpecifierNode) {
            mxmlNode = (IMXMLSpecifierNode) offsetNode;
        }
        if (mxmlNode == null) {
            mxmlNode = (IMXMLSpecifierNode) offsetNode.getAncestorOfType(IMXMLSpecifierNode.class);
        }
        if (mxmlNode != null) {
            //start in the current MXML node and ignore the start of comments
            //that appear in earlier MXML nodes
            minCommentStartIndex = mxmlNode.getAbsoluteStart();
        }

        return !ASTUtils.isInActionScriptComment(offsetNode, fileText, currentOffset, minCommentStartIndex);
    }

    public static IASNode getSelfOrAncestorOfType(IASNode node, Class<? extends IASNode> nodeType) {
        if (nodeType.isInstance(node)) {
            return node;
        }
        return node.getAncestorOfType(nodeType);
    }

    private static void findDisabledConfigConditionBlocks(IASNode node, List<ConfigConditionBlockNode> result) {
        for (int i = 0, count = node.getChildCount(); i < count; i++) {
            IASNode child = node.getChild(i);
            if (child instanceof ConfigConditionBlockNode) {
                ConfigConditionBlockNode configBlock = (ConfigConditionBlockNode) child;
                if (configBlock.getChildCount() == 0) {
                    result.add(configBlock);
                    continue;
                }
            }
            if (child.isTerminal()) {
                continue;
            }
            findDisabledConfigConditionBlocks(child, result);
        }
    }

    public static boolean nodeContainsNode(IASNode parent, IASNode child) {
        if (parent == null) {
            return false;
        }
        while (child != null) {
            child = child.getParent();
            if (parent.equals(child)) {
                return true;
            }
        }
        return false;
    }
}