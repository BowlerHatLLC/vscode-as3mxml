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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.royale.compiler.common.ISourceLocation;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IParameterDefinition;
import org.apache.royale.compiler.internal.mxml.MXMLData;
import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.mxml.IMXMLUnitData;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IExpressionNode;
import org.apache.royale.compiler.tree.as.IFunctionCallNode;
import org.apache.royale.compiler.tree.as.IIdentifierNode;
import org.apache.royale.compiler.tree.as.ILiteralNode;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

import com.as3mxml.vscode.project.ActionScriptProjectData;
import com.as3mxml.vscode.utils.ActionScriptProjectManager;
import com.as3mxml.vscode.utils.DefinitionDocumentationUtils;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;

public class InlayHintProvider {
    private static final String FILE_EXTENSION_AS = ".as";
    private static final String FILE_EXTENSION_MXML = ".mxml";

    private ActionScriptProjectManager actionScriptProjectManager;

    public String inlayHints_parameterNames_enabled = "none";
    public boolean inlayHints_parameterNames_suppressWhenArgumentMatchesName = true;

    public InlayHintProvider(ActionScriptProjectManager actionScriptProjectManager) {
        this.actionScriptProjectManager = actionScriptProjectManager;
    }

    public List<InlayHint> inlayHint(InlayHintParams params, CancelChecker cancelToken) {
        if (cancelToken != null) {
            cancelToken.checkCanceled();
        }
        if ("none".equals(inlayHints_parameterNames_enabled)) {
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return Collections.emptyList();
        }
        TextDocumentIdentifier textDocument = params.getTextDocument();
        Range range = params.getRange();
        String uriString = textDocument.getUri();
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(uriString);
        if (path == null) {
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return Collections.emptyList();
        }
        ActionScriptProjectData projectData = actionScriptProjectManager.getProjectDataForSourceFile(path);
        if (projectData == null || projectData.project == null) {
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return Collections.emptyList();
        }

        if (uriString.endsWith(FILE_EXTENSION_MXML)) {
            MXMLData mxmlData = actionScriptProjectManager.getMXMLDataForPath(path, projectData);
            List<InlayHint> result = mxmlInlayHint(mxmlData, range, path, projectData);
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return result;
        }

        if (!uriString.endsWith(FILE_EXTENSION_AS)) {
            return Collections.emptyList();
        }

        IASNode ast = actionScriptProjectManager.getAST(path, projectData);
        if (ast == null) {
            // can happen if the file is completely empty
            return Collections.emptyList();
        }
        List<InlayHint> result = actionScriptInlayHint(ast, range, projectData);
        if (cancelToken != null) {
            cancelToken.checkCanceled();
        }
        return result;
    }

    private List<InlayHint> actionScriptInlayHint(IASNode ast, Range range, ActionScriptProjectData projectData) {
        List<InlayHint> result = new ArrayList<>();
        findInlayHints(ast, range, projectData, result);
        return result;
    }

    private List<InlayHint> mxmlInlayHint(MXMLData mxmlData, Range range, Path path,
            ActionScriptProjectData projectData) {
        List<InlayHint> result = new ArrayList<>();
        for (IMXMLUnitData unitData : mxmlData.getUnits()) {
            if (!(unitData instanceof IMXMLTagData)) {
                continue;
            }
            IMXMLTagData tagData = (IMXMLTagData) unitData;
            List<IASNode> embeddedNodes = actionScriptProjectManager.getEmbeddedActionScriptNodesInMXMLTag(
                    tagData, path, projectData);
            for (IASNode node : embeddedNodes) {
                findInlayHints(node, range, projectData, result);
            }
            if (!tagData.isOpenTag() || tagData.isEmptyTag()) {
                continue;
            }
            if (tagData.getXMLName().equals(tagData.getMXMLDialect().resolveScript())) {
                ISourceLocation offsetSourceLocation = actionScriptProjectManager
                        .getOffsetSourceLocation(path, tagData.getContentStart(), projectData);
                if (offsetSourceLocation instanceof IASNode) {
                    IASNode offsetNode = (IASNode) offsetSourceLocation;
                    findInlayHints(offsetNode, range, projectData, result);
                }
            }
        }
        return result;
    }

    private boolean findInlayHints(IASNode node, Range range, ActionScriptProjectData projectData,
            List<InlayHint> result) {
        Position start = range.getStart();
        Position end = range.getEnd();
        int line = node.getLine();
        int column = node.getColumn();
        int endLine = node.getEndLine();
        int endColumn = node.getEndColumn();
        // sometimes, the position is unknown, and we need to skip this check
        // entirely, or we might stop searching early.
        if (line != -1 && column != -1 && endLine != -1 && endColumn != -1) {
            if (endLine < start.getLine()
                    || (endLine == start.getLine() && endColumn < start.getCharacter())) {
                // node is completely before the start of the range
                return false;
            }
            if (line > end.getLine()
                    || (line == end.getLine() && column > end.getCharacter())) {
                // node is completely after the end of the range
                return true;
            }
        }

        if (node instanceof IFunctionCallNode) {
            IFunctionCallNode functionCallNode = (IFunctionCallNode) node;
            IDefinition calledDefinition = functionCallNode.resolveCalledExpression(projectData.project);
            if (calledDefinition instanceof IFunctionDefinition) {
                IFunctionDefinition functionDefinition = (IFunctionDefinition) calledDefinition;
                IParameterDefinition[] paramDefs = functionDefinition.getParameters();
                IExpressionNode[] argNodes = functionCallNode.getArgumentNodes();
                int minLength = Math.min(paramDefs.length, argNodes.length);
                for (int i = 0; i < minLength; i++) {
                    IExpressionNode argNode = argNodes[i];
                    if ("literals".equals(inlayHints_parameterNames_enabled) && !(argNode instanceof ILiteralNode)) {
                        continue;
                    }
                    int argLine = argNode.getLine();
                    int argColumn = argNode.getColumn();
                    if (argLine == -1 || argColumn == -1) {
                        continue;
                    }
                    IParameterDefinition paramDef = paramDefs[i];
                    String paramName = paramDef.getBaseName();
                    if (paramName == null || paramName.length() == 0) {
                        if (paramDef.isRest()) {
                            paramName = "rest";
                        } else {
                            // parameter doesn't have a name, for some reason,
                            // so skip it!
                            continue;
                        }
                    }
                    if (paramDef.isRest()) {
                        paramName = "..." + paramName;
                    }
                    boolean needsPaddingLeft = false;
                    if (argNode instanceof IIdentifierNode) {
                        IIdentifierNode argIdentifier = (IIdentifierNode) argNode;
                        String identiferName = argIdentifier.getName();
                        if (inlayHints_parameterNames_suppressWhenArgumentMatchesName
                                && paramName.equals(identiferName)) {
                            continue;
                        }
                        // if the identifier is missing, add a little extra
                        // padding after the preceding ( or , character
                        needsPaddingLeft = "".equals(argIdentifier.getName());
                    }
                    InlayHint inlayHint = new InlayHint();
                    inlayHint.setLabel(paramName + ":");
                    inlayHint.setPosition(new Position(argLine, argColumn));
                    inlayHint.setKind(InlayHintKind.Parameter);
                    inlayHint.setPaddingRight(true);
                    inlayHint.setPaddingLeft(needsPaddingLeft);
                    String markdown = DefinitionDocumentationUtils.getDocumentationForParameter(paramDef, true,
                            projectData.project.getWorkspace());
                    if (markdown != null) {
                        inlayHint.setTooltip(new MarkupContent(MarkupKind.MARKDOWN, markdown));
                    }
                    result.add(inlayHint);
                }
            }
            return false;
        } else if (node.isTerminal()) {
            return false;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            IASNode child = node.getChild(i);
            if (findInlayHints(child, range, projectData, result)) {
                return true;
            }
        }

        return false;
    }
}