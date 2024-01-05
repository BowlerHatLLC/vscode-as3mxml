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
import { parseXml } from "@rgrove/parse-xml";
import validateFrameworkSDK from "../utils/validateFrameworkSDK";

const FILE_EXTENSION_AS3PROJ = ".as3proj";
const FILE_ASCONFIG_JSON = "asconfig.json";
const FILE_APPLICATION_XML = "application.xml";

const MESSAGE_IMPORT_START = "🚀 Importing FlashDevelop project...";
const MESSAGE_IMPORT_COMPLETE = "✅ Import complete.";
const MESSAGE_IMPORT_FAILED = "❌ Import failed.";

const ERROR_NO_FOLDER = "Workspace folder parameter is missing.";
const ERROR_NO_PROJECTS = "No FlashDevelop projects found in workspace.";
const ERROR_FILE_READ = "Failed to read file: ";
const ERROR_XML_PARSE =
  "Failed to parse FlashDevelop project. Invalid XML in file: ";
const ERROR_PROJECT_PARSE = "Failed to parse FlashDevelop project: ";
const WARNING_INVALID_DEFINE = "Failed to parse define: ";
const WARNING_PRE_BUILD_COMMAND =
  "Custom pre-build commands are not supported. Skipping... ";
const WARNING_POST_BUILD_COMMAND =
  "Custom post-build commands are not supported. Skipping... ";
const WARNING_TEST_MOVIE_COMMAND =
  "Custom test movie commands are not supported. Skipping... ";
const WARNING_COMMAND_TASK =
  'To run this command, you may define a custom "shell" task in .vscode/tasks.json.';
const WARNING_RSL_PATHS =
  "Runtime shared libraries are not supported. Skipping... ";
const WARNING_LIBRARY_ASSETS = "Library assets are not supported. Skipping... ";

const CHANNEL_NAME_IMPORTER = "FlashDevelop Importer";

export function isFlashDevelopProject(folder: vscode.WorkspaceFolder) {
  let idealProjectPath = path.resolve(
    folder.uri.fsPath,
    folder.name + FILE_EXTENSION_AS3PROJ
  );
  if (fs.existsSync(idealProjectPath)) {
    return true;
  }
  return fs.readdirSync(folder.uri.fsPath).some((file) => {
    return path.extname(file) === FILE_EXTENSION_AS3PROJ;
  });
}

function findProjectFile(folder: vscode.WorkspaceFolder) {
  let idealProjectPath = path.resolve(
    folder.uri.fsPath,
    folder.name + FILE_EXTENSION_AS3PROJ
  );
  if (fs.existsSync(idealProjectPath)) {
    return idealProjectPath;
  }
  let fileName = fs.readdirSync(folder.uri.fsPath).find((file) => {
    return path.extname(file) === FILE_EXTENSION_AS3PROJ;
  });
  if (fileName === undefined) {
    return null;
  }
  return path.resolve(folder.uri.fsPath, fileName);
}

export function importFlashDevelopProject(
  workspaceFolder: vscode.WorkspaceFolder
) {
  getOutputChannel().clear();
  getOutputChannel().appendLine(MESSAGE_IMPORT_START);
  getOutputChannel().show(true);
  let result = importFlashDevelopProjectInternal(workspaceFolder);
  if (result) {
    getOutputChannel().appendLine(MESSAGE_IMPORT_COMPLETE);
  } else {
    getOutputChannel().appendLine(MESSAGE_IMPORT_FAILED);
  }
}

function importFlashDevelopProjectInternal(
  workspaceFolder: vscode.WorkspaceFolder
) {
  if (!workspaceFolder) {
    addError(ERROR_NO_FOLDER);
    return false;
  }
  let projectFilePath = findProjectFile(workspaceFolder);
  if (!projectFilePath) {
    addError(ERROR_NO_PROJECTS);
    return false;
  }

  let projectText = null;
  try {
    projectText = fs.readFileSync(projectFilePath, "utf8");
  } catch (error) {
    addError(ERROR_FILE_READ + projectFilePath);
    return false;
  }
  let project = null;
  try {
    let parsedXML = parseXml(projectText);
    project = parsedXML.children[0];
  } catch (error) {
    addError(ERROR_XML_PARSE + projectFilePath);
    return false;
  }

  try {
    let result = createProjectFiles(
      workspaceFolder.uri.fsPath,
      projectFilePath,
      project
    );
    if (!result) {
      return false;
    }
  } catch (error) {
    addError(ERROR_PROJECT_PARSE + projectFilePath);
    if (error instanceof Error) {
      getOutputChannel().appendLine(error.stack);
    }
    return false;
  }

  return true;
}

function createProjectFiles(
  folderPath: string,
  projectFilePath: string,
  project: any
) {
  let result: any = {
    compilerOptions: {},
  };

  migrateProjectFile(project, result);

  let appName = path.basename(projectFilePath);
  let fileName = FILE_ASCONFIG_JSON;
  let asconfigPath = path.resolve(folderPath, fileName);
  let resultText = JSON.stringify(result, undefined, "\t");
  fs.writeFileSync(asconfigPath, resultText);

  vscode.workspace.openTextDocument(asconfigPath).then((document) => {
    vscode.window.showTextDocument(document);
  });

  getOutputChannel().appendLine(appName + " ➡ " + fileName);
  return true;
}

function migrateProjectFile(project: any, result: any) {
  let rootChildren = project.children as any[];
  if (!rootChildren) {
    return null;
  }
  let classpathsElement = findChildElementByName(rootChildren, "classpaths");
  if (classpathsElement) {
    migrateClasspathsElement(classpathsElement, result);
  }
  let compileTargetsElement = findChildElementByName(
    rootChildren,
    "compileTargets"
  );
  if (compileTargetsElement) {
    migrateCompileTargetsElement(compileTargetsElement, result);
  }
  let outputElement = findChildElementByName(rootChildren, "output");
  if (outputElement) {
    migrateOutputElement(outputElement, result);
  }
  let buildElement = findChildElementByName(rootChildren, "build");
  if (buildElement) {
    migrateBuildElement(buildElement, result);
  }
  let libraryPathsElement = findChildElementByName(
    rootChildren,
    "libraryPaths"
  );
  if (libraryPathsElement) {
    migrateLibraryPathsElement(libraryPathsElement, result);
  }
  let externalLibraryPathsElement = findChildElementByName(
    rootChildren,
    "externalLibraryPaths"
  );
  if (externalLibraryPathsElement) {
    migrateExternalLibraryPathsElement(externalLibraryPathsElement, result);
  }
  let includeLibrariesElement = findChildElementByName(
    rootChildren,
    "includeLibraries"
  );
  if (includeLibrariesElement) {
    migrateIncludeLibrariesElement(includeLibrariesElement, result);
  }
  let rslPathsElement = findChildElementByName(rootChildren, "rslPaths");
  if (rslPathsElement) {
    migrateRslPathsElement(rslPathsElement, result);
  }
  let preBuildCommandElement = findChildElementByName(
    rootChildren,
    "preBuildCommand"
  );
  if (preBuildCommandElement) {
    migratePreBuildCommandElement(preBuildCommandElement, result);
  }
  let postBuildCommandElement = findChildElementByName(
    rootChildren,
    "postBuildCommand"
  );
  if (postBuildCommandElement) {
    migratePostBuildCommandElement(postBuildCommandElement, result);
  }
  let optionsElement = findChildElementByName(rootChildren, "options");
  if (optionsElement) {
    migrateOptionsElement(optionsElement, result);
  }
  let libraryElement = findChildElementByName(rootChildren, "library");
  if (libraryElement) {
    migrateLibraryElement(libraryElement, result);
  }
}

function migrateClasspathsElement(classpathsElement: any, result: any) {
  let sourcePaths = [];
  let children = classpathsElement.children as any[];
  children.forEach((child) => {
    if (child.type !== "element" || child.name !== "class") {
      return;
    }
    let attributes = child.attributes;
    if ("path" in attributes) {
      let sourcePath = attributes.path as string;
      sourcePath = normalizeFilePath(sourcePath);
      if (sourcePath.length > 0) {
        sourcePaths.push(sourcePath);
      }
    }
  });
  if (sourcePaths.length > 0) {
    result.compilerOptions["source-path"] = sourcePaths;
  }
}

function migrateCompileTargetsElement(compileTargetsElement: any, result: any) {
  let files = [];
  let children = compileTargetsElement.children as any[];
  children.forEach((child) => {
    if (child.type !== "element" || child.name !== "compile") {
      return;
    }
    let attributes = child.attributes;
    if ("path" in attributes) {
      let compileTargetPath = attributes.path as string;
      compileTargetPath = normalizeFilePath(compileTargetPath);
      if (compileTargetPath.length > 0) {
        files.push(compileTargetPath);
      }
    }
  });
  if (files.length > 0) {
    result.files = files;
  }
}

function migrateOutputElement(outputElement: any, result: any) {
  let width = 800;
  let height = 600;
  let version = 19;
  let minorVersion = 0;

  let children = outputElement.children as any[];
  children.forEach((child) => {
    if (child.type !== "element" || child.name !== "movie") {
      return;
    }
    let attributes = child.attributes;
    if ("platform" in attributes) {
      let platform = attributes.platform as string;
      switch (platform) {
        case "AIR": {
          result.config = "air";
          result.application = "application.xml";
          break;
        }
        case "AIR Mobile": {
          result.config = "airmobile";
          result.application = "application.xml";
          break;
        }
      }
    } else if ("path" in attributes) {
      let outputPath = attributes.path as string;
      outputPath = normalizeFilePath(outputPath);
      if (outputPath.length > 0) {
        result.compilerOptions.output = outputPath;
      }
    } else if ("fps" in attributes) {
      let fps = parseInt(attributes.fps as string, 10);
      result.compilerOptions["default-frame-rate"] = fps;
    } else if ("width" in attributes) {
      width = parseInt(attributes.width as string, 10);
    } else if ("height" in attributes) {
      height = parseInt(attributes.height as string, 10);
    } else if ("version" in attributes) {
      version = parseInt(attributes.version as string, 10);
    } else if ("minorVersion" in attributes) {
      minorVersion = parseInt(attributes.minorVersion as string, 10);
    } else if ("background" in attributes) {
      let backgroundColor = attributes.background as string;
      result.compilerOptions["default-background-color"] = backgroundColor;
    } else if ("preferredSDK" in attributes) {
      let frameworkSDKConfig = vscode.workspace.getConfiguration("as3mxml");
      let frameworkSDK =
        frameworkSDKConfig.inspect("sdk.framework").workspaceValue;
      if (!frameworkSDK) {
        let preferredSDK = attributes.preferredSDK as string;
        let validatedSDKPath = validateFrameworkSDK(preferredSDK);
        if (validatedSDKPath !== null) {
          frameworkSDKConfig.update("sdk.framework", validatedSDKPath);
        }
      }
    }
  });
  result.compilerOptions["default-size"] = { width, height };
  result.compilerOptions["target-player"] = version + "." + minorVersion;
}

function migrateBuildElement(buildElement: any, result: any) {
  let children = buildElement.children as any[];
  children.forEach((child) => {
    if (child.type !== "element" || child.name !== "option") {
      return;
    }
    let attributes = child.attributes;
    if ("accessible" in attributes) {
      let accessible = attributes.accessible as string;
      result.compilerOptions.accessible = accessible === "True";
    } else if ("advancedTelemetry" in attributes) {
      let advancedTelemetry = attributes.advancedTelemetry as string;
      result.compilerOptions["advanced-telemetry"] =
        advancedTelemetry === "True";
    }
    //TODO: allowSourcePathOverlap
    else if ("benchmark" in attributes) {
      let benchmark = attributes.benchmark as string;
      result.compilerOptions.benchmark = benchmark === "True";
    }
    //TODO: es
    //TODO: inline
    else if ("locale" in attributes) {
      let locale = attributes.locale as string;
      if (locale.length > 0) {
        result.compilerOptions.locale = [locale];
      }
    } else if ("loadConfig" in attributes) {
      let loadConfig = attributes.loadConfig as string;
      loadConfig = normalizeFilePath(loadConfig);
      if (loadConfig.length > 0) {
        result.compilerOptions["load-config"] = [loadConfig];
      }
    } else if ("optimize" in attributes) {
      let optimize = attributes.optimize as string;
      result.compilerOptions.optimize = optimize === "True";
    } else if ("omitTraces" in attributes) {
      let omitTraces = attributes.omitTraces as string;
      result.compilerOptions["omit-trace-statements"] = omitTraces === "True";
    }
    //TODO: showActionScriptWarnings
    //TODO: showBindingWarnings
    //TODO: showInvalidCSS
    //TODO: showDeprecationWarnings
    else if ("showUnusedTypeSelectorWarnings" in attributes) {
      let showUnusedTypeSelectorWarnings =
        attributes.showUnusedTypeSelectorWarnings as string;
      result.compilerOptions["show-unused-type-selector-warnings"] =
        showUnusedTypeSelectorWarnings === "True";
    } else if ("strict" in attributes) {
      let strict = attributes.strict as string;
      result.compilerOptions.strict = strict === "True";
    } else if ("useNetwork" in attributes) {
      let useNetwork = attributes.useNetwork as string;
      result.compilerOptions["use-network"] = useNetwork === "True";
    } else if ("useResourceBundleMetadata" in attributes) {
      let useResourceBundleMetadata =
        attributes.useResourceBundleMetadata as string;
      result.compilerOptions["use-resource-bundle-metadata"] =
        useResourceBundleMetadata === "True";
    } else if ("warnings" in attributes) {
      let warnings = attributes.warnings as string;
      result.compilerOptions.warnings = warnings === "True";
    } else if ("verboseStackTraces" in attributes) {
      let verboseStackTraces = attributes.verboseStackTraces as string;
      result.compilerOptions["verbose-stacktraces"] =
        verboseStackTraces === "True";
    } else if ("linkReport" in attributes) {
      let linkReport = attributes.linkReport as string;
      linkReport = normalizeFilePath(linkReport);
      if (linkReport.length > 0) {
        result.compilerOptions["link-report"] = [linkReport];
      }
    } else if ("loadExterns" in attributes) {
      let loadExterns = attributes.loadExterns as string;
      loadExterns = normalizeFilePath(loadExterns);
      if (loadExterns.length > 0) {
        result.compilerOptions["load-externs"] = [loadExterns];
      }
    } else if ("staticLinkRSL" in attributes) {
      let staticLinkRSL = attributes.staticLinkRSL as string;
      result.compilerOptions["static-link-runtime-shared-libraries"] =
        staticLinkRSL === "True";
    } else if ("additional" in attributes) {
      let additional = attributes.additional as string;
      if (additional.length > 0) {
        additional = additional.replace(/\n/g, " ");
        result.additionalOptions = additional;
      }
    } else if ("compilerConstants" in attributes) {
      let compilerConstants = attributes.compilerConstants as string;
      if (compilerConstants.length > 0) {
        let splitConstants = compilerConstants.split("\n");
        let define = splitConstants.map((constant) => {
          let parts = constant.split(",");
          let name = parts[0];
          let valueAsString = parts[1];
          let value: any = undefined;
          if (valueAsString.startsWith('"') && valueAsString.endsWith('"')) {
            value = valueAsString;
          } else if (
            valueAsString.startsWith("'") &&
            valueAsString.endsWith("'")
          ) {
            value = valueAsString;
          } else if (valueAsString === "true" || valueAsString === "false") {
            value = valueAsString === "true";
          } else if (
            /^-?[0-9]+(\.[0-9]+)?([eE](\-|\+)?[0-9]+)?$/.test(valueAsString)
          ) {
            value = parseFloat(valueAsString);
          } else {
            addWarning(WARNING_INVALID_DEFINE + name);
          }
          return { name, value };
        });
        result.compilerOptions.define = define;
      }
    }
  });
}

function migrateLibraryPathsElement(libraryPathsElement: any, result: any) {
  let libraryPaths = [];
  let children = libraryPathsElement.children as any[];
  children.forEach((child) => {
    if (child.type !== "element" || child.name !== "element") {
      return;
    }
    let attributes = child.attributes;
    if ("path" in attributes) {
      let libraryPath = attributes.path as string;
      libraryPath = normalizeFilePath(libraryPath);
      if (libraryPath.length > 0) {
        libraryPaths.push(libraryPath);
      }
    }
  });
  if (libraryPaths.length > 0) {
    result.compilerOptions["library-path"] = libraryPaths;
  }
}

function migrateExternalLibraryPathsElement(
  externalLibraryPathsElement: any,
  result: any
) {
  let externalLibraryPaths = [];
  let children = externalLibraryPathsElement.children as any[];
  children.forEach((child) => {
    if (child.type !== "element" || child.name !== "element") {
      return;
    }
    let attributes = child.attributes;
    if ("path" in attributes) {
      let externalLibraryPath = attributes.path as string;
      externalLibraryPath = normalizeFilePath(externalLibraryPath);
      if (externalLibraryPath.length > 0) {
        externalLibraryPaths.push(externalLibraryPath);
      }
    }
  });
  if (externalLibraryPaths.length > 0) {
    result.compilerOptions["external-library-path"] = externalLibraryPaths;
  }
}

function migrateIncludeLibrariesElement(
  includeLibrariesElement: any,
  result: any
) {
  let includeLibraries = [];
  let children = includeLibrariesElement.children as any[];
  children.forEach((child) => {
    if (child.type !== "element" || child.name !== "element") {
      return;
    }
    let attributes = child.attributes;
    if ("path" in attributes) {
      let includeLibrary = attributes.path as string;
      includeLibrary = normalizeFilePath(includeLibrary);
      if (includeLibrary.length > 0) {
        includeLibraries.push(includeLibrary);
      }
    }
  });
  if (includeLibraries.length > 0) {
    result.compilerOptions["include-libraries"] = includeLibraries;
  }
}

function migratePreBuildCommandElement(optionsElement: any, result: any) {
  let children = optionsElement.children as any[];
  if (children.length > 1) {
    return;
  }
  children.forEach((child) => {
    if (child.type !== "text") {
      return;
    }
    let preBuildCommand = child.text.trim();
    if (preBuildCommand.length > 0) {
      addWarning(WARNING_PRE_BUILD_COMMAND + preBuildCommand);
      addWarning(WARNING_COMMAND_TASK);
    }
  });
}

function migratePostBuildCommandElement(optionsElement: any, result: any) {
  let children = optionsElement.children as any[];
  if (children.length > 1) {
    return;
  }
  children.forEach((child) => {
    if (child.type !== "text") {
      return;
    }
    let postBuildCommand = child.text.trim();
    if (postBuildCommand.length > 0) {
      addWarning(WARNING_POST_BUILD_COMMAND + postBuildCommand);
      addWarning(WARNING_COMMAND_TASK);
    }
  });
}

function migrateOptionsElement(optionsElement: any, result: any) {
  let customTestMovie = false;
  let testMovieCommand = "";
  let children = optionsElement.children as any[];
  children.forEach((child) => {
    if (child.type !== "element" || child.name !== "option") {
      return;
    }
    let attributes = child.attributes;
    if ("testMovie" in attributes) {
      customTestMovie = attributes.testMovie === "Custom";
    }
    if ("testMovieCommand" in attributes) {
      testMovieCommand = attributes.testMovieCommand.trim();
    }
  });
  if (customTestMovie && testMovieCommand.length > 0) {
    addWarning(WARNING_TEST_MOVIE_COMMAND + testMovieCommand);
    addWarning(WARNING_COMMAND_TASK);
  }
}

function migrateRslPathsElement(rslPathsElement: any, result: any) {
  let children = rslPathsElement.children as any[];
  children.forEach((child) => {
    if (child.type !== "element" && child.name !== "element") {
      return;
    }
    let attributes = child.attributes;
    if ("path" in attributes) {
      let rslPath = attributes.path as string;
      addWarning(WARNING_RSL_PATHS + rslPath);
    }
  });
}

function migrateLibraryElement(libraryElement: any, result: any) {
  let children = libraryElement.children as any[];
  children.forEach((child) => {
    if (child.type !== "element" && child.name !== "asset") {
      return;
    }
    let attributes = child.attributes;
    if ("path" in attributes) {
      let assetPath = attributes.path as string;
      addWarning(WARNING_LIBRARY_ASSETS + assetPath);
    }
  });
}

function normalizeFilePath(filePath: string) {
  return filePath.replace(/\\/g, "/").trim();
}

let outputChannel: vscode.OutputChannel;

function getOutputChannel() {
  if (!outputChannel) {
    outputChannel = vscode.window.createOutputChannel(CHANNEL_NAME_IMPORTER);
  }
  return outputChannel;
}

function addWarning(message: string) {
  getOutputChannel().appendLine("🚧 " + message);
}

function addError(message: string) {
  getOutputChannel().appendLine("⛔ " + message);
}

function findChildElementByName(children: any[], name: string) {
  return children.find((child) => {
    return child.type === "element" && child.name === name;
  });
}
