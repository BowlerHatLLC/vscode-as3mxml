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
package com.as3mxml.vscode.utils;

import java.util.ArrayList;
import java.util.List;

import com.as3mxml.vscode.project.ILspProject;

import org.apache.royale.compiler.common.ISourceLocation;
import org.apache.royale.compiler.common.PrefixMap;
import org.apache.royale.compiler.common.XMLName;
import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.internal.mxml.MXMLData;
import org.apache.royale.compiler.mxml.IMXMLData;
import org.apache.royale.compiler.mxml.IMXMLLanguageConstants;
import org.apache.royale.compiler.mxml.IMXMLTagAttributeData;
import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.mxml.IMXMLUnitData;

public class MXMLDataUtils {
    private static final String[] LANGUAGE_TYPE_NAMES = { IMXMLLanguageConstants.ARRAY, IMXMLLanguageConstants.BOOLEAN,
            IMXMLLanguageConstants.CLASS, IMXMLLanguageConstants.DATE, IMXMLLanguageConstants.FUNCTION,
            IMXMLLanguageConstants.INT, IMXMLLanguageConstants.NUMBER, IMXMLLanguageConstants.OBJECT,
            IMXMLLanguageConstants.STRING, IMXMLLanguageConstants.XML, IMXMLLanguageConstants.XML_LIST,
            IMXMLLanguageConstants.UINT };

    public static boolean isInsideTagPrefix(IMXMLTagData tag, int offset) {
        //next, check that we're after the prefix
        //one extra for bracket
        int maxOffset = tag.getAbsoluteStart() + 1;
        String prefix = tag.getPrefix();
        int prefixLength = prefix.length();
        if (prefixLength > 0) {
            //one extra for colon
            maxOffset += prefixLength + 1;
        }
        return offset > tag.getAbsoluteStart() && offset < maxOffset;
    }

    public static boolean isDeclarationsTag(IMXMLTagData tag) {
        if (tag == null) {
            return false;
        }
        String shortName = tag.getShortName();
        if (shortName == null || !shortName.equals(IMXMLLanguageConstants.DECLARATIONS)) {
            return false;
        }
        String uri = tag.getURI();
        if (uri == null || !uri.equals(IMXMLLanguageConstants.NAMESPACE_MXML_2009)) {
            return false;
        }
        return true;
    }

    public static boolean needsNamespace(IMXMLTagData offsetTag, String prefix, String uri) {
        if (offsetTag == null) {
            return false;
        }
        PrefixMap prefixMap = offsetTag.getCompositePrefixMap();
        if (prefixMap == null) {
            return true;
        }
        String foundURI = prefixMap.getNamespaceForPrefix(prefix);
        if (foundURI == null) {
            return true;
        }
        return !foundURI.equals(uri);
    }

    public static IMXMLTagAttributeData getMXMLTagAttributeAtOffset(IMXMLTagData tag, int offset) {
        IMXMLTagAttributeData[] attributes = tag.getAttributeDatas();
        for (IMXMLTagAttributeData attributeData : attributes) {
            if (offset >= attributeData.getAbsoluteStart() && offset <= attributeData.getValueEnd()) {
                return attributeData;
            }
        }
        return null;
    }

    public static IMXMLTagAttributeData getMXMLTagAttributeWithNameAtOffset(IMXMLTagData tag, int offset,
            boolean includeEnd) {
        IMXMLTagAttributeData[] attributes = tag.getAttributeDatas();
        for (IMXMLTagAttributeData attributeData : attributes) {
            if (offset >= attributeData.getAbsoluteStart()) {
                if (includeEnd && offset <= attributeData.getAbsoluteEnd()) {
                    return attributeData;
                } else if (offset < attributeData.getAbsoluteEnd()) {
                    return attributeData;
                }
            }
        }
        return null;
    }

    public static IMXMLTagAttributeData getMXMLTagAttributeWithValueAtOffset(IMXMLTagData tag, int offset) {
        IMXMLTagAttributeData[] attributes = tag.getAttributeDatas();
        for (IMXMLTagAttributeData attributeData : attributes) {
            if (offset >= attributeData.getValueStart() && offset <= attributeData.getValueEnd()) {
                return attributeData;
            }
        }
        return null;
    }

    private static XMLName getXMLNameForTagWithFallback(IMXMLTagData tag) {
        XMLName xmlName = tag.getXMLName();
        //if the XML isn't valid, it's possible that the namespace for this tag
        //wasn't properly resolved. however, if we find the tag's prefix on the
        //root tag, we may be able to find the namespace manually
        if (xmlName.getXMLNamespace().length() == 0) {
            IMXMLData parent = tag.getParent();
            if (parent != null) {
                IMXMLTagData rootTag = parent.getRootTag();
                if (rootTag != null) {
                    PrefixMap prefixMap = rootTag.getPrefixMap();
                    //prefixMap may be null if there are no prefixes
                    if (prefixMap != null && prefixMap.containsPrefix(tag.getPrefix())) {
                        String ns = prefixMap.getNamespaceForPrefix(tag.getPrefix());
                        return new XMLName(ns, xmlName.getName());
                    }
                }
            }
        }
        return xmlName;
    }

    public static IDefinition getDefinitionForMXMLTag(IMXMLTagData tag, ILspProject project) {
        if (tag == null) {
            return null;
        }

        XMLName xmlName = getXMLNameForTagWithFallback(tag);
        IDefinition offsetDefinition = project.resolveXMLNameToDefinition(xmlName, tag.getMXMLDialect());
        if (offsetDefinition != null) {
            return offsetDefinition;
        }
        if (xmlName.getXMLNamespace().equals(tag.getMXMLDialect().getLanguageNamespace())) {
            for (String typeName : LANGUAGE_TYPE_NAMES) {
                if (tag.getShortName().equals(typeName)) {
                    return project.resolveQNameToDefinition(typeName);
                }
            }
        }
        IMXMLTagData parentTag = tag.getParentTag();
        if (parentTag == null) {
            return null;
        }
        XMLName parentXMLName = getXMLNameForTagWithFallback(parentTag);
        IDefinition parentDefinition = project.resolveXMLNameToDefinition(parentXMLName, parentTag.getMXMLDialect());
        if (parentDefinition == null || !(parentDefinition instanceof IClassDefinition)) {
            return null;
        }
        IClassDefinition classDefinition = (IClassDefinition) parentDefinition;
        return project.resolveSpecifier(classDefinition, tag.getShortName());
    }

    public static IDefinition getTypeDefinitionForMXMLTag(IMXMLTagData tag, ILspProject project) {
        IDefinition result = getDefinitionForMXMLTag(tag, project);
        if (result == null) {
            return null;
        }
        return result.resolveType(project);
    }

    public static IDefinition getDefinitionForMXMLTagAttribute(IMXMLTagData tag, int offset, boolean includeValue,
            ILspProject project) {
        IMXMLTagAttributeData attributeData = null;
        if (includeValue) {
            attributeData = getMXMLTagAttributeAtOffset(tag, offset);
        } else {
            attributeData = getMXMLTagAttributeWithNameAtOffset(tag, offset, false);
        }
        if (attributeData == null) {
            return null;
        }
        IDefinition tagDefinition = getDefinitionForMXMLTag(tag, project);
        if (tagDefinition != null && tagDefinition instanceof IClassDefinition) {
            IClassDefinition classDefinition = (IClassDefinition) tagDefinition;
            return project.resolveSpecifier(classDefinition, attributeData.getShortName());
        }
        return null;
    }

    public static IDefinition getTypeDefinitionForMXMLTagAttribute(IMXMLTagData tag, int offset, boolean includeValue,
            ILspProject project) {
        IDefinition result = getDefinitionForMXMLTagAttribute(tag, offset, includeValue, project);
        if (result == null) {
            return null;
        }
        return result.resolveType(project);
    }

    public static IDefinition getDefinitionForMXMLNameAtOffset(IMXMLTagData tag, int offset, ILspProject project) {
        if (tag.isOffsetInAttributeList(offset)) {
            return getDefinitionForMXMLTagAttribute(tag, offset, false, project);
        }
        return getDefinitionForMXMLTag(tag, project);
    }

    public static IDefinition getTypeDefinitionForMXMLNameAtOffset(IMXMLTagData tag, int offset, ILspProject project) {
        IDefinition result = getDefinitionForMXMLNameAtOffset(tag, offset, project);
        if (result == null) {
            return null;
        }
        return result.resolveType(project);
    }

    public static boolean isMXMLCodeIntelligenceAvailableForTag(IMXMLTagData tag) {
        if (tag.getXMLName().equals(tag.getMXMLDialect().resolveScript())) {
            //not available inside an <fx:Script> tag that isn't self-closing
            return tag.isEmptyTag();
        }
        return true;
    }

    public static IMXMLTagData findMXMLScriptTag(IMXMLTagData tagData) {
        //quick check
        if (tagData.getXMLName().equals(tagData.getMXMLDialect().resolveScript())) {
            return tagData;
        }
        //go to the root tag
        while (tagData.getParentTag() != null) {
            tagData = tagData.getParentTag();
        }
        return findMXMLScriptTagInternal(tagData);
    }

    private static IMXMLTagData findMXMLScriptTagInternal(IMXMLTagData tagData) {
        if (tagData.getXMLName().equals(tagData.getMXMLDialect().resolveScript())) {
            return tagData;
        }
        IMXMLTagData child = tagData.getFirstChild(true);
        while (child != null) {
            IMXMLTagData foundScript = findMXMLScriptTagInternal(child);
            if (foundScript != null) {
                return foundScript;
            }
            child = child.getNextSibling(true);
        }
        return null;
    }

    public static IMXMLTagData[] findMXMLScriptTags(IMXMLTagData tagData) {
        //go to the root tag
        while (tagData.getParentTag() != null) {
            tagData = tagData.getParentTag();
        }
        ArrayList<IMXMLTagData> result = new ArrayList<>();
        findMXMLScriptTagsInternal(tagData, result);
        return result.toArray(new IMXMLTagData[result.size()]);
    }

    private static void findMXMLScriptTagsInternal(IMXMLTagData tagData, List<IMXMLTagData> result) {
        if (tagData.getXMLName().equals(tagData.getMXMLDialect().resolveScript())) {
            result.add(tagData);
        }
        IMXMLTagData child = tagData.getFirstChild(true);
        while (child != null) {
            findMXMLScriptTagsInternal(child, result);
            child = child.getNextSibling(true);
        }
    }

    public static IMXMLTagData getOffsetMXMLTag(MXMLData mxmlData, int currentOffset) {
        if (mxmlData == null) {
            return null;
        }
        IMXMLUnitData unitData = mxmlData.findContainmentReferenceUnit(currentOffset);
        IMXMLUnitData currentUnitData = unitData;
        while (currentUnitData != null) {
            if (currentUnitData instanceof IMXMLTagData) {
                IMXMLTagData tagData = (IMXMLTagData) currentUnitData;
                return tagData;
            }
            currentUnitData = currentUnitData.getParentUnitData();
        }
        return null;
    }

    public static void findMXMLUnits(IMXMLTagData tagData, IDefinition definition, ILspProject project,
            List<ISourceLocation> result) {
        IDefinition tagDefinition = project.resolveXMLNameToDefinition(tagData.getXMLName(), tagData.getMXMLDialect());
        if (tagDefinition != null && definition == tagDefinition) {
            result.add(tagData);
        }
        if (tagDefinition instanceof IClassDefinition) {
            IClassDefinition classDefinition = (IClassDefinition) tagDefinition;
            IMXMLTagAttributeData[] attributes = tagData.getAttributeDatas();
            for (IMXMLTagAttributeData attributeData : attributes) {
                IDefinition attributeDefinition = project.resolveSpecifier(classDefinition,
                        attributeData.getShortName());
                if (attributeDefinition != null && definition == attributeDefinition) {
                    result.add(attributeData);
                }
            }
        }
        IMXMLTagData childTag = tagData.getFirstChild(true);
        while (childTag != null) {
            if (childTag.isCloseTag()) {
                //only open tags matter
                continue;
            }
            findMXMLUnits(childTag, definition, project, result);
            childTag = childTag.getNextSibling(true);
        }
    }
}