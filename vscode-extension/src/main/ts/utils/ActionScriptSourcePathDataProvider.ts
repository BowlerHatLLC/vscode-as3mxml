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

export class ActionScriptSourcePath extends vscode.TreeItem
{
	constructor(label: string, filePath?: string)
	{
		let contextValue: string = null;
		let command: vscode.Command;
		let collapsibleState = vscode.TreeItemCollapsibleState.None;
		if(filePath)
		{
			if(fs.statSync(filePath).isDirectory())
			{
				collapsibleState = vscode.TreeItemCollapsibleState.Collapsed;
				contextValue = "folder";
			}
			else
			{
				let extname = path.extname(filePath);
				if(extname === ".as")
				{
					contextValue = "nextgenas";
				}
				else if(extname === ".mxml")
				{
					contextValue = "mxml";
				}
				command = 
				{
					title: "Open File",
					command: "vscode.open",
					arguments:
					[
						vscode.Uri.file(filePath)
					]
				}
			}
		}
		super(label, collapsibleState);
		this.path = filePath;
		this.command = command;
		this.contextValue = contextValue;
	}

	path: string;
}

export default class ActionScriptSourcePathDataProvider implements vscode.TreeDataProvider<ActionScriptSourcePath>
{
	private _workspaceRoot: string;
	private _asconfigPath: string;
	private _onDidChangeTreeData: vscode.EventEmitter<ActionScriptSourcePath | null> = new vscode.EventEmitter<ActionScriptSourcePath | null>();
	private _rootPaths: ActionScriptSourcePath[];

	constructor(workspaceRoot: string)
	{
		this._workspaceRoot = workspaceRoot;
		this._asconfigPath = path.join(this._workspaceRoot, "asconfig.json");
		let watcher = vscode.workspace.createFileSystemWatcher("**/asconfig.json");
		watcher.onDidChange(this.asconfigFileSystemWatcher_onEvent, this);
		watcher.onDidCreate(this.asconfigFileSystemWatcher_onEvent, this);
		watcher.onDidDelete(this.asconfigFileSystemWatcher_onEvent, this);
		this.refreshSourcePaths();
	}

	readonly onDidChangeTreeData: vscode.Event<ActionScriptSourcePath | null> = this._onDidChangeTreeData.event;

	getTreeItem(element: ActionScriptSourcePath): vscode.TreeItem
	{
		return element;
	}

	getChildren(element?: ActionScriptSourcePath): Thenable<ActionScriptSourcePath[]>
	{
		if(!this._workspaceRoot)
		{
			return Promise.resolve([new ActionScriptSourcePath("Open a workspace to see source paths")]);
		}
		if(!fs.existsSync(this._asconfigPath))
		{
			return Promise.resolve([new ActionScriptSourcePath("No source paths in asconfig.json")]);
		}
		return new Promise(resolve =>
		{
			if(element)
			{
				let elementPath = element.path;
				if(!fs.statSync(elementPath).isDirectory())
				{
					return resolve([]);
				}
				let files = fs.readdirSync(elementPath);
				let sourcePaths = files.map((filePath) =>
				{
					filePath = path.join(elementPath, filePath);
					return this.pathToSourcePath(filePath);
				})
				return resolve(sourcePaths);
			}
			else
			{
				if(this._rootPaths.length === 0)
				{
					return resolve([new ActionScriptSourcePath("No source paths")]);
				}
				return resolve(this._rootPaths);
			}
		});
	}

	private pathToSourcePath(pathToResolve: string): ActionScriptSourcePath
	{
		let rootPath = path.resolve(this._workspaceRoot, pathToResolve);
		let name = path.basename(rootPath);
		return new ActionScriptSourcePath(name, rootPath);
	}

	private refreshSourcePaths()
	{
		let oldPaths = this._rootPaths;
		this._rootPaths = [];
		if(!fs.existsSync(this._asconfigPath))
		{
			this._onDidChangeTreeData.fire();
			return;
		}
		try
		{
			let contents = fs.readFileSync(this._asconfigPath, "utf8");
			let result = json5.parse(contents);
			if("compilerOptions" in result)
			{
				let compilerOptions = result.compilerOptions;
				if("source-path" in compilerOptions)
				{
					let sourcePath = compilerOptions["source-path"];
					if(Array.isArray(sourcePath))
					{
						sourcePath.forEach((sourcePath: string) =>
						{
							let rootPath = this.pathToSourcePath(sourcePath);
							this._rootPaths.push(rootPath);
						});
					}
				}
			}
		}
		catch(error)
		{
			//we'll ignore this one
		}
		this._onDidChangeTreeData.fire();
	}

	private asconfigFileSystemWatcher_onEvent(uri: vscode.Uri)
	{
		this.refreshSourcePaths();
	}
}