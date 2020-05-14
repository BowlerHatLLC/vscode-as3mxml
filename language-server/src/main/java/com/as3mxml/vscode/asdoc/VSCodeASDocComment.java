/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.as3mxml.vscode.asdoc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.royale.compiler.asdoc.IASDocComment;
import org.apache.royale.compiler.asdoc.IASDocTag;

import antlr.Token;

public class VSCodeASDocComment implements IASDocComment
{
    public VSCodeASDocComment(Token t)
    {
        token = t.getText();
	}
	
	public VSCodeASDocComment(String t)
	{
		token = t;
	}

    private String token;
    private String description = null;
	private Map<String, List<IASDocTag>> tagMap = new HashMap<String, List<IASDocTag>>();
	private boolean insidePreformatted = false;
	private boolean usingMarkdown = false;
    
    @Override
    public String getDescription()
    {
        return description;
    }
	
	@Override
	public void compile()
	{
		compile(false);
	}

    public void compile(boolean useMarkdown)
    {
		usingMarkdown = useMarkdown;
		insidePreformatted = false;
        String[] lines = token.split("\n");
        StringBuilder sb = new StringBuilder();
        int n = lines.length;
        if (n == 1)
        {
			//strip end of asdoc comment
        	int c = lines[0].indexOf("*/");
			if (c != -1)
			{
				lines[0] = lines[0].substring(0, c);
			}
        }
        //strip start of asdoc coment
		String line = lines[0].substring(3);
		appendLine(sb, line, insidePreformatted);
        for (int i = 1; i < n - 1; i++)
        {
			line = lines[i];
            int star = line.indexOf("*");
            int at = line.indexOf("@");
            if (at == -1)
            {
	            if (star > -1) //line starts with a *
	            {
					appendLine(sb, line.substring(star + 1), insidePreformatted);
	            }
            }
            else //tag
            {	
            	int after = line.indexOf(" ", at + 1);
            	if (after == -1)
            	{
            		tagMap.put(line.substring(at + 1), null);
            	}
            	else
            	{
            		String tagName = line.substring(at + 1, after);
            		List<IASDocTag> tags = tagMap.get(tagName);
            		if (tags == null)
            		{
            			tags = new ArrayList<IASDocTag>();
            			tagMap.put(tagName, tags);
					}
					String tagDescription = reformatLine(line.substring(after + 1), false);
            		tags.add(new ASDocTag(tagName, tagDescription));
            	}            		
            }
        }
		description = sb.toString().trim();
    }

    @Override
    public boolean hasTag(String name)
    {
		if (tagMap == null)
		{
			return false;
		}
    	return (tagMap.containsKey(name));
    }

    @Override
    public IASDocTag getTag(String name)
    {
		if (tagMap == null)	
		{
			return null;
		}
    	List<IASDocTag> tags = tagMap.get(name);
		if (tags == null)
		{
			return null;
		}
        return tags.get(0);
    }

    @Override
    public Map<String, List<IASDocTag>> getTags()
    {
        return tagMap;
    }

    @Override
    public Collection<IASDocTag> getTagsByName(String string)
    {
        return tagMap.get(string);
    }

    @Override
    public void paste(IASDocComment source)
    {
	}
	
	private void appendLine(StringBuilder sb, String line, boolean addNewLine)
	{
		line = reformatLine(line);
		if(line.length() == 0)
		{
			return;
		}
		sb.append(line);
		if(addNewLine)
		{
			sb.append("\n");
		}
		else if(sb.charAt(sb.length() - 1) != '\n')
		{
			//if we don't currently end with a new line, add an extra
			//space before the next line is appended
			sb.append(" ");
		}
	}
	
	private String reformatLine(String line)
	{
		return reformatLine(line, usingMarkdown);
	}
	
	private String reformatLine(String line, boolean useMarkdown)
	{
		if(!insidePreformatted)
		{
			line = line.trim();
		}
		//remove all attributes (including namespaced)
		line = line.replaceAll("<(\\w+)(?:\\s+\\w+(?::\\w+)?=(\"|\')[^\"\']*\\2)*\\s*(\\/{0,1})>", "<$1$3>");
		int beforeLength = line.length();
		if(useMarkdown)
		{
			line = line.replaceAll("<(pre|listing|codeblock)>", "\n\n```\n");
		}
		else
		{
			line = line.replaceAll("<(pre|listing|codeblock)>", "\n\n");
		}
		if(line.length() < beforeLength)
		{
			insidePreformatted = true;
		}
		beforeLength = line.length();
		if(useMarkdown)
		{
			line = line.replaceAll("</(pre|listing|codeblock)>", "\n```\n");
		}
		else
		{
			line = line.replaceAll("</(pre|listing|codeblock)>", "");
		}
		if(line.length() < beforeLength)
		{
			insidePreformatted = false;
		}
		if(useMarkdown)
		{
			line = line.replaceAll("</?(em|i)>", "_");
			line = line.replaceAll("</?(strong|b)>", "**");
			line = line.replaceAll("</?(code|codeph)>", "`");
			line = line.replaceAll("<hr ?\\/>", "\n\n---\n\n");
		}
		line = line.replaceAll("<(p|ul|ol|dl|li|dt|table|tr|div|blockquote)>", "\n\n");
		
		//note: we allow <br/>, but not <br> because asdoc expects XHTML
		if(useMarkdown)
		{
			//to add a line break to markdown, there needs to be at least two
			//spaces at the end of the line
			line = line.replaceAll("<br ?\\/>\\s*", "  \n");
		}
		else
		{
			line = line.replaceAll("<br ?\\/>\\s*", "\n");
		}
		line = line.replaceAll("<\\/{0,1}\\w+\\/{0,1}>", "");
		return line;
	}

    class ASDocTag implements IASDocTag
    {
    	public ASDocTag(String name, String description)
    	{
    		this.name = name;
    		this.description = description;
    	}

    	private String name;
    	private String description;
    	
		@Override
		public String getName()
		{
			return name;
		}

		@Override
		public String getDescription()
		{
			return description;
		}

		@Override
		public boolean hasDescription()
		{
			return description != null;
		}
    	
    }
}
