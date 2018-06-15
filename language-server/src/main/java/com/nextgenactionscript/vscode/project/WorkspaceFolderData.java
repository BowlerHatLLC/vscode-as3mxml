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
package com.nextgenactionscript.vscode.project;

import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.nextgenactionscript.vscode.utils.ProblemTracker;

import org.apache.royale.compiler.internal.projects.RoyaleProject;
import org.apache.royale.compiler.units.IInvisibleCompilationUnit;
import org.eclipse.lsp4j.WorkspaceFolder;

public class WorkspaceFolderData
{
	public WorkspaceFolderData(WorkspaceFolder folder, IProjectConfigStrategy config)
	{
		this.folder = folder;
		this.config = config;
	}

	public WorkspaceFolder folder;
	public IProjectConfigStrategy config;
	public RoyaleProject project;
	public Map<WatchKey, Path> sourcePathWatchKeys = new HashMap<>();
	public List<IInvisibleCompilationUnit> invisibleUnits = new ArrayList<>();
    public ProblemTracker codeProblemTracker = new ProblemTracker();
    public ProblemTracker configProblemTracker = new ProblemTracker();
	
	public void cleanup()
	{
		cleanupInvisibleUnits();

		if(project != null)
		{
			project.delete();
			project = null;
		}
		
        for(WatchKey watchKey : sourcePathWatchKeys.keySet())
        {
            watchKey.cancel();
        }
        sourcePathWatchKeys.clear();
	}

	public void cleanupInvisibleUnits()
	{
        //invisible units may exist for new files that haven't been saved, so
        //they don't exist on the file system. the first compilation unit
        //created will be invisible too, at least to start out.
        //if needed, we'll recreate invisible compilation units later.
        for (IInvisibleCompilationUnit unit : invisibleUnits)
        {
            unit.remove();
        }
        invisibleUnits.clear();
	}
}