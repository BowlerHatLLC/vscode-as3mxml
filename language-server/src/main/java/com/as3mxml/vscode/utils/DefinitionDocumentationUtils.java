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
package com.as3mxml.vscode.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;

import com.as3mxml.vscode.asdoc.VSCodeASDocComment;

import org.apache.royale.compiler.asdoc.IASDocTag;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IDocumentableDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IParameterDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition;
import org.apache.royale.compiler.workspaces.IWorkspace;
import org.apache.royale.swc.ISWC;
import org.apache.royale.swc.dita.IDITAList;

public class DefinitionDocumentationUtils {
    private static final String ASDOC_TAG_PARAM = "param";
    private static final String ASDOC_TAG_RETURN = "return";
    private static final String ASDOC_TAG_THROWS = "throws";
    private static final String ASDOC_TAG_DEFAULT = "default";
    private static final String SDK_LIBRARY_PATH_SIGNATURE_UNIX = "/frameworks/libs/";
    private static final String SDK_LIBRARY_PATH_SIGNATURE_WINDOWS = "\\frameworks\\libs\\";

    public static String getDocumentationForDefinition(IDefinition definition, boolean useMarkdown,
            IWorkspace workspace, boolean allowDITA) {
        if (!(definition instanceof IDocumentableDefinition)) {
            return null;
        }
        IDocumentableDefinition documentableDefinition = (IDocumentableDefinition) definition;
        VSCodeASDocComment comment = getCommentForDefinition(documentableDefinition, useMarkdown, workspace, allowDITA);
        if (comment == null) {
            return null;
        }
        comment.compile(useMarkdown);
        String description = comment.getDescription();
        if (description == null) {
            return null;
        }
        if (documentableDefinition instanceof IVariableDefinition) {
            if (comment.hasTag(ASDOC_TAG_DEFAULT)) {
                StringBuilder descriptionBuilder = new StringBuilder(description);
                for (IASDocTag defaultTag : comment.getTagsByName(ASDOC_TAG_DEFAULT)) {
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
        if (documentableDefinition instanceof IFunctionDefinition) {
            if (comment.hasTag(ASDOC_TAG_PARAM)) {
                StringBuilder descriptionBuilder = new StringBuilder(description);
                for (IASDocTag paramTag : comment.getTagsByName(ASDOC_TAG_PARAM)) {
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
            if (comment.hasTag(ASDOC_TAG_RETURN)) {
                StringBuilder descriptionBuilder = new StringBuilder(description);
                for (IASDocTag returnTag : comment.getTagsByName(ASDOC_TAG_RETURN)) {
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
        if (documentableDefinition instanceof IVariableDefinition
                || documentableDefinition instanceof IFunctionDefinition) {
            if (comment.hasTag(ASDOC_TAG_THROWS)) {
                StringBuilder descriptionBuilder = new StringBuilder(description);
                for (IASDocTag throwsTag : comment.getTagsByName(ASDOC_TAG_THROWS)) {
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
        return description;
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
        Collection<IASDocTag> paramTags = comment.getTagsByName(ASDOC_TAG_PARAM);
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
        if (allowDITA && comment == null && definitionFilePath != null && definitionFilePath.endsWith(".swc")) {
            IDITAList ditaList = null;
            File swcFile = new File(definitionFilePath);
            if (swcFile.exists()) {
                // first try to find the asdoc in the .swc
                ISWC swc = workspace.getSWCManager().get(swcFile);
                String fileName = swcFile.getName();
                ditaList = swc.getDITAList();

                // next, if it's a SDK/framework liberary, check for an
                // associated resource bundle .swc
                if (ditaList == null && (definitionFilePath.contains(SDK_LIBRARY_PATH_SIGNATURE_UNIX)
                        || definitionFilePath.contains(SDK_LIBRARY_PATH_SIGNATURE_WINDOWS))) {
                    String rbName = fileName.substring(0, fileName.length() - 4) + "_rb.swc";
                    File frameworksDir = swcFile.getParentFile();
                    while (!frameworksDir.getName().equals("frameworks")) {
                        frameworksDir = frameworksDir.getParentFile();
                    }
                    File rbSwcFile = new File(frameworksDir, "locale/en_US/" + rbName);
                    if (rbSwcFile.exists()) {
                        ISWC rbSwc = workspace.getSWCManager().get(rbSwcFile);
                        ditaList = rbSwc.getDITAList();
                    }
                }
                // finally, fall back to the bundled documentation for
                // playerglobal or airglobal, if the filename matches
                if (ditaList == null && (fileName.contains("playerglobal") || fileName.contains("airglobal"))) {
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
}