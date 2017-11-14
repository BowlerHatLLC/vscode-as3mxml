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

const ENVIRONMENT_VARIABLE_FLEX_HOME = "FLEX_HOME";

export default function findSDKInLocalNodeModule(): string
{
	if(vscode.workspace.workspaceFolders === undefined)
	{
		return null;
	}
	let flexjsModule = path.join(vscode.workspace.workspaceFolders[0].uri.fsPath, "node_modules", "flexjs");
	if(validateFrameworkSDK(flexjsModule))
	{
		return flexjsModule;
	}
	return null;
}