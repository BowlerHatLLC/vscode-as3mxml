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
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.antlr.runtime.ANTLRStringStream;
import org.apache.royale.compiler.common.ISourceLocation;
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
import org.apache.royale.compiler.definitions.IFunctionDefinition.FunctionClassification;
import org.apache.royale.compiler.definitions.IStyleDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition.VariableClassification;
import org.apache.royale.compiler.internal.css.CSSDocument;
import org.apache.royale.compiler.internal.mxml.MXMLData;
import org.apache.royale.compiler.internal.mxml.MXMLDialect;
import org.apache.royale.compiler.internal.scopes.ASProjectScope.DefinitionPromise;
import org.apache.royale.compiler.mxml.IMXMLDataManager;
import org.apache.royale.compiler.mxml.IMXMLLanguageConstants;
import org.apache.royale.compiler.mxml.IMXMLTagAttributeData;
import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.scopes.IASScope;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IIdentifierNode;
import org.apache.royale.compiler.tree.metadata.IEventTagNode;
import org.apache.royale.compiler.tree.metadata.IInspectableTagNode;
import org.apache.royale.compiler.tree.metadata.IStyleTagNode;
import org.apache.royale.compiler.tree.metadata.ITypedTagNode;
import org.apache.royale.compiler.tree.mxml.IMXMLStyleNode;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.apache.royale.compiler.units.ICompilationUnit.UnitType;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

import com.as3mxml.vscode.asdoc.VSCodeASDocComment;
import com.as3mxml.vscode.project.ActionScriptProjectData;
import com.as3mxml.vscode.project.ILspProject;
import com.as3mxml.vscode.utils.ASTUtils;
import com.as3mxml.vscode.utils.ActionScriptProjectManager;
import com.as3mxml.vscode.utils.CSSDocumentUtils;
import com.as3mxml.vscode.utils.CompilationUnitUtils.IncludeFileData;
import com.as3mxml.vscode.utils.CompilerProjectUtils;
import com.as3mxml.vscode.utils.DefinitionUtils;
import com.as3mxml.vscode.utils.FileTracker;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;
import com.as3mxml.vscode.utils.MXMLDataUtils;
import com.google.common.collect.ImmutableList;

public class ReferencesProvider {
    private static final String FILE_EXTENSION_CSS = ".css";
    private static final String FILE_EXTENSION_MXML = ".mxml";

    private ActionScriptProjectManager actionScriptProjectManager;
    private FileTracker fileTracker;

    public ReferencesProvider(ActionScriptProjectManager actionScriptProjectManager, FileTracker fileTracker) {
        this.actionScriptProjectManager = actionScriptProjectManager;
        this.fileTracker = fileTracker;
    }

    public List<? extends Location> references(ReferenceParams params, CancelChecker cancelToken) {
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
            return Collections.emptyList();
        }
        ActionScriptProjectData projectData = actionScriptProjectManager.getProjectDataForSourceFile(path);
        if (projectData == null || projectData.project == null
                || projectData.equals(actionScriptProjectManager.getFallbackProjectData())) {
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return Collections.emptyList();
        }
        ILspProject project = projectData.project;

        IncludeFileData includeFileData = projectData.includedFiles.get(path.toString());
        int currentOffset = LanguageServerCompilerUtils.getOffsetFromPosition(fileTracker.getReader(path), position,
                includeFileData);
        if (currentOffset == -1) {
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return Collections.emptyList();
        }
        if (uriString.endsWith(FILE_EXTENSION_CSS)) {
            String cssText = fileTracker.getText(path);
            CSSDocument cssDocument = null;
            if (cssText != null) {
                cssDocument = CSSDocument.parse(new ANTLRStringStream(cssText), new ArrayList<>());
            }
            if (cssDocument != null) {
                cssDocument.setSourcePath(path.toString());
                List<? extends Location> result = cssReferences(cssDocument, currentOffset, 0, projectData);
                if (cancelToken != null) {
                    cancelToken.checkCanceled();
                }
                return result;
            }
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return Collections.emptyList();
        }
        boolean isMXML = uriString.endsWith(FILE_EXTENSION_MXML);
        if (isMXML) {
            MXMLData mxmlData = actionScriptProjectManager.getMXMLDataForPath(path, projectData);
            IMXMLTagData offsetTag = MXMLDataUtils.getOffsetMXMLTag(mxmlData, currentOffset);
            if (offsetTag != null) {
                IASNode embeddedNode = actionScriptProjectManager.getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path,
                        currentOffset, projectData);
                if (embeddedNode != null) {
                    List<? extends Location> result = actionScriptReferences(embeddedNode, project);
                    if (cancelToken != null) {
                        cancelToken.checkCanceled();
                    }
                    return result;
                }
                // if we're inside an <fx:Script> tag, we want ActionScript lookup,
                // so that's why we call isMXMLTagValidForCompletion()
                if (MXMLDataUtils.isMXMLCodeIntelligenceAvailableForTag(offsetTag, currentOffset)) {
                    ICompilationUnit offsetUnit = CompilerProjectUtils.findCompilationUnit(path, project);
                    List<? extends Location> result = mxmlReferences(offsetTag, currentOffset, offsetUnit, project);
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
            List<? extends Location> result = cssReferences(styleNode, currentOffset, projectData);
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return result;
        }
        if (offsetSourceLocation instanceof VSCodeASDocComment) {
            // special case for doc comments
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return Collections.emptyList();
        }
        if (!(offsetSourceLocation instanceof IASNode)) {
            // we don't recognize what type this is, so don't try to treat
            // it as an IASNode
            offsetSourceLocation = null;
        }
        IASNode offsetNode = (IASNode) offsetSourceLocation;
        List<? extends Location> result = actionScriptReferences(offsetNode, project);
        if (cancelToken != null) {
            cancelToken.checkCanceled();
        }
        return result;
    }

    private List<? extends Location> actionScriptReferences(IASNode offsetNode, ILspProject project) {
        if (offsetNode == null) {
            // we couldn't find a node at the specified location
            return Collections.emptyList();
        }

        IDefinition definition = null;
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

        if (definition == null && offsetNode instanceof IIdentifierNode) {
            IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
            definition = DefinitionUtils.resolveWithExtras(identifierNode, project);
        }

        if (definition == null) {
            // VSCode may call references() when there isn't necessarily a
            // definition referenced at the current position.
            return Collections.emptyList();
        }
        List<Location> result = new ArrayList<>();
        referencesForDefinition(definition, project, result);
        return result;
    }

    private List<? extends Location> mxmlReferences(IMXMLTagData offsetTag, int currentOffset,
            ICompilationUnit offsetUnit, ILspProject project) {
        IDefinition definition = MXMLDataUtils.getDefinitionForMXMLNameAtOffset(offsetTag, currentOffset, project);
        if (definition != null) {
            if (MXMLDataUtils.isInsideTagPrefix(offsetTag, currentOffset)) {
                // ignore the tag's prefix
                return Collections.emptyList();
            }
            ArrayList<Location> result = new ArrayList<>();
            referencesForDefinition(definition, project, result);
            return result;
        }

        // finally, check if we're looking for references to a tag's id
        IMXMLTagAttributeData attributeData = MXMLDataUtils.getMXMLTagAttributeWithValueAtOffset(offsetTag,
                currentOffset);
        if (attributeData == null || !attributeData.getName().equals(IMXMLLanguageConstants.ATTRIBUTE_ID)) {
            // VSCode may call references() when there isn't necessarily a
            // definition referenced at the current position.
            return Collections.emptyList();
        }
        Collection<IDefinition> definitions = null;
        try {
            definitions = offsetUnit.getFileScopeRequest().get().getExternallyVisibleDefinitions();
        } catch (Exception e) {
            // safe to ignore
        }
        if (definitions == null || definitions.size() == 0) {
            return Collections.emptyList();
        }
        IClassDefinition classDefinition = null;
        for (IDefinition currentDefinition : definitions) {
            if (currentDefinition instanceof IClassDefinition) {
                classDefinition = (IClassDefinition) currentDefinition;
                break;
            }
        }
        if (classDefinition == null) {
            // this probably shouldn't happen, but check just to be safe
            return Collections.emptyList();
        }
        IASScope scope = classDefinition.getContainedScope();
        Collection<IDefinition> localDefs = new ArrayList<>(scope.getAllLocalDefinitions());
        for (IDefinition currentDefinition : localDefs) {
            if (currentDefinition.getBaseName().equals(attributeData.getRawValue())) {
                definition = currentDefinition;
                break;
            }
        }
        if (definition == null) {
            // VSCode may call references() when there isn't necessarily a
            // definition referenced at the current position.
            return Collections.emptyList();
        }
        ArrayList<Location> result = new ArrayList<>();
        referencesForDefinition(definition, project, result);
        return result;
    }

    private List<? extends Location> cssReferences(IMXMLStyleNode styleNode, int currentOffset,
            ActionScriptProjectData projectData) {
        ICSSDocument cssDocument = styleNode.getCSSDocument(new ArrayList<>());
        if (cssDocument == null) {
            return Collections.emptyList();
        }
        return cssReferences(cssDocument, currentOffset, styleNode.getContentStart(), projectData);
    }

    private List<? extends Location> cssReferences(ICSSDocument cssDocument, int currentOffset, int contentStart,
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
                int elementNameStart = conditionsStart - cssSelector.getElementName().length();
                if (currentOffset >= elementNameStart && currentOffset < conditionsStart) {
                    XMLName xmlName = new XMLName(cssNamespace.getURI(), cssSelector.getElementName());
                    definition = projectData.project.resolveXMLNameToDefinition(xmlName, MXMLDialect.DEFAULT);
                }
            }
        }

        if (definition == null) {
            // VSCode may call references() when there isn't necessarily a
            // definition referenced at the current position.
            return Collections.emptyList();
        }
        ArrayList<Location> result = new ArrayList<>();
        referencesForDefinition(definition, projectData.project, result);
        return result;
    }

    private void referencesForDefinition(IDefinition definition, ILspProject project, List<Location> result) {
        if (definition instanceof IFunctionDefinition) {
            IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
            if (functionDefinition.isOverride()) {
                IFunctionDefinition overriddenFunction = functionDefinition.resolveOverriddenFunction(project);
                if (overriddenFunction != null) {
                    // if function overrides one from a superclass, resolve the
                    // original function instead.
                    // this makes it easier to find all overrides.
                    definition = overriddenFunction;
                }
            }
        }

        boolean isPrivate = definition.isPrivate();
        boolean isLocal = false;
        if (definition instanceof IVariableDefinition) {
            IVariableDefinition variableDef = (IVariableDefinition) definition;
            isLocal = VariableClassification.LOCAL.equals(variableDef.getVariableClassification());
        } else if (definition instanceof IFunctionDefinition) {
            IFunctionDefinition functionDef = (IFunctionDefinition) definition;
            isLocal = FunctionClassification.LOCAL.equals(functionDef.getFunctionClassification());
        }

        for (ICompilationUnit unit : project.getCompilationUnits()) {
            if (unit == null) {
                continue;
            }
            UnitType unitType = unit.getCompilationUnitType();
            if (!UnitType.AS_UNIT.equals(unitType) && !UnitType.MXML_UNIT.equals(unitType)) {
                // compiled compilation units won't have problems
                continue;
            }
            if ((isLocal || isPrivate) && !unit.getAbsoluteFilename().equals(definition.getContainingFilePath())) {
                // no need to check this file
                continue;
            }
            referencesForDefinitionInCompilationUnit(definition, unit, project, result);
        }
    }

    private void referencesForDefinitionInCompilationUnit(IDefinition definition, ICompilationUnit compilationUnit,
            ILspProject project, List<Location> result) {
        if (compilationUnit.getAbsoluteFilename().endsWith(FILE_EXTENSION_MXML)) {
            IMXMLDataManager mxmlDataManager = project.getWorkspace().getMXMLDataManager();
            MXMLData mxmlData = (MXMLData) mxmlDataManager
                    .get(fileTracker.getFileSpecification(compilationUnit.getAbsoluteFilename()));
            IMXMLTagData rootTag = mxmlData.getRootTag();
            if (rootTag != null) {
                IDefinition rootTagDefinition = null;
                List<IDefinition> definitions = compilationUnit.getDefinitionPromises();
                if (definitions.size() > 0) {
                    rootTagDefinition = definitions.get(0);
                    if (rootTagDefinition instanceof DefinitionPromise) {
                        DefinitionPromise definitionPromise = (DefinitionPromise) rootTagDefinition;
                        rootTagDefinition = definitionPromise.getActualDefinition();
                    }
                }
                boolean includeIDs = definition instanceof IVariableDefinition && rootTagDefinition != null
                        && rootTagDefinition.equals(definition.getParent());
                ArrayList<ISourceLocation> units = new ArrayList<>();
                MXMLDataUtils.findMXMLUnits(mxmlData.getRootTag(), definition, includeIDs, project, units);
                for (ISourceLocation otherUnit : units) {
                    Location location = LanguageServerCompilerUtils.getLocationFromSourceLocation(otherUnit);
                    if (location == null) {
                        continue;
                    }
                    result.add(location);
                }
            }
        }
        IASNode ast = ASTUtils.getCompilationUnitAST(compilationUnit);
        if (ast == null) {
            return;
        }
        ArrayList<IIdentifierNode> identifiers = new ArrayList<>();
        ASTUtils.findIdentifiersForDefinition(ast, definition, project, identifiers);
        for (IIdentifierNode otherNode : identifiers) {
            Location location = LanguageServerCompilerUtils.getLocationFromSourceLocation(otherNode);
            if (location == null) {
                continue;
            }
            result.add(location);
        }
    }
}