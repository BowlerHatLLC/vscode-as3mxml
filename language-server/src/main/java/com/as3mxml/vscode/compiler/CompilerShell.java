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
package com.as3mxml.vscode.compiler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.as3mxml.asconfigc.ASConfigCException;
import com.as3mxml.asconfigc.compiler.IASConfigCCompiler;
import com.as3mxml.asconfigc.compiler.ProjectType;
import com.as3mxml.vscode.services.ActionScriptLanguageClient;
import com.as3mxml.vscode.utils.ActionScriptSDKUtils;

public class CompilerShell implements IASConfigCCompiler {
    private static final String ERROR_COMPILER_SHELL_NOT_FOUND = "Quick Compile requires the Adobe AIR SDK & Compiler or Apache Royale. Please choose a different SDK or build using a standard task.";
    private static final String ERROR_COMPILER_SHELL_START = "Quick Compile failed. Error starting compiler shell.";
    private static final String ERROR_COMPILER_SHELL_WRITE = "Quick Compile failed. Error writing to compiler shell.";
    private static final String ERROR_COMPILER_SHELL_READ = "Quick Compile failed. Error reading from compiler shell.";
    private static final String ERROR_COMPILER_ERRORS_FOUND = "Quick Compile failed. Errors in compiler output.";
    private static final String ERROR_COMPILER_ACTIVE = "Quick compile failed because the compiler has not yet completed a previous task.";
    private static final String COMMAND_COMPILE = "compile";
    private static final String COMMAND_CLEAR = "clear";
    private static final String COMMAND_QUIT = "quit\n";
    private static final String ASSIGNED_ID_PREFIX = "fcsh: Assigned ";
    private static final String ASSIGNED_ID_SUFFIX = " as the compile target id";
    private static final String OUTPUT_PROBLEM_TYPE_ERROR = "Error: ";
    private static final String OUTPUT_PROBLEM_TYPE_SYNTAX_ERROR = "Syntax error: ";
    private static final String OUTPUT_PROBLEM_TYPE_INTERNAL_ERROR = "Internal error: ";
    private static final String COMPILER_SHELL_PROMPT = "(fcsh) ";
    private static final String FILE_NAME_RCSH = "rcsh.jar";
    private static final String FILE_NAME_ASCSH = "ascsh.jar";
    private static final String CLASS_RCSH = "com.as3mxml.vscode.rcsh.RCSH";
    private static final String CLASS_ASCSH = "ascsh";
    private static final String EXECUTABLE_MXMLC = "mxmlc";
    private static final String EXECUTABLE_COMPC = "compc";

    private ActionScriptLanguageClient languageClient;
    private Process process;
    private String compileID;
    private String previousCommand;
    private Path previousSDKPath;
    private Path rcshPath;
    private Path ascshPath;
    private boolean isRoyale = false;
    private boolean isAIR = false;
    private List<String> jvmargs = null;
    private boolean active = false;

    public CompilerShell(ActionScriptLanguageClient languageClient, List<String> jvmargs) throws URISyntaxException {
        this.languageClient = languageClient;
        this.jvmargs = jvmargs;
        URI uri = getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
        Path binPath = Paths.get(uri).getParent().normalize();
        rcshPath = binPath.resolve(FILE_NAME_RCSH);
        ascshPath = binPath.resolve(FILE_NAME_ASCSH);
    }

    public void compile(String projectType, List<String> compilerOptions, Path workspaceRoot, Path sdkPath)
            throws ASConfigCException {
        if (active) {
            throw new ASConfigCException(ERROR_COMPILER_ACTIVE);
        }
        try {
            active = true;
            isRoyale = ActionScriptSDKUtils.isRoyaleSDK(sdkPath);
            isAIR = ActionScriptSDKUtils.isAIRSDK(sdkPath);

            String oldCompileID = compileID;

            boolean isFCSH = !isRoyale && !isAIR;
            if (isFCSH) {
                // fcsh has a bug when run in Java 8 or newer that causes
                // exceptions to be thrown after multiple builds.
                // we can force a fresh build and still gain partial performance
                // improvement from keeping the compiler process loaded in memory.
                compileID = null;
            }
            boolean sdkChanged = previousSDKPath != null && !previousSDKPath.equals(sdkPath);
            if (sdkChanged) {
                // we need to start a different compiler shell process with the new
                // SDK, so the old compileID is no longer valid
                compileID = null;
            }
            previousSDKPath = sdkPath;

            String command = getCommand(projectType, compilerOptions);

            boolean compileIDChanged = oldCompileID != null && compileID == null;
            if (process != null && (compileIDChanged || sdkChanged)) {
                if (sdkChanged) {
                    // we need to start a different compiler shell process with the
                    // new SDK
                    quit();
                } else if (isFCSH) {
                    // we don't need to restart. we only need to clear.
                    String clearCommand = getClearCommand(oldCompileID);
                    executeCommandAndWaitForPrompt(clearCommand);
                } else {
                    // if we have a new command, start with a fresh instance of the
                    // compiler shell.
                    quit();
                }
            }
            startProcess(sdkPath, workspaceRoot);
            executeCommandAndWaitForPrompt(command, true);
        } finally {
            active = false;
        }
    }

    public void buildASDoc(String projectType, String swcToOutputTo, List<String> compilerOptions, Path workspaceRoot,
            Path sdkPath) {

    }

    public void dispose() {
        if (process == null) {
            return;
        }
        try {
            quit();
        } catch (ASConfigCException e) {

        }
    }

    private void quit() throws ASConfigCException {
        // we don't need to wait for the prompt because we'll just wait
        // for the process to end.
        executeCommand(COMMAND_QUIT);
        try {
            Process oldProcess = process;
            process = null;
            int exitCode = oldProcess.waitFor();
            languageClient.logCompilerShellOutput("Compiler shell exited with code: " + exitCode + "\n");
        } catch (InterruptedException e) {
            e.printStackTrace(System.err);
        }
    }

    private void startProcess(Path sdkPath, Path workspaceRoot) throws ASConfigCException {
        Path compilerShellPath = null;

        if (isRoyale) {
            compilerShellPath = rcshPath;
        } else if (isAIR) {
            compilerShellPath = ascshPath;
        } else {
            Path fcshPath = sdkPath.resolve("lib/fcsh.jar");
            if (fcshPath.toFile().exists()) {
                compilerShellPath = fcshPath;
            } else {
                throw new ASConfigCException(ERROR_COMPILER_SHELL_NOT_FOUND);
            }
        }

        if (process != null) {
            languageClient.clearCompilerShellOutput();
            languageClient.logCompilerShellOutput(COMPILER_SHELL_PROMPT);
            return;
        }

        String classPath = null;
        if (isRoyale || isAIR) {
            StringBuilder builder = new StringBuilder();
            if (isRoyale) {
                builder.append(sdkPath.resolve("lib/").toString());
                builder.append(File.separator);
                builder.append("*");
                builder.append(File.pathSeparator);
                builder.append(sdkPath.resolve("js/lib/").toString());
                builder.append(File.separator);
                builder.append("*");
                builder.append(File.pathSeparator);
            } else if (isAIR) {
                // we can't use * here because it might load a newer version of Guava
                // which will result in strange errors
                builder.append(sdkPath.resolve("lib/compiler.jar").toString());
                builder.append(File.pathSeparator);
            }
            builder.append(compilerShellPath.toAbsolutePath().toString());
            classPath = builder.toString();
        }

        Path javaExecutablePath = Paths.get(System.getProperty("java.home"), "bin", "java");
        ArrayList<String> options = new ArrayList<>();
        options.add(javaExecutablePath.toString());
        if (jvmargs != null) {
            options.addAll(jvmargs);
        }
        boolean isMacOS = System.getProperty("os.name").toLowerCase().startsWith("mac os");
        if (isMacOS) {
            options.add("-Dapple.awt.UIElement=true");
        }
        if (isRoyale) {
            // Royale requires this so that it doesn't changing the encoding of
            // UTF-8 characters and display ???? instead
            options.add("-Dfile.encoding=UTF8");
        }
        options.add("-Dsun.io.useCanonCaches=false");
        options.add("-Duser.language=en");
        options.add("-Duser.region=en");
        options.add("-Dapplication.home=" + sdkPath);
        options.add("-Dtrace.error=true");
        if (classPath != null) {
            options.add("-cp");
            options.add(classPath.toString());
            if (isRoyale) {
                options.add(CLASS_RCSH);
            } else if (isAIR) {
                options.add(CLASS_ASCSH);
            }
        } else // fcsh
        {
            options.add("-jar");
            options.add(compilerShellPath.toAbsolutePath().toString());
        }
        try {
            process = new ProcessBuilder().command(options).directory(workspaceRoot.toFile()).start();
        } catch (IOException e) {
            e.printStackTrace(System.err);
            throw new ASConfigCException(ERROR_COMPILER_SHELL_START);
        }

        waitForPrompt();
    }

    private void executeCommand(String command) throws ASConfigCException {
        languageClient.logCompilerShellOutput(command);

        OutputStream outputStream = process.getOutputStream();
        try {
            outputStream.write(command.getBytes());
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace(System.err);
            throw new ASConfigCException(ERROR_COMPILER_SHELL_WRITE);
        }
    }

    private void executeCommandAndWaitForPrompt(String command) throws ASConfigCException {
        executeCommandAndWaitForPrompt(command, false);
    }

    private void executeCommandAndWaitForPrompt(String command, boolean measure) throws ASConfigCException {
        executeCommand(command);
        waitForPrompt(measure);
    }

    private void waitForPrompt() throws ASConfigCException {
        waitForPrompt(false);
    }

    private boolean textContainsError(String text) {
        return text.contains(OUTPUT_PROBLEM_TYPE_ERROR) || text.contains(OUTPUT_PROBLEM_TYPE_SYNTAX_ERROR)
                || text.contains(OUTPUT_PROBLEM_TYPE_INTERNAL_ERROR);
    }

    private void waitForPrompt(boolean measure) throws ASConfigCException {
        long startTime = 0L;
        if (measure) {
            startTime = System.nanoTime();
        }
        String currentError = "";
        String currentInput = "";
        InputStream inputStream = process.getInputStream();
        InputStream errorStream = process.getErrorStream();
        boolean waitingForInput = true;
        boolean waitingForError = false;
        boolean success = true;
        try {
            waitingForError = errorStream.available() > 0;
        } catch (IOException e) {
            e.printStackTrace(System.err);
            throw new ASConfigCException(ERROR_COMPILER_SHELL_READ);
        }
        do {
            try {
                if (waitingForError) {
                    char next = (char) errorStream.read();
                    currentError += next;
                    if (next == '\n') {
                        if (textContainsError(currentError)) {
                            success = false;
                        }
                        languageClient.logCompilerShellOutput(currentError);
                        currentError = "";
                    }
                }
                waitingForError = errorStream.available() > 0;

                // we need to check inputStream.available() here every time
                // because if we just go straight to read, it may freeze while
                // the errorStream still has data.
                if (waitingForInput && inputStream.available() > 0) {
                    char next = (char) inputStream.read();
                    currentInput += next;
                    // fcsh: Assigned 1 as the compile target id
                    if (currentInput.startsWith(ASSIGNED_ID_PREFIX) && currentInput.endsWith(ASSIGNED_ID_SUFFIX)) {
                        compileID = currentInput.substring(ASSIGNED_ID_PREFIX.length(),
                                currentInput.length() - ASSIGNED_ID_SUFFIX.length());
                    }
                    if (next == '\n') {
                        languageClient.logCompilerShellOutput(currentInput);
                        currentInput = "";
                    }
                    if (currentInput.endsWith(COMPILER_SHELL_PROMPT)) {
                        waitingForInput = false;
                        if (measure) {
                            double totalSeconds = (double) (System.nanoTime() - startTime) / 1000000000.0;
                            languageClient.logCompilerShellOutput("Elapsed time: " + totalSeconds + " seconds\n");
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace(System.err);
                throw new ASConfigCException(ERROR_COMPILER_SHELL_READ);
            }
        } while (waitingForInput || waitingForError);
        if (currentError.length() > 0) {
            if (textContainsError(currentError)) {
                success = false;
            }
            languageClient.logCompilerShellOutput(currentError);
        }
        if (currentInput.length() > 0) {
            languageClient.logCompilerShellOutput(currentInput);
        }
        if (!success) {
            throw new ASConfigCException(ERROR_COMPILER_ERRORS_FOUND);
        }
    }

    private String getCommand(String projectType, List<String> compilerOptions) {
        String command = getNewCommand(projectType, compilerOptions);
        if (!command.equals(previousCommand)) {
            // the compiler options have changed,
            // so we can't use the old ID anymore
            compileID = null;
            previousCommand = command;
        } else if (compileID != null) {
            command = getCompileCommand();
        }
        return command;
    }

    private String getNewCommand(String projectType, List<String> compilerOptions) {
        StringBuilder command = new StringBuilder();
        if (projectType.equals(ProjectType.LIB)) {
            command.append(EXECUTABLE_COMPC);
        } else {
            command.append(EXECUTABLE_MXMLC);
        }
        command.append(" ");
        for (String option : compilerOptions) {
            command.append(option);
            command.append(" ");
        }
        command.append("\n");
        return command.toString();
    }

    private String getClearCommand(String compileID) {
        StringBuilder builder = new StringBuilder();
        builder.append(COMMAND_CLEAR);
        builder.append(" ");
        builder.append(compileID);
        builder.append("\n");
        return builder.toString();
    }

    private String getCompileCommand() {
        StringBuilder builder = new StringBuilder();
        builder.append(COMMAND_COMPILE);
        builder.append(" ");
        builder.append(compileID);
        builder.append("\n");
        return builder.toString();
    }
}