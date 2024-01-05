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
package com.as3mxml.asconfigc.air;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public class AIROptionsParser {
	public AIROptionsParser() {
	}

	public void parse(String platform, boolean debug, String applicationDescriptorPath, String applicationContentPath,
			List<String> modulePaths, List<String> workerPaths, JsonNode options, List<String> result) {
		result.add("-" + AIROptions.PACKAGE);

		// AIR_SIGNING_OPTIONS begin
		// these are *desktop* signing options only
		// mobile signing options must be specified later!
		if (platform.equals(AIRPlatform.AIR)
				|| platform.equals(AIRPlatform.WINDOWS)
				|| platform.equals(AIRPlatform.MAC)) {
			if (options.has(AIROptions.SIGNING_OPTIONS)
					&& !overridesOptionForPlatform(options, AIROptions.SIGNING_OPTIONS, platform)) {
				parseSigningOptions(options.get(AIROptions.SIGNING_OPTIONS), debug, result);
			} else if (overridesOptionForPlatform(options, AIROptions.SIGNING_OPTIONS, platform)) {
				// desktop captive runtime
				parseSigningOptions(options.get(platform).get(AIROptions.SIGNING_OPTIONS), debug, result);
			} else if (!options.has(AIROptions.SIGNING_OPTIONS)) {
				// desktop shared runtime, but signing options overridden for windows or mac
				String osName = System.getProperty("os.name").toLowerCase();
				if (osName.startsWith("mac")
						&& overridesOptionForPlatform(options, AIROptions.SIGNING_OPTIONS, AIRPlatform.MAC)) {
					parseSigningOptions(options.get(AIRPlatform.MAC).get(AIROptions.SIGNING_OPTIONS), debug, result);
				} else if (osName.startsWith("windows")
						&& overridesOptionForPlatform(options, AIROptions.SIGNING_OPTIONS, AIRPlatform.WINDOWS)) {
					parseSigningOptions(options.get(AIRPlatform.WINDOWS).get(AIROptions.SIGNING_OPTIONS), debug,
							result);
				}
			}
		}
		// AIR_SIGNING_OPTIONS end

		if (overridesOptionForPlatform(options, AIROptions.TARGET, platform)) {
			setValueWithoutAssignment(AIROptions.TARGET, options.get(platform).get(AIROptions.TARGET).asText(), result);
		} else if (options.has(AIROptions.TARGET)) {
			setValueWithoutAssignment(AIROptions.TARGET, options.get(AIROptions.TARGET).asText(), result);
		} else {
			switch (platform) {
				case AIRPlatform.ANDROID: {
					if (debug) {
						setValueWithoutAssignment(AIROptions.TARGET, AIRTarget.APK_DEBUG, result);
					} else {
						setValueWithoutAssignment(AIROptions.TARGET, AIRTarget.APK_CAPTIVE_RUNTIME, result);
					}
					break;
				}
				case AIRPlatform.IOS: {
					if (debug) {
						setValueWithoutAssignment(AIROptions.TARGET, AIRTarget.IPA_DEBUG, result);
					} else {
						setValueWithoutAssignment(AIROptions.TARGET, AIRTarget.IPA_APP_STORE, result);
					}
					break;
				}
				case AIRPlatform.IOS_SIMULATOR: {
					if (debug) {
						setValueWithoutAssignment(AIROptions.TARGET, AIRTarget.IPA_DEBUG_INTERPRETER_SIMULATOR, result);
					} else {
						setValueWithoutAssignment(AIROptions.TARGET, AIRTarget.IPA_TEST_INTERPRETER_SIMULATOR, result);
					}
					break;
				}
				case AIRPlatform.WINDOWS: {
					// captive runtime
					setValueWithoutAssignment(AIROptions.TARGET, AIRTarget.BUNDLE, result);
					break;
				}
				case AIRPlatform.MAC: {
					// captive runtime
					setValueWithoutAssignment(AIROptions.TARGET, AIRTarget.BUNDLE, result);
					break;
				}
				default: {
					// shared runtime
					setValueWithoutAssignment(AIROptions.TARGET, AIRTarget.AIR, result);
					break;
				}
			}
		}

		// DEBUGGER_CONNECTION_OPTIONS begin
		if (debug && (platform.equals(AIRPlatform.ANDROID) || platform.equals(AIRPlatform.IOS)
				|| platform.equals(AIRPlatform.IOS_SIMULATOR))) {
			parseDebugOptions(options, platform, result);
		}
		// DEBUGGER_CONNECTION_OPTIONS end

		// iOS options begin
		if (overridesOptionForPlatform(options, AIROptions.SAMPLER, platform)) {
			result.add("-" + AIROptions.SAMPLER);
		}
		if (overridesOptionForPlatform(options, AIROptions.HIDE_ANE_LIB_SYMBOLS, platform)) {
			setBooleanValueWithoutAssignment(AIROptions.HIDE_ANE_LIB_SYMBOLS,
					options.get(platform).get(AIROptions.HIDE_ANE_LIB_SYMBOLS).asBoolean(), result);
		}
		if (overridesOptionForPlatform(options, AIROptions.EMBED_BITCODE, platform)) {
			setBooleanValueWithoutAssignment(AIROptions.EMBED_BITCODE,
					options.get(platform).get(AIROptions.EMBED_BITCODE).asBoolean(), result);
		}
		// iOS options end

		// Android options begin
		if (overridesOptionForPlatform(options, AIROptions.AIR_DOWNLOAD_URL, platform)) {
			setValueWithoutAssignment(AIROptions.AIR_DOWNLOAD_URL,
					options.get(platform).get(AIROptions.AIR_DOWNLOAD_URL).asText(), result);
		}
		if (overridesOptionForPlatform(options, AIROptions.ARCH, platform)) {
			setValueWithoutAssignment(AIROptions.ARCH, options.get(platform).get(AIROptions.ARCH).asText(), result);
		}
		// Android options end

		// NATIVE_SIGNING_OPTIONS begin
		// these are *mobile* signing options only
		// desktop signing options were already handled earlier
		if (platform.equals(AIRPlatform.ANDROID) || platform.equals(AIRPlatform.IOS)
				|| platform.equals(AIRPlatform.IOS_SIMULATOR)) {
			if (overridesOptionForPlatform(options, AIROptions.SIGNING_OPTIONS, platform)) {
				parseSigningOptions(options.get(platform).get(AIROptions.SIGNING_OPTIONS), debug, result);
			} else if (options.has(AIROptions.SIGNING_OPTIONS)) {
				parseSigningOptions(options.get(AIROptions.SIGNING_OPTIONS), debug, result);
			}
		}
		// NATIVE_SIGNING_OPTIONS end

		if (overridesOptionForPlatform(options, AIROptions.OUTPUT, platform)) {
			String outputPath = options.get(platform).get(AIROptions.OUTPUT).asText();
			result.add(outputPath);
		} else if (options.has(AIROptions.OUTPUT)) {
			String outputPath = options.get(AIROptions.OUTPUT).asText();
			result.add(outputPath);
		} else {
			// output is not defined, so generate an appropriate file name based
			// on the content's file name
			Path applicationContentFilePath = Paths.get(applicationContentPath);
			String fileName = applicationContentFilePath.getFileName().toString();
			int index = fileName.lastIndexOf(".");
			if (index != -1) {
				// remove the file extension, if it exists
				// adt will automatically add an extension, if necessary
				fileName = fileName.substring(0, index);
			} else {
				throw new Error("Cannot find Adobe AIR application output path.");
			}
			Path outputPath = applicationContentFilePath.resolveSibling(fileName);
			result.add(outputPath.toString());
		}

		result.add(applicationDescriptorPath);

		if (overridesOptionForPlatform(options, AIROptions.PLATFORMSDK, platform)) {
			setPathValueWithoutAssignment(AIROptions.PLATFORMSDK,
					options.get(platform).get(AIROptions.PLATFORMSDK).asText(), result);
		}

		// FILE_OPTIONS begin
		if (overridesOptionForPlatform(options, AIROptions.FILES, platform)) {
			parseFiles(options.get(platform).get(AIROptions.FILES), result);
		} else if (options.has(AIROptions.FILES)) {
			parseFiles(options.get(AIROptions.FILES), result);
		}
		appendSWFPath(applicationContentPath, result);
		if (modulePaths != null) {
			for (String modulePath : modulePaths) {
				appendSWFPath(modulePath, result);
			}
		}
		if (workerPaths != null) {
			for (String workerPath : workerPaths) {
				appendSWFPath(workerPath, result);
			}
		}

		if (overridesOptionForPlatform(options, AIROptions.EXTDIR, platform)) {
			parseExtdir(options.get(platform).get(AIROptions.EXTDIR), result);
		} else if (options.has(AIROptions.EXTDIR)) {
			parseExtdir(options.get(AIROptions.EXTDIR), result);
		}
		if (overridesOptionForPlatform(options, AIROptions.RESDIR, platform)) {
			setPathValueWithoutAssignment(AIROptions.RESDIR, options.get(platform).get(AIROptions.RESDIR).asText(),
					result);
		} else if (options.has(AIROptions.RESDIR)) {
			setPathValueWithoutAssignment(AIROptions.RESDIR, options.get(AIROptions.RESDIR).asText(), result);
		}
		// FILE_OPTIONS end

		// ANE_OPTIONS begin
		// ANE_OPTIONS end

		Iterator<String> fieldNames = options.fieldNames();
		while (fieldNames.hasNext()) {
			String fieldName = fieldNames.next();
			switch (fieldName) {
				case AIRPlatform.AIR:
				case AIRPlatform.ANDROID:
				case AIRPlatform.IOS:
				case AIRPlatform.IOS_SIMULATOR:
				case AIRPlatform.MAC:
				case AIRPlatform.WINDOWS:

				case AIROptions.AIR_DOWNLOAD_URL:
				case AIROptions.ARCH:
				case AIROptions.EMBED_BITCODE:
				case AIROptions.EXTDIR:
				case AIROptions.FILES:
				case AIROptions.HIDE_ANE_LIB_SYMBOLS:
				case AIROptions.OUTPUT:
				case AIROptions.PLATFORMSDK:
				case AIROptions.SAMPLER:
				case AIROptions.SIGNING_OPTIONS:
				case AIROptions.TARGET: {
					break;
				}
				default: {
					throw new Error("Unknown AIR option: " + fieldName);
				}
			}
		}
	}

	/**
	 * @private
	 *          Determines if an option is also specified for a specific platform.
	 */
	private boolean overridesOptionForPlatform(JsonNode globalOptions, String optionName, String platform) {
		return globalOptions.has(platform) && globalOptions.get(platform).has(optionName);
	}

	private void setValueWithoutAssignment(String optionName, String value, List<String> result) {
		result.add("-" + optionName);
		result.add(value.toString());
	}

	private void setBooleanValueWithoutAssignment(String optionName, boolean value, List<String> result) {
		result.add("-" + optionName);
		result.add(value ? "yes" : "no");
	}

	private void appendSWFPath(String swfPath, List<String> result) {
		Path swfFilePath = Paths.get(swfPath);
		if (!swfPath.equals(swfFilePath.getFileName().toString())) {
			result.add("-C");
			String dirname = swfFilePath.getParent().toString();
			result.add(dirname);
			String basename = swfFilePath.getFileName().toString();
			result.add(basename);
		} else {
			result.add(swfPath);
		}
	}

	private void parseExtdir(JsonNode extdir, List<String> result) {
		for (int i = 0, size = extdir.size(); i < size; i++) {
			String current = extdir.get(i).asText();
			setPathValueWithoutAssignment(AIROptions.EXTDIR, current, result);
		}
	}

	private class FolderToAddWithCOption {
		public FolderToAddWithCOption(File file) {
			this(file, null);
		}

		public FolderToAddWithCOption(File file, Path destPath) {
			this.file = file;
			this.destPath = destPath;
		}

		public File file;
		public Path destPath;
	}

	private boolean canUseCOptionForFolder(File file, Path destPath) {
		File currentFile = file;
		for (int i = destPath.getNameCount() - 1; i >= 0; i--) {
			if (currentFile == null) {
				return false;
			}
			String currentName = destPath.getName(i).toString();
			if (!currentFile.getName().equals(currentName)) {
				return false;
			}
			currentFile = file.getParentFile();
		}
		return true;
	}

	private void parseFiles(JsonNode files, List<String> result) {
		List<FolderToAddWithCOption> cOptionFolders = new ArrayList<>();
		List<File> cOptionRootFolders = new ArrayList<>();
		for (int i = 0, size = files.size(); i < size; i++) {
			JsonNode fileNode = files.get(i);
			String srcFile = null;
			String destPath = null;
			if (fileNode.isTextual()) {
				srcFile = fileNode.asText();
			} else {
				srcFile = fileNode.get(AIROptions.FILES__FILE).asText();
				destPath = fileNode.get(AIROptions.FILES__PATH).asText();
			}
			File fileToAdd = new File(srcFile);
			File absoluteFileToAdd = fileToAdd;
			if (!absoluteFileToAdd.isAbsolute()) {
				absoluteFileToAdd = new File(System.getProperty("user.dir"), srcFile);
			}

			// for some reason, isDirectory() may not work properly when we check
			// a file with a relative path
			if (absoluteFileToAdd.isDirectory()) {
				if (destPath == null) {
					// add these folders after everything else because we'll use
					// the -C option
					cOptionFolders.add(new FolderToAddWithCOption(fileToAdd));
					continue;
				} else if (destPath.equals(".")) {
					// add these folders after everything else because we'll use
					// the -C option
					cOptionRootFolders.add(fileToAdd);
					continue;
				} else {
					Path destPathPath = Paths.get(destPath);
					if (canUseCOptionForFolder(fileToAdd, destPathPath)) {
						// add these folders after everything else because we'll use
						// the -C option
						cOptionFolders.add(new FolderToAddWithCOption(fileToAdd, destPathPath));
						continue;
					}
				}
			}

			if (destPath == null) {
				destPath = fileToAdd.getName();
			}
			addFile(fileToAdd, destPath, result);
		}
		for (File folder : cOptionRootFolders) {
			result.add("-C");
			result.add(folder.getPath());
			result.add(".");
		}
		for (FolderToAddWithCOption cOptionFolder : cOptionFolders) {
			File folder = cOptionFolder.file;
			Path destPath = cOptionFolder.destPath;
			if (destPath == null) {
				String parentPath = folder.getParent();
				if (parentPath == null) {
					parentPath = ".";
				}
				result.add("-C");
				result.add(parentPath);
				result.add(folder.getName());
			} else {
				Path baseFolderPath = folder.toPath();
				for (int i = 0; i < destPath.getNameCount(); i++) {
					baseFolderPath = baseFolderPath.getParent();
					if (baseFolderPath == null) {
						break;
					}
				}
				String baseFolderPathString = ".";
				if (baseFolderPath != null) {
					baseFolderPathString = baseFolderPath.toString();
				}
				result.add("-C");
				result.add(baseFolderPathString);
				result.add(destPath.toString());
			}
		}
	}

	private void addFile(File srcFile, String destPath, List<String> result) {
		File absoluteSrcFile = srcFile;
		if (!absoluteSrcFile.isAbsolute()) {
			absoluteSrcFile = new File(System.getProperty("user.dir"), srcFile.getPath());
		}
		// for some reason, isDirectory() may not work properly when we check
		// a file with a relative path
		if (absoluteSrcFile.isDirectory()) {
			// Adobe's documentation for adt says that the -e option can
			// accept a directory, but it only seems to work with files, so
			// we read the directory contents to add the files individually
			File[] files = srcFile.listFiles();
			for (int i = 0, length = files.length; i < length; i++) {
				File file = files[i];
				String fileDestPath = Paths.get(destPath, file.getName()).toString();
				addFile(file, fileDestPath, result);
			}
			return;
		}
		result.add("-e");
		result.add(srcFile.getPath());
		result.add(destPath);
	}

	private void setPathValueWithoutAssignment(String optionName, String value, List<String> result) {
		result.add("-" + optionName);
		result.add(value);
	}

	private void parseDebugOptions(JsonNode airOptions, String platform, List<String> result) {
		boolean useDefault = true;
		if (airOptions.has(platform)) {
			JsonNode platformOptions = airOptions.get(platform);
			if (platformOptions.has(AIROptions.CONNECT)) {
				useDefault = false;
				JsonNode connectValue = platformOptions.get(AIROptions.CONNECT);
				if (!connectValue.isBoolean()) {
					result.add("-" + AIROptions.CONNECT);
					result.add(connectValue.asText());
				} else if (connectValue.asBoolean() == true) {
					result.add("-" + AIROptions.CONNECT);
				}
			}
			if (platformOptions.has(AIROptions.LISTEN)) {
				useDefault = false;
				JsonNode listenValue = platformOptions.get(AIROptions.LISTEN);
				if (!listenValue.isBoolean()) {
					result.add("-" + AIROptions.LISTEN);
					result.add(listenValue.asText());
				} else if (listenValue.asBoolean() == true) {
					result.add("-" + AIROptions.LISTEN);
				}
			}
		}
		if (useDefault) {
			// if both connect and listen options are omitted, use the
			// connect as the default with no host name.
			result.add("-" + AIROptions.CONNECT);
		}
	}

	protected void parseSigningOptions(JsonNode signingOptions, boolean debug, List<String> result) {
		if (signingOptions.has(AIRSigningOptions.DEBUG) && debug) {
			parseSigningOptions(signingOptions.get(AIRSigningOptions.DEBUG), debug, result);
			return;
		}
		if (signingOptions.has(AIRSigningOptions.RELEASE) && !debug) {
			parseSigningOptions(signingOptions.get(AIRSigningOptions.RELEASE), debug, result);
			return;
		}

		if (signingOptions.has(AIRSigningOptions.PROVISIONING_PROFILE)) {
			setPathValueWithoutAssignment(AIRSigningOptions.PROVISIONING_PROFILE,
					signingOptions.get(AIRSigningOptions.PROVISIONING_PROFILE).asText(), result);
		}
		if (signingOptions.has(AIRSigningOptions.ALIAS)) {
			setValueWithoutAssignment(AIRSigningOptions.ALIAS, signingOptions.get(AIRSigningOptions.ALIAS).asText(),
					result);
		}
		if (signingOptions.has(AIRSigningOptions.STORETYPE)) {
			setValueWithoutAssignment(AIRSigningOptions.STORETYPE,
					signingOptions.get(AIRSigningOptions.STORETYPE).asText(), result);
		}
		if (signingOptions.has(AIRSigningOptions.KEYSTORE)) {
			setPathValueWithoutAssignment(AIRSigningOptions.KEYSTORE,
					signingOptions.get(AIRSigningOptions.KEYSTORE).asText(), result);
		}
		if (signingOptions.has(AIRSigningOptions.PROVIDER_NAME)) {
			setValueWithoutAssignment(AIRSigningOptions.PROVIDER_NAME,
					signingOptions.get(AIRSigningOptions.PROVIDER_NAME).asText(), result);
		}
		if (signingOptions.has(AIRSigningOptions.TSA)) {
			setValueWithoutAssignment(AIRSigningOptions.TSA, signingOptions.get(AIRSigningOptions.TSA).asText(),
					result);
		}
		Iterator<String> fieldNames = signingOptions.fieldNames();
		while (fieldNames.hasNext()) {
			String fieldName = fieldNames.next();
			switch (fieldName) {
				case AIRSigningOptions.ALIAS:
				case AIRSigningOptions.STORETYPE:
				case AIRSigningOptions.KEYSTORE:
				case AIRSigningOptions.PROVIDER_NAME:
				case AIRSigningOptions.TSA:
				case AIRSigningOptions.PROVISIONING_PROFILE: {
					break;
				}
				default: {
					throw new Error("Unknown Adobe AIR signing option: " + fieldName);
				}
			}
		}
	}
}