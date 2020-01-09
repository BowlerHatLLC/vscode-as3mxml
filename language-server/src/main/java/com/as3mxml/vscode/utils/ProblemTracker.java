/*
Copyright 2016-2020 Bowler Hat LLC

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

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;

import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;

/**
 * Tracks files that have previously had problems so that they can be cleared
 * when the problems are fixed.
 */
public class ProblemTracker
{
    private LanguageClient languageClient;
    private HashSet<URI> newFilesWithProblems = new HashSet<>();
    private HashSet<URI> staleFilesWithProblems = new HashSet<>();

    public ProblemTracker()
    {
    }

    public LanguageClient getLanguageClient()
    {
        return languageClient;
    }

    public void setLanguageClient(LanguageClient value)
    {
        languageClient = value;
    }

    public void trackFileWithProblems(URI uri)
    {
        newFilesWithProblems.add(uri);
        staleFilesWithProblems.remove(uri);
    }

    public void makeStale()
    {
        staleFilesWithProblems.addAll(newFilesWithProblems);
        newFilesWithProblems.clear();
    }

    public void releaseStale()
    {
        //if any files have been removed, they will still appear in this set, so
        //clear the errors so that they don't persist
        for (URI uri : staleFilesWithProblems)
        {
            PublishDiagnosticsParams publish = new PublishDiagnosticsParams();
            publish.setDiagnostics(new ArrayList<>());
            publish.setUri(uri.toString());
            if (languageClient != null)
            {
                languageClient.publishDiagnostics(publish);
            }
        }
        staleFilesWithProblems.clear();
        HashSet<URI> temp = newFilesWithProblems;
        newFilesWithProblems = staleFilesWithProblems;
        staleFilesWithProblems = temp;
    }
}
