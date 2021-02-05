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
import * as fs from "fs";
import * as path from "path";
import validateFrameworkSDK from "./validateFrameworkSDK";

const ENVIRONMENT_VARIABLE_PATH = "PATH";
const NODE_MODULES = "node_modules";
const MODULE_ORG = "@apache-royale";
const MODULE_NAMES = ["royale-js", "royale-js-swf"];
const MODULE_NAME_FLEXJS = "flexjs";

export default function findSDKsInPathEnvironmentVariable(): string[] {
  let result: string[] = [];

  if (!(ENVIRONMENT_VARIABLE_PATH in process.env)) {
    return result;
  }

  let PATH = <string>process.env.PATH;
  let paths = PATH.split(path.delimiter);
  paths.forEach((currentPath) => {
    //first check if this directory contains the NPM version of either
    //Apache Royale or Apache FlexJS for Windows
    let mxmlcPath = path.join(currentPath, "mxmlc.cmd");
    if (fs.existsSync(mxmlcPath)) {
      for (let i = 0, count = MODULE_NAMES.length; i < count; i++) {
        let moduleName = MODULE_NAMES[i];
        let sdkPath = path.join(
          path.dirname(mxmlcPath),
          NODE_MODULES,
          MODULE_ORG,
          moduleName
        );
        let validSDK = validateFrameworkSDK(sdkPath);
        if (validSDK !== null) {
          result.push(validSDK);
        }
      }
      let sdkPath = path.join(
        path.dirname(mxmlcPath),
        NODE_MODULES,
        MODULE_NAME_FLEXJS
      );
      let validSDK = validateFrameworkSDK(sdkPath);
      if (validSDK !== null) {
        result.push(validSDK);
      }
    } else {
      mxmlcPath = path.join(currentPath, "mxmlc");
      if (fs.existsSync(mxmlcPath)) {
        //this may a symbolic link rather than the actual file, such as
        //when an SDK is installed with npm on macOS, so get the real
        //path.
        mxmlcPath = fs.realpathSync(mxmlcPath);
        //first, check for bin/mxmlc
        let frameworksPath = path.join(
          path.dirname(mxmlcPath),
          "..",
          "frameworks"
        );
        if (
          fs.existsSync(frameworksPath) &&
          fs.statSync(frameworksPath).isDirectory()
        ) {
          let sdkPath = path.join(path.dirname(mxmlcPath), "..");
          let validSDK = validateFrameworkSDK(sdkPath);
          if (validSDK !== null) {
            result.push(validSDK);
          }
        }
        //then, check for js/bin/mxmlc
        frameworksPath = path.join(
          path.dirname(mxmlcPath),
          "..",
          "..",
          "frameworks"
        );
        if (
          fs.existsSync(frameworksPath) &&
          fs.statSync(frameworksPath).isDirectory()
        ) {
          let sdkPath = path.join(path.dirname(mxmlcPath), "..", "..");
          let validSDK = validateFrameworkSDK(sdkPath);
          if (validSDK !== null) {
            result.push(validSDK);
          }
        }
      }
    }
  });
  return result;
}
