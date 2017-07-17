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

const ASCONFIG_JSON = "asconfig.json";

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

		let asconfigJson = path.join(workspaceRoot, ASCONFIG_JSON);
		if(!fs.existsSync(asconfigJson))
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
		//start out by looking for asconfigc in the locally installed Node modules
		const platform = process.platform;
		if(platform === "win32")
		{
			let winPath = path.join(nodeModulesBin, "asconfigc.cmd");
			if(fs.existsSync(winPath))
			{
				return winPath;
			}
		}
		else
		{
			let unixPath = path.join(nodeModulesBin, "asconfigc");
			if(fs.existsSync(unixPath))
			{
				return unixPath;
			}
		}
		//use global
		return "asconfigc";
}
}