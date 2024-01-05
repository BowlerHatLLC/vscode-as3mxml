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
package com.as3mxml.vscode.providers;

import java.nio.file.Path;
import java.util.List;

import org.apache.royale.compiler.config.ConfigurationPathResolver;
import org.apache.royale.compiler.problems.ICompilerProblem;
import org.apache.royale.linter.ASLinter;
import org.apache.royale.linter.LinterSettings;
import org.apache.royale.linter.LinterUtils;
import org.apache.royale.linter.MXMLLinter;
import org.apache.royale.linter.config.Configuration;
import org.apache.royale.linter.config.Configurator;

import com.as3mxml.vscode.utils.FileTracker;

public class LintingProvider {
	private static final String FILE_EXTENSION_MXML = ".mxml";
	private static final String FILE_EXTENSION_AS = ".as";

	private FileTracker fileTracker;

	public LintingProvider(FileTracker fileTracker) {
		this.fileTracker = fileTracker;
	}

	public void linting(Path filePath, List<ICompilerProblem> problems) {
		String fileText = fileTracker.getText(filePath);
		if (fileText == null) {
			return;
		}
		Configurator configurator = new Configurator();
		ConfigurationPathResolver resolver = new ConfigurationPathResolver(System.getProperty("user.dir"));
		configurator.setConfigurationPathResolver(resolver);
		configurator.setConfiguration(new String[0], "files");
		Configuration configuration = configurator.getConfiguration();
		LinterSettings settings = LinterUtils.configurationToLinterSettings(configuration);
		String stringFilePath = filePath.toString();
		if (stringFilePath.endsWith(FILE_EXTENSION_AS)) {
			ASLinter linter = new ASLinter(settings);
			linter.lint(stringFilePath, fileText, problems);
		} else if (stringFilePath.endsWith(FILE_EXTENSION_MXML)) {
			MXMLLinter linter = new MXMLLinter(settings);
			linter.lint(stringFilePath, fileText, problems);
		}
	}

}
