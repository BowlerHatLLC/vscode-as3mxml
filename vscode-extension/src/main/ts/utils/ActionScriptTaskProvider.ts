/*
Copyright 2016-2025 Bowler Hat LLC

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
import getFrameworkSDKPathWithFallbacks from "./getFrameworkSDKPathWithFallbacks";
import BaseAsconfigTaskProvider from "./BaseAsconfigTaskProvider";
import validateRoyale from "./validateRoyale";

const ASCONFIG_JSON = "asconfig.json";
const FIELD_AIR_OPTIONS = "airOptions";
const FIELD_TARGET = "target";
const PLATFORM_IOS = "ios";
const PLATFORM_IOS_SIMULATOR = "ios_simulator";
const PLATFORM_ANDROID = "android";
const PLATFORM_AIR = "air";
const PLATFORM_WINDOWS = "windows";
const PLATFORM_MAC = "mac";
const PLATFORM_LINUX = "linux";
const PLATFORM_BUNDLE = "bundle";
const TARGET_AIR = "air";
const TARGET_BUNDLE = "bundle";
const TARGET_NATIVE = "native";
const MATCHER: string[] = [];
const TASK_TYPE_ACTIONSCRIPT = "actionscript";
const TASK_SOURCE_ACTIONSCRIPT = "ActionScript";
const TASK_SOURCE_AIR = "Adobe AIR";
const TASK_NAME_COMPILE_DEBUG = "compile debug";
const TASK_NAME_COMPILE_RELEASE = "compile release";
const TASK_NAME_CLEAN = "clean";
const TASK_NAME_WATCH = "watch";
const TASK_NAME_PACKAGE_IOS_DEBUG = "package iOS debug";
const TASK_NAME_PACKAGE_IOS_RELEASE = "package iOS release";
const TASK_NAME_PACKAGE_IOS_SIMULATOR_DEBUG = "package iOS simulator debug";
const TASK_NAME_PACKAGE_IOS_SIMULATOR_RELEASE = "package iOS simulator release";
const TASK_NAME_PACKAGE_ANDROID_DEBUG = "package Android debug";
const TASK_NAME_PACKAGE_ANDROID_RELEASE = "package Android release";
const TASK_NAME_PACKAGE_DESKTOP_SHARED_DEBUG =
  "package desktop debug (shared runtime)";
const TASK_NAME_PACKAGE_DESKTOP_SHARED_RELEASE =
  "package desktop release (shared runtime)";
const TASK_NAME_PACKAGE_DESKTOP_CAPTIVE =
  "package desktop bundle release (captive runtime)";
const TASK_NAME_PACKAGE_WINDOWS_SHARED_DEBUG =
  "package Windows debug (shared runtime)";
const TASK_NAME_PACKAGE_WINDOWS_SHARED_RELEASE =
  "package Windows release (shared runtime)";
const TASK_NAME_PACKAGE_MAC_SHARED_DEBUG =
  "package macOS debug (shared runtime)";
const TASK_NAME_PACKAGE_MAC_SHARED_RELEASE =
  "package macOS release (shared runtime)";
const TASK_NAME_PACKAGE_LINUX_SHARED_DEBUG =
  "package Linux debug (shared runtime)";
const TASK_NAME_PACKAGE_LINUX_SHARED_RELEASE =
  "package Linux release (shared runtime)";
const TASK_NAME_PACKAGE_WINDOWS_CAPTIVE =
  "package Windows release (captive runtime)";
const TASK_NAME_PACKAGE_MAC_CAPTIVE = "package macOS release (captive runtime)";
const TASK_NAME_PACKAGE_LINUX_CAPTIVE =
  "package Linux release (captive runtime)";
const TASK_NAME_PACKAGE_WINDOWS_NATIVE =
  "package Windows release (native installer)";
const TASK_NAME_PACKAGE_MAC_NATIVE = "package macOS release (native installer)";
const TASK_NAME_PACKAGE_LINUX_NATIVE =
  "package Linux release (native installer)";

interface ActionScriptTaskDefinition extends vscode.TaskDefinition {
  type: typeof TASK_TYPE_ACTIONSCRIPT;
  debug?: boolean;
  air?: string;
  asconfig?: string;
  clean?: boolean;
  watch?: boolean;
}

const WATCH_MINIMUM_APACHE_ROYALE_VERSION = "0.9.10";

export default class ActionScriptTaskProvider
  extends BaseAsconfigTaskProvider
  implements vscode.TaskProvider
{
  resolveTask(
    task: vscode.Task,
    token?: vscode.CancellationToken
  ): vscode.ProviderResult<vscode.Task> {
    if (task.definition.type !== TASK_TYPE_ACTIONSCRIPT) {
      return undefined;
    }
    const taskDef = task.definition as ActionScriptTaskDefinition;
    const frameworkSDK = getFrameworkSDKPathWithFallbacks();
    if (!frameworkSDK) {
      //we don't have a valid SDK
      return undefined;
    }
    if (task.scope === vscode.TaskScope.Workspace) {
      return this.resolveTaskForMultiRootWorkspace(task, taskDef, frameworkSDK);
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
        frameworkSDK
      );
    }
    // nothing could be resolved
    return undefined;
  }

  protected resolveTaskForMultiRootWorkspace(
    originalTask: vscode.Task,
    taskDef: ActionScriptTaskDefinition,
    frameworkSDK: string
  ): vscode.Task | undefined {
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
    let isAIRProject = false;
    const asconfigJson = this.readASConfigJSON(jsonUri);
    if (asconfigJson !== null) {
      isAIRProject =
        this.isAIRMobile(asconfigJson) || this.isAIRDesktop(asconfigJson);
    }
    const result = this.getTask(
      originalTask.name,
      jsonUri,
      workspaceFolder,
      frameworkSDK,
      taskDef.debug,
      taskDef.air,
      isAIRProject
    );
    // the new task's definition must strictly equal task.definition
    result.definition = taskDef;
    return result;
  }

  protected resolveTaskForMissingAsconfigField(
    originalTask: vscode.Task,
    taskDef: ActionScriptTaskDefinition,
    workspaceFolder: vscode.WorkspaceFolder,
    frameworkSDK: string
  ): vscode.Task {
    const jsonUri = vscode.Uri.joinPath(workspaceFolder.uri, ASCONFIG_JSON);
    if (taskDef.clean) {
      const newTask = this.getCleanTask(
        originalTask.name,
        jsonUri,
        workspaceFolder,
        frameworkSDK
      );
      // the new task's definition must strictly equal task.definition
      newTask.definition = taskDef;
      return newTask;
    }
    if (taskDef.watch) {
      const newTask = this.getWatchTask(
        originalTask.name,
        jsonUri,
        workspaceFolder,
        frameworkSDK
      );
      // the new task's definition must strictly equal task.definition
      newTask.definition = taskDef;
      return newTask;
    }
    let isAIRProject = false;
    const asconfigJson = this.readASConfigJSON(jsonUri);
    if (asconfigJson !== null) {
      isAIRProject =
        this.isAIRMobile(asconfigJson) || this.isAIRDesktop(asconfigJson);
    }
    const newTask = this.getTask(
      originalTask.name,
      jsonUri,
      workspaceFolder,
      frameworkSDK,
      taskDef.debug,
      taskDef.air,
      isAIRProject
    );
    // the new task's definition must strictly equal task.definition
    newTask.definition = taskDef;
    return newTask;
  }

  protected provideTasksForASConfigJSON(
    jsonURI: vscode.Uri,
    workspaceFolder: vscode.WorkspaceFolder | undefined,
    result: vscode.Task[]
  ): void {
    if (!workspaceFolder) {
      return;
    }

    let isLibrary = false;
    let isAnimate = false;
    let isAIRMobile = false;
    let isAIRDesktop = false;
    let isSharedOverride = false;
    let isRootTargetShared = false;
    let isRootTargetEmpty = false;
    let isRootTargetBundle = false;
    let isRootTargetNativeInstaller = false;
    let isWindowsOverrideBundle = false;
    let isMacOverrideBundle = false;
    let isLinuxOverrideBundle = false;
    let isWindowsOverrideNativeInstaller = false;
    let isMacOverrideNativeInstaller = false;
    let isLinuxOverrideNativeInstaller = false;
    let isWindowsOverrideShared = false;
    let isMacOverrideShared = false;
    let isLinuxOverrideShared = false;
    const asconfigJson = this.readASConfigJSON(jsonURI);
    if (asconfigJson !== null) {
      isLibrary = this.isLibrary(asconfigJson);
      isAnimate = this.isAnimate(asconfigJson);
      isAIRMobile = this.isAIRMobile(asconfigJson);
      if (!isAIRMobile) {
        isAIRDesktop = this.isAIRDesktop(asconfigJson);
      }
      if (isAIRDesktop) {
        isSharedOverride = this.isSharedOverride(asconfigJson);
        isRootTargetEmpty = this.isRootTargetEmpty(asconfigJson);
        isRootTargetShared = this.isRootTargetShared(asconfigJson);
        isRootTargetBundle = this.isRootTargetBundle(asconfigJson);
        isRootTargetNativeInstaller =
          this.isRootTargetNativeInstaller(asconfigJson);
        isWindowsOverrideShared = this.isWindowsOverrideShared(asconfigJson);
        isMacOverrideShared = this.isMacOverrideShared(asconfigJson);
        isLinuxOverrideShared = this.isLinuxOverrideShared(asconfigJson);
        isWindowsOverrideBundle = this.isWindowsOverrideBundle(asconfigJson);
        isMacOverrideBundle = this.isMacOverrideBundle(asconfigJson);
        isLinuxOverrideBundle = this.isLinuxOverrideBundle(asconfigJson);
        isWindowsOverrideNativeInstaller =
          this.isWindowsOverrideNativeInstaller(asconfigJson);
        isMacOverrideNativeInstaller =
          this.isMacOverrideNativeInstaller(asconfigJson);
        isLinuxOverrideNativeInstaller =
          this.isLinuxOverrideNativeInstaller(asconfigJson);
      }
    }

    const frameworkSDK = getFrameworkSDKPathWithFallbacks();
    if (!frameworkSDK) {
      //we don't have a valid SDK
      return;
    }

    if (isAnimate) {
      //handled by the Animate task provider
      return;
    }

    const taskNameSuffix = this.getTaskNameSuffix(jsonURI, workspaceFolder);

    //compile SWF or Royale JS with asconfigc
    result.push(
      this.getTask(
        `${TASK_NAME_COMPILE_DEBUG} - ${taskNameSuffix}`,
        jsonURI,
        workspaceFolder,
        frameworkSDK,
        true,
        undefined,
        isAIRDesktop || isAIRMobile
      )
    );
    result.push(
      this.getTask(
        `${TASK_NAME_COMPILE_RELEASE} - ${taskNameSuffix}`,
        jsonURI,
        workspaceFolder,
        frameworkSDK,
        false,
        undefined,
        isAIRDesktop || isAIRMobile
      )
    );
    result.push(
      this.getCleanTask(
        `${TASK_NAME_CLEAN} - ${taskNameSuffix}`,
        jsonURI,
        workspaceFolder,
        frameworkSDK
      )
    );

    if (validateRoyale(frameworkSDK, WATCH_MINIMUM_APACHE_ROYALE_VERSION)) {
      result.push(
        this.getWatchTask(
          `${TASK_NAME_WATCH} - ${taskNameSuffix}`,
          jsonURI,
          workspaceFolder,
          frameworkSDK
        )
      );
    }

    if (!isLibrary) {
      //package mobile AIR application
      if (isAIRMobile) {
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_IOS_DEBUG} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            true,
            PLATFORM_IOS,
            isAIRDesktop || isAIRMobile
          )
        );
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_IOS_RELEASE} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            false,
            PLATFORM_IOS,
            isAIRDesktop || isAIRMobile
          )
        );
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_IOS_SIMULATOR_DEBUG} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            true,
            PLATFORM_IOS_SIMULATOR,
            isAIRDesktop || isAIRMobile
          )
        );
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_IOS_SIMULATOR_RELEASE} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            false,
            PLATFORM_IOS_SIMULATOR,
            isAIRDesktop || isAIRMobile
          )
        );
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_ANDROID_DEBUG} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            true,
            PLATFORM_ANDROID,
            isAIRDesktop || isAIRMobile
          )
        );
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_ANDROID_RELEASE} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            false,
            PLATFORM_ANDROID,
            isAIRDesktop || isAIRMobile
          )
        );
      }

      //desktop platform targets are a little trickier because some can only
      //be built on certain platforms. windows can't package for mac, and mac
      //can't package for windows, for instance.

      //if the windows or mac section exists, we need to check its target
      //to determine what to display in the list of tasks.

      //captive runtime
      if (
        isWindowsOverrideBundle ||
        isMacOverrideBundle ||
        isLinuxOverrideBundle
      ) {
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_DESKTOP_CAPTIVE} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            false,
            PLATFORM_BUNDLE,
            isAIRDesktop || isAIRMobile
          )
        );
      }
      if (isWindowsOverrideBundle) {
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_WINDOWS_CAPTIVE} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            false,
            PLATFORM_WINDOWS,
            isAIRDesktop || isAIRMobile
          )
        );
      } else if (isMacOverrideBundle) {
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_MAC_CAPTIVE} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            false,
            PLATFORM_MAC,
            isAIRDesktop || isAIRMobile
          )
        );
      } else if (isLinuxOverrideBundle) {
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_LINUX_CAPTIVE} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            false,
            PLATFORM_LINUX,
            isAIRDesktop || isAIRMobile
          )
        );
      }
      //shared runtime with platform overrides
      else if (isWindowsOverrideShared) {
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_WINDOWS_SHARED_DEBUG} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            true,
            PLATFORM_WINDOWS,
            isAIRDesktop || isAIRMobile
          )
        );
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_WINDOWS_SHARED_RELEASE} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            false,
            PLATFORM_WINDOWS,
            isAIRDesktop || isAIRMobile
          )
        );
      } else if (isMacOverrideShared) {
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_MAC_SHARED_DEBUG} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            true,
            PLATFORM_MAC,
            isAIRDesktop || isAIRMobile
          )
        );
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_MAC_SHARED_RELEASE} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            false,
            PLATFORM_MAC,
            isAIRDesktop || isAIRMobile
          )
        );
      } else if (isLinuxOverrideShared) {
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_LINUX_SHARED_DEBUG} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            true,
            PLATFORM_LINUX,
            isAIRDesktop || isAIRMobile
          )
        );
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_LINUX_SHARED_RELEASE} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            false,
            PLATFORM_LINUX,
            isAIRDesktop || isAIRMobile
          )
        );
      }
      //native installers
      else if (isWindowsOverrideNativeInstaller) {
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_WINDOWS_NATIVE} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            false,
            PLATFORM_WINDOWS,
            isAIRDesktop || isAIRMobile
          )
        );
      } else if (isMacOverrideNativeInstaller) {
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_MAC_NATIVE} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            false,
            PLATFORM_MAC,
            isAIRDesktop || isAIRMobile
          )
        );
      } else if (isLinuxOverrideNativeInstaller) {
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_LINUX_NATIVE} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            false,
            PLATFORM_LINUX,
            isAIRDesktop || isAIRMobile
          )
        );
      }

      //--- root target in airOptions

      //the root target is used if it hasn't been overridden for the current
      //desktop platform. if it is overridden, it should be skipped to avoid
      //duplicate items in the list.
      const isWindows = process.platform === "win32";
      const isMac = process.platform === "darwin";
      const isLinux = process.platform === "linux";

      if (
        isRootTargetNativeInstaller &&
        ((isWindows && !isWindowsOverrideNativeInstaller) ||
          (isMac && !isMacOverrideNativeInstaller) ||
          (isLinux && !isLinuxOverrideNativeInstaller))
      ) {
        let taskName = isWindows
          ? TASK_NAME_PACKAGE_WINDOWS_NATIVE
          : TASK_NAME_PACKAGE_MAC_NATIVE;
        result.push(
          this.getTask(
            `${taskName} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            false,
            PLATFORM_AIR,
            isAIRDesktop || isAIRMobile
          )
        );
      }
      if (
        (isRootTargetBundle || isRootTargetEmpty) &&
        ((isWindows && !isWindowsOverrideBundle) ||
          (isMac && !isMacOverrideBundle) ||
          (isLinux && !isLinuxOverrideBundle))
      ) {
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_DESKTOP_CAPTIVE} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            false,
            PLATFORM_BUNDLE,
            isAIRDesktop || isAIRMobile
          )
        );

        let taskName = isWindows
          ? TASK_NAME_PACKAGE_WINDOWS_CAPTIVE
          : TASK_NAME_PACKAGE_MAC_CAPTIVE;
        let airPlatform = isWindows ? PLATFORM_WINDOWS : PLATFORM_MAC;
        result.push(
          this.getTask(
            `${taskName} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            false,
            airPlatform,
            isAIRDesktop || isAIRMobile
          )
        );
      }
      if (
        (isRootTargetShared || isRootTargetEmpty) &&
        ((isWindows && !isWindowsOverrideShared) ||
          (isMac && !isMacOverrideShared) ||
          (isLinux && !isLinuxOverrideShared))
      ) {
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_DESKTOP_SHARED_DEBUG} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            true,
            PLATFORM_AIR,
            isAIRDesktop || isAIRMobile
          )
        );
        result.push(
          this.getTask(
            `${TASK_NAME_PACKAGE_DESKTOP_SHARED_RELEASE} - ${taskNameSuffix}`,
            jsonURI,
            workspaceFolder,
            frameworkSDK,
            false,
            PLATFORM_AIR,
            isAIRDesktop || isAIRMobile
          )
        );
      }
    }
  }

  private getTaskNameSuffix(
    jsonURI: vscode.Uri,
    workspaceFolder: vscode.WorkspaceFolder
  ): string {
    let suffix = "";
    let workspaceFolders = vscode.workspace.workspaceFolders;
    if (workspaceFolders && workspaceFolders.length > 1) {
      suffix += workspaceFolder.name + "/";
    }
    if (jsonURI) {
      suffix += jsonURI
        .toString()
        .substr(workspaceFolder.uri.toString().length + 1);
    } else {
      suffix += ASCONFIG_JSON;
    }
    return suffix;
  }

  private getTask(
    description: string,
    jsonURI: vscode.Uri,
    workspaceFolder: vscode.WorkspaceFolder,
    sdk: string,
    debug: boolean | undefined,
    airPlatform: string | undefined,
    isAIRProject: boolean
  ): vscode.Task {
    let asconfig: string | undefined = this.getASConfigValue(
      jsonURI,
      workspaceFolder.uri
    );
    let definition: ActionScriptTaskDefinition = {
      type: TASK_TYPE_ACTIONSCRIPT,
      debug,
      asconfig,
    };
    if (airPlatform) {
      definition.air = airPlatform;
    }
    let options = ["--sdk", sdk];
    if (debug) {
      options.push("--debug=true");
    } else {
      options.push("--debug=false");
    }
    if (jsonURI) {
      options.push("--project", jsonURI.fsPath);
    }
    if (airPlatform) {
      options.push("--air", airPlatform);
    }
    if (isAIRProject && debug && !airPlatform) {
      options.push("--unpackage-anes=true");
    }
    if (
      vscode.workspace
        .getConfiguration("as3mxml")
        .get("asconfigc.verboseOutput")
    ) {
      options.push("--verbose=true");
    }
    let jvmargs = vscode.workspace
      .getConfiguration("as3mxml")
      .get("asconfigc.jvmargs");
    if (typeof jvmargs === "string") {
      options.push(`--jvmargs="${jvmargs}"`);
    }
    const command = this.getCommand(workspaceFolder);
    if (command.length > 1) {
      options.unshift(...command.slice(1));
    }
    let source = !airPlatform ? TASK_SOURCE_ACTIONSCRIPT : TASK_SOURCE_AIR;
    let execution = new vscode.ProcessExecution(command[0], options);
    let task = new vscode.Task(
      definition,
      workspaceFolder,
      description,
      source,
      execution,
      MATCHER
    );
    task.group = vscode.TaskGroup.Build;
    return task;
  }

  private getCleanTask(
    description: string,
    jsonURI: vscode.Uri,
    workspaceFolder: vscode.WorkspaceFolder,
    sdk: string
  ): vscode.Task {
    let asconfig: string | undefined = undefined;
    if (jsonURI) {
      let rootJSON = path.resolve(workspaceFolder.uri.fsPath, ASCONFIG_JSON);
      if (rootJSON !== jsonURI.fsPath) {
        //the asconfig field should remain empty if it's the root
        //asconfig.json in the workspace.
        //this is different than TypeScript because we didn't originally
        //create tasks for additional asconfig files in the workspace, and
        //we don't want to break old tasks.json files that already existed
        //before this feature was added.
        //ideally, we'd be able to use resolveTask() to populate the
        //asconfig field, but that function never seems to be called.
        asconfig = jsonURI
          .toString()
          .substr(workspaceFolder.uri.toString().length + 1);
      }
    }
    let definition: ActionScriptTaskDefinition = {
      type: TASK_TYPE_ACTIONSCRIPT,
      asconfig,
      clean: true,
    };
    let options = ["--sdk", sdk, "--clean=true"];
    if (jsonURI) {
      options.push("--project", jsonURI.fsPath);
    }
    if (
      vscode.workspace
        .getConfiguration("as3mxml")
        .get("asconfigc.verboseOutput")
    ) {
      options.push("--verbose=true");
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
      "ActionScript",
      execution,
      MATCHER
    );
    task.group = vscode.TaskGroup.Build;
    return task;
  }

  private getWatchTask(
    description: string,
    jsonURI: vscode.Uri,
    workspaceFolder: vscode.WorkspaceFolder,
    sdk: string
  ): vscode.Task {
    let asconfig: string | undefined = undefined;
    if (jsonURI) {
      let rootJSON = path.resolve(workspaceFolder.uri.fsPath, ASCONFIG_JSON);
      if (rootJSON !== jsonURI.fsPath) {
        //the asconfig field should remain empty if it's the root
        //asconfig.json in the workspace.
        //this is different than TypeScript because we didn't originally
        //create tasks for additional asconfig files in the workspace, and
        //we don't want to break old tasks.json files that already existed
        //before this feature was added.
        //ideally, we'd be able to use resolveTask() to populate the
        //asconfig field, but that function never seems to be called.
        asconfig = jsonURI
          .toString()
          .substr(workspaceFolder.uri.toString().length + 1);
      }
    }
    let definition: ActionScriptTaskDefinition = {
      type: TASK_TYPE_ACTIONSCRIPT,
      asconfig,
      debug: true,
      watch: true,
    };
    let options = ["--sdk", sdk, "--debug=true", "--watch=true"];
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
      "ActionScript",
      execution,
      MATCHER
    );
    task.group = vscode.TaskGroup.Build;
    return task;
  }

  private isWindowsOverrideShared(asconfigJson: any): boolean {
    if (process.platform !== "win32") {
      return false;
    }
    if (!(FIELD_AIR_OPTIONS in asconfigJson)) {
      return false;
    }
    let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
    if (!(PLATFORM_WINDOWS in airOptions)) {
      return false;
    }
    let windows = airOptions[PLATFORM_WINDOWS];
    if (!(FIELD_TARGET in windows)) {
      //if target is omitted, defaults to bundle
      return false;
    }
    let target = windows[FIELD_TARGET];
    return target === TARGET_AIR;
  }

  private isMacOverrideShared(asconfigJson: any): boolean {
    if (process.platform !== "darwin") {
      return false;
    }
    if (!(FIELD_AIR_OPTIONS in asconfigJson)) {
      return false;
    }
    let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
    if (!(PLATFORM_MAC in airOptions)) {
      return false;
    }
    let mac = airOptions[PLATFORM_MAC];
    if (!(FIELD_TARGET in mac)) {
      //if target is omitted, defaults to bundle
      return false;
    }
    let target = mac[FIELD_TARGET];
    return target === TARGET_AIR;
  }

  private isLinuxOverrideShared(asconfigJson: any): boolean {
    if (process.platform !== "linux") {
      return false;
    }
    if (!(FIELD_AIR_OPTIONS in asconfigJson)) {
      return false;
    }
    let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
    if (!(PLATFORM_LINUX in airOptions)) {
      return false;
    }
    let linux = airOptions[PLATFORM_LINUX];
    if (!(FIELD_TARGET in linux)) {
      //if target is omitted, defaults to bundle
      return false;
    }
    let target = linux[FIELD_TARGET];
    return target === TARGET_AIR;
  }

  private isWindowsOverrideNativeInstaller(asconfigJson: any): boolean {
    if (process.platform !== "win32") {
      return false;
    }
    if (!(FIELD_AIR_OPTIONS in asconfigJson)) {
      return false;
    }
    let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
    if (!(PLATFORM_WINDOWS in airOptions)) {
      return false;
    }
    let windows = airOptions[PLATFORM_WINDOWS];
    if (!(FIELD_TARGET in windows)) {
      //if target is omitted, defaults to bundle
      return false;
    }
    let target = windows[FIELD_TARGET];
    return target === TARGET_NATIVE;
  }

  private isMacOverrideNativeInstaller(asconfigJson: any): boolean {
    if (process.platform !== "darwin") {
      return false;
    }
    if (!(FIELD_AIR_OPTIONS in asconfigJson)) {
      return false;
    }
    let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
    if (!(PLATFORM_MAC in airOptions)) {
      return false;
    }
    let mac = airOptions[PLATFORM_MAC];
    if (!(FIELD_TARGET in mac)) {
      //if target is omitted, defaults to bundle
      return false;
    }
    let target = mac[FIELD_TARGET];
    return target === TARGET_NATIVE;
  }

  private isLinuxOverrideNativeInstaller(asconfigJson: any): boolean {
    if (process.platform !== "linux") {
      return false;
    }
    if (!(FIELD_AIR_OPTIONS in asconfigJson)) {
      return false;
    }
    let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
    if (!(PLATFORM_LINUX in airOptions)) {
      return false;
    }
    let linux = airOptions[PLATFORM_LINUX];
    if (!(FIELD_TARGET in linux)) {
      //if target is omitted, defaults to bundle
      return false;
    }
    let target = linux[FIELD_TARGET];
    return target === TARGET_NATIVE;
  }

  private isSharedOverride(asconfigJson: any): boolean {
    if (!(FIELD_AIR_OPTIONS in asconfigJson)) {
      return false;
    }
    let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
    return PLATFORM_AIR in airOptions;
  }

  private isWindowsOverrideBundle(asconfigJson: any): boolean {
    if (process.platform !== "win32") {
      return false;
    }
    if (!(FIELD_AIR_OPTIONS in asconfigJson)) {
      return false;
    }
    let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
    if (!(PLATFORM_WINDOWS in airOptions)) {
      return false;
    }
    let windows = airOptions[PLATFORM_WINDOWS];
    if (!(FIELD_TARGET in windows)) {
      //if target is omitted, default to bundle
      return true;
    }
    let target = windows[FIELD_TARGET];
    return target === TARGET_BUNDLE;
  }

  private isMacOverrideBundle(asconfigJson: any): boolean {
    if (process.platform !== "darwin") {
      return false;
    }
    if (!(FIELD_AIR_OPTIONS in asconfigJson)) {
      return false;
    }
    let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
    if (!(PLATFORM_MAC in airOptions)) {
      return false;
    }
    let mac = airOptions[PLATFORM_MAC];
    if (!(FIELD_TARGET in mac)) {
      //if target is omitted, default to bundle
      return true;
    }
    let target = mac[FIELD_TARGET];
    return target === TARGET_BUNDLE;
  }

  private isLinuxOverrideBundle(asconfigJson: any): boolean {
    if (process.platform !== "linux") {
      return false;
    }
    if (!(FIELD_AIR_OPTIONS in asconfigJson)) {
      return false;
    }
    let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
    if (!(PLATFORM_LINUX in airOptions)) {
      return false;
    }
    let linux = airOptions[PLATFORM_LINUX];
    if (!(FIELD_TARGET in linux)) {
      //if target is omitted, default to bundle
      return true;
    }
    let target = linux[FIELD_TARGET];
    return target === TARGET_BUNDLE;
  }

  private isRootTargetShared(asconfigJson: any): boolean {
    if (!(FIELD_AIR_OPTIONS in asconfigJson)) {
      //if no airOptions are specified at all, consider it shared runtime
      return this.isAIRDesktop(asconfigJson);
    }
    let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
    if (!(FIELD_TARGET in airOptions)) {
      //special case for mobile
      if (this.isAIRMobile(asconfigJson)) {
        return false;
      }
      //if target is omitted, defaults to air/shared
      return true;
    }
    let target = airOptions[FIELD_TARGET];
    return target === TARGET_AIR;
  }

  private isRootTargetEmpty(asconfigJson: any): boolean {
    if (!(FIELD_AIR_OPTIONS in asconfigJson)) {
      //if no airOptions are specified at all, consider it shared runtime
      return this.isAIRDesktop(asconfigJson);
    }
    let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
    return !(FIELD_TARGET in airOptions);
  }

  private isRootTargetBundle(asconfigJson: any): boolean {
    if (!(FIELD_AIR_OPTIONS in asconfigJson)) {
      return false;
    }
    let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
    if (!(FIELD_TARGET in airOptions)) {
      //if target is omitted, defaults to air/shared
      return false;
    }
    let target = airOptions[FIELD_TARGET];
    return target === TARGET_BUNDLE;
  }

  private isRootTargetNativeInstaller(asconfigJson: any): boolean {
    if (!(FIELD_AIR_OPTIONS in asconfigJson)) {
      return false;
    }
    let airOptions = asconfigJson[FIELD_AIR_OPTIONS];
    if (!(FIELD_TARGET in airOptions)) {
      //if target is omitted, defaults to air/shared
      return false;
    }
    let target = airOptions[FIELD_TARGET];
    return target === TARGET_NATIVE;
  }
}
