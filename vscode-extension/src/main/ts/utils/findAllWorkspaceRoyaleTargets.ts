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
import * as fs from "fs";
import * as path from "path";
import json5 from "json5/dist/index.mjs";

const ASCONFIG_JSON = "asconfig.json";

export default function findAllWorkspaceRoyaleTargets(): Set<string> {
  const allWorkspaceTargets: Set<string> = new Set();
  if (vscode.workspace.workspaceFolders !== undefined) {
    for (let workspaceFolder of vscode.workspace.workspaceFolders) {
      const asconfigPath = path.resolve(
        workspaceFolder.uri.fsPath,
        ASCONFIG_JSON
      );

      if (!fs.existsSync(asconfigPath)) {
        continue;
      }
      let asconfigJSON: any;
      try {
        const asconfigContent = fs.readFileSync(asconfigPath, {
          encoding: "utf8",
        });
        asconfigJSON = json5.parse(asconfigContent);
      } catch (e) {
        continue;
      }
      const targets: string[] | undefined =
        asconfigJSON.compilerOptions?.targets;
      if (targets) {
        targets.forEach((target) => allWorkspaceTargets.add(target));
      }
    }
  }
  if (allWorkspaceTargets.size === 0) {
    // make sure that there's always at least one target
    allWorkspaceTargets.add("JSRoyale");
  }
  return allWorkspaceTargets;
}
