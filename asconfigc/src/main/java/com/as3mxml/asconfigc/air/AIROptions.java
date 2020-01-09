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
package com.as3mxml.asconfigc.air;

public class AIROptions
{
	public static final String EXTDIR = "extdir";
	public static final String FILES = "files";
	public static final String OUTPUT = "output";
	public static final String PACKAGE = "package";
	public static final String SIGNING_OPTIONS = "signingOptions";
	public static final String TARGET = "target";

	public static final String PLATFORMSDK = "platformsdk";

	public static final String CONNECT = "connect";
	public static final String LISTEN = "listen";

	//ios
	public static final String SAMPLER = "sampler";
	public static final String HIDE_ANE_LIB_SYMBOLS = "hideAneLibSymbols";
	public static final String EMBED_BITCODE = "embedBitcode";

	//android
	public static final String AIR_DOWNLOAD_URL = "airDownloadURL";
	public static final String ARCH = "arch";
	
	//sub-values
	public static final String FILES__FILE = "file";
	public static final String FILES__PATH = "path";
}