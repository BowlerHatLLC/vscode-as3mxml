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
import * as fs from "fs";
import * as path from "path";
import * as vscode from "vscode";
import getFrameworkSDKPathWithFallbacks from "./getFrameworkSDKPathWithFallbacks";
import findAnimate from "./findAnimate";
import BaseAsconfigTaskProvider from "./BaseAsconfigTaskProvider";
import getExtraCompilerTokens from "./getExtraCompilerTokens";

const FIELD_ANIMATE_OPTIONS = "animateOptions";
const FIELD_FILE = "file";
const MATCHER = [];
const TASK_TYPE_ANIMATE = "animate";
const TASK_SOURCE_ANIMATE = "Adobe Animate";
const TASK_NAME_COMPILE_DEBUG = "compile debug";
const TASK_NAME_COMPILE_RELEASE = "compile release";
const TASK_NAME_PUBLISH_DEBUG = "publish debug";
const TASK_NAME_PUBLISH_RELEASE = "publish release";

interface AnimateTaskDefinition extends vscode.TaskDefinition {
  type: typeof TASK_TYPE_ANIMATE;
  debug: boolean;
  publish: boolean;
  asconfig?: string;
}

export default class ActionScriptTaskProvider
  extends BaseAsconfigTaskProvider
  implements vscode.TaskProvider {
  protected provideTasksForASConfigJSON(
    jsonURI: vscode.Uri,
    workspaceFolder: vscode.WorkspaceFolder,
    result: vscode.Task[]
  ) {
    let isAnimate = false;
    let isAIR = false;
    let asconfigJson = this.readASConfigJSON(jsonURI);
    if (asconfigJson !== null) {
      isAnimate = this.isAnimate(asconfigJson);
      isAIR = this.isAIRDesktop(asconfigJson) || this.isAIRMobile(asconfigJson);
    }

    let frameworkSDK = getFrameworkSDKPathWithFallbacks();
    if (frameworkSDK === null) {
      //we don't have a valid SDK
      return;
    }
    let command = this.getCommand(workspaceFolder);

    if (!isAnimate) {
      //handled by the ActionScript task provider
      return;
    }

    //compile SWF with Animate
    let animatePath = findAnimate();
    if (!animatePath) {
      //don't add the tasks if we cannot find Adobe Animate
      return;
    }
    let flaPath = asconfigJson[FIELD_ANIMATE_OPTIONS][FIELD_FILE];
    if (!path.isAbsolute(flaPath)) {
      flaPath = path.resolve(workspaceFolder.uri.fsPath, flaPath);
    }
    if (!fs.existsSync(flaPath)) {
      //don't add the tasks if we cannot find the FLA file
      return;
    }

    let taskNameSuffix = path.basename(flaPath);
    result.push(
      this.getAnimateTask(
        `${TASK_NAME_COMPILE_DEBUG} - ${taskNameSuffix}`,
        jsonURI,
        workspaceFolder,
        command,
        animatePath,
        true,
        false,
        isAIR
      )
    );
    result.push(
      this.getAnimateTask(
        `${TASK_NAME_COMPILE_RELEASE} - ${taskNameSuffix}`,
        jsonURI,
        workspaceFolder,
        command,
        animatePath,
        false,
        false,
        false
      )
    );
    result.push(
      this.getAnimateTask(
        `${TASK_NAME_PUBLISH_DEBUG} - ${taskNameSuffix}`,
        jsonURI,
        workspaceFolder,
        command,
        animatePath,
        true,
        true,
        false
      )
    );
    result.push(
      this.getAnimateTask(
        `${TASK_NAME_PUBLISH_RELEASE} - ${taskNameSuffix}`,
        jsonURI,
        workspaceFolder,
        command,
        animatePath,
        false,
        true,
        false
      )
    );
  }

  private getAnimateTask(
    description: string,
    jsonURI: vscode.Uri,
    workspaceFolder: vscode.WorkspaceFolder,
    command: string[],
    animatePath: string,
    debug: boolean,
    publish: boolean,
    unpackageANEs: boolean
  ): vscode.Task {
    let asconfig: string = this.getASConfigValue(jsonURI, workspaceFolder.uri);
    let definition: AnimateTaskDefinition = {
      type: TASK_TYPE_ANIMATE,
      debug,
      publish,
      asconfig,
    };
    let options = ["--animate", animatePath];
    if (debug) {
      options.push("--debug=true");
    } else {
      options.push("--debug=false");
    }
    if (publish) {
      options.push("--publish-animate=true");
    } else {
      options.push("--publish-animate=false");
    }
    if (unpackageANEs) {
      options.push("--unpackage-anes=true");
    }
    if (
      vscode.workspace
        .getConfiguration("as3mxml")
        .get("asconfigc.verboseOutput")
    ) {
      options.push("--verbose=true");
    }
    if (jsonURI) {
      options.push("--project", jsonURI.fsPath);
    }
    if (command.length > 1) {
      options.unshift(...command.slice(1));
    }
    let tokens = getExtraCompilerTokens();
    if (tokens && tokens.length != 0)
    {
      options = options.concat(tokens);
    }
    let execution = new vscode.ProcessExecution(command[0], options);
    let task = new vscode.Task(
      definition,
      workspaceFolder,
      description,
      TASK_SOURCE_ANIMATE,
      execution,
      MATCHER
    );
    task.group = vscode.TaskGroup.Build;
    return task;
  }
}
