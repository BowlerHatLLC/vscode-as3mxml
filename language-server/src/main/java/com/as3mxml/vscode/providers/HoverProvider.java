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

import com.as3mxml.vscode.project.ILspProject;
import com.as3mxml.vscode.project.WorkspaceFolderData;
import com.as3mxml.vscode.utils.CompilationUnitUtils.IncludeFileData;
import com.as3mxml.vscode.utils.DefinitionDocumentationUtils;
import com.as3mxml.vscode.utils.DefinitionTextUtils;
import com.as3mxml.vscode.utils.DefinitionUtils;
import com.as3mxml.vscode.utils.FileTracker;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;
import com.as3mxml.vscode.utils.MXMLDataUtils;
import com.as3mxml.vscode.utils.WorkspaceFolderManager;

import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.internal.mxml.MXMLData;
import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IFunctionCallNode;
import org.apache.royale.compiler.tree.as.IIdentifierNode;
import org.apache.royale.compiler.tree.as.INamespaceDecorationNode;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class HoverProvider
{
    private static final String MARKED_STRING_LANGUAGE_ACTIONSCRIPT = "actionscript";
    private static final String MARKED_STRING_LANGUAGE_MXML = "mxml";
    private static final String FILE_EXTENSION_MXML = ".mxml";

    private WorkspaceFolderManager workspaceFolderManager;
    private FileTracker fileTracker;

	public HoverProvider(WorkspaceFolderManager workspaceFolderManager, FileTracker fileTracker)
	{
        this.workspaceFolderManager = workspaceFolderManager;
        this.fileTracker = fileTracker;
	}

	public Hover hover(TextDocumentPositionParams params, CancelChecker cancelToken)
	{
		cancelToken.checkCanceled();
		TextDocumentIdentifier textDocument = params.getTextDocument();
		Position position = params.getPosition();
		Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
		if (path == null)
		{
			cancelToken.checkCanceled();
			return new Hover(Collections.emptyList(), null);
		}
		WorkspaceFolderData folderData = workspaceFolderManager.getWorkspaceFolderDataForSourceFile(path);
		if(folderData == null || folderData.project == null)
		{
			cancelToken.checkCanceled();
			return new Hover(Collections.emptyList(), null);
		}

        IncludeFileData includeFileData = folderData.includedFiles.get(path.toString());
		int currentOffset = LanguageServerCompilerUtils.getOffsetFromPosition(fileTracker.getReader(path), position, includeFileData);
		if (currentOffset == -1)
		{
			cancelToken.checkCanceled();
			return new Hover(Collections.emptyList(), null);
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
                    Hover result = actionScriptHover(embeddedNode, folderData.project);
                    cancelToken.checkCanceled();
                    return result;
                }
                //if we're inside an <fx:Script> tag, we want ActionScript hover,
                //so that's why we call isMXMLTagValidForCompletion()
                if (MXMLDataUtils.isMXMLCodeIntelligenceAvailableForTag(offsetTag))
                {
                    Hover result = mxmlHover(offsetTag, currentOffset, folderData.project);
                    cancelToken.checkCanceled();
                    return result;
                }
            }
        }
		IASNode offsetNode = workspaceFolderManager.getOffsetNode(path, currentOffset, folderData);
		Hover result = actionScriptHover(offsetNode, folderData.project);
		cancelToken.checkCanceled();
		return result;
	}

    private Hover actionScriptHover(IASNode offsetNode, ILspProject project)
    {
        IDefinition definition = null;
        if (offsetNode == null)
        {
            //we couldn't find a node at the specified location
            return new Hover(Collections.emptyList(), null);
        }

        //INamespaceDecorationNode extends IIdentifierNode, but we don't want
        //any hover information for it.
        if (definition == null
                && offsetNode instanceof IIdentifierNode
                && !(offsetNode instanceof INamespaceDecorationNode))
        {
            IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
            definition = DefinitionUtils.resolveWithExtras(identifierNode, project);
        }

        if (definition == null)
        {
            return new Hover(Collections.emptyList(), null);
        }

        IASNode parentNode = offsetNode.getParent();
        if (definition instanceof IClassDefinition
                && parentNode instanceof IFunctionCallNode)
        {
            IFunctionCallNode functionCallNode = (IFunctionCallNode) parentNode;
            if (functionCallNode.isNewExpression())
            {
                IClassDefinition classDefinition = (IClassDefinition) definition;
                //if it's a class in a new expression, use the constructor
                //definition instead
                IFunctionDefinition constructorDefinition = classDefinition.getConstructor();
                if (constructorDefinition != null)
                {
                    definition = constructorDefinition;
                }
            }
        }

        Hover result = new Hover();
        String detail = DefinitionTextUtils.definitionToDetail(definition, project);
        MarkedString markedDetail = new MarkedString(MARKED_STRING_LANGUAGE_ACTIONSCRIPT, detail);
        List<Either<String,MarkedString>> contents = new ArrayList<>();
        contents.add(Either.forRight(markedDetail));
        String docs = DefinitionDocumentationUtils.getDocumentationForDefinition(definition, true, project.getWorkspace(), true);
        if(docs != null)
        {
            contents.add(Either.forLeft(docs));
        }
        result.setContents(contents);
        return result;
    }

    private Hover mxmlHover(IMXMLTagData offsetTag, int currentOffset, ILspProject project)
    {
        IDefinition definition = MXMLDataUtils.getDefinitionForMXMLNameAtOffset(offsetTag, currentOffset, project);
        if (definition == null)
        {
            return new Hover(Collections.emptyList(), null);
        }

        if (MXMLDataUtils.isInsideTagPrefix(offsetTag, currentOffset))
        {
            //inside the prefix
            String prefix = offsetTag.getPrefix();
            Hover result = new Hover();
            List<Either<String,MarkedString>> contents = new ArrayList<>();
            StringBuilder detailBuilder = new StringBuilder();
            if (prefix.length() > 0)
            {
                detailBuilder.append("xmlns:" + prefix + "=\"" + offsetTag.getURI() + "\"");
            }
            else
            {
                detailBuilder.append("xmlns=\"" + offsetTag.getURI() + "\"");
            }
            MarkedString markedDetail = new MarkedString(MARKED_STRING_LANGUAGE_MXML, detailBuilder.toString());
            contents.add(Either.forRight(markedDetail));
            result.setContents(contents);
            return result;
        }

        Hover result = new Hover();
        String detail = DefinitionTextUtils.definitionToDetail(definition, project);
        MarkedString markedDetail = new MarkedString(MARKED_STRING_LANGUAGE_ACTIONSCRIPT, detail);
        List<Either<String,MarkedString>> contents = new ArrayList<>();
        contents.add(Either.forRight(markedDetail));
        result.setContents(contents);
        return result;
    }
}