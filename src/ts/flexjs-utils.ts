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

export function isValidSDK(absolutePath: string)
{
	if(!absolutePath)
	{
		return false;
	}
	let sdkDescriptionPath = path.join(absolutePath, "flex-sdk-description.xml");	
	if(!fs.existsSync(sdkDescriptionPath) || fs.statSync(sdkDescriptionPath).isDirectory())
	{
		return false;
	}
	let asjscPath = path.join(absolutePath, "js", "bin", "asjsc");
	if(!fs.existsSync(asjscPath) || fs.statSync(asjscPath).isDirectory())
	{
		return false;
	}
	return true;
}

export function findSDK(): string
{
	let sdkPath = <string> vscode.workspace.getConfiguration("nextgenas").get("flexjssdk");;
	if(isValidSDK(sdkPath))
	{
		return sdkPath;
	}
	try
	{
		sdkPath = require.resolve("flexjs");
		if(isValidSDK(sdkPath))
		{
			return sdkPath;
		}
	}
	catch(error) {};
	if("FLEX_HOME" in process.env)
	{
		sdkPath = <string> process.env.FLEX_HOME;
		if(isValidSDK(sdkPath))
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
			let asjscPath = path.join(currentPath, "asjsc");
			if(fs.existsSync(asjscPath))
			{
				//this may not be the actual file if Apache FlexJS was
				//installed with NPM
				asjscPath = fs.realpathSync(asjscPath);
				sdkPath = path.join(path.dirname(asjscPath), "..", "..");
				if(isValidSDK(sdkPath))
				{
					return sdkPath;
				}
			}
		}
	}
	return null;
}

export function findJava(): string
{
	var executableFile:String = "java";
	if(process["platform"] === "win32")
	{
		executableFile += ".exe";
	}

	if("JAVA_HOME" in process.env)
	{
		let javaHome = <string> process.env.JAVA_HOME;
		let javaPath = path.join(javaHome, "bin", executableFile);
		if(fs.existsSync(javaPath))
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
			if(fs.existsSync(javaPath))
			{
				return javaPath;
			}
		}
	}
     
	return null;
}