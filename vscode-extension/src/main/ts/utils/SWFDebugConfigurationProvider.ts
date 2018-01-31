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
import * as fs from "fs";
import * as path from "path";
import * as json5 from "json5";

const FILE_EXTENSION_SWF = ".swf";

const CONFIG_AIR = "air";
const CONFIG_AIRMOBILE = "airmobile";

const PROFILE_MOBILE_DEVICE = "mobileDevice";

interface SWFDebugConfiguration extends vscode.DebugConfiguration
{
	program?: string;
	profile?: string;
	screenDPI?: number;
	screensize?: string;
	args?: string[];
	versionPlatform?: string;
	runtimeExecutable?: string;
	runtimeArgs?: string[];
	extdir?: string;
}

export default class SWFDebugConfigurationProvider implements vscode.DebugConfigurationProvider
{
	provideDebugConfigurations(workspaceFolder: vscode.WorkspaceFolder | undefined, token?: vscode.CancellationToken): vscode.ProviderResult<SWFDebugConfiguration[]>
	{
		if(workspaceFolder === undefined)
		{
			return [];
		}
		const initialConfigurations = 
		[
			//this is enough to resolve the rest based on asconfig.json
			{
				type: "swf",
				request: "launch",
				name: "Launch SWF"
			},
			{
				type: "swf",
				request: "attach",
				name: "Attach SWF"
			}
		];
		return initialConfigurations;
	}

	resolveDebugConfiguration?(workspaceFolder: vscode.WorkspaceFolder | undefined, debugConfiguration: SWFDebugConfiguration, token?: vscode.CancellationToken): vscode.ProviderResult<SWFDebugConfiguration>
	{
		if(workspaceFolder === undefined)
		{
			vscode.window.showErrorMessage("Failed to debug SWF. A workspace must be open.");
			return null;
		}
		//see if we can find the SWF file
		let workspaceFolderPath = workspaceFolder.uri.fsPath;
		let asconfigPath = path.resolve(workspaceFolderPath, "asconfig.json");
		if(!fs.existsSync(asconfigPath))
		{
			vscode.window.showErrorMessage("Failed to debug SWF. Workspace does not contain asconfig.json.");
			return null;
		}
		
		let program: string = debugConfiguration.program;
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
		let appDescriptorPath: string = null;
		let outputPath: string = null;
		let mainClassPath: string = null;
		let requireAIR = false;
		let isMobile = false;
		if("config" in asconfigJSON)
		{
			isMobile = asconfigJSON.config === CONFIG_AIRMOBILE;
			requireAIR = isMobile || asconfigJSON.config === CONFIG_AIR;
		}
		if("application" in asconfigJSON)
		{
			appDescriptorPath = asconfigJSON.application;
			requireAIR = true;
		}
		if("profile" in debugConfiguration ||
			"screensize" in debugConfiguration ||
			"screenDPI" in debugConfiguration ||
			"versionPlatform" in debugConfiguration ||
			"extdir" in debugConfiguration ||
			"args" in debugConfiguration)
		{
			//if any of these fields are specified in the debug
			//configuration, then AIR is required!
			requireAIR = true;
		}
		if("compilerOptions" in asconfigJSON)
		{
			let compilerOptions = asconfigJSON.compilerOptions;
			if("output" in compilerOptions)
			{
				outputPath = asconfigJSON.compilerOptions.output;
			}
		}
		if("files" in asconfigJSON)
		{
			let files = asconfigJSON.files;
			if(Array.isArray(files) && files.length > 0)
			{
				//the last entry in the files field is the main
				//class used as the entry point.
				mainClassPath = files[files.length - 1];
			}
		}
		if(!program)
		{
			if(appDescriptorPath !== null)
			{
				//start by checking if this is an AIR app
				if(outputPath !== null)
				{
					//if an output compiler option is specified, use the
					//version of the AIR application descriptor that is copied
					//to the output directory.
					let appDescriptorBaseName = path.basename(appDescriptorPath);
					let outputDir: string = path.dirname(outputPath);
					program = path.join(outputDir, appDescriptorBaseName);
				}
				else
				{
					//if there is no output compiler option, default to the
					//original path of the AIR application descriptor.
					program = appDescriptorPath;
				}
			}
			else if(requireAIR)
			{
				vscode.window.showErrorMessage("Failed to debug SWF. Field \"application\" is missing in asconfig.json.");
				return null;
			}
			else if(outputPath !== null)
			{
				//if the output compiler option is specified, then that's what
				//we'll launch.
				program = outputPath;
			}
			else if(mainClassPath !== null)
			{
				//if no output compiler option is specified, the compiler
				//defaults to using the same file name as the main class, but
				//with the .swf extension instead. the .swf file goes into the
				//the same directory as the main class
				let extension = path.extname(mainClassPath);
				program = mainClassPath.substr(0, mainClassPath.length - extension.length) + FILE_EXTENSION_SWF;
			}
			else
			{
				//we tried our best to guess the program to launch, but there's
				//simply not enough information in asconfig.json.
				//we'll provide a suggestion to use the output compiler option.
				vscode.window.showErrorMessage("Failed to debug SWF. Missing \"output\" compiler option in asconfig.json.");
				return null;
			}
		}
		if(!program)
		{
			///this shouldn't happen, but in case there's a bug,
			//this will catch any missing programs
			vscode.window.showErrorMessage("Failed to debug SWF. Program not found.");
			return null;
		}
		if(isMobile && !debugConfiguration.profile)
		{
			//save the user from having to specify the profile manually
			debugConfiguration.profile = PROFILE_MOBILE_DEVICE;
		}
		debugConfiguration.program = program;
		return debugConfiguration;
	}
}