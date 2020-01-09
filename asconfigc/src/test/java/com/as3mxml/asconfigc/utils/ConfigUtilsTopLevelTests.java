/*
Copyright 2016-2020 Bowler Hat LLC

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
import com.as3mxml.asconfigc.air.AIRPlatform;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ConfigUtilsTopLevelTests
{
	//--- copySourcePathAssets
	//copySourcePathAssets is a normal field that is not a special case
	
	@Test
	void testCopySourcePathAssetsWithBaseOnly() throws IOException
	{
		boolean baseValue = false;
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"copySourcePathAssets\": " + baseValue +
			"}"
		);
		JsonNode configData = mapper.readTree("{}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.COPY_SOURCE_PATH_ASSETS));
		boolean resultValue = result.get(TopLevelFields.COPY_SOURCE_PATH_ASSETS).asBoolean();
		Assertions.assertEquals(baseValue, resultValue);
	}
	
	@Test
	void testCopySourcePathAssetsWithoutBase() throws IOException
	{
		boolean newValue = true;
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree("{}");
		JsonNode configData = mapper.readTree(
			"{" +
				"\"copySourcePathAssets\": " + newValue +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.COPY_SOURCE_PATH_ASSETS));
		boolean resultValue = result.get(TopLevelFields.COPY_SOURCE_PATH_ASSETS).asBoolean();
		Assertions.assertEquals(newValue, resultValue);
	}
	
	@Test
	void testCopySourcePathAssetsMerge() throws IOException
	{
		boolean baseValue = false;
		boolean newValue = true;
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"copySourcePathAssets\": " + baseValue +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" +
				"\"copySourcePathAssets\": " + newValue +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.COPY_SOURCE_PATH_ASSETS));
		boolean resultValue = result.get(TopLevelFields.COPY_SOURCE_PATH_ASSETS).asBoolean();
		Assertions.assertEquals(newValue, resultValue);
	}

	//--- files

	//files is an array, but unlike other arrays, it does not get merged,
	//so it should be tested as a special case

	//the files array is not merged because the order elements in the array must
	//be preserved, unlike other arrays which can be handled more leniently
	
	@Test
	void testFilesWithBaseOnly() throws IOException
	{
		String baseValue = "src/Base.as";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"files\": [\"" + baseValue + "\"]" +
			"}"
		);
		JsonNode configData = mapper.readTree("{}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.FILES));
		JsonNode resultValue = result.get(TopLevelFields.FILES);
		Assertions.assertTrue(resultValue.isArray());
		Iterator<JsonNode> elements = resultValue.elements();
		Assertions.assertTrue(elements.hasNext());
		String resultValue0 = elements.next().asText();
		Assertions.assertEquals(baseValue, resultValue0);
		Assertions.assertFalse(elements.hasNext());
	}
	
	@Test
	void testFilesWithoutBase() throws IOException
	{
		String newValue = "src/New.as";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree("{}");
		JsonNode configData = mapper.readTree(
			"{" +
				"\"files\": [\"" + newValue + "\"]" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.FILES));
		JsonNode resultValue = result.get(TopLevelFields.FILES);
		Assertions.assertTrue(resultValue.isArray());
		Iterator<JsonNode> elements = resultValue.elements();
		Assertions.assertTrue(elements.hasNext());
		String resultValue0 = elements.next().asText();
		Assertions.assertEquals(newValue, resultValue0);
		Assertions.assertFalse(elements.hasNext());
	}
	
	@Test
	void testFilesMerge() throws IOException
	{
		String baseValue = "src/Base.as";
		String newValue = "src/New.as";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"files\": [\"" + baseValue + "\"]" +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" +
				"\"files\": [\"" + newValue + "\"]" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.FILES));
		JsonNode resultValue = result.get(TopLevelFields.FILES);
		Assertions.assertTrue(resultValue.isArray());
		Iterator<JsonNode> elements = resultValue.elements();
		Assertions.assertTrue(elements.hasNext());
		String resultValue0 = elements.next().asText();
		Assertions.assertEquals(newValue, resultValue0);
		Assertions.assertFalse(elements.hasNext());
	}

	//--- application

	//application can be a string or an object, and it has special rules for
	//merging
	
	@Test
	void testApplicationStringWithBaseOnly() throws IOException
	{
		String baseValue = "src/Base-app.xml";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"application\": \"" + baseValue + "\"" +
			"}"
		);
		JsonNode configData = mapper.readTree("{}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.APPLICATION));
		String resultValue = result.get(TopLevelFields.APPLICATION).asText();
		Assertions.assertEquals(baseValue, resultValue);
	}
	
	@Test
	void testApplicationStringWithoutBase() throws IOException
	{
		String newValue = "src/New-app.xml";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree("{}");
		JsonNode configData = mapper.readTree(
			"{" +
				"\"application\": \"" + newValue + "\"" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.APPLICATION));
		String resultValue = result.get(TopLevelFields.APPLICATION).asText();
		Assertions.assertEquals(newValue, resultValue);
	}
	
	@Test
	void testApplicationStringMerge() throws IOException
	{
		String baseValue = "src/Base-app.xml";
		String newValue = "src/New-app.xml";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"application\": \"" + baseValue + "\"" +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" +
				"\"application\": \"" + newValue + "\"" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.APPLICATION));
		String resultValue = result.get(TopLevelFields.APPLICATION).asText();
		Assertions.assertEquals(newValue, resultValue);
	}
	
	@Test
	void testApplicationObjectWithBaseOnly() throws IOException
	{
		String baseValue0 = "src/BaseIOS-app.xml";
		String baseValue1 = "src/BaseAndroid-app.xml";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"application\": {" +
					"\"ios\": \"" + baseValue0 + "\"," +
					"\"android\": \"" + baseValue1 + "\"" +
				"}" +
			"}"
		);
		JsonNode configData = mapper.readTree("{}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.APPLICATION));
		JsonNode resultValue = result.get(TopLevelFields.APPLICATION);
		Assertions.assertTrue(resultValue.isObject());
		Assertions.assertTrue(resultValue.has(AIRPlatform.IOS));
		Assertions.assertTrue(resultValue.has(AIRPlatform.ANDROID));
		String resultValue0 = resultValue.get(AIRPlatform.IOS).asText();
		Assertions.assertEquals(resultValue0, baseValue0);
		String resultValue1 = resultValue.get(AIRPlatform.ANDROID).asText();
		Assertions.assertEquals(resultValue1, baseValue1);
	}
	
	@Test
	void testApplicationObjectWithoutBase() throws IOException
	{
		String newValue0 = "src/NewIOS-app.xml";
		String newValue1 = "src/NewAndroid-app.xml";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree("{}");
		JsonNode configData = mapper.readTree(
			"{" +
				"\"application\": {" +
					"\"ios\": \"" + newValue0 + "\"," +
					"\"android\": \"" + newValue1 + "\"" +
				"}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.APPLICATION));
		JsonNode resultValue = result.get(TopLevelFields.APPLICATION);
		Assertions.assertTrue(resultValue.isObject());
		Assertions.assertTrue(resultValue.has(AIRPlatform.IOS));
		Assertions.assertTrue(resultValue.has(AIRPlatform.ANDROID));
		String resultValue0 = resultValue.get(AIRPlatform.IOS).asText();
		Assertions.assertEquals(resultValue0, newValue0);
		String resultValue1 = resultValue.get(AIRPlatform.ANDROID).asText();
		Assertions.assertEquals(resultValue1, newValue1);
	}
	
	@Test
	void testApplicationObjectMerge() throws IOException
	{
		String baseValue0 = "src/BaseIOS-app.xml";
		String baseValue1 = "src/BaseAndroid-app.xml";
		String baseValue2 = "src/BaseWindows-app.xml";
		String newValue0 = "src/NewIOS-app.xml";
		String newValue1 = "src/NewAndroid-app.xml";
		String newValue2 = "src/BaseMac-app.xml";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"application\": {" +
					"\"windows\": \"" + baseValue2 + "\"," +
					"\"ios\": \"" + baseValue0 + "\"," +
					"\"android\": \"" + baseValue1 + "\"" +
				"}" +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" +
				"\"application\": {" +
					"\"mac\": \"" + newValue2 + "\"," +
					"\"ios\": \"" + newValue0 + "\"," +
					"\"android\": \"" + newValue1 + "\"" +
				"}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.APPLICATION));
		JsonNode resultValue = result.get(TopLevelFields.APPLICATION);
		Assertions.assertTrue(resultValue.isObject());
		Assertions.assertTrue(resultValue.has(AIRPlatform.IOS));
		Assertions.assertTrue(resultValue.has(AIRPlatform.ANDROID));
		Assertions.assertTrue(resultValue.has(AIRPlatform.WINDOWS));
		Assertions.assertTrue(resultValue.has(AIRPlatform.MAC));
		String resultValue0 = resultValue.get(AIRPlatform.IOS).asText();
		Assertions.assertEquals(resultValue0, newValue0);
		String resultValue1 = resultValue.get(AIRPlatform.ANDROID).asText();
		Assertions.assertEquals(resultValue1, newValue1);
		String resultValue2 = resultValue.get(AIRPlatform.MAC).asText();
		Assertions.assertEquals(resultValue2, newValue2);
		String resultValue3 = resultValue.get(AIRPlatform.WINDOWS).asText();
		Assertions.assertEquals(resultValue3, baseValue2);
	}
	
	@Test
	void testApplicationBaseStringNewObjectMerge() throws IOException
	{
		String baseValue = "src/Base-app.xml";
		String newValue0 = "src/NewIOS-app.xml";
		String newValue1 = "src/NewAndroid-app.xml";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"application\": \"" + baseValue + "\"" +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" +
				"\"application\": {" +
					"\"ios\": \"" + newValue0 + "\"," +
					"\"android\": \"" + newValue1 + "\"" +
				"}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.APPLICATION));
		JsonNode resultValue = result.get(TopLevelFields.APPLICATION);
		Assertions.assertTrue(resultValue.isObject());
		Assertions.assertTrue(resultValue.has(AIRPlatform.IOS));
		Assertions.assertTrue(resultValue.has(AIRPlatform.ANDROID));
		Assertions.assertTrue(resultValue.has(AIRPlatform.WINDOWS));
		Assertions.assertTrue(resultValue.has(AIRPlatform.MAC));
		Assertions.assertTrue(resultValue.has(AIRPlatform.IOS_SIMULATOR));
		Assertions.assertTrue(resultValue.has(AIRPlatform.AIR));
		String resultValue0 = resultValue.get(AIRPlatform.IOS).asText();
		Assertions.assertEquals(resultValue0, newValue0);
		String resultValue1 = resultValue.get(AIRPlatform.ANDROID).asText();
		Assertions.assertEquals(resultValue1, newValue1);
		String resultValue2 = resultValue.get(AIRPlatform.MAC).asText();
		Assertions.assertEquals(resultValue2, baseValue);
		String resultValue3 = resultValue.get(AIRPlatform.WINDOWS).asText();
		Assertions.assertEquals(resultValue3, baseValue);
		String resultValue4 = resultValue.get(AIRPlatform.IOS_SIMULATOR).asText();
		Assertions.assertEquals(resultValue4, baseValue);
		String resultValue5 = resultValue.get(AIRPlatform.AIR).asText();
		Assertions.assertEquals(resultValue5, baseValue);
	}
	
	@Test
	void testApplicationBaseObjectNewStringMerge() throws IOException
	{
		String baseValue0 = "src/BaseIOS-app.xml";
		String baseValue1 = "src/BaseAndroid-app.xml";
		String newValue = "src/New-app.xml";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"application\": {" +
					"\"ios\": \"" + baseValue0 + "\"," +
					"\"android\": \"" + baseValue1 + "\"" +
				"}" +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" +
				"\"application\": \"" + newValue + "\"" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.APPLICATION));
		String resultValue = result.get(TopLevelFields.APPLICATION).asText();
		Assertions.assertEquals(resultValue, newValue);
	}
}