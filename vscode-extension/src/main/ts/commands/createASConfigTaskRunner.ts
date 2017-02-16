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

export default function(flexHome?: string)
{
	let vscodePath = path.resolve(vscode.workspace.rootPath, ".vscode/");
	let tasksPath = path.resolve(vscodePath, "tasks.json");
	vscode.workspace.openTextDocument(tasksPath).then((document: vscode.TextDocument) =>
	{
		//if it already exists, just open it. do nothing else.
		//even if it doesn't run asconfigc.
		vscode.window.showTextDocument(document);
	},
	() =>
	{
		let tasks = "{\n\t// See https://go.microsoft.com/fwlink/?LinkId=733558\n\t// for the documentation about the tasks.json format\n\t\"version\": \"0.1.0\",\n\t\"command\": \"asconfigc\",\n\t\"isShellCommand\": true,\n\t\"args\": [\n\t\t";
		if(flexHome)
		{
			tasks += "\"--flexHome=" + flexHome + "\"";
		}
		else
		{
			tasks += "//\"--flexHome=path/to/sdk\"";
		}
		tasks += "\n\t],\n\t\"showOutput\": \"always\"\n}";
		if(!fs.existsSync(vscodePath))
		{
			//on Windows, if the directory isn't created first, writing the
			//file will fail
			fs.mkdirSync(vscodePath);
		}
		fs.writeFileSync(tasksPath, tasks,
		{
			encoding: "utf8"
		});
		vscode.workspace.openTextDocument(tasksPath).then((document: vscode.TextDocument) =>
		{
			vscode.window.showTextDocument(document);
		}, () =>
		{
			vscode.window.showErrorMessage("Failed to create tasks.json for asconfigc.");
		});
	});
}