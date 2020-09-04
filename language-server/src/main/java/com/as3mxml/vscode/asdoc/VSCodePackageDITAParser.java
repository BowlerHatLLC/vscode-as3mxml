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
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
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
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

/**
 * A custom implementation of IPackageDITAParser for the AS3 & MXML language server.
 */
public final class VSCodePackageDITAParser implements IPackageDITAParser {
	private IWorkspace workspace;

	public VSCodePackageDITAParser(IWorkspace workspace) {
		this.workspace = workspace;
	}

	public IDITAList parse(String swcFilePath, InputStream stream) {
		SAXReader xmlReader = new SAXReader();
		Document xmlDoc = null;
		try {
			xmlDoc = xmlReader.read(stream);
		} catch (DocumentException e) {
			return null;
		}

		final ArrayList<String> entryHrefs = new ArrayList<>();
		for (Element apiItemRefElement : xmlDoc.getRootElement().elements("apiItemRef")) {
			Attribute hrefAttribute = apiItemRefElement.attribute("href");
			if (hrefAttribute != null) {
				entryHrefs.add(hrefAttribute.getStringValue());
			}
		}

		return new IDITAList() {
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
				Element defElement = null;
				IDefinition parentDef = definition.getParent();
				if (parentDef instanceof IPackageDefinition || parentDef == null) {
					defElement = getDefinitionDITAFromPackageDITA(definition);
				} else if (parentDef instanceof ITypeDefinition) {
					defElement = getDefinitionDITAFromTypeDITA(definition);
				}
				if (defElement == null) {
					return null;
				}
				String defName = defElement.getName();
				Element apiDetailElement = defElement.element(defName + "Detail");
				if (apiDetailElement == null) {
					return null;
				}
				Element apiDescElement = apiDetailElement.element("apiDesc");
				if (apiDescElement == null) {
					return null;
				}
				String description = apiDescElement.asXML();
				StringBuilder builder = new StringBuilder();
				builder.append("/**");
				BufferedReader reader = new BufferedReader(new StringReader(description));
				String line = null;
				while ((line = reader.readLine()) != null) {
					builder.append("\n * ");
					builder.append(line);
				}
				if ("apiOperation".equals(defName) || "apiConstructor".equals(defName)) {
					Element apiDefElement = apiDetailElement.element(defName + "Def");
					if (apiDefElement != null) {
						for (Element apiParamElement : apiDefElement.elements("apiParam")) {
							Element apiItemNameElement = apiParamElement.element("apiItemName");
							builder.append("\n * @param ");
							if (apiItemNameElement == null) {
								builder.append("_");
							} else {
								builder.append(apiItemNameElement.getStringValue());
								Element paramApiDescElement = apiParamElement.element("apiDesc");
								if (paramApiDescElement != null) {
									String paramDescription = paramApiDescElement.getStringValue();
									paramDescription = paramDescription.replaceAll("\n", " ");
									builder.append(" ");
									builder.append(paramDescription);
								}
							}
						}
						Element apiReturnElement = apiDefElement.element("apiReturn");
						if (apiReturnElement != null) {
							Element returnApiDescElement = apiReturnElement.element("apiDesc");
							if (returnApiDescElement != null) {
								String returnDescription = returnApiDescElement.getStringValue();
								returnDescription = returnDescription.replaceAll("\n", " ");
								builder.append("\n * @return ");
								builder.append(returnDescription);
							}
						}
					}
				}
				builder.append("\n */");
				return new VSCodeASDocComment(builder.toString());
			}

			private Element getDefinitionDITAFromTypeDITA(IDefinition definition) {
				ITypeDefinition typeDef = (ITypeDefinition) definition.getParent();
				Element parentElement = getDefinitionDITAFromPackageDITA(typeDef);
				if (parentElement == null) {
					return null;
				}
				StringBuilder builder = new StringBuilder();
				if (typeDef.getPackageName().length() > 0) {
					builder.append(typeDef.getPackageName());
					builder.append(":");
				}
				builder.append(typeDef.getBaseName());
				builder.append(":");
				builder.append(definition.getBaseName());
				if (definition instanceof IAccessorDefinition) {
					builder.append(":get");
				}
				String elementName = null;
				if (definition instanceof IVariableDefinition) {
					elementName = "apiValue";
				} else if (definition instanceof IFunctionDefinition) {
					IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
					if (functionDefinition.isConstructor()) {
						elementName = "apiConstructor";
					} else {
						elementName = "apiOperation";
					}
				}
				String definitionID = builder.toString();

				for (Element childElement : parentElement.elements(elementName)) {
					Attribute idAttribute = childElement.attribute("id");
					if (idAttribute != null && idAttribute.getStringValue().equals(definitionID)) {
						return childElement;
					}
				}
				return null;

			}

			private Element getDefinitionDITAFromPackageDITA(IDefinition definition) {
				InputStream packageDITAStream = getPackageDITAStream(definition.getPackageName());
				if (packageDITAStream == null) {
					return null;
				}
				SAXReader xmlReader = new SAXReader();
				Document xmlDoc = null;
				try {
					xmlDoc = xmlReader.read(packageDITAStream);
				} catch (DocumentException e) {
					return null;
				}
				String elementName = null;
				StringBuilder builder = new StringBuilder();
				if (definition instanceof IVariableDefinition) {
					elementName = "apiValue";
					builder.append("globalValue:");
				} else if (definition instanceof IFunctionDefinition) {
					elementName = "apiOperation";
					builder.append("globalOperation:");
				} else if (definition instanceof ITypeDefinition) {
					elementName = "apiClassifier";
					if (definition.getPackageName().length() == 0) {
						builder.append("globalClassifier:");
					}
				}
				if (definition.getPackageName().length() > 0) {
					builder.append(definition.getPackageName());
					builder.append(":");
				}
				builder.append(definition.getBaseName());
				String definitionID = builder.toString();

				for (Element childElement : xmlDoc.getRootElement().elements(elementName)) {
					Attribute idAttribute = childElement.attribute("id");
					if (idAttribute != null && idAttribute.getStringValue().equals(definitionID)) {
						return childElement;
					}
				}
				return null;
			}

			private InputStream getPackageDITAStream(String packageName) {
				if (packageName == null || packageName.length() == 0) {
					packageName = "__Global__";
				}
				InputStream stream = null;
				String fileName = new File(swcFilePath).getName();
				if (fileName.endsWith(".swc")
						&& (fileName.contains("playerglobal") || fileName.contains("airglobal"))) {
					try {
						File jarPath = new File(VSCodePackageDITAParser.class.getProtectionDomain().getCodeSource()
								.getLocation().toURI());
						File docsFile = new File(jarPath.getParentFile().getParentFile(),
								"playerglobal_docs/" + packageName + ".xml");
						stream = new FileInputStream(docsFile);
					} catch (URISyntaxException e) {
						return null;
					} catch (FileNotFoundException e) {
						return null;
					}
				} else {
					String filePath = "docs/" + packageName + ".xml";
					ISWC swc = workspace.getSWCManager().get(new File(swcFilePath));
					if (swc == null) {
						return null;
					}
					try {
						ZipFile zipFile = new ZipFile(swc.getSWCFile());
						stream = SWCReader.getInputStream(zipFile, filePath);
					} catch (ZipException e) {
						return null;
					} catch (IOException e) {
						return null;
					}
				}
				return stream;
			}
		};
	}
}