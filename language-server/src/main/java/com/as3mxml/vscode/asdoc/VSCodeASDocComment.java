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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.royale.compiler.asdoc.IASDocComment;
import org.apache.royale.compiler.asdoc.IASDocTag;
import org.apache.royale.compiler.common.ISourceLocation;
import org.apache.royale.compiler.common.SourceLocation;

import antlr.Token;

public class VSCodeASDocComment extends SourceLocation implements IASDocComment {
	private static final Pattern beginPreformatPattern = Pattern.compile("(?i)(^\\s*)?<(pre|listing|codeblock)>");
	private static final Pattern endPreformatPattern = Pattern.compile("(?i)</(pre|listing|codeblock)>");
	private static final Pattern markdownInlineCodePattern = Pattern.compile("`(.*?)`");
	private static final Pattern asdocTagPattern = Pattern.compile("^\\s*\\*\\s+@\\w+");

	public VSCodeASDocComment(Token t) {
		super((ISourceLocation) t);
		token = t;
		tokenText = token.getText();
	}

	public VSCodeASDocComment(String t) {
		super();
		token = null;
		tokenText = t;
	}

	private Token token;
	private String tokenText;
	private String description = null;
	private Map<String, List<IASDocTag>> tagMap = new HashMap<String, List<IASDocTag>>();
	private boolean insidePreformatted = false;
	private String preformattedPrefix = null;
	private boolean usingMarkdown = false;

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		String text = tokenText;
		text = text.replaceAll("\n", "\\\\n");
		text = text.replaceAll("\r", "\\\\r");
		text = text.replaceAll("\t", "\\\\t");
		sb.append('|');
		sb.append(text);
		sb.append('|');
		sb.append(' ');
		sb.append(getLineColumnString());
		sb.append(getOffsetsString());
		sb.append(getSourcePathString());
		return sb.toString();
	}

	@Override
	public String getDescription() {
		return description;
	}

	public Token getToken() {
		return token;
	}

	public String getTokenText() {
		return tokenText;
	}

	@Override
	public void compile() {
		compile(false);
	}

	public void compile(boolean useMarkdown) {
		tagMap.clear();
		description = null;
		usingMarkdown = useMarkdown;
		insidePreformatted = false;
		preformattedPrefix = null;
		String[] lines = tokenText.split("\r?\n");
		StringBuilder sb = new StringBuilder();
		int n = lines.length;
		if (n == 1) {
			// strip end of asdoc comment
			int c = lines[0].indexOf("*/");
			if (c != -1) {
				lines[0] = lines[0].substring(0, c);
			}
		}
		// strip start of asdoc coment
		String line = lines[0];
		int lengthToRemove = Math.min(line.length(), 3);
		line = line.substring(lengthToRemove);
		VSCodeASDocTag lastTag = null;
		appendLine(sb, line, insidePreformatted);
		for (int i = 1; i < n - 1; i++) {
			line = lines[i];
			if (insidePreformatted || !asdocTagPattern.matcher(line).find()) {
				int star = line.indexOf("*");
				if (star != -1) // line starts with a *
				{
					if (lastTag != null) {
						StringBuilder tagDescriptionBuilder = new StringBuilder(lastTag.description);
						appendTagLine(tagDescriptionBuilder, line.substring(star + 1));
						lastTag.description = tagDescriptionBuilder.toString();
					} else {
						appendLine(sb, line.substring(star + 1), insidePreformatted);
					}
				}
			} else // tag
			{
				int at = line.indexOf('@');
				// look for nearest space or tab character
				int after = line.indexOf(' ', at + 1);
				int tabAfter = line.indexOf('\t', at + 1);
				if (tabAfter != -1 && tabAfter < after) {
					after = tabAfter;
				}
				if (after == -1) {
					after = line.length();
				}
				String tagName = line.substring(at + 1, after);
				List<IASDocTag> tags = tagMap.get(tagName);
				if (tags == null) {
					tags = new ArrayList<IASDocTag>();
					tagMap.put(tagName, tags);
				}
				StringBuilder tagDescriptionBuilder = new StringBuilder();
				if (after < line.length() - 1) {
					appendTagLine(tagDescriptionBuilder, line.substring(after + 1));
				}
				VSCodeASDocTag newTag = null;
				if (token instanceof ISourceLocation) {
					ISourceLocation tokenLocation = (ISourceLocation) token;
					newTag = new VSCodeASDocTag(tagName, tagDescriptionBuilder.toString(),
							tokenLocation.getSourcePath(),
							-1, -1,
							tokenLocation.getLine() + i, at, tokenLocation.getLine() + i,
							line.length());
				} else {
					newTag = new VSCodeASDocTag(tagName, tagDescriptionBuilder.toString());
				}
				tags.add(newTag);
				lastTag = newTag;
			}
		}
		description = sb.toString().trim();
		// don't allow more than two consecutive line breaks
		description = description.replaceAll("\\n{3,}", "\n\n");
	}

	@Override
	public boolean hasTag(String name) {
		if (tagMap == null) {
			return false;
		}
		return (tagMap.containsKey(name));
	}

	@Override
	public IASDocTag getTag(String name) {
		if (tagMap == null) {
			return null;
		}
		List<IASDocTag> tags = tagMap.get(name);
		if (tags == null) {
			return null;
		}
		return tags.get(0);
	}

	@Override
	public Map<String, List<IASDocTag>> getTags() {
		return tagMap;
	}

	@Override
	public Collection<IASDocTag> getTagsByName(String string) {
		return tagMap.get(string);
	}

	@Override
	public void paste(IASDocComment source) {
	}

	private void appendTagLine(StringBuilder sb, String line) {
		line = reformatLine(line);
		if (line.length() == 0) {
			return;
		}
		sb.append(line);
		if (sb.charAt(sb.length() - 1) != ' ') {
			// if we don't currently end with a space, add an extra
			// space before the next line is appended
			sb.append(" ");
		}
	}

	private void appendLine(StringBuilder sb, String line, boolean addNewLine) {
		line = reformatLine(line);
		if (line.length() == 0) {
			return;
		}
		sb.append(line);
		if (addNewLine) {
			sb.append("\n");
		} else if (sb.charAt(sb.length() - 1) != '\n') {
			// if we don't currently end with a new line, add an extra
			// space before the next line is appended
			sb.append(" ");
		}
	}

	private String reformatLine(String line) {
		return reformatLine(line, usingMarkdown);
	}

	private String reformatLine(String line, boolean useMarkdown) {
		// remove all attributes (including namespaced)
		line = line.replaceAll("<(\\w+)(?:\\s+\\w+(?::\\w+)?=(\"|\')[^\"\']*\\2)*\\s*(\\/{0,1})>", "<$1$3>");
		Matcher beginPreformatMatcher = beginPreformatPattern.matcher(line);
		boolean lineStartsWithPreformatted = insidePreformatted;
		if (beginPreformatMatcher.find()) {
			insidePreformatted = true;
			lineStartsWithPreformatted = beginPreformatMatcher.start() == 0;
			preformattedPrefix = beginPreformatMatcher.group(1);
			if (useMarkdown) {
				line = beginPreformatMatcher.replaceAll("\n\n```\n");
			} else {
				line = beginPreformatMatcher.replaceAll("\n\n");
			}
		}
		Matcher endPreformatMatcher = endPreformatPattern.matcher(line);
		if (endPreformatMatcher.find()) {
			insidePreformatted = false;
			if (useMarkdown) {
				line = endPreformatMatcher.replaceAll("\n```\n");
			} else {
				line = endPreformatMatcher.replaceAll("\n\n");
			}
		}
		if (lineStartsWithPreformatted) {
			if (preformattedPrefix != null && preformattedPrefix.length() > 0 && line.startsWith(preformattedPrefix)) {
				line = line.substring(preformattedPrefix.length());
			}
		} else {
			line = line.trim();
		}
		if (useMarkdown) {
			line = line.replaceAll("(?i)</?(em|i)>", "_");
			line = line.replaceAll("(?i)</?(strong|b)>", "**");
			line = line.replaceAll("(?i)</?(code|codeph)>", "`");
			line = line.replaceAll("(?i)<hr ?\\/>", "\n\n---\n\n");
		}
		line = line.replaceAll("(?i)<li>\\s*", "\n\n- ");
		line = line.replaceAll("(?i)<(p|ul|ol|dl|li|dt|table|tr|adobetable|row|div|blockquote)>\\s*", "\n\n");
		line = line.replaceAll("(?i)\\s*<\\/(p|ul|ol|dl|li|dt|table|tr|adobetable|row|div|blockquote)>", "\n\n");
		// ensure that there's at least one space between table cells
		line = line.replaceAll("(?i)<\\/(th|td|entry)><(th|td|entry)>", " ");

		// note: we allow <br/>, but not <br> because asdoc expects XHTML
		if (useMarkdown) {
			// to add a line break to markdown, there needs to be at least two
			// spaces at the end of the line
			line = line.replaceAll("(?i)<br ?\\/>\\s*", "  \n");
		} else {
			line = line.replaceAll("(?i)<br ?\\/>\\s*", "\n");
		}
		// remove all remaining tags
		line = line.replaceAll("<\\/{0,1}\\w+\\/{0,1}>", "");

		if (useMarkdown) {
			int startIndex = 0;
			while (true) {
				Matcher codeMatcher = markdownInlineCodePattern.matcher(line).region(startIndex, line.length());
				if (codeMatcher.find()) {
					startIndex = codeMatcher.end();
					String codeText = codeMatcher.group(1);
					codeText = codeText.replaceAll("(?i)&amp;", "&");
					codeText = codeText.replaceAll("(?i)&gt;", ">");
					codeText = codeText.replaceAll("(?i)&lt;", "<");
					codeText = codeText.replaceAll("(?i)&quot;", "\"");
					line = line.substring(0, codeMatcher.start()) + "`" + codeText + "`" + line.substring(startIndex);
					if (startIndex >= line.length()) {
						break;
					}
				} else {
					break;
				}
			}
		}
		if (!useMarkdown || lineStartsWithPreformatted) {
			line = line.replaceAll("(?i)&amp;", "&");
			line = line.replaceAll("(?i)&gt;", ">");
			line = line.replaceAll("(?i)&lt;", "<");
			line = line.replaceAll("(?i)&quot;", "\"");
		}
		return line;
	}

	public class VSCodeASDocTag extends SourceLocation implements IASDocTag {
		public VSCodeASDocTag(String name, String description) {
			this(name, description, null, -1, -1, -1, -1, -1, -1);
		}

		public VSCodeASDocTag(String name, String description, String sourcePath, int start, int end,
				int line, int column, int endLine, int endColumn) {
			super(sourcePath, start, end, line, column, endLine, endColumn);
			this.name = name;
			this.description = description;
		}

		private String name;
		private String description;

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getDescription() {
			return description;
		}

		@Override
		public boolean hasDescription() {
			return description != null;
		}

	}
}
