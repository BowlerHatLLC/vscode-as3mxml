package com.as3mxml.vscode.providers;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import com.as3mxml.vscode.utils.FileTracker;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;

import org.apache.royale.formatter.FORMATTER;
import org.apache.royale.formatter.config.Semicolons;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class FormattingProvider {
    private FileTracker fileTracker;

    public String semicolons = null;
    public Boolean placeOpenBraceOnNewLine = null;
    public Integer maxPreserveNewLines = null;
    public Boolean mxmlAlignAttributes = null;
    public Boolean mxmlInsertNewLineBetweenAttributes = null;
    public Boolean insertSpaceAtStartOfLineComment = null;
    public Boolean insertSpaceBeforeAndAfterBinaryOperators = null;
    public Boolean insertSpaceAfterSemicolonInForStatements = null;
    public Boolean insertSpaceAfterKeywordsInControlFlowStatements = null;
    public Boolean insertSpaceAfterFunctionKeywordForAnonymousFunctions = null;
    public Boolean insertSpaceBetweenMetadataAttributes = null;
    public Boolean insertSpaceAfterCommaDelimiter = null;
    public Boolean collapseEmptyBlocks = null;

    public FormattingProvider(FileTracker fileTracker) {
        this.fileTracker = fileTracker;
    }

    public List<? extends TextEdit> formatting(DocumentFormattingParams params, CancelChecker cancelToken) {
        if (cancelToken != null) {
            cancelToken.checkCanceled();
        }
        TextDocumentIdentifier textDocument = params.getTextDocument();
        FormattingOptions options = params.getOptions();
        Path path = LanguageServerCompilerUtils.getPathFromLanguageServerURI(textDocument.getUri());
        if (path == null) {
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return Collections.emptyList();
        }
        String fileText = fileTracker.getText(path);
        if (fileText == null) {
            if (cancelToken != null) {
                cancelToken.checkCanceled();
            }
            return Collections.emptyList();
        }
        FORMATTER formatter = new FORMATTER();
        formatter.insertSpaces = options.isInsertSpaces();
        formatter.tabSize = options.getTabSize();
        if (semicolons != null) {
            formatter.semicolons = Semicolons.valueOf(semicolons.toUpperCase());
        }
        if (placeOpenBraceOnNewLine != null) {
            formatter.placeOpenBraceOnNewLine = placeOpenBraceOnNewLine;
        }
        if (maxPreserveNewLines != null) {
            formatter.maxPreserveNewLines = maxPreserveNewLines;
        }
        if (mxmlAlignAttributes != null) {
            formatter.mxmlAlignAttributes = mxmlAlignAttributes;
        }
        if (mxmlInsertNewLineBetweenAttributes != null) {
            formatter.mxmlInsertNewLineBetweenAttributes = mxmlInsertNewLineBetweenAttributes;
        }
        if (insertSpaceAtStartOfLineComment != null) {
            formatter.insertSpaceAtStartOfLineComment = insertSpaceAtStartOfLineComment;
        }
        if (insertSpaceBeforeAndAfterBinaryOperators != null) {
            formatter.insertSpaceBeforeAndAfterBinaryOperators = insertSpaceBeforeAndAfterBinaryOperators;
        }
        if (insertSpaceAfterSemicolonInForStatements != null) {
            formatter.insertSpaceAfterSemicolonInForStatements = insertSpaceAfterSemicolonInForStatements;
        }
        if (insertSpaceAfterKeywordsInControlFlowStatements != null) {
            formatter.insertSpaceAfterKeywordsInControlFlowStatements = insertSpaceAfterKeywordsInControlFlowStatements;
        }
        if (insertSpaceAfterFunctionKeywordForAnonymousFunctions != null) {
            formatter.insertSpaceAfterFunctionKeywordForAnonymousFunctions = insertSpaceAfterFunctionKeywordForAnonymousFunctions;
        }
        if (insertSpaceBetweenMetadataAttributes != null) {
            formatter.insertSpaceBetweenMetadataAttributes = insertSpaceBetweenMetadataAttributes;
        }
        if (insertSpaceAfterCommaDelimiter != null) {
            formatter.insertSpaceAfterCommaDelimiter = insertSpaceAfterCommaDelimiter;
        }
        if (collapseEmptyBlocks != null) {
            formatter.collapseEmptyBlocks = collapseEmptyBlocks;
        }
        String formattedFileText = formatter.formatFileText(path.toString(), fileText);
        if (fileText.equals(formattedFileText)) {
            return Collections.emptyList();
        }
        String[] lines = fileText.split("\n");
        String lastLine = lines[lines.length - 1];
        Range range = new Range(new Position(0, 0), new Position(lines.length - 1, lastLine.length()));
        return Collections.singletonList(new TextEdit(range, formattedFileText));
    }
}
