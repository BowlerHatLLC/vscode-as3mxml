/*
Copyright 2016-2020 Bowler Hat LLC

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
import parseXML = require("@rgrove/parse-xml");

const FILE_EXTENSION_AS3PROJ = ".as3proj";
const FILE_ASCONFIG_JSON = "asconfig.json";

const MESSAGE_IMPORT_START = "🚀 Importing FlashDevelop project...";
const MESSAGE_IMPORT_COMPLETE = "✅ Import complete.";
const MESSAGE_IMPORT_FAILED = "❌ Import failed."

const ERROR_NO_FOLDER = "Workspace folder parameter is missing.";
const ERROR_NO_PROJECTS = "No FlashDevelop projects found in workspace.";
const ERROR_FILE_READ = "Failed to read file: ";
const ERROR_XML_PARSE = "Failed to parse FlashDevelop project. Invalid XML in file: ";
const ERROR_PROJECT_PARSE = "Failed to parse FlashDevelop project: ";

const CHANNEL_NAME_IMPORTER = "FlashDevelop Importer";

export function isFlashDevelopProject(folder: vscode.WorkspaceFolder)
{
	let idealProjectPath = path.resolve(folder.uri.fsPath, folder.name + FILE_EXTENSION_AS3PROJ);
	if(fs.existsSync(idealProjectPath))
	{
		return true;
	}
	return fs.readdirSync(folder.uri.fsPath).some((file) =>
	{
		return path.extname(file) === FILE_EXTENSION_AS3PROJ;
	});
}

function findProjectFile(folder: vscode.WorkspaceFolder)
{
	let idealProjectPath = path.resolve(folder.uri.fsPath, folder.name + FILE_EXTENSION_AS3PROJ);
	if(fs.existsSync(idealProjectPath))
	{
		return idealProjectPath;
	}
	let fileName = fs.readdirSync(folder.uri.fsPath).find((file) =>
	{
		return path.extname(file) === FILE_EXTENSION_AS3PROJ;
	});
	if(fileName === undefined)
	{
		return null;
	}
	return path.resolve(folder.uri.fsPath, fileName)
}

export function importFlashDevelopProject(workspaceFolder: vscode.WorkspaceFolder)
{
	getOutputChannel().clear();
	getOutputChannel().appendLine(MESSAGE_IMPORT_START);
	getOutputChannel().show();
	let result = importFlashDevelopProjectInternal(workspaceFolder);
	if(result)
	{
		getOutputChannel().appendLine(MESSAGE_IMPORT_COMPLETE);
	}
	else
	{
		getOutputChannel().appendLine(MESSAGE_IMPORT_FAILED);
	}
}

function importFlashDevelopProjectInternal(workspaceFolder: vscode.WorkspaceFolder)
{
	if(!workspaceFolder)
	{
		addError(ERROR_NO_FOLDER);
		return false;
	}
	let projectFilePath = findProjectFile(workspaceFolder);
	if(!projectFilePath)
	{
		addError(ERROR_NO_PROJECTS);
		return false;
	}

	let projectText = null;
	try
	{
		projectText = fs.readFileSync(projectFilePath, "utf8");
	}
	catch(error)
	{
		addError(ERROR_FILE_READ + projectFilePath);
		return false;
	}
	console.log("*** " + projectText);
	let project = null;
	try
	{
		let parsedXML = parseXML(projectText);
		project = parsedXML.children[0];
	}
	catch(error)
	{
		addError(ERROR_XML_PARSE + projectFilePath);
		return false;
	}

	try
	{
		let result = createProjectFiles(workspaceFolder.uri.fsPath, projectFilePath, project);
		if(!result)
		{
			return false;
		}
	}
	catch(error)
	{
		addError(ERROR_PROJECT_PARSE + projectFilePath);
		if(error instanceof Error)
		{
			getOutputChannel().appendLine(error.stack);
		}
		return false;
	}

	return true;
}

function createProjectFiles(folderPath: string, projectFilePath: string, project: any)
{
	let result: any =
	{
		compilerOptions: {},
	};
	
	let appName = path.basename(projectFilePath);
	let fileName = FILE_ASCONFIG_JSON;
	let asconfigPath = path.resolve(folderPath, fileName);
	let resultText = JSON.stringify(result, undefined, "\t");
	fs.writeFileSync(asconfigPath, resultText);

	vscode.workspace.openTextDocument(asconfigPath).then((document) =>
	{
		vscode.window.showTextDocument(document)
	});

	getOutputChannel().appendLine(appName + " ➡ " + fileName);
	return true;
}

let outputChannel: vscode.OutputChannel;

function getOutputChannel()
{
	if(!outputChannel)
	{
		outputChannel = vscode.window.createOutputChannel(CHANNEL_NAME_IMPORTER);
	}
	return outputChannel;
}

function addWarning(message: string)
{
	getOutputChannel().appendLine("🚧 " + message);
}

function addError(message: string)
{
	getOutputChannel().appendLine("⛔ " + message);
}