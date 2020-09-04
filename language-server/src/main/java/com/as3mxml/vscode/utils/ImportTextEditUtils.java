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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.royale.compiler.tree.as.IImportNode;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

public class ImportTextEditUtils {
    private static final Pattern organizeImportPattern = Pattern
            .compile("(?m)^([ \\t]*)import ((\\w+\\.)+\\w+(\\.\\*)?);?");
    private static final Pattern packagePattern = Pattern
            .compile("(?m)^package(?: [\\w\\.]+)*\\s*\\{(?:[ \\t]*[\\r\\n]+)+([ \\t]*)");

    protected static int organizeImportsFromStartIndex(String text, int startIndex, List<IImportNode> importsToRemove,
            Set<String> importsToAdd, List<TextEdit> edits) {
        Matcher importMatcher = organizeImportPattern.matcher(text);
        if (startIndex != -1) {
            importMatcher.region(startIndex, text.length());
        }
        //use a Set to avoid adding duplicate names
        Set<String> nameSet = new HashSet<>();
        if (importsToAdd != null && startIndex == 0) {
            //add our extra imports at the first available opportunity
            nameSet.addAll(importsToAdd);
        }
        String indent = "";
        int startImportsIndex = -1;
        int endImportsIndex = -1;
        int endIndex = -1;
        while (importMatcher.find()) {
            int matchIndex = importMatcher.start();
            if (startImportsIndex == -1) {
                startImportsIndex = matchIndex;
                int nextBlockOpenIndex = text.indexOf("{", startImportsIndex);
                int nextBlockCloseIndex = text.indexOf("}", startImportsIndex);
                endIndex = nextBlockOpenIndex;
                if (endIndex == -1 || (nextBlockCloseIndex != -1 && nextBlockCloseIndex < endIndex)) {
                    endIndex = nextBlockCloseIndex;
                }
                indent = importMatcher.group(1);
            }
            if (endIndex != -1 && matchIndex >= endIndex) {
                break;
            }
            endImportsIndex = matchIndex + importMatcher.group(0).length();
            String importName = importMatcher.group(2);
            boolean removeImport = false;
            if (importsToRemove != null) {
                for (IImportNode importNode : importsToRemove) {
                    int importStart = importNode.getAbsoluteStart();
                    if (importStart >= matchIndex && importStart < endImportsIndex
                            && importNode.getImportName().equals(importName)) {
                        removeImport = true;
                        break;
                    }
                }
            }
            if (!removeImport) {
                nameSet.add(importName);
            }
        }
        if (nameSet.size() == 0 && importsToRemove.size() == 0) {
            //nothing to organize
            return endIndex;
        }

        if (startImportsIndex == -1) {
            Matcher packageMatcher = packagePattern.matcher(text);
            packageMatcher.region(0, text.length());
            if (packageMatcher.find()) //found the package
            {
                indent = packageMatcher.group(1);
                startImportsIndex = packageMatcher.end() - indent.length();
            }
        }
        //make the Set a List and put them in alphabetical order
        List<String> names = new ArrayList<>(nameSet);
        Collections.sort(names);
        StringBuilder result = new StringBuilder();
        String previousFirstPart = null;
        for (int i = 0, count = names.size(); i < count; i++) {
            String name = names.get(i);
            String[] parts = name.split("\\.");
            String firstPart = parts[0];
            if (previousFirstPart == null) {
                previousFirstPart = firstPart;
            } else if (parts.length > 1 && !firstPart.equals(previousFirstPart)) {
                //add an extra line when the first part of the package name
                //is different than the previous import
                result.append("\n");
                previousFirstPart = firstPart;
            }
            if (i > 0) {
                result.append("\n");
            }
            result.append(indent);
            result.append("import ");
            result.append(name);
            result.append(";");
        }

        if (endImportsIndex == -1) {
            endImportsIndex = startImportsIndex;
            if (nameSet.size() > 0) {
                //we're only adding new imports, so add some extra whitespace
                result.append("\n\n");
            }
        }

        TextEdit edit = new TextEdit();
        edit.setNewText(result.toString());
        Position start = LanguageServerCompilerUtils.getPositionFromOffset(new StringReader(text), startImportsIndex);
        Position end = LanguageServerCompilerUtils.getPositionFromOffset(new StringReader(text), endImportsIndex);
        edit.setRange(new Range(start, end));
        edits.add(edit);
        return endIndex;
    }

    public static List<TextEdit> organizeImports(String text) {
        return organizeImports(text, null, null);
    }

    public static List<TextEdit> organizeImports(String text, List<IImportNode> importsToRemove,
            Set<String> importsToAdd) {
        List<TextEdit> edits = new ArrayList<>();
        int index = 0;
        do {
            index = organizeImportsFromStartIndex(text, index, importsToRemove, importsToAdd, edits);
        } while (index != -1);
        return edits;
    }
}