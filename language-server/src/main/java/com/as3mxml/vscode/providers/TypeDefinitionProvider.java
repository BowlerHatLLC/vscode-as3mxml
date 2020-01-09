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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.as3mxml.vscode.project.WorkspaceFolderData;
import com.as3mxml.vscode.utils.DefinitionUtils;
import com.as3mxml.vscode.utils.FileTracker;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;
import com.as3mxml.vscode.utils.MXMLDataUtils;
import com.as3mxml.vscode.utils.WorkspaceFolderManager;
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
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class TypeDefinitionProvider
{
    private static final String FILE_EXTENSION_MXML = ".mxml";

	private WorkspaceFolderManager workspaceFolderManager;
	private FileTracker fileTracker;

	public TypeDefinitionProvider(WorkspaceFolderManager workspaceFolderManager, FileTracker fileTracker)
	{
		this.workspaceFolderManager = workspaceFolderManager;
		this.fileTracker = fileTracker;
	}

	public Either<List<? extends Location>, List<? extends LocationLink>> typeDefinition(TextDocumentPositionParams params, CancelChecker cancelToken)
	{
		cancelToken.checkCanceled();
		TextDocumentIdentifier textDocument = params.getTextDocument();
		Position position = params.getPosition();
		Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
		if (path == null)
		{
			cancelToken.checkCanceled();
			return Either.forLeft(Collections.emptyList());
		}
		WorkspaceFolderData folderData = workspaceFolderManager.getWorkspaceFolderDataForSourceFile(path);
		if(folderData == null || folderData.project == null)
		{
			cancelToken.checkCanceled();
			return Either.forLeft(Collections.emptyList());
		}

        IncludeFileData includeFileData = folderData.includedFiles.get(path.toString());
		int currentOffset = LanguageServerCompilerUtils.getOffsetFromPosition(fileTracker.getReader(path), position, includeFileData);
		if (currentOffset == -1)
		{
			cancelToken.checkCanceled();
			return Either.forLeft(Collections.emptyList());
		}
        boolean isMXML = textDocument.getUri().endsWith(FILE_EXTENSION_MXML);
        if (isMXML)
        {
			MXMLData mxmlData = workspaceFolderManager.getMXMLDataForPath(path, folderData);
			IMXMLTagData offsetTag = MXMLDataUtils.getOffsetMXMLTag(mxmlData, currentOffset);
			if (offsetTag != null)
			{
				IASNode embeddedNode = workspaceFolderManager.getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path, currentOffset, folderData);
				if (embeddedNode != null)
				{
					List<? extends Location> result = actionScriptTypeDefinition(embeddedNode, folderData);
					cancelToken.checkCanceled();
					return Either.forLeft(result);
				}
				//if we're inside an <fx:Script> tag, we want ActionScript lookup,
				//so that's why we call isMXMLTagValidForCompletion()
				if (MXMLDataUtils.isMXMLCodeIntelligenceAvailableForTag(offsetTag))
				{
					List<? extends Location> result = mxmlTypeDefinition(offsetTag, currentOffset, folderData);
					cancelToken.checkCanceled();
					return Either.forLeft(result);
				}
			}
		}
		IASNode offsetNode = workspaceFolderManager.getOffsetNode(path, currentOffset, folderData);
		List<? extends Location> result = actionScriptTypeDefinition(offsetNode, folderData);
		cancelToken.checkCanceled();
		return Either.forLeft(result);
	}

    private List<? extends Location> actionScriptTypeDefinition(IASNode offsetNode, WorkspaceFolderData folderData)
    {
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return Collections.emptyList();
        }

        IDefinition definition = null;

        if (offsetNode instanceof IIdentifierNode)
        {
            IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
            definition = DefinitionUtils.resolveTypeWithExtras(identifierNode, folderData.project);
        }

        if (definition == null)
        {
            //VSCode may call typeDefinition() when there isn't necessarily a
            //type definition referenced at the current position.
            return Collections.emptyList();
        }
        List<Location> result = new ArrayList<>();
        workspaceFolderManager.resolveDefinition(definition, folderData, result);
        return result;
    }

    private List<? extends Location> mxmlTypeDefinition(IMXMLTagData offsetTag, int currentOffset, WorkspaceFolderData folderData)
    {
        IDefinition definition = MXMLDataUtils.getTypeDefinitionForMXMLNameAtOffset(offsetTag, currentOffset, folderData.project);
        if (definition == null)
        {
            //VSCode may call definition() when there isn't necessarily a
            //definition referenced at the current position.
            return Collections.emptyList();
        }

        if (MXMLDataUtils.isInsideTagPrefix(offsetTag, currentOffset))
        {
            //ignore the tag's prefix
            return Collections.emptyList();
        }

        List<Location> result = new ArrayList<>();
        workspaceFolderManager.resolveDefinition(definition, folderData, result);
        return result;
    }
}