/*
Copyright 2016-2018 Bowler Hat LLC

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
package com.as3mxml.asconfigc.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.as3mxml.asconfigc.compiler.ProjectType;

public class ProjectUtils
{
	private static final String FILE_EXTENSION_AS = ".as";
	private static final String FILE_EXTENSION_MXML = ".mxml";
	private static final String JAR_NAME_ADT = "adt.jar";

	public static String findAIRDescriptorOutputPath(String mainFile, String airDescriptor, String outputPath, boolean isSWF, boolean debugBuild)
	{
		String outputDir = ProjectUtils.findOutputDirectory(mainFile, outputPath, isSWF);
		Path path = Paths.get(airDescriptor);
		if(isSWF)
		{
			Path outputDirPath = Paths.get(outputDir);
			return outputDirPath.resolve(path.getFileName().toString()).toString();
		}
		String jsDir = "js-release";
		if(debugBuild)
		{
			jsDir = "js-debug";
		}
		Path outputDirPath = Paths.get(outputDir, "bin", jsDir);
		return outputDirPath.resolve(path.getFileName().toString()).toString();
	}

	public static String findApplicationContentOutputPath(String mainFile, String outputPath, boolean isSWF, boolean debugBuild)
	{
		String outputDirectory = ProjectUtils.findOutputDirectory(mainFile, outputPath, isSWF);
		String applicationContentName = ProjectUtils.findApplicationContent(mainFile, outputPath, isSWF);
		if(applicationContentName == null)
		{
			return null;
		}
		if(isSWF)
		{
			Path outputDirPath = Paths.get(outputDirectory);
			return outputDirPath.resolve(applicationContentName).toString();
		}
		String jsDir = "js-release";
		if(debugBuild)
		{
			jsDir = "js-debug";
		}
		Path outputDirPath = Paths.get(outputDirectory, "bin", jsDir);
		return outputDirPath.resolve(applicationContentName).toString();
	}

	public static String findOutputDirectory(String mainFile, String outputValue, boolean isSWF)
	{
		if(outputValue == null)
		{
			if(mainFile == null)
			{
				return System.getProperty("user.dir");
			}
			Path mainFilePath = Paths.get(mainFile);
			if(!mainFilePath.isAbsolute())
			{
				mainFilePath = Paths.get(System.getProperty("user.dir"), mainFile);
			}
			Path mainFileParentPath = mainFilePath.getParent();
			if(mainFileParentPath == null)
			{
				return System.getProperty("user.dir");
			}
			if(!isSWF)
			{
				//Royale treats these directory structures as a special case
				String mainFileParentPathAsString = mainFileParentPath.toString();
				if(mainFileParentPathAsString.endsWith("/src") ||
					mainFileParentPathAsString.endsWith("\\src"))
				{
					mainFileParentPath = mainFileParentPath.resolve("../");
				}
				else if(mainFileParentPathAsString.endsWith("/src/main/flex") ||
					mainFileParentPathAsString.endsWith("\\src\\main\\flex") ||
					mainFileParentPathAsString.endsWith("/src/main/royale") ||
					mainFileParentPathAsString.endsWith("\\src\\main\\royale"))
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
			return mainFileParentPath.toString();
		}
		Path outputPath = Paths.get(outputValue);
		if(!outputPath.isAbsolute())
		{
			outputPath = Paths.get(System.getProperty("user.dir"), outputValue);
		}
		if(!isSWF)
		{
			return outputPath.toString();
		}
		Path outputValueParentPath = outputPath.getParent();
		return outputValueParentPath.toString();
	}

	public static String findApplicationContent(String mainFile, String outputPath, boolean isSWF)
	{
		if(!isSWF)
		{
			//An Adobe AIR app for Royale will load an HTML file as its main content
			return "index.html";
		}
		if(outputPath == null)
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
				extension = fileName.substring(extensionIndex);
			}
			return fileName.substring(0, fileName.length() - extension.length()) + ".swf";
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
				jarPath = Paths.get(sdkPath, "js", "lib", jarName);
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

	public static Path findAdobeAIRPackagerJarPath(String sdkPath)
	{
		Path jarPath = Paths.get(sdkPath, "lib", JAR_NAME_ADT);
		if(Files.exists(jarPath))
		{
			return jarPath;
		}
		return null;
	}

	public static List<String> findSourcePathAssets(String mainFile, List<String> sourcePaths, String outputDirectory, List<String> excludes) throws IOException
	{
		List<String> result = new ArrayList<>();
		List<String> sourcePathsCopy = new ArrayList<>();
		if(sourcePaths != null)
		{
			//we don't want to modify the original list, so copy the items over
			sourcePathsCopy.addAll(sourcePaths);
		}
		if(mainFile != null)
		{
			//the parent directory of the main file is automatically added as a
			//source path by the compiler
			Path mainFileParent = Paths.get(mainFile).getParent();
			sourcePathsCopy.add(mainFileParent.toString());
		}
		for(int i = 0, size = sourcePathsCopy.size(); i < size; i++)
		{
			String sourcePath = sourcePathsCopy.get(i);
			Path path = Paths.get(sourcePath);
			if(!path.isAbsolute())
			{
				//force all source paths into absolute paths
				path = Paths.get(System.getProperty("user.dir"), sourcePath);
				sourcePathsCopy.set(i, path.toString());
			}
		}
		if(sourcePathsCopy.contains(outputDirectory))
		{
			//assets in source path will not be copied because the output
			//directory is a source path
			return result;
		}
		if(excludes != null)
		{
			for(int i = 0, size = excludes.size(); i < size; i++)
			{
				String exclude = excludes.get(i);
				Path path = Paths.get(exclude);
				if(!path.isAbsolute())
				{
					//force all excludes into absolute paths
					path = Paths.get(System.getProperty("user.dir"), exclude);
					excludes.set(i, path.toString());
				}
			}
		}
		for(int i = 0, size = sourcePathsCopy.size(); i < size; i++)
		{
			String sourcePath = sourcePathsCopy.get(i);
			File file = new File(sourcePath);
			File[] listedFiles = file.listFiles();
			if(listedFiles == null)
			{
				//this file is invalid for some reason
				throw new IOException("Invalid source path: " + sourcePath);
			}
			for(File innerFile : file.listFiles())
			{
				String innerFilePath = innerFile.getAbsolutePath();
				if(innerFile.isDirectory())
				{
					sourcePathsCopy.add(innerFilePath);
					size++;
					continue;
				}
				String extension = null;
				int index = innerFilePath.lastIndexOf(".");
				if(index != -1)
				{
					extension = innerFilePath.substring(index);
				}
				if(extension != null && (extension.equals(FILE_EXTENSION_AS) || extension.equals(FILE_EXTENSION_MXML)))
				{
					continue;
				}
				if(excludes != null && excludes.contains(innerFilePath))
				{
					continue;
				}
				result.add(innerFilePath);
			}
		}
		return result;
	}

	public static String assetPathToOutputPath(String assetPath, String mainFile, List<String> sourcePaths, String outputDirectory) throws IOException
	{
		List<String> sourcePathsCopy = new ArrayList<>();
		if(sourcePaths != null)
		{
			//we don't want to modify the original list, so copy the items over
			sourcePathsCopy.addAll(sourcePaths);
		}
		if(mainFile != null)
		{
			//the parent directory of the main file is automatically added as a
			//source path by the compiler
			Path mainFileParent = Paths.get(mainFile).getParent();
			sourcePathsCopy.add(mainFileParent.toString());
		}
		Path assetPathPath = Paths.get(assetPath);
		if(!assetPathPath.isAbsolute())
		{
			assetPathPath = Paths.get(System.getProperty("user.dir"), assetPath);
			assetPath = assetPathPath.toString();
		}
		String relativePath = null;
		for(int i = 0, size = sourcePathsCopy.size(); i < size; i++)
		{
			String sourcePath = sourcePathsCopy.get(i);
			Path path = Paths.get(sourcePath);
			if(!path.isAbsolute())
			{
				path = Paths.get(System.getProperty("user.dir"), sourcePath);
			}
			if(assetPath.startsWith(path.toString()))
			{
				relativePath = path.relativize(Paths.get(assetPath)).toString();
			}
		}
		if(relativePath == null)
		{
			throw new IOException("Could not find asset in source path: " + assetPath);
		}
		return new File(outputDirectory, relativePath).getAbsolutePath();
	}

	public static String populateAdobeAIRDescriptorContent(String descriptor, String contentValue)
	{
		return descriptor.replaceFirst("<content>.+<\\/content>", "<content>" + contentValue + "</content>");
	}
}