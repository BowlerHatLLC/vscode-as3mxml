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

import java.util.Comparator;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

/**
 * Utility functions for converting between language server types and Flex
 * compiler types.
 */
public class LSPUtils {
	private static final Comparator<Position> POSITION_COMPARATOR = (Position p1, Position p2) -> {
		if (p1.getLine() != p2.getLine()) {
			return p1.getLine() - p2.getLine();
		}
		return p1.getCharacter() - p2.getCharacter();
	};

	public static boolean rangesIntersect(Range r1, Range r2) {
		if (r1 == null || r2 == null) {
			return false;
		}
		int resultStartLine = r1.getStart().getLine();
		int resultStartChar = r1.getStart().getCharacter();
		int resultEndLine = r1.getEnd().getLine();
		int resultEndChar = r1.getEnd().getCharacter();
		int otherStartLine = r2.getStart().getLine();
		int otherStartChar = r2.getStart().getCharacter();
		int otherEndLine = r2.getEnd().getLine();
		int otherEndChar = r2.getEnd().getCharacter();
		if (resultStartLine < otherStartLine) {
			resultStartLine = otherStartLine;
			resultStartChar = otherStartChar;
		} else if (resultStartLine == otherStartLine && resultStartChar < otherStartChar) {
			resultStartChar = otherStartChar;
		}
		if (resultEndLine > otherEndLine) {
			resultEndLine = otherEndLine;
			resultEndChar = otherEndChar;
		} else if (resultEndLine == otherEndLine && resultEndChar < otherEndChar) {
			resultEndChar = otherEndChar;
		}
		if (resultStartLine > resultEndLine) {
			return false;
		}
		if (resultStartLine == resultEndLine && resultStartChar > resultEndChar) {
			return false;
		}
		return true;
	}

	public static boolean rangeContains(Range range, Position position) {
		return POSITION_COMPARATOR.compare(position, range.getStart()) >= 0
				&& POSITION_COMPARATOR.compare(position, range.getEnd()) <= 0;
	}

	public static Diagnostic createDiagnosticWithoutRange() {
		Diagnostic diagnostic = new Diagnostic();
		Range range = new Range();
		range.setStart(new Position());
		range.setEnd(new Position());
		diagnostic.setRange(range);
		return diagnostic;
	}
}