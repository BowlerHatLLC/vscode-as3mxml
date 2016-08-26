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