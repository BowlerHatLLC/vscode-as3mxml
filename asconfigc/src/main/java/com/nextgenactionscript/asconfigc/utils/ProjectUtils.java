/*
Copyright 2016-2017 Bowler Hat LLC

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
package com.nextgenactionscript.asconfigc.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.nextgenactionscript.asconfigc.compiler.ProjectType;

public class ProjectUtils
{
	public static String findAIRDescriptorOutputPath(String mainFile, String airDescriptor, String outputPath, boolean isSWF)
	{
		String outputDir = ProjectUtils.findOutputDirectory(mainFile, outputPath, isSWF);
		Path path = Paths.get(airDescriptor);
		Path outputDirPath = Paths.get(outputDir);
		return outputDirPath.resolve(path.getFileName().toString()).toString();
	}

	public static String findApplicationContentOutputPath(String mainFile, String outputPath, boolean isSWF)
	{
		String outputDirectory = ProjectUtils.findOutputDirectory(mainFile, outputPath, isSWF);
		String applicationContentName = ProjectUtils.findApplicationContent(mainFile, outputPath, isSWF);
		if(applicationContentName == null)
		{
			return null;
		}
		Path outputDirPath = Paths.get(outputDirectory);
		return outputDirPath.resolve(applicationContentName).toString();
	}

	public static String findOutputDirectory(String mainFile, String outputPath, boolean isSWF)
	{
		if(outputPath == null)
		{
			if(mainFile == null)
			{
				return System.getProperty("user.dir");
			}
			Path mainFilePath = Paths.get(mainFile);
			if(!isSWF)
			{
				Path mainFileParentPath = mainFilePath.getParent();
				//Royale treats these directory structures as a special case
				if(mainFileParentPath.endsWith("/src") ||
					mainFileParentPath.endsWith("\\src"))
				{
					mainFileParentPath = mainFileParentPath.resolve("../");
				}
				else if(mainFileParentPath.endsWith("/src/main/flex") ||
					mainFileParentPath.endsWith("\\src\\main\\flex") ||
					mainFileParentPath.endsWith("/src/main/royale") ||
					mainFileParentPath.endsWith("\\src\\main\\royale"))
				{
					mainFileParentPath = mainFileParentPath.resolve("../../../");
				}
				try
				{
					return mainFileParentPath.toFile().getCanonicalPath();
				}
				catch(IOException e)
				{
					return null;
				}
			}
		}
		Path mainFilePath = Paths.get(mainFile);
		File mainFileParentFile = mainFilePath.getParent().toFile();
		try
		{
			return mainFileParentFile.getCanonicalPath();
		}
		catch(IOException e)
		{
			return null;
		}
	}

	public static String findApplicationContent(String mainFile, String outputPath, boolean isSWF)
	{
		if(outputPath == null)
		{
			if(isSWF)
			{
				if(mainFile == null)
				{
					return null;
				}
				//replace .as or .mxml with .swf
				Path mainFilePath = Paths.get(mainFile);
				String fileName = mainFilePath.getFileName().toString();
				String extension = "";
				int extensionIndex = fileName.lastIndexOf(".");
				if(extensionIndex != -1)
				{
					extension = fileName.substring(extensionIndex + 1);
				}
				return fileName.substring(0, fileName.length() - extension.length()) + ".swf";
			}
			//An AIR app will load an HTML file as its main content if there's no SWF
			return "index.html";
		}
		return Paths.get(outputPath).getFileName().toString();
	}

	public static Path findCompilerJarPath(String projectType, String sdkPath, boolean isSWF)
	{
		Path jarPath = null;
		List<String> jarNames = Arrays.asList(
			
			"falcon-mxmlc.jar",
			"mxmlc-cli.jar",
			"mxmlc.jar"
		);
		if(projectType.equals(ProjectType.LIB))
		{
			jarNames = Arrays.asList(
				"falcon-compc.jar",
				"compc-cli.jar",
				"compc.jar"
			);
		}
		for(String jarName : jarNames)
		{
			if(isSWF)
			{
				jarPath = Paths.get(sdkPath, "lib", jarName);
			}
			else //js
			{
				jarPath = Paths.get(sdkPath, "lib", jarName);
			}
			if(Files.exists(jarPath))
			{
				break;
			}
			jarPath = null;
		}
		if(jarPath == null)
		{
			return null;
		}
		return jarPath;
	}
}