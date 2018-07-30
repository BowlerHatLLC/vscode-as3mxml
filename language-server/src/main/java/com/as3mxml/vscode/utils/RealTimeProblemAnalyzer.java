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
import org.apache.royale.compiler.filespecs.IFileSpecification;
import org.apache.royale.compiler.internal.projects.RoyaleProject;
import org.apache.royale.compiler.problems.ICompilerProblem;
import org.apache.royale.compiler.problems.InternalCompilerProblem2;
import org.apache.royale.compiler.units.ICompilationUnit;
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

	private WorkspaceFolderData folderData;

	public WorkspaceFolderData getWorkspaceFolderData()
	{
		return folderData;
	}

	public void setWorkspaceFolderData(WorkspaceFolderData value)
	{
		folderData = value;
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
				handleFileChangedPending();
				//if we're changing compilation units, publish immediately...
				//unless the project has been cleared because that means that
				//the compilation unit is no longer valid
				if (compilationUnit.getProject() != null)
				{
					publishDiagnostics();
				}
			}
		}
		compilationUnit = unit;
		if(unit != null)
		{
			//we don't need to save the return values of these methods. it's
			//enough to simply start the process
			unit.getSyntaxTreeRequest();
			unit.getFileScopeRequest();
			unit.getOutgoingDependenciesRequest();
			unit.getABCBytesRequest();
		}
	}

	public void run()
	{
		while(true)
		{
			ICompilationUnit unit = compilationUnit;
			if(unit == null)
			{
				break;
			}
			if(unit.getSyntaxTreeRequest().isDone()
					&& unit.getFileScopeRequest().isDone()
					&& unit.getOutgoingDependenciesRequest().isDone()
					&& unit.getABCBytesRequest().isDone())
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

	public void completePendingRequests()
	{
		ICompilationUnit unit = compilationUnit;
		if (unit == null)
		{
			//no compilation unit is being analyzed right now
			return;
		}
		handleFileChangedPending();
		try
		{
			unit.getSyntaxTreeRequest().get();
			unit.getFileScopeRequest().get();
			unit.getOutgoingDependenciesRequest().get();
			unit.getABCBytesRequest().get();
		}
		catch(InterruptedException e)
		{
		}
	}

	private void handleFileChangedPending()
	{
		if (!fileChangedPending)
		{
			return;
		}
		fileChangedPending = false;
		RoyaleProject project = folderData.project;
		IWorkspace workspace = project.getWorkspace();
		workspace.fileChanged(fileSpec);
	}

	private void publishDiagnostics()
	{
		ICompilationUnit unit = compilationUnit;
		if(unit == null)
		{
			return;
		}
		ProblemQuery problemQuery = new ProblemQuery(folderData.configurator.getCompilerProblemSettings());

		URI uri = Paths.get(unit.getAbsoluteFilename()).toUri();
        PublishDiagnosticsParams publish = new PublishDiagnosticsParams();
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        publish.setDiagnostics(diagnostics);
		publish.setUri(uri.toString());

		ArrayList<ICompilerProblem> problems = new ArrayList<>();
        try
        {
            //if we pass in null, it's designed to ignore certain errors that
            //don't matter for IDE code intelligence.
			Collections.addAll(problems, unit.getSyntaxTreeRequest().get().getProblems());
			Collections.addAll(problems, unit.getFileScopeRequest().get().getProblems());
			Collections.addAll(problems, unit.getOutgoingDependenciesRequest().get().getProblems());
			ICompilerProblem[] probs = unit.getABCBytesRequest().get().getProblems();
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

		//if the unit has changed, it happened by calling publishNow() and the
		//diagnostics should already be accurate
		if(!unit.equals(compilationUnit))
		{
			return;
		}
		if (languageClient != null)
        {
            languageClient.publishDiagnostics(publish);
		}
		compilationUnit = null;
		if (fileChangedPending)
		{
			handleFileChangedPending();
			setCompilationUnit(unit);
		}
	}
}