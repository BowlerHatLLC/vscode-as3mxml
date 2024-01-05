/*
Copyright 2016-2024 Bowler Hat LLC

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

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.royale.compiler.config.Configuration;
import org.apache.royale.compiler.internal.projects.RoyaleProjectConfigurator;
import org.apache.royale.compiler.projects.IASProject;
import org.apache.royale.compiler.projects.IRoyaleProject;

public class SourcePathUtils {
    public static String getPackageForDirectoryPath(Path directory, IASProject project) {
        // find the source path that the parent directory is inside
        // that way we can strip it down to just the package
        String basePath = null;
        for (File sourcePath : project.getSourcePath()) {
            if (directory.startsWith(sourcePath.toPath())) {
                basePath = sourcePath.toPath().toString();
                break;
            }
        }
        if (basePath == null) {
            // we couldn't find the source path!
            return "";
        }

        String expectedPackage = directory.toString().substring(basePath.length());
        // replace / in path on Unix
        expectedPackage = expectedPackage.replaceAll("/", ".");
        // replaces \ in path on Windows
        expectedPackage = expectedPackage.replaceAll("\\\\", ".");
        if (expectedPackage.startsWith(".")) {
            expectedPackage = expectedPackage.substring(1);
        }
        return expectedPackage;
    }

    public static boolean isInProjectSourcePath(Path path, IASProject project, RoyaleProjectConfigurator configurator) {
        if (project == null) {
            return false;
        }
        for (File sourcePath : project.getSourcePath()) {
            if (path.startsWith(sourcePath.toPath())) {
                return true;
            }
        }
        if (configurator != null) {
            Configuration configuration = configurator.getConfiguration();
            for (String includedSource : configuration.getIncludeSources()) {
                if (path.startsWith(Paths.get(includedSource))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isInProjectLibraryPathOrExternalLibraryPath(Path path, IRoyaleProject project,
            Configuration configuration) {
        if (project == null || configuration == null) {
            return false;
        }
        List<String> libraryPaths = project.getCompilerLibraryPath(configuration);
        for (String libraryPath : libraryPaths) {
            if (path.startsWith(Paths.get(libraryPath))) {
                return true;
            }
        }
        List<String> externalLibraryPaths = project.getCompilerExternalLibraryPath(configuration);
        for (String externalLibraryPath : externalLibraryPaths) {
            if (path.startsWith(Paths.get(externalLibraryPath))) {
                return true;
            }
        }
        return false;
    }
}