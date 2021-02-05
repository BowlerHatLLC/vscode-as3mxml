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
package com.as3mxml.asconfigc.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ApacheFlexJSUtils {
	private static final String ENV_FLEX_HOME = "FLEX_HOME";
	private static final String ENV_PATH = "PATH";
	private static final String NPM_FLEXJS = "flexjs";
	private static final String JS = "js";
	private static final String BIN = "bin";
	private static final String ASJSC = "asjsc";
	private static final String FLEX_SDK_DESCRIPTION = "flex-sdk-description.xml";

	/**
	 * Determines if a directory contains a valid Apache FlexJS SDK.
	 */
	public static boolean isValidSDK(Path absolutePath) {
		if (absolutePath == null || !absolutePath.isAbsolute()) {
			return false;
		}
		File file = absolutePath.toFile();
		if (!file.isDirectory()) {
			return false;
		}
		Path sdkDescriptionPath = absolutePath.resolve(FLEX_SDK_DESCRIPTION);
		file = sdkDescriptionPath.toFile();
		if (!file.exists() || file.isDirectory()) {
			return false;
		}
		Path compilerPath = absolutePath.resolve(JS).resolve(BIN).resolve(ASJSC);
		file = compilerPath.toFile();
		if (!file.exists() || file.isDirectory()) {
			return false;
		}
		return true;
	}

	/**
	 * Attempts to find a valid Apache FlexJS SDK by searching for the
	 * flexjs NPM module, testing the FLEX_HOME environment variable, and
	 * finally, testing the PATH environment variable.
	 */
	public static String findSDK() {
		String flexHome = System.getenv(ENV_FLEX_HOME);
		if (flexHome != null && isValidSDK(Paths.get(flexHome))) {
			return flexHome;
		}
		String envPath = System.getenv(ENV_PATH);
		if (envPath != null) {
			String[] paths = envPath.split(File.pathSeparator);
			for (String currentPath : paths) {
				//first check if this directory contains the NPM version for
				//Windows
				File file = new File(currentPath, ASJSC + ".cmd");
				if (file.exists() && !file.isDirectory()) {
					Path npmPath = Paths.get(currentPath, "node_modules", NPM_FLEXJS);
					if (isValidSDK(npmPath)) {
						return npmPath.toString();
					}
				}
				file = new File(currentPath, ASJSC);
				if (file.exists() && !file.isDirectory()) {
					//this may a symbolic link rather than the actual file,
					//such as when Apache Royale is installed with NPM on
					//Mac, so get the real path.
					Path sdkPath = file.toPath();
					try {
						sdkPath = sdkPath.toRealPath();
					} catch (IOException e) {
						//didn't seem to work, for some reason
						return null;
					}
					sdkPath = sdkPath.getParent().getParent().getParent();
					if (isValidSDK(sdkPath)) {
						return sdkPath.toString();
					}
				}
			}
		}
		return null;
	}
}