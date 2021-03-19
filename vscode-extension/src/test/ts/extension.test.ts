/*
Copyright 2016-2021 Bowler Hat LLC

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
const COMMAND_ADD_IMPORT = "as3mxml.addImport";
const COMMAND_ADD_MXML_NAMESPACE = "as3mxml.addMXMLNamespace";
const COMMAND_ORGANIZE_IMPORTS_IN_URI = "as3mxml.organizeImportsInUri";

function openAndEditDocument(
  uri: vscode.Uri,
  callback: (editor: vscode.TextEditor) => PromiseLike<void>
): PromiseLike<void> {
  return vscode.workspace
    .openTextDocument(uri)
    .then(
      (document: vscode.TextDocument) =>
        new Promise((resolve) => setTimeout(resolve, 100, document)),
      (err) =>
        assert(false, "Failed to open text document: " + uri + "\n" + err)
    )
    .then(
      (document: vscode.TextDocument) =>
        vscode.window.showTextDocument(document),
      (err) =>
        assert(false, "Failed to show text document: " + uri + "\n" + err)
    )
    .then(callback);
}

function revertAndCloseActiveEditor(): PromiseLike<void> {
  return vscode.commands
    .executeCommand("workbench.action.revertAndCloseActiveEditor")
    .then(() => {
      return new Promise((resolve) => setTimeout(resolve, 100));
    });
}

function revertAndCloseAllEditors() {
  return vscode.commands
    .executeCommand("workbench.action.revertAndCloseActiveEditor")
    .then(
      () => {
        return new Promise<void>((resolve, reject) => {
          setTimeout(() => {
            if (vscode.window.activeTextEditor) {
              resolve(revertAndCloseAllEditors());
              return;
            }
            resolve();
          }, 100);
        });
      },
      (reason) => {
        console.error("close text editor failed:", reason);
      }
    );
}

function createRange(
  startLine: number,
  startCharacter: number,
  endLine?: number,
  endCharacter?: number
): vscode.Range {
  if (endLine === undefined) {
    endLine = startLine;
  }
  if (endCharacter === undefined) {
    endCharacter = startCharacter;
  }
  return new vscode.Range(
    new vscode.Position(startLine, startCharacter),
    new vscode.Position(endLine, endCharacter)
  );
}

function findDocumentSymbol(
  symbols: vscode.DocumentSymbol[],
  symbolToFind: vscode.DocumentSymbol
): boolean {
  return symbols.some((symbol: vscode.DocumentSymbol) => {
    if (symbol.children && findDocumentSymbol(symbol.children, symbolToFind)) {
      return true;
    }
    if (symbol.name !== symbolToFind.name) {
      return false;
    }
    if (symbol.kind !== symbolToFind.kind) {
      return false;
    }
    if (symbol.range.start.line !== symbolToFind.range.start.line) {
      return false;
    }
    if (symbol.range.start.character !== symbolToFind.range.start.character) {
      return false;
    }
    return true;
  });
}

function findSymbolInformation(
  symbols: vscode.SymbolInformation[],
  symbolToFind: vscode.SymbolInformation
): boolean {
  return symbols.some((symbol: vscode.SymbolInformation) => {
    if (symbol.name !== symbolToFind.name) {
      return false;
    }
    if (
      symbolToFind.containerName &&
      symbol.containerName !== symbolToFind.containerName
    ) {
      return false;
    }
    if (symbol.kind !== symbolToFind.kind) {
      return false;
    }
    if (
      symbol.location.uri.toString() !== symbolToFind.location.uri.toString()
    ) {
      return false;
    }
    if (
      symbol.location.range.start.line !==
      symbolToFind.location.range.start.line
    ) {
      return false;
    }
    if (
      symbol.location.range.start.character !==
      symbolToFind.location.range.start.character
    ) {
      return false;
    }
    return true;
  });
}

function findCompletionItemOfKind(
  name: string,
  kind: vscode.CompletionItemKind,
  items: vscode.CompletionItem[]
): vscode.CompletionItem {
  return items.find((item: vscode.CompletionItem) => {
    return item.label === name && item.kind === kind;
  });
}

function findCompletionItem(
  name: string,
  items: vscode.CompletionItem[]
): vscode.CompletionItem {
  return items.find((item: vscode.CompletionItem) => {
    return item.label === name;
  });
}

function containsCompletionItemsOtherThanTextOrSnippet(
  items: vscode.CompletionItem[]
): boolean {
  return items.some((item: vscode.CompletionItem) => {
    //vscode can automatically provide Text items based on other text in the editor
    return (
      item.kind !== vscode.CompletionItemKind.Text &&
      //vscode simply include snippets everywhere in the file, without restriction
      item.kind !== vscode.CompletionItemKind.Snippet
    );
  });
}

function findImportCodeActionForType(
  qualifiedName: string,
  codeActions: vscode.CodeAction[]
) {
  for (let i = 0, count = codeActions.length; i < count; i++) {
    let codeAction = codeActions[i];
    if (codeAction.title === "Import " + qualifiedName) {
      return codeAction;
    }
  }
  return null;
}

suite("ActionScript & MXML extension: Application workspace", () => {
  test("vscode.extensions.getExtension(), isActive, and isLanguageClientReady", (done) => {
    let extension = vscode.extensions.getExtension(EXTENSION_ID);
    assert.ok(extension, `Extension "${EXTENSION_ID}" not found!`);
    //wait a bit for the the extension to fully activate because we need
    //the project to be fully loaded into the compiler for future tests
    setTimeout(() => {
      assert.ok(extension.isActive, `Extension "${EXTENSION_ID}" not active!`);
      assert.ok(
        extension.exports.isLanguageClientReady,
        `Extension "${EXTENSION_ID}" language client not ready!`
      );
      done();
    }, 6500);
  });
});

suite("document symbol provider: Application workspace", () => {
  suiteTeardown(revertAndCloseAllEditors);
  test("vscode.executeDocumentSymbolProvider not empty", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDocumentSymbolProvider", uri)
        .then(
          (symbols: vscode.DocumentSymbol[]) => {
            assert.notStrictEqual(
              symbols.length,
              0,
              "vscode.executeDocumentSymbolProvider failed to provide symbols in text document: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute document symbol provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentSymbolProvider includes class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDocumentSymbolProvider", uri)
        .then(
          (symbols: vscode.DocumentSymbol[]) => {
            let className = "Main";
            assert.ok(
              findDocumentSymbol(
                symbols,
                new vscode.DocumentSymbol(
                  className,
                  null,
                  vscode.SymbolKind.Class,
                  createRange(2, 14),
                  createRange(2, 14)
                )
              ),
              "vscode.executeDocumentSymbolProvider failed to provide symbol for class: " +
                className
            );
          },
          (err) => {
            assert(false, "Failed to execute document symbol provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentSymbolProvider includes constructor", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDocumentSymbolProvider", uri)
        .then(
          (symbols: vscode.DocumentSymbol[]) => {
            let className = "Main";
            assert.ok(
              findDocumentSymbol(
                symbols,
                new vscode.DocumentSymbol(
                  className,
                  null,
                  vscode.SymbolKind.Constructor,
                  createRange(16, 18),
                  createRange(16, 18)
                )
              ),
              "vscode.executeDocumentSymbolProvider failed to provide symbol for constructor: " +
                className
            );
          },
          (err) => {
            assert(false, "Failed to execute document symbol provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentSymbolProvider includes member variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDocumentSymbolProvider", uri)
        .then(
          (symbols: vscode.DocumentSymbol[]) => {
            let memberVarName = "memberVar";
            assert.ok(
              findDocumentSymbol(
                symbols,
                new vscode.DocumentSymbol(
                  memberVarName,
                  null,
                  vscode.SymbolKind.Field,
                  createRange(21, 13),
                  createRange(21, 13)
                )
              ),
              "vscode.executeDocumentSymbolProvider failed to provide symbol for member variable: " +
                memberVarName
            );
          },
          (err) => {
            assert(false, "Failed to execute document symbol provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentSymbolProvider includes member function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDocumentSymbolProvider", uri)
        .then(
          (symbols: vscode.DocumentSymbol[]) => {
            let memberFunctionName = "memberFunction";
            assert.ok(
              findDocumentSymbol(
                symbols,
                new vscode.DocumentSymbol(
                  memberFunctionName,
                  null,
                  vscode.SymbolKind.Method,
                  createRange(23, 19),
                  createRange(23, 19)
                )
              ),
              "vscode.executeDocumentSymbolProvider failed to provide symbol for member function: " +
                memberFunctionName
            );
          },
          (err) => {
            assert(false, "Failed to execute document symbol provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentSymbolProvider includes member property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDocumentSymbolProvider", uri)
        .then(
          (symbols: vscode.DocumentSymbol[]) => {
            let memberPropertyName = "memberProperty";
            assert.ok(
              findDocumentSymbol(
                symbols,
                new vscode.DocumentSymbol(
                  memberPropertyName,
                  null,
                  vscode.SymbolKind.Property,
                  createRange(27, 22),
                  createRange(27, 22)
                )
              ),
              "vscode.executeDocumentSymbolProvider failed to provide symbol for member variable: " +
                memberPropertyName
            );
          },
          (err) => {
            assert(false, "Failed to execute document symbol provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentSymbolProvider includes static variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDocumentSymbolProvider", uri)
        .then(
          (symbols: vscode.DocumentSymbol[]) => {
            let staticVarName = "staticVar";
            assert.ok(
              findDocumentSymbol(
                symbols,
                new vscode.DocumentSymbol(
                  staticVarName,
                  null,
                  vscode.SymbolKind.Field,
                  createRange(4, 21),
                  createRange(4, 21)
                )
              ),
              "vscode.executeDocumentSymbolProvider failed to provide symbol for static variable: " +
                staticVarName
            );
          },
          (err) => {
            assert(false, "Failed to execute document symbol provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentSymbolProvider includes static constant", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDocumentSymbolProvider", uri)
        .then(
          (symbols: vscode.DocumentSymbol[]) => {
            let staticConstName = "STATIC_CONST";
            assert.ok(
              findDocumentSymbol(
                symbols,
                new vscode.DocumentSymbol(
                  staticConstName,
                  null,
                  vscode.SymbolKind.Constant,
                  createRange(5, 22),
                  createRange(5, 22)
                )
              ),
              "vscode.executeDocumentSymbolProvider failed to provide symbol for static constant: " +
                staticConstName
            );
          },
          (err) => {
            assert(false, "Failed to execute document symbol provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentSymbolProvider includes static function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDocumentSymbolProvider", uri)
        .then(
          (symbols: vscode.DocumentSymbol[]) => {
            let staticFunctionName = "staticFunction";
            assert.ok(
              findDocumentSymbol(
                symbols,
                new vscode.DocumentSymbol(
                  staticFunctionName,
                  null,
                  vscode.SymbolKind.Method,
                  createRange(7, 28),
                  createRange(7, 28)
                )
              ),
              "vscode.executeDocumentSymbolProvider failed to provide symbol for static function: " +
                staticFunctionName
            );
          },
          (err) => {
            assert(false, "Failed to execute document symbol provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentSymbolProvider includes static property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDocumentSymbolProvider", uri)
        .then(
          (symbols: vscode.DocumentSymbol[]) => {
            let staticPropertyName = "staticProperty";
            assert.ok(
              findDocumentSymbol(
                symbols,
                new vscode.DocumentSymbol(
                  staticPropertyName,
                  null,
                  vscode.SymbolKind.Property,
                  createRange(11, 29),
                  createRange(11, 29)
                )
              ),
              "vscode.executeDocumentSymbolProvider failed to provide symbol for static variable: " +
                staticPropertyName
            );
          },
          (err) => {
            assert(false, "Failed to execute document symbol provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentSymbolProvider includes internal class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDocumentSymbolProvider", uri)
        .then(
          (symbols: vscode.DocumentSymbol[]) => {
            let className = "MainInternalClass";
            assert.ok(
              findDocumentSymbol(
                symbols,
                new vscode.DocumentSymbol(
                  className,
                  null,
                  vscode.SymbolKind.Class,
                  createRange(34, 6),
                  createRange(34, 6)
                )
              ),
              "vscode.executeDocumentSymbolProvider failed to provide symbol for internal class: " +
                className
            );
          },
          (err) => {
            assert(false, "Failed to execute document symbol provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentSymbolProvider includes member variable in internal class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDocumentSymbolProvider", uri)
        .then(
          (symbols: vscode.DocumentSymbol[]) => {
            let memberVarName = "internalClassMemberVar";
            assert.ok(
              findDocumentSymbol(
                symbols,
                new vscode.DocumentSymbol(
                  memberVarName,
                  null,
                  vscode.SymbolKind.Field,
                  createRange(36, 13),
                  createRange(36, 13)
                )
              ),
              "vscode.executeDocumentSymbolProvider failed to provide symbol for member variable in internal class: " +
                memberVarName
            );
          },
          (err) => {
            assert(false, "Failed to execute document symbol provider: " + uri);
          }
        );
    });
  });
});

suite("workspace symbol provider: Application workspace", () => {
  suiteTeardown(revertAndCloseAllEditors);
  test("vscode.executeWorkspaceSymbolProvider includes class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    let query = "Main";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeWorkspaceSymbolProvider", query)
        .then(
          (symbols: vscode.SymbolInformation[]) => {
            assert.ok(
              findSymbolInformation(
                symbols,
                new vscode.SymbolInformation(
                  query,
                  vscode.SymbolKind.Class,
                  createRange(2, 14),
                  uri,
                  "No Package"
                )
              ),
              "vscode.executeWorkspaceSymbolProvider failed to provide symbol for class: " +
                query
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeWorkspaceSymbolProvider includes constructor", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    let query = "Main";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeWorkspaceSymbolProvider", query)
        .then(
          (symbols: vscode.SymbolInformation[]) => {
            assert.ok(
              findSymbolInformation(
                symbols,
                new vscode.SymbolInformation(
                  query,
                  vscode.SymbolKind.Constructor,
                  createRange(16, 18),
                  uri,
                  "Main"
                )
              ),
              "vscode.executeWorkspaceSymbolProvider failed to provide symbol for constructor: " +
                query
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeWorkspaceSymbolProvider includes class in package", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    let query = "PackageClass";
    let packageName = "com.example";
    let classUri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "PackageClass.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeWorkspaceSymbolProvider", query)
        .then(
          (symbols: vscode.SymbolInformation[]) => {
            assert.ok(
              findSymbolInformation(
                symbols,
                new vscode.SymbolInformation(
                  query,
                  vscode.SymbolKind.Class,
                  createRange(2, 14),
                  classUri,
                  packageName
                )
              ),
              "vscode.executeWorkspaceSymbolProvider failed to provide symbol for class: " +
                query
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeWorkspaceSymbolProvider includes member variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    let query = "memberVar";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeWorkspaceSymbolProvider", query)
        .then(
          (symbols: vscode.SymbolInformation[]) => {
            assert.ok(
              findSymbolInformation(
                symbols,
                new vscode.SymbolInformation(
                  query,
                  vscode.SymbolKind.Field,
                  createRange(21, 13),
                  uri,
                  "Main"
                )
              ),
              "vscode.executeWorkspaceSymbolProvider failed to provide symbol for member variable: " +
                query
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeWorkspaceSymbolProvider includes member function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    let query = "memberFunction";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeWorkspaceSymbolProvider", query)
        .then(
          (symbols: vscode.SymbolInformation[]) => {
            assert.ok(
              findSymbolInformation(
                symbols,
                new vscode.SymbolInformation(
                  query,
                  vscode.SymbolKind.Method,
                  createRange(23, 19),
                  uri,
                  "Main"
                )
              ),
              "vscode.executeWorkspaceSymbolProvider failed to provide symbol for member function: " +
                query
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeWorkspaceSymbolProvider includes member property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    let query = "memberProperty";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeWorkspaceSymbolProvider", query)
        .then(
          (symbols: vscode.SymbolInformation[]) => {
            assert.ok(
              findSymbolInformation(
                symbols,
                new vscode.SymbolInformation(
                  query,
                  vscode.SymbolKind.Property,
                  createRange(27, 22),
                  uri,
                  "Main"
                )
              ),
              "vscode.executeWorkspaceSymbolProvider failed to provide symbol for member property: " +
                query
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeWorkspaceSymbolProvider includes static variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    let query = "staticVar";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeWorkspaceSymbolProvider", query)
        .then(
          (symbols: vscode.SymbolInformation[]) => {
            assert.ok(
              findSymbolInformation(
                symbols,
                new vscode.SymbolInformation(
                  query,
                  vscode.SymbolKind.Field,
                  createRange(4, 21),
                  uri,
                  "Main"
                )
              ),
              "vscode.executeWorkspaceSymbolProvider failed to provide symbol for static variable: " +
                query
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeWorkspaceSymbolProvider includes static constant", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    let query = "STATIC_CONST";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeWorkspaceSymbolProvider", query)
        .then(
          (symbols: vscode.SymbolInformation[]) => {
            assert.ok(
              findSymbolInformation(
                symbols,
                new vscode.SymbolInformation(
                  query,
                  vscode.SymbolKind.Constant,
                  createRange(5, 22),
                  uri,
                  "Main"
                )
              ),
              "vscode.executeWorkspaceSymbolProvider failed to provide symbol for static constant: " +
                query
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeWorkspaceSymbolProvider includes static function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    let query = "staticFunction";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeWorkspaceSymbolProvider", query)
        .then(
          (symbols: vscode.SymbolInformation[]) => {
            assert.ok(
              findSymbolInformation(
                symbols,
                new vscode.SymbolInformation(
                  query,
                  vscode.SymbolKind.Method,
                  createRange(7, 28),
                  uri,
                  "Main"
                )
              ),
              "vscode.executeWorkspaceSymbolProvider failed to provide symbol for static function: " +
                query
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeWorkspaceSymbolProvider includes static property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    let query = "staticProperty";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeWorkspaceSymbolProvider", query)
        .then(
          (symbols: vscode.SymbolInformation[]) => {
            assert.ok(
              findSymbolInformation(
                symbols,
                new vscode.SymbolInformation(
                  query,
                  vscode.SymbolKind.Property,
                  createRange(11, 29),
                  uri,
                  "Main"
                )
              ),
              "vscode.executeWorkspaceSymbolProvider failed to provide symbol for static property: " +
                query
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeWorkspaceSymbolProvider includes internal class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    let query = "MainInternalClass";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeWorkspaceSymbolProvider", query)
        .then(
          (symbols: vscode.SymbolInformation[]) => {
            assert.ok(
              findSymbolInformation(
                symbols,
                new vscode.SymbolInformation(
                  query,
                  vscode.SymbolKind.Class,
                  createRange(34, 6),
                  uri,
                  "No Package"
                )
              ),
              "vscode.executeWorkspaceSymbolProvider did not provide internal class"
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeWorkspaceSymbolProvider includes member variable in internal class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    let query = "internalClassMemberVar";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeWorkspaceSymbolProvider", query)
        .then(
          (symbols: vscode.SymbolInformation[]) => {
            assert.ok(
              findSymbolInformation(
                symbols,
                new vscode.SymbolInformation(
                  query,
                  vscode.SymbolKind.Field,
                  createRange(36, 13),
                  uri,
                  "MainInternalClass"
                )
              ),
              "vscode.executeWorkspaceSymbolProvider failed to provide symbol for member variable in internal class: " +
                query
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeWorkspaceSymbolProvider includes symbols in unreferenced files", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    let query = "Unreferenced";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let qualifiedClassName = "com.example.UnreferencedClass";
      let classUri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "com",
          "example",
          "UnreferencedClass.as"
        )
      );
      return vscode.commands
        .executeCommand("vscode.executeWorkspaceSymbolProvider", query)
        .then(
          (symbols: vscode.SymbolInformation[]) => {
            assert.notStrictEqual(
              symbols.length,
              0,
              "vscode.executeWorkspaceSymbolProvider failed to provide unreferenced symbols for query: " +
                query
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeWorkspaceSymbolProvider includes class in unreferenced file", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    let query = "Unreferenced";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let symbolName = "UnreferencedClass";
      let packageName = "com.example";
      let classUri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "com",
          "example",
          "UnreferencedClass.as"
        )
      );
      return vscode.commands
        .executeCommand("vscode.executeWorkspaceSymbolProvider", query)
        .then(
          (symbols: vscode.SymbolInformation[]) => {
            assert.ok(
              findSymbolInformation(
                symbols,
                new vscode.SymbolInformation(
                  symbolName,
                  vscode.SymbolKind.Class,
                  createRange(2, 14),
                  classUri,
                  packageName
                )
              ),
              "vscode.executeWorkspaceSymbolProvider failed to provide symbol for class in unreferenced file with query: " +
                query
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeWorkspaceSymbolProvider includes constructor in unreferenced file", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    let query = "Unreferenced";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let symbolName = "UnreferencedClass";
      let packageName = "com.example";
      let classUri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "com",
          "example",
          "UnreferencedClass.as"
        )
      );
      return vscode.commands
        .executeCommand("vscode.executeWorkspaceSymbolProvider", query)
        .then(
          (symbols: vscode.SymbolInformation[]) => {
            assert.ok(
              findSymbolInformation(
                symbols,
                new vscode.SymbolInformation(
                  symbolName,
                  vscode.SymbolKind.Constructor,
                  createRange(4, 18),
                  classUri,
                  packageName
                )
              ),
              "vscode.executeWorkspaceSymbolProvider failed to provide symbol for constructor in unreferenced file with query: " +
                query
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeWorkspaceSymbolProvider includes interface in unreferenced file", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    let query = "Unreferenced";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let symbolName = "UnreferencedInterface";
      let packageName = "com.example.core";
      let interfaceUri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "com",
          "example",
          "core",
          "UnreferencedInterface.as"
        )
      );
      return vscode.commands
        .executeCommand("vscode.executeWorkspaceSymbolProvider", query)
        .then(
          (symbols: vscode.SymbolInformation[]) => {
            assert.ok(
              findSymbolInformation(
                symbols,
                new vscode.SymbolInformation(
                  symbolName,
                  vscode.SymbolKind.Interface,
                  createRange(2, 18),
                  interfaceUri,
                  packageName
                )
              ),
              "vscode.executeWorkspaceSymbolProvider failed to provide symbol for interface in unreferenced file with query: " +
                query
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeWorkspaceSymbolProvider includes class with camel-case shorthand", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );
    let resultUri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "workspaceSymbols",
        "WSFindMe1.as"
      )
    );
    let query = "WFiMe";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeWorkspaceSymbolProvider", query)
        .then(
          (symbols: vscode.SymbolInformation[]) => {
            assert.ok(
              findSymbolInformation(
                symbols,
                new vscode.SymbolInformation(
                  "WSFindMe1",
                  vscode.SymbolKind.Class,
                  createRange(2, 14),
                  resultUri,
                  "com.example.workspaceSymbols"
                )
              ),
              "vscode.executeWorkspaceSymbolProvider failed to provide symbol for class: " +
                query
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
});

suite("signature help provider: Application workspace", () => {
  suiteTeardown(revertAndCloseAllEditors);
  test("vscode.executeSignatureHelpProvider provides help for local function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "SignatureHelp.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeSignatureHelpProvider",
          uri,
          new vscode.Position(19, 17),
          "("
        )
        .then(
          (signatureHelp: vscode.SignatureHelp) => {
            assert.strictEqual(
              signatureHelp.signatures.length,
              1,
              "Signature help not provided for local function"
            );
            assert.strictEqual(
              signatureHelp.activeSignature,
              0,
              "Active signature incorrect for local function"
            );
            assert.strictEqual(
              signatureHelp.activeParameter,
              0,
              "Active parameter incorrect for local function"
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeSignatureHelpProvider provides help for member function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "SignatureHelp.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeSignatureHelpProvider",
          uri,
          new vscode.Position(20, 18),
          "("
        )
        .then(
          (signatureHelp: vscode.SignatureHelp) => {
            assert.strictEqual(
              signatureHelp.signatures.length,
              1,
              "Signature help not provided for member function"
            );
            assert.strictEqual(
              signatureHelp.activeSignature,
              0,
              "Active signature incorrect for member function"
            );
            assert.strictEqual(
              signatureHelp.activeParameter,
              0,
              "Active parameter incorrect for member function"
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeSignatureHelpProvider provides help for member function with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "SignatureHelp.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeSignatureHelpProvider",
          uri,
          new vscode.Position(21, 23),
          "("
        )
        .then(
          (signatureHelp: vscode.SignatureHelp) => {
            assert.strictEqual(
              signatureHelp.signatures.length,
              1,
              "Signature help not provided for member function with member access operator on this"
            );
            assert.strictEqual(
              signatureHelp.activeSignature,
              0,
              "Active signature incorrect for member function with member access operator on this"
            );
            assert.strictEqual(
              signatureHelp.activeParameter,
              0,
              "Active parameter incorrect for member function with member access operator on this"
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeSignatureHelpProvider provides help for static function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "SignatureHelp.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeSignatureHelpProvider",
          uri,
          new vscode.Position(22, 18),
          "("
        )
        .then(
          (signatureHelp: vscode.SignatureHelp) => {
            assert.strictEqual(
              signatureHelp.signatures.length,
              1,
              "Signature help not provided for static function"
            );
            assert.strictEqual(
              signatureHelp.activeSignature,
              0,
              "Active signature incorrect for static function"
            );
            assert.strictEqual(
              signatureHelp.activeParameter,
              0,
              "Active parameter incorrect for static function"
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeSignatureHelpProvider provides help for static function with member access operator on class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "SignatureHelp.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeSignatureHelpProvider",
          uri,
          new vscode.Position(23, 32),
          "("
        )
        .then(
          (signatureHelp: vscode.SignatureHelp) => {
            assert.strictEqual(
              signatureHelp.signatures.length,
              1,
              "Signature help not provided for static function with member access operator on class"
            );
            assert.strictEqual(
              signatureHelp.activeSignature,
              0,
              "Active signature incorrect for static function with member access operator on class"
            );
            assert.strictEqual(
              signatureHelp.activeParameter,
              0,
              "Active parameter incorrect for static function with member access operator on class"
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeSignatureHelpProvider provides help for package function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "SignatureHelp.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeSignatureHelpProvider",
          uri,
          new vscode.Position(24, 19),
          "("
        )
        .then(
          (signatureHelp: vscode.SignatureHelp) => {
            assert.strictEqual(
              signatureHelp.signatures.length,
              1,
              "Signature help not provided for package function"
            );
            assert.strictEqual(
              signatureHelp.activeSignature,
              0,
              "Active signature incorrect for package function"
            );
            assert.strictEqual(
              signatureHelp.activeParameter,
              0,
              "Active parameter incorrect for package function"
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeSignatureHelpProvider provides help for fully-qualified package function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "SignatureHelp.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeSignatureHelpProvider",
          uri,
          new vscode.Position(25, 31),
          "("
        )
        .then(
          (signatureHelp: vscode.SignatureHelp) => {
            assert.strictEqual(
              signatureHelp.signatures.length,
              1,
              "Signature help not provided for fully-qualified package function"
            );
            assert.strictEqual(
              signatureHelp.activeSignature,
              0,
              "Active signature incorrect for fully-qualified package function"
            );
            assert.strictEqual(
              signatureHelp.activeParameter,
              0,
              "Active parameter incorrect for fully-qualified package function"
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeSignatureHelpProvider provides help for internal function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "SignatureHelp.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeSignatureHelpProvider",
          uri,
          new vscode.Position(26, 20),
          "("
        )
        .then(
          (signatureHelp: vscode.SignatureHelp) => {
            assert.strictEqual(
              signatureHelp.signatures.length,
              1,
              "Signature help not provided for internal function"
            );
            assert.strictEqual(
              signatureHelp.activeSignature,
              0,
              "Active signature incorrect for internal function"
            );
            assert.strictEqual(
              signatureHelp.activeParameter,
              0,
              "Active parameter incorrect for internal function"
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeSignatureHelpProvider provides help for super constructor", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "SignatureHelp.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeSignatureHelpProvider",
          uri,
          new vscode.Position(27, 9),
          "("
        )
        .then(
          (signatureHelp: vscode.SignatureHelp) => {
            assert.strictEqual(
              signatureHelp.signatures.length,
              1,
              "Signature help not provided for super constructor"
            );
            assert.strictEqual(
              signatureHelp.activeSignature,
              0,
              "Active signature incorrect for super constructor"
            );
            assert.strictEqual(
              signatureHelp.activeParameter,
              0,
              "Active parameter incorrect for super constructor"
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeSignatureHelpProvider provides help for super member method", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "SignatureHelp.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeSignatureHelpProvider",
          uri,
          new vscode.Position(28, 27),
          "("
        )
        .then(
          (signatureHelp: vscode.SignatureHelp) => {
            assert.strictEqual(
              signatureHelp.signatures.length,
              1,
              "Signature help not provided for super member method"
            );
            assert.strictEqual(
              signatureHelp.activeSignature,
              0,
              "Active signature incorrect for super member method"
            );
            assert.strictEqual(
              signatureHelp.activeParameter,
              0,
              "Active parameter incorrect for super member method"
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeSignatureHelpProvider must not provide help for private super member method", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "SignatureHelp.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeSignatureHelpProvider",
          uri,
          new vscode.Position(29, 34),
          "("
        )
        .then(
          (signatureHelp: vscode.SignatureHelp) => {
            assert.strictEqual(
              signatureHelp.signatures.length,
              0,
              "Signature help incorrectly provided for private super member method"
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
  test("vscode.executeSignatureHelpProvider provides help for constructor of new instance", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "SignatureHelp.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeSignatureHelpProvider",
          uri,
          new vscode.Position(30, 25),
          "("
        )
        .then(
          (signatureHelp: vscode.SignatureHelp) => {
            assert.strictEqual(
              signatureHelp.signatures.length,
              1,
              "Signature help not provided for constructor of new instance"
            );
            assert.strictEqual(
              signatureHelp.activeSignature,
              0,
              "Active signature incorrect for constructor of new instance"
            );
            assert.strictEqual(
              signatureHelp.activeParameter,
              0,
              "Active parameter incorrect for constructor of new instance"
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
});

suite("definition provider: Application workspace", () => {
  suiteTeardown(revertAndCloseAllEditors);
  test("vscode.executeDefinitionProvider finds definition of local variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(90, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of local variable definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for local variable definition"
            );
            assert.strictEqual(
              location.range.start.line,
              42,
              "vscode.executeDefinitionProvider provided incorrect line for local variable definition"
            );
            assert.strictEqual(
              location.range.start.character,
              7,
              "vscode.executeDefinitionProvider provided incorrect character for local variable definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of local function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(92, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of local function definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for local function definition"
            );
            assert.strictEqual(
              location.range.start.line,
              43,
              "vscode.executeDefinitionProvider provided incorrect line for local function definition"
            );
            assert.strictEqual(
              location.range.start.character,
              12,
              "vscode.executeDefinitionProvider provided incorrect character for local function definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of member variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(54, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of member variable definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for member variable definition"
            );
            assert.strictEqual(
              location.range.start.line,
              14,
              "vscode.executeDefinitionProvider provided incorrect line for member variable definition"
            );
            assert.strictEqual(
              location.range.start.character,
              14,
              "vscode.executeDefinitionProvider provided incorrect character for member variable definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of member variable with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(55, 10);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of member variable definition with member access operator on this: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for member variable definition with member access operator on this"
            );
            assert.strictEqual(
              location.range.start.line,
              14,
              "vscode.executeDefinitionProvider provided incorrect line for member variable definition with member access operator on this"
            );
            assert.strictEqual(
              location.range.start.character,
              14,
              "vscode.executeDefinitionProvider provided incorrect character for member variable definition with member access operator on this"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of member function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(45, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of member function definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for member function definition"
            );
            assert.strictEqual(
              location.range.start.line,
              16,
              "vscode.executeDefinitionProvider provided incorrect line for member function definition"
            );
            assert.strictEqual(
              location.range.start.character,
              19,
              "vscode.executeDefinitionProvider provided incorrect character for member function definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of member function with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(46, 10);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of member function definition with member access operator on this: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for member function definition with member access operator on this"
            );
            assert.strictEqual(
              location.range.start.line,
              16,
              "vscode.executeDefinitionProvider provided incorrect line for member function definition with member access operator on this"
            );
            assert.strictEqual(
              location.range.start.character,
              19,
              "vscode.executeDefinitionProvider provided incorrect character for member function definition with member access operator on this"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of member property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(57, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of member property definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for member property definition"
            );
            assert.strictEqual(
              location.range.start.line,
              20,
              "vscode.executeDefinitionProvider provided incorrect line for member property definition"
            );
            assert.strictEqual(
              location.range.start.character,
              22,
              "vscode.executeDefinitionProvider provided incorrect character for member property definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of member property with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(58, 10);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of member property definition with member access operator on this: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for member property definition with member access operator on this"
            );
            assert.strictEqual(
              location.range.start.line,
              20,
              "vscode.executeDefinitionProvider provided incorrect line for member property definition with member access operator on this"
            );
            assert.strictEqual(
              location.range.start.character,
              22,
              "vscode.executeDefinitionProvider provided incorrect character for member property definition with member access operator on this"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of static variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(51, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of static variable definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for static variable definition"
            );
            assert.strictEqual(
              location.range.start.line,
              8,
              "vscode.executeDefinitionProvider provided incorrect line for static variable definition"
            );
            assert.strictEqual(
              location.range.start.character,
              20,
              "vscode.executeDefinitionProvider provided incorrect character for static variable definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of static variable with member access operator on class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(52, 17);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of static variable definition with member access operator on class: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for static variable definition with member access operator on class"
            );
            assert.strictEqual(
              location.range.start.line,
              8,
              "vscode.executeDefinitionProvider provided incorrect line for static variable definition with member access operator on class"
            );
            assert.strictEqual(
              location.range.start.character,
              20,
              "vscode.executeDefinitionProvider provided incorrect character for static variable definition with member access operator on class"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of static function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(48, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of static function definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for static function definition"
            );
            assert.strictEqual(
              location.range.start.line,
              10,
              "vscode.executeDefinitionProvider provided incorrect line for static function definition"
            );
            assert.strictEqual(
              location.range.start.character,
              26,
              "vscode.executeDefinitionProvider provided incorrect character for static function definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of static function with member access operator on class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(49, 17);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of static function definition with member access operator on class: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for static function definition with member access operator on class"
            );
            assert.strictEqual(
              location.range.start.line,
              10,
              "vscode.executeDefinitionProvider provided incorrect line for static function definition with member access operator on class"
            );
            assert.strictEqual(
              location.range.start.character,
              26,
              "vscode.executeDefinitionProvider provided incorrect character for static function definition with member access operator on class"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of static property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(60, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of static property definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for static property definition"
            );
            assert.strictEqual(
              location.range.start.line,
              29,
              "vscode.executeDefinitionProvider provided incorrect line for static property definition"
            );
            assert.strictEqual(
              location.range.start.character,
              29,
              "vscode.executeDefinitionProvider provided incorrect character for static property definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of static property with member access operator on class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(61, 17);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of static property definition with member access operator on class: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for static property definition with member access operator on class"
            );
            assert.strictEqual(
              location.range.start.line,
              29,
              "vscode.executeDefinitionProvider provided incorrect line for static property definition with member access operator on class"
            );
            assert.strictEqual(
              location.range.start.character,
              29,
              "vscode.executeDefinitionProvider provided incorrect character for static property definition with member access operator on class"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of package function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "packageFunction.as"
      )
    );
    let position = new vscode.Position(84, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of package function definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              definitionURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for package function definition"
            );
            assert.strictEqual(
              location.range.start.line,
              2,
              "vscode.executeDefinitionProvider provided incorrect line for package function definition"
            );
            assert.strictEqual(
              location.range.start.character,
              17,
              "vscode.executeDefinitionProvider provided incorrect character for package function definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of fully-qualified package function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "packageFunction.as"
      )
    );
    let position = new vscode.Position(85, 17);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of fully-qualified package function definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              definitionURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for fully-qualified package function definition"
            );
            assert.strictEqual(
              location.range.start.line,
              2,
              "vscode.executeDefinitionProvider provided incorrect line for fully-qualified package function definition"
            );
            assert.strictEqual(
              location.range.start.character,
              17,
              "vscode.executeDefinitionProvider provided incorrect character for fully-qualified package function definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of package variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "packageVar.as"
      )
    );
    let position = new vscode.Position(87, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of package variable definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              definitionURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for package variable definition"
            );
            assert.strictEqual(
              location.range.start.line,
              2,
              "vscode.executeDefinitionProvider provided incorrect line for package variable definition"
            );
            assert.strictEqual(
              location.range.start.character,
              12,
              "vscode.executeDefinitionProvider provided incorrect character for package variable definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of fully-qualified package variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "packageVar.as"
      )
    );
    let position = new vscode.Position(88, 17);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of fully-qualified package variable definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              definitionURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for fully-qualified package variable definition"
            );
            assert.strictEqual(
              location.range.start.line,
              2,
              "vscode.executeDefinitionProvider provided incorrect line for fully-qualified package variable definition"
            );
            assert.strictEqual(
              location.range.start.character,
              12,
              "vscode.executeDefinitionProvider provided incorrect character for fully-qualified package variable definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of super static variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(74, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of super static variable definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              definitionURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for super static variable definition"
            );
            assert.strictEqual(
              location.range.start.line,
              4,
              "vscode.executeDefinitionProvider provided incorrect line for super static variable definition"
            );
            assert.strictEqual(
              location.range.start.character,
              20,
              "vscode.executeDefinitionProvider provided incorrect character for super static variable definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of super static variable with member access operator on superclass", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(75, 22);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of super static variable definition with member access operator on superclass: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              definitionURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for super static variable definition with member access operator on superclass"
            );
            assert.strictEqual(
              location.range.start.line,
              4,
              "vscode.executeDefinitionProvider provided incorrect line for super static variable definition with member access operator on superclass"
            );
            assert.strictEqual(
              location.range.start.character,
              20,
              "vscode.executeDefinitionProvider provided incorrect character for super static variable definition with member access operator on superclass"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of super static property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(81, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of super static property definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              definitionURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for super static property definition"
            );
            assert.strictEqual(
              location.range.start.line,
              6,
              "vscode.executeDefinitionProvider provided incorrect line for super static property definition"
            );
            assert.strictEqual(
              location.range.start.character,
              29,
              "vscode.executeDefinitionProvider provided incorrect character for super static property definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of super static property with member access operator on superclass", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(82, 22);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of super static property definition with member access operator on superclass: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              definitionURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for super static property definition with member access operator on superclass"
            );
            assert.strictEqual(
              location.range.start.line,
              6,
              "vscode.executeDefinitionProvider provided incorrect line for super static property definition with member access operator on superclass"
            );
            assert.strictEqual(
              location.range.start.character,
              29,
              "vscode.executeDefinitionProvider provided incorrect character for super static property definition with member access operator on superclass"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of super static function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(67, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of super static function definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              definitionURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for super static function definition"
            );
            assert.strictEqual(
              location.range.start.line,
              15,
              "vscode.executeDefinitionProvider provided incorrect line for super static function definition"
            );
            assert.strictEqual(
              location.range.start.character,
              28,
              "vscode.executeDefinitionProvider provided incorrect character for super static function definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of super static function with member access operator on superclass", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(68, 22);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of super static function definition with member access operator on superclass: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              definitionURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for super static function definition with member access operator on superclass"
            );
            assert.strictEqual(
              location.range.start.line,
              15,
              "vscode.executeDefinitionProvider provided incorrect line for super static function definition with member access operator on superclass"
            );
            assert.strictEqual(
              location.range.start.character,
              28,
              "vscode.executeDefinitionProvider provided incorrect character for super static function definition with member access operator on superclass"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of super member function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(63, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of super member function definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              definitionURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for super member function definition"
            );
            assert.strictEqual(
              location.range.start.line,
              30,
              "vscode.executeDefinitionProvider provided incorrect line for super member function definition"
            );
            assert.strictEqual(
              location.range.start.character,
              21,
              "vscode.executeDefinitionProvider provided incorrect character for super member function definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of super member function with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(64, 10);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of super member function definition with member access operator on this: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              definitionURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for super member function definition with member access operator on this"
            );
            assert.strictEqual(
              location.range.start.line,
              30,
              "vscode.executeDefinitionProvider provided incorrect line for super member function definition with member access operator on this"
            );
            assert.strictEqual(
              location.range.start.character,
              21,
              "vscode.executeDefinitionProvider provided incorrect character for super member function definition with member access operator on this"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of super member function with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(65, 11);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of super member function definition with member access operator on super: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              definitionURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for super member function definition with member access operator on super"
            );
            assert.strictEqual(
              location.range.start.line,
              30,
              "vscode.executeDefinitionProvider provided incorrect line for super member function definition with member access operator on super"
            );
            assert.strictEqual(
              location.range.start.character,
              21,
              "vscode.executeDefinitionProvider provided incorrect character for super member function definition with member access operator on super"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of super member variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(70, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of super member variable definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              definitionURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for super member variable definition"
            );
            assert.strictEqual(
              location.range.start.line,
              19,
              "vscode.executeDefinitionProvider provided incorrect line for super member variable definition"
            );
            assert.strictEqual(
              location.range.start.character,
              13,
              "vscode.executeDefinitionProvider provided incorrect character for super member variable definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of super member variable with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(71, 10);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of super member variable definition with member access operator on this: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              definitionURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for super member variable definition with member access operator on this"
            );
            assert.strictEqual(
              location.range.start.line,
              19,
              "vscode.executeDefinitionProvider provided incorrect line for super member variable definition with member access operator on this"
            );
            assert.strictEqual(
              location.range.start.character,
              13,
              "vscode.executeDefinitionProvider provided incorrect character for super member variable definition with member access operator on this"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of super member variable with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(72, 11);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of super member variable definition with member access operator on super: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              definitionURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for super member variable definition with member access operator on super"
            );
            assert.strictEqual(
              location.range.start.line,
              19,
              "vscode.executeDefinitionProvider provided incorrect line for super member variable definition with member access operator on super"
            );
            assert.strictEqual(
              location.range.start.character,
              13,
              "vscode.executeDefinitionProvider provided incorrect character for super member variable definition with member access operator on super"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of super member property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(77, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of super member property definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              definitionURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for super member property definition"
            );
            assert.strictEqual(
              location.range.start.line,
              21,
              "vscode.executeDefinitionProvider provided incorrect line for super member property definition"
            );
            assert.strictEqual(
              location.range.start.character,
              22,
              "vscode.executeDefinitionProvider provided incorrect character for super member property definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of super member property with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(78, 10);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of super member property definition with member access operator on this: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              definitionURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for super member property definition with member access operator on this"
            );
            assert.strictEqual(
              location.range.start.line,
              21,
              "vscode.executeDefinitionProvider provided incorrect line for super member property definition with member access operator on this"
            );
            assert.strictEqual(
              location.range.start.character,
              22,
              "vscode.executeDefinitionProvider provided incorrect character for super member property definition with member access operator on this"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of super member property with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(79, 11);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of super member property definition with member access operator on super: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              definitionURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for super member property definition with member access operator on super"
            );
            assert.strictEqual(
              location.range.start.line,
              21,
              "vscode.executeDefinitionProvider provided incorrect line for super member property definition with member access operator on super"
            );
            assert.strictEqual(
              location.range.start.character,
              22,
              "vscode.executeDefinitionProvider provided incorrect character for super member property definition with member access operator on super"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of constructor", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(107, 10);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of super member property definition with member access operator on super: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              definitionURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for super member property definition with member access operator on super"
            );
            assert.strictEqual(
              location.range.start.line,
              34,
              "vscode.executeDefinitionProvider provided incorrect line for super member property definition with member access operator on super"
            );
            assert.strictEqual(
              location.range.start.character,
              18,
              "vscode.executeDefinitionProvider provided incorrect character for super member property definition with member access operator on super"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of file-internal variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(94, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of file-internal variable definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for file-internal variable definition"
            );
            assert.strictEqual(
              location.range.start.line,
              113,
              "vscode.executeDefinitionProvider provided incorrect line for file-internal variable definition"
            );
            assert.strictEqual(
              location.range.start.character,
              4,
              "vscode.executeDefinitionProvider provided incorrect character for file-internal variable definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of file-internal function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(95, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of file-internal function definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for file-internal function definition"
            );
            assert.strictEqual(
              location.range.start.line,
              112,
              "vscode.executeDefinitionProvider provided incorrect line for file-internal function definition"
            );
            assert.strictEqual(
              location.range.start.character,
              9,
              "vscode.executeDefinitionProvider provided incorrect character for file-internal function definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of file-internal class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(97, 37);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of file-internal class definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for file-internal class definition"
            );
            assert.strictEqual(
              location.range.start.line,
              115,
              "vscode.executeDefinitionProvider provided incorrect line for file-internal class definition"
            );
            assert.strictEqual(
              location.range.start.character,
              6,
              "vscode.executeDefinitionProvider provided incorrect character for file-internal class definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of file-internal constructor", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(97, 64);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of file-internal class definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for file-internal class definition"
            );
            assert.strictEqual(
              location.range.start.line,
              147,
              "vscode.executeDefinitionProvider provided incorrect line for file-internal class definition"
            );
            assert.strictEqual(
              location.range.start.character,
              17,
              "vscode.executeDefinitionProvider provided incorrect character for file-internal class definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of file-internal member function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(99, 33);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of file-internal member function definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for file-internal member function definition"
            );
            assert.strictEqual(
              location.range.start.line,
              143,
              "vscode.executeDefinitionProvider provided incorrect line for file-internal member function definition"
            );
            assert.strictEqual(
              location.range.start.character,
              17,
              "vscode.executeDefinitionProvider provided incorrect character for file-internal member function definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of file-internal member variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(100, 33);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of file-internal member variable definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for file-internal member variable definition"
            );
            assert.strictEqual(
              location.range.start.line,
              132,
              "vscode.executeDefinitionProvider provided incorrect line for file-internal member variable definition"
            );
            assert.strictEqual(
              location.range.start.character,
              12,
              "vscode.executeDefinitionProvider provided incorrect character for file-internal member variable definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of file-internal member property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(101, 33);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of file-internal member property definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for file-internal member property definition"
            );
            assert.strictEqual(
              location.range.start.line,
              134,
              "vscode.executeDefinitionProvider provided incorrect line for file-internal member property definition"
            );
            assert.strictEqual(
              location.range.start.character,
              21,
              "vscode.executeDefinitionProvider provided incorrect character for file-internal member property definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of file-internal static property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(105, 25);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of file-internal static property definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for file-internal static property definition"
            );
            assert.strictEqual(
              location.range.start.line,
              119,
              "vscode.executeDefinitionProvider provided incorrect line for file-internal static property definition"
            );
            assert.strictEqual(
              location.range.start.character,
              28,
              "vscode.executeDefinitionProvider provided incorrect character for file-internal static property definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of file-internal static variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(104, 25);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of file-internal static variable definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for file-internal static variable definition"
            );
            assert.strictEqual(
              location.range.start.line,
              117,
              "vscode.executeDefinitionProvider provided incorrect line for file-internal static variable definition"
            );
            assert.strictEqual(
              location.range.start.character,
              19,
              "vscode.executeDefinitionProvider provided incorrect character for file-internal static variable definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of file-internal static function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(103, 25);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of file-internal static function definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for file-internal static function definition"
            );
            assert.strictEqual(
              location.range.start.line,
              128,
              "vscode.executeDefinitionProvider provided incorrect line for file-internal static function definition"
            );
            assert.strictEqual(
              location.range.start.character,
              24,
              "vscode.executeDefinitionProvider provided incorrect character for file-internal static function definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
});

suite("type definition provider: Application workspace", () => {
  suiteTeardown(revertAndCloseAllEditors);
  test("vscode.executeTypeDefinitionProvider finds type definition of local variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "TypeDefinitions.as"
      )
    );
    let expected = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "typeDefinition",
        "LocalVarTypeDefinition.as"
      )
    );
    let position = new vscode.Position(14, 10);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeTypeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeTypeDefinitionProvider failed to provide location of local variable definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              expected.toString(),
              "vscode.executeTypeDefinitionProvider provided incorrect uri for local variable definition"
            );
            assert.strictEqual(
              location.range.start.line,
              2,
              "vscode.executeTypeDefinitionProvider provided incorrect line for local variable definition"
            );
            assert.strictEqual(
              location.range.start.character,
              14,
              "vscode.executeTypeDefinitionProvider provided incorrect character for local variable definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute type definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeTypeDefinitionProvider finds type definition of member variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "TypeDefinitions.as"
      )
    );
    let expected = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "typeDefinition",
        "MemberVarTypeDefinition.as"
      )
    );
    let position = new vscode.Position(10, 14);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeTypeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeTypeDefinitionProvider failed to provide location of member variable definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              expected.toString(),
              "vscode.executeTypeDefinitionProvider provided incorrect uri for member variable definition"
            );
            assert.strictEqual(
              location.range.start.line,
              2,
              "vscode.executeTypeDefinitionProvider provided incorrect line for member variable definition"
            );
            assert.strictEqual(
              location.range.start.character,
              14,
              "vscode.executeTypeDefinitionProvider provided incorrect character for member variable definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute type definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeTypeDefinitionProvider finds type definition of static variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "TypeDefinitions.as"
      )
    );
    let expected = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "typeDefinition",
        "StaticVarTypeDefinition.as"
      )
    );
    let position = new vscode.Position(9, 22);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeTypeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeTypeDefinitionProvider failed to provide location of static variable definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              expected.toString(),
              "vscode.executeTypeDefinitionProvider provided incorrect uri for static variable definition"
            );
            assert.strictEqual(
              location.range.start.line,
              2,
              "vscode.executeTypeDefinitionProvider provided incorrect line for static variable definition"
            );
            assert.strictEqual(
              location.range.start.character,
              14,
              "vscode.executeTypeDefinitionProvider provided incorrect character for static variable definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute type definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeTypeDefinitionProvider finds type definition of parameter", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "TypeDefinitions.as"
      )
    );
    let expected = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "typeDefinition",
        "ParameterTypeDefinition.as"
      )
    );
    let position = new vscode.Position(12, 34);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeTypeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeTypeDefinitionProvider failed to provide location of parameter: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              expected.toString(),
              "vscode.executeTypeDefinitionProvider provided incorrect uri for parameter definition"
            );
            assert.strictEqual(
              location.range.start.line,
              2,
              "vscode.executeTypeDefinitionProvider provided incorrect line for parameter definition"
            );
            assert.strictEqual(
              location.range.start.character,
              14,
              "vscode.executeTypeDefinitionProvider provided incorrect character for parameter definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute type definition provider: " + uri);
          }
        );
    });
  });
});

suite("hover provider: Application workspace", () => {
  suiteTeardown(revertAndCloseAllEditors);
  test("vscode.executeHoverProvider displays hover for local variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(90, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for local variable reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for local variable reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("localVar:String") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for local variable reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              90,
              "vscode.executeHoverProvider provided incorrect line for local variable reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              3,
              "vscode.executeHoverProvider provided incorrect character for local variable reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of local function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(92, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for local function reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for local function reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("localFunction():void") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for local function reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              92,
              "vscode.executeDefinitionProvider provided incorrect line for local function reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              3,
              "vscode.executeDefinitionProvider provided incorrect character for local function reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of member variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(54, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for member variable reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for member variable reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("memberVar:Boolean") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for member variable reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              54,
              "vscode.executeHoverProvider provided incorrect line for member variable reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              3,
              "vscode.executeHoverProvider provided incorrect character for member variable reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });

  test("vscode.executeHoverProvider displays hover of member variable with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(55, 10);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for member variable reference with member access operator on this: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for member variable reference with member access operator on this: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("memberVar:Boolean") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for member variable reference with member access operator on this"
            );
            assert.strictEqual(
              hover.range.start.line,
              55,
              "vscode.executeHoverProvider provided incorrect line for member variable reference with member access operator on this"
            );
            assert.strictEqual(
              hover.range.start.character,
              8,
              "vscode.executeHoverProvider provided incorrect character for member variable reference with member access operator on this"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of member function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(45, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for member function reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for member function reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("memberFunction():void") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for member function reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              45,
              "vscode.executeHoverProvider provided incorrect line for member function reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              3,
              "vscode.executeHoverProvider provided incorrect character for member function reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of member function with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(46, 10);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for member function reference with member access operator on this: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for member function reference with member access operator on this: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("memberFunction():void") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for member function reference with member access operator on this"
            );
            assert.strictEqual(
              hover.range.start.line,
              46,
              "vscode.executeHoverProvider provided incorrect line for member function reference with member access operator on this"
            );
            assert.strictEqual(
              hover.range.start.character,
              8,
              "vscode.executeHoverProvider provided incorrect character for member function reference with member access operator on this"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of member property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(57, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for member property reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for member property reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("memberProperty:Boolean") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for member property reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              57,
              "vscode.executeHoverProvider provided incorrect line for member property reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              3,
              "vscode.executeHoverProvider provided incorrect character for member property reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of member property with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(58, 10);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for member property reference with member access operator on this: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for member property reference with member access operator on this: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("memberProperty:Boolean") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for member property reference with member access operator on this"
            );
            assert.strictEqual(
              hover.range.start.line,
              58,
              "vscode.executeHoverProvider provided incorrect line for member property reference with member access operator on this"
            );
            assert.strictEqual(
              hover.range.start.character,
              8,
              "vscode.executeHoverProvider provided incorrect character for member property reference with member access operator on this"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of static variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(51, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for static variable reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for static variable reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("staticVar:Number") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for static variable reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              51,
              "vscode.executeHoverProvider provided incorrect line for static variable reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              3,
              "vscode.executeHoverProvider provided incorrect character for static variable reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of static variable with member access operator on class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(52, 17);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for static variable reference with member access operator on class: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for static variable reference with member access operator on class: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("staticVar:Number") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for static variable reference with member access operator on class"
            );
            assert.strictEqual(
              hover.range.start.line,
              52,
              "vscode.executeHoverProvider provided incorrect line for static variable reference with member access operator on class"
            );
            assert.strictEqual(
              hover.range.start.character,
              15,
              "vscode.executeHoverProvider provided incorrect character for static variable reference with member access operator on class"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of static function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(48, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for static function reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for static function reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("staticFunction():void") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for static function reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              48,
              "vscode.executeHoverProvider provided incorrect line for static function reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              3,
              "vscode.executeHoverProvider provided incorrect character for static function reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of static function with member access operator on class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(49, 17);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for static function reference with member access operator on class: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for static function reference with member access operator on class: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("staticFunction():void") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for static function reference with member access operator on class"
            );
            assert.strictEqual(
              hover.range.start.line,
              49,
              "vscode.executeHoverProvider provided incorrect line for static function reference with member access operator on class"
            );
            assert.strictEqual(
              hover.range.start.character,
              15,
              "vscode.executeHoverProvider provided incorrect character for static function reference with member access operator on class"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of static property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(60, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for static property reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for static property reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("staticProperty:Boolean") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for static property reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              60,
              "vscode.executeHoverProvider provided incorrect line for static property reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              3,
              "vscode.executeHoverProvider provided incorrect character for static property reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of static property with member access operator on class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(61, 17);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for static property reference with member access operator on class: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for static property reference with member access operator on class: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("staticProperty:Boolean") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for static property reference with member access operator on class"
            );
            assert.strictEqual(
              hover.range.start.line,
              61,
              "vscode.executeHoverProvider provided incorrect line for static property reference with member access operator on class"
            );
            assert.strictEqual(
              hover.range.start.character,
              15,
              "vscode.executeHoverProvider provided incorrect character for static property reference with member access operator on class"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of package function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "packageFunction.as"
      )
    );
    let position = new vscode.Position(84, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for package function reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for package function reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf(
                "packageFunction(one:String, two:Number = 3, ...rest:Array):Boolean"
              ) >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for package function reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              84,
              "vscode.executeHoverProvider provided incorrect line for package function reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              3,
              "vscode.executeHoverProvider provided incorrect character for package function reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of fully-qualified package function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "packageFunction.as"
      )
    );
    let position = new vscode.Position(85, 17);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for fully-qualified package function reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for fully-qualified package function reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf(
                "packageFunction(one:String, two:Number = 3, ...rest:Array):Boolean"
              ) >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for fully-qualified package function reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              85,
              "vscode.executeHoverProvider provided incorrect line for fully-qualified package function reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              15,
              "vscode.executeHoverProvider provided incorrect character for fully-qualified package function reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of package variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "packageVar.as"
      )
    );
    let position = new vscode.Position(87, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for package variable reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for package variable reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("packageVar:Number") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for package variable reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              87,
              "vscode.executeHoverProvider provided incorrect line for package variable reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              3,
              "vscode.executeHoverProvider provided incorrect character for package variable reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of fully-qualified package variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "packageVar.as"
      )
    );
    let position = new vscode.Position(88, 17);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for fully-qualified package variable reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for fully-qualified package variable reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("packageVar:Number") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for fully-qualified package variable reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              88,
              "vscode.executeHoverProvider provided incorrect line for fully-qualified package variable reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              15,
              "vscode.executeHoverProvider provided incorrect character for fully-qualified package variable reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of super static variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(74, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for super static variable reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for super static variable reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("superStaticVar:Boolean") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for super static variable reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              74,
              "vscode.executeHoverProvider provided incorrect line for super static variable reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              3,
              "vscode.executeHoverProvider provided incorrect character for super static variable reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of super static variable with member access operator on superclass", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(75, 22);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for super static variable reference with member access operator on superclass: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for super static variable reference with member access operator on superclass: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("superStaticVar:Boolean") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for super static variable reference with member access operator on superclass"
            );
            assert.strictEqual(
              hover.range.start.line,
              75,
              "vscode.executeHoverProvider provided incorrect line for super static variable reference with member access operator on superclass"
            );
            assert.strictEqual(
              hover.range.start.character,
              20,
              "vscode.executeHoverProvider provided incorrect character for super static variable reference with member access operator on superclass"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of super static property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(81, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for super static property reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for super static property reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("superStaticProperty:Boolean") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for super static property reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              81,
              "vscode.executeHoverProvider provided incorrect line for super static property reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              3,
              "vscode.executeHoverProvider provided incorrect character for super static property reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of super static property with member access operator on superclass", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(82, 22);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for super static property reference with member access operator on superclass: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for super static property reference with member access operator on superclass: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("superStaticProperty:Boolean") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for super static property reference with member access operator on superclass"
            );
            assert.strictEqual(
              hover.range.start.line,
              82,
              "vscode.executeHoverProvider provided incorrect line for super static property reference with member access operator on superclass"
            );
            assert.strictEqual(
              hover.range.start.character,
              20,
              "vscode.executeHoverProvider provided incorrect character for super static property reference with member access operator on superclass"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of super static function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(67, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for super static function reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for super static function reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("superStaticFunction():void") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for super static function reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              67,
              "vscode.executeHoverProvider provided incorrect line for super static function reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              3,
              "vscode.executeHoverProvider provided incorrect character for super static function reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of super static function with member access operator on superclass", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(68, 22);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for super static function reference with member access operator on superclass: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for super static function reference with member access operator on superclass: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("superStaticFunction():void") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for super static function reference with member access operator on superclass"
            );
            assert.strictEqual(
              hover.range.start.line,
              68,
              "vscode.executeHoverProvider provided incorrect line for super static function reference with member access operator on superclass"
            );
            assert.strictEqual(
              hover.range.start.character,
              20,
              "vscode.executeHoverProvider provided incorrect character for super static function reference with member access operator on superclass"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of super member function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(63, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for super member function reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for super member function reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("superMemberFunction():void") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for super member function reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              63,
              "vscode.executeHoverProvider provided incorrect line for super member function reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              3,
              "vscode.executeHoverProvider provided incorrect character for super member function reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of super member function with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(64, 10);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for super member function reference with member access operator on this: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for super member function reference with member access operator on this: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("superMemberFunction():void") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for super member function reference with member access operator on this"
            );
            assert.strictEqual(
              hover.range.start.line,
              64,
              "vscode.executeHoverProvider provided incorrect line for super member function reference with member access operator on this"
            );
            assert.strictEqual(
              hover.range.start.character,
              8,
              "vscode.executeHoverProvider provided incorrect character for super member function reference with member access operator on this"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of super member function with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(65, 11);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for super member function reference with member access operator on super: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for super member function reference with member access operator on super: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("superMemberFunction():void") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for super member function reference with member access operator on super"
            );
            assert.strictEqual(
              hover.range.start.line,
              65,
              "vscode.executeHoverProvider provided incorrect line for super member function reference with member access operator on super"
            );
            assert.strictEqual(
              hover.range.start.character,
              9,
              "vscode.executeHoverProvider provided incorrect character for super member function reference with member access operator on super"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of super member variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(70, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for super member variable reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for super member variable reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("superMemberVar:String") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for super member variable reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              70,
              "vscode.executeHoverProvider provided incorrect line for super member variable reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              3,
              "vscode.executeHoverProvider provided incorrect character for super member variable reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of super member variable with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(71, 10);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for super member variable reference with member access operator on this: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for super member variable reference with member access operator on this: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("superMemberVar:String") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for super member variable reference with member access operator on this"
            );
            assert.strictEqual(
              hover.range.start.line,
              71,
              "vscode.executeHoverProvider provided incorrect line for super member variable reference with member access operator on this"
            );
            assert.strictEqual(
              hover.range.start.character,
              8,
              "vscode.executeHoverProvider provided incorrect character for super member variable reference with member access operator on this"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of super member variable with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(72, 11);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for super member variable reference with member access operator on super: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for super member variable reference with member access operator on super: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("superMemberVar:String") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for super member variable reference with member access operator on super"
            );
            assert.strictEqual(
              hover.range.start.line,
              72,
              "vscode.executeHoverProvider provided incorrect line for super member variable reference with member access operator on super"
            );
            assert.strictEqual(
              hover.range.start.character,
              9,
              "vscode.executeHoverProvider provided incorrect character for super member variable reference with member access operator on super"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of super member property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(77, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for super member property reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for super member property reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("superMemberProperty:Boolean") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for super member property reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              77,
              "vscode.executeHoverProvider provided incorrect line for super member property reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              3,
              "vscode.executeHoverProvider provided incorrect character for super member property reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of super member property with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(78, 10);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for super member property reference with member access operator on this: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for super member property reference with member access operator on this: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("superMemberProperty:Boolean") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for super member property reference with member access operator on this"
            );
            assert.strictEqual(
              hover.range.start.line,
              78,
              "vscode.executeHoverProvider provided incorrect line for super member property reference with member access operator on this"
            );
            assert.strictEqual(
              hover.range.start.character,
              8,
              "vscode.executeHoverProvider provided incorrect character for super member property reference with member access operator on this"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of super member property with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let definitionURI = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "SuperDefinitions.as"
      )
    );
    let position = new vscode.Position(79, 11);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for super member property reference with member access operator on super: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for super member property reference with member access operator on super: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("superMemberProperty:Boolean") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for super member property reference with member access operator on super"
            );
            assert.strictEqual(
              hover.range.start.line,
              79,
              "vscode.executeHoverProvider provided incorrect line for super member property reference with member access operator on super"
            );
            assert.strictEqual(
              hover.range.start.character,
              9,
              "vscode.executeHoverProvider provided incorrect character for super member property reference with member access operator on super"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of file-internal variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(94, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for file-internal variable reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for file-internal variable reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("internalVar:Number") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for file-internal variable reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              94,
              "vscode.executeHoverProvider provided incorrect line for file-internal variable reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              3,
              "vscode.executeHoverProvider provided incorrect character for file-internal variable reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of file-internal function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(95, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for file-internal function reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for file-internal function reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("internalFunction():void") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for file-internal function reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              95,
              "vscode.executeHoverProvider provided incorrect line for file-internal function reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              3,
              "vscode.executeHoverProvider provided incorrect character for file-internal function reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of file-internal class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(97, 37);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for file-internal class reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for file-internal class reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("class InternalDefinitions") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for file-internal class reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              97,
              "vscode.executeHoverProvider provided incorrect line for file-internal class reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              35,
              "vscode.executeHoverProvider provided incorrect character for file-internal class reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of file-internal member function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(99, 33);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for file-internal member function reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for file-internal member function reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("internalMemberFunction():void") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for file-internal member function reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              99,
              "vscode.executeHoverProvider provided incorrect line for file-internal member function reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              31,
              "vscode.executeHoverProvider provided incorrect character for file-internal member function reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of file-internal member variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(100, 33);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for file-internal member variable reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for file-internal member variable reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("internalMemberVar:String") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for file-internal member variable reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              100,
              "vscode.executeHoverProvider provided incorrect line for file-internal member variable reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              31,
              "vscode.executeHoverProvider provided incorrect character for file-internal member variable reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of file-internal member property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(101, 33);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for file-internal member property reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for file-internal member property reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("internalMemberProperty:Boolean") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for file-internal member property reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              101,
              "vscode.executeHoverProvider provided incorrect line for file-internal member property reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              31,
              "vscode.executeHoverProvider provided incorrect character for file-internal member property reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of file-internal static property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(105, 25);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for file-internal static property reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for file-internal static property reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("internalStaticProperty:Boolean") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for file-internal static property reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              105,
              "vscode.executeHoverProvider provided incorrect line for file-internal static property reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              23,
              "vscode.executeHoverProvider provided incorrect character for file-internal static property reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of file-internal static variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(104, 25);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for file-internal static variable reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for file-internal static variable reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("internalStaticVar:Boolean") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for file-internal static variable reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              104,
              "vscode.executeHoverProvider provided incorrect line for file-internal static variable reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              23,
              "vscode.executeHoverProvider provided incorrect character for file-internal static variable reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of file-internal static function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Definitions.as"
      )
    );
    let position = new vscode.Position(103, 25);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for file-internal static function reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for file-internal static function reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf("internalStaticFunction():void") >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover for file-internal static function reference"
            );
            assert.strictEqual(
              hover.range.start.line,
              103,
              "vscode.executeHoverProvider provided incorrect line for file-internal static function reference"
            );
            assert.strictEqual(
              hover.range.start.character,
              23,
              "vscode.executeHoverProvider provided incorrect character for file-internal static function reference"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of Number static constant", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "hover",
        "HoverConstants.as"
      )
    );
    let position = new vscode.Position(4, 24);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for file-internal static function reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for file-internal static function reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf(
                "(const) com.example.hover.HoverConstants.NUMBER:Number = 2"
              ) >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover"
            );
            assert.strictEqual(
              hover.range.start.line,
              4,
              "vscode.executeHoverProvider provided incorrect line"
            );
            assert.strictEqual(
              hover.range.start.character,
              22,
              "vscode.executeHoverProvider provided incorrect character"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of Boolean static constant", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "hover",
        "HoverConstants.as"
      )
    );
    let position = new vscode.Position(5, 25);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for file-internal static function reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for file-internal static function reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf(
                "(const) com.example.hover.HoverConstants.BOOLEAN:Boolean = false"
              ) >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover"
            );
            assert.strictEqual(
              hover.range.start.line,
              5,
              "vscode.executeHoverProvider provided incorrect line"
            );
            assert.strictEqual(
              hover.range.start.character,
              23,
              "vscode.executeHoverProvider provided incorrect character"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of String static constant", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "hover",
        "HoverConstants.as"
      )
    );
    let position = new vscode.Position(6, 27);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for file-internal static function reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for file-internal static function reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf(
                '(const) com.example.hover.HoverConstants.STRING:String = "hello"'
              ) >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover"
            );
            assert.strictEqual(
              hover.range.start.line,
              6,
              "vscode.executeHoverProvider provided incorrect line"
            );
            assert.strictEqual(
              hover.range.start.character,
              25,
              "vscode.executeHoverProvider provided incorrect character"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeHoverProvider displays hover of Object static constant", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "hover",
        "HoverConstants.as"
      )
    );
    let position = new vscode.Position(7, 24);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeHoverProvider", uri, position)
        .then(
          (hovers: vscode.Hover[]) => {
            assert.strictEqual(
              hovers.length,
              1,
              "vscode.executeHoverProvider failed to provide hover for file-internal static function reference: " +
                uri
            );
            let hover = hovers[0];
            let contents = hover.contents;
            assert.strictEqual(
              contents.length,
              1,
              "vscode.executeHoverProvider failed to provide hover contents for file-internal static function reference: " +
                uri
            );
            let content = contents[0];
            let contentValue: string;
            if (typeof content === "string") {
              contentValue = content;
            } else {
              contentValue = content.value;
            }
            assert.strictEqual(
              contentValue.indexOf(
                "(const) com.example.hover.HoverConstants.OBJECT:Object"
              ) >= 0,
              true,
              "vscode.executeHoverProvider provided incorrect hover"
            );
            assert.strictEqual(
              hover.range.start.line,
              7,
              "vscode.executeHoverProvider provided incorrect line"
            );
            assert.strictEqual(
              hover.range.start.character,
              22,
              "vscode.executeHoverProvider provided incorrect character"
            );
          },
          (err) => {
            assert(false, "Failed to execute hover provider: " + uri);
          }
        );
    });
  });
});

suite("completion item provider: Application workspace", () => {
  suiteTeardown(revertAndCloseAllEditors);
  test("vscode.executeCompletionItemProvider includes local variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(46, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let localVarItem = findCompletionItem("localVar", items);
            assert.notEqual(
              localVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide local variable: " +
                uri
            );
            assert.strictEqual(
              localVarItem.kind,
              vscode.CompletionItemKind.Variable,
              "vscode.executeCompletionItemProvider failed to provide correct kind of local variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits local variable with member access operator on class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(49, 14);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let localVarItem = findCompletionItem("localVar", items);
            assert.equal(
              localVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit local variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits local variable with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(47, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let localVarItem = findCompletionItem("localVar", items);
            assert.equal(
              localVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit local variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits local variable with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(48, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let localVarItem = findCompletionItem("localVar", items);
            assert.equal(
              localVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit local variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits local variable with member access operator on fully-qualified package", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(51, 15);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let localVarItem = findCompletionItem("localVar", items);
            assert.equal(
              localVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit local variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits local variable from type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberVarItem = findCompletionItem("localVar", items);
            assert.equal(
              superMemberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit local variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes local function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(46, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let localFunctionItem = findCompletionItem("localFunction", items);
            assert.notEqual(
              localFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide local function: " +
                uri
            );
            assert.strictEqual(
              localFunctionItem.kind,
              vscode.CompletionItemKind.Function,
              "vscode.executeCompletionItemProvider failed to provide correct kind of local function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits local function with member access operator on class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(49, 14);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let localFunctionItem = findCompletionItem("localFunction", items);
            assert.equal(
              localFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit local function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits local function with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(47, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let localFunctionItem = findCompletionItem("localFunction", items);
            assert.equal(
              localFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit local function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits local function with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(48, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let localVarItem = findCompletionItem("localFunction", items);
            assert.equal(
              localVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit local function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits local function with member access operator on fully-qualified package", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(51, 51);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let localFunctionItem = findCompletionItem("localFunction", items);
            assert.equal(
              localFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit local function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits local function from type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberVarItem = findCompletionItem("localFunction", items);
            assert.equal(
              superMemberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit local function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes member variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(46, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let memberVarItem = findCompletionItem("memberVar", items);
            assert.notEqual(
              memberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide member variable: " +
                uri
            );
            assert.strictEqual(
              memberVarItem.kind,
              vscode.CompletionItemKind.Field,
              "vscode.executeCompletionItemProvider failed to provide correct kind of member variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits member variable with member access operator on class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(49, 14);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let memberVarItem = findCompletionItem("memberVar", items);
            assert.equal(
              memberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit member variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes member variable with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(47, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let memberVarItem = findCompletionItem("memberVar", items);
            assert.notEqual(
              memberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide member variable: " +
                uri
            );
            assert.strictEqual(
              memberVarItem.kind,
              vscode.CompletionItemKind.Field,
              "vscode.executeCompletionItemProvider failed to provide correct kind of member variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits member variable with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(48, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let memberVarItem = findCompletionItem("memberVar", items);
            assert.equal(
              memberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit member variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits member variable with member access operator on fully-qualified package", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(51, 15);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let memberVarItem = findCompletionItem("memberVar", items);
            assert.equal(
              memberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit member variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits member variable from type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberVarItem = findCompletionItem("memberVar", items);
            assert.equal(
              superMemberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit member variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes member function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(46, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let memberFunctionItem = findCompletionItem(
              "memberFunction",
              items
            );
            assert.notEqual(
              memberFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide member function: " +
                uri
            );
            assert.strictEqual(
              memberFunctionItem.kind,
              vscode.CompletionItemKind.Method,
              "vscode.executeCompletionItemProvider failed to provide correct kind of member function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits member function with member access operator on class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(49, 14);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let memberFunctionItem = findCompletionItem(
              "memberFunction",
              items
            );
            assert.equal(
              memberFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit member variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes member function with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(47, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let memberFunctionItem = findCompletionItem(
              "memberFunction",
              items
            );
            assert.notEqual(
              memberFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide member function: " +
                uri
            );
            assert.strictEqual(
              memberFunctionItem.kind,
              vscode.CompletionItemKind.Method,
              "vscode.executeCompletionItemProvider failed to provide correct kind of member function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits member function with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(48, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let memberFunctionItem = findCompletionItem(
              "memberFunction",
              items
            );
            assert.equal(
              memberFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit member variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits member function with member access operator on fully-qualified package", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(51, 15);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let memberFunctionItem = findCompletionItem(
              "memberFunction",
              items
            );
            assert.equal(
              memberFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit member variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits member function from type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let memberFunctionItem = findCompletionItem(
              "memberFunction",
              items
            );
            assert.equal(
              memberFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit member function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes member property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(46, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let memberPropItem = findCompletionItem("memberProperty", items);
            assert.notEqual(
              memberPropItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide member property: " +
                uri
            );
            assert.strictEqual(
              memberPropItem.kind,
              vscode.CompletionItemKind.Property,
              "vscode.executeCompletionItemProvider failed to provide correct kind of member property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits member property with member access operator on class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(49, 14);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let memberPropItem = findCompletionItem("memberProperty", items);
            assert.equal(
              memberPropItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit member property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes member property with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(47, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let memberPropItem = findCompletionItem("memberProperty", items);
            assert.notEqual(
              memberPropItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide member property: " +
                uri
            );
            assert.strictEqual(
              memberPropItem.kind,
              vscode.CompletionItemKind.Property,
              "vscode.executeCompletionItemProvider failed to provide correct kind of member property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits member property with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(48, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let memberPropItem = findCompletionItem("memberProperty", items);
            assert.equal(
              memberPropItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit member property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits member property with member access operator on fully-qualified package", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(51, 15);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let memberPropItem = findCompletionItem("memberProperty", items);
            assert.equal(
              memberPropItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit member property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits member property from type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let memberPropItem = findCompletionItem("memberProperty", items);
            assert.equal(
              memberPropItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit member property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes static variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(46, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let staticVarItem = findCompletionItem("staticVar", items);
            assert.notEqual(
              staticVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide static variable: " +
                uri
            );
            assert.strictEqual(
              staticVarItem.kind,
              vscode.CompletionItemKind.Field,
              "vscode.executeCompletionItemProvider failed to provide correct kind of static variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes static variable with member access operator on class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(49, 14);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let staticVarItem = findCompletionItem("staticVar", items);
            assert.notEqual(
              staticVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide static variable: " +
                uri
            );
            assert.strictEqual(
              staticVarItem.kind,
              vscode.CompletionItemKind.Field,
              "vscode.executeCompletionItemProvider failed to provide correct kind of static variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits static variable with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(47, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let staticVarItem = findCompletionItem("staticVar", items);
            assert.equal(
              staticVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit static variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits static variable with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(48, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let staticVarItem = findCompletionItem("staticVar", items);
            assert.equal(
              staticVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit static variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits static variable with member access operator on fully-qualified package", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(51, 15);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let staticVarItem = findCompletionItem("staticVar", items);
            assert.equal(
              staticVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit static variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits static variable from type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let staticVarItem = findCompletionItem("staticVar", items);
            assert.equal(
              staticVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit static variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes static function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(46, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let staticFunctionItem = findCompletionItem(
              "staticFunction",
              items
            );
            assert.notEqual(
              staticFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide static function: " +
                uri
            );
            assert.strictEqual(
              staticFunctionItem.kind,
              vscode.CompletionItemKind.Method,
              "vscode.executeCompletionItemProvider failed to provide correct kind of static function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes static function with member access operator on class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(49, 14);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let staticFunctionItem = findCompletionItem(
              "staticFunction",
              items
            );
            assert.notEqual(
              staticFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide static function: " +
                uri
            );
            assert.strictEqual(
              staticFunctionItem.kind,
              vscode.CompletionItemKind.Method,
              "vscode.executeCompletionItemProvider failed to provide correct kind of static function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits static function with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(47, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let staticFunctionItem = findCompletionItem(
              "staticFunction",
              items
            );
            assert.equal(
              staticFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit static function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits static function with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(48, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let staticFunctionItem = findCompletionItem(
              "staticFunction",
              items
            );
            assert.equal(
              staticFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit static function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits static function with member access operator on fully-qualified package", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(51, 15);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let staticFunctionItem = findCompletionItem(
              "staticFunction",
              items
            );
            assert.equal(
              staticFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit static function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits static function from type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let staticFunctionItem = findCompletionItem(
              "staticFunction",
              items
            );
            assert.equal(
              staticFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit static function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes static property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(46, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let staticPropItem = findCompletionItem("staticProperty", items);
            assert.notEqual(
              staticPropItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide static property: " +
                uri
            );
            assert.strictEqual(
              staticPropItem.kind,
              vscode.CompletionItemKind.Property,
              "vscode.executeCompletionItemProvider failed to provide correct kind of static property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes static property with member access operator on class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(49, 14);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let staticPropItem = findCompletionItem("staticProperty", items);
            assert.notEqual(
              staticPropItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide static property: " +
                uri
            );
            assert.strictEqual(
              staticPropItem.kind,
              vscode.CompletionItemKind.Property,
              "vscode.executeCompletionItemProvider failed to provide correct kind of static property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits static property with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(47, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let staticPropItem = findCompletionItem("staticProperty", items);
            assert.equal(
              staticPropItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit static property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits static property with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(48, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let staticPropItem = findCompletionItem("staticProperty", items);
            assert.equal(
              staticPropItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit static property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits static property with member access operator on fully-qualified package", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(51, 15);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let staticPropItem = findCompletionItem("staticProperty", items);
            assert.equal(
              staticPropItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit static property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits static property from type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let staticPropItem = findCompletionItem("staticProperty", items);
            assert.equal(
              staticPropItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit static property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes package class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(46, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let packageClassItem = findCompletionItem(
              "UnreferencedClass",
              items
            );
            assert.notEqual(
              packageClassItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide package class: " +
                uri
            );
            assert.strictEqual(
              packageClassItem.kind,
              vscode.CompletionItemKind.Class,
              "vscode.executeCompletionItemProvider failed to provide correct kind of package class: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes package class with member access on fully-qualified package", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(50, 15);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let classItem = findCompletionItem("UnreferencedClass", items);
            assert.notEqual(
              classItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide package class: " +
                uri
            );
            assert.strictEqual(
              classItem.kind,
              vscode.CompletionItemKind.Class,
              "vscode.executeCompletionItemProvider failed to provide correct kind of package class: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits package class with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(47, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let localVarItem = findCompletionItem("UnreferencedClass", items);
            assert.equal(
              localVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit package class: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits package class with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(48, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let localVarItem = findCompletionItem("UnreferencedClass", items);
            assert.equal(
              localVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit package class: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes package class as type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let packageClassItem = findCompletionItem(
              "UnreferencedClass",
              items
            );
            assert.notEqual(
              packageClassItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide package class: " +
                uri
            );
            assert.strictEqual(
              packageClassItem.kind,
              vscode.CompletionItemKind.Class,
              "vscode.executeCompletionItemProvider failed to provide correct kind of package class: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes package variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(46, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let packageVarItem = findCompletionItem("packageVar", items);
            assert.notEqual(
              packageVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide package variable: " +
                uri
            );
            assert.strictEqual(
              packageVarItem.kind,
              vscode.CompletionItemKind.Variable,
              "vscode.executeCompletionItemProvider failed to provide correct kind of package variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes package variable with member access on fully-qualified package", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(50, 15);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let packageVarItem = findCompletionItem("packageVar", items);
            assert.notEqual(
              packageVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide package variable: " +
                uri
            );
            assert.strictEqual(
              packageVarItem.kind,
              vscode.CompletionItemKind.Variable,
              "vscode.executeCompletionItemProvider failed to provide correct kind of package variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits package variable with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(47, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let localVarItem = findCompletionItem("packageVar", items);
            assert.equal(
              localVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit package variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits package variable with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(48, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let localVarItem = findCompletionItem("packageVar", items);
            assert.equal(
              localVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit package variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits package variable as type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let packageClassItem = findCompletionItem("packageVar", items);
            assert.equal(
              packageClassItem,
              null,
              "vscode.executeCompletionItemProvider failed to omits package variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes package function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(46, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let packageFunctionItem = findCompletionItem(
              "packageFunction",
              items
            );
            assert.notEqual(
              packageFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide package function: " +
                uri
            );
            assert.strictEqual(
              packageFunctionItem.kind,
              vscode.CompletionItemKind.Function,
              "vscode.executeCompletionItemProvider failed to provide correct kind of package function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes package function with member access on fully-qualified package", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(50, 15);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let packageFunctionItem = findCompletionItem(
              "packageFunction",
              items
            );
            assert.notEqual(
              packageFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide package function: " +
                uri
            );
            assert.strictEqual(
              packageFunctionItem.kind,
              vscode.CompletionItemKind.Function,
              "vscode.executeCompletionItemProvider failed to provide correct kind of package function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits package function with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(47, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let localVarItem = findCompletionItem("packageFunction", items);
            assert.equal(
              localVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit package function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits package function with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(48, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let localVarItem = findCompletionItem("packageFunction", items);
            assert.equal(
              localVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit package function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits package function as type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let packageClassItem = findCompletionItem("packageFunction", items);
            assert.equal(
              packageClassItem,
              null,
              "vscode.executeCompletionItemProvider failed to omits package function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes super member variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(46, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberVarItem = findCompletionItem(
              "superMemberVar",
              items
            );
            assert.notEqual(
              superMemberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super member variable: " +
                uri
            );
            assert.strictEqual(
              superMemberVarItem.kind,
              vscode.CompletionItemKind.Field,
              "vscode.executeCompletionItemProvider failed to provide correct kind of super member variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes super member variable with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(47, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberVarItem = findCompletionItem(
              "superMemberVar",
              items
            );
            assert.notEqual(
              superMemberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super member variable: " +
                uri
            );
            assert.strictEqual(
              superMemberVarItem.kind,
              vscode.CompletionItemKind.Field,
              "vscode.executeCompletionItemProvider failed to provide correct kind of super member variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits super member variable from type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberVarItem = findCompletionItem(
              "superMemberVar",
              items
            );
            assert.equal(
              superMemberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit super member variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes super member variable with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(48, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberVarItem = findCompletionItem(
              "superMemberVar",
              items
            );
            assert.notEqual(
              superMemberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide member super variable: " +
                uri
            );
            assert.strictEqual(
              superMemberVarItem.kind,
              vscode.CompletionItemKind.Field,
              "vscode.executeCompletionItemProvider failed to provide correct kind of super member variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes super member property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(46, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberPropertyItem = findCompletionItem(
              "superMemberProperty",
              items
            );
            assert.notEqual(
              superMemberPropertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super member property: " +
                uri
            );
            assert.strictEqual(
              superMemberPropertyItem.kind,
              vscode.CompletionItemKind.Property,
              "vscode.executeCompletionItemProvider failed to provide correct kind of super member property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes super member property with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(47, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberPropertyItem = findCompletionItem(
              "superMemberProperty",
              items
            );
            assert.notEqual(
              superMemberPropertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super member property: " +
                uri
            );
            assert.strictEqual(
              superMemberPropertyItem.kind,
              vscode.CompletionItemKind.Property,
              "vscode.executeCompletionItemProvider failed to provide correct kind of super member property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes super member property with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(48, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberPropertyItem = findCompletionItem(
              "superMemberProperty",
              items
            );
            assert.notEqual(
              superMemberPropertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super member property: " +
                uri
            );
            assert.strictEqual(
              superMemberPropertyItem.kind,
              vscode.CompletionItemKind.Property,
              "vscode.executeCompletionItemProvider failed to provide correct kind of super member property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits super member property from type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberVarItem = findCompletionItem(
              "superMemberProperty",
              items
            );
            assert.equal(
              superMemberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit super member property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes super member function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(46, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberFunctionItem = findCompletionItem(
              "superMemberFunction",
              items
            );
            assert.notEqual(
              superMemberFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super member function: " +
                uri
            );
            assert.strictEqual(
              superMemberFunctionItem.kind,
              vscode.CompletionItemKind.Method,
              "vscode.executeCompletionItemProvider failed to provide correct kind of super member function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes super member function with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(47, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberFunctionItem = findCompletionItem(
              "superMemberFunction",
              items
            );
            assert.notEqual(
              superMemberFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super member function: " +
                uri
            );
            assert.strictEqual(
              superMemberFunctionItem.kind,
              vscode.CompletionItemKind.Method,
              "vscode.executeCompletionItemProvider failed to provide correct kind of super member function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes super member function with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(48, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberFunctionItem = findCompletionItem(
              "superMemberFunction",
              items
            );
            assert.notEqual(
              superMemberFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super member function: " +
                uri
            );
            assert.strictEqual(
              superMemberFunctionItem.kind,
              vscode.CompletionItemKind.Method,
              "vscode.executeCompletionItemProvider failed to provide correct kind of super member function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits super member function from type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberVarItem = findCompletionItem(
              "superMemberFunction",
              items
            );
            assert.equal(
              superMemberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit super member function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes super static variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(46, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superStaticVarItem = findCompletionItem(
              "superStaticVar",
              items
            );
            assert.notEqual(
              superStaticVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super static variable: " +
                uri
            );
            assert.strictEqual(
              superStaticVarItem.kind,
              vscode.CompletionItemKind.Field,
              "vscode.executeCompletionItemProvider failed to provide correct kind of super static variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits super static variable with member access operator on class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(49, 15);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superStaticVarItem = findCompletionItem(
              "superStaticVar",
              items
            );
            assert.equal(
              superStaticVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit super static variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes super static variable with member access operator on superclass", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(50, 20);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superStaticVarItem = findCompletionItem(
              "superStaticVar",
              items
            );
            assert.notEqual(
              superStaticVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super static variable: " +
                uri
            );
            assert.strictEqual(
              superStaticVarItem.kind,
              vscode.CompletionItemKind.Field,
              "vscode.executeCompletionItemProvider failed to provide correct kind of super static variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes super static variable with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(47, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberFunctionItem = findCompletionItem(
              "superStaticVar",
              items
            );
            assert.equal(
              superMemberFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit super static variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits super static variable with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(48, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberFunctionItem = findCompletionItem(
              "superStaticVar",
              items
            );
            assert.equal(
              superMemberFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super static variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits super static variable from type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberVarItem = findCompletionItem(
              "superStaticVar",
              items
            );
            assert.equal(
              superMemberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit super static variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes super static property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(46, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superStaticPropertyItem = findCompletionItem(
              "superStaticProperty",
              items
            );
            assert.notEqual(
              superStaticPropertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super static property: " +
                uri
            );
            assert.strictEqual(
              superStaticPropertyItem.kind,
              vscode.CompletionItemKind.Property,
              "vscode.executeCompletionItemProvider failed to provide correct kind of super static property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits super static property with member access operator on class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(49, 15);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superStaticPropertyItem = findCompletionItem(
              "superStaticProperty",
              items
            );
            assert.equal(
              superStaticPropertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit super static property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes super static property with member access operator on superclass", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(50, 20);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superStaticPropertyItem = findCompletionItem(
              "superStaticProperty",
              items
            );
            assert.notEqual(
              superStaticPropertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super static property: " +
                uri
            );
            assert.strictEqual(
              superStaticPropertyItem.kind,
              vscode.CompletionItemKind.Property,
              "vscode.executeCompletionItemProvider failed to provide correct kind of super static property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes super static property with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(47, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberFunctionItem = findCompletionItem(
              "superStaticProperty",
              items
            );
            assert.equal(
              superMemberFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit super static property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits super static property with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(48, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberFunctionItem = findCompletionItem(
              "superStaticProperty",
              items
            );
            assert.equal(
              superMemberFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super static property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits super static property from type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberVarItem = findCompletionItem(
              "superStaticProperty",
              items
            );
            assert.equal(
              superMemberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit super static property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes super static function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(46, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superStaticFunctionItem = findCompletionItem(
              "superStaticFunction",
              items
            );
            assert.notEqual(
              superStaticFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super static function: " +
                uri
            );
            assert.strictEqual(
              superStaticFunctionItem.kind,
              vscode.CompletionItemKind.Method,
              "vscode.executeCompletionItemProvider failed to provide correct kind of super static function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits super static function with member access operator on class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(49, 15);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superStaticFunctionItem = findCompletionItem(
              "superStaticFunction",
              items
            );
            assert.equal(
              superStaticFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit super static function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes super static function with member access operator on superclass", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(50, 20);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superStaticFunctionItem = findCompletionItem(
              "superStaticFunction",
              items
            );
            assert.notEqual(
              superStaticFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super static function: " +
                uri
            );
            assert.strictEqual(
              superStaticFunctionItem.kind,
              vscode.CompletionItemKind.Method,
              "vscode.executeCompletionItemProvider failed to provide correct kind of super static function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes super static function with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(47, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberFunctionItem = findCompletionItem(
              "superStaticFunction",
              items
            );
            assert.equal(
              superMemberFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit super static function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits super static function with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(48, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberFunctionItem = findCompletionItem(
              "superStaticFunction",
              items
            );
            assert.equal(
              superMemberFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super static function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits super static function from type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberVarItem = findCompletionItem(
              "superStaticFunction",
              items
            );
            assert.equal(
              superMemberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit super static function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes file-internal variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(46, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let fileInternalVarItem = findCompletionItem(
              "fileInternalVar",
              items
            );
            assert.notEqual(
              fileInternalVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide file-internal variable: " +
                uri
            );
            assert.strictEqual(
              fileInternalVarItem.kind,
              vscode.CompletionItemKind.Variable,
              "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits file-internal variable with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(47, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let fileInternalVarItem = findCompletionItem(
              "fileInternalVar",
              items
            );
            assert.equal(
              fileInternalVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit file-internal variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits file-internal variable from type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberVarItem = findCompletionItem(
              "fileInternalVar",
              items
            );
            assert.equal(
              superMemberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit file-internal variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes file-internal function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(46, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let fileInternalFunctionItem = findCompletionItem(
              "fileInternalFunction",
              items
            );
            assert.notEqual(
              fileInternalFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide file-internal function: " +
                uri
            );
            assert.strictEqual(
              fileInternalFunctionItem.kind,
              vscode.CompletionItemKind.Function,
              "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits file-internal function with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(47, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let fileInternalFunctionItem = findCompletionItem(
              "fileInternalFunction",
              items
            );
            assert.equal(
              fileInternalFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit file-internal function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits file-internal function from type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeCompletionItemProvider",
          uri,
          position,
          "."
        )
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let superMemberVarItem = findCompletionItem(
              "fileInternalFunction",
              items
            );
            assert.equal(
              superMemberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit file-internal function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes file-internal class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(46, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let fileInternalClassItem = findCompletionItem(
              "FileInternalCompletion",
              items
            );
            assert.notEqual(
              fileInternalClassItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide file-internal class: " +
                uri
            );
            assert.strictEqual(
              fileInternalClassItem.kind,
              vscode.CompletionItemKind.Class,
              "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal class: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes file-internal class as type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let fileInternalClassItem = findCompletionItem(
              "FileInternalCompletion",
              items
            );
            assert.notEqual(
              fileInternalClassItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide file-internal class: " +
                uri
            );
            assert.strictEqual(
              fileInternalClassItem.kind,
              vscode.CompletionItemKind.Class,
              "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal class: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes file-internal member variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(53, 12);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let fileInternalMemberVarItem = findCompletionItem(
              "fileInternalMemberVar",
              items
            );
            assert.notEqual(
              fileInternalMemberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide file-internal member variable: " +
                uri
            );
            assert.strictEqual(
              fileInternalMemberVarItem.kind,
              vscode.CompletionItemKind.Field,
              "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal member variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes file-internal member function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(53, 12);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let fileInternalMemberFunctionItem = findCompletionItem(
              "fileInternalMemberFunction",
              items
            );
            assert.notEqual(
              fileInternalMemberFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide file-internal member function: " +
                uri
            );
            assert.strictEqual(
              fileInternalMemberFunctionItem.kind,
              vscode.CompletionItemKind.Method,
              "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal member function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes file-internal member property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(53, 12);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let fileInternalMemberPropertyItem = findCompletionItem(
              "fileInternalMemberProperty",
              items
            );
            assert.notEqual(
              fileInternalMemberPropertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide file-internal member property: " +
                uri
            );
            assert.strictEqual(
              fileInternalMemberPropertyItem.kind,
              vscode.CompletionItemKind.Property,
              "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal member property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes file-internal static variable", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(54, 26);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let fileInternalStaticVarItem = findCompletionItem(
              "fileInternalStaticVar",
              items
            );
            assert.notEqual(
              fileInternalStaticVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide file-internal static variable: " +
                uri
            );
            assert.strictEqual(
              fileInternalStaticVarItem.kind,
              vscode.CompletionItemKind.Field,
              "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal static variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes file-internal static function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(54, 26);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let fileInternalStaticFunctionItem = findCompletionItem(
              "fileInternalStaticFunction",
              items
            );
            assert.notEqual(
              fileInternalStaticFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide file-internal static function: " +
                uri
            );
            assert.strictEqual(
              fileInternalStaticFunctionItem.kind,
              vscode.CompletionItemKind.Method,
              "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal static function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes file-internal static property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(54, 26);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let fileInternalStaticPropertyItem = findCompletionItem(
              "fileInternalStaticProperty",
              items
            );
            assert.notEqual(
              fileInternalStaticPropertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide file-internal static property: " +
                uri
            );
            assert.strictEqual(
              fileInternalStaticPropertyItem.kind,
              vscode.CompletionItemKind.Property,
              "vscode.executeCompletionItemProvider failed to provide correct kind of file-internal static property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits protected member variable not in superclass", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(53, 12);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let protectedMemberVarItem = findCompletionItem(
              "protectedMemberVar",
              items
            );
            assert.equal(
              protectedMemberVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit protected member variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits protected member function not in superclass", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(53, 12);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let protectedMemberFunctionItem = findCompletionItem(
              "protectedMemberFunction",
              items
            );
            assert.equal(
              protectedMemberFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit protected member function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits protected member property not in superclass", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(53, 12);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let protectedMemberPropertyItem = findCompletionItem(
              "protectedMemberProperty",
              items
            );
            assert.equal(
              protectedMemberPropertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit protected member property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits protected static variable not in superclass", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(54, 26);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let protectedStaticVarItem = findCompletionItem(
              "protectedStaticVar",
              items
            );
            assert.equal(
              protectedStaticVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit protected static variable: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits protected static function not in superclass", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(54, 26);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let protectedStaticFunctionItem = findCompletionItem(
              "protectedStaticFunction",
              items
            );
            assert.equal(
              protectedStaticFunctionItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit protected static function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits protected static property not in superclass", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(54, 26);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let protectedStaticPropertyItem = findCompletionItem(
              "protectedStaticProperty",
              items
            );
            assert.equal(
              protectedStaticPropertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit protected static property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes class on left side of member access for static constant", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(56, 21);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let classItem = findCompletionItem("ClassWithConstants", items);
            assert.notEqual(
              classItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide class: " +
                uri
            );
            assert.strictEqual(
              classItem.kind,
              vscode.CompletionItemKind.Class,
              "vscode.executeCompletionItemProvider failed to provide correct kind of class: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes package interface with member access on fully-qualified package", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(50, 15);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let interfaceItem = findCompletionItem("IPackageInterface", items);
            assert.notEqual(
              interfaceItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide package interface: " +
                uri
            );
            assert.strictEqual(
              interfaceItem.kind,
              vscode.CompletionItemKind.Interface,
              "vscode.executeCompletionItemProvider failed to provide correct kind of package interface: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits package interface with member access operator on this", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(47, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let localVarItem = findCompletionItem("IPackageInterface", items);
            assert.equal(
              localVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit package interface: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits package interface with member access operator on super", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(48, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let localVarItem = findCompletionItem("IPackageInterface", items);
            assert.equal(
              localVarItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit package interface: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes package interface as type annotation", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(55, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let packageInterfaceItem = findCompletionItem(
              "IPackageInterface",
              items
            );
            assert.notEqual(
              packageInterfaceItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide package interface: " +
                uri
            );
            assert.strictEqual(
              packageInterfaceItem.kind,
              vscode.CompletionItemKind.Interface,
              "vscode.executeCompletionItemProvider failed to provide correct kind of package interface: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes interface member property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(58, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let propertyItem = findCompletionItem("memberProperty", items);
            assert.notEqual(
              propertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide member property: " +
                uri
            );
            assert.strictEqual(
              propertyItem.kind,
              vscode.CompletionItemKind.Property,
              "vscode.executeCompletionItemProvider failed to provide correct kind of member property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes interface member function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(58, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let functionItem = findCompletionItem("memberFunction", items);
            assert.notEqual(
              functionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide member function: " +
                uri
            );
            assert.strictEqual(
              functionItem.kind,
              vscode.CompletionItemKind.Method,
              "vscode.executeCompletionItemProvider failed to provide correct kind of member function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes interface super member property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(58, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let propertyItem = findCompletionItem("superMemberProperty", items);
            assert.notEqual(
              propertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super member property: " +
                uri
            );
            assert.strictEqual(
              propertyItem.kind,
              vscode.CompletionItemKind.Property,
              "vscode.executeCompletionItemProvider failed to provide correct kind of super member property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes interface super member function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(58, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let functionItem = findCompletionItem("superMemberFunction", items);
            assert.notEqual(
              functionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super member function: " +
                uri
            );
            assert.strictEqual(
              functionItem.kind,
              vscode.CompletionItemKind.Method,
              "vscode.executeCompletionItemProvider failed to provide correct kind of super member function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes interface super super member property", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(58, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let propertyItem = findCompletionItem(
              "superSuperMemberProperty",
              items
            );
            assert.notEqual(
              propertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super super member property: " +
                uri
            );
            assert.strictEqual(
              propertyItem.kind,
              vscode.CompletionItemKind.Property,
              "vscode.executeCompletionItemProvider failed to provide correct kind of super super member property: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes interface super super member function", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(58, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let functionItem = findCompletionItem(
              "superSuperMemberFunction",
              items
            );
            assert.notEqual(
              functionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide super super member function: " +
                uri
            );
            assert.strictEqual(
              functionItem.kind,
              vscode.CompletionItemKind.Method,
              "vscode.executeCompletionItemProvider failed to provide correct kind of super super member function: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider empty inside string literal", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(59, 33);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            assert.ok(
              !containsCompletionItemsOtherThanTextOrSnippet(list.items),
              "vscode.executeCompletionItemProvider incorrectly provides items inside a string literal"
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider empty inside RegExp literal", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(60, 33);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            assert.ok(
              !containsCompletionItemsOtherThanTextOrSnippet(list.items),
              "vscode.executeCompletionItemProvider incorrectly provides items inside a RegExp literal"
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes package name for package block", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "PackageCompletion.as"
      )
    );
    let position = new vscode.Position(0, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let packageItem = findCompletionItem("com.example", items);
            assert.notEqual(
              packageItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide package name: " +
                uri
            );
            assert.strictEqual(
              packageItem.kind,
              vscode.CompletionItemKind.Module,
              "vscode.executeCompletionItemProvider failed to provide correct kind of package name: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes package name at end of package keyword", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "PackageCompletion4.as"
      )
    );
    let position = new vscode.Position(0, 7);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let packageItem = findCompletionItem(
              "package com.example {}",
              items
            );
            assert.notEqual(
              packageItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide package name: " +
                uri
            );
            assert.strictEqual(
              packageItem.kind,
              vscode.CompletionItemKind.Module,
              "vscode.executeCompletionItemProvider failed to provide correct kind of package name: " +
                uri
            );
            let snippet = packageItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "package com.example\n{\n\t$0\n}",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for package name: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes package name for unfinished package block", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "PackageCompletion2.as"
      )
    );
    let position = new vscode.Position(0, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let packageItem = findCompletionItem("com.example", items);
            assert.notEqual(
              packageItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide package name: " +
                uri
            );
            assert.strictEqual(
              packageItem.kind,
              vscode.CompletionItemKind.Module,
              "vscode.executeCompletionItemProvider failed to provide correct kind of package name: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes package name for empty file", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "PackageCompletion3.as"
      )
    );
    let position = new vscode.Position(0, 0);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let packageItem = findCompletionItem(
              "package com.example {}",
              items
            );
            assert.notEqual(
              packageItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide package name: " +
                uri
            );
            assert.strictEqual(
              packageItem.kind,
              vscode.CompletionItemKind.Module,
              "vscode.executeCompletionItemProvider failed to provide correct kind of package name: " +
                uri
            );
            let snippet = packageItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "package com.example\n{\n\t$0\n}",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for package name: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes protected member function on protected override", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(63, 30);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let functionItem = findCompletionItem("superMemberFunction", items);
            assert.notEqual(
              functionItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide protected member function on protected override " +
                uri
            );
            assert.strictEqual(
              functionItem.kind,
              vscode.CompletionItemKind.Method,
              "vscode.executeCompletionItemProvider failed to provide correct kind of override: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider excludes public member function on protected override", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Completion.as"
      )
    );
    let position = new vscode.Position(63, 30);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let functionItem = findCompletionItem(
              "superMemberFunction2",
              items
            );
            assert.equal(
              functionItem,
              null,
              "vscode.executeCompletionItemProvider incorrectly provided public member function on protected override: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
});

suite("MXML completion item provider: Application workspace", () => {
  suiteTeardown(revertAndCloseAllEditors);
  test("vscode.executeCompletionItemProvider includes property as attribute", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(9, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let propertyItem = findCompletionItem("className", items);
            assert.notEqual(
              propertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide property as attribute: " +
                uri
            );
            assert.strictEqual(
              propertyItem.kind,
              vscode.CompletionItemKind.Property,
              "vscode.executeCompletionItemProvider failed to provide correct kind of property: " +
                uri
            );
            let snippet = propertyItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              'className="$0"',
              "vscode.executeCompletionItemProvider failed to provide correct insert text for property as attribute: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes property as child element (after < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(10, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let propertyItem = findCompletionItem("className", items);
            assert.notEqual(
              propertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide property as child element: " +
                uri
            );
            assert.strictEqual(
              propertyItem.kind,
              vscode.CompletionItemKind.Property,
              "vscode.executeCompletionItemProvider failed to provide correct kind of property: " +
                uri
            );
            let snippet = propertyItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "js:className>$0</js:className>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for property as child element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes property as child element (without < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(24, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let propertyItem = findCompletionItem("className", items);
            assert.notEqual(
              propertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide property as child element: " +
                uri
            );
            assert.strictEqual(
              propertyItem.kind,
              vscode.CompletionItemKind.Property,
              "vscode.executeCompletionItemProvider failed to provide correct kind of property: " +
                uri
            );
            let snippet = propertyItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "<js:className>$0</js:className>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for property as child element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes property as child element (after < bracket and existing prefix)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(14, 12);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let propertyItem = findCompletionItem("className", items);
            assert.notEqual(
              propertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide property as child element: " +
                uri
            );
            assert.strictEqual(
              propertyItem.kind,
              vscode.CompletionItemKind.Property,
              "vscode.executeCompletionItemProvider failed to provide correct kind of property: " +
                uri
            );
            let snippet = propertyItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "className>$0</js:className>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for property as child element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits property as attribute of closing element", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(11, 14);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let propertyItem = findCompletionItem("className", items);
            assert.equal(
              propertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit property as attribute of closing element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes member variable as attribute", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(9, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let propertyItem = findCompletionItem("beads", items);
            assert.notEqual(
              propertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide member variable as attribute: " +
                uri
            );
            assert.strictEqual(
              propertyItem.kind,
              vscode.CompletionItemKind.Field,
              "vscode.executeCompletionItemProvider failed to provide correct kind of member variable: " +
                uri
            );
            let snippet = propertyItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              'beads="$0"',
              "vscode.executeCompletionItemProvider failed to provide correct insert text for member variable as attribute: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes member variable as child element (after < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(10, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let propertyItem = findCompletionItem("beads", items);
            assert.notEqual(
              propertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide member variable as child element: " +
                uri
            );
            assert.strictEqual(
              propertyItem.kind,
              vscode.CompletionItemKind.Field,
              "vscode.executeCompletionItemProvider failed to provide correct kind of member variable: " +
                uri
            );
            let snippet = propertyItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "js:beads>$0</js:beads>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for member variable as child element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes member variable as child element (without < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(24, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let propertyItem = findCompletionItem("beads", items);
            assert.notEqual(
              propertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide member variable as child element: " +
                uri
            );
            assert.strictEqual(
              propertyItem.kind,
              vscode.CompletionItemKind.Field,
              "vscode.executeCompletionItemProvider failed to provide correct kind of member variable: " +
                uri
            );
            let snippet = propertyItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "<js:beads>$0</js:beads>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for member variable as child element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes member variable as child element (after < bracket and exiting prefix)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(14, 12);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let propertyItem = findCompletionItem("beads", items);
            assert.notEqual(
              propertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide member variable as child element: " +
                uri
            );
            assert.strictEqual(
              propertyItem.kind,
              vscode.CompletionItemKind.Field,
              "vscode.executeCompletionItemProvider failed to provide correct kind of member variable: " +
                uri
            );
            let snippet = propertyItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "beads>$0</js:beads>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for member variable as child element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits member variable as attribute of closing element", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(11, 14);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let propertyItem = findCompletionItem("beads", items);
            assert.equal(
              propertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit member variable as attribute of closing element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes event as attribute", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(9, 13);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let eventItem = findCompletionItem("click", items);
            assert.notEqual(
              eventItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide event as attribute: " +
                uri
            );
            assert.strictEqual(
              eventItem.kind,
              vscode.CompletionItemKind.Event,
              "vscode.executeCompletionItemProvider failed to provide correct kind of event: " +
                uri
            );
            let snippet = eventItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              'click="$0"',
              "vscode.executeCompletionItemProvider failed to provide correct insert text for event as attribute: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes event as child element (after < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(10, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let eventItem = findCompletionItem("click", items);
            assert.notEqual(
              eventItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide event as child element: " +
                uri
            );
            assert.strictEqual(
              eventItem.kind,
              vscode.CompletionItemKind.Event,
              "vscode.executeCompletionItemProvider failed to provide correct kind of event: " +
                uri
            );
            let snippet = eventItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "js:click>$0</js:click>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for event as child element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes event as child element (without < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(24, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let eventItem = findCompletionItem("click", items);
            assert.notEqual(
              eventItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide event as child element: " +
                uri
            );
            assert.strictEqual(
              eventItem.kind,
              vscode.CompletionItemKind.Event,
              "vscode.executeCompletionItemProvider failed to provide correct kind of event: " +
                uri
            );
            let snippet = eventItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "<js:click>$0</js:click>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for event as child element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes event as child element (after < bracket and existing prefix)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(14, 12);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let eventItem = findCompletionItem("click", items);
            assert.notEqual(
              eventItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide event as child element: " +
                uri
            );
            assert.strictEqual(
              eventItem.kind,
              vscode.CompletionItemKind.Event,
              "vscode.executeCompletionItemProvider failed to provide correct kind of event: " +
                uri
            );
            let snippet = eventItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "click>$0</js:click>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for event as child element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits event as attribute of closing element", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(11, 14);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let eventItem = findCompletionItem("click", items);
            assert.equal(
              eventItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit event as attribute of closing element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes class as child element (after < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(10, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let packageClassItem = findCompletionItem(
              "example:UnreferencedClass",
              items
            );
            assert.notEqual(
              packageClassItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide package class: " +
                uri
            );
            assert.strictEqual(
              packageClassItem.kind,
              vscode.CompletionItemKind.Class,
              "vscode.executeCompletionItemProvider failed to provide correct kind of package class: " +
                uri
            );
            assert.strictEqual(
              packageClassItem.sortText,
              "UnreferencedClass",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for package class as child element: " +
                uri
            );
            assert.strictEqual(
              packageClassItem.filterText,
              "UnreferencedClass",
              "vscode.executeCompletionItemProvider failed to provide correct filter text for package class as child element: " +
                uri
            );
            assert.strictEqual(
              packageClassItem.insertText,
              "example:UnreferencedClass",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for package class as child element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes class as child element (without < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(24, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let packageClassItem = findCompletionItem(
              "example:UnreferencedClass",
              items
            );
            assert.notEqual(
              packageClassItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide package class: " +
                uri
            );
            assert.strictEqual(
              packageClassItem.kind,
              vscode.CompletionItemKind.Class,
              "vscode.executeCompletionItemProvider failed to provide correct kind of package class: " +
                uri
            );
            assert.strictEqual(
              packageClassItem.sortText,
              "UnreferencedClass",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for package class as child element: " +
                uri
            );
            assert.strictEqual(
              packageClassItem.filterText,
              "UnreferencedClass",
              "vscode.executeCompletionItemProvider failed to provide correct filter text for package class as child element: " +
                uri
            );
            assert.strictEqual(
              packageClassItem.insertText,
              "<example:UnreferencedClass",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for package class as child element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Binding> as child element of root (after < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(12, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Binding",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Binding>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Binding",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Binding>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Binding",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Binding>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "fx:Binding>\n\t$0\n</fx:Binding>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Binding>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Binding> as child element of root (without < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(26, 4);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Binding",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Binding>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Binding",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Binding>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Binding",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Binding>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "<fx:Binding>\n\t$0\n</fx:Binding>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Binding>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits <fx:Binding> as child of non-root element (after < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(10, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Binding",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.equal(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit <fx:Binding> in non-root element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits <fx:Binding> as child of non-root element (without < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(24, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Binding",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.equal(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit <fx:Binding> in non-root element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Binding> as child element of root with existing prefix", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(20, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Binding",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Binding>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Binding",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Binding>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Binding",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Binding>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "Binding>\n\t$0\n</fx:Binding>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Binding>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits <fx:Binding> as child of non-root element with existing prefix", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(18, 12);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Binding",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.equal(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit <fx:Binding> in non-root element existing prefix: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Component> as child element of root (after < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(12, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Component",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Component>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Component",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Component",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "fx:Component>\n\t$0\n</fx:Component>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Component>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Component> as child element of root (without < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(26, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Component",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Component>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Component",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Component",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "<fx:Component>\n\t$0\n</fx:Component>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Component>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Component> as child of non-root element (after < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(10, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Component",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Component>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Component",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Component",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "fx:Component>\n\t$0\n</fx:Component>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Component>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Component> as child of non-root element (without < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(24, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Component",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Component>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Component",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Component",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "<fx:Component>\n\t$0\n</fx:Component>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Component>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Component> as child element of root with existing prefix", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(20, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Component",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Component>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Component",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Component",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "Component>\n\t$0\n</fx:Component>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Component>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Component> as child of non-root element with existing prefix", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(18, 12);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Component",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Component>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Component",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Component",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Component>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "Component>\n\t$0\n</fx:Component>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Component>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Declarations> as child element of root (after < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(12, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Declarations",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Declarations>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Declarations",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Declarations>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Declarations",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Declarations>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "fx:Declarations>\n\t$0\n</fx:Declarations>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Declarations>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Declarations> as child element of root (without < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(26, 4);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Declarations",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Declarations>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Declarations",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Declarations>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Declarations",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Declarations>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "<fx:Declarations>\n\t$0\n</fx:Declarations>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Declarations>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits <fx:Declarations> as child of non-root element (after < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(10, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Declarations",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.equal(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit <fx:Declarations> in non-root element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits <fx:Declarations> as child of non-root element (without < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(24, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Declarations",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.equal(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit <fx:Declarations> in non-root element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Declarations> as child element of root with existing prefix", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(20, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Declarations",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Declarations>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Declarations",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Declarations>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Declarations",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Declarations>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "Declarations>\n\t$0\n</fx:Declarations>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Declarations>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits <fx:Declarations> as child of non-root element with existing prefix", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(18, 12);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Declarations",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.equal(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit <fx:Declarations> in non-root element existing prefix: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Metadata> as child element of root (after < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(12, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Metadata",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Metadata>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Metadata",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Metadata>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Metadata",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Metadata>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "fx:Metadata>\n\t$0\n</fx:Metadata>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Metadata>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Metadata> as child element of root (without < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(26, 4);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Metadata",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Metadata>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Metadata",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Metadata>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Metadata",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Metadata>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "<fx:Metadata>\n\t$0\n</fx:Metadata>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Metadata>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits <fx:Metadata> as child of non-root element (after < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(10, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Metadata",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.equal(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit <fx:Metadata> in non-root element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits <fx:Metadata> as child of non-root element (without < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(24, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Metadata",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.equal(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit <fx:Metadata> in non-root element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Metadata> as child element of root with existing prefix", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(20, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Metadata",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Metadata>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Metadata",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Metadata>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Metadata",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Metadata>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "Metadata>\n\t$0\n</fx:Metadata>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Metadata>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits <fx:Metadata> as child of non-root element with existing prefix", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(18, 12);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Metadata",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.equal(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit <fx:Metadata> in non-root element existing prefix: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Script> as child element of root (after < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(12, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Script",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Script>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Script",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Script>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Script",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Script>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "fx:Script>\n\t<![CDATA[\n\t\t$0\n\t]]>\n</fx:Script>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Script>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Script> as child element of root (without < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(26, 4);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Script",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Script>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Script",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Script>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Script",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Script>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "<fx:Script>\n\t<![CDATA[\n\t\t$0\n\t]]>\n</fx:Script>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Script>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits <fx:Script> as child of non-root element (after < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(10, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Script",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.equal(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit <fx:Script> in non-root element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits <fx:Script> as child of non-root element (without < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(24, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Script",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.equal(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit <fx:Script> in non-root element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Script> as child element of root with existing prefix", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(20, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Script",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Script>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Script",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Script>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Script",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Script>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "Script>\n\t<![CDATA[\n\t\t$0\n\t]]>\n</fx:Script>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Script>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits <fx:Script> as child of non-root element with existing prefix", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(18, 12);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Script",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.equal(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit <fx:Script> in non-root element existing prefix: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Style> as child element of root (after < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(12, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Style",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Style>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Style",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Style>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Style",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Style>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "fx:Style>\n\t$0\n</fx:Style>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Style>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Style> as child element of root (without < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(26, 4);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Style",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Style>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Style",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Style>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Style",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Style>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "<fx:Style>\n\t$0\n</fx:Style>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Style>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits <fx:Style> as child of non-root element (after < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(10, 9);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Style",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.equal(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit <fx:Style> in non-root element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits <fx:Style> as child of non-root element (without < bracket)", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(24, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Style",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.equal(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit <fx:Style> in non-root element: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes <fx:Style> as child element of root with existing prefix", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(20, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Style",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide <fx:Style>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.sortText,
              "Style",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Style>: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.filterText,
              "Style",
              "vscode.executeCompletionItemProvider failed to provide correct sort text for <fx:Style>: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              "Style>\n\t$0\n</fx:Style>",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for <fx:Style>: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits <fx:Style> as child of non-root element with existing prefix", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(18, 12);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "fx:Style",
              vscode.CompletionItemKind.Keyword,
              items
            );
            assert.equal(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit <fx:Style> in non-root element existing prefix: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes state for property attribute", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(21, 25);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let stateItem = findCompletionItemOfKind(
              "stateOne",
              vscode.CompletionItemKind.Field,
              items
            );
            assert.notEqual(
              stateItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide state for property attribute: " +
                uri
            );
            assert.strictEqual(
              stateItem.kind,
              vscode.CompletionItemKind.Field,
              "vscode.executeCompletionItemProvider failed to provide correct kind of state: " +
                uri
            );
            assert.strictEqual(
              stateItem.insertText,
              "stateOne",
              "vscode.executeCompletionItemProvider failed to provide correct insert text for state for property attribute: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes class in a package xmlns", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(22, 10);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let classItem = findCompletionItemOfKind(
              "MXMLPackageNS",
              vscode.CompletionItemKind.Class,
              items
            );
            assert.notEqual(
              classItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide class in a package xmlns: " +
                uri
            );
            assert.strictEqual(
              classItem.kind,
              vscode.CompletionItemKind.Class,
              "vscode.executeCompletionItemProvider failed to provide correct kind of class: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes class with existing prefix", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(16, 8);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "View",
              vscode.CompletionItemKind.Class,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide class with existing prefix: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.kind,
              vscode.CompletionItemKind.Class,
              "vscode.executeCompletionItemProvider failed to provide correct kind of class: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes class with partial name and existing prefix", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(30, 11);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "View",
              vscode.CompletionItemKind.Class,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to include class with partial name and existing prefix: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes class with full name and existing prefix", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(9, 12);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "View",
              vscode.CompletionItemKind.Class,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to include class with partial name and existing prefix: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits id as attribute of <fx:Declarations>", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(28, 21);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let propertyItem = findCompletionItem("id", items);
            assert.equal(
              propertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit keyword as attribute: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes id as attribute of <fx:Object>", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(29, 15);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let propertyItem = findCompletionItem("id", items);
            assert.notEqual(
              propertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to include keyword as attribute: " +
                uri
            );
            assert.strictEqual(
              propertyItem.kind,
              vscode.CompletionItemKind.Keyword,
              "vscode.executeCompletionItemProvider failed to provide correct kind of keyword as attribute: " +
                uri
            );
            let snippet = propertyItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              'id="$0"',
              "vscode.executeCompletionItemProvider failed to provide correct insert text for keyword as attribute: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits includeIn as attribute of <fx:Declarations>", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(28, 21);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let propertyItem = findCompletionItem("includeIn", items);
            assert.equal(
              propertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit keyword as attribute: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes includeIn as attribute of <fx:Object>", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(29, 15);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let propertyItem = findCompletionItem("includeIn", items);
            assert.notEqual(
              propertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to include keyword as attribute: " +
                uri
            );
            assert.strictEqual(
              propertyItem.kind,
              vscode.CompletionItemKind.Keyword,
              "vscode.executeCompletionItemProvider failed to provide correct kind of keyword as attribute: " +
                uri
            );
            let snippet = propertyItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              'includeIn="$0"',
              "vscode.executeCompletionItemProvider failed to provide correct insert text for keyword as attribute: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider omits excludeFrom as attribute of <fx:Declarations>", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(28, 21);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let propertyItem = findCompletionItem("excludeFrom", items);
            assert.equal(
              propertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to omit keyword as attribute: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider includes excludeFrom as attribute of <fx:Object>", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLCompletion.mxml"
      )
    );
    let position = new vscode.Position(29, 15);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let propertyItem = findCompletionItem("excludeFrom", items);
            assert.notEqual(
              propertyItem,
              null,
              "vscode.executeCompletionItemProvider failed to include keyword as attribute: " +
                uri
            );
            assert.strictEqual(
              propertyItem.kind,
              vscode.CompletionItemKind.Keyword,
              "vscode.executeCompletionItemProvider failed to provide correct kind of keyword as attribute: " +
                uri
            );
            let snippet = propertyItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              'excludeFrom="$0"',
              "vscode.executeCompletionItemProvider failed to provide correct insert text for keyword as attribute: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider works in an empty MXML file", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "mxmlCompletion",
        "MXMLCompletionEmpty.mxml"
      )
    );
    let position = new vscode.Position(0, 0);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "example:PackageClass",
              vscode.CompletionItemKind.Class,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide class: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.kind,
              vscode.CompletionItemKind.Class,
              "vscode.executeCompletionItemProvider failed to provide correct kind of class: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              '<example:PackageClass xmlns:fx="http://ns.adobe.com/mxml/2009"\n\txmlns:example="com.example.*">\n\t$0\n</example:PackageClass>'
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider works in an MXML file with only <", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "mxmlCompletion",
        "MXMLCompletionEmptyExceptBracket.mxml"
      )
    );
    let position = new vscode.Position(0, 1);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "example:PackageClass",
              vscode.CompletionItemKind.Class,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide class: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.kind,
              vscode.CompletionItemKind.Class,
              "vscode.executeCompletionItemProvider failed to provide correct kind of class: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              'example:PackageClass xmlns:fx="http://ns.adobe.com/mxml/2009"\n\txmlns:example="com.example.*">\n\t$0\n</example:PackageClass>'
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCompletionItemProvider works in an MXML file with partial root tag", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "mxmlCompletion",
        "MXMLCompletionPartialRootTag.mxml"
      )
    );
    let position = new vscode.Position(0, 12);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeCompletionItemProvider", uri, position)
        .then(
          (list: vscode.CompletionList) => {
            let items = list.items;
            let mxmlItem = findCompletionItemOfKind(
              "example:PackageClass",
              vscode.CompletionItemKind.Class,
              items
            );
            assert.notEqual(
              mxmlItem,
              null,
              "vscode.executeCompletionItemProvider failed to provide class: " +
                uri
            );
            assert.strictEqual(
              mxmlItem.kind,
              vscode.CompletionItemKind.Class,
              "vscode.executeCompletionItemProvider failed to provide correct kind of class: " +
                uri
            );
            let snippet = mxmlItem.insertText as vscode.SnippetString;
            assert.strictEqual(
              snippet.value,
              'example:PackageClass xmlns:fx="http://ns.adobe.com/mxml/2009"\n\txmlns:example="com.example.*">\n\t$0\n</example:PackageClass>'
            );
          },
          (err) => {
            assert(false, "Failed to execute completion item provider: " + uri);
          }
        );
    });
  });
});

suite("imports: Application workspace", () => {
  teardown(revertAndCloseActiveEditor);
  test("as3mxml.addImport adds import for qualified class inside package block", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "addImport",
        "AddImport.as"
      )
    );
    let qualifiedName = "com.example.PackageClass";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(COMMAND_ADD_IMPORT, qualifiedName, uri.toString(), 6, 9)
        .then(
          () => {
            return new Promise((resolve, reject) => {
              //the text edit is not applied immediately, so give
              //it a short delay before we check
              setTimeout(() => {
                let start = new vscode.Position(2, 0);
                let end = new vscode.Position(4, 0);
                let range = new vscode.Range(start, end);
                let importText = editor.document.getText(range);
                assert.strictEqual(
                  importText,
                  "\timport com.example.PackageClass;\n\n",
                  "as3mxml.addImport failed to add import in file: " + uri
                );
                resolve();
              }, 250);
            });
          },
          (err) => {
            assert(false, "Failed to execute add import command: " + uri);
          }
        );
    });
  });
  test("as3mxml.addImport adds import for qualified interface inside package block", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "addImport",
        "AddImport.as"
      )
    );
    let qualifiedName = "com.example.IPackageInterface";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          COMMAND_ADD_IMPORT,
          qualifiedName,
          uri.toString(),
          7,
          26
        )
        .then(
          () => {
            return new Promise((resolve, reject) => {
              //the text edit is not applied immediately, so give
              //it a short delay before we check
              setTimeout(() => {
                let start = new vscode.Position(2, 0);
                let end = new vscode.Position(4, 0);
                let range = new vscode.Range(start, end);
                let importText = editor.document.getText(range);
                assert.strictEqual(
                  importText,
                  "\timport com.example.IPackageInterface;\n\n",
                  "as3mxml.addImport failed to add import in file: " + uri
                );
                resolve();
              }, 250);
            });
          },
          (err) => {
            assert(false, "Failed to execute add import command: " + uri);
          }
        );
    });
  });
  test("as3mxml.addImport adds import for qualified class after package block", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "addImport",
        "AddImport.as"
      )
    );
    let qualifiedName = "com.example.PackageClass";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          COMMAND_ADD_IMPORT,
          qualifiedName,
          uri.toString(),
          11,
          25
        )
        .then(
          () => {
            return new Promise((resolve, reject) => {
              //the text edit is not applied immediately, so give
              //it a short delay before we check
              setTimeout(() => {
                let start = new vscode.Position(11, 0);
                let end = new vscode.Position(13, 0);
                let range = new vscode.Range(start, end);
                let importText = editor.document.getText(range);
                assert.strictEqual(
                  importText,
                  "import com.example.PackageClass;\n\n",
                  "as3mxml.addImport failed to add import in file: " + uri
                );
                resolve();
              }, 250);
            });
          },
          (err) => {
            assert(false, "Failed to execute add import command: " + uri);
          }
        );
    });
  });
  test("as3mxml.addImport adds import for qualified class inside MXML <fx:Script> tag", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "addImport",
        "MXMLAddImport.mxml"
      )
    );
    let qualifiedName = "com.example.PackageClass";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          COMMAND_ADD_IMPORT,
          qualifiedName,
          uri.toString(),
          7,
          17
        )
        .then(
          () => {
            return new Promise((resolve, reject) => {
              //the text edit is not applied immediately, so give
              //it a short delay before we check
              setTimeout(() => {
                let start = new vscode.Position(5, 0);
                let end = new vscode.Position(7, 0);
                let range = new vscode.Range(start, end);
                let importText = editor.document.getText(range);
                assert.strictEqual(
                  importText,
                  "\t\t\timport com.example.PackageClass;\n\n",
                  "as3mxml.addImport failed to add import in file: " + uri
                );
                resolve();
              }, 250);
            });
          },
          (err) => {
            assert(false, "Failed to execute add import command: " + uri);
          }
        );
    });
  });
  test("as3mxml.addImport adds import for qualified class inside MXML event to an empty <fx:Script> tag", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "addImport",
        "MXMLAddImportFromEventEmptyScript.mxml"
      )
    );
    let qualifiedName = "com.example.PackageClass";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          COMMAND_ADD_IMPORT,
          qualifiedName,
          uri.toString(),
          3,
          24
        )
        .then(
          () => {
            return new Promise((resolve, reject) => {
              //the text edit is not applied immediately, so give
              //it a short delay before we check
              setTimeout(() => {
                let start = new vscode.Position(6, 0);
                let end = new vscode.Position(8, 0);
                let range = new vscode.Range(start, end);
                let importText = editor.document.getText(range);
                assert.strictEqual(
                  importText,
                  "import com.example.PackageClass;\n\n",
                  "as3mxml.addImport failed to add import in file: " + uri
                );
                resolve();
              }, 250);
            });
          },
          (err) => {
            assert(false, "Failed to execute add import command: " + uri);
          }
        );
    });
  });
  test("as3mxml.addImport adds import for qualified class inside MXML event with no <fx:Script> tag", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "addImport",
        "MXMLAddImportFromEventNoScript.mxml"
      )
    );
    let qualifiedName = "com.example.PackageClass";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          COMMAND_ADD_IMPORT,
          qualifiedName,
          uri.toString(),
          3,
          24
        )
        .then(
          () => {
            return new Promise((resolve, reject) => {
              //the text edit is not applied immediately, so give
              //it a short delay before we check
              setTimeout(() => {
                let start = new vscode.Position(4, 0);
                let end = new vscode.Position(10, 0);
                let range = new vscode.Range(start, end);
                let importText = editor.document.getText(range);
                assert.strictEqual(
                  importText,
                  "\t<fx:Script>\n\t\t<![CDATA[\n\t\t\timport com.example.PackageClass;\n\n\t\t]]>\n\t</fx:Script>\n",
                  "as3mxml.addImport failed to add import in file: " + uri
                );
                resolve();
              }, 250);
            });
          },
          (err) => {
            assert(false, "Failed to execute add import command: " + uri);
          }
        );
    });
  });
  test("as3mxml.addImport adds import for qualified class inside MXML event with no <mx:Script> tag", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "addImport",
        "MXMLAddImportNoScript2006.mxml"
      )
    );
    let qualifiedName = "com.example.PackageClass";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          COMMAND_ADD_IMPORT,
          qualifiedName,
          uri.toString(),
          3,
          24
        )
        .then(
          () => {
            return new Promise((resolve, reject) => {
              //the text edit is not applied immediately, so give
              //it a short delay before we check
              setTimeout(() => {
                let start = new vscode.Position(4, 0);
                let end = new vscode.Position(10, 0);
                let range = new vscode.Range(start, end);
                let importText = editor.document.getText(range);
                assert.strictEqual(
                  importText,
                  "\t<mx:Script>\n\t\t<![CDATA[\n\t\t\timport com.example.PackageClass;\n\n\t\t]]>\n\t</mx:Script>\n",
                  "as3mxml.addImport failed to add import in file: " + uri
                );
                resolve();
              }, 250);
            });
          },
          (err) => {
            assert(false, "Failed to execute add import command: " + uri);
          }
        );
    });
  });
});

suite("mxml namespaces: Application workspace", () => {
  teardown(revertAndCloseActiveEditor);
  test("as3mxml.addMXMLNamespace adds new namespace", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLNamespace.mxml"
      )
    );
    let nsPrefix = "mx";
    let nsUri = "library://ns.adobe.com/flex/mx";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          COMMAND_ADD_MXML_NAMESPACE,
          nsPrefix,
          nsUri,
          uri.toString(),
          48,
          140
        )
        .then(
          () => {
            return new Promise((resolve, reject) => {
              //the text edit is not applied immediately, so give
              //it a short delay before we check
              setTimeout(() => {
                let start = new vscode.Position(2, 51);
                let end = new vscode.Position(2, 93);
                let range = new vscode.Range(start, end);
                let importText = editor.document.getText(range);
                assert.strictEqual(
                  importText,
                  ' xmlns:mx="library://ns.adobe.com/flex/mx"',
                  "as3mxml.addMXMLNamespace failed to add MXML namspace in file: " +
                    uri
                );
                resolve();
              }, 250);
            });
          },
          (err) => {
            assert(false, "Failed to execute add import command: " + uri);
          }
        );
    });
  });
});

suite("code action provider: imports : Application workspace", () => {
  suiteTeardown(revertAndCloseAllEditors);
  test("vscode.executeCodeActionProvider finds import for base class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(2, 45);
      let end = new vscode.Position(2, 45);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let typeToImport = "com.example.codeActions.CodeActionsBase";
            let codeAction = findImportCodeActionForType(
              typeToImport,
              codeActions
            );
            assert.notEqual(codeAction, null, "Code action not found");
            assert.strictEqual(
              codeAction.title,
              "Import " + typeToImport,
              "Code action provided incorrect title"
            );
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "\timport " + typeToImport + ";\n\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.notEqual(range, null, "Code action range invalid");
            assert.strictEqual(
              range.start.line,
              2,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              2,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider finds import for base class for file-internal class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(19, 50);
      let end = new vscode.Position(29, 50);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let typeToImport = "com.example.codeActions.CodeActionsBase";
            let codeAction = findImportCodeActionForType(
              typeToImport,
              codeActions
            );
            assert.notEqual(codeAction, null, "Code action not found");
            assert.strictEqual(
              codeAction.title,
              "Import " + typeToImport,
              "Code action provided incorrect title"
            );
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "import " + typeToImport + ";\n\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.notEqual(range, null, "Code action range invalid");
            assert.strictEqual(
              range.start.line,
              19,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              19,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider finds import for implemented interface", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(2, 75);
      let end = new vscode.Position(2, 75);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let typeToImport = "com.example.codeActions.ICodeActionsInterface";
            let codeAction = findImportCodeActionForType(
              typeToImport,
              codeActions
            );
            assert.notEqual(codeAction, null, "Code action not found");
            assert.strictEqual(
              codeAction.title,
              "Import " + typeToImport,
              "Code action provided incorrect title"
            );
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "\timport " + typeToImport + ";\n\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.notEqual(range, null, "Code action range invalid");
            assert.strictEqual(
              range.start.line,
              2,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              2,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider finds import for implemented interface in file-internal class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(19, 78);
      let end = new vscode.Position(19, 78);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let typeToImport = "com.example.codeActions.ICodeActionsInterface";
            let codeAction = findImportCodeActionForType(
              typeToImport,
              codeActions
            );
            assert.notEqual(codeAction, null, "Code action not found");
            assert.strictEqual(
              codeAction.title,
              "Import " + typeToImport,
              "Code action provided incorrect title"
            );
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "import " + typeToImport + ";\n\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.notEqual(range, null, "Code action range invalid");
            assert.strictEqual(
              range.start.line,
              19,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              19,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider finds import for new instance", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(7, 42);
      let end = new vscode.Position(7, 42);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let typeToImport = "com.example.codeActions.CodeActionsNew";
            let codeAction = findImportCodeActionForType(
              typeToImport,
              codeActions
            );
            assert.notEqual(codeAction, null, "Code action not found");
            assert.strictEqual(
              codeAction.title,
              "Import " + typeToImport,
              "Code action provided incorrect title"
            );
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "\timport " + typeToImport + ";\n\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.notEqual(range, null, "Code action range invalid");
            assert.strictEqual(
              range.start.line,
              2,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              2,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider finds import for new instance in file-internal class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(24, 40);
      let end = new vscode.Position(24, 40);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let typeToImport = "com.example.codeActions.CodeActionsNew";
            let codeAction = findImportCodeActionForType(
              typeToImport,
              codeActions
            );
            assert.notEqual(codeAction, null, "Code action not found");
            assert.strictEqual(
              codeAction.title,
              "Import " + typeToImport,
              "Code action provided incorrect title"
            );
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "import " + typeToImport + ";\n\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.notEqual(range, null, "Code action range invalid");
            assert.strictEqual(
              range.start.line,
              19,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              19,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider finds import for variable type", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(6, 22);
      let end = new vscode.Position(6, 22);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let typeToImport = "com.example.codeActions.CodeActionsVarType";
            let codeAction = findImportCodeActionForType(
              typeToImport,
              codeActions
            );
            assert.notEqual(codeAction, null, "Code action not found");
            assert.strictEqual(
              codeAction.title,
              "Import " + typeToImport,
              "Code action provided incorrect title"
            );
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "\timport " + typeToImport + ";\n\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.notEqual(range, null, "Code action range invalid");
            assert.strictEqual(
              range.start.line,
              2,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              2,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider finds import for variable type in file-internal class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(23, 20);
      let end = new vscode.Position(23, 20);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let typeToImport = "com.example.codeActions.CodeActionsVarType";
            let codeAction = findImportCodeActionForType(
              typeToImport,
              codeActions
            );
            assert.notEqual(codeAction, null, "Code action not found");
            assert.strictEqual(
              codeAction.title,
              "Import " + typeToImport,
              "Code action provided incorrect title"
            );
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "import " + typeToImport + ";\n\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.notEqual(range, null, "Code action range invalid");
            assert.strictEqual(
              range.start.line,
              19,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              19,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider finds import for parameter type", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(12, 46);
      let end = new vscode.Position(12, 46);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let typeToImport = "com.example.codeActions.CodeActionsParamType";
            let codeAction = findImportCodeActionForType(
              typeToImport,
              codeActions
            );
            assert.notEqual(codeAction, null, "Code action not found");
            assert.strictEqual(
              codeAction.title,
              "Import " + typeToImport,
              "Code action provided incorrect title"
            );
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "\timport " + typeToImport + ";\n\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.notEqual(range, null, "Code action range invalid");
            assert.strictEqual(
              range.start.line,
              2,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              2,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider finds import for parameter type in file-internal class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(29, 46);
      let end = new vscode.Position(29, 46);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let typeToImport = "com.example.codeActions.CodeActionsParamType";
            let codeAction = findImportCodeActionForType(
              typeToImport,
              codeActions
            );
            assert.notEqual(codeAction, null, "Code action not found");
            assert.strictEqual(
              codeAction.title,
              "Import " + typeToImport,
              "Code action provided incorrect title"
            );
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "import " + typeToImport + ";\n\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.notEqual(range, null, "Code action range invalid");
            assert.strictEqual(
              range.start.line,
              19,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              19,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider finds import for return type", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(12, 70);
      let end = new vscode.Position(12, 70);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let typeToImport = "com.example.codeActions.CodeActionsReturnType";
            let codeAction = findImportCodeActionForType(
              typeToImport,
              codeActions
            );
            assert.notEqual(codeAction, null, "Code action not found");
            assert.strictEqual(
              codeAction.title,
              "Import " + typeToImport,
              "Code action provided incorrect title"
            );
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "\timport " + typeToImport + ";\n\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.notEqual(range, null, "Code action range invalid");
            assert.strictEqual(
              range.start.line,
              2,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              2,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider finds import for return type in file-internal class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(29, 68);
      let end = new vscode.Position(29, 68);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let typeToImport = "com.example.codeActions.CodeActionsReturnType";
            let codeAction = findImportCodeActionForType(
              typeToImport,
              codeActions
            );
            assert.notEqual(codeAction, null, "Code action not found");
            assert.strictEqual(
              codeAction.title,
              "Import " + typeToImport,
              "Code action provided incorrect title"
            );
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "import " + typeToImport + ";\n\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.notEqual(range, null, "Code action range invalid");
            assert.strictEqual(
              range.start.line,
              19,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              19,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider finds import for type in assignment", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(8, 33);
      let end = new vscode.Position(8, 33);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let typeToImport = "com.example.codeActions.CodeActionsAssign";
            let codeAction = findImportCodeActionForType(
              typeToImport,
              codeActions
            );
            assert.notEqual(codeAction, null, "Code action not found");
            assert.strictEqual(
              codeAction.title,
              "Import " + typeToImport,
              "Code action provided incorrect title"
            );
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "\timport " + typeToImport + ";\n\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.notEqual(range, null, "Code action range invalid");
            assert.strictEqual(
              range.start.line,
              2,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              2,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider finds import for type in assignment in file-internal class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(25, 32);
      let end = new vscode.Position(25, 32);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let typeToImport = "com.example.codeActions.CodeActionsAssign";
            let codeAction = findImportCodeActionForType(
              typeToImport,
              codeActions
            );
            assert.notEqual(codeAction, null, "Code action not found");
            assert.strictEqual(
              codeAction.title,
              "Import " + typeToImport,
              "Code action provided incorrect title"
            );
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "import " + typeToImport + ";\n\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.notEqual(range, null, "Code action range invalid");
            assert.strictEqual(
              range.start.line,
              19,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              19,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider finds import for multiple types with the same base name", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(9, 8);
      let end = new vscode.Position(9, 8);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let typeToImport1 = "com.example.codeActions.CodeActionsMultiple";
            let codeAction1 = findImportCodeActionForType(
              typeToImport1,
              codeActions
            );
            assert.notEqual(codeAction1, null, "Code action 1 not found");
            assert.strictEqual(
              codeAction1.title,
              "Import " + typeToImport1,
              "Code action 1 provided incorrect title"
            );
            assert.strictEqual(
              codeAction1.command,
              undefined,
              "Code action 1 provided incorrect command"
            );
            assert.strictEqual(
              codeAction1.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action 1 provided incorrect kind"
            );
            let workspaceEdit1 = codeAction1.edit;
            assert.notEqual(
              workspaceEdit1,
              undefined,
              "Code action 1 missing workspace edit"
            );
            assert.ok(
              workspaceEdit1.has(uri),
              "Code action 1 workspace edit missing URI: " + uri
            );
            let textEdits1 = workspaceEdit1.get(uri);
            assert.strictEqual(textEdits1.length, 1);
            let textEdit1 = textEdits1[0];
            assert.strictEqual(
              textEdit1.newText,
              "\timport " + typeToImport1 + ";\n\n",
              "Code action 1 workspace edit provided incorrect new text"
            );
            let range1 = textEdit1.range;
            assert.notEqual(range1, null, "Code action 1 range invalid");
            assert.strictEqual(
              range1.start.line,
              2,
              "Code action 1 workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range1.start.character,
              0,
              "Code action 1 workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range1.end.line,
              2,
              "Code action 1 workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range1.end.character,
              0,
              "Code action 1 workspace edit provided incorrect end character"
            );

            let typeToImport2 =
              "com.example.codeActions.more.CodeActionsMultiple";
            let codeAction2 = findImportCodeActionForType(
              typeToImport2,
              codeActions
            );
            assert.notEqual(codeAction2, null, "Code action 2 not found");
            assert.strictEqual(
              codeAction2.title,
              "Import " + typeToImport2,
              "Code action 2 provided incorrect title"
            );
            assert.strictEqual(
              codeAction2.command,
              undefined,
              "Code action 2 provided incorrect command"
            );
            assert.strictEqual(
              codeAction2.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action 2 provided incorrect kind"
            );
            let workspaceEdit2 = codeAction2.edit;
            assert.notEqual(
              workspaceEdit2,
              undefined,
              "Code action 2 missing workspace edit"
            );
            assert.ok(
              workspaceEdit2.has(uri),
              "Code action 2 workspace edit missing URI: " + uri
            );
            let textEdits2 = workspaceEdit2.get(uri);
            assert.strictEqual(textEdits2.length, 1);
            let textEdit2 = textEdits2[0];
            assert.strictEqual(
              textEdit2.newText,
              "\timport " + typeToImport2 + ";\n\n",
              "Code action 2 workspace edit provided incorrect new text"
            );
            let range2 = textEdit2.range;
            assert.notEqual(range2, null, "Code action 2 range invalid");
            assert.strictEqual(
              range2.start.line,
              2,
              "Code action 2 workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range2.start.character,
              0,
              "Code action 2 workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range2.end.line,
              2,
              "Code action 2 workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range2.end.character,
              0,
              "Code action 2 workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider finds import for multiple types with the same base name in file-internal class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(26, 6);
      let end = new vscode.Position(26, 6);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let typeToImport1 = "com.example.codeActions.CodeActionsMultiple";
            let codeAction1 = findImportCodeActionForType(
              typeToImport1,
              codeActions
            );
            assert.notEqual(codeAction1, null, "Code action 1 not found");
            assert.strictEqual(
              codeAction1.title,
              "Import " + typeToImport1,
              "Code action 1 provided incorrect title"
            );
            assert.strictEqual(
              codeAction1.command,
              undefined,
              "Code action 1 provided incorrect command"
            );
            assert.strictEqual(
              codeAction1.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action 1 provided incorrect kind"
            );
            let workspaceEdit1 = codeAction1.edit;
            assert.notEqual(
              workspaceEdit1,
              undefined,
              "Code action 1 missing workspace edit"
            );
            assert.ok(
              workspaceEdit1.has(uri),
              "Code action 1 workspace edit missing URI: " + uri
            );
            let textEdits1 = workspaceEdit1.get(uri);
            assert.strictEqual(textEdits1.length, 1);
            let textEdit1 = textEdits1[0];
            assert.strictEqual(
              textEdit1.newText,
              "import " + typeToImport1 + ";\n\n",
              "Code action 1 workspace edit provided incorrect new text"
            );
            let range1 = textEdit1.range;
            assert.notEqual(range1, null, "Code action 1 range invalid");
            assert.strictEqual(
              range1.start.line,
              19,
              "Code action 1 workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range1.start.character,
              0,
              "Code action 1 workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range1.end.line,
              19,
              "Code action 1 workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range1.end.character,
              0,
              "Code action 1 workspace edit provided incorrect end character"
            );

            let typeToImport2 =
              "com.example.codeActions.more.CodeActionsMultiple";
            let codeAction2 = findImportCodeActionForType(
              typeToImport2,
              codeActions
            );
            assert.notEqual(codeAction2, null, "Code action 2 not found");
            assert.strictEqual(
              codeAction2.title,
              "Import " + typeToImport2,
              "Code action 2 provided incorrect title"
            );
            assert.strictEqual(
              codeAction2.command,
              undefined,
              "Code action 2 provided incorrect command"
            );
            assert.strictEqual(
              codeAction2.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action 2 provided incorrect kind"
            );
            let workspaceEdit2 = codeAction2.edit;
            assert.notEqual(
              workspaceEdit2,
              undefined,
              "Code action 2 missing workspace edit"
            );
            assert.ok(
              workspaceEdit2.has(uri),
              "Code action 2 workspace edit missing URI: " + uri
            );
            let textEdits2 = workspaceEdit2.get(uri);
            assert.strictEqual(textEdits2.length, 1);
            let textEdit2 = textEdits2[0];
            assert.strictEqual(
              textEdit2.newText,
              "import " + typeToImport2 + ";\n\n",
              "Code action 2 workspace edit provided incorrect new text"
            );
            let range2 = textEdit2.range;
            assert.notEqual(range2, null, "Code action 2 range invalid");
            assert.strictEqual(
              range2.start.line,
              19,
              "Code action 2 workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range2.start.character,
              0,
              "Code 2 action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range2.end.line,
              19,
              "Code action 2 workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range2.end.character,
              0,
              "Code action 2 workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider adds new import after existing imports", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsExistingImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(8, 22);
      let end = new vscode.Position(8, 22);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let typeToImport = "com.example.codeActions.CodeActionsVarType";
            let codeAction = findImportCodeActionForType(
              typeToImport,
              codeActions
            );
            assert.notEqual(codeAction, null, "Code action not found");
            assert.strictEqual(
              codeAction.title,
              "Import " + typeToImport,
              "Code action provided incorrect title"
            );
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "\timport " + typeToImport + ";\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.notEqual(range, null, "Code action range invalid");
            assert.strictEqual(
              range.start.line,
              3,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              3,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider adds new import after existing imports for file-internal class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsExistingImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(18, 22);
      let end = new vscode.Position(18, 22);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let typeToImport = "com.example.codeActions.CodeActionsVarType";
            let codeAction = findImportCodeActionForType(
              typeToImport,
              codeActions
            );
            assert.notEqual(codeAction, null, "Code action not found");
            assert.strictEqual(
              codeAction.title,
              "Import " + typeToImport,
              "Code action provided incorrect title"
            );
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "import " + typeToImport + ";\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.notEqual(range, null, "Code action range invalid");
            assert.strictEqual(
              range.start.line,
              13,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              13,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider finds import for type in MXML script", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsMXMLImports.mxml"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(7, 0);
      let end = new vscode.Position(7, 38);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let typeToImport = "com.example.codeActions.CodeActionsVarType";
            let codeAction = findImportCodeActionForType(
              typeToImport,
              codeActions
            );
            assert.notEqual(codeAction, null, "Code action not found");
            assert.strictEqual(
              codeAction.title,
              "Import " + typeToImport,
              "Code action provided incorrect title"
            );
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            let range = textEdit.range;
            assert.strictEqual(
              range.start.line,
              5,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              5,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
            assert.strictEqual(
              textEdit.newText,
              "\t\t\timport " + typeToImport + ";\n\n",
              "Code action workspace edit provided incorrect new text"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider finds import for type in MXML event", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsMXMLImports.mxml"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(11, 0);
      let end = new vscode.Position(11, 42);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let typeToImport = "com.example.codeActions.CodeActionsNew";
            let codeAction = findImportCodeActionForType(
              typeToImport,
              codeActions
            );
            assert.notEqual(codeAction, null, "Code action not found");
            assert.strictEqual(
              codeAction.title,
              "Import " + typeToImport,
              "Code action provided incorrect title"
            );
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            let range = textEdit.range;
            assert.strictEqual(
              range.start.line,
              5,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              5,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
            assert.strictEqual(
              textEdit.newText,
              "\t\t\timport " + typeToImport + ";\n\n",
              "Code action workspace edit provided incorrect new text"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
});

suite(
  "code action provider: generate local variable : Application workspace",
  () => {
    suiteTeardown(revertAndCloseAllEditors);
    test("vscode.executeCodeActionProvider can generate local variable without this member access", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateVariable.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(6, 4);
        let end = new vscode.Position(6, 4);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate Local Variable";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.QuickFix.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "\t\t\tvar myVar:Object;\n",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                6,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                0,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                6,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                0,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate local variable without this member access for file-internal class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateVariable.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(16, 4);
        let end = new vscode.Position(16, 4);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate Local Variable";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.QuickFix.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "\t\tvar myVar:Object;\n",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                16,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                0,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                16,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                0,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider must not generate local variable with this member access", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateVariable.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(7, 10);
        let end = new vscode.Position(7, 10);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate Local Variable";
              });
              assert.strictEqual(
                codeAction,
                undefined,
                "Code action incorrectly found"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider must not generate local variable with this member access for file-internal class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateVariable.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(17, 10);
        let end = new vscode.Position(17, 10);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate Local Variable";
              });
              assert.strictEqual(
                codeAction,
                undefined,
                "Code action incorrectly found"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
  }
);

suite(
  "code action provider: generate field variable : Application workspace",
  () => {
    suiteTeardown(revertAndCloseAllEditors);
    test("vscode.executeCodeActionProvider can generate field variable without this member access", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateVariable.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(6, 4);
        let end = new vscode.Position(6, 4);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate Field Variable";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.QuickFix.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "\n\t\tpublic var myVar:Object;\n",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                9,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                0,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                9,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                0,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate field variable without this member access for file-internal class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateVariable.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(16, 4);
        let end = new vscode.Position(16, 4);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate Field Variable";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.QuickFix.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "\n\tpublic var myVar:Object;\n",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                19,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                0,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                19,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                0,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate field variable with this member access", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateVariable.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(7, 10);
        let end = new vscode.Position(7, 10);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate Field Variable";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.QuickFix.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "\n\t\tpublic var myVar:Object;\n",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                9,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                0,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                9,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                0,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate field variable with this member access for file-internal class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateVariable.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(17, 10);
        let end = new vscode.Position(17, 10);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate Field Variable";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.QuickFix.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "\n\tpublic var myVar:Object;\n",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                19,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                0,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                19,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                0,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
  }
);

suite("code action provider: generate method : Application workspace", () => {
  suiteTeardown(revertAndCloseAllEditors);
  test("vscode.executeCodeActionProvider can generate member method without parameters without this member access", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "GenerateMethod.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(6, 6);
      let end = new vscode.Position(6, 6);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let codeAction = codeActions.find((codeAction) => {
              return codeAction.title == "Generate Method";
            });
            assert.notEqual(codeAction, undefined, "Code action not found");
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "\n\t\tprivate function myMethod():void\n\t\t{\n\t\t}\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.strictEqual(
              range.start.line,
              11,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              11,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider can generate member method without parameters without this member access for file-internal class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "GenerateMethod.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(18, 4);
      let end = new vscode.Position(18, 4);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let codeAction = codeActions.find((codeAction) => {
              return codeAction.title == "Generate Method";
            });
            assert.notEqual(codeAction, undefined, "Code action not found");
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "\n\tprivate function myMethod():void\n\t{\n\t}\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.strictEqual(
              range.start.line,
              23,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              23,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider can generate member method without parameters with this member access", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "GenerateMethod.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(7, 10);
      let end = new vscode.Position(7, 10);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let codeAction = codeActions.find((codeAction) => {
              return codeAction.title == "Generate Method";
            });
            assert.notEqual(codeAction, undefined, "Code action not found");
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "\n\t\tprivate function myMethod():void\n\t\t{\n\t\t}\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.strictEqual(
              range.start.line,
              11,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              11,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider can generate member method without parameters with this member access for file-internal class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "GenerateMethod.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(19, 10);
      let end = new vscode.Position(19, 10);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let codeAction = codeActions.find((codeAction) => {
              return codeAction.title == "Generate Method";
            });
            assert.notEqual(codeAction, undefined, "Code action not found");
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "\n\tprivate function myMethod():void\n\t{\n\t}\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.strictEqual(
              range.start.line,
              23,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              23,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider can generate member method with parameters", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "GenerateMethod.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(8, 6);
      let end = new vscode.Position(8, 6);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let codeAction = codeActions.find((codeAction) => {
              return codeAction.title == "Generate Method";
            });
            assert.notEqual(codeAction, undefined, "Code action not found");
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "\n\t\tprivate function myMethod(param0:Number, param1:Boolean, param2:String):void\n\t\t{\n\t\t}\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.strictEqual(
              range.start.line,
              11,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              11,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeCodeActionProvider can generate member method with parameters for file-internal class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "GenerateMethod.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(20, 4);
      let end = new vscode.Position(20, 4);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let codeAction = codeActions.find((codeAction) => {
              return codeAction.title == "Generate Method";
            });
            assert.notEqual(codeAction, undefined, "Code action not found");
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "\n\tprivate function myMethod(param0:Number, param1:Boolean, param2:String):void\n\t{\n\t}\n",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.strictEqual(
              range.start.line,
              23,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              0,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              23,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              0,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test('vscode.executeCodeActionProvider must not generate member method with from "new" expression', () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "GenerateMethod.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(9, 6);
      let end = new vscode.Position(9, 6);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let codeAction = codeActions.find((codeAction) => {
              return codeAction.title == "Generate Method";
            });
            assert.strictEqual(
              codeAction,
              undefined,
              "Code action incorrectly found"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test('vscode.executeCodeActionProvider must not generate member method with from "new" expression in file-internal class', () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "GenerateMethod.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(21, 8);
      let end = new vscode.Position(21, 8);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let codeAction = codeActions.find((codeAction) => {
              return codeAction.title == "Generate Method";
            });
            assert.strictEqual(
              codeAction,
              undefined,
              "Code action incorrectly found"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
});

suite(
  "code action provider: generate getter and setter : Application workspace",
  () => {
    suiteTeardown(revertAndCloseAllEditors);
    test("vscode.executeCodeActionProvider can generate getter without assignment", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(7, 16);
        let end = new vscode.Position(7, 16);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return (
                  codeAction.title == "Generate 'get' accessor (make read-only)"
                );
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private var _noAssignment:Object;\n\n\t\tpublic function get noAssignment():Object\n\t\t{\n\t\t\treturn _noAssignment;\n\t\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                7,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                2,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                7,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                33,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate getter without assignment for file-internal class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(26, 14);
        let end = new vscode.Position(26, 14);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return (
                  codeAction.title == "Generate 'get' accessor (make read-only)"
                );
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private var _noAssignment:Object;\n\n\tpublic function get noAssignment():Object\n\t{\n\t\treturn _noAssignment;\n\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                26,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                1,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                26,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                32,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate setter without assignment", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(7, 16);
        let end = new vscode.Position(7, 16);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return (
                  codeAction.title ==
                  "Generate 'set' accessor (make write-only)"
                );
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private var _noAssignment:Object;\n\n\t\tpublic function set noAssignment(value:Object):void\n\t\t{\n\t\t\t_noAssignment = value;\n\t\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                7,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                2,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                7,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                33,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate setter without assignment for file-internal class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(26, 14);
        let end = new vscode.Position(26, 14);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return (
                  codeAction.title ==
                  "Generate 'set' accessor (make write-only)"
                );
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private var _noAssignment:Object;\n\n\tpublic function set noAssignment(value:Object):void\n\t{\n\t\t_noAssignment = value;\n\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                26,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                1,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                26,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                32,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate getter and setter without assignment", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(7, 16);
        let end = new vscode.Position(7, 16);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate 'get' and 'set' accessors";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private var _noAssignment:Object;\n\n\t\tpublic function get noAssignment():Object\n\t\t{\n\t\t\treturn _noAssignment;\n\t\t}\n\n\t\tpublic function set noAssignment(value:Object):void\n\t\t{\n\t\t\t_noAssignment = value;\n\t\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                7,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                2,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                7,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                33,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate getter and setter without assignment for file-internal class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(26, 14);
        let end = new vscode.Position(26, 14);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate 'get' and 'set' accessors";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private var _noAssignment:Object;\n\n\tpublic function get noAssignment():Object\n\t{\n\t\treturn _noAssignment;\n\t}\n\n\tpublic function set noAssignment(value:Object):void\n\t{\n\t\t_noAssignment = value;\n\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                26,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                1,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                26,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                32,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate getter with assignment", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(9, 16);
        let end = new vscode.Position(9, 16);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return (
                  codeAction.title == "Generate 'get' accessor (make read-only)"
                );
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                'private var _assignment:String = "hello";\n\n\t\tpublic function get assignment():String\n\t\t{\n\t\t\treturn _assignment;\n\t\t}',
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                9,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                2,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                9,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                41,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate getter with assignment for file-internal class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(28, 14);
        let end = new vscode.Position(28, 14);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return (
                  codeAction.title == "Generate 'get' accessor (make read-only)"
                );
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                'private var _assignment:String = "hello";\n\n\tpublic function get assignment():String\n\t{\n\t\treturn _assignment;\n\t}',
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                28,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                1,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                28,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                40,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate setter with assignment", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(9, 16);
        let end = new vscode.Position(9, 16);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return (
                  codeAction.title ==
                  "Generate 'set' accessor (make write-only)"
                );
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                'private var _assignment:String = "hello";\n\n\t\tpublic function set assignment(value:String):void\n\t\t{\n\t\t\t_assignment = value;\n\t\t}',
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                9,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                2,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                9,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                41,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate setter with assignment for file-internal class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(28, 14);
        let end = new vscode.Position(28, 14);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return (
                  codeAction.title ==
                  "Generate 'set' accessor (make write-only)"
                );
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                'private var _assignment:String = "hello";\n\n\tpublic function set assignment(value:String):void\n\t{\n\t\t_assignment = value;\n\t}',
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                28,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                1,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                28,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                40,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate getter and setter with assignment", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(9, 16);
        let end = new vscode.Position(9, 16);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate 'get' and 'set' accessors";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                'private var _assignment:String = "hello";\n\n\t\tpublic function get assignment():String\n\t\t{\n\t\t\treturn _assignment;\n\t\t}\n\n\t\tpublic function set assignment(value:String):void\n\t\t{\n\t\t\t_assignment = value;\n\t\t}',
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                9,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                2,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                9,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                41,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate getter and setter with assignment for file-internal class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(28, 14);
        let end = new vscode.Position(28, 14);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate 'get' and 'set' accessors";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                'private var _assignment:String = "hello";\n\n\tpublic function get assignment():String\n\t{\n\t\treturn _assignment;\n\t}\n\n\tpublic function set assignment(value:String):void\n\t{\n\t\t_assignment = value;\n\t}',
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                28,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                1,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                28,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                40,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate static getter without assignment", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(11, 24);
        let end = new vscode.Position(11, 24);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return (
                  codeAction.title == "Generate 'get' accessor (make read-only)"
                );
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private static var _isStatic:Boolean;\n\n\t\tpublic static function get isStatic():Boolean\n\t\t{\n\t\t\treturn _isStatic;\n\t\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                11,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                2,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                11,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                37,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate static getter without assignment for file-internal class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(30, 22);
        let end = new vscode.Position(30, 22);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return (
                  codeAction.title == "Generate 'get' accessor (make read-only)"
                );
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private static var _isStatic:Boolean;\n\n\tpublic static function get isStatic():Boolean\n\t{\n\t\treturn _isStatic;\n\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                30,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                1,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                30,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                36,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate static setter without assignment", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(11, 24);
        let end = new vscode.Position(11, 24);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return (
                  codeAction.title ==
                  "Generate 'set' accessor (make write-only)"
                );
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private static var _isStatic:Boolean;\n\n\t\tpublic static function set isStatic(value:Boolean):void\n\t\t{\n\t\t\t_isStatic = value;\n\t\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                11,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                2,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                11,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                37,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate static setter without assignment for file-internal class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(30, 22);
        let end = new vscode.Position(30, 22);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return (
                  codeAction.title ==
                  "Generate 'set' accessor (make write-only)"
                );
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private static var _isStatic:Boolean;\n\n\tpublic static function set isStatic(value:Boolean):void\n\t{\n\t\t_isStatic = value;\n\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                30,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                1,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                30,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                36,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate static getter and setter without assignment", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(11, 24);
        let end = new vscode.Position(11, 24);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate 'get' and 'set' accessors";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private static var _isStatic:Boolean;\n\n\t\tpublic static function get isStatic():Boolean\n\t\t{\n\t\t\treturn _isStatic;\n\t\t}\n\n\t\tpublic static function set isStatic(value:Boolean):void\n\t\t{\n\t\t\t_isStatic = value;\n\t\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                11,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                2,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                11,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                37,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate static getter and setter without assignment for file-internal class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(30, 22);
        let end = new vscode.Position(30, 22);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate 'get' and 'set' accessors";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private static var _isStatic:Boolean;\n\n\tpublic static function get isStatic():Boolean\n\t{\n\t\treturn _isStatic;\n\t}\n\n\tpublic static function set isStatic(value:Boolean):void\n\t{\n\t\t_isStatic = value;\n\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                30,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                1,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                30,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                36,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate private getter without assignment", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(13, 16);
        let end = new vscode.Position(13, 16);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return (
                  codeAction.title == "Generate 'get' accessor (make read-only)"
                );
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private var _isPrivate:Object;\n\n\t\tprivate function get isPrivate():Object\n\t\t{\n\t\t\treturn _isPrivate;\n\t\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                13,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                2,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                13,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                31,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate private getter without assignment for file-internal class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(32, 14);
        let end = new vscode.Position(32, 14);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return (
                  codeAction.title == "Generate 'get' accessor (make read-only)"
                );
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private var _isPrivate:Object;\n\n\tprivate function get isPrivate():Object\n\t{\n\t\treturn _isPrivate;\n\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                32,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                1,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                32,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                30,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate private setter without assignment", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(13, 16);
        let end = new vscode.Position(13, 16);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return (
                  codeAction.title ==
                  "Generate 'set' accessor (make write-only)"
                );
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private var _isPrivate:Object;\n\n\t\tprivate function set isPrivate(value:Object):void\n\t\t{\n\t\t\t_isPrivate = value;\n\t\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                13,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                2,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                13,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                31,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate private setter without assignment for file-internal class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(32, 14);
        let end = new vscode.Position(32, 14);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return (
                  codeAction.title ==
                  "Generate 'set' accessor (make write-only)"
                );
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private var _isPrivate:Object;\n\n\tprivate function set isPrivate(value:Object):void\n\t{\n\t\t_isPrivate = value;\n\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                32,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                1,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                32,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                30,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate private getter and setter without assignment", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(13, 16);
        let end = new vscode.Position(13, 16);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate 'get' and 'set' accessors";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private var _isPrivate:Object;\n\n\t\tprivate function get isPrivate():Object\n\t\t{\n\t\t\treturn _isPrivate;\n\t\t}\n\n\t\tprivate function set isPrivate(value:Object):void\n\t\t{\n\t\t\t_isPrivate = value;\n\t\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                13,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                2,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                13,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                31,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate private getter and setter without assignment for file-internal class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(32, 14);
        let end = new vscode.Position(32, 14);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate 'get' and 'set' accessors";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private var _isPrivate:Object;\n\n\tprivate function get isPrivate():Object\n\t{\n\t\treturn _isPrivate;\n\t}\n\n\tprivate function set isPrivate(value:Object):void\n\t{\n\t\t_isPrivate = value;\n\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                32,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                1,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                32,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                30,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate getter without type without assignment", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(15, 16);
        let end = new vscode.Position(15, 16);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return (
                  codeAction.title == "Generate 'get' accessor (make read-only)"
                );
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private var _noType;\n\n\t\tpublic function get noType()\n\t\t{\n\t\t\treturn _noType;\n\t\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                15,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                2,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                15,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                20,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate getter without type without assignment for file-internal class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(34, 14);
        let end = new vscode.Position(34, 14);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return (
                  codeAction.title == "Generate 'get' accessor (make read-only)"
                );
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private var _noType;\n\n\tpublic function get noType()\n\t{\n\t\treturn _noType;\n\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                34,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                1,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                34,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                19,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate setter without type without assignment", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(15, 16);
        let end = new vscode.Position(15, 16);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return (
                  codeAction.title ==
                  "Generate 'set' accessor (make write-only)"
                );
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private var _noType;\n\n\t\tpublic function set noType(value):void\n\t\t{\n\t\t\t_noType = value;\n\t\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                15,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                2,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                15,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                20,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate setter without type without assignment for file-internal class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(34, 14);
        let end = new vscode.Position(34, 14);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return (
                  codeAction.title ==
                  "Generate 'set' accessor (make write-only)"
                );
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private var _noType;\n\n\tpublic function set noType(value):void\n\t{\n\t\t_noType = value;\n\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                34,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                1,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                34,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                19,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate getter and setter without type without assignment", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(15, 16);
        let end = new vscode.Position(15, 16);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate 'get' and 'set' accessors";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private var _noType;\n\n\t\tpublic function get noType()\n\t\t{\n\t\t\treturn _noType;\n\t\t}\n\n\t\tpublic function set noType(value):void\n\t\t{\n\t\t\t_noType = value;\n\t\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                15,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                2,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                15,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                20,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider can generate getter and setter without type without assignment for file-internal class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateGetterAndSetter.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(34, 14);
        let end = new vscode.Position(34, 14);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate 'get' and 'set' accessors";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.RefactorRewrite.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "private var _noType;\n\n\tpublic function get noType()\n\t{\n\t\treturn _noType;\n\t}\n\n\tpublic function set noType(value):void\n\t{\n\t\t_noType = value;\n\t}",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                34,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                1,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                34,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                19,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
  }
);

suite("code action provider: generate catch : Application workspace", () => {
  suiteTeardown(revertAndCloseAllEditors);
  test("vscode.executeCodeActionProvider can generate catch", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "CodeActionsTry.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(6, 4);
      let end = new vscode.Position(6, 4);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let codeAction = codeActions.find((codeAction) => {
              return codeAction.title == "Generate catch";
            });
            assert.notEqual(codeAction, undefined, "Code action not found");
            assert.strictEqual(
              codeAction.command,
              undefined,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.QuickFix.value,
              "Code action provided incorrect kind"
            );
            let workspaceEdit = codeAction.edit;
            assert.notEqual(
              workspaceEdit,
              undefined,
              "Code action missing workspace edit"
            );
            assert.ok(
              workspaceEdit.has(uri),
              "Code action workspace edit missing URI: " + uri
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(textEdits.length, 1);
            let textEdit = textEdits[0];
            assert.strictEqual(
              textEdit.newText,
              "\n\t\t\tcatch(e:Error)\n\t\t\t{\n\t\t\t}",
              "Code action workspace edit provided incorrect new text"
            );
            let range = textEdit.range;
            assert.strictEqual(
              range.start.line,
              8,
              "Code action workspace edit provided incorrect start line"
            );
            assert.strictEqual(
              range.start.character,
              4,
              "Code action workspace edit provided incorrect start character"
            );
            assert.strictEqual(
              range.end.line,
              8,
              "Code action workspace edit provided incorrect end line"
            );
            assert.strictEqual(
              range.end.character,
              4,
              "Code action workspace edit provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
});

suite(
  "code action provider: generate event listener : Application workspace",
  () => {
    suiteTeardown(revertAndCloseAllEditors);
    test("vscode.executeCodeActionProvider finds generate event listener with string literal and no metadata", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateEventListener.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(13, 61);
        let end = new vscode.Position(13, 61);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate Event Listener";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.QuickFix.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "\n\t\tprivate function literalWithoutMetadataListener(event:Object):void\n\t\t{\n\t\t}\n",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                25,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                0,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                25,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                0,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider finds generate event listener with string literal and metadata with missing class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateEventListener.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(14, 81);
        let end = new vscode.Position(14, 81);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate Event Listener";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.QuickFix.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "\n\t\tprivate function literalWithMetadataButMissingMetadataClassListener(event:Object):void\n\t\t{\n\t\t}\n",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                25,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                0,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                25,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                0,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider finds generate event listener with string literal and full metadata", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateEventListener.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(15, 66);
        let end = new vscode.Position(15, 66);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate Event Listener";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.QuickFix.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "\n\t\tprivate function literalWithMetadataAndClassListener(event:GenerateEventEvent):void\n\t\t{\n\t\t}\n",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                25,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                0,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                25,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                0,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider finds generate event listener with constant and no metadata", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateEventListener.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(16, 81);
        let end = new vscode.Position(16, 81);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate Event Listener";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.QuickFix.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "\n\t\tprivate function constantWithoutMetadataListener(event:Object):void\n\t\t{\n\t\t}\n",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                25,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                0,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                25,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                0,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider finds generate event listener with constant and metadata with missing class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateEventListener.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(17, 105);
        let end = new vscode.Position(17, 105);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate Event Listener";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.QuickFix.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "\n\t\tprivate function constantWithMetadataButMissingMetadataClassListener(event:Object):void\n\t\t{\n\t\t}\n",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                25,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                0,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                25,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                0,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider finds generate event listener with constant and full metadata", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateEventListener.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(18, 88);
        let end = new vscode.Position(18, 88);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate Event Listener";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.QuickFix.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "\n\t\tprivate function constantWithMetadataAndClassListener(event:GenerateEventEvent):void\n\t\t{\n\t\t}\n",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                25,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                0,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                25,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                0,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider finds generate event listener with explicit this member access on addEventListener", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateEventListener.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(19, 82);
        let end = new vscode.Position(19, 82);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate Event Listener";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.QuickFix.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "\n\t\tprivate function explicitThisListener(event:GenerateEventEvent):void\n\t\t{\n\t\t}\n",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                25,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                0,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                25,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                0,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider finds generate event listener with implement this member access on addEventListener", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateEventListener.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(20, 77);
        let end = new vscode.Position(20, 77);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate Event Listener";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.QuickFix.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "\n\t\tprivate function implicitThisListener(event:GenerateEventEvent):void\n\t\t{\n\t\t}\n",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                25,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                0,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                25,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                0,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider finds generate event listener with member access before listener name", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateEventListener.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(21, 93);
        let end = new vscode.Position(21, 93);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate Event Listener";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.QuickFix.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "\n\t\tprivate function listenerWithMemberAccess(event:GenerateEventEvent):void\n\t\t{\n\t\t}\n",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                25,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                0,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                25,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                0,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider omits generate event listener with non-this member access before listener name", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateEventListener.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(22, 99);
        let end = new vscode.Position(22, 99);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate Event Listener";
              });
              assert.strictEqual(
                codeAction,
                undefined,
                "Code action must not be found"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider omits generate event listener with function that already exists", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateEventListener.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(23, 88);
        let end = new vscode.Position(23, 88);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate Event Listener";
              });
              assert.strictEqual(
                codeAction,
                undefined,
                "Code action must not be found"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
    test("vscode.executeCodeActionProvider finds generate event listener in file-internal class", () => {
      let uri = vscode.Uri.file(
        path.join(
          vscode.workspace.workspaceFolders[0].uri.fsPath,
          "src",
          "GenerateEventListener.as"
        )
      );
      return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
        let start = new vscode.Position(36, 87);
        let end = new vscode.Position(36, 87);
        let range = new vscode.Range(start, end);
        return vscode.commands
          .executeCommand("vscode.executeCodeActionProvider", uri, range)
          .then(
            (codeActions: vscode.CodeAction[]) => {
              let codeAction = codeActions.find((codeAction) => {
                return codeAction.title == "Generate Event Listener";
              });
              assert.notEqual(codeAction, undefined, "Code action not found");
              assert.strictEqual(
                codeAction.command,
                undefined,
                "Code action provided incorrect command"
              );
              assert.strictEqual(
                codeAction.kind.value,
                vscode.CodeActionKind.QuickFix.value,
                "Code action provided incorrect kind"
              );
              let workspaceEdit = codeAction.edit;
              assert.notEqual(
                workspaceEdit,
                undefined,
                "Code action missing workspace edit"
              );
              assert.ok(
                workspaceEdit.has(uri),
                "Code action workspace edit missing URI: " + uri
              );
              let textEdits = workspaceEdit.get(uri);
              assert.strictEqual(textEdits.length, 1);
              let textEdit = textEdits[0];
              assert.strictEqual(
                textEdit.newText,
                "\n\tprivate function constantWithMetadataAndClassListener(event:GenerateEventEvent):void\n\t{\n\t}\n",
                "Code action workspace edit provided incorrect new text"
              );
              let range = textEdit.range;
              assert.strictEqual(
                range.start.line,
                38,
                "Code action workspace edit provided incorrect start line"
              );
              assert.strictEqual(
                range.start.character,
                0,
                "Code action workspace edit provided incorrect start character"
              );
              assert.strictEqual(
                range.end.line,
                38,
                "Code action workspace edit provided incorrect end line"
              );
              assert.strictEqual(
                range.end.character,
                0,
                "Code action workspace edit provided incorrect end character"
              );
            },
            (err) => {
              assert(false, "Failed to execute code actions provider: " + uri);
            }
          );
      });
    });
  }
);

suite("organize imports: Application workspace", () => {
  teardown(revertAndCloseActiveEditor);
  test("vscode.executeCodeActionProvider finds organize imports", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "OrganizeImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      let start = new vscode.Position(2, 45);
      let end = new vscode.Position(2, 45);
      let range = new vscode.Range(start, end);
      return vscode.commands
        .executeCommand("vscode.executeCodeActionProvider", uri, range)
        .then(
          (codeActions: vscode.CodeAction[]) => {
            let codeAction = codeActions.find(
              (codeAction: vscode.CodeAction) => {
                return (
                  codeAction.kind.value ===
                  vscode.CodeActionKind.SourceOrganizeImports.value
                );
              }
            );
            assert.notEqual(codeAction, null, "Code action not found");
            assert.strictEqual(
              codeAction.title,
              "Organize Imports",
              "Code action provided incorrect title"
            );
            assert.strictEqual(
              codeAction.kind.value,
              vscode.CodeActionKind.SourceOrganizeImports.value,
              "Code action provided incorrect kind"
            );
            let command = codeAction.command;
            assert.notEqual(command, null, "Code action command not found");
            assert.strictEqual(
              command.command,
              COMMAND_ORGANIZE_IMPORTS_IN_URI,
              "Code action provided incorrect command"
            );
            assert.strictEqual(
              command.title,
              "Organize Imports",
              "Code action provided incorrect command title"
            );
          },
          (err) => {
            assert(false, "Failed to execute code actions provider: " + uri);
          }
        );
    });
  });
  test("as3mxml.organizeImportsInUri organizes imports in ActionScript: removes unused imports, adds missing imports, and reorganizes remaining imports in alphabetical order", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "OrganizeImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(COMMAND_ORGANIZE_IMPORTS_IN_URI, uri)
        .then(
          () => {
            return new Promise((resolve, reject) => {
              //the text edit is not applied immediately, so give
              //it a short delay before we check
              setTimeout(() => {
                let start = new vscode.Position(2, 0);
                let end = new vscode.Position(12, 0);
                let range = new vscode.Range(start, end);
                let generatedText = editor.document.getText(range);
                assert.strictEqual(
                  generatedText,
                  "\timport com.example.organizeImports.ImportToAdd;\n\timport com.example.organizeImports.ImportToAddFromAsOperator;\n\timport com.example.organizeImports.ImportToAddFromCast;\n\timport com.example.organizeImports.ImportToAddFromIsOperator;\n\timport com.example.organizeImports.ImportToAddFromNew;\n\timport com.example.organizeImports.ImportToAddFromReturnType;\n\timport com.example.organizeImports.ImportToKeepClass;\n\timport com.example.organizeImports.ImportToKeepInterface;\n\timport com.example.organizeImports.wildcards.*;\n\n",
                  "as3mxml.organizeImportsInUri failed to organize imports"
                );
                resolve();
              }, 1000);
            });
          },
          (err) => {
            assert(false, "Failed to execute organize imports command: " + uri);
          }
        );
    });
  });
  test("as3mxml.organizeImportsInUri organizes imports in MXML: removes unused imports, adds missing imports, and reorganizes remaining imports in alphabetical order", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "MXMLOrganizeImports.mxml"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(COMMAND_ORGANIZE_IMPORTS_IN_URI, uri)
        .then(
          () => {
            return new Promise((resolve, reject) => {
              //the text edit is not applied immediately, so give
              //it a short delay before we check
              setTimeout(() => {
                let start = new vscode.Position(5, 0);
                let end = new vscode.Position(15, 0);
                let range = new vscode.Range(start, end);
                let generatedText = editor.document.getText(range);
                assert.strictEqual(
                  generatedText,
                  "\t\t\timport com.example.organizeImports.ImportToAdd;\n\t\t\timport com.example.organizeImports.ImportToAddFromAsOperator;\n\t\t\timport com.example.organizeImports.ImportToAddFromCast;\n\t\t\timport com.example.organizeImports.ImportToAddFromIsOperator;\n\t\t\timport com.example.organizeImports.ImportToAddFromNew;\n\t\t\timport com.example.organizeImports.ImportToAddFromReturnType;\n\t\t\timport com.example.organizeImports.ImportToKeepClass;\n\t\t\timport com.example.organizeImports.ImportToKeepInterface;\n\t\t\timport com.example.organizeImports.wildcards.*;\n\n",
                  "as3mxml.organizeImportsInUri failed to organize imports"
                );
                resolve();
              }, 1000);
            });
          },
          (err) => {
            assert(false, "Failed to execute organize imports command: " + uri);
          }
        );
    });
  });
  //BowlerHatLLC/vscode-as3mxml#182
  test("as3mxml.organizeImportsInUri must be able to remove all imports, if necessary", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "RemoveAllImports.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(COMMAND_ORGANIZE_IMPORTS_IN_URI, uri)
        .then(
          () => {
            return new Promise((resolve, reject) => {
              //the text edit is not applied immediately, so give
              //it a short delay before we check
              setTimeout(() => {
                let start = new vscode.Position(2, 0);
                let end = new vscode.Position(3, 0);
                let range = new vscode.Range(start, end);
                let generatedText = editor.document.getText(range);
                assert.strictEqual(
                  generatedText,
                  "\n",
                  "as3mxml.organizeImportsInUri failed to organize imports"
                );
                resolve();
              }, 1000);
            });
          },
          (err) => {
            assert(false, "Failed to execute organize imports command: " + uri);
          }
        );
    });
  });
});

suite("includes: application workspace", () => {
  suiteTeardown(revertAndCloseAllEditors);
  test("vscode.executeDefinitionProvider finds definition of method before include statement", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "includes",
        "Includes.as"
      )
    );
    let position = new vscode.Position(4, 20);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of local variable definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for definition"
            );
            assert.strictEqual(
              location.range.start.line,
              4,
              "vscode.executeDefinitionProvider provided incorrect line for definition"
            );
            assert.strictEqual(
              location.range.start.character,
              18,
              "vscode.executeDefinitionProvider provided incorrect character for definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of method between include statements", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "includes",
        "Includes.as"
      )
    );
    let position = new vscode.Position(8, 20);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of local variable definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for definition"
            );
            assert.strictEqual(
              location.range.start.line,
              8,
              "vscode.executeDefinitionProvider provided incorrect line for definition"
            );
            assert.strictEqual(
              location.range.start.character,
              18,
              "vscode.executeDefinitionProvider provided incorrect character for definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of method after include statements", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "includes",
        "Includes.as"
      )
    );
    let position = new vscode.Position(12, 20);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of local variable definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for definition"
            );
            assert.strictEqual(
              location.range.start.line,
              12,
              "vscode.executeDefinitionProvider provided incorrect line for definition"
            );
            assert.strictEqual(
              location.range.start.character,
              18,
              "vscode.executeDefinitionProvider provided incorrect character for definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of method in first file included with include statement", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "includes",
        "scripts",
        "include1.as"
      )
    );
    let position = new vscode.Position(0, 18);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of local variable definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for definition"
            );
            assert.strictEqual(
              location.range.start.line,
              0,
              "vscode.executeDefinitionProvider provided incorrect line for definition"
            );
            assert.strictEqual(
              location.range.start.character,
              16,
              "vscode.executeDefinitionProvider provided incorrect character for definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of method in second file included with include statement", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "includes",
        "scripts",
        "include2.as"
      )
    );
    let position = new vscode.Position(1, 18);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of local variable definition: " +
                uri
            );
            let location = locations[0];
            assert.strictEqual(
              location.uri.toString(),
              uri.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for definition"
            );
            assert.strictEqual(
              location.range.start.line,
              1,
              "vscode.executeDefinitionProvider provided incorrect line for definition"
            );
            assert.strictEqual(
              location.range.start.character,
              16,
              "vscode.executeDefinitionProvider provided incorrect character for definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of method in one included file from another included file", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "includes",
        "scripts",
        "include2.as"
      )
    );
    let position = new vscode.Position(3, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of local variable definition: " +
                uri
            );
            let location = locations[0];
            let expectedURI = vscode.Uri.file(
              path.join(
                vscode.workspace.workspaceFolders[0].uri.fsPath,
                "src",
                "com",
                "example",
                "includes",
                "scripts",
                "include1.as"
              )
            );
            assert.strictEqual(
              location.uri.toString(),
              expectedURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for definition"
            );
            assert.strictEqual(
              location.range.start.line,
              0,
              "vscode.executeDefinitionProvider provided incorrect line for definition"
            );
            assert.strictEqual(
              location.range.start.character,
              16,
              "vscode.executeDefinitionProvider provided incorrect character for definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of method in main file from an included file", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "includes",
        "scripts",
        "include1.as"
      )
    );
    let position = new vscode.Position(2, 3);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of local variable definition: " +
                uri
            );
            let location = locations[0];
            let expectedURI = vscode.Uri.file(
              path.join(
                vscode.workspace.workspaceFolders[0].uri.fsPath,
                "src",
                "com",
                "example",
                "includes",
                "Includes.as"
              )
            );
            assert.strictEqual(
              location.uri.toString(),
              expectedURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for definition"
            );
            assert.strictEqual(
              location.range.start.line,
              12,
              "vscode.executeDefinitionProvider provided incorrect line for definition"
            );
            assert.strictEqual(
              location.range.start.character,
              18,
              "vscode.executeDefinitionProvider provided incorrect character for definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDefinitionProvider finds definition of method in included file from main file", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "includes",
        "Includes.as"
      )
    );
    let position = new vscode.Position(14, 4);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDefinitionProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              1,
              "vscode.executeDefinitionProvider failed to provide location of local variable definition: " +
                uri
            );
            let location = locations[0];
            let expectedURI = vscode.Uri.file(
              path.join(
                vscode.workspace.workspaceFolders[0].uri.fsPath,
                "src",
                "com",
                "example",
                "includes",
                "scripts",
                "include1.as"
              )
            );
            assert.strictEqual(
              location.uri.toString(),
              expectedURI.toString(),
              "vscode.executeDefinitionProvider provided incorrect uri for definition"
            );
            assert.strictEqual(
              location.range.start.line,
              0,
              "vscode.executeDefinitionProvider provided incorrect line for definition"
            );
            assert.strictEqual(
              location.range.start.character,
              16,
              "vscode.executeDefinitionProvider provided incorrect character for definition"
            );
          },
          (err) => {
            assert(false, "Failed to execute definition provider: " + uri);
          }
        );
    });
  });
});

suite("reference provider: Application workspace", () => {
  suiteTeardown(revertAndCloseAllEditors);
  test("vscode.executeReferenceProvider finds references to local variable from its declaration", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(38, 10);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeReferenceProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              2,
              "vscode.executeReferenceProvider failed to provide locations: " +
                uri
            );
            let location1 = locations[0];
            assert.strictEqual(
              location1.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location1.range.start.line,
              38,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location1.range.start.character,
              7,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location2 = locations[1];
            assert.strictEqual(
              location2.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location2.range.start.line,
              86,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location2.range.start.character,
              3,
              "vscode.executeReferenceProvider provided incorrect character"
            );
          },
          (err) => {
            assert(false, "Failed to execute reference provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeReferenceProvider finds references to local variable from assignment that is not initialization", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(86, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeReferenceProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              2,
              "vscode.executeReferenceProvider failed to provide locations: " +
                uri
            );
            let location1 = locations[0];
            assert.strictEqual(
              location1.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location1.range.start.line,
              38,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location1.range.start.character,
              7,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location2 = locations[1];
            assert.strictEqual(
              location2.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location2.range.start.line,
              86,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location2.range.start.character,
              3,
              "vscode.executeReferenceProvider provided incorrect character"
            );
          },
          (err) => {
            assert(false, "Failed to execute reference provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeReferenceProvider finds references to local function from its declaration", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(39, 15);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeReferenceProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              2,
              "vscode.executeReferenceProvider failed to provide locations: " +
                uri
            );
            let location1 = locations[0];
            assert.strictEqual(
              location1.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location1.range.start.line,
              39,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location1.range.start.character,
              12,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location2 = locations[1];
            assert.strictEqual(
              location2.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location2.range.start.line,
              88,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location2.range.start.character,
              3,
              "vscode.executeReferenceProvider provided incorrect character"
            );
          },
          (err) => {
            assert(false, "Failed to execute reference provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeReferenceProvider finds references to local function from a call", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(88, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeReferenceProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              2,
              "vscode.executeReferenceProvider failed to provide locations: " +
                uri
            );
            let location1 = locations[0];
            assert.strictEqual(
              location1.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location1.range.start.line,
              39,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location1.range.start.character,
              12,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location2 = locations[1];
            assert.strictEqual(
              location2.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location2.range.start.line,
              88,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location2.range.start.character,
              3,
              "vscode.executeReferenceProvider provided incorrect character"
            );
          },
          (err) => {
            assert(false, "Failed to execute reference provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeReferenceProvider finds references to member variable from its declaration", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(10, 16);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeReferenceProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              3,
              "vscode.executeReferenceProvider failed to provide locations: " +
                uri
            );
            let location1 = locations[0];
            assert.strictEqual(
              location1.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location1.range.start.line,
              10,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location1.range.start.character,
              14,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location2 = locations[1];
            assert.strictEqual(
              location2.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location2.range.start.line,
              50,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location2.range.start.character,
              3,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location3 = locations[2];
            assert.strictEqual(
              location3.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location3.range.start.line,
              51,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location3.range.start.character,
              8,
              "vscode.executeReferenceProvider provided incorrect character"
            );
          },
          (err) => {
            assert(false, "Failed to execute reference provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeReferenceProvider finds references to member variable from assignment without this member access", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(50, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeReferenceProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              3,
              "vscode.executeReferenceProvider failed to provide locations: " +
                uri
            );
            let location1 = locations[0];
            assert.strictEqual(
              location1.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location1.range.start.line,
              10,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location1.range.start.character,
              14,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location2 = locations[1];
            assert.strictEqual(
              location2.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location2.range.start.line,
              50,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location2.range.start.character,
              3,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location3 = locations[2];
            assert.strictEqual(
              location3.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location3.range.start.line,
              51,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location3.range.start.character,
              8,
              "vscode.executeReferenceProvider provided incorrect character"
            );
          },
          (err) => {
            assert(false, "Failed to execute reference provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeReferenceProvider finds references to member variable from assignment with this member access", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(51, 10);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeReferenceProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              3,
              "vscode.executeReferenceProvider failed to provide locations: " +
                uri
            );
            let location1 = locations[0];
            assert.strictEqual(
              location1.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location1.range.start.line,
              10,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location1.range.start.character,
              14,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location2 = locations[1];
            assert.strictEqual(
              location2.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location2.range.start.line,
              50,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location2.range.start.character,
              3,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location3 = locations[2];
            assert.strictEqual(
              location3.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location3.range.start.line,
              51,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location3.range.start.character,
              8,
              "vscode.executeReferenceProvider provided incorrect character"
            );
          },
          (err) => {
            assert(false, "Failed to execute reference provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeReferenceProvider finds references to member function from its declaration", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(12, 22);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeReferenceProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              3,
              "vscode.executeReferenceProvider failed to provide locations: " +
                uri
            );
            let location1 = locations[0];
            assert.strictEqual(
              location1.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location1.range.start.line,
              12,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location1.range.start.character,
              19,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location2 = locations[1];
            assert.strictEqual(
              location2.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location2.range.start.line,
              41,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location2.range.start.character,
              3,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location3 = locations[2];
            assert.strictEqual(
              location3.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location3.range.start.line,
              42,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location3.range.start.character,
              8,
              "vscode.executeReferenceProvider provided incorrect character"
            );
          },
          (err) => {
            assert(false, "Failed to execute reference provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeReferenceProvider finds references to member function from call without this member access", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(41, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeReferenceProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              3,
              "vscode.executeReferenceProvider failed to provide locations: " +
                uri
            );
            let location1 = locations[0];
            assert.strictEqual(
              location1.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location1.range.start.line,
              12,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location1.range.start.character,
              19,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location2 = locations[1];
            assert.strictEqual(
              location2.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location2.range.start.line,
              41,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location2.range.start.character,
              3,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location3 = locations[2];
            assert.strictEqual(
              location3.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location3.range.start.line,
              42,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location3.range.start.character,
              8,
              "vscode.executeReferenceProvider provided incorrect character"
            );
          },
          (err) => {
            assert(false, "Failed to execute reference provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeReferenceProvider finds references to member function from call with this member access", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(42, 10);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeReferenceProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              3,
              "vscode.executeReferenceProvider failed to provide locations: " +
                uri
            );
            let location1 = locations[0];
            assert.strictEqual(
              location1.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location1.range.start.line,
              12,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location1.range.start.character,
              19,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location2 = locations[1];
            assert.strictEqual(
              location2.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location2.range.start.line,
              41,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location2.range.start.character,
              3,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location3 = locations[2];
            assert.strictEqual(
              location3.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location3.range.start.line,
              42,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location3.range.start.character,
              8,
              "vscode.executeReferenceProvider provided incorrect character"
            );
          },
          (err) => {
            assert(false, "Failed to execute reference provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeReferenceProvider finds references to static variable from its declaration", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(4, 22);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeReferenceProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              3,
              "vscode.executeReferenceProvider failed to provide locations: " +
                uri
            );
            let location1 = locations[0];
            assert.strictEqual(
              location1.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location1.range.start.line,
              4,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location1.range.start.character,
              20,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location2 = locations[1];
            assert.strictEqual(
              location2.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location2.range.start.line,
              47,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location2.range.start.character,
              3,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location3 = locations[2];
            assert.strictEqual(
              location3.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location3.range.start.line,
              48,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location3.range.start.character,
              14,
              "vscode.executeReferenceProvider provided incorrect character"
            );
          },
          (err) => {
            assert(false, "Failed to execute reference provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeReferenceProvider finds references to static variable from assignment without type member access", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(47, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeReferenceProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              3,
              "vscode.executeReferenceProvider failed to provide locations: " +
                uri
            );
            let location1 = locations[0];
            assert.strictEqual(
              location1.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location1.range.start.line,
              4,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location1.range.start.character,
              20,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location2 = locations[1];
            assert.strictEqual(
              location2.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location2.range.start.line,
              47,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location2.range.start.character,
              3,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location3 = locations[2];
            assert.strictEqual(
              location3.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location3.range.start.line,
              48,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location3.range.start.character,
              14,
              "vscode.executeReferenceProvider provided incorrect character"
            );
          },
          (err) => {
            assert(false, "Failed to execute reference provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeReferenceProvider finds references to static variable from assignment with type member access", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(48, 16);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeReferenceProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              3,
              "vscode.executeReferenceProvider failed to provide locations: " +
                uri
            );
            let location1 = locations[0];
            assert.strictEqual(
              location1.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location1.range.start.line,
              4,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location1.range.start.character,
              20,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location2 = locations[1];
            assert.strictEqual(
              location2.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location2.range.start.line,
              47,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location2.range.start.character,
              3,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location3 = locations[2];
            assert.strictEqual(
              location3.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location3.range.start.line,
              48,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location3.range.start.character,
              14,
              "vscode.executeReferenceProvider provided incorrect character"
            );
          },
          (err) => {
            assert(false, "Failed to execute reference provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeReferenceProvider finds references to static function from its declaration", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(6, 28);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeReferenceProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              3,
              "vscode.executeReferenceProvider failed to provide locations: " +
                uri
            );
            let location1 = locations[0];
            assert.strictEqual(
              location1.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location1.range.start.line,
              6,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location1.range.start.character,
              26,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location2 = locations[1];
            assert.strictEqual(
              location2.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location2.range.start.line,
              44,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location2.range.start.character,
              3,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location3 = locations[2];
            assert.strictEqual(
              location3.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location3.range.start.line,
              45,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location3.range.start.character,
              14,
              "vscode.executeReferenceProvider provided incorrect character"
            );
          },
          (err) => {
            assert(false, "Failed to execute reference provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeReferenceProvider finds references to static function from call without type member access", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(44, 5);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeReferenceProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              3,
              "vscode.executeReferenceProvider failed to provide locations: " +
                uri
            );
            let location1 = locations[0];
            assert.strictEqual(
              location1.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location1.range.start.line,
              6,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location1.range.start.character,
              26,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location2 = locations[1];
            assert.strictEqual(
              location2.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location2.range.start.line,
              44,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location2.range.start.character,
              3,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location3 = locations[2];
            assert.strictEqual(
              location3.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location3.range.start.line,
              45,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location3.range.start.character,
              14,
              "vscode.executeReferenceProvider provided incorrect character"
            );
          },
          (err) => {
            assert(false, "Failed to execute reference provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeReferenceProvider finds references to static function from call with type member access", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(45, 16);
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeReferenceProvider", uri, position)
        .then(
          (locations: vscode.Location[]) => {
            assert.strictEqual(
              locations.length,
              3,
              "vscode.executeReferenceProvider failed to provide locations: " +
                uri
            );
            let location1 = locations[0];
            assert.strictEqual(
              location1.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location1.range.start.line,
              6,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location1.range.start.character,
              26,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location2 = locations[1];
            assert.strictEqual(
              location2.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location2.range.start.line,
              44,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location2.range.start.character,
              3,
              "vscode.executeReferenceProvider provided incorrect character"
            );
            let location3 = locations[2];
            assert.strictEqual(
              location3.uri.toString(),
              uri.toString(),
              "vscode.executeReferenceProvider provided incorrect uri"
            );
            assert.strictEqual(
              location3.range.start.line,
              45,
              "vscode.executeReferenceProvider provided incorrect line"
            );
            assert.strictEqual(
              location3.range.start.character,
              14,
              "vscode.executeReferenceProvider provided incorrect character"
            );
          },
          (err) => {
            assert(false, "Failed to execute reference provider: " + uri);
          }
        );
    });
  });
});

suite("rename provider: Application workspace", () => {
  suiteTeardown(revertAndCloseAllEditors);
  test("vscode.executeDocumentRenameProvider finds edits for local variable from its declaration", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(38, 10);
    let newText = "newName";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeDocumentRenameProvider",
          uri,
          position,
          newText
        )
        .then(
          (workspaceEdit: vscode.WorkspaceEdit) => {
            assert.strictEqual(
              workspaceEdit.size,
              1,
              "vscode.executeDocumentRenameProvider failed to provide correct number of uris"
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(
              textEdits.length,
              2,
              "vscode.executeDocumentRenameProvider failed to provide correct number of text edits"
            );
            let textEdit1 = textEdits[0];
            assert.strictEqual(
              textEdit1.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit1.range.start.line,
              38,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit1.range.start.character,
              7,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit1.range.end.line,
              38,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit1.range.end.character,
              15,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit2 = textEdits[1];
            assert.strictEqual(
              textEdit2.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit2.range.start.line,
              86,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit2.range.start.character,
              3,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit2.range.end.line,
              86,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit2.range.end.character,
              11,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute rename provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentRenameProvider finds edits for local variable from assignment that is not initialization", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(86, 5);
    let newText = "newName";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeDocumentRenameProvider",
          uri,
          position,
          newText
        )
        .then(
          (workspaceEdit: vscode.WorkspaceEdit) => {
            assert.strictEqual(
              workspaceEdit.size,
              1,
              "vscode.executeDocumentRenameProvider failed to provide correct number of uris"
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(
              textEdits.length,
              2,
              "vscode.executeDocumentRenameProvider failed to provide correct number of text edits"
            );
            let textEdit1 = textEdits[0];
            assert.strictEqual(
              textEdit1.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit1.range.start.line,
              38,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit1.range.start.character,
              7,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit1.range.end.line,
              38,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit1.range.end.character,
              15,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit2 = textEdits[1];
            assert.strictEqual(
              textEdit2.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit2.range.start.line,
              86,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit2.range.start.character,
              3,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit2.range.end.line,
              86,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit2.range.end.character,
              11,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute rename provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentRenameProvider finds edits to local function from its declaration", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(39, 15);
    let newText = "newName";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeDocumentRenameProvider",
          uri,
          position,
          newText
        )
        .then(
          (workspaceEdit: vscode.WorkspaceEdit) => {
            assert.strictEqual(
              workspaceEdit.size,
              1,
              "vscode.executeDocumentRenameProvider failed to provide correct number of uris"
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(
              textEdits.length,
              2,
              "vscode.executeDocumentRenameProvider failed to provide correct number of text edits"
            );
            let textEdit1 = textEdits[0];
            assert.strictEqual(
              textEdit1.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new name"
            );
            assert.strictEqual(
              textEdit1.range.start.line,
              39,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit1.range.start.character,
              12,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit1.range.end.line,
              39,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit1.range.end.character,
              25,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit2 = textEdits[1];
            assert.strictEqual(
              textEdit2.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new name"
            );
            assert.strictEqual(
              textEdit2.range.start.line,
              88,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit2.range.start.character,
              3,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit2.range.end.line,
              88,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit2.range.end.character,
              16,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute rename provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentRenameProvider finds edits to local function from call", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(88, 5);
    let newText = "newName";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeDocumentRenameProvider",
          uri,
          position,
          newText
        )
        .then(
          (workspaceEdit: vscode.WorkspaceEdit) => {
            assert.strictEqual(
              workspaceEdit.size,
              1,
              "vscode.executeDocumentRenameProvider failed to provide correct number of uris"
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(
              textEdits.length,
              2,
              "vscode.executeDocumentRenameProvider failed to provide correct number of text edits"
            );
            let textEdit1 = textEdits[0];
            assert.strictEqual(
              textEdit1.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new name"
            );
            assert.strictEqual(
              textEdit1.range.start.line,
              39,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit1.range.start.character,
              12,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit1.range.end.line,
              39,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit1.range.end.character,
              25,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit2 = textEdits[1];
            assert.strictEqual(
              textEdit2.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new name"
            );
            assert.strictEqual(
              textEdit2.range.start.line,
              88,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit2.range.start.character,
              3,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit2.range.end.line,
              88,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit2.range.end.character,
              16,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute rename provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentRenameProvider finds edits for member variable from its declaration", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(10, 16);
    let newText = "newName";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeDocumentRenameProvider",
          uri,
          position,
          newText
        )
        .then(
          (workspaceEdit: vscode.WorkspaceEdit) => {
            assert.strictEqual(
              workspaceEdit.size,
              1,
              "vscode.executeDocumentRenameProvider failed to provide correct number of uris"
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(
              textEdits.length,
              3,
              "vscode.executeDocumentRenameProvider failed to provide correct number of text edits"
            );
            let textEdit1 = textEdits[0];
            assert.strictEqual(
              textEdit1.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit1.range.start.line,
              10,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit1.range.start.character,
              14,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit1.range.end.line,
              10,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit1.range.end.character,
              23,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit2 = textEdits[1];
            assert.strictEqual(
              textEdit2.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit2.range.start.line,
              50,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit2.range.start.character,
              3,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit2.range.end.line,
              50,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit2.range.end.character,
              12,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit3 = textEdits[2];
            assert.strictEqual(
              textEdit3.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit3.range.start.line,
              51,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit3.range.start.character,
              8,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit3.range.end.line,
              51,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit3.range.end.character,
              17,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute rename provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentRenameProvider finds edits for member variable from assignment without this member access", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(50, 5);
    let newText = "newName";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeDocumentRenameProvider",
          uri,
          position,
          newText
        )
        .then(
          (workspaceEdit: vscode.WorkspaceEdit) => {
            assert.strictEqual(
              workspaceEdit.size,
              1,
              "vscode.executeDocumentRenameProvider failed to provide correct number of uris"
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(
              textEdits.length,
              3,
              "vscode.executeDocumentRenameProvider failed to provide correct number of text edits"
            );
            let textEdit1 = textEdits[0];
            assert.strictEqual(
              textEdit1.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit1.range.start.line,
              10,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit1.range.start.character,
              14,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit1.range.end.line,
              10,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit1.range.end.character,
              23,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit2 = textEdits[1];
            assert.strictEqual(
              textEdit2.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit2.range.start.line,
              50,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit2.range.start.character,
              3,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit2.range.end.line,
              50,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit2.range.end.character,
              12,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit3 = textEdits[2];
            assert.strictEqual(
              textEdit3.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit3.range.start.line,
              51,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit3.range.start.character,
              8,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit3.range.end.line,
              51,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit3.range.end.character,
              17,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute rename provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentRenameProvider finds edits for member variable from assignment with this member access", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(51, 10);
    let newText = "newName";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeDocumentRenameProvider",
          uri,
          position,
          newText
        )
        .then(
          (workspaceEdit: vscode.WorkspaceEdit) => {
            assert.strictEqual(
              workspaceEdit.size,
              1,
              "vscode.executeDocumentRenameProvider failed to provide correct number of uris"
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(
              textEdits.length,
              3,
              "vscode.executeDocumentRenameProvider failed to provide correct number of text edits"
            );
            let textEdit1 = textEdits[0];
            assert.strictEqual(
              textEdit1.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit1.range.start.line,
              10,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit1.range.start.character,
              14,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit1.range.end.line,
              10,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit1.range.end.character,
              23,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit2 = textEdits[1];
            assert.strictEqual(
              textEdit2.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit2.range.start.line,
              50,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit2.range.start.character,
              3,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit2.range.end.line,
              50,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit2.range.end.character,
              12,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit3 = textEdits[2];
            assert.strictEqual(
              textEdit3.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit3.range.start.line,
              51,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit3.range.start.character,
              8,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit3.range.end.line,
              51,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit3.range.end.character,
              17,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute rename provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentRenameProvider finds edits for member function from its declaration", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(12, 22);
    let newText = "newName";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeDocumentRenameProvider",
          uri,
          position,
          newText
        )
        .then(
          (workspaceEdit: vscode.WorkspaceEdit) => {
            assert.strictEqual(
              workspaceEdit.size,
              1,
              "vscode.executeDocumentRenameProvider failed to provide correct number of uris"
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(
              textEdits.length,
              3,
              "vscode.executeDocumentRenameProvider failed to provide correct number of text edits"
            );
            let textEdit1 = textEdits[0];
            assert.strictEqual(
              textEdit1.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit1.range.start.line,
              12,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit1.range.start.character,
              19,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit1.range.end.line,
              12,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit1.range.end.character,
              33,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit2 = textEdits[1];
            assert.strictEqual(
              textEdit2.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit2.range.start.line,
              41,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit2.range.start.character,
              3,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit2.range.end.line,
              41,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit2.range.end.character,
              17,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit3 = textEdits[2];
            assert.strictEqual(
              textEdit3.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit3.range.start.line,
              42,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit3.range.start.character,
              8,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit3.range.end.line,
              42,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit3.range.end.character,
              22,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute rename provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentRenameProvider finds edits for member function from a call without this member access", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(41, 5);
    let newText = "newName";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeDocumentRenameProvider",
          uri,
          position,
          newText
        )
        .then(
          (workspaceEdit: vscode.WorkspaceEdit) => {
            assert.strictEqual(
              workspaceEdit.size,
              1,
              "vscode.executeDocumentRenameProvider failed to provide correct number of uris"
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(
              textEdits.length,
              3,
              "vscode.executeDocumentRenameProvider failed to provide correct number of text edits"
            );
            let textEdit1 = textEdits[0];
            assert.strictEqual(
              textEdit1.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit1.range.start.line,
              12,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit1.range.start.character,
              19,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit1.range.end.line,
              12,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit1.range.end.character,
              33,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit2 = textEdits[1];
            assert.strictEqual(
              textEdit2.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit2.range.start.line,
              41,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit2.range.start.character,
              3,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit2.range.end.line,
              41,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit2.range.end.character,
              17,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit3 = textEdits[2];
            assert.strictEqual(
              textEdit3.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit3.range.start.line,
              42,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit3.range.start.character,
              8,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit3.range.end.line,
              42,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit3.range.end.character,
              22,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute rename provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentRenameProvider finds edits for member function from a call with this member access", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(42, 10);
    let newText = "newName";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeDocumentRenameProvider",
          uri,
          position,
          newText
        )
        .then(
          (workspaceEdit: vscode.WorkspaceEdit) => {
            assert.strictEqual(
              workspaceEdit.size,
              1,
              "vscode.executeDocumentRenameProvider failed to provide correct number of uris"
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(
              textEdits.length,
              3,
              "vscode.executeDocumentRenameProvider failed to provide correct number of text edits"
            );
            let textEdit1 = textEdits[0];
            assert.strictEqual(
              textEdit1.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit1.range.start.line,
              12,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit1.range.start.character,
              19,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit1.range.end.line,
              12,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit1.range.end.character,
              33,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit2 = textEdits[1];
            assert.strictEqual(
              textEdit2.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit2.range.start.line,
              41,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit2.range.start.character,
              3,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit2.range.end.line,
              41,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit2.range.end.character,
              17,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit3 = textEdits[2];
            assert.strictEqual(
              textEdit3.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit3.range.start.line,
              42,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit3.range.start.character,
              8,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit3.range.end.line,
              42,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit3.range.end.character,
              22,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute rename provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentRenameProvider finds edits for static variable from its declaration", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(4, 22);
    let newText = "newName";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeDocumentRenameProvider",
          uri,
          position,
          newText
        )
        .then(
          (workspaceEdit: vscode.WorkspaceEdit) => {
            assert.strictEqual(
              workspaceEdit.size,
              1,
              "vscode.executeDocumentRenameProvider failed to provide correct number of uris"
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(
              textEdits.length,
              3,
              "vscode.executeDocumentRenameProvider failed to provide correct number of text edits"
            );
            let textEdit1 = textEdits[0];
            assert.strictEqual(
              textEdit1.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit1.range.start.line,
              4,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit1.range.start.character,
              20,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit1.range.end.line,
              4,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit1.range.end.character,
              29,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit2 = textEdits[1];
            assert.strictEqual(
              textEdit2.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit2.range.start.line,
              47,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit2.range.start.character,
              3,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit2.range.end.line,
              47,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit2.range.end.character,
              12,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit3 = textEdits[2];
            assert.strictEqual(
              textEdit3.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit3.range.start.line,
              48,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit3.range.start.character,
              14,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit3.range.end.line,
              48,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit3.range.end.character,
              23,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute rename provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentRenameProvider finds edits for static variable from assignment without type member access", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(47, 5);
    let newText = "newName";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeDocumentRenameProvider",
          uri,
          position,
          newText
        )
        .then(
          (workspaceEdit: vscode.WorkspaceEdit) => {
            assert.strictEqual(
              workspaceEdit.size,
              1,
              "vscode.executeDocumentRenameProvider failed to provide correct number of uris"
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(
              textEdits.length,
              3,
              "vscode.executeDocumentRenameProvider failed to provide correct number of text edits"
            );
            let textEdit1 = textEdits[0];
            assert.strictEqual(
              textEdit1.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit1.range.start.line,
              4,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit1.range.start.character,
              20,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit1.range.end.line,
              4,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit1.range.end.character,
              29,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit2 = textEdits[1];
            assert.strictEqual(
              textEdit2.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit2.range.start.line,
              47,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit2.range.start.character,
              3,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit2.range.end.line,
              47,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit2.range.end.character,
              12,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit3 = textEdits[2];
            assert.strictEqual(
              textEdit3.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit3.range.start.line,
              48,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit3.range.start.character,
              14,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit3.range.end.line,
              48,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit3.range.end.character,
              23,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute rename provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentRenameProvider finds edits for static variable from assignment with type member access", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(48, 16);
    let newText = "newName";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeDocumentRenameProvider",
          uri,
          position,
          newText
        )
        .then(
          (workspaceEdit: vscode.WorkspaceEdit) => {
            assert.strictEqual(
              workspaceEdit.size,
              1,
              "vscode.executeDocumentRenameProvider failed to provide correct number of uris"
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(
              textEdits.length,
              3,
              "vscode.executeDocumentRenameProvider failed to provide correct number of text edits"
            );
            let textEdit1 = textEdits[0];
            assert.strictEqual(
              textEdit1.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit1.range.start.line,
              4,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit1.range.start.character,
              20,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit1.range.end.line,
              4,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit1.range.end.character,
              29,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit2 = textEdits[1];
            assert.strictEqual(
              textEdit2.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit2.range.start.line,
              47,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit2.range.start.character,
              3,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit2.range.end.line,
              47,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit2.range.end.character,
              12,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit3 = textEdits[2];
            assert.strictEqual(
              textEdit3.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit3.range.start.line,
              48,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit3.range.start.character,
              14,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit3.range.end.line,
              48,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit3.range.end.character,
              23,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute rename provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentRenameProvider finds edits for static function from its declaration", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(6, 28);
    let newText = "newName";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeDocumentRenameProvider",
          uri,
          position,
          newText
        )
        .then(
          (workspaceEdit: vscode.WorkspaceEdit) => {
            assert.strictEqual(
              workspaceEdit.size,
              1,
              "vscode.executeDocumentRenameProvider failed to provide correct number of uris"
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(
              textEdits.length,
              3,
              "vscode.executeDocumentRenameProvider failed to provide correct number of text edits"
            );
            let textEdit1 = textEdits[0];
            assert.strictEqual(
              textEdit1.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit1.range.start.line,
              6,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit1.range.start.character,
              26,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit1.range.end.line,
              6,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit1.range.end.character,
              40,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit2 = textEdits[1];
            assert.strictEqual(
              textEdit2.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit2.range.start.line,
              44,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit2.range.start.character,
              3,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit2.range.end.line,
              44,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit2.range.end.character,
              17,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit3 = textEdits[2];
            assert.strictEqual(
              textEdit3.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit3.range.start.line,
              45,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit3.range.start.character,
              14,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit3.range.end.line,
              45,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit3.range.end.character,
              28,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute rename provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentRenameProvider finds edits for static function from call without type member access", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(44, 5);
    let newText = "newName";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeDocumentRenameProvider",
          uri,
          position,
          newText
        )
        .then(
          (workspaceEdit: vscode.WorkspaceEdit) => {
            assert.strictEqual(
              workspaceEdit.size,
              1,
              "vscode.executeDocumentRenameProvider failed to provide correct number of uris"
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(
              textEdits.length,
              3,
              "vscode.executeDocumentRenameProvider failed to provide correct number of text edits"
            );
            let textEdit1 = textEdits[0];
            assert.strictEqual(
              textEdit1.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit1.range.start.line,
              6,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit1.range.start.character,
              26,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit1.range.end.line,
              6,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit1.range.end.character,
              40,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit2 = textEdits[1];
            assert.strictEqual(
              textEdit2.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit2.range.start.line,
              44,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit2.range.start.character,
              3,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit2.range.end.line,
              44,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit2.range.end.character,
              17,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit3 = textEdits[2];
            assert.strictEqual(
              textEdit3.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit3.range.start.line,
              45,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit3.range.start.character,
              14,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit3.range.end.line,
              45,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit3.range.end.character,
              28,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute rename provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentRenameProvider finds edits for static function from call with type member access", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "references",
        "References.as"
      )
    );
    let position = new vscode.Position(45, 16);
    let newText = "newName";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand(
          "vscode.executeDocumentRenameProvider",
          uri,
          position,
          newText
        )
        .then(
          (workspaceEdit: vscode.WorkspaceEdit) => {
            assert.strictEqual(
              workspaceEdit.size,
              1,
              "vscode.executeDocumentRenameProvider failed to provide correct number of uris"
            );
            let textEdits = workspaceEdit.get(uri);
            assert.strictEqual(
              textEdits.length,
              3,
              "vscode.executeDocumentRenameProvider failed to provide correct number of text edits"
            );
            let textEdit1 = textEdits[0];
            assert.strictEqual(
              textEdit1.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit1.range.start.line,
              6,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit1.range.start.character,
              26,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit1.range.end.line,
              6,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit1.range.end.character,
              40,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit2 = textEdits[1];
            assert.strictEqual(
              textEdit2.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit2.range.start.line,
              44,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit2.range.start.character,
              3,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit2.range.end.line,
              44,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit2.range.end.character,
              17,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
            let textEdit3 = textEdits[2];
            assert.strictEqual(
              textEdit3.newText,
              newText,
              "vscode.executeDocumentRenameProvider provided incorrect new text"
            );
            assert.strictEqual(
              textEdit3.range.start.line,
              45,
              "vscode.executeDocumentRenameProvider provided incorrect start line"
            );
            assert.strictEqual(
              textEdit3.range.start.character,
              14,
              "vscode.executeDocumentRenameProvider provided incorrect start character"
            );
            assert.strictEqual(
              textEdit3.range.end.line,
              45,
              "vscode.executeDocumentRenameProvider provided incorrect end line"
            );
            assert.strictEqual(
              textEdit3.range.end.character,
              28,
              "vscode.executeDocumentRenameProvider provided incorrect end character"
            );
          },
          (err) => {
            assert(false, "Failed to execute rename provider: " + uri);
          }
        );
    });
  });
});

suite("document symbol provider: Library workspace", () => {
  suiteTeardown(revertAndCloseAllEditors);
  test("vscode.executeDocumentSymbolProvider not empty", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[1].uri.fsPath,
        "src",
        "com",
        "example",
        "LibraryDocumentSymbols.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDocumentSymbolProvider", uri)
        .then(
          (symbols: vscode.SymbolInformation[]) => {
            assert.notStrictEqual(
              symbols.length,
              0,
              "vscode.executeDocumentSymbolProvider failed to provide symbols in text document: " +
                uri
            );
          },
          (err) => {
            assert(false, "Failed to execute document symbol provider: " + uri);
          }
        );
    });
  });
  test("vscode.executeDocumentSymbolProvider includes class", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[1].uri.fsPath,
        "src",
        "com",
        "example",
        "LibraryDocumentSymbols.as"
      )
    );
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeDocumentSymbolProvider", uri)
        .then(
          (symbols: vscode.DocumentSymbol[]) => {
            let className = "LibraryDocumentSymbols";
            assert.ok(
              findDocumentSymbol(
                symbols,
                new vscode.DocumentSymbol(
                  className,
                  null,
                  vscode.SymbolKind.Class,
                  createRange(2, 14),
                  createRange(2, 14)
                )
              ),
              "vscode.executeDocumentSymbolProvider failed to provide symbol for class: " +
                className
            );
          },
          (err) => {
            assert(false, "Failed to execute document symbol provider: " + uri);
          }
        );
    });
  });
});

suite("workspace symbol provider: multiple workspaces", () => {
  suiteTeardown(revertAndCloseAllEditors);
  test("vscode.executeWorkspaceSymbolProvider includes classes from all workspaces", () => {
    let uri = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "Main.as"
      )
    );

    let packageName = "com.example.workspaceSymbols";
    let uri1 = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[0].uri.fsPath,
        "src",
        "com",
        "example",
        "workspaceSymbols",
        "WSFindMe1.as"
      )
    );
    let className1 = "WSFindMe1";
    let uri2 = vscode.Uri.file(
      path.join(
        vscode.workspace.workspaceFolders[1].uri.fsPath,
        "src",
        "com",
        "example",
        "workspaceSymbols",
        "WSFindMe2.as"
      )
    );
    let className2 = "WSFindMe2";

    let query = "WSFindMe";
    return openAndEditDocument(uri, (editor: vscode.TextEditor) => {
      return vscode.commands
        .executeCommand("vscode.executeWorkspaceSymbolProvider", query)
        .then(
          (symbols: vscode.SymbolInformation[]) => {
            assert.ok(
              findSymbolInformation(
                symbols,
                new vscode.SymbolInformation(
                  className1,
                  vscode.SymbolKind.Class,
                  createRange(2, 14),
                  uri1,
                  packageName
                )
              ),
              "vscode.executeWorkspaceSymbolProvider failed to provide symbol for class: " +
                className1
            );
            assert.ok(
              findSymbolInformation(
                symbols,
                new vscode.SymbolInformation(
                  className2,
                  vscode.SymbolKind.Class,
                  createRange(2, 14),
                  uri2,
                  packageName
                )
              ),
              "vscode.executeWorkspaceSymbolProvider failed to provide symbol for class: " +
                className2
            );
          },
          (err) => {
            assert(
              false,
              "Failed to execute workspace symbol provider: " + uri
            );
          }
        );
    });
  });
});
