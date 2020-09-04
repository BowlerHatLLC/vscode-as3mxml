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
import validateFrameworkSDK from "./validateFrameworkSDK";
import findSDKInLocalRoyaleNodeModule from "./findSDKInLocalRoyaleNodeModule";
import findSDKInLocalFlexJSNodeModule from "./findSDKInLocalFlexJSNodeModule";
import findSDKInRoyaleHomeEnvironmentVariable from "./findSDKInRoyaleHomeEnvironmentVariable";
import findSDKInFlexHomeEnvironmentVariable from "./findSDKInFlexHomeEnvironmentVariable";
import findSDKsInPathEnvironmentVariable from "./findSDKsInPathEnvironmentVariable";

export default function getFrameworkSDKPathWithFallbacks(): string {
  let sdkPath: string = null;
  let frameworkSetting = <string>(
    vscode.workspace.getConfiguration("as3mxml").get("sdk.framework")
  );
  if (frameworkSetting) {
    //no fallbacks if this SDK isn't valid!
    //this may return null
    return validateFrameworkSDK(frameworkSetting);
  }
  if (!sdkPath) {
    //for legacy reasons, we support falling back to the editor SDK
    let editorSetting = <string>(
      vscode.workspace.getConfiguration("as3mxml").get("sdk.editor")
    );
    if (editorSetting) {
      //no fallbacks if this SDK isn't valid!
      //this may return null
      return validateFrameworkSDK(editorSetting);
    }
  }
  //the following SDKs are all intelligent fallbacks
  if (!sdkPath) {
    //check if an Apache Royale Node module is installed locally in the workspace
    sdkPath = findSDKInLocalRoyaleNodeModule();
  }
  if (!sdkPath) {
    //check if an Apache FlexJS Node module is installed locally in the workspace
    sdkPath = findSDKInLocalFlexJSNodeModule();
  }
  if (!sdkPath) {
    //the ROYALE_HOME environment variable may point to an SDK
    sdkPath = findSDKInRoyaleHomeEnvironmentVariable();
  }
  if (!sdkPath) {
    //the FLEX_HOME environment variable may point to an SDK
    sdkPath = findSDKInFlexHomeEnvironmentVariable();
  }
  if (!sdkPath) {
    //this should be the same SDK that is used if the user tries to run the
    //compiler from the command line without an absolute path
    let sdkPaths = findSDKsInPathEnvironmentVariable();
    if (sdkPaths.length > 0) {
      sdkPath = sdkPaths[0];
    }
  }
  return sdkPath;
}
