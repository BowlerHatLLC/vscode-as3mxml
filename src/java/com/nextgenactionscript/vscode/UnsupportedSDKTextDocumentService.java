/*
Copyright 2016 Bowler Hat LLC

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
package com.nextgenactionscript.vscode;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import io.typefox.lsapi.CodeActionParams;
import io.typefox.lsapi.CodeLens;
import io.typefox.lsapi.CodeLensParams;
import io.typefox.lsapi.Command;
import io.typefox.lsapi.CompletionItem;
import io.typefox.lsapi.CompletionList;
import io.typefox.lsapi.DidChangeTextDocumentParams;
import io.typefox.lsapi.DidCloseTextDocumentParams;
import io.typefox.lsapi.DidOpenTextDocumentParams;
import io.typefox.lsapi.DidSaveTextDocumentParams;
import io.typefox.lsapi.DocumentFormattingParams;
import io.typefox.lsapi.DocumentHighlight;
import io.typefox.lsapi.DocumentOnTypeFormattingParams;
import io.typefox.lsapi.DocumentRangeFormattingParams;
import io.typefox.lsapi.DocumentSymbolParams;
import io.typefox.lsapi.Hover;
import io.typefox.lsapi.Location;
import io.typefox.lsapi.MessageType;
import io.typefox.lsapi.PublishDiagnosticsParams;
import io.typefox.lsapi.ReferenceParams;
import io.typefox.lsapi.RenameParams;
import io.typefox.lsapi.SignatureHelp;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.TextDocumentPositionParams;
import io.typefox.lsapi.TextEdit;
import io.typefox.lsapi.WorkspaceEdit;
import io.typefox.lsapi.impl.MessageParamsImpl;
import io.typefox.lsapi.services.TextDocumentService;

/**
 * Used when the supplied version of the Apache FlexJS compiler is not valid.
 * Does nothing except inform the user that they need to switch to a supported
 * version of Apache FlexJS.
 */
public class UnsupportedSDKTextDocumentService implements TextDocumentService
{
    private ActionScriptLanguageServer server;
    boolean hasShownMinVersionError = false;

    public UnsupportedSDKTextDocumentService(ActionScriptLanguageServer server)
    {
        this.server = server;
    }

    @Override
    public CompletableFuture<CompletionList> completion(TextDocumentPositionParams textDocumentPositionParams)
    {
        return null;
    }

    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem completionItem)
    {
        return null;
    }

    @Override
    public CompletableFuture<Hover> hover(TextDocumentPositionParams textDocumentPositionParams)
    {
        return null;
    }

    @Override
    public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams textDocumentPositionParams)
    {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams textDocumentPositionParams)
    {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams referenceParams)
    {
        return null;
    }

    @Override
    public CompletableFuture<DocumentHighlight> documentHighlight(TextDocumentPositionParams textDocumentPositionParams)
    {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams documentSymbolParams)
    {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams codeActionParams)
    {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams codeLensParams)
    {
        return null;
    }

    @Override
    public CompletableFuture<CodeLens> resolveCodeLens(CodeLens codeLens)
    {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams documentFormattingParams)
    {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams documentRangeFormattingParams)
    {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams documentOnTypeFormattingParams)
    {
        return null;
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams renameParams)
    {
        return null;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams didOpenTextDocumentParams)
    {
        if (!hasShownMinVersionError)
        {
            hasShownMinVersionError = true;
            MessageParamsImpl m = new MessageParamsImpl();
            m.setMessage("Expected Apache FlexJS SDK 0.7.0 or newer. Found: " + server.getFlexJSVersion() + " instead.");
            m.setType(MessageType.Error);
            server.showMessage(m);
        }
    }

    @Override
    public void didChange(DidChangeTextDocumentParams didChangeTextDocumentParams)
    {

    }

    @Override
    public void didClose(DidCloseTextDocumentParams didCloseTextDocumentParams)
    {

    }

    @Override
    public void didSave(DidSaveTextDocumentParams didSaveTextDocumentParams)
    {

    }

    @Override
    public void onPublishDiagnostics(Consumer<PublishDiagnosticsParams> consumer)
    {

    }
}
