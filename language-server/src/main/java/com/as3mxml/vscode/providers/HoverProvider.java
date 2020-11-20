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
import java.util.Collections;

import com.as3mxml.vscode.project.ILspProject;
import com.as3mxml.vscode.project.ActionScriptProjectData;
import com.as3mxml.vscode.utils.CompilationUnitUtils.IncludeFileData;
import com.as3mxml.vscode.utils.DefinitionDocumentationUtils;
import com.as3mxml.vscode.utils.DefinitionTextUtils;
import com.as3mxml.vscode.utils.DefinitionUtils;
import com.as3mxml.vscode.utils.FileTracker;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;
import com.as3mxml.vscode.utils.MXMLDataUtils;
import com.as3mxml.vscode.utils.ActionScriptProjectManager;

import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.internal.mxml.MXMLData;
import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IClassNode;
import org.apache.royale.compiler.tree.as.IExpressionNode;
import org.apache.royale.compiler.tree.as.IFunctionCallNode;
import org.apache.royale.compiler.tree.as.IIdentifierNode;
import org.apache.royale.compiler.tree.as.ILanguageIdentifierNode;
import org.apache.royale.compiler.tree.as.INamespaceDecorationNode;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class HoverProvider {
    private static final String MARKED_STRING_LANGUAGE_ACTIONSCRIPT = "actionscript";
    private static final String MARKED_STRING_LANGUAGE_XML = "xml";
    private static final String FILE_EXTENSION_MXML = ".mxml";

    private ActionScriptProjectManager actionScriptProjectManager;
    private FileTracker fileTracker;

    public HoverProvider(ActionScriptProjectManager actionScriptProjectManager, FileTracker fileTracker) {
        this.actionScriptProjectManager = actionScriptProjectManager;
        this.fileTracker = fileTracker;
    }

    public Hover hover(HoverParams params, CancelChecker cancelToken) {
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
            return new Hover(Collections.emptyList(), null);
        }
        ActionScriptProjectData projectData = actionScriptProjectManager.getProjectDataForSourceFile(path);
        if (projectData == null || projectData.project == null) {
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return new Hover(Collections.emptyList(), null);
        }

        IncludeFileData includeFileData = projectData.includedFiles.get(path.toString());
        int currentOffset = LanguageServerCompilerUtils.getOffsetFromPosition(fileTracker.getReader(path), position,
                includeFileData);
        if (currentOffset == -1) {
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return new Hover(Collections.emptyList(), null);
        }
        boolean isMXML = textDocument.getUri().endsWith(FILE_EXTENSION_MXML);
        if (isMXML) {
            MXMLData mxmlData = actionScriptProjectManager.getMXMLDataForPath(path, projectData);
            IMXMLTagData offsetTag = MXMLDataUtils.getOffsetMXMLTag(mxmlData, currentOffset);
            if (offsetTag != null) {
                IASNode embeddedNode = actionScriptProjectManager.getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path,
                        currentOffset, projectData);
                if (embeddedNode != null) {
                    Hover result = actionScriptHover(embeddedNode, projectData.project);
                    if (cancelToken != null) {
                        cancelToken.checkCanceled();
                    }
                    return result;
                }
                //if we're inside an <fx:Script> tag, we want ActionScript hover,
                //so that's why we call isMXMLTagValidForCompletion()
                if (MXMLDataUtils.isMXMLCodeIntelligenceAvailableForTag(offsetTag)) {
                    Hover result = mxmlHover(offsetTag, currentOffset, projectData.project);
                    if (cancelToken != null) {
                        cancelToken.checkCanceled();
                    }
                    return result;
                }
            }
        }
        IASNode offsetNode = actionScriptProjectManager.getOffsetNode(path, currentOffset, projectData);
        Hover result = actionScriptHover(offsetNode, projectData.project);
        if (cancelToken != null) {
            cancelToken.checkCanceled();
        }
        return result;
    }

    private Hover actionScriptHover(IASNode offsetNode, ILspProject project) {
        IDefinition definition = null;
        if (offsetNode == null) {
            //we couldn't find a node at the specified location
            return new Hover(Collections.emptyList(), null);
        }

        //INamespaceDecorationNode extends IIdentifierNode, but we don't want
        //any hover information for it.
        if (definition == null && offsetNode instanceof IIdentifierNode
                && !(offsetNode instanceof INamespaceDecorationNode)) {
            IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
            definition = DefinitionUtils.resolveWithExtras(identifierNode, project);
        }

        if (definition == null && offsetNode instanceof ILanguageIdentifierNode) {
            ILanguageIdentifierNode languageIdentifierNode = (ILanguageIdentifierNode) offsetNode;
            IExpressionNode expressionToResolve = null;
            switch (languageIdentifierNode.getKind()) {
                case THIS: {
                    IClassNode classNode = (IClassNode) offsetNode.getAncestorOfType(IClassNode.class);
                    if (classNode != null) {
                        expressionToResolve = classNode.getNameExpressionNode();
                    }
                    break;
                }
                case SUPER: {
                    IClassNode classNode = (IClassNode) offsetNode.getAncestorOfType(IClassNode.class);
                    if (classNode != null) {
                        expressionToResolve = classNode.getBaseClassExpressionNode();
                    }
                    break;
                }
                default:
            }
            if (expressionToResolve != null) {
                definition = expressionToResolve.resolve(project);
            }
        }

        if (definition == null) {
            return new Hover(Collections.emptyList(), null);
        }

        IASNode parentNode = offsetNode.getParent();
        if (definition instanceof IClassDefinition && parentNode instanceof IFunctionCallNode) {
            IFunctionCallNode functionCallNode = (IFunctionCallNode) parentNode;
            if (functionCallNode.isNewExpression()) {
                IClassDefinition classDefinition = (IClassDefinition) definition;
                //if it's a class in a new expression, use the constructor
                //definition instead
                IFunctionDefinition constructorDefinition = classDefinition.getConstructor();
                if (constructorDefinition != null) {
                    definition = constructorDefinition;
                }
            }
        }

        Hover result = new Hover();
        String detail = DefinitionTextUtils.definitionToDetail(definition, project);
        detail = codeBlock(MARKED_STRING_LANGUAGE_ACTIONSCRIPT, detail);
        String docs = DefinitionDocumentationUtils.getDocumentationForDefinition(definition, true,
                project.getWorkspace(), true);
        if (docs != null) {
            detail += "\n\n---\n\n" + docs;
        }
        result.setContents(new MarkupContent(MarkupKind.MARKDOWN, detail));
        return result;
    }

    private static String codeBlock(String languageId, String code) {
        StringBuilder builder = new StringBuilder();
        builder.append("```");
        builder.append(languageId);
        builder.append("\n");
        builder.append(code);
        builder.append("\n");
        builder.append("```");
        return builder.toString();
    }

    private Hover mxmlHover(IMXMLTagData offsetTag, int currentOffset, ILspProject project) {
        IDefinition definition = MXMLDataUtils.getDefinitionForMXMLNameAtOffset(offsetTag, currentOffset, project);
        if (definition == null) {
            return new Hover(Collections.emptyList(), null);
        }

        if (MXMLDataUtils.isInsideTagPrefix(offsetTag, currentOffset)) {
            //inside the prefix
            String prefix = offsetTag.getPrefix();
            Hover result = new Hover();
            StringBuilder detailBuilder = new StringBuilder();
            if (prefix.length() > 0) {
                detailBuilder.append("xmlns:" + prefix + "=\"" + offsetTag.getURI() + "\"");
            } else {
                detailBuilder.append("xmlns=\"" + offsetTag.getURI() + "\"");
            }
            String detail = codeBlock(MARKED_STRING_LANGUAGE_XML, detailBuilder.toString());
            result.setContents(new MarkupContent(MarkupKind.MARKDOWN, detail));
            return result;
        }

        Hover result = new Hover();
        String detail = DefinitionTextUtils.definitionToDetail(definition, project);
        detail = codeBlock(MARKED_STRING_LANGUAGE_ACTIONSCRIPT, detail);
        String docs = DefinitionDocumentationUtils.getDocumentationForDefinition(definition, true,
                project.getWorkspace(), true);
        if (docs != null) {
            detail += "\n\n---\n\n" + docs;
        }
        result.setContents(new MarkupContent(MarkupKind.MARKDOWN, detail));
        return result;
    }
}