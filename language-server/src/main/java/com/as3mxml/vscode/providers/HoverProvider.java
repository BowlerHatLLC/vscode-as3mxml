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

import org.antlr.runtime.ANTLRStringStream;
import org.apache.royale.compiler.common.ISourceLocation;
import org.apache.royale.compiler.constants.IASKeywordConstants;
import org.apache.royale.compiler.constants.IMetaAttributeConstants;
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
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IStyleDefinition;
import org.apache.royale.compiler.internal.css.CSSDocument;
import org.apache.royale.compiler.internal.mxml.MXMLData;
import org.apache.royale.compiler.internal.mxml.MXMLDialect;
import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IClassNode;
import org.apache.royale.compiler.tree.as.IExpressionNode;
import org.apache.royale.compiler.tree.as.IFunctionCallNode;
import org.apache.royale.compiler.tree.as.IIdentifierNode;
import org.apache.royale.compiler.tree.as.ILanguageIdentifierNode;
import org.apache.royale.compiler.tree.metadata.IEventTagNode;
import org.apache.royale.compiler.tree.metadata.IInspectableTagNode;
import org.apache.royale.compiler.tree.metadata.IStyleTagNode;
import org.apache.royale.compiler.tree.metadata.ITypedTagNode;
import org.apache.royale.compiler.tree.mxml.IMXMLStyleNode;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

import com.as3mxml.vscode.asdoc.VSCodeASDocComment;
import com.as3mxml.vscode.project.ActionScriptProjectData;
import com.as3mxml.vscode.project.ILspProject;
import com.as3mxml.vscode.utils.ASDocUtils;
import com.as3mxml.vscode.utils.ActionScriptProjectManager;
import com.as3mxml.vscode.utils.CSSDocumentUtils;
import com.as3mxml.vscode.utils.CompilationUnitUtils.IncludeFileData;
import com.as3mxml.vscode.utils.DefinitionDocumentationUtils;
import com.as3mxml.vscode.utils.DefinitionTextUtils;
import com.as3mxml.vscode.utils.DefinitionUtils;
import com.as3mxml.vscode.utils.FileTracker;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;
import com.as3mxml.vscode.utils.MXMLDataUtils;
import com.google.common.collect.ImmutableList;

public class HoverProvider {
    private static final String MARKED_STRING_LANGUAGE_ACTIONSCRIPT = "actionscript";
    private static final String MARKED_STRING_LANGUAGE_CSS = "css";
    private static final String MARKED_STRING_LANGUAGE_XML = "xml";
    private static final String FILE_EXTENSION_CSS = ".css";
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
        String uriString = textDocument.getUri();
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(uriString);
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
        if (uriString.endsWith(FILE_EXTENSION_CSS)) {
            String cssText = fileTracker.getText(path);
            CSSDocument cssDocument = null;
            if (cssText != null) {
                cssDocument = CSSDocument.parse(new ANTLRStringStream(cssText), new ArrayList<>());
            }
            if (cssDocument != null) {
                cssDocument.setSourcePath(path.toString());
                Hover result = cssHover(cssDocument, currentOffset, 0, projectData);
                if (cancelToken != null) {
                    cancelToken.checkCanceled();
                }
                return result;
            }
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return new Hover(Collections.emptyList(), null);
        }
        boolean isMXML = uriString.endsWith(FILE_EXTENSION_MXML);
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
                // if we're inside an <fx:Script> tag, we want ActionScript hover,
                // so that's why we call isMXMLTagValidForCompletion()
                if (MXMLDataUtils.isMXMLCodeIntelligenceAvailableForTag(offsetTag)) {
                    Hover result = mxmlHover(offsetTag, currentOffset, projectData.project);
                    if (cancelToken != null) {
                        cancelToken.checkCanceled();
                    }
                    return result;
                }
            }
        }
        ISourceLocation offsetSourceLocation = actionScriptProjectManager.getOffsetSourceLocation(path, currentOffset,
                projectData);
        if (offsetSourceLocation instanceof IMXMLStyleNode) {
            // special case for <fx:Style>
            IMXMLStyleNode styleNode = (IMXMLStyleNode) offsetSourceLocation;
            Hover result = cssHover(styleNode, currentOffset, projectData);
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return result;
        }
        if (offsetSourceLocation instanceof VSCodeASDocComment) {
            VSCodeASDocComment docComment = (VSCodeASDocComment) offsetSourceLocation;
            Hover result = asdocHover(docComment, path, position, projectData);
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return result;
        }
        if (!(offsetSourceLocation instanceof IASNode)) {
            // we don't recognize what type this is, so don't try to treat
            // it as an IASNode
            offsetSourceLocation = null;
        }
        IASNode offsetNode = (IASNode) offsetSourceLocation;
        Hover result = actionScriptHover(offsetNode, projectData.project);
        if (cancelToken != null) {
            cancelToken.checkCanceled();
        }
        return result;
    }

    private Hover actionScriptHover(IASNode offsetNode, ILspProject project) {
        IDefinition definition = null;
        if (offsetNode == null) {
            // we couldn't find a node at the specified location
            return new Hover(Collections.emptyList(), null);
        }

        Range sourceRange = null;
        IASNode parentNode = offsetNode.getParent();

        if (definition == null && parentNode instanceof IEventTagNode && offsetNode instanceof IIdentifierNode) {
            IEventTagNode parentEventNode = (IEventTagNode) parentNode;
            IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
            String eventName = parentEventNode.getAttributeValue(IMetaAttributeConstants.NAME_EVENT_NAME);
            String eventType = parentEventNode.getAttributeValue(IMetaAttributeConstants.NAME_EVENT_TYPE);
            if (eventName != null && eventName.equals(identifierNode.getName())) {
                definition = parentEventNode.getDefinition();
            } else if (eventType != null && eventType.equals(identifierNode.getName())) {
                String eventTypeName = identifierNode.getName();
                definition = project.resolveQNameToDefinition(eventTypeName);
            }
        }

        if (definition == null && parentNode instanceof IStyleTagNode && offsetNode instanceof IIdentifierNode) {
            IStyleTagNode parentStyleNode = (IStyleTagNode) parentNode;
            IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
            String styleName = parentStyleNode.getAttributeValue(IMetaAttributeConstants.NAME_STYLE_NAME);
            String styleType = parentStyleNode.getAttributeValue(IMetaAttributeConstants.NAME_STYLE_TYPE);
            String styleArrayType = parentStyleNode.getAttributeValue(IMetaAttributeConstants.NAME_STYLE_ARRAYTYPE);
            if (styleName != null && styleName.equals(identifierNode.getName())) {
                definition = parentStyleNode.getDefinition();
            } else if (styleType != null && styleType.equals(identifierNode.getName())) {
                String styleTypeName = identifierNode.getName();
                definition = project.resolveQNameToDefinition(styleTypeName);
            } else if (styleArrayType != null && styleArrayType.equals(identifierNode.getName())) {
                String styleArrayTypeName = identifierNode.getName();
                definition = project.resolveQNameToDefinition(styleArrayTypeName);
            }
        }

        if (definition == null && parentNode instanceof IInspectableTagNode && offsetNode instanceof IIdentifierNode) {
            IInspectableTagNode parentInspectableNode = (IInspectableTagNode) parentNode;
            IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
            String inspectableArrayType = parentInspectableNode
                    .getAttributeValue(IMetaAttributeConstants.NAME_INSPECTABLE_ARRAYTYPE);
            if (inspectableArrayType != null && inspectableArrayType.equals(identifierNode.getName())) {
                String styleArrayTypeName = identifierNode.getName();
                definition = project.resolveQNameToDefinition(styleArrayTypeName);
            }
        }

        // [ArrayElementType]
        // [HostComponent]
        // [InstanceType]
        if (definition == null && parentNode instanceof ITypedTagNode && offsetNode instanceof IIdentifierNode) {
            IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
            String typeName = identifierNode.getName();
            definition = project.resolveQNameToDefinition(typeName);
        }

        // ILanguageIdentifierNode is typically combined with IIdentifierNode,
        // but we have a different way to get hover information for it.
        if (definition == null && offsetNode instanceof IIdentifierNode
                && !(offsetNode instanceof ILanguageIdentifierNode)) {
            IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
            sourceRange = new Range();
            sourceRange.setStart(new Position(offsetNode.getLine(), offsetNode.getColumn()));
            sourceRange.setEnd(new Position(offsetNode.getEndLine(), offsetNode.getEndColumn()));
            definition = DefinitionUtils.resolveWithExtras(identifierNode, project, sourceRange);
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
                        if (parentNode instanceof IFunctionCallNode) {
                            IFunctionCallNode functionCallNode = (IFunctionCallNode) parentNode;
                            if (IASKeywordConstants.SUPER.equals(functionCallNode.getFunctionName())) {
                                expressionToResolve = (IExpressionNode) languageIdentifierNode;
                                IClassDefinition classDefinition = classNode.getDefinition();
                                if (classDefinition != null) {
                                    IClassDefinition baseClassDefinition = classDefinition
                                            .resolveBaseClass(project);
                                    if (baseClassDefinition != null) {
                                        definition = baseClassDefinition.getConstructor();
                                    }
                                }
                            }
                        }
                        if (definition == null && expressionToResolve == null) {
                            expressionToResolve = classNode.getBaseClassExpressionNode();
                        }
                    }
                    break;
                }
                default:
            }
            if (definition == null && expressionToResolve != null) {
                definition = expressionToResolve.resolve(project);
            }
        }

        if (definition == null) {
            return new Hover(Collections.emptyList(), null);
        }

        if (definition instanceof IClassDefinition && parentNode instanceof IFunctionCallNode) {
            IFunctionCallNode functionCallNode = (IFunctionCallNode) parentNode;
            if (functionCallNode.isNewExpression()) {
                IClassDefinition classDefinition = (IClassDefinition) definition;
                // if it's a class in a new expression, use the constructor
                // definition instead
                IFunctionDefinition constructorDefinition = classDefinition.getConstructor();
                if (constructorDefinition != null) {
                    definition = constructorDefinition;
                }
            }
        }
        return createHoverForDefinition(definition, sourceRange, project);
    }

    private static Hover createHoverForDefinition(IDefinition definition, Range sourceRange, ILspProject project) {
        Hover result = new Hover();
        if (sourceRange != null) {
            result.setRange(sourceRange);
        }
        String detail = DefinitionTextUtils.definitionToDetail(definition, project);
        detail = codeBlock(MARKED_STRING_LANGUAGE_ACTIONSCRIPT, detail);
        String docs = DefinitionDocumentationUtils.getDocumentationForDefinition(definition, true,
                project, true);
        if (docs != null) {
            detail += "\n\n---\n\n" + docs;
        }
        if (definition instanceof IFunctionDefinition) {
            IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
            if (functionDefinition.isConstructor()) {
                IDefinition parentDefinition = functionDefinition.getParent();
                if (parentDefinition != null) {
                    String parentDetail = DefinitionTextUtils.definitionToDetail(parentDefinition, project);
                    parentDetail = codeBlock(MARKED_STRING_LANGUAGE_ACTIONSCRIPT, parentDetail);
                    detail += "\n\n---\n\n" + parentDetail;
                    String parentDocs = DefinitionDocumentationUtils.getDocumentationForDefinition(parentDefinition,
                            true,
                            project, true);
                    if (parentDocs != null) {
                        detail += "\n\n---\n\n" + parentDocs;
                    }
                }
            }
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
            // inside the prefix
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
                project, true);
        if (docs != null) {
            detail += "\n\n---\n\n" + docs;
        }
        result.setContents(new MarkupContent(MarkupKind.MARKDOWN, detail));
        return result;
    }

    private Hover asdocHover(VSCodeASDocComment docComment,
            Path path, Position position, ActionScriptProjectData projectData) {
        if (docComment == null) {
            // we couldn't find a node at the specified location
            return new Hover(Collections.emptyList(), null);
        }
        IDefinition definition = null;
        ICompilationUnit unit = actionScriptProjectManager.getCompilationUnit(path, projectData);
        if (unit == null) {
            // we couldn't find the compilation unit
            return new Hover(Collections.emptyList(), null);
        }
        Range sourceRange = new Range();
        definition = ASDocUtils.resolveDefinitionAtPosition(docComment, unit, position, projectData.project,
                sourceRange);

        if (definition == null) {
            // VSCode may call hover() when there isn't necessarily a
            // definition referenced at the current position.
            return new Hover(Collections.emptyList(), null);
        }

        Hover result = new Hover();
        result.setRange(sourceRange);
        String detail = DefinitionTextUtils.definitionToDetail(definition, projectData.project);
        detail = codeBlock(MARKED_STRING_LANGUAGE_ACTIONSCRIPT, detail);
        String docs = DefinitionDocumentationUtils.getDocumentationForDefinition(definition, true,
                projectData.project, true);
        if (docs != null) {
            detail += "\n\n---\n\n" + docs;
        }
        result.setContents(new MarkupContent(MarkupKind.MARKDOWN, detail));
        return result;
    }

    private Hover cssHover(IMXMLStyleNode styleNode, int currentOffset, ActionScriptProjectData projectData) {
        ICSSDocument cssDocument = styleNode.getCSSDocument(new ArrayList<>());
        if (cssDocument == null) {
            return new Hover(Collections.emptyList(), null);
        }
        return cssHover(cssDocument, currentOffset, styleNode.getContentStart(), projectData);
    }

    private Hover cssHover(ICSSDocument cssDocument, int currentOffset, int contentStart,
            ActionScriptProjectData projectData) {
        IDefinition definition = null;

        if (cssDocument != null) {
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
                        Hover result = new Hover();
                        StringBuilder detailBuilder = new StringBuilder();
                        if (nsPrefix.length() > 0) {
                            detailBuilder.append("@namespace " + nsPrefix + " \"" + cssNamespace.getURI() + "\"");
                        } else {
                            detailBuilder.append("@namespace \"" + cssNamespace.getURI() + "\"");
                        }
                        String detail = codeBlock(MARKED_STRING_LANGUAGE_CSS, detailBuilder.toString());
                        result.setContents(new MarkupContent(MarkupKind.MARKDOWN, detail));
                        return result;
                    }
                }
            }
        }

        if (definition == null) {
            return new Hover(Collections.emptyList(), null);
        }
        return createHoverForDefinition(definition, null, projectData.project);
    }
}