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
package com.as3mxml.asconfigc.compiler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.as3mxml.asconfigc.ASConfigCException;
import com.as3mxml.asconfigc.utils.ApacheRoyaleUtils;
import com.as3mxml.asconfigc.utils.ProjectUtils;

public class DefaultCompiler implements IASConfigCCompiler {
	public DefaultCompiler() {
		this(false, null);
	}

	public DefaultCompiler(boolean verbose, List<String> jvmargs) {
		this.verbose = verbose;
		this.jvmargs = jvmargs;
	}

	private boolean verbose = false;
	private List<String> jvmargs = null;

	public void compile(String projectType, List<String> compilerOptions, Path workspaceRoot, Path sdkPath)
			throws ASConfigCException {
		if (verbose) {
			if (ProjectType.LIB.equals(projectType)) {
				System.out.println("Compiling library...");
			} else //app
			{
				System.out.println("Compiling application...");
			}
		}
		boolean sdkIsRoyale = ApacheRoyaleUtils.isValidSDK(sdkPath) != null;
		Path jarPath = ProjectUtils.findCompilerJarPath(projectType, sdkPath.toString(), !sdkIsRoyale);
		if (jarPath == null) {
			throw new ASConfigCException("Compiler not found in SDK. Expected in SDK: " + sdkPath);
		}
		Path frameworkPath = sdkPath.resolve("frameworks");
		if (sdkIsRoyale) {
			//royale is a special case that has renamed many of the common
			//configuration options for the compiler
			compilerOptions.add(0, "+royalelib=" + frameworkPath.toString());
			compilerOptions.add(0, jarPath.toString());
			compilerOptions.add(0, "-jar");
			compilerOptions.add(0, "-Droyalelib=" + frameworkPath.toString());
			compilerOptions.add(0, "-Droyalecompiler=" + sdkPath.toString());
			//Royale requires this so that it doesn't changing the encoding of
			//UTF-8 characters and display ???? instead
			compilerOptions.add(0, "-Dfile.encoding=UTF8");
		} else {
			//other SDKs all use the same options
			compilerOptions.add(0, "+flexlib=" + frameworkPath.toString());
			compilerOptions.add(0, jarPath.toString());
			compilerOptions.add(0, "-jar");
			compilerOptions.add(0, "-Dflexlib=" + frameworkPath.toString());
			compilerOptions.add(0, "-Dflexcompiler=" + sdkPath.toString());
		}
		if (jvmargs != null) {
			compilerOptions.addAll(0, jvmargs);
		}
		boolean isMacOS = System.getProperty("os.name").toLowerCase().startsWith("mac os");
		if (isMacOS) {
			compilerOptions.add(0, "-Dapple.awt.UIElement=true");
		}
		Path javaExecutablePath = Paths.get(System.getProperty("java.home"), "bin", "java");
		compilerOptions.add(0, javaExecutablePath.toString());

		if (verbose) {
			System.out.println(String.join(" ", compilerOptions));
		}
		try {
			File cwd = new File(System.getProperty("user.dir"));
			Process process = new ProcessBuilder().command(compilerOptions).directory(cwd).inheritIO().start();
			int status = process.waitFor();
			if (status != 0) {
				throw new ASConfigCException(status);
			}
		} catch (InterruptedException e) {
			throw new ASConfigCException("Failed to execute compiler: " + e.getMessage());
		} catch (IOException e) {
			throw new ASConfigCException("Failed to execute compiler: " + e.getMessage());
		}
	}
}