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

import org.apache.royale.compiler.constants.IASKeywordConstants;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.projects.ICompilerProject;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;

public class CompletionItemUtils {
    public static CompletionItem createDefinitionItem(IDefinition definition, ICompilerProject project) {
        CompletionItem item = new CompletionItem();
        item.setKind(LanguageServerCompilerUtils.getCompletionItemKindFromDefinition(definition));
        item.setDetail(DefinitionTextUtils.definitionToDetail(definition, project));
        item.setLabel(definition.getBaseName());
        String docs = DefinitionDocumentationUtils.getDocumentationForDefinition(definition, true,
                project.getWorkspace(), false);
        if (docs != null) {
            item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, docs));
        }
        return item;
    }

    public static CompletionItem createPackageBlockItem(String packageName, boolean asSnippet) {
        StringBuilder labelBuilder = new StringBuilder();
        labelBuilder.append(IASKeywordConstants.PACKAGE);
        if (packageName.length() > 0) {
            labelBuilder.append(" ");
            labelBuilder.append(packageName);
        }
        labelBuilder.append(" {}");

        StringBuilder insertTextBuilder = new StringBuilder();
        insertTextBuilder.append(IASKeywordConstants.PACKAGE);
        if (packageName.length() > 0) {
            insertTextBuilder.append(" ");
            insertTextBuilder.append(packageName);
        }
        insertTextBuilder.append("\n");
        insertTextBuilder.append("{");
        insertTextBuilder.append("\n");
        insertTextBuilder.append("\t");
        if (asSnippet) {
            insertTextBuilder.append("$0");
        }
        insertTextBuilder.append("\n");
        insertTextBuilder.append("}");

        CompletionItem packageItem = new CompletionItem();
        packageItem.setKind(CompletionItemKind.Module);
        packageItem.setLabel(labelBuilder.toString());
        packageItem.setInsertText(insertTextBuilder.toString());
        if (asSnippet) {
            packageItem.setInsertTextFormat(InsertTextFormat.Snippet);
        }
        return packageItem;
    }
}