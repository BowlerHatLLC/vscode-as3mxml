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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

public class ImportTextEditUtils
{
    private static final Pattern importPattern = Pattern.compile("(?m)^([ \\t]*)import ([\\w\\.]+)");
    private static final Pattern indentPattern = Pattern.compile("(?m)^([ \\t]*)\\w");
    private static final Pattern packagePattern = Pattern.compile("(?m)^package( [\\w\\.]+)*\\s*\\{[\\r\\n]+([ \\t]*)");

	public static TextEdit createTextEditForImport(String qualifiedName, String text, int startIndex, int endIndex)
	{
        Matcher importMatcher = importPattern.matcher(text);
        if (startIndex != -1)
        {
            int endRegion = endIndex;
            if (endRegion == -1)
            {
                endRegion = text.length();
            }
            importMatcher.region(startIndex, endRegion);
        }
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
            if(startIndex != -1) //we have a specific range
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
                int endRegion = endIndex;
                if (endRegion == -1)
                {
                    endRegion = text.length();
                }
                indentMatcher.region(startIndex, endRegion);
                if (indentMatcher.find())
                {
                    indent = indentMatcher.group(1);
                }
            }
            else //no range specified, so find the package block, if possible
            {
                Matcher packageMatcher = packagePattern.matcher(text);
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
                    //just put it at the very beginning of the file
                    position = new Position(0, 0);
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
}