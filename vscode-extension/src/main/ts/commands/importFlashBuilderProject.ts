/*
Copyright 2016-2019 Bowler Hat LLC

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
import validateFrameworkSDK from "../utils/validateFrameworkSDK";

const FILE_ASCONFIG_JSON = "asconfig.json";
const FILE_ACTIONSCRIPT_PROPERTIES = ".actionScriptProperties";
const FILE_FLEX_PROPERTIES = ".flexProperties";
const PATH_FLASH_BUILDER_WORKSPACE_SDK_PREFS = "../.metadata/.plugins/org.eclipse.core.runtime/.settings/com.adobe.flexbuilder.project.prefs";

const TOKEN_SDKS_PREF = "com.adobe.flexbuilder.project.flex_sdks=";

const ERROR_CANNOT_FIND_PROJECT = "No Adobe Flash Builder project found in workspace.";
const ERROR_CANNOT_PARSE_PROJECT = "Failed to parse Adobe Flash Builder project.";
const ERROR_ASCONFIG_JSON_EXISTS = "Cannot migrate Adobe Flash Builder project because asconfig.json already exists.";
const ERROR_MODULES = "Importing Adobe Flash Builder projects with modules is not supported.";

interface FlashBuilderSDK
{
	name: string;
	location: string;
	flashSDK: boolean;
	defaultSDK: boolean;
}

export function pickFlashBuilderProjectInWorkspace()
{
	if(vscode.workspace.workspaceFolders)
	{
		let workspaceFolders = vscode.workspace.workspaceFolders.filter((folder) =>
		{
			let asPropsPath = path.resolve(folder.uri.fsPath, FILE_ACTIONSCRIPT_PROPERTIES);
			let asconfigPath = path.resolve(folder.uri.fsPath, FILE_ASCONFIG_JSON);
			if(!fs.existsSync(asPropsPath) || fs.statSync(asPropsPath).isDirectory() || fs.existsSync(asconfigPath))
			{
				return false;
			}
			return true;
		});
		if(workspaceFolders.length === 0)
		{
			vscode.window.showErrorMessage(ERROR_CANNOT_FIND_PROJECT);
			return;
		}
		else if(workspaceFolders.length === 1)
		{
			importFlashBuilderProject(workspaceFolders[0]);
		}
		else
		{
			let items = workspaceFolders.map((folder) =>
			{
				return { label: folder.name, description: folder.uri.fsPath, folder};
			})
			vscode.window.showQuickPick(items).then((result) =>
			{
				importFlashBuilderProject(result.folder);
			});
		}
	}
}

function findSDKs(workspaceFolder: vscode.WorkspaceFolder): FlashBuilderSDK[]
{
	let sdkPrefsPath = path.resolve(workspaceFolder.uri.fsPath, PATH_FLASH_BUILDER_WORKSPACE_SDK_PREFS);
	if(!fs.existsSync(sdkPrefsPath))
	{
		return [];
	}	

	let sdksElement = null;
	try
	{
		let sdkPrefsText = fs.readFileSync(sdkPrefsPath, "utf8");
		let startIndex = sdkPrefsText.indexOf(TOKEN_SDKS_PREF);
		if(startIndex === -1)
		{
			return [];
		}
		startIndex += TOKEN_SDKS_PREF.length;
		let endIndex = sdkPrefsText.indexOf("\n", startIndex);
		if(endIndex === -1)
		{
			return [];
		}
		sdkPrefsText = sdkPrefsText.substr(startIndex, endIndex - startIndex);
		sdkPrefsText = sdkPrefsText.replace(/\\r/g, "\r");
		sdkPrefsText = sdkPrefsText.replace(/\\n/g, "\n");
		sdkPrefsText = sdkPrefsText.replace(/\\(.)/g, (match, p1) =>
		{
			return p1;
		});
		sdksElement = parseXML(sdkPrefsText)
	}
	catch(error)
	{
		return [];
	}
	let rootElement = sdksElement.children[0];
	let rootChildren = rootElement.children;
	return rootChildren
		.filter((child) =>
		{
			if(child.type !== "element" || child.name !== "sdk")
			{
				return false;
			}
			let attributes = child.attributes;
			return "name" in attributes && "location" in attributes && "flashSDK" in attributes;
		})
		.map((child) =>
		{
			let sdkAttributes = child.attributes;
			
			return {
				name: sdkAttributes.name,
				location: sdkAttributes.location,
				flashSDK: sdkAttributes.flashSDK === "true",
				defaultSDK: sdkAttributes.defaultSDK === "true"
			};
		});
}

export function importFlashBuilderProject(workspaceFolder: vscode.WorkspaceFolder)
{
	if(!workspaceFolder)
	{
		//it's possible for no folder to be chosen when using
		//showWorkspaceFolderPick()
		return false;
	}
	let folderPath = workspaceFolder.uri.fsPath;
	let actionScriptPropertiesPath = path.resolve(folderPath, FILE_ACTIONSCRIPT_PROPERTIES);
	if(!fs.existsSync(actionScriptPropertiesPath))
	{
		vscode.window.showErrorMessage(ERROR_CANNOT_FIND_PROJECT);
		return;
	}
	let asconfigPath = path.resolve(folderPath, FILE_ASCONFIG_JSON);
	if(fs.existsSync(asconfigPath))
	{
		vscode.window.showErrorMessage(ERROR_ASCONFIG_JSON_EXISTS);
		return;
	}

	let flexProperties = path.resolve(folderPath, FILE_FLEX_PROPERTIES);
	let isFlexApp = fs.existsSync(flexProperties);

	let actionScriptPropertiesText = fs.readFileSync(actionScriptPropertiesPath, "utf8");
	let actionScriptProperties = null;
	try
	{
		let parsedXML = parseXML(actionScriptPropertiesText)
		actionScriptProperties = parsedXML.children[0];
	}
	catch(error)
	{
		vscode.window.showErrorMessage(ERROR_CANNOT_PARSE_PROJECT);
		return;
	}

	let sdks = findSDKs(workspaceFolder);

	createProjectFiles(folderPath, actionScriptProperties, sdks, isFlexApp);
}

function findApplications(actionScriptProperties: any)
{
	if(!actionScriptProperties)
	{
		return [];
	}
	let rootChildren = actionScriptProperties.children as any[];
	if(!rootChildren)
	{
		return [];
	}
	let applicationsElement = rootChildren.find((child) =>
	{
		return child.type === "element" && child.name === "applications";
	});
	if(!applicationsElement)
	{
		return [];
	}
	let appChildren = applicationsElement.children as any[];
	if(!appChildren)
	{
		return [];
	}
	return appChildren.filter((child) =>
	{
		return child.type === "element" && child.name === "application";
	})
}

function findMainApplicationPath(actionScriptProperties: any)
{
	let attributes = actionScriptProperties.attributes;
	if(!attributes)
	{
		return null;
	}
	return attributes.mainApplicationPath;
}

function getApplicationNameFromPath(appPath: string)
{
	appPath = path.basename(appPath);
	return appPath.substr(0, appPath.length - path.extname(appPath).length);
}

function createProjectFiles(folderPath: string, actionScriptProperties: any, sdks: FlashBuilderSDK[], isFlexApp: boolean)
{
	let mainAppPath = findMainApplicationPath(actionScriptProperties);

	let applications = findApplications(actionScriptProperties);
	applications.forEach((application) =>
	{
		let result: any =
		{
			compilerOptions: {},
			files: [],
		};
		migrateActionScriptProperties(application, actionScriptProperties, isFlexApp, sdks, result);
		
		let resultText = JSON.stringify(result, undefined, "\t");

		let appPath = application.attributes.path;
		let fileName = FILE_ASCONFIG_JSON;
		if(appPath !== mainAppPath && applications.length > 1)
		{
			let appName = getApplicationNameFromPath(appPath);
			fileName = "asconfig." + appName + ".json";
		}
		let asconfigPath = path.resolve(folderPath, fileName);
		fs.writeFileSync(asconfigPath, resultText);

		vscode.workspace.openTextDocument(asconfigPath).then((document) =>
		{
			vscode.window.showTextDocument(document)
		});
	});
}

function migrateActionScriptProperties(application: any, actionScriptProperties: any, isFlexApp: boolean, sdks: FlashBuilderSDK[], result: any)
{
	let rootChildren = actionScriptProperties.children as any[];
	if(!rootChildren)
	{
		return null;
	}
	let rootAttributes = actionScriptProperties.attributes;
	if(!rootAttributes)
	{
		return null;
	}
	let appAttributes = application.attributes;
	if(!appAttributes)
	{
		return null;
	}

	let applicationPath = null;
	if("path" in appAttributes)
	{
		applicationPath = appAttributes.path;
	}
	if(!applicationPath)
	{
		applicationPath = isFlexApp ? "MyProject.mxml" : "MyProject.as";
	}

	let compilerElement = rootChildren.find((child) =>
	{
		return child.type === "element" && child.name === "compiler";
	});
	if(compilerElement)
	{
		migrateCompilerElement(compilerElement, applicationPath, sdks, result);
	}

	let buildTargetsElement = rootChildren.find((child) =>
	{
		return child.type === "element" && child.name === "buildTargets";
	});
	if(buildTargetsElement)
	{
		migrateBuildTargetsElement(buildTargetsElement, applicationPath, result);
	}

	let modulesElement = rootChildren.find((child) =>
	{
		return child.type === "element" && child.name === "modules";
	});
	if(modulesElement)
	{
		let moduleAppPath = applicationPath;
		if(compilerElement)
		{
			moduleAppPath = path.posix.join(compilerElement.attributes.sourceFolderPath, moduleAppPath);
		}
		migrateModulesElement(modulesElement, moduleAppPath, result);
	}
}

function migrateCompilerElement(compilerElement: any, appPath: string, sdks: FlashBuilderSDK[], result: any)
{
	let attributes = compilerElement.attributes;
	vscode.workspace.getConfiguration()
	vscode.ConfigurationTarget
	let frameworkSDKConfig = vscode.workspace.getConfiguration("as3mxml");
	let frameworkSDK = frameworkSDKConfig.inspect("sdk.framework").workspaceValue;
	if(!frameworkSDK)
	{
		let sdk: FlashBuilderSDK;
		let useFlashSDK = false;
		if("useFlashSDK" in attributes)
		{
			useFlashSDK = attributes.useFlashSDK === "true";
		}
		if(useFlashSDK)
		{
			sdk = sdks.find((sdk) =>
			{
				return sdk.flashSDK;
			});
		}
		else if("flexSDK" in attributes)
		{
			let sdkName = attributes.flexSDK;
			sdk = sdks.find((sdk) =>
			{
				return sdk.name === sdkName;
			});
		}
		else
		{
			sdk = sdks.find((sdk) =>
			{
				return sdk.defaultSDK;
			});
		}
		if(sdk)
		{
			let validatedSDKPath = validateFrameworkSDK(sdk.location);
			if(validatedSDKPath !== null)
			{
				frameworkSDKConfig.update("sdk.framework", validatedSDKPath);
			}
		}
	}
	if("useApolloConfig" in attributes && attributes.useApolloConfig === "true")
	{
		result.application = path.posix.join(attributes.sourceFolderPath, getApplicationNameFromPath(appPath) + "-app.xml");
	}
	if("copyDependentFiles" in attributes && attributes.copyDependentFiles === "true")
	{
		result.copySourcePathAssets = true;
	}
	if("outputFolderPath" in attributes)
	{
		result.compilerOptions.output = path.posix.join(attributes.outputFolderPath, getApplicationNameFromPath(appPath) + ".swf");
	}
	if("additionalCompilerArguments" in attributes)
	{
		result.additionalOptions = attributes.additionalCompilerArguments;
	}
	if("generateAccessible" in attributes && attributes.generateAccessible === "true")
	{
		result.compilerOptions.accessible = true;
	}
	if("targetPlayerVersion" in attributes && attributes.targetPlayerVersion !== "0.0.0")
	{
		result.compilerOptions["target-player"] = attributes.targetPlayerVersion;
	}
	let sourceFolderPath: string = null;
	if("sourceFolderPath" in attributes)
	{
		sourceFolderPath = attributes.sourceFolderPath;
		let mainFilePath = path.posix.join(attributes.sourceFolderPath, appPath);
		result.files.push(mainFilePath);
	}
	let children = compilerElement.children as any[];
	let compilerSourcePathElement = children.find((child) =>
	{
		return child.type === "element" && child.name === "compilerSourcePath";
	});
	if(compilerSourcePathElement)
	{
		migrateCompilerSourcePathElement(compilerSourcePathElement, sourceFolderPath, result);
	}
	let libraryPathElement = children.find((child) =>
	{
		return child.type === "element" && child.name === "libraryPath";
	});
	if(libraryPathElement)
	{
		migrateCompilerLibraryPathElement(libraryPathElement, result);
	}
}

function migrateCompilerSourcePathElement(compilerSourcePathElement: any, sourceFolderPath: string, result: any)
{
	let sourcePaths = [];
	if(sourceFolderPath)
	{
		sourcePaths.push(sourceFolderPath);
	}
	let children = compilerSourcePathElement.children as any[];
	children.forEach((child) =>
	{
		if(child.type !== "element" || child.name !== "compilerSourcePathEntry")
		{
			return;
		}
		let attributes = child.attributes;
		if("path" in attributes && "kind" in attributes)
		{
			let sourcePath = replaceSourceOrLibraryPathTokens(attributes.path as string);
			let kind = attributes.kind as string;
			if(kind !== "1")
			{
				console.warn("Skipping sources with unknown kind " + kind + " at path " + sourcePath);
				return;
			}
			sourcePaths.push(sourcePath);
		}
	});
	if(sourcePaths.length > 0)
	{
		result.compilerOptions["source-path"] = sourcePaths;
	}
}

function replaceSourceOrLibraryPathTokens(sourceOrLibraryPath: string)
{
	//relative to the Flash Builder workspace
	sourceOrLibraryPath = sourceOrLibraryPath.replace("${DOCUMENTS}", "..");
	//relative to the SDK frameworks folder
	sourceOrLibraryPath = sourceOrLibraryPath.replace("${PROJECT_FRAMEWORKS}", "${flexlib}");
	return sourceOrLibraryPath;
}

function migrateCompilerLibraryPathElement(libraryPathElement: any, result: any)
{
	let libraryPaths = [];
	let externalLibraryPaths = [];

	let defaultLinkType = "0";
	let libraryPathAttributes = libraryPathElement.attributes;
	if("defaultLinkType" in libraryPathAttributes)
	{
		defaultLinkType = libraryPathAttributes.defaultLinkType;
	}

	let children = libraryPathElement.children as any[];
	children.forEach((child) =>
	{
		if(child.type !== "element" || child.name !== "libraryPathEntry")
		{
			return;
		}
		let libraryPathEntryAttributes = child.attributes;
		if("path" in libraryPathEntryAttributes &&
			"kind" in libraryPathEntryAttributes &&
			"linkType" in libraryPathEntryAttributes)
		{
			let libraryPath = replaceSourceOrLibraryPathTokens(libraryPathEntryAttributes.path as string);
			
			let kind = libraryPathEntryAttributes.kind as string;
			if(kind !== "1" && //folder
				kind !== "3" && //swc
				kind !== "5") //ane
			{
				console.warn("Skipping library with unknown kind " + kind + " at path " + libraryPath);
				return;
			}
			let useDefaultLinkType = false;
			if("useDefaultLinkType" in libraryPathEntryAttributes)
			{
				useDefaultLinkType = libraryPathEntryAttributes.useDefaultLinkType === "true";
			}
			let linkType = libraryPathEntryAttributes.linkType;
			if (useDefaultLinkType && defaultLinkType !== "0")
			{
				linkType = defaultLinkType;
			}
			if(linkType === "1") //library-path
			{
				libraryPaths.push(libraryPath);
			}
			else if(linkType === "2") //external-ibrary-path
			{
				externalLibraryPaths.push(libraryPath);
			}
			else if(linkType === "3") //runtime shared library
			{
				console.warn("Skipping library with linkType 3 (runtime shared library) located at path: " + libraryPath);
			}
			else
			{
				console.warn("Skipping library with unknown linkType " + linkType + " located at path: " + libraryPath);
			}
		}
	});
	if(libraryPaths.length > 0)
	{
		result.compilerOptions["library-path"] = libraryPaths;
	}
	if(externalLibraryPaths.length > 0)
	{
		result.compilerOptions["external-library-path"] = externalLibraryPaths;
	}
}

function migrateBuildTargetsElement(buildTargetsElement: any, applicationFileName: string, result: any)
{
	let children = buildTargetsElement.children as any[];
	children.forEach((buildTarget) =>
	{
		if(buildTarget.type !== "element" || buildTarget.name !== "buildTarget")
		{
			return;
		}
		let buildTargetAttributes = buildTarget.attributes;
		if(!("platformId" in buildTargetAttributes))
		{
			return;
		}
		let platformId = buildTargetAttributes.platformId;
		let isIOS = platformId === "com.adobe.flexide.multiplatform.ios.platform";
		let isAndroid = platformId === "com.adobe.flexide.multiplatform.android.platform";
		let isDefault = platformId === "default";
		let buildTargetChildren = buildTarget.children;
		let multiPlatformSettings = children.find((child) =>
		{
			return child.type === "element" && child.name === "multiPlatformSettings";
		})
		if(multiPlatformSettings)
		{
			let multiPlatformSettingsAttributes = multiPlatformSettings.attributes;
			if("enabled" in multiPlatformSettingsAttributes)
			{
				let enabled = multiPlatformSettingsAttributes.enabled === "true";
				if(!enabled)
				{
					//we can skip this one because it's not enabled
					return;
				}
			}
		}
		result.airOptions = result.airOptions || {};
		let platformOptions = null;
		if(isIOS)
		{
			platformOptions = result.airOptions.ios || {};
			platformOptions.output = path.posix.join(getApplicationNameFromPath(applicationFileName) + ".ipa");
			if("provisioningFile" in buildTargetAttributes)
			{
				let provisioningFile = buildTargetAttributes.provisioningFile;
				if(provisioningFile)
				{
					platformOptions.signingOptions = platformOptions.signingOptions || {};
					platformOptions.signingOptions["provisioning-profile"] = provisioningFile;
				}
			}
		}
		else if(isAndroid)
		{
			platformOptions = result.airOptions.android || {};
			platformOptions.output = path.posix.join(getApplicationNameFromPath(applicationFileName) + ".apk");
		}
		else if(isDefault)
		{
			result.config = "air";
			platformOptions = result.airOptions;
			platformOptions.output = path.posix.join(getApplicationNameFromPath(applicationFileName) + ".air");
		}
		else
		{
			vscode.window.showErrorMessage("Unknown Adobe AIR platform in Adobe Flash Builder project: " + platformId);
			return;
		}
		if(isIOS || isAndroid)
		{
			result.config = "airmobile";
		}
		let airSettings = buildTargetChildren.find((child) =>
		{
			return child.type === "element" && child.name === "airSettings";
		});
		if(airSettings)
		{
			let airSettingsAttributes = airSettings.attributes;
			if("airCertificatePath" in airSettingsAttributes)
			{
				let airCertificatePath = airSettingsAttributes.airCertificatePath;
				if(airCertificatePath)
				{
					platformOptions.signingOptions = platformOptions.signingOptions || {};
					platformOptions.signingOptions.keystore = airCertificatePath;
					platformOptions.signingOptions.storetype = "pkcs12";
				}
			}
			let airSettingsChildren = airSettings.children;
			let anePaths = airSettingsChildren.find((child) =>
			{
				return child.type === "element" && child.name === "anePaths";
			});
			if(anePaths)
			{
				let anePathsChildren = anePaths.children;
				let anePathEntries = anePathsChildren.filter((child) =>
				{
					return child.type === "element" && child.name === "anePathEntry";
				});
				if(anePathEntries.length > 0)
				{
					let extdir = [];
					anePathEntries.forEach((anePathEntry) =>
					{
						let anePathEntryAttributes = anePathEntry.attributes;
						if("path" in anePathEntryAttributes)
						{
							extdir.push(anePathEntryAttributes.path);
						}
					});
					platformOptions.extdir = extdir;
				}
			}
		}
	});
}

let alreadyWarned = false;
function migrateModulesElement(modulesElement: any, appPath: string, result: any)
{
	if(alreadyWarned)
	{
		return;
	}
	let children = modulesElement.children as any[];
	let modules = children.filter((module) =>
	{
		return module.type === "element" && module.name === "module" && module.attributes.application === appPath;
	});
	let hasModules = modules.length > 0;
	if(hasModules)
	{
		alreadyWarned = true;
		vscode.window.showErrorMessage(ERROR_MODULES);
	}
}