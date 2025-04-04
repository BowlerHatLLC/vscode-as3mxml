/*
Copyright 2016-2025 Bowler Hat LLC

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
package com.as3mxml.vscode.providers;

import java.util.Collections;
import java.util.List;

import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

import com.as3mxml.vscode.utils.ActionScriptProjectManager;
import com.as3mxml.vscode.utils.FileTracker;

public class InlayHintProvider {
    private ActionScriptProjectManager actionScriptProjectManager;
    private FileTracker fileTracker;

    public InlayHintProvider(ActionScriptProjectManager actionScriptProjectManager, FileTracker fileTracker) {
        this.actionScriptProjectManager = actionScriptProjectManager;
        this.fileTracker = fileTracker;
    }

    public List<InlayHint> inlayHint(InlayHintParams params, CancelChecker cancelToken) {
        if (cancelToken != null) {
            cancelToken.checkCanceled();
        }
        return Collections.emptyList();
    }
}