/*
Copyright 2016-2019 Bowler Hat LLC

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
import com.as3mxml.asconfigc.air.AIROptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConfigUtilsAirOptionsTests
{
	//--- output
	//output is a normal field that is not a special case
	
	@Test
	void testOutputWithBaseAndEmptyAirOptions() throws IOException
	{
		String baseValue = "bin/Base.air";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"output\": \"" + baseValue + "\"" +
				"}" +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" +
				"\"airOptions\": {}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIROptions.OUTPUT));
		String resultValue = airOptions.get(AIROptions.OUTPUT).asText();
		Assertions.assertEquals(baseValue, resultValue);
	}
	
	@Test
	void testOutputWithBaseOnly() throws IOException
	{
		String baseValue = "bin/Base.air";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"output\": \"" + baseValue + "\"" +
				"}" +
			"}"
		);
		JsonNode configData = mapper.readTree("{}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIROptions.OUTPUT));
		String resultValue = airOptions.get(AIROptions.OUTPUT).asText();
		Assertions.assertEquals(baseValue, resultValue);
	}
	
	@Test
	void testOutputWithEmptyBaseAirOptions() throws IOException
	{
		String newValue = "bin/New.air";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"airOptions\": {}" +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"output\": \"" + newValue + "\"" +
				"}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIROptions.OUTPUT));
		String resultValue = airOptions.get(AIROptions.OUTPUT).asText();
		Assertions.assertEquals(newValue, resultValue);
	}
	
	@Test
	void testOutputWithoutBaseAirOptions() throws IOException
	{
		String newValue = "bin/New.air";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree("{}");
		JsonNode configData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"output\": \"" + newValue + "\"" +
				"}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIROptions.OUTPUT));
		String resultValue = airOptions.get(AIROptions.OUTPUT).asText();
		Assertions.assertEquals(newValue, resultValue);
	}
	
	@Test
	void testOutputMerge() throws IOException
	{
		String baseValue = "bin/Base.air";
		String newValue = "bin/New.air";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"output\": \"" + baseValue + "\"" +
				"}" +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"output\": \"" + newValue + "\"" +
				"}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIROptions.OUTPUT));
		String resultValue = airOptions.get(AIROptions.OUTPUT).asText();
		Assertions.assertEquals(newValue, resultValue);
	}

	//--- files
	//this air option is an array of objects, and a certain key should not
	//be duplicated
	
	@Test
	void testFilesWithBaseOnly() throws IOException
	{
		String baseFile = "assets/base.png";
		String basePath = "base.png";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"files\": [" +
						"{" +
							"\"file\": \"" + baseFile + "\"," + 
							"\"path\": \"" + basePath + "\"" + 
						"}" +
					"]" +
				"}" +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" + 
				"\"airOptions\": {}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIROptions.FILES));
		JsonNode resultValue = airOptions.get(AIROptions.FILES);
		Assertions.assertTrue(resultValue.isArray());
		Iterator<JsonNode> elements = resultValue.elements();
		Assertions.assertTrue(elements.hasNext());
		JsonNode resultValue0 = elements.next();
		Assertions.assertTrue(resultValue0.has(AIROptions.FILES__FILE));
		Assertions.assertTrue(resultValue0.has(AIROptions.FILES__PATH));
		String result0File = resultValue0.get(AIROptions.FILES__FILE).asText();
		String result0Path = resultValue0.get(AIROptions.FILES__PATH).asText();
		Assertions.assertEquals(baseFile, result0File);
		Assertions.assertEquals(basePath, result0Path);
		Assertions.assertFalse(elements.hasNext());
	}
	
	@Test
	void testFilesWithoutBase() throws IOException
	{
		String newFile = "assets/new.png";
		String newPath = "new.png";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"airOptions\": {}" +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" + 
				"\"airOptions\": {" +
					"\"files\": [" +
						"{" +
							"\"file\": \"" + newFile + "\"," + 
							"\"path\": \"" + newPath + "\"" + 
						"}" +
					"]" +
				"}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIROptions.FILES));
		JsonNode resultValue = airOptions.get(AIROptions.FILES);
		Assertions.assertTrue(resultValue.isArray());
		Iterator<JsonNode> elements = resultValue.elements();
		Assertions.assertTrue(elements.hasNext());
		JsonNode resultValue0 = elements.next();
		Assertions.assertTrue(resultValue0.has(AIROptions.FILES__FILE));
		Assertions.assertTrue(resultValue0.has(AIROptions.FILES__PATH));
		String result0File = resultValue0.get(AIROptions.FILES__FILE).asText();
		String result0Path = resultValue0.get(AIROptions.FILES__PATH).asText();
		Assertions.assertEquals(newFile, result0File);
		Assertions.assertEquals(newPath, result0Path);
		Assertions.assertFalse(elements.hasNext());
	}
	
	@Test
	void testFilesMerge() throws IOException
	{
		String baseFile = "assets/base.png";
		String basePath = "base.png";
		String newFile = "assets/new.png";
		String newPath = "new.png";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"files\": [" +
						"{" +
							"\"file\": \"" + baseFile + "\"," + 
							"\"path\": \"" + basePath + "\"" + 
						"}" +
					"]" +
				"}" +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" + 
				"\"airOptions\": {" +
					"\"files\": [" +
						"{" +
							"\"file\": \"" + newFile + "\"," + 
							"\"path\": \"" + newPath + "\"" + 
						"}" +
					"]" +
				"}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIROptions.FILES));
		JsonNode resultValue = airOptions.get(AIROptions.FILES);
		Assertions.assertTrue(resultValue.isArray());
		Iterator<JsonNode> elements = resultValue.elements();
		Assertions.assertTrue(elements.hasNext());
		JsonNode resultValue0 = elements.next();
		Assertions.assertTrue(resultValue0.has(AIROptions.FILES__FILE));
		Assertions.assertTrue(resultValue0.has(AIROptions.FILES__PATH));
		String result0File = resultValue0.get(AIROptions.FILES__FILE).asText();
		String result0Path = resultValue0.get(AIROptions.FILES__PATH).asText();
		Assertions.assertEquals(baseFile, result0File);
		Assertions.assertEquals(basePath, result0Path);
		Assertions.assertTrue(elements.hasNext());
		JsonNode resultValue1 = elements.next();
		Assertions.assertTrue(resultValue1.has(AIROptions.FILES__FILE));
		Assertions.assertTrue(resultValue1.has(AIROptions.FILES__PATH));
		String result1Name = resultValue1.get(AIROptions.FILES__FILE).asText();
		String result1Value = resultValue1.get(AIROptions.FILES__PATH).asText();
		Assertions.assertEquals(newFile, result1Name);
		Assertions.assertEquals(newPath, result1Value);
		Assertions.assertFalse(elements.hasNext());
	}
	
	@Test
	void testFilesMergeDuplicate() throws IOException
	{
		String baseFile = "assets/base.png";
		String newFile = "assets/new.png";
		String duplicatePath = "duplicate.png";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"files\": [" +
						"{" +
							"\"file\": \"" + baseFile + "\"," + 
							"\"path\": \"" + duplicatePath + "\"" + 
						"}" +
					"]" +
				"}" +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" + 
				"\"airOptions\": {" +
					"\"files\": [" +
						"{" +
							"\"file\": \"" + newFile + "\"," + 
							"\"path\": \"" + duplicatePath + "\"" + 
						"}" +
					"]" +
				"}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIROptions.FILES));
		JsonNode resultValue = airOptions.get(AIROptions.FILES);
		Assertions.assertTrue(resultValue.isArray());
		Iterator<JsonNode> elements = resultValue.elements();
		Assertions.assertTrue(elements.hasNext());
		JsonNode resultValue0 = elements.next();
		Assertions.assertTrue(resultValue0.has(AIROptions.FILES__FILE));
		Assertions.assertTrue(resultValue0.has(AIROptions.FILES__PATH));
		String result0File = resultValue0.get(AIROptions.FILES__FILE).asText();
		String result0Path = resultValue0.get(AIROptions.FILES__PATH).asText();
		Assertions.assertEquals(newFile, result0File);
		Assertions.assertEquals(duplicatePath, result0Path);
		Assertions.assertFalse(elements.hasNext());
	}
}