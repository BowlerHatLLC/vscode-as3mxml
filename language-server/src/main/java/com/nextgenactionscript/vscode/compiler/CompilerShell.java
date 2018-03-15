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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import com.nextgenactionscript.asconfigc.compiler.ProjectType;
import com.nextgenactionscript.vscode.project.ProjectOptions;
import com.nextgenactionscript.vscode.services.ActionScriptLanguageClient;

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;

public class CompilerShell
{
    private static final String ERROR_PROJECT_OPTIONS = "Quick Compile & Debug failed because project options are invalid.";
    private static final String ERROR_COMPILER_SHELL_NOT_FOUND = "Quick Compile & Debug requires a supported SDK that contains a \"compiler shell\". Compiler shell not found in this SDK. The Apache Flex SDK is recommended.";
    private static final String ERROR_COMPILER_SHELL_START = "Quick Compile & Debug failed. Error starting compiler shell.";
    private static final String ERROR_COMPILER_SHELL_WRITE = "Quick Compile & Debug failed. Error writing to compiler shell.";
    private static final String ERROR_COMPILER_SHELL_READ = "Quick Compile & Debug failed. Error reading from compiler shell.";
    private static final String COMMAND_MXMLC = "mxmlc";
    private static final String COMMAND_COMPC = "compc";
    private static final String COMMAND_COMPILE = "compile";
    private static final String COMMAND_CLEAR = "clear";
    private static final String ASSIGNED_ID_PREFIX = "fcsh: Assigned ";
    private static final String ASSIGNED_ID_SUFFIX = " as the compile target id";
    private static final String OUTPUT_PROBLEM_TYPE_ERROR = "Error: ";
    private static final String COMPILER_SHELL_PROMPT = "(fcsh) ";

	private ActionScriptLanguageClient languageClient;
	private Process process;
    private String compileID;
    private String previousCommand;

	public CompilerShell(ActionScriptLanguageClient languageClient)
	{
		this.languageClient = languageClient;
	}

	public boolean compile(ProjectOptions projectOptions, Path workspaceRoot, Path frameworkSDKHome)
	{
        if (projectOptions == null)
        {
            languageClient.showMessage(new MessageParams(MessageType.Error, ERROR_PROJECT_OPTIONS));
            return false;
        }

        if (!startProcess(frameworkSDKHome, workspaceRoot))
        {
            return false;
        }

        String oldCompileID = compileID;
        String command = getCommand(projectOptions);
        if (oldCompileID != null && compileID == null)
        {
            //if we have a new command, clear the old one from memory
            String clearCommand = getClearCommand(oldCompileID);
            if (!executeCommand(clearCommand))
            {
                return false;
            }
        }
        return executeCommand(command);
    }

    private boolean startProcess(Path frameworkSDKHome, Path workspaceRoot)
    {
        Path compilerShellPath = frameworkSDKHome.resolve("lib/fcsh.jar");
        if (!compilerShellPath.toFile().exists())
        {
            languageClient.showMessage(new MessageParams(MessageType.Error, ERROR_COMPILER_SHELL_NOT_FOUND));
            return false;
        }
        if (process != null)
        {
            return true;
        }

        Path javaExecutablePath = Paths.get(System.getProperty("java.home"), "bin", "java");
        ArrayList<String> options = new ArrayList<>();
        options.add(javaExecutablePath.toString());
        options.add("-Dapplication.home=" + frameworkSDKHome);
        options.add("-Djava.util.Arrays.useLegacyMergeSort=true");
        options.add("-jar");
        options.add(compilerShellPath.toAbsolutePath().toString());
        try
        {
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

        if (!waitForPrompt())
        {
            return false;
        }
        return true;
    }

    private boolean waitForPrompt()
    {
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
                        if (currentError.startsWith(OUTPUT_PROBLEM_TYPE_ERROR))
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