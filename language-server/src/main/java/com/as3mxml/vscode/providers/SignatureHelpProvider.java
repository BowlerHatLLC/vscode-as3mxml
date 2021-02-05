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

import com.as3mxml.vscode.project.ILspProject;
import com.as3mxml.vscode.project.ActionScriptProjectData;
import com.as3mxml.vscode.utils.ASTUtils;
import com.as3mxml.vscode.utils.CompilationUnitUtils.IncludeFileData;
import com.as3mxml.vscode.utils.DefinitionDocumentationUtils;
import com.as3mxml.vscode.utils.DefinitionTextUtils;
import com.as3mxml.vscode.utils.FileTracker;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;
import com.as3mxml.vscode.utils.MXMLDataUtils;
import com.as3mxml.vscode.utils.ActionScriptProjectManager;

import org.apache.royale.compiler.constants.IASKeywordConstants;
import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IParameterDefinition;
import org.apache.royale.compiler.definitions.ITypeDefinition;
import org.apache.royale.compiler.internal.mxml.MXMLData;
import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IExpressionNode;
import org.apache.royale.compiler.tree.as.IFunctionCallNode;
import org.apache.royale.compiler.tree.as.IIdentifierNode;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class SignatureHelpProvider {
	private static final String FILE_EXTENSION_MXML = ".mxml";

	private ActionScriptProjectManager actionScriptProjectManager;
	private FileTracker fileTracker;

	public SignatureHelpProvider(ActionScriptProjectManager actionScriptProjectManager, FileTracker fileTracker) {
		this.actionScriptProjectManager = actionScriptProjectManager;
		this.fileTracker = fileTracker;
	}

	public SignatureHelp signatureHelp(SignatureHelpParams params, CancelChecker cancelToken) {
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
			return new SignatureHelp(Collections.emptyList(), -1, -1);
		}
		ActionScriptProjectData projectData = actionScriptProjectManager.getProjectDataForSourceFile(path);
		if (projectData == null || projectData.project == null) {
			if (cancelToken != null) {
				cancelToken.checkCanceled();
			}
			return new SignatureHelp(Collections.emptyList(), -1, -1);
		}
		ILspProject project = projectData.project;

		IncludeFileData includeFileData = projectData.includedFiles.get(path.toString());
		int currentOffset = LanguageServerCompilerUtils.getOffsetFromPosition(fileTracker.getReader(path), position,
				includeFileData);
		if (currentOffset == -1) {
			if (cancelToken != null) {
				cancelToken.checkCanceled();
			}
			return new SignatureHelp(Collections.emptyList(), -1, -1);
		}
		IASNode offsetNode = null;
		boolean isMXML = textDocument.getUri().endsWith(FILE_EXTENSION_MXML);
		if (isMXML) {
			MXMLData mxmlData = actionScriptProjectManager.getMXMLDataForPath(path, projectData);
			IMXMLTagData offsetTag = MXMLDataUtils.getOffsetMXMLTag(mxmlData, currentOffset);
			if (offsetTag != null) {
				offsetNode = actionScriptProjectManager.getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path,
						currentOffset, projectData);
				if (offsetNode != null) {
					IASNode containingNode = ASTUtils.getContainingNodeIncludingStart(offsetNode, currentOffset);
					if (containingNode != null) {
						offsetNode = containingNode;
					}
				}
			}
		}
		if (offsetNode == null) {
			offsetNode = actionScriptProjectManager.getOffsetNode(path, currentOffset, projectData);
		}
		if (offsetNode == null) {
			if (cancelToken != null) {
				cancelToken.checkCanceled();
			}
			//we couldn't find a node at the specified location
			return new SignatureHelp(Collections.emptyList(), -1, -1);
		}

		IFunctionCallNode functionCallNode = ASTUtils.getAncestorFunctionCallNode(offsetNode);
		IFunctionDefinition functionDefinition = null;
		if (functionCallNode != null) {
			IExpressionNode nameNode = functionCallNode.getNameNode();
			IDefinition definition = nameNode.resolve(project);
			if (definition instanceof IFunctionDefinition) {
				functionDefinition = (IFunctionDefinition) definition;
			} else if (definition instanceof IClassDefinition) {
				IClassDefinition classDefinition = (IClassDefinition) definition;
				functionDefinition = classDefinition.getConstructor();
			} else if (nameNode instanceof IIdentifierNode) {
				//special case for super()
				IIdentifierNode identifierNode = (IIdentifierNode) nameNode;
				if (identifierNode.getName().equals(IASKeywordConstants.SUPER)) {
					ITypeDefinition typeDefinition = nameNode.resolveType(project);
					if (typeDefinition instanceof IClassDefinition) {
						IClassDefinition classDefinition = (IClassDefinition) typeDefinition;
						functionDefinition = classDefinition.getConstructor();
					}
				}
			}
		}
		if (functionDefinition != null) {
			SignatureHelp result = new SignatureHelp();
			List<SignatureInformation> signatures = new ArrayList<>();

			SignatureInformation signatureInfo = new SignatureInformation();
			signatureInfo.setLabel(DefinitionTextUtils.functionDefinitionToSignature(functionDefinition, project));
			String docs = DefinitionDocumentationUtils.getDocumentationForDefinition(functionDefinition, true,
					project.getWorkspace(), true);
			if (docs != null) {
				signatureInfo.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, docs));
			}

			List<ParameterInformation> parameters = new ArrayList<>();
			for (IParameterDefinition param : functionDefinition.getParameters()) {
				ParameterInformation paramInfo = new ParameterInformation();
				paramInfo.setLabel(param.getBaseName());
				String paramDocs = DefinitionDocumentationUtils.getDocumentationForParameter(param, true,
						project.getWorkspace());
				if (paramDocs != null) {
					paramInfo.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, paramDocs));
				}
				parameters.add(paramInfo);
			}
			signatureInfo.setParameters(parameters);
			signatures.add(signatureInfo);
			result.setSignatures(signatures);
			result.setActiveSignature(0);

			int index = ASTUtils.getFunctionCallNodeArgumentIndex(functionCallNode, offsetNode);
			IParameterDefinition[] parameterDefs = functionDefinition.getParameters();
			int paramCount = parameterDefs.length;
			if (paramCount > 0 && index >= paramCount) {
				if (index >= paramCount) {
					IParameterDefinition lastParam = parameterDefs[paramCount - 1];
					if (lastParam.isRest()) {
						//functions with rest parameters may accept any
						//number of arguments, so continue to make the rest
						//parameter active
						index = paramCount - 1;
					} else {
						//if there's no rest parameter, and we're beyond the
						//final parameter, none should be active
						index = -1;
					}
				}
			}
			if (index != -1) {
				result.setActiveParameter(index);
			}
			if (cancelToken != null) {
				cancelToken.checkCanceled();
			}
			return result;
		}
		if (cancelToken != null) {
			cancelToken.checkCanceled();
		}
		return new SignatureHelp(Collections.emptyList(), -1, -1);
	}
}