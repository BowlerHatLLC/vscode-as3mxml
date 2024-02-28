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

import org.apache.royale.compiler.css.ICSSDocument;
import org.apache.royale.compiler.css.ICSSFontFace;
import org.apache.royale.compiler.css.ICSSNamespaceDefinition;
import org.apache.royale.compiler.css.ICSSNode;
import org.apache.royale.compiler.css.ICSSRule;

public class CSSDocumentUtils {
	public static boolean containsWithStart(ICSSNode node, int offset) {
		return offset >= node.getAbsoluteStart() && offset <= node.getAbsoluteEnd();
	}

	public static ICSSNode getContainingCSSNodeIncludingStart(ICSSDocument cssDocument, int offset) {
		for (ICSSNamespaceDefinition cssNamespace : cssDocument.getAtNamespaces()) {
			if (containsWithStart(cssNamespace, offset)) {
				return cssNamespace;
			}
		}
		for (ICSSFontFace cssFontFace : cssDocument.getFontFaces()) {
			if (containsWithStart(cssFontFace, offset)) {
				return cssFontFace;
			}
		}
		for (ICSSRule cssRule : cssDocument.getRules()) {
			if (containsWithStart(cssRule, offset)) {
				return cssRule;
			}
		}
		return cssDocument;
	}
}
