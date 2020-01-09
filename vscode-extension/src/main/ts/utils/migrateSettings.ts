
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

const SECTION_NEXTGENAS = "nextgenas";
const SECTION_AS3MXML = "as3mxml";
const SECTION_SDK__FRAMEWORK = "sdk.framework";
const SECTION_SDK__EDITOR = "sdk.editor";
const SECTION_SDK__SEARCH_PATHS = "sdk.searchPaths";
const SECTION_SDK__ASCONFIGC__USE_BUNDLED = "asconfigc.useBundled";
const SECTION_JAVA = "java";
const SECTION_JAVA__PATH = "java.path";

export default function migrateSettings()
{
	let newConfig = vscode.workspace.getConfiguration(SECTION_AS3MXML);
	let oldConfig = vscode.workspace.getConfiguration(SECTION_NEXTGENAS);
	migrateSetting(oldConfig, newConfig, SECTION_SDK__FRAMEWORK);
	migrateSetting(oldConfig, newConfig, SECTION_SDK__EDITOR);
	migrateSetting(oldConfig, newConfig, SECTION_SDK__SEARCH_PATHS);
	migrateSetting(oldConfig, newConfig, SECTION_SDK__ASCONFIGC__USE_BUNDLED);
	migrateSetting(oldConfig, newConfig, SECTION_JAVA, SECTION_JAVA__PATH);
}

function migrateSetting(oldConfig: vscode.WorkspaceConfiguration, newConfig: vscode.WorkspaceConfiguration, oldSection: string, newSection?: string)
{
	if(!newSection)
	{
		newSection = oldSection;
	}
	let oldSectionInspect = oldConfig.inspect(oldSection);
	let newSectionInspect = newConfig.inspect(newSection);
	if(oldSectionInspect && oldSectionInspect.globalValue)
	{
		//don't overwrite an existing value
		if(!newSectionInspect || !newSectionInspect.globalValue)
		{
			newConfig.update(newSection, oldSectionInspect.globalValue, vscode.ConfigurationTarget.Global);
		}
		oldConfig.update(oldSection, undefined, vscode.ConfigurationTarget.Global);
	}
	if(oldSectionInspect && oldSectionInspect.workspaceValue)
	{
		//don't overwrite an existing value
		if(!newSectionInspect || !newSectionInspect.workspaceValue)
		{
			newConfig.update(newSection, oldSectionInspect.workspaceValue, vscode.ConfigurationTarget.Workspace);
		}
		oldConfig.update(oldSection, undefined, vscode.ConfigurationTarget.Workspace);
	}
	if(oldSectionInspect && oldSectionInspect.workspaceFolderValue)
	{
		//don't overwrite an existing value
		if(!newSectionInspect || !newSectionInspect.workspaceFolderValue)
		{
			newConfig.update(newSection, oldSectionInspect.workspaceFolderValue, vscode.ConfigurationTarget.WorkspaceFolder);
		}
		oldConfig.update(oldSection, undefined, vscode.ConfigurationTarget.WorkspaceFolder);
	}
}