package com.as3mxml.vscode.utils;

import java.nio.file.Path;

public class ActionScriptSDKUtils {
	public static boolean isRoyaleSDK(Path path) {
		path = path.resolve("frameworks");
		return isRoyaleFramework(path);
	}

	public static boolean isRoyaleFramework(Path path) {
		path = path.resolve("../royale-sdk-description.xml");
		return path.toFile().exists();
	}

	public static boolean isAIRSDK(Path path) {
		Path airDescriptionPath = path.resolve("air-sdk-description.xml");
		if (!airDescriptionPath.toFile().exists()) {
			return false;
		}
		Path mxmlcJarPath = path.resolve("lib/mxmlc-cli.jar");
		if (!mxmlcJarPath.toFile().exists()) {
			return false;
		}
		Path compcJarPath = path.resolve("lib/compc-cli.jar");
		if (!compcJarPath.toFile().exists()) {
			return false;
		}
		return true;
	}
}