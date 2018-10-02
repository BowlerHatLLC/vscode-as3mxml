/*
Copyright 2016-2018 Bowler Hat LLC

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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.as3mxml.vscode.commands.ICommandConstants;

import org.apache.royale.compiler.common.ASModifier;
import org.apache.royale.compiler.definitions.IAccessorDefinition;
import org.apache.royale.compiler.definitions.IConstantDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition.VariableClassification;
import org.apache.royale.compiler.projects.ICompilerProject;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IExpressionNode;
import org.apache.royale.compiler.tree.as.IFunctionNode;
import org.apache.royale.compiler.tree.as.IVariableNode;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class CodeActionsUtils
{
    private static final Pattern importPattern = Pattern.compile("(?m)^([ \\t]*)import ([\\w\\.]+)");
    private static final Pattern indentPattern = Pattern.compile("(?m)^([ \\t]*)\\w");
    private static final String UNDERSCORE_UNDERSCORE_AS3_PACKAGE = "__AS3__.";
    private static final Pattern packagePattern = Pattern.compile("(?m)^package(?: [\\w\\.]+)*\\s*\\{(?:[ \\t]*[\\r\\n]+)+([ \\t]*)");

    public static void findCodeActions(IASNode node, ICompilerProject project, Path path, String fileText, Range range, List<Either<Command, CodeAction>> codeActions)
    {
        if (node instanceof IVariableNode)
        {
            IVariableNode variableNode = (IVariableNode) node;
            IExpressionNode expressionNode = variableNode.getNameExpressionNode();
            IDefinition definition = expressionNode.resolve(project);
            if (definition instanceof IVariableDefinition
                && !(definition instanceof IConstantDefinition)
                && !(definition instanceof IAccessorDefinition))
            {
                //we want variables, but not constants or accessors
                IVariableDefinition variableDefinition = (IVariableDefinition) definition;
                if (variableDefinition.getVariableClassification().equals(VariableClassification.CLASS_MEMBER))
                {
                    createCommandsForGenerateGetterAndSetter(variableNode, path, fileText, range, codeActions);
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
            findCodeActions(child, project, path, fileText, range, codeActions);
        }
    }

    private static void createCommandsForGenerateGetterAndSetter(IVariableNode variableNode, Path path, String fileText, Range range, List<Either<Command, CodeAction>> codeActions)
    {
        Range variableRange = LanguageServerCompilerUtils.getRangeFromSourceLocation(variableNode);
        if(!LSPUtils.rangesIntersect(variableRange, range))
        {
            return;
        }
        IExpressionNode assignedValueNode = variableNode.getAssignedValueNode();
        String assignedValue = null;
        if (assignedValueNode != null)
        {
            int startIndex = assignedValueNode.getAbsoluteStart();
            int endIndex = assignedValueNode.getAbsoluteEnd();
            //if the variables value is assigned by [Embed] metadata, the
            //assigned value node won't be null, but its start/end will be -1!
            if (startIndex != -1
                    && endIndex != -1
                    //just to be safe
                    && startIndex < endIndex
                    //see BowlerHatLLC/vscode-nextgenas#234 for an example where
                    //the index values could be out of range!
                    && startIndex <= fileText.length()
                    && endIndex <= fileText.length())
            {
                assignedValue = fileText.substring(startIndex, endIndex);
            }
        }
        Command getAndSetCommand = new Command();
        getAndSetCommand.setTitle("Generate Getter and Setter");
        getAndSetCommand.setCommand(ICommandConstants.GENERATE_GETTER_AND_SETTER);
        getAndSetCommand.setArguments(Arrays.asList(
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
        CodeAction getAndSetCodeAction = new CodeAction();
        getAndSetCodeAction.setTitle(getAndSetCommand.getTitle());
        getAndSetCodeAction.setCommand(getAndSetCommand);
        getAndSetCodeAction.setKind(CodeActionKind.RefactorRewrite);
        codeActions.add(Either.forRight(getAndSetCodeAction));
        
        Command getterCommand = new Command();
        getterCommand.setTitle("Generate Getter");
        getterCommand.setCommand(ICommandConstants.GENERATE_GETTER);
        getterCommand.setArguments(Arrays.asList(
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
        CodeAction getterCodeAction = new CodeAction();
        getterCodeAction.setTitle(getterCommand.getTitle());
        getterCodeAction.setCommand(getterCommand);
        getterCodeAction.setKind(CodeActionKind.RefactorRewrite);
        codeActions.add(Either.forRight(getterCodeAction));

        Command setterCommand = new Command();
        setterCommand.setTitle("Generate Setter");
        setterCommand.setCommand(ICommandConstants.GENERATE_SETTER);
        setterCommand.setArguments(Arrays.asList(
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
        CodeAction setterCodeAction = new CodeAction();
        setterCodeAction.setTitle(setterCommand.getTitle());
        setterCodeAction.setCommand(setterCommand);
        setterCodeAction.setKind(CodeActionKind.RefactorRewrite);
        codeActions.add(Either.forRight(setterCodeAction));
    }

    private static boolean validatePackageNameForImport(IDefinition definition)
    {
        String packageName = definition.getPackageName();
        if (packageName == null
                || packageName.isEmpty()
                || packageName.startsWith(UNDERSCORE_UNDERSCORE_AS3_PACKAGE))
        {
            return false;
        }
        return true;
    }

    public static WorkspaceEdit createWorkspaceEditForAddImport(IDefinition definition, String fileText, String uri, int startIndex, int endIndex)
    {
        TextEdit textEdit = createTextEditForAddImport(definition, fileText, startIndex, endIndex);
        if (textEdit == null)
        {
            return null;
        }

        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        HashMap<String,List<TextEdit>> changes = new HashMap<>();
        List<TextEdit> edits = new ArrayList<>();
        edits.add(textEdit);
        changes.put(uri, edits);
        workspaceEdit.setChanges(changes);
        return workspaceEdit;
    }

    public static WorkspaceEdit createWorkspaceEditForAddImport(String qualfiedName, String fileText, String uri, int startIndex, int endIndex)
    {
        TextEdit textEdit = createTextEditForAddImport(qualfiedName, fileText, startIndex, endIndex);
        if (textEdit == null)
        {
            return null;
        }

        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        HashMap<String,List<TextEdit>> changes = new HashMap<>();
        List<TextEdit> edits = new ArrayList<>();
        edits.add(textEdit);
        changes.put(uri, edits);
        workspaceEdit.setChanges(changes);
        return workspaceEdit;
    }

    public static TextEdit createTextEditForAddImport(IDefinition definition, String fileText, int startIndex, int endIndex)
    {
        if(!validatePackageNameForImport(definition))
        {
            //don't even bother with these things that don't need importing
            return null;
        }
        String qualifiedName = definition.getQualifiedName();
        return createTextEditForAddImport(qualifiedName, fileText, startIndex, endIndex);
    }

    public static TextEdit createTextEditForAddImport(String qualifiedName, String fileText, int startIndex, int endIndex)
    {
        if(qualifiedName.lastIndexOf(".") == -1)
        {
            //if it's not in a package, it doesn't need to be imported
            return null;
        }
        if(qualifiedName.startsWith(UNDERSCORE_UNDERSCORE_AS3_PACKAGE))
        {
            //things in this package don't need to be imported
            return null;
        }
        Matcher importMatcher = importPattern.matcher(fileText);
        if(startIndex == -1)
        {
            startIndex = 0;
        }
        if (endIndex == -1)
        {
            endIndex = fileText.length();
        }
        importMatcher.region(startIndex, endIndex);
        String indent = "";
        int importIndex = -1;
        while (importMatcher.find())
        {
            if(importMatcher.group(2).equals(qualifiedName))
            {
                //this class is already imported!
                return null;
            }
            indent = importMatcher.group(1);
            importIndex = importMatcher.start();
        }
        Position position = null;
        String lineBreaks = "\n";
        if(importIndex != -1) //found existing imports
        {
			position = LanguageServerCompilerUtils.getPositionFromOffset(new StringReader(fileText), importIndex);
            position.setLine(position.getLine() + 1);
            position.setCharacter(0);
        }
        else //no existing imports
        {
            //start by looking for the package block
            Matcher packageMatcher = packagePattern.matcher(fileText);
            packageMatcher.region(startIndex, endIndex);
            if (packageMatcher.find()) //found the package
            {
                position = LanguageServerCompilerUtils.getPositionFromOffset(
                    new StringReader(fileText), packageMatcher.end());
                if(position.getCharacter() > 0)
                {
                    //go to the beginning of the line, if we're not there
                    position.setCharacter(0);
                }
                indent = packageMatcher.group(1);
            }
            else //couldn't find the start of a package or existing imports
            {
                position = LanguageServerCompilerUtils.getPositionFromOffset(new StringReader(fileText), startIndex);
                if (position.getCharacter() > 0)
                {
                    //go to the next line, if we're not at the start
                    position.setLine(position.getLine() + 1);
                    position.setCharacter(0);
                }
                //try to use the same indent as whatever follows
                Matcher indentMatcher = indentPattern.matcher(fileText);
                indentMatcher.region(startIndex, endIndex);
                if (indentMatcher.find())
                {
                    indent = indentMatcher.group(1);
                }
            }
            lineBreaks += "\n"; //add an extra line break
        }
        String textToInsert = indent + "import " + qualifiedName + ";" + lineBreaks;
        
        TextEdit textEdit = new TextEdit();
        textEdit.setNewText(textToInsert);
        textEdit.setRange(new Range(position, position));
        return textEdit;
    }
}