/*
Copyright 2016-2025 Bowler Hat LLC

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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.antlr.runtime.ANTLRStringStream;
import org.apache.royale.compiler.common.ISourceLocation;
import org.apache.royale.compiler.common.XMLName;
import org.apache.royale.compiler.css.ICSSDocument;
import org.apache.royale.compiler.css.ICSSNamespaceDefinition;
import org.apache.royale.compiler.css.ICSSNode;
import org.apache.royale.compiler.css.ICSSProperty;
import org.apache.royale.compiler.css.ICSSRule;
import org.apache.royale.compiler.css.ICSSSelector;
import org.apache.royale.compiler.css.ICSSSelectorCondition;
import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IStyleDefinition;
import org.apache.royale.compiler.internal.css.CSSDocument;
import org.apache.royale.compiler.internal.mxml.MXMLData;
import org.apache.royale.compiler.internal.mxml.MXMLDialect;
import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IIdentifierNode;
import org.apache.royale.compiler.tree.mxml.IMXMLStyleNode;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TypeDefinitionParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.as3mxml.vscode.asdoc.VSCodeASDocComment;
import com.as3mxml.vscode.project.ActionScriptProjectData;
import com.as3mxml.vscode.utils.ActionScriptProjectManager;
import com.as3mxml.vscode.utils.CSSDocumentUtils;
import com.as3mxml.vscode.utils.CompilationUnitUtils.IncludeFileData;
import com.as3mxml.vscode.utils.DefinitionUtils;
import com.as3mxml.vscode.utils.FileTracker;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;
import com.as3mxml.vscode.utils.MXMLDataUtils;
import com.google.common.collect.ImmutableList;

public class TypeDefinitionProvider {
	private static final String FILE_EXTENSION_CSS = ".css";
	private static final String FILE_EXTENSION_MXML = ".mxml";

	private ActionScriptProjectManager actionScriptProjectManager;
	private FileTracker fileTracker;

	public TypeDefinitionProvider(ActionScriptProjectManager actionScriptProjectManager, FileTracker fileTracker) {
		this.actionScriptProjectManager = actionScriptProjectManager;
		this.fileTracker = fileTracker;
	}

	public Either<List<? extends Location>, List<? extends LocationLink>> typeDefinition(TypeDefinitionParams params,
			CancelChecker cancelToken) {
		if (cancelToken != null) {
			cancelToken.checkCanceled();
		}
		TextDocumentIdentifier textDocument = params.getTextDocument();
		Position position = params.getPosition();
		String uriString = textDocument.getUri();
		Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(uriString);
		if (path == null) {
			if (cancelToken != null) {
				cancelToken.checkCanceled();
			}
			return Either.forLeft(Collections.emptyList());
		}
		ActionScriptProjectData projectData = actionScriptProjectManager.getProjectDataForSourceFile(path);
		if (projectData == null || projectData.project == null) {
			if (cancelToken != null) {
				cancelToken.checkCanceled();
			}
			return Either.forLeft(Collections.emptyList());
		}

		IncludeFileData includeFileData = projectData.includedFiles.get(path.toString());
		int currentOffset = LanguageServerCompilerUtils.getOffsetFromPosition(fileTracker.getReader(path), position,
				includeFileData);
		if (currentOffset == -1) {
			if (cancelToken != null) {
				cancelToken.checkCanceled();
			}
			return Either.forLeft(Collections.emptyList());
		}
		if (uriString.endsWith(FILE_EXTENSION_CSS)) {
			String cssText = fileTracker.getText(path);
			CSSDocument cssDocument = null;
			if (cssText != null) {
				cssDocument = CSSDocument.parse(new ANTLRStringStream(cssText), new ArrayList<>());
			}
			if (cssDocument != null) {
				cssDocument.setSourcePath(path.toString());
				List<? extends Location> result = cssTypeDefinition(cssDocument, currentOffset, 0, projectData);
				if (cancelToken != null) {
					cancelToken.checkCanceled();
				}
				return Either.forLeft(result);
			}
			if (cancelToken != null) {
				cancelToken.checkCanceled();
			}
			return Either.forLeft(Collections.emptyList());
		}
		boolean isMXML = uriString.endsWith(FILE_EXTENSION_MXML);
		if (isMXML) {
			MXMLData mxmlData = actionScriptProjectManager.getMXMLDataForPath(path, projectData);
			IMXMLTagData offsetTag = MXMLDataUtils.getOffsetMXMLTag(mxmlData, currentOffset);
			if (offsetTag != null) {
				IASNode embeddedNode = actionScriptProjectManager.getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path,
						currentOffset, projectData);
				if (embeddedNode != null) {
					List<? extends Location> result = actionScriptTypeDefinition(embeddedNode, projectData);
					if (cancelToken != null) {
						cancelToken.checkCanceled();
					}
					return Either.forLeft(result);
				}
				// if we're inside an <fx:Script> tag, we want ActionScript lookup,
				// so that's why we call isMXMLTagValidForCompletion()
				if (MXMLDataUtils.isMXMLCodeIntelligenceAvailableForTag(offsetTag)) {
					List<? extends Location> result = mxmlTypeDefinition(offsetTag, currentOffset, projectData);
					if (cancelToken != null) {
						cancelToken.checkCanceled();
					}
					return Either.forLeft(result);
				}
			}
		}
		ISourceLocation offsetSourceLocation = actionScriptProjectManager.getOffsetSourceLocation(path, currentOffset,
				projectData);
		if (offsetSourceLocation instanceof IMXMLStyleNode) {
			// special case for <fx:Style>
			IMXMLStyleNode styleNode = (IMXMLStyleNode) offsetSourceLocation;
			List<? extends Location> result = cssTypeDefinition(styleNode, currentOffset, projectData);
			if (cancelToken != null) {
				cancelToken.checkCanceled();
			}
			return Either.forLeft(result);
		}
		if (offsetSourceLocation instanceof VSCodeASDocComment) {
			// special case for doc comments
			if (cancelToken != null) {
				cancelToken.checkCanceled();
			}
			return Either.forLeft(Collections.emptyList());
		}
		if (!(offsetSourceLocation instanceof IASNode)) {
			// we don't recognize what type this is, so don't try to treat
			// it as an IASNode
			offsetSourceLocation = null;
		}
		IASNode offsetNode = (IASNode) offsetSourceLocation;
		List<? extends Location> result = actionScriptTypeDefinition(offsetNode, projectData);
		if (cancelToken != null) {
			cancelToken.checkCanceled();
		}
		return Either.forLeft(result);
	}

	private List<? extends Location> actionScriptTypeDefinition(IASNode offsetNode,
			ActionScriptProjectData projectData) {
		if (offsetNode == null) {
			// we couldn't find a node at the specified location
			return Collections.emptyList();
		}

		IDefinition definition = null;

		if (definition == null && offsetNode instanceof IIdentifierNode) {
			IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
			definition = DefinitionUtils.resolveTypeWithExtras(identifierNode, projectData.project);
		}

		if (definition == null) {
			// VSCode may call typeDefinition() when there isn't necessarily a
			// type definition referenced at the current position.
			return Collections.emptyList();
		}
		List<Location> result = new ArrayList<>();
		actionScriptProjectManager.resolveDefinition(definition, projectData, result);
		return result;
	}

	private List<? extends Location> mxmlTypeDefinition(IMXMLTagData offsetTag, int currentOffset,
			ActionScriptProjectData projectData) {
		IDefinition definition = MXMLDataUtils.getTypeDefinitionForMXMLNameAtOffset(offsetTag, currentOffset,
				projectData.project);
		if (definition == null) {
			// VSCode may call typeDefinition() when there isn't necessarily a
			// definition referenced at the current position.
			return Collections.emptyList();
		}

		if (MXMLDataUtils.isInsideTagPrefix(offsetTag, currentOffset)) {
			// ignore the tag's prefix
			return Collections.emptyList();
		}

		List<Location> result = new ArrayList<>();
		actionScriptProjectManager.resolveDefinition(definition, projectData, result);
		return result;
	}

	private List<? extends Location> cssTypeDefinition(IMXMLStyleNode styleNode, int currentOffset,
			ActionScriptProjectData projectData) {
		ICSSDocument cssDocument = styleNode.getCSSDocument(new ArrayList<>());
		if (cssDocument == null) {
			return Collections.emptyList();
		}
		return cssTypeDefinition(cssDocument, currentOffset, styleNode.getContentStart(), projectData);
	}

	private List<? extends Location> cssTypeDefinition(ICSSDocument cssDocument, int currentOffset, int contentStart,
			ActionScriptProjectData projectData) {
		IDefinition definition = null;

		ICSSNode cssNode = CSSDocumentUtils.getContainingCSSNodeIncludingStart(cssDocument,
				currentOffset - contentStart);

		if (cssNode instanceof ICSSProperty) {
			ICSSProperty cssProperty = (ICSSProperty) cssNode;
			int propertyNameEnd = contentStart + cssProperty.getAbsoluteStart()
					+ cssProperty.getName().length();
			if (currentOffset < propertyNameEnd) {
				ICSSNode propertyParent = cssProperty.getParent();
				ICSSRule cssRule = null;
				if (propertyParent instanceof ICSSRule) {
					cssRule = (ICSSRule) propertyParent;
				}
				if (cssRule != null) {
					ImmutableList<ICSSSelector> selectors = cssRule.getSelectorGroup();
					for (int i = selectors.size() - 1; i >= 0; i--) {
						ICSSSelector cssSelector = selectors.get(i);
						String elementName = cssSelector.getElementName();
						if (elementName == null || elementName.length() == 0) {
							continue;
						}
						ICSSNamespaceDefinition cssNamespace = CSSDocumentUtils
								.getNamespaceForPrefix(cssSelector.getNamespacePrefix(), cssDocument);
						if (cssNamespace != null) {
							XMLName xmlName = new XMLName(cssNamespace.getURI(), cssSelector.getElementName());
							IDefinition selectorDefinition = projectData.project.resolveXMLNameToDefinition(xmlName,
									MXMLDialect.DEFAULT);
							if (selectorDefinition instanceof IClassDefinition) {
								IClassDefinition classDefinition = (IClassDefinition) selectorDefinition;
								IClassDefinition currentClass = classDefinition;
								while (currentClass != null) {
									IStyleDefinition[] styleDefinitions = currentClass
											.getStyleDefinitions(projectData.project.getWorkspace());
									if (styleDefinitions != null) {
										for (IStyleDefinition styleDef : styleDefinitions) {
											if (styleDef.getBaseName().equals(cssProperty.getName())) {
												definition = styleDef;
												break;
											}
										}
									}
									if (definition != null) {
										break;
									}
									currentClass = currentClass.resolveBaseClass(projectData.project);
								}
								if (definition != null) {
									break;
								}
							}
						}
					}
				}
			}
		} else if (cssNode instanceof ICSSSelector) {
			ICSSSelector cssSelector = (ICSSSelector) cssNode;
			ICSSNamespaceDefinition cssNamespace = CSSDocumentUtils
					.getNamespaceForPrefix(cssSelector.getNamespacePrefix(), cssDocument);
			if (cssNamespace != null) {
				int conditionsStart = contentStart + cssSelector.getAbsoluteEnd();
				for (ICSSSelectorCondition condition : cssSelector.getConditions()) {
					conditionsStart = contentStart + condition.getAbsoluteStart();
					break;
				}
				String nsPrefix = cssNamespace.getPrefix();
				int elementNameStart = conditionsStart - cssSelector.getElementName().length();
				int prefixEnd = elementNameStart;
				if (nsPrefix.length() > 0) {
					prefixEnd--;
				}
				int prefixStart = prefixEnd - nsPrefix.length();
				if (currentOffset >= elementNameStart && currentOffset < conditionsStart) {
					XMLName xmlName = new XMLName(cssNamespace.getURI(), cssSelector.getElementName());
					definition = projectData.project.resolveXMLNameToDefinition(xmlName, MXMLDialect.DEFAULT);
				} else if (currentOffset >= prefixStart && currentOffset < prefixEnd) {
					List<Location> result = new ArrayList<>();
					Path resolvedPath = Paths.get(cssDocument.getSourcePath());
					Location location = new Location();
					Position start = new Position(cssNamespace.getLine(), cssNamespace.getColumn());
					Position end = new Position(cssNamespace.getEndLine(), cssNamespace.getEndColumn());
					Range range = new Range(start, end);
					location.setRange(range);
					location.setUri(resolvedPath.toUri().toString());
					result.add(location);
					return result;
				}
			}
		}

		if (definition == null) {
			// VSCode may call typeDefinition() when there isn't necessarily a
			// definition referenced at the current position.
			return Collections.emptyList();
		}

		List<Location> result = new ArrayList<>();
		actionScriptProjectManager.resolveDefinition(definition, projectData, result);
		return result;
	}
}