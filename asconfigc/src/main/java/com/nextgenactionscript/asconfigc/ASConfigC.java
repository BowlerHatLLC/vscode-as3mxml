/*
Copyright 2016-2017 Bowler Hat LLC

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
package com.nextgenactionscript.asconfigc;

import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.UnrecognizedOptionException;

/**
 * Parses asconfig.json and executes the compiler with the specified options.
 * Can also, optionally, run adt (the AIR Developer Tool) to package an Adobe
 * AIR application.
 */
public class ASConfigC
{
	public static void main(String[] args)
	{
		CommandLineParser parser = new DefaultParser();

		Options options = new Options();
		options.addOption(new Option("h", "help", false, "Print this help message."));
		options.addOption(new Option("v", "version", false, "Print the version."));
		Option projectOption = new Option("p", "project", true, "Compile a project with the path to its configuration file or a directory containing asconfig.json. If omitted, will look for asconfig.json in current directory.");
		projectOption.setArgName("FILE OR DIRECTORY");
		options.addOption(projectOption);
		Option sdkOption = new Option(null, "sdk", true, "Specify the directory where the ActionScript SDK is located. If omitted, defaults to checking FLEX_HOME and PATH environment variables.");
		sdkOption.setArgName("DIRECTORY");
		options.addOption(sdkOption);
		Option debugOption = new Option(null, "debug", true, "Specify debug or release mode. Overrides the debug compiler option, if specified in asconfig.json.");
		debugOption.setArgName("true OR false");
		debugOption.setOptionalArg(true);
		options.addOption(debugOption);
		Option airOption = new Option(null, "air", true, "Package the project as an Adobe AIR application. The allowed platforms include `android`, `ios`, `windows`, `mac`, and `air`.");
		airOption.setArgName("PLATFORM");
		airOption.setOptionalArg(true);
		options.addOption(airOption);

		ASConfigCOptions asconfigcOptions = null;
		try
		{
			CommandLine line = parser.parse(options, args);
			if(line.hasOption("h"))
			{
				String syntax = "asconfigc [options]\n\n" +
								"Examples: asconfigc -p .\n" +
								"          asconfigc -p path/to/custom.json\n\n" +
								"Options:";
				HelpFormatter formatter = new HelpFormatter();
				formatter.setSyntaxPrefix("Syntax:   ");
				formatter.printHelp(syntax, options);
				System.exit(0);
			}
			if(line.hasOption("v"))
			{
				String version = ASConfigC.class.getPackage().getImplementationVersion();
				System.out.println("Version: " + version);
				System.exit(0);
			}
			asconfigcOptions = new ASConfigCOptions(line);
		}
		catch(UnrecognizedOptionException e)
		{
			System.err.println("Unknown asconfigc option: " + e.getOption());
			System.exit(1);
		}
		catch(ParseException e)
		{
			System.err.println("Failed to parse asconfigc options.");
			e.printStackTrace(System.err);
			System.exit(1);
		}

		try
		{
			new ASConfigC(asconfigcOptions);
		}
		catch(ConfigurationException e)
		{
			System.err.println("Failed to parse asconfigc options.");
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

	private static final String ASCONFIG_JSON = "asconfig.json";

	public ASConfigC(ASConfigCOptions options) throws ConfigurationException
	{
		File configFile = findConfigurationFile(options.project);

		//the current working directory must be where asconfig.json is located
		System.setProperty("user.dir", configFile.getParent());

		parseConfig();
		validateSDK();
		compileProject();
		copySourcePathAssets();
		processAdobeAIRDescriptor();
		packageAIR();
	}

	private File findConfigurationFile(String projectPath) throws ConfigurationException
	{
		File configFile = null;
		if(projectPath != null)
		{
			configFile = new File(projectPath);
		}
		else
		{
			configFile = new File(System.getProperty("user.dir"));
		}
		if(!configFile.exists())
		{
			throw new ConfigurationException("Project directory or JSON file not found: " + projectPath);
		}
		if(configFile.isDirectory())
		{
			configFile = new File(configFile, ASCONFIG_JSON);
			if(!configFile.exists())
			{
				throw new ConfigurationException("asconfig.json not found in directory: " + projectPath);
			}
		}
		return configFile;
	}

	private void parseConfig()
	{

	}

	private void validateSDK()
	{
		
	}
	
	private void compileProject()
	{
		
	}
	
	private void copySourcePathAssets()
	{
		
	}
	
	private void processAdobeAIRDescriptor()
	{
		
	}
	
	private void packageAIR()
	{
		
	}
}