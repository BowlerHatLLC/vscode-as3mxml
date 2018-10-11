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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
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
		if(status != 0 && status != 14)
		{
			return new DeviceCommandResult(true, "Device uninstall failed for platform \"" + platform + "\" with status code " + status + ".");
		}
		return new DeviceCommandResult(false);
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
		if(status != 0)
		{
			return new DeviceCommandResult(true, "Installing app on device failed for platform \"" + platform + "\" with status code: " + status + ".");
		}
		return new DeviceCommandResult(false);
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
		if(status != 0)
		{
			return new DeviceCommandResult(true, "Launching app on device failed for platform \"" + platform + "\" with status code: " + status + ".");
		}
		return new DeviceCommandResult(false);
    }

    public static void stopForwardPortCommand(String platform, int port, Path workspacePath, Path adbPath, Path idbPath)
    {
		ArrayList<String> options = new ArrayList<>();
		if(platform.equals("ios"))
		{
			options.add(idbPath.toString());
			options.add("-stopforward");
			options.add(Integer.toString(port));
		}
		else if(platform.equals("android"))
		{
			options.add(adbPath.toString());
			options.add("forward");
			options.add("--remove");
			options.add(Integer.toString(port));
		}
		File cwd = workspacePath.toFile();
		try
		{
			Process process = new ProcessBuilder()
				.command(options)
				.directory(cwd)
				.inheritIO()
				.start();
			process.waitFor();
		}
		catch(InterruptedException e)
		{
		}
		catch(IOException e)
		{
		}
	}

    public static DeviceCommandResult forwardPortCommand(String platform, int port, Path workspacePath, Path adbPath, Path idbPath)
    {
		ArrayList<String> options = new ArrayList<>();
		if(platform.equals("ios"))
		{
			String deviceHandle = findDeviceHandle(workspacePath, idbPath);
			if(deviceHandle == null)
			{
				return new DeviceCommandResult(true, "Forwarding port for debugging failed for platform \"" + platform + "\" and port " + port + " because no connected devices could be found.");
			}
			options.add(idbPath.toString());
			options.add("-forward");
			options.add(Integer.toString(port));
			options.add(Integer.toString(port));
			options.add(deviceHandle);
		}
		else if(platform.equals("android"))
		{
			options.add(adbPath.toString());
			options.add("forward");
			options.add("tcp:" + port);
			options.add("tcp:" + port);
		}

		File cwd = workspacePath.toFile();
		int status = -1;
		try
		{
			Process process = new ProcessBuilder()
				.command(options)
				.directory(cwd)
				.inheritIO()
				.start();
			if(platform.equals("ios"))
			{
				//if idb starts successfully, it will continue running without
				//exiting. we'll stop it later!
				try
				{
					status = process.exitValue();
				}
				catch(IllegalThreadStateException e)
				{
					status = 0;
				}
			}
			else
			{
				status = process.waitFor();
			}
		}
		catch(InterruptedException e)
		{
			return new DeviceCommandResult(true, "Forwarding port for debugging failed for platform \"" + platform + "\" and port " + port + " with error: " + e.toString());
		}
		catch(IOException e)
		{
			return new DeviceCommandResult(true, "Forwarding port for debugging failed for platform \"" + platform + "\" and port " + port + " with error: " + e.toString());
		}
		if(status != 0)
		{
			return new DeviceCommandResult(true, "Forwarding port for debugging failed for platform \"" + platform + "\" and port " + port + " with status code: " + status + ".");
		}
		return new DeviceCommandResult(false);
	}
	
	private static String findDeviceHandle(Path workspacePath, Path idbPath)
	{
		ArrayList<String> options = new ArrayList<>();
		options.add(idbPath.toString());
		options.add("-devices");

		File cwd = workspacePath.toFile();
		Process process = null;
		int status = -1;
		try
		{
			process = new ProcessBuilder()
				.command(options)
				.directory(cwd)
				.redirectInput(Redirect.INHERIT)
				.redirectError(Redirect.INHERIT)
				.redirectOutput(Redirect.PIPE)
				.start();
			status = process.waitFor();
		}
		catch(InterruptedException e)
		{
			return null;
		}
		catch(IOException e)
		{
			return null;
		}
		if(status != 0)
		{
			return null;
		}
		BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
		try
		{
			//if no devices are attached, the output looks like this:
			//No connected device found.

			//otherwise, the output looks like this:
			//List of attached devices:
			//Handle	DeviceClass	DeviceUUID					DeviceName
			//   1	iPhone  	0000000000000000000000000000000000000000	iPhone
			String line = null;
			while((line = reader.readLine()) != null)
			{
				if(line.startsWith("   "))
				{
					int index = line.indexOf("\t");
					if (index != -1)
					{
						return line.substring(3, index);
					}
				}
			}
			return null;
		}
		catch(IOException e)
		{
			return null;
		}
	}
}
