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

interface ActionScriptTaskDefinition extends vscode.TaskDefinition
{
	debug: boolean;
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
		let asconfigJson = path.join(workspaceRoot, ASCONFIG_JSON);
		if(fs.existsSync(asconfigJson))
		{
			//if asconfig.json exists in the root, always provide the tasks
			provideTask = true;
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

		let debugIdentifier: ActionScriptTaskDefinition = { type: "actionscript", debug: true };
		let debugOptions = ["--debug=true", "--flexHome", frameworkSDK];
		let debugBuildTask = new vscode.Task(debugIdentifier, "debug build using asconfig.json", "ActionScript",
			new vscode.ProcessExecution(command, debugOptions), ["$nextgenas_nomatch"]);
		debugBuildTask.group = vscode.TaskGroup.Build;

		let releaseIdentifier: ActionScriptTaskDefinition = { type: "actionscript", debug: false };
		let releaseOptions = ["--debug=false", "--flexHome", frameworkSDK];
		let releaseBuildTask = new vscode.Task(releaseIdentifier, "release build using asconfig.json", "ActionScript",
			new vscode.ProcessExecution(command, releaseOptions), ["$nextgenas_nomatch"]);
		releaseBuildTask.group = vscode.TaskGroup.Build;
		return Promise.resolve([debugBuildTask, releaseBuildTask]);
	}

	resolveTask(task: vscode.Task): vscode.Task | undefined
	{
		return undefined;
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
}