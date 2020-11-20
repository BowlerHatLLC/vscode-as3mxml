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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.as3mxml.vscode.project.ILspProject;
import com.as3mxml.vscode.project.ActionScriptProjectData;
import com.as3mxml.vscode.utils.ASTUtils;
import com.as3mxml.vscode.utils.CompilationUnitUtils.IncludeFileData;
import com.as3mxml.vscode.utils.DefinitionUtils;
import com.as3mxml.vscode.utils.FileTracker;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;
import com.as3mxml.vscode.utils.MXMLDataUtils;
import com.as3mxml.vscode.utils.ActionScriptProjectManager;
import com.google.common.io.Files;

import org.apache.royale.compiler.common.ISourceLocation;
import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IPackageDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition.FunctionClassification;
import org.apache.royale.compiler.definitions.IVariableDefinition.VariableClassification;
import org.apache.royale.compiler.internal.mxml.MXMLData;
import org.apache.royale.compiler.mxml.IMXMLDataManager;
import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.scopes.IASScope;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IDefinitionNode;
import org.apache.royale.compiler.tree.as.IExpressionNode;
import org.apache.royale.compiler.tree.as.IIdentifierNode;
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

public class RenameProvider {
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
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
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
        boolean isMXML = textDocument.getUri().endsWith(FILE_EXTENSION_MXML);
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
                //if we're inside an <fx:Script> tag, we want ActionScript rename,
                //so that's why we call isMXMLTagValidForCompletion()
                if (MXMLDataUtils.isMXMLCodeIntelligenceAvailableForTag(offsetTag)) {
                    WorkspaceEdit result = mxmlRename(offsetTag, currentOffset, params.getNewName(), project);
                    if (cancelToken != null) {
                        cancelToken.checkCanceled();
                    }
                    return result;
                }
            }
        }
        IASNode offsetNode = actionScriptProjectManager.getOffsetNode(path, currentOffset, projectData);
        WorkspaceEdit result = actionScriptRename(offsetNode, params.getNewName(), project);
        if (cancelToken != null) {
            cancelToken.checkCanceled();
        }
        return result;
    }

    private WorkspaceEdit actionScriptRename(IASNode offsetNode, String newName, ILspProject project) {
        if (offsetNode == null) {
            //we couldn't find a node at the specified location
            return new WorkspaceEdit(new HashMap<>());
        }

        IDefinition definition = null;

        if (offsetNode instanceof IDefinitionNode) {
            IDefinitionNode definitionNode = (IDefinitionNode) offsetNode;
            IExpressionNode expressionNode = definitionNode.getNameExpressionNode();
            definition = expressionNode.resolve(project);
        } else if (offsetNode instanceof IIdentifierNode) {
            IIdentifierNode identifierNode = (IIdentifierNode) offsetNode;
            definition = DefinitionUtils.resolveWithExtras(identifierNode, project);
        }

        if (definition == null) {
            //Cannot rename this element
            return null;
        }

        WorkspaceEdit result = renameDefinition(definition, newName, project);
        return result;
    }

    private WorkspaceEdit mxmlRename(IMXMLTagData offsetTag, int currentOffset, String newName, ILspProject project) {
        IDefinition definition = MXMLDataUtils.getDefinitionForMXMLNameAtOffset(offsetTag, currentOffset, project);
        if (definition != null) {
            if (MXMLDataUtils.isInsideTagPrefix(offsetTag, currentOffset)) {
                //ignore the tag's prefix
                return new WorkspaceEdit(new HashMap<>());
            }
            WorkspaceEdit result = renameDefinition(definition, newName, project);
            return result;
        }

        //Cannot rename this element
        return null;
    }

    private WorkspaceEdit renameDefinition(IDefinition definition, String newName, ILspProject project) {
        if (definition == null) {
            //Cannot rename this element
            return null;
        }
        WorkspaceEdit result = new WorkspaceEdit();
        List<Either<TextDocumentEdit, ResourceOperation>> documentChanges = new ArrayList<>();
        result.setDocumentChanges(documentChanges);
        if (definition.getContainingFilePath().endsWith(FILE_EXTENSION_SWC)) {
            //Cannot rename this element
            return null;
        }
        if (definition instanceof IPackageDefinition) {
            //Cannot rename this element
            return null;
        }
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
                //compiled compilation units won't have problems
                continue;
            }
            if (isLocal && !unit.getAbsoluteFilename().equals(definition.getContainingFilePath())) {
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
                    ArrayList<ISourceLocation> units = new ArrayList<>();
                    MXMLDataUtils.findMXMLUnits(mxmlData.getRootTag(), definition, project, units);
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

            //null is supposed to work for the version, but it doesn't seem to
            //be serialized properly. Integer.MAX_VALUE seems to work fine, but
            //it may break in the future...
            VersionedTextDocumentIdentifier versionedIdentifier = new VersionedTextDocumentIdentifier(
                    textDocumentPath.toUri().toString(), Integer.MAX_VALUE);
            TextDocumentEdit textDocumentEdit = new TextDocumentEdit(versionedIdentifier, textEdits);
            documentChanges.add(Either.forLeft(textDocumentEdit));
        }
        if (newDefinitionFilePath != null) {
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
            for (IDefinition localDefinition : scope.getAllLocalDefinitions()) {
                if (localDefinition instanceof IPackageDefinition) {
                    IPackageDefinition packageDefinition = (IPackageDefinition) localDefinition;
                    IASScope packageScope = packageDefinition.getContainedScope();
                    boolean mightBeConstructor = definition instanceof IFunctionDefinition;
                    for (IDefinition localDefinition2 : packageScope.getAllLocalDefinitions()) {
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