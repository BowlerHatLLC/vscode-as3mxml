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
package com.nextgenactionscript.asconfigc.air;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.nextgenactionscript.asconfigc.utils.PathUtils;

public class AIROptionsParser
{
	public AIROptionsParser()
	{
	}
	
	private boolean checkPaths = true;
	
	public boolean getCheckPaths()
	{
		return checkPaths;
	}

	public void setCheckPaths(boolean value)
	{
		checkPaths = value;
	}

	public void parse(String platform, boolean debug, String applicationDescriptorPath,
		String applicationContentPath, JsonNode options, List<String> result) throws FileNotFoundException
	{
		result.add("-" + AIROptions.PACKAGE);
		
		//AIR_SIGNING_OPTIONS begin
		//these are *desktop* signing options only
		//mobile signing options must be specified later!
		if(platform.equals(AIRPlatform.AIR) ||
			platform.equals(AIRPlatform.WINDOWS) ||
			platform.equals(AIRPlatform.MAC))
		{
			if(options.has(AIROptions.SIGNING_OPTIONS) &&
				!overridesOptionForPlatform(options, AIROptions.SIGNING_OPTIONS, platform))
			{
				parseSigningOptions(options.get(AIROptions.SIGNING_OPTIONS), debug, result);
			}
			else if(overridesOptionForPlatform(options, AIROptions.SIGNING_OPTIONS, platform))
			{
				//desktop captive runtime
				parseSigningOptions(options.get(platform).get(AIROptions.SIGNING_OPTIONS), debug, result);
			}
			else if(!options.has(AIROptions.SIGNING_OPTIONS))
			{
				//desktop shared runtime, but signing options overridden for windows or mac
				if(System.getProperty("os.name").toLowerCase().startsWith("mac") &&
					overridesOptionForPlatform(options, AIROptions.SIGNING_OPTIONS, AIRPlatform.MAC))
				{
					parseSigningOptions(options.get(AIRPlatform.MAC).get(AIROptions.SIGNING_OPTIONS), debug, result);
				}
				else if(System.getProperty("os.name").toLowerCase().startsWith("windows") &&
					overridesOptionForPlatform(options, AIROptions.SIGNING_OPTIONS, AIRPlatform.WINDOWS))
				{
					parseSigningOptions(options.get(AIRPlatform.WINDOWS).get(AIROptions.SIGNING_OPTIONS), debug, result);
				}
			}
		}
		//AIR_SIGNING_OPTIONS end

		if(overridesOptionForPlatform(options, AIROptions.TARGET, platform))
		{
			setValueWithoutAssignment(AIROptions.TARGET, options.get(platform).get(AIROptions.TARGET).asText(), result);
		}
		else if(options.has(AIROptions.TARGET))
		{
			setValueWithoutAssignment(AIROptions.TARGET, options.get(AIROptions.TARGET).asText(), result);
		}
		else
		{
			switch(platform)
			{
				case AIRPlatform.ANDROID:
				{
					if(debug)
					{
						setValueWithoutAssignment(AIROptions.TARGET, AIRTarget.APK_DEBUG, result);
					}
					else
					{
						setValueWithoutAssignment(AIROptions.TARGET, AIRTarget.APK_CAPTIVE_RUNTIME, result);
					}
					break;
				}
				case AIRPlatform.IOS:
				{
					if(debug)
					{
						setValueWithoutAssignment(AIROptions.TARGET, AIRTarget.IPA_DEBUG, result);
					}
					else
					{
						setValueWithoutAssignment(AIROptions.TARGET, AIRTarget.IPA_APP_STORE, result);
					}
					break;
				}
				case AIRPlatform.WINDOWS:
				{
					//captive runtime
					setValueWithoutAssignment(AIROptions.TARGET, AIRTarget.BUNDLE, result);
					break;
				}
				case AIRPlatform.MAC:
				{
					//captive runtime
					setValueWithoutAssignment(AIROptions.TARGET, AIRTarget.BUNDLE, result);
					break;
				}
				default:
				{
					//shared runtime
					setValueWithoutAssignment(AIROptions.TARGET, AIRTarget.AIR, result);
					break;
				}
			}
		}
		if(overridesOptionForPlatform(options, AIROptions.SAMPLER, platform))
		{
			setValueWithoutAssignment(AIROptions.SAMPLER, options.get(platform).get(AIROptions.SAMPLER).asText(), result);
		}
		if(overridesOptionForPlatform(options, AIROptions.HIDE_ANE_LIB_SYMBOLS, platform))
		{
			setValueWithoutAssignment(AIROptions.HIDE_ANE_LIB_SYMBOLS, options.get(platform).get(AIROptions.HIDE_ANE_LIB_SYMBOLS).asText(), result);
		}
		if(overridesOptionForPlatform(options, AIROptions.EMBED_BITCODE, platform))
		{
			setValueWithoutAssignment(AIROptions.EMBED_BITCODE, options.get(platform).get(AIROptions.EMBED_BITCODE).asText(), result);
		}

		//DEBUGGER_CONNECTION_OPTIONS begin
		if(debug && (platform.equals(AIRPlatform.ANDROID) || platform.equals(AIRPlatform.IOS)))
		{
			parseDebugOptions(options, platform, result);
		}
		//DEBUGGER_CONNECTION_OPTIONS end

		if(overridesOptionForPlatform(options, AIROptions.AIR_DOWNLOAD_URL, platform))
		{
			setValueWithoutAssignment(AIROptions.AIR_DOWNLOAD_URL, options.get(platform).get(AIROptions.AIR_DOWNLOAD_URL).asText(), result);
		}

		//NATIVE_SIGNING_OPTIONS begin
		//these are *mobile* signing options only
		//desktop signing options were already handled earlier
		if(platform.equals(AIRPlatform.ANDROID) || platform.equals(AIRPlatform.IOS))
		{
			if(overridesOptionForPlatform(options, AIROptions.SIGNING_OPTIONS, platform))
			{
				parseSigningOptions(options.get(platform).get(AIROptions.SIGNING_OPTIONS), debug, result);
			}
			else if(options.has(AIROptions.SIGNING_OPTIONS))
			{
				parseSigningOptions(options.get(AIROptions.SIGNING_OPTIONS), debug, result);
			}
		}
		//NATIVE_SIGNING_OPTIONS end

		if(overridesOptionForPlatform(options, AIROptions.OUTPUT, platform))
		{
			String outputPath = options.get(platform).get(AIROptions.OUTPUT).asText();
			outputPath = PathUtils.escapePath(outputPath, false);
			result.add(outputPath);
		}
		else if(options.has(AIROptions.OUTPUT))
		{
			String outputPath = options.get(AIROptions.OUTPUT).asText();
			outputPath = PathUtils.escapePath(outputPath, false);
			result.add(outputPath);
		}
		
		result.add(PathUtils.escapePath(applicationDescriptorPath, false));

		if(overridesOptionForPlatform(options, AIROptions.PLATFORMSDK, platform))
		{
			setPathValueWithoutAssignment(AIROptions.PLATFORMSDK, options.get(platform).get(AIROptions.PLATFORMSDK).asText(), checkPaths, result);
		}
		if(overridesOptionForPlatform(options, AIROptions.ARCH, platform))
		{
			setValueWithoutAssignment(AIROptions.ARCH, options.get(platform).get(AIROptions.ARCH).asText(), result);
		}

		//FILE_OPTIONS begin
		if(overridesOptionForPlatform(options, AIROptions.FILES, platform))
		{
			parseFiles(options.get(platform).get(AIROptions.FILES), result);
		}
		else if(options.has(AIROptions.FILES))
		{
			parseFiles(options.get(AIROptions.FILES), result);
		}
		File applicationContentFile = new File(applicationContentPath);
		if(!applicationContentPath.equals(applicationContentFile.getName()))
		{
			result.add("-C");
			String dirname = applicationContentFile.getParentFile().getPath();
			dirname = PathUtils.escapePath(dirname, false);
			result.add(dirname);
			String basename = applicationContentFile.getName();
			basename = PathUtils.escapePath(basename, false);
			result.add(basename);
		}
		else
		{
			result.add(PathUtils.escapePath(applicationContentPath, false));
		}

		if(overridesOptionForPlatform(options, AIROptions.EXTDIR, platform))
		{
			parseExtdir(options.get(platform).get(AIROptions.EXTDIR), result);
		}
		else if(options.has(AIROptions.EXTDIR))
		{
			parseExtdir(options.get(AIROptions.EXTDIR), result);
		}
		//FILE_OPTIONS end
		
		//ANE_OPTIONS begin
		//ANE_OPTIONS end

		Iterator<String> fieldNames = options.fieldNames();
		while(fieldNames.hasNext())
		{
			String fieldName = fieldNames.next();
			switch(fieldName)
			{
				case AIRPlatform.AIR:
				case AIRPlatform.ANDROID:
				case AIRPlatform.IOS:
				case AIRPlatform.MAC:
				case AIRPlatform.WINDOWS:

				case AIROptions.AIR_DOWNLOAD_URL:
				case AIROptions.ARCH:
				case AIROptions.EMBED_BITCODE:
				case AIROptions.EXTDIR:
				case AIROptions.FILES:
				case AIROptions.HIDE_ANE_LIB_SYMBOLS:
				case AIROptions.OUTPUT:
				case AIROptions.PLATFORMSDK:
				case AIROptions.SAMPLER:
				case AIROptions.SIGNING_OPTIONS:
				case AIROptions.TARGET:
				{
					break;
				}
				default:
				{
					throw new Error("Unknown AIR option: " + fieldName);
				}
			}
		}
	}
	
	/**
	 * @private
	 * Determines if an option is also specified for a specific platform.
	 */
	private boolean overridesOptionForPlatform(JsonNode globalOptions, String optionName, String platform)
	{
		return globalOptions.has(platform) && globalOptions.get(platform).has(optionName);
	}
	
	private void setValueWithoutAssignment(String optionName, String value, List<String> result)
	{
		result.add("-" + optionName);
		result.add(value.toString());
	}
	
	private void parseExtdir(JsonNode extdir, List<String> result) throws FileNotFoundException
	{
		for(int i = 0, size = extdir.size(); i < size; i++)
		{
			String current = extdir.get(i).asText();
			setPathValueWithoutAssignment(AIROptions.EXTDIR, current, checkPaths, result);
		}
	}

	private void parseFiles(JsonNode files, List<String> result)
	{
		for(int i = 0, size = files.size(); i < size; i++)
		{
			JsonNode file = files.get(i);
			if(file.isTextual())
			{
				result.add(file.asText());
			}
			else
			{
				String srcFile = file.get(AIROptions.FILES__FILE).asText();
				String destPath = file.get(AIROptions.FILES__PATH).asText();
				addFile(new File(srcFile), destPath, result);
			}
		}
	}

	private void addFile(File srcFile, String destPath, List<String> result)
	{
		if(srcFile.isDirectory())
		{
			//Adobe's documentation for adt says that the -e option can
			//accept a directory, but it only seems to work with files, so
			//we read the directory contents to add the files individually
			File destFile = new File(destPath, srcFile.getName());
			destPath = destFile.getAbsolutePath();
			File[] files = destFile.listFiles();
			for(int i = 0, length = files.length; i < length; i++)
			{
				File file = files[i];
				addFile(file, destPath, result);
			}
			return;
		}
		result.add("-e");
		result.add(PathUtils.escapePath(srcFile.getPath(), false));
		result.add(PathUtils.escapePath(destPath, false));
	}
	
	private void setPathValueWithoutAssignment(String optionName, String value, boolean checkIfExists, List<String> result) throws FileNotFoundException
	{
		if(checkIfExists)
		{
			File file = new File(value);
			if(!file.isAbsolute())
			{
				file = new File(System.getProperty("user.dir"), value);
			}
			if(!file.exists())
			{
				throw new FileNotFoundException("Path for Adobe AIR option \"" + optionName + "\" not found: " + value);
			}
		}
		String pathValue = PathUtils.escapePath(value.toString(), false);
		result.add("-" + optionName);
		result.add(pathValue);
	}
	
	private void parseDebugOptions(JsonNode airOptions, String platform, List<String> result)
	{
		boolean useDefault = true;
		if(airOptions.has(platform))
		{
			JsonNode platformOptions = airOptions.get(platform);
			if(platformOptions.has(AIROptions.CONNECT))
			{
				useDefault = false;
				JsonNode connectValue = platformOptions.get(AIROptions.CONNECT);
				if(!connectValue.isBoolean())
				{
					result.add("-" + AIROptions.CONNECT);
					result.add(connectValue.asText());
				}
				else if(connectValue.asBoolean() == true)
				{
					result.add("-" + AIROptions.CONNECT);
				}
			}
			if(platformOptions.has(AIROptions.LISTEN))
			{
				useDefault = false;
				JsonNode listenValue = platformOptions.get(AIROptions.LISTEN);
				if(!listenValue.isBoolean())
				{
					result.add("-" + AIROptions.LISTEN);
					result.add(listenValue.asText());
				}
				else if(listenValue.asBoolean() == true)
				{
					result.add("-" + AIROptions.LISTEN);
				}
			}
		}
		if(useDefault)
		{
			//if both connect and listen options are omitted, use the
			//connect as the default with no host name.
			result.add("-" + AIROptions.CONNECT);
		}
	}
	
	protected void parseSigningOptions(JsonNode signingOptions, boolean debug, List<String> result) throws FileNotFoundException
	{
		if(signingOptions.has(AIRSigningOptions.DEBUG) && debug)
		{
			parseSigningOptions(signingOptions.get(AIRSigningOptions.DEBUG), debug, result);
			return;
		}
		if(signingOptions.has(AIRSigningOptions.RELEASE) && !debug)
		{
			parseSigningOptions(signingOptions.get(AIRSigningOptions.RELEASE), debug, result);
			return;
		}

		if(signingOptions.has(AIRSigningOptions.PROVISIONING_PROFILE))
		{
			setPathValueWithoutAssignment(AIRSigningOptions.PROVISIONING_PROFILE, signingOptions.get(AIRSigningOptions.PROVISIONING_PROFILE).asText(), checkPaths, result);
		}
		if(signingOptions.has(AIRSigningOptions.ALIAS))
		{
			setValueWithoutAssignment(AIRSigningOptions.ALIAS, signingOptions.get(AIRSigningOptions.ALIAS).asText(), result);
		}
		if(signingOptions.has(AIRSigningOptions.STORETYPE))
		{
			setValueWithoutAssignment(AIRSigningOptions.STORETYPE, signingOptions.get(AIRSigningOptions.STORETYPE).asText(), result);
		}
		if(signingOptions.has(AIRSigningOptions.KEYSTORE))
		{
			setPathValueWithoutAssignment(AIRSigningOptions.KEYSTORE, signingOptions.get(AIRSigningOptions.KEYSTORE).asText(), checkPaths, result);
		}
		if(signingOptions.has(AIRSigningOptions.PROVIDER_NAME))
		{
			setValueWithoutAssignment(AIRSigningOptions.PROVIDER_NAME, signingOptions.get(AIRSigningOptions.PROVIDER_NAME).asText(), result);
		}
		if(signingOptions.has(AIRSigningOptions.TSA))
		{
			setValueWithoutAssignment(AIRSigningOptions.TSA, signingOptions.get(AIRSigningOptions.TSA).asText(), result);
		}
		Iterator<String> fieldNames = signingOptions.fieldNames();
		while(fieldNames.hasNext())
		{
			String fieldName = fieldNames.next();
			switch(fieldName)
			{
				case AIRSigningOptions.ALIAS:
				case AIRSigningOptions.STORETYPE:
				case AIRSigningOptions.KEYSTORE:
				case AIRSigningOptions.PROVIDER_NAME:
				case AIRSigningOptions.TSA:
				case AIRSigningOptions.PROVISIONING_PROFILE:
				{
					break;
				}
				default:
				{
					throw new Error("Unknown Adobe AIR signing option: " + fieldName);
				}
			}
		}
	}
}