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

import org.apache.royale.compiler.css.ICSSNode;

public class CSSDocumentUtils {
	public static boolean containsWithStart(ICSSNode node, int offset) {
		return offset >= node.getAbsoluteStart() && offset <= node.getAbsoluteEnd();
	}

	public static ICSSNode getContainingCSSNodeIncludingStart(ICSSNode node, int offset) {
		if (!containsWithStart(node, offset)) {
			return null;
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
}
