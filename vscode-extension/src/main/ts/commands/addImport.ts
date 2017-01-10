import * as vscode from "vscode";

export default function(textEditor: vscode.TextEditor, edit: vscode.TextEditorEdit, qualifiedName: string, startIndex: number, endIndex: number)
{
	if(!qualifiedName)
	{
		return;
	}
	let document = textEditor.document;
	let text = document.getText();
	let regExp = /^([ \t]*)import ([\w\.]+)/gm;
	let matches;
	let currentMatches;
	if(startIndex !== -1)
	{
		regExp.lastIndex = startIndex;
	}
	do
	{
		currentMatches = regExp.exec(text);
		if(currentMatches)
		{
			if(endIndex !== -1 && currentMatches.index >= endIndex)
			{
				break;
			}
			if(currentMatches[2] === qualifiedName)
			{
				//this class is already imported!
				return;
			}
			matches = currentMatches;
		}
	}
	while(currentMatches);
	let indent = "";
	let lineBreaks = "\n";
	let position: vscode.Position;
	if(matches)
	{
		//we found existing imports
		position = document.positionAt(matches.index);
		indent = matches[1];
		position = new vscode.Position(position.line + 1, 0);
	}
	else //no existing imports
	{
		if(startIndex !== -1)
		{
			position = document.positionAt(startIndex);
			if(position.character > 0)
			{
				//go to the next line, if we're not at the start
				position = position.with(position.line + 1, 0);
			}
			//try to use the same indent as whatever follows
			let regExp = /^([ \t]*)\w/gm;
			regExp.lastIndex = startIndex;
			matches = regExp.exec(text);
			if(matches)
			{
				indent = matches[1];
			}
			else
			{
				indent = "";
			}
		}
		else
		{
			regExp = /^package( [\w\.]+)*\s*{[\r\n]+([ \t]*)/g;
			matches = regExp.exec(text);
			if(!matches)
			{
				return;
			}
			position = document.positionAt(regExp.lastIndex);
			if(position.character > 0)
			{
				//go to the beginning of the line, if we're not there
				position = position.with(position.line, 0);
			}
			indent = matches[2];
		}
		lineBreaks += "\n"; //add an extra line break
	}
	let textToInsert = indent + "import " + qualifiedName + ";" + lineBreaks;
	edit.insert(position, textToInsert);
}