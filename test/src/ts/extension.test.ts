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

function openAndEditDocument(uri: vscode.Uri, callback: (editor: vscode.TextEditor) => PromiseLike<void>): PromiseLike<void>
{
	return vscode.workspace.openTextDocument(uri)
		.then((document: vscode.TextDocument) =>
			{
				return vscode.window.showTextDocument(document)
					.then(callback, (err) =>
					{
						assert.fail("Failed to show text document: " + uri);
					});
			}, (err) =>
			{
				assert.fail("Failed to open text document: " + uri);
			});
}

function createRange(startLine: number, startCharacter:number,
	endLine?: number, endCharacter? : number): vscode.Range
{
	if(endLine === undefined)
	{
		endLine = startLine;
	}
	if(endCharacter === undefined)
	{
		endCharacter = startCharacter;
	}
	return new vscode.Range(
		new vscode.Position(startLine, startCharacter),
		new vscode.Position(endLine, endCharacter))
}

function findSymbol(symbols: vscode.SymbolInformation[], symbolToFind: vscode.SymbolInformation): boolean
{
	return symbols.some((symbol: vscode.SymbolInformation) =>
	{
		if(symbol.name !== symbolToFind.name)
		{
			return false;
		}
		if(symbol.kind !== symbolToFind.kind)
		{
			return false;
		}
		if(symbol.location.uri.path !== symbolToFind.location.uri.path)
		{
			return false;
		}
		if(symbol.location.range.start.line !== symbolToFind.location.range.start.line)
		{
			return false;
		}
		if(symbol.location.range.start.character !== symbolToFind.location.range.start.character)
		{
			return false;
		}
		return true;
	});
}

suite("NextGenAS extension", () =>
{
	test("vscode.extensions.getExtension() and isActive", (done) =>
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
});

suite("document symbol provider", () =>
{
	test("vscode.executeDocumentSymbolProvider not empty", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.notStrictEqual(symbols.length, 0,
							"vscode.executeDocumentSymbolProvider failed to provide symbols in text document: " + uri);
					}, (err) =>
					{
						assert.fail("Failed to execute document symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDocumentSymbolProvider includes class", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let classQualifiedName = "Main";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							classQualifiedName,
							vscode.SymbolKind.Class,
							createRange(2, 1),
							uri)),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for class: " + classQualifiedName);
					}, (err) =>
					{
						assert.fail("Failed to execute document symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDocumentSymbolProvider includes constructor", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let classQualifiedName = "Main";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							classQualifiedName,
							vscode.SymbolKind.Constructor,
							createRange(11, 2),
							uri)),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for constructor: " + classQualifiedName);
					}, (err) =>
					{
						assert.fail("Failed to execute document symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDocumentSymbolProvider includes member variable", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let memberVarName = "memberVar";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							memberVarName,
							vscode.SymbolKind.Variable,
							createRange(16, 2),
							uri)),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for member variable: " + memberVarName);
					}, (err) =>
					{
						assert.fail("Failed to execute document symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDocumentSymbolProvider includes member function", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let memberFunctionName = "memberFunction";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							memberFunctionName,
							vscode.SymbolKind.Function,
							createRange(18, 2),
							uri)),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for member function: " + memberFunctionName);
					}, (err) =>
					{
						assert.fail("Failed to execute document symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDocumentSymbolProvider includes static variable", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let staticVarName = "staticVar";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							staticVarName,
							vscode.SymbolKind.Variable,
							createRange(4, 2),
							uri)),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for static variable: " + staticVarName);
					}, (err) =>
					{
						assert.fail("Failed to execute document symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDocumentSymbolProvider includes static constant", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let staticConstName = "STATIC_CONST";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							staticConstName,
							vscode.SymbolKind.Constant,
							createRange(5, 2),
							uri)),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for static constant: " + staticConstName);
					}, (err) =>
					{
						assert.fail("Failed to execute document symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDocumentSymbolProvider includes static function", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let staticFunctionName = "staticFunction";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							staticFunctionName,
							vscode.SymbolKind.Function,
							createRange(7, 2),
							uri)),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for static function: " + staticFunctionName);
					}, (err) =>
					{
						assert.fail("Failed to execute document symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDocumentSymbolProvider includes internal class", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let internalClassQualifiedName = "MainInternalClass";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							internalClassQualifiedName,
							vscode.SymbolKind.Class,
							createRange(24, 0),
							uri)),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for internal class: " + internalClassQualifiedName);
					}, (err) =>
					{
						assert.fail("Failed to execute document symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDocumentSymbolProvider includes member variable in internal class", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let memberVarName = "internalClassMemberVar";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							memberVarName,
							vscode.SymbolKind.Variable,
							createRange(26, 1),
							uri)),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for member variable in internal class: " + memberVarName);
					}, (err) =>
					{
						assert.fail("Failed to execute document symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
});

suite("workspace symbol provider", () =>
{
	test("vscode.executeWorkspaceSymbolProvider includes class", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		let query = "Main";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Class,
							createRange(2, 1),
							uri)),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for class: " + query);
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeWorkspaceSymbolProvider includes constructor", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		let query = "Main";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Constructor,
							createRange(11, 2),
							uri)),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for constructor: " + query);
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeWorkspaceSymbolProvider includes member variable", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		let query = "memberVar";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Variable,
							createRange(16, 2),
							uri)),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for member variable: " + query);
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeWorkspaceSymbolProvider includes member function", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		let query = "memberFunction";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Function,
							createRange(18, 2),
							uri)),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for member function: " + query);
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeWorkspaceSymbolProvider includes static variable", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		let query = "staticVar";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Variable,
							createRange(4, 2),
							uri)),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for static variable: " + query);
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeWorkspaceSymbolProvider includes static constant", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		let query = "STATIC_CONST";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Constant,
							createRange(5, 2),
							uri)),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for static constant: " + query);
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeWorkspaceSymbolProvider includes static function", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		let query = "staticFunction";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Function,
							createRange(7, 2),
							uri)),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for static function: " + query);
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeWorkspaceSymbolProvider includes internal class", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		let query = "MainInternalClass";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Class,
							createRange(24, 0),
							uri)),
							"vscode.executeWorkspaceSymbolProvider did not provide internal class");
							
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeWorkspaceSymbolProvider includes member variable in internal class", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		let query = "internalClassMemberVar";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Variable,
							createRange(26, 1),
							uri)),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for member variable in internal class: " + query);
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeWorkspaceSymbolProvider includes symbols in unreferenced files", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		let query = "Unreferenced";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let qualifiedClassName = "com.example.UnreferencedClass";
			let classUri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "UnreferencedClass.as"));
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.notStrictEqual(symbols.length, 0,
							"vscode.executeWorkspaceSymbolProvider failed to provide unreferenced symbols for query: " + query);
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeWorkspaceSymbolProvider includes class in unreferenced file", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		let query = "Unreferenced";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let qualifiedClassName = "com.example.UnreferencedClass";
			let classUri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "UnreferencedClass.as"));
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(qualifiedClassName,
							vscode.SymbolKind.Class,
							createRange(2, 1),
							classUri)),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for class in unreferenced file with query: " + query);
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeWorkspaceSymbolProvider includes constructor in unreferenced file", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		let query = "Unreferenced";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let qualifiedClassName = "com.example.UnreferencedClass";
			let classUri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "UnreferencedClass.as"));
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(qualifiedClassName,
							vscode.SymbolKind.Constructor,
							createRange(4, 2),
							classUri)),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for constructor in unreferenced file with query: " + query);
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeWorkspaceSymbolProvider includes interface in unreferenced file", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Main.as"));
		let query = "Unreferenced";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let qualifiedInterfaceName = "com.example.core.UnreferencedInterface";
			let interfaceUri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "core", "UnreferencedInterface.as"));
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(qualifiedInterfaceName,
							vscode.SymbolKind.Interface,
							createRange(2, 1),
							interfaceUri)),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for interface in unreferenced file with query: " + query);
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
});

suite("signature help provider", () =>
{
	test("vscode.executeSignatureHelpProvider provides help for local function", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "SignatureHelp.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeSignatureHelpProvider",
				uri, new vscode.Position(19, 17), "(")
				.then((signatureHelp: vscode.SignatureHelp) =>
					{
						assert.strictEqual(signatureHelp.signatures.length, 1,
							"Signature help not provided for local function");
						assert.strictEqual(signatureHelp.activeSignature, 0,
							"Active signature incorrect for local function");
						assert.strictEqual(signatureHelp.activeParameter, 0,
							"Active parameter incorrect for local function");
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeSignatureHelpProvider provides help for member function", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "SignatureHelp.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeSignatureHelpProvider",
				uri, new vscode.Position(20, 18), "(")
				.then((signatureHelp: vscode.SignatureHelp) =>
					{
						assert.strictEqual(signatureHelp.signatures.length, 1,
							"Signature help not provided for member function");
						assert.strictEqual(signatureHelp.activeSignature, 0,
							"Active signature incorrect for member function");
						assert.strictEqual(signatureHelp.activeParameter, 0,
							"Active parameter incorrect for member function");
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeSignatureHelpProvider provides help for member function with member access operator on this", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "SignatureHelp.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeSignatureHelpProvider",
				uri, new vscode.Position(21, 23), "(")
				.then((signatureHelp: vscode.SignatureHelp) =>
					{
						assert.strictEqual(signatureHelp.signatures.length, 1,
							"Signature help not provided for member function with member access operator on this");
						assert.strictEqual(signatureHelp.activeSignature, 0,
							"Active signature incorrect for member function with member access operator on this");
						assert.strictEqual(signatureHelp.activeParameter, 0,
							"Active parameter incorrect for member function with member access operator on this");
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeSignatureHelpProvider provides help for static function", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "SignatureHelp.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeSignatureHelpProvider",
				uri, new vscode.Position(22, 18), "(")
				.then((signatureHelp: vscode.SignatureHelp) =>
					{
						assert.strictEqual(signatureHelp.signatures.length, 1,
							"Signature help not provided for static function");
						assert.strictEqual(signatureHelp.activeSignature, 0,
							"Active signature incorrect for static function");
						assert.strictEqual(signatureHelp.activeParameter, 0,
							"Active parameter incorrect for static function");
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeSignatureHelpProvider provides help for static function with member access operator on class", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "SignatureHelp.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeSignatureHelpProvider",
				uri, new vscode.Position(23, 32), "(")
				.then((signatureHelp: vscode.SignatureHelp) =>
					{
						assert.strictEqual(signatureHelp.signatures.length, 1,
							"Signature help not provided for static function with member access operator on class");
						assert.strictEqual(signatureHelp.activeSignature, 0,
							"Active signature incorrect for static function with member access operator on class");
						assert.strictEqual(signatureHelp.activeParameter, 0,
							"Active parameter incorrect for static function with member access operator on class");
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeSignatureHelpProvider provides help for package function", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "SignatureHelp.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeSignatureHelpProvider",
				uri, new vscode.Position(24, 19), "(")
				.then((signatureHelp: vscode.SignatureHelp) =>
					{
						assert.strictEqual(signatureHelp.signatures.length, 1,
							"Signature help not provided for package function");
						assert.strictEqual(signatureHelp.activeSignature, 0,
							"Active signature incorrect for package function");
						assert.strictEqual(signatureHelp.activeParameter, 0,
							"Active parameter incorrect for package function");
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeSignatureHelpProvider provides help for internal function", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "SignatureHelp.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeSignatureHelpProvider",
				uri, new vscode.Position(25, 31), "(")
				.then((signatureHelp: vscode.SignatureHelp) =>
					{
						assert.strictEqual(signatureHelp.signatures.length, 1,
							"Signature help not provided for internal function");
						assert.strictEqual(signatureHelp.activeSignature, 0,
							"Active signature incorrect for internal function");
						assert.strictEqual(signatureHelp.activeParameter, 0,
							"Active parameter incorrect for internal function");
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeSignatureHelpProvider provides help for internal function", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "SignatureHelp.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeSignatureHelpProvider",
				uri, new vscode.Position(26, 20), "(")
				.then((signatureHelp: vscode.SignatureHelp) =>
					{
						assert.strictEqual(signatureHelp.signatures.length, 1,
							"Signature help not provided for internal function");
						assert.strictEqual(signatureHelp.activeSignature, 0,
							"Active signature incorrect for internal function");
						assert.strictEqual(signatureHelp.activeParameter, 0,
							"Active parameter incorrect for internal function");
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeSignatureHelpProvider provides help for super constructor", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "SignatureHelp.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeSignatureHelpProvider",
				uri, new vscode.Position(27, 9), "(")
				.then((signatureHelp: vscode.SignatureHelp) =>
					{
						assert.strictEqual(signatureHelp.signatures.length, 1,
							"Signature help not provided for super constructor");
						assert.strictEqual(signatureHelp.activeSignature, 0,
							"Active signature incorrect for super constructor");
						assert.strictEqual(signatureHelp.activeParameter, 0,
							"Active parameter incorrect for super constructor");
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeSignatureHelpProvider provides help for super member method", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "SignatureHelp.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeSignatureHelpProvider",
				uri, new vscode.Position(28, 27), "(")
				.then((signatureHelp: vscode.SignatureHelp) =>
					{
						assert.strictEqual(signatureHelp.signatures.length, 1,
							"Signature help not provided for super member method");
						assert.strictEqual(signatureHelp.activeSignature, 0,
							"Active signature incorrect for super member method");
						assert.strictEqual(signatureHelp.activeParameter, 0,
							"Active parameter incorrect for super member method");
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeSignatureHelpProvider must not provide help for private super member method", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "SignatureHelp.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeSignatureHelpProvider",
				uri, new vscode.Position(29, 34), "(")
				.then((signatureHelp: vscode.SignatureHelp) =>
					{
						assert.strictEqual(signatureHelp.signatures.length, 0,
							"Signature help incorrectly provided for private super member method");
					}, (err) =>
					{
						assert.fail("Failed to execute workspace symbol provider: " + uri);
					});
		}).then(() => done(), done);
	});
});

suite("definition provider", () =>
{
	test("vscode.executeDefinitionProvider finds definition of local variable", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(90, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of local variable definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for local variable definition");
						assert.strictEqual(location.range.start.line, 42, "vscode.executeDefinitionProvider provided incorrect line for local variable definition");
						assert.strictEqual(location.range.start.character, 7, "vscode.executeDefinitionProvider provided incorrect character for local variable definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of local function", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(92, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of local function definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for local function definition");
						assert.strictEqual(location.range.start.line, 43, "vscode.executeDefinitionProvider provided incorrect line for local function definition");
						assert.strictEqual(location.range.start.character, 12, "vscode.executeDefinitionProvider provided incorrect character for local function definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of member variable", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(54, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of member variable definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for member variable definition");
						assert.strictEqual(location.range.start.line, 14, "vscode.executeDefinitionProvider provided incorrect line for member variable definition");
						assert.strictEqual(location.range.start.character, 14, "vscode.executeDefinitionProvider provided incorrect character for member variable definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of member variable with member access operator on this", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(55, 10);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of member variable definition with member access operator on this: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for member variable definition with member access operator on this");
						assert.strictEqual(location.range.start.line, 14, "vscode.executeDefinitionProvider provided incorrect line for member variable definition with member access operator on this");
						assert.strictEqual(location.range.start.character, 14, "vscode.executeDefinitionProvider provided incorrect character for member variable definition with member access operator on this");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of member function", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(45, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of member function definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for member function definition");
						assert.strictEqual(location.range.start.line, 16, "vscode.executeDefinitionProvider provided incorrect line for member function definition");
						assert.strictEqual(location.range.start.character, 19, "vscode.executeDefinitionProvider provided incorrect character for member function definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of member function with member access operator on this", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(46, 10);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of member function definition with member access operator on this: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for member function definition with member access operator on this");
						assert.strictEqual(location.range.start.line, 16, "vscode.executeDefinitionProvider provided incorrect line for member function definition with member access operator on this");
						assert.strictEqual(location.range.start.character, 19, "vscode.executeDefinitionProvider provided incorrect character for member function definition with member access operator on this");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of member property", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(57, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of member property definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for member property definition");
						assert.strictEqual(location.range.start.line, 20, "vscode.executeDefinitionProvider provided incorrect line for member property definition");
						assert.strictEqual(location.range.start.character, 22, "vscode.executeDefinitionProvider provided incorrect character for member property definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of member property with member access operator on this", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(58, 10);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of member property definition with member access operator on this: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for member property definition with member access operator on this");
						assert.strictEqual(location.range.start.line, 20, "vscode.executeDefinitionProvider provided incorrect line for member property definition with member access operator on this");
						assert.strictEqual(location.range.start.character, 22, "vscode.executeDefinitionProvider provided incorrect character for member property definition with member access operator on this");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of static variable", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(51, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of static variable definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for static variable definition");
						assert.strictEqual(location.range.start.line, 8, "vscode.executeDefinitionProvider provided incorrect line for static variable definition");
						assert.strictEqual(location.range.start.character, 20, "vscode.executeDefinitionProvider provided incorrect character for static variable definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of static variable with member access operator on class", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(52, 17);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of static variable definition with member access operator on class: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for static variable definition with member access operator on class");
						assert.strictEqual(location.range.start.line, 8, "vscode.executeDefinitionProvider provided incorrect line for static variable definition with member access operator on class");
						assert.strictEqual(location.range.start.character, 20, "vscode.executeDefinitionProvider provided incorrect character for static variable definition with member access operator on class");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of static function", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(48, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of static function definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for static function definition");
						assert.strictEqual(location.range.start.line, 10, "vscode.executeDefinitionProvider provided incorrect line for static function definition");
						assert.strictEqual(location.range.start.character, 26, "vscode.executeDefinitionProvider provided incorrect character for static function definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of static function with member access operator on class", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(49, 17);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of static function definition with member access operator on class: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for static function definition with member access operator on class");
						assert.strictEqual(location.range.start.line, 10, "vscode.executeDefinitionProvider provided incorrect line for static function definition with member access operator on class");
						assert.strictEqual(location.range.start.character, 26, "vscode.executeDefinitionProvider provided incorrect character for static function definition with member access operator on class");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of static property", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(60, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of static property definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for static property definition");
						assert.strictEqual(location.range.start.line, 29, "vscode.executeDefinitionProvider provided incorrect line for static property definition");
						assert.strictEqual(location.range.start.character, 29, "vscode.executeDefinitionProvider provided incorrect character for static property definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of static property with member access operator on class", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(61, 17);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of static property definition with member access operator on class: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for static property definition with member access operator on class");
						assert.strictEqual(location.range.start.line, 29, "vscode.executeDefinitionProvider provided incorrect line for static property definition with member access operator on class");
						assert.strictEqual(location.range.start.character, 29, "vscode.executeDefinitionProvider provided incorrect character for static property definition with member access operator on class");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of package function", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "packageFunction.as"));
		let position = new vscode.Position(84, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of package function definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, definitionURI.path, "vscode.executeDefinitionProvider provided incorrect uri for package function definition");
						assert.strictEqual(location.range.start.line, 2, "vscode.executeDefinitionProvider provided incorrect line for package function definition");
						assert.strictEqual(location.range.start.character, 17, "vscode.executeDefinitionProvider provided incorrect character for package function definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of fully-qualified package function", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "packageFunction.as"));
		let position = new vscode.Position(85, 17);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of fully-qualified package function definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, definitionURI.path, "vscode.executeDefinitionProvider provided incorrect uri for fully-qualified package function definition");
						assert.strictEqual(location.range.start.line, 2, "vscode.executeDefinitionProvider provided incorrect line for fully-qualified package function definition");
						assert.strictEqual(location.range.start.character, 17, "vscode.executeDefinitionProvider provided incorrect character for fully-qualified package function definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of package variable", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "packageVar.as"));
		let position = new vscode.Position(87, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of package variable definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, definitionURI.path, "vscode.executeDefinitionProvider provided incorrect uri for package variable definition");
						assert.strictEqual(location.range.start.line, 2, "vscode.executeDefinitionProvider provided incorrect line for package variable definition");
						assert.strictEqual(location.range.start.character, 12, "vscode.executeDefinitionProvider provided incorrect character for package variable definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of fully-qualified package variable", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "packageVar.as"));
		let position = new vscode.Position(88, 17);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of fully-qualified package variable definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, definitionURI.path, "vscode.executeDefinitionProvider provided incorrect uri for fully-qualified package variable definition");
						assert.strictEqual(location.range.start.line, 2, "vscode.executeDefinitionProvider provided incorrect line for fully-qualified package variable definition");
						assert.strictEqual(location.range.start.character, 12, "vscode.executeDefinitionProvider provided incorrect character for fully-qualified package variable definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of super static variable", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(74, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of super static variable definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, definitionURI.path, "vscode.executeDefinitionProvider provided incorrect uri for super static variable definition");
						assert.strictEqual(location.range.start.line, 4, "vscode.executeDefinitionProvider provided incorrect line for super static variable definition");
						assert.strictEqual(location.range.start.character, 20, "vscode.executeDefinitionProvider provided incorrect character for super static variable definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of super static variable with member access operator on superclass", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(75, 22);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of super static variable definition with member access operator on superclass: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, definitionURI.path, "vscode.executeDefinitionProvider provided incorrect uri for super static variable definition with member access operator on superclass");
						assert.strictEqual(location.range.start.line, 4, "vscode.executeDefinitionProvider provided incorrect line for super static variable definition with member access operator on superclass");
						assert.strictEqual(location.range.start.character, 20, "vscode.executeDefinitionProvider provided incorrect character for super static variable definition with member access operator on superclass");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of super static property", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(81, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of super static property definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, definitionURI.path, "vscode.executeDefinitionProvider provided incorrect uri for super static property definition");
						assert.strictEqual(location.range.start.line, 6, "vscode.executeDefinitionProvider provided incorrect line for super static property definition");
						assert.strictEqual(location.range.start.character, 29, "vscode.executeDefinitionProvider provided incorrect character for super static property definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of super static property with member access operator on superclass", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(82, 22);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of super static property definition with member access operator on superclass: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, definitionURI.path, "vscode.executeDefinitionProvider provided incorrect uri for super static property definition with member access operator on superclass");
						assert.strictEqual(location.range.start.line, 6, "vscode.executeDefinitionProvider provided incorrect line for super static property definition with member access operator on superclass");
						assert.strictEqual(location.range.start.character, 29, "vscode.executeDefinitionProvider provided incorrect character for super static property definition with member access operator on superclass");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of super static function", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(67, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of super static function definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, definitionURI.path, "vscode.executeDefinitionProvider provided incorrect uri for super static function definition");
						assert.strictEqual(location.range.start.line, 15, "vscode.executeDefinitionProvider provided incorrect line for super static function definition");
						assert.strictEqual(location.range.start.character, 28, "vscode.executeDefinitionProvider provided incorrect character for super static function definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of super static function with member access operator on superclass", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(68, 22);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of super static function definition with member access operator on superclass: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, definitionURI.path, "vscode.executeDefinitionProvider provided incorrect uri for super static function definition with member access operator on superclass");
						assert.strictEqual(location.range.start.line, 15, "vscode.executeDefinitionProvider provided incorrect line for super static function definition with member access operator on superclass");
						assert.strictEqual(location.range.start.character, 28, "vscode.executeDefinitionProvider provided incorrect character for super static function definition with member access operator on superclass");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of super member function", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(63, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of super member function definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, definitionURI.path, "vscode.executeDefinitionProvider provided incorrect uri for super member function definition");
						assert.strictEqual(location.range.start.line, 30, "vscode.executeDefinitionProvider provided incorrect line for super member function definition");
						assert.strictEqual(location.range.start.character, 21, "vscode.executeDefinitionProvider provided incorrect character for super member function definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of super member function with member access operator on this", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(64, 10);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of super member function definition with member access operator on this: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, definitionURI.path, "vscode.executeDefinitionProvider provided incorrect uri for super member function definition with member access operator on this");
						assert.strictEqual(location.range.start.line, 30, "vscode.executeDefinitionProvider provided incorrect line for super member function definition with member access operator on this");
						assert.strictEqual(location.range.start.character, 21, "vscode.executeDefinitionProvider provided incorrect character for super member function definition with member access operator on this");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of super member function with member access operator on super", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(65, 11);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of super member function definition with member access operator on super: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, definitionURI.path, "vscode.executeDefinitionProvider provided incorrect uri for super member function definition with member access operator on super");
						assert.strictEqual(location.range.start.line, 30, "vscode.executeDefinitionProvider provided incorrect line for super member function definition with member access operator on super");
						assert.strictEqual(location.range.start.character, 21, "vscode.executeDefinitionProvider provided incorrect character for super member function definition with member access operator on super");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of super member variable", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(70, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of super member variable definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, definitionURI.path, "vscode.executeDefinitionProvider provided incorrect uri for super member variable definition");
						assert.strictEqual(location.range.start.line, 19, "vscode.executeDefinitionProvider provided incorrect line for super member variable definition");
						assert.strictEqual(location.range.start.character, 13, "vscode.executeDefinitionProvider provided incorrect character for super member variable definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of super member variable with member access operator on this", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(71, 10);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of super member variable definition with member access operator on this: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, definitionURI.path, "vscode.executeDefinitionProvider provided incorrect uri for super member variable definition with member access operator on this");
						assert.strictEqual(location.range.start.line, 19, "vscode.executeDefinitionProvider provided incorrect line for super member variable definition with member access operator on this");
						assert.strictEqual(location.range.start.character, 13, "vscode.executeDefinitionProvider provided incorrect character for super member variable definition with member access operator on this");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of super member variable with member access operator on super", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(72, 11);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of super member variable definition with member access operator on super: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, definitionURI.path, "vscode.executeDefinitionProvider provided incorrect uri for super member variable definition with member access operator on super");
						assert.strictEqual(location.range.start.line, 19, "vscode.executeDefinitionProvider provided incorrect line for super member variable definition with member access operator on super");
						assert.strictEqual(location.range.start.character, 13, "vscode.executeDefinitionProvider provided incorrect character for super member variable definition with member access operator on super");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of super member property", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(77, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of super member property definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, definitionURI.path, "vscode.executeDefinitionProvider provided incorrect uri for super member property definition");
						assert.strictEqual(location.range.start.line, 21, "vscode.executeDefinitionProvider provided incorrect line for super member property definition");
						assert.strictEqual(location.range.start.character, 22, "vscode.executeDefinitionProvider provided incorrect character for super member property definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of super member property with member access operator on this", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(78, 10);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of super member property definition with member access operator on this: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, definitionURI.path, "vscode.executeDefinitionProvider provided incorrect uri for super member property definition with member access operator on this");
						assert.strictEqual(location.range.start.line, 21, "vscode.executeDefinitionProvider provided incorrect line for super member property definition with member access operator on this");
						assert.strictEqual(location.range.start.character, 22, "vscode.executeDefinitionProvider provided incorrect character for super member property definition with member access operator on this");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of super member property with member access operator on super", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(79, 11);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of super member property definition with member access operator on super: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, definitionURI.path, "vscode.executeDefinitionProvider provided incorrect uri for super member property definition with member access operator on super");
						assert.strictEqual(location.range.start.line, 21, "vscode.executeDefinitionProvider provided incorrect line for super member property definition with member access operator on super");
						assert.strictEqual(location.range.start.character, 22, "vscode.executeDefinitionProvider provided incorrect character for super member property definition with member access operator on super");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of file-internal variable", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(94, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of file-internal variable definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for file-internal variable definition");
						assert.strictEqual(location.range.start.line, 111, "vscode.executeDefinitionProvider provided incorrect line for file-internal variable definition");
						assert.strictEqual(location.range.start.character, 4, "vscode.executeDefinitionProvider provided incorrect character for file-internal variable definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of file-internal function", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(95, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of file-internal function definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for file-internal function definition");
						assert.strictEqual(location.range.start.line, 110, "vscode.executeDefinitionProvider provided incorrect line for file-internal function definition");
						assert.strictEqual(location.range.start.character, 9, "vscode.executeDefinitionProvider provided incorrect character for file-internal function definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of file-internal class", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(97, 37);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of file-internal class definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for file-internal class definition");
						assert.strictEqual(location.range.start.line, 113, "vscode.executeDefinitionProvider provided incorrect line for file-internal class definition");
						assert.strictEqual(location.range.start.character, 6, "vscode.executeDefinitionProvider provided incorrect character for file-internal class definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of file-internal member function", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(99, 33);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of file-internal member function definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for file-internal member function definition");
						assert.strictEqual(location.range.start.line, 141, "vscode.executeDefinitionProvider provided incorrect line for file-internal member function definition");
						assert.strictEqual(location.range.start.character, 17, "vscode.executeDefinitionProvider provided incorrect character for file-internal member function definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of file-internal member variable", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(100, 33);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of file-internal member variable definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for file-internal member variable definition");
						assert.strictEqual(location.range.start.line, 130, "vscode.executeDefinitionProvider provided incorrect line for file-internal member variable definition");
						assert.strictEqual(location.range.start.character, 12, "vscode.executeDefinitionProvider provided incorrect character for file-internal member variable definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of file-internal member property", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(101, 33);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of file-internal member property definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for file-internal member property definition");
						assert.strictEqual(location.range.start.line, 132, "vscode.executeDefinitionProvider provided incorrect line for file-internal member property definition");
						assert.strictEqual(location.range.start.character, 21, "vscode.executeDefinitionProvider provided incorrect character for file-internal member property definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of file-internal static property", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(105, 25);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of file-internal static property definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for file-internal static property definition");
						assert.strictEqual(location.range.start.line, 117, "vscode.executeDefinitionProvider provided incorrect line for file-internal static property definition");
						assert.strictEqual(location.range.start.character, 28, "vscode.executeDefinitionProvider provided incorrect character for file-internal static property definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of file-internal static variable", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(104, 25);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of file-internal static variable definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for file-internal static variable definition");
						assert.strictEqual(location.range.start.line, 115, "vscode.executeDefinitionProvider provided incorrect line for file-internal static variable definition");
						assert.strictEqual(location.range.start.character, 19, "vscode.executeDefinitionProvider provided incorrect character for file-internal static variable definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
	test("vscode.executeDefinitionProvider finds definition of file-internal static function", (done) =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(103, 25);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeDefinitionProvider failed to provide location of file-internal static function definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, uri.path, "vscode.executeDefinitionProvider provided incorrect uri for file-internal static function definition");
						assert.strictEqual(location.range.start.line, 126, "vscode.executeDefinitionProvider provided incorrect line for file-internal static function definition");
						assert.strictEqual(location.range.start.character, 24, "vscode.executeDefinitionProvider provided incorrect character for file-internal static function definition");
					}, (err) =>
					{
						assert.fail("Failed to execute definition provider: " + uri);
					});
		}).then(() => done(), done);
	});
});