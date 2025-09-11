package com.as3mxml.vscode.project;

import org.apache.commons.io.FilenameUtils;
import org.apache.royale.compiler.internal.projects.CompilerProject;
import org.apache.royale.compiler.internal.projects.DefinitionPriority;
import org.apache.royale.compiler.internal.projects.ISourceFileHandler;
import org.apache.royale.compiler.units.ICompilationUnit;

public final class LspMXMLSourceFileHandler implements ISourceFileHandler {
	public static final String EXTENSION = "mxml";
	public static final LspMXMLSourceFileHandler INSTANCE = new LspMXMLSourceFileHandler();

	private LspMXMLSourceFileHandler() {
	}

	@Override
	public String[] getExtensions() {
		return new String[] { EXTENSION };
	}

	@Override
	public ICompilationUnit createCompilationUnit(CompilerProject project,
			String path,
			DefinitionPriority.BasePriority basePriority,
			int order,
			String qname,
			String locale) {
		if (qname == null)
			qname = FilenameUtils.getBaseName(path);
		return new LspMXMLCompilationUnit(project, path, basePriority, order, qname);
	}

	@Override
	public boolean needCompilationUnit(CompilerProject project, String path, String qname, String locale) {
		return true;
	}

	@Override
	public boolean canCreateInvisibleCompilationUnit() {
		return true;
	}
}
