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
package com.nextgenactionscript.vscode.utils;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import com.nextgenactionscript.vscode.commands.ICommandConstants;

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
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Range;

public class CodeActionsUtils
{
    public static class CommandAndRange
    {
        public CommandAndRange(Command command, Range range)
        {
            this.command = command;
            this.range = range;
        }
        
        public Command command;
        public Range range;
    }
	
	public static void findCodeActions(IASNode node, ICompilerProject project, Path path, String fileText, List<CommandAndRange> codeActions)
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
                    createCommandsForGenerateGetterAndSetter(variableNode, path, fileText, codeActions);
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
            findCodeActions(child, project, path, fileText, codeActions);
        }
    }

    private static void createCommandsForGenerateGetterAndSetter(IVariableNode variableNode, Path path, String fileText, List<CommandAndRange> codeActions)
    {
        Range range = LanguageServerCompilerUtils.getRangeFromSourceLocation(variableNode);
        IExpressionNode assignedValueNode = variableNode.getAssignedValueNode();
        String assignedValue = null;
        if (assignedValueNode != null)
        {
            int startIndex = assignedValueNode.getAbsoluteStart();
            int endIndex = assignedValueNode.getAbsoluteEnd();
            //if the variables value is assigned by [Embed] metadata, the
            //assigned value node won't be null, but its start/end will be -1!
            if (startIndex != -1 && endIndex != -1)
            {
                assignedValue = fileText.substring(startIndex, endIndex);
            }
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
        CommandAndRange getterSetterCodeAction = new CommandAndRange(generateGetterAndSetterCommand, range);
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
        CommandAndRange getterCodeAction = new CommandAndRange(generateGetterCommand, range);
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
        CommandAndRange setterCodeAction = new CommandAndRange(generateSetterCommand, range);
        codeActions.add(setterCodeAction);
    }
}