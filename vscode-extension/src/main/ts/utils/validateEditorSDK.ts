/*
Copyright 2016-2021 Bowler Hat LLC

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
import * as child_process from "child_process";
import * as fs from "fs";
import * as path from "path";
import getJavaClassPathDelimiter from "./getJavaClassPathDelimiter";

/**
 * Checks if the path contains a valid Apache Royale SDK. May return a modified
 * path, if the real SDK appears in royale-asjs.
 * Returns null if the SDK is not valid.
 */
export default function validateEditorSDK(
  extensionPath: string,
  javaPath: string,
  sdkPath: string
): string {
  if (!sdkPath || !javaPath || !extensionPath) {
    return null;
  }
  if (
    !fs.existsSync(extensionPath) ||
    !fs.existsSync(javaPath) ||
    !fs.existsSync(sdkPath) ||
    !fs.statSync(sdkPath).isDirectory()
  ) {
    return null;
  }
  if (validatePossibleEditorSDK(extensionPath, javaPath, sdkPath)) {
    return sdkPath;
  }
  //if it's an Apache Royale SDK, the "real" SDK might be inside the
  //royale-asjs directory instead of at the root
  let royalePath = path.join(sdkPath, "royale-asjs");
  if (validatePossibleEditorSDK(extensionPath, javaPath, royalePath)) {
    return royalePath;
  }
  return null;
}

function validatePossibleEditorSDK(
  extensionPath: string,
  javaPath: string,
  sdkPath: string
): boolean {
  if (!hasRequiredFilesInSDK(sdkPath)) {
    return false;
  }
  let cpDelimiter = getJavaClassPathDelimiter();
  let args = [
    "-cp",
    path.resolve(sdkPath, "lib", "*") +
      cpDelimiter +
      path.resolve(sdkPath, "js", "lib", "*") +
      cpDelimiter +
      path.resolve(extensionPath, "bin", "check-royale-version.jar"),
    "com.as3mxml.vscode.CheckRoyaleVersion",
  ];
  let result = child_process.spawnSync(javaPath, args);
  if (result.status !== 0) {
    return false;
  }
  return true;
}

function hasRequiredFilesInSDK(absolutePath: string): boolean {
  if (!absolutePath) {
    return false;
  }
  //the following files are required to consider this a valid SDK
  let folderPaths = [
    path.join(absolutePath, "frameworks"),
    path.join(absolutePath, "bin"),
    path.join(absolutePath, "lib"),
    path.join(absolutePath, "js", "bin"),
    path.join(absolutePath, "js", "lib"),
  ];
  for (let i = 0, count = folderPaths.length; i < count; i++) {
    let folderPath = folderPaths[i];
    if (!fs.existsSync(folderPath) || !fs.statSync(folderPath).isDirectory()) {
      return false;
    }
  }
  let filePaths = [
    path.join(absolutePath, "royale-sdk-description.xml"),
    path.join(absolutePath, "js", "bin", "mxmlc"),
    path.join(absolutePath, "js", "bin", "asjsc"),
    path.join(absolutePath, "lib", "compiler.jar"),
  ];
  for (let i = 0, count = filePaths.length; i < count; i++) {
    let filePath = filePaths[i];
    if (!fs.existsSync(filePath) || fs.statSync(filePath).isDirectory()) {
      return false;
    }
  }
  return true;
}
