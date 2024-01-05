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
package com.as3mxml.asconfigc.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ApacheRoyaleUtils {
	private static final String ENV_ROYALE_HOME = "ROYALE_HOME";
	private static final String ENV_PATH = "PATH";
	private static final String ROYALE_ASJS = "royale-asjs";
	private static final String NODE_MODULES = "node_modules";
	private static final String NPM_ORG_ROYALE = "@apache-royale";
	private static final String NPM_PACKAGE_ROYALE_JS = "royale-js";
	private static final String NPM_PACKAGE_ROYALE_SWF = "royale-js-swf";
	private static final String JS = "js";
	private static final String BIN = "bin";
	private static final String ASJSC = "asjsc";
	private static final String ROYALE_SDK_DESCRIPTION = "royale-sdk-description.xml";

	/**
	 * Determines if a directory contains a valid Apache Royale SDK. May modify
	 * the path of the "real" SDK is in royale-asjs
	 */
	public static Path isValidSDK(Path absolutePath) {
		if (absolutePath == null) {
			return null;
		}
		if (isValidSDKInternal(absolutePath)) {
			return absolutePath;
		}
		Path royalePath = absolutePath.resolve(ROYALE_ASJS);
		if (isValidSDKInternal(royalePath)) {
			return royalePath;
		}
		return null;
	}

	private static boolean isValidSDKInternal(Path absolutePath) {
		if (absolutePath == null || !absolutePath.isAbsolute()) {
			return false;
		}
		File file = absolutePath.toFile();
		if (!file.isDirectory()) {
			return false;
		}
		Path sdkDescriptionPath = absolutePath.resolve(ROYALE_SDK_DESCRIPTION);
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
	 * Attempts to find a valid Apache Royale SDK by searching for the
	 * royale NPM module, testing the ROYALE_HOME environment variable, and
	 * finally, testing the PATH environment variable.
	 */
	public static String findSDK() {
		String royaleHome = System.getenv(ENV_ROYALE_HOME);
		if (royaleHome != null) {
			Path royaleHomePath = Paths.get(royaleHome);
			royaleHomePath = isValidSDK(royaleHomePath);
			if (royaleHomePath != null) {
				return royaleHomePath.toString();
			}
		}
		String envPath = System.getenv(ENV_PATH);
		if (envPath != null) {
			String[] paths = envPath.split(File.pathSeparator);
			for (String currentPath : paths) {
				// first check if this directory contains the NPM version for
				// Windows
				File file = new File(currentPath, ASJSC + ".cmd");
				if (file.exists() && !file.isDirectory()) {
					Path npmPath = Paths.get(currentPath, NODE_MODULES, NPM_ORG_ROYALE, NPM_PACKAGE_ROYALE_JS);
					npmPath = isValidSDK(npmPath);
					if (npmPath != null) {
						return npmPath.toString();
					}
					npmPath = Paths.get(currentPath, NODE_MODULES, NPM_ORG_ROYALE, NPM_PACKAGE_ROYALE_SWF);
					npmPath = isValidSDK(npmPath);
					if (npmPath != null) {
						return npmPath.toString();
					}
				}
				file = new File(currentPath, ASJSC);
				if (file.exists() && !file.isDirectory()) {
					// this may a symbolic link rather than the actual file,
					// such as when Apache Royale is installed with NPM on
					// Mac, so get the real path.
					Path sdkPath = file.toPath();
					try {
						sdkPath = sdkPath.toRealPath();
					} catch (IOException e) {
						// didn't seem to work, for some reason
						return null;
					}
					sdkPath = sdkPath.getParent().getParent().getParent();
					sdkPath = isValidSDK(sdkPath);
					if (sdkPath != null) {
						return sdkPath.toString();
					}
				}
			}
		}
		return null;
	}
}