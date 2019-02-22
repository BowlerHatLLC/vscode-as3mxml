/*
Copyright 2016-2019 Bowler Hat LLC

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

import java.util.Map;

import org.apache.royale.compiler.internal.parsing.as.IncludeHandler;
import org.apache.royale.compiler.internal.parsing.as.OffsetCue;
import org.apache.royale.compiler.internal.tree.as.FileNode;
import org.apache.royale.compiler.internal.units.ResourceBundleCompilationUnit;
import org.apache.royale.compiler.internal.units.SWCCompilationUnit;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.units.ICompilationUnit;

public class CompilationUnitUtils
{
	public static class IncludeFileData
	{
		public IncludeFileData(String parentPath, OffsetCue offsetCue)
		{
			this.parentPath = parentPath;
			this.offsetCue = offsetCue;
		}

		public String parentPath;
		public OffsetCue offsetCue;
	}

	public static void findIncludedFiles(ICompilationUnit unit, Map<String,IncludeFileData> result)
	{
		if (unit == null
				|| unit instanceof SWCCompilationUnit
				|| unit instanceof ResourceBundleCompilationUnit)
		{
			//compiled compilation units won't have problems
			return;
		}
		findActionScriptIncludes(unit, result);
	}

	private static void findActionScriptIncludes(ICompilationUnit unit, Map<String,IncludeFileData> includes)
	{
		try
		{
			IASNode ast = unit.getSyntaxTreeRequest().get().getAST();
			unit.getFileScopeRequest().get();
			unit.getOutgoingDependenciesRequest().get();
			unit.getABCBytesRequest().get();
			if (ast instanceof FileNode)
			{
				FileNode fileNode = (FileNode) ast;
				String parentPath = unit.getAbsoluteFilename();
				IncludeHandler includeHandler = fileNode.getIncludeHandler();
				for(OffsetCue offsetCue : includeHandler.getOffsetCueList())
				{
					if(offsetCue.adjustment == 0
							&& offsetCue.local == 0
							&& offsetCue.absolute == 0)
					{
						//ignore because this data isn't valid, for some reason
						continue;
					}
					includes.put(offsetCue.filename, new IncludeFileData(parentPath, offsetCue));
				}
			}
		}
		catch(InterruptedException e)
		{

		}
	}
}