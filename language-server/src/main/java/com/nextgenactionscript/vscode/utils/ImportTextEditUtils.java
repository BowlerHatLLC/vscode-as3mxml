/*
Copyright 2016-2017 Bowler Hat LLC

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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.flex.compiler.projects.ICompilerProject;
import org.apache.flex.compiler.tree.as.IASNode;
import org.apache.flex.compiler.tree.as.IFunctionCallNode;
import org.apache.flex.compiler.tree.as.IIdentifierNode;
import org.apache.flex.compiler.tree.as.IImportNode;
import org.apache.flex.compiler.tree.as.IScopedNode;
import org.apache.flex.compiler.tree.as.IVariableNode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

public class ImportTextEditUtils
{
    private static final Pattern organizeImportPattern = Pattern.compile("(?m)^([ \\t]*)import ((\\w+\\.)+\\w+(\\.\\*)?);?");
    private static final Pattern importPattern = Pattern.compile("(?m)^([ \\t]*)import ([\\w\\.]+)");
    private static final Pattern indentPattern = Pattern.compile("(?m)^([ \\t]*)\\w");
    private static final Pattern packagePattern = Pattern.compile("(?m)^package( [\\w\\.]+)*\\s*\\{[\\r\\n]+([ \\t]*)");
    private static final String UNDERSCORE_UNDERSCORE_AS3_PACKAGE = "__AS3__.";

    protected static int organizeImportsFromStartIndex(String text, int startIndex, Set<IImportNode> importsToRemove, Set<String> importsToAdd, List<TextEdit> edits)
    {
        Matcher importMatcher = organizeImportPattern.matcher(text);
        if(startIndex != -1)
        {
            importMatcher.region(startIndex, text.length());
        }
        //use a Set to avoid adding duplicate names
        Set<String> nameSet = new HashSet<>();
        if (importsToAdd != null && startIndex == 0)
        {
            //add our extra imports at the first available opportunity
            nameSet.addAll(importsToAdd);
        }
        String indent = "";
        int startImportsIndex = -1;
        int endImportsIndex = -1;
        int endIndex = -1;
        while (importMatcher.find())
        {
            int matchIndex = importMatcher.start();
            if (startImportsIndex == -1)
            {
                startImportsIndex = matchIndex;
                int nextBlockOpenIndex = text.indexOf("{", startImportsIndex);
                int nextBlockCloseIndex = text.indexOf("}", startImportsIndex);
                endIndex = nextBlockOpenIndex;
                if(endIndex == -1 || (nextBlockCloseIndex != -1 && nextBlockCloseIndex < endIndex))
                {
                    endIndex = nextBlockCloseIndex;
                }
                indent = importMatcher.group(1);
            }
            if(endIndex != -1 && matchIndex >= endIndex)
            {
                break;
            }
            endImportsIndex = matchIndex + importMatcher.group(0).length();
            String importName = importMatcher.group(2);
            boolean removeImport = false;
            if (importsToRemove != null)
            {
                for (IImportNode importNode : importsToRemove)
                {
                    int importStart = importNode.getAbsoluteStart();
                    if (importStart >= matchIndex && importStart < endImportsIndex
                            && importNode.getImportName().equals(importName))
                    {
                        removeImport = true;
                        break;
                    }
                }
            }
            if (!removeImport)
            {
                nameSet.add(importName);
            }
        }
        if(nameSet.size() == 0)
        {
            //nothing to organize
            return endIndex;
        }
        //make the Set a List and put them in alphabetical order
        List<String> names = new ArrayList<>(nameSet);
        Collections.sort(names);
        StringBuilder result = new StringBuilder();
        String previousFirstPart = null;
        for(int i = 0, count = names.size(); i < count; i++)
        {
            String name = names.get(i);
            String[] parts = name.split("\\.");
            String firstPart = parts[0];
            if(previousFirstPart == null)
            {
                previousFirstPart = firstPart;
            }
            else if(parts.length > 1 && !firstPart.equals(previousFirstPart))
            {
                //add an extra line when the first part of the package name
                //is different than the previous import
                result.append("\n");
                previousFirstPart = firstPart;
            }
            if(i > 0)
            {
                result.append("\n");
            }
            result.append(indent);
            result.append("import ");
            result.append(name);
            result.append(";");
        }

        TextEdit edit = new TextEdit();
        edit.setNewText(result.toString());
        Position start = LanguageServerUtils.getPositionFromOffset(new StringReader(text), startImportsIndex);
        Position end = LanguageServerUtils.getPositionFromOffset(new StringReader(text), endImportsIndex);
        edit.setRange(new Range(start, end));
        edits.add(edit);
        return endIndex;
    }
    
    public static List<TextEdit> organizeImports(String text)
    {
        return organizeImports(text, null, null);
    }

    public static List<TextEdit> organizeImports(String text, Set<IImportNode> importsToRemove, Set<String> importsToAdd)
    {
        List<TextEdit> edits = new ArrayList<>();
        int index = 0;
        do
        {
            index = organizeImportsFromStartIndex(text, index, importsToRemove, importsToAdd, edits);
        }
        while(index != -1);
        return edits;
    }

	public static TextEdit createTextEditForImport(String qualifiedName, String text, int startIndex, int endIndex)
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
        Matcher importMatcher = importPattern.matcher(text);
        if(startIndex == -1)
        {
            startIndex = 0;
        }
        if (endIndex == -1)
        {
            endIndex = text.length();
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
			position = LanguageServerUtils.getPositionFromOffset(new StringReader(text), importIndex);
            position.setLine(position.getLine() + 1);
            position.setCharacter(0);
        }
        else //no existing imports
        {
            //start by looking for the package block
            Matcher packageMatcher = packagePattern.matcher(text);
            packageMatcher.region(startIndex, endIndex);
            if (packageMatcher.find()) //found the package
            {
                position = LanguageServerUtils.getPositionFromOffset(
                    new StringReader(text), packageMatcher.end());
                if(position.getCharacter() > 0)
                {
                    //go to the beginning of the line, if we're not there
                    position.setCharacter(0);
                }
                indent = packageMatcher.group(2);
            }
            else //couldn't find the start of a package or existing imports
            {
                position = LanguageServerUtils.getPositionFromOffset(new StringReader(text), startIndex);
                if (position.getCharacter() > 0)
                {
                    //go to the next line, if we're not at the start
                    position.setLine(position.getLine() + 1);
                    position.setCharacter(0);
                }
                //try to use the same indent as whatever follows
                Matcher indentMatcher = indentPattern.matcher(text);
                indentMatcher.region(startIndex, endIndex);
                if (indentMatcher.find())
                {
                    indent = indentMatcher.group(1);
                }
            }
            lineBreaks += "\n"; //add an extra line break
        }
        String textToInsert = indent + "import " + qualifiedName + ";" + lineBreaks;
        
        TextEdit edit = new TextEdit();
        edit.setNewText(textToInsert);
		edit.setRange(new Range(position, position));
		return edit;
	}
    
    public static TextEdit createTextEditForMXMLNamespace(String prefix, String uri, String text, int startIndex, int endIndex)
    {
        //exclude the whitespace before the namespace so that finding duplicates
        //doesn't depend on it
        String textToInsert = "xmlns:" + prefix + "=\"" + uri + "\"";
        //check if this namespace URI and prefix already exist
        int index = text.indexOf(textToInsert, startIndex);
        if(index != -1 && index < endIndex)
        {
            return null;
        }
        Position position = LanguageServerUtils.getPositionFromOffset(new StringReader(text), endIndex);
    
        TextEdit edit = new TextEdit();
        edit.setNewText(" " + textToInsert);
		edit.setRange(new Range(position, position));
		return edit;
    }
}