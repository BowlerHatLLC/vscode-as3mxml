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
package com.as3mxml.vscode.utils;

import java.nio.file.Paths;

import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.mxml.IMXMLTextData;
import org.apache.royale.compiler.mxml.IMXMLUnitData;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IFileNode;
import org.apache.royale.compiler.tree.as.IPackageNode;

public class ImportRange
{
	public String uri = null;
	public int startIndex = -1;
	public int endIndex = -1;

    public static ImportRange fromOffsetTag(IMXMLTagData tagData, int currentOffset)
    {
        ImportRange range = new ImportRange();
        if (tagData == null)
        {
            return range;
        }

        if (tagData.getXMLName().equals(tagData.getMXMLDialect().resolveScript()))
        {
            IMXMLUnitData childData = tagData.getFirstChildUnit();
            while (childData != null)
            {
                if (childData instanceof IMXMLTextData)
                {
                    IMXMLTextData textData = (IMXMLTextData) childData;
                    if (textData.getTextType() == IMXMLTextData.TextType.CDATA)
                    {
                        range.uri = Paths.get(textData.getSourcePath()).toUri().toString();
                        range.startIndex = textData.getCompilableTextStart();
                        range.endIndex = textData.getCompilableTextEnd();
                    }
                }
                childData = childData.getNextSiblingUnit();
            }
        }
        return range;
    }

    public static ImportRange fromOffsetNode(IASNode offsetNode)
    {
        ImportRange range = new ImportRange();
        if (offsetNode == null)
        {
            return range;
        }
        IPackageNode packageNode = (IPackageNode) offsetNode.getAncestorOfType(IPackageNode.class);
        if (packageNode == null)
        {
            IFileNode fileNode = (IFileNode) offsetNode.getAncestorOfType(IFileNode.class);
            if (fileNode != null)
            {
                boolean foundPackage = false;
                for (int i = 0; i < fileNode.getChildCount(); i++)
                {
                    IASNode childNode = fileNode.getChild(i);
                    if (foundPackage)
                    {
                        //this is the node following the package
                        range.startIndex = childNode.getAbsoluteStart();
                        break;
                    }
                    if (childNode instanceof IPackageNode)
                    {
                        //use the start of the the next node after the
                        //package as the place where the import can be added
                        foundPackage = true;
                    }
                }
            }
        }
        else
        {
            range.endIndex = packageNode.getAbsoluteEnd();
        }
        range.uri = Paths.get(offsetNode.getSourcePath()).toUri().toString();
        return range;
    }
}