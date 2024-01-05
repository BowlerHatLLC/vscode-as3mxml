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
package com.as3mxml.vscode.providers;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IInterfaceDefinition;
import org.apache.royale.compiler.definitions.IPackageDefinition;
import org.apache.royale.compiler.definitions.ITypeDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition;
import org.apache.royale.compiler.definitions.IClassDefinition.ClassClassification;
import org.apache.royale.compiler.definitions.IFunctionDefinition.FunctionClassification;
import org.apache.royale.compiler.definitions.IInterfaceDefinition.InterfaceClassification;
import org.apache.royale.compiler.definitions.IVariableDefinition.VariableClassification;
import org.apache.royale.compiler.internal.scopes.ASProjectScope.DefinitionPromise;
import org.apache.royale.compiler.projects.ICompilerProject;
import org.apache.royale.compiler.scopes.IASScope;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.apache.royale.compiler.units.ICompilationUnit.UnitType;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolCapabilities;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.as3mxml.vscode.project.ActionScriptProjectData;
import com.as3mxml.vscode.project.ILspProject;
import com.as3mxml.vscode.utils.ActionScriptProjectManager;
import com.as3mxml.vscode.utils.DefinitionURI;

public class WorkspaceSymbolProvider {
	private static final Pattern FULLY_QUALIFIED_NAME_PATTERN = Pattern.compile("^(\\w+\\.)+\\w*$");
	private ActionScriptProjectManager actionScriptProjectManager;
	public SymbolCapabilities symbolCapabilities;

	public WorkspaceSymbolProvider(ActionScriptProjectManager actionScriptProjectManager) {
		this.actionScriptProjectManager = actionScriptProjectManager;
	}

	public Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>> workspaceSymbol(
			WorkspaceSymbolParams params, CancelChecker cancelToken) {
		if (cancelToken != null) {
			cancelToken.checkCanceled();
		}
		boolean allowResolveRange = false;
		if (symbolCapabilities != null) {
			try {
				allowResolveRange = symbolCapabilities.getResolveSupport().getProperties().contains("location.range");
			} catch (NullPointerException e) {
			}
		}
		Set<String> qualifiedNames = new HashSet<>();
		List<WorkspaceSymbol> result = new ArrayList<>();
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
		String fullyQualifiedQuery = null;
		if (FULLY_QUALIFIED_NAME_PATTERN.matcher(query).matches()) {
			fullyQualifiedQuery = query.toLowerCase();
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
							// we won't be able to detect what type of definition
							// this is without getting the actual definition from the
							// promise.
							DefinitionPromise promise = (DefinitionPromise) definition;
							definition = promise.getActualDefinition();
						}
						if (definition == null) {
							// one reason this could happen is a badly-formed
							// playerglobal.swc file
							continue;
						}
						if (definition.isImplicit()) {
							continue;
						}
						String qualifiedName = definition.getQualifiedName();
						boolean fullyQualifiedMatch = fullyQualifiedQuery != null
								&& qualifiedName.toLowerCase().startsWith(fullyQualifiedQuery);
						if (fullyQualifiedMatch || matchesQueries(queries, qualifiedName)) {
							if (qualifiedNames.contains(qualifiedName)) {
								// we've already added this symbol
								// this can happen when there are multiple root
								// folders in the workspace
								continue;
							}
							WorkspaceSymbol symbol = actionScriptProjectManager.definitionToWorkspaceSymbol(definition,
									project, allowResolveRange);
							if (symbol != null) {
								if (fullyQualifiedMatch) {
									symbol.setName(qualifiedName);
								}
								qualifiedNames.add(qualifiedName);
								result.add(symbol);
							}
						}
						if (definition instanceof ITypeDefinition) {
							ITypeDefinition typeDef = (ITypeDefinition) definition;
							IASScope typeScope = typeDef.getContainedScope();
							if (typeScope != null) {
								Collection<IDefinition> localDefs = new ArrayList<>(typeScope.getAllLocalDefinitions());
								for (IDefinition localDef : localDefs) {
									if (localDef.isOverride() || localDef.isPrivate()) {
										// skip overrides and private
										continue;
									}
									if (!matchesQueries(queries, localDef.getQualifiedName())) {
										continue;
									}
									WorkspaceSymbol localSymbol = actionScriptProjectManager
											.definitionToWorkspaceSymbol(localDef,
													project, allowResolveRange);
									if (localSymbol != null) {
										result.add(localSymbol);
									}
								}
							}
						}
					}
				} else if (UnitType.AS_UNIT.equals(unitType) || UnitType.MXML_UNIT.equals(unitType)) {
					IASScope[] scopes;
					try {
						scopes = unit.getFileScopeRequest().get().getScopes();
					} catch (Exception e) {
						return Either.forRight(Collections.emptyList());
					}
					for (IASScope scope : scopes) {
						querySymbolsInScope(queries, fullyQualifiedQuery, scope, allowResolveRange, qualifiedNames,
								project, result);
					}
				}
			}
		}
		if (cancelToken != null) {
			cancelToken.checkCanceled();
		}
		return Either.forRight(result);
	}

	public WorkspaceSymbol resolveWorkspaceSymbol(WorkspaceSymbol workspaceSymbol, CancelChecker cancelToken) {
		if (cancelToken != null) {
			cancelToken.checkCanceled();
		}
		if (!workspaceSymbol.getLocation().isRight()) {
			return workspaceSymbol;
		}
		URI uri = null;
		try {
			uri = URI.create(workspaceSymbol.getLocation().getRight().getUri());
		} catch (Exception e) {
			return workspaceSymbol;
		}
		String query = uri.getQuery();
		DefinitionURI decodedQuery = DefinitionURI.decode(query, actionScriptProjectManager);
		IDefinition definition = decodedQuery.definition;
		ICompilerProject project = decodedQuery.project;
		if (definition != null && project != null) {
			Location location = actionScriptProjectManager
					.definitionToLocation(definition, project);
			if (location != null) {
				workspaceSymbol.setLocation(Either.forLeft(location));
				return workspaceSymbol;
			}
		}
		if (cancelToken != null) {
			cancelToken.checkCanceled();
		}
		return workspaceSymbol;
	}

	private void querySymbolsInScope(List<String> queries, String fullyQualifiedQuery, IASScope scope,
			boolean allowResolveRange, Set<String> foundSymbols, ILspProject project,
			Collection<WorkspaceSymbol> result) {
		Collection<IDefinition> localDefs = new ArrayList<>(scope.getAllLocalDefinitions());
		for (IDefinition definition : localDefs) {
			if (definition.isImplicit()) {
				continue;
			}
			if (definition instanceof IPackageDefinition) {
				IPackageDefinition packageDefinition = (IPackageDefinition) definition;
				IASScope packageScope = packageDefinition.getContainedScope();
				querySymbolsInScope(queries, fullyQualifiedQuery, packageScope, allowResolveRange, foundSymbols,
						project,
						result);
			} else if (definition instanceof IClassDefinition) {
				IClassDefinition classDefinition = (IClassDefinition) definition;
				String qualifiedName = definition.getQualifiedName();
				boolean fullyQualifiedMatch = false;
				if (ClassClassification.PACKAGE_MEMBER.equals(classDefinition.getClassClassification())) {
					if (foundSymbols.contains(qualifiedName)) {
						// skip symbols that we've already encountered because
						// we don't want duplicates in the result
						continue;
					}
					foundSymbols.add(qualifiedName);
					fullyQualifiedMatch = fullyQualifiedQuery != null
							&& qualifiedName.toLowerCase().startsWith(fullyQualifiedQuery);
				}
				if (fullyQualifiedMatch || matchesQueries(queries, qualifiedName)) {
					WorkspaceSymbol symbol = actionScriptProjectManager.definitionToWorkspaceSymbol(classDefinition,
							project, allowResolveRange);
					if (symbol != null) {
						if (fullyQualifiedMatch) {
							symbol.setName(qualifiedName);
						}
						result.add(symbol);
					}
				}
				IASScope typeScope = classDefinition.getContainedScope();
				querySymbolsInScope(queries, fullyQualifiedQuery, typeScope, allowResolveRange, foundSymbols, project,
						result);
			} else if (definition instanceof IInterfaceDefinition) {
				IInterfaceDefinition interfaceDefinition = (IInterfaceDefinition) definition;
				String qualifiedName = definition.getQualifiedName();
				boolean fullyQualifiedMatch = false;
				if (InterfaceClassification.PACKAGE_MEMBER.equals(interfaceDefinition.getInterfaceClassification())) {
					if (foundSymbols.contains(qualifiedName)) {
						// skip symbols that we've already encountered because
						// we don't want duplicates in the result
						continue;
					}
					foundSymbols.add(qualifiedName);
					fullyQualifiedMatch = fullyQualifiedQuery != null
							&& qualifiedName.toLowerCase().startsWith(fullyQualifiedQuery);
				}
				if (fullyQualifiedMatch || matchesQueries(queries, qualifiedName)) {
					WorkspaceSymbol symbol = actionScriptProjectManager.definitionToWorkspaceSymbol(interfaceDefinition,
							project, allowResolveRange);
					if (symbol != null) {
						if (fullyQualifiedMatch) {
							symbol.setName(qualifiedName);
						}
						result.add(symbol);
					}
				}
				IASScope typeScope = interfaceDefinition.getContainedScope();
				querySymbolsInScope(queries, fullyQualifiedQuery, typeScope, allowResolveRange, foundSymbols, project,
						result);
			} else if (definition instanceof IFunctionDefinition) {
				IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
				String qualifiedName = definition.getQualifiedName();
				boolean fullyQualifiedMatch = false;
				if (FunctionClassification.PACKAGE_MEMBER.equals(functionDefinition.getFunctionClassification())) {
					if (foundSymbols.contains(qualifiedName)) {
						// skip symbols that we've already encountered because
						// we don't want duplicates in the result
						continue;
					}
					foundSymbols.add(qualifiedName);
					fullyQualifiedMatch = fullyQualifiedQuery != null
							&& qualifiedName.toLowerCase().startsWith(fullyQualifiedQuery);
				}
				if (!fullyQualifiedMatch && !matchesQueries(queries, qualifiedName)) {
					continue;
				}
				WorkspaceSymbol symbol = actionScriptProjectManager.definitionToWorkspaceSymbol(functionDefinition,
						project, allowResolveRange);
				if (symbol != null) {
					if (fullyQualifiedMatch) {
						symbol.setName(qualifiedName);
					}
					result.add(symbol);
				}
			} else if (definition instanceof IVariableDefinition) {
				IVariableDefinition variableDefinition = (IVariableDefinition) definition;
				String qualifiedName = definition.getQualifiedName();
				boolean fullyQualifiedMatch = false;
				if (VariableClassification.PACKAGE_MEMBER.equals(variableDefinition.getVariableClassification())) {
					if (foundSymbols.contains(qualifiedName)) {
						// skip symbols that we've already encountered because
						// we don't want duplicates in the result
						continue;
					}
					foundSymbols.add(qualifiedName);
					fullyQualifiedMatch = fullyQualifiedQuery != null
							&& qualifiedName.toLowerCase().startsWith(fullyQualifiedQuery);
				}
				if (!fullyQualifiedMatch && !matchesQueries(queries, qualifiedName)) {
					continue;
				}
				WorkspaceSymbol symbol = actionScriptProjectManager.definitionToWorkspaceSymbol(variableDefinition,
						project, allowResolveRange);
				if (symbol != null) {
					if (fullyQualifiedMatch) {
						symbol.setName(qualifiedName);
					}
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