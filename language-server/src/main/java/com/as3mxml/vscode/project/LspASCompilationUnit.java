package com.as3mxml.vscode.project;

import org.apache.royale.compiler.internal.projects.CompilerProject;
import org.apache.royale.compiler.internal.projects.DefinitionPriority;
import org.apache.royale.compiler.internal.units.ASCompilationUnit;

public class LspASCompilationUnit extends ASCompilationUnit {
	public LspASCompilationUnit(CompilerProject project, String path,
			DefinitionPriority.BasePriority basePriority,
			int order,
			String qname) {
		super(project, path, basePriority, order, qname);
	}

	@Override
	protected void removeAST() {
		// using a custom subclass of ASCompilationUnit to override this method
		// because the default implementation allows ASTs to be garbage
		// collected, and the scopes can get out of sync.
		// that's probably a Royale compiler bug, but a workaround is easier.
	}
}
