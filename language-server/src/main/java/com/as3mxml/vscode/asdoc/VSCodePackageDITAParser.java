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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.royale.compiler.asdoc.IASDocComment;
import org.apache.royale.compiler.asdoc.IPackageDITAParser;
import org.apache.royale.compiler.definitions.IAccessorDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IPackageDefinition;
import org.apache.royale.compiler.definitions.ITypeDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition;
import org.apache.royale.compiler.workspaces.IWorkspace;
import org.apache.royale.swc.ISWC;
import org.apache.royale.swc.dita.IDITAEntry;
import org.apache.royale.swc.dita.IDITAList;
import org.apache.royale.swc.io.SWCReader;

/**
 * A custom implementation of IPackageDITAParser for the AS3 & MXML language server.
 */
public final class VSCodePackageDITAParser implements IPackageDITAParser
{
	private static final Pattern ENTRY_PATTERN = Pattern.compile("<apiItemRef\\s+href=\"([\\w\\.]*?)\"\\s*/>");
	private static final Pattern DESC_PATTERN = Pattern.compile("<(apiDesc)[^>]*?>([\\s\\S]*?)<\\/\\1>");

	private IWorkspace workspace;

	public VSCodePackageDITAParser(IWorkspace workspace)
	{
		this.workspace = workspace;
	}

	public IDITAList parse(String swcFilePath, InputStream stream)
	{
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
		StringBuilder builder = new StringBuilder();
		try
		{
			String line = null;
			while((line = reader.readLine()) != null)
			{
				builder.append(line);
				builder.append("\n");
			}
		}
		catch(IOException e)
		{
			return null;
		}
		finally
		{
			try
			{
				reader.close();
			}
			catch(IOException e) {}
		}
		String contents = builder.toString();

		Matcher entryMatcher = ENTRY_PATTERN.matcher(contents);
		final ArrayList<String> entryHrefs = new ArrayList<>();
		while (entryMatcher.find())
		{
			String href = entryMatcher.group(1);
			entryHrefs.add(href);
		}
		return new IDITAList(){
			@Override
			public boolean hasEntries() {
				return entryHrefs.size() > 0;
			}
		
			@Override
			public IDITAEntry getEntry(String packageName) {
				return null;
			}
		
			@Override
			public List<IDITAEntry> getEntries() {
				return null;
			}
		
			@Override
			public IASDocComment getComment(IDefinition definition) throws Exception {
				String defDocs = null;
				IDefinition parentDef = definition.getParent();
				if(parentDef instanceof IPackageDefinition || parentDef == null)
				{
					defDocs = getDefinitionDITAFromPackageDITA(definition);
				}
				else if(parentDef instanceof ITypeDefinition)
				{
					defDocs = getDefinitionDITAFromTypeDITA(definition);
				}
				if (defDocs == null)
				{
					return null;
				}
				Matcher descMatcher = DESC_PATTERN.matcher(defDocs);
				if (!descMatcher.find())
				{
					return null;
				}
				String description = descMatcher.group(2);
				description = "/**\n * " + String.join("\n * ", description.split("\n")) + "\n */";
				return new VSCodeASDocComment(description);
			}

			private String getDefinitionDITAFromTypeDITA(IDefinition definition)
			{
				ITypeDefinition typeDef = (ITypeDefinition) definition.getParent();
				String typeDITA = getDefinitionDITAFromPackageDITA(typeDef);
				if(typeDITA == null)
				{
					return null;
				}
				StringBuilder builder = new StringBuilder();
				if (typeDef.getPackageName().length() > 0)
				{
					builder.append(typeDef.getPackageName().replace(".", "\\."));
					builder.append(":");
				}
				builder.append(typeDef.getBaseName());
				builder.append(":");
				builder.append(definition.getBaseName());
				if(definition instanceof IAccessorDefinition)
				{
					builder.append(":get");
				}
				String elementName = null;
				String elementNameDef = null;
				if (definition instanceof IVariableDefinition)
				{
					elementName = "apiValue";
					elementNameDef = "apiValueDef";
				}
				else if (definition instanceof IFunctionDefinition)
				{
					elementName = "apiOperation";
					elementNameDef = "apiOperationDef";
				}
				String definitionID = builder.toString();
				Pattern apiOperationPattern = Pattern.compile("<(" + elementName + ") [^>]*?id=\"" + definitionID + "\"[^>]*?>[\\s\\S]*?<\\/\\1>");
				Matcher matcher = apiOperationPattern.matcher(typeDITA);
				if(!matcher.find())
				{
					return null;
				}
				String result = matcher.group();
				result = result.replaceAll("<(" + elementNameDef + ")[^>]*?>[\\s\\S]*?<\\/\\1>", "");
				return result;
			}

			private String getDefinitionDITAFromPackageDITA(IDefinition definition)
			{
				String packageDITA = getPackageDITA(definition.getPackageName());
				if(packageDITA == null)
				{
					return null;
				}
				String elementName = null;
				String elementNameDef = null;
				StringBuilder builder = new StringBuilder();
				if (definition instanceof IVariableDefinition)
				{
					elementName = "apiValue";
					elementNameDef = "apiValueDef";
					builder.append("globalValue:");
				}
				else if (definition instanceof IFunctionDefinition)
				{
					elementName = "apiOperation";
					elementNameDef = "apiOperationDef";
					builder.append("globalOperation:");
				}
				else if (definition instanceof ITypeDefinition)
				{
					elementName = "apiClassifier";
					elementNameDef = "apiClassifierDef";
					if (definition.getPackageName().length() == 0)
					{
						builder.append("globalClassifier:");
					}
				}
				if (definition.getPackageName().length() > 0)
				{
					builder.append(definition.getPackageName().replace(".", "\\."));
					builder.append(":");
				}
				builder.append(definition.getBaseName());
				String definitionID = builder.toString();
				Pattern apiClassifierPattern = Pattern.compile("<(" + elementName + ") [^>]*?id=\"" + definitionID + "\"[^>]*?>[\\s\\S]*?<\\/\\1>");
				Matcher classifierMatcher = apiClassifierPattern.matcher(packageDITA);
				if(!classifierMatcher.find())
				{
					return null;
				}
				String result = classifierMatcher.group();
				result = result.replaceAll("<(" + elementNameDef + ")[^>]*?>[\\s\\S]*?<\\/\\1>", "");
				return result;
			}

			private String getPackageDITA(String packageName)
			{
				if(packageName == null || packageName.length() == 0)
				{
					packageName = "__Global__";
				}
				InputStream stream = null;
				String fileName = new File(swcFilePath).getName();
				if(fileName.endsWith(".swc") && (fileName.contains("playerglobal") || fileName.contains("airglobal")))
				{
					try
					{
						File jarPath = new File(VSCodePackageDITAParser.class.getProtectionDomain().getCodeSource().getLocation().toURI());
						File docsFile = new File(jarPath.getParentFile().getParentFile(), "playerglobal_docs/" + packageName + ".xml");
						stream = new FileInputStream(docsFile);
					}
					catch(URISyntaxException e)
					{
						return null;
					}
					catch(FileNotFoundException e)
					{
						return null;
					}
				}
				else
				{
					String filePath = "docs/" + packageName + ".xml";
					ISWC swc = workspace.getSWCManager().get(new File(swcFilePath));
					if (swc == null)
					{
						return null;
					}
					try
					{
						ZipFile zipFile = new ZipFile(swc.getSWCFile());
						stream = SWCReader.getInputStream(zipFile, filePath);
					}
					catch(ZipException e)
					{
						return null;
					}
					catch(IOException e)
					{
						return null;
					}
				}
				if(stream == null)
				{
					return null;
				}
				StringBuilder builder = new StringBuilder();
				BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
				try
				{
					String line = null;
					while((line = reader.readLine()) != null)
					{
						builder.append(line);
						builder.append("\n");
					}
				}
				catch(IOException e)
				{
					return null;
				}
				finally
				{
					try
					{
						reader.close();
					}
					catch(IOException e) {}
				}
				return builder.toString();
			}
		};
	}
}