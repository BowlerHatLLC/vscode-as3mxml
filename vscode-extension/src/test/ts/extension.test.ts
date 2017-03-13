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
						assert(false, "Failed to show text document: " + uri);
					});
			}, (err) =>
			{
				assert(false, "Failed to open text document: " + uri);
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

function findCompletionItem(name: string, items: vscode.CompletionItem[]): vscode.CompletionItem
{
	return items.find((item: vscode.CompletionItem) =>
	{
		return item.label === name;
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
	test("vscode.executeDocumentSymbolProvider not empty", () =>
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
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeDocumentSymbolProvider includes class", () =>
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
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeDocumentSymbolProvider includes constructor", () =>
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
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeDocumentSymbolProvider includes member variable", () =>
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
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeDocumentSymbolProvider includes member function", () =>
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
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeDocumentSymbolProvider includes static variable", () =>
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
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeDocumentSymbolProvider includes static constant", () =>
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
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeDocumentSymbolProvider includes static function", () =>
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
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeDocumentSymbolProvider includes internal class", () =>
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
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeDocumentSymbolProvider includes member variable in internal class", () =>
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
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
});

suite("workspace symbol provider", () =>
{
	test("vscode.executeWorkspaceSymbolProvider includes class", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes constructor", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes member variable", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes member function", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes static variable", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes static constant", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes static function", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes internal class", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes member variable in internal class", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes symbols in unreferenced files", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes class in unreferenced file", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes constructor in unreferenced file", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes interface in unreferenced file", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
});

suite("signature help provider", () =>
{
	test("vscode.executeSignatureHelpProvider provides help for local function", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeSignatureHelpProvider provides help for member function", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeSignatureHelpProvider provides help for member function with member access operator on this", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeSignatureHelpProvider provides help for static function", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeSignatureHelpProvider provides help for static function with member access operator on class", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeSignatureHelpProvider provides help for package function", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeSignatureHelpProvider provides help for fully-qualified package function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "SignatureHelp.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeSignatureHelpProvider",
				uri, new vscode.Position(25, 31), "(")
				.then((signatureHelp: vscode.SignatureHelp) =>
					{
						assert.strictEqual(signatureHelp.signatures.length, 1,
							"Signature help not provided for fully-qualified package function");
						assert.strictEqual(signatureHelp.activeSignature, 0,
							"Active signature incorrect for fully-qualified package function");
						assert.strictEqual(signatureHelp.activeParameter, 0,
							"Active parameter incorrect for fully-qualified package function");
					}, (err) =>
					{
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeSignatureHelpProvider provides help for internal function", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeSignatureHelpProvider provides help for super constructor", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeSignatureHelpProvider provides help for super member method", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeSignatureHelpProvider must not provide help for private super member method", () =>
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
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
});

suite("definition provider", () =>
{
	test("vscode.executeDefinitionProvider finds definition of local variable", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of local function", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of member variable", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of member variable with member access operator on this", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of member function", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of member function with member access operator on this", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of member property", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of member property with member access operator on this", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of static variable", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of static variable with member access operator on class", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of static function", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of static function with member access operator on class", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of static property", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of static property with member access operator on class", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of package function", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of fully-qualified package function", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of package variable", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of fully-qualified package variable", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of super static variable", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of super static variable with member access operator on superclass", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of super static property", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of super static property with member access operator on superclass", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of super static function", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of super static function with member access operator on superclass", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of super member function", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of super member function with member access operator on this", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of super member function with member access operator on super", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of super member variable", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of super member variable with member access operator on this", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of super member variable with member access operator on super", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of super member property", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of super member property with member access operator on this", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of super member property with member access operator on super", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of file-internal variable", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of file-internal function", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of file-internal class", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of file-internal member function", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of file-internal member variable", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of file-internal member property", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of file-internal static property", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of file-internal static variable", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
	test("vscode.executeDefinitionProvider finds definition of file-internal static function", () =>
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
						assert(false, "Failed to execute definition provider: " + uri);
					});
		});
	});
});

suite("hover provider", () =>
{
	test("vscode.executeHoverProvider displays hover for local variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(90, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for local variable reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for local variable reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("localVar:String") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for local variable reference");
						assert.strictEqual(hover.range.start.line, 90, "vscode.executeHoverProvider provided incorrect line for local variable reference");
						assert.strictEqual(hover.range.start.character, 3, "vscode.executeHoverProvider provided incorrect character for local variable reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of local function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(92, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for local function reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for local function reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("localFunction():void") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for local function reference");
						assert.strictEqual(hover.range.start.line, 92, "vscode.executeDefinitionProvider provided incorrect line for local function reference");
						assert.strictEqual(hover.range.start.character, 3, "vscode.executeDefinitionProvider provided incorrect character for local function reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of member variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(54, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for member variable reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for member variable reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("memberVar:Boolean") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for member variable reference");
						assert.strictEqual(hover.range.start.line, 54, "vscode.executeHoverProvider provided incorrect line for member variable reference");
						assert.strictEqual(hover.range.start.character, 3, "vscode.executeHoverProvider provided incorrect character for member variable reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});

	test("vscode.executeHoverProvider displays hover of member variable with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(55, 10);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for member variable reference with member access operator on this: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for member variable reference with member access operator on this: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("memberVar:Boolean") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for member variable reference with member access operator on this");
						assert.strictEqual(hover.range.start.line, 55, "vscode.executeHoverProvider provided incorrect line for member variable reference with member access operator on this");
						assert.strictEqual(hover.range.start.character, 8, "vscode.executeHoverProvider provided incorrect character for member variable reference with member access operator on this");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of member function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(45, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for member function reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for member function reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("memberFunction():void") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for member function reference");
						assert.strictEqual(hover.range.start.line, 45, "vscode.executeHoverProvider provided incorrect line for member function reference");
						assert.strictEqual(hover.range.start.character, 3, "vscode.executeHoverProvider provided incorrect character for member function reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of member function with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(46, 10);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for member function reference with member access operator on this: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for member function reference with member access operator on this: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("memberFunction():void") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for member function reference with member access operator on this");
						assert.strictEqual(hover.range.start.line, 46, "vscode.executeHoverProvider provided incorrect line for member function reference with member access operator on this");
						assert.strictEqual(hover.range.start.character, 8, "vscode.executeHoverProvider provided incorrect character for member function reference with member access operator on this");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of member property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(57, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for member property reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for member property reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("memberProperty:Boolean") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for member property reference");
						assert.strictEqual(hover.range.start.line, 57, "vscode.executeHoverProvider provided incorrect line for member property reference");
						assert.strictEqual(hover.range.start.character, 3, "vscode.executeHoverProvider provided incorrect character for member property reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of member property with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(58, 10);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for member property reference with member access operator on this: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for member property reference with member access operator on this: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("memberProperty:Boolean") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for member property reference with member access operator on this");
						assert.strictEqual(hover.range.start.line, 58, "vscode.executeHoverProvider provided incorrect line for member property reference with member access operator on this");
						assert.strictEqual(hover.range.start.character, 8, "vscode.executeHoverProvider provided incorrect character for member property reference with member access operator on this");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of static variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(51, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for static variable reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for static variable reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("staticVar:Number") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for static variable reference");
						assert.strictEqual(hover.range.start.line, 51, "vscode.executeHoverProvider provided incorrect line for static variable reference");
						assert.strictEqual(hover.range.start.character, 3, "vscode.executeHoverProvider provided incorrect character for static variable reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of static variable with member access operator on class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(52, 17);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for static variable reference with member access operator on class: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for static variable reference with member access operator on class: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("staticVar:Number") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for static variable reference with member access operator on class");
						assert.strictEqual(hover.range.start.line, 52, "vscode.executeHoverProvider provided incorrect line for static variable reference with member access operator on class");
						assert.strictEqual(hover.range.start.character, 15, "vscode.executeHoverProvider provided incorrect character for static variable reference with member access operator on class");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of static function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(48, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for static function reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for static function reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("staticFunction():void") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for static function reference");
						assert.strictEqual(hover.range.start.line, 48, "vscode.executeHoverProvider provided incorrect line for static function reference");
						assert.strictEqual(hover.range.start.character, 3, "vscode.executeHoverProvider provided incorrect character for static function reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of static function with member access operator on class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(49, 17);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for static function reference with member access operator on class: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for static function reference with member access operator on class: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("staticFunction():void") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for static function reference with member access operator on class");
						assert.strictEqual(hover.range.start.line, 49, "vscode.executeHoverProvider provided incorrect line for static function reference with member access operator on class");
						assert.strictEqual(hover.range.start.character, 15, "vscode.executeHoverProvider provided incorrect character for static function reference with member access operator on class");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of static property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(60, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for static property reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for static property reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("staticProperty:Boolean") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for static property reference");
						assert.strictEqual(hover.range.start.line, 60, "vscode.executeHoverProvider provided incorrect line for static property reference");
						assert.strictEqual(hover.range.start.character, 3, "vscode.executeHoverProvider provided incorrect character for static property reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of static property with member access operator on class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(61, 17);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for static property reference with member access operator on class: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for static property reference with member access operator on class: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("staticProperty:Boolean") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for static property reference with member access operator on class");
						assert.strictEqual(hover.range.start.line, 61, "vscode.executeHoverProvider provided incorrect line for static property reference with member access operator on class");
						assert.strictEqual(hover.range.start.character, 15, "vscode.executeHoverProvider provided incorrect character for static property reference with member access operator on class");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of package function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "packageFunction.as"));
		let position = new vscode.Position(84, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for package function reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for package function reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("packageFunction(one:String, two:Number = 3, ...rest:Array):Boolean") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for package function reference");
						assert.strictEqual(hover.range.start.line, 84, "vscode.executeHoverProvider provided incorrect line for package function reference");
						assert.strictEqual(hover.range.start.character, 3, "vscode.executeHoverProvider provided incorrect character for package function reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of fully-qualified package function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "packageFunction.as"));
		let position = new vscode.Position(85, 17);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for fully-qualified package function reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for fully-qualified package function reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("packageFunction(one:String, two:Number = 3, ...rest:Array):Boolean") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for fully-qualified package function reference");
						assert.strictEqual(hover.range.start.line, 85, "vscode.executeHoverProvider provided incorrect line for fully-qualified package function reference");
						assert.strictEqual(hover.range.start.character, 15, "vscode.executeHoverProvider provided incorrect character for fully-qualified package function reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of package variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "packageVar.as"));
		let position = new vscode.Position(87, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for package variable reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for package variable reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("packageVar:Number") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for package variable reference");
						assert.strictEqual(hover.range.start.line, 87, "vscode.executeHoverProvider provided incorrect line for package variable reference");
						assert.strictEqual(hover.range.start.character, 3, "vscode.executeHoverProvider provided incorrect character for package variable reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of fully-qualified package variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "packageVar.as"));
		let position = new vscode.Position(88, 17);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for fully-qualified package variable reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for fully-qualified package variable reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("packageVar:Number") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for fully-qualified package variable reference");
						assert.strictEqual(hover.range.start.line, 88, "vscode.executeHoverProvider provided incorrect line for fully-qualified package variable reference");
						assert.strictEqual(hover.range.start.character, 15, "vscode.executeHoverProvider provided incorrect character for fully-qualified package variable reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of super static variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(74, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for super static variable reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for super static variable reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("superStaticVar:Boolean") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for super static variable reference");
						assert.strictEqual(hover.range.start.line, 74, "vscode.executeHoverProvider provided incorrect line for super static variable reference");
						assert.strictEqual(hover.range.start.character, 3, "vscode.executeHoverProvider provided incorrect character for super static variable reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of super static variable with member access operator on superclass", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(75, 22);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for super static variable reference with member access operator on superclass: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for super static variable reference with member access operator on superclass: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("superStaticVar:Boolean") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for super static variable reference with member access operator on superclass");
						assert.strictEqual(hover.range.start.line, 75, "vscode.executeHoverProvider provided incorrect line for super static variable reference with member access operator on superclass");
						assert.strictEqual(hover.range.start.character, 20, "vscode.executeHoverProvider provided incorrect character for super static variable reference with member access operator on superclass");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of super static property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(81, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for super static property reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for super static property reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("superStaticProperty:Boolean") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for super static property reference");
						assert.strictEqual(hover.range.start.line, 81, "vscode.executeHoverProvider provided incorrect line for super static property reference");
						assert.strictEqual(hover.range.start.character, 3, "vscode.executeHoverProvider provided incorrect character for super static property reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of super static property with member access operator on superclass", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(82, 22);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for super static property reference with member access operator on superclass: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for super static property reference with member access operator on superclass: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("superStaticProperty:Boolean") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for super static property reference with member access operator on superclass");
						assert.strictEqual(hover.range.start.line, 82, "vscode.executeHoverProvider provided incorrect line for super static property reference with member access operator on superclass");
						assert.strictEqual(hover.range.start.character, 20, "vscode.executeHoverProvider provided incorrect character for super static property reference with member access operator on superclass");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of super static function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(67, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for super static function reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for super static function reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("superStaticFunction():void") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for super static function reference");
						assert.strictEqual(hover.range.start.line, 67, "vscode.executeHoverProvider provided incorrect line for super static function reference");
						assert.strictEqual(hover.range.start.character, 3, "vscode.executeHoverProvider provided incorrect character for super static function reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of super static function with member access operator on superclass", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(68, 22);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for super static function reference with member access operator on superclass: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for super static function reference with member access operator on superclass: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("superStaticFunction():void") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for super static function reference with member access operator on superclass");
						assert.strictEqual(hover.range.start.line, 68, "vscode.executeHoverProvider provided incorrect line for super static function reference with member access operator on superclass");
						assert.strictEqual(hover.range.start.character, 20, "vscode.executeHoverProvider provided incorrect character for super static function reference with member access operator on superclass");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of super member function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(63, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for super member function reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for super member function reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("superMemberFunction():void") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for super member function reference");
						assert.strictEqual(hover.range.start.line, 63, "vscode.executeHoverProvider provided incorrect line for super member function reference");
						assert.strictEqual(hover.range.start.character, 3, "vscode.executeHoverProvider provided incorrect character for super member function reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of super member function with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(64, 10);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for super member function reference with member access operator on this: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for super member function reference with member access operator on this: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("superMemberFunction():void") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for super member function reference with member access operator on this");
						assert.strictEqual(hover.range.start.line, 64, "vscode.executeHoverProvider provided incorrect line for super member function reference with member access operator on this");
						assert.strictEqual(hover.range.start.character, 8, "vscode.executeHoverProvider provided incorrect character for super member function reference with member access operator on this");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of super member function with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(65, 11);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for super member function reference with member access operator on super: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for super member function reference with member access operator on super: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("superMemberFunction():void") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for super member function reference with member access operator on super");
						assert.strictEqual(hover.range.start.line, 65, "vscode.executeHoverProvider provided incorrect line for super member function reference with member access operator on super");
						assert.strictEqual(hover.range.start.character, 9, "vscode.executeHoverProvider provided incorrect character for super member function reference with member access operator on super");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of super member variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(70, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for super member variable reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for super member variable reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("superMemberVar:String") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for super member variable reference");
						assert.strictEqual(hover.range.start.line, 70, "vscode.executeHoverProvider provided incorrect line for super member variable reference");
						assert.strictEqual(hover.range.start.character, 3, "vscode.executeHoverProvider provided incorrect character for super member variable reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of super member variable with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(71, 10);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for super member variable reference with member access operator on this: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for super member variable reference with member access operator on this: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("superMemberVar:String") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for super member variable reference with member access operator on this");
						assert.strictEqual(hover.range.start.line, 71, "vscode.executeHoverProvider provided incorrect line for super member variable reference with member access operator on this");
						assert.strictEqual(hover.range.start.character, 8, "vscode.executeHoverProvider provided incorrect character for super member variable reference with member access operator on this");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of super member variable with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(72, 11);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for super member variable reference with member access operator on super: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for super member variable reference with member access operator on super: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("superMemberVar:String") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for super member variable reference with member access operator on super");
						assert.strictEqual(hover.range.start.line, 72, "vscode.executeHoverProvider provided incorrect line for super member variable reference with member access operator on super");
						assert.strictEqual(hover.range.start.character, 9, "vscode.executeHoverProvider provided incorrect character for super member variable reference with member access operator on super");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of super member property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(77, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for super member property reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for super member property reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("superMemberProperty:Boolean") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for super member property reference");
						assert.strictEqual(hover.range.start.line, 77, "vscode.executeHoverProvider provided incorrect line for super member property reference");
						assert.strictEqual(hover.range.start.character, 3, "vscode.executeHoverProvider provided incorrect character for super member property reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of super member property with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(78, 10);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for super member property reference with member access operator on this: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for super member property reference with member access operator on this: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("superMemberProperty:Boolean") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for super member property reference with member access operator on this");
						assert.strictEqual(hover.range.start.line, 78, "vscode.executeHoverProvider provided incorrect line for super member property reference with member access operator on this");
						assert.strictEqual(hover.range.start.character, 8, "vscode.executeHoverProvider provided incorrect character for super member property reference with member access operator on this");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of super member property with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "com", "example", "SuperDefinitions.as"));
		let position = new vscode.Position(79, 11);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for super member property reference with member access operator on super: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for super member property reference with member access operator on super: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("superMemberProperty:Boolean") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for super member property reference with member access operator on super");
						assert.strictEqual(hover.range.start.line, 79, "vscode.executeHoverProvider provided incorrect line for super member property reference with member access operator on super");
						assert.strictEqual(hover.range.start.character, 9, "vscode.executeHoverProvider provided incorrect character for super member property reference with member access operator on super");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of file-internal variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(94, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for file-internal variable reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for file-internal variable reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("internalVar:Number") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for file-internal variable reference");
						assert.strictEqual(hover.range.start.line, 94, "vscode.executeHoverProvider provided incorrect line for file-internal variable reference");
						assert.strictEqual(hover.range.start.character, 3, "vscode.executeHoverProvider provided incorrect character for file-internal variable reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of file-internal function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(95, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for file-internal function reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for file-internal function reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("internalFunction():void") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for file-internal function reference");
						assert.strictEqual(hover.range.start.line, 95, "vscode.executeHoverProvider provided incorrect line for file-internal function reference");
						assert.strictEqual(hover.range.start.character, 3, "vscode.executeHoverProvider provided incorrect character for file-internal function reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of file-internal class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(97, 37);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for file-internal class reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for file-internal class reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("class InternalDefinitions") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for file-internal class reference");
						assert.strictEqual(hover.range.start.line, 97, "vscode.executeHoverProvider provided incorrect line for file-internal class reference");
						assert.strictEqual(hover.range.start.character, 35, "vscode.executeHoverProvider provided incorrect character for file-internal class reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of file-internal member function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(99, 33);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for file-internal member function reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for file-internal member function reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("internalMemberFunction():void") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for file-internal member function reference");
						assert.strictEqual(hover.range.start.line, 99, "vscode.executeHoverProvider provided incorrect line for file-internal member function reference");
						assert.strictEqual(hover.range.start.character, 31, "vscode.executeHoverProvider provided incorrect character for file-internal member function reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of file-internal member variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(100, 33);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for file-internal member variable reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for file-internal member variable reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("internalMemberVar:String") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for file-internal member variable reference");
						assert.strictEqual(hover.range.start.line, 100, "vscode.executeHoverProvider provided incorrect line for file-internal member variable reference");
						assert.strictEqual(hover.range.start.character, 31, "vscode.executeHoverProvider provided incorrect character for file-internal member variable reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of file-internal member property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(101, 33);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for file-internal member property reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for file-internal member property reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("internalMemberProperty:Boolean") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for file-internal member property reference");
						assert.strictEqual(hover.range.start.line, 101, "vscode.executeHoverProvider provided incorrect line for file-internal member property reference");
						assert.strictEqual(hover.range.start.character, 31, "vscode.executeHoverProvider provided incorrect character for file-internal member property reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of file-internal static property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(105, 25);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for file-internal static property reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for file-internal static property reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("internalStaticProperty:Boolean") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for file-internal static property reference");
						assert.strictEqual(hover.range.start.line, 105, "vscode.executeHoverProvider provided incorrect line for file-internal static property reference");
						assert.strictEqual(hover.range.start.character, 23, "vscode.executeHoverProvider provided incorrect character for file-internal static property reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of file-internal static variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(104, 25);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for file-internal static variable reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for file-internal static variable reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("internalStaticVar:Boolean") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for file-internal static variable reference");
						assert.strictEqual(hover.range.start.line, 104, "vscode.executeHoverProvider provided incorrect line for file-internal static variable reference");
						assert.strictEqual(hover.range.start.character, 23, "vscode.executeHoverProvider provided incorrect character for file-internal static variable reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
	test("vscode.executeHoverProvider displays hover of file-internal static function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Definitions.as"));
		let position = new vscode.Position(103, 25);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeHoverProvider", uri, position)
				.then((hovers: vscode.Hover[]) =>
					{
						assert.strictEqual(hovers.length, 1,
							"vscode.executeHoverProvider failed to provide hover for file-internal static function reference: " + uri);
						let hover = hovers[0];
						let contents = hover.contents;
						assert.strictEqual(contents.length, 1,
							"vscode.executeHoverProvider failed to provide hover contents for file-internal static function reference: " + uri);
						let content = contents[0];
						let contentValue: string;
						if(typeof content === "string")
						{
							contentValue = content;
						}
						else
						{
							contentValue = content.value;
						}
						assert.strictEqual(contentValue.indexOf("internalStaticFunction():void") >= 0, true, "vscode.executeHoverProvider provided incorrect hover for file-internal static function reference");
						assert.strictEqual(hover.range.start.line, 103, "vscode.executeHoverProvider provided incorrect line for file-internal static function reference");
						assert.strictEqual(hover.range.start.character, 23, "vscode.executeHoverProvider provided incorrect character for file-internal static function reference");
					}, (err) =>
					{
						assert(false, "Failed to execute hover provider: " + uri);
					});
		});
	});
});

suite("completion item provider", () =>
{
	test("vscode.executeCompletionItemProvider includes local variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(45, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localVarItem = findCompletionItem("localVar", items);
						assert.notEqual(localVarItem, null, "vscode.executeCompletionItemProvider failed to provide local variable: " + uri);
						assert.strictEqual(localVarItem.kind, vscode.CompletionItemKind.Variable, "vscode.executeCompletionItemProvider failed to provide correct kind of local variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits local variable with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localVarItem = findCompletionItem("localVar", items);
						assert.equal(localVarItem, null, "vscode.executeCompletionItemProvider failed to omit local variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits local variable with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localVarItem = findCompletionItem("localVar", items);
						assert.equal(localVarItem, null, "vscode.executeCompletionItemProvider failed to omit local variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits local variable from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("localVar", items);
						assert.equal(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to omit local variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes local function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(45, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localFunctionItem = findCompletionItem("localFunction", items);
						assert.notEqual(localFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide local function: " + uri);
						assert.strictEqual(localFunctionItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of local function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits local function with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localFunctionItem = findCompletionItem("localFunction", items);
						assert.equal(localFunctionItem, null, "vscode.executeCompletionItemProvider failed to omit local function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits local function with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localVarItem = findCompletionItem("localFunction", items);
						assert.equal(localVarItem, null, "vscode.executeCompletionItemProvider failed to omit local function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits local function from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("localFunction", items);
						assert.equal(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to omit local function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes member variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(45, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberVarItem = findCompletionItem("memberVar", items);
						assert.notEqual(memberVarItem, null, "vscode.executeCompletionItemProvider failed to provide member variable: " + uri);
						assert.strictEqual(memberVarItem.kind, vscode.CompletionItemKind.Variable, "vscode.executeCompletionItemProvider failed to provide correct kind of member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes member variable with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberVarItem = findCompletionItem("memberVar", items);
						assert.notEqual(memberVarItem, null, "vscode.executeCompletionItemProvider failed to provide member variable: " + uri);
						assert.strictEqual(memberVarItem.kind, vscode.CompletionItemKind.Variable, "vscode.executeCompletionItemProvider failed to provide correct kind of member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits member variable with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localVarItem = findCompletionItem("memberVar", items);
						assert.equal(localVarItem, null, "vscode.executeCompletionItemProvider failed to omit member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits member variable from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("memberVar", items);
						assert.equal(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to omit member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes member function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(45, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberFunctionItem = findCompletionItem("memberFunction", items);
						assert.notEqual(memberFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide member function: " + uri);
						assert.strictEqual(memberFunctionItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of member function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes member function with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberFunctionItem = findCompletionItem("memberFunction", items);
						assert.notEqual(memberFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide member function: " + uri);
						assert.strictEqual(memberFunctionItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of member function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits member function with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localVarItem = findCompletionItem("memberFunction", items);
						assert.equal(localVarItem, null, "vscode.executeCompletionItemProvider failed to omit member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits member function from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("memberFunction", items);
						assert.equal(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to omit member function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes member property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(45, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberPropItem = findCompletionItem("memberProperty", items);
						assert.notEqual(memberPropItem, null, "vscode.executeCompletionItemProvider failed to provide member property: " + uri);
						assert.strictEqual(memberPropItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes member property with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberPropItem = findCompletionItem("memberProperty", items);
						assert.notEqual(memberPropItem, null, "vscode.executeCompletionItemProvider failed to provide member property: " + uri);
						assert.strictEqual(memberPropItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits member property with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localVarItem = findCompletionItem("memberProperty", items);
						assert.equal(localVarItem, null, "vscode.executeCompletionItemProvider failed to omit member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits member property from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("memberProperty", items);
						assert.equal(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to omit member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes static variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(45, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let staticVarItem = findCompletionItem("staticVar", items);
						assert.notEqual(staticVarItem, null, "vscode.executeCompletionItemProvider failed to provide static variable: " + uri);
						assert.strictEqual(staticVarItem.kind, vscode.CompletionItemKind.Variable, "vscode.executeCompletionItemProvider failed to provide correct kind of static variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes static variable with member access operator on class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 14);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let staticVarItem = findCompletionItem("staticVar", items);
						assert.notEqual(staticVarItem, null, "vscode.executeCompletionItemProvider failed to provide static variable: " + uri);
						assert.strictEqual(staticVarItem.kind, vscode.CompletionItemKind.Variable, "vscode.executeCompletionItemProvider failed to provide correct kind of static variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits static variable with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let staticVarItem = findCompletionItem("staticVar", items);
						assert.equal(staticVarItem, null, "vscode.executeCompletionItemProvider failed to omit static variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits static variable with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localVarItem = findCompletionItem("staticVar", items);
						assert.equal(localVarItem, null, "vscode.executeCompletionItemProvider failed to omit static variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits static variable from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("staticVar", items);
						assert.equal(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to omit static variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes static function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(45, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let staticFunctionItem = findCompletionItem("staticFunction", items);
						assert.notEqual(staticFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide static function: " + uri);
						assert.strictEqual(staticFunctionItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of static function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes static function with member access operator on class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 14);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let staticFunctionItem = findCompletionItem("staticFunction", items);
						assert.notEqual(staticFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide static function: " + uri);
						assert.strictEqual(staticFunctionItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of static function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits static function with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let staticFunctionItem = findCompletionItem("staticFunction", items);
						assert.equal(staticFunctionItem, null, "vscode.executeCompletionItemProvider failed to omit static function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits static function with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localVarItem = findCompletionItem("staticFunction", items);
						assert.equal(localVarItem, null, "vscode.executeCompletionItemProvider failed to omit static function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits static function from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("staticFunction", items);
						assert.equal(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to omit static function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes static property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(45, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let staticPropItem = findCompletionItem("staticProperty", items);
						assert.notEqual(staticPropItem, null, "vscode.executeCompletionItemProvider failed to provide static property: " + uri);
						assert.strictEqual(staticPropItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of static property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes static property with member access operator on class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 14);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let staticPropItem = findCompletionItem("staticProperty", items);
						assert.notEqual(staticPropItem, null, "vscode.executeCompletionItemProvider failed to provide static property: " + uri);
						assert.strictEqual(staticPropItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of static property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits static property with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let staticPropItem = findCompletionItem("staticProperty", items);
						assert.equal(staticPropItem, null, "vscode.executeCompletionItemProvider failed to omit static property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits static property with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localVarItem = findCompletionItem("staticProperty", items);
						assert.equal(localVarItem, null, "vscode.executeCompletionItemProvider failed to omit static property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits static property from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("staticProperty", items);
						assert.equal(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to omit static property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes package class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(45, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let packageClassItem = findCompletionItem("UnreferencedClass", items);
						assert.notEqual(packageClassItem, null, "vscode.executeCompletionItemProvider failed to provide package class: " + uri);
						assert.strictEqual(packageClassItem.kind, vscode.CompletionItemKind.Class, "vscode.executeCompletionItemProvider failed to provide correct kind of package class: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits package class with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localVarItem = findCompletionItem("UnreferenceClass", items);
						assert.equal(localVarItem, null, "vscode.executeCompletionItemProvider failed to omit package class: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits package class with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localVarItem = findCompletionItem("UnreferenceClass", items);
						assert.equal(localVarItem, null, "vscode.executeCompletionItemProvider failed to omit package class: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes package class as type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let packageClassItem = findCompletionItem("UnreferencedClass", items);
						assert.notEqual(packageClassItem, null, "vscode.executeCompletionItemProvider failed to provide package class: " + uri);
						assert.strictEqual(packageClassItem.kind, vscode.CompletionItemKind.Class, "vscode.executeCompletionItemProvider failed to provide correct kind of package class: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes package variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(45, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let packageVarItem = findCompletionItem("packageVar", items);
						assert.notEqual(packageVarItem, null, "vscode.executeCompletionItemProvider failed to provide package variable: " + uri);
						assert.strictEqual(packageVarItem.kind, vscode.CompletionItemKind.Variable, "vscode.executeCompletionItemProvider failed to provide correct kind of package variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	/*test("vscode.executeCompletionItemProvider includes package variable with member access on fully-qualified package", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(50, 15);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let packageVarItem = findCompletionItem("packageVar", items);
						assert.notEqual(packageVarItem, null, "vscode.executeCompletionItemProvider failed to provide package variable: " + uri);
						assert.strictEqual(packageVarItem.kind, vscode.CompletionItemKind.Variable, "vscode.executeCompletionItemProvider failed to provide correct kind of package variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});*/
	test("vscode.executeCompletionItemProvider omits package variable with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localVarItem = findCompletionItem("packageVar", items);
						assert.equal(localVarItem, null, "vscode.executeCompletionItemProvider failed to omit package variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits package variable with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localVarItem = findCompletionItem("packageVar", items);
						assert.equal(localVarItem, null, "vscode.executeCompletionItemProvider failed to omit package variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits package variable as type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let packageClassItem = findCompletionItem("packageVar", items);
						assert.equal(packageClassItem, null, "vscode.executeCompletionItemProvider failed to omits package variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes package function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(45, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let packageFunctionItem = findCompletionItem("packageFunction", items);
						assert.notEqual(packageFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide package function: " + uri);
						assert.strictEqual(packageFunctionItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of package function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	/*test("vscode.executeCompletionItemProvider includes package function with member access on fully-qualified package", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(50, 15);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let packageFunctionItem = findCompletionItem("packageFunction", items);
						assert.notEqual(packageFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide package function: " + uri);
						assert.strictEqual(packageFunctionItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of package function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});*/
	test("vscode.executeCompletionItemProvider omits package function with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localVarItem = findCompletionItem("packageFunction", items);
						assert.equal(localVarItem, null, "vscode.executeCompletionItemProvider failed to omit package function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits package function with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localVarItem = findCompletionItem("packageFunction", items);
						assert.equal(localVarItem, null, "vscode.executeCompletionItemProvider failed to omit package function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits package function as type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let packageClassItem = findCompletionItem("packageFunction", items);
						assert.equal(packageClassItem, null, "vscode.executeCompletionItemProvider failed to omits package function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super member variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(45, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("superMemberVar", items);
						assert.notEqual(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to provide super member variable: " + uri);
						assert.strictEqual(superMemberVarItem.kind, vscode.CompletionItemKind.Variable, "vscode.executeCompletionItemProvider failed to provide correct kind of super member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super member variable with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("superMemberVar", items);
						assert.notEqual(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to provide super member variable: " + uri);
						assert.strictEqual(superMemberVarItem.kind, vscode.CompletionItemKind.Variable, "vscode.executeCompletionItemProvider failed to provide correct kind of super member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits super member variable from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("superMemberVar", items);
						assert.equal(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to omit super member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super member variable with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("superMemberVar", items);
						assert.notEqual(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to provide member super variable: " + uri);
						assert.strictEqual(superMemberVarItem.kind, vscode.CompletionItemKind.Variable, "vscode.executeCompletionItemProvider failed to provide correct kind of super member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super member property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(45, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberPropertyItem = findCompletionItem("superMemberProperty", items);
						assert.notEqual(superMemberPropertyItem, null, "vscode.executeCompletionItemProvider failed to provide super member property: " + uri);
						assert.strictEqual(superMemberPropertyItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of super member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super member property with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberPropertyItem = findCompletionItem("superMemberProperty", items);
						assert.notEqual(superMemberPropertyItem, null, "vscode.executeCompletionItemProvider failed to provide super member property: " + uri);
						assert.strictEqual(superMemberPropertyItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of super member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super member property with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberPropertyItem = findCompletionItem("superMemberProperty", items);
						assert.notEqual(superMemberPropertyItem, null, "vscode.executeCompletionItemProvider failed to provide super member property: " + uri);
						assert.strictEqual(superMemberPropertyItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of super member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits super member property from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("superMemberProperty", items);
						assert.equal(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to omit super member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super member function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(45, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberFunctionItem = findCompletionItem("superMemberFunction", items);
						assert.notEqual(superMemberFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide super member function: " + uri);
						assert.strictEqual(superMemberFunctionItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of super member function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super member function with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberFunctionItem = findCompletionItem("superMemberFunction", items);
						assert.notEqual(superMemberFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide super member function: " + uri);
						assert.strictEqual(superMemberFunctionItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of super member function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super member function with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberFunctionItem = findCompletionItem("superMemberFunction", items);
						assert.notEqual(superMemberFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide super member function: " + uri);
						assert.strictEqual(superMemberFunctionItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of super member function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits super member function from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("superMemberFunction", items);
						assert.equal(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to omit super member function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super static variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(45, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superStaticVarItem = findCompletionItem("superStaticVar", items);
						assert.notEqual(superStaticVarItem, null, "vscode.executeCompletionItemProvider failed to provide super static variable: " + uri);
						assert.strictEqual(superStaticVarItem.kind, vscode.CompletionItemKind.Variable, "vscode.executeCompletionItemProvider failed to provide correct kind of super static variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits super static variable with member access operator on class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 15);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superStaticVarItem = findCompletionItem("superStaticVar", items);
						assert.equal(superStaticVarItem, null, "vscode.executeCompletionItemProvider failed to omit super static variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super static variable with member access operator on superclass", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(49, 20);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superStaticVarItem = findCompletionItem("superStaticVar", items);
						assert.notEqual(superStaticVarItem, null, "vscode.executeCompletionItemProvider failed to provide super static variable: " + uri);
						assert.strictEqual(superStaticVarItem.kind, vscode.CompletionItemKind.Variable, "vscode.executeCompletionItemProvider failed to provide correct kind of super static variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super static variable with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberFunctionItem = findCompletionItem("superStaticVar", items);
						assert.equal(superMemberFunctionItem, null, "vscode.executeCompletionItemProvider failed to omit super static variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits super static variable with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberFunctionItem = findCompletionItem("superStaticVar", items);
						assert.equal(superMemberFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide super static variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits super static variable from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("superStaticVar", items);
						assert.equal(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to omit super static variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super static property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(45, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superStaticPropertyItem = findCompletionItem("superStaticProperty", items);
						assert.notEqual(superStaticPropertyItem, null, "vscode.executeCompletionItemProvider failed to provide super static property: " + uri);
						assert.strictEqual(superStaticPropertyItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of super static property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits super static property with member access operator on class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 15);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superStaticPropertyItem = findCompletionItem("superStaticProperty", items);
						assert.equal(superStaticPropertyItem, null, "vscode.executeCompletionItemProvider failed to omit super static property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super static property with member access operator on superclass", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(49, 20);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superStaticPropertyItem = findCompletionItem("superStaticProperty", items);
						assert.notEqual(superStaticPropertyItem, null, "vscode.executeCompletionItemProvider failed to provide super static property: " + uri);
						assert.strictEqual(superStaticPropertyItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of super static property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super static property with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberFunctionItem = findCompletionItem("superStaticProperty", items);
						assert.equal(superMemberFunctionItem, null, "vscode.executeCompletionItemProvider failed to omit super static property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits super static property with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberFunctionItem = findCompletionItem("superStaticProperty", items);
						assert.equal(superMemberFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide super static property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits super static property from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("superStaticProperty", items);
						assert.equal(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to omit super static property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super static function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(45, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superStaticFunctionItem = findCompletionItem("superStaticFunction", items);
						assert.notEqual(superStaticFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide super static function: " + uri);
						assert.strictEqual(superStaticFunctionItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of super static function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits super static function with member access operator on class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 15);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superStaticFunctionItem = findCompletionItem("superStaticFunction", items);
						assert.equal(superStaticFunctionItem, null, "vscode.executeCompletionItemProvider failed to omit super static function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super static function with member access operator on superclass", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(49, 20);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superStaticFunctionItem = findCompletionItem("superStaticFunction", items);
						assert.notEqual(superStaticFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide super static function: " + uri);
						assert.strictEqual(superStaticFunctionItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of super static function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super static function with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberFunctionItem = findCompletionItem("superStaticFunction", items);
						assert.equal(superMemberFunctionItem, null, "vscode.executeCompletionItemProvider failed to omit super static function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits super static function with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberFunctionItem = findCompletionItem("superStaticFunction", items);
						assert.equal(superMemberFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide super static function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits super static function from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("superStaticFunction", items);
						assert.equal(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to omit super static function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes file-internal variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(45, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let fileInternalVarItem = findCompletionItem("fileInternalVar", items);
						assert.notEqual(fileInternalVarItem, null, "vscode.executeCompletionItemProvider failed to provide file-internal variable: " + uri);
						assert.strictEqual(fileInternalVarItem.kind, vscode.CompletionItemKind.Variable, "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits file-internal variable with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let fileInternalVarItem = findCompletionItem("fileInternalVar", items);
						assert.equal(fileInternalVarItem, null, "vscode.executeCompletionItemProvider failed to omit file-internal variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits file-internal variable from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("fileInternalVar", items);
						assert.equal(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to omit file-internal variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes file-internal function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(45, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let fileInternalFunctionItem = findCompletionItem("fileInternalFunction", items);
						assert.notEqual(fileInternalFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide file-internal function: " + uri);
						assert.strictEqual(fileInternalFunctionItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits file-internal function with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let fileInternalFunctionItem = findCompletionItem("fileInternalFunction", items);
						assert.equal(fileInternalFunctionItem, null, "vscode.executeCompletionItemProvider failed to omit file-internal function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits file-internal function from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("fileInternalFunction", items);
						assert.equal(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to omit file-internal function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes file-internal class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(45, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let fileInternalClassItem = findCompletionItem("FileInternalCompletion", items);
						assert.notEqual(fileInternalClassItem, null, "vscode.executeCompletionItemProvider failed to provide file-internal class: " + uri);
						assert.strictEqual(fileInternalClassItem.kind, vscode.CompletionItemKind.Class, "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal class: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes file-internal class as type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let fileInternalClassItem = findCompletionItem("FileInternalCompletion", items);
						assert.notEqual(fileInternalClassItem, null, "vscode.executeCompletionItemProvider failed to provide file-internal class: " + uri);
						assert.strictEqual(fileInternalClassItem.kind, vscode.CompletionItemKind.Class, "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal class: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes file-internal member variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(52, 12);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let fileInternalMemberVarItem = findCompletionItem("fileInternalMemberVar", items);
						assert.notEqual(fileInternalMemberVarItem, null, "vscode.executeCompletionItemProvider failed to provide file-internal member variable: " + uri);
						assert.strictEqual(fileInternalMemberVarItem.kind, vscode.CompletionItemKind.Variable, "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes file-internal member function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(52, 12);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let fileInternalMemberFunctionItem = findCompletionItem("fileInternalMemberFunction", items);
						assert.notEqual(fileInternalMemberFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide file-internal member function: " + uri);
						assert.strictEqual(fileInternalMemberFunctionItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal member function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes file-internal member property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(52, 12);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let fileInternalMemberPropertyItem = findCompletionItem("fileInternalMemberProperty", items);
						assert.notEqual(fileInternalMemberPropertyItem, null, "vscode.executeCompletionItemProvider failed to provide file-internal member property: " + uri);
						assert.strictEqual(fileInternalMemberPropertyItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes file-internal static variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(53, 26);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let fileInternalStaticVarItem = findCompletionItem("fileInternalStaticVar", items);
						assert.notEqual(fileInternalStaticVarItem, null, "vscode.executeCompletionItemProvider failed to provide file-internal static variable: " + uri);
						assert.strictEqual(fileInternalStaticVarItem.kind, vscode.CompletionItemKind.Variable, "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal static variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes file-internal static function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(53, 26);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let fileInternalStaticFunctionItem = findCompletionItem("fileInternalStaticFunction", items);
						assert.notEqual(fileInternalStaticFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide file-internal static function: " + uri);
						assert.strictEqual(fileInternalStaticFunctionItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal static function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes file-internal static property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(53, 26);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let fileInternalStaticPropertyItem = findCompletionItem("fileInternalStaticProperty", items);
						assert.notEqual(fileInternalStaticPropertyItem, null, "vscode.executeCompletionItemProvider failed to provide file-internal static property: " + uri);
						assert.strictEqual(fileInternalStaticPropertyItem.kind, vscode.CompletionItemKind.Function, "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal static property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits protected member variable not in superclass", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(52, 12);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let protectedMemberVarItem = findCompletionItem("protectedMemberVar", items);
						assert.equal(protectedMemberVarItem, null, "vscode.executeCompletionItemProvider failed to omit protected member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits protected member function not in superclass", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(52, 12);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let protectedMemberFunctionItem = findCompletionItem("protectedMemberFunction", items);
						assert.equal(protectedMemberFunctionItem, null, "vscode.executeCompletionItemProvider failed to omit protected member function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits protected member property not in superclass", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(52, 12);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let protectedMemberPropertyItem = findCompletionItem("protectedMemberProperty", items);
						assert.equal(protectedMemberPropertyItem, null, "vscode.executeCompletionItemProvider failed to omit protected member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits protected static variable not in superclass", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(53, 26);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let protectedStaticVarItem = findCompletionItem("protectedStaticVar", items);
						assert.equal(protectedStaticVarItem, null, "vscode.executeCompletionItemProvider failed to omit protected static variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits protected static function not in superclass", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(53, 26);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let protectedStaticFunctionItem = findCompletionItem("protectedStaticFunction", items);
						assert.equal(protectedStaticFunctionItem, null, "vscode.executeCompletionItemProvider failed to omit protected static function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits protected static property not in superclass", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "Completion.as"));
		let position = new vscode.Position(53, 26);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let protectedStaticPropertyItem = findCompletionItem("protectedStaticProperty", items);
						assert.equal(protectedStaticPropertyItem, null, "vscode.executeCompletionItemProvider failed to omit protected static property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
});

suite("MXML completion item provider", () =>
{
	test("vscode.executeCompletionItemProvider includes property as attribute", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(5, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let propertyItem = findCompletionItem("beads", items);
						assert.notEqual(propertyItem, null, "vscode.executeCompletionItemProvider failed to provide property as attribute: " + uri);
						assert.strictEqual(propertyItem.kind, vscode.CompletionItemKind.Variable, "vscode.executeCompletionItemProvider failed to provide correct kind of property: " + uri);
						assert.strictEqual(propertyItem.insertText, "beads", "vscode.executeCompletionItemProvider failed to provide correct insert text for property as attribute: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes property as child element", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(6, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let propertyItem = findCompletionItem("beads", items);
						assert.notEqual(propertyItem, null, "vscode.executeCompletionItemProvider failed to provide property as child element: " + uri);
						assert.strictEqual(propertyItem.kind, vscode.CompletionItemKind.Variable, "vscode.executeCompletionItemProvider failed to provide correct kind of property: " + uri);
						assert.strictEqual(propertyItem.insertText, "js:beads", "vscode.executeCompletionItemProvider failed to provide correct insert text for property as child element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits property as attribute of closing element", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(7, 14);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let propertyItem = findCompletionItem("beads", items);
						assert.equal(propertyItem, null, "vscode.executeCompletionItemProvider failed to omit property as attribute of closing element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes event as attribute", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(5, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let eventItem = findCompletionItem("click", items);
						assert.notEqual(eventItem, null, "vscode.executeCompletionItemProvider failed to provide event as attribute: " + uri);
						assert.strictEqual(eventItem.kind, vscode.CompletionItemKind.Field, "vscode.executeCompletionItemProvider failed to provide correct kind of event: " + uri);
						assert.strictEqual(eventItem.insertText, "click", "vscode.executeCompletionItemProvider failed to provide correct insert text for event as attribute: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes event as child element", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(6, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let eventItem = findCompletionItem("click", items);
						assert.notEqual(eventItem, null, "vscode.executeCompletionItemProvider failed to provide event as child element: " + uri);
						assert.strictEqual(eventItem.kind, vscode.CompletionItemKind.Field, "vscode.executeCompletionItemProvider failed to provide correct kind of event: " + uri);
						assert.strictEqual(eventItem.insertText, "js:click", "vscode.executeCompletionItemProvider failed to provide correct insert text for event as child element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits event as attribute of closing element", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(7, 14);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let eventItem = findCompletionItem("click", items);
						assert.equal(eventItem, null, "vscode.executeCompletionItemProvider failed to omit event as attribute of closing element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes class as child element", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.rootPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(6, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let packageClassItem = findCompletionItem("UnreferencedClass", items);
						assert.notEqual(packageClassItem, null, "vscode.executeCompletionItemProvider failed to provide package class: " + uri);
						assert.strictEqual(packageClassItem.kind, vscode.CompletionItemKind.Class, "vscode.executeCompletionItemProvider failed to provide correct kind of package class: " + uri);
						assert.strictEqual(packageClassItem.insertText, "ns1:UnreferencedClass", "vscode.executeCompletionItemProvider failed to provide correct insert text for package class as child element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
});