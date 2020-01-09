/*
Copyright 2016-2020 Bowler Hat LLC

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

export default function findQuickCompileWorkspaceFolders():vscode.WorkspaceFolder[]
{
	if(vscode.workspace.workspaceFolders === undefined)
	{
		return [];
	}
	return vscode.workspace.workspaceFolders.filter((folder) =>
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
}