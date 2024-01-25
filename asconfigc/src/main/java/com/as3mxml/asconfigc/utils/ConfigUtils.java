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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.as3mxml.asconfigc.TopLevelFields;
import com.as3mxml.asconfigc.air.AIROptions;
import com.as3mxml.asconfigc.air.AIRPlatform;
import com.as3mxml.asconfigc.air.AIRSigningOptions;
import com.as3mxml.asconfigc.compiler.CompilerOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ConfigUtils {
	private static final String FILE_EXTENSION_AS = ".as";
	private static final String FILE_EXTENSION_MXML = ".mxml";

	public static String resolveMainClass(String mainClass, List<String> sourcePaths) {
		return resolveMainClass(mainClass, sourcePaths, null);
	}

	public static String resolveMainClass(String mainClass, List<String> sourcePaths, String rootWorkspacePath) {
		String mainClassBasePath = mainClass.replace(".", File.separator);
		if (sourcePaths != null) {
			for (String sourcePath : sourcePaths) {
				Path sourcePathPath = Paths.get(sourcePath);
				Path mainClassPathAS = sourcePathPath.resolve(mainClassBasePath + FILE_EXTENSION_AS);
				Path absoluteMainClassPathAS = mainClassPathAS;
				if (!absoluteMainClassPathAS.isAbsolute() && rootWorkspacePath != null) {
					absoluteMainClassPathAS = Paths.get(rootWorkspacePath).resolve(absoluteMainClassPathAS);
				}
				if (absoluteMainClassPathAS.toFile().exists()) {
					// verify that the absolute path exists, but return the
					// relative path instead.
					// this keeps the compile command smaller, but it also
					// reduces the possibility of spaces appearing in the
					// compile command options.
					// FCSH doesn't like paths with spaces.
					// see BowlerHatLLC/vscode-as3mxml#726
					return mainClassPathAS.toString();
				}
				Path mainClassPathMXML = sourcePathPath.resolve(mainClassBasePath + FILE_EXTENSION_MXML);
				Path absoluteMainClassPathMXML = mainClassPathMXML;
				if (!absoluteMainClassPathMXML.isAbsolute() && rootWorkspacePath != null) {
					absoluteMainClassPathMXML = Paths.get(rootWorkspacePath).resolve(absoluteMainClassPathMXML);
				}
				if (absoluteMainClassPathMXML.toFile().exists()) {
					// verify that the absolute path exists, but return the
					// relative path instead. see note above.
					return mainClassPathMXML.toString();
				}
			}
		} else {
			// no source paths, so assume the root of the project (which is the
			// same directory as asconfig.json)
			Path mainClassPathAS = Paths.get(mainClassBasePath + FILE_EXTENSION_AS);
			Path absoluteMainClassPathAS = mainClassPathAS;
			if (!absoluteMainClassPathAS.isAbsolute() && rootWorkspacePath != null) {
				absoluteMainClassPathAS = Paths.get(rootWorkspacePath).resolve(absoluteMainClassPathAS);
			}
			if (absoluteMainClassPathAS.toFile().exists()) {
				// verify that the absolute path exists, but return the relative
				// path instead. see note above.
				return mainClassPathAS.toString();
			}
			Path mainClassPathMXML = Paths.get(mainClassBasePath + FILE_EXTENSION_MXML);
			Path absoluteMainClassPathMXML = mainClassPathMXML;
			if (!absoluteMainClassPathMXML.isAbsolute() && rootWorkspacePath != null) {
				absoluteMainClassPathMXML = Paths.get(rootWorkspacePath).resolve(absoluteMainClassPathMXML);
			}
			if (absoluteMainClassPathMXML.toFile().exists()) {
				// verify that the absolute path exists, but return the relative
				// path instead. see note above.
				return mainClassPathMXML.toString();
			}
		}
		// as a final fallback, try in the current working directory
		Path mainClassPathAS = Paths.get(mainClassBasePath + FILE_EXTENSION_AS);
		if (mainClassPathAS.toFile().exists()) {
			return mainClassPathAS.toString();
		}
		Path mainClassPathMXML = Paths.get(mainClassBasePath + FILE_EXTENSION_MXML);
		if (mainClassPathMXML.toFile().exists()) {
			return mainClassPathMXML.toString();
		}
		return null;
	}

	public static JsonNode mergeConfigs(JsonNode configData, JsonNode baseConfigData) {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode result = mapper.createObjectNode();

		Set<String> allFieldNames = new HashSet<>();
		Iterator<String> fieldNames = baseConfigData.fieldNames();
		while (fieldNames.hasNext()) {
			String fieldName = fieldNames.next();
			allFieldNames.add(fieldName);
		}
		fieldNames = configData.fieldNames();
		while (fieldNames.hasNext()) {
			String fieldName = fieldNames.next();
			allFieldNames.add(fieldName);
		}

		allFieldNames.forEach(fieldName -> {
			if (TopLevelFields.EXTENDS.equals(fieldName)) {
				// safe to skip
				return;
			}
			boolean hasField = configData.has(fieldName);
			boolean baseHasField = baseConfigData.has(fieldName);
			if (hasField && baseHasField) {
				JsonNode newValue = configData.get(fieldName);
				JsonNode baseValue = baseConfigData.get(fieldName);
				if (TopLevelFields.APPLICATION.equals(fieldName)) {
					result.set(fieldName, mergeApplication(newValue, baseValue));
				} else if (TopLevelFields.COMPILER_OPTIONS.equals(fieldName)) {
					result.set(fieldName, mergeCompilerOptions(newValue, baseValue));
				} else if (TopLevelFields.AIR_OPTIONS.equals(fieldName)) {
					result.set(fieldName, mergeAirOptions(newValue, baseValue, true));
				} else {
					result.set(fieldName, mergeObjectsSimple(newValue, baseValue));
				}
			} else if (hasField) {
				result.set(fieldName, configData.get(fieldName));
			} else if (baseHasField) {
				result.set(fieldName, baseConfigData.get(fieldName));
			}
		});
		return result;
	}

	private static JsonNode mergeObjectsSimple(JsonNode object, JsonNode baseObject) {
		if (!object.isObject()) {
			return object;
		}

		ObjectMapper mapper = new ObjectMapper();
		ObjectNode result = mapper.createObjectNode();

		Iterator<String> fieldNames = baseObject.fieldNames();
		while (fieldNames.hasNext()) {
			String fieldName = fieldNames.next();
			result.set(fieldName, baseObject.get(fieldName));
		}

		fieldNames = object.fieldNames();
		while (fieldNames.hasNext()) {
			String fieldName = fieldNames.next();
			result.set(fieldName, object.get(fieldName));
		}
		return result;
	}

	private static JsonNode mergeArrays(JsonNode array, JsonNode baseArray) {
		Set<JsonNode> combinedNodes = new HashSet<>();

		Iterator<JsonNode> elements = baseArray.elements();
		while (elements.hasNext()) {
			JsonNode element = elements.next();
			combinedNodes.add(element);
		}

		elements = array.elements();
		while (elements.hasNext()) {
			JsonNode element = elements.next();
			combinedNodes.add(element);
		}

		ObjectMapper mapper = new ObjectMapper();
		ArrayNode result = mapper.createArrayNode();
		result.addAll(combinedNodes);
		return result;
	}

	private static JsonNode mergeArraysWithComparisonKey(JsonNode array, JsonNode baseArray, String comparisonKey) {
		Set<JsonNode> combinedNodes = new HashSet<>();

		Iterator<JsonNode> elements = array.elements();
		while (elements.hasNext()) {
			JsonNode element = elements.next();
			combinedNodes.add(element);
		}

		elements = baseArray.elements();
		while (elements.hasNext()) {
			JsonNode element = elements.next();
			if (combinedNodes.stream().noneMatch(otherElement -> {
				return otherElement.get(comparisonKey).equals(element.get(comparisonKey));
			})) {
				combinedNodes.add(element);
			}
		}

		ObjectMapper mapper = new ObjectMapper();
		ArrayNode result = mapper.createArrayNode();
		result.addAll(combinedNodes);
		return result;
	}

	private static JsonNode mergeCompilerOptions(JsonNode compilerOptions, JsonNode baseCompilerOptions) {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode result = mapper.createObjectNode();

		Iterator<String> fieldNames = baseCompilerOptions.fieldNames();
		while (fieldNames.hasNext()) {
			String fieldName = fieldNames.next();
			result.set(fieldName, baseCompilerOptions.get(fieldName));
		}

		fieldNames = compilerOptions.fieldNames();
		while (fieldNames.hasNext()) {
			String fieldName = fieldNames.next();
			JsonNode newValue = compilerOptions.get(fieldName);
			if (CompilerOptions.DEFINE.equals(fieldName)) {
				if (result.has(fieldName)) {
					JsonNode oldDefine = result.get(fieldName);
					result.set(fieldName,
							mergeArraysWithComparisonKey(newValue, oldDefine, CompilerOptions.DEFINE__NAME));
				} else {
					result.set(fieldName, newValue);
				}
			} else if (newValue.isArray()) {
				JsonNode oldArray = result.get(fieldName);
				if (oldArray != null && oldArray.isArray()) {
					result.set(fieldName, mergeArrays(newValue, oldArray));
				} else {
					result.set(fieldName, newValue);
				}
			} else {
				result.set(fieldName, newValue);
			}
		}

		return result;
	}

	private static JsonNode mergeApplication(JsonNode application, JsonNode baseApplication) {
		if (application.isTextual()) {
			return application;
		}

		JsonNode result = null;
		if (baseApplication.isTextual()) {
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode stringAsObject = mapper.createObjectNode();

			Set<String> platforms = Arrays.asList(AIRPlatform.class.getDeclaredFields()).stream().map((field) -> {
				String value = null;
				try {
					value = (String) field.get(AIRPlatform.class);
				} catch (IllegalAccessException e) {
					System.err.println("Fatal error");
				}
				return value;
			}).collect(Collectors.toSet());
			for (String platform : platforms) {
				stringAsObject.set(platform, baseApplication);
			}
			result = stringAsObject;
		} else {
			result = baseApplication;
		}

		return mergeObjectsSimple(application, result);
	}

	private static JsonNode mergeAirOptions(JsonNode airOptions, JsonNode baseAirOptions, boolean handlePlatforms) {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode result = mapper.createObjectNode();

		Set<String> allFieldNames = new HashSet<>();
		Iterator<String> fieldNames = baseAirOptions.fieldNames();
		while (fieldNames.hasNext()) {
			String fieldName = fieldNames.next();
			allFieldNames.add(fieldName);
		}
		fieldNames = airOptions.fieldNames();
		while (fieldNames.hasNext()) {
			String fieldName = fieldNames.next();
			allFieldNames.add(fieldName);
		}

		Set<String> platforms = Arrays.asList(AIRPlatform.class.getDeclaredFields()).stream().map((field) -> {
			String value = null;
			try {
				value = (String) field.get(AIRPlatform.class);
			} catch (IllegalAccessException e) {
				System.err.println("Fatal error");
			}
			return value;
		}).collect(Collectors.toSet());
		allFieldNames.forEach(fieldName -> {
			boolean hasField = airOptions.has(fieldName);
			boolean baseHasField = baseAirOptions.has(fieldName);
			if (hasField && baseHasField) {
				JsonNode newValue = airOptions.get(fieldName);
				JsonNode baseValue = baseAirOptions.get(fieldName);
				if (handlePlatforms && platforms.contains(fieldName)) {
					result.set(fieldName, mergeAirOptions(newValue, baseValue, false));
				} else if (AIROptions.FILES.equals(fieldName)) {
					result.set(fieldName, mergeArraysWithComparisonKey(newValue, baseValue, AIROptions.FILES__PATH));
				} else if (AIROptions.SIGNING_OPTIONS.equals(fieldName)) {
					result.set(fieldName, mergeSigningOptions(newValue, baseValue));
				} else if (newValue.isArray() && baseValue.isArray()) {
					result.set(fieldName, mergeArrays(newValue, baseValue));
				} else {
					result.set(fieldName, mergeObjectsSimple(newValue, baseValue));
				}
			} else if (hasField) {
				result.set(fieldName, airOptions.get(fieldName));
			} else if (baseHasField) {
				result.set(fieldName, baseAirOptions.get(fieldName));
			}
		});

		return result;
	}

	private static JsonNode mergeSigningOptions(JsonNode signingOptions, JsonNode baseSigningOptions) {
		boolean hasDebug = signingOptions.has(AIRSigningOptions.DEBUG);
		boolean hasRelease = signingOptions.has(AIRSigningOptions.RELEASE);
		if (!hasDebug && !hasRelease) {
			// nothing to merge. fully overrides the base
			return signingOptions;
		}
		if (hasDebug && hasRelease) {
			// fully overrides the base
			return signingOptions;
		}

		boolean baseHasDebug = baseSigningOptions.has(AIRSigningOptions.DEBUG);
		boolean baseHasRelease = baseSigningOptions.has(AIRSigningOptions.RELEASE);

		ObjectMapper mapper = new ObjectMapper();
		ObjectNode result = mapper.createObjectNode();

		if (hasDebug) {
			result.set(AIRSigningOptions.DEBUG, signingOptions.get(AIRSigningOptions.DEBUG));
		} else if (baseHasDebug) {
			result.set(AIRSigningOptions.DEBUG, baseSigningOptions.get(AIRSigningOptions.DEBUG));
		} else if (!baseHasRelease) // neither debug nor release
		{
			result.set(AIRSigningOptions.DEBUG, baseSigningOptions);
		}

		if (hasRelease) {
			result.set(AIRSigningOptions.RELEASE, signingOptions.get(AIRSigningOptions.RELEASE));
		} else if (baseHasRelease) {
			result.set(AIRSigningOptions.RELEASE, baseSigningOptions.get(AIRSigningOptions.RELEASE));
		} else if (!baseHasDebug) // neither debug nor release
		{
			result.set(AIRSigningOptions.RELEASE, baseSigningOptions);
		}

		return result;
	}
}