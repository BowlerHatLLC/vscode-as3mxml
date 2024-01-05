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

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class OptionsUtilsTests {
	@Test
	void testBasicOption() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-optimize");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-optimize", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testDoubleHyphenOption() {
		List<String> options = OptionsUtils.parseAdditionalOptions("--optimize");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("--optimize", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testMultiWordOption() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-multi-word-option");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-multi-word-option", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testDoubleHyphenMultiWordOption() {
		List<String> options = OptionsUtils.parseAdditionalOptions("--multi-word-option");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("--multi-word-option", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithAssignment() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-optimize=true");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-optimize=true", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testDoubleHyphenOptionWithAssignment() {
		List<String> options = OptionsUtils.parseAdditionalOptions("--optimize=true");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("--optimize=true", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testMultiWordOptionWithAssignment() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-multi-word-option=true");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-multi-word-option=true", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testDoubleHyphenMultiWordOptionWithAssignment() {
		List<String> options = OptionsUtils.parseAdditionalOptions("--multi-word-option=true");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("--multi-word-option=true", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithPlusAssignment() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-option+=true");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-option+=true", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testDoubleHyphenOptionWithPlusAssignment() {
		List<String> options = OptionsUtils.parseAdditionalOptions("--option+=true");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("--option+=true", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testMultiWordOptionWithPlusAssignment() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-multi-word-option+=true");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-multi-word-option+=true", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testDoubleHyphenMultiWordOptionWithPlusAssignment() {
		List<String> options = OptionsUtils.parseAdditionalOptions("--multi-word-option+=true");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("--multi-word-option+=true", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithSeparateValue() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-optimize true");
		Assertions.assertEquals(2, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-optimize", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
		Assertions.assertEquals("true", options.get(1),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testDoubleHyphenOptionWithSeparateValue() {
		List<String> options = OptionsUtils.parseAdditionalOptions("--optimize true");
		Assertions.assertEquals(2, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("--optimize", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
		Assertions.assertEquals("true", options.get(1),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testMultiWordOptionWithSeparateValue() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-multi-word-option true");
		Assertions.assertEquals(2, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-multi-word-option", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
		Assertions.assertEquals("true", options.get(1),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testDoubleHyphenMultiWordOptionWithSeparateValue() {
		List<String> options = OptionsUtils.parseAdditionalOptions("--multi-word-option true");
		Assertions.assertEquals(2, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("--multi-word-option", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
		Assertions.assertEquals("true", options.get(1),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithSeparateStringValueInDoubleQuotesWithoutSpaces() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-source-path \"path/to/src\"");
		Assertions.assertEquals(2, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-source-path", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
		Assertions.assertEquals("\"path/to/src\"", options.get(1),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithSeparateStringValueInSingleQuotesWithoutSpaces() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-source-path 'path/to/src'");
		Assertions.assertEquals(2, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-source-path", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
		Assertions.assertEquals("'path/to/src'", options.get(1),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithSeparateStringValueWithNestedDoubleQuotesInsideSingleQuotesWithoutSpaces() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-define CONFIG::TEST '\"path/to/src\"'");
		Assertions.assertEquals(3, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-define", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
		Assertions.assertEquals("CONFIG::TEST", options.get(1),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
		Assertions.assertEquals("'\"path/to/src\"'", options.get(2),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithSeparateStringValueWithNestedSingleQuotesInsideDoubleQuotesWithoutSpaces() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-define CONFIG::TEST \"'path/to/src'\"");
		Assertions.assertEquals(3, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-define", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
		Assertions.assertEquals("CONFIG::TEST", options.get(1),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
		Assertions.assertEquals("\"'path/to/src'\"", options.get(2),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithSeparateStringValueInDoubleQuotesWithSpaces() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-source-path \"path/with spaces/to/src\"");
		Assertions.assertEquals(2, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-source-path", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
		Assertions.assertEquals("\"path/with spaces/to/src\"", options.get(1),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithSeparateStringValueInSingleQuotesWithSpaces() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-source-path 'path/with spaces/to/src'");
		Assertions.assertEquals(2, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-source-path", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
		Assertions.assertEquals("'path/with spaces/to/src'", options.get(1),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithSeparateStringValueWithNestedDoubleQuotesInsideSingleQuotesWithSpaces() {
		List<String> options = OptionsUtils
				.parseAdditionalOptions("-define CONFIG::TEST '\"path/with spaces/to/src\"'");
		Assertions.assertEquals(3, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-define", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
		Assertions.assertEquals("CONFIG::TEST", options.get(1),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
		Assertions.assertEquals("'\"path/with spaces/to/src\"'", options.get(2),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithSeparateStringValueWithNestedSingleQuotesInsideDoubleQuotesWithSpaces() {
		List<String> options = OptionsUtils
				.parseAdditionalOptions("-define CONFIG::TEST \"'path/with spaces/to/src'\"");
		Assertions.assertEquals(3, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-define", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
		Assertions.assertEquals("CONFIG::TEST", options.get(1),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
		Assertions.assertEquals("\"'path/with spaces/to/src'\"", options.get(2),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithAssignedStringValueInDoubleQuotesWithoutSpaces() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-source-path=\"path/to/src\"");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-source-path=\"path/to/src\"", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithAssignedStringValueInSingleQuotesWithoutSpaces() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-source-path='path/to/src'");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-source-path='path/to/src'", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithAssignedStringValueInDoubleQuotesWithSpaces() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-source-path=\"path/with spaces/to/src\"");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-source-path=\"path/with spaces/to/src\"", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithAssignedStringValueInSingleQuotesWithSpaces() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-source-path='path/with spaces/to/src'");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-source-path='path/with spaces/to/src'", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithPlusAssignedStringValueInDoubleQuotesWithoutSpaces() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-source-path+=\"path/to/src\"");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-source-path+=\"path/to/src\"", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithPlusAssignedStringValueInSingleQuotesWithoutSpaces() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-source-path+='path/to/src'");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-source-path+='path/to/src'", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithPlusAssignedStringValueInDoubleQuotesWithSpaces() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-source-path+=\"path/with spaces/to/src\"");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-source-path+=\"path/with spaces/to/src\"", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithPlusAssignedStringValueInSingleQuotesWithSpaces() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-source-path='path/with spaces/to/src'");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-source-path='path/with spaces/to/src'", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithAssignmentAndMultipleValues() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-define=CONFIG::TEST,123");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-define=CONFIG::TEST,123", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testDoubleHyphenOptionWithAssignmentAndMultipleValues() {
		List<String> options = OptionsUtils.parseAdditionalOptions("--define=CONFIG::TEST,123");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("--define=CONFIG::TEST,123", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithAssignmentAndMultipleValuesIncludingStringWithDoubleQuotes() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-define=CONFIG::TEST,\"123\"");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-define=CONFIG::TEST,\"123\"", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testDoubleHyphenOptionWithAssignmentAndMultipleValuesIncludingStringWithDoubleQuotes() {
		List<String> options = OptionsUtils.parseAdditionalOptions("--define=CONFIG::TEST,\"123\"");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("--define=CONFIG::TEST,\"123\"", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithAssignmentAndMultipleValuesIncludingStringWithSingleQuotes() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-define=CONFIG::TEST,'123'");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-define=CONFIG::TEST,'123'", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testDoubleHyphenOptionWithAssignmentAndMultipleValuesIncludingStringWithSingleQuotes() {
		List<String> options = OptionsUtils.parseAdditionalOptions("--define=CONFIG::TEST,'123'");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("--define=CONFIG::TEST,'123'", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithAssignmentAndMultipleValuesIncludingStringWithDoubleQuotesAndSpaces() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-define=CONFIG::TEST,\"ABC 123\"");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-define=CONFIG::TEST,\"ABC 123\"", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testDoubleHyphenOptionWithAssignmentAndMultipleValuesIncludingStringWithDoubleQuotesAndSpaces() {
		List<String> options = OptionsUtils.parseAdditionalOptions("--define=CONFIG::TEST,\"ABC 123\"");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("--define=CONFIG::TEST,\"ABC 123\"", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithAssignmentAndMultipleValuesIncludingStringWithSingleQuotesAndSpaces() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-define=CONFIG::TEST,'ABC 123'");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-define=CONFIG::TEST,'ABC 123'", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testDoubleHyphenOptionWithAssignmentAndMultipleValuesIncludingStringWithSingleQuotesAndSpaces() {
		List<String> options = OptionsUtils.parseAdditionalOptions("--define=CONFIG::TEST,'ABC 123'");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("--define=CONFIG::TEST,'ABC 123'", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithAssignmentAndMultipleValuesIncludingStringWithNestedDoubleInsideSingleQuotes() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-define=CONFIG::TEST,'\"123\"'");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-define=CONFIG::TEST,'\"123\"'", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testDoubleHyphenOptionWithAssignmentAndMultipleValuesIncludingStringWithNestedDoubleInsideSingleQuotes() {
		List<String> options = OptionsUtils.parseAdditionalOptions("--define=CONFIG::TEST,'\"123\"'");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("--define=CONFIG::TEST,'\"123\"'", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testOptionWithAssignmentAndMultipleValuesIncludingStringWithNestedSingleInsideDoubleQuotes() {
		List<String> options = OptionsUtils.parseAdditionalOptions("-define=CONFIG::TEST,\"'123'\"");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("-define=CONFIG::TEST,\"'123'\"", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}

	@Test
	void testDoubleHyphenOptionWithAssignmentAndMultipleValuesIncludingStringWithNestedSingleInsideDoubleQuotes() {
		List<String> options = OptionsUtils.parseAdditionalOptions("--define=CONFIG::TEST,\"'123'\"");
		Assertions.assertEquals(1, options.size(),
				"OptionsUtils.parseAdditionalOptions() returned incorrect number of options.");
		Assertions.assertEquals("--define=CONFIG::TEST,\"'123'\"", options.get(0),
				"OptionsUtils.parseAdditionalOptions() returned incorrect value.");
	}
}