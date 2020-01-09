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
package com.as3mxml.asconfigc.htmlTemplate;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.as3mxml.asconfigc.utils.ProjectUtils;
import com.as3mxml.asconfigc.compiler.CompilerOptions;

public class HTMLTemplateOptionsParser
{
	public HTMLTemplateOptionsParser()
	{
	}

	public Map<String, String> parse(JsonNode compilerOptions, String mainFile, String outputPath)
	{
		Map<String, String> result = new HashMap<>();
		if(compilerOptions != null && compilerOptions.has(CompilerOptions.DEFAULT_SIZE))
		{
			JsonNode defaultSizeJson = compilerOptions.get(CompilerOptions.DEFAULT_SIZE);
			result.put(HTMLTemplateOptions.WIDTH, defaultSizeJson.get(CompilerOptions.DEFAULT_SIZE__WIDTH).asText());
			result.put(HTMLTemplateOptions.HEIGHT, defaultSizeJson.get(CompilerOptions.DEFAULT_SIZE__HEIGHT).asText());
		}
		else
		{
			result.put(HTMLTemplateOptions.WIDTH, "100%");
			result.put(HTMLTemplateOptions.HEIGHT, "100%");
		}
		if(compilerOptions != null && compilerOptions.has(CompilerOptions.DEFAULT_BACKGROUND_COLOR))
		{
			String defaultBackgroundColor = compilerOptions.get(CompilerOptions.DEFAULT_BACKGROUND_COLOR).asText();
			result.put(HTMLTemplateOptions.BGCOLOR, defaultBackgroundColor);
		}
		else
		{
			result.put(HTMLTemplateOptions.BGCOLOR, "#ffffff");
		}
		if(compilerOptions != null && compilerOptions.has(CompilerOptions.TARGET_PLAYER))
		{
			String targetPlayer = compilerOptions.get(CompilerOptions.TARGET_PLAYER).asText();
			String[] parts = targetPlayer.split("\\.");
			result.put(HTMLTemplateOptions.VERSION_MAJOR, parts[0]);
			if(parts.length > 1)
			{
				result.put(HTMLTemplateOptions.VERSION_MINOR, parts[1]);
			}
			else
			{
				result.put(HTMLTemplateOptions.VERSION_MINOR, "0");
			}
			if(parts.length > 2)
			{
				result.put(HTMLTemplateOptions.VERSION_REVISION, parts[2]);
			}
			else
			{
				result.put(HTMLTemplateOptions.VERSION_REVISION, "0");
			}
		}
		else
		{
			//TODO: get the default target-player value from the SDK
			result.put(HTMLTemplateOptions.VERSION_MAJOR, "9");
			result.put(HTMLTemplateOptions.VERSION_MINOR, "0");
			result.put(HTMLTemplateOptions.VERSION_REVISION, "124");
		}
		String outputFileName = ProjectUtils.findOutputFileName(mainFile, outputPath);
		if(outputFileName != null)
		{
			int extensionIndex = outputFileName.indexOf(".");
			String swfName = outputFileName.substring(0, extensionIndex);
			result.put(HTMLTemplateOptions.SWF, swfName);
			if(mainFile != null)
			{
				Path mainFilePath = Paths.get(mainFile);
				//remove any directory names from the beginning
				String mainFileName = mainFilePath.getFileName().toString();
				extensionIndex = mainFileName.indexOf(".");
				result.put(HTMLTemplateOptions.APPLICATION, mainFileName.substring(0, extensionIndex));
			}
			else
			{
				result.put(HTMLTemplateOptions.APPLICATION, swfName);
			}
		}
		else
		{
			result.put(HTMLTemplateOptions.APPLICATION, "");
		}
		result.put(HTMLTemplateOptions.EXPRESS_INSTALL_SWF, "playerProductInstall.swf");
		result.put(HTMLTemplateOptions.USE_BROWSER_HISTORY, "--");
		//TODO: get the title token value from the main MXML application
		result.put(HTMLTemplateOptions.TITLE, "");
		return result;
	}
}