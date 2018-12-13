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
import * as path from "path";
import * as vscode from "vscode";
import getJavaClassPathDelimiter from "../utils/getJavaClassPathDelimiter";
import findJava from "./findJava";
import validateJava from "./validateJava";

export interface SWFDebugPaths
{
	javaPath: string;
	frameworkSDKPath: string;
	editorSDKPath: string;
}

export type SWFDebugPathsCallback = () => SWFDebugPaths;

export default class SWFDebugAdapterDescriptorFactory implements vscode.DebugAdapterDescriptorFactory
{
	constructor(public pathsCallback: SWFDebugPathsCallback) {}

	createDebugAdapterDescriptor(session: vscode.DebugSession, executable: vscode.DebugAdapterExecutable): vscode.ProviderResult<vscode.DebugAdapterDescriptor>
	{
		let paths = this.pathsCallback();
		if(!paths)
		{
			throw new Error("SWF debugger launch failed. Java path or SDK path not found.");
		}
		if(!paths.javaPath)
		{
			throw new Error("SWF debugger launch failed. Java path not found.");
		}
		if(!paths.frameworkSDKPath)
		{
			throw new Error("SWF debugger launch failed. Framework SDK path not found.");
		}
		let args =
		[
			"-Dworkspace=" + session.workspaceFolder.uri.fsPath,
	
			//uncomment to debug the SWF debugger JAR
			//"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005",
	
			"-Dflexlib=" + path.resolve(paths.frameworkSDKPath, "frameworks"),
			"-cp",
			getClassPath(paths.editorSDKPath),
			"com.as3mxml.vscode.SWFDebug"
		];
		return new vscode.DebugAdapterExecutable(paths.javaPath, args);
	}

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