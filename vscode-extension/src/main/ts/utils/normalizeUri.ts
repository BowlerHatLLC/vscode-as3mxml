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

export default function normalizeUri(value: vscode.Uri): string
{
	if (/^win32/.test(process.platform))
	{
		//there are a couple of inconsistencies on Windows
		//between VSCode and Java
		//1. The : character after the drive letter is
		//   encoded as %3A in VSCode, but : in Java.
		//2. The driver letter is lowercase in VSCode, but
		//   is uppercase in Java when watching for file
		//   system changes.
		let valueAsString = value.toString().replace("%3A", ":");
		let matches = /^file:\/\/\/([a-z]):\//.exec(valueAsString);
		if(matches !== null)
		{
			let driveLetter = matches[1].toUpperCase();
			valueAsString = `file:///${driveLetter}:/` + valueAsString.substr(matches[0].length);
		}
		return valueAsString;
	}
	else
	{
		return value.toString();
	}
}