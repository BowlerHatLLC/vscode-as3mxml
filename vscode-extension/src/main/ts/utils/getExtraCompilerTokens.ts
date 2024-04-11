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
import * as vscode from "vscode"

export default function getExtraCompilerTokens(): string[]
{
    let tokens : object = vscode.workspace.getConfiguration("as3mxml").get("asconfigc.additionalTokens");
    if (!tokens)
    {
        return [];
    }
    
    let k : keyof typeof tokens;
    let ret : string[] = [];
    for (const k in tokens)
    {
        ret.push("+" + k + "=" + <string>(tokens[k]));
    }

    return ret;
}
