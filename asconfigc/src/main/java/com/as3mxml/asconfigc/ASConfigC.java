/*
Copyright 2016-2024 Bowler Hat LLC

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

import java.io.BufferedInputStream;
import java.io.Console;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.UnrecognizedOptionException;

import com.as3mxml.asconfigc.air.AIROptions;
import com.as3mxml.asconfigc.air.AIROptionsParser;
import com.as3mxml.asconfigc.air.AIRSigningOptions;
import com.as3mxml.asconfigc.animate.AnimateOptions;
import com.as3mxml.asconfigc.compiler.CompilerOptions;
import com.as3mxml.asconfigc.compiler.CompilerOptionsParser;
import com.as3mxml.asconfigc.compiler.ConfigName;
import com.as3mxml.asconfigc.compiler.ModuleFields;
import com.as3mxml.asconfigc.compiler.ProjectType;
import com.as3mxml.asconfigc.compiler.RoyaleTarget;
import com.as3mxml.asconfigc.compiler.WorkerFields;
import com.as3mxml.asconfigc.htmlTemplate.HTMLTemplateOptionsParser;
import com.as3mxml.asconfigc.utils.ApacheRoyaleUtils;
import com.as3mxml.asconfigc.utils.ConfigUtils;
import com.as3mxml.asconfigc.utils.GenericSDKUtils;
import com.as3mxml.asconfigc.utils.JsonUtils;
import com.as3mxml.asconfigc.utils.OptionsFormatter;
import com.as3mxml.asconfigc.utils.OptionsUtils;
import com.as3mxml.asconfigc.utils.ProjectUtils;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.ValidationMessage;

/**
 * Parses asconfig.json and executes the compiler with the specified options.
 * Can also, optionally, run adt (the AIR Developer Tool) to package an Adobe
 * AIR application.
 */
public class ASConfigC {
	private static final String FILE_EXTENSION_AS = ".as";
	private static final String FILE_EXTENSION_MXML = ".mxml";
	private static final String FILE_EXTENSION_ANE = ".ane";
	private static final String FILE_NAME_UNPACKAGED_ANES = ".as3mxml-unpackaged-anes";
	private static final String FILE_NAME_ANIMATE_PUBLISH_LOG = "AnimateDocument.log";
	private static final String FILE_NAME_ANIMATE_ERROR_LOG = "AnimateErrors.log";
	private static final String FILE_NAME_BIN_JS_DEBUG = "bin/js-debug";
	private static final String FILE_NAME_BIN_JS_RELEASE = "bin/js-release";
	private static final Pattern COMPILER_OPTION_OUTPUT_PATTERN = Pattern.compile("^-{1,2}output\\b");

	public static void main(String[] args) {
		CommandLineParser parser = new DefaultParser();

		Options options = new Options();
		options.addOption(new Option("h", "help", false, "Print this help message."));
		options.addOption(new Option("v", "version", false, "Print the version."));
		Option projectOption = new Option("p", "project", true,
				"Compile a project with the path to its configuration file or a directory containing asconfig.json. If omitted, will look for asconfig.json in current directory.");
		projectOption.setArgName("FILE OR DIRECTORY");
		options.addOption(projectOption);
		Option sdkOption = new Option(null, "sdk", true,
				"Specify the directory where the ActionScript SDK is located. If omitted, defaults to checking ROYALE_HOME, FLEX_HOME and PATH environment variables.");
		sdkOption.setArgName("DIRECTORY");
		options.addOption(sdkOption);
		Option debugOption = new Option(null, "debug", true,
				"Specify debug or release mode. Overrides the debug compiler option, if specified in asconfig.json.");
		debugOption.setArgName("true OR false");
		debugOption.setOptionalArg(true);
		options.addOption(debugOption);
		Option airOption = new Option(null, "air", true,
				"Package the project as an Adobe AIR application. The allowed platforms include `android`, `ios`, `ios_simulator`, `windows`, `mac`, `bundle`, and `air`.");
		airOption.setArgName("PLATFORM");
		airOption.setOptionalArg(true);
		options.addOption(airOption);
		Option storepassOption = new Option(null, "storepass", true,
				"The password required to access the keystore used when packging the Adobe AIR application. If not specified, prompts for the password.");
		storepassOption.setArgName("PASSWORD");
		storepassOption.setOptionalArg(true);
		options.addOption(storepassOption);
		Option unpackageOption = new Option(null, "unpackage-anes", true,
				"Unpackage native extensions to the output directory when creating a debug build for the Adobe AIR simulator.");
		unpackageOption.setArgName("true OR false");
		unpackageOption.setOptionalArg(true);
		options.addOption(unpackageOption);
		Option cleanOption = new Option(null, "clean", true, "Clean the output directory. Will not build the project.");
		cleanOption.setArgName("true OR false");
		cleanOption.setOptionalArg(true);
		options.addOption(cleanOption);
		Option watchOption = new Option(null, "watch", true,
				"Watch for file system changes and rebuild if detected (Royale only).");
		watchOption.setArgName("true OR false");
		watchOption.setOptionalArg(true);
		options.addOption(watchOption);
		Option animateOption = new Option(null, "animate", true, "Specify the path to Adobe Animate.");
		animateOption.setArgName("FILE");
		animateOption.setOptionalArg(true);
		options.addOption(animateOption);
		Option publishOption = new Option(null, "publish-animate", true,
				"Publish Adobe Animate document, instead of exporting the SWF.");
		publishOption.setArgName("true OR false");
		publishOption.setOptionalArg(true);
		options.addOption(publishOption);
		Option verboseOption = new Option(null, "verbose", true, "Displays verbose output.");
		verboseOption.setArgName("true OR false");
		verboseOption.setOptionalArg(true);
		options.addOption(verboseOption);
		Option jvmargsOption = new Option(null, "jvmargs", true,
				"(Advanced) Pass custom arguments to the Java virtual machine.");
		jvmargsOption.setArgName("ARGS");
		jvmargsOption.setOptionalArg(true);
		options.addOption(jvmargsOption);
		Option printConfigOption = new Option(null, "print-config", true,
				"(Advanced) Prints the contents of the asconfig.json file used to build, including any extensions.");
		printConfigOption.setArgName("true OR false");
		printConfigOption.setOptionalArg(true);
		options.addOption(printConfigOption);

		ASConfigCOptions asconfigcOptions = null;
		try {
			CommandLine line = parser.parse(options, args);
			if (line.hasOption("h")) {
				String syntax = "asconfigc [options]\n\n" + "Examples: asconfigc\n" + "          asconfigc -p .\n"
						+ "          asconfigc -p path/to/custom.json\n\n" + "Options:";
				HelpFormatter formatter = new HelpFormatter();
				formatter.setSyntaxPrefix("Syntax:   ");
				formatter.printHelp(syntax, options);
				System.exit(0);
			}
			if (line.hasOption("v")) {
				String version = ASConfigC.class.getPackage().getImplementationVersion();
				System.out.println("Version: " + version);
				System.exit(0);
			}
			asconfigcOptions = new ASConfigCOptions(line);
		} catch (UnrecognizedOptionException e) {
			System.err.println("Unknown asconfigc option: " + e.getOption());
			System.exit(1);
		} catch (ParseException e) {
			System.err.println("Failed to parse asconfigc options.");
			e.printStackTrace(System.err);
			System.exit(1);
		}

		try {
			new ASConfigC(asconfigcOptions);
		} catch (ASConfigCException e) {
			if (e.status != 0) {
				System.exit(e.status);
			}
			System.err.println(e.getMessage());
			System.exit(1);
		}
	}

	private static final String ASCONFIG_JSON = "asconfig.json";

	public ASConfigC(ASConfigCOptions options) throws ASConfigCException {
		this.options = options;
		File configFile = findConfigurationFile(options.project);

		// the current working directory must be where asconfig.json is located
		System.setProperty("user.dir", configFile.getParent());

		JsonNode json = loadConfigFromFile(configFile);
		if (options.printConfig) {
			printConfig(json);
			return;
		}
		parseConfig(json);
		if (animateFile != null) {
			compileAnimateFile();
			prepareNativeExtensions();
		} else {
			cleanProject();
			copySourcePathAssets();
			copyHTMLTemplate();
			processAdobeAIRDescriptors();
			copyAIRFiles();
			prepareNativeExtensions();
			compileProject();
			packageAIR();
		}
	}

	private ASConfigCOptions options;
	private List<String> compilerOptions;
	private List<List<String>> allModuleCompilerOptions;
	private List<List<String>> allWorkerCompilerOptions;
	private List<String> airOptions;
	private JsonNode compilerOptionsJSON;
	private JsonNode airOptionsJSON;
	private String projectType;
	private boolean clean;
	private boolean watch;
	private boolean debugBuild;
	private boolean copySourcePathAssets;
	private String swfOutputPath;
	private String jsOutputPath = ".";
	private String outputPathForTarget;
	private String mainFile;
	private List<String> moduleOutputPaths;
	private List<String> workerOutputPaths;
	private List<String> airDescriptorPaths;
	private List<String> sourcePaths;
	private boolean configRequiresRoyale;
	private boolean configRequiresAIR;
	private boolean sdkIsRoyale;
	private boolean isSWFTargetOnly;
	private boolean outputIsJS;
	private String sdkHome;
	private String htmlTemplate;
	private Map<String, String> htmlTemplateOptions;
	private String animateFile;
	private WatchService animateWatcher;

	private File findConfigurationFile(String projectPath) throws ASConfigCException {
		File projectFile = null;
		if (projectPath != null) {
			projectFile = new File(projectPath);
		} else {
			projectFile = new File(System.getProperty("user.dir"));
		}
		if (!projectFile.exists()) {
			throw new ASConfigCException("Project directory or JSON file not found: " + projectFile.getAbsolutePath());
		}
		if (projectFile.isDirectory()) {
			File configFile = new File(projectFile, ASCONFIG_JSON);
			if (!configFile.exists()) {
				throw new ASConfigCException("asconfig.json not found in directory: " + projectFile.getAbsolutePath());
			}
			return configFile;
		}
		return projectFile;
	}

	private JsonNode loadConfigFromFile(File configFile) throws ASConfigCException {
		JsonSchema schema = null;
		try (InputStream schemaInputStream = getClass().getResourceAsStream("/schemas/asconfig.schema.json")) {
			JsonSchemaFactory factory = JsonSchemaFactory.getInstance(VersionFlag.V7);
			schema = factory.getSchema(schemaInputStream);
		} catch (Exception e) {
			// this exception is unexpected, so it should be reported
			throw new ASConfigCException("Failed to load asconfig.json schema: " + e);
		}
		return loadConfigFromFileWithSchema(configFile, schema);
	}

	private JsonNode loadConfigFromFileWithSchema(File configFile, JsonSchema schema) throws ASConfigCException {
		JsonNode json = null;
		try {
			if (options.verbose) {
				System.out.println("Configuration file: " + configFile.getAbsolutePath());
			}
			if (options.verbose) {
				System.out.println("Reading configuration file...");
			}
			String contents = new String(Files.readAllBytes(configFile.toPath()));
			ObjectMapper mapper = new ObjectMapper();
			// VSCode allows comments, so we should too
			mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
			mapper.configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true);
			json = mapper.readTree(contents);
			if (options.verbose) {
				System.out.println("Validating configuration file...");
			}
			Set<ValidationMessage> errors = schema.validate(json);
			if (!errors.isEmpty()) {
				StringBuilder combinedMessage = new StringBuilder();
				combinedMessage.append("Invalid asconfig.json:\n");
				for (ValidationMessage error : errors) {
					combinedMessage.append(error.getMessage() + "\n");
				}
				throw new ASConfigCException(combinedMessage.toString());
			}
			if (json.has(TopLevelFields.EXTENDS)) {
				String otherConfigPath = json.get(TopLevelFields.EXTENDS).asText();
				File otherConfigFile = new File(otherConfigPath);
				if (!otherConfigFile.isAbsolute()) {
					otherConfigFile = new File(System.getProperty("user.dir"), otherConfigPath);
				}
				JsonNode otherJson = loadConfigFromFileWithSchema(otherConfigFile, schema);
				json = ConfigUtils.mergeConfigs(json, otherJson);
			}
		} catch (JsonProcessingException e) {
			// this exception is expected sometimes if the JSON is invalid
			JsonLocation location = e.getLocation();
			throw new ASConfigCException(
					"Invalid configuration in file " + configFile.getName() + ":\n" + e.getOriginalMessage() + " (line "
							+ location.getLineNr() + ", column " + location.getColumnNr() + ")");
		} catch (IOException e) {
			throw new ASConfigCException("Failed to read " + configFile.getName() + ": " + e);
		}
		return json;
	}

	private void printConfig(JsonNode json) throws ASConfigCException {
		ObjectMapper mapper = new ObjectMapper();
		try {
			String configAsString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
			System.out.println(configAsString);
		} catch (JsonProcessingException e) {
			throw new ASConfigCException("Failed to write config: " + e);
		}
	}

	private void parseConfig(JsonNode json) throws ASConfigCException {
		if (options.verbose) {
			System.out.println("Parsing configuration file...");
		}
		clean = options.clean != null && options.clean.equals(true);
		watch = options.watch != null && options.watch.equals(true);
		if (watch) {
			debugBuild = true;
			configRequiresRoyale = true;
			if (options.debug != null && !options.debug.equals(true)) {
				throw new ASConfigCException("Watch requires debug to be true");
			}
		} else {
			debugBuild = options.debug != null && options.debug.equals(true);
		}
		compilerOptions = new ArrayList<>();
		allModuleCompilerOptions = new ArrayList<>();
		allWorkerCompilerOptions = new ArrayList<>();
		if (options.debug != null) {
			OptionsFormatter.setBoolean(CompilerOptions.DEBUG, options.debug, compilerOptions);
		}
		airOptions = new ArrayList<>();
		projectType = ProjectType.APP;
		if (json.has(TopLevelFields.TYPE)) {
			projectType = json.get(TopLevelFields.TYPE).asText();
		}
		if (json.has(TopLevelFields.CONFIG)) {
			String configName = json.get(TopLevelFields.CONFIG).asText();
			detectConfigRequirements(configName);
			compilerOptions.add("+configname=" + configName);
		}
		if (json.has(TopLevelFields.COMPILER_OPTIONS)) {
			compilerOptionsJSON = json.get(TopLevelFields.COMPILER_OPTIONS);
			readCompilerOptions(compilerOptionsJSON);
			if (options.debug == null && compilerOptionsJSON.has(CompilerOptions.DEBUG)
					&& compilerOptionsJSON.get(CompilerOptions.DEBUG).asBoolean() == true) {
				debugBuild = true;
			}
			if (compilerOptionsJSON.has(CompilerOptions.SOURCE_PATH)) {
				JsonNode sourcePath = compilerOptionsJSON.get(CompilerOptions.SOURCE_PATH);
				sourcePaths = JsonUtils.jsonNodeToListOfStrings(sourcePath);
			}
			if (compilerOptionsJSON.has(CompilerOptions.OUTPUT)) {
				swfOutputPath = compilerOptionsJSON.get(CompilerOptions.OUTPUT).asText();
			}
			if (compilerOptionsJSON.has(CompilerOptions.JS_OUTPUT)) {
				jsOutputPath = compilerOptionsJSON.get(CompilerOptions.JS_OUTPUT).asText();
			}
		}
		if (json.has(TopLevelFields.ADDITIONAL_OPTIONS)) {
			JsonNode jsonAdditionalOptions = json.get(TopLevelFields.ADDITIONAL_OPTIONS);
			if (jsonAdditionalOptions.isArray()) {
				jsonAdditionalOptions.elements()
						.forEachRemaining((jsonOption) -> compilerOptions.add(jsonOption.asText()));
			} else {
				String additionalOptions = jsonAdditionalOptions.asText();
				if (additionalOptions != null) {
					// split the additionalOptions into separate values so that we can
					// pass them in as String[], as the compiler expects.
					compilerOptions.addAll(OptionsUtils.parseAdditionalOptions(additionalOptions));
				}
			}
		}
		if (watch) {
			compilerOptions.add("--watch");
		}
		if (json.has(TopLevelFields.APPLICATION)) {
			configRequiresAIR = true;
			airDescriptorPaths = new ArrayList<String>();
			JsonNode application = json.get(TopLevelFields.APPLICATION);
			if (application.isTextual()) {
				// if it's a string, just use it as is for all platforms
				String airDescriptorPath = application.asText();
				airDescriptorPaths.add(airDescriptorPath);
			} else if (options.air != null) {
				// if it's an object, and we're packaging an AIR app, we need to
				// grab the descriptor for the platform we're targeting
				// we can ignore the rest
				if (application.has(options.air)) {
					String airDescriptorPath = application.get(options.air).asText();
					airDescriptorPaths.add(airDescriptorPath);
				}
			} else {
				// if it's an object, and we're compiling and not packaging an
				// AIR app, we need to use all of the descriptors
				Iterator<String> fieldNames = application.fieldNames();
				while (fieldNames.hasNext()) {
					String fieldName = fieldNames.next();
					String airDescriptorPath = application.get(fieldName).asText();
					airDescriptorPaths.add(airDescriptorPath);
				}
			}
			if (airDescriptorPaths != null) {
				for (String airDescriptorPath : airDescriptorPaths) {
					File airDescriptor = new File(airDescriptorPath);
					if (!airDescriptor.isAbsolute()) {
						airDescriptor = new File(System.getProperty("user.dir"), airDescriptorPath);
					}
					if (!airDescriptor.exists() || airDescriptor.isDirectory()) {
						throw new ASConfigCException("Adobe AIR application descriptor not found: " + airDescriptor);
					}
				}
			}
		}
		if (json.has(TopLevelFields.MODULES)) {
			List<String> templateModuleCompilerOptions = duplicateCompilerOptionsForModuleOrWorker(compilerOptions);
			JsonNode modulesJSON = json.get(TopLevelFields.MODULES);
			int size = modulesJSON.size();
			File linkReportFile = null;
			if (size > 0) {
				try {
					linkReportFile = File.createTempFile("asconfigc-link-report", ".xml");
				} catch (IOException e) {
					throw new ASConfigCException("Failed to create link report for modules.");
				}
			}
			linkReportFile.deleteOnExit();
			for (int i = 0; i < size; i++) {
				moduleOutputPaths = new ArrayList<>();
				List<String> moduleCompilerOptions = new ArrayList<>(templateModuleCompilerOptions);
				JsonNode module = modulesJSON.get(i);
				String output = "";
				if (module.has(ModuleFields.OUTPUT)) {
					output = module.get(ModuleFields.OUTPUT).asText();
					moduleOutputPaths.add(output);
				}
				if (output.length() > 0) {
					moduleCompilerOptions.add("--" + CompilerOptions.OUTPUT + "=" + output);
				}
				boolean optimize = false;
				if (module.has(ModuleFields.OPTIMIZE)) {
					optimize = module.get(ModuleFields.OPTIMIZE).asBoolean();
				}
				if (optimize) {
					moduleCompilerOptions
							.add("--" + CompilerOptions.LOAD_EXTERNS + "+=" + linkReportFile.getAbsolutePath());
				}
				String file = module.get(ModuleFields.FILE).asText();
				moduleCompilerOptions.add("--");
				moduleCompilerOptions.add(file);
				allModuleCompilerOptions.add(moduleCompilerOptions);
			}
			if (size > 0) {
				compilerOptions.add("--" + CompilerOptions.LINK_REPORT + "+=" + linkReportFile.getAbsolutePath());
			}
		}
		if (json.has(TopLevelFields.WORKERS)) {
			workerOutputPaths = new ArrayList<>();
			List<String> templateWorkerCompilerOptions = duplicateCompilerOptionsForModuleOrWorker(compilerOptions);
			JsonNode workersJSON = json.get(TopLevelFields.WORKERS);
			for (int i = 0, size = workersJSON.size(); i < size; i++) {
				List<String> workerCompilerOptions = new ArrayList<>(templateWorkerCompilerOptions);
				JsonNode worker = workersJSON.get(i);
				String output = "";
				if (worker.has(WorkerFields.OUTPUT)) {
					output = worker.get(WorkerFields.OUTPUT).asText();
				}
				if (output.length() > 0) {
					workerOutputPaths.add(output);
					workerCompilerOptions.add("--" + CompilerOptions.OUTPUT + "=" + output);
				}
				String file = worker.get(WorkerFields.FILE).asText();
				workerCompilerOptions.add("--");
				workerCompilerOptions.add(file);
				allWorkerCompilerOptions.add(workerCompilerOptions);
			}
		}
		// parse files before airOptions because the mainFile may be
		// needed to generate some file paths
		if (json.has(TopLevelFields.FILES)) {
			JsonNode files = json.get(TopLevelFields.FILES);
			if (projectType.equals(ProjectType.LIB)) {
				for (int i = 0, size = files.size(); i < size; i++) {
					String file = files.get(i).asText();
					compilerOptions.add("--include-sources+=" + file);
				}
			} else {
				int size = files.size();
				if (size > 0) {
					// terminate previous options and start default options
					compilerOptions.add("--");
					// mainClass is preferred, but for backwards compatibility,
					// we need to support setting the entry point with files too
					mainFile = files.get(size - 1).asText();
				}
				for (int i = 0; i < size; i++) {
					String file = files.get(i).asText();
					compilerOptions.add(file);
				}
			}
		}
		// mainClass must be parsed after files
		if (ProjectType.APP.equals(projectType) && json.has(TopLevelFields.MAIN_CLASS)) {
			// if set already, clear it because we're going to replace it
			boolean hadMainFile = mainFile != null;
			String mainClass = json.get(TopLevelFields.MAIN_CLASS).asText();
			mainFile = ConfigUtils.resolveMainClass(mainClass, sourcePaths, System.getProperty("user.dir"));
			if (mainFile == null) {
				throw new ASConfigCException("Main class not found in source paths: " + mainClass);
			}
			if (!hadMainFile) {
				// terminate previous options and start default options
				compilerOptions.add("--");
			}
			compilerOptions.add(mainFile);
		}
		if (json.has(TopLevelFields.ANIMATE_OPTIONS)) {
			JsonNode animateOptions = json.get(TopLevelFields.ANIMATE_OPTIONS);
			if (animateOptions.has(AnimateOptions.FILE)) {
				animateFile = animateOptions.get(AnimateOptions.FILE).asText();
				Path animateFilePath = Paths.get(animateFile);
				if (!animateFilePath.isAbsolute()) {
					animateFile = Paths.get(System.getProperty("user.dir")).resolve(animateFile).toString();
				}
			}
		}
		// before parsing AIR options, we need to figure out where the output
		// directory is, based on the SDK type and compiler options
		validateSDK();
		if (json.has(TopLevelFields.AIR_OPTIONS)) {
			configRequiresAIR = true;
			airOptionsJSON = json.get(TopLevelFields.AIR_OPTIONS);
			readAIROptions(airOptionsJSON);
		}
		if (json.has(TopLevelFields.COPY_SOURCE_PATH_ASSETS)) {
			copySourcePathAssets = json.get(TopLevelFields.COPY_SOURCE_PATH_ASSETS).asBoolean();
		}
		if (json.has(TopLevelFields.HTML_TEMPLATE)) {
			htmlTemplate = json.get(TopLevelFields.HTML_TEMPLATE).asText();

			// the HTML template needs to be parsed after files and outputPath have
			// both been parsed
			JsonNode compilerOptionsJson = null;
			if (json.has(TopLevelFields.COMPILER_OPTIONS)) {
				compilerOptionsJson = json.get(TopLevelFields.COMPILER_OPTIONS);
			}
			readHTMLTemplateOptions(compilerOptionsJson);
		}
	}

	private List<String> duplicateCompilerOptionsForModuleOrWorker(List<String> compilerOptions) {
		return compilerOptions.stream().filter(option -> {
			return !COMPILER_OPTION_OUTPUT_PATTERN.matcher(option).find();
		}).collect(Collectors.toList());
	}

	private void detectConfigRequirements(String configName) {
		switch (configName) {
			case ConfigName.JS: {
				configRequiresRoyale = true;
				break;
			}
			case ConfigName.NODE: {
				configRequiresRoyale = true;
				break;
			}
			case ConfigName.ROYALE: {
				configRequiresRoyale = true;
				break;
			}
			case ConfigName.AIR: {
				configRequiresAIR = true;
				break;
			}
			case ConfigName.AIRMOBILE: {
				configRequiresAIR = true;
				break;
			}
		}
	}

	private void compileAnimateFile() throws ASConfigCException {
		String animatePath = options.animate;
		if (animatePath == null) {
			throw new ASConfigCException("Adobe Animate not found. Use --animate option.");
		}
		if (!Files.exists(Paths.get(animatePath))) {
			throw new ASConfigCException("Adobe Animate not found at path: " + animatePath);
		}
		File jarFile = null;
		try {
			jarFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
		} catch (URISyntaxException e) {
			StringWriter stackTrace = new StringWriter();
			e.printStackTrace(new PrintWriter(stackTrace));
			throw new ASConfigCException("Error: Failed to find Adobe Animate script.\n" + stackTrace.toString());
		}
		boolean publish = options.publishAnimate;
		String jsflFileName = null;
		if (publish) {
			jsflFileName = debugBuild ? "publish-debug.jsfl" : "publish-release.jsfl";
		} else {
			jsflFileName = debugBuild ? "compile-debug.jsfl" : "compile-release.jsfl";
		}
		Path jsflPath = Paths.get(jarFile.getParentFile().getParentFile().getAbsolutePath(), "jsfl", jsflFileName);
		try {
			File tempFile = File.createTempFile("vscode-as3mxml", ".jsfl");
			tempFile.deleteOnExit();
			Path tempPath = tempFile.toPath();
			String contents = new String(Files.readAllBytes(jsflPath));
			Path resolvedOutputPath = null;
			if (swfOutputPath == null) {
				resolvedOutputPath = Paths.get(animateFile);
				if (!resolvedOutputPath.isAbsolute()) {
					resolvedOutputPath = Paths.get(System.getProperty("user.dir")).resolve(resolvedOutputPath);
				}
				String fileName = resolvedOutputPath.getFileName().toString();
				int extIndex = fileName.lastIndexOf(".");
				if (extIndex != -1) {
					fileName = fileName.substring(0, extIndex) + ".swf";
				}
				resolvedOutputPath = resolvedOutputPath.getParent().resolve(fileName);
			} else {
				resolvedOutputPath = Paths.get(ProjectUtils.findOutputPath(mainFile, swfOutputPath, true));
			}
			Path parentPath = resolvedOutputPath.getParent();
			if (!Files.exists(parentPath) && !parentPath.toFile().mkdirs()) {
				throw new ASConfigCException(
						"Error: Failed to create Adobe Animate output folder: " + parentPath.toString());
			}
			URI resolvedUri = resolvedOutputPath.toUri();
			contents = contents.replace("${OUTPUT_URI}", resolvedUri.toString());
			Files.write(tempPath, contents.getBytes());
			jsflPath = tempPath;
		} catch (IOException e) {
			StringWriter stackTrace = new StringWriter();
			e.printStackTrace(new PrintWriter(stackTrace));
			throw new ASConfigCException("Error: Failed to copy Adobe Animate script.\n" + stackTrace.toString());
		}

		boolean isMacOS = System.getProperty("os.name").toLowerCase().startsWith("mac os");
		List<String> command = new ArrayList<>();
		if (isMacOS) {
			command.add("open");
			command.add("-a");
		}
		command.add(animatePath);
		command.add(animateFile);
		command.add(jsflPath.toString());

		if (options.verbose) {
			System.out.println("Compiling Adobe Animate project...");
			System.out.println(String.join(" ", command));
		}
		File cwd = new File(System.getProperty("user.dir"));
		try {
			new ProcessBuilder().command(command).directory(cwd).inheritIO().start();
		} catch (IOException e) {
			throw new ASConfigCException("Failed to execute Adobe Animate: " + e.getMessage());
		}

		Path pathToWatch = null;
		if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			pathToWatch = Paths.get(System.getenv("LOCALAPPDATA"), "Adobe", "vscode-as3mxml");
		} else if (isMacOS) {
			pathToWatch = Paths.get(System.getProperty("user.home"))
					.resolve("Library/Application Support/Adobe/vscode-as3mxml");
		}
		if (pathToWatch == null) {
			throw new ASConfigCException("Failed to locate Adobe Animate logs.");
		}
		// macOS seems to require these files to be manually deleted to detect
		// the appropriate create event
		if (Files.exists(pathToWatch)) {
			try {
				Files.walk(pathToWatch).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
			} catch (IOException e) {
				throw new ASConfigCException("Failed to delete Adobe Animate logs because an I/O exception occurred.");
			}
		}
		if (!Files.exists(pathToWatch) && !pathToWatch.toFile().mkdirs()) {
			throw new ASConfigCException("Failed to create folder for Adobe Animate publish: " + pathToWatch);
		}
		createAnimatePublishWatcher(pathToWatch);
	}

	private void createAnimatePublishWatcher(Path pathToWatch) throws ASConfigCException {
		try {
			animateWatcher = FileSystems.getDefault().newWatchService();
		} catch (IOException e) {
			StringWriter stackTrace = new StringWriter();
			e.printStackTrace(new PrintWriter(stackTrace));
			throw new ASConfigCException(
					"Failed to get file system watch service for Adobe Animate publish.\n" + stackTrace.toString());
		}
		try {
			try {
				// file system changes are detected very, very slowly on macOS
				// without high sensitivity
				Class<?> c = Class.forName("com.sun.nio.file.SensitivityWatchEventModifier");
				Field f = c.getField("HIGH");
				Modifier modifier = (Modifier) f.get(c);
				pathToWatch.register(animateWatcher, new WatchEvent.Kind[] { StandardWatchEventKinds.ENTRY_CREATE },
						modifier);
			} catch (Exception e) {
				// fall back to the slow version
				pathToWatch.register(animateWatcher, StandardWatchEventKinds.ENTRY_CREATE);
			}
		} catch (IOException e) {
			StringWriter stackTrace = new StringWriter();
			e.printStackTrace(new PrintWriter(stackTrace));
			throw new ASConfigCException("Failed to get watch path for Adobe Animate publish: " + pathToWatch + "\n"
					+ stackTrace.toString());
		}
		Path errorLogPath = Paths.get(FILE_NAME_ANIMATE_ERROR_LOG);
		Path publishLogPath = Paths.get(FILE_NAME_ANIMATE_PUBLISH_LOG);
		boolean hasErrors = false;
		while (true) {
			WatchKey watchKey = null;
			try {
				// pause the thread while there are no changes pending,
				// for better performance
				watchKey = animateWatcher.take();
			} catch (InterruptedException e) {
				return;
			}
			while (watchKey != null) {
				for (WatchEvent<?> event : watchKey.pollEvents()) {
					WatchEvent.Kind<?> kind = event.kind();
					Path childPath = (Path) event.context();
					if (errorLogPath.equals(childPath) && kind.equals(StandardWatchEventKinds.ENTRY_CREATE)) {
						hasErrors = true;
					} else if (publishLogPath.equals(childPath) && kind.equals(StandardWatchEventKinds.ENTRY_CREATE)) {
						if (hasErrors) {
							// switch to full path
							errorLogPath = pathToWatch.resolve(errorLogPath);
							String contents = null;
							try {
								contents = new String(Files.readAllBytes(errorLogPath));
								if (contents.startsWith("ï»¿")) {
									// remove byte order mark
									contents = contents.substring(3);
								}
							} catch (IOException e) {
								StringWriter stackTrace = new StringWriter();
								e.printStackTrace(new PrintWriter(stackTrace));
								throw new ASConfigCException("Failed to read Adobe Animate error log: " + errorLogPath
										+ "\n" + stackTrace.toString());
							}
							// print the errors/warnings to the console
							System.err.println(contents);
							if (contents.contains("**Error** ")) {
								// compiler errors
								throw new ASConfigCException(1);
							}
						}
						// publish has completed, so we don't need to watch for
						// any more changes
						return;
					}
				}
				watchKey.reset();

				// keep handling new changes until we run out
				watchKey = animateWatcher.poll();
			}
		}
	}

	private void readHTMLTemplateOptions(JsonNode compilerOptionsJson) throws ASConfigCException {
		HTMLTemplateOptionsParser parser = new HTMLTemplateOptionsParser();
		try {
			htmlTemplateOptions = parser.parse(compilerOptionsJson, mainFile, outputPathForTarget);
		} catch (Exception e) {
			StringWriter stackTrace = new StringWriter();
			e.printStackTrace(new PrintWriter(stackTrace));
			throw new ASConfigCException("Error: Failed to parse HTML template options.\n" + stackTrace.toString());
		}
	}

	private void readCompilerOptions(JsonNode compilerOptionsJson) throws ASConfigCException {
		CompilerOptionsParser parser = new CompilerOptionsParser();
		try {
			parser.parse(compilerOptionsJson, options.debug, compilerOptions);
		} catch (Exception e) {
			StringWriter stackTrace = new StringWriter();
			e.printStackTrace(new PrintWriter(stackTrace));
			throw new ASConfigCException("Error: Failed to parse compiler options.\n" + stackTrace.toString());
		}
		// make sure that we require Royale for certain compiler options
		if (compilerOptionsJson.has(CompilerOptions.JS_OUTPUT_TYPE)) {
			configRequiresRoyale = true;
		}
		if (compilerOptionsJson.has(CompilerOptions.TARGETS)) {
			configRequiresRoyale = true;
		}
		if (compilerOptionsJson.has(CompilerOptions.SOURCE_MAP)) {
			// source-map compiler option is supported by Royale
			configRequiresRoyale = true;
		}
	}

	private void readAIROptions(JsonNode airOptionsJson) throws ASConfigCException {
		if (options.air == null) {
			return;
		}
		String airDescriptorPath = null;
		if (airDescriptorPaths != null && airDescriptorPaths.size() > 0) {
			airDescriptorPath = airDescriptorPaths.get(0);
		}
		AIROptionsParser parser = new AIROptionsParser();
		try {
			parser.parse(options.air, debugBuild,
					ProjectUtils.findAIRDescriptorOutputPath(mainFile, airDescriptorPath,
							outputPathForTarget, System.getProperty("user.dir"), !outputIsJS, debugBuild),
					ProjectUtils.findApplicationContentOutputPath(mainFile, outputPathForTarget, !outputIsJS,
							debugBuild),
					moduleOutputPaths,
					workerOutputPaths,
					airOptionsJson, airOptions);
		} catch (Exception e) {
			StringWriter stackTrace = new StringWriter();
			e.printStackTrace(new PrintWriter(stackTrace));
			throw new ASConfigCException("Error: Failed to parse Adobe AIR options.\n" + stackTrace.toString());
		}
	}

	private void validateSDK() throws ASConfigCException {
		if (animateFile != null) {
			return;
		}
		sdkHome = options.sdk;
		if (sdkHome == null) {
			sdkHome = ApacheRoyaleUtils.findSDK();
		}
		if (sdkHome == null && !configRequiresRoyale) {
			sdkHome = GenericSDKUtils.findSDK();
		}
		if (sdkHome == null) {
			String envHome = "FLEX_HOME";
			if (configRequiresRoyale) {
				envHome = "ROYALE_HOME";
			}
			throw new ASConfigCException(
					"SDK not found. Set " + envHome + ", add SDK to PATH environment variable, or use --sdk option.");
		}
		Path sdkHomePath = Paths.get(sdkHome);
		Path royalePath = ApacheRoyaleUtils.isValidSDK(sdkHomePath);
		if (royalePath != null) {
			sdkHomePath = royalePath;
			sdkIsRoyale = true;
		}
		if (configRequiresRoyale && !sdkIsRoyale) {
			throw new ASConfigCException(
					"Configuration options in asconfig.json require Apache Royale. Path to SDK is not valid: "
							+ sdkHome);
		}
		outputIsJS = sdkIsRoyale && !isSWFTargetOnly;
		outputPathForTarget = outputIsJS ? jsOutputPath : swfOutputPath;
		if (options.verbose) {
			System.out.println("SDK: " + sdkHomePath);
		}
	}

	private void cleanProject() throws ASConfigCException {
		if (!clean) {
			return;
		}

		if (options.verbose) {
			System.out.println("Cleaning project...");
		}
		String outputDirectory = ProjectUtils.findOutputDirectory(mainFile, outputPathForTarget, !outputIsJS);
		Path outputPath = Paths.get(outputDirectory);
		if (outputIsJS) {
			Path debugOutputPath = outputPath.resolve(FILE_NAME_BIN_JS_DEBUG);
			deleteOutputDirectory(debugOutputPath);
			Path releaseOutputPath = outputPath.resolve(FILE_NAME_BIN_JS_RELEASE);
			deleteOutputDirectory(releaseOutputPath);
		} else // swf
		{
			deleteOutputDirectory(outputPath);
		}
		if (moduleOutputPaths != null) {
			for (String moduleOutputPath : moduleOutputPaths) {
				Path moduleSWFPath = Paths.get(moduleOutputPath);
				if (Files.exists(moduleSWFPath)) {
					try {
						Files.delete(moduleSWFPath);
					} catch (IOException e) {
						throw new ASConfigCException(
								"Failed to clean project because an I/O exception occurred while deleting file: "
										+ moduleSWFPath.toString());
					}
				}
			}
		}
		if (workerOutputPaths != null) {
			for (String workerOutputPath : workerOutputPaths) {
				Path workerSWFPath = Paths.get(workerOutputPath);
				if (Files.exists(workerSWFPath)) {
					try {
						Files.delete(workerSWFPath);
					} catch (IOException e) {
						throw new ASConfigCException(
								"Failed to clean project because an I/O exception occurred while deleting file: "
										+ workerSWFPath.toString());
					}
				}
			}
		}

		// immediately exits after cleaning
		System.exit(0);
	}

	private void deleteOutputDirectory(Path outputPath) throws ASConfigCException {
		Path cwd = Paths.get(System.getProperty("user.dir"));
		if (cwd.startsWith(outputPath)) {
			throw new ASConfigCException(
					"Failed to clean project because the output path overlaps with the current working directory.");
		}

		List<String> sourcePathsCopy = new ArrayList<>();
		if (sourcePaths != null) {
			// we don't want to modify the original list, so copy the items over
			sourcePathsCopy.addAll(sourcePaths);
		}
		if (mainFile != null) {
			// the parent directory of the main file is automatically added as a
			// source path by the compiler
			Path mainFileParent = Paths.get(mainFile).getParent();
			// may be null if the path is already root
			if (mainFileParent != null) {
				sourcePathsCopy.add(mainFileParent.toString());
			}
		}
		for (int i = 0, size = sourcePathsCopy.size(); i < size; i++) {
			String sourcePath = sourcePathsCopy.get(i);
			Path path = Paths.get(sourcePath);
			if (!path.isAbsolute()) {
				// force all source paths into absolute paths
				path = Paths.get(System.getProperty("user.dir"), sourcePath);
			}
			if (path.startsWith(outputPath) || outputPath.startsWith(path)) {
				throw new ASConfigCException(
						"Failed to clean project because the output path overlaps with a source path.");
			}
		}

		if (Files.exists(outputPath)) {
			if (options.verbose) {
				System.out.println("Deleting: " + outputPath);
			}
			try {
				Files.walk(outputPath).sorted(Comparator.reverseOrder()).filter(path -> !path.equals(outputPath))
						.map(Path::toFile).forEach(File::delete);
			} catch (IOException e) {
				throw new ASConfigCException("Failed to clean project because an I/O exception occurred.");
			}
		}
	}

	private void compileProject() throws ASConfigCException {
		Path workspacePath = Paths.get(System.getProperty("user.dir"));
		Path sdkPath = Paths.get(sdkHome);
		// compile workers first because they might be embedded in the app
		for (int i = 0; i < allWorkerCompilerOptions.size(); i++) {
			List<String> workerCompilerOptions = allWorkerCompilerOptions.get(i);
			options.compiler.compile(projectType, workerCompilerOptions, workspacePath, sdkPath);
		}
		options.compiler.compile(projectType, compilerOptions, workspacePath, sdkPath);
		// compile modules last because they might be optimized for the app
		for (int i = 0; i < allModuleCompilerOptions.size(); i++) {
			List<String> moduleCompilerOptions = allModuleCompilerOptions.get(i);
			options.compiler.compile(projectType, moduleCompilerOptions, workspacePath, sdkPath);
		}
	}

	private void copySourcePathAssetToOutputDirectory(String assetPath, String mainFile, List<String> sourcePaths,
			String outputDirectory) throws ASConfigCException {
		String targetPath = null;
		try {
			targetPath = ProjectUtils.assetPathToOutputPath(assetPath, mainFile, sourcePaths, outputDirectory);
		} catch (IOException e) {
			throw new ASConfigCException(e.getMessage());
		}
		createParentAndCopyAsset(Paths.get(assetPath), Paths.get(targetPath));
	}

	private void copySourcePathAssets() throws ASConfigCException {
		if (!copySourcePathAssets) {
			return;
		}
		List<String> pathsToSearch = new ArrayList<>();
		if (sourcePaths != null) {
			pathsToSearch.addAll(sourcePaths);
		}
		String outputDirectory = ProjectUtils.findOutputDirectory(mainFile, outputPathForTarget, !outputIsJS);
		ArrayList<String> excludes = new ArrayList<>();
		if (airDescriptorPaths != null) {
			excludes.addAll(airDescriptorPaths);
		}
		Set<String> assetPaths = null;
		try {
			assetPaths = ProjectUtils.findSourcePathAssets(mainFile, sourcePaths, outputDirectory, excludes,
					Arrays.asList(FILE_EXTENSION_AS, FILE_EXTENSION_MXML));
		} catch (IOException e) {
			throw new ASConfigCException(e.getMessage());
		}
		if (assetPaths.size() == 0) {
			return;
		}
		if (options.verbose) {
			System.out.println("Copying source path assets...");
		}
		for (String assetPath : assetPaths) {
			if (outputIsJS) {
				File outputDirectoryJSDebug = new File(outputDirectory, FILE_NAME_BIN_JS_DEBUG);
				copySourcePathAssetToOutputDirectory(assetPath, mainFile, sourcePaths,
						outputDirectoryJSDebug.getAbsolutePath());
				if (!debugBuild) {
					File outputDirectoryJSRelease = new File(outputDirectory, FILE_NAME_BIN_JS_RELEASE);
					copySourcePathAssetToOutputDirectory(assetPath, mainFile, sourcePaths,
							outputDirectoryJSRelease.getAbsolutePath());
				}
			} else // swf
			{
				copySourcePathAssetToOutputDirectory(assetPath, mainFile, sourcePaths, outputDirectory);
			}
		}
	}

	private void copyHTMLTemplate() throws ASConfigCException {
		if (htmlTemplate == null) {
			// nothing to copy if this field is omitted
			return;
		}

		if (options.verbose) {
			System.out.println("Copying HTML template...");
		}

		File templateDirectory = new File(htmlTemplate);
		if (!templateDirectory.isAbsolute()) {
			templateDirectory = new File(System.getProperty("user.dir"), htmlTemplate);
		}
		if (!templateDirectory.exists()) {
			throw new ASConfigCException("htmlTemplate directory does not exist: " + htmlTemplate);
		}
		if (!templateDirectory.isDirectory()) {
			throw new ASConfigCException("htmlTemplate path must be a directory. Invalid path: " + htmlTemplate);
		}

		String outputDirectoryPath = ProjectUtils.findOutputDirectory(mainFile, outputPathForTarget, !outputIsJS);
		if (outputIsJS) {
			File outputDirectoryJSDebug = new File(outputDirectoryPath, FILE_NAME_BIN_JS_DEBUG);
			copyHTMLTemplateDirectory(templateDirectory, outputDirectoryJSDebug);
			if (!debugBuild) {
				File outputDirectoryJSRelease = new File(outputDirectoryPath, FILE_NAME_BIN_JS_RELEASE);
				copyHTMLTemplateDirectory(templateDirectory, outputDirectoryJSRelease);
			}
		} else // swf
		{
			File outputDirectory = new File(outputDirectoryPath);
			copyHTMLTemplateDirectory(templateDirectory, outputDirectory);
		}
	}

	private void copyHTMLTemplateDirectory(File inputDirectory, File outputDirectory) throws ASConfigCException {
		if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
			throw new ASConfigCException(
					"Failed to create output directory for HTML template: " + outputDirectory.getAbsolutePath() + ".");
		}
		try {
			for (File file : inputDirectory.listFiles()) {
				if (file.isDirectory()) {
					File newOutputDirectory = new File(outputDirectory, file.getName());
					copyHTMLTemplateDirectory(file, newOutputDirectory);
					continue;
				}
				String fileName = file.getName();
				int extensionIndex = fileName.lastIndexOf('.');
				if (extensionIndex != -1) {
					String extension = fileName.substring(extensionIndex);
					String templateExtension = ".template" + extension;
					if (fileName.endsWith(templateExtension)) {
						if (options.verbose) {
							System.out.println("Copying template asset: " + file.getAbsolutePath());
						}
						String beforeExtension = fileName.substring(0, fileName.length() - templateExtension.length());
						if (beforeExtension.equals("index")) {
							if (mainFile != null) {
								Path mainFilePath = Paths.get(mainFile);
								// strip any directory names from the beginning
								String mainFileName = mainFilePath.getFileName().toString();
								int mainFileExtensionIndex = mainFileName.indexOf(".");
								if (mainFileExtensionIndex != -1) {
									// exclude the file extension
									beforeExtension = mainFileName.substring(0, mainFileExtensionIndex);
								}
							}
						}
						String contents = new String(Files.readAllBytes(file.toPath()));
						contents = ProjectUtils.populateHTMLTemplateFile(contents, htmlTemplateOptions);
						String outputFileName = beforeExtension + extension;
						File outputFile = new File(outputDirectory, outputFileName);
						Files.write(outputFile.toPath(), contents.getBytes());
						continue;
					}
				}
				File outputFile = new File(outputDirectory, fileName);
				createParentAndCopyAsset(file.toPath(), outputFile.toPath());
			}
		} catch (IOException e) {
			throw new ASConfigCException(e.getMessage());
		}
	}

	private void prepareNativeExtensions() throws ASConfigCException {
		if (options.air != null) {
			// don't copy anything when packaging an app. these files are used
			// for debug builds that run in the AIR simulator only.
			return;
		}
		if (!options.unpackageANEs) {
			// don't copy anything if it's not requested.
			return;
		}
		if (!debugBuild) {
			// don't copy anything when it's a release build.
			return;
		}
		if (compilerOptionsJSON == null) {
			// the compilerOptions field is not defined, so there's nothing to copy
			return;
		}

		List<File> anes = new ArrayList<>();
		if (compilerOptionsJSON.has(CompilerOptions.LIBRARY_PATH)) {
			JsonNode libraryPathJSON = compilerOptionsJSON.get(CompilerOptions.LIBRARY_PATH);
			findANEs(libraryPathJSON, anes);
		}
		if (compilerOptionsJSON.has(CompilerOptions.EXTERNAL_LIBRARY_PATH)) {
			JsonNode externalLibraryPathJSON = compilerOptionsJSON.get(CompilerOptions.EXTERNAL_LIBRARY_PATH);
			findANEs(externalLibraryPathJSON, anes);
		}

		if (anes.size() == 0) {
			return;
		}

		if (options.verbose) {
			System.out.println("Unpacking Adobe AIR native extensions...");
		}

		for (File aneFile : anes) {
			unpackANE(aneFile);
		}
	}

	private void findANEs(JsonNode libraryPathJSON, List<File> result) throws ASConfigCException {
		for (int i = 0, size = libraryPathJSON.size(); i < size; i++) {
			String libraryPath = libraryPathJSON.get(i).asText();
			if (libraryPath.endsWith(FILE_EXTENSION_ANE)) {
				File file = new File(libraryPath);
				if (!file.isAbsolute()) {
					file = new File(System.getProperty("user.dir"), libraryPath);
				}
				result.add(file);
			} else {
				File file = Paths.get(libraryPath).toFile();
				if (!file.isAbsolute()) {
					file = new File(System.getProperty("user.dir"), libraryPath);
				}
				if (!file.isDirectory()) {
					continue;
				}
				for (String child : file.list()) {
					if (!child.endsWith(FILE_EXTENSION_ANE)) {
						continue;
					}
					File childFile = new File(file, child);
					result.add(childFile);
				}
			}
		}
	}

	private void unpackANE(File aneFile) throws ASConfigCException {
		if (aneFile.isDirectory()) {
			// this is either an ANE that's already unpacked
			// ...or something else entirely
			return;
		}
		if (options.verbose) {
			System.out.println("Unpacking: " + aneFile.getAbsolutePath());
		}
		String outputDirectoryPath = ProjectUtils.findOutputDirectory(mainFile, outputPathForTarget, !outputIsJS);
		File outputDirectory = new File(outputDirectoryPath);
		File unpackedAneDirectory = new File(outputDirectory, FILE_NAME_UNPACKAGED_ANES);
		File currentAneDirectory = new File(unpackedAneDirectory, aneFile.getName());
		if (currentAneDirectory.exists() && currentAneDirectory.isDirectory()) {
			if (currentAneDirectory.lastModified() == aneFile.lastModified()) {
				if (options.verbose) {
					System.out.println("Skipping unchanged: " + currentAneDirectory.getName());
				}
				return;
			}
		} else if (!currentAneDirectory.mkdirs()) {
			throw new ASConfigCException("Failed to copy Adobe AIR native extension to path: " + currentAneDirectory
					+ " because the directories could not be created.");
		}

		try {
			ZipFile zipFile = new ZipFile(aneFile);
			Enumeration<?> zipEntries = zipFile.entries();
			while (zipEntries.hasMoreElements()) {
				ZipEntry zipEntry = (ZipEntry) zipEntries.nextElement();
				if (zipEntry.isDirectory()) {
					continue;
				}

				File destFile = new File(currentAneDirectory, zipEntry.getName());
				File destParent = new File(destFile.getParent());
				destParent.mkdirs();

				BufferedInputStream inStream = new BufferedInputStream(zipFile.getInputStream(zipEntry));
				FileOutputStream fileOutStream = new FileOutputStream(destFile);
				byte buffer[] = new byte[2048];
				int len = 0;
				while ((len = inStream.read(buffer)) > 0) {
					fileOutStream.write(buffer, 0, len);
				}
				fileOutStream.flush();
				fileOutStream.close();
				inStream.close();
			}
			zipFile.close();
		} catch (FileNotFoundException e) {
			throw new ASConfigCException("Failed to copy Adobe AIR native extension from path: "
					+ aneFile.getAbsolutePath() + " because the file was not found.");
		} catch (IOException e) {
			throw new ASConfigCException(
					"Failed to copy Adobe AIR native extension from path: " + aneFile.getAbsolutePath() + ".");
		}
		// save the last modified time of the .ane file to determine later
		// if we need to unpack again or not
		currentAneDirectory.setLastModified(aneFile.lastModified());
	}

	private void createParentAndCopyAsset(Path srcPath, Path destPath) throws ASConfigCException {
		File destFile = new File(destPath.toString());
		File destParent = destFile.getParentFile();
		if (!destParent.exists() && !destParent.mkdirs()) {
			throw new ASConfigCException("Failed to copy file from source " + srcPath + " to destination "
					+ destParent.getAbsolutePath() + " because the directories could not be created.");
		}
		copyAsset(srcPath, destPath, true);
	}

	private void copyAsset(Path srcPath, Path destPath, boolean retry) throws ASConfigCException {
		if (options.verbose) {
			System.out.println("Copying asset: " + srcPath);
		}
		try {
			Files.copy(srcPath, destPath, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			// if the destination file is not writable, make it writable and try
			// again one more time.
			// do this check AFTER failure because it's slow to check whether
			// every file exists and is writable
			if (retry && Files.exists(destPath) && !Files.isWritable(destPath)) {
				destPath.toFile().setWritable(true);
				copyAsset(srcPath, destPath, false);
				return;
			}
			throw new ASConfigCException(
					"Failed to copy file from source " + srcPath.toString() + " to destination " + destPath.toString());
		}
	}

	private void copyAIRFiles() throws ASConfigCException {
		if (options.air != null) {
			// don't copy anything when packaging an app. these files are used
			// for debug builds only.
			return;
		}
		if (airOptionsJSON == null) {
			// the airOptions field is not defined, so there's nothing to copy
			return;
		}
		if (!airOptionsJSON.has(AIROptions.FILES)) {
			// the files field is not defined, so there's nothing to copy
			return;
		}

		if (options.verbose) {
			System.out.println("Copying Adobe AIR application files...");
		}

		String outputDirectoryPath = ProjectUtils.findOutputDirectory(mainFile, outputPathForTarget, !outputIsJS);
		File outputDirectory = new File(outputDirectoryPath);

		JsonNode filesJSON = airOptionsJSON.get(AIROptions.FILES);
		for (int i = 0, size = filesJSON.size(); i < size; i++) {
			JsonNode fileJSON = filesJSON.get(i);
			if (fileJSON.isTextual()) // just a string
			{
				String filePath = fileJSON.asText();
				File srcFile = new File(filePath);
				if (outputIsJS) {
					File outputDirectoryJSDebug = new File(outputDirectory, FILE_NAME_BIN_JS_DEBUG);
					File destFileJSDebug = new File(outputDirectoryJSDebug, srcFile.getName());
					createParentAndCopyAsset(srcFile.toPath(), destFileJSDebug.toPath());
					if (!debugBuild) {
						File outputDirectoryJSRelease = new File(outputDirectory, FILE_NAME_BIN_JS_RELEASE);
						File destFileJSRelease = new File(outputDirectoryJSRelease, srcFile.getName());
						createParentAndCopyAsset(srcFile.toPath(), destFileJSRelease.toPath());
					}
				} else // swf
				{
					File destFile = new File(outputDirectory, srcFile.getName());
					createParentAndCopyAsset(srcFile.toPath(), destFile.toPath());
				}
			} else // JSON object
			{
				String srcFilePath = fileJSON.get(AIROptions.FILES__FILE).asText();
				File srcFile = new File(srcFilePath);
				if (!srcFile.isAbsolute()) {
					srcFile = new File(System.getProperty("user.dir"), srcFilePath);
				}
				boolean srcIsDir = srcFile.isDirectory();

				String destFilePath = fileJSON.get(AIROptions.FILES__PATH).asText();
				File destFile = new File(outputDirectory, destFilePath);

				Path relativePath = outputDirectory.toPath().relativize(destFile.toPath());
				try {
					if (relativePath.toString().startsWith("..")
							|| (!srcIsDir && destFile.getCanonicalPath().equals(outputDirectory.getCanonicalPath()))) {
						throw new ASConfigCException(
								"Invalid destination path for file in Adobe AIR application. Source: " + srcFilePath
										+ ", Destination: " + destFilePath);
					}
				} catch (IOException e) {
					throw new ASConfigCException(e.getMessage());
				}

				if (srcIsDir) {
					Set<String> assetPaths = null;
					try {
						List<String> assetDirList = Arrays.asList(srcFile.getAbsolutePath());
						assetPaths = ProjectUtils.findSourcePathAssets(null, assetDirList,
								outputDirectory.getAbsolutePath(), null, null);
					} catch (IOException e) {
						throw new ASConfigCException(e.getMessage());
					}
					for (String assetPath : assetPaths) {
						Path assetPathPath = Paths.get(assetPath);
						Path relativeAssetPath = srcFile.toPath().relativize(assetPathPath);
						if (outputIsJS) {
							Path jsDebugAssetOutputPath = destFile.toPath().resolve(FILE_NAME_BIN_JS_DEBUG)
									.resolve(relativeAssetPath);
							createParentAndCopyAsset(assetPathPath, jsDebugAssetOutputPath);
							if (!debugBuild) {
								Path jsReleaseAssetOutputPath = destFile.toPath().resolve(FILE_NAME_BIN_JS_RELEASE)
										.resolve(relativeAssetPath);
								createParentAndCopyAsset(assetPathPath, jsReleaseAssetOutputPath);
							}
						} else // swf
						{
							Path assetOutputPath = destFile.toPath().resolve(relativeAssetPath);
							createParentAndCopyAsset(assetPathPath, assetOutputPath);
						}
					}
				} else // not a directory
				{
					if (outputIsJS) {
						File outputDirectoryJSDebug = new File(outputDirectory, FILE_NAME_BIN_JS_DEBUG);
						File destFileJSDebug = new File(outputDirectoryJSDebug, destFilePath);
						createParentAndCopyAsset(srcFile.toPath(), destFileJSDebug.toPath());
						if (!debugBuild) {
							File outputDirectoryJSRelease = new File(outputDirectory, FILE_NAME_BIN_JS_RELEASE);
							File destFileJSRelease = new File(outputDirectoryJSRelease, destFilePath);
							createParentAndCopyAsset(srcFile.toPath(), destFileJSRelease.toPath());
						}
					} else // swf
					{
						createParentAndCopyAsset(srcFile.toPath(), destFile.toPath());
					}
				}
			}
		}
	}

	private void copyAIRDescriptor(String descriptorOutputPath, String descriptorContents) throws ASConfigCException {
		File descriptorOutputFile = new File(descriptorOutputPath);
		File descriptorOutputParent = descriptorOutputFile.getParentFile();
		if (!descriptorOutputParent.exists() && !descriptorOutputParent.mkdirs()) {
			throw new ASConfigCException("Failed to copy Adobe AIR application descriptor to path: "
					+ descriptorOutputPath + " because the directories could not be created.");
		}
		try {
			Files.write(descriptorOutputFile.toPath(), descriptorContents.getBytes());
		} catch (IOException e) {
			throw new ASConfigCException(
					"Failed to copy Adobe AIR application descriptor to path: " + descriptorOutputPath);
		}
	}

	private void processAdobeAIRDescriptors() throws ASConfigCException {
		if (!configRequiresAIR) {
			return;
		}
		if (options.verbose) {
			System.out.println("Processing Adobe AIR application descriptor(s)...");
		}
		String templatePath = Paths.get(sdkHome).resolve("templates/air/descriptor-template.xml").toString();
		String templateNamespace = null;
		try {
			String templateContents = new String(Files.readAllBytes(Paths.get(templatePath)));
			templateNamespace = ProjectUtils.findAIRDescriptorNamespace(templateContents);
		} catch (IOException e) {
		}
		boolean populateTemplate = false;
		if (airDescriptorPaths == null || airDescriptorPaths.size() == 0) {
			airDescriptorPaths = new ArrayList<String>();
			airDescriptorPaths.add(templatePath);
			populateTemplate = true;
			if (options.verbose) {
				System.out.println("Using template fallback: " + templatePath);
			}
		}
		String contentValue = ProjectUtils.findApplicationContent(mainFile, outputPathForTarget, !outputIsJS);
		if (contentValue == null) {
			throw new ASConfigCException("Failed to find initial window content for Adobe AIR application.");
		}
		if (options.verbose) {
			System.out.println("Initial window content: " + contentValue);
		}
		for (String airDescriptorPath : airDescriptorPaths) {
			Path resolvedDescriptorPath = Paths.get(airDescriptorPath);
			if (!resolvedDescriptorPath.isAbsolute()) {
				resolvedDescriptorPath = Paths.get(System.getProperty("user.dir")).resolve(resolvedDescriptorPath);
			}
			if (options.verbose) {
				System.out.println("Descriptor: " + resolvedDescriptorPath);
			}
			String descriptorContents = null;
			try {
				descriptorContents = new String(Files.readAllBytes(resolvedDescriptorPath));
			} catch (IOException e) {
				throw new ASConfigCException(
						"Failed to read Adobe AIR application descriptor at path: " + resolvedDescriptorPath);
			}
			if (populateTemplate) {
				String appID = ProjectUtils.generateApplicationID(mainFile, outputPathForTarget,
						System.getProperty("user.dir"));
				if (appID == null) {
					throw new ASConfigCException("Failed to generate application ID for Adobe AIR.");
				}
				if (options.verbose) {
					System.out.println("Generated application ID: " + appID);
				}
				descriptorContents = ProjectUtils.populateAdobeAIRDescriptorTemplate(descriptorContents, appID);

				// clear this so that the name is based on the project name
				airDescriptorPath = null;
			}
			descriptorContents = ProjectUtils.populateAdobeAIRDescriptorContent(descriptorContents, contentValue);
			if (templateNamespace != null) {
				descriptorContents = ProjectUtils.populateAdobeAIRDescriptorNamespace(descriptorContents,
						templateNamespace);
			}
			if (outputIsJS) {
				String debugDescriptorOutputPath = ProjectUtils.findAIRDescriptorOutputPath(mainFile, airDescriptorPath,
						outputPathForTarget, System.getProperty("user.dir"), false, true);
				copyAIRDescriptor(debugDescriptorOutputPath, descriptorContents);
				if (!debugBuild) {
					String releaseDescriptorOutputPath = ProjectUtils.findAIRDescriptorOutputPath(mainFile,
							airDescriptorPath, outputPathForTarget, System.getProperty("user.dir"), false, false);
					copyAIRDescriptor(releaseDescriptorOutputPath, descriptorContents);
				}

			} else // swf
			{
				String descriptorOutputPath = ProjectUtils.findAIRDescriptorOutputPath(mainFile, airDescriptorPath,
						outputPathForTarget, System.getProperty("user.dir"), true, debugBuild);
				if ((outputPathForTarget == null || outputPathForTarget.length() == 0)
						&& (mainFile != null && mainFile.length() > 0)) {
					if (Paths.get(descriptorOutputPath).toFile().exists()) {
						throw new ASConfigCException("Failed to copy Adobe AIR application descriptor template.");
					}
				}
				copyAIRDescriptor(descriptorOutputPath, descriptorContents);
			}
		}
	}

	private void packageAIR() throws ASConfigCException {
		if (options.air == null) {
			return;
		}

		if (options.verbose) {
			System.out.println("Packaging Adobe AIR application...");
		}

		Path jarPath = ProjectUtils.findAdobeAIRPackagerJarPath(sdkHome);
		if (jarPath == null) {
			throw new ASConfigCException("AIR ADT not found in SDK. Expected: " + Paths.get(sdkHome, "lib", "adt.jar"));
		}

		// if the certificate password isn't already specified, ask for it and add it
		int passwordIndex = airOptions.indexOf("-" + AIRSigningOptions.STOREPASS);
		if (passwordIndex == -1) {
			int keystoreIndex = airOptions.indexOf("-" + AIRSigningOptions.KEYSTORE);
			if (keystoreIndex != -1) {
				String storepass = options.storepass;
				if (storepass == null) {
					// ask for password if keystore is specified in airOptions,
					// but storepass is not passed to asconfigc
					Console console = System.console();
					char[] password = console.readPassword("Adobe AIR code signing password: ");
					storepass = new String(password);
				}
				airOptions.add(keystoreIndex + 2, "-" + AIRSigningOptions.STOREPASS);
				airOptions.add(keystoreIndex + 3, storepass);
			}
		}

		Path javaExecutablePath = Paths.get(System.getProperty("java.home"), "bin", "java");
		airOptions.add(0, jarPath.toString());
		airOptions.add(0, "-jar");
		airOptions.add(0, javaExecutablePath.toString());
		if (options.verbose) {
			System.out.println(String.join(" ", airOptions));
		}
		try {
			File cwd = new File(System.getProperty("user.dir"));
			Process process = new ProcessBuilder().command(airOptions).directory(cwd).inheritIO().start();
			int status = process.waitFor();
			if (status != 0) {
				throw new ASConfigCException(status);
			}
		} catch (InterruptedException e) {
			throw new ASConfigCException("Failed to execute Adobe AIR packager: " + e.getMessage());
		} catch (IOException e) {
			throw new ASConfigCException("Failed to execute Adobe AIR Packager: " + e.getMessage());
		}
	}
}