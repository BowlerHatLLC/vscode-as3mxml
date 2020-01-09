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

import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.royale.compiler.common.IFileSpecificationGetter;
import org.apache.royale.compiler.constants.IASKeywordConstants;
import org.apache.royale.compiler.filespecs.FileSpecification;
import org.apache.royale.compiler.filespecs.IFileSpecification;
import org.apache.royale.compiler.internal.filespecs.StringFileSpecification;
import org.apache.royale.compiler.workspaces.IWorkspace;

/**
 * Returns instances of IFileSpecification to be used by the Apache Royale
 * compiler to get the contents of files. If a file is open, and it is being
 * edited (possibly with changes not saved to the file system), returns a
 * StringFileSpecification. StringFileSpecification stores the code in a String.
 * If a file is not open, and it's simply coming from the file system, returns a
 * FileSpecification. FileSpecification reads the actual file.
 */
public class LanguageServerFileSpecGetter implements IFileSpecificationGetter
{
    private static final String PACKAGE_WITHOUT_BRACES = "package ";
    private static final String FILE_EXTENSION_AS = ".as";

    public LanguageServerFileSpecGetter(IWorkspace workspace, FileTracker fileTracker)
    {
        this.workspace = workspace;
        this.fileTracker = fileTracker;
    }

    private FileTracker fileTracker;
    private IWorkspace workspace;

    public IWorkspace getWorkspace()
    {
        return workspace;
    }

    public void setWorkspace(IWorkspace value)
    {
        workspace = value;
    }

    public IFileSpecification getFileSpecification(String filePath)
    {
        Path path = Paths.get(filePath);
        if (fileTracker.isOpen(path))
        {
            String code = fileTracker.getText(path);
            if (filePath.endsWith(FILE_EXTENSION_AS))
            {
                code = fixPackageWithoutBraces(code);
            }
            return new StringFileSpecification(filePath, code);
        }
        return new FileSpecification(filePath);
    }

    /**
     * If the file only contains the package keyword followed by a space, the
     * compiler will return an IFileNode with no children.
     */
    private String fixPackageWithoutBraces(String code)
    {
        if (code.equals(IASKeywordConstants.PACKAGE))
        {
            return code + " {}";
        }
        if (code.equals(PACKAGE_WITHOUT_BRACES))
        {
            return code + "{}";
        }
        return code;
    }
}
