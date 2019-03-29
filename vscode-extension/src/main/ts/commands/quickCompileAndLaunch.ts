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
import * as vscode from "vscode";
import * as fs from "fs";
import * as path from "path";
import * as json5 from "json5";

const QUICK_COMPILE_MESSAGE = "Building ActionScript & MXML project...";
const CANNOT_LAUNCH_QUICK_COMPILE_FAILED_ERROR = "Quick compile failed with errors. Launch cancelled.";

export default function quickCompileAndLaunch(debug: boolean)
{
	if(vscode.workspace.workspaceFolders)
	{
		let workspaceFolders = vscode.workspace.workspaceFolders.filter((folder) =>
		{
			let asconfigPath = path.resolve(folder.uri.fsPath, "asconfig.json");
			if(!fs.existsSync(asconfigPath) || fs.statSync(asconfigPath).isDirectory())
			{
				return false;
			}
			try
			{
				let contents = fs.readFileSync(asconfigPath, "utf8");
				let result = json5.parse(contents);
				if ("type" in result && result.type !== "app")
				{
					return false;
				}
			}
			catch(error)
			{
				return false;
			}
			return true;
		});
		if(workspaceFolders.length === 1)
		{
			quickCompileAndDebugWorkspaceFolder(workspaceFolders[0], debug);
		}
		else
		{
			let items = workspaceFolders.map((folder): vscode.QuickPickItem =>
			{
				return { label: folder.name, description: folder.uri.fsPath, uri: folder.uri } as any;
			})
			vscode.window.showQuickPick(items).then(result => quickCompileAndDebugWorkspaceFolder(result, debug));
		}
	}
}

async function quickCompileAndDebugWorkspaceFolder(workspaceFolder, debug: boolean)
{
	if(!workspaceFolder)
	{
		//it's possible for no folder to be chosen when using
		//showWorkspaceFolderPick()
		return;
	}
	//before running a task, VSCode saves all files. we should do the same
	//before running a quick compile, since it's like a task.
	await vscode.commands.executeCommand("workbench.action.files.saveAll");
	let workspaceFolderUri = workspaceFolder.uri.toString();
	vscode.window.withProgress({location: vscode.ProgressLocation.Window}, (progress) =>
	{
		progress.report({message: QUICK_COMPILE_MESSAGE});
		return new Promise((resolve, reject) =>
		{
			return vscode.commands.executeCommand("workbench.action.debug.stop").then(() =>
			{
				return vscode.commands.executeCommand("as3mxml.quickCompile", workspaceFolderUri, debug).then((result) =>
				{
					resolve();
	
					if(result === true)
					{
						if(debug)
						{
							vscode.commands.executeCommand("workbench.action.debug.start");
						}
						else
						{
							vscode.commands.executeCommand("workbench.action.debug.run");
						}
					}
					else
					{
						vscode.window.showErrorMessage(CANNOT_LAUNCH_QUICK_COMPILE_FAILED_ERROR);
					}
				}, 
				() =>
				{
					resolve();
	
					//if the build failed, notify the user that we're not starting
					//a debug session
					vscode.window.showErrorMessage(CANNOT_LAUNCH_QUICK_COMPILE_FAILED_ERROR);
				});
			});
		});
	});
}