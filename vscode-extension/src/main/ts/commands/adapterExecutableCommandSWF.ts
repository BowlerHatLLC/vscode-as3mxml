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
import * as path from "path";
import * as vscode from "vscode";
import getJavaClassPathDelimiter from "../utils/getJavaClassPathDelimiter";

export default function(javaPath: string, editorSDKPath: string, frameworkSDKPath: string)
{
	let args = [

		//uncomment to debug the SWF debugger JAR
		//"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005",

		"-Dflexlib=" + path.resolve(frameworkSDKPath, "frameworks"),
		"-cp",
		getClassPath(editorSDKPath),
		"com.nextgenactionscript.vscode.SWFDebug"
	];
	if(vscode.workspace.rootPath)
	{
		args.unshift("-Dworkspace=" + vscode.workspace.rootPath);
	}
	let result: any =
	{
		command: javaPath,
		args: args
	};
	return result;
}

function getClassPath(sdkPath: string)
{
	let extension = vscode.extensions.getExtension("bowlerhatllc.vscode-nextgenas");
	let cp = path.resolve(extension.extensionPath, "bin", "*");
	if(sdkPath)
	{
		cp += getJavaClassPathDelimiter() + path.resolve(sdkPath, "lib", "*");
	}
	else
	{
		cp += getJavaClassPathDelimiter() + path.resolve(extension.extensionPath, "bundled-compiler", "*");
	}
	return cp;
		
}