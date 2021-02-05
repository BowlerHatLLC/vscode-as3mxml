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

import java.io.IOException;
import java.util.Iterator;

import com.as3mxml.asconfigc.TopLevelFields;
import com.as3mxml.asconfigc.compiler.CompilerOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConfigUtilsCompilerOptionsTests {
	//--- warnings
	//warnings is a normal field that is not a special case

	@Test
	void testWarningsWithBaseAndEmptyCompilerOptions() throws IOException {
		boolean baseValue = false;
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper
				.readTree("{" + "\"compilerOptions\": {" + "\"warnings\": " + baseValue + "}" + "}");
		JsonNode configData = mapper.readTree("{" + "\"compilerOptions\": {}" + "}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.COMPILER_OPTIONS));
		JsonNode compilerOptions = result.get(TopLevelFields.COMPILER_OPTIONS);
		Assertions.assertTrue(compilerOptions.isObject());
		Assertions.assertTrue(compilerOptions.has(CompilerOptions.WARNINGS));
		boolean resultValue = compilerOptions.get(CompilerOptions.WARNINGS).asBoolean();
		Assertions.assertEquals(baseValue, resultValue);
	}

	@Test
	void testWarningsWithBaseOnly() throws IOException {
		boolean baseValue = false;
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper
				.readTree("{" + "\"compilerOptions\": {" + "\"warnings\": " + baseValue + "}" + "}");
		JsonNode configData = mapper.readTree("{}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.COMPILER_OPTIONS));
		JsonNode compilerOptions = result.get(TopLevelFields.COMPILER_OPTIONS);
		Assertions.assertTrue(compilerOptions.isObject());
		Assertions.assertTrue(compilerOptions.has(CompilerOptions.WARNINGS));
		boolean resultValue = compilerOptions.get(CompilerOptions.WARNINGS).asBoolean();
		Assertions.assertEquals(baseValue, resultValue);
	}

	@Test
	void testWarningsWithEmptyBaseCompilerOptions() throws IOException {
		boolean newValue = true;
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree("{" + "\"compilerOptions\": {}" + "}");
		JsonNode configData = mapper.readTree("{" + "\"compilerOptions\": {" + "\"warnings\": " + newValue + "}" + "}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.COMPILER_OPTIONS));
		JsonNode compilerOptions = result.get(TopLevelFields.COMPILER_OPTIONS);
		Assertions.assertTrue(compilerOptions.isObject());
		Assertions.assertTrue(compilerOptions.has(CompilerOptions.WARNINGS));
		boolean resultValue = compilerOptions.get(CompilerOptions.WARNINGS).asBoolean();
		Assertions.assertEquals(newValue, resultValue);
	}

	@Test
	void testWarningsWithoutBaseCompilerOptions() throws IOException {
		boolean newValue = true;
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree("{}");
		JsonNode configData = mapper.readTree("{" + "\"compilerOptions\": {" + "\"warnings\": " + newValue + "}" + "}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.COMPILER_OPTIONS));
		JsonNode compilerOptions = result.get(TopLevelFields.COMPILER_OPTIONS);
		Assertions.assertTrue(compilerOptions.isObject());
		Assertions.assertTrue(compilerOptions.has(CompilerOptions.WARNINGS));
		boolean resultValue = compilerOptions.get(CompilerOptions.WARNINGS).asBoolean();
		Assertions.assertEquals(newValue, resultValue);
	}

	@Test
	void testWarningsMerge() throws IOException {
		boolean baseValue = false;
		boolean newValue = true;
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper
				.readTree("{" + "\"compilerOptions\": {" + "\"warnings\": " + baseValue + "}" + "}");
		JsonNode configData = mapper.readTree("{" + "\"compilerOptions\": {" + "\"warnings\": " + newValue + "}" + "}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.COMPILER_OPTIONS));
		JsonNode compilerOptions = result.get(TopLevelFields.COMPILER_OPTIONS);
		Assertions.assertTrue(compilerOptions.isObject());
		Assertions.assertTrue(compilerOptions.has(CompilerOptions.WARNINGS));
		boolean resultValue = compilerOptions.get(CompilerOptions.WARNINGS).asBoolean();
		Assertions.assertEquals(newValue, resultValue);
	}

	//--- source-path
	//since this compiler option supports appending --source-path+=src
	//the array is merged

	@Test
	void testSourcePathWithBaseOnly() throws IOException {
		String baseValue = "./base/src";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper
				.readTree("{" + "\"compilerOptions\": {" + "\"source-path\": [\"" + baseValue + "\"]" + "}" + "}");
		JsonNode configData = mapper.readTree("{" + "\"compilerOptions\": {}" + "}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.COMPILER_OPTIONS));
		JsonNode compilerOptions = result.get(TopLevelFields.COMPILER_OPTIONS);
		Assertions.assertTrue(compilerOptions.isObject());
		Assertions.assertTrue(compilerOptions.has(CompilerOptions.SOURCE_PATH));
		JsonNode resultValue = compilerOptions.get(CompilerOptions.SOURCE_PATH);
		Assertions.assertTrue(resultValue.isArray());
		Iterator<JsonNode> elements = resultValue.elements();
		Assertions.assertTrue(elements.hasNext());
		String resultValue0 = elements.next().asText();
		Assertions.assertEquals(baseValue, resultValue0);
		Assertions.assertFalse(elements.hasNext());
	}

	@Test
	void testSourcePathWithBaseAndEmptyArray() throws IOException {
		String baseValue = "./base/src";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper
				.readTree("{" + "\"compilerOptions\": {" + "\"source-path\": [\"" + baseValue + "\"]" + "}" + "}");
		JsonNode configData = mapper.readTree("{" + "\"compilerOptions\": {" + "\"source-path\": []" + "}" + "}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.COMPILER_OPTIONS));
		JsonNode compilerOptions = result.get(TopLevelFields.COMPILER_OPTIONS);
		Assertions.assertTrue(compilerOptions.isObject());
		Assertions.assertTrue(compilerOptions.has(CompilerOptions.SOURCE_PATH));
		JsonNode resultValue = compilerOptions.get(CompilerOptions.SOURCE_PATH);
		Assertions.assertTrue(resultValue.isArray());
		Iterator<JsonNode> elements = resultValue.elements();
		Assertions.assertTrue(elements.hasNext());
		String resultValue0 = elements.next().asText();
		Assertions.assertEquals(baseValue, resultValue0);
		Assertions.assertFalse(elements.hasNext());
	}

	@Test
	void testSourcePathWithoutBase() throws IOException {
		String newValue = "./new/src";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree("{" + "\"compilerOptions\": {}" + "}");
		JsonNode configData = mapper
				.readTree("{" + "\"compilerOptions\": {" + "\"source-path\": [\"" + newValue + "\"]" + "}" + "}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.COMPILER_OPTIONS));
		JsonNode compilerOptions = result.get(TopLevelFields.COMPILER_OPTIONS);
		Assertions.assertTrue(compilerOptions.isObject());
		Assertions.assertTrue(compilerOptions.has(CompilerOptions.SOURCE_PATH));
		JsonNode resultValue = compilerOptions.get(CompilerOptions.SOURCE_PATH);
		Assertions.assertTrue(resultValue.isArray());
		Iterator<JsonNode> elements = resultValue.elements();
		Assertions.assertTrue(elements.hasNext());
		String resultValue0 = elements.next().asText();
		Assertions.assertEquals(newValue, resultValue0);
		Assertions.assertFalse(elements.hasNext());
	}

	@Test
	void testSourcePathWithEmptyBaseArray() throws IOException {
		String newValue = "./new/src";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree("{" + "\"compilerOptions\": {" + "\"source-path\": []" + "}" + "}");
		JsonNode configData = mapper
				.readTree("{" + "\"compilerOptions\": {" + "\"source-path\": [\"" + newValue + "\"]" + "}" + "}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.COMPILER_OPTIONS));
		JsonNode compilerOptions = result.get(TopLevelFields.COMPILER_OPTIONS);
		Assertions.assertTrue(compilerOptions.isObject());
		Assertions.assertTrue(compilerOptions.has(CompilerOptions.SOURCE_PATH));
		JsonNode resultValue = compilerOptions.get(CompilerOptions.SOURCE_PATH);
		Assertions.assertTrue(resultValue.isArray());
		Iterator<JsonNode> elements = resultValue.elements();
		Assertions.assertTrue(elements.hasNext());
		String resultValue0 = elements.next().asText();
		Assertions.assertEquals(newValue, resultValue0);
		Assertions.assertFalse(elements.hasNext());
	}

	@Test
	void testSourcePathMerge() throws IOException {
		String baseValue = "./base/src";
		String newValue = "./new/src";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper
				.readTree("{" + "\"compilerOptions\": {" + "\"source-path\": [\"" + baseValue + "\"]" + "}" + "}");
		JsonNode configData = mapper
				.readTree("{" + "\"compilerOptions\": {" + "\"source-path\": [\"" + newValue + "\"]" + "}" + "}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.COMPILER_OPTIONS));
		JsonNode compilerOptions = result.get(TopLevelFields.COMPILER_OPTIONS);
		Assertions.assertTrue(compilerOptions.isObject());
		Assertions.assertTrue(compilerOptions.has(CompilerOptions.SOURCE_PATH));
		JsonNode resultValue = compilerOptions.get(CompilerOptions.SOURCE_PATH);
		Assertions.assertTrue(resultValue.isArray());
		Iterator<JsonNode> elements = resultValue.elements();
		Assertions.assertTrue(elements.hasNext());
		String resultValue0 = elements.next().asText();
		Assertions.assertEquals(baseValue, resultValue0);
		Assertions.assertTrue(elements.hasNext());
		String resultValue1 = elements.next().asText();
		Assertions.assertEquals(newValue, resultValue1);
		Assertions.assertFalse(elements.hasNext());
	}

	@Test
	void testSourcePathMergeDuplicates() throws IOException {
		String duplicateValue = "./duplicate/src";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper
				.readTree("{" + "\"compilerOptions\": {" + "\"source-path\": [\"" + duplicateValue + "\"]" + "}" + "}");
		JsonNode configData = mapper
				.readTree("{" + "\"compilerOptions\": {" + "\"source-path\": [\"" + duplicateValue + "\"]" + "}" + "}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.COMPILER_OPTIONS));
		JsonNode compilerOptions = result.get(TopLevelFields.COMPILER_OPTIONS);
		Assertions.assertTrue(compilerOptions.isObject());
		Assertions.assertTrue(compilerOptions.has(CompilerOptions.SOURCE_PATH));
		JsonNode resultValue = compilerOptions.get(CompilerOptions.SOURCE_PATH);
		Assertions.assertTrue(resultValue.isArray());
		Iterator<JsonNode> elements = resultValue.elements();
		Assertions.assertTrue(elements.hasNext());
		String resultValue0 = elements.next().asText();
		Assertions.assertEquals(duplicateValue, resultValue0);
		Assertions.assertFalse(elements.hasNext());
	}

	//--- define
	//this compiler option is an array of objects, and a specific key in the
	//object should not be duplicated

	@Test
	void testDefineWithBaseOnly() throws IOException {
		String baseName = "CONFIG::BASE";
		boolean baseValue = false;
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree("{" + "\"compilerOptions\": {" + "\"define\": [" + "{"
				+ "\"name\": \"" + baseName + "\"," + "\"value\": \"" + baseValue + "\"" + "}" + "]" + "}" + "}");
		JsonNode configData = mapper.readTree("{" + "\"compilerOptions\": {}" + "}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.COMPILER_OPTIONS));
		JsonNode compilerOptions = result.get(TopLevelFields.COMPILER_OPTIONS);
		Assertions.assertTrue(compilerOptions.isObject());
		Assertions.assertTrue(compilerOptions.has(CompilerOptions.DEFINE));
		JsonNode resultValue = compilerOptions.get(CompilerOptions.DEFINE);
		Assertions.assertTrue(resultValue.isArray());
		Iterator<JsonNode> elements = resultValue.elements();
		Assertions.assertTrue(elements.hasNext());
		JsonNode resultValue0 = elements.next();
		Assertions.assertTrue(resultValue0.has(CompilerOptions.DEFINE__NAME));
		Assertions.assertTrue(resultValue0.has(CompilerOptions.DEFINE__VALUE));
		String result0Name = resultValue0.get(CompilerOptions.DEFINE__NAME).asText();
		boolean result0Value = resultValue0.get(CompilerOptions.DEFINE__VALUE).asBoolean();
		Assertions.assertEquals(baseName, result0Name);
		Assertions.assertEquals(baseValue, result0Value);
		Assertions.assertFalse(elements.hasNext());
	}

	@Test
	void testDefineWithoutBase() throws IOException {
		String newName = "CONFIG::NEW";
		boolean newValue = true;
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree("{" + "\"compilerOptions\": {}" + "}");
		JsonNode configData = mapper.readTree("{" + "\"compilerOptions\": {" + "\"define\": [" + "{" + "\"name\": \""
				+ newName + "\"," + "\"value\": \"" + newValue + "\"" + "}" + "]" + "}" + "}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.COMPILER_OPTIONS));
		JsonNode compilerOptions = result.get(TopLevelFields.COMPILER_OPTIONS);
		Assertions.assertTrue(compilerOptions.isObject());
		Assertions.assertTrue(compilerOptions.has(CompilerOptions.DEFINE));
		JsonNode resultValue = compilerOptions.get(CompilerOptions.DEFINE);
		Assertions.assertTrue(resultValue.isArray());
		Iterator<JsonNode> elements = resultValue.elements();
		Assertions.assertTrue(elements.hasNext());
		JsonNode resultValue0 = elements.next();
		Assertions.assertTrue(resultValue0.has(CompilerOptions.DEFINE__NAME));
		Assertions.assertTrue(resultValue0.has(CompilerOptions.DEFINE__VALUE));
		String result0Name = resultValue0.get(CompilerOptions.DEFINE__NAME).asText();
		boolean result0Value = resultValue0.get(CompilerOptions.DEFINE__VALUE).asBoolean();
		Assertions.assertEquals(newName, result0Name);
		Assertions.assertEquals(newValue, result0Value);
		Assertions.assertFalse(elements.hasNext());
	}

	@Test
	void testDefineMerge() throws IOException {
		String baseName = "CONFIG::BASE";
		boolean baseValue = false;
		String newName = "CONFIG::NEW";
		boolean newValue = true;
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree("{" + "\"compilerOptions\": {" + "\"define\": [" + "{"
				+ "\"name\": \"" + baseName + "\"," + "\"value\": \"" + baseValue + "\"" + "}" + "]" + "}" + "}");
		JsonNode configData = mapper.readTree("{" + "\"compilerOptions\": {" + "\"define\": [" + "{" + "\"name\": \""
				+ newName + "\"," + "\"value\": \"" + newValue + "\"" + "}" + "]" + "}" + "}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.COMPILER_OPTIONS));
		JsonNode compilerOptions = result.get(TopLevelFields.COMPILER_OPTIONS);
		Assertions.assertTrue(compilerOptions.isObject());
		Assertions.assertTrue(compilerOptions.has(CompilerOptions.DEFINE));
		JsonNode resultValue = compilerOptions.get(CompilerOptions.DEFINE);
		Assertions.assertTrue(resultValue.isArray());
		Iterator<JsonNode> elements = resultValue.elements();
		Assertions.assertTrue(elements.hasNext());
		JsonNode resultValue0 = elements.next();
		Assertions.assertTrue(resultValue0.has(CompilerOptions.DEFINE__NAME));
		Assertions.assertTrue(resultValue0.has(CompilerOptions.DEFINE__VALUE));
		String result0Name = resultValue0.get(CompilerOptions.DEFINE__NAME).asText();
		boolean result0Value = resultValue0.get(CompilerOptions.DEFINE__VALUE).asBoolean();
		Assertions.assertEquals(baseName, result0Name);
		Assertions.assertEquals(baseValue, result0Value);
		Assertions.assertTrue(elements.hasNext());
		JsonNode resultValue1 = elements.next();
		Assertions.assertTrue(resultValue1.has(CompilerOptions.DEFINE__NAME));
		Assertions.assertTrue(resultValue1.has(CompilerOptions.DEFINE__VALUE));
		String result1Name = resultValue1.get(CompilerOptions.DEFINE__NAME).asText();
		boolean result1Value = resultValue1.get(CompilerOptions.DEFINE__VALUE).asBoolean();
		Assertions.assertEquals(newName, result1Name);
		Assertions.assertEquals(newValue, result1Value);
		Assertions.assertFalse(elements.hasNext());
	}

	@Test
	void testDefineMergeDuplicate() throws IOException {
		String duplicateName = "CONFIG::DUPLICATE";
		boolean baseValue = false;
		boolean newValue = true;
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree("{" + "\"compilerOptions\": {" + "\"define\": [" + "{"
				+ "\"name\": \"" + duplicateName + "\"," + "\"value\": \"" + baseValue + "\"" + "}" + "]" + "}" + "}");
		JsonNode configData = mapper.readTree("{" + "\"compilerOptions\": {" + "\"define\": [" + "{" + "\"name\": \""
				+ duplicateName + "\"," + "\"value\": \"" + newValue + "\"" + "}" + "]" + "}" + "}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.COMPILER_OPTIONS));
		JsonNode compilerOptions = result.get(TopLevelFields.COMPILER_OPTIONS);
		Assertions.assertTrue(compilerOptions.isObject());
		Assertions.assertTrue(compilerOptions.has(CompilerOptions.DEFINE));
		JsonNode resultValue = compilerOptions.get(CompilerOptions.DEFINE);
		Assertions.assertTrue(resultValue.isArray());
		Iterator<JsonNode> elements = resultValue.elements();
		Assertions.assertTrue(elements.hasNext());
		JsonNode resultValue0 = elements.next();
		Assertions.assertTrue(resultValue0.has(CompilerOptions.DEFINE__NAME));
		Assertions.assertTrue(resultValue0.has(CompilerOptions.DEFINE__VALUE));
		String result0Name = resultValue0.get(CompilerOptions.DEFINE__NAME).asText();
		boolean result0Value = resultValue0.get(CompilerOptions.DEFINE__VALUE).asBoolean();
		Assertions.assertEquals(duplicateName, result0Name);
		Assertions.assertEquals(newValue, result0Value);
		Assertions.assertFalse(elements.hasNext());
	}
}