/*
Copyright 2016-2021 Bowler Hat LLC

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
package com.as3mxml.vscode.project;

import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.util.HashMap;
import java.util.Map;

import com.as3mxml.vscode.utils.CompilationUnitUtils.IncludeFileData;
import com.as3mxml.vscode.utils.LanguageServerCompilerUtils;
import com.as3mxml.vscode.utils.ProblemTracker;

import org.apache.royale.compiler.internal.projects.RoyaleProjectConfigurator;
import org.eclipse.lsp4j.WorkspaceFolder;

public class ActionScriptProjectData {
	public ActionScriptProjectData(Path projectRoot, WorkspaceFolder folder, IProjectConfigStrategy config) {
		this.projectRoot = projectRoot;
		this.folder = folder;
		this.config = config;

		// the project's depth determines its priority when determining if it
		// contains a particular source file. the deeper, the better.
		Path folderPath = LanguageServerCompilerUtils.getPathFromLanguageServerURI(folder.getUri());
		projectDepth = 0;
		Path currentPath = projectRoot;
		while (currentPath.startsWith(folderPath) && !currentPath.equals(folderPath)) {
			projectDepth++;
			currentPath = currentPath.getParent();
		}
	}

	public Path projectRoot;
	public WorkspaceFolder folder;
	public int projectDepth;
	public IProjectConfigStrategy config;
	public ProjectOptions options;
	public ILspProject project;
	//needed for ProblemQuery filtering
	public RoyaleProjectConfigurator configurator;
	public Map<WatchKey, Path> sourceOrLibraryPathWatchKeys = new HashMap<>();
	public ProblemTracker codeProblemTracker = new ProblemTracker();
	public ProblemTracker configProblemTracker = new ProblemTracker();
	public Map<String, IncludeFileData> includedFiles = new HashMap<>();

	public void cleanup() {
		if (project != null) {
			project.delete();
			project = null;
		}

		for (WatchKey watchKey : sourceOrLibraryPathWatchKeys.keySet()) {
			watchKey.cancel();
		}
		sourceOrLibraryPathWatchKeys.clear();

		configurator = null;
	}
}