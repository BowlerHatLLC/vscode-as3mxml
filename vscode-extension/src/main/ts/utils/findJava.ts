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
import * as path from "path";

export default function findJava(
  settingsPath: string,
  validate: (javaPath: string) => boolean
): string {
  if (settingsPath) {
    if (validate(settingsPath)) {
      return settingsPath;
    }
    //if the user specified java in the settings, no fallback
    //otherwise, it could be confusing
    return null;
  }

  var executableFile: string = "java";
  if (process["platform"] === "win32") {
    executableFile += ".exe";
  }

  if ("JAVA_HOME" in process.env) {
    let javaHome = <string>process.env.JAVA_HOME;
    let javaPath = path.join(javaHome, "bin", executableFile);
    if (validate(javaPath)) {
      return javaPath;
    }
  }

  if ("PATH" in process.env) {
    let PATH = <string>process.env.PATH;
    let paths = PATH.split(path.delimiter);
    let pathCount = paths.length;
    for (let i = 0; i < pathCount; i++) {
      let javaPath = path.join(paths[i], executableFile);
      if (validate(javaPath)) {
        return javaPath;
      }
    }
  }

  return null;
}
