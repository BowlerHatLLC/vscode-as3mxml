/*
Copyright 2016-2020 Bowler Hat LLC

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
import * as fs from "fs";
import * as json5 from "json5";
import * as path from "path";
import * as vscode from "vscode";

const FILE_ASCONFIG_JSON = "asconfig.json";
const FILE_EXTENSION_AS = ".as";
const FILE_EXTENSION_MXML = ".mxml";

export class ActionScriptSourcePath extends vscode.TreeItem {
  constructor(
    file: vscode.Uri | string,
    workspaceFolder: vscode.WorkspaceFolder
  ) {
    let contextValue: string = null;
    let command: vscode.Command;
    let collapsibleState = vscode.TreeItemCollapsibleState.None;
    let uri: vscode.Uri = undefined;
    let label: string = undefined;
    if (typeof file === "string") {
      label = file;
    } //uri
    else {
      uri = file;
      if (fs.statSync(uri.fsPath).isDirectory()) {
        collapsibleState = vscode.TreeItemCollapsibleState.Collapsed;
        contextValue = "folder";
      } else {
        let extname = path.extname(uri.fsPath);
        if (extname === FILE_EXTENSION_AS) {
          contextValue = "actionscript";
        } else if (extname === FILE_EXTENSION_MXML) {
          contextValue = "mxml";
        }
        command = {
          title: "Open File",
          command: "vscode.open",
          arguments: [uri],
        };
      }
    }
    super(label, collapsibleState);
    this.resourceUri = uri;
    this.command = command;
    this.contextValue = contextValue;
    this.workspaceFolder = workspaceFolder;
  }

  workspaceFolder: vscode.WorkspaceFolder;
}

export default class ActionScriptSourcePathDataProvider
  implements vscode.TreeDataProvider<ActionScriptSourcePath> {
  private _onDidChangeTreeData: vscode.EventEmitter<ActionScriptSourcePath | null> = new vscode.EventEmitter<ActionScriptSourcePath | null>();
  private _rootSourcePaths: ActionScriptSourcePath[];
  private _rootPathStrings: Set<string>;

  constructor() {
    let watcher = vscode.workspace.createFileSystemWatcher("**/asconfig.json");
    watcher.onDidChange(this.asconfigFileSystemWatcher_onEvent, this);
    watcher.onDidCreate(this.asconfigFileSystemWatcher_onEvent, this);
    watcher.onDidDelete(this.asconfigFileSystemWatcher_onEvent, this);
    this.refreshSourcePaths();
  }

  readonly onDidChangeTreeData: vscode.Event<ActionScriptSourcePath | null> = this
    ._onDidChangeTreeData.event;

  getTreeItem(element: ActionScriptSourcePath): vscode.TreeItem {
    return element;
  }

  getChildren(
    element?: ActionScriptSourcePath
  ): Thenable<ActionScriptSourcePath[]> {
    if (!element) {
      if (this._rootSourcePaths.length === 0) {
        return Promise.resolve([
          new ActionScriptSourcePath(
            "No source paths",
            element.workspaceFolder
          ),
        ]);
      }
      return Promise.resolve(this._rootSourcePaths);
    }

    const workspaceFolder = element.workspaceFolder;
    if (!workspaceFolder) {
      return Promise.resolve([
        new ActionScriptSourcePath(
          "Open a workspace to see source paths",
          undefined
        ),
      ]);
    }

    let asconfigPath = path.resolve(
      workspaceFolder.uri.fsPath,
      FILE_ASCONFIG_JSON
    );
    if (!fs.existsSync(asconfigPath)) {
      return Promise.resolve([
        new ActionScriptSourcePath(
          "No source paths in asconfig.json",
          workspaceFolder
        ),
      ]);
    }

    let elementUri = element.resourceUri;
    let elementPath = elementUri.fsPath;
    if (!fs.statSync(elementPath).isDirectory()) {
      return Promise.resolve([]);
    }
    let files = fs.readdirSync(elementPath);
    let sourcePaths = [];
    files.forEach((filePath) => {
      filePath = path.join(elementPath, filePath);
      if (fs.statSync(filePath).isDirectory()) {
        filePath = path.resolve(workspaceFolder.uri.fsPath, filePath);
        let fileUri = vscode.Uri.file(filePath);
        sourcePaths.push(new ActionScriptSourcePath(fileUri, workspaceFolder));
      } else {
        let extension = path.extname(filePath);
        //don't show files that have different extensions
        if (
          extension === FILE_EXTENSION_AS ||
          extension === FILE_EXTENSION_MXML
        ) {
          filePath = path.resolve(workspaceFolder.uri.fsPath, filePath);
          let fileUri = vscode.Uri.file(filePath);
          sourcePaths.push(
            new ActionScriptSourcePath(fileUri, workspaceFolder)
          );
        }
      }
    });
    return Promise.resolve(sourcePaths);
  }

  private refreshSourcePaths() {
    this._rootSourcePaths = [];
    this._rootPathStrings = new Set();
    if (vscode.workspace.workspaceFolders == undefined) {
      this._onDidChangeTreeData.fire();
      return;
    }
    vscode.workspace.workspaceFolders.forEach((workspaceFolder) => {
      let asconfigPath = path.resolve(
        workspaceFolder.uri.fsPath,
        FILE_ASCONFIG_JSON
      );
      if (!fs.existsSync(asconfigPath)) {
        this._onDidChangeTreeData.fire();
        return;
      }
      try {
        let contents = fs.readFileSync(asconfigPath, "utf8");
        let result = json5.parse(contents);
        if ("compilerOptions" in result) {
          let compilerOptions = result.compilerOptions;
          if ("source-path" in compilerOptions) {
            let sourcePath = compilerOptions["source-path"];
            if (Array.isArray(sourcePath)) {
              sourcePath.forEach((sourcePath: string) => {
                let resolvedRootFilePath = path.resolve(
                  workspaceFolder.uri.fsPath,
                  sourcePath
                );
                if (this._rootPathStrings.has(resolvedRootFilePath)) {
                  //resolved path already exists
                  return;
                }
                this._rootPathStrings.add(resolvedRootFilePath);
                let resolvedRootUri = vscode.Uri.file(resolvedRootFilePath);
                let rootPath = new ActionScriptSourcePath(
                  resolvedRootUri,
                  workspaceFolder
                );
                let name = path.basename(rootPath.resourceUri.fsPath);
                let extension = path.extname(name);
                if (extension.length > 0) {
                  //don't show the file extension
                  name = name.substr(0, name.length - extension.length);
                }
                rootPath.label = name;
                this._rootSourcePaths.push(rootPath);
              });
            }
          }
        }
      } catch (error) {
        //we'll ignore this one
      }
    });
    this.handleDuplicateRootPathNames();
    this._onDidChangeTreeData.fire();
  }

  private removeDuplicateLabel(
    duplicatePaths: string[],
    rootPath: ActionScriptSourcePath,
    otherRootPath?: ActionScriptSourcePath
  ) {
    let rootPathLabel = rootPath.label;
    let duplicateIndex = duplicatePaths.indexOf(rootPathLabel);
    if (
      duplicateIndex === -1 &&
      otherRootPath &&
      rootPathLabel === otherRootPath.label
    ) {
      duplicateIndex = duplicatePaths.length;
      duplicatePaths[duplicateIndex] = rootPathLabel;
    }
    if (duplicateIndex === -1) {
      return;
    }
    let count = rootPathLabel.split(path.sep).length;
    let resolved = path.resolve(
      rootPath.resourceUri.fsPath,
      "..".repeat(count)
    );
    rootPath.label = path.basename(resolved) + path.sep + rootPathLabel;
  }

  private handleDuplicateRootPathNames() {
    let duplicatePaths: string[] = [];
    let pathCount = this._rootSourcePaths.length;
    for (let i = 0; i < pathCount; i++) {
      let rootPath = this._rootSourcePaths[i];
      let j = i + 1;
      if (j === pathCount) {
        this.removeDuplicateLabel(duplicatePaths, rootPath);
        break;
      }
      for (; j < pathCount; j++) {
        let otherRootPath = this._rootSourcePaths[j];
        this.removeDuplicateLabel(duplicatePaths, rootPath, otherRootPath);
      }
    }
  }

  private asconfigFileSystemWatcher_onEvent(uri: vscode.Uri) {
    this.refreshSourcePaths();
  }
}
