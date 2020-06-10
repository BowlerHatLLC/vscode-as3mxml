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
import * as fs from "fs";
import * as path from "path";
import * as vscode from "vscode";

const ANIMATE_PATH_WINDOWS_REGEXP = /^c:\\Program Files( \(x86\))?\\Adobe\\Adobe (Animate|Flash) [\w \.]+$/i;
const ANIMATE_PATH_MACOS_REGEXP = /^\/Applications\/Adobe (Animate|Flash) [\w \.]+$/i;
const ANIMATE_APP_MACOS_REGEXP = /^Adobe (Animate|Flash) [\w \.]+\.app$/i;

const APPLICATIONS_MACOS = "/Applications";
const ADOBE_FOLDERS_WINDOWS =
[
	"c:\\Program Files\\Adobe",
	"c:\\Program Files (x86)\\Adobe"
];
const EXE_NAMES_WINDOWS =
[
	"Animate.exe",
	"Flash.exe",
];

export default function findAnimate(): string
{
	let settingsPath = vscode.workspace.getConfiguration("as3mxml").get("sdk.animate") as string;
	if(settingsPath)
	{
		if(process.platform === "win32" && fs.existsSync(settingsPath) && !fs.statSync(settingsPath).isDirectory())
		{
			return settingsPath;
		}
		else if(process.platform === "darwin") //macOS
		{
			let appName = path.basename(settingsPath)
			if(ANIMATE_APP_MACOS_REGEXP.test(appName))
			{
				return settingsPath;
			}
		}
		return null;
	}
	if(process.platform === "win32")
	{
		let animatePath: string = null;
		ADOBE_FOLDERS_WINDOWS.find((folderPath) =>
		{
			let files = fs.readdirSync(folderPath);
			files.find((filePath) =>
			{
				filePath = path.resolve(folderPath, filePath);
				if(fs.statSync(filePath).isDirectory() && ANIMATE_PATH_WINDOWS_REGEXP.test(filePath))
				{
					EXE_NAMES_WINDOWS.find((exeName) =>
					{
						let exePath = path.resolve(filePath, exeName);
						if(fs.existsSync(exePath) && !fs.statSync(exePath).isDirectory())
						{
							animatePath = exePath;
							return true;
						}
						return false;
					});
				}
				return animatePath !== null;
			});
			return animatePath !== null;
		});
		return animatePath;
	}
	else if(process.platform === "darwin") //macOS
	{
		let animatePath: string = null;
		let files = fs.readdirSync(APPLICATIONS_MACOS);
		files.find((filePath) =>
		{
			filePath = path.resolve(APPLICATIONS_MACOS, filePath);
			if(fs.statSync(filePath).isDirectory() && ANIMATE_PATH_MACOS_REGEXP.test(filePath))
			{
				let appFiles = fs.readdirSync(filePath);
				appFiles.find((fileName) =>
				{
					if(ANIMATE_APP_MACOS_REGEXP.test(fileName))
					{
						animatePath = path.resolve(filePath, fileName);
						return true;
					}
					return false;
				});
			}
			return animatePath !== null;
		});
		return animatePath;
	}
	return null;
}