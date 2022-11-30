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
package com.as3mxml.vscode.project;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.as3mxml.asconfigc.TopLevelFields;
import com.as3mxml.asconfigc.compiler.CompilerOptions;
import com.as3mxml.asconfigc.compiler.CompilerOptionsParser;
import com.as3mxml.asconfigc.compiler.ProjectType;
import com.as3mxml.asconfigc.compiler.CompilerOptionsParser.UnknownCompilerOptionException;
import com.as3mxml.asconfigc.utils.ConfigUtils;
import com.as3mxml.asconfigc.utils.JsonUtils;
import com.as3mxml.asconfigc.utils.OptionsUtils;
import com.as3mxml.vscode.utils.ActionScriptSDKUtils;

import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.WorkspaceFolder;

/**
 * Configures a project using an asconfig.json file.
 */
public class ASConfigProjectConfigStrategy implements IProjectConfigStrategy {
    private static final String ASCONFIG_JSON = "asconfig.json";
    private static final String PROPERTY_FRAMEWORK_LIB = "royalelib";
    private static final String CONFIG_ROYALE = "royale";
    private static final String CONFIG_FLEX = "flex";

    private WorkspaceFolder workspaceFolder;
    private Path projectPath;
    private Path asconfigPath;
    private boolean changed = true;
    private List<String> tokens = null;

    public ASConfigProjectConfigStrategy(Path projectPath, WorkspaceFolder workspaceFolder) {
        this.projectPath = projectPath;
        this.workspaceFolder = workspaceFolder;

        asconfigPath = projectPath.resolve(ASCONFIG_JSON);
    }

    public String getDefaultConfigurationProblemPath() {
        return ASCONFIG_JSON;
    }

    public WorkspaceFolder getWorkspaceFolder() {
        return workspaceFolder;
    }

    public Path getProjectPath() {
        return projectPath;
    }

    public Path getConfigFilePath() {
        return asconfigPath;
    }

    public void setASConfigPath(Path value) {
        asconfigPath = value;
    }

    public boolean getChanged() {
        return changed;
    }

    public void forceChanged() {
        changed = true;
    }

    public ProjectOptions getOptions() {
        changed = false;
        if (asconfigPath == null) {
            return null;
        }
        File asconfigFile = asconfigPath.toFile();
        if (!asconfigFile.exists()) {
            return null;
        }
        Path sdkPath = Paths.get(System.getProperty(PROPERTY_FRAMEWORK_LIB));
        boolean isRoyale = ActionScriptSDKUtils.isRoyaleFramework(sdkPath);
        Path projectRoot = asconfigPath.getParent();
        String projectType = ProjectType.APP;
        String config = CONFIG_FLEX;
        if (isRoyale) {
            config = CONFIG_ROYALE;
        }
        String mainClass = null;
        String[] files = new String[0];
        List<String> additionalOptions = null;
        List<String> compilerOptions = null;
        List<String> targets = null;
        List<String> sourcePaths = null;
        JsonSchema schema = null;
        try (InputStream schemaInputStream = getClass().getResourceAsStream("/schemas/asconfig.schema.json")) {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(VersionFlag.V7);
            schema = factory.getSchema(schemaInputStream);
        } catch (Exception e) {
            // this exception is unexpected, so it should be reported
            System.err.println("Failed to load asconfig.json schema: " + e);
            e.printStackTrace(System.err);
            return null;
        }
        JsonNode json = null;
        try {
            String contents = FileUtils.readFileToString(asconfigFile);
            ObjectMapper mapper = new ObjectMapper();
            // VSCode allows comments, so we should too
            mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
            mapper.configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true);
            json = mapper.readTree(contents);
            Set<ValidationMessage> errors = schema.validate(json);
            if (!errors.isEmpty()) {
                // don't print anything to the console. the editor will validate
                // and display any errors, if necessary.
                return null;
            }
        } catch (Exception e) {
            // this exception is expected sometimes if the JSON is invalid
            return null;
        }
        try {
            if (json.has(TopLevelFields.TYPE)) // optional, defaults to "app"
            {
                projectType = json.get(TopLevelFields.TYPE).asText();
            }
            if (json.has(TopLevelFields.CONFIG)) // optional, defaults to "flex"
            {
                config = json.get(TopLevelFields.CONFIG).asText();
            }
            if (json.has(TopLevelFields.FILES)) // optional
            {
                JsonNode jsonFiles = json.get(TopLevelFields.FILES);
                int fileCount = jsonFiles.size();
                files = new String[fileCount];
                for (int i = 0; i < fileCount; i++) {
                    String pathString = jsonFiles.get(i).asText();
                    Path filePath = projectRoot.resolve(pathString);
                    files[i] = filePath.toString();
                }
            }
            if (json.has(TopLevelFields.COMPILER_OPTIONS)) // optional
            {
                compilerOptions = new ArrayList<>();
                JsonNode jsonCompilerOptions = json.get(TopLevelFields.COMPILER_OPTIONS);
                CompilerOptionsParser parser = new CompilerOptionsParser();
                parser.parse(jsonCompilerOptions, null, compilerOptions);

                // while the following compiler options will be included in the
                // result above, we need to parse them separately because the
                // language server needs to check their specific values for
                // certain behaviors.
                if (jsonCompilerOptions.has(CompilerOptions.TARGETS)) {
                    targets = new ArrayList<>();
                    JsonNode jsonTargets = jsonCompilerOptions.get(CompilerOptions.TARGETS);
                    for (int i = 0, count = jsonTargets.size(); i < count; i++) {
                        String target = jsonTargets.get(i).asText();
                        targets.add(target);
                    }
                }
                // we use this to resolve the mainClass
                if (jsonCompilerOptions.has(CompilerOptions.SOURCE_PATH)) {
                    JsonNode sourcePath = jsonCompilerOptions.get(CompilerOptions.SOURCE_PATH);
                    sourcePaths = JsonUtils.jsonNodeToListOfStrings(sourcePath);
                }
            }
            if (projectType.equals(ProjectType.APP) && json.has(TopLevelFields.MAIN_CLASS)) {
                mainClass = json.get(TopLevelFields.MAIN_CLASS).asText();
                String resolvedMainClass = ConfigUtils.resolveMainClass(mainClass, sourcePaths, projectRoot.toString());
                if (resolvedMainClass != null) {
                    Path mainClassPath = Paths.get(resolvedMainClass);
                    mainClassPath = projectRoot.resolve(resolvedMainClass);
                    files = Arrays.copyOf(files, files.length + 1);
                    files[files.length - 1] = mainClassPath.toString();
                }
            }
            // these options are formatted as if sent in through the command line
            if (json.has(TopLevelFields.ADDITIONAL_OPTIONS)) // optional
            {
                additionalOptions = new ArrayList<>();
                JsonNode jsonAdditionalOptions = json.get(TopLevelFields.ADDITIONAL_OPTIONS);
                if (jsonAdditionalOptions.isArray()) {
                    Iterator<JsonNode> iterator = jsonAdditionalOptions.elements();
                    while (iterator.hasNext()) {
                        JsonNode jsonOption = iterator.next();
                        additionalOptions.add(jsonOption.asText());
                    }
                } else {
                    String additionalOptionsText = jsonAdditionalOptions.asText();
                    if (additionalOptionsText != null) {
                        // split the additionalOptions into separate values so that we can
                        // pass them in as String[], as the compiler expects.
                        additionalOptions.addAll(OptionsUtils.parseAdditionalOptions(additionalOptionsText));
                    }
                }
            }
        } catch (UnknownCompilerOptionException e) {
            // there's a compiler option that the parser doesn't recognize
            return null;
        } catch (Exception e) {
            // this exception is unexpected, so it should be reported
            System.err.println("Failed to parse asconfig.json: " + e);
            e.printStackTrace(System.err);
            return null;
        }
        // in a library project, the files field will be treated the same as the
        // include-sources compiler option
        if (projectType.equals(ProjectType.LIB) && files != null) {
            for (int i = 0, count = files.length; i < count; i++) {
                String filePath = files[i];
                compilerOptions.add("--include-sources+=" + filePath);
            }
            files = null;
        }
        ProjectOptions options = new ProjectOptions();
        options.type = projectType;
        options.config = config;
        options.files = files;
        options.compilerOptions = compilerOptions;
        options.additionalOptions = additionalOptions;
        options.targets = targets;
        options.additionalTokens = tokens;
        return options;
    }

    public void setTokens(List<String> tokens) {
        this.tokens = tokens;
    }
}
