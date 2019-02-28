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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.royale.compiler.filespecs.IFileSpecification;
import org.apache.royale.compiler.internal.mxml.MXMLData;
import org.apache.royale.compiler.internal.parsing.as.IncludeHandler;
import org.apache.royale.compiler.internal.parsing.as.OffsetCue;
import org.apache.royale.compiler.internal.parsing.as.OffsetLookup;
import org.apache.royale.compiler.internal.scopes.MXMLFileScope;
import org.apache.royale.compiler.internal.tree.as.FileNode;
import org.apache.royale.compiler.internal.units.ResourceBundleCompilationUnit;
import org.apache.royale.compiler.internal.units.SWCCompilationUnit;
import org.apache.royale.compiler.mxml.IMXMLDataManager;
import org.apache.royale.compiler.mxml.IMXMLLanguageConstants;
import org.apache.royale.compiler.mxml.IMXMLTagAttributeData;
import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.scopes.IASScope;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.apache.royale.compiler.workspaces.IWorkspace;

public class CompilationUnitUtils
{
	public static class IncludeFileData
	{
		public IncludeFileData(String parentPath)
		{
			this.parentPath = parentPath;
		}

		public String parentPath;

		private List<OffsetCue> offsetCues = new ArrayList<>();

		public List<OffsetCue> getOffsetCues()
		{
			return offsetCues;
		}
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
		String path = unit.getAbsoluteFilename();
		if(path.endsWith(".mxml"))
		{
			//findMXMLIncludes(unit, result);
		}
		else
		{
			findActionScriptIncludes(unit, result);
		}
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
					String filename = offsetCue.filename;
					if(!includes.containsKey(filename))
					{
						includes.put(filename, new IncludeFileData(parentPath));
					}
					IncludeFileData includeFileData = includes.get(filename);
					includeFileData.getOffsetCues().add(offsetCue);
				}
			}
		}
		catch(InterruptedException e)
		{

		}
	}

	private static void findMXMLIncludes(ICompilationUnit unit, Map<String,IncludeFileData> includes)
	{
		IWorkspace workspace = unit.getProject().getWorkspace();
		IMXMLDataManager mxmlDataManager = workspace.getMXMLDataManager();
		IFileSpecification fileSpecification = workspace.getFileSpecification(unit.getAbsoluteFilename());
		MXMLData mxmlData = (MXMLData) mxmlDataManager.get(fileSpecification);

		IMXMLTagData rootTag = mxmlData.getRootTag();
		String parentPath = mxmlData.getPath();
		IMXMLTagData[] scriptTags = MXMLDataUtils.findMXMLScriptTags(rootTag);
		for(IMXMLTagData scriptTag : scriptTags)
		{
			IMXMLTagAttributeData sourceAttribute = scriptTag.getTagAttributeData(IMXMLLanguageConstants.ATTRIBUTE_SOURCE);
			if(sourceAttribute == null)
			{
				continue;
			}
			Path scriptPath = Paths.get(sourceAttribute.getRawValue());
			if(!scriptPath.isAbsolute())
			{
				Path mxmlPath = Paths.get(mxmlData.getPath());
				scriptPath = mxmlPath.getParent().resolve(scriptPath);
			}

			int offset = 0;
			try
			{
				IASScope[] scopes = unit.getFileScopeRequest().get().getScopes();
				if (scopes.length == 0)
				{
					continue;
				}
				IASScope firstScope = scopes[0];
				if (firstScope instanceof MXMLFileScope)
				{
					MXMLFileScope fileScope = (MXMLFileScope) firstScope;
					OffsetLookup offsetLookup = fileScope.getOffsetLookup();
					int[] absoluteOffset = offsetLookup.getAbsoluteOffset(scriptPath.toString(), 0);
					if (absoluteOffset.length == 0)
					{
						continue;
					}
					offset = absoluteOffset[0];
				}
			}
			catch(InterruptedException e)
			{
				continue;
			}

			if(offset == 0)
			{
				continue;
			}

			//includes.put(scriptPath.toString(), new IncludeFileData(parentPath, 0, offset));

			int scriptLength = 0;
			try
			{
				IFileSpecification fileSpec = workspace.getFileSpecification(scriptPath.toString());
				Reader scriptReader = fileSpec.createReader();
				while(scriptReader.read() != -1)
				{
					scriptLength++;
				}
				scriptReader.close();
			}
			catch(FileNotFoundException e)
			{
				continue;
			}
			catch(IOException e)
			{
				//just ignore it
			}

			if(scriptLength == 0)
			{
				continue;
			}

			//includes.put(parentPath, new IncludeFileData(parentPath, offset, scriptLength));
		}
	}
}