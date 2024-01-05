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
import * as fs from "fs";
import * as path from "path";

const PATH_SDK_DESCRIPTION_AIR = "air-sdk-description.xml";
const PATH_SDK_DESCRIPTION_FLEX = "flex-sdk-description.xml";
const PATH_SDK_DESCRIPTION_ROYALE = "royale-sdk-description.xml";
const XML_NAME_START = "<name>";
const XML_NAME_END = "</name>";
const XML_VERSION_START = "<version>";
const XML_VERSION_END = "</version>";
const XML_BUILD_START = "<build>";
const XML_BUILD_END = "</build>";
const XML_OUTPUT_TARGET_JS = '<output-target name="js"';
const XML_OUTPUT_TARGET_SWF = '<output-target name="swf"';
const NAME_ROYALE = "Apache Royale";

function readBetween(
  fileContents: string,
  startText: string,
  endText: string
): string {
  let startIndex = fileContents.indexOf(startText);
  if (startIndex !== -1) {
    startIndex += startText.length;
    let endIndex = fileContents.indexOf(endText, startIndex + 1);
    if (endIndex !== -1) {
      return fileContents.substr(startIndex, endIndex - startIndex);
    }
  }
  return null;
}

function readName(fileContents: string, includeBuild: boolean): string {
  let sdkName = readBetween(fileContents, XML_NAME_START, XML_NAME_END);
  if (sdkName === NAME_ROYALE) {
    //in royale-sdk-description.xml, the version appears in a different field
    sdkName +=
      " " + readBetween(fileContents, XML_VERSION_START, XML_VERSION_END);
    //we should also display the output targets
    let hasJS = fileContents.indexOf(XML_OUTPUT_TARGET_JS) !== -1;
    let hasSWF = fileContents.indexOf(XML_OUTPUT_TARGET_SWF) !== -1;
    if (hasJS && !hasSWF) {
      sdkName += " (JS Only)";
    } else if (hasJS && hasSWF) {
      sdkName += " (JS & SWF)";
    } else if (!hasJS && hasSWF) {
      sdkName += " (SWF Only)";
    }
  }
  if (sdkName !== null && includeBuild) {
    let build = readBetween(fileContents, XML_BUILD_START, XML_BUILD_END);
    if (build !== null) {
      sdkName += "." + build;
    }
  }
  return sdkName;
}

export default function findSDKName(sdkPath: string): string {
  let sdkName: string = null;
  let royaleDescriptionPath = path.join(sdkPath, PATH_SDK_DESCRIPTION_ROYALE);
  if (fs.existsSync(royaleDescriptionPath)) {
    let royaleDescription = fs.readFileSync(royaleDescriptionPath, "utf8");
    sdkName = readName(royaleDescription, false);
  }
  if (sdkName === null) {
    let flexDescriptionPath = path.join(sdkPath, PATH_SDK_DESCRIPTION_FLEX);
    if (fs.existsSync(flexDescriptionPath)) {
      let flexDescription = fs.readFileSync(flexDescriptionPath, "utf8");
      sdkName = readName(flexDescription, false);
    }
  }
  if (sdkName === null) {
    let airDescriptionPath = path.join(sdkPath, PATH_SDK_DESCRIPTION_AIR);
    if (fs.existsSync(airDescriptionPath)) {
      let airDescription = fs.readFileSync(airDescriptionPath, "utf8");
      sdkName = readName(airDescription, true);
    }
  }
  return sdkName;
}
