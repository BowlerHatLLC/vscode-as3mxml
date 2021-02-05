/*
Copyright 2016-2021 Bowler Hat LLC

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.as3mxml.vscode.project.ActionScriptProjectData;
import com.as3mxml.vscode.utils.DefinitionUtils;
import com.as3mxml.vscode.utils.FileTracker;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;
import com.as3mxml.vscode.utils.MXMLDataUtils;
import com.as3mxml.vscode.utils.ActionScriptProjectManager;
import com.as3mxml.vscode.utils.CompilationUnitUtils.IncludeFileData;

import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.internal.mxml.MXMLData;
import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IIdentifierNode;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TypeDefinitionParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class TypeDefinitionProvider {
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
		Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
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
		boolean isMXML = textDocument.getUri().endsWith(FILE_EXTENSION_MXML);
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
				//if we're inside an <fx:Script> tag, we want ActionScript lookup,
				//so that's why we call isMXMLTagValidForCompletion()
				if (MXMLDataUtils.isMXMLCodeIntelligenceAvailableForTag(offsetTag)) {
					List<? extends Location> result = mxmlTypeDefinition(offsetTag, currentOffset, projectData);
					if (cancelToken != null) {
						cancelToken.checkCanceled();
					}
					return Either.forLeft(result);
				}
			}
		}
		IASNode offsetNode = actionScriptProjectManager.getOffsetNode(path, currentOffset, projectData);
		List<? extends Location> result = actionScriptTypeDefinition(offsetNode, projectData);
		if (cancelToken != null) {
			cancelToken.checkCanceled();
		}
		return Either.forLeft(result);
	}

	private List<? extends Location> actionScriptTypeDefinition(IASNode offsetNode,
			ActionScriptProjectData projectData) {
		if (offsetNode == null) {
			//we couldn't find a node at the specified location
			return Collections.emptyList();
		}

		IDefinition definition = null;

		if (offsetNode instanceof IIdentifierNode) {
			IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
			definition = DefinitionUtils.resolveTypeWithExtras(identifierNode, projectData.project);
		}

		if (definition == null) {
			//VSCode may call typeDefinition() when there isn't necessarily a
			//type definition referenced at the current position.
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
			//VSCode may call definition() when there isn't necessarily a
			//definition referenced at the current position.
			return Collections.emptyList();
		}

		if (MXMLDataUtils.isInsideTagPrefix(offsetTag, currentOffset)) {
			//ignore the tag's prefix
			return Collections.emptyList();
		}

		List<Location> result = new ArrayList<>();
		actionScriptProjectManager.resolveDefinition(definition, projectData, result);
		return result;
	}
}