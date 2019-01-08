/*
Copyright 2016-2019 Bowler Hat LLC

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
package com.as3mxml.vscode;

/**
 * Checks that the Java version is capable of running the AS3 & MXML language
 * server.
 */
public class CheckJavaVersion
{
    public static final int GOOD_VERSION = 0;
    public static final int BAD_VERSION = 100;

    public static void main(String[] args)
    {
        String version = System.getProperty("java.specification.version");
        String[] versionParts = version.split("-")[0].split("\\.");
        int major = Integer.parseInt(versionParts[0]);
        if (major > 1)
        {
            System.exit(GOOD_VERSION);
        }
        if (versionParts.length > 1)
        {
            int minor = Integer.parseInt(versionParts[1]);
            if (major == 1 && minor >= 8)
            {
                System.exit(GOOD_VERSION);
            }
        }
        System.exit(BAD_VERSION);
    }
}
