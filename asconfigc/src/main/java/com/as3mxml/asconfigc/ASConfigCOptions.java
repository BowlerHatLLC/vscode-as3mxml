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
package com.as3mxml.asconfigc;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.as3mxml.asconfigc.air.AIRPlatform;
import com.as3mxml.asconfigc.compiler.DefaultCompiler;
import com.as3mxml.asconfigc.compiler.IASConfigCCompiler;

import org.apache.commons.cli.CommandLine;

public class ASConfigCOptions {
	private static final String OPTION_PROJECT = "p"; // CommandLine uses the short name
	private static final String OPTION_SDK = "sdk";
	private static final String OPTION_DEBUG = "debug";
	private static final String OPTION_AIR = "air";
	private static final String OPTION_STOREPASS = "storepass";
	private static final String OPTION_UNPACKAGE_ANES = "unpackage-anes";
	private static final String OPTION_CLEAN = "clean";
	private static final String OPTION_WATCH = "watch";
	private static final String OPTION_ANIMATE = "animate";
	private static final String OPTION_PUBLISH_ANIMATE = "publish-animate";
	private static final String OPTION_VERBOSE = "verbose";
	private static final String OPTION_JVMARGS = "jvmargs";
	private static final String OPTION_PRINT_CONFIG = "print-config";

	public String project = null;
	public String sdk = null;
	public Boolean debug = null;
	public String air = null;
	public boolean unpackageANEs = false;
	public String storepass = null;
	public Boolean clean = null;
	public Boolean watch = null;
	public IASConfigCCompiler compiler = null;
	public String animate = null;
	public Boolean publishAnimate = null;
	public boolean verbose = false;
	public List<String> jvmargs = null;
	public boolean printConfig = false;
	public List<String> tokens = null;

	public ASConfigCOptions(String project, String sdk, Boolean debug, String air, String storepass,
			Boolean unpackageANEs, IASConfigCCompiler compiler) {
		this.project = project;
		this.sdk = sdk;
		this.debug = debug;
		this.air = air;
		this.unpackageANEs = unpackageANEs;
		this.compiler = compiler;
	}

	public ASConfigCOptions(CommandLine line) {
		if (line.hasOption(OPTION_PROJECT)) {
			project = line.getOptionValue(OPTION_PROJECT, null);
		}
		if (line.hasOption(OPTION_SDK)) {
			sdk = line.getOptionValue(OPTION_SDK, null);
		}
		if (line.hasOption(OPTION_DEBUG)) {
			String debugString = line.getOptionValue(OPTION_DEBUG, Boolean.TRUE.toString());
			debug = debugString.equals(Boolean.TRUE.toString());
		}
		if (line.hasOption(OPTION_AIR)) {
			air = line.getOptionValue(OPTION_AIR, "air");
			if (air.equals("bundle")) {
				String osName = System.getProperty("os.name").toLowerCase();
				if (osName.startsWith("mac")) {
					air = AIRPlatform.MAC;
				} else if (osName.startsWith("windows")) {
					air = AIRPlatform.WINDOWS;
				} else {
					throw new Error(
							"Adobe AIR target \"bundle\" specified, but current operating system not recognized: "
									+ System.getProperty("os.name"));
				}
			}
		}
		if (line.hasOption(OPTION_STOREPASS)) {
			storepass = line.getOptionValue(OPTION_STOREPASS, null);
		}
		if (line.hasOption(OPTION_UNPACKAGE_ANES)) {
			String unpackageString = line.getOptionValue(OPTION_UNPACKAGE_ANES, Boolean.TRUE.toString());
			unpackageANEs = unpackageString.equals(Boolean.TRUE.toString());
		}
		if (line.hasOption(OPTION_CLEAN)) {
			String cleanString = line.getOptionValue(OPTION_CLEAN, Boolean.TRUE.toString());
			clean = cleanString.equals(Boolean.TRUE.toString());
		}
		if (line.hasOption(OPTION_WATCH)) {
			String watchString = line.getOptionValue(OPTION_WATCH, Boolean.TRUE.toString());
			watch = watchString.equals(Boolean.TRUE.toString());
		}
		if (line.hasOption(OPTION_ANIMATE)) {
			animate = line.getOptionValue(OPTION_ANIMATE, null);
		}
		if (line.hasOption(OPTION_PUBLISH_ANIMATE)) {
			String publishString = line.getOptionValue(OPTION_PUBLISH_ANIMATE, Boolean.TRUE.toString());
			publishAnimate = publishString.equals(Boolean.TRUE.toString());
		}
		if (line.hasOption(OPTION_VERBOSE)) {
			String verboseString = line.getOptionValue(OPTION_VERBOSE, Boolean.FALSE.toString());
			verbose = verboseString.equals(Boolean.TRUE.toString());
		}
		if (line.hasOption(OPTION_JVMARGS)) {
			String argsString = line.getOptionValue(OPTION_JVMARGS, null);
			if (argsString != null) {
				if (argsString.startsWith("\"") && argsString.endsWith("\"")) {
					argsString = argsString.substring(1, argsString.length() - 1);
				}
				String[] argsArray = argsString.split(" ");
				jvmargs = Arrays.stream(argsArray).collect(Collectors.toList());
			}
		}
		if (line.hasOption(OPTION_PRINT_CONFIG)) {
			String printConfigString = line.getOptionValue(OPTION_PRINT_CONFIG, Boolean.FALSE.toString());
			printConfig = printConfigString.equals(Boolean.TRUE.toString());
		}
		if (line.getArgList().size() != 0) {
			tokens = line.getArgList().stream().filter((v) -> {
				return v.startsWith("+");
			}).collect(Collectors.toList());
		}
		compiler = new DefaultCompiler(verbose, jvmargs, tokens);
	}
}
