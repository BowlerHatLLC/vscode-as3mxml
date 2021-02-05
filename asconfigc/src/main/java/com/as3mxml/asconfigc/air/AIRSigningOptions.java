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

public class AIRSigningOptions {
	public static final String ALIAS = "alias";
	public static final String STORETYPE = "storetype";
	public static final String STOREPASS = "storepass";
	public static final String KEYSTORE = "keystore";
	public static final String PROVIDER_NAME = "providerName";
	public static final String TSA = "tsa";
	public static final String PROVISIONING_PROFILE = "provisioning-profile";

	//these aren't real options, but they might exist to provide separate
	//options for debug and release builds
	public static final String DEBUG = "debug";
	public static final String RELEASE = "release";
}