/*
Copyright 2016 Bowler Hat LLC

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
import * as fs from "fs";
import * as path from "path";
import * as vscode from "vscode";

export function isValidSDK(absolutePath: string, validateVersion?: (path: string) => boolean): boolean
{
	if(!absolutePath)
	{
		return false;
	}
	//the following files are required to consider this a valid SDK
	let filePaths =
	[
		path.join(absolutePath, "flex-sdk-description.xml"),
		path.join(absolutePath, "js", "bin", "asjsc"),
		path.join(absolutePath, "lib", "compiler.jar")
	]
	for(let i = 0, count = filePaths.length; i < count; i++)
	{
		let filePath = filePaths[i];	
		if(!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory())
		{
			return false;
		}
	}
	if(validateVersion && !validateVersion(absolutePath))
	{
		return false;
	}
	return true;
}

export function findSDK(validateVersion?: (path: string) => boolean): string
{
	let sdkPath = <string> vscode.workspace.getConfiguration("nextgenas").get("flexjssdk");
	if(sdkPath)
	{
		if(isValidSDK(sdkPath, validateVersion))
		{
			return sdkPath;
		}
		//if the user specified an SDK in the settings, no fallback
		//otherwise, it could be confusing
		return null;
	}
	try
	{
		sdkPath = require.resolve("flexjs");
		if(isValidSDK(sdkPath, validateVersion))
		{
			return sdkPath;
		}
	}
	catch(error) {};
	if("FLEX_HOME" in process.env)
	{
		sdkPath = <string> process.env.FLEX_HOME;
		if(isValidSDK(sdkPath, validateVersion))
		{
			return sdkPath;
		}
	}

	if("PATH" in process.env)
	{
		let PATH = <string> process.env.PATH;
		let paths = PATH.split(path.delimiter);
		let pathCount = paths.length;
		for(let i = 0; i < pathCount; i++)
		{
			let currentPath = paths[i];
			//first check if this directory contains the NPM version for Windows
			let asjscPath = path.join(currentPath, "asjsc.cmd");
			if(fs.existsSync(asjscPath))
			{
				sdkPath = path.join(path.dirname(asjscPath), "node_modules", "flexjs");
				if(isValidSDK(sdkPath, validateVersion))
				{
					return sdkPath;
				}
			}
			asjscPath = path.join(currentPath, "asjsc");
			if(fs.existsSync(asjscPath))
			{
				//this may a symbolic link rather than the actual file, such as
				//when Apache FlexJS is installed with NPM on Mac, so get the
				//real path.
				asjscPath = fs.realpathSync(asjscPath);
				sdkPath = path.join(path.dirname(asjscPath), "..", "..");
				if(isValidSDK(sdkPath, validateVersion))
				{
					return sdkPath;
				}
			}
		}
	}
	return null;
}

export function findJava(validate: (path: string) => boolean): string
{
	let configJavaPath = <string> vscode.workspace.getConfiguration("nextgenas").get("java");
	if(configJavaPath)
	{
		if(validate(configJavaPath))
		{
			return configJavaPath;
		}
		//if the user specified java in the settings, no fallback
		//otherwise, it could be confusing
		return null;
	}

	var executableFile:String = "java";
	if(process["platform"] === "win32")
	{
		executableFile += ".exe";
	}

	if("JAVA_HOME" in process.env)
	{
		let javaHome = <string> process.env.JAVA_HOME;
		let javaPath = path.join(javaHome, "bin", executableFile);
		if(validate(javaPath))
		{
			return javaPath;
		}
	}

	if("PATH" in process.env)
	{
		let PATH = <string> process.env.PATH;
		let paths = PATH.split(path.delimiter);
		let pathCount = paths.length;
		for(let i = 0; i < pathCount; i++)
		{
			let javaPath = path.join(paths[i], executableFile);
			if(validate(javaPath))
			{
				return javaPath;
			}
		}
	}
     
	return null;
}