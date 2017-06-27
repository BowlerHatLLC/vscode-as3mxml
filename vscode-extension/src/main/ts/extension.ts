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
import createASConfigTaskRunner from "./commands/createASConfigTaskRunner";
import createInitialConfigurationsForSWFDebug from "./commands/createInitialConfigurationsForSWFDebug";
import findPort from "./utils/findPort";
import findJava from "./utils/findJava";
import validateJava from "./utils/validateJava";
import validateEditorSDK from "./utils/validateEditorSDK";
import ActionScriptSourcePathDataProvider from "./utils/ActionScriptSourcePathDataProvider";
import getJavaClassPathDelimiter from "./utils/getJavaClassPathDelimiter";
import findSDKShortName from "./utils/findSDKShortName";
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

const INVALID_SDK_ERROR = "nextgenas.sdk.editor in settings does not point to a valid SDK. Requires Apache FlexJS 0.7.0 or newer.";
const MISSING_FRAMEWORK_SDK_ERROR = "You must configure an SDK to enable all ActionScript and MXML features.";
const MISSING_JAVA_ERROR = "Could not locate valid Java executable. Configure nextgenas.java, add to $PATH, or set $JAVA_HOME.";
const MISSING_WORKSPACE_ROOT_ERROR = "Open a folder and create a file named asconfig.json to enable all ActionScript and MXML language features.";
const RESTART_MESSAGE = "To apply new settings for NextGen ActionScript, please restart Visual Studio Code.";
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
	let newFrameworkSDKHome = <string> vscode.workspace.getConfiguration("nextgenas").get("sdk.framework");
	if(editorSDKHome != newEditorSDKHome ||
		javaExecutablePath != newJavaExecutablePath ||
		frameworkSDKHome != newFrameworkSDKHome)
	{
		editorSDKHome = newEditorSDKHome;
		frameworkSDKHome = newFrameworkSDKHome;
		updateSDKStatusBarItem();

		//on Windows, the language server doesn't restart very gracefully,
		//so force a restart. 
		vscode.window.showWarningMessage(RESTART_MESSAGE, RESTART_BUTTON_LABEL).then((action) =>
		{
			if(action === RESTART_BUTTON_LABEL)
			{
				vscode.commands.executeCommand("workbench.action.reloadWindow");
			}
		});
	}
}

function updateSDKStatusBarItem()
{
	let sdkShortName = NO_SDK;
	if(frameworkSDKHome)
	{
		sdkShortName = findSDKShortName(frameworkSDKHome);
	}
	else if(editorSDKHome)
	{
		//for legacy reasons, we fall back to the editor SDK if the framework
		//SDK is not defined.
		sdkShortName = findSDKShortName(editorSDKHome);
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
	frameworkSDKHome = <string> vscode.workspace.getConfiguration("nextgenas").get("sdk.framework");
	vscode.workspace.onDidChangeConfiguration(onDidChangeConfiguration);
	vscode.commands.registerCommand("nextgenas.createASConfigTaskRunner", () =>
	{
		let tasksHome = frameworkSDKHome;
		if(!tasksHome)
		{
			tasksHome = editorSDKHome;
		}
		createASConfigTaskRunner(tasksHome);
	});
	vscode.commands.registerCommand("nextgenas.adapterExecutableCommandSWF", () =>
	{
		return adapterExecutableCommandSWF(javaExecutablePath, editorSDKHome, frameworkSDKHome ? frameworkSDKHome : editorSDKHome);
	});
	vscode.commands.registerCommand("nextgenas.createInitialConfigurationsForSWFDebug", createInitialConfigurationsForSWFDebug);
	vscode.commands.registerTextEditorCommand("nextgenas.addImport", addImport);
	vscode.commands.registerTextEditorCommand("nextgenas.addMXMLNamespace", addMXMLNamespace);
	vscode.commands.registerCommand("nextgenas.organizeImportsInUri", organizeImportsInUri);
	vscode.commands.registerCommand("nextgenas.organizeImportsInTextEditor", organizeImportsInTextEditor);
	vscode.commands.registerCommand("nextgenas.organizeImportsInDirectory", organizeImportsInDirectory);
	vscode.commands.registerCommand("nextgenas.selectWorkspaceSDK", selectWorkspaceSDK);

	sdkStatusBarItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right);
	updateSDKStatusBarItem();
	sdkStatusBarItem.tooltip = "Select ActionScript SDK";
	sdkStatusBarItem.command = "nextgenas.selectWorkspaceSDK";
	sdkStatusBarItem.show();

	sourcePathDataProvider = new ActionScriptSourcePathDataProvider(vscode.workspace.rootPath);
	vscode.window.registerTreeDataProvider("actionScriptSourcePaths", sourcePathDataProvider);

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
	vscode.window.showErrorMessage("NextGen ActionScript extension exited with error code " + code);
}

function childErrorListener(error)
{
	vscode.window.showErrorMessage("Failed to start NextGen ActionScript extension.");
	console.error("Error connecting to child process.");
	console.error(error);
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
		if(!frameworkSDKHome && !editorSDKHome)
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
		if(!frameworkSDKHome && !editorSDKHome)
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
	if(!frameworkSDKHome && !editorSDKHome)
	{
		showMissingFrameworkSDKError();
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

	let clientOptions: LanguageClientOptions =
	{
		documentSelector:
		[
			"nextgenas",
			"mxml"
		],
		synchronize:
		{
			//the server will be notified when these files change
			fileEvents:
			[
				vscode.workspace.createFileSystemWatcher("**/asconfig.json"),
				vscode.workspace.createFileSystemWatcher("**/*.as"),
				vscode.workspace.createFileSystemWatcher("**/*.mxml"),
			]
		},
		errorHandler: new CustomErrorHandler("NextGen ActionScript")
	};
	vscode.languages.setLanguageConfiguration("nextgenas",
	{
		"onEnterRules":
		[
			{
				beforeText: /^\s*\/\*\*(?!\/)([^\*]|\*(?!\/))*$/,
				afterText: /^\s*\*\/$/,
				action:
				{
					indentAction: vscode.IndentAction.IndentOutdent,
					appendText: " * "
				}
			},
		]
	});
	let client = new LanguageClient("nextgenas", "NextGen ActionScript Language Server", createLanguageServer, clientOptions);
	let disposable = client.start();
	savedContext.subscriptions.push(disposable);
}