package com.as3mxml.vscode.project;

import org.apache.royale.compiler.internal.projects.CompilerProject;
import org.apache.royale.compiler.internal.projects.DefinitionPriority;
import org.apache.royale.compiler.internal.units.MXMLCompilationUnit;
import org.apache.royale.compiler.targets.ITarget.TargetType;

public class LspMXMLCompilationUnit extends MXMLCompilationUnit {
	public LspMXMLCompilationUnit(CompilerProject project, String path,
			DefinitionPriority.BasePriority basePriority,
			int order,
			String qname) {
		super(project, path, basePriority, order, qname);
	}

	@Override
	protected void removeAST() {
		// at the time this was written, MXMLCompilationUnit doesn't do anything
		// in removeAST(), but if that ever changes, we should probably have the
		// same empty override as LspASCompilationUnit.
	}

	@Override
	public void startBuildAsync(TargetType targetType) {
		getSyntaxTreeRequest();
		getFileScopeRequest();
		// if this method gets called as part of real-time problem checking
		// getting the other requests can get very expensive, so skip them
		// the skipped requests should still get triggered eventually.
	}
}
