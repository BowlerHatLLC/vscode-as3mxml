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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.as3mxml.vscode.project.ActionScriptProjectData;
import com.as3mxml.vscode.utils.CompilationUnitUtils.IncludeFileData;
import com.as3mxml.vscode.utils.DefinitionUtils;
import com.as3mxml.vscode.utils.FileTracker;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;
import com.as3mxml.vscode.utils.MXMLDataUtils;
import com.as3mxml.vscode.utils.ActionScriptProjectManager;

import org.apache.royale.compiler.common.XMLName;
import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.internal.mxml.MXMLData;
import org.apache.royale.compiler.mxml.IMXMLLanguageConstants;
import org.apache.royale.compiler.mxml.IMXMLTagAttributeData;
import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IClassNode;
import org.apache.royale.compiler.tree.as.IExpressionNode;
import org.apache.royale.compiler.tree.as.IFunctionCallNode;
import org.apache.royale.compiler.tree.as.IIdentifierNode;
import org.apache.royale.compiler.tree.as.ILanguageIdentifierNode;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class DefinitionProvider {
    private static final String FILE_EXTENSION_MXML = ".mxml";

    private ActionScriptProjectManager actionScriptProjectManager;
    private FileTracker fileTracker;

    public DefinitionProvider(ActionScriptProjectManager actionScriptProjectManager, FileTracker fileTracker) {
        this.actionScriptProjectManager = actionScriptProjectManager;
        this.fileTracker = fileTracker;
    }

    public Either<List<? extends Location>, List<? extends LocationLink>> definition(DefinitionParams params,
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
                    List<? extends Location> result = actionScriptDefinition(embeddedNode, projectData);
                    if (cancelToken != null) {
                        cancelToken.checkCanceled();
                    }
                    return Either.forLeft(result);
                }
                //if we're inside an <fx:Script> tag, we want ActionScript lookup,
                //so that's why we call isMXMLTagValidForCompletion()
                if (MXMLDataUtils.isMXMLCodeIntelligenceAvailableForTag(offsetTag)) {
                    List<? extends Location> result = mxmlDefinition(offsetTag, currentOffset, projectData);
                    if (cancelToken != null) {
                        cancelToken.checkCanceled();
                    }
                    return Either.forLeft(result);
                }
            }
        }
        IASNode offsetNode = actionScriptProjectManager.getOffsetNode(path, currentOffset, projectData);
        List<? extends Location> result = actionScriptDefinition(offsetNode, projectData);
        if (cancelToken != null) {
            cancelToken.checkCanceled();
        }
        return Either.forLeft(result);
    }

    private List<? extends Location> actionScriptDefinition(IASNode offsetNode, ActionScriptProjectData projectData) {
        if (offsetNode == null) {
            //we couldn't find a node at the specified location
            return Collections.emptyList();
        }

        IDefinition definition = null;

        if (offsetNode instanceof IIdentifierNode) {
            IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
            definition = DefinitionUtils.resolveWithExtras(identifierNode, projectData.project);
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
                definition = expressionToResolve.resolve(projectData.project);
            }
        }

        if (definition == null) {
            //VSCode may call definition() when there isn't necessarily a
            //definition referenced at the current position.
            return Collections.emptyList();
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

        List<Location> result = new ArrayList<>();
        actionScriptProjectManager.resolveDefinition(definition, projectData, result);
        return result;
    }

    private List<? extends Location> mxmlDefinition(IMXMLTagData offsetTag, int currentOffset,
            ActionScriptProjectData projectData) {
        IDefinition definition = MXMLDataUtils.getDefinitionForMXMLNameAtOffset(offsetTag, currentOffset,
                projectData.project);
        if (definition == null) {
            XMLName offsetXMLName = offsetTag.getXMLName();
            if ((offsetXMLName.equals(offsetTag.getMXMLDialect().resolveStyle())
                    || offsetXMLName.equals(offsetTag.getMXMLDialect().resolveScript()))
                    && offsetTag.isOffsetInAttributeList(currentOffset)) {
                IMXMLTagAttributeData attributeData = MXMLDataUtils.getMXMLTagAttributeWithValueAtOffset(offsetTag,
                        currentOffset);
                if (attributeData != null && attributeData.getName().equals(IMXMLLanguageConstants.ATTRIBUTE_SOURCE)) {
                    Path sourcePath = Paths.get(attributeData.getRawValue());
                    if (!sourcePath.isAbsolute()) {
                        sourcePath = Paths.get(offsetTag.getSourcePath()).getParent().resolve(sourcePath);
                    }

                    List<Location> result = new ArrayList<>();
                    Location location = new Location();
                    location.setUri(sourcePath.toUri().toString());
                    location.setRange(new Range(new Position(), new Position()));
                    result.add(location);
                    return result;
                }
            }

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