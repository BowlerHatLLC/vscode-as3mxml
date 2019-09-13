/*
Copyright 2016-2019 Bowler Hat LLC

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
import findJava from "./utils/findJava";
import validateJava from "./utils/validateJava";
import validateEditorSDK from "./utils/validateEditorSDK";
import ActionScriptSourcePathDataProvider, { ActionScriptSourcePath } from "./utils/ActionScriptSourcePathDataProvider";
import ActionScriptTaskProvider from "./utils/ActionScriptTaskProvider";
import SWFDebugConfigurationProvider from "./utils/SWFDebugConfigurationProvider";
import SWCTextDocumentContentProvider from "./utils/SWCTextDocumentContentProvider";
import getJavaClassPathDelimiter from "./utils/getJavaClassPathDelimiter";
import findSDKShortName from "./utils/findSDKShortName";
import getFrameworkSDKPathWithFallbacks from "./utils/getFrameworkSDKPathWithFallbacks";
import selectWorkspaceSDK from "./commands/selectWorkspaceSDK";
import { pickFlashBuilderProjectInWorkspace, checkForFlashBuilderProjectsToImport } from "./commands/importFlashBuilderProject";
import * as path from "path";
import * as vscode from "vscode";
import {LanguageClient, LanguageClientOptions, Executable, ExecutableOptions} from "vscode-languageclient";
import logCompilerShellOutput from "./commands/logCompilerShellOutput";
import quickCompileAndLaunch from "./commands/quickCompileAndLaunch";
import migrateSettings from "./utils/migrateSettings";
import SWFDebugAdapterDescriptorFactory from "./utils/SWFDebugAdapterDescriptorFactory";
import saveSessionPassword from "./commands/saveSessionPassword";

const INVALID_SDK_ERROR = "as3mxml.sdk.editor in settings does not point to a valid SDK. Requires Apache Royale 0.9.6 or newer.";
const MISSING_FRAMEWORK_SDK_ERROR = "You must configure an SDK to enable all ActionScript & MXML features.";
const INVALID_JAVA_ERROR = "as3mxml.java.path in settings does not point to a valid executable. It cannot be a directory, and Java 1.8 or newer is required.";
const MISSING_JAVA_ERROR = "Could not locate valid Java executable. To configure Java manually, use the as3mxml.java.path setting.";
const INITIALIZING_MESSAGE = "Initializing ActionScript & MXML language server...";
const RELOAD_WINDOW_MESSAGE = "To apply new settings for ActionScript & MXML, please reload the window.";
const RELOAD_WINDOW_BUTTON_LABEL = "Reload Window";
const CONFIGURE_SDK_LABEL = "Configure SDK";
const STARTUP_ERROR = "The ActionScript & MXML extension failed to start.";
const QUICK_COMPILE_AND_DEBUG_INIT_MESSAGE = "Quick Compile & Debug is waiting for initialization...";
const QUICK_COMPILE_AND_RUN_INIT_MESSAGE = "Quick Compile & Run is waiting for initialization...";
const NO_SDK = "$(alert) No SDK";
let savedContext: vscode.ExtensionContext;
let savedLanguageClient: LanguageClient;
let isLanguageClientReady = false;
let editorSDKHome: string;
let javaExecutablePath: string;
let frameworkSDKHome: string;
let sdkStatusBarItem: vscode.StatusBarItem;
let sourcePathView: vscode.TreeView<ActionScriptSourcePath> = null;
let sourcePathDataProvider: ActionScriptSourcePathDataProvider = null;
let actionScriptTaskProvider: ActionScriptTaskProvider = null;
let debugConfigurationProvider: SWFDebugConfigurationProvider = null;
let swcTextDocumentContentProvider: SWCTextDocumentContentProvider = null;
let swfDebugAdapterDescriptorFactory: SWFDebugAdapterDescriptorFactory = null;
let pendingQuickCompileAndDebug = false;
let pendingQuickCompileAndRun = false;

function getValidatedEditorSDKConfiguration(javaExecutablePath: string): string
{
	let result = vscode.workspace.getConfiguration("as3mxml").get("sdk.editor") as string;
	//this may return null
	return validateEditorSDK(savedContext.extensionPath, javaExecutablePath, result);
}

function onDidChangeConfiguration(event: vscode.ConfigurationChangeEvent)
{
	let javaSettingsPath = vscode.workspace.getConfiguration("as3mxml").get("java.path") as string;
	let newJavaExecutablePath = findJava(javaSettingsPath, (javaPath) =>
	{
		return validateJava(savedContext.extensionPath, javaPath);
	});
	let newEditorSDKHome = getValidatedEditorSDKConfiguration(newJavaExecutablePath);
	let newFrameworkSDKHome = getFrameworkSDKPathWithFallbacks();
	let explicitFrameworkSetting = vscode.workspace.getConfiguration("as3mxml").get("sdk.framework") as string;
	let frameworkChanged = frameworkSDKHome != newFrameworkSDKHome;
	let restarting = false;
	if(event.affectsConfiguration("as3mxml.java.path") ||
		event.affectsConfiguration("as3mxml.sdk.editor") ||
		(frameworkChanged && !explicitFrameworkSetting))
	{
		//we're going to try to kill the language server and then restart
		//it with the new settings
		restarting = true;
		restartServer();
	}
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
	isLanguageClientReady = false;
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
	migrateSettings();
	checkForFlashBuilderProjectsToImport();
	let javaSettingsPath = vscode.workspace.getConfiguration("as3mxml").get("java.path") as string;
	javaExecutablePath = findJava(javaSettingsPath, (javaPath) =>
	{
		return validateJava(savedContext.extensionPath, javaPath);
	});
	editorSDKHome = getValidatedEditorSDKConfiguration(javaExecutablePath);
	frameworkSDKHome = getFrameworkSDKPathWithFallbacks();
	vscode.workspace.onDidChangeConfiguration(onDidChangeConfiguration);

	vscode.languages.setLanguageConfiguration("actionscript",
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

	vscode.commands.registerCommand("as3mxml.selectWorkspaceSDK", selectWorkspaceSDK);
	vscode.commands.registerCommand("as3mxml.restartServer", restartServer);
	vscode.commands.registerCommand("as3mxml.logCompilerShellOutput", logCompilerShellOutput);
	vscode.commands.registerCommand("as3mxml.saveSessionPassword", saveSessionPassword);
	vscode.commands.registerCommand("as3mxml.importFlashBuilderProject", pickFlashBuilderProjectInWorkspace);
	vscode.commands.registerCommand("as3mxml.quickCompileAndDebug", () =>
	{	
		if(!savedLanguageClient || !isLanguageClientReady)
		{
			pendingQuickCompileAndDebug = true;
			pendingQuickCompileAndRun = false;
			logCompilerShellOutput(QUICK_COMPILE_AND_DEBUG_INIT_MESSAGE, true, false);
			return;
		}
		quickCompileAndLaunch(true);
	});
	vscode.commands.registerCommand("as3mxml.quickCompileAndRun", () =>
	{	
		if(!savedLanguageClient || !isLanguageClientReady)
		{
			pendingQuickCompileAndRun = true;
			pendingQuickCompileAndDebug = false;
			logCompilerShellOutput(QUICK_COMPILE_AND_RUN_INIT_MESSAGE, true, false);
			return;
		}
		quickCompileAndLaunch(false);
	});
	
	//don't activate these things unless we're in a workspace
	if(vscode.workspace.workspaceFolders !== undefined)
	{
		let rootPath = vscode.workspace.workspaceFolders[0].uri.fsPath;
		sourcePathDataProvider = new ActionScriptSourcePathDataProvider(rootPath);
		sourcePathView = vscode.window.createTreeView("actionScriptSourcePaths", {treeDataProvider: sourcePathDataProvider});
		context.subscriptions.push(sourcePathView);
	}

	sdkStatusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right);
	updateSDKStatusBarItem();
	sdkStatusBarItem.tooltip = "Select ActionScript SDK";
	sdkStatusBarItem.command = "as3mxml.selectWorkspaceSDK";
	sdkStatusBarItem.show();

	actionScriptTaskProvider = new ActionScriptTaskProvider(context, javaExecutablePath);
	let taskProviderDisposable = vscode.tasks.registerTaskProvider("actionscript", actionScriptTaskProvider);
	context.subscriptions.push(taskProviderDisposable);

	debugConfigurationProvider = new SWFDebugConfigurationProvider();
	let debugConfigDisposable = vscode.debug.registerDebugConfigurationProvider("swf", debugConfigurationProvider);
	context.subscriptions.push(debugConfigDisposable);

	swcTextDocumentContentProvider = new SWCTextDocumentContentProvider();
	let swcContentDisposable = vscode.workspace.registerTextDocumentContentProvider("swc", swcTextDocumentContentProvider);
	context.subscriptions.push(swcContentDisposable);

	swfDebugAdapterDescriptorFactory = new SWFDebugAdapterDescriptorFactory(() =>
	{
		return(
			{
				javaPath: javaExecutablePath,
				frameworkSDKPath: frameworkSDKHome,
				editorSDKPath: editorSDKHome,
			}
		);
	});
	let debugAdapterDisposable = vscode.debug.registerDebugAdapterDescriptorFactory("swf", swfDebugAdapterDescriptorFactory);
	context.subscriptions.push(debugAdapterDisposable);

	startClient();

	//this is the public API of the extension that may be accessed from other
	//extensions.
	return (
		{
			/**
			 * Indicates if the connection between the language client and the
			 * language server has initialized.
			 */
			get isLanguageClientReady(): boolean
			{
				return isLanguageClientReady;
			},

			/**
			 * The absolute file path of the currently selected framework SDK.
			 * May be null or undefined if no SDK is selected or the currently
			 * selected SDK is invalid.
			 */
			get frameworkSDKPath(): string
			{
				return frameworkSDKHome;
			},
		}
	)
}

export function deactivate()
{
	 savedContext = null;
}

function hasInvalidJava(): boolean
{
	let javaPath = vscode.workspace.getConfiguration("as3mxml").get("java.path") as string;
	return !javaExecutablePath && javaPath != null;
}

function hasInvalidEditorSDK(): boolean
{
	let sdkPath = vscode.workspace.getConfiguration("as3mxml").get("sdk.editor") as string;
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
					{ scheme: "file", language: "actionscript" },
					{ scheme: "file", language: "mxml" },
				],
				synchronize:
				{
					configurationSection: "as3mxml",
				},
				uriConverters:
				{
					code2Protocol: (value: vscode.Uri) =>
					{
						if (/^win32/.test(process.platform))
						{
							//there are a couple of inconsistencies on Windows
							//between VSCode and Java
							//1. The : character after the drive letter is
							//   encoded as %3A in VSCode, but : in Java.
							//2. The driver letter is lowercase in VSCode, but
							//   is uppercase in Java when watching for file
							//   system changes.
							let valueAsString = value.toString().replace("%3A", ":");
							let matches = /^file:\/\/\/([a-z]):\//.exec(valueAsString);
							if(matches !== null)
							{
								let driveLetter = matches[1].toUpperCase();
								valueAsString = `file:///${driveLetter}:/` + valueAsString.substr(matches[0].length);
							}
							return valueAsString;
						}
						else
						{
							return value.toString();
						}
					},
					//this is just the default behavior, but we need to define both
					protocol2Code: value => vscode.Uri.parse(value)
				},
				middleware:
				{
					//TODO: move tags to language server when lsp4j supports it
					handleDiagnostics: (uri, diagnostics, next) =>
					{
						diagnostics.forEach((diagnostic) =>
						{
							switch(diagnostic.code)
							{
								case "as3mxml-unused-import":
								case "as3mxml-disabled-config-condition-block":
								{
									diagnostic.tags = [vscode.DiagnosticTag.Unnecessary];
									break;
								}
								case "3602":
								case "3604":
								case "3606":
								case "3608":
								case "3610":
								{
									diagnostic.tags = [vscode.DiagnosticTag.Deprecated];
									break;
								}
							}
						});
						next(uri, diagnostics);
					}
				}
			};
			let cpDelimiter = getJavaClassPathDelimiter();
			let cp = path.resolve(savedContext.extensionPath, "bin", "*");
			if(editorSDKHome)
			{
				//use the as3mxml.sdk.editor configuration
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
				"com.as3mxml.vscode.Main",
			];
			if(frameworkSDKHome)
			{
				args.unshift("-Droyalelib=" + path.join(frameworkSDKHome, "frameworks"));
			}
			let primaryWorkspaceFolder: vscode.WorkspaceFolder = null;
			if(vscode.workspace.workspaceFolders !== undefined)
			{
				primaryWorkspaceFolder = vscode.workspace.workspaceFolders[0];
			}
			//uncomment to allow a debugger to attach to the language server
			//args.unshift("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005,quiet=y");
			let executable: Executable =
			{
				command: javaExecutablePath,
				args: args,
				options:
				{
					cwd: primaryWorkspaceFolder ? vscode.workspace.workspaceFolders[0].uri.fsPath : undefined
				}
			};
			isLanguageClientReady = false;
			savedLanguageClient = new LanguageClient("actionscript", "ActionScript & MXML Language Server", executable, clientOptions);
			savedLanguageClient.onReady().then(() =>
			{
				resolve();
				isLanguageClientReady = true;
				savedLanguageClient.onNotification("as3mxml/logCompilerShellOutput", (notification: string) =>
				{
					logCompilerShellOutput(notification, false, false);
				});
				savedLanguageClient.onNotification("as3mxml/clearCompilerShellOutput", () =>
				{
					logCompilerShellOutput(null, false, true);
				});
				if(pendingQuickCompileAndDebug)
				{
					vscode.commands.executeCommand("as3mxml.quickCompileAndDebug");
				}
				else if(pendingQuickCompileAndRun)
				{
					vscode.commands.executeCommand("as3mxml.quickCompileAndRun");
				}
				pendingQuickCompileAndDebug = false;
				pendingQuickCompileAndRun = false;
			}, (reason) =>
			{
				resolve();
				vscode.window.showErrorMessage(STARTUP_ERROR);
			});
			let disposable = savedLanguageClient.start();
			savedContext.subscriptions.push(disposable);
		});
	});
}