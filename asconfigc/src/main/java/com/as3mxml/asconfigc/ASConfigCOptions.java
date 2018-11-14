/*
Copyright 2016-2018 Bowler Hat LLC

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

import com.as3mxml.asconfigc.compiler.DefaultCompiler;
import com.as3mxml.asconfigc.compiler.IASConfigCCompiler;

import org.apache.commons.cli.CommandLine;

public class ASConfigCOptions
{
	private static final String OPTION_PROJECT = "p"; //CommandLine uses the short name
	private static final String OPTION_SDK = "sdk";
	private static final String OPTION_DEBUG = "debug";
	private static final String OPTION_AIR = "air";
	private static final String OPTION_CLEAN = "clean";

	public String project = null;
	public String sdk = null;
	public Boolean debug = null;
	public String air = null;
	public Boolean clean = null;
	public IASConfigCCompiler compiler = null;

	public ASConfigCOptions(String project, String sdk, Boolean debug, String air, IASConfigCCompiler compiler)
	{
		this.project = project;
		this.sdk = sdk;
		this.debug = debug;
		this.air = air;
		this.compiler = compiler;
	}

	public ASConfigCOptions(CommandLine line)
	{
		if(line.hasOption(OPTION_PROJECT))
		{
			project = line.getOptionValue(OPTION_PROJECT, null);
		}
		if(line.hasOption(OPTION_SDK))
		{
			sdk = line.getOptionValue(OPTION_SDK, null);
		}
		if(line.hasOption(OPTION_DEBUG))
		{
			String debugString = line.getOptionValue(OPTION_DEBUG, Boolean.TRUE.toString());
			debug = debugString.equals(Boolean.TRUE.toString());
		}
		if(line.hasOption(OPTION_AIR))
		{
			air = line.getOptionValue(OPTION_AIR, "air");
		}
		if(line.hasOption(OPTION_CLEAN))
		{
			String cleanString = line.getOptionValue(OPTION_CLEAN, Boolean.TRUE.toString());
			clean = cleanString.equals(Boolean.TRUE.toString());
		}
		compiler = new DefaultCompiler();
	}
}