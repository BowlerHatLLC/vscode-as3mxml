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
import * as child_process from "child_process";

const EXECUTABLE_WINDOWS_FCSH = "fcsh.bat";
const EXECUTABLE_UNIX_FCSH = "fcsh";
const OUTPUT_ID = "fcsh: Assigned "; //fcsh: Assigned 1 as the compile target id
const OUTPUT_PROMPT_FCSH = "(fcsh) ";
const OUTPUT_COLUMN_NUMBER = "): col: ";
const OUTPUT_PROBLEM_TYPE_ERROR = "Error: ";
const ERROR_CANNOT_FIND_FCSH = "Flex Compiler Shell (fcsh) not found in the workspace's SDK.";

let fcshPath: string = null;
let fcshProcess: child_process.ChildProcess = null;
let outputChannel: vscode.OutputChannel = null;
let waitingForInput = false;
let compileID: string = null;

function executeCommand(command: string)
{
	waitingForInput = false;
	outputChannel.appendLine(command);
	fcshProcess.stdin.write(command + "\n");
}

export default function buildWithFlexCompilerShell(workspaceFolder: vscode.WorkspaceFolder, javaExecutablePath: string, frameworkSDKHome: string, debugBuild: boolean)
{
	if(outputChannel === null)
	{
		outputChannel = vscode.window.createOutputChannel("Flex Compiler Shell (fcsh)");
	}
	outputChannel.show();
	let executable = EXECUTABLE_UNIX_FCSH;
	if(process.platform === "win32")
	{
		executable = EXECUTABLE_WINDOWS_FCSH;
	}
	let newFcshPath = path.join(frameworkSDKHome, "lib", "fcsh.jar");
	if(newFcshPath !== fcshPath)
	{
		fcshPath = newFcshPath;
		compileID = null;
		waitingForInput = false;
		if(fcshProcess !== null)
		{
			//if the path doesn't match, and the process was previously
			//started, exit so that we can launch the new executable.
			let command = "exit\n";
			outputChannel.append(command);
			fcshProcess.stdin.write(command);
			fcshProcess = null;
		}
	}

	return new Promise((resolve, reject) =>
	{
		if(!fs.existsSync(fcshPath))
		{
			vscode.window.showErrorMessage(ERROR_CANNOT_FIND_FCSH);
			reject();
			return;
		}

		let hasErrors = false;
		let waitingForStart = false;
		if(fcshProcess === null)
		{
			waitingForStart = true;
			let fcshArgs =
			[
				"-Dapplication.home=" + frameworkSDKHome,
				"-Djava.util.Arrays.useLegacyMergeSort=true",
				"-jar",
				fcshPath,
			];
			fcshProcess = child_process.spawn(javaExecutablePath, fcshArgs, { cwd: workspaceFolder.uri.fsPath });
		}

		let stdout_onData = (chunk: Uint8Array) =>
		{
			let text = String.fromCharCode.apply(null, chunk) as string;
			if(text.startsWith(OUTPUT_ID))
			{
				let idText = text.substr(OUTPUT_ID.length);
				compileID = idText.substr(0, idText.indexOf(" "));
			}
			waitingForInput = text.endsWith(OUTPUT_PROMPT_FCSH);
			if(waitingForInput)
			{
				if(waitingForStart)
				{
					waitingForStart = false;
					outputChannel.append(text);
					let command = "mxmlc"; //TODO: replace with real command
					executeCommand(command);
				}
				else
				{
					//stop listening until next time
					fcshProcess.stdout.removeListener("data", stdout_onData);
					fcshProcess.stderr.removeListener("data", stderr_onData);
					if(hasErrors)
					{
						outputChannel.appendLine("");
						outputChannel.appendLine("Build failed.");
						reject();
						return;
					}
					else
					{
						outputChannel.appendLine("");
						outputChannel.appendLine("Build complete.");
						resolve();
						return;
					}
				}
			}
			else
			{
				outputChannel.append(text);
			}
		};
		
		let stderr_onData = (chunk: string) =>
		{
			let text = String.fromCharCode.apply(null, chunk) as string;
			if(text.startsWith(OUTPUT_PROBLEM_TYPE_ERROR))
			{
				hasErrors = true;
			}
			let index = text.indexOf(OUTPUT_COLUMN_NUMBER);
			if(index !== -1)
			{
				index = text.indexOf(" ", index + OUTPUT_COLUMN_NUMBER.length);
				if(index !== -1)
				{
					let problemText = text.substr(index + 1); 
					index = problemText.indexOf(OUTPUT_PROBLEM_TYPE_ERROR);
					if(index === 0)
					{
						hasErrors = true;
					}
				}
			}
			outputChannel.append(text);
		};
		
		fcshProcess.stdout.addListener("data", stdout_onData);
		fcshProcess.stderr.addListener("data", stderr_onData);

		if(!waitingForStart && waitingForInput)
		{
			waitingForInput = false;
			outputChannel.clear();
			outputChannel.append(OUTPUT_PROMPT_FCSH);
			executeCommand("compile " + compileID);
		}
	});
}