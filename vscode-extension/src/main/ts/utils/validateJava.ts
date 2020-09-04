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
import * as child_process from "child_process";
import * as fs from "fs";
import * as path from "path";

export default function validateJava(
  extensionPath: string,
  javaPath: string
): boolean {
  //on macOS, /usr/libexec/java_home may be specified accidentally
  //it's an executable, and it returns 0 (even if it receives invalid
  //options), so for usability, we treat it as a special case.
  if (path.basename(javaPath) === "java_home") {
    return false;
  }
  if (!fs.existsSync(javaPath)) {
    return false;
  }
  let args = [
    "-jar",
    path.join(extensionPath, "bin", "check-java-version.jar"),
  ];
  let result = child_process.spawnSync(javaPath, args);
  return result.status === 0;
}
