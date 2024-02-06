/*
Copyright 2016-2024 Bowler Hat LLC

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
import validateRoyale from "../utils/validateRoyale";
import validateFlex from "../utils/validateFlex";
import validateFeathers from "../utils/validateFeathers";

const FILE_ASCONFIG_JSON = "asconfig.json";
const FILE_SETTINGS_JSON = "settings.json";
const FILE_LAUNCH_JSON = "launch.json";
const FILE_EXTENSION_AS = ".as";
const FILE_EXTENSION_MXML = ".mxml";
const FILE_EXTENSION_SWF = ".swf";

interface WorkspaceFolderQuickPickItem extends vscode.QuickPickItem {
  workspaceFolder?: vscode.WorkspaceFolder;
  mode?: "open" | "add";
}

interface ProjectQuickPickItem {
  flex?: boolean;
  royale?: boolean;
  feathers?: boolean;
  mobile?: boolean;
}

export function createNewProject() {
  var workspaceFolders = vscode.workspace.workspaceFolders;
  if (workspaceFolders == null) {
    openOrAddFolderAndCreateNewProject(false);
    return;
  }

  var items: Array<WorkspaceFolderQuickPickItem> = [];
  for (let workspaceFolder of workspaceFolders) {
    const asconfigJsonPath = path.resolve(
      workspaceFolder.uri.fsPath,
      FILE_ASCONFIG_JSON
    );
    if (fs.existsSync(asconfigJsonPath)) {
      // already contains a project
      continue;
    }
    items.push({
      label: workspaceFolder.name,
      detail: workspaceFolder.uri.fsPath,
      workspaceFolder: workspaceFolder,
    });
  }
  items.push({ label: "Open Folder…", mode: "open" });
  items.push({ label: "Add Folder to Workspace…", mode: "add" });
  vscode.window
    .showQuickPick(items, { title: "Create new project in folder…" })
    .then(
      async (result) => {
        if (!result) {
          // nothing was chosen
          return;
        }
        if (!result.workspaceFolder) {
          openOrAddFolderAndCreateNewProject(result.mode === "add");
          return;
        }
        try {
          await createNewProjectAtUri(result.workspaceFolder.uri, true);
        } catch (e: any) {
          console.error(e);
          vscode.window.showErrorMessage("Project creation failed with error");
        }
        return;
      },
      (reason) => {}
    );
}

function openOrAddFolderAndCreateNewProject(addToWorkspace: boolean) {
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
          result = await createNewProjectAtUri(uri, addToWorkspace);
        } catch (e: any) {
          console.error(e);
          vscode.window.showErrorMessage("Project creation failed with error");
          result = false;
        }
        if (!result) {
          return;
        }
        if (addToWorkspace) {
          if (
            !vscode.workspace.updateWorkspaceFolders(
              vscode.workspace.workspaceFolders.length,
              0,
              { uri: uri }
            )
          ) {
            vscode.window.showErrorMessage(
              "Project creation failed. Folder not added to workspace."
            );
            return;
          }
        } else {
          await vscode.commands.executeCommand("vscode.openFolder", uri);
        }
        return;
      },
      (reason) => {}
    );
}

async function createNewProjectAtUri(
  uri: vscode.Uri,
  detectExistingSDK: boolean
): Promise<boolean> {
  const projectRoot = uri.fsPath;

  if (fs.readdirSync(projectRoot).length > 0) {
    vscode.window.showErrorMessage(
      "Project creation failed. To create a new ActionScript & MXML project, the target directory must be empty."
    );
    return Promise.resolve(false);
  }

  let sdkSettingValue: string | undefined = undefined;
  if (detectExistingSDK) {
    sdkSettingValue = vscode.workspace
      .getConfiguration("as3mxml", uri)
      .get("sdk.framework") as string | undefined;
  }
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

  const isFeathers = validateFeathers(sdkPath);
  const isRoyale = validateRoyale(sdkPath);
  const isFlex = !isFeathers && !isRoyale && validateFlex(sdkPath);
  let projectType: ProjectQuickPickItem = {};

  if (isFlex) {
    await vscode.window
      .showQuickPick(
        [
          { label: "Apache Flex Desktop", flex: true },
          { label: "Apache Flex Mobile", flex: true, mobile: true },
          { label: "ActionScript Desktop" },
          { label: "ActionScript Mobile", mobile: true },
        ] as (vscode.QuickPickItem & ProjectQuickPickItem)[],
        {
          title: "Select a project type…",
        }
      )
      .then((result) => (projectType = result));
  } else if (isRoyale) {
    projectType = { royale: true };
  } else if (isFeathers) {
    await vscode.window
      .showQuickPick(
        [
          { label: "Feathers SDK Desktop", feathers: true },
          { label: "Feathers SDK Mobile", feathers: true, mobile: true },
          { label: "ActionScript Desktop" },
          { label: "ActionScript Mobile", mobile: true },
        ] as (vscode.QuickPickItem & ProjectQuickPickItem)[],
        {
          title: "Select a project type…",
        }
      )
      .then((result) => (projectType = result));
  } else {
    await vscode.window
      .showQuickPick(
        [
          { label: "ActionScript Desktop" },
          { label: "ActionScript Mobile", mobile: true },
        ] as (vscode.QuickPickItem & ProjectQuickPickItem)[],
        {
          title: "Select a project type…",
        }
      )
      .then((result) => (projectType = result));
  }

  if (!projectType) {
    return;
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
	"as3mxml.sdk.framework": "${sdkPath.replace(/\\/g, "\\\\")}"
}`;
  fs.writeFileSync(settingsJsonPath, settingsJsonContents, {
    encoding: "utf8",
  });

  if (!isRoyale) {
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
  }

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

  let hasAirDescriptor = false;
  if (!isRoyale) {
    const descriptorTemplatePath = sdkPath
      ? path.resolve(sdkPath, "templates/air/descriptor-template.xml")
      : undefined;
    let applicationId = mainClassName
      .replace(/_/g, "-")
      .replace(/[^A-Za-z0-9\-\.]/g, "");
    if (applicationId.length == 0) {
      // if we replaced every single character, fall back to MyApplication
      applicationId = "MyApplication";
    }
    const airDescriptorContents = createAirDescriptor(
      descriptorTemplatePath,
      projectType,
      swfFileName,
      applicationId,
      mainClassName
    );
    if (airDescriptorContents) {
      hasAirDescriptor = true;
      const descriptorOutputPath = path.resolve(srcPath, descriptorFileName);
      fs.writeFileSync(descriptorOutputPath, airDescriptorContents, {
        encoding: "utf8",
      });
    }
  }

  let asconfigJsonContents: string | undefined = undefined;
  if (projectType.royale) {
    asconfigJsonContents = createAsconfigJsonRoyale(mainClassName);
  } else {
    asconfigJsonContents = createAsconfigJson(
      mainClassName,
      swfFileName,
      hasAirDescriptor ? descriptorFileName : null,
      projectType.mobile
    );
  }
  const asconfigJsonPath = path.resolve(projectRoot, FILE_ASCONFIG_JSON);
  fs.writeFileSync(asconfigJsonPath, asconfigJsonContents, {
    encoding: "utf8",
  });

  createSourceFiles(projectType, srcPath, mainClassName);

  return Promise.resolve(true);
}

function createAirDescriptor(
  descriptorTemplatePath: string,
  projectType: ProjectQuickPickItem,
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

  // set <content> value, if it isn't commented out
  descriptorContents = descriptorContents.replace(
    /<content>.*<\/content>(?!\s*-->)/,
    `<content>${swfFileName}</content>`
  );
  // set <id> value, if it isn't commented out
  descriptorContents = descriptorContents.replace(
    /<id>.*<\/id>(?!\s*-->)/,
    `<id>${applicationId}</id>`
  );
  // set <filename> value, if it isn't commented out
  descriptorContents = descriptorContents.replace(
    /<filename>.*<\/filename>(?!\s*-->)/,
    `<filename>${fileName}</filename>`
  );
  // set <visible> to true, if it isn't already populated
  // (AIR-only; other SDKs handle it automatically)
  if (
    !projectType.flex &&
    !projectType.royale &&
    !projectType.feathers &&
    !/<visible>.*<\/visible>(?!\s*-->)/.test(descriptorContents)
  ) {
    descriptorContents = descriptorContents.replace(
      /<!-- <visible><\/visible> -->/,
      `<visible>true</visible>`
    );
  }
  // set <renderMode> to direct, if it isn't already populated
  // (Feathers-only; not assumed for other SDKs)
  if (
    projectType.feathers &&
    !/<renderMode>.*<\/renderMode>(?!\s*-->)/.test(descriptorContents)
  ) {
    descriptorContents = descriptorContents.replace(
      /<!-- <renderMode>.*<\/renderMode> -->/,
      `<renderMode>direct</renderMode>`
    );
  }

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

function createAsconfigJsonRoyale(mainClassName: string): string {
  return `{
	"config": "royale",
	"compilerOptions": {
		"targets": [
			"JSRoyale"
		],
		"source-path": [
			"src"
		],
		"library-path": [
			"libs"
		]
	},
	"mainClass": "${mainClassName}"
}
`;
}

function createSourceFiles(
  projectType: ProjectQuickPickItem,
  srcPath: string,
  mainClassName: string
) {
  if (projectType.flex) {
    if (projectType.mobile) {
      createSourceFilesFlexMobile(srcPath, mainClassName);
    } else {
      createSourceFilesFlexDesktop(srcPath, mainClassName);
    }
  } else if (projectType.royale) {
    createSourceFilesRoyaleBasic(srcPath, mainClassName);
  } else if (projectType.feathers) {
    createSourceFilesFeathers(srcPath, mainClassName);
  } else {
    createSourceFilesAS3(srcPath, mainClassName);
  }
}

function createSourceFilesAS3(srcPath: string, mainClassName: string) {
  const mainClassContents = `package
{
	import flash.display.Sprite;
	import flash.display.StageAlign;
	import flash.display.StageScaleMode;
	import flash.text.TextField;
	import flash.text.TextFieldAutoSize;
	import flash.text.TextFormat;

	public class ${mainClassName} extends Sprite
	{
		public function ${mainClassName}()
		{
			super();

			stage.scaleMode = StageScaleMode.NO_SCALE;
			stage.align = StageAlign.TOP_LEFT;

			var textField:TextField = new TextField();
			textField.autoSize = TextFieldAutoSize.LEFT;
			textField.defaultTextFormat = new TextFormat("_sans", 12, 0x0000cc);
			textField.text = "Hello, Adobe AIR!";
			addChild(textField);
		}
	}
}
`;
  const mainClassOutputPath = path.resolve(
    srcPath,
    `${mainClassName}${FILE_EXTENSION_AS}`
  );
  fs.writeFileSync(mainClassOutputPath, mainClassContents, {
    encoding: "utf8",
  });
}

function createSourceFilesFlexDesktop(srcPath: string, mainClassName: string) {
  const mainClassContents = `<?xml version="1.0" encoding="utf-8"?>
<s:WindowedApplication xmlns:fx="http://ns.adobe.com/mxml/2009"
                       xmlns:s="library://ns.adobe.com/flex/spark"
                       xmlns:mx="library://ns.adobe.com/flex/mx">
	<fx:Declarations>
		<!-- Place non-visual elements (e.g., services, value objects) here -->
	</fx:Declarations>

	<s:Label text="Hello, Flex!"/>
</s:WindowedApplication>
`;
  const mainClassOutputPath = path.resolve(
    srcPath,
    `${mainClassName}${FILE_EXTENSION_MXML}`
  );
  fs.writeFileSync(mainClassOutputPath, mainClassContents, {
    encoding: "utf8",
  });
}

function createSourceFilesFlexMobile(srcPath: string, mainClassName: string) {
  const mainClassContents = `<?xml version="1.0" encoding="utf-8"?>
<s:ViewNavigatorApplication xmlns:fx="http://ns.adobe.com/mxml/2009"
                       xmlns:s="library://ns.adobe.com/flex/spark"
                       firstView="views.HomeView"
                       applicationDPI="160">
	<fx:Declarations>
		<!-- Place non-visual elements (e.g., services, value objects) here -->
	</fx:Declarations>
</s:ViewNavigatorApplication>
`;
  const mainClassOutputPath = path.resolve(
    srcPath,
    `${mainClassName}${FILE_EXTENSION_MXML}`
  );
  fs.writeFileSync(mainClassOutputPath, mainClassContents, {
    encoding: "utf8",
  });

  const viewsPath = path.resolve(srcPath, "views");
  fs.mkdirSync(viewsPath, { recursive: true });
  const homeViewClassContents = `<?xml version="1.0" encoding="utf-8"?>
<s:View xmlns:fx="http://ns.adobe.com/mxml/2009"
        xmlns:s="library://ns.adobe.com/flex/spark"
        title="Home">
	<fx:Declarations>
		<!-- Place non-visual elements (e.g., services, value objects) here -->
	</fx:Declarations>

	<s:Label text="Hello, Flex Mobile!"/>
</s:View>
`;
  const homeViewClassOutputPath = path.resolve(
    srcPath,
    `views/HomeView${FILE_EXTENSION_MXML}`
  );
  fs.writeFileSync(homeViewClassOutputPath, homeViewClassContents, {
    encoding: "utf8",
  });
}

function createSourceFilesRoyaleBasic(srcPath: string, mainClassName: string) {
  const mainClassContents = `<?xml version="1.0" encoding="utf-8"?>
<js:Application xmlns:fx="http://ns.adobe.com/mxml/2009"
                xmlns:js="library://ns.apache.org/royale/basic">
	<fx:Declarations>
	</fx:Declarations>

	<js:valuesImpl>
		<js:SimpleCSSValuesImpl/>
	</js:valuesImpl>

	<js:initialView>
		<js:View>
			<js:Label text="Hello, Apache Royale!"/>
		</js:View>
	</js:initialView>

	<fx:Script>
		<![CDATA[

		]]>
	</fx:Script>
</js:Application>
`;
  const mainClassOutputPath = path.resolve(
    srcPath,
    `${mainClassName}${FILE_EXTENSION_MXML}`
  );
  fs.writeFileSync(mainClassOutputPath, mainClassContents, {
    encoding: "utf8",
  });
}

function createSourceFilesFeathers(srcPath: string, mainClassName: string) {
  const mainClassContents = `<?xml version="1.0" encoding="utf-8"?>
<f:Application xmlns:fx="http://ns.adobe.com/mxml/2009"
                       xmlns:f="library://ns.feathersui.com/mxml">
	<fx:Declarations>
		<!-- Place non-visual elements (e.g., services, value objects) here -->
	</fx:Declarations>

	<f:Label text="Hello, Feathers!"/>
</f:Application>
`;
  const mainClassOutputPath = path.resolve(
    srcPath,
    `${mainClassName}${FILE_EXTENSION_MXML}`
  );
  fs.writeFileSync(mainClassOutputPath, mainClassContents, {
    encoding: "utf8",
  });
}
