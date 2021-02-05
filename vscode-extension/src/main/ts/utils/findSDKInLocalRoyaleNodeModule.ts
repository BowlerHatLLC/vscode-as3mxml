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
import validateFrameworkSDK from "./validateFrameworkSDK";

const NODE_MODULES = "node_modules";
const MODULE_ORG = "@apache-royale";
const MODULE_NAMES = ["royale-js", "royale-js-swf"];

export default function findSDKInLocalRoyaleNodeModule(): string {
  if (vscode.workspace.workspaceFolders === undefined) {
    return null;
  }
  for (let i = 0, count = MODULE_NAMES.length; i < count; i++) {
    let moduleName = MODULE_NAMES[i];
    let nodeModule = path.join(
      vscode.workspace.workspaceFolders[0].uri.fsPath,
      NODE_MODULES,
      MODULE_ORG,
      moduleName
    );
    nodeModule = validateFrameworkSDK(nodeModule);
    if (nodeModule !== null) {
      return nodeModule;
    }
  }
  return null;
}
