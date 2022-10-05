package com.as3mxml.vscode.providers;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.royale.compiler.config.ConfigurationPathResolver;
import org.apache.royale.compiler.problems.ICompilerProblem;
import org.apache.royale.formatter.ASTokenFormatter;
import org.apache.royale.formatter.FormatterSettings;
import org.apache.royale.formatter.FormatterUtils;
import org.apache.royale.formatter.MXMLTokenFormatter;
import org.apache.royale.formatter.config.Configuration;
import org.apache.royale.formatter.config.Configurator;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

import com.as3mxml.vscode.formatter.VSCodeFormatterConfiguration;
import com.as3mxml.vscode.utils.FileTracker;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;

public class FormattingProvider {
    private static final String FILE_EXTENSION_AS = ".as";
    private static final String FILE_EXTENSION_MXML = ".mxml";

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
        VSCodeFormatterConfiguration.insertSpaces = options.isInsertSpaces();
        VSCodeFormatterConfiguration.tabSize = options.getTabSize();
        VSCodeFormatterConfiguration.insertFinalNewLine = options.isInsertFinalNewline();
        Configurator configurator = new Configurator(VSCodeFormatterConfiguration.class);
        ConfigurationPathResolver resolver = new ConfigurationPathResolver(System.getProperty("user.dir"));
        configurator.setConfigurationPathResolver(resolver);
        configurator.setConfiguration(new String[0], "files");
        Configuration configuration = configurator.getConfiguration();
        FormatterSettings settings = FormatterUtils.configurationToFormatterSettings(configuration);
        String formattedFileText = fileText;
        if (path.toString().endsWith(FILE_EXTENSION_MXML)) {
            MXMLTokenFormatter formatter = new MXMLTokenFormatter(settings);
            List<ICompilerProblem> problems = new ArrayList<>();
            formattedFileText = formatter.format(path.toString(), fileText, problems);
        } else if (path.toString().endsWith(FILE_EXTENSION_AS)) {
            ASTokenFormatter formatter = new ASTokenFormatter(settings);
            List<ICompilerProblem> problems = new ArrayList<>();
            formattedFileText = formatter.format(path.toString(), fileText, problems);
        }
        if (fileText.equals(formattedFileText)) {
            return Collections.emptyList();
        }
        int lastNewLineIndex = -1;
        int lastLineIndex = 0;
        int lastLineLength = 0;
        while (true) {
            int index = fileText.indexOf('\n', lastNewLineIndex + 1);
            if (index == -1) {
                lastLineLength = fileText.length() - (lastNewLineIndex + 1);
                break;
            }
            lastNewLineIndex = index;
            lastLineIndex++;
        }
        Range range = new Range(new Position(0, 0), new Position(lastLineIndex, lastLineLength));
        return Collections.singletonList(new TextEdit(range, formattedFileText));
    }
}
