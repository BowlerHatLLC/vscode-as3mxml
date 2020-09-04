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
package com.as3mxml.vscode.project;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.lsp4j.WorkspaceFolder;

/**
 * Creates an IProjectConfigStrategy
 */
public interface IProjectConfigStrategyFactory {
    @Deprecated
    /**
     * Create a project config strategy for a workspace folder.
     */
    default IProjectConfigStrategy create(WorkspaceFolder workspaceFolder) {
        Path projectPath = Paths.get(URI.create(workspaceFolder.getUri()));
        return create(projectPath, workspaceFolder);
    }

    /**
     * Create a project config strategy for a path inside a workspace folder.
     */
    IProjectConfigStrategy create(Path projectPath, WorkspaceFolder workspaceFolder);
}
