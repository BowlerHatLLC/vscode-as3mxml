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
import findAllWorkspaceRoyaleTargets from "../utils/findAllWorkspaceRoyaleTargets";

const ROYALE_PREFERRED_TARGET = "as3mxml.royale.preferredTarget";

export default async function selectRoyalePreferredTarget(
  context: vscode.ExtensionContext
) {
  const allWorkspaceTargets = findAllWorkspaceRoyaleTargets();
  const selectedTarget = await vscode.window.showQuickPick(
    Array.from(allWorkspaceTargets),
    {
      placeHolder: "Select preferred Royale target",
    }
  );
  if (!selectedTarget) {
    // nothing selected, so keep existing target
    return Promise.resolve();
  }
  context.workspaceState.update(ROYALE_PREFERRED_TARGET, selectedTarget);
  return Promise.resolve();
}
