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
package com.as3mxml.vscode.compiler.problems;

import org.apache.royale.compiler.problems.CompilerProblem;

public class LSPFileNotFoundProblem extends CompilerProblem
{
    public static String DESCRIPTION =
        "File not found: ${file}";
   
    public static final int errorCode = 1457;

	public LSPFileNotFoundProblem(String filePath, String sourcePath)
    {
        super(sourcePath != null ? sourcePath : filePath);
        file = filePath;
    }
    
    /// Path to the file that was not found.
    public final String file;
}