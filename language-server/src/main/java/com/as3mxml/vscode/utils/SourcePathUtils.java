/*
Copyright 2016-2018 Bowler Hat LLC

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

import org.apache.royale.compiler.projects.IASProject;

public class SourcePathUtils
{
    public static String getPackageForDirectoryPath(Path directory, IASProject project)
    {
        //find the source path that the parent directory is inside
        //that way we can strip it down to just the package
        String basePath = null;
        for (File sourcePath : project.getSourcePath())
        {
            if (directory.startsWith(sourcePath.toPath()))
            {
                basePath = sourcePath.toPath().toString();
                break;
            }
        }
        if (basePath == null)
        {
            //we couldn't find the source path!
            return "";
        }

        String expectedPackage = directory.toString().substring(basePath.length());
        //replace / in path on Unix
        expectedPackage = expectedPackage.replaceAll("/", ".");
        //replaces \ in path on Windows
        expectedPackage = expectedPackage.replaceAll("\\\\", ".");
        if (expectedPackage.startsWith("."))
        {
            expectedPackage = expectedPackage.substring(1);
        }
        return expectedPackage;
    }

    public static boolean isInProjectSourcePath(Path path, IASProject project)
    {
		if (project == null)
		{
			return false;
		}
        for (File sourcePath : project.getSourcePath())
        {
			if (path.startsWith(sourcePath.toPath()))
			{
				return true;
			}
        }
        return false;
    }
}