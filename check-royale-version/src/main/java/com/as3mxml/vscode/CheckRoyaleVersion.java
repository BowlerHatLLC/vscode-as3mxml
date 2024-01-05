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
package com.as3mxml.vscode;

import org.apache.royale.compiler.tree.as.IASNode;

/**
 * Checks that the Apache Royale version is compatible with the
 * ActionScript/MXML language server.
 */
public class CheckRoyaleVersion {
    public static final int GOOD_VERSION = 0;
    public static final int BAD_VERSION = 100;
    public static final int EXCEPTION_VERSION = 101;

    private static final int MIN_MAJOR = 0;
    private static final int MIN_MINOR = 9;
    private static final int MIN_REVISION = 10;

    public static void main(String[] args) {
        try {
            String sdkVersion = IASNode.class.getPackage().getImplementationVersion();
            // remove -SNAPSHOT, if present. then, split on the "." character.
            String[] versionParts = sdkVersion.split("-")[0].split("\\.");
            int major = 0;
            int minor = 0;
            int revision = 0;
            if (versionParts.length >= 3) {
                major = Integer.parseInt(versionParts[0]);
                minor = Integer.parseInt(versionParts[1]);
                revision = Integer.parseInt(versionParts[2]);
            }
            if (major > MIN_MAJOR) {
                // major version is valid
                System.exit(GOOD_VERSION);
            } else if (major == MIN_MAJOR) {
                if (minor > MIN_MINOR) {
                    // minor version is valid
                    System.exit(GOOD_VERSION);
                } else if (minor == MIN_MINOR) {
                    if (revision >= MIN_REVISION) {
                        // revision is valid
                        System.exit(GOOD_VERSION);
                    }
                }
            }
            // version is too old!
            System.exit(BAD_VERSION);
        } catch (Exception e) {
            System.exit(EXCEPTION_VERSION);
        }
    }
}
