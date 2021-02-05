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
import * as vscode from "vscode";
import * as fs from "fs";
import * as path from "path";
import findSDKName from "../utils/findSDKName";
import validateFrameworkSDK from "../utils/validateFrameworkSDK";
import findSDKInLocalRoyaleNodeModule from "../utils/findSDKInLocalRoyaleNodeModule";
import findSDKInLocalFlexJSNodeModule from "../utils/findSDKInLocalFlexJSNodeModule";
import findSDKInRoyaleHomeEnvironmentVariable from "../utils/findSDKInRoyaleHomeEnvironmentVariable";
import findSDKInFlexHomeEnvironmentVariable from "../utils/findSDKInFlexHomeEnvironmentVariable";
import findSDKsInPathEnvironmentVariable from "../utils/findSDKsInPathEnvironmentVariable";

const DESCRIPTION_NODE_MODULE = "Node.js Module";
const DESCRIPTION_ROYALE_HOME = "ROYALE_HOME environment variable";
const DESCRIPTION_FLEX_HOME = "FLEX_HOME environment variable";
const DESCRIPTION_PATH = "PATH environment variable";
const DESCRIPTION_CURRENT = "Current SDK";
const DESCRIPTION_EDITOR_SDK = "Editor SDK in Settings";
const DESCRIPTION_FLASH_BUILDER_4_7 = "Flash Builder 4.7";
const DESCRIPTION_FLASH_BUILDER_4_6 = "Flash Builder 4.6";
const DESCRIPTION_USER_DEFINED = "User Defined";
const SEARCH_PATHS_MAC = [
  {
    path: "/Applications/Adobe Flash Builder 4.7/sdks/",
    description: DESCRIPTION_FLASH_BUILDER_4_7,
  },
  {
    path: "/Applications/Adobe Flash Builder 4.6/sdks/",
    description: DESCRIPTION_FLASH_BUILDER_4_6,
  },
];
const SEARCH_PATHS_WIN = [
  {
    path: "C:\\Program Files\\Adobe\\Adobe Flash Builder 4.7 (64 Bit)\\sdks\\",
    description: DESCRIPTION_FLASH_BUILDER_4_7,
  },
  {
    path: "C:\\Program Files (x86)\\Adobe\\Adobe Flash Builder 4.6\\sdks\\",
    description: DESCRIPTION_FLASH_BUILDER_4_6,
  },
  {
    path: "C:\\Program Files\\Adobe\\Adobe Flash Builder 4.6\\sdks\\",
    description: DESCRIPTION_FLASH_BUILDER_4_6,
  },
];

interface SDKQuickPickItem extends vscode.QuickPickItem {
  custom?: any;
}

function openSettingsForSearchPaths() {
  vscode.window
    .showOpenDialog({
      canSelectFiles: false,
      canSelectFolders: true,
    })
    .then(
      (folders: vscode.Uri[] | undefined) => {
        if (folders === undefined || folders.length === 0) {
          return;
        }
        let config = vscode.workspace.getConfiguration("as3mxml");
        let searchPaths: string[] = config.get("sdk.searchPaths");
        if (!searchPaths) {
          searchPaths = [];
        }
        let beforeLength = searchPaths.length;
        folders.forEach((folder) => {
          let path = folder.fsPath;
          if (searchPaths.indexOf(path) !== -1) {
            return;
          }
          searchPaths.push(path);
        });
        if (searchPaths.length > beforeLength) {
          let searchPathsInspection = vscode.workspace
            .getConfiguration("as3mxml")
            .inspect("sdk.searchPaths");
          if (searchPathsInspection.workspaceValue) {
            config.update(
              "sdk.searchPaths",
              searchPaths,
              vscode.ConfigurationTarget.Workspace
            );
          } else {
            config.update(
              "sdk.searchPaths",
              searchPaths,
              vscode.ConfigurationTarget.Global
            );
          }
        }
      },
      () => {
        return vscode.window.showErrorMessage(
          "Failed to add folder to SDK search paths"
        );
      }
    );
}

function addSDKItem(
  path: string,
  description: string,
  items: SDKQuickPickItem[],
  allPaths: string[],
  require: boolean
): void {
  if (allPaths.indexOf(path) !== -1) {
    //skip duplicate
    return;
  }
  allPaths.push(path);
  let label = findSDKName(path);
  if (label === null) {
    //we couldn't find the name of this SDK
    if (!require) {
      //if it's not required, skip it
      return;
    }
    label = "Unknown SDK";
  }
  items.push({
    label: label,
    detail: path,
    description: description,
  });
}

function checkSearchPath(
  searchPath: string,
  description: string,
  items: SDKQuickPickItem[],
  allPaths: string[]
) {
  let validSDK = validateFrameworkSDK(searchPath);
  if (validSDK !== null) {
    addSDKItem(validSDK, description, items, allPaths, false);
  } else if (
    fs.existsSync(searchPath) &&
    fs.statSync(searchPath).isDirectory()
  ) {
    let files = fs.readdirSync(searchPath);
    files.forEach((file) => {
      let filePath = path.join(searchPath, file);
      let validSDK = validateFrameworkSDK(filePath);
      if (validSDK !== null) {
        addSDKItem(validSDK, description, items, allPaths, false);
      }
    });
  }
}

function createSearchPathsItem(): SDKQuickPickItem {
  let item: SDKQuickPickItem = {
    label: "Add more SDKs to this list...",
    description: null,
    detail: "Choose a folder containing one or more ActionScript SDKs",
    custom: true,
  };
  return item;
}

export default function selectWorkspaceSDK(): void {
  let allPaths: string[] = [];
  let items: SDKQuickPickItem[] = [];
  //for convenience, add an option to open user settings and define custom SDK paths
  items.push(createSearchPathsItem());
  //start with the current framework and editor SDKs
  let frameworkSDK = <string>(
    vscode.workspace.getConfiguration("as3mxml").get("sdk.framework")
  );
  frameworkSDK = validateFrameworkSDK(frameworkSDK);
  let editorSDK = <string>(
    vscode.workspace.getConfiguration("as3mxml").get("sdk.editor")
  );
  editorSDK = validateFrameworkSDK(editorSDK);
  let addedEditorSDK = false;
  if (frameworkSDK) {
    addSDKItem(frameworkSDK, DESCRIPTION_CURRENT, items, allPaths, true);
  } else if (editorSDK) {
    //for legacy reasons, we fall back to the editor SDK if the framework
    //SDK is not defined.
    addedEditorSDK = true;
    addSDKItem(editorSDK, DESCRIPTION_CURRENT, items, allPaths, true);
  }
  //then search for an SDK that's a locally installed Node.js module
  let royaleNodeModuleSDK = findSDKInLocalRoyaleNodeModule();
  if (royaleNodeModuleSDK) {
    addSDKItem(
      royaleNodeModuleSDK,
      DESCRIPTION_NODE_MODULE,
      items,
      allPaths,
      true
    );
  }
  let flexjsNodeModuleSDK = findSDKInLocalFlexJSNodeModule();
  if (flexjsNodeModuleSDK) {
    addSDKItem(
      flexjsNodeModuleSDK,
      DESCRIPTION_NODE_MODULE,
      items,
      allPaths,
      true
    );
  }
  //if the user has defined search paths for SDKs, include them
  let searchPaths = vscode.workspace
    .getConfiguration("as3mxml")
    .get("sdk.searchPaths");
  if (Array.isArray(searchPaths)) {
    searchPaths.forEach((searchPath) => {
      checkSearchPath(searchPath, DESCRIPTION_USER_DEFINED, items, allPaths);
    });
  } else if (typeof searchPaths === "string") {
    checkSearchPath(searchPaths, DESCRIPTION_USER_DEFINED, items, allPaths);
  }
  //check some common locations where SDKs might exist
  let knownPaths = SEARCH_PATHS_MAC;
  if (process.platform === "win32") {
    knownPaths = SEARCH_PATHS_WIN;
  }
  knownPaths.forEach((knownPath) => {
    checkSearchPath(knownPath.path, knownPath.description, items, allPaths);
  });
  //if we haven't already added the editor SDK, do it now
  if (!addedEditorSDK && editorSDK) {
    addSDKItem(editorSDK, DESCRIPTION_EDITOR_SDK, items, allPaths, true);
  }
  //check if the ROYALE_HOME environment variable is defined
  let royaleHome = findSDKInRoyaleHomeEnvironmentVariable();
  if (royaleHome) {
    addSDKItem(royaleHome, DESCRIPTION_ROYALE_HOME, items, allPaths, false);
  }
  //check if the FLEX_HOME environment variable is defined
  let flexHome = findSDKInFlexHomeEnvironmentVariable();
  if (flexHome) {
    addSDKItem(flexHome, DESCRIPTION_FLEX_HOME, items, allPaths, false);
  }
  //check if any SDKs are in the PATH environment variable
  let paths = findSDKsInPathEnvironmentVariable();
  paths.forEach((sdkPath) => {
    addSDKItem(sdkPath, DESCRIPTION_PATH, items, allPaths, false);
  });
  vscode.window
    .showQuickPick(items, {
      placeHolder: "Select an ActionScript SDK for this workspace",
    })
    .then(
      (value: SDKQuickPickItem) => {
        if (!value) {
          //no new SDK was picked, so do nothing
          return;
        }
        if (typeof value.custom !== "undefined") {
          //if the user chose to define a custom SDK, open workspace settings
          openSettingsForSearchPaths();
          return;
        }
        //if they chose an SDK, save it to the settings
        let newFrameworkPath = value.detail;
        //if a workspace folder is open, save it to the workspace settings
        //if no folder is open, save it globally
        let configurationTarget =
          vscode.workspace.workspaceFolders !== undefined
            ? undefined
            : vscode.ConfigurationTarget.Global;
        vscode.workspace
          .getConfiguration("as3mxml")
          .update("sdk.framework", newFrameworkPath, configurationTarget);
      },
      () => {
        //do nothing
      }
    );
}
