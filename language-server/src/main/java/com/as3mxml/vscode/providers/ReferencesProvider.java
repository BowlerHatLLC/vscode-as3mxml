/*
Copyright 2016-2024 Bowler Hat LLC

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

import org.apache.royale.compiler.common.ISourceLocation;
import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition.FunctionClassification;
import org.apache.royale.compiler.definitions.IVariableDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition.VariableClassification;
import org.apache.royale.compiler.internal.mxml.MXMLData;
import org.apache.royale.compiler.internal.scopes.ASProjectScope.DefinitionPromise;
import org.apache.royale.compiler.mxml.IMXMLDataManager;
import org.apache.royale.compiler.mxml.IMXMLLanguageConstants;
import org.apache.royale.compiler.mxml.IMXMLTagAttributeData;
import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.scopes.IASScope;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IIdentifierNode;
import org.apache.royale.compiler.tree.mxml.IMXMLStyleNode;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.apache.royale.compiler.units.ICompilationUnit.UnitType;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

import com.as3mxml.vscode.project.ActionScriptProjectData;
import com.as3mxml.vscode.project.ILspProject;
import com.as3mxml.vscode.utils.ASTUtils;
import com.as3mxml.vscode.utils.ActionScriptProjectManager;
import com.as3mxml.vscode.utils.CompilationUnitUtils.IncludeFileData;
import com.as3mxml.vscode.utils.CompilerProjectUtils;
import com.as3mxml.vscode.utils.DefinitionUtils;
import com.as3mxml.vscode.utils.FileTracker;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;
import com.as3mxml.vscode.utils.MXMLDataUtils;

public class ReferencesProvider {
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
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
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
        boolean isMXML = textDocument.getUri().endsWith(FILE_EXTENSION_MXML);
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
                if (MXMLDataUtils.isMXMLCodeIntelligenceAvailableForTag(offsetTag)) {
                    ICompilationUnit offsetUnit = CompilerProjectUtils.findCompilationUnit(path, project);
                    List<? extends Location> result = mxmlReferences(offsetTag, currentOffset, offsetUnit, project);
                    if (cancelToken != null) {
                        cancelToken.checkCanceled();
                    }
                    return result;
                }
            }
        }
        IASNode offsetNode = actionScriptProjectManager.getOffsetNode(path, currentOffset, projectData);
        if (offsetNode instanceof IMXMLStyleNode) {
            // special case for <fx:Style>
            return Collections.emptyList();
        }
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

        if (offsetNode instanceof IIdentifierNode) {
            IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
            IDefinition resolved = DefinitionUtils.resolveWithExtras(identifierNode, project);
            if (resolved == null) {
                return Collections.emptyList();
            }
            List<Location> result = new ArrayList<>();
            referencesForDefinition(resolved, project, result);
            return result;
        }

        // VSCode may call references() when there isn't necessarily a
        // definition referenced at the current position.
        return Collections.emptyList();
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