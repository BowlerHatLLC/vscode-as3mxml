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
import * as vscode from "vscode";

export default function(textEditor: vscode.TextEditor, edit: vscode.TextEditorEdit, prefix: string, uri: string, startIndex: number, endIndex: number)
{
	if(!prefix || !uri)
	{
		return;
	}
	let document = textEditor.document;
	let position = document.positionAt(endIndex);
	edit.insert(position, " xmlns:" + prefix + "=\"" + uri + "\"");
}