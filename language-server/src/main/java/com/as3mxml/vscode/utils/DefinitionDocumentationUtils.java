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
package com.as3mxml.vscode.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Iterator;

import org.apache.royale.compiler.asdoc.IASDocTag;
import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IClassDefinition.IClassIterator;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IDocumentableDefinition;
import org.apache.royale.compiler.definitions.IEventDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IInterfaceDefinition;
import org.apache.royale.compiler.definitions.IParameterDefinition;
import org.apache.royale.compiler.definitions.ITypeDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition;
import org.apache.royale.compiler.projects.ICompilerProject;
import org.apache.royale.compiler.scopes.IASScope;
import org.apache.royale.compiler.scopes.IDefinitionSet;
import org.apache.royale.compiler.workspaces.IWorkspace;
import org.apache.royale.swc.ISWC;
import org.apache.royale.swc.dita.IDITAList;

import com.as3mxml.vscode.asdoc.IASDocTagConstants;
import com.as3mxml.vscode.asdoc.VSCodeASDocComment;

public class DefinitionDocumentationUtils {
    private static final String SDK_LIBRARY_PATH_SIGNATURE_UNIX = "/frameworks/libs/";
    private static final String SDK_LIBRARY_PATH_SIGNATURE_WINDOWS = "\\frameworks\\libs\\";
    private static final String LOCALE_EN_US = "locale/en_US/";
    private static final String PLAYERGLOBAL = "playerglobal";
    private static final String AIRGLOBAL = "airglobal";
    private static final String RB_SWC_SUFFIX = "_rb.swc";
    private static final String FRAMEWORKS = "frameworks";
    private static final String FILE_EXTENSION_SWC = ".swc";

    public static String getDocumentationForDefinition(IDefinition definition, boolean useMarkdown,
            ICompilerProject project, boolean allowDITA) {
        if (!(definition instanceof IDocumentableDefinition)) {
            return null;
        }
        IDocumentableDefinition documentableDefinition = (IDocumentableDefinition) definition;
        VSCodeASDocComment comment = getCommentForDefinition(documentableDefinition, useMarkdown,
                project.getWorkspace(), allowDITA);
        if (comment == null) {
            return null;
        }
        comment.compile(useMarkdown);
        if (documentableDefinition.isOverride() && comment.hasTag(IASDocTagConstants.INHERIT_DOC)) {
            return getDocumentationForInheritDoc(documentableDefinition, useMarkdown, project, allowDITA);
        }
        return getDocumentationForDefinitionInternal(documentableDefinition, comment, useMarkdown, project, allowDITA);
    }

    public static String getDocumentationForParameter(IParameterDefinition definition, boolean useMarkdown,
            IWorkspace workspace) {
        IDefinition parentDefinition = definition.getParent();
        if (!(parentDefinition instanceof IFunctionDefinition)) {
            return null;
        }
        IFunctionDefinition functionDefinition = (IFunctionDefinition) parentDefinition;
        VSCodeASDocComment comment = getCommentForDefinition(functionDefinition, useMarkdown, workspace, true);
        if (comment == null) {
            return null;
        }
        comment.compile(useMarkdown);
        Collection<IASDocTag> paramTags = comment.getTagsByName(IASDocTagConstants.PARAM);
        if (paramTags == null) {
            return null;
        }
        String paramName = definition.getBaseName();
        for (IASDocTag paramTag : paramTags) {
            String description = paramTag.getDescription();
            if (description.startsWith(paramName + " ")) {
                return description.substring(paramName.length() + 1);
            }
        }
        return null;
    }

    private static VSCodeASDocComment getCommentForDefinition(IDocumentableDefinition documentableDefinition,
            boolean useMarkdown, IWorkspace workspace, boolean allowDITA) {
        VSCodeASDocComment comment = (VSCodeASDocComment) documentableDefinition.getExplicitSourceComment();
        String definitionFilePath = documentableDefinition.getContainingFilePath();
        if (allowDITA && comment == null && definitionFilePath != null
                && definitionFilePath.endsWith(FILE_EXTENSION_SWC)) {
            IDITAList ditaList = null;
            File swcFile = new File(definitionFilePath);
            if (swcFile.exists()) {
                // first try to find the asdoc in the .swc
                ISWC swc = workspace.getSWCManager().get(swcFile);
                String fileName = swcFile.getName();
                ditaList = swc.getDITAList();

                // next, if it's a SDK/framework library, check for an
                // associated resource bundle .swc
                if (ditaList == null && (definitionFilePath.contains(SDK_LIBRARY_PATH_SIGNATURE_UNIX)
                        || definitionFilePath.contains(SDK_LIBRARY_PATH_SIGNATURE_WINDOWS))) {
                    String swcBaseName = fileName.substring(0, fileName.length() - 4);
                    String rbName = swcBaseName + RB_SWC_SUFFIX;
                    File frameworksDir = swcFile.getParentFile();
                    while (!frameworksDir.getName().equals(FRAMEWORKS)) {
                        frameworksDir = frameworksDir.getParentFile();
                    }
                    File rbSwcFile = new File(frameworksDir, LOCALE_EN_US + rbName);
                    if (rbSwcFile.exists()) {
                        ISWC rbSwc = workspace.getSWCManager().get(rbSwcFile);
                        ditaList = rbSwc.getDITAList();
                    } else if (fileName.contains(AIRGLOBAL)) {
                        // airglobal_rb.swc may not exist, but
                        // playerglobal_rb.swc may be used as a fallback
                        rbName = PLAYERGLOBAL + RB_SWC_SUFFIX;
                        rbSwcFile = new File(frameworksDir, LOCALE_EN_US + rbName);
                        if (rbSwcFile.exists()) {
                            ISWC rbSwc = workspace.getSWCManager().get(rbSwcFile);
                            ditaList = rbSwc.getDITAList();
                        }
                    }
                }
                // finally, fall back to the bundled documentation for
                // playerglobal or airglobal, if the filename matches
                if (ditaList == null && (fileName.contains(PLAYERGLOBAL) || fileName.contains(AIRGLOBAL))) {
                    try {
                        File jarPath = new File(DefinitionDocumentationUtils.class.getProtectionDomain().getCodeSource()
                                .getLocation().toURI());
                        File packageDitaFile = new File(jarPath.getParentFile().getParentFile(),
                                "playerglobal_docs/packages.dita");
                        FileInputStream packageDitaStream = new FileInputStream(packageDitaFile);
                        ditaList = workspace.getASDocDelegate().getPackageDitaParser().parse(definitionFilePath,
                                packageDitaStream);
                        try {
                            packageDitaStream.close();
                        } catch (IOException e) {
                        }
                    } catch (URISyntaxException e) {
                        return null;
                    } catch (FileNotFoundException e) {
                        return null;
                    }
                }
            }
            if (ditaList == null) {
                return null;
            }
            try {
                comment = (VSCodeASDocComment) ditaList.getComment(documentableDefinition);
            } catch (Exception e) {
                return null;
            }
        }
        return comment;
    }

    private static String getDocumentationForInheritDocType(ITypeDefinition typeDefinition,
            String nameToFind, boolean useMarkdown, ICompilerProject project, boolean allowDITA) {

        IASScope interfaceScope = typeDefinition.getContainedScope();
        if (interfaceScope == null) {
            return null;
        }
        IDefinitionSet defSet = interfaceScope.getLocalDefinitionSetByName(nameToFind);
        if (defSet == null) {
            return null;
        }
        IDefinition memberDef = defSet.getDefinition(0);
        if (memberDef instanceof IDocumentableDefinition) {
            IDocumentableDefinition documentableMemberDef = (IDocumentableDefinition) memberDef;
            VSCodeASDocComment comment = getCommentForDefinition(documentableMemberDef, useMarkdown,
                    project.getWorkspace(), allowDITA);
            if (comment == null) {
                return null;
            }
            comment.compile(useMarkdown);
            String description = comment.getDescription();
            if (description == null || description.length() == 0) {
                return null;
            }
            return getDocumentationForDefinitionInternal(typeDefinition, comment, useMarkdown, project, allowDITA);
        }
        return null;
    }

    private static String getDocumentationForInheritDoc(IDocumentableDefinition definition, boolean useMarkdown,
            ICompilerProject project, boolean allowDITA) {
        String nameToFind = definition.getBaseName();
        IDefinition parentDefinition = definition.getParent();
        if (!(parentDefinition instanceof IClassDefinition)) {
            return null;
        }
        IClassDefinition parentClass = (IClassDefinition) parentDefinition;
        Iterator<IInterfaceDefinition> interfaceIterator = parentClass.interfaceIterator(project);
        while (interfaceIterator.hasNext()) {
            IInterfaceDefinition interfaceDefinition = interfaceIterator.next();
            String interfaceResult = getDocumentationForInheritDocType(interfaceDefinition, nameToFind,
                    useMarkdown, project, allowDITA);
            if (interfaceResult != null) {
                return interfaceResult;
            }
        }
        IClassIterator classIterator = parentClass.classIterator(project, true);
        while (classIterator.hasNext()) {
            IClassDefinition currentClass = classIterator.next();
            String classResult = getDocumentationForInheritDocType(currentClass, nameToFind, useMarkdown, project,
                    allowDITA);
            if (classResult != null) {
                return classResult;
            }
        }
        return null;
    }

    private static String getDocumentationForDefinitionInternal(IDefinition definition, VSCodeASDocComment comment,
            boolean useMarkdown, ICompilerProject project, boolean allowDITA) {
        String description = comment.getDescription();
        if (description == null) {
            return null;
        }
        if (definition instanceof IVariableDefinition) {
            if (comment.hasTag(IASDocTagConstants.DEFAULT)) {
                StringBuilder descriptionBuilder = new StringBuilder(description);
                for (IASDocTag defaultTag : comment.getTagsByName(IASDocTagConstants.DEFAULT)) {
                    descriptionBuilder.append("\n\n");
                    if (useMarkdown) {
                        descriptionBuilder.append("_");
                    }
                    descriptionBuilder.append("@default");
                    if (useMarkdown) {
                        descriptionBuilder.append("_");
                    }
                    descriptionBuilder.append(" ");
                    if (useMarkdown) {
                        descriptionBuilder.append("`");
                    }
                    descriptionBuilder.append(defaultTag.getDescription());
                    if (useMarkdown) {
                        descriptionBuilder.append("`");
                    }
                }
                description = descriptionBuilder.toString();
            }
        }
        if (definition instanceof IFunctionDefinition) {
            if (comment.hasTag(IASDocTagConstants.PARAM)) {
                StringBuilder descriptionBuilder = new StringBuilder(description);
                for (IASDocTag paramTag : comment.getTagsByName(IASDocTagConstants.PARAM)) {
                    descriptionBuilder.append("\n\n");
                    if (useMarkdown) {
                        descriptionBuilder.append("_");
                    }
                    descriptionBuilder.append("@param");
                    if (useMarkdown) {
                        descriptionBuilder.append("_");
                    }
                    descriptionBuilder.append(" ");
                    String paramTagDescription = paramTag.getDescription();
                    String paramName = paramTagDescription;
                    String paramDescription = null;
                    int spaceIndex = paramName.indexOf(' ');
                    int tabIndex = paramName.indexOf('\t');
                    int delimiterIndex = spaceIndex;
                    if (tabIndex != -1 && (delimiterIndex == -1 || tabIndex < delimiterIndex)) {
                        delimiterIndex = tabIndex;
                    }
                    if (delimiterIndex > 0) {
                        paramDescription = paramName.substring(delimiterIndex + 1);
                        paramName = paramName.substring(0, delimiterIndex);
                    }
                    if (useMarkdown) {
                        descriptionBuilder.append("`");
                    }
                    descriptionBuilder.append(paramName);
                    if (useMarkdown) {
                        descriptionBuilder.append("`");
                    }
                    if (paramDescription != null) {
                        descriptionBuilder.append(" ");
                        descriptionBuilder.append(paramDescription);
                    }
                }
                description = descriptionBuilder.toString();
            }
            if (comment.hasTag(IASDocTagConstants.RETURN)) {
                StringBuilder descriptionBuilder = new StringBuilder(description);
                for (IASDocTag returnTag : comment.getTagsByName(IASDocTagConstants.RETURN)) {
                    descriptionBuilder.append("\n\n");
                    if (useMarkdown) {
                        descriptionBuilder.append("_");
                    }
                    descriptionBuilder.append("@return");
                    if (useMarkdown) {
                        descriptionBuilder.append("_");
                    }
                    descriptionBuilder.append(" ");
                    descriptionBuilder.append(returnTag.getDescription());
                }
                description = descriptionBuilder.toString();
            }
        }
        if (definition instanceof IVariableDefinition
                || definition instanceof IFunctionDefinition) {
            if (comment.hasTag(IASDocTagConstants.THROWS)) {
                StringBuilder descriptionBuilder = new StringBuilder(description);
                for (IASDocTag throwsTag : comment.getTagsByName(IASDocTagConstants.THROWS)) {
                    descriptionBuilder.append("\n\n");
                    if (useMarkdown) {
                        descriptionBuilder.append("_");
                    }
                    descriptionBuilder.append("@throws");
                    if (useMarkdown) {
                        descriptionBuilder.append("_");
                    }
                    descriptionBuilder.append(" ");
                    String throwsTagDescription = throwsTag.getDescription();
                    String throwsName = throwsTagDescription;
                    String throwsDescription = null;
                    int spaceIndex = throwsName.indexOf(' ');
                    int tabIndex = throwsName.indexOf('\t');
                    int delimiterIndex = spaceIndex;
                    if (tabIndex != -1 && (delimiterIndex == -1 || tabIndex < delimiterIndex)) {
                        delimiterIndex = tabIndex;
                    }
                    if (delimiterIndex > 0) {
                        throwsDescription = throwsName.substring(delimiterIndex + 1);
                        throwsName = throwsName.substring(0, delimiterIndex);
                    }
                    if (useMarkdown) {
                        descriptionBuilder.append("`");
                    }
                    descriptionBuilder.append(throwsName);
                    if (useMarkdown) {
                        descriptionBuilder.append("`");
                    }
                    if (throwsDescription != null) {
                        descriptionBuilder.append(" ");
                        descriptionBuilder.append(throwsDescription);
                    }
                }
                description = descriptionBuilder.toString();
            }
        }
        if (definition instanceof IEventDefinition) {
            if (comment.hasTag(IASDocTagConstants.EVENT_TYPE)) {
                StringBuilder descriptionBuilder = new StringBuilder(description);
                for (IASDocTag eventTypeTag : comment.getTagsByName(IASDocTagConstants.EVENT_TYPE)) {
                    String eventTypeDescription = eventTypeTag.getDescription();
                    if (eventTypeDescription != null) {
                        descriptionBuilder.append("\n\n");
                        if (useMarkdown) {
                            descriptionBuilder.append("_");
                        }
                        descriptionBuilder.append("@eventType");
                        if (useMarkdown) {
                            descriptionBuilder.append("_");
                        }
                        descriptionBuilder.append(" ");
                        if (useMarkdown) {
                            descriptionBuilder.append("`");
                        }
                        descriptionBuilder.append(eventTypeDescription);
                        if (useMarkdown) {
                            descriptionBuilder.append("`");
                        }
                    }
                }
                description = descriptionBuilder.toString();
            }
        }
        return description;
    }
}