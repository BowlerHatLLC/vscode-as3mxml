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
import * as path from "path";
import * as vscode from "vscode";
import validateFrameworkSDK from "./validateFrameworkSDK";
import findSDKInLocalNodeModule from "./findSDKInLocalNodeModule";
import findSDKInFlexHomeEnvironmentVariable from "./findSDKInFlexHomeEnvironmentVariable";
import findSDKsInPathEnvironmentVariable from "./findSDKsInPathEnvironmentVariable";

const ENVIRONMENT_VARIABLE_FLEX_HOME = "FLEX_HOME";
const ENVIRONMENT_VARIABLE_PATH = "PATH";

export default function getFrameworkSDKPathWithFallbacks(): string
{
	if(!vscode.workspace.rootPath)
	{
		//no open workspace means no SDK
		return null;
	}
	let sdkPath: string = null;
	let frameworkSetting = <string> vscode.workspace.getConfiguration("nextgenas").get("sdk.framework");
	if(frameworkSetting)
	{
		if(validateFrameworkSDK(frameworkSetting))
		{
			return frameworkSetting;
		}
		//no fallbacks if this SDK isn't valid!
		return null;
	}
	if(!sdkPath)
	{
		//for legacy reasons, we support falling back to the editor SDK
		let editorSetting = <string> vscode.workspace.getConfiguration("nextgenas").get("sdk.editor");
		if(editorSetting)
		{
			if(validateFrameworkSDK(editorSetting))
			{
				return editorSetting;
			}
			//no fallbacks if this SDK isn't valid!
			return null;
		}
	}
	//the following SDKs are all intelligent fallbacks
	if(!sdkPath)
	{
		//check if the FlexJS Node module is installed locally in the workspace
		sdkPath = findSDKInLocalNodeModule();
	}
	if(!sdkPath)
	{
		//the FLEX_HOME environment variable may point to an SDK
		sdkPath = findSDKInFlexHomeEnvironmentVariable();
	}
	if(!sdkPath)
	{
		//this should be the same SDK that is used if the user tries to run the
		//compiler from the command line without an absolute path
		let sdkPaths = findSDKsInPathEnvironmentVariable();
		if(sdkPaths.length > 0)
		{
			sdkPath = sdkPaths[0];
		}
	}
	return sdkPath;
}