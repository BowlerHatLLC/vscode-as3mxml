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
import * as path from "path";
import * as vscode from "vscode";

const FILE_EXTENSION_AS = ".as";
const FILE_EXTENSION_MXML = ".mxml";
const FILE_NAME_ASCONFIG_JSON = "asconfig.json";

export default function createActionScriptSDKStatusBarItem(): vscode.StatusBarItem {
  let statusBarItem = vscode.window.createStatusBarItem(
    vscode.StatusBarAlignment.Right,
    99
  );
  statusBarItem.command = "as3mxml.selectWorkspaceSDK";
  vscode.window.onDidChangeActiveTextEditor((editor) => {
    refreshStatusBarItemVisibility(statusBarItem);
  });
  refreshStatusBarItemVisibility(statusBarItem);
  return statusBarItem;
}

function refreshStatusBarItemVisibility(statusBarItem: vscode.StatusBarItem) {
  if (vscode.window.activeTextEditor === undefined) {
    statusBarItem.hide();
    return;
  }
  var fileName = vscode.window.activeTextEditor.document.fileName;
  fileName = path.basename(fileName);
  if (
    !fileName.endsWith(FILE_EXTENSION_AS) &&
    !fileName.endsWith(FILE_EXTENSION_MXML) &&
    fileName !== FILE_NAME_ASCONFIG_JSON &&
    !/^asconfig\.\w+\.json$/.test(fileName)
  ) {
    statusBarItem.hide();
    return;
  }
  statusBarItem.show();
}
