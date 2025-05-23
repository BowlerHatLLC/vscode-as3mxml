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
import * as fs from "fs";
import * as path from "path";
import * as vscode from "vscode";
import {
  Executable,
  LanguageClient,
  LanguageClientOptions,
} from "vscode-languageclient/node";
import { createNewProject } from "./commands/createNewProject";
import {
  checkForProjectsToImport,
  pickProjectInWorkspace,
} from "./commands/importProject";
import logCompilerShellOutput from "./commands/logCompilerShellOutput";
import quickCompileAndLaunch from "./commands/quickCompileAndLaunch";
import saveSessionPassword from "./commands/saveSessionPassword";
import selectWorkspaceSDK from "./commands/selectWorkspaceSDK";
import ActionScriptSourcePathDataProvider from "./utils/ActionScriptSourcePathDataProvider";
import ActionScriptTaskProvider from "./utils/ActionScriptTaskProvider";
import AnimateTaskProvider from "./utils/AnimateTaskProvider";
import SWCTextDocumentContentProvider from "./utils/SWCTextDocumentContentProvider";
import createActionScriptSDKStatusBarItem from "./utils/createActionScriptSDKStatusBarItem";
import createRoyaleTargetStatusBarItem from "./utils/createRoyaleTargetStatusBarItem";
import findJava from "./utils/findJava";
import findSDKShortName from "./utils/findSDKShortName";
import getFrameworkSDKPathWithFallbacks from "./utils/getFrameworkSDKPathWithFallbacks";
import getJavaClassPathDelimiter from "./utils/getJavaClassPathDelimiter";
import normalizeUri from "./utils/normalizeUri";
import validateEditorSDK from "./utils/validateEditorSDK";
import validateJava from "./utils/validateJava";
import selectRoyalePreferredTarget from "./commands/selectRoyalePreferredTarget";
import getRoyalePreferredTarget from "./utils/getRoyalePreferredTarget";
import validateRoyale from "./utils/validateRoyale";

const MINIMUM_APACHE_ROYALE_VERSION = "0.9.12";
const MINIMUM_JDK_VERSION = "11";
const INVALID_SDK_ERROR = `as3mxml.sdk.editor in settings does not point to a valid SDK. Requires Apache Royale ${MINIMUM_APACHE_ROYALE_VERSION} or newer.`;
const INVALID_JAVA_ERROR = `as3mxml.java.path in settings does not point to a valid executable. It cannot be a directory, and Java JDK ${MINIMUM_JDK_VERSION} or newer is required.`;
const MISSING_JAVA_ERROR = `Could not locate valid Java executable. Java JDK ${MINIMUM_JDK_VERSION} or newer is required. To configure Java manually, use the as3mxml.java.path setting.`;
const INITIALIZING_MESSAGE =
  "Initializing ActionScript & MXML language server...";
const RELOAD_WINDOW_MESSAGE =
  "To apply new settings for ActionScript & MXML, please reload the window.";
const RELOAD_WINDOW_BUTTON_LABEL = "Reload Window";
const STARTUP_ERROR = "The ActionScript & MXML extension failed to start.";
const QUICK_COMPILE_AND_DEBUG_INIT_MESSAGE =
  "Quick Compile & Debug is waiting for initialization...";
const QUICK_COMPILE_AND_RUN_INIT_MESSAGE =
  "Quick Compile & Run is waiting for initialization...";
const NO_SDK = "$(alert) No SDK";
const FILE_EXTENSION_AS = ".as";
const FILE_EXTENSION_MXML = ".mxml";
const FILE_NAME_ASCONFIG_JSON = "asconfig.json";
let savedContext: vscode.ExtensionContext | null;
let savedLanguageClient: LanguageClient | null;
let isLanguageClientReady = false;
let editorSDKHome: string | null;
let javaExecutablePath: string | null;
let frameworkSDKHome: string | null;
let sdkStatusBarItem: vscode.StatusBarItem;
let royaleTargetStatusBarItem: vscode.StatusBarItem;
let pendingQuickCompileAndDebug = false;
let pendingQuickCompileAndRun = false;
let as3mxmlCodeIntelligenceReady = false;
let actionScriptTaskProvider: ActionScriptTaskProvider;
let animateTaskProvider: AnimateTaskProvider;

function getValidatedEditorSDKConfiguration(
  javaExecutablePath: string | null
): string | null {
  if (!savedContext) {
    return null;
  }
  let result = vscode.workspace
    .getConfiguration("as3mxml")
    .get("sdk.editor") as string;
  //this may return null
  return validateEditorSDK(
    savedContext.extensionPath,
    javaExecutablePath,
    result
  );
}

function onDidChangeVisibleTextEditors() {
  refreshSDKStatusBarItemVisibility();
  refreshRoyaleTargetStatusBarItemVisibility();
}

function onDidChangeConfiguration(event: vscode.ConfigurationChangeEvent) {
  let needsSDKUpdate = false;
  let needsRestart = false;
  if (
    event.affectsConfiguration("as3mxml.java.path") ||
    event.affectsConfiguration("as3mxml.sdk.editor") ||
    event.affectsConfiguration("as3mxml.languageServer.enabled") ||
    event.affectsConfiguration("as3mxml.languageServer.jvmargs")
  ) {
    //we're going to try to kill the language server and then restart
    //it with the new settings
    const javaSettingsPath = vscode.workspace
      .getConfiguration("as3mxml")
      .get("java.path") as string;
    javaExecutablePath = findJava(javaSettingsPath, (javaPath) => {
      if (!savedContext) {
        return false;
      }
      return validateJava(savedContext.extensionPath, javaPath);
    });
    actionScriptTaskProvider.javaExecutablePath = javaExecutablePath;
    animateTaskProvider.javaExecutablePath = javaExecutablePath;
    editorSDKHome = getValidatedEditorSDKConfiguration(javaExecutablePath);
    needsSDKUpdate = true;
    needsRestart = true;
  }

  if (needsSDKUpdate || event.affectsConfiguration("as3mxml.sdk.framework")) {
    frameworkSDKHome = getFrameworkSDKPathWithFallbacks();
    if (!frameworkSDKHome) {
      let explicitFrameworkSetting = vscode.workspace
        .getConfiguration("as3mxml")
        .get("sdk.framework") as string;
      needsRestart = !explicitFrameworkSetting;
    }
    needsSDKUpdate = true;
  }

  if (needsSDKUpdate) {
    updateSDKStatusBarItem();
  }

  if (
    event.affectsConfiguration("as3mxml.sdk.framework") ||
    event.affectsConfiguration("as3mxml.sdk.editor")
  ) {
    refreshRoyaleTargetStatusBarItemVisibility();
  }

  if (needsRestart) {
    restartServer();
  }
}

function updateSDKStatusBarItem() {
  if (frameworkSDKHome) {
    sdkStatusBarItem.text = findSDKShortName(frameworkSDKHome) ?? NO_SDK;
    sdkStatusBarItem.tooltip = frameworkSDKHome ?? undefined;
  } else {
    sdkStatusBarItem.text = NO_SDK;
    sdkStatusBarItem.tooltip = undefined;
  }
}

function refreshSDKStatusBarItemVisibility() {
  let showStatusBarItem = as3mxmlCodeIntelligenceReady;
  if (!showStatusBarItem) {
    const textEditor = vscode.window.visibleTextEditors.find((textEditor) => {
      var fileName = textEditor.document.fileName;
      fileName = path.basename(fileName);
      return (
        fileName.endsWith(FILE_EXTENSION_AS) ||
        fileName.endsWith(FILE_EXTENSION_MXML) ||
        fileName === FILE_NAME_ASCONFIG_JSON ||
        /^asconfig\.\w+\.json$/.test(fileName)
      );
    });
    if (textEditor) {
      showStatusBarItem = true;
    }
  }
  if (showStatusBarItem) {
    sdkStatusBarItem.show();
  } else {
    sdkStatusBarItem.hide();
  }
}

function updateRoyaleTargetStatusBarItem() {
  royaleTargetStatusBarItem.text = getRoyalePreferredTarget(savedContext);
}

function refreshRoyaleTargetStatusBarItemVisibility() {
  let showStatusBarItem = false;
  // show for Royale projects only
  if (validateRoyale(getFrameworkSDKPathWithFallbacks())) {
    showStatusBarItem = as3mxmlCodeIntelligenceReady;
    if (!showStatusBarItem) {
      const textEditor = vscode.window.visibleTextEditors.find((textEditor) => {
        var fileName = textEditor.document.fileName;
        fileName = path.basename(fileName);
        return (
          fileName.endsWith(FILE_EXTENSION_AS) ||
          fileName.endsWith(FILE_EXTENSION_MXML) ||
          fileName === FILE_NAME_ASCONFIG_JSON ||
          /^asconfig\.\w+\.json$/.test(fileName)
        );
      });
      if (textEditor) {
        showStatusBarItem = true;
      }
    }
  }
  if (showStatusBarItem) {
    royaleTargetStatusBarItem.show();
  } else {
    royaleTargetStatusBarItem.hide();
  }
}

function restartServer() {
  if (!savedLanguageClient) {
    startClient();
    return;
  }
  let languageClient = savedLanguageClient;
  savedLanguageClient = null;
  isLanguageClientReady = false;
  as3mxmlCodeIntelligenceReady = false;
  vscode.commands.executeCommand(
    "setContext",
    "as3mxml.codeIntelligenceReady",
    as3mxmlCodeIntelligenceReady
  );
  languageClient.stop().then(
    () => {
      startClient();
    },
    () => {
      //something went wrong restarting the language server...
      //this shouldn't happen, but if it does, the user can manually
      //restart
      vscode.window
        .showWarningMessage(RELOAD_WINDOW_MESSAGE, RELOAD_WINDOW_BUTTON_LABEL)
        .then((action) => {
          if (action === RELOAD_WINDOW_BUTTON_LABEL) {
            vscode.commands.executeCommand("workbench.action.reloadWindow");
          }
        });
    }
  );
}

export function activate(context: vscode.ExtensionContext) {
  savedContext = context;
  checkForProjectsToImport();
  let javaSettingsPath = vscode.workspace
    .getConfiguration("as3mxml")
    .get("java.path") as string;
  javaExecutablePath = findJava(javaSettingsPath, (javaPath) => {
    return validateJava(context.extensionPath, javaPath);
  });
  editorSDKHome = getValidatedEditorSDKConfiguration(javaExecutablePath);
  frameworkSDKHome = getFrameworkSDKPathWithFallbacks();
  context.subscriptions.push(
    vscode.workspace.onDidChangeConfiguration(onDidChangeConfiguration)
  );
  context.subscriptions.push(
    vscode.window.onDidChangeVisibleTextEditors(onDidChangeVisibleTextEditors)
  );
  context.subscriptions.push(
    vscode.workspace.onDidSaveTextDocument((textDocument) => {
      if (!savedLanguageClient || !isLanguageClientReady) {
        return;
      }
      const fileName = path.basename(textDocument.fileName);
      if (fileName !== FILE_NAME_ASCONFIG_JSON) {
        return;
      }
      updateRoyaleTargetStatusBarItem();
      vscode.commands.executeCommand(
        "as3mxml.setRoyalePreferredTarget",
        getRoyalePreferredTarget(context)
      );
    })
  );

  context.subscriptions.push(
    vscode.languages.setLanguageConfiguration("actionscript", {
      //this code is MIT licensed from Microsoft's official TypeScript
      //extension that's built into VSCode
      //https://github.com/Microsoft/vscode/blob/9d611d4dfd5a4a101b5201b8c9e21af97f06e7a7/extensions/typescript/src/typescriptMain.ts#L186
      onEnterRules: [
        {
          beforeText: /^\s*\/\*\*(?!\/)([^\*]|\*(?!\/))*$/,
          afterText: /^\s*\*\/$/,
          action: {
            //if you press enter between /** and */ on the same line,
            //it will insert a * on the next line
            indentAction: vscode.IndentAction.IndentOutdent,
            appendText: " * ",
          },
        },
        {
          beforeText: /^\s*\/\*\*(?!\/)([^\*]|\*(?!\/))*$/,
          action: {
            //if you press enter after /**, when there is no */, it
            //will insert a * on the next line
            indentAction: vscode.IndentAction.None,
            appendText: " * ",
          },
        },
        {
          beforeText: /^(\t|(\ \ ))*\ \*(\ ([^\*]|\*(?!\/))*)?$/,
          action: {
            //if you press enter on a line with *, it will insert
            //another * on the next line
            indentAction: vscode.IndentAction.None,
            appendText: "* ",
          },
        },
        {
          beforeText: /^(\t|(\ \ ))*\ \*\/\s*$/,
          action: {
            //removes the extra space if you press enter after a line
            //that contains only */
            indentAction: vscode.IndentAction.None,
            removeText: 1,
          },
        },
        {
          beforeText: /^(\t|(\ \ ))*\ \*[^/]*\*\/\s*$/,
          action: {
            //removes the extra space if you press enter after a line
            //that starts with * and also has */ at the end
            indentAction: vscode.IndentAction.None,
            removeText: 1,
          },
        },
      ],
    })
  );

  context.subscriptions.push(
    vscode.commands.registerCommand(
      "as3mxml.createNewProject",
      createNewProject
    )
  );
  context.subscriptions.push(
    vscode.commands.registerCommand(
      "as3mxml.selectWorkspaceSDK",
      selectWorkspaceSDK
    )
  );
  context.subscriptions.push(
    vscode.commands.registerCommand(
      "as3mxml.selectRoyalePreferredTarget",
      async () => {
        await selectRoyalePreferredTarget(context);
        updateRoyaleTargetStatusBarItem();
        vscode.commands.executeCommand(
          "as3mxml.setRoyalePreferredTarget",
          getRoyalePreferredTarget(savedContext)
        );
      }
    )
  );
  context.subscriptions.push(
    vscode.commands.registerCommand("as3mxml.restartServer", restartServer)
  );
  context.subscriptions.push(
    vscode.commands.registerCommand(
      "as3mxml.logCompilerShellOutput",
      logCompilerShellOutput
    )
  );
  context.subscriptions.push(
    vscode.commands.registerCommand(
      "as3mxml.saveSessionPassword",
      saveSessionPassword
    )
  );
  context.subscriptions.push(
    vscode.commands.registerCommand("as3mxml.importFlashBuilderProject", () => {
      pickProjectInWorkspace(true, false);
    })
  );
  context.subscriptions.push(
    vscode.commands.registerCommand("as3mxml.importFlashDevelopProject", () => {
      pickProjectInWorkspace(false, true);
    })
  );
  context.subscriptions.push(
    vscode.commands.registerCommand("as3mxml.quickCompileAndDebug", () => {
      let enabled = vscode.workspace
        .getConfiguration("as3mxml")
        .get("quickCompile.enabled") as boolean;
      if (!enabled) {
        return;
      }
      vscode.commands.getCommands(true).then((commands) => {
        if (
          commands.some((command) => command === "as3mxml.getActiveProjectURIs")
        ) {
          vscode.commands
            .executeCommand<string[] | undefined>(
              "as3mxml.getActiveProjectURIs",
              true
            )
            .then((uris: string[] | undefined) => {
              if (!uris || uris.length === 0) {
                //no projects with asconfig.json files
                return;
              }
              quickCompileAndLaunch(uris, true);
            });
        } else if (!savedLanguageClient || !isLanguageClientReady) {
          if (
            vscode.workspace.workspaceFolders === undefined ||
            !vscode.workspace.workspaceFolders.some((workspaceFolder) =>
              fs.existsSync(
                path.resolve(
                  workspaceFolder.uri.fsPath,
                  FILE_NAME_ASCONFIG_JSON
                )
              )
            )
          ) {
            //skip the message if there aren't any workspace folders
            //that contain asconfig.json
            pendingQuickCompileAndDebug = false;
            pendingQuickCompileAndRun = false;
            return;
          }
          pendingQuickCompileAndDebug = true;
          pendingQuickCompileAndRun = false;
          logCompilerShellOutput(
            QUICK_COMPILE_AND_DEBUG_INIT_MESSAGE,
            true,
            false
          );
        }
      });
    })
  );
  context.subscriptions.push(
    vscode.commands.registerCommand("as3mxml.quickCompileAndRun", () => {
      let enabled = vscode.workspace
        .getConfiguration("as3mxml")
        .get("quickCompile.enabled") as boolean;
      if (!enabled) {
        return;
      }
      vscode.commands.getCommands(true).then((commands) => {
        if (
          commands.some((command) => command === "as3mxml.getActiveProjectURIs")
        ) {
          vscode.commands
            .executeCommand<string[] | undefined>(
              "as3mxml.getActiveProjectURIs",
              true
            )
            .then((uris: string[] | undefined) => {
              if (!uris || uris.length === 0) {
                //no projects with asconfig.json files
                return;
              }
              quickCompileAndLaunch(uris, false);
            });
        } else if (!savedLanguageClient || !isLanguageClientReady) {
          if (
            vscode.workspace.workspaceFolders === undefined ||
            !vscode.workspace.workspaceFolders.some((workspaceFolder) =>
              fs.existsSync(
                path.resolve(
                  workspaceFolder.uri.fsPath,
                  FILE_NAME_ASCONFIG_JSON
                )
              )
            )
          ) {
            //skip the message if there aren't any workspace folders
            //that contain asconfig.json
            pendingQuickCompileAndDebug = false;
            pendingQuickCompileAndRun = false;
            return;
          }
          pendingQuickCompileAndRun = true;
          pendingQuickCompileAndDebug = false;
          logCompilerShellOutput(
            QUICK_COMPILE_AND_RUN_INIT_MESSAGE,
            true,
            false
          );
          return;
        }
      });
    })
  );

  //don't activate these things unless we're in a workspace
  if (vscode.workspace.workspaceFolders !== undefined) {
    context.subscriptions.push(
      vscode.window.createTreeView("actionScriptSourcePaths", {
        treeDataProvider: new ActionScriptSourcePathDataProvider(),
      })
    );
  }

  sdkStatusBarItem = createActionScriptSDKStatusBarItem();
  updateSDKStatusBarItem();
  refreshSDKStatusBarItemVisibility();
  royaleTargetStatusBarItem = createRoyaleTargetStatusBarItem();
  updateRoyaleTargetStatusBarItem();
  refreshRoyaleTargetStatusBarItemVisibility();

  actionScriptTaskProvider = new ActionScriptTaskProvider(
    context,
    javaExecutablePath
  );
  context.subscriptions.push(
    vscode.tasks.registerTaskProvider("actionscript", actionScriptTaskProvider)
  );

  animateTaskProvider = new AnimateTaskProvider(context, javaExecutablePath);
  context.subscriptions.push(
    vscode.tasks.registerTaskProvider("animate", animateTaskProvider)
  );

  context.subscriptions.push(
    vscode.workspace.registerTextDocumentContentProvider(
      "swc",
      new SWCTextDocumentContentProvider()
    )
  );

  startClient();

  //this is the public API of the extension that may be accessed from other
  //extensions.
  return {
    /**
     * Indicates if the connection between the language client and the
     * language server has initialized.
     */
    get isLanguageClientReady(): boolean {
      return isLanguageClientReady;
    },

    /**
     * The absolute file path of the currently selected framework SDK.
     * May be null or undefined if no SDK is selected or the currently
     * selected SDK is invalid.
     */
    get frameworkSDKPath(): string | null {
      return frameworkSDKHome;
    },
  };
}

export function deactivate() {
  savedLanguageClient = null;
  savedContext = null;
}

function hasInvalidJava(): boolean {
  let javaPath = vscode.workspace
    .getConfiguration("as3mxml")
    .get("java.path") as string;
  return !javaExecutablePath && javaPath != null;
}

function hasInvalidEditorSDK(): boolean {
  let sdkPath = vscode.workspace
    .getConfiguration("as3mxml")
    .get("sdk.editor") as string;
  return !editorSDKHome && sdkPath != null;
}

function startClient() {
  if (!savedContext) {
    //something very bad happened!
    return;
  }
  if (
    !vscode.workspace
      .getConfiguration("as3mxml")
      .get("languageServer.enabled") as boolean
  ) {
    return;
  }

  if (!javaExecutablePath || hasInvalidJava()) {
    vscode.window.showErrorMessage(INVALID_JAVA_ERROR);
    return;
  }
  if (!javaExecutablePath) {
    vscode.window.showErrorMessage(MISSING_JAVA_ERROR);
    return;
  }
  if (hasInvalidEditorSDK()) {
    vscode.window.showErrorMessage(INVALID_SDK_ERROR);
    return;
  }

  vscode.window.withProgress(
    { location: vscode.ProgressLocation.Window },
    (progress) => {
      return new Promise<void>(async (resolve, reject) => {
        if (!savedContext || !javaExecutablePath) {
          resolve();
          vscode.window.showErrorMessage(STARTUP_ERROR);
          return;
        }
        progress.report({ message: INITIALIZING_MESSAGE });
        let clientOptions: LanguageClientOptions = {
          documentSelector: [
            { scheme: "file", language: "actionscript" },
            { scheme: "file", language: "mxml" },
            { scheme: "file", language: "css" },
          ],
          synchronize: {
            configurationSection: "as3mxml",
          },
          uriConverters: {
            code2Protocol: (value: vscode.Uri) => {
              return normalizeUri(value);
            },
            //this is just the default behavior, but we need to define both
            protocol2Code: (value) => vscode.Uri.parse(value),
          },
          initializationOptions: {
            preferredRoyaleTarget: getRoyalePreferredTarget(savedContext),
            notifyActiveProject: true,
          },
        };
        let cpDelimiter = getJavaClassPathDelimiter();
        let cp = path.resolve(savedContext.extensionPath, "bin", "*");
        if (editorSDKHome) {
          //use the as3mxml.sdk.editor configuration
          cp +=
            cpDelimiter +
            //the following jars come from apache royale
            path.resolve(editorSDKHome, "lib", "*") +
            cpDelimiter +
            path.resolve(editorSDKHome, "lib", "external", "*") +
            cpDelimiter +
            path.resolve(editorSDKHome, "js", "lib", "*");
        } else {
          //use the bundled compiler
          cp +=
            cpDelimiter +
            path.join(savedContext.asAbsolutePath("./bundled-compiler"), "*");
        }
        let args = [
          "-Dfile.encoding=UTF8",
          "-cp",
          cp,
          "com.as3mxml.vscode.Main",
        ];
        if (frameworkSDKHome) {
          args.unshift(
            "-Droyalelib=" + path.join(frameworkSDKHome, "frameworks")
          );
        }
        if (process.platform === "darwin") {
          args.unshift("-Dapple.awt.UIElement=true");
        }
        let jvmargsString = vscode.workspace
          .getConfiguration("as3mxml")
          .get("languageServer.jvmargs") as string;
        if (jvmargsString) {
          let jvmargs = jvmargsString.split(" ");
          args.unshift(...jvmargs);
        }
        // if JDK 11 or newer is ever required, it's probably a good idea to
        // add the following option:
        // args.unshift("-Xlog:all=warning:stderr")
        let primaryWorkspaceFolder: vscode.WorkspaceFolder | undefined;
        if (vscode.workspace.workspaceFolders !== undefined) {
          primaryWorkspaceFolder = vscode.workspace.workspaceFolders[0];
        }
        //uncomment to allow a debugger to attach to the language server
        //args.unshift("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005,quiet=y");
        let executable: Executable = {
          command: javaExecutablePath,
          args: args,
          options: {
            cwd: primaryWorkspaceFolder
              ? primaryWorkspaceFolder.uri.fsPath
              : undefined,
          },
        };
        as3mxmlCodeIntelligenceReady = false;
        vscode.commands.executeCommand(
          "setContext",
          "as3mxml.codeIntelligenceReady",
          as3mxmlCodeIntelligenceReady
        );
        isLanguageClientReady = false;
        // NOTE: isLanguageClientReady and as3mxmlCodeIntelligenceReady mean
        // different things.
        // isLanguageClientReady is true once the language server has started,
        // and is communicating with the extension, but that doesn't mean any
        // projects have been found. in that case, as3mxmlCodeIntelligenceReady
        // will still be false. if a valid asconfig.json file is created, the
        // language server will activate a project, and
        // as3mxmlCodeIntelligenceReady will be changed to true.
        savedLanguageClient = new LanguageClient(
          "actionscript",
          "ActionScript & MXML Language Server",
          executable,
          clientOptions
        );
        savedLanguageClient.onNotification(
          "as3mxml/logCompilerShellOutput",
          (notification: string) => {
            logCompilerShellOutput(notification, false, false);
          }
        );
        savedLanguageClient.onNotification(
          "as3mxml/clearCompilerShellOutput",
          () => {
            logCompilerShellOutput(null, false, true);
          }
        );
        savedLanguageClient.onNotification(
          "as3mxml/setActionScriptActive",
          () => {
            as3mxmlCodeIntelligenceReady = true;
            vscode.commands.executeCommand(
              "setContext",
              "as3mxml.codeIntelligenceReady",
              as3mxmlCodeIntelligenceReady
            );
            refreshSDKStatusBarItemVisibility();
            refreshRoyaleTargetStatusBarItemVisibility();
          }
        );

        try {
          await savedLanguageClient.start();
        } catch (e) {
          resolve();
          vscode.window.showErrorMessage(STARTUP_ERROR);
          return;
        }

        resolve();
        isLanguageClientReady = true;
        vscode.commands.executeCommand(
          "as3mxml.setRoyalePreferredTarget",
          getRoyalePreferredTarget(savedContext)
        );
        if (pendingQuickCompileAndDebug) {
          vscode.commands.executeCommand("as3mxml.quickCompileAndDebug");
        } else if (pendingQuickCompileAndRun) {
          vscode.commands.executeCommand("as3mxml.quickCompileAndRun");
        }
        pendingQuickCompileAndDebug = false;
        pendingQuickCompileAndRun = false;
      });
    }
  );
}
