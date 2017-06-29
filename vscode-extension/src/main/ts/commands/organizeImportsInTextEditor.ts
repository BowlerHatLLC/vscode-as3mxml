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

function organizeImportsInDocumentFromIndex(document: vscode.TextDocument, startIndex: number, edit: vscode.TextEditorEdit): number
{
	let text = document.getText();
	let regExp = /^([ \t]*)import ([\w\.\*]+);?/gm;
	if(startIndex !== -1)
	{
		regExp.lastIndex = startIndex;
	}
	let matches;
	let names: string[] = [];
	let insertIndex = 0;
	let indent = "";
	let startImportsIndex = -1;
	let endImportsIndex = -1;
	let endIndex = -1;
	do
	{
		matches = regExp.exec(text);
		if(matches)
		{
			let matchIndex = matches.index;
			if(startImportsIndex === -1)
			{
				startImportsIndex = matchIndex;
				let nextBlockOpenIndex = text.indexOf("{", startImportsIndex);
				let nextBlockCloseIndex = text.indexOf("}", startImportsIndex);
				endIndex = nextBlockOpenIndex;
				if(endIndex === -1 || (nextBlockCloseIndex !== -1 && nextBlockCloseIndex < endIndex))
				{
					endIndex = nextBlockCloseIndex;
				}
				indent = matches[1];
			}
			if(endIndex !== -1 && matchIndex >= endIndex)
			{
				break;
			}
			endImportsIndex = matchIndex + matches[0].length;
			names[insertIndex] = matches[2];
			insertIndex++;
		}
	}
	while(matches);
	if(names.length === 0)
	{
		//nothing to organize
		return endIndex;
	}
	//put them in alphabetical order
	names = names.sort(function(a: string, b: string): number
	{
		if(a < b)
		{
			return -1;
		}
		if(a > b)
		{
			return 1;
		}
		return 0;
	});
	let result = "";
	let previousFirstPart: string = null;
	for(let i = 0, count = names.length; i < count; i++)
	{
		let name = names[i];
		let firstPart = name.split(".")[0];
		if(previousFirstPart === null)
		{
			previousFirstPart = firstPart;
		}
		else if(firstPart !== previousFirstPart)
		{
			//add an extra line if the top-level package changes
			result += "\n";
			previousFirstPart = firstPart;
		}
		if(i > 0)
		{
			result += "\n";
		}
		result += indent + "import " + names[i] + ";";
	}
	let range = new vscode.Range(document.positionAt(startImportsIndex), document.positionAt(endImportsIndex));
	edit.replace(range, result);
	return endIndex;
}

export default function organizeImportsInTextEditor(editor: vscode.TextEditor, edit: vscode.TextEditorEdit)
{
	if(!vscode.workspace.rootPath)
	{
		vscode.window.showErrorMessage("Cannot organize imports because no workspace is currently open.");
		return;
	}
	let document = editor.document;
	let fileName = document.fileName;
	if(!fileName.endsWith(".as") && !fileName.endsWith(".mxml"))
	{
		//we can't organize imports in this file
		return;
	}
	let nextIndex = 0;
	while(nextIndex !== -1)
	{
		nextIndex = organizeImportsInDocumentFromIndex(document, nextIndex, edit);
	}
}