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
import * as fs from "fs";
import * as json5 from "json5";
import * as path from "path";
import * as vscode from "vscode";
import getFrameworkSDKPathWithFallbacks from "./getFrameworkSDKPathWithFallbacks";

const ASCONFIG_JSON = "asconfig.json"
const FILE_EXTENSION_AS = ".as";;
const FILE_EXTENSION_MXML = ".mxml";
const CONFIG_AIR = "air";
const CONFIG_AIRMOBILE = "airmobile";
const FIELD_CONFIG = "config";
const FIELD_APPLICATION = "application";
const FIELD_AIR_OPTIONS = "airOptions";
const FIELD_TARGET = "target";
const PLATFORM_IOS = "ios";
const PLATFORM_ANDROID = "android";
const PLATFORM_AIR = "air";
const PLATFORM_WINDOWS = "windows";
const PLATFORM_MAC = "mac";
const TARGET_BUNDLE = "bundle";

interface ActionScriptTaskDefinition extends vscode.TaskDefinition
{
	debug: boolean;
	air?: string;
}

export default class ActionScriptTaskProvider implements vscode.TaskProvider
{
	provideTasks(token: vscode.CancellationToken): Promise<vscode.Task[]>
	{
		let workspaceRoot = vscode.workspace.rootPath;
		if(!workspaceRoot)
		{
			return Promise.resolve([]);
		}

		let provideTask = false;
		let isAIRMobile = false;
		let isBundleWindows = false;
		let isBundleMac = false;
		let isAIRSharedRuntime = false;
		let asconfigJsonPath = path.join(workspaceRoot, ASCONFIG_JSON);
		if(fs.existsSync(asconfigJsonPath))
		{
			//if asconfig.json exists in the root, always provide the tasks
			provideTask = true;
			let asconfigJson = this.readASConfigJSON(asconfigJsonPath);
			if(asconfigJson !== null)
			{
				isAIRMobile = this.isAIRMobile(asconfigJson);
				isBundleWindows = this.isBundleWindows(asconfigJson);
				isBundleMac = this.isBundleMac(asconfigJson);
				if(!isAIRMobile && !isBundleWindows && !isBundleMac)
				{
					isAIRSharedRuntime = this.isAIRSharedRuntime(asconfigJson);
				}
			}
		}
		if(!provideTask && vscode.window.activeTextEditor)
		{
			let fileName = vscode.window.activeTextEditor.document.fileName;
			if(fileName.endsWith(FILE_EXTENSION_AS) || fileName.endsWith(FILE_EXTENSION_MXML))
			{
				//we couldn't find asconfig.json, but an .as or .mxml file is
				//currently open, so might as well provide the tasks
				provideTask = true;
			}
		}
		if(!provideTask)
		{
			return Promise.resolve([]);
		}

		let command = this.getCommand();
		let frameworkSDK = getFrameworkSDKPathWithFallbacks();

		let result =
		[
			this.getTask("debug build using asconfig.json",
				command, frameworkSDK, true, null),
			this.getTask("release build using asconfig.json",
				command, frameworkSDK, false, null),
		];

		if(isAIRMobile)
		{
			result.push(this.getTask("iOS debug package using asconfig.json",
				command, frameworkSDK, true, PLATFORM_IOS));
			result.push(this.getTask("iOS release package using asconfig.json",
				command, frameworkSDK, false, PLATFORM_IOS));
			result.push(this.getTask("Android debug package using asconfig.json",
				command, frameworkSDK, true, PLATFORM_ANDROID));
			result.push(this.getTask("Android release package using asconfig.json",
				command, frameworkSDK, false, PLATFORM_ANDROID));
		}
		if(isBundleWindows)
		{
			result.push(this.getTask("Windows captive runtime package using asconfig.json",
				command, frameworkSDK, false, PLATFORM_WINDOWS));
		}
		if(isBundleMac)
		{
			result.push(this.getTask("macOS captive runtime package using asconfig.json",
				command, frameworkSDK, false, PLATFORM_MAC));
		}
		if(isAIRSharedRuntime)
		{
			result.push(this.getTask("shared runtime debug package using asconfig.json", command, frameworkSDK, true, PLATFORM_AIR));
			result.push(this.getTask("shared runtime debug package using asconfig.json", command, frameworkSDK, false, PLATFORM_AIR));
		}

		return Promise.resolve(result);
	}

	resolveTask(task: vscode.Task): vscode.Task | undefined
	{
		return undefined;
	}

	private getTask(description: string, command: string, sdk: string, debug: boolean, airPlatform: string|null): vscode.Task
	{
		let airIdentifier: ActionScriptTaskDefinition = { type: "actionscript", debug: debug, air: airPlatform };
		let airOptions = ["--flexHome", sdk];
		if(debug)
		{
			airOptions.push("--debug=true");
		}
		if(airPlatform !== null)
		{
			airOptions.push("--air", airPlatform);
		}
		let source = airPlatform === null ? "ActionScript" : "Adobe AIR";
		let airTask = new vscode.Task(airIdentifier, description, source,
			new vscode.ProcessExecution(command, airOptions), ["$nextgenas_nomatch"]);
		airTask.group = vscode.TaskGroup.Build;
		return airTask;
	}

	private getCommand(): string
	{
		let nodeModulesBin = path.join(vscode.workspace.rootPath, "node_modules", ".bin");
		const platform = process.platform;
		if(platform === "win32")
		{
			let executableName = "asconfigc.cmd";
			//start out by looking for asconfigc in the workspace's local Node modules
			let winPath = path.join(nodeModulesBin, executableName);
			if(fs.existsSync(winPath))
			{
				return winPath;
			}
			//otherwise, try to use a global executable
			return executableName;
		}
		let executableName = "asconfigc";
		let unixPath = path.join(nodeModulesBin, executableName);
		if(fs.existsSync(unixPath))
		{
			return unixPath;
		}
		return executableName;
	}
	
	private readASConfigJSON(filePath: string): string
	{
		try
		{
			let contents = fs.readFileSync(filePath, "utf8");
			return json5.parse(contents);
		}
		catch(error)
		{

		}
		return null;
	}

	private isAIRSharedRuntime(asconfigJson: any): boolean
	{
		if(FIELD_APPLICATION in asconfigJson)
		{
			return true;
		}
		if(FIELD_AIR_OPTIONS in asconfigJson)
		{
			return true;
		}
		if(FIELD_CONFIG in asconfigJson)
		{
			let config = asconfigJson[FIELD_CONFIG];
			if(config === CONFIG_AIR || config === CONFIG_AIRMOBILE)
			{
				return true;
			}
		}
		return false;
	}
	
	private isAIRMobile(asconfigJson: any): boolean
	{
		if(FIELD_CONFIG in asconfigJson)
		{
			let config = asconfigJson[FIELD_CONFIG];
			if(config === CONFIG_AIRMOBILE)
			{
				return true;
			}
		}
		return false;
	}
	
	private isBundleWindows(asconfigJson: any): boolean
	{
		if(process.platform !== "win32")
		{
			return false;
		}
		if(!(PLATFORM_WINDOWS in asconfigJson))
		{
			return false;
		}
		let windows = asconfigJson[PLATFORM_WINDOWS];
		if(!(FIELD_TARGET in windows))
		{
			return false;
		}
		let target = windows[FIELD_TARGET];
		return target === TARGET_BUNDLE;
	}
	
	private isBundleMac(asconfigJson: any): boolean
	{
		if(process.platform !== "darwin")
		{
			return false;
		}
		if(!(PLATFORM_MAC in asconfigJson))
		{
			return false;
		}
		let mac = asconfigJson[PLATFORM_MAC];
		if(!(FIELD_TARGET in mac))
		{
			return false;
		}
		let target = mac[FIELD_TARGET];
		return target === TARGET_BUNDLE;
	}
}