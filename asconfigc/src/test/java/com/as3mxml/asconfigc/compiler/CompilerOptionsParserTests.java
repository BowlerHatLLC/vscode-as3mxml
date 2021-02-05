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

import java.util.ArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.as3mxml.asconfigc.compiler.CompilerOptionsParser.UnknownCompilerOptionException;

class CompilerOptionsParserTests {
	private CompilerOptionsParser parser;

	@BeforeEach
	void setup() {
		parser = new CompilerOptionsParser();
	}

	@AfterEach
	void tearDown() {
		parser = null;
	}

	@Test
	void testAccessible() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.ACCESSIBLE, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.ACCESSIBLE + "=" + Boolean.toString(value), result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testAdvancedTelemetry() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.ADVANCED_TELEMETRY, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.ADVANCED_TELEMETRY + "=" + Boolean.toString(value),
				result.get(0), "CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testBenchmark() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.BENCHMARK, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.BENCHMARK + "=" + Boolean.toString(value), result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testDebug() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.DEBUG, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.DEBUG + "=" + value, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testDebugWithTrueOverride() {
		boolean value = false;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.DEBUG, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, true, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(0, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
	}

	@Test
	void testDebugWithFalseOverride() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.DEBUG, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, false, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(0, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
	}

	@Test
	void testDebugPassword() {
		String value = "12345";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.DEBUG_PASSWORD, JsonNodeFactory.instance.textNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.DEBUG_PASSWORD + "=" + value, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testDefaultFrameRate() {
		int value = 60;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.DEFAULT_FRAME_RATE, JsonNodeFactory.instance.numberNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.DEFAULT_FRAME_RATE + "=" + value, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testDefaultSize() {
		int width = 828;
		int height = 367;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode defaultSize = JsonNodeFactory.instance.objectNode();
		defaultSize.set(CompilerOptions.DEFAULT_SIZE__WIDTH, JsonNodeFactory.instance.numberNode(width));
		defaultSize.set(CompilerOptions.DEFAULT_SIZE__HEIGHT, JsonNodeFactory.instance.numberNode(height));
		options.set(CompilerOptions.DEFAULT_SIZE, defaultSize);
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(3, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.DEFAULT_SIZE, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals(Integer.toString(width), result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals(Integer.toString(height), result.get(2),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testDefaultsCssFiles() {
		String value1 = "defaults/css/path1";
		String value2 = "defaults/css/path2 with spaces";
		String value3 = "./defaults/css/path3";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode externalLibraryPath = JsonNodeFactory.instance.arrayNode();

		externalLibraryPath.add(JsonNodeFactory.instance.textNode(value1));
		externalLibraryPath.add(JsonNodeFactory.instance.textNode(value2));
		externalLibraryPath.add(JsonNodeFactory.instance.textNode(value3));

		options.set(CompilerOptions.DEFAULTS_CSS_FILES, externalLibraryPath);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(3, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.DEFAULTS_CSS_FILES + "+=" + value1, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.DEFAULTS_CSS_FILES + "+=" + value2, result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.DEFAULTS_CSS_FILES + "+=" + value3, result.get(2),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testDefine() {
		String name1 = "CONFIG::bool";
		boolean value1 = true;
		String name2 = "CONFIG::str";
		String value2 = "'test'";
		String name3 = "CONFIG::str2";
		String value3 = "\"test\"";
		String name4 = "CONFIG::num";
		double value4 = 12.3;
		String name5 = "CONFIG::expr";
		String value5 = "2 + 4";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode define = JsonNodeFactory.instance.arrayNode();

		ObjectNode define1 = JsonNodeFactory.instance.objectNode();
		define1.set(CompilerOptions.DEFINE__NAME, JsonNodeFactory.instance.textNode(name1));
		define1.set(CompilerOptions.DEFINE__VALUE, JsonNodeFactory.instance.booleanNode(value1));
		define.add(define1);

		ObjectNode define2 = JsonNodeFactory.instance.objectNode();
		define2.set(CompilerOptions.DEFINE__NAME, JsonNodeFactory.instance.textNode(name2));
		define2.set(CompilerOptions.DEFINE__VALUE, JsonNodeFactory.instance.textNode(value2));
		define.add(define2);

		ObjectNode define3 = JsonNodeFactory.instance.objectNode();
		define3.set(CompilerOptions.DEFINE__NAME, JsonNodeFactory.instance.textNode(name3));
		define3.set(CompilerOptions.DEFINE__VALUE, JsonNodeFactory.instance.textNode(value3));
		define.add(define3);

		ObjectNode define4 = JsonNodeFactory.instance.objectNode();
		define4.set(CompilerOptions.DEFINE__NAME, JsonNodeFactory.instance.textNode(name4));
		define4.set(CompilerOptions.DEFINE__VALUE, JsonNodeFactory.instance.numberNode(value4));
		define.add(define4);

		ObjectNode define5 = JsonNodeFactory.instance.objectNode();
		define5.set(CompilerOptions.DEFINE__NAME, JsonNodeFactory.instance.textNode(name5));
		define5.set(CompilerOptions.DEFINE__VALUE, JsonNodeFactory.instance.textNode(value5));
		define.add(define5);

		options.set(CompilerOptions.DEFINE, define);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(5, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.DEFINE + "+=" + name1 + "," + Boolean.toString(value1),
				result.get(0), "CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.DEFINE + "+=" + name2 + ",\"" + value2 + "\"", result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.DEFINE + "+=" + name3 + ",\"\\\"test\\\"\"", result.get(2),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.DEFINE + "+=" + name4 + "," + Double.toString(value4),
				result.get(3), "CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.DEFINE + "+=" + name5 + ",\"" + value5 + "\"", result.get(4),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testJSDefine() {
		String name1 = "CONFIG::bool";
		boolean value1 = true;
		String name2 = "CONFIG::str";
		String value2 = "'test'";
		String name3 = "CONFIG::str2";
		String value3 = "\"test\"";
		String name4 = "CONFIG::num";
		double value4 = 12.3;
		String name5 = "CONFIG::expr";
		String value5 = "2 + 4";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode define = JsonNodeFactory.instance.arrayNode();

		ObjectNode define1 = JsonNodeFactory.instance.objectNode();
		define1.set(CompilerOptions.DEFINE__NAME, JsonNodeFactory.instance.textNode(name1));
		define1.set(CompilerOptions.DEFINE__VALUE, JsonNodeFactory.instance.booleanNode(value1));
		define.add(define1);

		ObjectNode define2 = JsonNodeFactory.instance.objectNode();
		define2.set(CompilerOptions.DEFINE__NAME, JsonNodeFactory.instance.textNode(name2));
		define2.set(CompilerOptions.DEFINE__VALUE, JsonNodeFactory.instance.textNode(value2));
		define.add(define2);

		ObjectNode define3 = JsonNodeFactory.instance.objectNode();
		define3.set(CompilerOptions.DEFINE__NAME, JsonNodeFactory.instance.textNode(name3));
		define3.set(CompilerOptions.DEFINE__VALUE, JsonNodeFactory.instance.textNode(value3));
		define.add(define3);

		ObjectNode define4 = JsonNodeFactory.instance.objectNode();
		define4.set(CompilerOptions.DEFINE__NAME, JsonNodeFactory.instance.textNode(name4));
		define4.set(CompilerOptions.DEFINE__VALUE, JsonNodeFactory.instance.numberNode(value4));
		define.add(define4);

		ObjectNode define5 = JsonNodeFactory.instance.objectNode();
		define5.set(CompilerOptions.DEFINE__NAME, JsonNodeFactory.instance.textNode(name5));
		define5.set(CompilerOptions.DEFINE__VALUE, JsonNodeFactory.instance.textNode(value5));
		define.add(define5);

		options.set(CompilerOptions.JS_DEFINE, define);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(5, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.JS_DEFINE + "+=" + name1 + "," + Boolean.toString(value1),
				result.get(0), "CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.JS_DEFINE + "+=" + name2 + ",\"" + value2 + "\"", result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.JS_DEFINE + "+=" + name3 + ",\"\\\"test\\\"\"", result.get(2),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.JS_DEFINE + "+=" + name4 + "," + Double.toString(value4),
				result.get(3), "CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.JS_DEFINE + "+=" + name5 + ",\"" + value5 + "\"", result.get(4),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testDirectory() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.DIRECTORY, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.DIRECTORY + "=" + Boolean.toString(value), result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testDumpConfig() {
		String value = "path/to/file.xml";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.DUMP_CONFIG, JsonNodeFactory.instance.textNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.DUMP_CONFIG + "=" + value, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testExternalLibraryPath() {
		String value1 = "external/library/path1";
		String value2 = "external/library/path2 with spaces";
		String value3 = "./external/library/path3";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode externalLibraryPath = JsonNodeFactory.instance.arrayNode();

		externalLibraryPath.add(JsonNodeFactory.instance.textNode(value1));
		externalLibraryPath.add(JsonNodeFactory.instance.textNode(value2));
		externalLibraryPath.add(JsonNodeFactory.instance.textNode(value3));

		options.set(CompilerOptions.EXTERNAL_LIBRARY_PATH, externalLibraryPath);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(3, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.EXTERNAL_LIBRARY_PATH + "+=" + value1, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.EXTERNAL_LIBRARY_PATH + "+=" + value2, result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.EXTERNAL_LIBRARY_PATH + "+=" + value3, result.get(2),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testHTMLOutputFilename() {
		String value = "html-output.html";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.HTML_OUTPUT_FILENAME, JsonNodeFactory.instance.textNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.HTML_OUTPUT_FILENAME + "=" + value, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testHTMLTemplate() {
		String value = "path/to/html-template.html";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.HTML_TEMPLATE, JsonNodeFactory.instance.textNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.HTML_TEMPLATE + "=" + value, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testIncludeClasses() {
		String value1 = "com.example.SomeClass";
		String value2 = "AnotherClass";
		String value3 = "org.example.one.two.three.HelloWorld";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode includeClasses = JsonNodeFactory.instance.arrayNode();

		includeClasses.add(JsonNodeFactory.instance.textNode(value1));
		includeClasses.add(JsonNodeFactory.instance.textNode(value2));
		includeClasses.add(JsonNodeFactory.instance.textNode(value3));

		options.set(CompilerOptions.INCLUDE_CLASSES, includeClasses);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.INCLUDE_CLASSES + "=" + value1 + "," + value2 + "," + value3,
				result.get(0), "CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testIncludeFile() {
		String src = "file.txt";
		String dest = "assets/file.txt";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode includeFile = JsonNodeFactory.instance.arrayNode();

		ObjectNode value = JsonNodeFactory.instance.objectNode();
		value.set(CompilerOptions.INCLUDE_FILE__FILE, JsonNodeFactory.instance.textNode(src));
		value.set(CompilerOptions.INCLUDE_FILE__PATH, JsonNodeFactory.instance.textNode(dest));
		includeFile.add(value);

		options.set(CompilerOptions.INCLUDE_FILE, includeFile);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.INCLUDE_FILE + "+=" + dest + "," + src, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testIncludeLibraries() {
		String value1 = "library/path1.swc";
		String value2 = "library/path2 with spaces.swc";
		String value3 = "./library/path3.swc";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode includeLibraries = JsonNodeFactory.instance.arrayNode();

		includeLibraries.add(JsonNodeFactory.instance.textNode(value1));
		includeLibraries.add(JsonNodeFactory.instance.textNode(value2));
		includeLibraries.add(JsonNodeFactory.instance.textNode(value3));

		options.set(CompilerOptions.INCLUDE_LIBRARIES, includeLibraries);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(3, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.INCLUDE_LIBRARIES + "+=" + value1, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.INCLUDE_LIBRARIES + "+=" + value2, result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.INCLUDE_LIBRARIES + "+=" + value3, result.get(2),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testIncludeNamespaces() {
		String value1 = "http://ns.example.com";
		String value2 = "library://example.com/library";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode includeNamespaces = JsonNodeFactory.instance.arrayNode();

		includeNamespaces.add(JsonNodeFactory.instance.textNode(value1));
		includeNamespaces.add(JsonNodeFactory.instance.textNode(value2));

		options.set(CompilerOptions.INCLUDE_NAMESPACES, includeNamespaces);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(2, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.INCLUDE_NAMESPACES + "+=" + value1, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.INCLUDE_NAMESPACES + "+=" + value2, result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testIncludeSources() {
		String value1 = "/absolute/path";
		String value2 = "./relative/path";
		String value3 = "src";
		String value4 = "path/with spaces";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode includeSources = JsonNodeFactory.instance.arrayNode();

		includeSources.add(JsonNodeFactory.instance.textNode(value1));
		includeSources.add(JsonNodeFactory.instance.textNode(value2));
		includeSources.add(JsonNodeFactory.instance.textNode(value3));
		includeSources.add(JsonNodeFactory.instance.textNode(value4));

		options.set(CompilerOptions.INCLUDE_SOURCES, includeSources);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(4, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.INCLUDE_SOURCES + "+=" + value1, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.INCLUDE_SOURCES + "+=" + value2, result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.INCLUDE_SOURCES + "+=" + value3, result.get(2),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.INCLUDE_SOURCES + "+=" + value4, result.get(3),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testJSCompilationOption() {
		String value1 = "--compilation_level WHITESPACE_ONLY";
		String value2 = "--formatting pretty_print";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode jsComplilationOptions = JsonNodeFactory.instance.arrayNode();

		jsComplilationOptions.add(JsonNodeFactory.instance.textNode(value1));
		jsComplilationOptions.add(JsonNodeFactory.instance.textNode(value2));

		options.set(CompilerOptions.JS_COMPILER_OPTION, jsComplilationOptions);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(2, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.JS_COMPILER_OPTION + "+=\"" + value1 + "\"", result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.JS_COMPILER_OPTION + "+=\"" + value2 + "\"", result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testJSExternalLibraryPath() {
		String value1 = "js/external/library/path1";
		String value2 = "js/external/library/path2 with spaces";
		String value3 = "./js/external/library/path3";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode externalLibraryPath = JsonNodeFactory.instance.arrayNode();

		externalLibraryPath.add(JsonNodeFactory.instance.textNode(value1));
		externalLibraryPath.add(JsonNodeFactory.instance.textNode(value2));
		externalLibraryPath.add(JsonNodeFactory.instance.textNode(value3));

		options.set(CompilerOptions.JS_EXTERNAL_LIBRARY_PATH, externalLibraryPath);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(3, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.JS_EXTERNAL_LIBRARY_PATH + "+=" + value1, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.JS_EXTERNAL_LIBRARY_PATH + "+=" + value2, result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.JS_EXTERNAL_LIBRARY_PATH + "+=" + value3, result.get(2),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testJSLibraryPath() {
		String value1 = "js/library/path1";
		String value2 = "js/library/path2 with spaces";
		String value3 = "./js/library/path3";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode libraryPath = JsonNodeFactory.instance.arrayNode();

		libraryPath.add(JsonNodeFactory.instance.textNode(value1));
		libraryPath.add(JsonNodeFactory.instance.textNode(value2));
		libraryPath.add(JsonNodeFactory.instance.textNode(value3));

		options.set(CompilerOptions.JS_LIBRARY_PATH, libraryPath);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(3, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.JS_LIBRARY_PATH + "+=" + value1, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.JS_LIBRARY_PATH + "+=" + value2, result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.JS_LIBRARY_PATH + "+=" + value3, result.get(2),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testJSDefaultInitializers() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.JS_DEFAULT_INITIALIZERS, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.JS_DEFAULT_INITIALIZERS + "=" + Boolean.toString(value),
				result.get(0), "CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testJSOutput() {
		String value = "path/to/output";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.JS_OUTPUT, JsonNodeFactory.instance.textNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.JS_OUTPUT + "=" + value, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testJSOutputType() {
		String value = "node";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.JS_OUTPUT_TYPE, JsonNodeFactory.instance.textNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.JS_OUTPUT_TYPE + "=" + value, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testKeepAllTypeSelectors() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.KEEP_ALL_TYPE_SELECTORS, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.KEEP_ALL_TYPE_SELECTORS + "=" + Boolean.toString(value),
				result.get(0), "CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testKeepAS3Metadata() {
		String value1 = "Inject";
		String value2 = "Test";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode keepMetadata = JsonNodeFactory.instance.arrayNode();

		keepMetadata.add(JsonNodeFactory.instance.textNode(value1));
		keepMetadata.add(JsonNodeFactory.instance.textNode(value2));

		options.set(CompilerOptions.KEEP_AS3_METADATA, keepMetadata);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(2, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.KEEP_AS3_METADATA + "+=" + value1, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.KEEP_AS3_METADATA + "+=" + value2, result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testKeepGeneratedActionScript() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.KEEP_GENERATED_ACTIONSCRIPT, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.KEEP_GENERATED_ACTIONSCRIPT + "=" + Boolean.toString(value),
				result.get(0), "CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testLibraryPath() {
		String value1 = "library/path1";
		String value2 = "library/path2 with spaces";
		String value3 = "./library/path3";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode libraryPath = JsonNodeFactory.instance.arrayNode();

		libraryPath.add(JsonNodeFactory.instance.textNode(value1));
		libraryPath.add(JsonNodeFactory.instance.textNode(value2));
		libraryPath.add(JsonNodeFactory.instance.textNode(value3));

		options.set(CompilerOptions.LIBRARY_PATH, libraryPath);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(3, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.LIBRARY_PATH + "+=" + value1, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.LIBRARY_PATH + "+=" + value2, result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.LIBRARY_PATH + "+=" + value3, result.get(2),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testLinkReport() {
		String value = "path/to/file.xml";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.LINK_REPORT, JsonNodeFactory.instance.textNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.LINK_REPORT + "=" + value, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testLoadConfig() {
		String value1 = "path/to/config.xml";
		String value2 = "path/with spaces/to/config.xml";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode loadConfig = JsonNodeFactory.instance.arrayNode();

		loadConfig.add(JsonNodeFactory.instance.textNode(value1));
		loadConfig.add(JsonNodeFactory.instance.textNode(value2));

		options.set(CompilerOptions.LOAD_CONFIG, loadConfig);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(2, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.LOAD_CONFIG + "+=" + value1, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.LOAD_CONFIG + "+=" + value2, result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testLoadExterns() {
		String value1 = "path/to/externs.xml";
		String value2 = "path/with spaces/to/externs.xml";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode loadExterns = JsonNodeFactory.instance.arrayNode();

		loadExterns.add(JsonNodeFactory.instance.textNode(value1));
		loadExterns.add(JsonNodeFactory.instance.textNode(value2));

		options.set(CompilerOptions.LOAD_EXTERNS, loadExterns);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(2, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.LOAD_EXTERNS + "+=" + value1, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.LOAD_EXTERNS + "+=" + value2, result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testJSLoadConfig() {
		String value1 = "path/to/config.xml";
		String value2 = "path/with spaces/to/config.xml";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode loadConfig = JsonNodeFactory.instance.arrayNode();

		loadConfig.add(JsonNodeFactory.instance.textNode(value1));
		loadConfig.add(JsonNodeFactory.instance.textNode(value2));

		options.set(CompilerOptions.JS_LOAD_CONFIG, loadConfig);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(2, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.JS_LOAD_CONFIG + "+=" + value1, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.JS_LOAD_CONFIG + "+=" + value2, result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testLocale() {
		String value1 = "en_US";
		String value2 = "fr_FR";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode locale = JsonNodeFactory.instance.arrayNode();

		locale.add(JsonNodeFactory.instance.textNode(value1));
		locale.add(JsonNodeFactory.instance.textNode(value2));

		options.set(CompilerOptions.LOCALE, locale);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(2, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.LOCALE + "=" + value1, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.LOCALE + "+=" + value2, result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testNamespace() {
		String uri1 = "http://ns.example.com";
		String manifest1 = "path/to/manifest.xml";
		String uri2 = "library://example.com/library";
		String manifest2 = "path/with spaces/to/manifest.xml";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode namespace = JsonNodeFactory.instance.arrayNode();

		ObjectNode ns1 = JsonNodeFactory.instance.objectNode();
		ns1.set(CompilerOptions.NAMESPACE__URI, JsonNodeFactory.instance.textNode(uri1));
		ns1.set(CompilerOptions.NAMESPACE__MANIFEST, JsonNodeFactory.instance.textNode(manifest1));
		namespace.add(ns1);

		ObjectNode ns2 = JsonNodeFactory.instance.objectNode();
		ns2.set(CompilerOptions.NAMESPACE__URI, JsonNodeFactory.instance.textNode(uri2));
		ns2.set(CompilerOptions.NAMESPACE__MANIFEST, JsonNodeFactory.instance.textNode(manifest2));
		namespace.add(ns2);

		options.set(CompilerOptions.NAMESPACE, namespace);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(2, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.NAMESPACE + "+=" + uri1 + "," + manifest1, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.NAMESPACE + "+=" + uri2 + "," + manifest2, result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testOmitTraceStatements() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.OMIT_TRACE_STATEMENTS, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.OMIT_TRACE_STATEMENTS + "=" + Boolean.toString(value),
				result.get(0), "CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testOptimize() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.OPTIMIZE, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.OPTIMIZE + "=" + Boolean.toString(value), result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testOutput() {
		String value = "path/to/output.swf";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.OUTPUT, JsonNodeFactory.instance.textNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.OUTPUT + "=" + value, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testPreloader() {
		String value = "mx.preloaders.SparkDownloadProgressBar";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.PRELOADER, JsonNodeFactory.instance.textNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.PRELOADER + "=" + value, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testRemoveCirculars() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.REMOVE_CIRCULARS, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.REMOVE_CIRCULARS + "=" + Boolean.toString(value), result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testShowUnusedTypeSelectorWarningss() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.SHOW_UNUSED_TYPE_SELECTOR_WARNINGS, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals(
				"--" + CompilerOptions.SHOW_UNUSED_TYPE_SELECTOR_WARNINGS + "=" + Boolean.toString(value),
				result.get(0), "CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testSizeReport() {
		String value = "path/to/file.xml";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.SIZE_REPORT, JsonNodeFactory.instance.textNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.SIZE_REPORT + "=" + value, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testSourceMap() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.SOURCE_MAP, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.SOURCE_MAP + "=" + Boolean.toString(value), result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testSourcePath() {
		String value1 = "source/path1";
		String value2 = "source/path2 with spaces";
		String value3 = "./source/path3";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode sourcePath = JsonNodeFactory.instance.arrayNode();

		sourcePath.add(JsonNodeFactory.instance.textNode(value1));
		sourcePath.add(JsonNodeFactory.instance.textNode(value2));
		sourcePath.add(JsonNodeFactory.instance.textNode(value3));

		options.set(CompilerOptions.SOURCE_PATH, sourcePath);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(3, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.SOURCE_PATH + "+=" + value1, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.SOURCE_PATH + "+=" + value2, result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.SOURCE_PATH + "+=" + value3, result.get(2),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testStaticLinkRuntimeSharedLibraries() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.STATIC_LINK_RUNTIME_SHARED_LIBRARIES, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals(
				"--" + CompilerOptions.STATIC_LINK_RUNTIME_SHARED_LIBRARIES + "=" + Boolean.toString(value),
				result.get(0), "CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testStrict() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.STRICT, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.STRICT + "=" + Boolean.toString(value), result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testSWFExternalLibraryPath() {
		String value1 = "swf/external/library/path1";
		String value2 = "swf/external/library/path2 with spaces";
		String value3 = "./swf/external/library/path3";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode externalLibraryPath = JsonNodeFactory.instance.arrayNode();

		externalLibraryPath.add(JsonNodeFactory.instance.textNode(value1));
		externalLibraryPath.add(JsonNodeFactory.instance.textNode(value2));
		externalLibraryPath.add(JsonNodeFactory.instance.textNode(value3));

		options.set(CompilerOptions.SWF_EXTERNAL_LIBRARY_PATH, externalLibraryPath);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(3, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.SWF_EXTERNAL_LIBRARY_PATH + "+=" + value1, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.SWF_EXTERNAL_LIBRARY_PATH + "+=" + value2, result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.SWF_EXTERNAL_LIBRARY_PATH + "+=" + value3, result.get(2),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testSWFLibraryPath() {
		String value1 = "swf/library/path1";
		String value2 = "swf/library/path2 with spaces";
		String value3 = "./swf/library/path3";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode libraryPath = JsonNodeFactory.instance.arrayNode();

		libraryPath.add(JsonNodeFactory.instance.textNode(value1));
		libraryPath.add(JsonNodeFactory.instance.textNode(value2));
		libraryPath.add(JsonNodeFactory.instance.textNode(value3));

		options.set(CompilerOptions.SWF_LIBRARY_PATH, libraryPath);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(3, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.SWF_LIBRARY_PATH + "+=" + value1, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.SWF_LIBRARY_PATH + "+=" + value2, result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.SWF_LIBRARY_PATH + "+=" + value3, result.get(2),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testSWFVersion() {
		int value = 30;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.SWF_VERSION, JsonNodeFactory.instance.numberNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.SWF_VERSION + "=" + value, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testTargetPlayer() {
		String value = "22.0";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.TARGET_PLAYER, JsonNodeFactory.instance.textNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.TARGET_PLAYER + "=" + value, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testTargetsWithOneValue() {
		String value1 = "JS";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode targets = JsonNodeFactory.instance.arrayNode();

		targets.add(JsonNodeFactory.instance.textNode(value1));

		options.set(CompilerOptions.TARGETS, targets);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.TARGETS + "=" + value1, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testTargetsWithTwoValues() {
		String value1 = "JS";
		String value2 = "SWF";

		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode targets = JsonNodeFactory.instance.arrayNode();

		targets.add(JsonNodeFactory.instance.textNode(value1));
		targets.add(JsonNodeFactory.instance.textNode(value2));

		options.set(CompilerOptions.TARGETS, targets);

		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.TARGETS + "=" + value1 + "," + value2, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testTheme() {
		String value = "path/to/theme.swc";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.THEME, JsonNodeFactory.instance.textNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.THEME + "=" + value, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testThemeMultiple() {
		String value1 = "path/to/theme.swc";
		String value2 = "another_theme.swc";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode theme = JsonNodeFactory.instance.arrayNode();

		theme.add(JsonNodeFactory.instance.textNode(value1));
		theme.add(JsonNodeFactory.instance.textNode(value2));

		options.set(CompilerOptions.THEME, theme);
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(2, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.THEME + "=" + value1, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
		Assertions.assertEquals("--" + CompilerOptions.THEME + "+=" + value2, result.get(1),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testToolsLocale() {
		String value = "fr_FR";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.TOOLS_LOCALE, JsonNodeFactory.instance.textNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.TOOLS_LOCALE + "=" + value, result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testUseDirectBlit() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.USE_DIRECT_BLIT, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.USE_DIRECT_BLIT + "=" + Boolean.toString(value), result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testUseGPU() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.USE_GPU, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.USE_GPU + "=" + Boolean.toString(value), result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testUseNetwork() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.USE_NETWORK, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.USE_NETWORK + "=" + Boolean.toString(value), result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testUseResourceBundleMetadata() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.USE_RESOURCE_BUNDLE_METADATA, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.USE_RESOURCE_BUNDLE_METADATA + "=" + Boolean.toString(value),
				result.get(0), "CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testVerboseStackTraces() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.VERBOSE_STACKTRACES, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.VERBOSE_STACKTRACES + "=" + Boolean.toString(value),
				result.get(0), "CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testWarnings() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.WARNINGS, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.WARNINGS + "=" + Boolean.toString(value), result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}

	@Test
	void testWarnPublicVars() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(CompilerOptions.WARN_PUBLIC_VARS, JsonNodeFactory.instance.booleanNode(value));
		ArrayList<String> result = new ArrayList<>();
		try {
			parser.parse(options, null, result);
		} catch (UnknownCompilerOptionException e) {
		}
		Assertions.assertEquals(1, result.size(), "CompilerOptionsParser.parse() created incorrect number of options.");
		Assertions.assertEquals("--" + CompilerOptions.WARN_PUBLIC_VARS + "=" + Boolean.toString(value), result.get(0),
				"CompilerOptionsParser.parse() incorrectly formatted compiler option.");
	}
}