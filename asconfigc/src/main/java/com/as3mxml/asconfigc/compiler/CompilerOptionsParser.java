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
package com.as3mxml.asconfigc.compiler;

import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.as3mxml.asconfigc.utils.OptionsFormatter;
import com.as3mxml.asconfigc.utils.JsonUtils;

public class CompilerOptionsParser {
	public static class UnknownCompilerOptionException extends Exception {
		private static final long serialVersionUID = 1L;
		private static final String MESSAGE = "Unknown compiler option: ";

		public UnknownCompilerOptionException(String optionName) {
			super(MESSAGE + optionName + ".");
			this.optionName = optionName;
		}

		private String optionName;

		public String getOptionName() {
			return optionName;
		}
	}

	public CompilerOptionsParser() {
	}

	public void parse(JsonNode options, Boolean debugBuild, List<String> result) throws UnknownCompilerOptionException {
		Iterator<String> iterator = options.fieldNames();
		while (iterator.hasNext()) {
			String key = iterator.next();
			switch (key) {
				case CompilerOptions.ACCESSIBLE: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.ADVANCED_TELEMETRY: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.ALLOW_ABSTRACT_CLASSES: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.ALLOW_IMPORT_ALIASES: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.ALLOW_PRIVATE_CONSTRUCTORS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.BENCHMARK: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.CONTEXT_ROOT: {
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.CONTRIBUTOR: {
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.CREATOR: {
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.DATE: {
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.DEBUG: {
					if (debugBuild == null) {
						// don't set -debug if it's been overridden
						OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					}
					break;
				}
				case CompilerOptions.DEBUG_PASSWORD: {
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.DEFAULT_BACKGROUND_COLOR: {
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.DEFAULT_FRAME_RATE: {
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.DEFAULT_SIZE: {
					setDefaultSize(options.get(key), result);
					break;
				}
				case CompilerOptions.DEFAULTS_CSS_FILES: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, result);
					break;
				}
				case CompilerOptions.DEFINE: {
					setDefine(key, options.get(key), result);
					break;
				}
				case CompilerOptions.JS_DEFINE: {
					setDefine(key, options.get(key), result);
					break;
				}
				case CompilerOptions.DESCRIPTION: {
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.DIRECTORY: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.DUMP_CONFIG: {
					OptionsFormatter.setPathValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.EXCLUDE_DEFAULTS_CSS_FILES: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, result);
					break;
				}
				case CompilerOptions.EXPORT_PUBLIC_SYMBOLS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.EXPORT_PROTECTED_SYMBOLS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.EXPORT_INTERNAL_SYMBOLS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.EXTERNAL_LIBRARY_PATH: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, result);
					break;
				}
				case CompilerOptions.HTML_OUTPUT_FILENAME: {
					OptionsFormatter.setPathValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.HTML_TEMPLATE: {
					OptionsFormatter.setPathValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.INCLUDE_CLASSES: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.setValuesWithCommas(key, values, result);
					break;
				}
				case CompilerOptions.INCLUDE_FILE: {
					parseIncludeFile(options.get(key), result);
					break;
				}
				case CompilerOptions.INCLUDE_LIBRARIES: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, result);
					break;
				}
				case CompilerOptions.INCLUDE_NAMESPACES: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendValues(key, values, result);
					break;
				}
				case CompilerOptions.INCLUDE_SOURCES: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, result);
					break;
				}
				case CompilerOptions.INLINE_CONSTANTS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.JS_COMPILER_OPTION: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					appendJSCompilerOptions(key, values, result);
					break;
				}
				case CompilerOptions.JS_COMPLEX_IMPLICIT_COERCIONS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.JS_DEFAULT_INITIALIZERS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.JS_DYNAMIC_ACCESS_UNKNOWN_MEMBERS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.JS_EXTERNAL_LIBRARY_PATH: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, result);
					break;
				}
				case CompilerOptions.JS_LIBRARY_PATH: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, result);
					break;
				}
				case CompilerOptions.JS_OUTPUT: {
					OptionsFormatter.setPathValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.JS_OUTPUT_OPTIMIZATION: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.setValues(key, values, result);
					break;
				}
				case CompilerOptions.JS_OUTPUT_TYPE: {
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.JS_VECTOR_EMULATION_CLASS: {
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.JS_VECTOR_INDEX_CHECKS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.KEEP_ALL_TYPE_SELECTORS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.KEEP_AS3_METADATA: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendValues(key, values, result);
					break;
				}
				case CompilerOptions.KEEP_GENERATED_ACTIONSCRIPT: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.LANGUAGE: {
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.LIBRARY_PATH: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, result);
					break;
				}
				case CompilerOptions.LINK_REPORT: {
					OptionsFormatter.setPathValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.LOAD_CONFIG: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, result);
					break;
				}
				case CompilerOptions.JS_LOAD_CONFIG: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, result);
					break;
				}
				case CompilerOptions.LOAD_EXTERNS: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, result);
					break;
				}
				case CompilerOptions.LOCALE: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.setValues(key, values, result);
					break;
				}
				case CompilerOptions.NAMESPACE: {
					appendNamespace(options.get(key), result);
					break;
				}
				case CompilerOptions.OPTIMIZE: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.OMIT_TRACE_STATEMENTS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.OUTPUT: {
					OptionsFormatter.setPathValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.PRELOADER: {
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_PUBLIC_SYMBOLS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_PUBLIC_STATIC_METHODS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_PUBLIC_INSTANCE_METHODS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_PUBLIC_STATIC_VARIABLES: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_PUBLIC_INSTANCE_VARIABLES: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_PUBLIC_STATIC_ACCESSORS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_PUBLIC_INSTANCE_ACCESSORS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_PROTECTED_SYMBOLS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_PROTECTED_STATIC_METHODS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_PROTECTED_INSTANCE_METHODS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_PROTECTED_STATIC_VARIABLES: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_PROTECTED_INSTANCE_VARIABLES: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_PROTECTED_STATIC_ACCESSORS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_PROTECTED_INSTANCE_ACCESSORS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_INTERNAL_SYMBOLS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_INTERNAL_STATIC_METHODS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_INTERNAL_INSTANCE_METHODS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_INTERNAL_STATIC_VARIABLES: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_INTERNAL_INSTANCE_VARIABLES: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_INTERNAL_STATIC_ACCESSORS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PREVENT_RENAME_INTERNAL_INSTANCE_ACCESSORS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.PUBLISHER: {
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.REMOVE_CIRCULARS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.SERVICES: {
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.SHOW_UNUSED_TYPE_SELECTOR_WARNINGS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.SIZE_REPORT: {
					OptionsFormatter.setPathValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.SOURCE_MAP: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.SOURCE_MAP_SOURCE_ROOT: {
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.SOURCE_PATH: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, result);
					break;
				}
				case CompilerOptions.STATIC_LINK_RUNTIME_SHARED_LIBRARIES: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.STRICT: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.STRICT_IDENTIFIER_NAMES: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.SWF_EXTERNAL_LIBRARY_PATH: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, result);
					break;
				}
				case CompilerOptions.SWF_LIBRARY_PATH: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, result);
					break;
				}
				case CompilerOptions.SWF_VERSION: {
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.TARGET_PLAYER: {
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.TARGETS: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.setValuesWithCommas(key, values, result);
					break;
				}
				case CompilerOptions.THEME: {
					JsonNode themeNode = options.get(key);
					if (themeNode.isArray()) {
						List<String> values = JsonUtils.jsonNodeToListOfStrings(themeNode);
						OptionsFormatter.setThenAppendPaths(key, values, result);
					} else {
						OptionsFormatter.setPathValue(key, options.get(key).asText(), result);
					}
					break;
				}
				case CompilerOptions.TITLE: {
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.TOOLS_LOCALE: {
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.USE_DIRECT_BLIT: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.USE_GPU: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.USE_NETWORK: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.USE_RESOURCE_BUNDLE_METADATA: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.VERBOSE_STACKTRACES: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.WARNINGS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.WARN_PUBLIC_VARS: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				default: {
					throw new UnknownCompilerOptionException(key);
				}
			}
		}
	}

	public void parseForASDoc(JsonNode options, List<String> result)
			throws UnknownCompilerOptionException {
		Iterator<String> iterator = options.fieldNames();
		while (iterator.hasNext()) {
			String key = iterator.next();
			switch (key) {
				case CompilerOptions.DEFINE: {
					setDefine(key, options.get(key), result);
					break;
				}
				case CompilerOptions.EXTERNAL_LIBRARY_PATH: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, result);
					break;
				}
				case CompilerOptions.INCLUDE_LIBRARIES: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, result);
					break;
				}
				case CompilerOptions.LIBRARY_PATH: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, result);
					break;
				}
				case CompilerOptions.LOAD_CONFIG: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, result);
					break;
				}
				case CompilerOptions.STRICT: {
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.SOURCE_PATH: {
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, result);
					break;
				}
				case CompilerOptions.ACCESSIBLE:
				case CompilerOptions.ADVANCED_TELEMETRY:
				case CompilerOptions.ALLOW_ABSTRACT_CLASSES:
				case CompilerOptions.ALLOW_IMPORT_ALIASES:
				case CompilerOptions.ALLOW_PRIVATE_CONSTRUCTORS:
				case CompilerOptions.BENCHMARK:
				case CompilerOptions.DEBUG:
				case CompilerOptions.DEBUG_PASSWORD:
				case CompilerOptions.DEFAULT_BACKGROUND_COLOR:
				case CompilerOptions.DEFAULT_FRAME_RATE:
				case CompilerOptions.DEFAULT_SIZE:
				case CompilerOptions.DEFAULTS_CSS_FILES:
				case CompilerOptions.JS_DEFINE:
				case CompilerOptions.DIRECTORY:
				case CompilerOptions.DUMP_CONFIG:
				case CompilerOptions.EXCLUDE_DEFAULTS_CSS_FILES:
				case CompilerOptions.EXPORT_PUBLIC_SYMBOLS:
				case CompilerOptions.EXPORT_PROTECTED_SYMBOLS:
				case CompilerOptions.EXPORT_INTERNAL_SYMBOLS:
				case CompilerOptions.HTML_OUTPUT_FILENAME:
				case CompilerOptions.HTML_TEMPLATE:
				case CompilerOptions.INCLUDE_CLASSES:
				case CompilerOptions.INCLUDE_FILE:
				case CompilerOptions.INCLUDE_NAMESPACES:
				case CompilerOptions.INCLUDE_SOURCES:
				case CompilerOptions.INLINE_CONSTANTS:
				case CompilerOptions.JS_COMPILER_OPTION:
				case CompilerOptions.JS_COMPLEX_IMPLICIT_COERCIONS:
				case CompilerOptions.JS_DEFAULT_INITIALIZERS:
				case CompilerOptions.JS_DYNAMIC_ACCESS_UNKNOWN_MEMBERS:
				case CompilerOptions.JS_EXTERNAL_LIBRARY_PATH:
				case CompilerOptions.JS_LIBRARY_PATH:
				case CompilerOptions.JS_OUTPUT:
				case CompilerOptions.JS_OUTPUT_OPTIMIZATION:
				case CompilerOptions.JS_OUTPUT_TYPE:
				case CompilerOptions.JS_VECTOR_EMULATION_CLASS:
				case CompilerOptions.JS_VECTOR_INDEX_CHECKS:
				case CompilerOptions.KEEP_ALL_TYPE_SELECTORS:
				case CompilerOptions.KEEP_AS3_METADATA:
				case CompilerOptions.KEEP_GENERATED_ACTIONSCRIPT:
				case CompilerOptions.LINK_REPORT:
				case CompilerOptions.JS_LOAD_CONFIG:
				case CompilerOptions.LOAD_EXTERNS:
				case CompilerOptions.LOCALE:
				case CompilerOptions.NAMESPACE:
				case CompilerOptions.OPTIMIZE:
				case CompilerOptions.OMIT_TRACE_STATEMENTS:
				case CompilerOptions.OUTPUT:
				case CompilerOptions.PRELOADER:
				case CompilerOptions.PREVENT_RENAME_PUBLIC_SYMBOLS:
				case CompilerOptions.PREVENT_RENAME_PUBLIC_STATIC_METHODS:
				case CompilerOptions.PREVENT_RENAME_PUBLIC_INSTANCE_METHODS:
				case CompilerOptions.PREVENT_RENAME_PUBLIC_STATIC_VARIABLES:
				case CompilerOptions.PREVENT_RENAME_PUBLIC_INSTANCE_VARIABLES:
				case CompilerOptions.PREVENT_RENAME_PUBLIC_STATIC_ACCESSORS:
				case CompilerOptions.PREVENT_RENAME_PUBLIC_INSTANCE_ACCESSORS:
				case CompilerOptions.PREVENT_RENAME_PROTECTED_SYMBOLS:
				case CompilerOptions.PREVENT_RENAME_PROTECTED_STATIC_METHODS:
				case CompilerOptions.PREVENT_RENAME_PROTECTED_INSTANCE_METHODS:
				case CompilerOptions.PREVENT_RENAME_PROTECTED_STATIC_VARIABLES:
				case CompilerOptions.PREVENT_RENAME_PROTECTED_INSTANCE_VARIABLES:
				case CompilerOptions.PREVENT_RENAME_PROTECTED_STATIC_ACCESSORS:
				case CompilerOptions.PREVENT_RENAME_PROTECTED_INSTANCE_ACCESSORS:
				case CompilerOptions.PREVENT_RENAME_INTERNAL_SYMBOLS:
				case CompilerOptions.PREVENT_RENAME_INTERNAL_STATIC_METHODS:
				case CompilerOptions.PREVENT_RENAME_INTERNAL_INSTANCE_METHODS:
				case CompilerOptions.PREVENT_RENAME_INTERNAL_STATIC_VARIABLES:
				case CompilerOptions.PREVENT_RENAME_INTERNAL_INSTANCE_VARIABLES:
				case CompilerOptions.PREVENT_RENAME_INTERNAL_STATIC_ACCESSORS:
				case CompilerOptions.PREVENT_RENAME_INTERNAL_INSTANCE_ACCESSORS:
				case CompilerOptions.REMOVE_CIRCULARS:
				case CompilerOptions.SHOW_UNUSED_TYPE_SELECTOR_WARNINGS:
				case CompilerOptions.SIZE_REPORT:
				case CompilerOptions.SOURCE_MAP:
				case CompilerOptions.SOURCE_MAP_SOURCE_ROOT:
				case CompilerOptions.STATIC_LINK_RUNTIME_SHARED_LIBRARIES:
				case CompilerOptions.STRICT_IDENTIFIER_NAMES:
				case CompilerOptions.SWF_EXTERNAL_LIBRARY_PATH:
				case CompilerOptions.SWF_LIBRARY_PATH:
				case CompilerOptions.SWF_VERSION:
				case CompilerOptions.TARGET_PLAYER:
				case CompilerOptions.TARGETS:
				case CompilerOptions.THEME:
				case CompilerOptions.TOOLS_LOCALE:
				case CompilerOptions.USE_DIRECT_BLIT:
				case CompilerOptions.USE_GPU:
				case CompilerOptions.USE_NETWORK:
				case CompilerOptions.USE_RESOURCE_BUNDLE_METADATA:
				case CompilerOptions.VERBOSE_STACKTRACES:
				case CompilerOptions.WARNINGS:
				case CompilerOptions.WARN_PUBLIC_VARS:
					break;
				default: {
					throw new UnknownCompilerOptionException(key);
				}
			}
		}
	}

	public static void parseASDoc(JsonNode options, List<String> result) {
		if (options.has(ASDocOptions.DOC_SOURCES)) {
			for (String source : JsonUtils.jsonNodeToListOfStrings(options.get(ASDocOptions.DOC_SOURCES))) {
				result.add(0, "-doc-sources+=" + source);
			}
		}
		if (options.has(ASDocOptions.DOC_CLASSES)) {
			for (String source : JsonUtils.jsonNodeToListOfStrings(options.get(ASDocOptions.DOC_CLASSES))) {
				result.add(0, "-doc-classes+=" + source);
			}
		}
		if (options.has(ASDocOptions.DOC_NAMESPACES)) {
			for (String source : JsonUtils.jsonNodeToListOfStrings(options.get(ASDocOptions.DOC_NAMESPACES))) {
				result.add(0, "-doc-namespaces+=" + source);
			}
		}
	}

	private void appendNamespace(JsonNode values, List<String> result) {
		int size = values.size();
		if (size == 0) {
			return;
		}
		for (int i = 0; i < size; i++) {
			JsonNode currentValue = values.get(i);
			String uri = currentValue.get(CompilerOptions.NAMESPACE__URI).asText();
			String manifest = currentValue.get(CompilerOptions.NAMESPACE__MANIFEST).asText();
			result.add("--" + CompilerOptions.NAMESPACE + "+=" + uri + "," + manifest);
		}
	}

	private void appendJSCompilerOptions(String optionName, List<String> values, List<String> result) {
		int size = values.size();
		if (size == 0) {
			return;
		}
		for (int i = 0; i < size; i++) {
			String currentValue = values.get(i);
			result.add("--" + optionName + "+=\"" + currentValue.toString() + "\"");
		}
	}

	private void setDefaultSize(JsonNode sizePair, List<String> result) {
		int width = sizePair.get(CompilerOptions.DEFAULT_SIZE__WIDTH).asInt();
		int height = sizePair.get(CompilerOptions.DEFAULT_SIZE__HEIGHT).asInt();
		result.add("--" + CompilerOptions.DEFAULT_SIZE);
		result.add(Integer.toString(width));
		result.add(Integer.toString(height));
	}

	private void setDefine(String optionName, JsonNode values, List<String> result) {
		int size = values.size();
		if (size == 0) {
			return;
		}
		for (int i = 0; i < size; i++) {
			JsonNode currentValue = values.get(i);
			String defineName = currentValue.get(CompilerOptions.DEFINE__NAME).asText();
			JsonNode defineValue = currentValue.get(CompilerOptions.DEFINE__VALUE);
			String defineValueAsString = defineValue.asText();
			if (defineValue.isTextual()) {
				defineValueAsString = defineValueAsString.replace("\"", "\\\"");
				defineValueAsString = "\"" + defineValueAsString + "\"";
			}
			result.add("--" + optionName + "+=" + defineName + "," + defineValueAsString);
		}
	}

	private void parseIncludeFile(JsonNode files, List<String> result) {
		for (int i = 0, size = files.size(); i < size; i++) {
			JsonNode file = files.get(i);
			String src = null;
			String dest = null;
			if (file.isTextual()) {
				String filePath = file.asText();
				src = filePath;
				dest = filePath;
			} else {
				src = file.get(CompilerOptions.INCLUDE_FILE__FILE).asText();
				dest = file.get(CompilerOptions.INCLUDE_FILE__PATH).asText();
			}
			result.add("--" + CompilerOptions.INCLUDE_FILE + "+=" + dest + "," + src);
		}
	}
}
