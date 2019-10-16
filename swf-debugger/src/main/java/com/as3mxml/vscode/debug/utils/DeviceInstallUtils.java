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
package com.as3mxml.vscode.debug.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Path;
import java.util.ArrayList;

public class DeviceInstallUtils
{
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
			return new DeviceCommandResult(true, "Device uninstall failed for platform \"" + platform + "\" and application ID \"" + appID + "\" with error: " + e.toString());
		}
		catch(IOException e)
		{
			return new DeviceCommandResult(true, "Device uninstall failed for platform \"" + platform + "\" and application ID \"" + appID + "\" with error: " + e.toString());
		}
		//14 imeans that the app isn't installed on the device, and that's fine
		if(status != 0 && status != 14)
		{
			return new DeviceCommandResult(true, "Device uninstall failed for platform \"" + platform + "\" and application ID \"" + appID + "\" with status code " + status + ".");
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
			return new DeviceCommandResult(true, "Installing app on device failed for platform \"" + platform + "\" and path \"" + packagePath.toString() + "\" with error: " + e.toString());
		}
		catch(IOException e)
		{
			return new DeviceCommandResult(true, "Installing app on device failed for platform \"" + platform + "\" and path \"" + packagePath.toString() + "\" with error: " + e.toString());
		}
		if(status != 0)
		{
			return new DeviceCommandResult(true, "Installing app on device failed for platform \"" + platform + "\" and path \"" + packagePath.toString() + "\" with status code: " + status + ".");
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
			return new DeviceCommandResult(true, "Launching app on device failed for platform \"" + platform + "\" and application ID \"" + appID + "\" with error: " + e.toString());
		}
		catch(IOException e)
		{
			return new DeviceCommandResult(true, "Launching app on device failed for platform \"" + platform + "\" and application ID \"" + appID + "\" with error: " + e.toString());
		}
		if(status != 0)
		{
			return new DeviceCommandResult(true, "Launching app on device failed for platform \"" + platform + "\" and application ID \"" + appID + "\" with status code: " + status + ".");
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
			options.add("tcp:" + Integer.toString(port));
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
