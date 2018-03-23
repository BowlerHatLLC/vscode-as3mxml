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
package com.nextgenactionscript.vscode.compiler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import com.nextgenactionscript.asconfigc.compiler.ProjectType;
import com.nextgenactionscript.vscode.project.ProjectOptions;
import com.nextgenactionscript.vscode.services.ActionScriptLanguageClient;
import com.nextgenactionscript.vscode.utils.ActionScriptSDKUtils;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;

public class CompilerShell
{
    private static final String ERROR_PROJECT_OPTIONS = "Quick Compile & Debug failed because project options are invalid.";
    private static final String ERROR_COMPILER_SHELL_NOT_FOUND = "Quick Compile & Debug requires the Adobe AIR SDK & Compiler or Apache Royale. Please choose a different SDK or build using a standard task.";
    private static final String ERROR_COMPILER_SHELL_START = "Quick Compile & Debug failed. Error starting compiler shell.";
    private static final String ERROR_COMPILER_SHELL_WRITE = "Quick Compile & Debug failed. Error writing to compiler shell.";
    private static final String ERROR_COMPILER_SHELL_READ = "Quick Compile & Debug failed. Error reading from compiler shell.";
    private static final String COMMAND_MXMLC = "mxmlc";
    private static final String COMMAND_COMPC = "compc";
    private static final String COMMAND_COMPILE = "compile";
    private static final String COMMAND_CLEAR = "clear";
    private static final String COMMAND_QUIT = "quit\n";
    private static final String ASSIGNED_ID_PREFIX = "fcsh: Assigned ";
    private static final String ASSIGNED_ID_SUFFIX = " as the compile target id";
    private static final String OUTPUT_PROBLEM_TYPE_ERROR = "Error: ";
    private static final String OUTPUT_PROBLEM_TYPE_SYNTAX_ERROR = "Syntax error: ";
    private static final String COMPILER_SHELL_PROMPT = "(fcsh) ";
    private static final String FILE_NAME_RCSH = "rcsh.jar";
    private static final String FILE_NAME_ASCSH = "ascsh.jar";
    private static final String CLASS_RCSH = "com.nextgenactionscript.vscode.rcsh.RCSH";
    private static final String CLASS_ASCSH = "ascsh";

	private ActionScriptLanguageClient languageClient;
	private Process process;
    private String compileID;
    private String previousCommand;
    private Path rcshPath;
    private Path ascshPath;
    private boolean isRoyale = false;
    private boolean isAIR = false;

	public CompilerShell(ActionScriptLanguageClient languageClient) throws URISyntaxException
	{
		this.languageClient = languageClient;
        URI uri = getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
        Path binPath = Paths.get(uri).getParent().normalize();
        rcshPath = binPath.resolve(FILE_NAME_RCSH);
        ascshPath = binPath.resolve(FILE_NAME_ASCSH);
	}

	public boolean compile(ProjectOptions projectOptions, Path workspaceRoot, Path sdkPath)
	{
        if (projectOptions == null)
        {
            languageClient.showMessage(new MessageParams(MessageType.Error, ERROR_PROJECT_OPTIONS));
            return false;
        }

        isRoyale = ActionScriptSDKUtils.isRoyaleSDK(sdkPath);
        isAIR = ActionScriptSDKUtils.isAIRSDK(sdkPath);

        String oldCompileID = compileID;

        boolean isFCSH = !isRoyale && !isAIR;
        if (isFCSH)
        {
            //fcsh has a bug when run in JAva 1.8 or newer that causes
            //exceptions to be thrown after multiple builds.
            //we can force a fresh build and still gain partial performance
            //improvement from keeping the compiler process loaded in memory.
            compileID = null;
        }

        String command = getCommand(projectOptions);

        boolean compileIDChanged = oldCompileID != null && compileID == null;
        if (process != null && compileIDChanged)
        {
            if (isFCSH)
            {
                //we don't need to restart. we only need to clear.
                if (!executeCommandAndWaitForPrompt(getClearCommand(oldCompileID)))
                {
                    return false;
                }
            }
            else
            {
                //if we have a new command, start with a fresh instance of the
                //compiler shell.
                //we don't need to wait for the prompt because we'll just wait
                //for the process to end.
                if (!executeCommand(COMMAND_QUIT))
                {
                    return false;
                }
                try
                {
                    Process oldProcess = process;
                    process = null;
                    int exitCode = oldProcess.waitFor();
                    languageClient.logCompilerShellOutput("Compiler shell exited with code: " + exitCode);
                }
                catch(InterruptedException e)
                {
                    e.printStackTrace(System.err);
                }
            }
        }
        if (!startProcess(sdkPath, workspaceRoot))
        {
            return false;
        }
        return executeCommandAndWaitForPrompt(command, true);
    }

    private boolean startProcess(Path sdkPath, Path workspaceRoot)
    {
        Path compilerShellPath = null;

        if (isRoyale)
        {
            compilerShellPath = rcshPath;
        }
        else if (isAIR)
        {
            compilerShellPath = ascshPath;
        }
        else
        {
            Path fcshPath = sdkPath.resolve("lib/fcsh.jar");
            if (fcshPath.toFile().exists())
            {
                compilerShellPath = fcshPath;
            }
            else
            {
                languageClient.showMessage(new MessageParams(MessageType.Error, ERROR_COMPILER_SHELL_NOT_FOUND));
                return false;
            }
        }

        if (process != null)
        {
            languageClient.clearCompilerShellOutput();
            languageClient.logCompilerShellOutput(COMPILER_SHELL_PROMPT);
            return true;
        }

        String classPath = null;
        if (isRoyale || isAIR)
        {
            StringBuilder builder = new StringBuilder();
            if (isRoyale)
            {
                builder.append(sdkPath.resolve("lib/").toString());
                builder.append(File.separator);
                builder.append("*");
                builder.append(File.pathSeparator);
                builder.append(sdkPath.resolve("js/lib/").toString());
                builder.append(File.separator);
                builder.append("*");
                builder.append(File.pathSeparator);
            }
            else if (isAIR)
            {
                //we can't use * here because it might load a newer version of Guava
                //which will result in strange errors
                builder.append(sdkPath.resolve("lib/compiler.jar").toString());
                builder.append(File.pathSeparator);
            }
            builder.append(compilerShellPath.toAbsolutePath().toString());
            classPath = builder.toString();
        }

        Path javaExecutablePath = Paths.get(System.getProperty("java.home"), "bin", "java");
        ArrayList<String> options = new ArrayList<>();
        options.add(javaExecutablePath.toString());
        options.add("-Dsun.io.useCanonCaches=false");
        options.add("-Duser.language=en");
        options.add("-Duser.region=en");
        options.add("-Dapplication.home=" + sdkPath);
        options.add("-Dtrace.error=true");
        if (classPath != null)
        {
            options.add("-cp");
            options.add(classPath.toString());
            if (isRoyale)
            {
                options.add(CLASS_RCSH);
            }
            else if (isAIR)
            {
                options.add(CLASS_ASCSH);
            }
        }
        else //fcsh
        {
            options.add("-jar");
            options.add(compilerShellPath.toAbsolutePath().toString());
        }
        try
        {
            System.err.println(String.join(" ", options));
            process = new ProcessBuilder()
                .command(options)
                .directory(workspaceRoot.toFile())
                .start();
        }
        catch (IOException e)
        {
            e.printStackTrace(System.err);
            languageClient.showMessage(new MessageParams(MessageType.Error, ERROR_COMPILER_SHELL_START));
            return false;
        }

        return waitForPrompt();
    }
    
    private boolean executeCommand(String command)
    {
        languageClient.logCompilerShellOutput(command);

        OutputStream outputStream = process.getOutputStream();
        try
        {
            outputStream.write(command.getBytes());
            outputStream.flush();
        }
        catch(IOException e)
        {
            e.printStackTrace(System.err);
            languageClient.showMessage(new MessageParams(MessageType.Error, ERROR_COMPILER_SHELL_WRITE));
            return false;
        }
        return true;
    }
    
    private boolean executeCommandAndWaitForPrompt(String command)
    {
        return executeCommandAndWaitForPrompt(command, false);
    }
    
    private boolean executeCommandAndWaitForPrompt(String command, boolean measure)
    {
        if (!executeCommand(command))
        {
            return false;
        }
        if (!waitForPrompt(measure))
        {
            return false;
        }
        return true;
    }

    private boolean waitForPrompt()
    {
        return waitForPrompt(false);
    }

    private boolean waitForPrompt(boolean measure)
    {
        long startTime = 0L;
        if (measure)
        {
            startTime = System.nanoTime();
        }
        String currentError = "";
        String currentInput = "";
        InputStream inputStream = process.getInputStream();
        InputStream errorStream = process.getErrorStream();
        boolean waitingForInput = true;
        boolean waitingForError = false;
        boolean success = true;
        try
        {
            waitingForError = errorStream.available() > 0;
        }
        catch (IOException e)
        {
            e.printStackTrace(System.err);
            languageClient.showMessage(new MessageParams(MessageType.Error, ERROR_COMPILER_SHELL_READ));
            return false;
        }
        do
        {
            try
            {
                if (waitingForError)
                {
                    char next = (char) errorStream.read();
                    currentError += next;
                    if (next == '\n')
                    {
                        if (currentError.contains(OUTPUT_PROBLEM_TYPE_ERROR) ||
                            currentError.contains(OUTPUT_PROBLEM_TYPE_SYNTAX_ERROR))
                        {
                            success = false;
                        }
                        languageClient.logCompilerShellOutput(currentError);
                        currentError = "";
                    }
                }
                waitingForError = errorStream.available() > 0;
                if (waitingForInput)
                {
                    char next = (char) inputStream.read();
                    currentInput += next;
                    //fcsh: Assigned 1 as the compile target id
                    if (currentInput.startsWith(ASSIGNED_ID_PREFIX) && currentInput.endsWith(ASSIGNED_ID_SUFFIX))
                    {
                        compileID = currentInput.substring(ASSIGNED_ID_PREFIX.length(), currentInput.length() - ASSIGNED_ID_SUFFIX.length());
                    }
                    if (next == '\n')
                    {
                        languageClient.logCompilerShellOutput(currentInput);
                        currentInput = "";
                    }
                    if (currentInput.endsWith(COMPILER_SHELL_PROMPT))
                    {
                        waitingForInput = false;
                        if (measure)
                        {
                            double totalSeconds = (double) (System.nanoTime() - startTime) / 1000000000.0;
                            languageClient.logCompilerShellOutput("Elapsed time: " + totalSeconds + " seconds\n");
                        }
                    }
                }
            }
            catch (IOException e)
            {
                e.printStackTrace(System.err);
                languageClient.showMessage(new MessageParams(MessageType.Error, ERROR_COMPILER_SHELL_READ));
                return false;
            }
        }
        while (waitingForInput || waitingForError);
        if (currentError.length() > 0)
        {
            if (currentError.startsWith(OUTPUT_PROBLEM_TYPE_ERROR))
            {
                success = false;
            }
            languageClient.logCompilerShellOutput(currentError);
        }
        if (currentInput.length() > 0)
        {
            languageClient.logCompilerShellOutput(currentInput);
        }
        return success;
    }

    private String getCommand(ProjectOptions projectOptions)
    {
        String command = getNewCommand(projectOptions);
        if (!command.equals(previousCommand))
        {
            //the compiler options have changed,
            //so we can't use the old ID anymore
            compileID = null;
            previousCommand = command;
        }
        else if (compileID != null)
        {
            command = getCompileCommand();
        }
        return command;
    }

    private String getClearCommand(String compileID)
    {
        StringBuilder builder = new StringBuilder();
        builder.append(COMMAND_CLEAR);
        builder.append(" ");
        builder.append(compileID);
        builder.append("\n");
        return builder.toString();
    }

    private String getCompileCommand()
    {
        StringBuilder builder = new StringBuilder();
        builder.append(COMMAND_COMPILE);
        builder.append(" ");
        builder.append(compileID);
        builder.append("\n");
        return builder.toString();
    }

	private String getNewCommand(ProjectOptions projectOptions)
	{
		StringBuilder commandBuilder = new StringBuilder();
		if (projectOptions.type.equals(ProjectType.APP))
		{
			commandBuilder.append(COMMAND_MXMLC);
		}
		else if (projectOptions.type.equals(ProjectType.LIB))
		{
			commandBuilder.append(COMMAND_COMPC);
		}
		commandBuilder.append(" ");
		commandBuilder.append("+configname=");
		commandBuilder.append(projectOptions.config);
		if (projectOptions.compilerOptions != null)
		{
			commandBuilder.append(" ");
			commandBuilder.append(String.join(" ", projectOptions.compilerOptions));
		}
		if (projectOptions.additionalOptions != null)
		{
			commandBuilder.append(" ");
			commandBuilder.append(projectOptions.additionalOptions);
		}
		if (projectOptions.files != null)
		{
			commandBuilder.append(" ");
			commandBuilder.append(String.join(" ", projectOptions.files));
		}
		commandBuilder.append("\n");
		return commandBuilder.toString();
    }
}