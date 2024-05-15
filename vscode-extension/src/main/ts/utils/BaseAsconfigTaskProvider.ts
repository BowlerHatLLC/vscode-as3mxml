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
import * as fs from "fs";
import json5 from "json5/dist/index.mjs";

const ASCONFIG_JSON = "asconfig.json";
const FILE_EXTENSION_AS = ".as";
const FILE_EXTENSION_MXML = ".mxml";
const CONFIG_AIR = "air";
const CONFIG_AIRMOBILE = "airmobile";
const FIELD_CONFIG = "config";
const FIELD_ANIMATE_OPTIONS = "animateOptions";
const FIELD_FILE = "file";
const FIELD_APPLICATION = "application";
const FIELD_AIR_OPTIONS = "airOptions";
const FIELD_TYPE = "type";
const TYPE_LIB = "lib";

export default class BaseAsconfigTaskProvider {
  constructor(
    context: vscode.ExtensionContext,
    public javaExecutablePath: string
  ) {
    this._context = context;
  }

  protected _context: vscode.ExtensionContext;

  provideTasks(
    token: vscode.CancellationToken
  ): vscode.ProviderResult<vscode.Task[]> {
    if (vscode.workspace.workspaceFolders === undefined) {
      return [];
    }

    return new Promise(async (resolve) => {
      let result: vscode.Task[] = [];
      let asconfigJSONs = await vscode.workspace.findFiles(
        "**/asconfig*.json",
        "**/node_modules/**"
      );
      asconfigJSONs.forEach((jsonURI) => {
        let workspaceFolder = vscode.workspace.getWorkspaceFolder(jsonURI);
        this.provideTasksForASConfigJSON(jsonURI, workspaceFolder, result);
      });
      if (result.length === 0 && vscode.window.activeTextEditor) {
        let activeURI = vscode.window.activeTextEditor.document.uri;
        let workspaceFolder = vscode.workspace.getWorkspaceFolder(activeURI);
        if (workspaceFolder) {
          let activePath = activeURI.fsPath;
          if (
            activePath.endsWith(FILE_EXTENSION_AS) ||
            activePath.endsWith(FILE_EXTENSION_MXML)
          ) {
            //we couldn't find asconfig.json, but an .as or .mxml file
            //is currently open from the this workspace, so might as
            //well provide the tasks
            this.provideTasksForASConfigJSON(null, workspaceFolder, result);
          }
        }
      }
      resolve(result);
    });
  }

  resolveTask(
    task: vscode.Task,
    token?: vscode.CancellationToken
  ): vscode.ProviderResult<vscode.Task> {
    return undefined;
  }

  protected provideTasksForASConfigJSON(
    jsonURI: vscode.Uri,
    workspaceFolder: vscode.WorkspaceFolder,
    result: vscode.Task[]
  ) {
    return undefined;
  }

  protected getCommand(workspaceRoot: vscode.WorkspaceFolder): string[] {
    let nodeModulesBin = path.join(
      workspaceRoot.uri.fsPath,
      "node_modules",
      ".bin"
    );
    if (process.platform === "win32") {
      let executableName = "asconfigc.cmd";
      //start out by looking for asconfigc in the workspace's local Node modules
      let winPath = path.join(nodeModulesBin, executableName);
      if (fs.existsSync(winPath)) {
        return [winPath];
      }
      let useBundled = <string>(
        vscode.workspace.getConfiguration("as3mxml").get("asconfigc.useBundled")
      );
      if (!useBundled) {
        //use an executable on the system path
        return [executableName];
      }
      //use the version bundled with the extension
      return this.getDefaultCommand();
    }
    let executableName = "asconfigc";
    let unixPath = path.join(nodeModulesBin, executableName);
    if (fs.existsSync(unixPath)) {
      return [unixPath];
    }
    let useBundled = <string>(
      vscode.workspace.getConfiguration("as3mxml").get("asconfigc.useBundled")
    );
    if (!useBundled) {
      //use an executable on the system path
      return [executableName];
    }
    //use the version bundled with the extension
    return this.getDefaultCommand();
  }

  protected getDefaultCommand(): string[] {
    return [
      this.javaExecutablePath,
      "-jar",
      path.join(this._context.extensionPath, "bin", "asconfigc.jar"),
    ];
  }

  protected getASConfigValue(
    jsonURI: vscode.Uri,
    workspaceURI: vscode.Uri
  ): string {
    if (!jsonURI) {
      return undefined;
    }
    return jsonURI.toString().substring(workspaceURI.toString().length + 1);
  }

  protected readASConfigJSON(jsonURI: vscode.Uri) {
    if (!jsonURI) {
      return null;
    }
    let jsonPath = jsonURI.fsPath;
    if (!fs.existsSync(jsonPath)) {
      return null;
    }
    try {
      let contents = fs.readFileSync(jsonPath, "utf8");
      return json5.parse(contents);
    } catch (error) {
      console.error(`Error reading file: ${jsonPath}. ${error}`);
    }
    return null;
  }

  protected isLibrary(asconfigJson: any): boolean {
    if (!(FIELD_TYPE in asconfigJson)) {
      return false;
    }
    let type = asconfigJson[FIELD_TYPE];
    return type === TYPE_LIB;
  }

  protected isAnimate(asconfigJson: any): boolean {
    if (!(FIELD_ANIMATE_OPTIONS in asconfigJson)) {
      return false;
    }
    let animateOptions = asconfigJson[FIELD_ANIMATE_OPTIONS];
    return FIELD_FILE in animateOptions;
  }

  protected isAIRDesktop(asconfigJson: any): boolean {
    if (FIELD_APPLICATION in asconfigJson) {
      return true;
    }
    if (FIELD_AIR_OPTIONS in asconfigJson) {
      return true;
    }
    if (FIELD_CONFIG in asconfigJson) {
      let config = asconfigJson[FIELD_CONFIG];
      if (config === CONFIG_AIR) {
        return true;
      }
    }
    return false;
  }

  protected isAIRMobile(asconfigJson: any): boolean {
    if (FIELD_CONFIG in asconfigJson) {
      let config = asconfigJson[FIELD_CONFIG];
      if (config === CONFIG_AIRMOBILE) {
        return true;
      }
    }
    return false;
  }
}
