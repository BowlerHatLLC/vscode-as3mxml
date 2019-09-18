/*
Copyright 2016-2019 Bowler Hat LLC

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
import java.util.Set;

import com.as3mxml.vscode.project.ILspProject;
import com.as3mxml.vscode.project.WorkspaceFolderData;

import org.apache.royale.compiler.clients.problems.ProblemQuery;
import org.apache.royale.compiler.filespecs.IFileSpecification;
import org.apache.royale.compiler.problems.ICompilerProblem;
import org.apache.royale.compiler.problems.InternalCompilerProblem2;
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

public class WaitForBuildFinishRunner implements Runnable
{
	public WaitForBuildFinishRunner(ICompilationUnit unit, IFileSpecification fileSpec, WorkspaceFolderData folderData, LanguageClient languageClient, CompilerProblemFilter filter)
	{
		this.compilationUnit = unit;
		this.fileSpec = fileSpec;
		this.folderData = folderData;
		this.languageClient = languageClient;
		this.compilerProblemFilter = filter;
	}

	public CompilerProblemFilter compilerProblemFilter;
	public LanguageClient languageClient;
	private WorkspaceFolderData folderData;
	private IFileSpecification fileSpec;

	private ICompilationUnit compilationUnit;

	public ICompilationUnit getCompilationUnit()
	{
		return compilationUnit;
	}

	private IRequest<ISyntaxTreeRequestResult, ICompilationUnit> syntaxTreeRequest;
	private IRequest<IFileScopeRequestResult, ICompilationUnit> fileScopeRequest;
	private IRequest<IOutgoingDependenciesRequestResult, ICompilationUnit> outgoingDepsRequest;
	private IRequest<IABCBytesRequestResult, ICompilationUnit> abcBytesRequest;

	private boolean running = false;

	public boolean isRunning()
	{
		return running;
	}

	private boolean changed = false;

	public boolean getChanged()
	{
		return changed;
	}

	public void setChanged(IFileSpecification fileSpec)
	{
		changed = true;
		this.fileSpec = fileSpec;
	}

	private boolean cancelled = false;

	public boolean getCancelled()
	{
		return cancelled;
	}

	public void setCancelled()
	{
		if(cancelled)
		{
			//already canceled. no need to do it again.
			return;
		}
		cancelled = true;
		if(changed)
		{
			//make sure that the workspace has the latest changes because they
			//may have been queued up
			changed = false;	
			IWorkspace workspace = folderData.project.getWorkspace();
			workspace.fileChanged(fileSpec);
		}
		try
		{
			//force the compilation unit to finish building
			(syntaxTreeRequest = compilationUnit.getSyntaxTreeRequest()).get();
			(fileScopeRequest = compilationUnit.getFileScopeRequest()).get();
			(outgoingDepsRequest = compilationUnit.getOutgoingDependenciesRequest()).get();
			(abcBytesRequest = compilationUnit.getABCBytesRequest()).get();
		}
		catch(InterruptedException e) {}
	}

	public void run()
	{
		running = true;
		while (true)
		{
			if (cancelled)
			{
				break;
			}
			if (compilationUnit.getProject() == null)
			{
				//this compilation unit is no longer valid
				break;
			}
			if (syntaxTreeRequest == null)
			{
				syntaxTreeRequest = compilationUnit.getSyntaxTreeRequest();
			}
			if (fileScopeRequest == null)
			{
				fileScopeRequest = compilationUnit.getFileScopeRequest();
			}
			if (outgoingDepsRequest == null)
			{
				outgoingDepsRequest = compilationUnit.getOutgoingDependenciesRequest();
			}
			if (abcBytesRequest == null)
			{
				abcBytesRequest = compilationUnit.getABCBytesRequest();
			}
			if(syntaxTreeRequest.isDone()
					&& fileScopeRequest.isDone()
					&& outgoingDepsRequest.isDone()
					&& abcBytesRequest.isDone())
			{
				publishDiagnostics();
				syntaxTreeRequest = null;
				fileScopeRequest = null;
				outgoingDepsRequest = null;
				abcBytesRequest = null;
				if(!changed)
				{
					break;
				}
				changed = false;
				IWorkspace workspace = folderData.project.getWorkspace();
				workspace.fileChanged(fileSpec);
			}
			try
			{
				//wait a short time between checks
				//it's okay if problems are updated a little slowly
				Thread.sleep(100);
			}
			catch(InterruptedException e) {}
		}
		running = false;
	}

	private void publishDiagnostics()
	{
		ArrayList<Diagnostic> diagnostics = new ArrayList<>();
		ArrayList<ICompilerProblem> problems = new ArrayList<>();
        try
        {
			Collections.addAll(problems, syntaxTreeRequest.get().getProblems());
			Collections.addAll(problems, fileScopeRequest.get().getProblems());
			Collections.addAll(problems, outgoingDepsRequest.get().getProblems());
			ICompilerProblem[] probs = abcBytesRequest.get().getProblems();
			for (ICompilerProblem prob : probs)
			{
				if (!(prob instanceof InternalCompilerProblem2))
				{
					problems.add(prob);
				}
			}
			
			ILspProject project = folderData.project;
			Set<String> requiredImports = project.getQNamesOfDependencies(compilationUnit);
			ASTUtils.findUnusedImportProblems(syntaxTreeRequest.get().getAST(), requiredImports, problems);
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

		ProblemQuery problemQuery = new ProblemQuery(folderData.configurator.getCompilerProblemSettings());
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

		URI uri = Paths.get(compilationUnit.getAbsoluteFilename()).toUri();
        PublishDiagnosticsParams publish = new PublishDiagnosticsParams();
        publish.setDiagnostics(diagnostics);
		publish.setUri(uri.toString());
		if (cancelled)
		{
			return;
		}
		languageClient.publishDiagnostics(publish);
	}
}