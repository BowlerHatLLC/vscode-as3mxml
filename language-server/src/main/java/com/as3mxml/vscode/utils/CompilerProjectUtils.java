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
package com.as3mxml.vscode.utils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.as3mxml.asconfigc.compiler.ProjectType;
import com.as3mxml.vscode.project.ILspProject;
import com.as3mxml.vscode.project.LspJSProject;
import com.as3mxml.vscode.project.LspProject;
import com.as3mxml.vscode.project.ProjectOptions;
import com.as3mxml.vscode.project.VSCodeConfiguration;

import org.apache.royale.compiler.clients.MXMLJSC;
import org.apache.royale.compiler.config.ICompilerSettingsConstants;
import org.apache.royale.compiler.driver.IBackend;
import org.apache.royale.compiler.internal.driver.js.goog.JSGoogConfiguration;
import org.apache.royale.compiler.internal.driver.js.jsc.JSCBackend;
import org.apache.royale.compiler.internal.driver.js.node.NodeBackend;
import org.apache.royale.compiler.internal.driver.js.node.NodeModuleBackend;
import org.apache.royale.compiler.internal.driver.js.royale.RoyaleBackend;
import org.apache.royale.compiler.internal.projects.RoyaleJSProject;
import org.apache.royale.compiler.internal.projects.RoyaleProjectConfigurator;
import org.apache.royale.compiler.internal.workspaces.Workspace;
import org.apache.royale.compiler.projects.ICompilerProject;
import org.apache.royale.compiler.units.ICompilationUnit;

public class CompilerProjectUtils {
    private static final String CONFIG_ROYALE = "royale";
    private static final String CONFIG_JS = "js";
    private static final String CONFIG_NODE = "node";

    private static final String TOKEN_CONFIGNAME = "configname";
    private static final String TOKEN_ROYALELIB = "royalelib";
    private static final String TOKEN_FLEXLIB = "flexlib";

    private static final String PROPERTY_FRAMEWORK_LIB = "royalelib";

    public static ILspProject createProject(ProjectOptions currentProjectOptions, Workspace compilerWorkspace) {
        Path frameworkLibPath = Paths.get(System.getProperty(PROPERTY_FRAMEWORK_LIB));
        boolean frameworkSDKIsRoyale = ActionScriptSDKUtils.isRoyaleFramework(frameworkLibPath);

        Path asjscPath = frameworkLibPath.resolve("../js/bin/asjsc");
        boolean frameworkSDKIsFlexJS = !frameworkSDKIsRoyale && asjscPath.toFile().exists();

        ILspProject project = null;

        //we're going to try to determine what kind of project we need
        //(either Royale or everything else). if it's a Royale project, we
        //should choose an appropriate backend.
        IBackend backend = null;

        //first, start by looking if the targets compiler option is
        //specified. if it is, we definitely have a Royale project. we'll
        //use the first target value as the indicator of what the user
        //thinks is most important for code intelligence (native JS classes
        //or native SWF classes?)
        //this isn't ideal because it would be better if we could provide
        //code intelligence for all targets simultaneously, but this is a
        //limitation that we need to live with, for now.
        List<String> targets = currentProjectOptions.targets;
        if (targets != null && targets.size() > 0) {
            //first, check if any targets are specified
            String firstTarget = targets.get(0);
            switch (MXMLJSC.JSTargetType.fromString(firstTarget)) {
                case SWF: {
                    //no backend. fall back to RoyaleProject.
                    backend = null;
                    break;
                }
                case JS_NATIVE: {
                    backend = new JSCBackend();
                    break;
                }
                case JS_NODE: {
                    backend = new NodeBackend();
                    break;
                }
                case JS_NODE_MODULE: {
                    backend = new NodeModuleBackend();
                    break;
                }
                default: {
                    //it actually shouldn't matter too much which JS
                    //backend is used when we're only using the project for
                    //code intelligence, so this is probably an acceptable
                    //fallback for just about everything.
                    //we just want to rule out SWF.
                    backend = new RoyaleBackend();
                    break;
                }
            }
        }
        //if no targets are specified, we can guess whether it's a Royale
        //project based on the config value.
        else if (currentProjectOptions.config.equals(CONFIG_ROYALE)) {
            backend = new RoyaleBackend();
        } else if (currentProjectOptions.config.equals(CONFIG_JS)) {
            backend = new JSCBackend();
        } else if (currentProjectOptions.config.equals(CONFIG_NODE)) {
            backend = new NodeBackend();
        }
        //finally, if the config value is missing, then choose a decent
        //default backend when the SDK is Royale
        else if (frameworkSDKIsRoyale || frameworkSDKIsFlexJS) {
            backend = new RoyaleBackend();
        }

        //if we created a backend, it's a Royale project (RoyaleJSProject)
        if (backend != null) {
            project = new LspJSProject(compilerWorkspace, backend);
        }
        //if we haven't created the project yet, then it's not Royale and
        //the project should be one that doesn't require a backend.
        if (project == null) {
            //yes, this is called RoyaleProject, but a *real* Royale project
            //is RoyaleJSProject...
            //RoyaleProject is for projects targeting the SWF format.
            project = new LspProject(compilerWorkspace);
        }
        project.setProblems(new ArrayList<>());
        return project;
    }

    public static RoyaleProjectConfigurator createConfigurator(ILspProject project, ProjectOptions projectOptions) {
        final Path frameworkLibPath = Paths.get(System.getProperty(PROPERTY_FRAMEWORK_LIB));
        final boolean frameworkSDKIsRoyale = ActionScriptSDKUtils.isRoyaleFramework(frameworkLibPath);

        //check if the framework SDK doesn't include the Spark theme
        Path sparkPath = frameworkLibPath.resolve("./themes/Spark/spark.css");
        boolean frameworkSDKContainsSparkTheme = sparkPath.toFile().exists();

        List<String> compilerOptions = projectOptions.compilerOptions;
        RoyaleProjectConfigurator configurator = null;
        if (project instanceof RoyaleJSProject || frameworkSDKIsRoyale) {
            configurator = new RoyaleProjectConfigurator(JSGoogConfiguration.class);
        } else //swf only
        {
            configurator = new RoyaleProjectConfigurator(VSCodeConfiguration.class);
        }
        if (frameworkSDKIsRoyale) {
            configurator.setToken(TOKEN_ROYALELIB, System.getProperty(PROPERTY_FRAMEWORK_LIB));
        } else //not royale
        {
            configurator.setToken(TOKEN_FLEXLIB, System.getProperty(PROPERTY_FRAMEWORK_LIB));
        }
        configurator.setToken(TOKEN_CONFIGNAME, projectOptions.config);
        String projectType = projectOptions.type;
        String[] files = projectOptions.files;
        List<String> additionalOptions = projectOptions.additionalOptions;
        ArrayList<String> combinedOptions = new ArrayList<>();
        if (compilerOptions != null) {
            combinedOptions.addAll(compilerOptions);
        }
        if (additionalOptions != null) {
            combinedOptions.addAll(additionalOptions);
        }

        //Github #245: avoid errors from -inline
        combinedOptions.removeIf((option) -> {
            return option.equals("-inline") || option.equals("--inline") || option.equals("-inline=true")
                    || option.equals("--inline=true");
        });

        //not all framework SDKs support a theme (such as Adobe's AIR SDK), so
        //we clear it for the editor to avoid a missing spark.css file.
        if (!frameworkSDKContainsSparkTheme) {
            combinedOptions.add("-theme=");
        }
        if (projectType.equals(ProjectType.LIB)) {
            configurator.setConfiguration(combinedOptions.toArray(new String[combinedOptions.size()]),
                    ICompilerSettingsConstants.INCLUDE_CLASSES_VAR, false);
        } else // app
        {
            combinedOptions.add("--");
            if (files != null && files.length > 0) {
                combinedOptions.addAll(Arrays.asList(files));
            }
            configurator.setConfiguration(combinedOptions.toArray(new String[combinedOptions.size()]),
                    ICompilerSettingsConstants.FILE_SPECS_VAR);
        }
        //this needs to be set before applyToProject() so that it's in the
        //configuration buffer before addExternalLibraryPath() is called
        configurator.setExcludeNativeJSLibraries(false);
        Path appendConfigPath = frameworkLibPath.resolve("../ide/vscode-as3mxml/vscode-as3mxml-config.xml");
        File appendConfigFile = appendConfigPath.toFile();
        if (appendConfigFile.exists()) {
            configurator.addConfiguration(appendConfigFile);
        } else {
            //fallback for backwards compatibility
            appendConfigPath = frameworkLibPath.resolve("../ide/vscode-nextgenas/vscode-nextgenas-config.xml");
            appendConfigFile = appendConfigPath.toFile();
            if (appendConfigFile.exists()) {
                configurator.addConfiguration(appendConfigFile);
            }
        }
        return configurator;
    }

    public static ICompilationUnit findCompilationUnit(Path pathToFind, ICompilerProject project) {
        if (project == null) {
            return null;
        }
        for (ICompilationUnit unit : project.getCompilationUnits()) {
            //it's possible for the collection of compilation units to contain
            //null values, so be sure to check for null values before checking
            //the file name
            if (unit == null) {
                continue;
            }
            Path unitPath = Paths.get(unit.getAbsoluteFilename());
            if (unitPath.equals(pathToFind)) {
                return unit;
            }
        }
        return null;
    }
}