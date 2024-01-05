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

import java.util.ArrayList;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OptionsFormatterTests {
	@Test
	void testSetValue() {
		String optionName = "optionName";
		String value = "value";
		ArrayList<String> result = new ArrayList<>();
		OptionsFormatter.setValue(optionName, value, result);
		Assertions.assertEquals(1, result.size(), "OptionsFormatter.setValue() created incorrect number of options.");
		Assertions.assertEquals("--optionName=value", result.get(0),
				"OptionsFormatter.setValue() incorrectly formatted option.");
	}

	@Test
	void testSetBoolean() {
		String optionName = "optionName";
		boolean value = true;
		ArrayList<String> result = new ArrayList<>();
		OptionsFormatter.setBoolean(optionName, value, result);
		Assertions.assertEquals(1, result.size(), "OptionsFormatter.setBoolean() created incorrect number of options.");
		Assertions.assertEquals("--optionName=true", result.get(0),
				"OptionsFormatter.setBoolean() incorrectly formatted option.");
	}

	@Test
	void testSetPathValueWithoutSpaces() {
		String optionName = "optionName";
		String value = "path/to/file.txt";
		ArrayList<String> result = new ArrayList<>();
		OptionsFormatter.setPathValue(optionName, value, result);
		Assertions.assertEquals(1, result.size(),
				"OptionsFormatter.setPathValue() created incorrect number of options.");
		Assertions.assertEquals("--optionName=" + value, result.get(0),
				"OptionsFormatter.setPathValue() incorrectly formatted option.");
	}

	@Test
	void testSetValues() {
		String optionName = "optionName";
		ArrayList<String> values = new ArrayList<>();
		values.add("one");
		values.add("two");
		values.add("three");
		ArrayList<String> result = new ArrayList<>();
		OptionsFormatter.setValues(optionName, values, result);
		Assertions.assertEquals(3, result.size(), "OptionsFormatter.setValues() created incorrect number of options.");
		Assertions.assertEquals("--optionName=one", result.get(0),
				"OptionsFormatter.setValues() incorrectly formatted option.");
		Assertions.assertEquals("--optionName+=two", result.get(1),
				"OptionsFormatter.setValues() incorrectly formatted option.");
		Assertions.assertEquals("--optionName+=three", result.get(2),
				"OptionsFormatter.setValues() incorrectly formatted option.");
	}

	@Test
	void testAppendValues() {
		String optionName = "optionName";
		ArrayList<String> values = new ArrayList<>();
		values.add("one");
		values.add("two");
		values.add("three");
		ArrayList<String> result = new ArrayList<>();
		OptionsFormatter.appendValues(optionName, values, result);
		Assertions.assertEquals(3, result.size(),
				"OptionsFormatter.appendValues() created incorrect number of options.");
		Assertions.assertEquals("--optionName+=one", result.get(0),
				"OptionsFormatter.appendValues() incorrectly formatted option.");
		Assertions.assertEquals("--optionName+=two", result.get(1),
				"OptionsFormatter.appendValues() incorrectly formatted option.");
		Assertions.assertEquals("--optionName+=three", result.get(2),
				"OptionsFormatter.appendValues() incorrectly formatted option.");
	}

	@Test
	void testSetValuesWithCommas() {
		String optionName = "optionName";
		ArrayList<String> values = new ArrayList<>();
		values.add("one");
		values.add("two");
		values.add("three");
		ArrayList<String> result = new ArrayList<>();
		OptionsFormatter.setValuesWithCommas(optionName, values, result);
		Assertions.assertEquals(1, result.size(),
				"OptionsFormatter.setValuesWithCommas() created incorrect number of options.");
		Assertions.assertEquals("--optionName=one,two,three", result.get(0),
				"OptionsFormatter.setValuesWithCommas() incorrectly formatted option.");
	}

	@Test
	void testAppendPaths() {
		String optionName = "optionName";
		String value1 = "path/to/file1.txt";
		String value2 = "path/to/file2 with spaces.txt";
		String value3 = "./path/to/file3.txt";
		ArrayList<String> values = new ArrayList<>();
		values.add(value1);
		values.add(value2);
		values.add(value3);
		ArrayList<String> result = new ArrayList<>();
		OptionsFormatter.appendPaths(optionName, values, result);
		Assertions.assertEquals(3, result.size(),
				"OptionsFormatter.testAppendPaths() created incorrect number of options.");
		Assertions.assertEquals("--optionName+=" + value1, result.get(0),
				"OptionsFormatter.testAppendPaths() incorrectly formatted option.");
		Assertions.assertEquals("--optionName+=" + value2, result.get(1),
				"OptionsFormatter.testAppendPaths() incorrectly formatted option.");
		Assertions.assertEquals("--optionName+=" + value3, result.get(2),
				"OptionsFormatter.testAppendPaths() incorrectly formatted option.");
	}
}