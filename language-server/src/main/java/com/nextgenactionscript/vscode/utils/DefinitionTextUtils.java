/*
Copyright 2016-2017 Bowler Hat LLC

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
import java.util.Set;

import org.apache.royale.compiler.constants.IASKeywordConstants;
import org.apache.royale.compiler.constants.IASLanguageConstants;
import org.apache.royale.compiler.constants.IMetaAttributeConstants;
import org.apache.royale.compiler.definitions.IAccessorDefinition;
import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IConstantDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IEventDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IGetterDefinition;
import org.apache.royale.compiler.definitions.IInterfaceDefinition;
import org.apache.royale.compiler.definitions.IPackageDefinition;
import org.apache.royale.compiler.definitions.IParameterDefinition;
import org.apache.royale.compiler.definitions.ISetterDefinition;
import org.apache.royale.compiler.definitions.IStyleDefinition;
import org.apache.royale.compiler.definitions.ITypeDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition;
import org.apache.royale.compiler.projects.ICompilerProject;
import org.apache.royale.compiler.tree.as.IFunctionNode;

public class DefinitionTextUtils
{
    private static final String UNDERSCORE_UNDERSCORE_AS3_PACKAGE = "__AS3__.";
    private static final String NEW_LINE = "\n";
    private static final String INDENT = "\t";

    public static String definitionToTextDocument(IDefinition definition, ICompilerProject currentProject)
	{
		if (definition instanceof IClassDefinition)
		{
            IClassDefinition classDefinition = (IClassDefinition) definition;
            return classDefinitionToTextDocument(classDefinition, currentProject);
		}
		if (definition instanceof IInterfaceDefinition)
		{
            IInterfaceDefinition interfaceDefinition = (IInterfaceDefinition) definition;
            return interfaceDefinitionToTextDocument(interfaceDefinition, currentProject);
		}
		if (definition instanceof IFunctionDefinition)
		{
            IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
            IDefinition parentDefinition = functionDefinition.getParent();
            if(parentDefinition instanceof ITypeDefinition)
            {
                if (parentDefinition instanceof IClassDefinition)
                {
                    IClassDefinition classDefinition = (IClassDefinition) parentDefinition;
                    return classDefinitionToTextDocument(classDefinition, currentProject);
                }
                else if (parentDefinition instanceof IInterfaceDefinition)
                {
                    IInterfaceDefinition interfaceDefinition = (IInterfaceDefinition) parentDefinition;
                    return interfaceDefinitionToTextDocument(interfaceDefinition, currentProject);
                }
            }
            else
            {
                return functionDefinitionToTextDocument(functionDefinition, currentProject);
            }
		}
		if (definition instanceof IVariableDefinition)
		{
            IVariableDefinition variableDefinition = (IVariableDefinition) definition;
            IDefinition parentDefinition = variableDefinition.getParent();
            if(parentDefinition instanceof ITypeDefinition)
            {
                if (parentDefinition instanceof IClassDefinition)
                {
                    IClassDefinition classDefinition = (IClassDefinition) parentDefinition;
                    return classDefinitionToTextDocument(classDefinition, currentProject);
                }
                else if (parentDefinition instanceof IInterfaceDefinition)
                {
                    IInterfaceDefinition interfaceDefinition = (IInterfaceDefinition) parentDefinition;
                    return interfaceDefinitionToTextDocument(interfaceDefinition, currentProject);
                }
            }
            else
            {
                return variableDefinitionToTextDocument(variableDefinition, currentProject);
            }
		}
		return null;
    }

    private static String classDefinitionToTextDocument(IClassDefinition classDefinition, ICompilerProject currentProject)
	{
        String indent = "";
        StringBuilder textDocumentBuilder = new StringBuilder();
        textDocumentBuilder.append(IASKeywordConstants.PACKAGE);
        String packageName = classDefinition.getPackageName();
        if(packageName != null && packageName.length() > 0)
        {
            textDocumentBuilder.append(" ");
            textDocumentBuilder.append(packageName);
        }
        textDocumentBuilder.append(NEW_LINE);
        textDocumentBuilder.append("{");
        textDocumentBuilder.append(NEW_LINE);
        indent = increaseIndent(indent);
        textDocumentBuilder.append(indent);
        if (classDefinition.isPublic())
        {
            textDocumentBuilder.append(IASKeywordConstants.PUBLIC);
            textDocumentBuilder.append(" ");
        }
        else if (classDefinition.isInternal())
        {
            textDocumentBuilder.append(IASKeywordConstants.INTERNAL);
            textDocumentBuilder.append(" ");
        }
        if (classDefinition.isFinal())
        {
            textDocumentBuilder.append(IASKeywordConstants.FINAL);
            textDocumentBuilder.append(" ");
        }
        if (classDefinition.isDynamic())
        {
            textDocumentBuilder.append(IASKeywordConstants.DYNAMIC);
            textDocumentBuilder.append(" ");
        }
        textDocumentBuilder.append(IASKeywordConstants.CLASS);
        textDocumentBuilder.append(" ");
        textDocumentBuilder.append(classDefinition.getBaseName());
        IClassDefinition baseClassDefinition = classDefinition.resolveBaseClass(currentProject);
        if (baseClassDefinition != null && !baseClassDefinition.getQualifiedName().equals(IASLanguageConstants.Object))
        {
            textDocumentBuilder.append(" ");
            textDocumentBuilder.append(IASKeywordConstants.EXTENDS);
            textDocumentBuilder.append(" ");
            textDocumentBuilder.append(classDefinition.getBaseClassAsDisplayString());
        }
        IInterfaceDefinition[] interfaceDefinitions = classDefinition.resolveImplementedInterfaces(currentProject);
        if (interfaceDefinitions.length > 0)
        {
            textDocumentBuilder.append(" ");
            textDocumentBuilder.append(IASKeywordConstants.IMPLEMENTS);
            textDocumentBuilder.append(" ");
            appendInterfaceNamesToDetail(textDocumentBuilder, interfaceDefinitions);
        }
        textDocumentBuilder.append(NEW_LINE);
        textDocumentBuilder.append(indent);
        textDocumentBuilder.append("{");
        textDocumentBuilder.append(NEW_LINE);
        indent = increaseIndent(indent);
        Collection<IDefinition> definitionSet = classDefinition.getContainedScope().getAllLocalDefinitions();
        for (IDefinition childDefinition : definitionSet)
        {
            if (childDefinition.isOverride() || childDefinition.isPrivate())
            {
                //skip overrides and private
                continue;
            }
            if(childDefinition instanceof IAccessorDefinition)
            {
                IAccessorDefinition functionDefinition = (IAccessorDefinition) childDefinition;
                textDocumentBuilder.append(indent);
                insertFunctionDefinitionIntoTextDocument(functionDefinition, textDocumentBuilder, currentProject);
            }
            else if (childDefinition instanceof IFunctionDefinition)
            {
                IFunctionDefinition functionDefinition = (IFunctionDefinition) childDefinition;
                textDocumentBuilder.append(indent);
                insertFunctionDefinitionIntoTextDocument(functionDefinition, textDocumentBuilder, currentProject);
            }
            else if (childDefinition instanceof IVariableDefinition)
            {
                IVariableDefinition variableDefinition = (IVariableDefinition) childDefinition;
                textDocumentBuilder.append(indent);
                insertVariableDefinitionIntoTextDocument(variableDefinition, textDocumentBuilder, currentProject);
            }
        }
        indent = decreaseIndent(indent);
        textDocumentBuilder.append(indent);
        textDocumentBuilder.append("}");
        textDocumentBuilder.append(NEW_LINE);
        indent = decreaseIndent(indent);
        textDocumentBuilder.append("}");
        return textDocumentBuilder.toString();
    }

    private static String interfaceDefinitionToTextDocument(IInterfaceDefinition interfaceDefinition, ICompilerProject currentProject)
	{
        String indent = "";
        StringBuilder textDocumentBuilder = new StringBuilder();
        textDocumentBuilder.append(IASKeywordConstants.PACKAGE);
        String packageName = interfaceDefinition.getPackageName();
        if(packageName != null && packageName.length() > 0)
        {
            textDocumentBuilder.append(" ");
            textDocumentBuilder.append(packageName);
        }
        textDocumentBuilder.append(NEW_LINE);
        textDocumentBuilder.append("{");
        textDocumentBuilder.append(NEW_LINE);
        indent = increaseIndent(indent);
        textDocumentBuilder.append(indent);
        if (interfaceDefinition.isPublic())
        {
            textDocumentBuilder.append(IASKeywordConstants.PUBLIC);
            textDocumentBuilder.append(" ");
        }
        else if (interfaceDefinition.isInternal())
        {
            textDocumentBuilder.append(IASKeywordConstants.INTERNAL);
            textDocumentBuilder.append(" ");
        }
        textDocumentBuilder.append(IASKeywordConstants.INTERFACE);
        textDocumentBuilder.append(" ");
        textDocumentBuilder.append(interfaceDefinition.getBaseName());
        IInterfaceDefinition[] interfaceDefinitions = interfaceDefinition.resolveExtendedInterfaces(currentProject);
        if (interfaceDefinitions.length > 0)
        {
            textDocumentBuilder.append(" ");
            textDocumentBuilder.append(IASKeywordConstants.EXTENDS);
            textDocumentBuilder.append(" ");
            appendInterfaceNamesToDetail(textDocumentBuilder, interfaceDefinitions);
        }
        textDocumentBuilder.append(NEW_LINE);
        textDocumentBuilder.append(indent);
        textDocumentBuilder.append("{");
        textDocumentBuilder.append(NEW_LINE);
        indent = increaseIndent(indent);
        Collection<IDefinition> definitionSet = interfaceDefinition.getContainedScope().getAllLocalDefinitions();
        for (IDefinition childDefinition : definitionSet)
        {
            if (childDefinition.isOverride() || childDefinition.isPrivate() || childDefinition.isInternal())
            {
                //skip overrides, private, and internal
                continue;
            }
            if(childDefinition instanceof IAccessorDefinition)
            {
                IAccessorDefinition functionDefinition = (IAccessorDefinition) childDefinition;
                textDocumentBuilder.append(indent);
                insertFunctionDefinitionIntoTextDocument(functionDefinition, textDocumentBuilder, currentProject);
            }
            else if (childDefinition instanceof IFunctionDefinition)
            {
                IFunctionDefinition functionDefinition = (IFunctionDefinition) childDefinition;
                textDocumentBuilder.append(indent);
                insertFunctionDefinitionIntoTextDocument(functionDefinition, textDocumentBuilder, currentProject);
            }
        }
        indent = decreaseIndent(indent);
        textDocumentBuilder.append(indent);
        textDocumentBuilder.append("}");
        textDocumentBuilder.append(NEW_LINE);
        indent = decreaseIndent(indent);
        textDocumentBuilder.append("}");
        return textDocumentBuilder.toString();
    }

    private static String functionDefinitionToTextDocument(IFunctionDefinition functionDefinition, ICompilerProject currentProject)
	{
        String indent = "";
        StringBuilder textDocumentBuilder = new StringBuilder();
        textDocumentBuilder.append(IASKeywordConstants.PACKAGE);
        String packageName = functionDefinition.getPackageName();
        if(packageName != null && packageName.length() > 0)
        {
            textDocumentBuilder.append(" ");
            textDocumentBuilder.append(packageName);
        }
        textDocumentBuilder.append(NEW_LINE);
        textDocumentBuilder.append("{");
        textDocumentBuilder.append(NEW_LINE);
        indent = increaseIndent(indent);
        textDocumentBuilder.append(indent);
        insertFunctionDefinitionIntoTextDocument(functionDefinition, textDocumentBuilder, currentProject);
        indent = decreaseIndent(indent);
        textDocumentBuilder.append("}");
        return textDocumentBuilder.toString();
    }

    private static String variableDefinitionToTextDocument(IVariableDefinition variableDefinition, ICompilerProject currentProject)
	{
        String indent = "";
        StringBuilder textDocumentBuilder = new StringBuilder();
        textDocumentBuilder.append(IASKeywordConstants.PACKAGE);
        String packageName = variableDefinition.getPackageName();
        if(packageName != null && packageName.length() > 0)
        {
            textDocumentBuilder.append(" ");
            textDocumentBuilder.append(packageName);
        }
        textDocumentBuilder.append(NEW_LINE);
        textDocumentBuilder.append("{");
        textDocumentBuilder.append(NEW_LINE);
        indent = increaseIndent(indent);
        textDocumentBuilder.append(indent);
        insertVariableDefinitionIntoTextDocument(variableDefinition, textDocumentBuilder, currentProject);
        indent = decreaseIndent(indent);
        textDocumentBuilder.append("}");
        return textDocumentBuilder.toString();
    }

    private static void insertFunctionDefinitionIntoTextDocument(IFunctionDefinition functionDefinition, StringBuilder textDocumentBuilder, ICompilerProject currentProject)
    {
        if (functionDefinition.isPublic() || functionDefinition.isConstructor())
        {
            textDocumentBuilder.append(IASKeywordConstants.PUBLIC);
            textDocumentBuilder.append(" ");
        }
        else if (functionDefinition.isInternal())
        {
            textDocumentBuilder.append(IASKeywordConstants.INTERNAL);
            textDocumentBuilder.append(" ");
        }
        if (functionDefinition.isStatic())
        {
            textDocumentBuilder.append(IASKeywordConstants.STATIC);
            textDocumentBuilder.append(" ");
        }
        if (functionDefinition.isNative())
        {
            textDocumentBuilder.append(IASKeywordConstants.NATIVE);
            textDocumentBuilder.append(" ");
        }
        if (functionDefinition.isFinal())
        {
            textDocumentBuilder.append(IASKeywordConstants.FINAL);
            textDocumentBuilder.append(" ");
        }
        textDocumentBuilder.append(IASKeywordConstants.FUNCTION);
        textDocumentBuilder.append(" ");
        if (functionDefinition instanceof IGetterDefinition)
        {
            textDocumentBuilder.append(IASKeywordConstants.GET);
            textDocumentBuilder.append(" ");
        }
        else if (functionDefinition instanceof ISetterDefinition)
        {
            textDocumentBuilder.append(IASKeywordConstants.SET);
            textDocumentBuilder.append(" ");
        }
        textDocumentBuilder.append(functionDefinitionToSignature(functionDefinition, currentProject));
        textDocumentBuilder.append(";");
        textDocumentBuilder.append(NEW_LINE);
    }

    private static void insertVariableDefinitionIntoTextDocument(IVariableDefinition variableDefinition, StringBuilder textDocumentBuilder, ICompilerProject currentProject)
	{
        if (variableDefinition.isPublic())
        {
            textDocumentBuilder.append(IASKeywordConstants.PUBLIC);
            textDocumentBuilder.append(" ");
        }
        else if (variableDefinition.isInternal())
        {
            textDocumentBuilder.append(IASKeywordConstants.INTERNAL);
            textDocumentBuilder.append(" ");
        }
        if (variableDefinition.isStatic())
        {
            textDocumentBuilder.append(IASKeywordConstants.STATIC);
            textDocumentBuilder.append(" ");
        }
        if(variableDefinition instanceof IConstantDefinition)
        {
            textDocumentBuilder.append(IASKeywordConstants.CONST);
        }
        else
        {
            textDocumentBuilder.append(IASKeywordConstants.VAR);
        }
        textDocumentBuilder.append(" ");
        textDocumentBuilder.append(variableDefinition.getBaseName());
        textDocumentBuilder.append(":");
        textDocumentBuilder.append(variableDefinition.getTypeAsDisplayString());
        textDocumentBuilder.append(";");
        textDocumentBuilder.append(NEW_LINE);
    }

    public static void appendInterfaceNamesToDetail(StringBuilder detailBuilder, IInterfaceDefinition[] interfaceDefinitions)
    {
        for (int i = 0, count = interfaceDefinitions.length; i < count; i++)
        {
            if (i > 0)
            {
                detailBuilder.append(", ");
            }
            IInterfaceDefinition baseInterface = interfaceDefinitions[i];
            detailBuilder.append(baseInterface.getBaseName());
        }
    }

    public static String definitionToDetail(IDefinition definition, ICompilerProject currentProject)
    {
        StringBuilder detailBuilder = new StringBuilder();
        if (definition instanceof IClassDefinition)
        {
            IClassDefinition classDefinition = (IClassDefinition) definition;
            if (classDefinition.isDynamic())
            {
                detailBuilder.append(IASKeywordConstants.DYNAMIC);
                detailBuilder.append(" ");
            }
            detailBuilder.append(IASKeywordConstants.CLASS);
            detailBuilder.append(" ");
            if (classDefinition.getPackageName().startsWith(UNDERSCORE_UNDERSCORE_AS3_PACKAGE))
            {
                //classes like __AS3__.vec.Vector should not include the
                //package name
                detailBuilder.append(classDefinition.getBaseName());
            }
            else
            {
                detailBuilder.append(classDefinition.getQualifiedName());
            }
            IClassDefinition baseClassDefinition = classDefinition.resolveBaseClass(currentProject);
            if (baseClassDefinition != null && !baseClassDefinition.getQualifiedName().equals(IASLanguageConstants.Object))
            {
                detailBuilder.append(" ");
                detailBuilder.append(IASKeywordConstants.EXTENDS);
                detailBuilder.append(" ");
                detailBuilder.append(baseClassDefinition.getBaseClassAsDisplayString());
            }
            IInterfaceDefinition[] interfaceDefinitions = classDefinition.resolveImplementedInterfaces(currentProject);
            if (interfaceDefinitions.length > 0)
            {
                detailBuilder.append(" ");
                detailBuilder.append(IASKeywordConstants.IMPLEMENTS);
                detailBuilder.append(" ");
                DefinitionTextUtils.appendInterfaceNamesToDetail(detailBuilder, interfaceDefinitions);
            }
        }
        else if (definition instanceof IInterfaceDefinition)
        {
            IInterfaceDefinition interfaceDefinition = (IInterfaceDefinition) definition;
            detailBuilder.append(IASKeywordConstants.INTERFACE);
            detailBuilder.append(" ");
            detailBuilder.append(interfaceDefinition.getQualifiedName());
            IInterfaceDefinition[] interfaceDefinitions = interfaceDefinition.resolveExtendedInterfaces(currentProject);
            if (interfaceDefinitions.length > 0)
            {
                detailBuilder.append(" ");
                detailBuilder.append(IASKeywordConstants.EXTENDS);
                detailBuilder.append(" ");
                DefinitionTextUtils.appendInterfaceNamesToDetail(detailBuilder, interfaceDefinitions);
            }
        }
        else if (definition instanceof IVariableDefinition)
        {
            IVariableDefinition variableDefinition = (IVariableDefinition) definition;
            IDefinition parentDefinition = variableDefinition.getParent();
            if (parentDefinition instanceof ITypeDefinition)
            {
                //an IAccessorDefinition actually extends both
                //IVariableDefinition and IFunctionDefinition 
                if (variableDefinition instanceof IAccessorDefinition)
                {
                    detailBuilder.append("(property) ");
                }
                else if (variableDefinition instanceof IConstantDefinition)
                {
                    detailBuilder.append("(const) ");
                }
                else
                {
                    detailBuilder.append("(variable) ");
                }
                detailBuilder.append(parentDefinition.getQualifiedName());
                detailBuilder.append(".");
            }
            else if (parentDefinition instanceof IFunctionDefinition)
            {
                if (variableDefinition instanceof IParameterDefinition)
                {
                    detailBuilder.append("(parameter) ");
                }
                else
                {
                    detailBuilder.append("(local ");
                    if (variableDefinition instanceof IConstantDefinition)
                    {
                        detailBuilder.append("const) ");
                    }
                    else
                    {
                        detailBuilder.append("var) ");
                    }
                }
            }
            else
            {
                if (variableDefinition instanceof IConstantDefinition)
                {
                    detailBuilder.append(IASKeywordConstants.CONST);
                }
                else
                {
                    detailBuilder.append(IASKeywordConstants.VAR);
                }
                detailBuilder.append(" ");
            }
            detailBuilder.append(variableDefinition.getBaseName());
            detailBuilder.append(":");
            detailBuilder.append(variableDefinition.getTypeAsDisplayString());
        }
        else if (definition instanceof IFunctionDefinition)
        {
            IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
            IDefinition parentDefinition = functionDefinition.getParent();
            if (parentDefinition instanceof ITypeDefinition)
            {
                if (functionDefinition.isConstructor())
                {
                    detailBuilder.append("(constructor) ");
                }
                else
                {
                    detailBuilder.append("(method) ");
                }
                detailBuilder.append(parentDefinition.getQualifiedName());
                detailBuilder.append(".");
            }
            else if (parentDefinition instanceof IFunctionDefinition)
            {
                detailBuilder.append("(local function) ");
            }
            else
            {
                detailBuilder.append(IASKeywordConstants.FUNCTION);
                detailBuilder.append(" ");
            }
            detailBuilder.append(functionDefinitionToSignature(functionDefinition, currentProject));
        }
        else if (definition instanceof IEventDefinition)
        {
            IEventDefinition eventDefinition = (IEventDefinition) definition;
            detailBuilder.append("(event) ");
            detailBuilder.append("[");
            detailBuilder.append(IMetaAttributeConstants.ATTRIBUTE_EVENT);
            detailBuilder.append("(");
            detailBuilder.append(IMetaAttributeConstants.NAME_EVENT_NAME);
            detailBuilder.append("=");
            detailBuilder.append("\"");
            detailBuilder.append(eventDefinition.getBaseName());
            detailBuilder.append("\"");
            detailBuilder.append(",");
            detailBuilder.append(IMetaAttributeConstants.NAME_EVENT_TYPE);
            detailBuilder.append("=");
            detailBuilder.append("\"");
            detailBuilder.append(eventDefinition.getTypeAsDisplayString());
            detailBuilder.append("\"");
            detailBuilder.append(")");
            detailBuilder.append("]");
        }
        else if (definition instanceof IStyleDefinition)
        {
            IStyleDefinition styleDefinition = (IStyleDefinition) definition;
            detailBuilder.append("(style) ");
            detailBuilder.append("[");
            detailBuilder.append(IMetaAttributeConstants.ATTRIBUTE_STYLE);
            detailBuilder.append("(");
            detailBuilder.append(IMetaAttributeConstants.NAME_STYLE_NAME);
            detailBuilder.append("=");
            detailBuilder.append("\"");
            detailBuilder.append(styleDefinition.getBaseName());
            detailBuilder.append("\"");
            detailBuilder.append(",");
            detailBuilder.append(IMetaAttributeConstants.NAME_STYLE_TYPE);
            detailBuilder.append("=");
            detailBuilder.append("\"");
            detailBuilder.append(styleDefinition.getTypeAsDisplayString());
            detailBuilder.append("\"");
            detailBuilder.append(")");
            detailBuilder.append("]");
        }
        return detailBuilder.toString();
    }

    public static String functionDefinitionToSignature(IFunctionDefinition functionDefinition, ICompilerProject currentProject)
    {
        StringBuilder labelBuilder = new StringBuilder();
        labelBuilder.append(functionDefinition.getBaseName());
        labelBuilder.append("(");
        IParameterDefinition[] parameters = functionDefinition.getParameters();
        for (int i = 0, count = parameters.length; i < count; i++)
        {
            if (i > 0)
            {
                labelBuilder.append(", ");
            }
            IParameterDefinition parameterDefinition = parameters[i];
            if (parameterDefinition.isRest())
            {
                labelBuilder.append(IASLanguageConstants.REST);
            }
            labelBuilder.append(parameterDefinition.getBaseName());
            labelBuilder.append(":");
            labelBuilder.append(parameterDefinition.getTypeAsDisplayString());
            if (parameterDefinition.hasDefaultValue())
            {
                labelBuilder.append(" = ");
                Object defaultValue = parameterDefinition.resolveDefaultValue(currentProject);
                if (defaultValue instanceof String)
                {
                    labelBuilder.append("\"");
                    labelBuilder.append(defaultValue);
                    labelBuilder.append("\"");
                }
                else if (defaultValue != null)
                {
                    if (defaultValue.getClass() == Object.class)
                    {
                        //for some reason, null is some strange random object
                        labelBuilder.append(IASLanguageConstants.NULL);
                    }
                    else
                    {
                        //numeric values and everything else should be okay
                        labelBuilder.append(defaultValue);
                    }
                }
                else
                {
                    //I don't know how this might happen, but this is probably
                    //a safe fallback value
                    labelBuilder.append(IASLanguageConstants.NULL);
                }
            }
        }
        labelBuilder.append(")");
        if (!functionDefinition.isConstructor())
        {
            labelBuilder.append(":");
            labelBuilder.append(functionDefinition.getReturnTypeAsDisplayString());
        }
        return labelBuilder.toString();
    }
    
    private static String increaseIndent(String indent)
    {
        return indent + INDENT;
    }
    
    private static String decreaseIndent(String indent)
    {
        if(indent.length() == 0)
        {
            return indent;
        }
        return indent.substring(1);
    }
}