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

let savedPassword = null;

export default async function saveSessionPassword(): Promise<string> {
  do {
    savedPassword = await vscode.window.showInputBox({
      placeHolder: "Password",
      prompt: "Save your password for current session",
      value: savedPassword,
      password: true,
    });
    if (savedPassword === undefined) {
      break;
    }
  } while (!savedPassword);
  return Promise.resolve(savedPassword);
}
