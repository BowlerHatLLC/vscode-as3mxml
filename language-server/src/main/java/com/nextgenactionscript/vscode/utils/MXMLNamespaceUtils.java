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
import java.util.HashMap;

import com.nextgenactionscript.vscode.mxml.IMXMLLibraryConstants;

import org.apache.royale.compiler.common.IFileSpecificationGetter;
import org.apache.royale.compiler.common.PrefixMap;
import org.apache.royale.compiler.common.XMLName;
import org.apache.royale.compiler.definitions.ITypeDefinition;
import org.apache.royale.compiler.internal.mxml.MXMLData;
import org.apache.royale.compiler.mxml.IMXMLDataManager;
import org.apache.royale.compiler.mxml.IMXMLLanguageConstants;
import org.apache.royale.compiler.projects.IRoyaleProject;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.apache.royale.compiler.workspaces.IWorkspace;

public class MXMLNamespaceUtils
{
    private static final HashMap<String, String> NAMESPACE_TO_PREFIX = new HashMap<>();

    {
        //MXML language
        NAMESPACE_TO_PREFIX.put(IMXMLLanguageConstants.NAMESPACE_MXML_2006, "mx");
        NAMESPACE_TO_PREFIX.put(IMXMLLanguageConstants.NAMESPACE_MXML_2009, "fx");

        //Flex
        NAMESPACE_TO_PREFIX.put(IMXMLLibraryConstants.MX, "mx");
        NAMESPACE_TO_PREFIX.put(IMXMLLibraryConstants.SPARK, "s");
        
        //FlexJS
        NAMESPACE_TO_PREFIX.put(IMXMLLibraryConstants.FLEXJS_EXPRESS, "js");
        NAMESPACE_TO_PREFIX.put(IMXMLLibraryConstants.FLEXJS_BASIC, "js");

        //Royale
        NAMESPACE_TO_PREFIX.put(IMXMLLibraryConstants.ROYALE_EXPRESS, "js");
        NAMESPACE_TO_PREFIX.put(IMXMLLibraryConstants.ROYALE_BASIC, "js");

        //Feathers
        NAMESPACE_TO_PREFIX.put(IMXMLLibraryConstants.FEATHERS, "f");
    }

	private static final String LOCAL_PREFIX = "local";
	private static final String DEFAULT_NS_PREFIX = "ns";
    private static final String STAR = "*";
    private static final String DOT_STAR = ".*";
    private static final String UNDERSCORE_UNDERSCORE_AS3_PACKAGE = "__AS3__.";

	public static MXMLNamespace getMXMLLanguageNamespace(ICompilationUnit unit, IFileSpecificationGetter fileSpecGetter, IWorkspace workspace)
    {
        IMXMLDataManager mxmlDataManager = workspace.getMXMLDataManager();
        MXMLData mxmlData = (MXMLData) mxmlDataManager.get(fileSpecGetter.getFileSpecification(unit.getAbsoluteFilename()));
        PrefixMap prefixMap = mxmlData.getRootTagPrefixMap();
        String fxURI = mxmlData.getRootTag().getMXMLDialect().getLanguageNamespace();
        MXMLNamespace fxNS = getNamespaceFromURI(fxURI, prefixMap);
        return fxNS;
    }

    public static MXMLNamespace getNamespaceFromURI(String uri, PrefixMap prefixMap)
    {
        String[] uriPrefixes = prefixMap.getPrefixesForNamespace(uri);
        if (uriPrefixes.length > 0)
        {
            return new MXMLNamespace(uriPrefixes[0], uri);
        }

        //we'll check if the namespace comes from a known library
        //with a common prefix
        if (NAMESPACE_TO_PREFIX.containsKey(uri))
        {
            String prefix = NAMESPACE_TO_PREFIX.get(uri);
            if (prefixMap.containsPrefix(prefix))
            {
                //the prefix already exists with a different URI, so we can't
                //use it for this URI
                prefix = null;
            }
            if (prefix != null)
            {
                return new MXMLNamespace(prefix, uri);
            }
        }
        return null;
	}
	

    public static MXMLNamespace getMXMLNamespaceForTypeDefinition(ITypeDefinition definition, MXMLData mxmlData, IRoyaleProject currentProject)
    {
        PrefixMap prefixMap = mxmlData.getRootTagPrefixMap();
        Collection<XMLName> tagNames = currentProject.getTagNamesForClass(definition.getQualifiedName());

        //1. try to use an existing xmlns with an uri
        for (XMLName tagName : tagNames)
        {
            String tagNamespace = tagName.getXMLNamespace();
            //getTagNamesForClass() returns the 2006 namespace, even if that's
            //not what we're using in this file
            if (tagNamespace.equals(IMXMLLanguageConstants.NAMESPACE_MXML_2006))
            {
                //use the language namespace of the root tag instead
                tagNamespace = mxmlData.getRootTag().getMXMLDialect().getLanguageNamespace();
            }
            String[] uriPrefixes = prefixMap.getPrefixesForNamespace(tagNamespace);
            if (uriPrefixes.length > 0)
            {
                return new MXMLNamespace(uriPrefixes[0], tagNamespace);
            }
        }

        //2. try to use an existing xmlns with a package name
        String packageName = definition.getPackageName();
        String packageNamespace = getPackageNameMXMLNamespaceURI(packageName);
        String[] packagePrefixes = prefixMap.getPrefixesForNamespace(packageNamespace);
        if (packagePrefixes.length > 0)
        {
            return new MXMLNamespace(packagePrefixes[0], packageNamespace);
        }

        //3. try to create a new xmlns with a prefix and uri


        //special case for the __AS3__ package
        if (packageName != null && packageName.startsWith(UNDERSCORE_UNDERSCORE_AS3_PACKAGE))
        {
            //anything in this package is in the language namespace
            String fxNamespace = mxmlData.getRootTag().getMXMLDialect().getLanguageNamespace();
            MXMLNamespace resultNS = MXMLNamespaceUtils.getNamespaceFromURI(fxNamespace, prefixMap);
            if (resultNS != null)
            {
                return resultNS;
            }
        }

        String fallbackNamespace = null;
        for (XMLName tagName : tagNames)
        {
            //we know this type is in one or more namespaces
            //let's try to figure out a nice prefix to use
            fallbackNamespace = tagName.getXMLNamespace();
            MXMLNamespace resultNS = MXMLNamespaceUtils.getNamespaceFromURI(fallbackNamespace, prefixMap);
            if (resultNS != null)
            {
                return resultNS;
            }
        }
        if (fallbackNamespace != null)
        {
            //if we couldn't find a known prefix, use a numbered one
            String prefix = getNumberedNamespacePrefix(DEFAULT_NS_PREFIX, prefixMap);
            return new MXMLNamespace(prefix, fallbackNamespace);
		}
		
		//4. special case: if the package namespace is simply *, try to use
		//local as the prefix, if it's not already defined. this matches the
		//behavior of Adoboe Flash Builder.
		if (packageNamespace.equals(STAR) && !prefixMap.containsPrefix(LOCAL_PREFIX))
		{
			return new MXMLNamespace(LOCAL_PREFIX, packageNamespace);
		}

		//5. try to use the final part of the package name as the prefix, if
		//it's not already defined.
		if (packageName != null && packageName.length() > 0)
		{
			String[] parts = packageName.split("\\.");
			String finalPart = parts[parts.length - 1];
			if (!prefixMap.containsPrefix(finalPart))
			{
				return new MXMLNamespace(finalPart, packageNamespace);
			}
		}

        //6. worst case: create a new xmlns with numbered prefix and package name
        String prefix = getNumberedNamespacePrefix(DEFAULT_NS_PREFIX, prefixMap);
        return new MXMLNamespace(prefix, packageNamespace);
    }

    private static String getPackageNameMXMLNamespaceURI(String packageName)
    {
        if (packageName != null && packageName.length() > 0)
        {
            return packageName + DOT_STAR;
        }
        return STAR;
    }

    private static String getNumberedNamespacePrefix(String prefixPrefix, PrefixMap prefixMap)
    {
        //if all else fails, fall back to a generic namespace
        int count = 1;
        String prefix = null;
        do
        {
            prefix = prefixPrefix + count;
            if (prefixMap.containsPrefix(prefix))
            {
                prefix = null;
            }
            count++;
        }
        while (prefix == null);
        return prefix;
    }
}