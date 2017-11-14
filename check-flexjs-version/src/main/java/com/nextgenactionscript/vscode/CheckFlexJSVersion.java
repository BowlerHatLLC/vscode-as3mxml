/*
Copyright 2016-2017 Bowler Hat LLC

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
package com.nextgenactionscript.vscode;

import org.apache.flex.compiler.tree.as.IASNode;

/**
 * Checks that the Apache FlexJS version is compatible with the NextGenAS
 * language server.
 */
public class CheckFlexJSVersion
{
    public static final int GOOD_VERSION = 0;
    public static final int BAD_VERSION = 100;
    public static final int EXCEPTION_VERSION = 101;

    public static void main(String[] args)
    {
        try
        {
            String sdkVersion = IASNode.class.getPackage().getImplementationVersion();
            String[] versionParts = sdkVersion.split("-")[0].split("\\.");
            int major = 0;
            int minor = 0;
            if (versionParts.length >= 3)
            {
                major = Integer.parseInt(versionParts[0]);
                minor = Integer.parseInt(versionParts[1]);
                //we don't actually care about the revision
                //revision = Integer.parseInt(versionParts[2]);
            }
            if (major > 0)
            {
                //major version is valid
                System.exit(GOOD_VERSION);
            }
            else if (major == 0)
            {
                if (minor >= 8)
                {
                    //minor version is valid
                    System.exit(GOOD_VERSION);
                }
            }
            //version is too old!
            System.exit(BAD_VERSION);
        }
        catch(Exception e)
        {
            System.exit(EXCEPTION_VERSION);
        }
    }
}
