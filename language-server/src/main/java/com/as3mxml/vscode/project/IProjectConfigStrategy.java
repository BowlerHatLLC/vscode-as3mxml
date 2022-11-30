/*
Copyright 2016-2021 Bowler Hat LLC

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

import java.nio.file.Path;
import java.util.List;

import org.eclipse.lsp4j.WorkspaceFolder;

/**
 * Loads the configuration for a project.
 */
public interface IProjectConfigStrategy {
    /**
     * The project's workspace folder.
     */
    WorkspaceFolder getWorkspaceFolder();

    /**
     * The project's root path (which may not necessarily be the same as the
     * root of the workspace folder).
     */
    Path getProjectPath();

    /**
     * If the compiler reports a problem without a file path, use this value.
     */
    String getDefaultConfigurationProblemPath();

    /**
     * The path of the configuration file. May return null.
     */
    Path getConfigFilePath();

    /**
     * Indicates if the project configuration has changed.
     */
    boolean getChanged();

    /**
     * Forces the strategy to consider itself changed.
     */
    void forceChanged();

    /**
     * Returns the project configuration options.
     */
    ProjectOptions getOptions();

    /**
     * Sets extra tokens that get added to ProjectOptions
     */
    void setTokens(List<String> tokens);
}
