package com.as3mxml.vscode.project;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.royale.compiler.internal.projects.CompilerProject;
import org.apache.royale.compiler.internal.projects.DefinitionPriority;
import org.apache.royale.compiler.internal.units.ASCompilationUnit;
import org.apache.royale.compiler.targets.ITarget.TargetType;
import org.apache.royale.compiler.units.ICompilationUnit;

public class LspASCompilationUnit extends ASCompilationUnit {
	private static volatile boolean purgeEnabled = false;
	private static final AtomicInteger pinCount = new AtomicInteger(0);
	private static volatile LspASCompilationUnit activeUnit = null;
	private static volatile Set<String> openPaths = Collections.emptySet();

	public static void setPurgeEnabled(boolean enabled) {
		purgeEnabled = enabled;
		if (!enabled) {
			pinCount.set(0);
			activeUnit = null;
			openPaths = Collections.emptySet();
		}
	}

	public static void setActiveUnit(LspASCompilationUnit unit) {
		activeUnit = unit;
	}

	public static void setOpenPaths(Set<String> paths) {
		if (paths == null) {
			openPaths = Collections.emptySet();
		} else {
			openPaths = paths;
		}
	}

	public static void pinASTsToKeepAlive() {
		if (!purgeEnabled) {
			return;
		}
		pinCount.incrementAndGet();
	}

	public static void unpinAndRemoveASTs(ILspProject project) {
		if (!purgeEnabled) {
			return;
		}
		int count = pinCount.decrementAndGet();
		if (count < 0) {
			pinCount.compareAndSet(count, 0);
		}
		if (count <= 0) {
			purgeUnpinnedASTs(project);
		}
	}

	private static void purgeUnpinnedASTs(ILspProject project) {
		if (project == null) {
			return;
		}
		try {
			for (ICompilationUnit unit : project.getCompilationUnits()) {
				if (unit instanceof LspASCompilationUnit) {
					((LspASCompilationUnit) unit).removeAST();
				}
			}
		} catch (Exception e) {
		}
	}

	public LspASCompilationUnit(CompilerProject project, String path,
			DefinitionPriority.BasePriority basePriority,
			int order,
			String qname) {
		super(project, path, basePriority, order, qname);
	}

	@Override
	protected void removeAST() {
		if (!purgeEnabled || pinCount.get() > 0 || (this == activeUnit) || isOpen()) {
			return;
		}
		super.removeAST();
	}

	private boolean isOpen() {
		String filename = getAbsoluteFilename();
		if (openPaths.contains(filename)) {
			return true;
		}
		try {
			String normalized = java.nio.file.Paths.get(filename).toAbsolutePath().normalize().toString();
			return openPaths.contains(normalized);
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public void startBuildAsync(TargetType targetType) {
		getSyntaxTreeRequest();
		getFileScopeRequest();
		// if this method gets called as part of real-time problem checking
		// getting the other requests can get very expensive, so skip them.
		// the skipped requests should still get triggered eventually.
	}
}
