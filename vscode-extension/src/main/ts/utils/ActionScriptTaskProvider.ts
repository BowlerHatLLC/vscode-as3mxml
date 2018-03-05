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
const PLATFORM_IOS_SIMULATOR = "ios_simulator";
const PLATFORM_ANDROID = "android";
const PLATFORM_AIR = "air";
const PLATFORM_WINDOWS = "windows";
const PLATFORM_MAC = "mac";
const TARGET_BUNDLE = "bundle";
const MATCHER = "$nextgenas_nomatch";
const TASK_TYPE = "actionscript";

interface ActionScriptTaskDefinition extends vscode.TaskDefinition
{
	debug: boolean;
	air?: string;
}

export default class ActionScriptTaskProvider implements vscode.TaskProvider
{
	constructor(context: vscode.ExtensionContext, public javaExecutablePath: string)
	{
		this._context = context;
	}

	private _context: vscode.ExtensionContext;

	provideTasks(token: vscode.CancellationToken): Promise<vscode.Task[]>
	{
		if(vscode.workspace.workspaceFolders === undefined)
		{
			return Promise.resolve([]);
		}

		let result: vscode.Task[] = [];
		vscode.workspace.workspaceFolders.forEach((workspaceFolder) =>
		{
			this.provideTasksForWorkspace(workspaceFolder, result);
		});
		return Promise.resolve(result);
	}

	private provideTasksForWorkspace(workspaceFolder: vscode.WorkspaceFolder, result: vscode.Task[])
	{
		let provideTask = false;
		let isAIRMobile = false;
		let isBundleWindows = false;
		let isBundleMac = false;
		let isAIRDesktop = false;
		let asconfigJsonPath = path.join(workspaceFolder.uri.fsPath, ASCONFIG_JSON);
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
				if(!isAIRMobile)
				{
					isAIRDesktop = this.isAIRDesktop(asconfigJson);
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
			return;
		}

		let command = this.getCommand(workspaceFolder);
		let frameworkSDK = getFrameworkSDKPathWithFallbacks();
		if(frameworkSDK === null)
		{
			//we don't have a valid SDK
			return;
		}

		//compile SWF or Royale JS
		result.push(this.getTask("compile debug build",
			workspaceFolder, command, frameworkSDK, true, null));
		result.push(this.getTask("compile release build",
			workspaceFolder, command, frameworkSDK, false, null));

		if(isAIRMobile)
		{
			result.push(this.getTask("package debug iOS application (Device)",
				workspaceFolder, command, frameworkSDK, true, PLATFORM_IOS));
			result.push(this.getTask("package release iOS application (Device)",
				workspaceFolder, command, frameworkSDK, false, PLATFORM_IOS));
			result.push(this.getTask("package debug iOS application (Simulator)",
				workspaceFolder, command, frameworkSDK, true, PLATFORM_IOS_SIMULATOR));
			result.push(this.getTask("package release iOS application (Simulator)",
				workspaceFolder, command, frameworkSDK, false, PLATFORM_IOS_SIMULATOR));
			result.push(this.getTask("package debug Android application",
				workspaceFolder, command, frameworkSDK, true, PLATFORM_ANDROID));
			result.push(this.getTask("package release Android application",
				workspaceFolder, command, frameworkSDK, false, PLATFORM_ANDROID));
		}
		if((isAIRDesktop && process.platform === "win32") || isBundleWindows)
		{
			result.push(this.getTask("package release Windows application (captive runtime)",
				workspaceFolder, command, frameworkSDK, false, PLATFORM_WINDOWS));
		}
		if((isAIRDesktop && process.platform === "darwin") || isBundleMac)
		{
			result.push(this.getTask("package release macOS application (captive runtime)",
				workspaceFolder, command, frameworkSDK, false, PLATFORM_MAC));
		}
		if(isAIRDesktop && (process.platform !== "win32" || !isBundleWindows) && (process.platform !== "darwin" || !isBundleMac))
		{
			//it's an AIR desktop application and the bundle target is not
			//specified explicitly
			result.push(this.getTask("package debug desktop application (shared runtime)",
				workspaceFolder, command, frameworkSDK, true, PLATFORM_AIR));
			result.push(this.getTask("package release desktop application (shared runtime)",
				workspaceFolder, command, frameworkSDK, false, PLATFORM_AIR));
		}
	}

	resolveTask(task: vscode.Task): vscode.Task | undefined
	{
		console.error("resolve task", task);
		return undefined;
	}

	private getTask(description: string, workspaceFolder: vscode.WorkspaceFolder,
		command: string[], sdk: string, debug: boolean, airPlatform: string): vscode.Task
	{
		let definition: ActionScriptTaskDefinition = { type: TASK_TYPE, debug: debug };
		if(airPlatform)
		{
			definition.air = airPlatform;
		}
		let options = ["--sdk", sdk];
		if(debug)
		{
			options.push("--debug=true");
		}
		else
		{
			options.push("--debug=false");
		}
		if(airPlatform)
		{
			options.push("--air", airPlatform);
		}
		if(command.length > 1)
		{
			options.unshift(...command.slice(1));
		}
		let source = airPlatform === null ? "ActionScript" : "Adobe AIR";
		let execution = new vscode.ProcessExecution(command[0], options);
		let task = new vscode.Task(definition, workspaceFolder, description,
			source, execution, MATCHER);
		task.group = vscode.TaskGroup.Build;
		return task;
	}

	private getDefaultCommand(): string[]
	{
		return [this.javaExecutablePath, "-jar", path.join(this._context.extensionPath, "bin", "asconfigc.jar")];
	}

	private getCommand(workspaceRoot: vscode.WorkspaceFolder): string[]
	{
		let nodeModulesBin = path.join(workspaceRoot.uri.fsPath, "node_modules", ".bin");
		if(process.platform === "win32")
		{
			let executableName = "asconfigc.cmd";
			//start out by looking for asconfigc in the workspace's local Node modules
			let winPath = path.join(nodeModulesBin, executableName);
			if(fs.existsSync(winPath))
			{
				return [winPath];
			}
			let useBundled = <string> vscode.workspace.getConfiguration("nextgenas").get("asconfigc.useBundled");
			if(!useBundled)
			{
				//use an executable on the system path
				return [executableName];
			}
			//use the version bundled with the extension
			return this.getDefaultCommand();
		}
		let executableName = "asconfigc";
		let unixPath = path.join(nodeModulesBin, executableName);
		if(fs.existsSync(unixPath))
		{
			return [unixPath];
		}
		let useBundled = <string> vscode.workspace.getConfiguration("nextgenas").get("asconfigc.useBundled");
		if(!useBundled)
		{
			//use an executable on the system path
			return [executableName];
		}
		//use the version bundled with the extension
		return this.getDefaultCommand();
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

	private isAIRDesktop(asconfigJson: any): boolean
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
			if(config === CONFIG_AIR)
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