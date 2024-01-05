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
import validateFrameworkSDK from "./validateFrameworkSDK";

const XML_VERSION_START = "<version>";
const XML_VERSION_END = "</version>";
const PATH_SDK_DESCRIPTION_FLEX = "flex-sdk-description.xml";

export default function validateFlex(
  sdkPath: string,
  minVersion?: string
): boolean {
  if (!validateFrameworkSDK(sdkPath)) {
    return false;
  }
  const flexDescriptionPath = path.join(sdkPath, PATH_SDK_DESCRIPTION_FLEX);
  if (!fs.existsSync(flexDescriptionPath)) {
    return false;
  }
  if (!minVersion) {
    return true;
  }
  const flexDescription = fs.readFileSync(flexDescriptionPath, "utf8");
  const versionString = readBetween(
    flexDescription,
    XML_VERSION_START,
    XML_VERSION_END
  );
  const versionParts = versionString.split("-")[0].split(".");
  const minVersionParts = minVersion.split("-")[0].split(".");
  const major = versionParts.length > 0 ? versionParts[0] : 0;
  const minor = versionParts.length > 1 ? versionParts[1] : 0;
  const revision = versionParts.length > 2 ? versionParts[2] : 0;
  const minMajor = minVersionParts.length > 0 ? minVersionParts[0] : 0;
  const minMinor = minVersionParts.length > 1 ? minVersionParts[1] : 0;
  const minRevision = minVersionParts.length > 2 ? minVersionParts[2] : 0;
  if (major > minMajor) {
    return true;
  } else if (major == minMajor) {
    if (minor > minMinor) {
      return true;
    } else if (minor == minMinor) {
      if (revision >= minRevision) {
        return true;
      }
    }
  }
  return false;
}

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
