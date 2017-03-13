/*
Copyright 2016-2017 Bowler Hat LLC

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
package com.nextgenactionscript.vscode.project;

import java.util.Arrays;

/**
 * Defines constants for all top-level fields of an asconfig.json file, and
 * stores the parsed values for those fields. 
 */
public class ProjectOptions
{
    public static final String TYPE = "type";
    public static final String CONFIG = "config";
    public static final String FILES = "files";
    public static final String COMPILER_OPTIONS = "compilerOptions";
    public static final String ADDITIONAL_OPTIONS = "additionalOptions";

    public ProjectType type;
    public String config;
    public String[] files;
    public CompilerOptions compilerOptions;
    public String additionalOptions;

    public boolean equals(ProjectOptions other)
    {
        return other.type.equals(type)
                && other.config.equals(config)
                && Arrays.equals(other.files, files)
                && other.compilerOptions.equals(compilerOptions)
                && other.additionalOptions.equals(additionalOptions);
    }
}