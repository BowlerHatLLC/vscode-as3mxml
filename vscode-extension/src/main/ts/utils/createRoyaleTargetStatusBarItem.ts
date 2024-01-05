/*
Copyright 2016-2024 Bowler Hat LLC

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
import * as path from "path";
import validateRoyale from "./validateRoyale";
import getFrameworkSDKPathWithFallbacks from "./getFrameworkSDKPathWithFallbacks";

const FILE_EXTENSION_AS = ".as";
const FILE_EXTENSION_MXML = ".mxml";
const FILE_NAME_ASCONFIG_JSON = "asconfig.json";

export default function createRoyaleTargetStatusBarItem(): vscode.StatusBarItem {
  let statusBarItem = vscode.window.createStatusBarItem(
    vscode.StatusBarAlignment.Right,
    98
  );
  statusBarItem.tooltip = "Set Preferred Royale Target";
  statusBarItem.command = "as3mxml.selectRoyalePreferredTarget";
  statusBarItem.text = "SWF";
  vscode.workspace.onDidChangeConfiguration((e) => {
    if (
      e.affectsConfiguration("as3mxml.sdk.framework") ||
      e.affectsConfiguration("as3mxml.sdk.editor")
    )
      refreshStatusBarItemVisibility(statusBarItem);
  });
  vscode.window.onDidChangeVisibleTextEditors((editor) => {
    refreshStatusBarItemVisibility(statusBarItem);
  });
  refreshStatusBarItemVisibility(statusBarItem);
  return statusBarItem;
}

function refreshStatusBarItemVisibility(statusBarItem: vscode.StatusBarItem) {
  let showStatusBarItem = true;
  if (!validateRoyale(getFrameworkSDKPathWithFallbacks())) {
    // show for Royale projects only
    showStatusBarItem = false;
  } else {
    const textEditor = vscode.window.visibleTextEditors.find((textEditor) => {
      var fileName = textEditor.document.fileName;
      fileName = path.basename(fileName);
      return (
        fileName.endsWith(FILE_EXTENSION_AS) ||
        fileName.endsWith(FILE_EXTENSION_MXML) ||
        fileName === FILE_NAME_ASCONFIG_JSON ||
        /^asconfig\.\w+\.json$/.test(fileName)
      );
    });
    if (!textEditor) {
      showStatusBarItem = false;
    }
  }
  if (showStatusBarItem) {
    statusBarItem.show();
  } else {
    statusBarItem.hide();
  }
}
