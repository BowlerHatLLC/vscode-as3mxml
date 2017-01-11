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