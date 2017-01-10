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

export default function(validate: (sdkPath: string) => boolean): string
{
	let sdkPath = <string> vscode.workspace.getConfiguration("nextgenas").get("flexjssdk");
	if(sdkPath)
	{
		if(validate(sdkPath))
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
		if(validate(sdkPath))
		{
			return sdkPath;
		}
	}
	catch(error) {};
	if("FLEX_HOME" in process.env)
	{
		sdkPath = <string> process.env.FLEX_HOME;
		if(validate(sdkPath))
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
				if(validate(sdkPath))
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
				if(validate(sdkPath))
				{
					return sdkPath;
				}
			}
		}
	}
	return null;
}