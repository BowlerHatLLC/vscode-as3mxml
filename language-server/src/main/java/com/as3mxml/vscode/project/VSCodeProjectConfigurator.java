/*
Copyright 2016-2024 Bowler Hat LLC

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.royale.compiler.config.Configuration;
import org.apache.royale.compiler.internal.projects.RoyaleProjectConfigurator;
import org.apache.royale.compiler.problems.ICompilerProblem;

public class VSCodeProjectConfigurator extends RoyaleProjectConfigurator {
	public VSCodeProjectConfigurator(Class<? extends Configuration> configurationClass) {
		super(configurationClass);
	}

	@Override
	public Collection<ICompilerProblem> getConfigurationProblems() {
		int size = configurationProblems.size();
		if (configuration != null) {
			size += configuration.getConfigurationProblems().size();
		}
		List<ICompilerProblem> problems = new ArrayList<ICompilerProblem>(size);
		problems.addAll(configurationProblems);
		if (configuration != null) {
			problems.addAll(configuration.getConfigurationProblems());
		}
		return problems;
	}

}
