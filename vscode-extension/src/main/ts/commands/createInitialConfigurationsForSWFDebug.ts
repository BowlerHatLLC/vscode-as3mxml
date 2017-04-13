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
import * as fs from "fs";
import * as path from "path";
import * as vscode from "vscode";

/*
 * Default configuration for SWF debugging
 */
const initialConfigurations =
[
	{
		type: "swf",
		request: "launch",
		name: "Launch SWF",
		program: "${workspaceRoot}/Main.swf"
	}
];

export default function(): string
{
	let program: string = null;

	//see if we can find the SWF file
	if(vscode.workspace.rootPath)
	{
		let asconfigPath = path.resolve(vscode.workspace.rootPath, "asconfig.json");
		if(fs.existsSync(asconfigPath))
		{
			try
			{
				let asconfigFile = fs.readFileSync(asconfigPath, "utf8");
				let asconfigJSON = JSON.parse(asconfigFile);
				let appDescriptorPath: string = null;
				let outputPath: string = null;
				if("application" in asconfigJSON)
				{
					appDescriptorPath = asconfigJSON.application;
				}
				if("compilerOptions" in asconfigJSON)
				{
					let compilerOptions = asconfigJSON.compilerOptions;
					if("output" in compilerOptions)
					{
						outputPath = asconfigJSON.compilerOptions.output;
					}
				}
				if(appDescriptorPath !== null)
				{
					let appDescriptorBaseName = path.basename(appDescriptorPath);
					let outputDir: string = null;
					if(outputPath !== null)
					{
						outputDir = path.dirname(outputPath);
					}
					if(outputDir !== null)
					{
						program = path.join(outputDir, appDescriptorBaseName);
					}
				}
				else if(outputPath)
				{
					program = outputPath;
				}
			}
			catch(error)
			{
				//we couldn't find the output path
			}
		}
	}

	if (program !== null)
	{
		initialConfigurations.forEach((config) =>
		{
			if(config["program"])
			{
				config["program"] = program;
			}
		});
	}
	//add an additional tab
	let configurationsMassaged = JSON.stringify(initialConfigurations, null, "\t")
		.split("\n").map(line => "\t" + line).join("\n").trim();
	return [
		"{",
		"\t// Use IntelliSense to learn about possible SWF debug attributes.",
		"\t// Hover to view descriptions of existing attributes.",
		"\t// For more information, visit: https://go.microsoft.com/fwlink/?linkid=830387",
		"\t\"version\": \"0.2.0\",",
		"\t\"configurations\": " + configurationsMassaged,
		"}"
	].join("\n");
}