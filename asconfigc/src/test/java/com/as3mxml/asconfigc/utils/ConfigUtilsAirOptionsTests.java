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
import com.as3mxml.asconfigc.air.AIROptions;
import com.as3mxml.asconfigc.air.AIRPlatform;
import com.as3mxml.asconfigc.air.AIRSigningOptions;
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
	void testFilesWithEmptyBaseAirOptions() throws IOException
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

	//--- signingOptions
	//this air option is a complex object, but it should just replace the
	//base and not get merged
	
	@Test
	void testSigningOptionsWithBaseAndEmptyAirOptions() throws IOException
	{
		String baseKeystore = "signing/base.p12";
		String baseStoretype = "pkcs12";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"signingOptions\": {" +
						"\"keystore\": \"" + baseKeystore + "\"," +
						"\"storetype\": \"" + baseStoretype + "\"" +
					"}" +
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
		Assertions.assertTrue(airOptions.has(AIROptions.SIGNING_OPTIONS));
		JsonNode signingOptions = airOptions.get(AIROptions.SIGNING_OPTIONS);
		Assertions.assertTrue(signingOptions.isObject());

		Assertions.assertFalse(signingOptions.has(AIRSigningOptions.DEBUG));
		Assertions.assertFalse(signingOptions.has(AIRSigningOptions.RELEASE));

		Assertions.assertTrue(signingOptions.has(AIRSigningOptions.KEYSTORE));
		String resultKeystore = signingOptions.get(AIRSigningOptions.KEYSTORE).asText();
		Assertions.assertEquals(baseKeystore, resultKeystore);

		Assertions.assertTrue(signingOptions.has(AIRSigningOptions.STORETYPE));
		String resultStoretype = signingOptions.get(AIRSigningOptions.STORETYPE).asText();
		Assertions.assertEquals(baseStoretype, resultStoretype);
	}
	
	@Test
	void testSigningOptionsWithBaseOnly() throws IOException
	{
		String baseKeystore = "signing/base.p12";
		String baseStoretype = "pkcs12";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"signingOptions\": {" +
						"\"keystore\": \"" + baseKeystore + "\"," +
						"\"storetype\": \"" + baseStoretype + "\"" +
					"}" +
				"}" +
			"}"
		);
		JsonNode configData = mapper.readTree("{}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIROptions.SIGNING_OPTIONS));
		JsonNode signingOptions = airOptions.get(AIROptions.SIGNING_OPTIONS);
		Assertions.assertTrue(signingOptions.isObject());

		Assertions.assertFalse(signingOptions.has(AIRSigningOptions.DEBUG));
		Assertions.assertFalse(signingOptions.has(AIRSigningOptions.RELEASE));

		Assertions.assertTrue(signingOptions.has(AIRSigningOptions.KEYSTORE));
		String resultKeystore = signingOptions.get(AIRSigningOptions.KEYSTORE).asText();
		Assertions.assertEquals(baseKeystore, resultKeystore);

		Assertions.assertTrue(signingOptions.has(AIRSigningOptions.STORETYPE));
		String resultStoretype = signingOptions.get(AIRSigningOptions.STORETYPE).asText();
		Assertions.assertEquals(baseStoretype, resultStoretype);
	}
	
	@Test
	void testSigningOptionsWithEmptyBaseAirOptions() throws IOException
	{
		String newProviderName = "Apple";
		String newStoretype = "KeychainStore";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"airOptions\": {}" +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"signingOptions\": {" +
						"\"providerName\": \"" + newProviderName + "\"," +
						"\"storetype\": \"" + newStoretype + "\"" +
					"}" +
				"}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIROptions.SIGNING_OPTIONS));
		JsonNode signingOptions = airOptions.get(AIROptions.SIGNING_OPTIONS);
		Assertions.assertTrue(signingOptions.isObject());

		Assertions.assertFalse(signingOptions.has(AIRSigningOptions.DEBUG));
		Assertions.assertFalse(signingOptions.has(AIRSigningOptions.RELEASE));

		Assertions.assertTrue(signingOptions.has(AIRSigningOptions.PROVIDER_NAME));
		String resultProviderName = signingOptions.get(AIRSigningOptions.PROVIDER_NAME).asText();
		Assertions.assertEquals(newProviderName, resultProviderName);

		Assertions.assertTrue(signingOptions.has(AIRSigningOptions.STORETYPE));
		String resultStoretype = signingOptions.get(AIRSigningOptions.STORETYPE).asText();
		Assertions.assertEquals(newStoretype, resultStoretype);
	}
	
	@Test
	void testSigningOptionsWithoutBaseAirOptions() throws IOException
	{
		String newProviderName = "Apple";
		String newStoretype = "KeychainStore";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree("{}");
		JsonNode configData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"signingOptions\": {" +
						"\"providerName\": \"" + newProviderName + "\"," +
						"\"storetype\": \"" + newStoretype + "\"" +
					"}" +
				"}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIROptions.SIGNING_OPTIONS));
		JsonNode signingOptions = airOptions.get(AIROptions.SIGNING_OPTIONS);
		Assertions.assertTrue(signingOptions.isObject());

		Assertions.assertFalse(signingOptions.has(AIRSigningOptions.DEBUG));
		Assertions.assertFalse(signingOptions.has(AIRSigningOptions.RELEASE));

		Assertions.assertTrue(signingOptions.has(AIRSigningOptions.PROVIDER_NAME));
		String resultProviderName = signingOptions.get(AIRSigningOptions.PROVIDER_NAME).asText();
		Assertions.assertEquals(newProviderName, resultProviderName);

		Assertions.assertTrue(signingOptions.has(AIRSigningOptions.STORETYPE));
		String resultStoretype = signingOptions.get(AIRSigningOptions.STORETYPE).asText();
		Assertions.assertEquals(newStoretype, resultStoretype);
	}
	
	@Test
	void testSigningOptionsMerge() throws IOException
	{
		String baseKeystore = "signing/base.p12";
		String baseStoretype = "pkcs12";
		String newProviderName = "Apple";
		String newStoretype = "KeychainStore";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"signingOptions\": {" +
						"\"keystore\": \"" + baseKeystore + "\"," +
						"\"storetype\": \"" + baseStoretype + "\"" +
					"}" +
				"}" +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"signingOptions\": {" +
						"\"providerName\": \"" + newProviderName + "\"," +
						"\"storetype\": \"" + newStoretype + "\"" +
					"}" +
				"}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIROptions.SIGNING_OPTIONS));
		JsonNode signingOptions = airOptions.get(AIROptions.SIGNING_OPTIONS);
		Assertions.assertTrue(signingOptions.isObject());

		Assertions.assertFalse(signingOptions.has(AIRSigningOptions.DEBUG));
		Assertions.assertFalse(signingOptions.has(AIRSigningOptions.RELEASE));
		Assertions.assertFalse(signingOptions.has(AIRSigningOptions.KEYSTORE));

		Assertions.assertTrue(signingOptions.has(AIRSigningOptions.PROVIDER_NAME));
		String resultProviderName = signingOptions.get(AIRSigningOptions.PROVIDER_NAME).asText();
		Assertions.assertEquals(newProviderName, resultProviderName);

		Assertions.assertTrue(signingOptions.has(AIRSigningOptions.STORETYPE));
		String resultStoretype = signingOptions.get(AIRSigningOptions.STORETYPE).asText();
		Assertions.assertEquals(newStoretype, resultStoretype);
	}
	
	@Test
	void testSigningOptionsMergeDebugAndRelease() throws IOException
	{
		String baseDebugKeystore = "signing/base-debug.p12";
		String baseDebugStoretype = "pkcs12";
		String baseReleaseKeystore = "signing/base-release.p12";
		String baseReleaseStoretype = "pkcs12";
		String newDebugProviderName = "Apple";
		String newDebugStoretype = "KeychainStore";
		String newReleaseProviderName = "Adobe";
		String newReleaseStoretype = "KeychainStore";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"signingOptions\": {" +
						"\"debug\": {" +
							"\"keystore\": \"" + baseDebugKeystore + "\"," +
							"\"storetype\": \"" + baseDebugStoretype + "\"" +
						"}," +
						"\"release\": {" +
							"\"keystore\": \"" + baseReleaseKeystore + "\"," +
							"\"storetype\": \"" + baseReleaseStoretype + "\"" +
						"}" +
					"}" +
				"}" +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"signingOptions\": {" +
						"\"debug\": {" +
							"\"providerName\": \"" + newDebugProviderName + "\"," +
							"\"storetype\": \"" + newDebugStoretype + "\"" +
						"}," +
						"\"release\": {" +
							"\"providerName\": \"" + newReleaseProviderName + "\"," +
							"\"storetype\": \"" + newReleaseStoretype + "\"" +
						"}" +
					"}" +
				"}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIROptions.SIGNING_OPTIONS));
		JsonNode signingOptions = airOptions.get(AIROptions.SIGNING_OPTIONS);
		Assertions.assertTrue(signingOptions.isObject());

		Assertions.assertTrue(signingOptions.has(AIRSigningOptions.DEBUG));
		Assertions.assertTrue(signingOptions.has(AIRSigningOptions.RELEASE));

		JsonNode debug = signingOptions.get(AIRSigningOptions.DEBUG);
		Assertions.assertTrue(debug.isObject());

		Assertions.assertFalse(debug.has(AIRSigningOptions.KEYSTORE));

		Assertions.assertTrue(debug.has(AIRSigningOptions.PROVIDER_NAME));
		String resultDebugProviderName = debug.get(AIRSigningOptions.PROVIDER_NAME).asText();
		Assertions.assertEquals(newDebugProviderName, resultDebugProviderName);

		Assertions.assertTrue(debug.has(AIRSigningOptions.STORETYPE));
		String resultDebugStoretype = debug.get(AIRSigningOptions.STORETYPE).asText();
		Assertions.assertEquals(newDebugStoretype, resultDebugStoretype);
		
		JsonNode release = signingOptions.get(AIRSigningOptions.RELEASE);
		Assertions.assertTrue(release.isObject());

		Assertions.assertFalse(release.has(AIRSigningOptions.KEYSTORE));

		Assertions.assertTrue(release.has(AIRSigningOptions.PROVIDER_NAME));
		String resultReleaseProviderName = release.get(AIRSigningOptions.PROVIDER_NAME).asText();
		Assertions.assertEquals(newReleaseProviderName, resultReleaseProviderName);

		Assertions.assertTrue(release.has(AIRSigningOptions.STORETYPE));
		String resultReleaseStoretype = release.get(AIRSigningOptions.STORETYPE).asText();
		Assertions.assertEquals(newReleaseStoretype, resultReleaseStoretype);
	}
	
	@Test
	void testSigningOptionsMergeWithSingleInBaseAndDebugAndReleaseInOverride() throws IOException
	{
		String baseKeystore = "signing/base-debug.p12";
		String baseStoretype = "pkcs12";
		String newDebugProviderName = "Apple";
		String newDebugStoretype = "KeychainStore";
		String newReleaseProviderName = "Adobe";
		String newReleaseStoretype = "KeychainStore";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"signingOptions\": {" +
						"\"keystore\": \"" + baseKeystore + "\"," +
						"\"storetype\": \"" + baseStoretype + "\"" +
					"}" +
				"}" +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"signingOptions\": {" +
						"\"debug\": {" +
							"\"providerName\": \"" + newDebugProviderName + "\"," +
							"\"storetype\": \"" + newDebugStoretype + "\"" +
						"}," +
						"\"release\": {" +
							"\"providerName\": \"" + newReleaseProviderName + "\"," +
							"\"storetype\": \"" + newReleaseStoretype + "\"" +
						"}" +
					"}" +
				"}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIROptions.SIGNING_OPTIONS));
		JsonNode signingOptions = airOptions.get(AIROptions.SIGNING_OPTIONS);
		Assertions.assertTrue(signingOptions.isObject());

		Assertions.assertTrue(signingOptions.has(AIRSigningOptions.DEBUG));
		Assertions.assertTrue(signingOptions.has(AIRSigningOptions.RELEASE));

		JsonNode debug = signingOptions.get(AIRSigningOptions.DEBUG);
		Assertions.assertTrue(debug.isObject());

		Assertions.assertFalse(debug.has(AIRSigningOptions.KEYSTORE));

		Assertions.assertTrue(debug.has(AIRSigningOptions.PROVIDER_NAME));
		String resultDebugProviderName = debug.get(AIRSigningOptions.PROVIDER_NAME).asText();
		Assertions.assertEquals(newDebugProviderName, resultDebugProviderName);

		Assertions.assertTrue(debug.has(AIRSigningOptions.STORETYPE));
		String resultDebugStoretype = debug.get(AIRSigningOptions.STORETYPE).asText();
		Assertions.assertEquals(newDebugStoretype, resultDebugStoretype);
		
		JsonNode release = signingOptions.get(AIRSigningOptions.RELEASE);
		Assertions.assertTrue(release.isObject());

		Assertions.assertFalse(release.has(AIRSigningOptions.KEYSTORE));

		Assertions.assertTrue(release.has(AIRSigningOptions.PROVIDER_NAME));
		String resultReleaseProviderName = release.get(AIRSigningOptions.PROVIDER_NAME).asText();
		Assertions.assertEquals(newReleaseProviderName, resultReleaseProviderName);

		Assertions.assertTrue(release.has(AIRSigningOptions.STORETYPE));
		String resultReleaseStoretype = release.get(AIRSigningOptions.STORETYPE).asText();
		Assertions.assertEquals(newReleaseStoretype, resultReleaseStoretype);
	}
	
	@Test
	void testSigningOptionsMergeDebugAndReleaseInBaseSingleInOverride() throws IOException
	{
		String baseDebugKeystore = "signing/base-debug.p12";
		String baseDebugStoretype = "pkcs12";
		String baseReleaseKeystore = "signing/base-release.p12";
		String baseReleaseStoretype = "pkcs12";
		String newProviderName = "Apple";
		String newStoretype = "KeychainStore";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"signingOptions\": {" +
						"\"debug\": {" +
							"\"keystore\": \"" + baseDebugKeystore + "\"," +
							"\"storetype\": \"" + baseDebugStoretype + "\"" +
						"}," +
						"\"release\": {" +
							"\"keystore\": \"" + baseReleaseKeystore + "\"," +
							"\"storetype\": \"" + baseReleaseStoretype + "\"" +
						"}" +
					"}" +
				"}" +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"signingOptions\": {" +
						"\"providerName\": \"" + newProviderName + "\"," +
						"\"storetype\": \"" + newStoretype + "\"" +
					"}" +
				"}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIROptions.SIGNING_OPTIONS));
		JsonNode signingOptions = airOptions.get(AIROptions.SIGNING_OPTIONS);
		Assertions.assertTrue(signingOptions.isObject());

		Assertions.assertFalse(signingOptions.has(AIRSigningOptions.DEBUG));
		Assertions.assertFalse(signingOptions.has(AIRSigningOptions.RELEASE));

		Assertions.assertFalse(signingOptions.has(AIRSigningOptions.KEYSTORE));

		Assertions.assertTrue(signingOptions.has(AIRSigningOptions.PROVIDER_NAME));
		String resultProviderName = signingOptions.get(AIRSigningOptions.PROVIDER_NAME).asText();
		Assertions.assertEquals(newProviderName, resultProviderName);

		Assertions.assertTrue(signingOptions.has(AIRSigningOptions.STORETYPE));
		String resultStoretype = signingOptions.get(AIRSigningOptions.STORETYPE).asText();
		Assertions.assertEquals(newStoretype, resultStoretype);
	}

	//--- output (platform)
	
	@Test
	void testOutputPlatformWithBaseAndEmptyPlatform() throws IOException
	{
		String baseValue = "bin/Base.air";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"android\": {" +
						"\"output\": \"" + baseValue + "\"" +
					"}" +
				"}" +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"android\": {}" +
				"}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIRPlatform.ANDROID));
		JsonNode android = airOptions.get(AIRPlatform.ANDROID);
		Assertions.assertTrue(android.isObject());
		Assertions.assertTrue(android.has(AIROptions.OUTPUT));
		String resultValue = android.get(AIROptions.OUTPUT).asText();
		Assertions.assertEquals(baseValue, resultValue);
	}
	
	@Test
	void testOutputPlatformWithBaseAndEmptyAirOptions() throws IOException
	{
		String baseValue = "bin/Base.air";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"android\": {" +
						"\"output\": \"" + baseValue + "\"" +
					"}" +
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
		Assertions.assertTrue(airOptions.has(AIRPlatform.ANDROID));
		JsonNode android = airOptions.get(AIRPlatform.ANDROID);
		Assertions.assertTrue(android.isObject());
		Assertions.assertTrue(android.has(AIROptions.OUTPUT));
		String resultValue = android.get(AIROptions.OUTPUT).asText();
		Assertions.assertEquals(baseValue, resultValue);
	}
	
	@Test
	void testOutputPlatformWithBaseOnly() throws IOException
	{
		String baseValue = "bin/Base.air";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"android\": {" +
						"\"output\": \"" + baseValue + "\"" +
					"}" +
				"}" +
			"}"
		);
		JsonNode configData = mapper.readTree("{}");
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIRPlatform.ANDROID));
		JsonNode android = airOptions.get(AIRPlatform.ANDROID);
		Assertions.assertTrue(android.isObject());
		Assertions.assertTrue(android.has(AIROptions.OUTPUT));
		String resultValue = android.get(AIROptions.OUTPUT).asText();
		Assertions.assertEquals(baseValue, resultValue);
	}
	
	@Test
	void testOutputPlatformWithEmptyBasePlatform() throws IOException
	{
		String newValue = "bin/New.air";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"android\": {}" +
				"}" +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"android\": {" +
						"\"output\": \"" + newValue + "\"" +
					"}" +
				"}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIRPlatform.ANDROID));
		JsonNode android = airOptions.get(AIRPlatform.ANDROID);
		Assertions.assertTrue(android.isObject());
		Assertions.assertTrue(android.has(AIROptions.OUTPUT));
		String resultValue = android.get(AIROptions.OUTPUT).asText();
		Assertions.assertEquals(newValue, resultValue);
	}
	
	@Test
	void testOutputPlatformWithEmptyBaseAirOptions() throws IOException
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
					"\"android\": {" +
						"\"output\": \"" + newValue + "\"" +
					"}" +
				"}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIRPlatform.ANDROID));
		JsonNode android = airOptions.get(AIRPlatform.ANDROID);
		Assertions.assertTrue(android.isObject());
		Assertions.assertTrue(android.has(AIROptions.OUTPUT));
		String resultValue = android.get(AIROptions.OUTPUT).asText();
		Assertions.assertEquals(newValue, resultValue);
	}
	
	@Test
	void testOutputPlatformWithoutBaseAirOptions() throws IOException
	{
		String newValue = "bin/New.air";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree("{}");
		JsonNode configData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"android\": {" +
						"\"output\": \"" + newValue + "\"" +
					"}" +
				"}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIRPlatform.ANDROID));
		JsonNode android = airOptions.get(AIRPlatform.ANDROID);
		Assertions.assertTrue(android.isObject());
		Assertions.assertTrue(android.has(AIROptions.OUTPUT));
		String resultValue = android.get(AIROptions.OUTPUT).asText();
		Assertions.assertEquals(newValue, resultValue);
	}
	
	@Test
	void testOutputPlatformMerge() throws IOException
	{
		String baseValue = "bin/Base.air";
		String newValue = "bin/New.air";
		ObjectMapper mapper = new ObjectMapper();
		JsonNode baseConfigData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"android\": {" +
						"\"output\": \"" + baseValue + "\"" +
					"}" +
				"}" +
			"}"
		);
		JsonNode configData = mapper.readTree(
			"{" +
				"\"airOptions\": {" +
					"\"android\": {" +
						"\"output\": \"" + newValue + "\"" +
					"}" +
				"}" +
			"}"
		);
		JsonNode result = ConfigUtils.mergeConfigs(configData, baseConfigData);
		Assertions.assertTrue(result.has(TopLevelFields.AIR_OPTIONS));
		JsonNode airOptions = result.get(TopLevelFields.AIR_OPTIONS);
		Assertions.assertTrue(airOptions.isObject());
		Assertions.assertTrue(airOptions.has(AIRPlatform.ANDROID));
		JsonNode android = airOptions.get(AIRPlatform.ANDROID);
		Assertions.assertTrue(android.isObject());
		Assertions.assertTrue(android.has(AIROptions.OUTPUT));
		String resultValue = android.get(AIROptions.OUTPUT).asText();
		Assertions.assertEquals(newValue, resultValue);
	}
	
}