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
const FILE_APPLICATION_XML = "application.xml";

const MESSAGE_IMPORT_START = "🚀 Importing FlashDevelop project...";
const MESSAGE_IMPORT_COMPLETE = "✅ Import complete.";
const MESSAGE_IMPORT_FAILED = "❌ Import failed."

const ERROR_NO_FOLDER = "Workspace folder parameter is missing.";
const ERROR_NO_PROJECTS = "No FlashDevelop projects found in workspace.";
const ERROR_FILE_READ = "Failed to read file: ";
const ERROR_XML_PARSE = "Failed to parse FlashDevelop project. Invalid XML in file: ";
const ERROR_PROJECT_PARSE = "Failed to parse FlashDevelop project: ";
const ERROR_OUTPUT_TYPE = "Project output type not supported: ";
const WARNING_INVALID_DEFINE = "Failed to parse define: ";

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
	let outputType = findOutputType(project);
	if(outputType !== "Application")
	{
		addError(ERROR_OUTPUT_TYPE + outputType);
		return false;
	}

	let result: any =
	{
		compilerOptions: {}
	};

	migrateProjectFile(project, result);
	
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

function migrateProjectFile(project: any, result: any)
{
	let rootChildren = project.children as any[];
	if(!rootChildren)
	{
		return null;
	}
	let classpathsElement = findChildElementByName(rootChildren, "classpaths");
	if(classpathsElement)
	{
		migrateClasspathsElement(classpathsElement, result);
	}
	let compileTargetsElement = findChildElementByName(rootChildren, "compileTargets");
	if(compileTargetsElement)
	{
		migrateCompileTargetsElement(compileTargetsElement, result);
	}
	let outputElement = findChildElementByName(rootChildren, "output");
	if(outputElement)
	{
		migrateOutputElement(outputElement, result);
	}
	let buildElement = findChildElementByName(rootChildren, "build");
	if(buildElement)
	{
		migrateBuildElement(buildElement, result);
	}
	/*migrateLibraryPathsElement();
	migrateExternalLibraryPathsElement();
	migrateIncludeLibrariesElement();*/
}

function migrateClasspathsElement(classpathsElement: any, result: any)
{
	let sourcePaths = [];
	let children = classpathsElement.children as any[];
	children.forEach((child) =>
	{
		if(child.type !== "element" || child.name !== "class")
		{
			return;
		}
		let attributes = child.attributes;
		if("path" in attributes)
		{
			let sourcePath = attributes.path as string;
			sourcePath = normalizeFilePath(sourcePath);
			sourcePaths.push(sourcePath);
		}
	});
	if(sourcePaths.length > 0)
	{
		result.compilerOptions["source-path"] = sourcePaths;
	}
}

function migrateCompileTargetsElement(compileTargetsElement: any, result: any)
{
	let files = [];
	let children = compileTargetsElement.children as any[];
	children.forEach((child) =>
	{
		if(child.type !== "element" || child.name !== "compile")
		{
			return;
		}
		let attributes = child.attributes;
		if("path" in attributes)
		{
			let compileTargetPath = attributes.path as string;
			compileTargetPath = normalizeFilePath(compileTargetPath);
			files.push(compileTargetPath);
		}
	});
	if(files.length > 0)
	{
		result.files = files;
	}
}

function migrateOutputElement(outputElement: any, result: any)
{
	let width = 800;
	let height = 600;
	let version = 19;
	let minorVersion = 0;

	let children = outputElement.children as any[];
	children.forEach((child) =>
	{
		if(child.type !== "element" || child.name !== "movie")
		{
			return;
		}
		let attributes = child.attributes;
		if("platform" in attributes)
		{
			let platform = attributes.platform as string;
			switch(platform)
			{
				case "AIR":
				{
					result.config = "air";
					result.application = "application.xml";
					break;
				}
				case "AIR Mobile":
				{
					result.config = "airmobile";
					result.application = "application.xml";
					break;
				}
			}
		}
		else if("path" in attributes)
		{
			let outputPath = attributes.path as string;
			outputPath = normalizeFilePath(outputPath);
			result.compilerOptions.output = outputPath;
		}
		else if("fps" in attributes)
		{
			let fps = parseInt(attributes.fps as string, 10);
			result.compilerOptions["default-frame-rate"] = fps;
		}
		else if("width" in attributes)
		{
			width = parseInt(attributes.width as string, 10);
		}
		else if("height" in attributes)
		{
			height = parseInt(attributes.height as string, 10);
		}
		else if("version" in attributes)
		{
			version = parseInt(attributes.version as string, 10);
		}
		else if("minorVersion" in attributes)
		{
			minorVersion = parseInt(attributes.minorVersion as string, 10);
		}
		else if("background" in attributes)
		{
			let backgroundColor = attributes.background as string;
			result.compilerOptions["default-background-color"] = backgroundColor;
		}
	});
	result.compilerOptions["default-size"] = {width, height};
	result.compilerOptions["target-player"] = version + "." + minorVersion;
}

function migrateBuildElement(buildElement: any, result: any)
{
	let children = buildElement.children as any[];
	children.forEach((child) =>
	{
		if(child.type !== "element" || child.name !== "option")
		{
			return;
		}
		let attributes = child.attributes;
		if("accessible" in attributes)
		{
			let accessible = attributes.accessible as string;
			result.compilerOptions.accessible = accessible === "True";
		}
		else if("advancedTelemetry" in attributes)
		{
			let advancedTelemetry = attributes.advancedTelemetry as string;
			result.compilerOptions["advanced-telemetry"] = advancedTelemetry === "True";
		}
		//TODO: allowSourcePathOverlap
		else if("benchmark" in attributes)
		{
			let benchmark = attributes.benchmark as string;
			result.compilerOptions.benchmark = benchmark === "True";
		}
		//TODO: es
		//TODO: inline
		else if("locale" in attributes)
		{
			let locale = attributes.locale as string;
			if(locale.length > 0)
			{
				result.compilerOptions.locale = [locale];
			}
		}
		else if("loadConfig" in attributes)
		{
			let loadConfig = attributes.loadConfig as string;
			if(loadConfig.length > 0)
			{
				loadConfig = normalizeFilePath(loadConfig);
				result.compilerOptions["load-config"] = [loadConfig];
			}
		}
		else if("optimize" in attributes)
		{
			let optimize = attributes.optimize as string;
			result.compilerOptions.optimize = optimize === "True";
		}
		else if("omitTraces" in attributes)
		{
			let omitTraces = attributes.omitTraces as string;
			result.compilerOptions["omit-trace-statements"] = omitTraces === "True";
		}
		//TODO: showActionScriptWarnings
		//TODO: showBindingWarnings
		//TODO: showInvalidCSS
		//TODO: showDeprecationWarnings
		else if("showUnusedTypeSelectorWarnings" in attributes)
		{
			let showUnusedTypeSelectorWarnings = attributes.showUnusedTypeSelectorWarnings as string;
			result.compilerOptions["show-unused-type-selector-warnings"] = showUnusedTypeSelectorWarnings === "True";
		}
		else if("strict" in attributes)
		{
			let strict = attributes.strict as string;
			result.compilerOptions.strict = strict === "True";
		}
		else if("useNetwork" in attributes)
		{
			let useNetwork = attributes.useNetwork as string;
			result.compilerOptions["use-network"] = useNetwork === "True";
		}
		else if("useResourceBundleMetadata" in attributes)
		{
			let useResourceBundleMetadata = attributes.useResourceBundleMetadata as string;
			result.compilerOptions["use-resource-bundle-metadata"] = useResourceBundleMetadata === "True";
		}
		else if("warnings" in attributes)
		{
			let warnings = attributes.warnings as string;
			result.compilerOptions.warnings = warnings === "True";
		}
		else if("verboseStackTraces" in attributes)
		{
			let verboseStackTraces = attributes.verboseStackTraces as string;
			result.compilerOptions["verbose-stacktraces"] = verboseStackTraces === "True";
		}
		else if("linkReport" in attributes)
		{
			let linkReport = attributes.linkReport as string;
			if(linkReport.length > 0)
			{
				linkReport = normalizeFilePath(linkReport);
				result.compilerOptions["link-report"] = [linkReport];
			}
		}
		else if("loadExterns" in attributes)
		{
			let loadExterns = attributes.loadExterns as string;
			if(loadExterns.length > 0)
			{
				loadExterns = normalizeFilePath(loadExterns);
				result.compilerOptions["load-externs"] = [loadExterns];
			}
		}
		else if("staticLinkRSL" in attributes)
		{
			let staticLinkRSL = attributes.staticLinkRSL as string;
			result.compilerOptions["static-link-runtime-shared-libraries"] = staticLinkRSL === "True";
		}
		else if("additional" in attributes)
		{
			let additional = attributes.additional as string;
			if(additional.length > 0)
			{
				additional = additional.replace(/\n/g, " ");
				result.additionalOptions = additional;
			}
		}
		else if("compilerConstants" in attributes)
		{
			let compilerConstants = attributes.compilerConstants as string;
			if(compilerConstants.length > 0)
			{
				let splitConstants = compilerConstants.split("\n");
				let define = splitConstants.map((constant) =>
				{
					let parts = constant.split(",")
					let name = parts[0];
					let valueAsString = parts[1];
					let value: any = undefined;
					if(valueAsString.startsWith("\"") && valueAsString.endsWith("\""))
					{
						value = valueAsString;
					}
					else if(valueAsString.startsWith("'") && valueAsString.endsWith("'"))
					{
						value = valueAsString;
					}
					else if(valueAsString === "true" || valueAsString === "false")
					{
						value = valueAsString === "true";
					}
					else if(/^-?[0-9]+(\.[0-9]+)?([eE](\-|\+)?[0-9]+)?$/.test(valueAsString))
					{
						value = parseFloat(valueAsString);
					}
					else
					{
						addWarning(WARNING_INVALID_DEFINE + name);
					}
					return { name, value };
				});
				result.compilerOptions.define = define;
			}
		}
	});
}

function normalizeFilePath(filePath: string)
{
	return filePath.replace(/\\/g, "/");
}

function findOutputType(project: any)
{
	if(!project)
	{
		return null;
	}
	let rootChildren = project.children as any[];
	if(!rootChildren)
	{
		return null;
	}
	let outputElement = findChildElementByName(rootChildren, "output");
	if(!outputElement)
	{
		return [];
	}
	let outputChildren = outputElement.children as any[];
	if(!outputChildren)
	{
		return null;
	}
	let result = outputChildren.find((child) =>
	{
		return child.type === "element" && child.name === "movie" && "outputType" in child.attributes;
	});
	if(!result)
	{
		return null;
	}
	return result.attributes.outputType;
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

function findChildElementByName(children: any[], name: string)
{
	return children.find((child) =>
	{
		return child.type === "element" && child.name === name;
	});
}