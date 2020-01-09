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

import org.apache.royale.compiler.config.CommandLineConfigurator;
import org.apache.royale.compiler.problems.CompilerProblem;
import org.apache.royale.compiler.problems.CompilerProblemSeverity;
import org.apache.royale.compiler.problems.annotations.DefaultSeverity;

@DefaultSeverity(CompilerProblemSeverity.WARNING)
public class SyntaxFallbackProblem extends CompilerProblem
{
    public static final String DESCRIPTION = "${reason}";
    
    public SyntaxFallbackProblem(String fileName, String reason)
    {
        super(fileName, -1, -1, 0, -1, !CommandLineConfigurator.SOURCE_COMMAND_LINE.equals(fileName));
        this.reason = reason;
    }
	
	public String reason;
}