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
package com.as3mxml.vscode.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.as3mxml.vscode.asdoc.IASDocTagConstants;
import com.as3mxml.vscode.asdoc.VSCodeASDocComment;
import com.as3mxml.vscode.asdoc.VSCodeASDocComment.VSCodeASDocTag;
import com.as3mxml.vscode.project.ILspProject;

import org.apache.royale.compiler.asdoc.IASDocTag;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.ITypeDefinition;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

public class ASDocUtils {
	private static final Pattern asdocDefinitionNamePattern = Pattern
			.compile("^(?:(\\w+\\.)*\\w+(#\\w+)?)|(?:#\\w+(?:\\(\\))?)$");

	public static IDefinition resolveDefinitionAtPosition(VSCodeASDocComment docComment,
			ICompilationUnit compilationUnit,
			Position position, ILspProject project, Range sourceRange) {

		docComment.compile(false);
		VSCodeASDocTag offsetTag = null;
		for (String tagName : docComment.getTags().keySet()) {
			if (!tagName.equals(IASDocTagConstants.SEE)
					&& !tagName.equals(IASDocTagConstants.COPY)
					&& !tagName.equals(IASDocTagConstants.THROWS)) {
				continue;
			}
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
		}

		if (offsetTag == null) {
			return null;
		}

		String description = offsetTag.getDescription();
		if (description == null) {
			return null;
		}

		int oldLength = description.length();
		// strip out leading whitespace
		description = description.replaceAll("^\\s+", "");
		// but keep track of how much we removed so that the range is
		// calculated correctly.
		int startOffset = description.length() - oldLength;
		int endIndex = description.indexOf(' ');
		if (endIndex == -1) {
			endIndex = description.indexOf('\t');
		}
		if (endIndex == -1) {
			endIndex = description.length();
		}
		String definitionName = description.substring(0, endIndex);
		Matcher matcher = asdocDefinitionNamePattern.matcher(definitionName);
		if (!matcher.matches()) {
			return null;
		}

		int line = offsetTag.getLine();
		int startChar = offsetTag.getColumn() + offsetTag.getName().length() + startOffset + 2;
		int endChar = startChar + definitionName.length();
		sourceRange.setStart(new Position(line, startChar));
		sourceRange.setEnd(new Position(line, endChar));
		if (!LSPUtils.rangeContains(sourceRange, position)) {
			return null;
		}

		String typeName = null;
		String memberName = null;
		if (!definitionName.contains("#")) {
			typeName = definitionName;
		} else {
			if (definitionName.startsWith("#")) {
				memberName = definitionName.substring(1);
				typeName = CompilationUnitUtils.getPrimaryQualifiedName(compilationUnit);
			} else {
				String[] definitionNameParts = definitionName.split("#");
				if (definitionNameParts.length == 2) {
					typeName = definitionNameParts[0];
					memberName = definitionNameParts[1];
				}
			}
		}
		IDefinition typeNameDefinition = null;
		if (typeName != null) {
			typeNameDefinition = DefinitionUtils.getDefinitionByName(typeName,
					project.getCompilationUnits());
			if (typeNameDefinition == null && typeName.indexOf('.') == -1) {
				String localTypeName = CompilationUnitUtils.getPrimaryQualifiedName(compilationUnit);
				if (localTypeName != null) {
					int endOfPackage = localTypeName.lastIndexOf('.');
					if (endOfPackage != -1) {
						// if the original type name doesn't include a package
						// try to find it in the same package as the current file
						typeName = localTypeName.substring(0, endOfPackage + 1) + typeName;
						typeNameDefinition = DefinitionUtils.getDefinitionByName(typeName,
								project.getCompilationUnits());
					}
				}
			}
		}
		if (typeNameDefinition != null) {
			if (memberName != null) {
				boolean mustBeFunction = false;
				if (memberName.endsWith("()")) {
					memberName = memberName.substring(0, memberName.length() - 2);
					mustBeFunction = true;
				}
				if (typeNameDefinition instanceof ITypeDefinition) {
					ITypeDefinition typeDefinition = (ITypeDefinition) typeNameDefinition;
					Collection<IDefinition> localDefs = new ArrayList<>(typeDefinition.getContainedScope()
							.getAllLocalDefinitions());
					for (IDefinition memberDefinition : localDefs) {
						if (mustBeFunction && !(memberDefinition instanceof IFunctionDefinition)) {
							continue;
						}
						if (memberName.equals(memberDefinition.getBaseName())) {
							return memberDefinition;
						}
					}
				}
			} else {
				return typeNameDefinition;
			}
		}

		return null;
	}
}
