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
package com.as3mxml.vscode.debug.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class DeviceInstallUtils
{
	private static final String ASCONFIG_JSON = "asconfig.json";
	private static final Pattern idPattern = Pattern.compile("<id>([\\w+\\.]+)<\\/id>");

	public static class DeviceCommandResult
	{
		public DeviceCommandResult(boolean error)
		{
			this.error = error;
		}

		public DeviceCommandResult(boolean error, String message)
		{
			this.error = error;
			this.message = message;
		}

		public boolean error;
		public String message;
	}

	public static String findApplicationID(Path workspacePath)
	{
		Path asconfigPath = workspacePath.resolve(ASCONFIG_JSON);
		if(!asconfigPath.toFile().exists())
		{
			return null;
		}
		String asconfigJsonContents = null;
		try
		{
			asconfigJsonContents = new String(Files.readAllBytes(asconfigPath));
		}
		catch(IOException e)
		{
			return null;
		}
		Path applicationPath = null;
		try
		{
			JsonParser parser = new JsonParser();
			JsonObject asconfigJSON = parser.parse(asconfigJsonContents).getAsJsonObject();
			if(!asconfigJSON.has("application"))
			{
				return null;
			}
			String application = asconfigJSON.get("application").getAsString();
			applicationPath = Paths.get(application);
			if(!applicationPath.isAbsolute())
			{
				applicationPath = workspacePath.resolve(application);
			}
		}
		catch(Exception e)
		{
			return null;
		}
		if(applicationPath == null)
		{
			return null;
		}
		String appDescriptorContents = null;
		try
		{
			appDescriptorContents = new String(Files.readAllBytes(applicationPath));
		}
		catch(IOException e)
		{
			return null;
		}

		Matcher matcher = idPattern.matcher(appDescriptorContents);
		if(!matcher.find(1))
		{
			return null;
		}
		return matcher.group(1);
	}

	public static Path findOutputPath(String platform, Path workspacePath)
	{
		Path asconfigPath = workspacePath.resolve(ASCONFIG_JSON);
		if(!asconfigPath.toFile().exists())
		{
			return null;
		}
		String contents = null;
		try
		{
			contents = new String(Files.readAllBytes(asconfigPath));
		}
		catch(IOException e)
		{
			return null;
		}
		String output = null;
		try
		{
			JsonParser parser = new JsonParser();
			JsonObject asconfigJSON = parser.parse(contents).getAsJsonObject();
			if(!asconfigJSON.has("airOptions"))
			{
				return null;
			}
			JsonObject airOptions = asconfigJSON.get("airOptions").getAsJsonObject();
			if(!airOptions.has(platform))
			{
				return null;
			}
			JsonObject platformJSON = airOptions.get(platform).getAsJsonObject();
			if(!platformJSON.has("output"))
			{
				return null;
			}
			output = platformJSON.get("output").getAsString();
		}
		catch(Exception e)
		{
			return null;
		}
		if(output == null)
		{
			return null;
		}
		return Paths.get(output);
	}

    public static DeviceCommandResult runUninstallCommand(String platform, String appID, Path workspacePath, Path adtPath)
    {
		ArrayList<String> options = new ArrayList<>();
		options.add(adtPath.toString());
		options.add("-uninstallApp");
		options.add("-platform");
		options.add(platform);
		options.add("-appid");
		options.add(appID);

		File cwd = workspacePath.toFile();
		int status = -1;
		try
		{
			Process process = new ProcessBuilder()
				.command(options)
				.directory(cwd)
				.start();
			status = process.waitFor();
		}
		catch(InterruptedException e)
		{
			return new DeviceCommandResult(true, "Device uninstall failed for platform \"" + platform + "\" with error: " + e.toString());
		}
		catch(IOException e)
		{
			return new DeviceCommandResult(true, "Device uninstall failed for platform \"" + platform + "\" with error: " + e.toString());
		}
		//14 imeans that the app isn't installed on the device, and that's fine
		if(status == 0 || status == 14)
		{
			return new DeviceCommandResult(false);
		}
		return new DeviceCommandResult(true, "Device uninstall failed for platform \"" + platform + "\" with status code " + status + ".");
    }

    public static DeviceCommandResult runInstallCommand(String platform, Path packagePath, Path workspacePath, Path adtPath)
    {
		ArrayList<String> options = new ArrayList<>();
		options.add(adtPath.toString());
		options.add("-installApp");
		options.add("-platform");
		options.add(platform);
		options.add("-package");
		options.add(packagePath.toString());

		File cwd = workspacePath.toFile();
		int status = -1;
		try
		{
			Process process = new ProcessBuilder()
				.command(options)
				.directory(cwd)
				.start();
			status = process.waitFor();
		}
		catch(InterruptedException e)
		{
			return new DeviceCommandResult(true, "Installing app on device failed for platform \"" + platform + "\" with error: " + e.toString());
		}
		catch(IOException e)
		{
			return new DeviceCommandResult(true, "Installing app on device failed for platform \"" + platform + "\" with error: " + e.toString());
		}
		if(status == 0)
		{
			return new DeviceCommandResult(false);
		}
		return new DeviceCommandResult(true, "Installing app on device failed for platform \"" + platform + "\" with status code: " + status + ".");
    }

    public static DeviceCommandResult runLaunchCommand(String platform, String appID, Path workspacePath, Path adtPath)
    {
		ArrayList<String> options = new ArrayList<>();
		options.add(adtPath.toString());
		options.add("-launchApp");
		options.add("-platform");
		options.add(platform);
		options.add("-appid");
		options.add(appID);

		File cwd = workspacePath.toFile();
		int status = -1;
		try
		{
			Process process = new ProcessBuilder()
				.command(options)
				.directory(cwd)
				.inheritIO()
				.start();
			status = process.waitFor();
		}
		catch(InterruptedException e)
		{
			return new DeviceCommandResult(true, "Launching app on device failed for platform \"" + platform + "\" with error: " + e.toString());
		}
		catch(IOException e)
		{
			return new DeviceCommandResult(true, "Launching app on device failed for platform \"" + platform + "\" with error: " + e.toString());
		}
		if(status == 0)
		{
			return new DeviceCommandResult(false);
		}
		return new DeviceCommandResult(true, "Launching app on device failed for platform \"" + platform + "\" with status code: " + status + ".");
    }
}
