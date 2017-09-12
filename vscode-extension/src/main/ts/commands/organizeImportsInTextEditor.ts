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
	vscode.commands.executeCommand("nextgenas.organizeImportsInUri", document.uri);
}