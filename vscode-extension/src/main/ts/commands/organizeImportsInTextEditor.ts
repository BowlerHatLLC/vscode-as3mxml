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

function getEOL(eol: vscode.EndOfLine): string
{
	if(eol === vscode.EndOfLine.LF)
	{
		return "\n";
	}
	return "\r\n";
}

export default function organizeImportsInTextEditor(editor: vscode.TextEditor, edit: vscode.TextEditorEdit)
{
	let document = editor.document;
	let fileName = document.fileName;
	if(!fileName.endsWith(".as") && !fileName.endsWith(".mxml"))
	{
		//we can't organize imports in this file
		return;
	}
	let text = document.getText();
	let regExp = /^([ \t]*)import ([\w\.]+);?/gm;
	let matches;
	let names: string[] = [];
	let insertIndex = 0;
	let indent = "";
	let startIndex = -1;
	let endIndex = -1;
	do
	{
		matches = regExp.exec(text);
		if(matches)
		{
			if(startIndex === -1)
			{
				startIndex = matches.index;
				indent = matches[1];
			}
			endIndex = matches.index + matches[0].length;
			names[insertIndex] = matches[2];
			insertIndex++;
		}
	}
	while(matches);
	if(names.length === 0)
	{
		//nothing to organize
		return;
	}
	//put them in alphabetical order
	names = names.sort(function(a: string, b: string): number
	{
		a = a.toLowerCase();
		b = b.toLowerCase();
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
	let eol = getEOL(document.eol);
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
			result += eol;
			previousFirstPart = firstPart;
		}
		if(i > 0)
		{
			result += eol;
		}
		result += indent + "import " + names[i] + ";";
	}
	let range = new vscode.Range(document.positionAt(startIndex), document.positionAt(endIndex));
	edit.replace(range, result);
}