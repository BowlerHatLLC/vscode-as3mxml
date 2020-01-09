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
import org.apache.royale.compiler.tree.as.IASNode;
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

public class RealTimeProblemsChecker implements Runnable
{
	public RealTimeProblemsChecker(LanguageClient languageClient, CompilerProblemFilter filter)
	{
		this.languageClient = languageClient;
		this.compilerProblemFilter = filter;
	}

	public CompilerProblemFilter compilerProblemFilter;
	public LanguageClient languageClient;

	private WorkspaceFolderData pendingFolderData;
	private IFileSpecification pendingFileSpec;
	private ICompilationUnit pendingCompilationUnit;

	private WorkspaceFolderData folderData;
	private IFileSpecification fileSpec;
	private ICompilationUnit compilationUnit;
	
	public synchronized IFileSpecification getFileSpecification()
	{
		return fileSpec;
	}

	public synchronized void setFileSpecification(IFileSpecification newFileSpec)
	{
		pendingFolderData = folderData;
		pendingCompilationUnit = compilationUnit;
		pendingFileSpec = newFileSpec;
	}

	public synchronized void setCompilationUnit(ICompilationUnit compilationUnit, IFileSpecification fileSpec, WorkspaceFolderData folderData)
	{
		if(this.compilationUnit != null && this.compilationUnit != compilationUnit)
		{
			updateNow();
			pendingFolderData = folderData;
			pendingCompilationUnit = compilationUnit;
			pendingFileSpec = fileSpec;
			return;
		}
		this.folderData = folderData;
		this.compilationUnit = compilationUnit;
		this.fileSpec = fileSpec;
		pendingFolderData = null;
		pendingCompilationUnit = null;
		pendingFileSpec = null;
		syntaxTreeRequest = compilationUnit.getSyntaxTreeRequest();
		fileScopeRequest = compilationUnit.getFileScopeRequest();
		outgoingDepsRequest = compilationUnit.getOutgoingDependenciesRequest();
		abcBytesRequest = compilationUnit.getABCBytesRequest();
	}

	public synchronized void clear()
	{
		folderData = null;
		compilationUnit = null;
		fileSpec = null;
		pendingFolderData = null;
		pendingCompilationUnit = null;
		pendingFileSpec = null;
	}

	public synchronized void updateNow()
	{
		applyPending();
		if(compilationUnit == null)
		{
			return;
		}
		if(syntaxTreeRequest == null)
		{
			syntaxTreeRequest = compilationUnit.getSyntaxTreeRequest();
		}
		if(fileScopeRequest == null)
		{
			fileScopeRequest = compilationUnit.getFileScopeRequest();
		}
		if(outgoingDepsRequest == null)
		{
			outgoingDepsRequest = compilationUnit.getOutgoingDependenciesRequest();
		}
		if(abcBytesRequest == null)
		{
			abcBytesRequest = compilationUnit.getABCBytesRequest();
		}
		try
		{
			syntaxTreeRequest.get();
			fileScopeRequest.get();
			outgoingDepsRequest.get();
			abcBytesRequest.get();
		}
		catch(InterruptedException e) {}
	}

	private IRequest<ISyntaxTreeRequestResult, ICompilationUnit> syntaxTreeRequest;
	private IRequest<IFileScopeRequestResult, ICompilationUnit> fileScopeRequest;
	private IRequest<IOutgoingDependenciesRequestResult, ICompilationUnit> outgoingDepsRequest;
	private IRequest<IABCBytesRequestResult, ICompilationUnit> abcBytesRequest;

	private synchronized long getWaitTime()
	{
		if(compilationUnit != null)
		{
			return 100;
		}
		return 500;
	}

	public void run()
	{
		while (true)
		{
			if(Thread.currentThread().isInterrupted())
			{
				break;
			}
			checkForProblems();
			long waitTime = getWaitTime();
			try
			{
				//wait a short time between checks
				//it's okay if problems are updated a little slowly
				Thread.sleep(waitTime);
			}
			catch(InterruptedException e) {}
		}
	}

	private synchronized void checkForProblems()
	{
		if (compilationUnit == null)
		{
			return;
		}
		if (compilationUnit.getProject() == null)
		{
			//this compilation unit is no longer valid
			clear();
			return;
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
			if(pendingCompilationUnit != null)
			{
				applyPending();
			}
			else
			{
				clear();
			}
		}
	}

	private synchronized void applyPending()
	{
		if(pendingCompilationUnit == null)
		{
			return;
		}

		if(pendingCompilationUnit == compilationUnit)
		{
			IWorkspace workspace = folderData.project.getWorkspace();
			workspace.fileChanged(pendingFileSpec);
		}

		compilationUnit = pendingCompilationUnit;
		fileSpec = pendingFileSpec;
		folderData = pendingFolderData;
		pendingCompilationUnit = null;
		pendingFileSpec = null;
		pendingFolderData = null;

		syntaxTreeRequest = null;
		fileScopeRequest = null;
		outgoingDepsRequest = null;
		abcBytesRequest = null;
	}

	private synchronized void publishDiagnostics()
	{
		if(compilationUnit == null)
		{
			return;
		}
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
			IASNode ast = syntaxTreeRequest.get().getAST();
			ASTUtils.findUnusedImportProblems(ast, requiredImports, problems);
			//TODO: enable after royale-compiler provides the correct range
			//ASTUtils.findDisabledConfigConditionBlockProblems(ast, problems);
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
		languageClient.publishDiagnostics(publish);
	}
}