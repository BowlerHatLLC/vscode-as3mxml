package com.as3mxml.vscode.providers;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import com.as3mxml.vscode.utils.FileTracker;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;

import org.apache.royale.formatter.FORMATTER;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

public class FormattingProvider {
    private FileTracker fileTracker;

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
