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
package com.as3mxml.asconfigc.air;

import java.nio.file.Paths;
import java.util.ArrayList;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AIROptionsParserTests {
	private AIROptionsParser parser;

	@BeforeEach
	void setup() {
		parser = new AIROptionsParser();
	}

	@AfterEach
	void tearDown() {
		parser = null;
	}

	@Test
	void testDefaultOptions() {
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.AIR, false, "application.xml", "content.swf", options, result);
		Assertions.assertEquals(0, result.indexOf("-package"));
		Assertions.assertEquals(1, result.indexOf("-target"));
		Assertions.assertEquals(2, result.indexOf(AIRPlatform.AIR));
	}

	@Test
	void testApplicationContent() {
		String filename = "content.swf";
		String dirPath = "path/to";
		String value = dirPath + "/" + filename;
		String formattedDirPath = Paths.get(dirPath).toString();
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.AIR, false, "application.xml", value, options, result);
		Assertions.assertFalse(result.contains(value),
				"AIROptionsParser.parse() incorrectly contains application content path.");
		int optionIndex = result.indexOf("-C");
		Assertions.assertNotEquals(-1, optionIndex);
		Assertions.assertEquals(optionIndex + 1, result.indexOf(formattedDirPath));
		Assertions.assertEquals(optionIndex + 2, result.indexOf(filename));
	}

	@Test
	void testDescriptor() {
		String value = "path/to/application.xml";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.AIR, false, value, "content.swf", options, result);
		Assertions.assertEquals(4, result.indexOf(value));
	}

	@Test
	void testAIRDownloadURL() {
		String value = "http://example.com";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode android = JsonNodeFactory.instance.objectNode();
		android.set(AIROptions.AIR_DOWNLOAD_URL, JsonNodeFactory.instance.textNode(value));
		options.set(AIRPlatform.ANDROID, android);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, false, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIROptions.AIR_DOWNLOAD_URL);
		Assertions.assertNotEquals(-1, optionIndex);
		Assertions.assertEquals(optionIndex + 1, result.indexOf(value));
	}

	@Test
	void testArch() {
		String value = "x86";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode android = JsonNodeFactory.instance.objectNode();
		android.set(AIROptions.ARCH, JsonNodeFactory.instance.textNode(value));
		options.set(AIRPlatform.ANDROID, android);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, false, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIROptions.ARCH);
		Assertions.assertNotEquals(-1, optionIndex);
		Assertions.assertEquals(optionIndex + 1, result.indexOf(value));
	}

	@Test
	void testResdir() {
		String value = "path/to/subpath";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode android = JsonNodeFactory.instance.objectNode();
		android.set(AIROptions.RESDIR, JsonNodeFactory.instance.textNode(value));
		options.set(AIRPlatform.ANDROID, android);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, false, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIROptions.RESDIR);
		Assertions.assertNotEquals(-1, optionIndex);
		Assertions.assertEquals(optionIndex + 1, result.indexOf(value));
	}

	@Test
	void testEmbedBitcode() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode ios = JsonNodeFactory.instance.objectNode();
		ios.set(AIROptions.EMBED_BITCODE, JsonNodeFactory.instance.booleanNode(value));
		options.set(AIRPlatform.IOS, ios);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.IOS, false, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIROptions.EMBED_BITCODE);
		Assertions.assertNotEquals(-1, optionIndex);
		Assertions.assertEquals(optionIndex + 1, result.indexOf(value ? "yes" : "no"));
	}

	@Test
	void testExtdir() {
		String value1 = "path/to/subpath1";
		String value2 = "path to/subpath2";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode extdir = JsonNodeFactory.instance.arrayNode();
		extdir.add(value1);
		extdir.add(value2);
		options.set(AIROptions.EXTDIR, extdir);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.AIR, false, "application.xml", "content.swf", options, result);
		int optionIndex1 = result.indexOf("-" + AIROptions.EXTDIR);
		Assertions.assertNotEquals(-1, optionIndex1);
		Assertions.assertEquals(optionIndex1 + 1, result.indexOf(value1));
		int optionIndex2 = optionIndex1 + 1
				+ result.subList(optionIndex1 + 1, result.size()).indexOf("-" + AIROptions.EXTDIR);
		Assertions.assertEquals(optionIndex1 + 2, optionIndex2);
		Assertions.assertEquals(optionIndex2 + 1, result.indexOf(value2));
	}

	@Test
	void testFiles() {
		String file1 = "path/to/file1.png";
		String path1 = ".";
		String file2 = "file2.jpg";
		String path2 = "images";
		String file3 = "file3 with spaces.jpg";
		String path3 = "path/with spaces/";
		String formattedFile1 = Paths.get(file1).toString();
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ArrayNode files = JsonNodeFactory.instance.arrayNode();
		ObjectNode f1 = JsonNodeFactory.instance.objectNode();
		f1.set(AIROptions.FILES__FILE, JsonNodeFactory.instance.textNode(file1));
		f1.set(AIROptions.FILES__PATH, JsonNodeFactory.instance.textNode(path1));
		files.add(f1);
		ObjectNode f2 = JsonNodeFactory.instance.objectNode();
		f2.set(AIROptions.FILES__FILE, JsonNodeFactory.instance.textNode(file2));
		f2.set(AIROptions.FILES__PATH, JsonNodeFactory.instance.textNode(path2));
		files.add(f2);
		ObjectNode f3 = JsonNodeFactory.instance.objectNode();
		f3.set(AIROptions.FILES__FILE, JsonNodeFactory.instance.textNode(file3));
		f3.set(AIROptions.FILES__PATH, JsonNodeFactory.instance.textNode(path3));
		files.add(f3);
		options.set(AIROptions.FILES, files);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.AIR, false, "application.xml", "content.swf", options, result);
		int optionIndex1 = result.indexOf("-e");
		Assertions.assertNotEquals(-1, optionIndex1);
		Assertions.assertEquals(optionIndex1 + 1, result.indexOf(formattedFile1));
		Assertions.assertEquals(optionIndex1 + 2, result.indexOf(path1));
		int optionIndex2 = optionIndex1 + 1 + result.subList(optionIndex1 + 1, result.size()).indexOf("-e");
		Assertions.assertEquals(optionIndex1 + 3, optionIndex2);
		Assertions.assertEquals(optionIndex2 + 1, result.indexOf(file2));
		Assertions.assertEquals(optionIndex2 + 2, result.indexOf(path2));
		int optionIndex3 = optionIndex2 + 1 + result.subList(optionIndex2 + 1, result.size()).indexOf("-e");
		Assertions.assertEquals(optionIndex2 + 3, optionIndex3);
		Assertions.assertEquals(optionIndex3 + 1, result.indexOf(file3));
		Assertions.assertEquals(optionIndex3 + 2, result.indexOf(path3));
	}

	@Test
	void testHideAneLibSymbols() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode ios = JsonNodeFactory.instance.objectNode();
		ios.set(AIROptions.HIDE_ANE_LIB_SYMBOLS, JsonNodeFactory.instance.booleanNode(value));
		options.set(AIRPlatform.IOS, ios);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.IOS, false, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIROptions.HIDE_ANE_LIB_SYMBOLS);
		Assertions.assertNotEquals(-1, optionIndex);
		Assertions.assertEquals(optionIndex + 1, result.indexOf(value ? "yes" : "no"));
	}

	@Test
	void testOutput() {
		String value = "path/to/file.air";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(AIROptions.OUTPUT, JsonNodeFactory.instance.textNode(value));
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.AIR, false, value, "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIROptions.OUTPUT);
		Assertions.assertEquals(-1, optionIndex);
		Assertions.assertNotEquals(-1, result.indexOf(value));
	}

	@Test
	void testAndroidOutput() {
		String androidValue = "path/to/file.apk";
		String iOSValue = "path/to/file.ipa";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode android = JsonNodeFactory.instance.objectNode();
		android.set(AIROptions.OUTPUT, JsonNodeFactory.instance.textNode(androidValue));
		options.set(AIRPlatform.ANDROID, android);
		ObjectNode ios = JsonNodeFactory.instance.objectNode();
		ios.set(AIROptions.OUTPUT, JsonNodeFactory.instance.textNode(iOSValue));
		options.set(AIRPlatform.IOS, ios);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, false, "application.xml", "content.swf", options, result);
		Assertions.assertNotEquals(-1, result.indexOf(androidValue));
		Assertions.assertEquals(-1, result.indexOf(iOSValue));
	}

	@Test
	void testIOSOutput() {
		String androidValue = "path/to/file.apk";
		String iOSValue = "path/to/file.ipa";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode android = JsonNodeFactory.instance.objectNode();
		android.set(AIROptions.OUTPUT, JsonNodeFactory.instance.textNode(androidValue));
		options.set(AIRPlatform.ANDROID, android);
		ObjectNode ios = JsonNodeFactory.instance.objectNode();
		ios.set(AIROptions.OUTPUT, JsonNodeFactory.instance.textNode(iOSValue));
		options.set(AIRPlatform.IOS, ios);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.IOS, false, "application.xml", "content.swf", options, result);
		Assertions.assertNotEquals(-1, result.indexOf(iOSValue));
		Assertions.assertEquals(-1, result.indexOf(androidValue));
	}

	@Test
	void testAndroidPlatformSDK() {
		String androidValue = "path/to/android_sdk";
		String iOSValue = "path/to/ios_sdk";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode android = JsonNodeFactory.instance.objectNode();
		android.set(AIROptions.PLATFORMSDK, JsonNodeFactory.instance.textNode(androidValue));
		options.set(AIRPlatform.ANDROID, android);
		ObjectNode ios = JsonNodeFactory.instance.objectNode();
		ios.set(AIROptions.PLATFORMSDK, JsonNodeFactory.instance.textNode(iOSValue));
		options.set(AIRPlatform.IOS, ios);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, false, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIROptions.PLATFORMSDK);
		Assertions.assertNotEquals(-1, optionIndex);
		Assertions.assertEquals(optionIndex + 1, result.indexOf(androidValue));
		Assertions.assertEquals(-1, result.indexOf(iOSValue));
	}

	@Test
	void testIOSPlatformSDK() {
		String androidValue = "path/to/android_sdk";
		String iOSValue = "path/to/ios_sdk";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode android = JsonNodeFactory.instance.objectNode();
		android.set(AIROptions.PLATFORMSDK, JsonNodeFactory.instance.textNode(androidValue));
		options.set(AIRPlatform.ANDROID, android);
		ObjectNode ios = JsonNodeFactory.instance.objectNode();
		ios.set(AIROptions.PLATFORMSDK, JsonNodeFactory.instance.textNode(iOSValue));
		options.set(AIRPlatform.IOS, ios);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.IOS, false, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIROptions.PLATFORMSDK);
		Assertions.assertNotEquals(-1, optionIndex);
		Assertions.assertEquals(optionIndex + 1, result.indexOf(iOSValue));
		Assertions.assertEquals(-1, result.indexOf(androidValue));
	}

	@Test
	void testSampler() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode ios = JsonNodeFactory.instance.objectNode();
		ios.set(AIROptions.SAMPLER, JsonNodeFactory.instance.booleanNode(value));
		options.set(AIRPlatform.IOS, ios);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.IOS, false, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIROptions.SAMPLER);
		Assertions.assertNotEquals(-1, optionIndex);
	}

	@Test
	void testTarget() {
		String value = AIRTarget.NATIVE;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		options.set(AIROptions.TARGET, JsonNodeFactory.instance.textNode(value));
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.AIR, false, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIROptions.TARGET);
		Assertions.assertNotEquals(-1, optionIndex);
		Assertions.assertEquals(optionIndex + 1, result.indexOf(value));
	}

	@Test
	void testIOSTarget() {
		String androidValue = AIRTarget.APK_CAPTIVE_RUNTIME;
		String iOSValue = AIRTarget.IPA_AD_HOC;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode android = JsonNodeFactory.instance.objectNode();
		android.set(AIROptions.TARGET, JsonNodeFactory.instance.textNode(androidValue));
		options.set(AIRPlatform.ANDROID, android);
		ObjectNode ios = JsonNodeFactory.instance.objectNode();
		ios.set(AIROptions.TARGET, JsonNodeFactory.instance.textNode(iOSValue));
		options.set(AIRPlatform.IOS, ios);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.IOS, false, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIROptions.TARGET);
		Assertions.assertNotEquals(-1, optionIndex);
		Assertions.assertEquals(optionIndex + 1, result.indexOf(iOSValue));
		Assertions.assertEquals(-1, result.indexOf(androidValue));
	}

	@Test
	void testAndroidTarget() {
		String androidValue = AIRTarget.APK_CAPTIVE_RUNTIME;
		String iOSValue = AIRTarget.IPA_AD_HOC;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode android = JsonNodeFactory.instance.objectNode();
		android.set(AIROptions.TARGET, JsonNodeFactory.instance.textNode(androidValue));
		options.set(AIRPlatform.ANDROID, android);
		ObjectNode ios = JsonNodeFactory.instance.objectNode();
		ios.set(AIROptions.TARGET, JsonNodeFactory.instance.textNode(iOSValue));
		options.set(AIRPlatform.IOS, ios);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, false, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIROptions.TARGET);
		Assertions.assertNotEquals(-1, optionIndex);
		Assertions.assertEquals(optionIndex + 1, result.indexOf(androidValue));
		Assertions.assertEquals(-1, result.indexOf(iOSValue));
	}

	//----- signing options

	@Test
	void testSigningOptionsAlias() {
		String value = "AIRCert";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode signingOptions = JsonNodeFactory.instance.objectNode();
		signingOptions.set(AIRSigningOptions.ALIAS, JsonNodeFactory.instance.textNode(value));
		options.set(AIROptions.SIGNING_OPTIONS, signingOptions);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.AIR, false, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIRSigningOptions.ALIAS);
		Assertions.assertNotEquals(-1, optionIndex);
		Assertions.assertEquals(optionIndex + 1, result.indexOf(value));
	}

	@Test
	void testSigningOptionsKeystore() {
		String value = "path/to/keystore.p12";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode signingOptions = JsonNodeFactory.instance.objectNode();
		signingOptions.set(AIRSigningOptions.KEYSTORE, JsonNodeFactory.instance.textNode(value));
		options.set(AIROptions.SIGNING_OPTIONS, signingOptions);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.AIR, false, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIRSigningOptions.KEYSTORE);
		Assertions.assertNotEquals(-1, optionIndex);
		Assertions.assertEquals(optionIndex + 1, result.indexOf(value));
	}

	@Test
	void testSigningOptionsProviderName() {
		String value = "className";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode signingOptions = JsonNodeFactory.instance.objectNode();
		signingOptions.set(AIRSigningOptions.PROVIDER_NAME, JsonNodeFactory.instance.textNode(value));
		options.set(AIROptions.SIGNING_OPTIONS, signingOptions);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.AIR, false, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIRSigningOptions.PROVIDER_NAME);
		Assertions.assertNotEquals(-1, optionIndex);
		Assertions.assertEquals(optionIndex + 1, result.indexOf(value));
	}

	@Test
	void testSigningOptionsTsa() {
		String value = "none";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode signingOptions = JsonNodeFactory.instance.objectNode();
		signingOptions.set(AIRSigningOptions.TSA, JsonNodeFactory.instance.textNode(value));
		options.set(AIROptions.SIGNING_OPTIONS, signingOptions);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.AIR, false, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIRSigningOptions.TSA);
		Assertions.assertNotEquals(-1, optionIndex);
		Assertions.assertEquals(optionIndex + 1, result.indexOf(value));
	}

	@Test
	void testSigningOptionsProvisioningProfile() {
		String value = "path/to/file.mobileprovision";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode signingOptions = JsonNodeFactory.instance.objectNode();
		signingOptions.set(AIRSigningOptions.PROVISIONING_PROFILE, JsonNodeFactory.instance.textNode(value));
		options.set(AIROptions.SIGNING_OPTIONS, signingOptions);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.AIR, false, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIRSigningOptions.PROVISIONING_PROFILE);
		Assertions.assertNotEquals(-1, optionIndex);
		Assertions.assertEquals(optionIndex + 1, result.indexOf(value));
	}

	//----- overrides

	@Test
	void testOutputPlatformOverride() {
		String androidValue = "path/to/file.apk";
		String defaultValue = "path/to/file.air";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode android = JsonNodeFactory.instance.objectNode();
		android.set(AIROptions.OUTPUT, JsonNodeFactory.instance.textNode(androidValue));
		options.set(AIRPlatform.ANDROID, android);
		options.set(AIROptions.OUTPUT, JsonNodeFactory.instance.textNode(defaultValue));
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, false, "application.xml", "content.swf", options, result);
		Assertions.assertNotEquals(-1, result.indexOf(androidValue));
		Assertions.assertEquals(-1, result.indexOf(defaultValue));
	}

	@Test
	void testTargetPlatformOverride() {
		String androidValue = AIRTarget.APK_CAPTIVE_RUNTIME;
		String defaultValue = AIRTarget.NATIVE;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode android = JsonNodeFactory.instance.objectNode();
		android.set(AIROptions.TARGET, JsonNodeFactory.instance.textNode(androidValue));
		options.set(AIRPlatform.ANDROID, android);
		options.set(AIROptions.TARGET, JsonNodeFactory.instance.textNode(defaultValue));
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, false, "application.xml", "content.swf", options, result);
		Assertions.assertNotEquals(-1, result.indexOf(androidValue));
		Assertions.assertEquals(-1, result.indexOf(defaultValue));
	}

	@Test
	void testExtdirPlatformOverride() {
		String androidValue1 = "path/to/android/subpath1";
		String androidValue2 = "path/to/android/subpath2";
		String defaultValue1 = "path/to/subpath1";
		String defaultValue2 = "path/to/subpath2";

		ObjectNode options = JsonNodeFactory.instance.objectNode();

		ObjectNode android = JsonNodeFactory.instance.objectNode();
		ArrayNode androidExtdir = JsonNodeFactory.instance.arrayNode();
		androidExtdir.add(androidValue1);
		androidExtdir.add(androidValue2);
		android.set(AIROptions.EXTDIR, androidExtdir);
		options.set(AIRPlatform.ANDROID, android);

		ArrayNode defaultExtdir = JsonNodeFactory.instance.arrayNode();
		defaultExtdir.add(defaultValue1);
		defaultExtdir.add(defaultValue2);
		options.set(AIROptions.EXTDIR, defaultExtdir);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, false, "application.xml", "content.swf", options, result);
		int optionIndex1 = result.indexOf("-" + AIROptions.EXTDIR);
		Assertions.assertNotEquals(-1, optionIndex1);
		Assertions.assertEquals(optionIndex1 + 1, result.indexOf(androidValue1));
		int optionIndex2 = optionIndex1 + 1
				+ result.subList(optionIndex1 + 1, result.size()).indexOf("-" + AIROptions.EXTDIR);
		Assertions.assertEquals(optionIndex1 + 2, optionIndex2);
		Assertions.assertEquals(optionIndex2 + 1, result.indexOf(androidValue2));
	}

	@Test
	void testSigningOptionsPlatformOverride() {
		String androidKeystore = "path/to/android/subpath1";
		String androidStoretype = "pkcs12";
		String defaultKeystore = "path/to/keystore.p12";
		String defaultStoretype = "pkcs12";
		String defaultTsa = "none";

		ObjectNode options = JsonNodeFactory.instance.objectNode();

		ObjectNode android = JsonNodeFactory.instance.objectNode();
		ObjectNode androidSigningOptions = JsonNodeFactory.instance.objectNode();
		androidSigningOptions.set(AIRSigningOptions.KEYSTORE, JsonNodeFactory.instance.textNode(androidKeystore));
		androidSigningOptions.set(AIRSigningOptions.STORETYPE, JsonNodeFactory.instance.textNode(androidStoretype));
		android.set(AIROptions.SIGNING_OPTIONS, androidSigningOptions);
		options.set(AIRPlatform.ANDROID, android);

		ObjectNode defaultSigningOptions = JsonNodeFactory.instance.objectNode();
		defaultSigningOptions.set(AIRSigningOptions.KEYSTORE, JsonNodeFactory.instance.textNode(defaultKeystore));
		defaultSigningOptions.set(AIRSigningOptions.STORETYPE, JsonNodeFactory.instance.textNode(defaultStoretype));
		defaultSigningOptions.set(AIRSigningOptions.TSA, JsonNodeFactory.instance.textNode(defaultTsa));
		options.set(AIROptions.SIGNING_OPTIONS, defaultSigningOptions);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, false, "application.xml", "content.swf", options, result);
		int optionIndex1 = result.indexOf("-" + AIRSigningOptions.STORETYPE);
		Assertions.assertNotEquals(-1, optionIndex1);
		Assertions.assertEquals(optionIndex1 + 1, result.indexOf(androidStoretype));
		int optionIndex2 = optionIndex1 + 1
				+ result.subList(optionIndex1 + 1, result.size()).indexOf("-" + AIRSigningOptions.KEYSTORE);
		Assertions.assertEquals(optionIndex1 + 2, optionIndex2);
		Assertions.assertEquals(optionIndex2 + 1, result.indexOf(androidKeystore));
		Assertions.assertEquals(-1, result.indexOf("-" + AIRSigningOptions.TSA));
		Assertions.assertEquals(-1, result.indexOf(defaultTsa));
	}

	//----- debug vs release

	@Test
	void testSigningOptionsDebug() {
		String debugKeystore = "path/to/debug_keystore.p12";
		String debugStoretype = "pkcs12";
		String releaseKeystore = "path/to/keystore.keystore";
		String releaseStoretype = "jks";

		ObjectNode options = JsonNodeFactory.instance.objectNode();

		ObjectNode signingOptions = JsonNodeFactory.instance.objectNode();

		ObjectNode debugSigningOptions = JsonNodeFactory.instance.objectNode();
		debugSigningOptions.set(AIRSigningOptions.KEYSTORE, JsonNodeFactory.instance.textNode(debugKeystore));
		debugSigningOptions.set(AIRSigningOptions.STORETYPE, JsonNodeFactory.instance.textNode(debugStoretype));
		signingOptions.set("debug", debugSigningOptions);

		ObjectNode releaseSigningOptions = JsonNodeFactory.instance.objectNode();
		releaseSigningOptions.set(AIRSigningOptions.KEYSTORE, JsonNodeFactory.instance.textNode(releaseKeystore));
		releaseSigningOptions.set(AIRSigningOptions.STORETYPE, JsonNodeFactory.instance.textNode(releaseStoretype));
		signingOptions.set("release", releaseSigningOptions);

		options.set(AIROptions.SIGNING_OPTIONS, signingOptions);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, true, "application.xml", "content.swf", options, result);
		int optionIndex1 = result.indexOf("-" + AIRSigningOptions.STORETYPE);
		Assertions.assertNotEquals(-1, optionIndex1);
		Assertions.assertEquals(optionIndex1 + 1, result.indexOf(debugStoretype));
		int optionIndex2 = optionIndex1 + 1
				+ result.subList(optionIndex1 + 1, result.size()).indexOf("-" + AIRSigningOptions.KEYSTORE);
		Assertions.assertEquals(optionIndex1 + 2, optionIndex2);
		Assertions.assertEquals(optionIndex2 + 1, result.indexOf(debugKeystore));
	}

	@Test
	void testSigningOptionsRelease() {
		String debugKeystore = "path/to/debug_keystore.p12";
		String debugStoretype = "pkcs12";
		String releaseKeystore = "path/to/keystore.keystore";
		String releaseStoretype = "jks";

		ObjectNode options = JsonNodeFactory.instance.objectNode();

		ObjectNode signingOptions = JsonNodeFactory.instance.objectNode();

		ObjectNode debugSigningOptions = JsonNodeFactory.instance.objectNode();
		debugSigningOptions.set(AIRSigningOptions.KEYSTORE, JsonNodeFactory.instance.textNode(debugKeystore));
		debugSigningOptions.set(AIRSigningOptions.STORETYPE, JsonNodeFactory.instance.textNode(debugStoretype));
		signingOptions.set("debug", debugSigningOptions);

		ObjectNode releaseSigningOptions = JsonNodeFactory.instance.objectNode();
		releaseSigningOptions.set(AIRSigningOptions.KEYSTORE, JsonNodeFactory.instance.textNode(releaseKeystore));
		releaseSigningOptions.set(AIRSigningOptions.STORETYPE, JsonNodeFactory.instance.textNode(releaseStoretype));
		signingOptions.set("release", releaseSigningOptions);

		options.set(AIROptions.SIGNING_OPTIONS, signingOptions);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, false, "application.xml", "content.swf", options, result);
		int optionIndex1 = result.indexOf("-" + AIRSigningOptions.STORETYPE);
		Assertions.assertNotEquals(-1, optionIndex1);
		Assertions.assertEquals(optionIndex1 + 1, result.indexOf(releaseStoretype));
		int optionIndex2 = optionIndex1 + 1
				+ result.subList(optionIndex1 + 1, result.size()).indexOf("-" + AIRSigningOptions.KEYSTORE);
		Assertions.assertEquals(optionIndex1 + 2, optionIndex2);
		Assertions.assertEquals(optionIndex2 + 1, result.indexOf(releaseKeystore));
	}

	@Test
	void testConnectDebugAndroid() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode android = JsonNodeFactory.instance.objectNode();
		android.set(AIROptions.CONNECT, JsonNodeFactory.instance.booleanNode(value));
		options.set(AIRPlatform.ANDROID, android);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, true, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIROptions.CONNECT);
		Assertions.assertNotEquals(-1, optionIndex);
	}

	@Test
	void testConnectHostStringDebugAndroid() {
		String value = "192.168.1.100";
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode android = JsonNodeFactory.instance.objectNode();
		android.set(AIROptions.CONNECT, JsonNodeFactory.instance.textNode(value));
		options.set(AIRPlatform.ANDROID, android);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, true, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIROptions.CONNECT);
		Assertions.assertNotEquals(-1, optionIndex);
		Assertions.assertEquals(optionIndex + 1, result.indexOf(value));
	}

	@Test
	void testNoConnectDebugAndroid() {
		boolean value = false;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode android = JsonNodeFactory.instance.objectNode();
		android.set(AIROptions.CONNECT, JsonNodeFactory.instance.booleanNode(value));
		options.set(AIRPlatform.ANDROID, android);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, true, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIROptions.CONNECT);
		Assertions.assertEquals(-1, optionIndex);
	}

	@Test
	void testConnectReleaseAndroid() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode android = JsonNodeFactory.instance.objectNode();
		android.set(AIROptions.CONNECT, JsonNodeFactory.instance.booleanNode(value));
		options.set(AIRPlatform.ANDROID, android);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, false, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIROptions.CONNECT);
		Assertions.assertEquals(-1, optionIndex);
	}

	@Test
	void testListenDebugAndroid() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode android = JsonNodeFactory.instance.objectNode();
		android.set(AIROptions.LISTEN, JsonNodeFactory.instance.booleanNode(value));
		options.set(AIRPlatform.ANDROID, android);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, true, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIROptions.LISTEN);
		Assertions.assertNotEquals(-1, optionIndex);
	}

	@Test
	void testListenPortDebugAndroid() {
		int value = 9000;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode android = JsonNodeFactory.instance.objectNode();
		android.set(AIROptions.LISTEN, JsonNodeFactory.instance.numberNode(value));
		options.set(AIRPlatform.ANDROID, android);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, true, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIROptions.LISTEN);
		Assertions.assertNotEquals(-1, optionIndex);
		Assertions.assertEquals(optionIndex + 1, result.indexOf(Integer.toString(value)));
	}

	@Test
	void testNoListenDebugAndroid() {
		boolean value = false;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode android = JsonNodeFactory.instance.objectNode();
		android.set(AIROptions.LISTEN, JsonNodeFactory.instance.booleanNode(value));
		options.set(AIRPlatform.ANDROID, android);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, true, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIROptions.LISTEN);
		Assertions.assertEquals(-1, optionIndex);
	}

	@Test
	void testListenReleaseAndroid() {
		boolean value = true;
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode android = JsonNodeFactory.instance.objectNode();
		android.set(AIROptions.LISTEN, JsonNodeFactory.instance.booleanNode(value));
		options.set(AIRPlatform.ANDROID, android);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, false, "application.xml", "content.swf", options, result);
		int optionIndex = result.indexOf("-" + AIROptions.LISTEN);
		Assertions.assertEquals(-1, optionIndex);
	}

	@Test
	void testConnectListenDebugDefaultsAndroid() {
		ObjectNode options = JsonNodeFactory.instance.objectNode();
		ObjectNode android = JsonNodeFactory.instance.objectNode();
		options.set(AIRPlatform.ANDROID, android);
		ArrayList<String> result = new ArrayList<>();
		parser.parse(AIRPlatform.ANDROID, true, "application.xml", "content.swf", options, result);
		int optionIndex1 = result.indexOf("-" + AIROptions.CONNECT);
		Assertions.assertNotEquals(-1, optionIndex1);
		int optionIndex2 = result.indexOf("-" + AIROptions.LISTEN);
		Assertions.assertEquals(-1, optionIndex2);
	}
}