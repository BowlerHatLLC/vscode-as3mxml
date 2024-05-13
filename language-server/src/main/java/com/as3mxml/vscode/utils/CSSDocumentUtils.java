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

import java.util.ArrayList;

import org.apache.royale.compiler.css.ICSSCombinator;
import org.apache.royale.compiler.css.ICSSDocument;
import org.apache.royale.compiler.css.ICSSFontFace;
import org.apache.royale.compiler.css.ICSSNamespaceDefinition;
import org.apache.royale.compiler.css.ICSSNode;
import org.apache.royale.compiler.css.ICSSProperty;
import org.apache.royale.compiler.css.ICSSPropertyValue;
import org.apache.royale.compiler.css.ICSSSelector;
import org.apache.royale.compiler.css.ICSSSelectorCondition;
import org.apache.royale.compiler.problems.ICompilerProblem;
import org.apache.royale.compiler.tree.mxml.IMXMLStyleNode;

import com.google.common.collect.ImmutableList;

public class CSSDocumentUtils {
	public static ICSSNamespaceDefinition getNamespaceForPrefix(String prefix, ICSSDocument cssDocument) {
		if (prefix == null) {
			prefix = "";
		}
		for (ICSSNamespaceDefinition cssNamespace : cssDocument.getAtNamespaces()) {
			if (prefix.equals(cssNamespace.getPrefix())) {
				return cssNamespace;
			}
		}
		return null;
	}

	public static ICSSNode getContainingCSSNodeIncludingStart(IMXMLStyleNode styleNode, int offset) {
		ICSSDocument cssDocument = styleNode.getCSSDocument(new ArrayList<ICompilerProblem>());
		if (cssDocument == null) {
			return null;
		}

		// ICSSNode absolute start/end locations are local to the IMXMLStyleNode
		// so we need to subtract the style node's real absolute content start
		return getContainingCSSNodeIncludingStart(cssDocument, offset - styleNode.getContentStart());
	}

	public static ICSSNode getContainingCSSNodeIncludingStart(ICSSNode node, int offset) {
		if (!containsWithStart(node, offset)) {
			return null;
		}
		// certain children don't appear with getNthChild()
		if (node instanceof ICSSProperty) {
			ICSSProperty cssProperty = (ICSSProperty) node;
			ICSSPropertyValue cssPropertyValue = cssProperty.getValue();
			if (cssPropertyValue != null) {
				ICSSNode result = getContainingCSSNodeIncludingStart(cssPropertyValue, offset);
				if (result != null) {
					return result;
				}
			}
		} else if (node instanceof ICSSCombinator) {
			ICSSCombinator cssCombinator = (ICSSCombinator) node;
			ICSSSelector cssSelector = cssCombinator.getSelector();
			if (cssSelector != null) {
				ICSSNode result = getContainingCSSNodeIncludingStart(cssSelector, offset);
				if (result != null) {
					return result;
				}
			}
		} else if (node instanceof ICSSSelector) {
			ICSSSelector cssSelector = (ICSSSelector) node;
			ICSSCombinator cssCombinator = cssSelector.getCombinator();
			if (cssCombinator != null) {
				ICSSNode result = getContainingCSSNodeIncludingStart(cssCombinator, offset);
				if (result != null) {
					return result;
				}
			}
			ImmutableList<ICSSSelectorCondition> cssConditions = cssSelector.getConditions();
			if (cssConditions != null) {
				for (ICSSSelectorCondition cssCondition : cssConditions) {
					ICSSNode result = getContainingCSSNodeIncludingStart(cssCondition, offset);
					if (result != null) {
						return result;
					}
				}
			}
		} else if (node instanceof ICSSFontFace) {
			ICSSFontFace cssFontFace = (ICSSFontFace) node;
			ImmutableList<ICSSProperty> cssProperties = cssFontFace.getProperties();
			if (cssProperties != null) {
				for (ICSSProperty cssProperty : cssProperties) {
					ICSSNode result = getContainingCSSNodeIncludingStart(cssProperty, offset);
					if (result != null) {
						return result;
					}
				}
			}
		}
		for (int i = 0, count = node.getArity(); i < count; i++) {
			ICSSNode child = node.getNthChild(i);
			if (child.getAbsoluteStart() == -1) {
				// the Royale compiler has a quirk where a node can have an
				// unknown offset, but its children have known offsets. this is
				// where we work around that...
				for (int j = 0, innerCount = child.getArity(); j < innerCount; j++) {
					ICSSNode innerChild = child.getNthChild(j);
					ICSSNode result = getContainingCSSNodeIncludingStart(innerChild, offset);
					if (result != null) {
						return result;
					}
				}
				continue;
			}
			ICSSNode result = getContainingCSSNodeIncludingStart(child, offset);
			if (result != null) {
				return result;
			}
		}
		return node;
	}

	private static boolean containsWithStart(ICSSNode node, int offset) {
		return offset >= node.getAbsoluteStart() && offset <= node.getAbsoluteEnd();
	}
}
