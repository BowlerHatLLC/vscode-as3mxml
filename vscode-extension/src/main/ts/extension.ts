/*
Copyright 2016-2018 Bowler Hat LLC

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
import organizeImportsInTextEditor from "./commands/organizeImportsInTextEditor";
import findJava from "./utils/findJava";
import validateJava from "./utils/validateJava";
import validateEditorSDK from "./utils/validateEditorSDK";
import ActionScriptSourcePathDataProvider from "./utils/ActionScriptSourcePathDataProvider";
import ActionScriptTaskProvider from "./utils/ActionScriptTaskProvider";
import SWFDebugConfigurationProvider from "./utils/SWFDebugConfigurationProvider";
import SWCTextDocumentContentProvider from "./utils/SWCTextDocumentContentProvider";
import getJavaClassPathDelimiter from "./utils/getJavaClassPathDelimiter";
import findSDKShortName from "./utils/findSDKShortName";
import getFrameworkSDKPathWithFallbacks from "./utils/getFrameworkSDKPathWithFallbacks";
import adapterExecutableCommandSWF from "./commands/adapterExecutableCommandSWF";
import selectWorkspaceSDK from "./commands/selectWorkspaceSDK";
import migrateFlashBuilderProject from "./commands/migrateFlashBuilderProject";
import * as child_process from "child_process";
import * as fs from "fs";
import * as net from "net";
import * as path from "path";
import * as vscode from "vscode";
import {LanguageClient, LanguageClientOptions, SettingMonitor,
	ServerOptions, StreamInfo, ErrorHandler, ErrorAction,
	CloseAction, Executable, ExecutableOptions} from "vscode-languageclient";
import { Message } from "vscode-jsonrpc";
import logCompilerShellOutput from "./commands/logCompilerShellOutput";

const INVALID_SDK_ERROR = "nextgenas.sdk.editor in settings does not point to a valid SDK. Requires Apache Royale 0.9.0 or newer.";
const MISSING_FRAMEWORK_SDK_ERROR = "You must configure an SDK to enable all ActionScript & MXML features.";
const INVALID_JAVA_ERROR = "nextgenas.java in settings does not point to a valid executable. It cannot be a directory, and Java 1.8 or newer is required.";
const MISSING_JAVA_ERROR = "Could not locate valid Java executable. To configure Java manually, use the nextgenas.java setting.";
const MISSING_WORKSPACE_ROOT_ERROR = "Open a folder and create a file named asconfig.json to enable all ActionScript & MXML language features.";
const CANNOT_LAUNCH_QUICK_COMPILE_FAILED_ERROR = "Quick compile failed with errors. Debug launch canceled.";
const QUICK_COMPILE_LANGUAGE_SERVER_NOT_STARTED_ERROR = "Quick compile failed. Try again after ActionScript & MXML extension is initialized.";
const INITIALIZING_MESSAGE = "Initializing ActionScript & MXML language server...";
const QUICK_COMPILE_MESSAGE = "Building ActionScript & MXML project...";
const RESTART_FAIL_MESSAGE = "Failed to restart ActionScript & MXML server. Please reload the window to continue.";
const RELOAD_WINDOW_MESSAGE = "To apply new settings for ActionScript & MXML, please reload the window.";
const RELOAD_WINDOW_BUTTON_LABEL = "Reload Window";
const CONFIGURE_SDK_LABEL = "Configure SDK";
const NO_SDK = "$(alert) No SDK";
let savedContext: vscode.ExtensionContext;
let savedLanguageClient: LanguageClient;
let languageClientStarted: boolean = false;
let bundledCompilerPath: string;
let editorSDKHome: string;
let javaExecutablePath: string;
let frameworkSDKHome: string;
let sdkStatusBarItem: vscode.StatusBarItem;
let sourcePathDataProvider: ActionScriptSourcePathDataProvider = null;
let actionScriptTaskProvider: ActionScriptTaskProvider = null;
let debugConfigurationProvider: SWFDebugConfigurationProvider = null;
let swcTextDocumentContentProvider: SWCTextDocumentContentProvider = null;

function getValidatedEditorSDKConfiguration(javaExecutablePath: string): string
{
	let result = <string> vscode.workspace.getConfiguration("nextgenas").get("sdk.editor");
	//this may return null
	return validateEditorSDK(savedContext.extensionPath, javaExecutablePath, result);
}

function onDidChangeConfiguration(event)
{
	let javaSettingsPath = <string> vscode.workspace.getConfiguration("nextgenas").get("java");
	let newJavaExecutablePath = findJava(javaSettingsPath, (javaPath) =>
	{
		return validateJava(savedContext.extensionPath, javaPath);
	});
	let newEditorSDKHome = getValidatedEditorSDKConfiguration(newJavaExecutablePath);
	let newFrameworkSDKHome = getFrameworkSDKPathWithFallbacks();
	let restarting = false;
	if(editorSDKHome != newEditorSDKHome ||
		javaExecutablePath != newJavaExecutablePath)
	{
		//we're going to try to kill the language server and then restart
		//it with the new settings
		restarting = true;
		restartServer();
	}
	let frameworkChanged = frameworkSDKHome != newFrameworkSDKHome;
	if(editorSDKHome != newEditorSDKHome ||
		frameworkChanged)
	{
		editorSDKHome = newEditorSDKHome;
		frameworkSDKHome = newFrameworkSDKHome;
		updateSDKStatusBarItem();
		if(!savedLanguageClient && !restarting && frameworkChanged)
		{
			restartServer();
		}
	}
}

function updateSDKStatusBarItem()
{
	let sdkShortName = NO_SDK;
	if(frameworkSDKHome)
	{
		sdkShortName = findSDKShortName(frameworkSDKHome);
	}
	sdkStatusBarItem.text = sdkShortName;
}

function restartServer()
{
	if(!savedLanguageClient)
	{
		startClient();
		return;
	}
	let languageClient = savedLanguageClient;
	savedLanguageClient = null;
	languageClientStarted = false;
	languageClient.stop().then(() =>
	{
		startClient();
	}, () =>
	{
		//something went wrong restarting the language server...
		//this shouldn't happen, but if it does, the user can manually
		//restart
		vscode.window.showWarningMessage(RELOAD_WINDOW_MESSAGE, RELOAD_WINDOW_BUTTON_LABEL).then((action) =>
		{
			if(action === RELOAD_WINDOW_BUTTON_LABEL)
			{
				vscode.commands.executeCommand("workbench.action.reloadWindow");
			}
		});
	});
}

export function activate(context: vscode.ExtensionContext)
{
	savedContext = context;
	let javaSettingsPath = <string> vscode.workspace.getConfiguration("nextgenas").get("java");
	javaExecutablePath = findJava(javaSettingsPath, (javaPath) =>
	{
		return validateJava(savedContext.extensionPath, javaPath);
	});
	editorSDKHome = getValidatedEditorSDKConfiguration(javaExecutablePath);
	frameworkSDKHome = getFrameworkSDKPathWithFallbacks();
	vscode.workspace.onDidChangeConfiguration(onDidChangeConfiguration);

	vscode.languages.setLanguageConfiguration("nextgenas",
	{
		//this code is MIT licensed from Microsoft's official TypeScript
		//extension that's built into VSCode
		//https://github.com/Microsoft/vscode/blob/9d611d4dfd5a4a101b5201b8c9e21af97f06e7a7/extensions/typescript/src/typescriptMain.ts#L186
		"onEnterRules":
		[
			{
				beforeText: /^\s*\/\*\*(?!\/)([^\*]|\*(?!\/))*$/,
				afterText: /^\s*\*\/$/,
				action:
				{
					//if you press enter between /** and */ on the same line,
					//it will insert a * on the next line
					indentAction: vscode.IndentAction.IndentOutdent,
					appendText: " * "
				}
			},
			{
				beforeText: /^\s*\/\*\*(?!\/)([^\*]|\*(?!\/))*$/,
				action:
				{
					//if you press enter after /**, when there is no */, it
					//will insert a * on the next line
					indentAction: vscode.IndentAction.None,
					appendText: " * "
				}
			},
			{
				beforeText: /^(\t|(\ \ ))*\ \*(\ ([^\*]|\*(?!\/))*)?$/,
				action:
				{
					//if you press enter on a line with *, it will insert
					//another * on the next line
					indentAction: vscode.IndentAction.None,
					appendText: "* "
				}
			},
			{
				beforeText: /^(\t|(\ \ ))*\ \*\/\s*$/,
				action:
				{
					//removes the extra space if you press enter after a line
					//that contains only */
					indentAction: vscode.IndentAction.None,
					removeText: 1
				}
			},
			{
				beforeText: /^(\t|(\ \ ))*\ \*[^/]*\*\/\s*$/,
				action:
				{
					//removes the extra space if you press enter after a line
					//that starts with * and also has */ at the end
					indentAction: vscode.IndentAction.None,
					removeText: 1
				}
			}
		]
	});

	vscode.commands.registerCommand("nextgenas.adapterExecutableCommandSWF", function(workspaceUri)
	{
		return adapterExecutableCommandSWF(workspaceUri, javaExecutablePath, editorSDKHome, frameworkSDKHome);
	});
	vscode.commands.registerCommand("nextgenas.selectWorkspaceSDK", selectWorkspaceSDK);
	vscode.commands.registerCommand("nextgenas.restartServer", restartServer);
	vscode.commands.registerCommand("nextgenas.logCompilerShellOutput", logCompilerShellOutput);
	vscode.commands.registerCommand("nextgenas.migrateFlashBuilderProject", () =>
	{
		if(vscode.workspace.workspaceFolders)
		{
			migrateFlashBuilderProject(vscode.workspace.workspaceFolders[0].uri);
		}
	});
	vscode.commands.registerCommand("nextgenas.quickCompileAndDebug", () =>
	{
		if(vscode.workspace.workspaceFolders)
		{
			if(!savedLanguageClient || !languageClientStarted)
			{
				vscode.window.showErrorMessage(QUICK_COMPILE_LANGUAGE_SERVER_NOT_STARTED_ERROR);
				return;
			}
			vscode.window.withProgress({location: vscode.ProgressLocation.Window}, (progress) =>
			{
				progress.report({message: QUICK_COMPILE_MESSAGE});
				return new Promise((resolve, reject) =>
				{
					return vscode.commands.executeCommand("nextgenas.quickCompile").then((result) =>
					{
						resolve();

						if(result)
						{
							vscode.commands.executeCommand("workbench.action.debug.start");
						}
						else
						{
							vscode.window.showErrorMessage(CANNOT_LAUNCH_QUICK_COMPILE_FAILED_ERROR);
						}
					}, 
					() =>
					{
						resolve();

						//if the build failed, notify the user that we're not starting
						//a debug session
						vscode.window.showErrorMessage(CANNOT_LAUNCH_QUICK_COMPILE_FAILED_ERROR);
					});
				});
			});
		}
	});
	vscode.commands.registerTextEditorCommand("nextgenas.organizeImportsInTextEditor", organizeImportsInTextEditor);
	
	//don't activate these things unless we're in a workspace
	if(vscode.workspace.workspaceFolders !== undefined)
	{
		sdkStatusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right);
		updateSDKStatusBarItem();
		sdkStatusBarItem.tooltip = "Select ActionScript SDK";
		sdkStatusBarItem.command = "nextgenas.selectWorkspaceSDK";
		sdkStatusBarItem.show();

		let rootPath = vscode.workspace.workspaceFolders[0].uri.fsPath;
		sourcePathDataProvider = new ActionScriptSourcePathDataProvider(rootPath);
		vscode.window.registerTreeDataProvider("actionScriptSourcePaths", sourcePathDataProvider);

		actionScriptTaskProvider = new ActionScriptTaskProvider(context, javaExecutablePath);
		vscode.workspace.registerTaskProvider("actionscript", actionScriptTaskProvider);

		debugConfigurationProvider = new SWFDebugConfigurationProvider();
		vscode.debug.registerDebugConfigurationProvider("swf", debugConfigurationProvider);

		swcTextDocumentContentProvider = new SWCTextDocumentContentProvider();
		vscode.workspace.registerTextDocumentContentProvider("swc", swcTextDocumentContentProvider);
	}
	startClient();
}

export function deactivate()
{
	 savedContext = null;
}

function childExitListener(code)
{
	console.info("Child process exited", code);
	if(code === 0)
	{
		return;
	}
	vscode.window.showErrorMessage("ActionScript & MXML extension exited with error code " + code);
}

function childErrorListener(error)
{
	vscode.window.showErrorMessage("Failed to start ActionScript & MXML extension.");
	console.error("Error connecting to child process.");
	console.error(error);
}

function hasInvalidJava(): boolean
{
	let javaPath = <string> vscode.workspace.getConfiguration("nextgenas").get("java");
	return !javaExecutablePath && javaPath != null;
}

function hasInvalidEditorSDK(): boolean
{
	let sdkPath = <string> vscode.workspace.getConfiguration("nextgenas").get("sdk.editor");
	return !editorSDKHome && sdkPath != null;
}

function showMissingFrameworkSDKError()
{
	vscode.window.showErrorMessage(MISSING_FRAMEWORK_SDK_ERROR, CONFIGURE_SDK_LABEL).then((value: string) =>
	{
		if(value === CONFIGURE_SDK_LABEL)
		{
			selectWorkspaceSDK();
		}
	});
}

function startClient()
{
	if(!savedContext)
	{
		//something very bad happened!
		return;
	}
	if(vscode.workspace.workspaceFolders === undefined)
	{
		vscode.window.showInformationMessage(MISSING_WORKSPACE_ROOT_ERROR,
			{ title: "Help", href: "https://github.com/BowlerHatLLC/vscode-nextgenas/wiki" }
		).then((value) =>
		{
			if(value && value.href)
			{
				let uri = vscode.Uri.parse(value.href);
				vscode.commands.executeCommand("vscode.open", uri);
			}
		});
		return;
	}
	if(hasInvalidJava())
	{
		vscode.window.showErrorMessage(INVALID_JAVA_ERROR);
		return;
	}
	if(!javaExecutablePath)
	{ 
		vscode.window.showErrorMessage(MISSING_JAVA_ERROR);
		return;
	}
	if(hasInvalidEditorSDK())
	{
		vscode.window.showErrorMessage(INVALID_SDK_ERROR);
		return;
	}
	if(!frameworkSDKHome)
	{
		showMissingFrameworkSDKError();
		return;
	}

	vscode.window.withProgress({location: vscode.ProgressLocation.Window}, (progress) =>
	{
		return new Promise((resolve, reject) =>
		{
			progress.report({message: INITIALIZING_MESSAGE});
			let clientOptions: LanguageClientOptions =
			{
				documentSelector:
				[
					"nextgenas",
					"mxml",
				],
				synchronize:
				{
					configurationSection: "nextgenas",
				}
			};
			let cpDelimiter = getJavaClassPathDelimiter();
			let cp = path.resolve(savedContext.extensionPath, "bin", "*");
			if(editorSDKHome)
			{
				//use the nextgenas.sdk.editor configuration
				cp += cpDelimiter +
					//the following jars come from apache royale
					path.resolve(editorSDKHome, "lib", "*") +
					cpDelimiter +
					path.resolve(editorSDKHome, "lib", "external", "*") +
					cpDelimiter +
					path.resolve(editorSDKHome, "js", "lib", "*");
			}
			else
			{
				//use the bundled compiler
				cp += cpDelimiter + path.join(savedContext.asAbsolutePath("./bundled-compiler"), "*");
			}
			let args =
			[
				"-Dfile.encoding=UTF8",
				"-cp",
				cp,
				"com.nextgenactionscript.vscode.Main",
			];
			if(frameworkSDKHome)
			{
				args.unshift("-Droyalelib=" + path.join(frameworkSDKHome, "frameworks"));
			}
			let executable: Executable =
			{
				command: javaExecutablePath,
				args: args,
				options:
				{
					cwd: vscode.workspace.workspaceFolders[0].uri.fsPath
				}
			};
			let options: ExecutableOptions;
			languageClientStarted = false;
			savedLanguageClient = new LanguageClient("nextgenas", "ActionScript & MXML Language Server", executable, clientOptions);
			savedLanguageClient.onReady().then(() =>
			{
				resolve();
				languageClientStarted = true;
				savedLanguageClient.onNotification("nextgenas/logCompilerShellOutput", (notification: string) =>
				{
					logCompilerShellOutput(notification, false, false);
				});
				savedLanguageClient.onNotification("nextgenas/clearCompilerShellOutput", () =>
				{
					logCompilerShellOutput(null, false, true);
				});
			});
			let disposable = savedLanguageClient.start();
			savedContext.subscriptions.push(disposable);
		});
	});
}