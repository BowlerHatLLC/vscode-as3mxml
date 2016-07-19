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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.flex.compiler.common.IFileSpecificationGetter;
import org.apache.flex.compiler.filespecs.FileSpecification;
import org.apache.flex.compiler.filespecs.IFileSpecification;
import org.apache.flex.compiler.internal.filespecs.StringFileSpecification;
import org.apache.flex.compiler.workspaces.IWorkspace;

/**
 * If a file is open in the editor, use the open file instead of the real file
 * that may not have the latest changes saved.
 */
public class LanguageServerFileSpecGetter implements IFileSpecificationGetter
{
    public LanguageServerFileSpecGetter(IWorkspace workspace, Map<Path, String> sourceByPath)
    {
        this.workspace = workspace;
        this.sourceByPath = sourceByPath;
    }

    private Map<Path, String> sourceByPath;
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
        if (sourceByPath.containsKey(path))
        {
            String code = sourceByPath.get(path);
            return new StringFileSpecification(filePath, code);
        }
        return new FileSpecification(filePath);
    }
}
