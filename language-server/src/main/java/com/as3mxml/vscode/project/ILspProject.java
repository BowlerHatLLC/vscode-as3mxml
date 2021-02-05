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

import java.util.Collection;
import java.util.Set;

import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.problems.ICompilerProblem;
import org.apache.royale.compiler.projects.IRoyaleProject;
import org.apache.royale.compiler.targets.ITargetSettings;
import org.apache.royale.compiler.units.ICompilationUnit;

public interface ILspProject extends IRoyaleProject {
	public Set<String> getQNamesOfDependencies(ICompilationUnit from);

	public IDefinition resolveSpecifier(IClassDefinition classDefinition, String specifierName);

	public Collection<ICompilerProblem> getFatalProblems();

	public ITargetSettings getTargetSettings();

	public void setTargetSettings(ITargetSettings value);

	public String getContainerInterface();

	public void collectProblems(Collection<ICompilerProblem> problems);
}