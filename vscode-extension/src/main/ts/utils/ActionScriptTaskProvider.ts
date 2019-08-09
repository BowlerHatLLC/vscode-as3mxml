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
import * as fs from "fs";
import * as json5 from "json5";
import * as path from "path";
import * as vscode from "vscode";
import getFrameworkSDKPathWithFallbacks from "./getFrameworkSDKPathWithFallbacks";
import findAnimate from "./findAnimate";

const ASCONFIG_JSON = "asconfig.json"
const FILE_EXTENSION_AS = ".as";;
const FILE_EXTENSION_MXML = ".mxml";
const CONFIG_AIR = "air";
const CONFIG_AIRMOBILE = "airmobile";
const FIELD_CONFIG = "config";
const FIELD_APPLICATION = "application";
const FIELD_AIR_OPTIONS = "airOptions";
const FIELD_TARGET = "target";
const FIELD_ANIMATE_OPTIONS = "animateOptions";
const FIELD_FILE = "file";
const PLATFORM_IOS = "ios";
const PLATFORM_IOS_SIMULATOR = "ios_simulator";
const PLATFORM_ANDROID = "android";
const PLATFORM_AIR = "air";
const PLATFORM_WINDOWS = "windows";
const PLATFORM_MAC = "mac";
const TARGET_AIR = "air";
const TARGET_BUNDLE = "bundle";
const TARGET_NATIVE = "native";
const MATCHER = [];
const TASK_TYPE_ACTIONSCRIPT = "actionscript";
const TASK_TYPE_ANIMATE = "animate";
const TASK_SOURCE_ACTIONSCRIPT = "ActionScript";
const TASK_SOURCE_AIR = "Adobe AIR";
const TASK_SOURCE_ANIMATE = "Adobe Animate";

interface ActionScriptTaskDefinition extends vscode.TaskDefinition
{
	type: typeof TASK_TYPE_ACTIONSCRIPT;
	debug?: boolean;
	air?: string;
	asconfig?: string;
	clean?: boolean;
}

interface AnimateTaskDefinition extends vscode.TaskDefinition
{
	type: typeof TASK_TYPE_ANIMATE;
	debug: boolean;
	publish: boolean;
	asconfig?: string;
}

export default class ActionScriptTaskProvider implements vscode.TaskProvider
{
	constructor(context: vscode.ExtensionContext, public javaExecutablePath: string)
	{
		this._context = context;
	}

	private _context: vscode.ExtensionContext;

	provideTasks(token: vscode.CancellationToken): vscode.ProviderResult<vscode.Task[]>
	{
		if(vscode.workspace.workspaceFolders === undefined)
		{
			return [];
		}

		return new Promise(async (resolve) =>
		{
			let result: vscode.Task[] = [];
			let asconfigJSONs = await vscode.workspace.findFiles("**/asconfig*.json", "**/node_modules/**");
			asconfigJSONs.forEach((jsonURI) =>
			{
				let workspaceFolder = vscode.workspace.getWorkspaceFolder(jsonURI);
				this.provideTasksForASConfigJSON(jsonURI, workspaceFolder, result);
			});
			if(result.length === 0 && vscode.window.activeTextEditor)
			{
				let activeURI = vscode.window.activeTextEditor.document.uri;
				let workspaceFolder = vscode.workspace.getWorkspaceFolder(activeURI);
				if(workspaceFolder)
				{
					let activePath = activeURI.fsPath;
					if(activePath.endsWith(FILE_EXTENSION_AS) || activePath.endsWith(FILE_EXTENSION_MXML))
					{
						//we couldn't find asconfig.json, but an .as or .mxml file
						//is currently open from the this workspace, so might as
						//well provide the tasks
						this.provideTasksForASConfigJSON(null, workspaceFolder, result);
					}
				}
			}
			resolve(result);
		});
	}

	private provideTasksForASConfigJSON(jsonURI: vscode.Uri, workspaceFolder: vscode.WorkspaceFolder, result: vscode.Task[])
	{
		let isAnimate = false;
		let isAIRMobile = false;
		let isAIRDesktop = false;
		let isSharedOverride = false;
		let isRootTargetShared = false;
		let isRootTargetBundle = false;
		let isRootTargetNativeInstaller = false;
		let isWindowsOverrideBundle = false;
		let isMacOverrideBundle = false;
		let isWindowsOverrideNativeInstaller = false;
		let isMacOverrideNativeInstaller = false;
		let isWindowsOverrideShared = false;
		let isMacOverrideShared = false;
		let asconfigJson = this.readASConfigJSON(jsonURI);
		if(asconfigJson !== null)
		{
			isAnimate = this.isAnimate(asconfigJson);
			isAIRMobile = this.isAIRMobile(asconfigJson);
			if(!isAIRMobile)
			{
				isAIRDesktop = this.isAIRDesktop(asconfigJson);
			}
			isSharedOverride = this.isSharedOverride(asconfigJson);
			isRootTargetShared = this.isRootTargetShared(asconfigJson);
			isRootTargetBundle = this.isRootTargetBundle(asconfigJson);
			isRootTargetNativeInstaller = this.isRootTargetNativeInstaller(asconfigJson);
			isWindowsOverrideShared = this.isWindowsOverrideShared(asconfigJson);
			isMacOverrideShared = this.isMacOverrideShared(asconfigJson);
			isWindowsOverrideBundle = this.isWindowsOverrideBundle(asconfigJson);
			isMacOverrideBundle = this.isMacOverrideBundle(asconfigJson);
			isWindowsOverrideNativeInstaller = this.isWindowsOverrideNativeInstaller(asconfigJson);
			isMacOverrideNativeInstaller = this.isMacOverrideNativeInstaller(asconfigJson);
		}

		let frameworkSDK = getFrameworkSDKPathWithFallbacks();
		if(frameworkSDK === null)
		{
			//we don't have a valid SDK
			return;
		}
		let command = this.getCommand(workspaceFolder);

		if(isAnimate)
		{
			//compile SWF with Animate
			let animatePath = findAnimate();
			//don't add the tasks if we cannot find Adobe Animate
			if(animatePath)
			{
				let flaPath = asconfigJson[FIELD_ANIMATE_OPTIONS][FIELD_FILE];
				if(!path.isAbsolute(flaPath))
				{
					flaPath = path.resolve(workspaceFolder.uri.fsPath, flaPath);
				}
				if(fs.existsSync(flaPath))
				{
					let taskNameSuffix = path.basename(flaPath);
					result.push(this.getAnimateTask("compile debug - " + taskNameSuffix,
						jsonURI, workspaceFolder, command, animatePath, true, false));
					result.push(this.getAnimateTask("compile release - " + taskNameSuffix,
						jsonURI, workspaceFolder, command, animatePath, false, false));
					result.push(this.getAnimateTask("publish debug - " + taskNameSuffix,
						jsonURI, workspaceFolder, command, animatePath, true, true));
					result.push(this.getAnimateTask("publish release - " + taskNameSuffix,
						jsonURI, workspaceFolder, command, animatePath, false, true));
				}
			}
			return;
		}

		let taskNameSuffix = this.getTaskNameSuffix(jsonURI, workspaceFolder);

		//compile SWF or Royale JS with asconfigc
		result.push(this.getTask("compile debug" + taskNameSuffix,
			jsonURI, workspaceFolder, command, frameworkSDK, true, null, isAIRDesktop || isAIRMobile));
		result.push(this.getTask("compile release" + taskNameSuffix,
			jsonURI, workspaceFolder, command, frameworkSDK, false, null, false));
		result.push(this.getCleanTask("clean" + taskNameSuffix,
			jsonURI, workspaceFolder, command, frameworkSDK));

		//package mobile AIR application
		if(isAIRMobile)
		{
			result.push(this.getTask("package iOS debug" + taskNameSuffix,
				jsonURI, workspaceFolder, command, frameworkSDK, true, PLATFORM_IOS, false));
			result.push(this.getTask("package iOS release" + taskNameSuffix,
				jsonURI, workspaceFolder, command, frameworkSDK, false, PLATFORM_IOS, false));
			result.push(this.getTask("package iOS simulator debug" + taskNameSuffix,
				jsonURI, workspaceFolder, command, frameworkSDK, true, PLATFORM_IOS_SIMULATOR, false));
			result.push(this.getTask("package iOS simulator release" + taskNameSuffix,
				jsonURI, workspaceFolder, command, frameworkSDK, false, PLATFORM_IOS_SIMULATOR, false));
			result.push(this.getTask("package Android debug" + taskNameSuffix,
				jsonURI, workspaceFolder, command, frameworkSDK, true, PLATFORM_ANDROID, false));
			result.push(this.getTask("package Android release" + taskNameSuffix,
				jsonURI, workspaceFolder, command, frameworkSDK, false, PLATFORM_ANDROID, false));
		}

		//desktop platform targets are a little trickier because some can only
		//be built on certain platforms. windows can't package for mac, and mac
		//can't package for windows, for instance.

		//if the windows or mac section exists, we need to check its target
		//to determine what to display in the list of tasks.

		//captive runtime
		if(isWindowsOverrideBundle)
		{
			result.push(this.getTask("package Windows release (captive runtime)" + taskNameSuffix,
				jsonURI, workspaceFolder, command, frameworkSDK, false, PLATFORM_WINDOWS, false));
		}
		else if(isMacOverrideBundle)
		{
			result.push(this.getTask("package macOS release (captive runtime)" + taskNameSuffix,
				jsonURI, workspaceFolder, command, frameworkSDK, false, PLATFORM_MAC, false));
		}
		//shared runtime with platform overrides
		else if(isWindowsOverrideShared)
		{
			result.push(this.getTask("package Windows debug (shared runtime)" + taskNameSuffix,
				jsonURI, workspaceFolder, command, frameworkSDK, true, PLATFORM_WINDOWS, false));
			result.push(this.getTask("package Windows release (shared runtime)" + taskNameSuffix,
				jsonURI, workspaceFolder, command, frameworkSDK, false, PLATFORM_WINDOWS, false));
		}
		else if(isMacOverrideShared)
		{
			result.push(this.getTask("package macOS debug (shared runtime)" + taskNameSuffix,
				jsonURI, workspaceFolder, command, frameworkSDK, false, PLATFORM_MAC, false));
			result.push(this.getTask("package macOS release (shared runtime)" + taskNameSuffix,
				jsonURI, workspaceFolder, command, frameworkSDK, true, PLATFORM_MAC, false));
		}
		//native installers
		else if(isWindowsOverrideNativeInstaller)
		{
			result.push(this.getTask("package Windows release (native installer)" + taskNameSuffix,
				jsonURI, workspaceFolder, command, frameworkSDK, false, PLATFORM_WINDOWS, false));
		}
		else if(isMacOverrideNativeInstaller)
		{
			result.push(this.getTask("package macOS release (native installer)" + taskNameSuffix,
				jsonURI, workspaceFolder, command, frameworkSDK, false, PLATFORM_MAC, false));
		}

		//--- root target in airOptions

		//the root target is used if it hasn't been overridden for the current
		//desktop platform. if it is overridden, it should be skipped to avoid
		//duplicate items in the list.

		if(isRootTargetBundle && !isWindowsOverrideBundle && !isMacOverrideBundle)
		{
			result.push(this.getTask("package desktop release (captive runtime)" + taskNameSuffix,
				jsonURI, workspaceFolder, command, frameworkSDK, false, PLATFORM_AIR, false));
		}
		else if(isRootTargetNativeInstaller && !isWindowsOverrideNativeInstaller && !isMacOverrideNativeInstaller)
		{
			result.push(this.getTask("package desktop release (native installer)" + taskNameSuffix,
				jsonURI, workspaceFolder, command, frameworkSDK, false, PLATFORM_AIR, false));
		}
		else if((isRootTargetShared || isSharedOverride) && !isWindowsOverrideShared && !isMacOverrideShared)
		{
			result.push(this.getTask("package desktop debug (shared runtime)" + taskNameSuffix,
				jsonURI, workspaceFolder, command, frameworkSDK, true, PLATFORM_AIR, false));
			result.push(this.getTask("package desktop release (shared runtime)" + taskNameSuffix,
				jsonURI, workspaceFolder, command, frameworkSDK, false, PLATFORM_AIR, false));
		}
	}

	resolveTask(task: vscode.Task, token?: vscode.CancellationToken): vscode.ProviderResult<vscode.Task>
	{
		return undefined;
	}

	private getTaskNameSuffix(jsonURI: vscode.Uri, workspaceFolder: vscode.WorkspaceFolder): string
	{
		let suffix = " - ";
		let workspaceFolders = vscode.workspace.workspaceFolders;
		if(workspaceFolders && workspaceFolders.length > 1)
		{ 
			suffix += workspaceFolder.name + "/";
		}
		if(jsonURI)
		{
			suffix += jsonURI.toString().substr(workspaceFolder.uri.toString().length + 1);
		}
		else
		{
			suffix += ASCONFIG_JSON;
		}
		return suffix;
	}

	private getTask(description: string, jsonURI: vscode.Uri, workspaceFolder: vscode.WorkspaceFolder,
		command: string[], sdk: string, debug: boolean, airPlatform: string, unpackageANEs: boolean): vscode.Task
	{
		let asconfig: string = this.getASConfigValue(jsonURI, workspaceFolder.uri);
		let definition: ActionScriptTaskDefinition = { type: TASK_TYPE_ACTIONSCRIPT, debug, asconfig };
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
		if(jsonURI)
		{
			options.push("--project", jsonURI.fsPath);
		}
		if(airPlatform)
		{
			options.push("--air", airPlatform);
		}
		if(unpackageANEs)
		{
			options.push("--unpackage-anes=true");
		}
		if(vscode.workspace.getConfiguration("as3mxml").get("asconfigc.verboseOutput"))
		{
			options.push("--verbose=true");
		}
		if(command.length > 1)
		{
			options.unshift(...command.slice(1));
		}
		let source = airPlatform === null ? TASK_SOURCE_ACTIONSCRIPT : TASK_SOURCE_AIR;
		let execution = new vscode.ProcessExecution(command[0], options);
		let task = new vscode.Task(definition, workspaceFolder, description,
			source, execution, MATCHER);
		task.group = vscode.TaskGroup.Build;
		return task;
	}

	private getCleanTask(description: string, jsonURI: vscode.Uri,
		workspaceFolder: vscode.WorkspaceFolder, command: string[], sdk: string): vscode.Task
	{
		let asconfig: string = undefined;
		if(jsonURI)
		{
			let rootJSON = path.resolve(workspaceFolder.uri.fsPath, ASCONFIG_JSON);
			if(rootJSON !== jsonURI.fsPath)
			{
				//the asconfig field should remain empty if it's the root
				//asconfig.json in the workspace.
				//this is different than TypeScript because we didn't originally
				//create tasks for additional asconfig files in the workspace, and
				//we don't want to break old tasks.json files that already existed
				//before this feature was added.
				//ideally, we'd be able to use resolveTask() to populate the
				//asconfig field, but that function never seems to be called.
				asconfig = jsonURI.toString().substr(workspaceFolder.uri.toString().length + 1);
			}
		}
		let definition: ActionScriptTaskDefinition = { type: TASK_TYPE_ACTIONSCRIPT, asconfig, clean: true };
		let options = ["--sdk", sdk, "--clean=true"];
		if(jsonURI)
		{
			options.push("--project", jsonURI.fsPath);
		}
		if(command.length > 1)
		{
			options.unshift(...command.slice(1));
		}
		let execution = new vscode.ProcessExecution(command[0], options);
		let task = new vscode.Task(definition, workspaceFolder, description,
			"ActionScript", execution, MATCHER);
		task.group = vscode.TaskGroup.Build;
		return task;
	}

	private getAnimateTask(description: string, jsonURI: vscode.Uri,
		workspaceFolder: vscode.WorkspaceFolder, command: string[],
		animatePath: string, debug: boolean, publish: boolean): vscode.Task
	{
		let asconfig: string = this.getASConfigValue(jsonURI, workspaceFolder.uri);
		let definition: AnimateTaskDefinition = { type: TASK_TYPE_ANIMATE, debug, publish, asconfig };
		let options = ["--animate", animatePath];
		if(debug)
		{
			options.push("--debug=true");
		}
		else
		{
			options.push("--debug=false");
		}
		if(publish)
		{
			options.push("--publish-animate=true");
		}
		else
		{
			options.push("--publish-animate=false");
		}
		if(jsonURI)
		{
			options.push("--project", jsonURI.fsPath);
		}
		if(command.length > 1)
		{
			options.unshift(...command.slice(1));
		}
		let execution = new vscode.ProcessExecution(command[0], options);
		let task = new vscode.Task(definition, workspaceFolder, description,
			TASK_SOURCE_ANIMATE, execution, MATCHER);
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
			let useBundled = <string> vscode.workspace.getConfiguration("as3mxml").get("asconfigc.useBundled");
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
		let useBundled = <string> vscode.workspace.getConfiguration("as3mxml").get("asconfigc.useBundled");
		if(!useBundled)
		{
			//use an executable on the system path
			return [executableName];
		}
		//use the version bundled with the extension
		return this.getDefaultCommand();
	}

	private getASConfigValue(jsonURI: vscode.Uri, workspaceURI: vscode.Uri): string
	{
		if(!jsonURI)
		{
			return undefined;
		}
		let rootJSON = path.resolve(workspaceURI.fsPath, ASCONFIG_JSON);
		if(rootJSON === jsonURI.fsPath)
		{
			//the asconfig field should remain empty if it's the root
			//asconfig.json in the workspace.
			//this is different than TypeScript because we didn't originally
			//create tasks for additional asconfig files in the workspace, and
			//we don't want to break old tasks.json files that already existed
			//before this feature was added.
			//ideally, we'd be able to use resolveTask() to populate the
			//asconfig field, but that function never seems to be called.
			return undefined;
		}
		return jsonURI.toString().substr(workspaceURI.toString().length + 1);
	}
	
	private readASConfigJSON(jsonURI: vscode.Uri)
	{
		if(!jsonURI)
		{
			return null;
		}
		let jsonPath = jsonURI.fsPath;
		if(!fs.existsSync(jsonPath))
		{
			return null;
		}
		try
		{
			let contents = fs.readFileSync(jsonPath, "utf8");
			return json5.parse(contents);
		}
		catch(error)
		{

		}
		return null;
	}
	
	private isAnimate(asconfigJson: any): boolean
	{
		if(!(FIELD_ANIMATE_OPTIONS in asconfigJson))
		{
			return false;
		}
		let animateOptions = asconfigJson[FIELD_ANIMATE_OPTIONS];
		return FIELD_FILE in animateOptions;
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
	
	private isWindowsOverrideShared(asconfigJson: any): boolean
	{
		if(process.platform !== "win32")
		{
			return false;
		}
		if(!(FIELD_AIR_OPTIONS in asconfigJson))
		{
			return false;
		}
		let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
		if(!(PLATFORM_WINDOWS in airOptions))
		{
			return false;
		}
		let windows = airOptions[PLATFORM_WINDOWS];
		if(!(FIELD_TARGET in windows))
		{
			//if target is omitted, defaults to bundle
			return false;
		}
		let target = windows[FIELD_TARGET];
		return target === TARGET_AIR;
	}
	
	private isMacOverrideShared(asconfigJson: any): boolean
	{
		if(process.platform !== "darwin")
		{
			return false;
		}
		if(!(FIELD_AIR_OPTIONS in asconfigJson))
		{
			return false;
		}
		let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
		if(!(PLATFORM_MAC in airOptions))
		{
			return false;
		}
		let mac = airOptions[PLATFORM_MAC];
		if(!(FIELD_TARGET in mac))
		{
			//if target is omitted, defaults to bundle
			return false;
		}
		let target = mac[FIELD_TARGET];
		return target === TARGET_AIR;
	}
	
	private isWindowsOverrideNativeInstaller(asconfigJson: any): boolean
	{
		if(process.platform !== "win32")
		{
			return false;
		}
		if(!(FIELD_AIR_OPTIONS in asconfigJson))
		{
			return false;
		}
		let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
		if(!(PLATFORM_WINDOWS in airOptions))
		{
			return false;
		}
		let windows = airOptions[PLATFORM_WINDOWS];
		if(!(FIELD_TARGET in windows))
		{
			//if target is omitted, defaults to bundle
			return false;
		}
		let target = windows[FIELD_TARGET];
		return target === TARGET_NATIVE;
	}
	
	private isMacOverrideNativeInstaller(asconfigJson: any): boolean
	{
		if(process.platform !== "darwin")
		{
			return false;
		}
		if(!(FIELD_AIR_OPTIONS in asconfigJson))
		{
			return false;
		}
		let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
		if(!(PLATFORM_MAC in airOptions))
		{
			return false;
		}
		let mac = airOptions[PLATFORM_MAC];
		if(!(FIELD_TARGET in mac))
		{
			//if target is omitted, defaults to bundle
			return false;
		}
		let target = mac[FIELD_TARGET];
		return target === TARGET_NATIVE;
	}
	
	private isSharedOverride(asconfigJson: any): boolean
	{
		if(process.platform !== "win32")
		{
			return false;
		}
		if(!(FIELD_AIR_OPTIONS in asconfigJson))
		{
			return false;
		}
		let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
		return PLATFORM_AIR in airOptions;
	}
	
	private isWindowsOverrideBundle(asconfigJson: any): boolean
	{
		if(process.platform !== "win32")
		{
			return false;
		}
		if(!(FIELD_AIR_OPTIONS in asconfigJson))
		{
			return false;
		}
		let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
		if(!(PLATFORM_WINDOWS in airOptions))
		{
			return false;
		}
		let windows = airOptions[PLATFORM_WINDOWS];
		if(!(FIELD_TARGET in windows))
		{
			//if target is omitted, default to bundle
			return true;
		}
		let target = windows[FIELD_TARGET];
		return target === TARGET_BUNDLE;
	}
	
	private isMacOverrideBundle(asconfigJson: any): boolean
	{
		if(process.platform !== "darwin")
		{
			return false;
		}
		if(!(FIELD_AIR_OPTIONS in asconfigJson))
		{
			return false;
		}
		let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
		if(!(PLATFORM_MAC in airOptions))
		{
			return false;
		}
		let mac = airOptions[PLATFORM_MAC];
		if(!(FIELD_TARGET in mac))
		{
			//if target is omitted, default to bundle
			return true;
		}
		let target = mac[FIELD_TARGET];
		return target === TARGET_BUNDLE;
	}
	
	private isRootTargetShared(asconfigJson: any): boolean
	{
		if(!(FIELD_AIR_OPTIONS in asconfigJson))
		{
			return false;
		}
		let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
		if(!(FIELD_TARGET in airOptions))
		{
			//special case for mobile
			if(this.isAIRMobile(asconfigJson))
			{
				return false;
			}
			//if target is omitted, defaults to air/shared
			return true;
		}
		let target = airOptions[FIELD_TARGET];
		return target === TARGET_AIR;
	}
	
	private isRootTargetBundle(asconfigJson: any): boolean
	{
		if(!(FIELD_AIR_OPTIONS in asconfigJson))
		{
			return false;
		}
		let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
		if(!(FIELD_TARGET in airOptions))
		{
			//if target is omitted, defaults to air/shared
			return false;
		}
		let target = airOptions[FIELD_TARGET];
		return target === TARGET_BUNDLE;
	}
	
	private isRootTargetNativeInstaller(asconfigJson: any): boolean
	{
		if(!(FIELD_AIR_OPTIONS in asconfigJson))
		{
			return false;
		}
		let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
		if(!(FIELD_TARGET in airOptions))
		{
			//if target is omitted, defaults to air/shared
			return false;
		}
		let target = airOptions[FIELD_TARGET];
		return target === TARGET_NATIVE;
	}
}