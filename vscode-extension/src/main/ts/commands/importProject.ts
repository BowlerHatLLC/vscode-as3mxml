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
import * as fbImport from "./importFlashBuilderProject";
import * as fdImport from "./importFlashDevelopProject";

const FILE_NAME_ASCONFIG_JSON = "asconfig.json";

const MESSAGE_DETECT_PROJECT = "Import existing ActionScript & MXML projects from Adobe Flash Builder or FlashDevelop?";
const MESSAGE_DETECT_PROJECT2 = "Import more Adobe Flash Builder projects?";
const MESSAGE_CHOOSE_PROJECT = "Choose a project to import";
const MESSAGE_CHOOSE_FORMAT = "Choose the format of the project to import.";
const ERROR_PROJECT_HAS_ASCONFIG = "No new ActionScript & XML projects found in workspace. If a project already contains asconfig.json, it cannot be imported from another format.";
const ERROR_NO_PROJECTS = "No Adobe Flash Builder or FlashDevelop projects found in workspace.";
const ERROR_NO_FLASH_BUILDER_PROJECTS = "No Adobe Flash Builder projects found in workspace.";
const ERROR_NO_FLASH_DEVELOP_PROJECTS = "No FlashDevelop projects found in workspace.";
const BUTTON_LABEL_IMPORT = "Import";
const BUTTON_LABEL_NO_IMPORT = "Don't Import";
const BUTTON_LABEL_FLASH_BUILDER = "Flash Builder";
const BUTTON_LABEL_FLASH_DEVELOP = "FlashDevelop";

export function checkForProjectsToImport()
{
	if(!shouldPromptToImport())
	{
		return;
	}

	let workspaceFolders = vscode.workspace.workspaceFolders.filter((folder) =>
	{
		if(isVSCodeProject(folder))
		{
			return false;
		}
		return fbImport.isFlashBuilderProject(folder) || fdImport.isFlashDevelopProject(folder);
	});
	if(workspaceFolders.length === 0)
	{
		return;
	}
	promptToImportWorkspaceFolders(workspaceFolders);
}

async function promptToImportWorkspaceFolders(workspaceFolders: vscode.WorkspaceFolder[])
{
	let importedOne = false;
	while(workspaceFolders.length > 0)
	{
		let message = importedOne ? MESSAGE_DETECT_PROJECT2 : MESSAGE_DETECT_PROJECT;
		let value = await vscode.window.showInformationMessage(
			message,
			BUTTON_LABEL_IMPORT, BUTTON_LABEL_NO_IMPORT);
		if(value == BUTTON_LABEL_NO_IMPORT)
		{
			break;
		}
		let importedFolder = await pickProjectInWorkspaceFolders(workspaceFolders);
		if(!importedFolder)
		{
			break;
		}
		workspaceFolders = workspaceFolders.filter((folder) =>
		{
			return folder !== importedFolder;
		});
		importedOne = true;
	}
}

async function pickProjectInWorkspaceFolders(workspaceFolders: vscode.WorkspaceFolder[])
{
	if(workspaceFolders.length === 1)
	{
		return await importProjectInWorkspaceFolder(workspaceFolders[0]);
	}
	else
	{
		let items = workspaceFolders.map((folder) =>
		{
			return { label: folder.name, description: folder.uri.fsPath, folder};
		})
		let result = await vscode.window.showQuickPick(items, { placeHolder: MESSAGE_CHOOSE_PROJECT });
		if(!result)
		{
			//it's possible for no format to be chosen with showQuickPick()
			return null;
		}
		return await importProjectInWorkspaceFolder(result.folder);
	}
}

async function importProjectInWorkspaceFolder(workspaceFolder: vscode.WorkspaceFolder)
{
	let isFlashBuilder = fbImport.isFlashBuilderProject(workspaceFolder);
	let isFlashDevelop = fdImport.isFlashDevelopProject(workspaceFolder);
	if(isFlashBuilder && isFlashDevelop)
	{
		let result = await vscode.window.showQuickPick([
			{ label: BUTTON_LABEL_FLASH_BUILDER },
			{ label: BUTTON_LABEL_FLASH_DEVELOP }
		], { placeHolder: MESSAGE_CHOOSE_FORMAT });
		switch(result.label)
		{
			case BUTTON_LABEL_FLASH_BUILDER:
			{
				isFlashDevelop = false;
			}
			case BUTTON_LABEL_FLASH_DEVELOP:
			{
				isFlashBuilder = false;
			}
			default:
			{
				//it's possible for no format to be chosen with showQuickPick()
				return null;
			}
		}
	}
	if(isFlashBuilder)
	{
		fbImport.importFlashBuilderProject(workspaceFolder);
		return workspaceFolder;
	}
	else if(isFlashDevelop)
	{	
		fdImport.importFlashDevelopProject(workspaceFolder);
		return workspaceFolder;
	}
	return null;
}

function notifyNoProjectsToImport(flashBuilder: boolean, flashDevelop: boolean)
{
	if(flashBuilder && flashDevelop)
	{
		vscode.window.showErrorMessage(ERROR_NO_PROJECTS);
	}
	else if(flashBuilder)
	{
		vscode.window.showErrorMessage(ERROR_NO_FLASH_BUILDER_PROJECTS);
	}
	else if(flashDevelop)
	{
		vscode.window.showErrorMessage(ERROR_NO_FLASH_DEVELOP_PROJECTS);
	}
}

export function pickProjectInWorkspace(flashBuilder: boolean, flashDevelop: boolean)
{
	let workspaceFolders = vscode.workspace.workspaceFolders;
	if(!workspaceFolders)
	{
		notifyNoProjectsToImport(flashBuilder, flashDevelop);
		return;
	}

	workspaceFolders = workspaceFolders.filter((folder) =>
	{
		if(flashBuilder && fbImport.isFlashBuilderProject(folder))
		{
			return true;
		}
		if(flashDevelop && fdImport.isFlashDevelopProject(folder))
		{
			return true;
		}
		return false;
	});
	if(workspaceFolders.length === 0)
	{
		notifyNoProjectsToImport(flashBuilder, flashDevelop);
		return;
	}

	workspaceFolders = workspaceFolders.filter((folder) =>
	{
		return !isVSCodeProject(folder);
	});
	if(workspaceFolders.length === 0)
	{
		vscode.window.showErrorMessage(ERROR_PROJECT_HAS_ASCONFIG);
		return;
	}

	pickProjectInWorkspaceFolders(workspaceFolders);
}

function isVSCodeProject(folder: vscode.WorkspaceFolder)
{
	let asconfigPath = path.resolve(folder.uri.fsPath, FILE_NAME_ASCONFIG_JSON);
	return fs.existsSync(asconfigPath) && !fs.statSync(asconfigPath).isDirectory();
}

function shouldPromptToImport()
{
	if(vscode.workspace.workspaceFolders === undefined)
	{
		return false;
	}
	let as3mxmlConfig = vscode.workspace.getConfiguration("as3mxml");
	return as3mxmlConfig.get("projectImport.prompt");
}

function onDidChangeWorkspaceFolders(event: vscode.WorkspaceFoldersChangeEvent)
{
	let added = event.added.filter((folder) =>
	{
		return fbImport.isFlashBuilderProject(folder) && !isVSCodeProject(folder);
	});
	if(added.length === 0)
	{
		return;
	}
	checkForProjectsToImport();
}
vscode.workspace.onDidChangeWorkspaceFolders(onDidChangeWorkspaceFolders);