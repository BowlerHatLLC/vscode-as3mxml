/*
Copyright 2016-2023 Bowler Hat LLC

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
import selectWorkspaceSDK from "./selectWorkspaceSDK";
import validateFrameworkSDK from "../utils/validateFrameworkSDK";

const FILE_ASCONFIG_JSON = "asconfig.json";
const FILE_SETTINGS_JSON = "settings.json";
const FILE_LAUNCH_JSON = "launch.json";
const FILE_EXTENSION_AS = ".as";
const FILE_EXTENSION_SWF = ".swf";

interface WorkspaceFolderQuickPickItem extends vscode.QuickPickItem {
  workspaceFolder?: vscode.WorkspaceFolder;
}

export function createNewProject() {
  var workspaceFolders = vscode.workspace.workspaceFolders;
  if (workspaceFolders == null) {
    openEmptyFolderAndCreateNewProject();
    return;
  }

  var items: Array<WorkspaceFolderQuickPickItem> = [];
  for (let workspaceFolder of workspaceFolders) {
    items.push({
      label: workspaceFolder.name,
      detail: workspaceFolder.uri.fsPath,
      workspaceFolder: workspaceFolder,
    });
  }
  items.push({ label: "Open Folder…" });
  vscode.window
    .showQuickPick(items, { title: "Create new project in folder…" })
    .then(
      async (result) => {
        if (!result) {
          // nothing was chosen
          return;
        }
        if (!result.workspaceFolder) {
          openEmptyFolderAndCreateNewProject();
          return;
        }
        try {
          await createNewProjectAtUri(result.workspaceFolder.uri);
        } catch (e: any) {
          console.error(e);
          vscode.window.showErrorMessage("Project creation failed with error");
        }
        return;
      },
      (reason) => {}
    );
}

function openEmptyFolderAndCreateNewProject() {
  return vscode.window
    .showOpenDialog({
      title: "Open empty folder for new project…",
      canSelectFiles: false,
      canSelectMany: false,
      canSelectFolders: true,
    })
    .then(
      async (uris) => {
        if (uris == null || uris.length == 0) {
          return;
        }
        let result = false;
        const uri = uris[0];
        try {
          result = await createNewProjectAtUri(uri);
        } catch (e: any) {
          console.error(e);
          vscode.window.showErrorMessage("Project creation failed with error");
          result = false;
        }
        if (!result) {
          return;
        }
        await vscode.commands.executeCommand("vscode.openFolder", uri);
        return;
      },
      (reason) => {}
    );
}

async function createNewProjectAtUri(uri: vscode.Uri): Promise<boolean> {
  const projectRoot = uri.fsPath;

  if (fs.readdirSync(projectRoot).length > 0) {
    vscode.window.showErrorMessage(
      "Project creation failed. To create a new ActionScript & MXML project, the target directory must be empty."
    );
    return Promise.resolve(false);
  }

  const sdkSettingValue = vscode.workspace
    .getConfiguration("as3mxml", uri)
    .get("sdk.framework") as string | undefined;
  const sdkUri = sdkSettingValue
    ? vscode.Uri.file(sdkSettingValue)
    : await selectWorkspaceSDK(false);

  if (!sdkUri) {
    return Promise.resolve(false);
  }

  const sdkPath = sdkUri.fsPath;
  if (!validateFrameworkSDK(sdkPath)) {
    vscode.window.showErrorMessage(`ActionScript SDK not valid: ${sdkPath}`);
    return Promise.resolve(false);
  }

  fs.mkdirSync(projectRoot, { recursive: true });
  const srcPath = path.resolve(projectRoot, "src");
  fs.mkdirSync(srcPath, { recursive: true });
  const libsPath = path.resolve(projectRoot, "libs");
  fs.mkdirSync(libsPath, { recursive: true });
  const vscodePath = path.resolve(projectRoot, ".vscode");
  fs.mkdirSync(vscodePath, { recursive: true });

  const settingsJsonPath = path.resolve(vscodePath, FILE_SETTINGS_JSON);
  const settingsJsonContents = `{
	"as3mxml.sdk.framework": "${sdkPath}"
}`;
  fs.writeFileSync(settingsJsonPath, settingsJsonContents, {
    encoding: "utf8",
  });

  const launchJsonPath = path.resolve(vscodePath, FILE_LAUNCH_JSON);
  const launchJsonContents = `{
	// Use IntelliSense to learn about possible attributes.
	// Hover to view descriptions of existing attributes.
	// For more information, visit: https://go.microsoft.com/fwlink/?linkid=830387
	"version": "0.2.0",
	"configurations": [
		{
			"type": "swf",
			"request": "launch",
			"name": "Launch SWF"
		}
	]
}`;
  fs.writeFileSync(launchJsonPath, launchJsonContents, { encoding: "utf8" });

  // generate the main class name from the project name
  let mainClassName = path
    .basename(uri.fsPath)
    // remove invalid characters
    .replace(/[^A-Za-z0-9_\$]/g, "")
    // remove invalid characters at beginning
    .replace(/^[\$_0-9]+/, "");
  if (mainClassName.length == 0) {
    // if we replaced every single character, fall back to Main
    mainClassName = "Main";
  }
  const swfFileName = `${mainClassName}${FILE_EXTENSION_SWF}`;
  const descriptorFileName = `${mainClassName}-app.xml`;
  const mainClassFileName = `${mainClassName}${FILE_EXTENSION_AS}`;

  const descriptorTemplatePath = sdkPath
    ? path.resolve(sdkPath, "templates/air/descriptor-template.xml")
    : undefined;
  const airDescriptorContents = createAirDescriptor(
    descriptorTemplatePath,
    swfFileName,
    mainClassName,
    mainClassName
  );
  if (airDescriptorContents) {
    const descriptorOutputPath = path.resolve(srcPath, descriptorFileName);
    fs.writeFileSync(descriptorOutputPath, airDescriptorContents, {
      encoding: "utf8",
    });
  }

  const asconfigContents = createAsconfigJson(
    mainClassName,
    swfFileName,
    airDescriptorContents ? descriptorFileName : null,
    false
  );
  const asconfigPath = path.resolve(projectRoot, FILE_ASCONFIG_JSON);
  fs.writeFileSync(asconfigPath, asconfigContents, { encoding: "utf8" });

  const mainClassOutputPath = path.resolve(srcPath, `${mainClassFileName}`);
  const mainClassContents = createMainClassAS3(mainClassName);
  fs.writeFileSync(mainClassOutputPath, mainClassContents, {
    encoding: "utf8",
  });

  return Promise.resolve(true);
}

function createAirDescriptor(
  descriptorTemplatePath: string,
  swfFileName: string,
  applicationId: string,
  fileName: string
): string | null {
  if (!fs.existsSync(descriptorTemplatePath)) {
    return null;
  }
  let descriptorContents = fs.readFileSync(descriptorTemplatePath, {
    encoding: "utf8",
  });
  // (?!\s*-->) ignores lines that are commented out
  descriptorContents = descriptorContents.replace(
    /<content>.*<\/content>(?!\s*-->)/,
    `<content>${swfFileName}</content>`
  );
  descriptorContents = descriptorContents.replace(
    /<id>.*<\/id>(?!\s*-->)/,
    `<id>${applicationId}</id>`
  );
  descriptorContents = descriptorContents.replace(
    /<filename>.*<\/filename>(?!\s*-->)/,
    `<filename>${fileName}</filename>`
  );
  descriptorContents = descriptorContents.replace(
    /<!-- <visible><\/visible> -->/,
    `<visible>true</visible>`
  );

  return descriptorContents;
}

function createAsconfigJson(
  mainClassName: string,
  swfFileName: string,
  descriptorFileName: string | null,
  mobile: boolean
): string {
  return `{
	"config": ${mobile ? `"airmobile"` : `"air"`},
	"compilerOptions": {
		"source-path": [
			"src"
		],
		"library-path": [
			"libs"
		],
		"output": "bin/${swfFileName}"
	},${descriptorFileName ? `\n\t"application": "src/${descriptorFileName}",` : ""}
	"mainClass": "${mainClassName}"
}
`;
}

function createMainClassAS3(mainClassName: string): string {
  return `package
{
	import flash.display.Sprite;
	import flash.text.TextField;
	import flash.text.TextFieldAutoSize;
	import flash.text.TextFormat;

	public class ${mainClassName} extends Sprite
	{
		public function ${mainClassName}()
		{
			super();

			var textField:TextField = new TextField();
			textField.autoSize = TextFieldAutoSize.LEFT;
			textField.defaultTextFormat = new TextFormat("_sans", 12, 0x0000cc);
			textField.text = "Hello, Adobe AIR!";
			addChild(textField);
		}
	}
}
`;
}
