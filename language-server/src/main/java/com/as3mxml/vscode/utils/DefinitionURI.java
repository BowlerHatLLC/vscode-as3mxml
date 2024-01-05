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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IScopedDefinition;
import org.apache.royale.compiler.projects.ICompilerProject;
import org.apache.royale.compiler.scopes.IASScope;

import com.as3mxml.vscode.project.ActionScriptProjectData;
import com.as3mxml.vscode.project.ILspProject;

public class DefinitionURI {
	private static final String FILE_EXTENSION_AS = ".as";
	private static final String PATH_PREFIX_GENERATED = "generated/";
	private static final String PROTOCOL_SWC = "swc://";

	public String swcFilePath;
	public Collection<String> symbols;
	public ICompilerProject project;
	public IDefinition definition;
	public IDefinition rootDefinition;
	public boolean includeASDoc;

	public DefinitionURI() {

	}

	public static DefinitionURI decode(String encodedQuery,
			ActionScriptProjectManager actionScriptProjectManager) {
		DefinitionURI result = new DefinitionURI();
		byte[] bytes = Base64.getDecoder().decode(encodedQuery.getBytes());
		String query = new String(bytes, StandardCharsets.UTF_8);
		String[] parts = query.split(",");
		result.swcFilePath = parts[0];
		result.includeASDoc = "true".equals(parts[1]);
		List<String> symbols = new ArrayList<>();
		for (int i = 2; i < parts.length; i++) {
			symbols.add(parts[i]);
		}
		result.symbols = symbols;

		if (symbols.size() > 0) {
			List<ActionScriptProjectData> allProjectData = actionScriptProjectManager
					.getAllProjectDataForSWCFile(Paths.get(result.swcFilePath));
			if (allProjectData.size() > 0) {
				ActionScriptProjectData projectData = allProjectData.get(0);
				ILspProject project = projectData.project;
				if (project != null) {
					result.project = project;
					String currentSymbol = symbols.remove(0);
					IASScope currentScope = project.getScope();
					while (currentScope != null) {
						IASScope newScope = null;
						List<IDefinition> localDefs = new ArrayList<>(currentScope.getAllLocalDefinitions());
						for (IDefinition definition : localDefs) {
							if (currentSymbol.equals(definition.getQualifiedName())) {
								if (symbols.size() > 0) {
									if (definition instanceof IScopedDefinition) {
										IScopedDefinition scopedDefinition = (IScopedDefinition) definition;
										newScope = scopedDefinition.getContainedScope();
										break;
									}
								} else {
									result.definition = definition;
									return result;
								}
							}
						}
						if (newScope == null || symbols.size() == 0) {
							break;
						}
						currentScope = newScope;
						currentSymbol = symbols.remove(0);
					}
				}
			}
		}
		return null;
	}

	public String encode() {
		StringBuilder queryBuilder = new StringBuilder();
		queryBuilder.append(swcFilePath);
		queryBuilder.append(",");
		queryBuilder.append(includeASDoc);
		for (String symbol : symbols) {
			queryBuilder.append(",");
			queryBuilder.append(symbol);
		}
		byte[] bytes = queryBuilder.toString().getBytes(StandardCharsets.UTF_8);
		bytes = Base64.getEncoder().encode(bytes);
		String query = new String(bytes, StandardCharsets.UTF_8);
		try {
			query = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
		} catch (UnsupportedEncodingException e) {
			query = "";
		}
		String sourceFilePath = rootDefinition.getQualifiedName().replaceAll("\\.", "/") + FILE_EXTENSION_AS;
		// we add a fake directory as a prefix here because VSCode won't display
		// the file name if it isn't in a directory
		return PROTOCOL_SWC + PATH_PREFIX_GENERATED + sourceFilePath + "?"
				+ query;
	}
}
