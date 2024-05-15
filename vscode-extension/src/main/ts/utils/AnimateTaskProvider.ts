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
import * as fs from "fs";
import * as path from "path";
import * as vscode from "vscode";
import findAnimate from "./findAnimate";
import BaseAsconfigTaskProvider from "./BaseAsconfigTaskProvider";

const ASCONFIG_JSON = "asconfig.json";
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

export default class AnimateTaskProvider
  extends BaseAsconfigTaskProvider
  implements vscode.TaskProvider
{
  resolveTask(
    task: vscode.Task,
    token?: vscode.CancellationToken
  ): vscode.ProviderResult<vscode.Task> {
    if (task.definition.type !== TASK_TYPE_ANIMATE) {
      return undefined;
    }
    const taskDef = task.definition as AnimateTaskDefinition;
    const animatePath = findAnimate();
    if (!animatePath) {
      //don't add the tasks if we cannot find Adobe Animate
      return undefined;
    }
    if (task.scope === vscode.TaskScope.Workspace) {
      return this.resolveTaskForMultiRootWorkspace(task, taskDef, animatePath);
    } else if (typeof task.scope !== "object") {
      return undefined;
    }
    const workspaceFolder = task.scope as vscode.WorkspaceFolder;
    if (!workspaceFolder) {
      return undefined;
    }
    if (!taskDef.asconfig) {
      // if the asconfig field is blank, populate it with a default value
      return this.resolveTaskForMissingAsconfigField(
        task,
        taskDef,
        workspaceFolder,
        animatePath
      );
    }
    // nothing could be resolved
    return undefined;
  }

  protected resolveTaskForMultiRootWorkspace(
    originalTask: vscode.Task,
    taskDef: AnimateTaskDefinition,
    animatePath: string
  ): vscode.Task {
    if (vscode.workspace.workspaceFolders === undefined) {
      return undefined;
    }
    const asconfigPath = taskDef.asconfig;
    if (!asconfigPath) {
      return undefined;
    }
    const asconfigPathParts = asconfigPath.split(/[\\\/]/g);
    if (asconfigPathParts.length < 2) {
      return undefined;
    }
    const workspaceNameToFind = asconfigPathParts[0];
    const workspaceFolder = vscode.workspace.workspaceFolders.find(
      (workspaceFolder) => workspaceFolder.name == workspaceNameToFind
    );
    if (!workspaceFolder) {
      return undefined;
    }
    const jsonUri = vscode.Uri.joinPath(
      workspaceFolder.uri,
      ...asconfigPathParts.slice(1)
    );
    let isAIR = false;
    const asconfigJson = this.readASConfigJSON(jsonUri);
    if (asconfigJson !== null) {
      isAIR = this.isAIRMobile(asconfigJson) || this.isAIRDesktop(asconfigJson);
    }
    const newTask = this.getAnimateTask(
      originalTask.name,
      jsonUri,
      workspaceFolder,
      animatePath,
      taskDef.debug,
      taskDef.publish,
      isAIR
    );
    // the new task's definition must strictly equal task.definition
    newTask.definition = taskDef;
    return newTask;
  }

  protected resolveTaskForMissingAsconfigField(
    originalTask: vscode.Task,
    taskDef: AnimateTaskDefinition,
    workspaceFolder: vscode.WorkspaceFolder,
    animatePath: string
  ): vscode.Task {
    const jsonUri = vscode.Uri.joinPath(workspaceFolder.uri, ASCONFIG_JSON);
    let isAIR = false;
    const asconfigJson = this.readASConfigJSON(jsonUri);
    if (asconfigJson !== null) {
      isAIR = this.isAIRMobile(asconfigJson) || this.isAIRDesktop(asconfigJson);
    }
    const newTask = this.getAnimateTask(
      originalTask.name,
      jsonUri,
      workspaceFolder,
      animatePath,
      taskDef.debug,
      taskDef.publish,
      isAIR
    );
    // the new task's definition must strictly equal task.definition
    newTask.definition = taskDef;
    return newTask;
  }

  protected provideTasksForASConfigJSON(
    jsonURI: vscode.Uri,
    workspaceFolder: vscode.WorkspaceFolder,
    result: vscode.Task[]
  ) {
    let isAnimate = false;
    let isAIR = false;
    const asconfigJson = this.readASConfigJSON(jsonURI);
    if (asconfigJson !== null) {
      isAnimate = this.isAnimate(asconfigJson);
      isAIR = this.isAIRDesktop(asconfigJson) || this.isAIRMobile(asconfigJson);
    }

    if (!isAnimate) {
      //handled by the ActionScript task provider
      return;
    }

    //compile SWF with Animate
    const animatePath = findAnimate();
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

    const taskNameSuffix = path.basename(flaPath);
    result.push(
      this.getAnimateTask(
        `${TASK_NAME_COMPILE_DEBUG} - ${taskNameSuffix}`,
        jsonURI,
        workspaceFolder,
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
        animatePath,
        false,
        false,
        isAIR
      )
    );
    result.push(
      this.getAnimateTask(
        `${TASK_NAME_PUBLISH_DEBUG} - ${taskNameSuffix}`,
        jsonURI,
        workspaceFolder,
        animatePath,
        true,
        true,
        isAIR
      )
    );
    result.push(
      this.getAnimateTask(
        `${TASK_NAME_PUBLISH_RELEASE} - ${taskNameSuffix}`,
        jsonURI,
        workspaceFolder,
        animatePath,
        false,
        true,
        isAIR
      )
    );
  }

  private getAnimateTask(
    description: string,
    jsonURI: vscode.Uri,
    workspaceFolder: vscode.WorkspaceFolder,
    animatePath: string,
    debug: boolean,
    publish: boolean,
    isAIRProject: boolean
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
    const unpackageANEs = debug && !publish && isAIRProject;
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
    const command = this.getCommand(workspaceFolder);
    if (command.length > 1) {
      options.unshift(...command.slice(1));
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
