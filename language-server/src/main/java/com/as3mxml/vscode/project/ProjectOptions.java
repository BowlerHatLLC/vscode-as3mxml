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

import java.util.Arrays;
import java.util.List;

/**
 * Defines constants for all top-level fields of an asconfig.json file, and
 * stores the parsed values for those fields.
 */
public class ProjectOptions {
    public String type;
    public String config;

    /**
     * In an application project, the final file must be the main class. Each
     * path must be absolute and canonical, or there will be problems on Windows
     * where the drive letter could have different cases.
     */
    public String[] files;
    public List<String> compilerOptions;
    public List<String> additionalOptions;
    public List<String> additionalTokens;

    // while the following values are also included in the compiler options,
    // we need them available for other things in the language server
    public List<String> targets;

    public boolean equals(ProjectOptions other) {
        return other.type.equals(type) && other.config.equals(config) && Arrays.equals(other.files, files)
                && other.compilerOptions.equals(compilerOptions) && other.additionalOptions.equals(additionalOptions)
                && other.targets.equals(targets) && other.additionalTokens.equals(additionalTokens);
    }
}
