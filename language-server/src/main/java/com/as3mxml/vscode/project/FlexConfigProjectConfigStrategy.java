package com.as3mxml.vscode.project;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.WorkspaceFolder;

import com.as3mxml.asconfigc.compiler.ProjectType;
import com.as3mxml.vscode.utils.ActionScriptSDKUtils;

public class FlexConfigProjectConfigStrategy implements IProjectConfigStrategy {
	private static final String FILE_EXTENSION_MXML = ".mxml";
	private static final String FILE_EXTENSION_AS = ".as";
	private static final String PREFIX_CONFIG_XML = "-config.xml";
	private static final String[] SRC_DIRECTORIES = {
			"src/main/flex",
			"src/main/royale",
			"src",
	};
	private static final String CONFIG_ROYALE = "royale";
	private static final String CONFIG_FLEX = "flex";
	private static final String PROPERTY_FRAMEWORK_LIB = "royalelib";

	public static Path findConfigXml(Path projectPath) {
		for (String srcDir : SRC_DIRECTORIES) {
			Path srcPath = projectPath.resolve(srcDir);
			if (Files.exists(srcPath) && Files.isDirectory(srcPath)) {
				try {
					Optional<Path> discoveredConfigPath = Files.list(srcPath)
							.filter(path -> {
								boolean isConfigXml = path.getFileName().toString().endsWith(PREFIX_CONFIG_XML);
								if (!isConfigXml) {
									return false;
								}
								Path mainFilePath = findMainFilePath(path);
								return mainFilePath != null;
							}).findFirst();
					if (discoveredConfigPath.isPresent()) {
						return discoveredConfigPath.get();
					}
				} catch (IOException e) {
					continue;
				}
			}
		}
		return null;
	}

	private static Path findMainFilePath(Path configFilePath) {
		if (configFilePath == null) {
			return null;
		}
		Path parentDir = configFilePath.getParent();
		String mainFileName = configFilePath.getFileName().toString();
		mainFileName = mainFileName.substring(0, mainFileName.length() - PREFIX_CONFIG_XML.length());
		Path mainFilePath = parentDir.resolve(mainFileName + FILE_EXTENSION_MXML);
		if (!Files.exists(mainFilePath)) {
			mainFilePath = parentDir.resolve(mainFileName + FILE_EXTENSION_AS);
			if (!Files.exists(mainFilePath)) {
				return null;
			}
		}
		return mainFilePath;
	}

	private WorkspaceFolder workspaceFolder;
	private Path projectPath;
	private Path configXmlPath;
	private Path mainFilePath;
	private boolean changed = true;

	public FlexConfigProjectConfigStrategy(Path projectPath, WorkspaceFolder workspaceFolder) {
		this.projectPath = projectPath;
		this.workspaceFolder = workspaceFolder;
	}

	public boolean isSupportedForProject(Path projectPath) {
		return findConfigXml(projectPath) != null;
	}

	public String getDefaultConfigurationProblemPath() {
		Path configXmlPath = getConfigFilePath();
		if (configXmlPath != null) {
			return configXmlPath.toString();
		}
		return projectPath.toString();
	}

	public WorkspaceFolder getWorkspaceFolder() {
		return workspaceFolder;
	}

	public Path getProjectPath() {
		return projectPath;
	}

	public Path getConfigFilePath() {
		configXmlPath = findConfigXml(projectPath);
		if (configXmlPath != null) {
			mainFilePath = findMainFilePath(configXmlPath);
			if (mainFilePath == null) {
				configXmlPath = null;
			}
		}
		return configXmlPath;
	}

	public void setConfigFilePath(Path value) {
		configXmlPath = value;
	}

	public boolean getChanged() {
		return changed;
	}

	public void forceChanged() {
		changed = true;
	}

	public ProjectOptions getOptions() {
		changed = false;
		Path configXmlPath = getConfigFilePath();
		if (configXmlPath == null) {
			return null;
		}
		File configXmlFile = configXmlPath.toFile();
		if (!configXmlFile.exists()) {
			return null;
		}

		Path frameworkPath = Paths.get(System.getProperty(PROPERTY_FRAMEWORK_LIB));
		boolean isRoyale = ActionScriptSDKUtils.isRoyaleFramework(frameworkPath);

		String projectType = ProjectType.APP;
		String config = CONFIG_FLEX;
		if (isRoyale) {
			config = CONFIG_ROYALE;
		}
		String mainClass = null;
		List<String> additionalOptions = null;
		List<String> compilerOptions = null;
		List<String> targets = null;

		ProjectOptions options = new ProjectOptions();
		options.type = projectType;
		options.config = config;
		options.files = new String[] { mainFilePath.toString() };
		options.mainClass = mainClass;
		options.compilerOptions = compilerOptions;
		options.additionalOptions = additionalOptions;
		options.targets = targets;
		return options;
	}
}
