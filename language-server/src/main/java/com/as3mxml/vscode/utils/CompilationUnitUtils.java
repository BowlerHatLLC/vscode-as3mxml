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
import org.apache.royale.compiler.mxml.IMXMLDataManager;
import org.apache.royale.compiler.mxml.IMXMLLanguageConstants;
import org.apache.royale.compiler.mxml.IMXMLTagAttributeData;
import org.apache.royale.compiler.mxml.IMXMLTagData;
import org.apache.royale.compiler.mxml.IMXMLTextData;
import org.apache.royale.compiler.mxml.IMXMLUnitData;
import org.apache.royale.compiler.scopes.IASScope;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.apache.royale.compiler.units.ICompilationUnit.UnitType;
import org.apache.royale.compiler.workspaces.IWorkspace;

public class CompilationUnitUtils
{
	private static final String FILE_EXTENSION_MXML = ".mxml";

	private static class OffsetCue2 extends OffsetCue
	{
		public OffsetCue2(String filename, int absolute, int adjustment)
		{
			super(filename, absolute, adjustment);
		}
	}

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
		if(unit == null)
		{
			return;
		}
		UnitType unitType = unit.getCompilationUnitType();
		if(!UnitType.AS_UNIT.equals(unitType) && !UnitType.MXML_UNIT.equals(unitType))
		{
			//compiled compilation units won't have problems
			return;
		}
		String path = unit.getAbsoluteFilename();
		if(path.endsWith(FILE_EXTENSION_MXML))
		{
			findMXMLIncludes(unit, result);
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

		OffsetLookup offsetLookup = null;
		try
		{
			IASScope[] scopes = unit.getFileScopeRequest().get().getScopes();
			if (scopes.length == 0)
			{
				return;
			}
			IASScope firstScope = scopes[0];
			if (firstScope instanceof MXMLFileScope)
			{
				MXMLFileScope fileScope = (MXMLFileScope) firstScope;
				offsetLookup = fileScope.getOffsetLookup();
			}
		}
		catch(InterruptedException e)
		{
			return;
		}

		IMXMLTagData rootTag = mxmlData.getRootTag();
		if(rootTag == null)
		{
			return;
		}
		String parentPath = mxmlData.getPath();
		IMXMLTagData[] scriptTags = MXMLDataUtils.findMXMLScriptTags(rootTag);
		for(IMXMLTagData scriptTag : scriptTags)
		{
			IMXMLTagAttributeData sourceAttribute = scriptTag.getTagAttributeData(IMXMLLanguageConstants.ATTRIBUTE_SOURCE);
			if(sourceAttribute == null)
			{
				IMXMLUnitData mxmlUnit = scriptTag.getFirstChildUnit();
				while(mxmlUnit != null)
				{
					if (mxmlUnit instanceof IMXMLTextData)
					{
						IMXMLTextData mxmlTextData = (IMXMLTextData) mxmlUnit;
						String text = mxmlTextData.getCompilableText();
						if (!scriptTag.getMXMLDialect().isWhitespace(text))
						{
							int localOffset = mxmlTextData.getParentUnitData().getAbsoluteEnd();
							int[] absoluteOffsets = offsetLookup.getAbsoluteOffset(parentPath, localOffset);
							int absoluteOffset = absoluteOffsets[0];
							
							if(!includes.containsKey(parentPath))
							{
								includes.put(parentPath, new IncludeFileData(parentPath));
							}
							IncludeFileData includeFileData = includes.get(parentPath);
							includeFileData.getOffsetCues().add(new OffsetCue2(parentPath, absoluteOffset, absoluteOffset - localOffset));
						}
					}
					mxmlUnit = mxmlUnit.getNextSiblingUnit();
				}
			}
			else
			{
				String scriptSourceValue = sourceAttribute.getRawValue();
				if(scriptSourceValue.length() == 0)
				{
					//no file specified yet... it's empty!
					continue;
				}
				Path scriptPath = Paths.get(sourceAttribute.getRawValue());
				if(!scriptPath.isAbsolute())
				{
					Path mxmlPath = Paths.get(mxmlData.getPath());
					scriptPath = mxmlPath.getParent().resolve(scriptPath);
				}
				if(!scriptPath.toFile().exists())
				{
					//the file doesn't actually exist, and getAbsoluteOffset()
					//will throw an exception if we call it
					continue;
				}

				int[] absoluteOffsets = offsetLookup.getAbsoluteOffset(scriptPath.toString(), 0);
				if (absoluteOffsets.length == 0)
				{
					continue;
				}
				int absoluteOffset = absoluteOffsets[0];

				if(absoluteOffset == 0)
				{
					continue;
				}
				
				String scriptFilename = scriptPath.toString();

				int scriptLength = 0;
				try
				{
					IFileSpecification fileSpec = workspace.getFileSpecification(scriptFilename);
					Reader scriptReader = fileSpec.createReader();
					try
					{
						while(scriptReader.read() != -1)
						{
							scriptLength++;
						}
					}
					finally
					{
						scriptReader.close();
					}
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

				if(!includes.containsKey(scriptFilename))
				{
					includes.put(scriptFilename, new IncludeFileData(parentPath));
				}
				IncludeFileData includeFileData = includes.get(scriptFilename);
				includeFileData.getOffsetCues().add(new OffsetCue2(scriptFilename, absoluteOffset, absoluteOffset));
			}
		}
	}
}