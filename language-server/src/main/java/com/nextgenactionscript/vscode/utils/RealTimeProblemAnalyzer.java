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
package com.nextgenactionscript.vscode.utils;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.royale.compiler.filespecs.IFileSpecification;
import org.apache.royale.compiler.problems.ICompilerProblem;
import org.apache.royale.compiler.problems.InternalCompilerProblem2;
import org.apache.royale.compiler.projects.ICompilerProject;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.apache.royale.compiler.units.requests.IABCBytesRequestResult;
import org.apache.royale.compiler.units.requests.IFileScopeRequestResult;
import org.apache.royale.compiler.units.requests.IOutgoingDependenciesRequestResult;
import org.apache.royale.compiler.units.requests.IRequest;
import org.apache.royale.compiler.units.requests.ISyntaxTreeRequestResult;
import org.apache.royale.compiler.workspaces.IWorkspace;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;

public class RealTimeProblemAnalyzer implements Runnable
{
	public RealTimeProblemAnalyzer()
	{

	}

	public CompilerProblemFilter compilerProblemFilter;
	public LanguageClient languageClient;

	private ICompilerProject project;

	public ICompilerProject getProject()
	{
		return project;
	}

	public void setProject(ICompilerProject value)
	{
		project = value;
	}

	private IFileSpecification fileSpec;

	public IFileSpecification getFileSpecification()
	{
		return fileSpec;
	}

	public void setFileSpecification(IFileSpecification value)
	{
		fileSpec = value;
	}

	private boolean fileChangedPending;

	public boolean getFileChangedPending()
	{
		return fileChangedPending;
	}

	public void setFileChangedPending(boolean value)
	{
		fileChangedPending = value;
	}

	private ICompilationUnit compilationUnit;

	public ICompilationUnit getCompilationUnit()
	{
		return compilationUnit;
	}

	public void setCompilationUnit(ICompilationUnit unit)
	{
		if(compilationUnit != null)
		{
			if(compilationUnit.equals(unit))
			{
				//the compilation unit hasn't changed
				return;
			}
			else
			{
				//if we're changing compilation units, force publish immediately
				publishDiagnostics();
			}
		}
		compilationUnit = unit;
		if(compilationUnit != null)
		{
			syntaxTreeRequest = compilationUnit.getSyntaxTreeRequest();
			fileScopeRequest = compilationUnit.getFileScopeRequest();
			outgoingDependenciesRequest = compilationUnit.getOutgoingDependenciesRequest();
			abcBytesRequest = compilationUnit.getABCBytesRequest();
		}
		else
		{
			syntaxTreeRequest = null;
			fileScopeRequest = null;
			outgoingDependenciesRequest = null;
			abcBytesRequest = null;
		}
	}

	private IRequest<ISyntaxTreeRequestResult, ICompilationUnit> syntaxTreeRequest;
	private IRequest<IFileScopeRequestResult, ICompilationUnit> fileScopeRequest;
	private IRequest<IOutgoingDependenciesRequestResult, ICompilationUnit> outgoingDependenciesRequest;
	private IRequest<IABCBytesRequestResult, ICompilationUnit> abcBytesRequest;

	private List<ICompilerProblem> problems = new ArrayList<>();

	public void run()
	{
		while(true)
		{
			if(compilationUnit == null)
			{
				break;
			}
			if(syntaxTreeRequest.isDone()
					&& fileScopeRequest.isDone()
					&& outgoingDependenciesRequest.isDone()
					&& abcBytesRequest.isDone())
			{
				publishDiagnostics();
				if(compilationUnit == null)
				{
					break;
				}
			}
			try
			{
				Thread.sleep(1);
			}
			catch(InterruptedException e)
			{
				//just ignore it
			}
		}
	}

	private void publishDiagnostics()
	{
        URI uri = Paths.get(compilationUnit.getAbsoluteFilename()).toUri();
        PublishDiagnosticsParams publish = new PublishDiagnosticsParams();
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        publish.setDiagnostics(diagnostics);
        publish.setUri(uri.toString());
		problems.clear();
        try
        {
            //if we pass in null, it's designed to ignore certain errors that
            //don't matter for IDE code intelligence.
			Collections.addAll(problems, syntaxTreeRequest.get().getProblems());
			Collections.addAll(problems, fileScopeRequest.get().getProblems());
			Collections.addAll(problems, outgoingDependenciesRequest.get().getProblems());
			ICompilerProblem[] probs = abcBytesRequest.get().getProblems();
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
		for (ICompilerProblem problem : problems)
		{
			if (compilerProblemFilter != null && !compilerProblemFilter.isAllowed(problem))
			{
				continue;
			}
			Diagnostic diagnostic = LanguageServerCompilerUtils.getDiagnosticFromCompilerProblem(problem);
			diagnostics.add(diagnostic);
		}
		problems.clear();

        if (languageClient != null)
        {
            languageClient.publishDiagnostics(publish);
		}
		ICompilationUnit unit = compilationUnit;
		compilationUnit = null;
		syntaxTreeRequest = null;
		fileScopeRequest = null;
		outgoingDependenciesRequest = null;
		abcBytesRequest = null;
		if(fileChangedPending)
		{
			fileChangedPending = false;
			IWorkspace workspace = project.getWorkspace();
			workspace.fileChanged(fileSpec);
			setCompilationUnit(unit);
		}
	}
}