/*
Copyright 2016-2018 Bowler Hat LLC

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

const EXTENSION_ID = "bowlerhatllc.vscode-nextgenas";
const COMMAND_ADD_IMPORT = "nextgenas.addImport";
const COMMAND_GENERATE_GETTER = "nextgenas.generateGetter";
const COMMAND_GENERATE_SETTER = "nextgenas.generateSetter";
const COMMAND_GENERATE_GETTER_AND_SETTER = "nextgenas.generateGetterAndSetter";
const COMMAND_GENERATE_LOCAL_VARIABLE = "nextgenas.generateLocalVariable";
const COMMAND_GENERATE_FIELD_VARIABLE = "nextgenas.generateFieldVariable";
const COMMAND_GENERATE_METHOD = "nextgenas.generateMethod";

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
		if(symbol.containerName !== symbolToFind.containerName)
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

function findCompletionItemOfKind(name: string, kind: vscode.CompletionItemKind, items: vscode.CompletionItem[]): vscode.CompletionItem
{
	return items.find((item: vscode.CompletionItem) =>
	{
		return item.label === name && item.kind === kind;
	});
}

function findCompletionItem(name: string, items: vscode.CompletionItem[]): vscode.CompletionItem
{
	return items.find((item: vscode.CompletionItem) =>
	{
		return item.label === name;
	});
}

function containsCompletionItemsOtherThanTextOrSnippet(items: vscode.CompletionItem[]): boolean
{
	return items.some((item: vscode.CompletionItem) =>
	{
		//vscode can automatically provide Text items based on other text in the editor
		return item.kind !== vscode.CompletionItemKind.Text &&
			//vscode simply include snippets everywhere in the file, without restriction
			item.kind !== vscode.CompletionItemKind.Snippet;
	})
}

function findImportCommandForType(qualifiedName: string, codeActions: vscode.Command[])
{
	for(let i = 0, count = codeActions.length; i < count; i++)
	{
		let codeAction = codeActions[i];
		if(codeAction.command === COMMAND_ADD_IMPORT)
		{
			if(codeAction.arguments[0] === qualifiedName)
			{
				return codeAction;
			}
		}
	}
	return null;
}

suite("ActionScript & MXML extension: Application workspace", () =>
{
	test("vscode.extensions.getExtension(), isActive, and isLanguageClientReady", (done) =>
	{
		let extension = vscode.extensions.getExtension(EXTENSION_ID);
		assert.ok(extension, `Extension "${EXTENSION_ID}" not found!`);
		//wait a bit for the the extension to fully activate because we need
		//the project to be fully loaded into the compiler for future tests
		setTimeout(() =>
		{
			assert.ok(extension.isActive, `Extension "${EXTENSION_ID}" not active!`);
			assert.ok(extension.exports.isLanguageClientReady, `Extension "${EXTENSION_ID}" language client not ready!`);
			done();
		}, 6500);
	});
});

suite("document symbol provider: Application workspace", () =>
{
	test("vscode.executeDocumentSymbolProvider not empty", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let classQualifiedName = "Main";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							classQualifiedName,
							vscode.SymbolKind.Class,
							createRange(2, 14),
							uri,
							"No Package")),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for class: " + classQualifiedName);
					}, (err) =>
					{
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeDocumentSymbolProvider includes constructor", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let classQualifiedName = "Main";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							classQualifiedName,
							vscode.SymbolKind.Constructor,
							createRange(16, 18),
							uri,
							"Main")),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for constructor: " + classQualifiedName);
					}, (err) =>
					{
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeDocumentSymbolProvider includes member variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let memberVarName = "memberVar";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							memberVarName,
							vscode.SymbolKind.Field,
							createRange(21, 13),
							uri,
							"Main")),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for member variable: " + memberVarName);
					}, (err) =>
					{
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeDocumentSymbolProvider includes member function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let memberFunctionName = "memberFunction";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							memberFunctionName,
							vscode.SymbolKind.Method,
							createRange(23, 19),
							uri,
							"Main")),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for member function: " + memberFunctionName);
					}, (err) =>
					{
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeDocumentSymbolProvider includes member property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let memberPropertyName = "memberProperty";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							memberPropertyName,
							vscode.SymbolKind.Property,
							createRange(27, 22),
							uri,
							"Main")),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for member variable: " + memberPropertyName);
					}, (err) =>
					{
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeDocumentSymbolProvider includes static variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let staticVarName = "staticVar";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							staticVarName,
							vscode.SymbolKind.Field,
							createRange(4, 21),
							uri,
							"Main")),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for static variable: " + staticVarName);
					}, (err) =>
					{
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeDocumentSymbolProvider includes static constant", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let staticConstName = "STATIC_CONST";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							staticConstName,
							vscode.SymbolKind.Constant,
							createRange(5, 22),
							uri,
							"Main")),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for static constant: " + staticConstName);
					}, (err) =>
					{
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeDocumentSymbolProvider includes static function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let staticFunctionName = "staticFunction";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							staticFunctionName,
							vscode.SymbolKind.Method,
							createRange(7, 28),
							uri,
							"Main")),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for static function: " + staticFunctionName);
					}, (err) =>
					{
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeDocumentSymbolProvider includes static property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let staticPropertyName = "staticProperty";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							staticPropertyName,
							vscode.SymbolKind.Property,
							createRange(11, 29),
							uri,
							"Main")),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for static variable: " + staticPropertyName);
					}, (err) =>
					{
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeDocumentSymbolProvider includes internal class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let internalClassQualifiedName = "MainInternalClass";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							internalClassQualifiedName,
							vscode.SymbolKind.Class,
							createRange(34, 6),
							uri,
							"No Package")),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for internal class: " + internalClassQualifiedName);
					}, (err) =>
					{
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeDocumentSymbolProvider includes member variable in internal class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let memberVarName = "internalClassMemberVar";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							memberVarName,
							vscode.SymbolKind.Field,
							createRange(36, 13),
							uri,
							"MainInternalClass")),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for member variable in internal class: " + memberVarName);
					}, (err) =>
					{
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
});

suite("workspace symbol provider: Application workspace", () =>
{
	test("vscode.executeWorkspaceSymbolProvider includes class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		let query = "Main";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Class,
							createRange(2, 14),
							uri,
							"No Package")),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for class: " + query);
					}, (err) =>
					{
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes constructor", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		let query = "Main";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Constructor,
							createRange(16, 18),
							uri,
							"Main")),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for constructor: " + query);
					}, (err) =>
					{
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes class in package", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		let query = "PackageClass";
		let packageName = "com.example";
		let classUri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "PackageClass.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Class,
							createRange(2, 14),
							classUri,
							packageName)),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for class: " + query);
					}, (err) =>
					{
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes member variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		let query = "memberVar";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Field,
							createRange(21, 13),
							uri,
							"Main")),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for member variable: " + query);
					}, (err) =>
					{
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes member function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		let query = "memberFunction";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Method,
							createRange(23, 19),
							uri,
							"Main")),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for member function: " + query);
					}, (err) =>
					{
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes member property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		let query = "memberProperty";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Property,
							createRange(27, 22),
							uri,
							"Main")),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for member property: " + query);
					}, (err) =>
					{
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes static variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		let query = "staticVar";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Field,
							createRange(4, 21),
							uri,
							"Main")),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for static variable: " + query);
					}, (err) =>
					{
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes static constant", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		let query = "STATIC_CONST";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Constant,
							createRange(5, 22),
							uri,
							"Main")),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for static constant: " + query);
					}, (err) =>
					{
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes static function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		let query = "staticFunction";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Method,
							createRange(7, 28),
							uri,
							"Main")),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for static function: " + query);
					}, (err) =>
					{
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes static property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		let query = "staticProperty";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Property,
							createRange(11, 29),
							uri,
							"Main")),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for static property: " + query);
					}, (err) =>
					{
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes internal class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		let query = "MainInternalClass";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Class,
							createRange(34, 6),
							uri,
							"No Package")),
							"vscode.executeWorkspaceSymbolProvider did not provide internal class");
							
					}, (err) =>
					{
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes member variable in internal class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		let query = "internalClassMemberVar";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							query,
							vscode.SymbolKind.Field,
							createRange(36, 13),
							uri,
							"MainInternalClass")),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for member variable in internal class: " + query);
					}, (err) =>
					{
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes symbols in unreferenced files", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		let query = "Unreferenced";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let qualifiedClassName = "com.example.UnreferencedClass";
			let classUri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "UnreferencedClass.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		let query = "Unreferenced";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let symbolName = "UnreferencedClass";
			let packageName = "com.example";
			let classUri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "UnreferencedClass.as"));
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(symbolName,
							vscode.SymbolKind.Class,
							createRange(2, 14),
							classUri,
							packageName)),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for class in unreferenced file with query: " + query);
					}, (err) =>
					{
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes constructor in unreferenced file", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		let query = "Unreferenced";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let symbolName = "UnreferencedClass";
			let packageName = "com.example";
			let classUri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "UnreferencedClass.as"));
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(symbolName,
							vscode.SymbolKind.Constructor,
							createRange(4, 18),
							classUri,
							packageName)),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for constructor in unreferenced file with query: " + query);
					}, (err) =>
					{
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
	test("vscode.executeWorkspaceSymbolProvider includes interface in unreferenced file", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Main.as"));
		let query = "Unreferenced";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let symbolName = "UnreferencedInterface";
			let packageName = "com.example.core";
			let interfaceUri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "core", "UnreferencedInterface.as"));
			return vscode.commands.executeCommand("vscode.executeWorkspaceSymbolProvider", query)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(symbolName,
							vscode.SymbolKind.Interface,
							createRange(2, 18),
							interfaceUri,
							packageName)),
							"vscode.executeWorkspaceSymbolProvider failed to provide symbol for interface in unreferenced file with query: " + query);
					}, (err) =>
					{
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
});

suite("signature help provider: Application workspace", () =>
{
	test("vscode.executeSignatureHelpProvider provides help for local function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "SignatureHelp.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "SignatureHelp.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "SignatureHelp.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "SignatureHelp.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "SignatureHelp.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "SignatureHelp.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "SignatureHelp.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "SignatureHelp.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "SignatureHelp.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "SignatureHelp.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "SignatureHelp.as"));
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
	test("vscode.executeSignatureHelpProvider provides help for constructor of new instance", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "SignatureHelp.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeSignatureHelpProvider",
				uri, new vscode.Position(30, 25), "(")
				.then((signatureHelp: vscode.SignatureHelp) =>
					{
						assert.strictEqual(signatureHelp.signatures.length, 1,
							"Signature help not provided for constructor of new instance");
						assert.strictEqual(signatureHelp.activeSignature, 0,
							"Active signature incorrect for constructor of new instance");
						assert.strictEqual(signatureHelp.activeParameter, 0,
							"Active parameter incorrect for constructor of new instance");
					}, (err) =>
					{
						assert(false, "Failed to execute workspace symbol provider: " + uri);
					});
		});
	});
});

suite("definition provider: Application workspace", () =>
{
	test("vscode.executeDefinitionProvider finds definition of local variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "packageFunction.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "packageFunction.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "packageVar.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "packageVar.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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

suite("type definition provider: Application workspace", () =>
{
	test("vscode.executeTypeDefinitionProvider finds type definition of local variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "TypeDefinitions.as"));
		let expected = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "typeDefinition", "LocalVarTypeDefinition.as"));
		let position = new vscode.Position(14, 10);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeTypeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeTypeDefinitionProvider failed to provide location of local variable definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, expected.path, "vscode.executeTypeDefinitionProvider provided incorrect uri for local variable definition");
						assert.strictEqual(location.range.start.line, 2, "vscode.executeTypeDefinitionProvider provided incorrect line for local variable definition");
						assert.strictEqual(location.range.start.character, 14, "vscode.executeTypeDefinitionProvider provided incorrect character for local variable definition");
					}, (err) =>
					{
						assert(false, "Failed to execute type definition provider: " + uri);
					});
		});
	});
	test("vscode.executeTypeDefinitionProvider finds type definition of member variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "TypeDefinitions.as"));
		let expected = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "typeDefinition", "MemberVarTypeDefinition.as"));
		let position = new vscode.Position(10, 14);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeTypeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeTypeDefinitionProvider failed to provide location of member variable definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, expected.path, "vscode.executeTypeDefinitionProvider provided incorrect uri for member variable definition");
						assert.strictEqual(location.range.start.line, 2, "vscode.executeTypeDefinitionProvider provided incorrect line for member variable definition");
						assert.strictEqual(location.range.start.character, 14, "vscode.executeTypeDefinitionProvider provided incorrect character for member variable definition");
					}, (err) =>
					{
						assert(false, "Failed to execute type definition provider: " + uri);
					});
		});
	});
	test("vscode.executeTypeDefinitionProvider finds type definition of static variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "TypeDefinitions.as"));
		let expected = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "typeDefinition", "StaticVarTypeDefinition.as"));
		let position = new vscode.Position(9, 22);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeTypeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeTypeDefinitionProvider failed to provide location of static variable definition: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, expected.path, "vscode.executeTypeDefinitionProvider provided incorrect uri for static variable definition");
						assert.strictEqual(location.range.start.line, 2, "vscode.executeTypeDefinitionProvider provided incorrect line for static variable definition");
						assert.strictEqual(location.range.start.character, 14, "vscode.executeTypeDefinitionProvider provided incorrect character for static variable definition");
					}, (err) =>
					{
						assert(false, "Failed to execute type definition provider: " + uri);
					});
		});
	});
	test("vscode.executeTypeDefinitionProvider finds type definition of parameter", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "TypeDefinitions.as"));
		let expected = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "typeDefinition", "ParameterTypeDefinition.as"));
		let position = new vscode.Position(12, 34);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeTypeDefinitionProvider", uri, position)
				.then((locations: vscode.Location[]) =>
					{
						assert.strictEqual(locations.length, 1,
							"vscode.executeTypeDefinitionProvider failed to provide location of parameter: " + uri);
						let location = locations[0];
						assert.strictEqual(location.uri.path, expected.path, "vscode.executeTypeDefinitionProvider provided incorrect uri for parameter definition");
						assert.strictEqual(location.range.start.line, 2, "vscode.executeTypeDefinitionProvider provided incorrect line for parameter definition");
						assert.strictEqual(location.range.start.character, 14, "vscode.executeTypeDefinitionProvider provided incorrect character for parameter definition");
					}, (err) =>
					{
						assert(false, "Failed to execute type definition provider: " + uri);
					});
		});
	});
});

suite("hover provider: Application workspace", () =>
{
	test("vscode.executeHoverProvider displays hover for local variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "packageFunction.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "packageFunction.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "packageVar.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "packageVar.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
		let definitionURI = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "SuperDefinitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Definitions.as"));
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

suite("completion item provider: Application workspace", () =>
{
	test("vscode.executeCompletionItemProvider includes local variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 3);
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
	test("vscode.executeCompletionItemProvider omits local variable with member access operator on class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(49, 14);
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
	test("vscode.executeCompletionItemProvider omits local variable with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 8);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 9);
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
	test("vscode.executeCompletionItemProvider omits local variable with member access operator on fully-qualified package", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(51, 15);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 3);
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
	test("vscode.executeCompletionItemProvider omits local function with member access operator on class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(49, 14);
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
	test("vscode.executeCompletionItemProvider omits local function with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 8);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 9);
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
	test("vscode.executeCompletionItemProvider omits local function with member access operator on fully-qualified package", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(51, 51);
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
	test("vscode.executeCompletionItemProvider omits local function from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberVarItem = findCompletionItem("memberVar", items);
						assert.notEqual(memberVarItem, null, "vscode.executeCompletionItemProvider failed to provide member variable: " + uri);
						assert.strictEqual(memberVarItem.kind, vscode.CompletionItemKind.Field, "vscode.executeCompletionItemProvider failed to provide correct kind of member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits member variable with member access operator on class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(49, 14);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberVarItem = findCompletionItem("memberVar", items);
						assert.equal(memberVarItem, null, "vscode.executeCompletionItemProvider failed to omit member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes member variable with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberVarItem = findCompletionItem("memberVar", items);
						assert.notEqual(memberVarItem, null, "vscode.executeCompletionItemProvider failed to provide member variable: " + uri);
						assert.strictEqual(memberVarItem.kind, vscode.CompletionItemKind.Field, "vscode.executeCompletionItemProvider failed to provide correct kind of member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits member variable with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberVarItem = findCompletionItem("memberVar", items);
						assert.equal(memberVarItem, null, "vscode.executeCompletionItemProvider failed to omit member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits member variable with member access operator on fully-qualified package", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(51, 15);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberVarItem = findCompletionItem("memberVar", items);
						assert.equal(memberVarItem, null, "vscode.executeCompletionItemProvider failed to omit member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits member variable from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberFunctionItem = findCompletionItem("memberFunction", items);
						assert.notEqual(memberFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide member function: " + uri);
						assert.strictEqual(memberFunctionItem.kind, vscode.CompletionItemKind.Method, "vscode.executeCompletionItemProvider failed to provide correct kind of member function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits member function with member access operator on class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(49, 14);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberFunctionItem = findCompletionItem("memberFunction", items);
						assert.equal(memberFunctionItem, null, "vscode.executeCompletionItemProvider failed to omit member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes member function with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberFunctionItem = findCompletionItem("memberFunction", items);
						assert.notEqual(memberFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide member function: " + uri);
						assert.strictEqual(memberFunctionItem.kind, vscode.CompletionItemKind.Method, "vscode.executeCompletionItemProvider failed to provide correct kind of member function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits member function with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberFunctionItem = findCompletionItem("memberFunction", items);
						assert.equal(memberFunctionItem, null, "vscode.executeCompletionItemProvider failed to omit member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits member function with member access operator on fully-qualified package", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(51, 15);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberFunctionItem = findCompletionItem("memberFunction", items);
						assert.equal(memberFunctionItem, null, "vscode.executeCompletionItemProvider failed to omit member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits member function from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberFunctionItem = findCompletionItem("memberFunction", items);
						assert.equal(memberFunctionItem, null, "vscode.executeCompletionItemProvider failed to omit member function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes member property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberPropItem = findCompletionItem("memberProperty", items);
						assert.notEqual(memberPropItem, null, "vscode.executeCompletionItemProvider failed to provide member property: " + uri);
						assert.strictEqual(memberPropItem.kind, vscode.CompletionItemKind.Property, "vscode.executeCompletionItemProvider failed to provide correct kind of member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits member property with member access operator on class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(49, 14);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberPropItem = findCompletionItem("memberProperty", items);
						assert.equal(memberPropItem, null, "vscode.executeCompletionItemProvider failed to omit member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes member property with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberPropItem = findCompletionItem("memberProperty", items);
						assert.notEqual(memberPropItem, null, "vscode.executeCompletionItemProvider failed to provide member property: " + uri);
						assert.strictEqual(memberPropItem.kind, vscode.CompletionItemKind.Property, "vscode.executeCompletionItemProvider failed to provide correct kind of member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits member property with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberPropItem = findCompletionItem("memberProperty", items);
						assert.equal(memberPropItem, null, "vscode.executeCompletionItemProvider failed to omit member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits member property with member access operator on fully-qualified package", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(51, 15);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberPropItem = findCompletionItem("memberProperty", items);
						assert.equal(memberPropItem, null, "vscode.executeCompletionItemProvider failed to omit member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits member property from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let memberPropItem = findCompletionItem("memberProperty", items);
						assert.equal(memberPropItem, null, "vscode.executeCompletionItemProvider failed to omit member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes static variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let staticVarItem = findCompletionItem("staticVar", items);
						assert.notEqual(staticVarItem, null, "vscode.executeCompletionItemProvider failed to provide static variable: " + uri);
						assert.strictEqual(staticVarItem.kind, vscode.CompletionItemKind.Field, "vscode.executeCompletionItemProvider failed to provide correct kind of static variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes static variable with member access operator on class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(49, 14);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let staticVarItem = findCompletionItem("staticVar", items);
						assert.notEqual(staticVarItem, null, "vscode.executeCompletionItemProvider failed to provide static variable: " + uri);
						assert.strictEqual(staticVarItem.kind, vscode.CompletionItemKind.Field, "vscode.executeCompletionItemProvider failed to provide correct kind of static variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits static variable with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 8);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
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
	test("vscode.executeCompletionItemProvider omits static variable with member access operator on fully-qualified package", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(51, 15);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
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
	test("vscode.executeCompletionItemProvider omits static variable from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
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
	test("vscode.executeCompletionItemProvider includes static function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let staticFunctionItem = findCompletionItem("staticFunction", items);
						assert.notEqual(staticFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide static function: " + uri);
						assert.strictEqual(staticFunctionItem.kind, vscode.CompletionItemKind.Method, "vscode.executeCompletionItemProvider failed to provide correct kind of static function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes static function with member access operator on class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(49, 14);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let staticFunctionItem = findCompletionItem("staticFunction", items);
						assert.notEqual(staticFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide static function: " + uri);
						assert.strictEqual(staticFunctionItem.kind, vscode.CompletionItemKind.Method, "vscode.executeCompletionItemProvider failed to provide correct kind of static function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits static function with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 8);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
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
	test("vscode.executeCompletionItemProvider omits static function with member access operator on fully-qualified package", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(51, 15);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
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
	test("vscode.executeCompletionItemProvider omits static function from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
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
	test("vscode.executeCompletionItemProvider includes static property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let staticPropItem = findCompletionItem("staticProperty", items);
						assert.notEqual(staticPropItem, null, "vscode.executeCompletionItemProvider failed to provide static property: " + uri);
						assert.strictEqual(staticPropItem.kind, vscode.CompletionItemKind.Property, "vscode.executeCompletionItemProvider failed to provide correct kind of static property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes static property with member access operator on class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(49, 14);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let staticPropItem = findCompletionItem("staticProperty", items);
						assert.notEqual(staticPropItem, null, "vscode.executeCompletionItemProvider failed to provide static property: " + uri);
						assert.strictEqual(staticPropItem.kind, vscode.CompletionItemKind.Property, "vscode.executeCompletionItemProvider failed to provide correct kind of static property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits static property with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 8);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
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
	test("vscode.executeCompletionItemProvider omits static property with member access operator on fully-qualified package", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(51, 15);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
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
	test("vscode.executeCompletionItemProvider omits static property from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
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
	test("vscode.executeCompletionItemProvider includes package class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 3);
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
	test("vscode.executeCompletionItemProvider includes package class with member access on fully-qualified package", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(50, 15);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let classItem = findCompletionItem("UnreferencedClass", items);
						assert.notEqual(classItem, null, "vscode.executeCompletionItemProvider failed to provide package class: " + uri);
						assert.strictEqual(classItem.kind, vscode.CompletionItemKind.Class, "vscode.executeCompletionItemProvider failed to provide correct kind of package class: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits package class with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localVarItem = findCompletionItem("UnreferencedClass", items);
						assert.equal(localVarItem, null, "vscode.executeCompletionItemProvider failed to omit package class: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits package class with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localVarItem = findCompletionItem("UnreferencedClass", items);
						assert.equal(localVarItem, null, "vscode.executeCompletionItemProvider failed to omit package class: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes package class as type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 3);
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
	test("vscode.executeCompletionItemProvider includes package variable with member access on fully-qualified package", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
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
	});
	test("vscode.executeCompletionItemProvider omits package variable with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 8);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 9);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 3);
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
	test("vscode.executeCompletionItemProvider includes package function with member access on fully-qualified package", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
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
	});
	test("vscode.executeCompletionItemProvider omits package function with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 8);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 9);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("superMemberVar", items);
						assert.notEqual(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to provide super member variable: " + uri);
						assert.strictEqual(superMemberVarItem.kind, vscode.CompletionItemKind.Field, "vscode.executeCompletionItemProvider failed to provide correct kind of super member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super member variable with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("superMemberVar", items);
						assert.notEqual(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to provide super member variable: " + uri);
						assert.strictEqual(superMemberVarItem.kind, vscode.CompletionItemKind.Field, "vscode.executeCompletionItemProvider failed to provide correct kind of super member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits super member variable from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberVarItem = findCompletionItem("superMemberVar", items);
						assert.notEqual(superMemberVarItem, null, "vscode.executeCompletionItemProvider failed to provide member super variable: " + uri);
						assert.strictEqual(superMemberVarItem.kind, vscode.CompletionItemKind.Field, "vscode.executeCompletionItemProvider failed to provide correct kind of super member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super member property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberPropertyItem = findCompletionItem("superMemberProperty", items);
						assert.notEqual(superMemberPropertyItem, null, "vscode.executeCompletionItemProvider failed to provide super member property: " + uri);
						assert.strictEqual(superMemberPropertyItem.kind, vscode.CompletionItemKind.Property, "vscode.executeCompletionItemProvider failed to provide correct kind of super member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super member property with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberPropertyItem = findCompletionItem("superMemberProperty", items);
						assert.notEqual(superMemberPropertyItem, null, "vscode.executeCompletionItemProvider failed to provide super member property: " + uri);
						assert.strictEqual(superMemberPropertyItem.kind, vscode.CompletionItemKind.Property, "vscode.executeCompletionItemProvider failed to provide correct kind of super member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super member property with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberPropertyItem = findCompletionItem("superMemberProperty", items);
						assert.notEqual(superMemberPropertyItem, null, "vscode.executeCompletionItemProvider failed to provide super member property: " + uri);
						assert.strictEqual(superMemberPropertyItem.kind, vscode.CompletionItemKind.Property, "vscode.executeCompletionItemProvider failed to provide correct kind of super member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits super member property from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberFunctionItem = findCompletionItem("superMemberFunction", items);
						assert.notEqual(superMemberFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide super member function: " + uri);
						assert.strictEqual(superMemberFunctionItem.kind, vscode.CompletionItemKind.Method, "vscode.executeCompletionItemProvider failed to provide correct kind of super member function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super member function with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberFunctionItem = findCompletionItem("superMemberFunction", items);
						assert.notEqual(superMemberFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide super member function: " + uri);
						assert.strictEqual(superMemberFunctionItem.kind, vscode.CompletionItemKind.Method, "vscode.executeCompletionItemProvider failed to provide correct kind of super member function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super member function with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superMemberFunctionItem = findCompletionItem("superMemberFunction", items);
						assert.notEqual(superMemberFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide super member function: " + uri);
						assert.strictEqual(superMemberFunctionItem.kind, vscode.CompletionItemKind.Method, "vscode.executeCompletionItemProvider failed to provide correct kind of super member function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits super member function from type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superStaticVarItem = findCompletionItem("superStaticVar", items);
						assert.notEqual(superStaticVarItem, null, "vscode.executeCompletionItemProvider failed to provide super static variable: " + uri);
						assert.strictEqual(superStaticVarItem.kind, vscode.CompletionItemKind.Field, "vscode.executeCompletionItemProvider failed to provide correct kind of super static variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits super static variable with member access operator on class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(49, 15);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(50, 20);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superStaticVarItem = findCompletionItem("superStaticVar", items);
						assert.notEqual(superStaticVarItem, null, "vscode.executeCompletionItemProvider failed to provide super static variable: " + uri);
						assert.strictEqual(superStaticVarItem.kind, vscode.CompletionItemKind.Field, "vscode.executeCompletionItemProvider failed to provide correct kind of super static variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super static variable with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 8);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 9);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superStaticPropertyItem = findCompletionItem("superStaticProperty", items);
						assert.notEqual(superStaticPropertyItem, null, "vscode.executeCompletionItemProvider failed to provide super static property: " + uri);
						assert.strictEqual(superStaticPropertyItem.kind, vscode.CompletionItemKind.Property, "vscode.executeCompletionItemProvider failed to provide correct kind of super static property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits super static property with member access operator on class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(49, 15);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(50, 20);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superStaticPropertyItem = findCompletionItem("superStaticProperty", items);
						assert.notEqual(superStaticPropertyItem, null, "vscode.executeCompletionItemProvider failed to provide super static property: " + uri);
						assert.strictEqual(superStaticPropertyItem.kind, vscode.CompletionItemKind.Property, "vscode.executeCompletionItemProvider failed to provide correct kind of super static property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super static property with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 8);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 9);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 3);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superStaticFunctionItem = findCompletionItem("superStaticFunction", items);
						assert.notEqual(superStaticFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide super static function: " + uri);
						assert.strictEqual(superStaticFunctionItem.kind, vscode.CompletionItemKind.Method, "vscode.executeCompletionItemProvider failed to provide correct kind of super static function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits super static function with member access operator on class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(49, 15);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(50, 20);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider",
				uri, position, ".")
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let superStaticFunctionItem = findCompletionItem("superStaticFunction", items);
						assert.notEqual(superStaticFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide super static function: " + uri);
						assert.strictEqual(superStaticFunctionItem.kind, vscode.CompletionItemKind.Method, "vscode.executeCompletionItemProvider failed to provide correct kind of super static function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes super static function with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 8);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 9);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 3);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 8);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 3);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 8);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(46, 3);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(53, 12);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let fileInternalMemberVarItem = findCompletionItem("fileInternalMemberVar", items);
						assert.notEqual(fileInternalMemberVarItem, null, "vscode.executeCompletionItemProvider failed to provide file-internal member variable: " + uri);
						assert.strictEqual(fileInternalMemberVarItem.kind, vscode.CompletionItemKind.Field, "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal member variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes file-internal member function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(53, 12);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let fileInternalMemberFunctionItem = findCompletionItem("fileInternalMemberFunction", items);
						assert.notEqual(fileInternalMemberFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide file-internal member function: " + uri);
						assert.strictEqual(fileInternalMemberFunctionItem.kind, vscode.CompletionItemKind.Method, "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal member function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes file-internal member property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(53, 12);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let fileInternalMemberPropertyItem = findCompletionItem("fileInternalMemberProperty", items);
						assert.notEqual(fileInternalMemberPropertyItem, null, "vscode.executeCompletionItemProvider failed to provide file-internal member property: " + uri);
						assert.strictEqual(fileInternalMemberPropertyItem.kind, vscode.CompletionItemKind.Property, "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes file-internal static variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 26);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let fileInternalStaticVarItem = findCompletionItem("fileInternalStaticVar", items);
						assert.notEqual(fileInternalStaticVarItem, null, "vscode.executeCompletionItemProvider failed to provide file-internal static variable: " + uri);
						assert.strictEqual(fileInternalStaticVarItem.kind, vscode.CompletionItemKind.Field, "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal static variable: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes file-internal static function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 26);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let fileInternalStaticFunctionItem = findCompletionItem("fileInternalStaticFunction", items);
						assert.notEqual(fileInternalStaticFunctionItem, null, "vscode.executeCompletionItemProvider failed to provide file-internal static function: " + uri);
						assert.strictEqual(fileInternalStaticFunctionItem.kind, vscode.CompletionItemKind.Method, "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal static function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes file-internal static property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 26);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let fileInternalStaticPropertyItem = findCompletionItem("fileInternalStaticProperty", items);
						assert.notEqual(fileInternalStaticPropertyItem, null, "vscode.executeCompletionItemProvider failed to provide file-internal static property: " + uri);
						assert.strictEqual(fileInternalStaticPropertyItem.kind, vscode.CompletionItemKind.Property, "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal static property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits protected member variable not in superclass", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(53, 12);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(53, 12);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(53, 12);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 26);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 26);
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(54, 26);
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
	test("vscode.executeCompletionItemProvider includes class on left side of member access for static constant", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(56, 21);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let classItem = findCompletionItem("ClassWithConstants", items);
						assert.notEqual(classItem, null, "vscode.executeCompletionItemProvider failed to provide class: " + uri);
						assert.strictEqual(classItem.kind, vscode.CompletionItemKind.Class, "vscode.executeCompletionItemProvider failed to provide correct kind of class: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes package interface with member access on fully-qualified package", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(50, 15);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let interfaceItem = findCompletionItem("IPackageInterface", items);
						assert.notEqual(interfaceItem, null, "vscode.executeCompletionItemProvider failed to provide package interface: " + uri);
						assert.strictEqual(interfaceItem.kind, vscode.CompletionItemKind.Interface, "vscode.executeCompletionItemProvider failed to provide correct kind of package interface: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits package interface with member access operator on this", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(47, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localVarItem = findCompletionItem("IPackageInterface", items);
						assert.equal(localVarItem, null, "vscode.executeCompletionItemProvider failed to omit package interface: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits package interface with member access operator on super", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(48, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let localVarItem = findCompletionItem("IPackageInterface", items);
						assert.equal(localVarItem, null, "vscode.executeCompletionItemProvider failed to omit package interface: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes package interface as type annotation", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(55, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let packageInterfaceItem = findCompletionItem("IPackageInterface", items);
						assert.notEqual(packageInterfaceItem, null, "vscode.executeCompletionItemProvider failed to provide package interface: " + uri);
						assert.strictEqual(packageInterfaceItem.kind, vscode.CompletionItemKind.Interface, "vscode.executeCompletionItemProvider failed to provide correct kind of package interface: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes interface member property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(58, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let propertyItem = findCompletionItem("memberProperty", items);
						assert.notEqual(propertyItem, null, "vscode.executeCompletionItemProvider failed to provide member property: " + uri);
						assert.strictEqual(propertyItem.kind, vscode.CompletionItemKind.Property, "vscode.executeCompletionItemProvider failed to provide correct kind of member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes interface member function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(58, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let functionItem = findCompletionItem("memberFunction", items);
						assert.notEqual(functionItem, null, "vscode.executeCompletionItemProvider failed to provide member function: " + uri);
						assert.strictEqual(functionItem.kind, vscode.CompletionItemKind.Method, "vscode.executeCompletionItemProvider failed to provide correct kind of member function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes interface super member property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(58, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let propertyItem = findCompletionItem("superMemberProperty", items);
						assert.notEqual(propertyItem, null, "vscode.executeCompletionItemProvider failed to provide super member property: " + uri);
						assert.strictEqual(propertyItem.kind, vscode.CompletionItemKind.Property, "vscode.executeCompletionItemProvider failed to provide correct kind of super member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes interface super member function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(58, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let functionItem = findCompletionItem("superMemberFunction", items);
						assert.notEqual(functionItem, null, "vscode.executeCompletionItemProvider failed to provide super member function: " + uri);
						assert.strictEqual(functionItem.kind, vscode.CompletionItemKind.Method, "vscode.executeCompletionItemProvider failed to provide correct kind of super member function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes interface super super member property", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(58, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let propertyItem = findCompletionItem("superSuperMemberProperty", items);
						assert.notEqual(propertyItem, null, "vscode.executeCompletionItemProvider failed to provide super super member property: " + uri);
						assert.strictEqual(propertyItem.kind, vscode.CompletionItemKind.Property, "vscode.executeCompletionItemProvider failed to provide correct kind of super super member property: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes interface super super member function", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(58, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let functionItem = findCompletionItem("superSuperMemberFunction", items);
						assert.notEqual(functionItem, null, "vscode.executeCompletionItemProvider failed to provide super super member function: " + uri);
						assert.strictEqual(functionItem.kind, vscode.CompletionItemKind.Method, "vscode.executeCompletionItemProvider failed to provide correct kind of super super member function: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider empty inside string literal", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(59, 33);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						assert.ok(!containsCompletionItemsOtherThanTextOrSnippet(list.items),
							"vscode.executeCompletionItemProvider incorrectly provides items inside a string literal");
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider empty inside RegExp literal", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(60, 33);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						assert.ok(!containsCompletionItemsOtherThanTextOrSnippet(list.items),
							"vscode.executeCompletionItemProvider incorrectly provides items inside a RegExp literal");
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes package name for package block", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "PackageCompletion.as"));
		let position = new vscode.Position(0, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let packageItem = findCompletionItem("com.example", items);
						assert.notEqual(packageItem, null, "vscode.executeCompletionItemProvider failed to provide package name: " + uri);
						assert.strictEqual(packageItem.kind, vscode.CompletionItemKind.Module, "vscode.executeCompletionItemProvider failed to provide correct kind of package name: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes package name at end of package keyword", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "PackageCompletion4.as"));
		let position = new vscode.Position(0, 7);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let packageItem = findCompletionItem("package com.example {}", items);
						assert.notEqual(packageItem, null, "vscode.executeCompletionItemProvider failed to provide package name: " + uri);
						assert.strictEqual(packageItem.kind, vscode.CompletionItemKind.Module, "vscode.executeCompletionItemProvider failed to provide correct kind of package name: " + uri);
						let snippet = packageItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "package com.example\n{\n\t$0\n}", "vscode.executeCompletionItemProvider failed to provide correct insert text for package name: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes package name for unfinished package block", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "PackageCompletion2.as"));
		let position = new vscode.Position(0, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let packageItem = findCompletionItem("com.example", items);
						assert.notEqual(packageItem, null, "vscode.executeCompletionItemProvider failed to provide package name: " + uri);
						assert.strictEqual(packageItem.kind, vscode.CompletionItemKind.Module, "vscode.executeCompletionItemProvider failed to provide correct kind of package name: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes package name for empty file", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "com", "example", "PackageCompletion3.as"));
		let position = new vscode.Position(0, 0);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let packageItem = findCompletionItem("package com.example {}", items);
						assert.notEqual(packageItem, null, "vscode.executeCompletionItemProvider failed to provide package name: " + uri);
						assert.strictEqual(packageItem.kind, vscode.CompletionItemKind.Module, "vscode.executeCompletionItemProvider failed to provide correct kind of package name: " + uri);
						let snippet = packageItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "package com.example\n{\n\t$0\n}", "vscode.executeCompletionItemProvider failed to provide correct insert text for package name: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes protected member function on protected override", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(63, 30);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let functionItem = findCompletionItem("superMemberFunction", items);
						assert.notEqual(functionItem, null, "vscode.executeCompletionItemProvider failed to provide protected member function on protected override " + uri);
						assert.strictEqual(functionItem.kind, vscode.CompletionItemKind.Method, "vscode.executeCompletionItemProvider failed to provide correct kind of override: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider excludes public member function on protected override", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Completion.as"));
		let position = new vscode.Position(63, 30);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let functionItem = findCompletionItem("superMemberFunction2", items);
						assert.equal(functionItem, null, "vscode.executeCompletionItemProvider incorrectly provided public member function on protected override: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
});

suite("MXML completion item provider: Application workspace", () =>
{
	test("vscode.executeCompletionItemProvider includes property as attribute", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(9, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let propertyItem = findCompletionItem("className", items);
						assert.notEqual(propertyItem, null, "vscode.executeCompletionItemProvider failed to provide property as attribute: " + uri);
						assert.strictEqual(propertyItem.kind, vscode.CompletionItemKind.Property, "vscode.executeCompletionItemProvider failed to provide correct kind of property: " + uri);
						let snippet = propertyItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "className=\"$0\"", "vscode.executeCompletionItemProvider failed to provide correct insert text for property as attribute: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes property as child element (after < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(10, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let propertyItem = findCompletionItem("className", items);
						assert.notEqual(propertyItem, null, "vscode.executeCompletionItemProvider failed to provide property as child element: " + uri);
						assert.strictEqual(propertyItem.kind, vscode.CompletionItemKind.Property, "vscode.executeCompletionItemProvider failed to provide correct kind of property: " + uri);
						let snippet = propertyItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "js:className>$0</js:className>", "vscode.executeCompletionItemProvider failed to provide correct insert text for property as child element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes property as child element (without < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(24, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let propertyItem = findCompletionItem("className", items);
						assert.notEqual(propertyItem, null, "vscode.executeCompletionItemProvider failed to provide property as child element: " + uri);
						assert.strictEqual(propertyItem.kind, vscode.CompletionItemKind.Property, "vscode.executeCompletionItemProvider failed to provide correct kind of property: " + uri);
						let snippet = propertyItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "<js:className>$0</js:className>", "vscode.executeCompletionItemProvider failed to provide correct insert text for property as child element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits property as attribute of closing element", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(11, 14);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let propertyItem = findCompletionItem("className", items);
						assert.equal(propertyItem, null, "vscode.executeCompletionItemProvider failed to omit property as attribute of closing element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes member variable as attribute", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(9, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let propertyItem = findCompletionItem("beads", items);
						assert.notEqual(propertyItem, null, "vscode.executeCompletionItemProvider failed to provide member variable as attribute: " + uri);
						assert.strictEqual(propertyItem.kind, vscode.CompletionItemKind.Field, "vscode.executeCompletionItemProvider failed to provide correct kind of member variable: " + uri);
						let snippet = propertyItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "beads=\"$0\"", "vscode.executeCompletionItemProvider failed to provide correct insert text for member variable as attribute: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes member variable as child element (after < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(10, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let propertyItem = findCompletionItem("beads", items);
						assert.notEqual(propertyItem, null, "vscode.executeCompletionItemProvider failed to provide member variable as child element: " + uri);
						assert.strictEqual(propertyItem.kind, vscode.CompletionItemKind.Field, "vscode.executeCompletionItemProvider failed to provide correct kind of member variable: " + uri);
						let snippet = propertyItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "js:beads>$0</js:beads>", "vscode.executeCompletionItemProvider failed to provide correct insert text for member variable as child element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes member variable as child element (without < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(24, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let propertyItem = findCompletionItem("beads", items);
						assert.notEqual(propertyItem, null, "vscode.executeCompletionItemProvider failed to provide member variable as child element: " + uri);
						assert.strictEqual(propertyItem.kind, vscode.CompletionItemKind.Field, "vscode.executeCompletionItemProvider failed to provide correct kind of member variable: " + uri);
						let snippet = propertyItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "<js:beads>$0</js:beads>", "vscode.executeCompletionItemProvider failed to provide correct insert text for member variable as child element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits member variable as attribute of closing element", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(11, 14);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let propertyItem = findCompletionItem("beads", items);
						assert.equal(propertyItem, null, "vscode.executeCompletionItemProvider failed to omit member variable as attribute of closing element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes event as attribute", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(9, 13);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let eventItem = findCompletionItem("click", items);
						assert.notEqual(eventItem, null, "vscode.executeCompletionItemProvider failed to provide event as attribute: " + uri);
						assert.strictEqual(eventItem.kind, vscode.CompletionItemKind.Event, "vscode.executeCompletionItemProvider failed to provide correct kind of event: " + uri);
						let snippet = eventItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "click=\"$0\"", "vscode.executeCompletionItemProvider failed to provide correct insert text for event as attribute: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes event as child element (after < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(10, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let eventItem = findCompletionItem("click", items);
						assert.notEqual(eventItem, null, "vscode.executeCompletionItemProvider failed to provide event as child element: " + uri);
						assert.strictEqual(eventItem.kind, vscode.CompletionItemKind.Event, "vscode.executeCompletionItemProvider failed to provide correct kind of event: " + uri);
						let snippet = eventItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "js:click>$0</js:click>", "vscode.executeCompletionItemProvider failed to provide correct insert text for event as child element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes event as child element (without < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(24, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let eventItem = findCompletionItem("click", items);
						assert.notEqual(eventItem, null, "vscode.executeCompletionItemProvider failed to provide event as child element: " + uri);
						assert.strictEqual(eventItem.kind, vscode.CompletionItemKind.Event, "vscode.executeCompletionItemProvider failed to provide correct kind of event: " + uri);
						let snippet = eventItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "<js:click>$0</js:click>", "vscode.executeCompletionItemProvider failed to provide correct insert text for event as child element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits event as attribute of closing element", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(11, 14);
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
	test("vscode.executeCompletionItemProvider includes class as child element (after < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(10, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let packageClassItem = findCompletionItem("example:UnreferencedClass", items);
						assert.notEqual(packageClassItem, null, "vscode.executeCompletionItemProvider failed to provide package class: " + uri);
						assert.strictEqual(packageClassItem.kind, vscode.CompletionItemKind.Class, "vscode.executeCompletionItemProvider failed to provide correct kind of package class: " + uri);
						assert.strictEqual(packageClassItem.sortText, "UnreferencedClass", "vscode.executeCompletionItemProvider failed to provide correct sort text for package class as child element: " + uri);
						assert.strictEqual(packageClassItem.filterText, "UnreferencedClass", "vscode.executeCompletionItemProvider failed to provide correct filter text for package class as child element: " + uri);
						assert.strictEqual(packageClassItem.insertText, "example:UnreferencedClass", "vscode.executeCompletionItemProvider failed to provide correct insert text for package class as child element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes class as child element (without < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(24, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let packageClassItem = findCompletionItem("example:UnreferencedClass", items);
						assert.notEqual(packageClassItem, null, "vscode.executeCompletionItemProvider failed to provide package class: " + uri);
						assert.strictEqual(packageClassItem.kind, vscode.CompletionItemKind.Class, "vscode.executeCompletionItemProvider failed to provide correct kind of package class: " + uri);
						assert.strictEqual(packageClassItem.sortText, "UnreferencedClass", "vscode.executeCompletionItemProvider failed to provide correct sort text for package class as child element: " + uri);
						assert.strictEqual(packageClassItem.filterText, "UnreferencedClass", "vscode.executeCompletionItemProvider failed to provide correct filter text for package class as child element: " + uri);
						assert.strictEqual(packageClassItem.insertText, "<example:UnreferencedClass", "vscode.executeCompletionItemProvider failed to provide correct insert text for package class as child element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Binding> as child element of root (after < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(12, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Binding", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Binding>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Binding", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Binding>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Binding", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Binding>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "fx:Binding>\n\t$0\n</fx:Binding>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Binding>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Binding> as child element of root (without < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(26, 4);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Binding", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Binding>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Binding", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Binding>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Binding", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Binding>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "<fx:Binding>\n\t$0\n</fx:Binding>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Binding>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits <fx:Binding> as child of non-root element (after < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(10, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Binding", vscode.CompletionItemKind.Keyword, items);
						assert.equal(mxmlItem, null, "vscode.executeCompletionItemProvider failed to omit <fx:Binding> in non-root element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits <fx:Binding> as child of non-root element (without < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(24, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Binding", vscode.CompletionItemKind.Keyword, items);
						assert.equal(mxmlItem, null, "vscode.executeCompletionItemProvider failed to omit <fx:Binding> in non-root element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Binding> as child element of root with existing prefix", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(20, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Binding", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Binding>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Binding", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Binding>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Binding", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Binding>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "Binding>\n\t$0\n</fx:Binding>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Binding>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits <fx:Binding> as child of non-root element with existing prefix", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(18, 12);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Binding", vscode.CompletionItemKind.Keyword, items);
						assert.equal(mxmlItem, null, "vscode.executeCompletionItemProvider failed to omit <fx:Binding> in non-root element existing prefix: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Component> as child element of root (after < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(12, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Component", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Component>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Component", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Component", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "fx:Component>\n\t$0\n</fx:Component>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Component>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Component> as child element of root (without < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(26, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Component", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Component>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Component", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Component", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "<fx:Component>\n\t$0\n</fx:Component>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Component>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Component> as child of non-root element (after < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(10, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Component", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Component>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Component", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Component", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "fx:Component>\n\t$0\n</fx:Component>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Component>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Component> as child of non-root element (without < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(24, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Component", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Component>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Component", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Component", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "<fx:Component>\n\t$0\n</fx:Component>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Component>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Component> as child element of root with existing prefix", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(20, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Component", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Component>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Component", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Component", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "Component>\n\t$0\n</fx:Component>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Component>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Component> as child of non-root element with existing prefix", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(18, 12);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Component", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Component>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Component", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Component", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "Component>\n\t$0\n</fx:Component>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Component>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Declarations> as child element of root (after < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(12, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Declarations", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Declarations>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Declarations", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Declarations>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Declarations", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Declarations>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "fx:Declarations>\n\t$0\n</fx:Declarations>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Declarations>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Declarations> as child element of root (without < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(26, 4);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Declarations", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Declarations>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Declarations", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Declarations>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Declarations", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Declarations>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "<fx:Declarations>\n\t$0\n</fx:Declarations>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Declarations>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits <fx:Declarations> as child of non-root element (after < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(10, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Declarations", vscode.CompletionItemKind.Keyword, items);
						assert.equal(mxmlItem, null, "vscode.executeCompletionItemProvider failed to omit <fx:Declarations> in non-root element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits <fx:Declarations> as child of non-root element (without < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(24, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Declarations", vscode.CompletionItemKind.Keyword, items);
						assert.equal(mxmlItem, null, "vscode.executeCompletionItemProvider failed to omit <fx:Declarations> in non-root element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Declarations> as child element of root with existing prefix", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(20, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Declarations", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Declarations>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Declarations", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Declarations>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Declarations", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Declarations>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "Declarations>\n\t$0\n</fx:Declarations>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Declarations>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits <fx:Declarations> as child of non-root element with existing prefix", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(18, 12);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Declarations", vscode.CompletionItemKind.Keyword, items);
						assert.equal(mxmlItem, null, "vscode.executeCompletionItemProvider failed to omit <fx:Declarations> in non-root element existing prefix: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Metadata> as child element of root (after < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(12, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Metadata", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Metadata>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Metadata", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Metadata>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Metadata", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Metadata>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "fx:Metadata>\n\t$0\n</fx:Metadata>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Metadata>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Metadata> as child element of root (without < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(26, 4);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Metadata", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Metadata>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Metadata", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Metadata>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Metadata", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Metadata>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "<fx:Metadata>\n\t$0\n</fx:Metadata>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Metadata>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits <fx:Metadata> as child of non-root element (after < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(10, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Metadata", vscode.CompletionItemKind.Keyword, items);
						assert.equal(mxmlItem, null, "vscode.executeCompletionItemProvider failed to omit <fx:Metadata> in non-root element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits <fx:Metadata> as child of non-root element (without < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(24, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Metadata", vscode.CompletionItemKind.Keyword, items);
						assert.equal(mxmlItem, null, "vscode.executeCompletionItemProvider failed to omit <fx:Metadata> in non-root element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Metadata> as child element of root with existing prefix", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(20, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Metadata", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Metadata>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Metadata", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Metadata>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Metadata", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Metadata>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "Metadata>\n\t$0\n</fx:Metadata>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Metadata>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits <fx:Metadata> as child of non-root element with existing prefix", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(18, 12);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Metadata", vscode.CompletionItemKind.Keyword, items);
						assert.equal(mxmlItem, null, "vscode.executeCompletionItemProvider failed to omit <fx:Metadata> in non-root element existing prefix: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Script> as child element of root (after < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(12, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Script", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Script>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Script", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Script>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Script", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Script>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "fx:Script>\n\t<![CDATA[\n\t\t$0\n\t]]>\n</fx:Script>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Script>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Script> as child element of root (without < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(26, 4);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Script", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Script>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Script", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Script>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Script", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Script>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "<fx:Script>\n\t<![CDATA[\n\t\t$0\n\t]]>\n</fx:Script>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Script>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits <fx:Script> as child of non-root element (after < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(10, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Script", vscode.CompletionItemKind.Keyword, items);
						assert.equal(mxmlItem, null, "vscode.executeCompletionItemProvider failed to omit <fx:Script> in non-root element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits <fx:Script> as child of non-root element (without < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(24, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Script", vscode.CompletionItemKind.Keyword, items);
						assert.equal(mxmlItem, null, "vscode.executeCompletionItemProvider failed to omit <fx:Script> in non-root element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Script> as child element of root with existing prefix", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(20, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Script", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Script>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Script", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Script>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Script", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Script>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "Script>\n\t<![CDATA[\n\t\t$0\n\t]]>\n</fx:Script>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Script>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits <fx:Script> as child of non-root element with existing prefix", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(18, 12);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Script", vscode.CompletionItemKind.Keyword, items);
						assert.equal(mxmlItem, null, "vscode.executeCompletionItemProvider failed to omit <fx:Script> in non-root element existing prefix: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Style> as child element of root (after < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(12, 5);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Style", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Style>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Style", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Style>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Style", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Style>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "fx:Style>\n\t$0\n</fx:Style>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Style>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Style> as child element of root (without < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(26, 4);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Style", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Style>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Style", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Style>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Style", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Style>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "<fx:Style>\n\t$0\n</fx:Style>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Style>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits <fx:Style> as child of non-root element (after < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(10, 9);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Style", vscode.CompletionItemKind.Keyword, items);
						assert.equal(mxmlItem, null, "vscode.executeCompletionItemProvider failed to omit <fx:Style> in non-root element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits <fx:Style> as child of non-root element (without < bracket)", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(24, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Style", vscode.CompletionItemKind.Keyword, items);
						assert.equal(mxmlItem, null, "vscode.executeCompletionItemProvider failed to omit <fx:Style> in non-root element: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes <fx:Style> as child element of root with existing prefix", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(20, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Style", vscode.CompletionItemKind.Keyword, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide <fx:Style>: " + uri);
						assert.strictEqual(mxmlItem.sortText, "Style", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Style>: " + uri);
						assert.strictEqual(mxmlItem.filterText, "Style", "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Style>: " + uri);
						let snippet = mxmlItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "Style>\n\t$0\n</fx:Style>", "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Style>: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits <fx:Style> as child of non-root element with existing prefix", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(18, 12);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("fx:Style", vscode.CompletionItemKind.Keyword, items);
						assert.equal(mxmlItem, null, "vscode.executeCompletionItemProvider failed to omit <fx:Style> in non-root element existing prefix: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes state for property attribute", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(21, 25);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let stateItem = findCompletionItemOfKind("stateOne", vscode.CompletionItemKind.Field, items);
						assert.notEqual(stateItem, null, "vscode.executeCompletionItemProvider failed to provide state for property attribute: " + uri);
						assert.strictEqual(stateItem.kind, vscode.CompletionItemKind.Field, "vscode.executeCompletionItemProvider failed to provide correct kind of state: " + uri);
						assert.strictEqual(stateItem.insertText, "stateOne", "vscode.executeCompletionItemProvider failed to provide correct insert text for state for property attribute: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes class in a package xmlns", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(22, 10);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let classItem = findCompletionItemOfKind("MXMLPackageNS", vscode.CompletionItemKind.Class, items);
						assert.notEqual(classItem, null, "vscode.executeCompletionItemProvider failed to provide class in a package xmlns: " + uri);
						assert.strictEqual(classItem.kind, vscode.CompletionItemKind.Class, "vscode.executeCompletionItemProvider failed to provide correct kind of class: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes class with existing prefix", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(16, 8);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("View", vscode.CompletionItemKind.Class, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to provide class with existing prefix: " + uri);
						assert.strictEqual(mxmlItem.kind, vscode.CompletionItemKind.Class, "vscode.executeCompletionItemProvider failed to provide correct kind of class: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes class with partial name and existing prefix", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(30, 11);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("View", vscode.CompletionItemKind.Class, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to include class with partial name and existing prefix: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes class with full name and existing prefix", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(9, 12);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let mxmlItem = findCompletionItemOfKind("View", vscode.CompletionItemKind.Class, items);
						assert.notEqual(mxmlItem, null, "vscode.executeCompletionItemProvider failed to include class with partial name and existing prefix: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits id as attribute of <fx:Declarations>", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(28, 21);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let propertyItem = findCompletionItem("id", items);
						assert.equal(propertyItem, null, "vscode.executeCompletionItemProvider failed to omit keyword as attribute: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes id as attribute of <fx:Object>", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(29, 15);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let propertyItem = findCompletionItem("id", items);
						assert.notEqual(propertyItem, null, "vscode.executeCompletionItemProvider failed to include keyword as attribute: " + uri);
						assert.strictEqual(propertyItem.kind, vscode.CompletionItemKind.Keyword, "vscode.executeCompletionItemProvider failed to provide correct kind of keyword as attribute: " + uri);
						let snippet = propertyItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "id=\"$0\"", "vscode.executeCompletionItemProvider failed to provide correct insert text for keyword as attribute: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits includeIn as attribute of <fx:Declarations>", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(28, 21);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let propertyItem = findCompletionItem("includeIn", items);
						assert.equal(propertyItem, null, "vscode.executeCompletionItemProvider failed to omit keyword as attribute: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes includeIn as attribute of <fx:Object>", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(29, 15);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let propertyItem = findCompletionItem("includeIn", items);
						assert.notEqual(propertyItem, null, "vscode.executeCompletionItemProvider failed to include keyword as attribute: " + uri);
						assert.strictEqual(propertyItem.kind, vscode.CompletionItemKind.Keyword, "vscode.executeCompletionItemProvider failed to provide correct kind of keyword as attribute: " + uri);
						let snippet = propertyItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "includeIn=\"$0\"", "vscode.executeCompletionItemProvider failed to provide correct insert text for keyword as attribute: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider omits excludeFrom as attribute of <fx:Declarations>", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(28, 21);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let propertyItem = findCompletionItem("excludeFrom", items);
						assert.equal(propertyItem, null, "vscode.executeCompletionItemProvider failed to omit keyword as attribute: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
	test("vscode.executeCompletionItemProvider includes excludeFrom as attribute of <fx:Object>", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLCompletion.mxml"));
		let position = new vscode.Position(29, 15);
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeCompletionItemProvider", uri, position)
				.then((list: vscode.CompletionList) =>
					{
						let items = list.items;
						let propertyItem = findCompletionItem("excludeFrom", items);
						assert.notEqual(propertyItem, null, "vscode.executeCompletionItemProvider failed to include keyword as attribute: " + uri);
						assert.strictEqual(propertyItem.kind, vscode.CompletionItemKind.Keyword, "vscode.executeCompletionItemProvider failed to provide correct kind of keyword as attribute: " + uri);
						let snippet = propertyItem.insertText as vscode.SnippetString;
						assert.strictEqual(snippet.value, "excludeFrom=\"$0\"", "vscode.executeCompletionItemProvider failed to provide correct insert text for keyword as attribute: " + uri);
					}, (err) =>
					{
						assert(false, "Failed to execute completion item provider: " + uri);
					});
		});
	});
});

suite("imports: Application workspace", () =>
{
	teardown(() =>
	{
		return vscode.commands.executeCommand("workbench.action.revertAndCloseActiveEditor").then(() =>
		{
			return new Promise((resolve, reject) =>
			{
				setTimeout(() =>
				{
					resolve();
				}, 100);
			});
		});
	});
	test("nextgenas.addImport adds import for qualified class with no range", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Imports.as"));
		let qualifiedName = "com.example.PackageClass";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("nextgenas.addImport", "com.example.PackageClass", uri.toString(), -1, -1)
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(2, 0);
								let end = new vscode.Position(4, 0);
								let range = new vscode.Range(start, end);
								let importText = editor.document.getText(range);
								assert.strictEqual(importText, "\timport com.example.PackageClass;\n\n", "nextgenas.addImport failed to add import for class: " + uri);
								resolve();
							}, 250);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute add import command: " + uri);
					});
		});
	});
	test("nextgenas.addImport adds import for qualified class in specific range", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Imports.as"));
		let qualifiedName = "com.example.PackageClass";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("nextgenas.addImport", "com.example.PackageClass", uri.toString(), 0, 126)
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(2, 0);
								let end = new vscode.Position(4, 0);
								let range = new vscode.Range(start, end);
								let importText = editor.document.getText(range);
								assert.strictEqual(importText, "\timport com.example.PackageClass;\n\n", "nextgenas.addImport failed to add import for class: " + uri);
								resolve();
							}, 250);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute add import command: " + uri);
					});
		});
	});
	test("nextgenas.addImport adds import for qualified class after package block", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "Imports.as"));
		let qualifiedName = "com.example.PackageClass";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("nextgenas.addImport", "com.example.PackageClass", uri.toString(), 127, -1)
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(11, 0);
								let end = new vscode.Position(13, 0);
								let range = new vscode.Range(start, end);
								let importText = editor.document.getText(range);
								assert.strictEqual(importText, "import com.example.PackageClass;\n\n", "nextgenas.addImport failed to add import for class: " + uri);
								resolve();
							}, 250);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute add import command: " + uri);
					});
		});
	});
});

suite("mxml namespaces: Application workspace", () =>
{
	teardown(() =>
	{
		return vscode.commands.executeCommand("workbench.action.revertAndCloseActiveEditor").then(() =>
		{
			return new Promise((resolve, reject) =>
			{
				setTimeout(() =>
				{
					resolve();
				}, 100);
			});
		});
	});
	test("nextgenas.addMXMLNamespace adds new namespace", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLNamespace.mxml"));
		let nsPrefix = "mx";
		let nsUri = "library://ns.adobe.com/flex/mx";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("nextgenas.addMXMLNamespace", nsPrefix, nsUri, uri.toString(), 48, 140)
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(2, 51);
								let end = new vscode.Position(2, 93);
								let range = new vscode.Range(start, end);
								let importText = editor.document.getText(range);
								assert.strictEqual(importText, " xmlns:mx=\"library://ns.adobe.com/flex/mx\"", "nextgenas.addMXMLNamespace failed to add MXML namspace in file: " + uri);
								resolve();
							}, 250);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute add import command: " + uri);
					});
		});
	});
	test("nextgenas.addMXMLNamespace skips duplicate namespace", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLNamespace.mxml"));
		let nsPrefix = "fx";
		let nsUri = "http://ns.adobe.com/mxml/2009";
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let originalText = editor.document.getText();
			return vscode.commands.executeCommand("nextgenas.addMXMLNamespace", nsPrefix, nsUri, uri.toString(), 48, 140)
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(2, 51);
								let end = new vscode.Position(2, 93);
								let range = new vscode.Range(start, end);
								let newText = editor.document.getText();
								assert.strictEqual(newText, originalText, "nextgenas.addMXMLNamespace incorrectly added duplicate MXML namespace in file: " + uri);
								resolve();
							}, 250);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute add import command: " + uri);
					});
		});
	});
});

suite("code action provider: Application workspace", () =>
{
	test("vscode.executeCodeActionProvider finds import for base class", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "CodeActionsImports.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let start = new vscode.Position(0, 0);
			let end = new vscode.Position(editor.document.lineCount, 0);
			let range = new vscode.Range(start, end);
			return vscode.commands.executeCommand("vscode.executeCodeActionProvider", uri, range)
				.then((codeActions: vscode.Command[]) =>
					{
						let typeToImport = "com.example.codeActions.CodeActionsBase";
						let codeAction = findImportCommandForType(typeToImport, codeActions);
						assert.notEqual(codeAction, null, "Code action not found");
						assert.strictEqual(codeAction.command, COMMAND_ADD_IMPORT);
						assert.strictEqual(codeAction.arguments[0], typeToImport, "Code action provided incorrect type to import");
						assert.strictEqual(vscode.Uri.parse(codeAction.arguments[1]).fsPath, uri.fsPath, "Code action provided incorrect URI");
					}, (err) =>
					{
						assert(false, "Failed to execute code actions provider: " + uri);
					});
		});
	});
	test("vscode.executeCodeActionProvider finds import for implemented interface", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "CodeActionsImports.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let start = new vscode.Position(0, 0);
			let end = new vscode.Position(editor.document.lineCount, 0);
			let range = new vscode.Range(start, end);
			return vscode.commands.executeCommand("vscode.executeCodeActionProvider", uri, range)
				.then((codeActions: vscode.Command[]) =>
					{
						let typeToImport = "com.example.codeActions.ICodeActionsInterface";
						let codeAction = findImportCommandForType(typeToImport, codeActions);
						assert.notEqual(codeAction, null, "Code action not found");
						assert.strictEqual(codeAction.command, COMMAND_ADD_IMPORT);
						assert.strictEqual(codeAction.arguments[0], typeToImport, "Code action provided incorrect type to import");
						assert.strictEqual(vscode.Uri.parse(codeAction.arguments[1]).fsPath, uri.fsPath, "Code action provided incorrect URI");
					}, (err) =>
					{
						assert(false, "Failed to execute code actions provider: " + uri);
					});
		});
	});
	test("vscode.executeCodeActionProvider finds import for new instance", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "CodeActionsImports.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let start = new vscode.Position(0, 0);
			let end = new vscode.Position(editor.document.lineCount, 0);
			let range = new vscode.Range(start, end);
			return vscode.commands.executeCommand("vscode.executeCodeActionProvider", uri, range)
				.then((codeActions: vscode.Command[]) =>
					{
						let typeToImport = "com.example.codeActions.CodeActionsNew";
						let codeAction = findImportCommandForType(typeToImport, codeActions);
						assert.notEqual(codeAction, null, "Code action not found");
						assert.strictEqual(codeAction.command, COMMAND_ADD_IMPORT);
						assert.strictEqual(codeAction.arguments[0], typeToImport, "Code action provided incorrect type to import");
						assert.strictEqual(vscode.Uri.parse(codeAction.arguments[1]).fsPath, uri.fsPath, "Code action provided incorrect URI");
					}, (err) =>
					{
						assert(false, "Failed to execute code actions provider: " + uri);
					});
		});
	});
	test("vscode.executeCodeActionProvider finds import for variable type", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "CodeActionsImports.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let start = new vscode.Position(0, 0);
			let end = new vscode.Position(editor.document.lineCount, 0);
			let range = new vscode.Range(start, end);
			return vscode.commands.executeCommand("vscode.executeCodeActionProvider", uri, range)
				.then((codeActions: vscode.Command[]) =>
					{
						let typeToImport = "com.example.codeActions.CodeActionsVarType";
						let codeAction = findImportCommandForType(typeToImport, codeActions);
						assert.notEqual(codeAction, null, "Code action not found");
						assert.strictEqual(codeAction.command, COMMAND_ADD_IMPORT);
						assert.strictEqual(codeAction.arguments[0], typeToImport, "Code action provided incorrect type to import");
						assert.strictEqual(vscode.Uri.parse(codeAction.arguments[1]).fsPath, uri.fsPath, "Code action provided incorrect URI");
					}, (err) =>
					{
						assert(false, "Failed to execute code actions provider: " + uri);
					});
		});
	});
	test("vscode.executeCodeActionProvider finds import for parameter type", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "CodeActionsImports.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let start = new vscode.Position(0, 0);
			let end = new vscode.Position(editor.document.lineCount, 0);
			let range = new vscode.Range(start, end);
			return vscode.commands.executeCommand("vscode.executeCodeActionProvider", uri, range)
				.then((codeActions: vscode.Command[]) =>
					{
						let typeToImport = "com.example.codeActions.CodeActionsParamType";
						let codeAction = findImportCommandForType(typeToImport, codeActions);
						assert.notEqual(codeAction, null, "Code action not found");
						assert.strictEqual(codeAction.command, COMMAND_ADD_IMPORT);
						assert.strictEqual(codeAction.arguments[0], typeToImport, "Code action provided incorrect type to import");
						assert.strictEqual(vscode.Uri.parse(codeAction.arguments[1]).fsPath, uri.fsPath, "Code action provided incorrect URI");
					}, (err) =>
					{
						assert(false, "Failed to execute code actions provider: " + uri);
					});
		});
	});
	test("vscode.executeCodeActionProvider finds import for return type", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "CodeActionsImports.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let start = new vscode.Position(0, 0);
			let end = new vscode.Position(editor.document.lineCount, 0);
			let range = new vscode.Range(start, end);
			return vscode.commands.executeCommand("vscode.executeCodeActionProvider", uri, range)
				.then((codeActions: vscode.Command[]) =>
					{
						let typeToImport = "com.example.codeActions.CodeActionsReturnType";
						let codeAction = findImportCommandForType(typeToImport, codeActions);
						assert.notEqual(codeAction, null, "Code action not found");
						assert.strictEqual(codeAction.command, COMMAND_ADD_IMPORT);
						assert.strictEqual(codeAction.arguments[0], typeToImport, "Code action provided incorrect type to import");
						assert.strictEqual(vscode.Uri.parse(codeAction.arguments[1]).fsPath, uri.fsPath, "Code action provided incorrect URI");
					}, (err) =>
					{
						assert(false, "Failed to execute code actions provider: " + uri);
					});
		});
	});
	test("vscode.executeCodeActionProvider finds import for type in assignment", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "CodeActionsImports.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let start = new vscode.Position(0, 0);
			let end = new vscode.Position(editor.document.lineCount, 0);
			let range = new vscode.Range(start, end);
			return vscode.commands.executeCommand("vscode.executeCodeActionProvider", uri, range)
				.then((codeActions: vscode.Command[]) =>
					{
						let typeToImport = "com.example.codeActions.CodeActionsAssign";
						let codeAction = findImportCommandForType(typeToImport, codeActions);
						assert.notEqual(codeAction, null, "Code action not found");
						assert.strictEqual(codeAction.command, COMMAND_ADD_IMPORT);
						assert.strictEqual(codeAction.arguments[0], typeToImport, "Code action provided incorrect type to import");
						assert.strictEqual(vscode.Uri.parse(codeAction.arguments[1]).fsPath, uri.fsPath, "Code action provided incorrect URI");
					}, (err) =>
					{
						assert(false, "Failed to execute code actions provider: " + uri);
					});
		});
	});
	test("vscode.executeCodeActionProvider finds import for multiple types with the same base name", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "CodeActionsImports.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let start = new vscode.Position(0, 0);
			let end = new vscode.Position(editor.document.lineCount, 0);
			let range = new vscode.Range(start, end);
			return vscode.commands.executeCommand("vscode.executeCodeActionProvider", uri, range)
				.then((codeActions: vscode.Command[]) =>
					{
						let typeToImport1 = "com.example.codeActions.CodeActionsMultiple";
						let codeAction1 = findImportCommandForType(typeToImport1, codeActions);
						assert.notEqual(codeAction1, null, "Code action 1 not found");
						assert.strictEqual(codeAction1.command, COMMAND_ADD_IMPORT);
						assert.strictEqual(codeAction1.arguments[0], typeToImport1, "Code action 1 provided incorrect type to import");
						assert.strictEqual(vscode.Uri.parse(codeAction1.arguments[1]).fsPath, uri.fsPath, "Code action 1 provided incorrect URI");

						let typeToImport2 = "com.example.codeActions.more.CodeActionsMultiple";
						let codeAction2 = findImportCommandForType(typeToImport2, codeActions);
						assert.notEqual(codeAction2, null, "Code action 2 not found");
						assert.strictEqual(codeAction2.command, COMMAND_ADD_IMPORT);
						assert.strictEqual(codeAction2.arguments[0], typeToImport2, "Code action 2 provided incorrect type to import");
						assert.strictEqual(vscode.Uri.parse(codeAction2.arguments[1]).fsPath, uri.fsPath, "Code action 2 provided incorrect URI");
					}, (err) =>
					{
						assert(false, "Failed to execute code actions provider: " + uri);
					});
		});
	});
	test("vscode.executeCodeActionProvider can generate local variable without this member access", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "CodeActionsGeneration.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let start = new vscode.Position(0, 0);
			let end = new vscode.Position(editor.document.lineCount, 0);
			let range = new vscode.Range(start, end);
			return vscode.commands.executeCommand("vscode.executeCodeActionProvider", uri, range)
				.then((codeActions: vscode.Command[]) =>
					{
						let variableName = "variableWithoutThis";
						let codeAction = codeActions.find((codeAction) =>
						{
							return codeAction.command === COMMAND_GENERATE_LOCAL_VARIABLE &&
								codeAction.arguments[codeAction.arguments.length - 1] === variableName;
						});
						assert.notEqual(codeAction, undefined, "Code action not found");
						assert.strictEqual(codeAction.command, COMMAND_GENERATE_LOCAL_VARIABLE);
						assert.strictEqual(codeAction.arguments[codeAction.arguments.length - 1], variableName, "Code action provided incorrect variable name");
						assert.strictEqual(vscode.Uri.parse(codeAction.arguments[0]).fsPath, uri.fsPath, "Code action provided incorrect URI");
					}, (err) =>
					{
						assert(false, "Failed to execute code actions provider: " + uri);
					});
		});
	});
	test("vscode.executeCodeActionProvider can generate member variable without this member access", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "CodeActionsGeneration.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let start = new vscode.Position(0, 0);
			let end = new vscode.Position(editor.document.lineCount, 0);
			let range = new vscode.Range(start, end);
			return vscode.commands.executeCommand("vscode.executeCodeActionProvider", uri, range)
				.then((codeActions: vscode.Command[]) =>
					{
						let variableName = "variableWithoutThis";
						let codeAction = codeActions.find((codeAction) =>
						{
							return codeAction.command === COMMAND_GENERATE_FIELD_VARIABLE &&
								codeAction.arguments[codeAction.arguments.length - 1] === variableName;
						});
						assert.notEqual(codeAction, undefined, "Code action not found");
						assert.strictEqual(codeAction.command, COMMAND_GENERATE_FIELD_VARIABLE);
						assert.strictEqual(codeAction.arguments[codeAction.arguments.length - 1], variableName, "Code action provided incorrect variable name");
						assert.strictEqual(vscode.Uri.parse(codeAction.arguments[0]).fsPath, uri.fsPath, "Code action provided incorrect URI");
					}, (err) =>
					{
						assert(false, "Failed to execute code actions provider: " + uri);
					});
		});
	});
	test("vscode.executeCodeActionProvider can generate member variable with this member access", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "CodeActionsGeneration.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let start = new vscode.Position(0, 0);
			let end = new vscode.Position(editor.document.lineCount, 0);
			let range = new vscode.Range(start, end);
			return vscode.commands.executeCommand("vscode.executeCodeActionProvider", uri, range)
				.then((codeActions: vscode.Command[]) =>
					{
						let variableName = "variableWithThis";
						let codeAction = codeActions.find((codeAction) =>
						{
							return codeAction.command === COMMAND_GENERATE_FIELD_VARIABLE &&
								codeAction.arguments[codeAction.arguments.length - 1] === variableName;
						});
						assert.notEqual(codeAction, undefined, "Code action not found");
						assert.strictEqual(codeAction.command, COMMAND_GENERATE_FIELD_VARIABLE);
						assert.strictEqual(codeAction.arguments[codeAction.arguments.length - 1], variableName, "Code action provided incorrect variable name");
						assert.strictEqual(vscode.Uri.parse(codeAction.arguments[0]).fsPath, uri.fsPath, "Code action provided incorrect URI");
					}, (err) =>
					{
						assert(false, "Failed to execute code actions provider: " + uri);
					});
		});
	});
	test("vscode.executeCodeActionProvider can generate method without this member access", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "CodeActionsGeneration.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let start = new vscode.Position(0, 0);
			let end = new vscode.Position(editor.document.lineCount, 0);
			let range = new vscode.Range(start, end);
			return vscode.commands.executeCommand("vscode.executeCodeActionProvider", uri, range)
				.then((codeActions: vscode.Command[]) =>
					{
						let methodName = "methodWithoutThis";
						let codeAction = codeActions.find((codeAction) =>
						{
							return codeAction.command === COMMAND_GENERATE_METHOD &&
								codeAction.arguments[codeAction.arguments.length - 2] === methodName;
						});
						assert.notEqual(codeAction, undefined, "Code action not found");
						assert.strictEqual(codeAction.command, COMMAND_GENERATE_METHOD);
						assert.strictEqual(codeAction.arguments[codeAction.arguments.length - 2], methodName, "Code action provided incorrect method name");
						assert.deepStrictEqual(codeAction.arguments[codeAction.arguments.length - 1], ["String", "Number"], "Code action provided incorrect argument types for method");
						assert.strictEqual(vscode.Uri.parse(codeAction.arguments[0]).fsPath, uri.fsPath, "Code action provided incorrect URI");
					}, (err) =>
					{
						assert(false, "Failed to execute code actions provider: " + uri);
					});
		});
	});
	test("vscode.executeCodeActionProvider can generate method with this member access", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "CodeActionsGeneration.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let start = new vscode.Position(0, 0);
			let end = new vscode.Position(editor.document.lineCount, 0);
			let range = new vscode.Range(start, end);
			return vscode.commands.executeCommand("vscode.executeCodeActionProvider", uri, range)
				.then((codeActions: vscode.Command[]) =>
					{
						let methodName = "methodWithThis";
						let codeAction = codeActions.find((codeAction) =>
						{
							return codeAction.command === COMMAND_GENERATE_METHOD &&
								codeAction.arguments[codeAction.arguments.length - 2] === methodName;
						});
						assert.notEqual(codeAction, undefined, "Code action not found");
						assert.strictEqual(codeAction.command, COMMAND_GENERATE_METHOD);
						assert.strictEqual(codeAction.arguments[codeAction.arguments.length - 2], methodName, "Code action provided incorrect method name");
						assert.deepStrictEqual(codeAction.arguments[codeAction.arguments.length - 1], ["Number"], "Code action provided incorrect argument types for method");
						assert.strictEqual(vscode.Uri.parse(codeAction.arguments[0]).fsPath, uri.fsPath, "Code action provided incorrect URI");
					}, (err) =>
					{
						assert(false, "Failed to execute code actions provider: " + uri);
					});
		});
	});
	test("vscode.executeCodeActionProvider must not generate method from \"new\" expression", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "CodeActionsGeneration.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let start = new vscode.Position(0, 0);
			let end = new vscode.Position(editor.document.lineCount, 0);
			let range = new vscode.Range(start, end);
			return vscode.commands.executeCommand("vscode.executeCodeActionProvider", uri, range)
				.then((codeActions: vscode.Command[]) =>
					{
						let methodName = "FakeClass";
						let codeAction = codeActions.find((codeAction) =>
						{
							return codeAction.command === COMMAND_GENERATE_METHOD &&
								codeAction.arguments[codeAction.arguments.length - 2] === methodName;
						});
						assert.strictEqual(codeAction, undefined, "Code action found");
					}, (err) =>
					{
						assert(false, "Failed to execute code actions provider: " + uri);
					});
		});
	});
	test("vscode.executeCodeActionProvider can generate getter", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "CodeActionsGeneration.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let start = new vscode.Position(0, 0);
			let end = new vscode.Position(editor.document.lineCount, 0);
			let range = new vscode.Range(start, end);
			return vscode.commands.executeCommand("vscode.executeCodeActionProvider", uri, range)
				.then((codeActions: vscode.Command[]) =>
					{
						let methodName = "getterAndSetter";
						let codeAction = codeActions.find((codeAction) =>
						{
							return codeAction.command === COMMAND_GENERATE_GETTER &&
								codeAction.arguments[codeAction.arguments.length - 5] === methodName;
						});
						assert.notEqual(codeAction, undefined, "Code action not found");
						assert.strictEqual(codeAction.command, COMMAND_GENERATE_GETTER);
						assert.strictEqual(codeAction.arguments[codeAction.arguments.length - 5], methodName, "Code action provided incorrect name");
						assert.strictEqual(codeAction.arguments[codeAction.arguments.length - 4], "protected", "Code action provided incorrect namespace");
						assert.strictEqual(codeAction.arguments[codeAction.arguments.length - 3], false, "Code action provided incorrect static modifier");
						assert.deepStrictEqual(codeAction.arguments[codeAction.arguments.length - 2], "String", "Code action provided incorrect type");
						assert.deepStrictEqual(codeAction.arguments[codeAction.arguments.length - 1], "\"getAndSet\"", "Code action provided incorrect assignment");
						assert.strictEqual(vscode.Uri.parse(codeAction.arguments[0]).fsPath, uri.fsPath, "Code action provided incorrect URI");
					}, (err) =>
					{
						assert(false, "Failed to execute code actions provider: " + uri);
					});
		});
	});
	test("vscode.executeCodeActionProvider can generate setter", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "CodeActionsGeneration.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let start = new vscode.Position(0, 0);
			let end = new vscode.Position(editor.document.lineCount, 0);
			let range = new vscode.Range(start, end);
			return vscode.commands.executeCommand("vscode.executeCodeActionProvider", uri, range)
				.then((codeActions: vscode.Command[]) =>
					{
						let methodName = "getterAndSetter";
						let codeAction = codeActions.find((codeAction) =>
						{
							return codeAction.command === COMMAND_GENERATE_SETTER &&
								codeAction.arguments[codeAction.arguments.length - 5] === methodName;
						});
						assert.notEqual(codeAction, undefined, "Code action not found");
						assert.strictEqual(codeAction.command, COMMAND_GENERATE_SETTER);
						assert.strictEqual(codeAction.arguments[codeAction.arguments.length - 5], methodName, "Code action provided incorrect name");
						assert.strictEqual(codeAction.arguments[codeAction.arguments.length - 4], "protected", "Code action provided incorrect namespace");
						assert.strictEqual(codeAction.arguments[codeAction.arguments.length - 3], false, "Code action provided incorrect static modifier");
						assert.deepStrictEqual(codeAction.arguments[codeAction.arguments.length - 2], "String", "Code action provided incorrect type");
						assert.deepStrictEqual(codeAction.arguments[codeAction.arguments.length - 1], "\"getAndSet\"", "Code action provided incorrect assignment");
						assert.strictEqual(vscode.Uri.parse(codeAction.arguments[0]).fsPath, uri.fsPath, "Code action provided incorrect URI");
					}, (err) =>
					{
						assert(false, "Failed to execute code actions provider: " + uri);
					});
		});
	});
	test("vscode.executeCodeActionProvider can generate getter and setter", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "CodeActionsGeneration.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let start = new vscode.Position(0, 0);
			let end = new vscode.Position(editor.document.lineCount, 0);
			let range = new vscode.Range(start, end);
			return vscode.commands.executeCommand("vscode.executeCodeActionProvider", uri, range)
				.then((codeActions: vscode.Command[]) =>
					{
						let methodName = "getterAndSetter";
						let codeAction = codeActions.find((codeAction) =>
						{
							return codeAction.command === COMMAND_GENERATE_GETTER_AND_SETTER &&
								codeAction.arguments[codeAction.arguments.length - 5] === methodName;
						});
						assert.notEqual(codeAction, undefined, "Code action not found");
						assert.strictEqual(codeAction.command, COMMAND_GENERATE_GETTER_AND_SETTER);
						assert.strictEqual(codeAction.arguments[codeAction.arguments.length - 5], methodName, "Code action provided incorrect name");
						assert.strictEqual(codeAction.arguments[codeAction.arguments.length - 4], "protected", "Code action provided incorrect namespace");
						assert.strictEqual(codeAction.arguments[codeAction.arguments.length - 3], false, "Code action provided incorrect static modifier");
						assert.deepStrictEqual(codeAction.arguments[codeAction.arguments.length - 2], "String", "Code action provided incorrect type");
						assert.deepStrictEqual(codeAction.arguments[codeAction.arguments.length - 1], "\"getAndSet\"", "Code action provided incorrect assignment");
						assert.strictEqual(vscode.Uri.parse(codeAction.arguments[0]).fsPath, uri.fsPath, "Code action provided incorrect URI");
					}, (err) =>
					{
						assert(false, "Failed to execute code actions provider: " + uri);
					});
		});
	});
	test("vscode.executeCodeActionProvider can generate static getter and setter", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "CodeActionsGeneration.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			let start = new vscode.Position(0, 0);
			let end = new vscode.Position(editor.document.lineCount, 0);
			let range = new vscode.Range(start, end);
			return vscode.commands.executeCommand("vscode.executeCodeActionProvider", uri, range)
				.then((codeActions: vscode.Command[]) =>
					{
						let methodName = "staticGetterAndSetter";
						let codeAction = codeActions.find((codeAction) =>
						{
							return codeAction.command === COMMAND_GENERATE_GETTER_AND_SETTER &&
								codeAction.arguments[codeAction.arguments.length - 5] === methodName;
						});
						assert.notEqual(codeAction, undefined, "Code action not found");
						assert.strictEqual(codeAction.command, COMMAND_GENERATE_GETTER_AND_SETTER);
						assert.strictEqual(codeAction.arguments[codeAction.arguments.length - 5], methodName, "Code action provided incorrect name");
						assert.strictEqual(codeAction.arguments[codeAction.arguments.length - 4], "private", "Code action provided incorrect namespace");
						assert.strictEqual(codeAction.arguments[codeAction.arguments.length - 3], true, "Code action provided incorrect static modifier");
						assert.deepStrictEqual(codeAction.arguments[codeAction.arguments.length - 2], "Number", "Code action provided incorrect type");
						assert.deepStrictEqual(codeAction.arguments[codeAction.arguments.length - 1], null, "Code action provided incorrect assignment");
						assert.strictEqual(vscode.Uri.parse(codeAction.arguments[0]).fsPath, uri.fsPath, "Code action provided incorrect URI");
					}, (err) =>
					{
						assert(false, "Failed to execute code actions provider: " + uri);
					});
		});
	});
});

suite("generate getter/setter: Application workspace", () =>
{
	teardown(() =>
	{
		return vscode.commands.executeCommand("workbench.action.revertAndCloseActiveEditor").then(() =>
		{
			return new Promise((resolve, reject) =>
			{
				setTimeout(() =>
				{
					resolve();
				}, 100);
			});
		});
	});
	test("nextgenas.generateGetter generates getter without assignment", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "GenerateGetterAndSetter.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("nextgenas.generateGetter", uri.toString(), 7, 2, 7, 32, "noAssignment", "public", false, "Object", null)
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(7, 0);
								let end = new vscode.Position(13, 0);
								let range = new vscode.Range(start, end);
								let generatedText = editor.document.getText(range);
								assert.strictEqual(generatedText, "\t\tprivate var _noAssignment:Object;\n\n\t\tpublic function get noAssignment():Object\n\t\t{\n\t\t\treturn _noAssignment;\n\t\t}\n", "nextgenas.generateGetter failed to generate getter");
								resolve();
							}, 250);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute generate getter command: " + uri);
					});
		});
	});
	test("nextgenas.generateGetter generates getter with a type and assignment", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "GenerateGetterAndSetter.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("nextgenas.generateGetter", uri.toString(), 9, 2, 9, 40, "assignment", "public", false, "String", "\"hello\"")
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(9, 0);
								let end = new vscode.Position(15, 0);
								let range = new vscode.Range(start, end);
								let generatedText = editor.document.getText(range);
								assert.strictEqual(generatedText, "\t\tprivate var _assignment:String = \"hello\";\n\n\t\tpublic function get assignment():String\n\t\t{\n\t\t\treturn _assignment;\n\t\t}\n", "nextgenas.generateGetter failed to generate getter");
								resolve();
							}, 250);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute generate getter command: " + uri);
					});
		});
	});
	test("nextgenas.generateGetter generates getter with static", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "GenerateGetterAndSetter.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("nextgenas.generateGetter", uri.toString(), 11, 2, 11, 37, "isStatic", "private", true, "Boolean", null)
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(11, 0);
								let end = new vscode.Position(17, 0);
								let range = new vscode.Range(start, end);
								let generatedText = editor.document.getText(range);
								assert.strictEqual(generatedText, "\t\tprivate static var _isStatic:Boolean;\n\n\t\tprivate static function get isStatic():Boolean\n\t\t{\n\t\t\treturn _isStatic;\n\t\t}\n", "nextgenas.generateGetter failed to generate getter");
								resolve();
							}, 250);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute generate getter command: " + uri);
					});
		});
	});
	test("nextgenas.generateSetter generates setter without assignment", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "GenerateGetterAndSetter.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("nextgenas.generateSetter", uri.toString(), 7, 2, 7, 32, "noAssignment", "public", false, "Object", null)
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(7, 0);
								let end = new vscode.Position(13, 0);
								let range = new vscode.Range(start, end);
								let generatedText = editor.document.getText(range);
								assert.strictEqual(generatedText, "\t\tprivate var _noAssignment:Object;\n\n\t\tpublic function set noAssignment(value:Object):void\n\t\t{\n\t\t\t_noAssignment = value;\n\t\t}\n", "nextgenas.generateSetter failed to generate setter");
								resolve();
							}, 250);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute generate setter command: " + uri);
					});
		});
	});
	test("nextgenas.generateSetter generates setter with a type and assignment", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "GenerateGetterAndSetter.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("nextgenas.generateSetter", uri.toString(), 9, 2, 9, 40, "assignment", "public", false, "String", "\"hello\"")
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(9, 0);
								let end = new vscode.Position(15, 0);
								let range = new vscode.Range(start, end);
								let generatedText = editor.document.getText(range);
								assert.strictEqual(generatedText, "\t\tprivate var _assignment:String = \"hello\";\n\n\t\tpublic function set assignment(value:String):void\n\t\t{\n\t\t\t_assignment = value;\n\t\t}\n", "nextgenas.generateSetter failed to generate setter");
								resolve();
							}, 250);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute generate setter command: " + uri);
					});
		});
	});
	test("nextgenas.generateSetter generates setter with static", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "GenerateGetterAndSetter.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("nextgenas.generateSetter", uri.toString(), 11, 2, 11, 37, "isStatic", "private", true, "Boolean", null)
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(11, 0);
								let end = new vscode.Position(17, 0);
								let range = new vscode.Range(start, end);
								let generatedText = editor.document.getText(range);
								assert.strictEqual(generatedText, "\t\tprivate static var _isStatic:Boolean;\n\n\t\tprivate static function set isStatic(value:Boolean):void\n\t\t{\n\t\t\t_isStatic = value;\n\t\t}\n", "nextgenas.generateSetter failed to generate setter");
								resolve();
							}, 250);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute generate setter command: " + uri);
					});
		});
	});
	test("nextgenas.generateGetterAndSetter generates getter and setter without assignment", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "GenerateGetterAndSetter.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("nextgenas.generateGetterAndSetter", uri.toString(), 7, 2, 7, 32, "noAssignment", "public", false, "Object", null)
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(7, 0);
								let end = new vscode.Position(18, 0);
								let range = new vscode.Range(start, end);
								let generatedText = editor.document.getText(range);
								assert.strictEqual(generatedText, "\t\tprivate var _noAssignment:Object;\n\n\t\tpublic function get noAssignment():Object\n\t\t{\n\t\t\treturn _noAssignment;\n\t\t}\n\n\t\tpublic function set noAssignment(value:Object):void\n\t\t{\n\t\t\t_noAssignment = value;\n\t\t}\n", "nextgenas.generateSetter failed to generate getter and setter");
								resolve();
							}, 250);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute generate getter and setter command: " + uri);
					});
		});
	});
	test("nextgenas.generateGetterAndSetter generates getter and setter with a type and assignment", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "GenerateGetterAndSetter.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("nextgenas.generateGetterAndSetter", uri.toString(), 9, 2, 9, 40, "assignment", "public", false, "String", "\"hello\"")
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(9, 0);
								let end = new vscode.Position(20, 0);
								let range = new vscode.Range(start, end);
								let generatedText = editor.document.getText(range);
								assert.strictEqual(generatedText, "\t\tprivate var _assignment:String = \"hello\";\n\n\t\tpublic function get assignment():String\n\t\t{\n\t\t\treturn _assignment;\n\t\t}\n\n\t\tpublic function set assignment(value:String):void\n\t\t{\n\t\t\t_assignment = value;\n\t\t}\n", "nextgenas.generateSetter failed to generate getter and setter");
								resolve();
							}, 250);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute generate getter and setter command: " + uri);
					});
		});
	});
	test("nextgenas.generateGetterAndSetter generates getter and setter with static", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "GenerateGetterAndSetter.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("nextgenas.generateGetterAndSetter", uri.toString(), 11, 2, 11, 37, "isStatic", "private", true, "Boolean", null)
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(11, 0);
								let end = new vscode.Position(22, 0);
								let range = new vscode.Range(start, end);
								let generatedText = editor.document.getText(range);
								assert.strictEqual(generatedText, "\t\tprivate static var _isStatic:Boolean;\n\n\t\tprivate static function get isStatic():Boolean\n\t\t{\n\t\t\treturn _isStatic;\n\t\t}\n\n\t\tprivate static function set isStatic(value:Boolean):void\n\t\t{\n\t\t\t_isStatic = value;\n\t\t}\n", "nextgenas.generateGetterAndSetter failed to generate getter and setter");
								resolve();
							}, 250);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute generate getter and setter command: " + uri);
					});
		});
	});
});

suite("generate variable: Application workspace", () =>
{
	teardown(() =>
	{
		return vscode.commands.executeCommand("workbench.action.revertAndCloseActiveEditor").then(() =>
		{
			return new Promise((resolve, reject) =>
			{
				setTimeout(() =>
				{
					resolve();
				}, 100);
			});
		});
	});
	test("nextgenas.generateLocalVariable generates local variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "GenerateVariable.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("nextgenas.generateLocalVariable", uri.toString(), 6, 3, 6, 8, "myVar")
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(6, 0);
								let end = new vscode.Position(8, 0);
								let range = new vscode.Range(start, end);
								let generatedText = editor.document.getText(range);
								assert.strictEqual(generatedText, "\t\t\tvar myVar:Object;\n\t\t\tmyVar = 12;\n", "nextgenas.generateLocalVariable failed to generate local variable");
								resolve();
							}, 250);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute generate local variable command: " + uri);
					});
		});
	});
	test("nextgenas.generateFieldVariable generates field variable", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "GenerateVariable.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("nextgenas.generateFieldVariable", uri.toString(), 6, 3, 6, 8, "myVar")
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(9, 0);
								let end = new vscode.Position(10, 0);
								let range = new vscode.Range(start, end);
								let generatedText = editor.document.getText(range);
								assert.strictEqual(generatedText, "\t\tpublic var myVar:Object;\n", "nextgenas.generateFieldVariable failed to generate field variable");
								resolve();
							}, 250);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute generate field variable command: " + uri);
					});
		});
	});
});

suite("generate method: Application workspace", () =>
{
	teardown(() =>
	{
		return vscode.commands.executeCommand("workbench.action.revertAndCloseActiveEditor").then(() =>
		{
			return new Promise((resolve, reject) =>
			{
				setTimeout(() =>
				{
					resolve();
				}, 100);
			});
		});
	});
	test("nextgenas.generateMethod generates method with no parameters", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "GenerateMethod.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("nextgenas.generateMethod", uri.toString(), 6, 3, 6, 11, "myMethod", null)
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(10, 0);
								let end = new vscode.Position(13, 0);
								let range = new vscode.Range(start, end);
								let generatedText = editor.document.getText(range);
								assert.strictEqual(generatedText, "\t\tprivate function myMethod():void\n\t\t{\n\t\t}\n", "nextgenas.generateMethod failed to generate method");
								resolve();
							}, 250);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute generate method command: " + uri);
					});
		});
	});
	test("nextgenas.generateMethod generates method with parameters", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "GenerateMethod.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("nextgenas.generateMethod", uri.toString(), 7, 3, 7, 11, "myMethod", ["Number", "Boolean", "String"])
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(10, 0);
								let end = new vscode.Position(13, 0);
								let range = new vscode.Range(start, end);
								let generatedText = editor.document.getText(range);
								assert.strictEqual(generatedText, "\t\tprivate function myMethod(param0:Number, param1:Boolean, param2:String):void\n\t\t{\n\t\t}\n", "nextgenas.generateMethod failed to generate method");
								resolve();
							}, 250);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute generate method command: " + uri);
					});
		});
	});
});

suite("organize imports: Application workspace", () =>
{
	teardown(() =>
	{
		return vscode.commands.executeCommand("workbench.action.revertAndCloseActiveEditor").then(() =>
		{
			return new Promise((resolve, reject) =>
			{
				setTimeout(() =>
				{
					resolve();
				}, 100);
			});
		});
	});
	test("nextgenas.organizeImportsInUri organizes imports in ActionScript: removes unused imports, adds missing imports, and reorganizes remaining imports in alphabetical order", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "OrganizeImports.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("nextgenas.organizeImportsInUri", uri)
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(2, 0);
								let end = new vscode.Position(11, 0);
								let range = new vscode.Range(start, end);
								let generatedText = editor.document.getText(range);
								assert.strictEqual(generatedText, "\timport com.example.organizeImports.ImportToAdd;\n\timport com.example.organizeImports.ImportToAddFromAsOperator;\n\timport com.example.organizeImports.ImportToAddFromCast;\n\timport com.example.organizeImports.ImportToAddFromIsOperator;\n\timport com.example.organizeImports.ImportToAddFromNew;\n\timport com.example.organizeImports.ImportToAddFromReturnType;\n\timport com.example.organizeImports.ImportToKeepClass;\n\timport com.example.organizeImports.ImportToKeepInterface;\n\n", "nextgenas.organizeImportsInUri failed to organize imports");
								resolve();
							}, 1000);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute organize imports command: " + uri);
					});
		});
	});
	test("nextgenas.organizeImportsInUri organizes imports in MXML: removes unused imports, adds missing imports, and reorganizes remaining imports in alphabetical order", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "MXMLOrganizeImports.mxml"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("nextgenas.organizeImportsInUri", uri)
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(5, 0);
								let end = new vscode.Position(14, 0);
								let range = new vscode.Range(start, end);
								let generatedText = editor.document.getText(range);
								assert.strictEqual(generatedText, "\t\t\timport com.example.organizeImports.ImportToAdd;\n\t\t\timport com.example.organizeImports.ImportToAddFromAsOperator;\n\t\t\timport com.example.organizeImports.ImportToAddFromCast;\n\t\t\timport com.example.organizeImports.ImportToAddFromIsOperator;\n\t\t\timport com.example.organizeImports.ImportToAddFromNew;\n\t\t\timport com.example.organizeImports.ImportToAddFromReturnType;\n\t\t\timport com.example.organizeImports.ImportToKeepClass;\n\t\t\timport com.example.organizeImports.ImportToKeepInterface;\n\n", "nextgenas.organizeImportsInUri failed to organize imports");
								resolve();
							}, 1000);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute organize imports command: " + uri);
					});
		});
	});
	//BowlerHatLLC/vscode-nextgenas#182
	test("nextgenas.organizeImportsInUri must be able to remove all imports, if necessary", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "src", "RemoveAllImports.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("nextgenas.organizeImportsInUri", uri)
				.then(() =>
					{
						return new Promise((resolve, reject) =>
						{
							//the text edit is not applied immediately, so give
							//it a short delay before we check
							setTimeout(() =>
							{
								let start = new vscode.Position(2, 0);
								let end = new vscode.Position(3, 0);
								let range = new vscode.Range(start, end);
								let generatedText = editor.document.getText(range);
								assert.strictEqual(generatedText, "\n", "nextgenas.organizeImportsInUri failed to organize imports");
								resolve();
							}, 1000);
						})
					}, (err) =>
					{
						assert(false, "Failed to execute organize imports command: " + uri);
					});
		});
	});
});

suite("ActionScript & MXML extension: Library workspace", () =>
{
	test("vscode.extensions.getExtension() and isActive", (done) =>
	{
		let oldWorkspacePath = vscode.workspace.workspaceFolders[0].uri.fsPath;
		let newWorkspacePath = path.resolve(oldWorkspacePath, "..", "library_workspace");
		vscode.workspace.updateWorkspaceFolders(vscode.workspace.workspaceFolders.length, 0, { uri: vscode.Uri.file(newWorkspacePath) });
		assert.strictEqual(vscode.workspace.workspaceFolders[1].uri.fsPath, newWorkspacePath, `Wrong workspace folder path!`);
		//wait a bit for the the extension to fully activate because we need
		//the project to be fully loaded into the compiler for future tests
		setTimeout(() =>
		{
			let extension = vscode.extensions.getExtension(EXTENSION_ID);
			assert.ok(extension, `Extension "${EXTENSION_ID}" not found!`);
			assert.ok(extension.isActive, `Extension "${EXTENSION_ID}" not active!`);
			assert.ok(extension.exports.isLanguageClientReady, `Extension "${EXTENSION_ID}" language client not ready!`);
			done();
		}, 6500);
	});
});

/*suite("document symbol provider: Library workspace", () =>
{
	test("vscode.executeDocumentSymbolProvider not empty", () =>
	{
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[1].uri.fsPath, "src", "com", "example", "LibraryDocumentSymbols.as"));
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
		let uri = vscode.Uri.file(path.join(vscode.workspace.workspaceFolders[1].uri.fsPath, "src", "com", "example", "LibraryDocumentSymbols.as"));
		return openAndEditDocument(uri, (editor: vscode.TextEditor) =>
		{
			return vscode.commands.executeCommand("vscode.executeDocumentSymbolProvider", uri)
				.then((symbols: vscode.SymbolInformation[]) =>
					{
						let className = "LibraryDocumentSymbols";
						let packageName = "com.example";
						assert.ok(findSymbol(symbols, new vscode.SymbolInformation(
							className,
							vscode.SymbolKind.Class,
							createRange(2, 14),
							uri,
							packageName)),
							"vscode.executeDocumentSymbolProvider failed to provide symbol for class: " + packageName);
					}, (err) =>
					{
						assert(false, "Failed to execute document symbol provider: " + uri);
					});
		});
	});
});*/