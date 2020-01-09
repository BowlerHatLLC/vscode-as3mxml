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

import java.nio.file.Paths;

import org.apache.royale.compiler.mxml.IMXMLData;
import org.apache.royale.compiler.mxml.IMXMLTagAttributeData;
import org.apache.royale.compiler.mxml.IMXMLTagData;

public class XmlnsRange
{
	public String uri = null;
	public int startIndex = -1;
	public int endIndex = -1;

    public static XmlnsRange fromOffsetTag(IMXMLTagData tagData, int currentOffset)
    {
		XmlnsRange range = new XmlnsRange();
		if (tagData == null)
		{
			return range;
		}

		IMXMLData mxmlData = tagData.getParent();
		
		IMXMLTagData rootTag = mxmlData.getRootTag();
        if (rootTag == null)
        {
            return null;
        }

		int startIndex = -1;
		int endIndex = -1;
        IMXMLTagAttributeData[] attributeDatas = rootTag.getAttributeDatas();
        for (IMXMLTagAttributeData attributeData : attributeDatas)
        {
            if (!attributeData.getName().startsWith("xmlns"))
            {
                if (startIndex == -1)
                {
                    startIndex = attributeData.getStart();
                    endIndex = startIndex;
                }
                break;
            }
            int start = attributeData.getAbsoluteStart();
            int end = attributeData.getValueEnd() + 1;
            if (startIndex == -1 || startIndex > start)
            {
                startIndex = start;
            }
            if (endIndex == -1 || endIndex < end)
            {
                endIndex = end;
            }
		}
		range.startIndex = startIndex;
		range.endIndex = endIndex;
		range.uri = Paths.get(tagData.getSourcePath()).toUri().toString();

        return range;
    }
}