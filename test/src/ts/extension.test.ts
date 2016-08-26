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
import * as assert from "assert";
import * as path from "path";
import * as vscode from "vscode";

suite("NextGenAS extension", () =>
{
	test("test vscode.extensions.getExtension", (done) =>
	{
		let extensionName = "bowlerhatllc.vscode-nextgenas";
		let extension = vscode.extensions.getExtension(extensionName);
		assert.ok(extension, `Extension "${extensionName}" not found!`);
		//wait a bit for the the extension to fully activate
		setTimeout(() =>
		{
			assert.ok(extension.isActive, `Extension "${extensionName}" not active!`);
			done();
		}, 1000);
	});
	test("test vscode.executeDocumentSymbolProvider", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		return vscode.workspace.openTextDocument(uri)
		.then((document: vscode.TextDocument) =>
			{
				return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.notEqual(symbols.length, 0, "No symbols found in text document: " + uri);
					}, (err) =>
					{
						assert.fail("Failed to execute document symbol provider: " + uri);
					});
				
			}, (err) =>
			{
				assert.fail("Failed to open text document: " + uri);
			}).then(() => done(), done);
	});
});