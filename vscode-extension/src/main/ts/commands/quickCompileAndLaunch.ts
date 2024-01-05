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
import * as child_process from "child_process";
import * as fs from "fs";
import * as path from "path";
import json5 from "json5/dist/index.mjs";
import findAnimate from "../utils/findAnimate";

const QUICK_COMPILE_MESSAGE = "Building ActionScript & MXML project...";
const CANNOT_LAUNCH_QUICK_COMPILE_FAILED_ERROR =
  "Quick compile failed with errors. Launch cancelled.";
const PREVIOUS_QUICK_COMPILE_NOT_COMPLETE_MESSAGE =
  "Quick compile not started. A previous quick compile has not completed yet.";

export default function quickCompileAndLaunch(uris: string[], debug: boolean) {
  if (uris.length === 0) {
    return;
  } else if (uris.length === 1) {
    quickCompileAndLaunchURI(uris[0], debug);
  } else {
    let items = uris.map((uri): vscode.QuickPickItem => {
      let vscodeUri = vscode.Uri.parse(uri);
      return {
        label: path.basename(vscodeUri.fsPath),
        description: vscodeUri.fsPath,
        uri: uri,
      } as any;
    });
    vscode.window.showQuickPick(items).then((result) => {
      if (!("uri" in result)) {
        return;
      }
      quickCompileAndLaunchURI(result["uri"] as string, debug);
    });
  }
}

let quickCompileActive = false;

async function quickCompileAndLaunchURI(uri: string, debug: boolean) {
  if (!uri) {
    //it's possible for no folder to be chosen when using
    //showWorkspaceFolderPick()
    return;
  }
  if (quickCompileActive) {
    vscode.window.showErrorMessage(PREVIOUS_QUICK_COMPILE_NOT_COMPLETE_MESSAGE);
    return;
  }
  quickCompileActive = true;
  try {
    //before running a task, VSCode saves all files. we should do the same
    //before running a quick compile, since it's like a task.
    await vscode.commands.executeCommand("workbench.action.files.saveAll");
    await vscode.window.withProgress(
      { location: vscode.ProgressLocation.Window },
      (progress) => {
        progress.report({ message: QUICK_COMPILE_MESSAGE });
        return new Promise<void>((resolve, reject) => {
          return vscode.commands
            .executeCommand("workbench.action.debug.stop")
            .then(() => {
              let animateFile = getAnimateFile(uri);
              if (animateFile) {
                let animatePath = findAnimate();

                let extension = vscode.extensions.getExtension(
                  "bowlerhatllc.vscode-as3mxml"
                );
                let fileName = debug ? "debug-movie.jsfl" : "test-movie.jsfl";
                let jsflPath = path.resolve(
                  extension.extensionPath,
                  "jsfl",
                  fileName
                );

                if (process.platform === "win32") {
                  child_process.spawn(animatePath, [animateFile, jsflPath]);
                } else if (process.platform === "darwin") {
                  //macOS
                  child_process.spawn("open", [
                    "-a",
                    animatePath,
                    animateFile,
                    jsflPath,
                  ]);
                } else {
                  reject();
                  return;
                }

                resolve();
                return;
              }
              return vscode.commands
                .executeCommand("as3mxml.quickCompile", uri, debug)
                .then(
                  (result) => {
                    resolve();

                    if (result === true) {
                      if (debug) {
                        vscode.commands.executeCommand(
                          "workbench.action.debug.start"
                        );
                      } else {
                        vscode.commands.executeCommand(
                          "workbench.action.debug.run"
                        );
                      }
                    } else {
                      vscode.window.showErrorMessage(
                        CANNOT_LAUNCH_QUICK_COMPILE_FAILED_ERROR
                      );
                    }
                  },
                  () => {
                    resolve();

                    //if the build failed, notify the user that we're not starting
                    //a debug session
                    vscode.window.showErrorMessage(
                      CANNOT_LAUNCH_QUICK_COMPILE_FAILED_ERROR
                    );
                  }
                );
            });
        });
      }
    );
  } finally {
    quickCompileActive = false;
  }
}

function getAnimateFile(uri: string) {
  let vscodeUri = vscode.Uri.parse(uri);
  let asconfigPath = path.resolve(vscodeUri.fsPath, "asconfig.json");
  if (!fs.existsSync(asconfigPath) || fs.statSync(asconfigPath).isDirectory()) {
    return null;
  }
  try {
    let contents = fs.readFileSync(asconfigPath, "utf8");
    let result = json5.parse(contents);
    if ("type" in result && result.type !== "app") {
      return null;
    }
    if (!("animateOptions" in result)) {
      return null;
    }
    let animateOptions = result.animateOptions;
    if (!("file" in animateOptions)) {
      return null;
    }
    let flaPath = animateOptions.file;
    if (path.isAbsolute(flaPath)) {
      return flaPath;
    }
    return path.resolve(vscodeUri.fsPath, flaPath);
  } catch (error) {
    return null;
  }
}
