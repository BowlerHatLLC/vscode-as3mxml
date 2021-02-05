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

/**
 * Utilities for finding and using an ActionScript SDK.
 */
public class GenericSDKUtils {
	private static final String ENV_FLEX_HOME = "FLEX_HOME";
	private static final String ENV_PATH = "PATH";
	private static final String BIN = "bin";
	private static final String MXMLC = "mxmlc";
	private static final String COMPC = "compc";
	private static final String FLEX_SDK_DESCRIPTION = "flex-sdk-description.xml";
	private static final String AIR_SDK_DESCRIPTION = "air-sdk-description.xml";
	private static final String ROYALE_SDK_DESCRIPTION = "royale-sdk-description.xml";

	/**
	 * Determines if a directory contains a valid ActionScript SDK.
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
		if (file.exists() && !file.isDirectory()) {
			return hasCompilers(absolutePath);
		}
		sdkDescriptionPath = absolutePath.resolve(AIR_SDK_DESCRIPTION);
		file = sdkDescriptionPath.toFile();
		if (file.exists() && !file.isDirectory()) {
			return hasCompilers(absolutePath);
		}
		sdkDescriptionPath = absolutePath.resolve(ROYALE_SDK_DESCRIPTION);
		file = sdkDescriptionPath.toFile();
		if (file.exists() && !file.isDirectory()) {
			return hasCompilers(absolutePath);
		}
		return false;
	}

	private static boolean hasCompilers(Path sdkPath) {
		Path compilerPath = sdkPath.resolve(BIN).resolve(MXMLC);
		File file = compilerPath.toFile();
		if (!file.exists() || file.isDirectory()) {
			return false;
		}
		compilerPath = sdkPath.resolve(BIN).resolve(COMPC);
		file = compilerPath.toFile();
		if (!file.exists() || file.isDirectory()) {
			return false;
		}
		return true;
	}

	/**
	 * Attempts to find a valid SDK by searching for the FLEX_HOME
	 * environment variable and testing the PATH environment variable.
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
				File file = new File(currentPath, MXMLC);
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