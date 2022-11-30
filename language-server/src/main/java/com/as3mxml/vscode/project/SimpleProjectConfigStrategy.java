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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.as3mxml.asconfigc.compiler.ProjectType;
import com.as3mxml.vscode.utils.ActionScriptSDKUtils;

import org.eclipse.lsp4j.WorkspaceFolder;

/**
 * Configures a simple project inside a workspace folder.
 */
public class SimpleProjectConfigStrategy implements IProjectConfigStrategy {
    private static final String PROPERTY_FRAMEWORK_LIB = "royalelib";
    private static final String CONFIG_ROYALE = "royale";
    private static final String CONFIG_FLEX = "flex";

    private Path projectPath;
    private WorkspaceFolder workspaceFolder;
    private boolean changed = true;
    private List<String> tokens = null;

    public SimpleProjectConfigStrategy(Path projectPath, WorkspaceFolder workspaceFolder) {
        this.projectPath = projectPath;
        this.workspaceFolder = workspaceFolder;
    }

    public String getDefaultConfigurationProblemPath() {
        return null;
    }

    public Path getProjectPath() {
        return projectPath;
    }

    public WorkspaceFolder getWorkspaceFolder() {
        return workspaceFolder;
    }

    public Path getConfigFilePath() {
        return null;
    }

    public boolean getChanged() {
        return changed;
    }

    public void forceChanged() {
        changed = true;
    }

    private List<Path> openPaths = new ArrayList<>();

    public void didOpen(Path path) {
        changed = true;
        openPaths.add(path);
    }

    public void didClose(Path path) {
        changed = true;
        openPaths.remove(path);
    }

    public ProjectOptions getOptions() {
        changed = false;

        if (openPaths.size() == 0) {
            return null;
        }

        Path sdkPath = Paths.get(System.getProperty(PROPERTY_FRAMEWORK_LIB));
        boolean isRoyale = ActionScriptSDKUtils.isRoyaleFramework(sdkPath);

        String config = CONFIG_FLEX;
        if (isRoyale) {
            config = CONFIG_ROYALE;
        }

        ArrayList<String> compilerOptions = new ArrayList<>();
        for (Path openPath : openPaths) {
            compilerOptions.add("--include-sources+=" + openPath);
        }

        // the output compiler option needs to be defined or there will be a
        // null pointer exception
        compilerOptions.add("--output=fake.swc");

        ArrayList<String> targets = null;
        if (isRoyale) {
            targets = new ArrayList<>();
            targets.add("JSRoyale");
        }

        ProjectOptions options = new ProjectOptions();
        options.type = ProjectType.LIB;
        options.config = config;
        options.files = null;
        options.compilerOptions = compilerOptions;
        options.additionalOptions = null;
        options.targets = targets;
        options.additionalTokens = tokens;
        return options;
    }

    public void setTokens(List<String> tokens) {
        this.tokens = tokens;
    }
}
