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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.royale.compiler.constants.IASLanguageConstants;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;

public class CodeGenerationUtils
{
	private static final String NEW_LINE = "\n";
	private static final String INDENT = "\t";
	private static final String SPACE = " ";

	public static WorkspaceEdit createGenerateLocalVariableWorkspaceEdit(
		String uri, int startLine, int startChar, int endLine, int endChar, String name)
    {
		StringBuilder builder = new StringBuilder();
		builder.append(NEW_LINE);
        builder.append(INDENT);
        builder.append(INDENT);
        builder.append(INDENT);
		builder.append("var ");
		builder.append(name);
		builder.append(":");
		builder.append(IASLanguageConstants.Object);
		builder.append(";");
        
        WorkspaceEdit workspaceEdit = new WorkspaceEdit();

        HashMap<String,List<TextEdit>> changes = new HashMap<>();
        workspaceEdit.setChanges(changes);

        List<TextEdit> edits = new ArrayList<>();
        changes.put(uri, edits);

        TextEdit edit = new TextEdit();
        edits.add(edit);

        edit.setNewText(builder.toString());
        Position editPosition = new Position(endLine, endChar);
		edit.setRange(new Range(editPosition, editPosition));
		
		return workspaceEdit;
	}

	public static WorkspaceEdit createGenerateFieldWorkspaceEdit(
		String uri, int startLine, int startChar, int endLine, int endChar,
		String name)
    {
        StringBuilder builder = new StringBuilder();
        builder.append(NEW_LINE);
        builder.append(INDENT);
        builder.append(INDENT);
        builder.append("public var ");
        builder.append(name);
        builder.append(":");
        builder.append(IASLanguageConstants.Object);
        builder.append(";");
        builder.append(NEW_LINE);

		WorkspaceEdit workspaceEdit = new WorkspaceEdit();

		HashMap<String,List<TextEdit>> changes = new HashMap<>();
        workspaceEdit.setChanges(changes);

        List<TextEdit> edits = new ArrayList<>();
        changes.put(uri, edits);

        TextEdit edit = new TextEdit();
        edits.add(edit);

        edit.setNewText(builder.toString());
        Position editPosition = new Position(endLine, endChar);
		edit.setRange(new Range(editPosition, editPosition));
		
		return workspaceEdit;
	}

	public static WorkspaceEdit createGenerateMethodWorkspaceEdit(
		String uri, int startLine, int startChar, int endLine, int endChar,
		String name, List<String> methodArgs, ImportRange importRange, String fileText)
    {
		WorkspaceEdit workspaceEdit = new WorkspaceEdit();

        HashMap<String,List<TextEdit>> changes = new HashMap<>();
        workspaceEdit.setChanges(changes);

        List<TextEdit> edits = new ArrayList<>();
        changes.put(uri, edits);

        TextEdit edit = new TextEdit();
        edits.add(edit);

        StringBuilder builder = new StringBuilder();
        builder.append(NEW_LINE);
        builder.append(INDENT);
        builder.append(INDENT);
        builder.append("private function ");
        builder.append(name);
        builder.append("(");
        if(methodArgs != null)
        {
            for (int i = 0, count = methodArgs.size(); i < count; i++)
            {
                if(i > 0)
                {
                    builder.append(", ");
                }
                String type = methodArgs.get(i);
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
        }
        builder.append(")");
        builder.append(":");
        builder.append(IASLanguageConstants.void_);
        builder.append(NEW_LINE);
        builder.append(INDENT);
        builder.append(INDENT);
        builder.append("{");
        builder.append(NEW_LINE);
        builder.append(INDENT);
        builder.append(INDENT);
        builder.append("}");
        builder.append(NEW_LINE);

        edit.setNewText(builder.toString());
        Position editPosition = new Position(endLine, endChar);
		edit.setRange(new Range(editPosition, editPosition));
		
		return workspaceEdit;
	}

	public static WorkspaceEdit createGenerateGetterAndSetterWorkspaceEdit(
		String uri, int startLine, int startChar, int endLine, int endChar,
		String name, String namespace, boolean isStatic, String type, String assignedValue,
		String fileText, boolean generateGetter, boolean generateSetter)
    {
		WorkspaceEdit workspaceEdit = new WorkspaceEdit();

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
			builder.append(NEW_LINE);
			builder.append(NEW_LINE);
			builder.append(INDENT);
			builder.append(INDENT);
			builder.append(namespace);
			builder.append(SPACE);
            if(isStatic)
            {
                builder.append("static ");
            }
            builder.append("function get " + name + "()");
            if(type != null && type.length() > 0)
            {
                builder.append(":" + type);
            }
            builder.append(NEW_LINE);
			builder.append(INDENT);
			builder.append(INDENT);
			builder.append("{");
			builder.append(NEW_LINE);
			builder.append(INDENT);
			builder.append(INDENT);
			builder.append(INDENT);
			builder.append("return _" + name +";");
			builder.append(NEW_LINE);
			builder.append(INDENT);
			builder.append(INDENT);
            builder.append("}");
        }
        if (generateSetter)
        {
			builder.append(NEW_LINE);
			builder.append(NEW_LINE);
			builder.append(INDENT);
			builder.append(INDENT);
			builder.append(namespace);
			builder.append(SPACE);
            if(isStatic)
            {
                builder.append("static ");
            }
            builder.append("function set " + name + "(value");
            if(type != null && type.length() > 0)
            {
                builder.append(":" + type);
            }
			builder.append("):void");
			builder.append(NEW_LINE);
			builder.append(INDENT);
			builder.append(INDENT);
			builder.append("{");
			builder.append(NEW_LINE);
			builder.append(INDENT);
			builder.append(INDENT);
			builder.append(INDENT);
			builder.append("_" + name + " = value;");
			builder.append(NEW_LINE);
			builder.append(INDENT);
			builder.append(INDENT);
            builder.append("}");
        }
        edit.setNewText(builder.toString());

        Position startPosition = new Position(startLine, startChar);
        Position endPosition = new Position(endLine, endChar);

        //we may need to adjust the end position to include the semi-colon
		int offset = LanguageServerCompilerUtils.getOffsetFromPosition(new StringReader(fileText), endPosition);
		if (offset < fileText.length() && fileText.charAt(offset) == ';')
		{
			endPosition.setCharacter(endChar + 1);
		}

		edit.setRange(new Range(startPosition, endPosition));

		return workspaceEdit;
	}
}