/*
Copyright 2016-2017 Bowler Hat LLC

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
import addImport from "./commands/addImport";
import addMXMLNamespace from "./commands/addMXMLNamespace";
import organizeImportsInUri from "./commands/organizeImportsInUri";
import organizeImportsInTextEditor from "./commands/organizeImportsInTextEditor";
import organizeImportsInDirectory from "./commands/organizeImportsInDirectory";
import createInitialConfigurationsForSWFDebug from "./commands/createInitialConfigurationsForSWFDebug";
import findPort from "./utils/findPort";
import findJava from "./utils/findJava";
import validateJava from "./utils/validateJava";
import validateEditorSDK from "./utils/validateEditorSDK";
import ActionScriptSourcePathDataProvider from "./utils/ActionScriptSourcePathDataProvider";
import ActionScriptTaskProvider from "./utils/ActionScriptTaskProvider";
import getJavaClassPathDelimiter from "./utils/getJavaClassPathDelimiter";
import findSDKShortName from "./utils/findSDKShortName";
import getFrameworkSDKPathWithFallbacks from "./utils/getFrameworkSDKPathWithFallbacks";
import adapterExecutableCommandSWF from "./commands/adapterExecutableCommandSWF";
import selectWorkspaceSDK from "./commands/selectWorkspaceSDK";
import * as child_process from "child_process";
import * as fs from "fs";
import * as net from "net";
import * as path from "path";
import * as vscode from "vscode";
import {LanguageClient, LanguageClientOptions, SettingMonitor,
	ServerOptions, StreamInfo, ErrorHandler, ErrorAction, CloseAction} from "vscode-languageclient";
import { Message } from "vscode-jsonrpc";

const INVALID_SDK_ERROR = "nextgenas.sdk.editor in settings does not point to a valid SDK. Requires Apache FlexJS 0.8.0 or newer.";
const MISSING_FRAMEWORK_SDK_ERROR = "You must configure an SDK to enable all ActionScript and MXML features.";
const INVALID_JAVA_ERROR = "nextgenas.java in settings does not point to a valid executable. It cannot be a directory, and Java 1.8 or newer is required.";
const MISSING_JAVA_ERROR = "Could not locate valid Java executable. To configure Java manually, use the nextgenas.java setting.";
const MISSING_WORKSPACE_ROOT_ERROR = "Open a folder and create a file named asconfig.json to enable all ActionScript and MXML language features.";
const RESTART_MESSAGE = "To apply new settings for ActionScript and MXML, please restart Visual Studio Code.";
const RESTART_BUTTON_LABEL = "Restart Now";
const CONFIGURE_SDK_LABEL = "Configure SDK";
const NO_SDK = "$(alert) No SDK";
let savedChild: child_process.ChildProcess;
let savedContext: vscode.ExtensionContext;
let bundledCompilerPath: string;
let editorSDKHome: string;
let javaExecutablePath: string;
let frameworkSDKHome: string;
let killed = false;
let hasShownFlexJSSDKWarning = false;
let hasShownFrameworkSDKWarning = false;
let sdkStatusBarItem: vscode.StatusBarItem;
let sourcePathDataProvider: ActionScriptSourcePathDataProvider = null;
let actionScriptTaskProvider: ActionScriptTaskProvider = null;

function killJavaProcess()
{
	if(!savedChild)
	{
		return;
	}
	killed = true;
	//we are killing the process on purpose, so we don't care
	//about these events anymore
	savedChild.removeListener("exit", childExitListener);
	savedChild.removeListener("error", childErrorListener);
	savedChild.kill();
	savedChild = null;
}

function getValidatedEditorSDKConfiguration(javaExecutablePath: string): string
{
	let result = <string> vscode.workspace.getConfiguration("nextgenas").get("sdk.editor");
	if(result && !validateEditorSDK(savedContext.extensionPath, javaExecutablePath, result))
	{
		//this is not a valid SDK
		return null;
	}
	return result;
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
	if(editorSDKHome != newEditorSDKHome ||
		javaExecutablePath != newJavaExecutablePath)
	{
		//on Windows, the language server doesn't restart very gracefully,
		//so force a restart if either of these two settings changes.
		//we don't need to restart if nextgenas.sdk.framework changes
		//because the language server can detect that and update.
		vscode.window.showWarningMessage(RESTART_MESSAGE, RESTART_BUTTON_LABEL).then((action) =>
		{
			if(action === RESTART_BUTTON_LABEL)
			{
				vscode.commands.executeCommand("workbench.action.reloadWindow");
			}
		});
	}
	if(editorSDKHome != newEditorSDKHome ||
		frameworkSDKHome != newFrameworkSDKHome)
	{
		editorSDKHome = newEditorSDKHome;
		frameworkSDKHome = newFrameworkSDKHome;
		updateSDKStatusBarItem();
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
	vscode.commands.registerCommand("nextgenas.createASConfigTaskRunner", () =>
	{
		//this command is deprecated
		vscode.commands.executeCommand("workbench.action.tasks.configureDefaultBuildTask");
	});
	vscode.commands.registerCommand("nextgenas.adapterExecutableCommandSWF", () =>
	{
		return adapterExecutableCommandSWF(javaExecutablePath, editorSDKHome, frameworkSDKHome);
	});
	vscode.commands.registerCommand("nextgenas.createInitialConfigurationsForSWFDebug", createInitialConfigurationsForSWFDebug);
	vscode.commands.registerTextEditorCommand("nextgenas.addImport", addImport);
	vscode.commands.registerTextEditorCommand("nextgenas.addMXMLNamespace", addMXMLNamespace);
	vscode.commands.registerCommand("nextgenas.organizeImportsInUri", organizeImportsInUri);
	vscode.commands.registerCommand("nextgenas.organizeImportsInTextEditor", organizeImportsInTextEditor);
	vscode.commands.registerCommand("nextgenas.organizeImportsInDirectory", organizeImportsInDirectory);
	vscode.commands.registerCommand("nextgenas.selectWorkspaceSDK", selectWorkspaceSDK);

	//don't activate these things unless we're in a workspace
	if(vscode.workspace.rootPath)
	{
		sdkStatusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right);
		updateSDKStatusBarItem();
		sdkStatusBarItem.tooltip = "Select ActionScript SDK";
		sdkStatusBarItem.command = "nextgenas.selectWorkspaceSDK";
		sdkStatusBarItem.show();

		sourcePathDataProvider = new ActionScriptSourcePathDataProvider(vscode.workspace.rootPath);
		vscode.window.registerTreeDataProvider("actionScriptSourcePaths", sourcePathDataProvider);

		actionScriptTaskProvider = new ActionScriptTaskProvider();
		vscode.workspace.registerTaskProvider("actionscript", actionScriptTaskProvider);
	}
	startClient();
}

export function deactivate()
{
	 savedContext = null;
	 killJavaProcess();
}

function childExitListener(code)
{
	console.info("Child process exited", code);
	if(code === 0)
	{
		return;
	}
	vscode.window.showErrorMessage("ActionScript and MXML extension exited with error code " + code);
}

function childErrorListener(error)
{
	vscode.window.showErrorMessage("Failed to start ActionScript and MXML extension.");
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

class CustomErrorHandler implements ErrorHandler
{
	private restarts: number[];

	constructor(private name: string)
	{
		this.restarts = [];
	}

	error(error: Error, message: Message, count): ErrorAction
	{
		//this is simply the default behavior
		if(count && count <= 3)
		{
			return ErrorAction.Continue;
		}
		return ErrorAction.Shutdown;
	}
	closed(): CloseAction
	{
		if(killed)
		{
			//we killed the process on purpose, so we will attempt to restart manually
			killed = false;
			return CloseAction.DoNotRestart;
		}
		if(!javaExecutablePath)
		{
			//if we can't find java, we can't restart the process
			vscode.window.showErrorMessage(MISSING_JAVA_ERROR);
			return CloseAction.DoNotRestart;
		}
		if(hasInvalidEditorSDK())
		{
			vscode.window.showErrorMessage(INVALID_SDK_ERROR);
			//if we can't find the SDK, we can't start the process
			return CloseAction.DoNotRestart;
		}
		if(!frameworkSDKHome)
		{
			showMissingFrameworkSDKError();
			//if we can't find an SDK, we can't start the process
			return CloseAction.DoNotRestart;
		}

		//this is the default behavior. the code above handles a special case
		//where we need to kill the process and restart it, but we don't want
		//that to be detected below.
		this.restarts.push(Date.now());
		if (this.restarts.length < 5)
		{
			return CloseAction.Restart;
		}
		else
		{
			let diff = this.restarts[this.restarts.length - 1] - this.restarts[0];
			if(diff <= 3 * 60 * 1000)
			{
				vscode.window.showErrorMessage(`The ${this.name} server crashed 5 times in the last 3 minutes. The server will not be restarted.`);
				return CloseAction.DoNotRestart;
			}
			else
			{
				this.restarts.shift();
				return CloseAction.Restart;
			}
		}
	}
}

function createLanguageServer(): Promise<StreamInfo>
{
	return new Promise((resolve, reject) =>
	{
		//immediately reject if flexjs or java cannot be found
		if(hasInvalidJava())
		{
			reject(INVALID_JAVA_ERROR)
			return;
		}
		if(!javaExecutablePath)
		{ 
			reject(MISSING_JAVA_ERROR);
			return;
		}
		if(hasInvalidEditorSDK())
		{
			reject(INVALID_SDK_ERROR);
			return;
		}
		if(!frameworkSDKHome)
		{
			reject(MISSING_FRAMEWORK_SDK_ERROR);
			return;
		}
		findPort(55282, (port) =>
		{
			let cpDelimiter = getJavaClassPathDelimiter();
			let cp = path.resolve(savedContext.extensionPath, "bin", "*");
			if(editorSDKHome)
			{
				//use the nextgenas.sdk.editor configuration
				cp += cpDelimiter +
					//the following jars come from apache flexjs
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
				"-cp",
				cp,
				//the language server communicates with vscode on this port
				"-Dnextgeas.vscode.port=" + port,
				"com.nextgenactionscript.vscode.Main",
			];
			if(frameworkSDKHome)
			{
				args.unshift("-Dflexlib=" + path.join(frameworkSDKHome, "frameworks"));
			}

			//remote java debugging
			//args.unshift("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005");

			//failed assertions in the compiler will crash the extension,
			//so this should not be enabled by default, even for debugging
			//args.unshift("-ea");
			
			let server = net.createServer(socket =>
			{
				resolve(
				{
					reader: socket,
					writer: socket
				});
			});
			server.listen(port, () =>
			{
				let options =
				{
					cwd: vscode.workspace.rootPath
				};
				
				// Start the child java process
				savedChild = child_process.spawn(javaExecutablePath, args, options);
				savedChild.on("error", childErrorListener);
				savedChild.on("exit", childExitListener);
				if(savedChild.stdout)
				{
					savedChild.stdout.on("data", (data: Buffer) =>
					{
						console.log(data.toString("utf8"));
					});
				}
				if(savedChild.stderr)
				{
					savedChild.stderr.on("data", (data: Buffer) =>
					{
						console.error(data.toString("utf8"));
					});
				}
			});
		});
	});
}

function startClient()
{
	if(!savedContext)
	{
		//something very bad happened!
		return;
	}
	if(!vscode.workspace.rootPath)
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

	let clientOptions: LanguageClientOptions =
	{
		documentSelector:
		[
			"nextgenas",
			"mxml"
		],
		synchronize:
		{
			configurationSection: "nextgenas",
			//the server will be notified when these files change
			fileEvents:
			[
				vscode.workspace.createFileSystemWatcher("**/asconfig.json"),
				vscode.workspace.createFileSystemWatcher("**/*.as"),
				vscode.workspace.createFileSystemWatcher("**/*.mxml"),
			]
		},
		errorHandler: new CustomErrorHandler("ActionScript and MXML")
	};
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
	let client = new LanguageClient("nextgenas", "ActionScript and MXML Language Server", createLanguageServer, clientOptions);
	let disposable = client.start();
	savedContext.subscriptions.push(disposable);
}