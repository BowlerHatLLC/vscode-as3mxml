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
package com.as3mxml.vscode.utils;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.royale.compiler.common.ASModifier;
import org.apache.royale.compiler.constants.IASKeywordConstants;
import org.apache.royale.compiler.constants.IASLanguageConstants;
import org.apache.royale.compiler.definitions.IAccessorDefinition;
import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IConstantDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IGetterDefinition;
import org.apache.royale.compiler.definitions.IInterfaceDefinition;
import org.apache.royale.compiler.definitions.IParameterDefinition;
import org.apache.royale.compiler.definitions.ISetterDefinition;
import org.apache.royale.compiler.definitions.ITypeDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition.VariableClassification;
import org.apache.royale.compiler.projects.ICompilerProject;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IBlockNode;
import org.apache.royale.compiler.tree.as.IClassNode;
import org.apache.royale.compiler.tree.as.IContainerNode;
import org.apache.royale.compiler.tree.as.IExpressionNode;
import org.apache.royale.compiler.tree.as.IFunctionCallNode;
import org.apache.royale.compiler.tree.as.IFunctionNode;
import org.apache.royale.compiler.tree.as.IIdentifierNode;
import org.apache.royale.compiler.tree.as.IInterfaceNode;
import org.apache.royale.compiler.tree.as.ILanguageIdentifierNode;
import org.apache.royale.compiler.tree.as.IMemberAccessExpressionNode;
import org.apache.royale.compiler.tree.as.IScopedNode;
import org.apache.royale.compiler.tree.as.ITryNode;
import org.apache.royale.compiler.tree.as.IVariableNode;
import org.apache.royale.compiler.tree.as.IContainerNode.ContainerType;
import org.apache.royale.compiler.tree.mxml.IMXMLFileNode;
import org.apache.royale.compiler.tree.mxml.IMXMLScriptNode;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class CodeActionsUtils {
    private static final Pattern importPattern = Pattern.compile("(?m)^([ \\t]*)import ([\\w\\.]+)");
    private static final Pattern indentPattern = Pattern.compile("(?m)^([ \\t]*)\\w");
    private static final Pattern packagePattern = Pattern
            .compile("(?m)^package(?: [\\w\\.]+)*\\s*\\{(?:[ \\t]*[\\r\\n]+)+([ \\t]*)");
    private static final String NEW_LINE = "\n";
    private static final String INDENT = "\t";
    private static final String SPACE = " ";

    public static void findGetSetCodeActions(IASNode node, ICompilerProject project, String uri, String fileText,
            Range range, List<Either<Command, CodeAction>> codeActions) {
        if (node instanceof IInterfaceNode) {
            //no variables to turn into getters and setters in an interface
            return;
        }
        if (node instanceof IFunctionNode) {
            //no variables to turn into getters and setters in a function
            return;
        }
        if (node instanceof IVariableNode) {
            IVariableNode variableNode = (IVariableNode) node;
            Range variableRange = LanguageServerCompilerUtils.getRangeFromSourceLocation(variableNode);
            if (!LSPUtils.rangesIntersect(variableRange, range)) {
                //this one is outside of the range, so ignore it
                return;
            }
            IExpressionNode expressionNode = variableNode.getNameExpressionNode();
            IDefinition definition = expressionNode.resolve(project);
            if (definition instanceof IVariableDefinition && !(definition instanceof IConstantDefinition)
                    && !(definition instanceof IAccessorDefinition)) {
                //we want variables, but not constants or accessors
                IVariableDefinition variableDefinition = (IVariableDefinition) definition;
                if (VariableClassification.CLASS_MEMBER.equals(variableDefinition.getVariableClassification())) {
                    createCodeActionsForGenerateGetterAndSetter(variableNode, uri, fileText, range, codeActions);
                }
            }
            //no need to look at its children
            return;
        }
        for (int i = 0, childCount = node.getChildCount(); i < childCount; i++) {
            IASNode child = node.getChild(i);
            findGetSetCodeActions(child, project, uri, fileText, range, codeActions);
        }
    }

    private static void createCodeActionsForGenerateGetterAndSetter(IVariableNode variableNode, String uri,
            String fileText, Range codeActionsRange, List<Either<Command, CodeAction>> codeActions) {
        WorkspaceEdit getSetEdit = createWorkspaceEditForGenerateGetterAndSetter(variableNode, uri, fileText, true,
                true);
        CodeAction getAndSetCodeAction = new CodeAction();
        getAndSetCodeAction.setTitle("Generate 'get' and 'set' accessors");
        getAndSetCodeAction.setEdit(getSetEdit);
        getAndSetCodeAction.setKind(CodeActionKind.RefactorRewrite);
        codeActions.add(Either.forRight(getAndSetCodeAction));

        WorkspaceEdit getterEdit = createWorkspaceEditForGenerateGetterAndSetter(variableNode, uri, fileText, true,
                false);
        CodeAction getterCodeAction = new CodeAction();
        getterCodeAction.setTitle("Generate 'get' accessor (make read-only)");
        getterCodeAction.setEdit(getterEdit);
        getterCodeAction.setKind(CodeActionKind.RefactorRewrite);
        codeActions.add(Either.forRight(getterCodeAction));

        WorkspaceEdit setterEdit = createWorkspaceEditForGenerateGetterAndSetter(variableNode, uri, fileText, false,
                true);
        CodeAction setterCodeAction = new CodeAction();
        setterCodeAction.setTitle("Generate 'set' accessor (make write-only)");
        setterCodeAction.setEdit(setterEdit);
        setterCodeAction.setKind(CodeActionKind.RefactorRewrite);
        codeActions.add(Either.forRight(setterCodeAction));
    }

    public static WorkspaceEdit createWorkspaceEditForAddImport(IDefinition definition, String fileText, String uri,
            ImportRange importRange) {
        TextEdit textEdit = createTextEditForAddImport(definition.getQualifiedName(), fileText, importRange);
        if (textEdit == null) {
            return null;
        }

        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        HashMap<String, List<TextEdit>> changes = new HashMap<>();
        List<TextEdit> edits = new ArrayList<>();
        edits.add(textEdit);
        changes.put(uri, edits);
        workspaceEdit.setChanges(changes);
        return workspaceEdit;
    }

    public static WorkspaceEdit createWorkspaceEditForAddImport(String qualfiedName, String fileText, String uri,
            ImportRange importRange) {
        TextEdit textEdit = createTextEditForAddImport(qualfiedName, fileText, importRange);
        if (textEdit == null) {
            return null;
        }

        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        HashMap<String, List<TextEdit>> changes = new HashMap<>();
        List<TextEdit> edits = new ArrayList<>();
        edits.add(textEdit);
        changes.put(uri, edits);
        workspaceEdit.setChanges(changes);
        return workspaceEdit;
    }

    public static AddImportData findAddImportData(String fileText, ImportRange importRange) {
        int startIndex = importRange.startIndex;
        int endIndex = importRange.endIndex;
        if (startIndex == -1) {
            startIndex = 0;
        }
        int textLength = fileText.length();
        if (endIndex == -1 || endIndex > textLength) {
            //it's possible for the end index to be longer than the text
            //for example, if the package block is incomplete in an .as file
            endIndex = textLength;
        }
        String indent = "";
        String lineBreaks = "\n";
        int importIndex = -1;
        Matcher importMatcher = importPattern.matcher(fileText);
        importMatcher.region(startIndex, endIndex);
        while (importMatcher.find()) {
            indent = importMatcher.group(1);
            importIndex = importMatcher.start();
        }
        Position position = null;
        if (importIndex != -1) //found existing imports
        {
            position = LanguageServerCompilerUtils.getPositionFromOffset(new StringReader(fileText), importIndex);
            position.setLine(position.getLine() + 1);
            position.setCharacter(0);
        } else //no existing imports
        {
            if (importRange.needsMXMLScript) {
                position = LanguageServerCompilerUtils.getPositionFromOffset(new StringReader(fileText),
                        importRange.endIndex);
            } else {
                //start by looking for the package block
                Matcher packageMatcher = packagePattern.matcher(fileText);
                packageMatcher.region(startIndex, endIndex);
                if (packageMatcher.find()) //found the package
                {
                    position = LanguageServerCompilerUtils.getPositionFromOffset(new StringReader(fileText),
                            packageMatcher.end());
                    if (position.getCharacter() > 0) {
                        //go to the beginning of the line, if we're not there
                        position.setCharacter(0);
                    }
                    indent = packageMatcher.group(1);
                } else //couldn't find the start of a package or existing imports
                {
                    position = LanguageServerCompilerUtils.getPositionFromOffset(new StringReader(fileText),
                            startIndex);
                    if (position.getCharacter() > 0) {
                        //go to the next line, if we're not at the start
                        position.setLine(position.getLine() + 1);
                        position.setCharacter(0);
                    }
                    //try to use the same indent as whatever follows
                    Matcher indentMatcher = indentPattern.matcher(fileText);
                    indentMatcher.region(startIndex, endIndex);
                    if (indentMatcher.find()) {
                        indent = indentMatcher.group(1);
                    }
                }
            }
            lineBreaks += "\n"; //add an extra line break
        }
        return new AddImportData(position, indent, lineBreaks, importRange);
    }

    public static TextEdit createTextEditForAddImport(IDefinition definition, AddImportData addImportData) {
        return createTextEditForAddImport(definition.getQualifiedName(), addImportData);
    }

    public static TextEdit createTextEditForAddImport(String qualifiedName, AddImportData addImportData) {
        return createTextEditForAddImports(Collections.singletonList(qualifiedName), addImportData);
    }

    public static TextEdit createTextEditForAddImports(List<String> qualifiedNames, AddImportData addImportData) {
        if (qualifiedNames.size() == 0) {
            return null;
        }

        Position position = addImportData.position;
        String indent = addImportData.indent;
        String newLines = addImportData.newLines;

        StringBuilder builder = new StringBuilder();

        if (addImportData.importRange.needsMXMLScript) {
            builder.append("\t");
            builder.append("<");
            builder.append(addImportData.importRange.mxmlLanguageNS.prefix);
            builder.append(":");
            builder.append("Script");
            builder.append(">");
            builder.append("\n\t\t");
            builder.append("<![CDATA[");
            builder.append("\n");
        }

        for (int i = 0; i < qualifiedNames.size(); i++) {
            String qualifiedName = qualifiedNames.get(i);
            if (addImportData.importRange.needsMXMLScript) {
                builder.append("\t\t\t");
            } else {
                builder.append(indent);
            }
            builder.append(IASKeywordConstants.IMPORT);
            builder.append(" ");
            builder.append(qualifiedName);
            builder.append(";");
            if (i < (qualifiedNames.size() - 1)) {
                builder.append("\n");
            }
        }
        builder.append(newLines);

        if (addImportData.importRange.needsMXMLScript) {
            builder.append("\t\t");
            builder.append("]]>");
            builder.append("\n\t");
            builder.append("</");
            builder.append(addImportData.importRange.mxmlLanguageNS.prefix);
            builder.append(":");
            builder.append("Script");
            builder.append(">");
            builder.append("\n");
        }

        TextEdit textEdit = new TextEdit();
        textEdit.setNewText(builder.toString());
        textEdit.setRange(new Range(position, position));
        return textEdit;
    }

    public static TextEdit createTextEditForAddImport(IDefinition definition, String fileText,
            ImportRange importRange) {
        return createTextEditForAddImport(definition.getQualifiedName(), fileText, importRange);
    }

    public static TextEdit createTextEditForAddImport(String qualifiedName, String fileText, ImportRange importRange) {
        AddImportData addImportData = findAddImportData(fileText, importRange);
        return createTextEditForAddImport(qualifiedName, addImportData);
    }

    public static TextEdit createTextEditForAddImports(List<String> qualifiedNames, String fileText,
            ImportRange importRange) {
        AddImportData addImportData = findAddImportData(fileText, importRange);
        return createTextEditForAddImports(qualifiedNames, addImportData);
    }

    public static WorkspaceEdit createWorkspaceEditForAddMXMLNamespace(String nsPrefix, String nsURI, String fileText,
            String fileURI, int startIndex, int endIndex) {
        TextEdit textEdit = createTextEditForAddMXMLNamespace(nsPrefix, nsURI, fileText, startIndex, endIndex);
        if (textEdit == null) {
            return null;
        }

        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        HashMap<String, List<TextEdit>> changes = new HashMap<>();
        List<TextEdit> edits = new ArrayList<>();
        edits.add(textEdit);
        changes.put(fileURI, edits);
        workspaceEdit.setChanges(changes);
        return workspaceEdit;
    }

    public static TextEdit createTextEditForAddMXMLNamespace(String nsPrefix, String nsURI, Position position) {
        String textToInsert = " xmlns:" + nsPrefix + "=\"" + nsURI + "\"";

        TextEdit edit = new TextEdit();
        edit.setNewText(textToInsert);
        edit.setRange(new Range(position, position));
        return edit;
    }

    public static TextEdit createTextEditForAddMXMLNamespace(String nsPrefix, String nsURI, String text, int startIndex,
            int endIndex) {
        Position position = LanguageServerCompilerUtils.getPositionFromOffset(new StringReader(text), endIndex);
        return createTextEditForAddMXMLNamespace(nsPrefix, nsURI, position);
    }

    public static WorkspaceEdit createWorkspaceEditForGenerateLocalVariable(IIdentifierNode identifierNode, String uri,
            String text) {
        TextEdit textEdit = createTextEditForGenerateLocalVariable(identifierNode, text);
        if (textEdit == null) {
            return null;
        }

        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        HashMap<String, List<TextEdit>> changes = new HashMap<>();
        List<TextEdit> edits = new ArrayList<>();
        edits.add(textEdit);
        changes.put(uri, edits);
        workspaceEdit.setChanges(changes);
        return workspaceEdit;
    }

    public static TextEdit createTextEditForGenerateLocalVariable(IIdentifierNode identifierNode, String text) {
        IFunctionNode functionNode = (IFunctionNode) identifierNode.getAncestorOfType(IFunctionNode.class);
        if (functionNode == null) {
            return null;
        }
        IScopedNode scopedNode = functionNode.getScopedNode();
        if (scopedNode == null || scopedNode.getChildCount() == 0) {
            return null;
        }

        IASNode firstChild = scopedNode.getChild(0);

        String indent = ASTUtils.getIndentBeforeNode(firstChild, text);

        StringBuilder builder = new StringBuilder();
        builder.append(indent);
        builder.append(IASKeywordConstants.VAR);
        builder.append(" ");
        builder.append(identifierNode.getName());
        builder.append(":");
        builder.append(IASLanguageConstants.Object);
        builder.append(";");
        builder.append(NEW_LINE);

        TextEdit textEdit = new TextEdit();
        textEdit.setNewText(builder.toString());
        Position editPosition = new Position(scopedNode.getLine() + 1, 0);
        textEdit.setRange(new Range(editPosition, editPosition));
        return textEdit;
    }

    public static WorkspaceEdit createWorkspaceEditForGenerateFieldVariable(IIdentifierNode identifierNode, String uri,
            String text) {
        TextEdit textEdit = createTextEditForGenerateFieldVariable(identifierNode, text);
        if (textEdit == null) {
            return null;
        }

        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        HashMap<String, List<TextEdit>> changes = new HashMap<>();
        List<TextEdit> edits = new ArrayList<>();
        edits.add(textEdit);
        changes.put(uri, edits);
        workspaceEdit.setChanges(changes);
        return workspaceEdit;
    }

    public static TextEdit createTextEditForGenerateFieldVariable(IIdentifierNode identifierNode, String text) {
        LineAndIndent lineAndIndent = findLineAndIndent(identifierNode, text);
        if (lineAndIndent == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(NEW_LINE);
        builder.append(lineAndIndent.indent);
        builder.append(IASKeywordConstants.PUBLIC);
        builder.append(" ");
        builder.append(IASKeywordConstants.VAR);
        builder.append(" ");
        builder.append(identifierNode.getName());
        builder.append(":");
        builder.append(IASLanguageConstants.Object);
        builder.append(";");
        builder.append(NEW_LINE);

        TextEdit textEdit = new TextEdit();
        textEdit.setNewText(builder.toString());
        Position editPosition = new Position(lineAndIndent.line, 0);
        textEdit.setRange(new Range(editPosition, editPosition));
        return textEdit;
    }

    public static WorkspaceEdit createWorkspaceEditForImplementInterface(IClassNode classNode,
            IInterfaceDefinition interfaceDefinition, String uri, String text, ICompilerProject project) {
        List<TextEdit> textEdits = createTextEditsForImplementInterface(classNode, interfaceDefinition, text, project);
        if (textEdits == null || textEdits.size() == 0) {
            return null;
        }

        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        HashMap<String, List<TextEdit>> changes = new HashMap<>();
        changes.put(uri, textEdits);
        workspaceEdit.setChanges(changes);
        return workspaceEdit;
    }

    private static List<TextEdit> createTextEditsForImplementInterface(IClassNode classNode,
            IInterfaceDefinition interfaceDefinition, String text, ICompilerProject project) {
        List<TextEdit> interfaceEdits = new ArrayList<>();

        List<IDefinition> classMembers = new ArrayList<>();
        IClassDefinition classDefinition = classNode.getDefinition();
        classDefinition.classIterator(project, true).forEachRemaining(otherClassDef -> {
            classMembers.addAll(otherClassDef.getContainedScope().getAllLocalDefinitions());
        });

        List<IDefinition> interfaceMembers = new ArrayList<>();
        interfaceDefinition.interfaceIterator(project, true).forEachRemaining(otherInterfaceDef -> {
            interfaceMembers.addAll(otherInterfaceDef.getContainedScope().getAllLocalDefinitions());
        });

        List<String> collectedImports = new ArrayList<>();
        for (IDefinition localDef : interfaceMembers) {
            if (!(localDef instanceof IFunctionDefinition) || localDef.isImplicit()) {
                continue;
            }
            IFunctionDefinition functionDefinition = (IFunctionDefinition) localDef;
            boolean isGetter = functionDefinition instanceof IGetterDefinition;
            boolean isSetter = functionDefinition instanceof ISetterDefinition;
            boolean found = false;
            String localDefName = localDef.getBaseName();
            for (IDefinition classDef : classMembers) {
                if (classDef instanceof IFunctionDefinition && classDef.getBaseName().equals(localDefName)) {
                    boolean isAlsoGetter = classDef instanceof IGetterDefinition;
                    boolean isAlsoSetter = classDef instanceof ISetterDefinition;
                    if (isGetter == isAlsoGetter && isSetter == isAlsoSetter) {
                        found = true;
                        break;
                    }
                }
            }
            if (found) {
                continue;
            }
            TextEdit methodEdit = CodeActionsUtils.createTextEditForImplementMethod(classNode, functionDefinition, text,
                    project);
            if (methodEdit == null) {
                continue;
            }
            interfaceEdits.add(methodEdit);

            IParameterDefinition[] params = functionDefinition.getParameters();
            if (params.length > 0) {
                for (int i = 0; i < params.length; i++) {
                    IParameterDefinition param = params[i];
                    ITypeDefinition paramType = param.resolveType(project);
                    if (paramType != null) {
                        String paramTypeName = paramType.getQualifiedName();
                        if (ASTUtils.needsImport(classNode, paramTypeName)) {
                            collectedImports.add(paramTypeName);
                        }
                    }
                }
            }
            IDefinition returnType = functionDefinition.resolveReturnType(project);
            String typeName = IASLanguageConstants.Object;
            if (returnType != null && ASTUtils.needsImport(classNode, typeName)) {
                String returnTypeName = returnType.getQualifiedName();
                if (ASTUtils.needsImport(classNode, returnTypeName)) {
                    collectedImports.add(returnTypeName);
                }
            }
        }

        if (collectedImports.size() > 0) {
            ImportRange importRange = ImportRange.fromOffsetNode(classNode);
            TextEdit importEdit = CodeActionsUtils.createTextEditForAddImports(collectedImports, text, importRange);
            if (importEdit != null) {
                interfaceEdits.add(importEdit);
            }
        }
        return interfaceEdits;
    }

    private static TextEdit createTextEditForImplementMethod(IClassNode classNode,
            IFunctionDefinition functionDefinition, String text, ICompilerProject project) {
        int line = 0;
        String indent = "";
        IScopedNode scopedNode = classNode.getScopedNode();
        if (scopedNode == null) {
            return null;
        }
        line = scopedNode.getEndLine();

        if (scopedNode.getChildCount() > 0) {
            indent = ASTUtils.getIndentBeforeNode(scopedNode.getChild(scopedNode.getChildCount() - 1), text);
        }

        StringBuilder builder = new StringBuilder();
        builder.append(NEW_LINE);
        builder.append(indent);
        builder.append(IASKeywordConstants.PUBLIC);
        builder.append(" ");
        builder.append(IASKeywordConstants.FUNCTION);
        builder.append(" ");
        if (functionDefinition instanceof IGetterDefinition) {
            builder.append(IASKeywordConstants.GET);
            builder.append(" ");
        }
        if (functionDefinition instanceof ISetterDefinition) {
            builder.append(IASKeywordConstants.SET);
            builder.append(" ");
        }
        builder.append(DefinitionTextUtils.functionDefinitionToSignature(functionDefinition, project));
        builder.append(NEW_LINE);
        builder.append(indent);
        builder.append("{");
        builder.append(NEW_LINE);
        builder.append(indent);
        builder.append("\t");
        builder.append(IASKeywordConstants.THROW);
        builder.append(" ");
        builder.append(IASKeywordConstants.NEW);
        builder.append(" ");
        builder.append(IASLanguageConstants.Error);
        builder.append("(");
        builder.append("\"Method not implemented.\"");
        builder.append(")");
        builder.append(";");
        builder.append(NEW_LINE);
        builder.append(indent);
        builder.append("}");
        builder.append(NEW_LINE);

        TextEdit textEdit = new TextEdit();
        textEdit.setNewText(builder.toString());
        Position editPosition = new Position(line, 0);
        textEdit.setRange(new Range(editPosition, editPosition));

        return textEdit;
    }

    public static WorkspaceEdit createWorkspaceEditForGenerateMethod(IFunctionCallNode functionCallNode, String uri,
            String text, ICompilerProject project) {
        List<TextEdit> textEdits = createTextEditsForGenerateMethod(functionCallNode, text, project);
        if (textEdits == null || textEdits.size() == 0) {
            return null;
        }

        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        HashMap<String, List<TextEdit>> changes = new HashMap<>();
        changes.put(uri, textEdits);
        workspaceEdit.setChanges(changes);
        return workspaceEdit;
    }

    private static List<TextEdit> createTextEditsForGenerateMethod(IFunctionCallNode functionCallNode, String text,
            ICompilerProject project) {
        if (functionCallNode.isNewExpression()) {
            return null;
        }

        String functionName = functionCallNode.getFunctionName();
        if (functionName.length() == 0) {
            IExpressionNode nameNode = functionCallNode.getNameNode();
            if (nameNode instanceof IMemberAccessExpressionNode) {
                IMemberAccessExpressionNode memberAccessExpressionNode = (IMemberAccessExpressionNode) nameNode;
                IExpressionNode leftOperandNode = memberAccessExpressionNode.getLeftOperandNode();
                IExpressionNode rightOperandNode = memberAccessExpressionNode.getRightOperandNode();
                if (rightOperandNode instanceof IIdentifierNode && leftOperandNode instanceof ILanguageIdentifierNode) {
                    ILanguageIdentifierNode leftIdentifierNode = (ILanguageIdentifierNode) leftOperandNode;
                    if (leftIdentifierNode.getKind() == ILanguageIdentifierNode.LanguageIdentifierKind.THIS) {
                        IIdentifierNode identifierNode = (IIdentifierNode) rightOperandNode;
                        functionName = identifierNode.getName();
                    }
                }
            }
        }
        if (functionName.length() == 0) {
            return null;
        }

        LineAndIndent lineAndIndent = findLineAndIndent(functionCallNode, text);
        if (lineAndIndent == null) {
            return null;
        }

        List<TextEdit> edits = new ArrayList<>();
        TextEdit textEdit = new TextEdit();
        edits.add(textEdit);

        StringBuilder builder = new StringBuilder();
        builder.append(NEW_LINE);
        builder.append(lineAndIndent.indent);
        builder.append(IASKeywordConstants.PRIVATE);
        builder.append(" ");
        builder.append(IASKeywordConstants.FUNCTION);
        builder.append(" ");
        builder.append(functionName);
        builder.append("(");

        IExpressionNode[] args = functionCallNode.getArgumentNodes();
        if (args.length > 0) {
            ImportRange importRange = ImportRange.fromOffsetNode(functionCallNode);
            for (int i = 0; i < args.length; i++) {
                IExpressionNode arg = args[i];
                String typeName = IASLanguageConstants.Object;
                ITypeDefinition typeDefinition = arg.resolveType(project);
                if (typeDefinition != null) {
                    typeName = typeDefinition.getQualifiedName();
                }
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append("param");
                builder.append(i);
                builder.append(":");
                int index = typeName.lastIndexOf(".");
                if (index == -1) {
                    builder.append(typeName);
                } else {
                    builder.append(typeName.substring(index + 1));
                }
                if (ASTUtils.needsImport(functionCallNode, typeName)) {
                    TextEdit importEdit = CodeActionsUtils.createTextEditForAddImport(typeName, text, importRange);
                    if (importEdit != null) {
                        edits.add(importEdit);
                    }
                }
            }
        }
        builder.append(")");
        builder.append(":");
        builder.append(IASKeywordConstants.VOID);
        builder.append(NEW_LINE);
        builder.append(lineAndIndent.indent);
        builder.append("{");
        builder.append(NEW_LINE);
        builder.append(lineAndIndent.indent);
        builder.append("}");
        builder.append(NEW_LINE);

        textEdit.setNewText(builder.toString());
        Position editPosition = new Position(lineAndIndent.line, 0);
        textEdit.setRange(new Range(editPosition, editPosition));

        return edits;
    }

    public static WorkspaceEdit createWorkspaceEditForGenerateGetterAndSetter(IVariableNode variableNode, String uri,
            String text, boolean generateGetter, boolean generateSetter) {
        TextEdit textEdit = createTextEditForGenerateGetterAndSetter(variableNode, text, generateGetter,
                generateSetter);
        if (textEdit == null) {
            return null;
        }

        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        HashMap<String, List<TextEdit>> changes = new HashMap<>();
        List<TextEdit> edits = new ArrayList<>();
        edits.add(textEdit);
        changes.put(uri, edits);
        workspaceEdit.setChanges(changes);
        return workspaceEdit;
    }

    public static TextEdit createTextEditForGenerateGetterAndSetter(IVariableNode variableNode, String text,
            boolean generateGetter, boolean generateSetter) {
        String name = variableNode.getName();
        String namespace = variableNode.getNamespace();
        boolean isStatic = variableNode.hasModifier(ASModifier.STATIC);
        String type = variableNode.getVariableType();

        IExpressionNode assignedValueNode = variableNode.getAssignedValueNode();
        String assignedValue = null;
        if (assignedValueNode != null) {
            int startIndex = assignedValueNode.getAbsoluteStart();
            int endIndex = assignedValueNode.getAbsoluteEnd();
            //if the variables value is assigned by [Embed] metadata, the
            //assigned value node won't be null, but its start/end will be -1!
            if (startIndex != -1 && endIndex != -1
            //just to be safe
                    && startIndex < endIndex
                    //see BowlerHatLLC/vscode-as3mxml#234 for an example where
                    //the index values could be out of range!
                    && startIndex <= text.length() && endIndex <= text.length()) {
                assignedValue = text.substring(startIndex, endIndex);
            }
        }

        String indent = ASTUtils.getIndentBeforeNode(variableNode, text);

        StringBuilder builder = new StringBuilder();
        builder.append(IASKeywordConstants.PRIVATE);
        builder.append(" ");
        if (isStatic) {
            builder.append(IASKeywordConstants.STATIC);
            builder.append(" ");
        }
        builder.append(IASKeywordConstants.VAR);
        builder.append(" _");
        builder.append(name);
        if (type != null && type.length() > 0) {
            builder.append(":" + type);
        }
        if (assignedValue != null) {
            builder.append(" = " + assignedValue);
        }
        builder.append(";");
        if (generateGetter) {
            builder.append(NEW_LINE);
            builder.append(NEW_LINE);
            builder.append(indent);
            builder.append(namespace);
            builder.append(SPACE);
            if (isStatic) {
                builder.append(IASKeywordConstants.STATIC);
                builder.append(" ");
            }
            builder.append(IASKeywordConstants.FUNCTION);
            builder.append(" ");
            builder.append(IASKeywordConstants.GET);
            builder.append(" ");
            builder.append(name);
            builder.append("()");
            if (type != null && type.length() > 0) {
                builder.append(":" + type);
            }
            builder.append(NEW_LINE);
            builder.append(indent);
            builder.append("{");
            builder.append(NEW_LINE);
            builder.append(indent);
            builder.append(INDENT); //extra indent
            builder.append(IASKeywordConstants.RETURN);
            builder.append(" _");
            builder.append(name);
            builder.append(";");
            builder.append(NEW_LINE);
            builder.append(indent);
            builder.append("}");
        }
        if (generateSetter) {
            builder.append(NEW_LINE);
            builder.append(NEW_LINE);
            builder.append(indent);
            builder.append(namespace);
            builder.append(SPACE);
            if (isStatic) {
                builder.append(IASKeywordConstants.STATIC);
                builder.append(" ");
            }
            builder.append(IASKeywordConstants.FUNCTION);
            builder.append(" ");
            builder.append(IASKeywordConstants.SET);
            builder.append(" ");
            builder.append(name);
            builder.append("(value");
            if (type != null && type.length() > 0) {
                builder.append(":" + type);
            }
            builder.append("):");
            builder.append(IASKeywordConstants.VOID);
            builder.append(NEW_LINE);
            builder.append(indent);
            builder.append("{");
            builder.append(NEW_LINE);
            builder.append(indent);
            builder.append(INDENT); //extra indent
            builder.append("_" + name + " = value;");
            builder.append(NEW_LINE);
            builder.append(indent);
            builder.append("}");
        }

        TextEdit edit = new TextEdit();
        edit.setNewText(builder.toString());

        Range variableRange = LanguageServerCompilerUtils.getRangeFromSourceLocation(variableNode);
        int startLine = variableRange.getStart().getLine();
        int startChar = variableRange.getStart().getCharacter();
        int endLine = variableRange.getEnd().getLine();
        int endChar = variableRange.getEnd().getCharacter();

        Position startPosition = new Position(startLine, startChar);
        Position endPosition = new Position(endLine, endChar);

        //we may need to adjust the end position to include the semi-colon
        int offset = LanguageServerCompilerUtils.getOffsetFromPosition(new StringReader(text), endPosition);
        if (offset < text.length() && text.charAt(offset) == ';') {
            endPosition.setCharacter(endChar + 1);
        }

        edit.setRange(new Range(startPosition, endPosition));

        return edit;
    }

    public static WorkspaceEdit createWorkspaceEditForGenerateCatch(ITryNode tryNode, String uri, String text,
            ICompilerProject project) {
        TextEdit textEdit = createTextEditForGenerateCatch(tryNode, text, project);
        if (textEdit == null) {
            return null;
        }

        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        HashMap<String, List<TextEdit>> changes = new HashMap<>();
        List<TextEdit> edits = new ArrayList<>();
        edits.add(textEdit);
        changes.put(uri, edits);
        workspaceEdit.setChanges(changes);
        return workspaceEdit;
    }

    private static TextEdit createTextEditForGenerateCatch(ITryNode tryNode, String text, ICompilerProject project) {
        IASNode statementContentsNode = tryNode.getStatementContentsNode();
        if (statementContentsNode == null || !(statementContentsNode instanceof IBlockNode)
                || !(statementContentsNode instanceof IContainerNode)) {
            return null;
        }
        IContainerNode containerNode = (IContainerNode) statementContentsNode;
        if (containerNode.getContainerType().equals(ContainerType.SYNTHESIZED)) {
            //this should be fixed first
            return null;
        }
        if (tryNode.getCatchNodeCount() > 0) {
            return null;
        }

        TextEdit textEdit = new TextEdit();

        String indent = ASTUtils.getIndentBeforeNode(tryNode, text);

        StringBuilder builder = new StringBuilder();
        builder.append(NEW_LINE);
        builder.append(indent);
        builder.append(IASKeywordConstants.CATCH);
        builder.append("(");
        builder.append("e");
        builder.append(":");
        builder.append(IASLanguageConstants.Error);
        builder.append(")");
        builder.append(NEW_LINE);
        builder.append(indent);
        builder.append("{");
        builder.append(NEW_LINE);
        builder.append(indent);
        builder.append("}");

        textEdit.setNewText(builder.toString());

        int column = containerNode.getEndColumn();
        if (containerNode.getContainerType().equals(ContainerType.BRACES)) {
            column++;
        }

        Position editPosition = new Position(containerNode.getEndLine(), column);
        textEdit.setRange(new Range(editPosition, editPosition));

        return textEdit;
    }

    public static WorkspaceEdit createWorkspaceEditForGenerateEventListener(IASNode context, String functionName,
            String eventClassName, String uri, String text, ICompilerProject project) {
        List<TextEdit> textEdits = createTextEditsForGenerateEventListener(context, functionName, eventClassName, text,
                project);
        if (textEdits == null || textEdits.size() == 0) {
            return null;
        }

        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        HashMap<String, List<TextEdit>> changes = new HashMap<>();
        changes.put(uri, textEdits);
        workspaceEdit.setChanges(changes);
        return workspaceEdit;
    }

    public static WorkspaceEdit createWorkspaceEditForRemoveUnusedImport(String fileText, String uri, Range range) {
        TextEdit textEdit = createTextEditForRemoveUnusedImport(fileText, range);
        if (textEdit == null) {
            return null;
        }

        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        HashMap<String, List<TextEdit>> changes = new HashMap<>();
        List<TextEdit> edits = new ArrayList<>();
        edits.add(textEdit);
        changes.put(uri, edits);
        workspaceEdit.setChanges(changes);
        return workspaceEdit;
    }

    public static TextEdit createTextEditForRemoveUnusedImport(String text, Range range) {
        int startLine = range.getStart().getLine();
        int endLine = range.getEnd().getLine();
        int endChar = range.getEnd().getCharacter();

        Position startPosition = new Position(startLine, 0);
        Position endPosition = new Position(endLine, endChar);

        Range resultRange = new Range(startPosition, endPosition);

        //we may need to adjust the end position to include the semi-colon and new line
        int offset = LanguageServerCompilerUtils.getOffsetFromPosition(new StringReader(text), endPosition);
        if (offset < text.length() && text.charAt(offset) == ';') {
            endPosition.setCharacter(endPosition.getCharacter() + 1);
            offset++;
        }
        if (offset < text.length() && (text.charAt(offset) == '\r' || text.charAt(offset) == '\n')) {
            endPosition.setLine(endPosition.getLine() + 1);
            endPosition.setCharacter(0);
        }

        TextEdit textEdit = new TextEdit();
        textEdit.setNewText("");
        textEdit.setRange(resultRange);
        return textEdit;
    }

    private static List<TextEdit> createTextEditsForGenerateEventListener(IASNode context, String functionName,
            String eventClassName, String text, ICompilerProject project) {
        LineAndIndent lineAndIndent = findLineAndIndent(context, text);
        if (lineAndIndent == null) {
            return null;
        }

        List<TextEdit> edits = new ArrayList<>();
        TextEdit textEdit = new TextEdit();
        edits.add(textEdit);

        StringBuilder builder = new StringBuilder();
        builder.append(NEW_LINE);
        builder.append(lineAndIndent.indent);
        builder.append(IASKeywordConstants.PRIVATE);
        builder.append(" ");
        builder.append(IASKeywordConstants.FUNCTION);
        builder.append(" ");
        builder.append(functionName);
        builder.append("(");

        builder.append("event");
        builder.append(":");

        ImportRange importRange = ImportRange.fromOffsetNode(context);
        int index = eventClassName.lastIndexOf(".");
        if (index == -1) {
            builder.append(eventClassName);
        } else {
            builder.append(eventClassName.substring(index + 1));
        }
        if (ASTUtils.needsImport(context, eventClassName)) {
            TextEdit importEdit = CodeActionsUtils.createTextEditForAddImport(eventClassName, text, importRange);
            if (importEdit != null) {
                edits.add(importEdit);
            }
        }
        builder.append(")");
        builder.append(":");
        builder.append(IASKeywordConstants.VOID);
        builder.append(NEW_LINE);
        builder.append(lineAndIndent.indent);
        builder.append("{");
        builder.append(NEW_LINE);
        builder.append(lineAndIndent.indent);
        builder.append("}");
        builder.append(NEW_LINE);

        textEdit.setNewText(builder.toString());
        Position editPosition = new Position(lineAndIndent.line, 0);
        textEdit.setRange(new Range(editPosition, editPosition));

        return edits;
    }

    private static class LineAndIndent {
        public LineAndIndent(int line, String indent) {
            this.line = line;
            this.indent = indent;
        }

        public int line;
        public String indent;
    }

    private static LineAndIndent findLineAndIndent(IASNode context, String fileText) {
        String indent = "";
        int line = context.getLine();
        IMXMLScriptNode scriptNode = (IMXMLScriptNode) context.getAncestorOfType(IMXMLScriptNode.class);
        if (scriptNode == null) {
            IMXMLFileNode fileNode = (IMXMLFileNode) context.getAncestorOfType(IMXMLFileNode.class);
            if (fileNode != null) {
                scriptNode = (IMXMLScriptNode) ASTUtils.findFirstDescendantOfType(fileNode, IMXMLScriptNode.class);
            }
        }
        if (scriptNode != null) {
            IASNode[] nodes = scriptNode.getASNodes();
            int nodeCount = nodes.length;
            if (nodeCount == 0) {
                return null;
            }
            IASNode finalNode = nodes[nodeCount - 1];
            line = finalNode.getEndLine() + 1;

            indent = ASTUtils.getIndentBeforeNode(finalNode, fileText);
        } else {
            IClassNode classNode = (IClassNode) context.getAncestorOfType(IClassNode.class);
            if (classNode == null) {
                return null;
            }
            IScopedNode scopedNode = classNode.getScopedNode();
            if (scopedNode == null) {
                return null;
            }
            line = scopedNode.getEndLine();

            IFunctionNode functionNode = (IFunctionNode) context.getAncestorOfType(IFunctionNode.class);
            if (functionNode == null) {
                return null;
            }
            indent = ASTUtils.getIndentBeforeNode(functionNode, fileText);
        }
        return new LineAndIndent(line, indent);
    }
}