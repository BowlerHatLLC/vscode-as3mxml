/*
Copyright 2016-2018 Bowler Hat LLC

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
package com.nextgenactionscript.vscode.utils;

import java.util.Collection;

import com.nextgenactionscript.vscode.asdoc.VSCodeASDocComment;

import org.apache.royale.compiler.asdoc.IASDocTag;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IDocumentableDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IParameterDefinition;

public class DefinitionDocumentationUtils
{
    private static final String ASDOC_TAG_PARAM = "param";

	public static String getDocumentationForDefinition(IDefinition definition, boolean useMarkdown)
    {
        if (!(definition instanceof IDocumentableDefinition))
        {
            return null;
        }
        IDocumentableDefinition documentableDefinition = (IDocumentableDefinition) definition;
        VSCodeASDocComment comment = (VSCodeASDocComment) documentableDefinition.getExplicitSourceComment();
        if (comment == null)
        {
            return null;
        }
        comment.compile(useMarkdown);
        String description = comment.getDescription();
        if (description == null)
        {
            return null;
        }
        return description;
    }
    
    public static String getDocumentationForParameter(IParameterDefinition definition, boolean useMarkdown)
    {
        IDefinition parentDefinition = definition.getParent();
        if (!(parentDefinition instanceof IFunctionDefinition))
        {
            return null;
        }
        IFunctionDefinition functionDefinition = (IFunctionDefinition) parentDefinition;
        VSCodeASDocComment comment = (VSCodeASDocComment) functionDefinition.getExplicitSourceComment();
        if (comment == null)
        {
            return null;
        }
        comment.compile(useMarkdown);
        Collection<IASDocTag> paramTags = comment.getTagsByName(ASDOC_TAG_PARAM);
        if (paramTags == null)
        {
            return null;
        }
        String paramName = definition.getBaseName();
        for (IASDocTag paramTag : paramTags)
        {
            String description = paramTag.getDescription();
            if (description.startsWith(paramName + " "))
            {
                return description.substring(paramName.length() + 1);
            }
        }
        return null;
    }
}