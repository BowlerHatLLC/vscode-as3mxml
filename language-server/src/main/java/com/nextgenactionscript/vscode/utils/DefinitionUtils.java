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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import org.apache.royale.abc.ABCParser;
import org.apache.royale.abc.Pool;
import org.apache.royale.abc.PoolingABCVisitor;
import org.apache.royale.compiler.constants.IASLanguageConstants;
import org.apache.royale.compiler.constants.IMetaAttributeConstants;
import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IGetterDefinition;
import org.apache.royale.compiler.definitions.IInterfaceDefinition;
import org.apache.royale.compiler.definitions.ISetterDefinition;
import org.apache.royale.compiler.definitions.ITypeDefinition;
import org.apache.royale.compiler.definitions.IClassDefinition.IClassIterator;
import org.apache.royale.compiler.definitions.metadata.IMetaTag;
import org.apache.royale.compiler.internal.projects.RoyaleProject;
import org.apache.royale.compiler.projects.ICompilerProject;
import org.apache.royale.compiler.units.ICompilationUnit;

public class DefinitionUtils
{
    private static final String PROPERTY_FRAMEWORK_LIB = "royalelib";
    private static final String SDK_FRAMEWORKS_PATH_SIGNATURE = "/frameworks/";
    private static final String SDK_SOURCE_PATH_SIGNATURE_UNIX = "/frameworks/projects/";
    private static final String SDK_SOURCE_PATH_SIGNATURE_WINDOWS = "\\frameworks\\projects\\";

	/**
	 * Returns the qualified name of the required type for child elements, or
	 * null.
	 */
	public static String getMXMLChildElementTypeForDefinition(IDefinition definition, ICompilerProject project)
	{
		return getMXMLChildElementTypeForDefinition(definition, project, true);
	}

	private static String getMXMLChildElementTypeForDefinition(IDefinition definition, ICompilerProject project, boolean resolvePaired)
	{
		IMetaTag arrayElementType = definition.getMetaTagByName(IMetaAttributeConstants.ATTRIBUTE_ARRAYELEMENTTYPE);
		if (arrayElementType != null)
		{
			return arrayElementType.getValue();
		}
		IMetaTag instanceType = definition.getMetaTagByName(IMetaAttributeConstants.ATTRIBUTE_INSTANCETYPE);
		if (instanceType != null)
		{
			return instanceType.getValue();
		}
		if (resolvePaired)
		{
			if (definition instanceof IGetterDefinition)
			{
				IGetterDefinition getterDefinition = (IGetterDefinition) definition;
				ISetterDefinition setterDefinition = getterDefinition.resolveSetter(project);
				if (setterDefinition != null)
				{
					return getMXMLChildElementTypeForDefinition(setterDefinition, project, false);
				}
			}
			else if (definition instanceof ISetterDefinition)
			{
				ISetterDefinition setterDefinition = (ISetterDefinition) definition;
				IGetterDefinition getterDefinition = setterDefinition.resolveGetter(project);
				if (getterDefinition != null)
				{
					return getMXMLChildElementTypeForDefinition(getterDefinition, project, false);
				}
			}
		}
		ITypeDefinition typeDefinition = definition.resolveType(project);
		if (typeDefinition != null)
		{
			String qualifiedName = typeDefinition.getQualifiedName();
			if (qualifiedName.equals(IASLanguageConstants.Array))
			{
				//the wrapping array can be omitted, and since there's no
				//[ArrayElementType] metadata, default to Object
				return IASLanguageConstants.Object;
			}
		}
		return null;
	}

	public static String getDefinitionDebugSourceFilePath(IDefinition definition, RoyaleProject project)
	{
		ICompilationUnit unit = project.getScope().getCompilationUnitForDefinition(definition);
		try
		{
			byte[] abcBytes = unit.getABCBytesRequest().get().getABCBytes();
			ABCParser parser = new ABCParser(abcBytes);
			PoolingABCVisitor visitor = new PoolingABCVisitor();
			parser.parseABC(visitor);
			Pool<String> pooledStrings = visitor.getStringPool();
			for (String pooledString : pooledStrings.getValues())
			{
				if (pooledString.contains(SDK_SOURCE_PATH_SIGNATURE_UNIX)
						|| pooledString.contains(SDK_SOURCE_PATH_SIGNATURE_WINDOWS))
				{
					//just go with the first one that we find
					return transformDebugFilePath(pooledString);
				}
			}
		}
		catch (InterruptedException e)
		{
			//safe to ignore
		}
		return null;
	}

	public static boolean isImplementationOfInterface(IClassDefinition classDefinition, IInterfaceDefinition interfaceDefinition, ICompilerProject project)
	{
		Iterator<IInterfaceDefinition> interfaceIterator = classDefinition.interfaceIterator(project);
		while (interfaceIterator.hasNext())
		{
			IInterfaceDefinition implementedInterfaceDefinition = interfaceIterator.next();
			if (implementedInterfaceDefinition.equals(interfaceDefinition))
			{
				return true;
			}
		}
		return false;
	}

	public static boolean extendsOrImplements(ICompilerProject project, ITypeDefinition typeDefinition, String qualifiedNameToFind)
    {
		if (typeDefinition instanceof IClassDefinition)
		{
			IClassDefinition classDefinition = (IClassDefinition) typeDefinition;
			IClassIterator classIterator = classDefinition.classIterator(project, true);
			while (classIterator.hasNext())
			{
				IClassDefinition baseClassDefinition = classIterator.next();
				if (baseClassDefinition.getQualifiedName().equals(qualifiedNameToFind))
				{
					return true;
				}
			}
			Iterator<IInterfaceDefinition> interfaceIterator = classDefinition.interfaceIterator(project);
			if (interfaceIteratorContainsQualifiedName(interfaceIterator, qualifiedNameToFind))
			{
				return true;
			}
		}
		else if (typeDefinition instanceof IInterfaceDefinition)
		{
			IInterfaceDefinition interfaceDefinition = (IInterfaceDefinition) typeDefinition;
			Iterator<IInterfaceDefinition> interfaceIterator = interfaceDefinition.interfaceIterator(project, true);
			if (interfaceIteratorContainsQualifiedName(interfaceIterator, qualifiedNameToFind))
			{
				return true;
			}
		}
		return false;
	}

    private static String transformDebugFilePath(String sourceFilePath)
    {
        int index = -1;
        if (System.getProperty("os.name").toLowerCase().startsWith("windows"))
        {
            //the debug file path divides directories with ; instead of slash in
            //a couple of places, but it's easy to fix
            sourceFilePath = sourceFilePath.replace(';', '\\');
            sourceFilePath = sourceFilePath.replace('/', '\\');
            index = sourceFilePath.indexOf(SDK_SOURCE_PATH_SIGNATURE_WINDOWS);
        }
        else
        {
            sourceFilePath = sourceFilePath.replace(';', '/');
            sourceFilePath = sourceFilePath.replace('\\', '/');
            index = sourceFilePath.indexOf(SDK_SOURCE_PATH_SIGNATURE_UNIX);
        }
        if (index == -1)
        {
            return sourceFilePath;
        }
        sourceFilePath = sourceFilePath.substring(index + SDK_FRAMEWORKS_PATH_SIGNATURE.length());
        Path frameworkPath = Paths.get(System.getProperty(PROPERTY_FRAMEWORK_LIB));
        Path transformedPath = frameworkPath.resolve(sourceFilePath);
        return transformedPath.toFile().getAbsolutePath();
    }
	
	private static boolean interfaceIteratorContainsQualifiedName(Iterator<IInterfaceDefinition> interfaceIterator, String qualifiedName)
	{
		while (interfaceIterator.hasNext())
		{
			IInterfaceDefinition interfaceDefinition = interfaceIterator.next();
			if (interfaceDefinition.getQualifiedName().equals(qualifiedName))
			{
				return true;
			}
		}
		return false;
	}
}