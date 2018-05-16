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
import * as vscode from "vscode";
import * as child_process from "child_process";
import * as fs from "fs";
import * as json5 from "json5";
import * as path from "path";
import getFrameworkSDKPathWithFallbacks from "../utils/getFrameworkSDKPathWithFallbacks";

const FILE_ASCONFIG_JSON = "asconfig.json";
const FILE_EXTENSION_IPA = ".ipa";
const FILE_EXTENSION_APK = ".apk";
const ADT_PLATFORM_IOS = "ios";
const ADT_PLATFORM_ANDROID = "android";

export default function installAppOnDevice(workspaceFolder: vscode.Uri, platform: string)
{
	let frameworkSDK = getFrameworkSDKPathWithFallbacks();
	let adtExecutable = "adt";
	if(process.platform === "win32")
	{
		adtExecutable += ".bat";
	}
	let adtCommand = path.resolve(frameworkSDK, path.join("bin", adtExecutable));
	if(!fs.existsSync(adtCommand))
	{
		vscode.window.showErrorMessage("Adobe AIR Developer Tool not found in SDK at location: " + adtCommand);
		return false;
	}
	if(platform !== ADT_PLATFORM_ANDROID && platform !== ADT_PLATFORM_IOS)
	{
		vscode.window.showErrorMessage("Unknown platform for Adobe AIR debugging: " + platform);
		return false;
	}
	let asconfigPath = path.resolve(workspaceFolder.fsPath, FILE_ASCONFIG_JSON);
	let asconfigJSON: any = null;
	try
	{
		let asconfigFile = fs.readFileSync(asconfigPath, "utf8");
		asconfigJSON = json5.parse(asconfigFile);
	}
	catch(error)
	{
		//something went terribly wrong!
		vscode.window.showErrorMessage("Failed to debug SWF. Error reading asconfig.json");
		return null;
	}

	let appID = getApplicationID(asconfigJSON, workspaceFolder);
	if(appID === null)
	{
		return null;
	}
	let appPackagePath = null;
	if(platform === ADT_PLATFORM_ANDROID)
	{
		appPackagePath = asconfigJSON.airOptions.android.output;
	}
	else if(platform === ADT_PLATFORM_IOS)
	{
		appPackagePath = asconfigJSON.airOptions.ios.output;
	}
	if(appPackagePath === null)
	{
		vscode.window.showErrorMessage(`Failed to debug SWF. Output path of application package not found in asconfig.json for platform "${platform}".`);
		return null;
	}

	let uninstallResult = child_process.spawnSync(adtCommand,
	[
		"-uninstallApp",
		"-platform",
		platform,
		"-appid",
		appID,
	],
	{
		cwd: workspaceFolder.fsPath
	});
	if(uninstallResult.status !== 0 && uninstallResult.status !== 14)
	{
		let error = uninstallResult.stderr.toString("utf8");
		vscode.window.showErrorMessage(`Device uninstall failed for platform "${platform}".\nError: ${error}`);
		//error code 14 means that the app isn't installed on the device.
		//that's okay! it might not have been installed before.
		//Source: https://help.adobe.com/en_US/air/build/WS901d38e593cd1bac1e63e3d128fc240122-7ff7.html
		//any other error code should be treated as a problem.
		return false;
	}

	let installResult = child_process.spawnSync(adtCommand,
	[
		"-installApp",
		"-platform",
		platform,
		"‑package",
		appPackagePath,
	],
	{
		cwd: workspaceFolder.fsPath
	});
	if(installResult.status !== 0)
	{
		let error = installResult.stderr.toString("utf8");
		vscode.window.showErrorMessage(`Installing app on device failed for platform "${platform}".\nError: ${error}`);
		return false;
	}
	
	if(platform === ADT_PLATFORM_IOS)
	{
		//ADT can't launch an iOS application automatically
		vscode.window.showInformationMessage("Debugger ready to attach. You must launch your application manually on the iOS device.");
	}
	else
	{
		let launchResult = child_process.spawnSync(adtCommand,
		[
			"-launchApp",
			"-platform",
			platform,
			"-appid",
			appID,
		],
		{
			cwd: workspaceFolder.fsPath
		});
		if(launchResult.status !== 0)
		{
			let error = launchResult.stderr.toString("utf8");
			vscode.window.showErrorMessage(`Launching app on device failed for platform "${platform}".\nError: ${error}`);
			return false;
		}
	}
	return true;
}

function getApplicationID(asconfigJSON: any, workspaceFolder: vscode.Uri)
{
	let appDescriptorPath = asconfigJSON.application;
	if(!path.isAbsolute(appDescriptorPath))
	{
		appDescriptorPath = path.resolve(workspaceFolder.fsPath, appDescriptorPath);
	}
	let applicationDescriptor: string = null;
	try
	{
		applicationDescriptor = fs.readFileSync(appDescriptorPath, "utf8");
	}
	catch(error)
	{
		//something went terribly wrong!
		vscode.window.showErrorMessage("Failed to debug SWF. Error reading application descriptor at path: " + appDescriptorPath);
		return null;
	}

	let idElement = /<id>([\w+\.]+)<\/id>/.exec(applicationDescriptor);
	if(!idElement || idElement.length < 2)
	{
		vscode.window.showErrorMessage("Failed to debug SWF. Error reading application <id> in application descriptor at path: " + appDescriptorPath);
		return null;
	}
	return idElement[1];
}