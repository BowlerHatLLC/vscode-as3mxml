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
package com.as3mxml.vscode.project;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.royale.compiler.common.DependencyTypeSet;
import org.apache.royale.compiler.internal.projects.RoyaleProject;
import org.apache.royale.compiler.internal.workspaces.Workspace;
import org.apache.royale.compiler.units.ICompilationUnit;

public class LspProject extends RoyaleProject implements ILspProject {
	public LspProject(Workspace workspace) {
		super(workspace);
	}

	public Set<String> getQNamesOfDependencies(ICompilationUnit from) {
		Set<String> result = new HashSet<>();
		Set<ICompilationUnit> directDeps = getDirectDependencies(from);
		for (ICompilationUnit to : directDeps) {
			Map<String, DependencyTypeSet> depSet = dependencyGraph.getDependencySet(from, to);
			result.addAll(depSet.keySet());
		}
		return result;
	}
}