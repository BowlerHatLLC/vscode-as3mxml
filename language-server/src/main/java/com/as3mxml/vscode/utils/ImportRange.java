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

import java.nio.file.Paths;

import org.apache.royale.compiler.common.ISourceLocation;
import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.mxml.IMXMLTextData;
import org.apache.royale.compiler.mxml.IMXMLUnitData;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IFileNode;
import org.apache.royale.compiler.tree.as.IPackageNode;

public class ImportRange {
    public String uri = null;
    public int startIndex = -1;
    public int endIndex = -1;
    public boolean needsMXMLScript = false;
    public MXMLNamespace mxmlLanguageNS = null;

    public static ImportRange fromOffsetTag(IMXMLTagData tagData, int currentOffset) {
        ImportRange range = new ImportRange();
        if (tagData == null) {
            return range;
        }

        range.uri = Paths.get(tagData.getSourcePath()).toUri().toString();

        IMXMLTagData scriptTagData = MXMLDataUtils.findMXMLScriptTag(tagData);
        if (scriptTagData != null) {
            IMXMLTextData cdataTextData = null;
            IMXMLTextData fallbackTextData = null;
            IMXMLUnitData childData = scriptTagData.getFirstChildUnit();
            while (childData != null) {
                if (childData instanceof IMXMLTextData) {
                    IMXMLTextData textData = (IMXMLTextData) childData;
                    if (textData.getTextType() == IMXMLTextData.TextType.CDATA) {
                        cdataTextData = textData;
                        break;
                    } else if (fallbackTextData == null) {
                        if (textData.getTextType() == IMXMLTextData.TextType.TEXT
                                || textData.getTextType() == IMXMLTextData.TextType.WHITESPACE) {
                            fallbackTextData = textData;
                            // don't break, keep searching
                        }
                    }
                }
                childData = childData.getNextSiblingUnit();
            }
            if (cdataTextData != null) {
                range.startIndex = cdataTextData.getCompilableTextStart();
                range.endIndex = cdataTextData.getCompilableTextEnd();
            } else if (fallbackTextData != null) {
                range.startIndex = fallbackTextData.getCompilableTextStart();
                range.endIndex = fallbackTextData.getCompilableTextEnd();
            }
        } else {
            IMXMLTagData rootTag = tagData.getParent().getRootTag();
            if (rootTag.hasExplicitCloseTag()) {
                ISourceLocation rootChildRange = rootTag.getLocationOfChildUnits();
                range.startIndex = rootChildRange.getAbsoluteEnd();
            } else {
                range.startIndex = rootTag.getAbsoluteEnd();
            }
            range.endIndex = range.startIndex;
            range.needsMXMLScript = true;
        }
        range.mxmlLanguageNS = MXMLNamespaceUtils.getMXMLLanguageNamespace(tagData);
        return range;
    }

    public static ImportRange fromOffsetNode(IASNode offsetNode) {
        ImportRange range = new ImportRange();
        if (offsetNode == null) {
            return range;
        }
        String sourcePath = offsetNode.getSourcePath();
        if (sourcePath == null) {
            return range;
        }
        range.uri = Paths.get(sourcePath).toUri().toString();

        IPackageNode packageNode = (IPackageNode) ASTUtils.getSelfOrAncestorOfType(offsetNode, IPackageNode.class);
        if (packageNode != null) {
            // we're inside a package block
            range.endIndex = packageNode.getAbsoluteEnd();
            return range;
        }

        IFileNode fileNode = (IFileNode) ASTUtils.getSelfOrAncestorOfType(offsetNode, IFileNode.class);
        if (fileNode != null) {
            // we're probably after the package block
            boolean foundPackage = false;
            for (int i = 0; i < fileNode.getChildCount(); i++) {
                IASNode childNode = fileNode.getChild(i);
                if (foundPackage) {
                    // this is the node following the package
                    range.startIndex = childNode.getAbsoluteStart();
                    break;
                }
                if (childNode instanceof IPackageNode) {
                    // use the start of the the next node after the
                    // package as the place where the import can be added
                    foundPackage = true;
                }
            }
            return range;
        }
        return range;
    }
}