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

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;

import com.as3mxml.vscode.project.WorkspaceFolderData;

import org.apache.royale.compiler.clients.problems.ProblemQuery;
import org.apache.royale.compiler.problems.ICompilerProblem;
import org.apache.royale.compiler.problems.InternalCompilerProblem2;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;

public class WaitForBuildFinishRunner implements Runnable
{
	public WaitForBuildFinishRunner(ICompilationUnit unit, WorkspaceFolderData folderData, LanguageClient languageClient, CompilerProblemFilter filter)
	{
		this.compilationUnit = unit;
		this.folderData = folderData;
		this.languageClient = languageClient;
		this.compilerProblemFilter = filter;
	}

	public CompilerProblemFilter compilerProblemFilter;
	public LanguageClient languageClient;
	private WorkspaceFolderData folderData;
	private ICompilationUnit compilationUnit;

	public void run()
	{
		ProblemQuery problemQuery = new ProblemQuery(folderData.configurator.getCompilerProblemSettings());

		URI uri = Paths.get(compilationUnit.getAbsoluteFilename()).toUri();
        PublishDiagnosticsParams publish = new PublishDiagnosticsParams();
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        publish.setDiagnostics(diagnostics);
		publish.setUri(uri.toString());

		ArrayList<ICompilerProblem> problems = new ArrayList<>();
        try
        {
            //if we pass in null, it's designed to ignore certain errors that
            //don't matter for IDE code intelligence.
			Collections.addAll(problems, compilationUnit.getSyntaxTreeRequest().get().getProblems());
			Collections.addAll(problems, compilationUnit.getFileScopeRequest().get().getProblems());
			Collections.addAll(problems, compilationUnit.getOutgoingDependenciesRequest().get().getProblems());
			ICompilerProblem[] probs = compilationUnit.getABCBytesRequest().get().getProblems();
			for (ICompilerProblem prob : probs)
			{
				if (!(prob instanceof InternalCompilerProblem2))
				{
					problems.add(prob);
				}
			}
        }
        catch (Exception e)
        {
            System.err.println("Exception in compiler while checking for problems: " + e);
            e.printStackTrace(System.err);

            Diagnostic diagnostic = LSPUtils.createDiagnosticWithoutRange();
            diagnostic.setSeverity(DiagnosticSeverity.Error);
            diagnostic.setMessage("A fatal error occurred while checking a file for problems: " + compilationUnit.getAbsoluteFilename());
			diagnostics.add(diagnostic);
		}
		problemQuery.addAll(problems);
		for (ICompilerProblem problem : problemQuery.getFilteredProblems())
		{
			if (compilerProblemFilter != null && !compilerProblemFilter.isAllowed(problem))
			{
				continue;
			}
			Diagnostic diagnostic = LanguageServerCompilerUtils.getDiagnosticFromCompilerProblem(problem);
			diagnostics.add(diagnostic);
		}

		languageClient.publishDiagnostics(publish);
	}
}