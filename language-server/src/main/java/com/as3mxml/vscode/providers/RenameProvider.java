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
import java.util.Collection;
import java.util.HashMap;
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
import org.apache.royale.compiler.definitions.IPackageDefinition;
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
import org.apache.royale.compiler.tree.as.IDefinitionNode;
import org.apache.royale.compiler.tree.as.IExpressionNode;
import org.apache.royale.compiler.tree.as.IIdentifierNode;
import org.apache.royale.compiler.tree.metadata.IEventTagNode;
import org.apache.royale.compiler.tree.metadata.IInspectableTagNode;
import org.apache.royale.compiler.tree.metadata.IStyleTagNode;
import org.apache.royale.compiler.tree.metadata.ITypedTagNode;
import org.apache.royale.compiler.tree.mxml.IMXMLStyleNode;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.apache.royale.compiler.units.ICompilationUnit.UnitType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameFile;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

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
import com.google.common.io.Files;

public class RenameProvider {
    private static final String FILE_EXTENSION_CSS = ".css";
    private static final String FILE_EXTENSION_MXML = ".mxml";
    private static final String FILE_EXTENSION_SWC = ".swc";

    private ActionScriptProjectManager actionScriptProjectManager;
    private FileTracker fileTracker;

    public RenameProvider(ActionScriptProjectManager actionScriptProjectManager, FileTracker fileTracker) {
        this.actionScriptProjectManager = actionScriptProjectManager;
        this.fileTracker = fileTracker;
    }

    public WorkspaceEdit rename(RenameParams params, CancelChecker cancelToken) {
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
            return new WorkspaceEdit(new HashMap<>());
        }
        ActionScriptProjectData projectData = actionScriptProjectManager.getProjectDataForSourceFile(path);
        if (projectData == null || projectData.project == null
                || projectData.equals(actionScriptProjectManager.getFallbackProjectData())) {
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return new WorkspaceEdit(new HashMap<>());
        }
        ILspProject project = projectData.project;

        IncludeFileData includeFileData = projectData.includedFiles.get(path.toString());
        int currentOffset = LanguageServerCompilerUtils.getOffsetFromPosition(fileTracker.getReader(path), position,
                includeFileData);
        if (currentOffset == -1) {
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return new WorkspaceEdit(new HashMap<>());
        }
        if (uriString.endsWith(FILE_EXTENSION_CSS)) {
            String cssText = fileTracker.getText(path);
            CSSDocument cssDocument = null;
            if (cssText != null) {
                cssDocument = CSSDocument.parse(new ANTLRStringStream(cssText), new ArrayList<>());
            }
            if (cssDocument != null) {
                cssDocument.setSourcePath(path.toString());
                WorkspaceEdit result = cssRename(cssDocument, currentOffset, 0, params.getNewName(), projectData);
                if (cancelToken != null) {
                    cancelToken.checkCanceled();
                }
                return result;
            }
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return new WorkspaceEdit(new HashMap<>());
        }
        boolean isMXML = uriString.endsWith(FILE_EXTENSION_MXML);
        if (isMXML) {
            MXMLData mxmlData = actionScriptProjectManager.getMXMLDataForPath(path, projectData);
            IMXMLTagData offsetTag = MXMLDataUtils.getOffsetMXMLTag(mxmlData, currentOffset);
            if (offsetTag != null) {
                IASNode embeddedNode = actionScriptProjectManager.getEmbeddedActionScriptNodeInMXMLTag(offsetTag, path,
                        currentOffset, projectData);
                if (embeddedNode != null) {
                    WorkspaceEdit result = actionScriptRename(embeddedNode, params.getNewName(), project);
                    if (cancelToken != null) {
                        cancelToken.checkCanceled();
                    }
                    return result;
                }
                // if we're inside an <fx:Script> tag, we want ActionScript rename,
                // so that's why we call isMXMLTagValidForCompletion()
                if (MXMLDataUtils.isMXMLCodeIntelligenceAvailableForTag(offsetTag)) {
                    ICompilationUnit offsetUnit = CompilerProjectUtils.findCompilationUnit(path, project);
                    WorkspaceEdit result = mxmlRename(offsetTag, currentOffset, params.getNewName(), offsetUnit,
                            project);
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
            WorkspaceEdit result = cssRename(styleNode, currentOffset, params.getNewName(), projectData);
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
            return new WorkspaceEdit(new HashMap<>());
        }
        if (!(offsetSourceLocation instanceof IASNode)) {
            // we don't recognize what type this is, so don't try to treat
            // it as an IASNode
            offsetSourceLocation = null;
        }
        IASNode offsetNode = (IASNode) offsetSourceLocation;
        WorkspaceEdit result = actionScriptRename(offsetNode, params.getNewName(), project);
        if (cancelToken != null) {
            cancelToken.checkCanceled();
        }
        return result;
    }

    private WorkspaceEdit actionScriptRename(IASNode offsetNode, String newName, ILspProject project) {
        if (offsetNode == null) {
            // we couldn't find a node at the specified location
            return new WorkspaceEdit(new HashMap<>());
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

        if (definition == null && offsetNode instanceof IDefinitionNode) {
            IDefinitionNode definitionNode = (IDefinitionNode) offsetNode;
            IExpressionNode expressionNode = definitionNode.getNameExpressionNode();
            if (expressionNode != null) {
                definition = expressionNode.resolve(project);
            }
        }

        if (definition == null && offsetNode instanceof IIdentifierNode) {
            IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
            definition = DefinitionUtils.resolveWithExtras(identifierNode, project);
        }

        if (definition == null) {
            // Cannot rename this element
            return null;
        }

        WorkspaceEdit result = renameDefinition(definition, newName, project);
        return result;
    }

    private WorkspaceEdit mxmlRename(IMXMLTagData offsetTag, int currentOffset, String newName,
            ICompilationUnit offsetUnit, ILspProject project) {
        IDefinition definition = MXMLDataUtils.getDefinitionForMXMLNameAtOffset(offsetTag, currentOffset, project);
        if (definition != null) {
            if (MXMLDataUtils.isInsideTagPrefix(offsetTag, currentOffset)) {
                // ignore the tag's prefix
                return new WorkspaceEdit(new HashMap<>());
            }
            return renameDefinition(definition, newName, project);
        }

        // finally, check if we're looking for references to a tag's id
        IMXMLTagAttributeData attributeData = MXMLDataUtils.getMXMLTagAttributeWithValueAtOffset(offsetTag,
                currentOffset);
        if (attributeData == null || !attributeData.getName().equals(IMXMLLanguageConstants.ATTRIBUTE_ID)) {
            // VSCode may call references() when there isn't necessarily a
            // definition referenced at the current position.
            return new WorkspaceEdit(new HashMap<>());
        }
        Collection<IDefinition> definitions = null;
        try {
            definitions = offsetUnit.getFileScopeRequest().get().getExternallyVisibleDefinitions();
        } catch (Exception e) {
            // safe to ignore
        }
        if (definitions == null || definitions.size() == 0) {
            return new WorkspaceEdit(new HashMap<>());
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
            return new WorkspaceEdit(new HashMap<>());
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
            return new WorkspaceEdit(new HashMap<>());
        }
        return renameDefinition(definition, newName, project);
    }

    private WorkspaceEdit cssRename(IMXMLStyleNode styleNode, int currentOffset, String newName,
            ActionScriptProjectData projectData) {
        ICSSDocument cssDocument = styleNode.getCSSDocument(new ArrayList<>());
        if (cssDocument == null) {
            return new WorkspaceEdit(new HashMap<>());
        }
        return cssRename(cssDocument, currentOffset, styleNode.getContentStart(), newName, projectData);
    }

    private WorkspaceEdit cssRename(ICSSDocument cssDocument, int currentOffset, int contentStart, String newName,
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
                                IStyleDefinition[] styleDefinitions = classDefinition
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
            return new WorkspaceEdit(new HashMap<>());
        }
        return renameDefinition(definition, newName, projectData.project);
    }

    private WorkspaceEdit renameDefinition(IDefinition definition, String newName, ILspProject project) {
        if (definition == null) {
            // Cannot rename this element
            return null;
        }
        if (definition instanceof IPackageDefinition) {
            // Cannot rename this element
            return null;
        }
        if (definition instanceof IFunctionDefinition) {
            IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
            if (functionDefinition.isOverride()) {
                IFunctionDefinition overriddenFunction = functionDefinition.resolveOverriddenFunction(project);
                if (overriddenFunction != null) {
                    // if function overrides one from a superclass, resolve the
                    // original function instead.
                    // this ensures that the original function isn't in a SWC,
                    // and makes it easier to find all overrides.
                    definition = overriddenFunction;
                }
            }
        }

        WorkspaceEdit result = new WorkspaceEdit();
        List<Either<TextDocumentEdit, ResourceOperation>> documentChanges = new ArrayList<>();
        result.setDocumentChanges(documentChanges);
        if (definition.getContainingFilePath().endsWith(FILE_EXTENSION_SWC)) {
            // Cannot rename this element
            return null;
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

        Path originalDefinitionFilePath = null;
        Path newDefinitionFilePath = null;
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
            ArrayList<TextEdit> textEdits = new ArrayList<>();
            if (unit.getAbsoluteFilename().endsWith(FILE_EXTENSION_MXML)) {
                IMXMLDataManager mxmlDataManager = project.getWorkspace().getMXMLDataManager();
                MXMLData mxmlData = (MXMLData) mxmlDataManager
                        .get(fileTracker.getFileSpecification(unit.getAbsoluteFilename()));
                IMXMLTagData rootTag = mxmlData.getRootTag();
                if (rootTag != null) {
                    IDefinition rootTagDefinition = null;
                    List<IDefinition> definitions = unit.getDefinitionPromises();
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
                        TextEdit textEdit = new TextEdit();
                        textEdit.setNewText(newName);

                        Range range = LanguageServerCompilerUtils.getRangeFromSourceLocation(otherUnit);
                        if (range == null) {
                            continue;
                        }
                        textEdit.setRange(range);

                        textEdits.add(textEdit);
                    }
                }
            }
            IASNode ast = ASTUtils.getCompilationUnitAST(unit);
            if (ast != null) {
                ArrayList<IIdentifierNode> identifiers = new ArrayList<>();
                ASTUtils.findIdentifiersForDefinition(ast, definition, project, identifiers);
                for (IIdentifierNode identifierNode : identifiers) {
                    TextEdit textEdit = new TextEdit();
                    textEdit.setNewText(newName);

                    Range range = LanguageServerCompilerUtils.getRangeFromSourceLocation(identifierNode);
                    if (range == null) {
                        continue;
                    }
                    textEdit.setRange(range);

                    textEdits.add(textEdit);
                }
            }
            if (textEdits.size() == 0) {
                continue;
            }

            Path textDocumentPath = Paths.get(unit.getAbsoluteFilename());
            if (definitionIsMainDefinitionInCompilationUnit(unit, definition)) {
                originalDefinitionFilePath = textDocumentPath;
                String newBaseName = newName + "."
                        + Files.getFileExtension(originalDefinitionFilePath.toFile().getName());
                newDefinitionFilePath = originalDefinitionFilePath.getParent().resolve(newBaseName);
            }

            // null is supposed to work for the version, but it doesn't seem to
            // be serialized properly. Integer.MAX_VALUE seems to work fine, but
            // it may break in the future...
            VersionedTextDocumentIdentifier versionedIdentifier = new VersionedTextDocumentIdentifier(
                    textDocumentPath.toUri().toString(), Integer.MAX_VALUE);
            TextDocumentEdit textDocumentEdit = new TextDocumentEdit(versionedIdentifier, textEdits);
            documentChanges.add(Either.forLeft(textDocumentEdit));
        }
        if (originalDefinitionFilePath != null && newDefinitionFilePath != null) {
            RenameFile renameFile = new RenameFile();
            renameFile.setOldUri(originalDefinitionFilePath.toUri().toString());
            renameFile.setNewUri(newDefinitionFilePath.toUri().toString());
            documentChanges.add(Either.forRight(renameFile));
        }
        return result;
    }

    private boolean definitionIsMainDefinitionInCompilationUnit(ICompilationUnit unit, IDefinition definition) {
        IASScope[] scopes;
        try {
            scopes = unit.getFileScopeRequest().get().getScopes();
        } catch (Exception e) {
            return false;
        }
        for (IASScope scope : scopes) {
            Collection<IDefinition> localDefs = new ArrayList<>(scope.getAllLocalDefinitions());
            for (IDefinition localDefinition : localDefs) {
                if (localDefinition instanceof IPackageDefinition) {
                    IPackageDefinition packageDefinition = (IPackageDefinition) localDefinition;
                    IASScope packageScope = packageDefinition.getContainedScope();
                    boolean mightBeConstructor = definition instanceof IFunctionDefinition;
                    Collection<IDefinition> packageDefs = new ArrayList<>(packageScope.getAllLocalDefinitions());
                    for (IDefinition localDefinition2 : packageDefs) {
                        if (localDefinition2 == definition) {
                            return true;
                        }
                        if (mightBeConstructor && localDefinition2 instanceof IClassDefinition) {
                            IClassDefinition classDefinition = (IClassDefinition) localDefinition2;
                            if (classDefinition.getConstructor() == definition) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }
}