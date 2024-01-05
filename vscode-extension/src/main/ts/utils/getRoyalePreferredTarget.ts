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
import findAllWorkspaceRoyaleTargets from "./findAllWorkspaceRoyaleTargets";
import validateRoyale from "./validateRoyale";
import getFrameworkSDKPathWithFallbacks from "./getFrameworkSDKPathWithFallbacks";

const ROYALE_PREFERRED_TARGET = "as3mxml.royale.preferredTarget";
const TARGET_JS_ROYALE = "JSRoyale";
const TARGET_SWF = "SWF";

export default function getRoyalePreferredTarget(
  context: vscode.ExtensionContext
): string {
  if (!validateRoyale(getFrameworkSDKPathWithFallbacks())) {
    // not royale, so clear the preferred target
    context.workspaceState.update(ROYALE_PREFERRED_TARGET, undefined);
    // if it's not Royale, then the
    return TARGET_SWF;
  }

  const savedPreferredTarget = context.workspaceState.get(
    ROYALE_PREFERRED_TARGET
  );
  const allWorkspaceTargets = findAllWorkspaceRoyaleTargets();

  if (
    typeof savedPreferredTarget === "string" &&
    allWorkspaceTargets.has(savedPreferredTarget)
  ) {
    return savedPreferredTarget;
  }

  let newTarget: string;
  if (allWorkspaceTargets.has(TARGET_JS_ROYALE)) {
    // prefer JSRoyale
    newTarget = TARGET_JS_ROYALE;
  } else {
    // otherwise, try to find any JS target
    for (let availableTarget of allWorkspaceTargets) {
      if (availableTarget.startsWith("JS")) {
        newTarget = availableTarget;
        break;
      }
    }
    if (!newTarget && allWorkspaceTargets.size > 0) {
      // finally, fall back to the first available target
      newTarget = allWorkspaceTargets.values().next().value;
    }
  }
  if (!newTarget) {
    // this shouldn't happen, but just to be safe
    newTarget = TARGET_JS_ROYALE;
  }
  if (savedPreferredTarget) {
    // if the saved target is no longer available, clear the preference
    context.workspaceState.update(ROYALE_PREFERRED_TARGET, undefined);
  }
  return newTarget;
}
