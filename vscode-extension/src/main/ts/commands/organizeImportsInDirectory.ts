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
import * as vscode from "vscode";
import * as path from "path";
import * as fs from "fs";
import organizeImportsInUri from "./organizeImportsInUri";

export default async function organizeImportsInDirectory(uri: vscode.Uri)
{
	let directoryPaths = [uri.fsPath];
	for(let i = 0, dirCount = 1; i < dirCount; i++)
	{
		let directoryPath = directoryPaths[i];
		let files = fs.readdirSync(directoryPath);
		for(let j = 0, fileCount = files.length; j < fileCount; j++)
		{
			let fileBasePath = files[j];
			let fullPath = path.resolve(directoryPath, fileBasePath);
			if(fs.statSync(fullPath).isDirectory())
			{
				//add this directory to the list to search
				directoryPaths.push(fullPath);
				dirCount++;
				continue;
			}
			var extname:String = path.extname(fileBasePath);
			if(extname !== ".as" && extname !== ".mxml")
			{
				continue;
			}
			let uri = vscode.Uri.file(fullPath);
			//if we open too many editors at once, some will close before
			//finishing the task, so we wait
			await organizeImportsInUri(uri, true, true);
		}
	}
}