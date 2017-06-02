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
import * as vscode from "vscode";
import * as fs from "fs";
import * as path from "path";
import findSDKName from "../utils/findSDKName";

const ENVIRONMENT_VARIABLE_FLEX_HOME = "FLEX_HOME";
const ENVIRONMENT_VARIABLE_PATH = "PATH";
const DESCRIPTION_FLEX_HOME = "FLEX_HOME environment variable";
const DESCRIPTION_PATH = "PATH environment variable";
const DESCRIPTION_CURRENT = "Current SDK";
const DESCRIPTION_EDITOR_SDK = "Editor SDK in Settings";
const DESCRIPTION_FLASH_BUILDER_4_7 = "Flash Builder 4.7";
const DESCRIPTION_FLASH_BUILDER_4_6 = "Flash Builder 4.6";
const KNOWN_SDKS_MAC =
[
	{
		path: "/Applications/Adobe Flash Builder 4.7/eclipse/plugins/com.adobe.flash.compiler_4.7.0.349722/AIRSDK",
		description: DESCRIPTION_FLASH_BUILDER_4_7
	},
	{
		path: "/Applications/Adobe Flash Builder 4.7/sdks/4.6.0/",
		description: DESCRIPTION_FLASH_BUILDER_4_7
	},
	{
		path: "/Applications/Adobe Flash Builder 4.7/sdks/3.6.0/",
		description: DESCRIPTION_FLASH_BUILDER_4_7
	},
	{
		path: "/Applications/Adobe Flash Builder 4.6/sdks/4.6.0/",
		description: DESCRIPTION_FLASH_BUILDER_4_6
	},
	{
		path: "/Applications/Adobe Flash Builder 4.6/sdks/3.6.0/",
		description: DESCRIPTION_FLASH_BUILDER_4_6
	},
];
const KNOWN_SDKS_WIN =
[
	{
		path: "C:\\Program Files\\Adobe\\Adobe Flash Builder 4.7 (64 Bit)\\eclipse\\plugins\\com.adobe.flash.compiler_4.7.0.349722\\AIRSDK",
		description: DESCRIPTION_FLASH_BUILDER_4_7
	},
	{
		path: "C:\\Program Files\\Adobe\\Adobe Flash Builder 4.7 (64 Bit)\\sdks\\4.6.0",
		description: DESCRIPTION_FLASH_BUILDER_4_7
	},
	{
		path: "C:\\Program Files\\Adobe\\Adobe Flash Builder 4.7 (64 Bit)\\sdks\\3.6.0",
		description: DESCRIPTION_FLASH_BUILDER_4_7
	},
	{
		path: "C:\\Program Files (x86)\\Adobe\\Adobe Flash Builder 4.6\\sdks\\4.6.0",
		description: DESCRIPTION_FLASH_BUILDER_4_6
	},
	{
		path: "C:\\Program Files (x86)\\Adobe\\Adobe Flash Builder 4.6\\sdks\\3.6.0",
		description: DESCRIPTION_FLASH_BUILDER_4_6
	},
	{
		path: "C:\\Program Files\\Adobe\\Adobe Flash Builder 4.6\\sdks\\4.6.0",
		description: DESCRIPTION_FLASH_BUILDER_4_6
	},
	{
		path: "C:\\Program Files\\Adobe\\Adobe Flash Builder 4.6\\sdks\\3.6.0",
		description: DESCRIPTION_FLASH_BUILDER_4_6
	},
];

interface SDKQuickPickItem extends vscode.QuickPickItem
{
	custom?: any;
}

function openWorkspaceSettings()
{
	let workspaceSettingsPath = path.join(vscode.workspace.rootPath, ".vscode", "settings.json");
	if(!fs.existsSync(workspaceSettingsPath))
	{
		vscode.workspace.getConfiguration("nextgenas").update("sdk.framework", null).then(() =>
		{
			openWorkspaceSettings();
		});
	}
	else
	{
		let uri = vscode.Uri.file(workspaceSettingsPath);
		vscode.workspace.openTextDocument(uri).then((document: vscode.TextDocument) =>
		{
			vscode.window.showTextDocument(document);
		});
	}
}

function addSDKItem(path: string, description: string, items: SDKQuickPickItem[], allPaths: string[], require: boolean): void
{
	if(allPaths.indexOf(path) !== -1)
	{
		//skip duplicate
		return;
	}
	allPaths.push(path);
	let label = findSDKName(path);
	if(label === null)
	{
		//we couldn't find the name of this SDK
		if(!require)
		{
			//if it's not required, skip it
			return;
		}
		label = "Unknown SDK";
	}
	items.push(
	{
		label: label,
		detail: path,
		description: description
	});
}

export default function selectWorkspaceSDK(): void
{
	if(!vscode.workspace.rootPath)
	{
		vscode.window.showErrorMessage("Cannot change ActionScript SDK because no workspace is currently open.");
		return;
	}
	let allPaths: string[] = [];
	let items: SDKQuickPickItem[] = [];
	//start with the current framework and editor SDKs
	let frameworkSDK = <string> vscode.workspace.getConfiguration("nextgenas").get("sdk.framework");
	let editorSDK = <string> vscode.workspace.getConfiguration("nextgenas").get("sdk.editor");
	let addedEditorSDK = false;
	if(frameworkSDK)
	{
		addSDKItem(frameworkSDK, DESCRIPTION_CURRENT, items, allPaths, true);
	}
	else if(editorSDK)
	{
		//for legacy reasons, we fall back to the editor SDK if the framework
		//SDK is not defined.
		addedEditorSDK = true;
		addSDKItem(editorSDK, DESCRIPTION_CURRENT, items, allPaths, true);
	}
	//for convenience, add an option to open workspace settings and define a custom SDK
	items.push(
	{
		label: "Define a custom SDK...",
		description: "Opens workspace settings",
		detail: "Set nextgenas.sdk.framework to the path of your custom SDK",
		custom: true
	});
	//then search for an SDK that's a locally installed Node.js module
	let nodeModuleSDK: string = null;
	try
	{
		nodeModuleSDK = require.resolve("flexjs");
	}
	catch(error){}
	if(nodeModuleSDK)
	{
		addSDKItem(nodeModuleSDK, "Node Module", items, allPaths, true);
	}
	//if we haven't already added the editor SDK, do it now
	if(!addedEditorSDK && editorSDK)
	{
		addSDKItem(editorSDK, DESCRIPTION_EDITOR_SDK, items, allPaths, true);
	}
	//check if the FLEX_HOME environment variable is defined
	if(ENVIRONMENT_VARIABLE_FLEX_HOME in process.env)
	{
		addSDKItem(process.env.FLEX_HOME, DESCRIPTION_FLEX_HOME, items, allPaths, false);
	}
	//try to discover SDKs from the PATH environment variable
	if(ENVIRONMENT_VARIABLE_PATH in process.env)
	{
		let PATH = <string> process.env.PATH;
		let paths = PATH.split(path.delimiter);
		let pathCount = paths.length;
		for(let i = 0; i < pathCount; i++)
		{
			let currentPath = paths[i];
			//first check if this directory contains the NPM version of FlexJS for Windows
			let mxmlcPath = path.join(currentPath, "mxmlc.cmd");
			if(fs.existsSync(mxmlcPath))
			{
				let sdkPath = path.join(path.dirname(mxmlcPath), "node_modules", "flexjs");
				if(fs.existsSync(sdkPath))
				{
					addSDKItem(sdkPath, DESCRIPTION_PATH, items, allPaths, false);
				}
			}
			else
			{
				mxmlcPath = path.join(currentPath, "mxmlc");
				if(fs.existsSync(mxmlcPath))
				{
					//this may a symbolic link rather than the actual file, such as
					//when Apache FlexJS is installed with NPM on macOS, so get the
					//real path.
					mxmlcPath = fs.realpathSync(mxmlcPath);
					//first, check for bin/mxmlc
					let frameworksPath = path.join(path.dirname(mxmlcPath), "..", "frameworks");
					if(fs.existsSync(frameworksPath) && fs.statSync(frameworksPath).isDirectory())
					{
						let sdkPath = path.join(path.dirname(mxmlcPath), "..");
						addSDKItem(sdkPath, DESCRIPTION_PATH, items, allPaths, false);
					}
					//then, check for js/bin/mxmlc
					frameworksPath = path.join(path.dirname(mxmlcPath), "..", "..", "frameworks");
					if(fs.existsSync(frameworksPath) && fs.statSync(frameworksPath).isDirectory())
					{
						let sdkPath = path.join(path.dirname(mxmlcPath), "..", "..");
						addSDKItem(sdkPath, DESCRIPTION_PATH, items, allPaths, false);
					}
				}
			}
		}
	}
	//finish by checking some known SDK locations that might exist
	let knownSDKs = KNOWN_SDKS_MAC;
	if(process.platform === "win32")
	{
		knownSDKs = KNOWN_SDKS_WIN;
	}
	for(let i = 0, count = knownSDKs.length; i < count; i++)
	{
		let knownSDK = knownSDKs[i];
		let path = knownSDK.path;
		if(fs.existsSync(path))
		{
			addSDKItem(path, knownSDK.description, items, allPaths, false);
		}
	}
	if(items.length === 1)
	{
		//if there are no SDKs in the list (only the option to define a custom
		//path), then open the workspace settings immediately.
		openWorkspaceSettings();
		return;
	}
	vscode.window.showQuickPick(items, {placeHolder: "Select an ActionScript SDK for this workspace"}).then((value: SDKQuickPickItem) =>
	{
		if(!value)
		{
			//no new SDK was picked, so do nothing
			return;
		}
		if(typeof value.custom !== "undefined")
		{
			//if the user chose to define a custom SDK, open workspace settings
			openWorkspaceSettings();
			return;
		}
		//if they chose an SDK, save it to the workspace settings
		let newFrameworkPath = value.detail;
		vscode.workspace.getConfiguration("nextgenas").update("sdk.framework", newFrameworkPath);
	});
}