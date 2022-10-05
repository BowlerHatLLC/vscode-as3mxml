package com.as3mxml.vscode.formatter;

import org.apache.royale.formatter.config.Configuration;

public class VSCodeFormatterConfiguration extends Configuration {
	// these default values can't be null
	public static boolean insertSpaces = false;
	public static int tabSize = 4;
	public static boolean insertFinalNewLine = false;

	// if any of these default values are null, they are ignored, and the
	// superclass defaults are used instead
	public static String semicolons = null;
	public static Boolean placeOpenBraceOnNewLine = null;
	public static Integer maxPreserveNewLines = null;
	public static Boolean mxmlAlignAttributes = null;
	public static Boolean mxmlInsertNewLineBetweenAttributes = null;
	public static Boolean insertSpaceAtStartOfLineComment = null;
	public static Boolean insertSpaceBeforeAndAfterBinaryOperators = null;
	public static Boolean insertSpaceAfterSemicolonInForStatements = null;
	public static Boolean insertSpaceAfterKeywordsInControlFlowStatements = null;
	public static Boolean insertSpaceAfterFunctionKeywordForAnonymousFunctions = null;
	public static Boolean insertSpaceBetweenMetadataAttributes = null;
	public static Boolean insertSpaceAfterCommaDelimiter = null;
	public static Boolean collapseEmptyBlocks = null;

	public VSCodeFormatterConfiguration() {
		super();
		setInsertSpaces(null, insertSpaces);
		setInsertFinalNewLine(null, insertFinalNewLine);
		setTabSize(null, tabSize);

		if (semicolons != null) {
			setSemicolons(null, semicolons);
		}
		if (placeOpenBraceOnNewLine != null) {
			setPlaceOpenBraceOnNewLine(null, placeOpenBraceOnNewLine);
		}
		if (maxPreserveNewLines != null) {
			setMaxPreserveNewLines(null, maxPreserveNewLines);
		}
		if (mxmlAlignAttributes != null) {
			setMxmlAlignAttributes(null, mxmlAlignAttributes);
		}
		if (mxmlInsertNewLineBetweenAttributes != null) {
			setMxmlInsertNewLineBetweenAttributes(null, mxmlInsertNewLineBetweenAttributes);
		}
		if (insertSpaceAtStartOfLineComment != null) {
			setInsertSpaceAtStartOfLineComment(null, insertSpaceAtStartOfLineComment);
		}
		if (insertSpaceBeforeAndAfterBinaryOperators != null) {
			setInsertSpaceBeforeAndAfterBinaryOperators(null, insertSpaceBeforeAndAfterBinaryOperators);
		}
		if (insertSpaceAfterSemicolonInForStatements != null) {
			setInsertSpaceAfterSemicolonInForStatements(null, insertSpaceAfterSemicolonInForStatements);
		}
		if (insertSpaceAfterKeywordsInControlFlowStatements != null) {
			setInsertSpaceAfterKeywordsInControlFlowStatements(null,
					insertSpaceAfterKeywordsInControlFlowStatements);
		}
		if (insertSpaceAfterFunctionKeywordForAnonymousFunctions != null) {
			setInsertSpaceAfterFunctionKeywordForAnonymousFunctions(null,
					insertSpaceAfterFunctionKeywordForAnonymousFunctions);
		}
		if (insertSpaceBetweenMetadataAttributes != null) {
			setInsertSpaceBetweenMetadataAttributes(null, insertSpaceBetweenMetadataAttributes);
		}
		if (insertSpaceAfterCommaDelimiter != null) {
			setInsertSpaceAfterCommaDelimiter(null, insertSpaceAfterCommaDelimiter);
		}
		if (collapseEmptyBlocks != null) {
			setCollapseEmptyBlocks(null, collapseEmptyBlocks);
		}
	}
}
