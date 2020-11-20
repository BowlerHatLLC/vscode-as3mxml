/*
Copyright 2016-2020 Bowler Hat LLC

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
package com.as3mxml.vscode.providers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.as3mxml.vscode.project.ILspProject;
import com.as3mxml.vscode.project.ActionScriptProjectData;
import com.as3mxml.vscode.utils.ActionScriptProjectManager;

import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IPackageDefinition;
import org.apache.royale.compiler.definitions.ITypeDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition;
import org.apache.royale.compiler.internal.scopes.ASProjectScope.DefinitionPromise;
import org.apache.royale.compiler.scopes.IASScope;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.apache.royale.compiler.units.ICompilationUnit.UnitType;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class WorkspaceSymbolProvider {
	private ActionScriptProjectManager actionScriptProjectManager;

	public WorkspaceSymbolProvider(ActionScriptProjectManager actionScriptProjectManager) {
		this.actionScriptProjectManager = actionScriptProjectManager;
	}

	public List<? extends SymbolInformation> workspaceSymbol(WorkspaceSymbolParams params, CancelChecker cancelToken) {
		if (cancelToken != null) {
			cancelToken.checkCanceled();
		}
		Set<String> qualifiedNames = new HashSet<>();
		List<SymbolInformation> result = new ArrayList<>();
		String query = params.getQuery();
		StringBuilder currentQuery = new StringBuilder();
		List<String> queries = new ArrayList<>();
		for (int i = 0, length = query.length(); i < length; i++) {
			String charAtI = query.substring(i, i + 1);
			if (i > 0 && charAtI.toUpperCase().equals(charAtI)) {
				queries.add(currentQuery.toString().toLowerCase());
				currentQuery = new StringBuilder();
			}
			currentQuery.append(charAtI);
		}
		if (currentQuery.length() > 0) {
			queries.add(currentQuery.toString().toLowerCase());
		}
		for (ActionScriptProjectData projectData : actionScriptProjectManager.getAllProjectData()) {
			ILspProject project = projectData.project;
			if (project == null) {
				continue;
			}
			for (ICompilationUnit unit : project.getCompilationUnits()) {
				if (unit == null) {
					continue;
				}
				UnitType unitType = unit.getCompilationUnitType();
				if (UnitType.SWC_UNIT.equals(unitType)) {
					List<IDefinition> definitions = unit.getDefinitionPromises();
					for (IDefinition definition : definitions) {
						if (definition instanceof DefinitionPromise) {
							//we won't be able to detect what type of definition
							//this is without getting the actual definition from the
							//promise.
							DefinitionPromise promise = (DefinitionPromise) definition;
							definition = promise.getActualDefinition();
						}
						if (definition.isImplicit()) {
							continue;
						}
						if (!matchesQueries(queries, definition.getQualifiedName())) {
							continue;
						}
						String qualifiedName = definition.getQualifiedName();
						if (qualifiedNames.contains(qualifiedName)) {
							//we've already added this symbol
							//this can happen when there are multiple root
							//folders in the workspace
							continue;
						}
						SymbolInformation symbol = actionScriptProjectManager.definitionToSymbolInformation(definition,
								project);
						if (symbol != null) {
							qualifiedNames.add(qualifiedName);
							result.add(symbol);
						}
					}
				} else if (UnitType.AS_UNIT.equals(unitType) || UnitType.MXML_UNIT.equals(unitType)) {
					IASScope[] scopes;
					try {
						scopes = unit.getFileScopeRequest().get().getScopes();
					} catch (Exception e) {
						return Collections.emptyList();
					}
					for (IASScope scope : scopes) {
						querySymbolsInScope(queries, scope, qualifiedNames, project, result);
					}
				}
			}
		}
		if (cancelToken != null) {
			cancelToken.checkCanceled();
		}
		return result;
	}

	private void querySymbolsInScope(List<String> queries, IASScope scope, Set<String> foundTypes, ILspProject project,
			Collection<SymbolInformation> result) {
		Collection<IDefinition> definitions = scope.getAllLocalDefinitions();
		for (IDefinition definition : definitions) {
			if (definition instanceof IPackageDefinition) {
				IPackageDefinition packageDefinition = (IPackageDefinition) definition;
				IASScope packageScope = packageDefinition.getContainedScope();
				querySymbolsInScope(queries, packageScope, foundTypes, project, result);
			} else if (definition instanceof ITypeDefinition) {
				String qualifiedName = definition.getQualifiedName();
				if (foundTypes.contains(qualifiedName)) {
					//skip types that we've already encountered because we don't
					//want duplicates in the result
					continue;
				}
				foundTypes.add(qualifiedName);
				ITypeDefinition typeDefinition = (ITypeDefinition) definition;
				if (!definition.isImplicit() && matchesQueries(queries, qualifiedName)) {
					SymbolInformation symbol = actionScriptProjectManager.definitionToSymbolInformation(typeDefinition,
							project);
					if (symbol != null) {
						result.add(symbol);
					}
				}
				IASScope typeScope = typeDefinition.getContainedScope();
				querySymbolsInScope(queries, typeScope, foundTypes, project, result);
			} else if (definition instanceof IFunctionDefinition) {
				if (definition.isImplicit()) {
					continue;
				}
				if (!matchesQueries(queries, definition.getQualifiedName())) {
					continue;
				}
				IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
				SymbolInformation symbol = actionScriptProjectManager.definitionToSymbolInformation(functionDefinition,
						project);
				if (symbol != null) {
					result.add(symbol);
				}
			} else if (definition instanceof IVariableDefinition) {
				if (definition.isImplicit()) {
					continue;
				}
				if (!matchesQueries(queries, definition.getQualifiedName())) {
					continue;
				}
				IVariableDefinition variableDefinition = (IVariableDefinition) definition;
				SymbolInformation symbol = actionScriptProjectManager.definitionToSymbolInformation(variableDefinition,
						project);
				if (symbol != null) {
					result.add(symbol);
				}
			}
		}
	}

	private boolean matchesQueries(List<String> queries, String target) {
		String lowerCaseTarget = target.toLowerCase();
		int fromIndex = 0;
		for (String query : queries) {
			int index = lowerCaseTarget.indexOf(query, fromIndex);
			if (index == -1) {
				return false;
			}
			fromIndex = index + query.length();
		}
		return true;
	}
}