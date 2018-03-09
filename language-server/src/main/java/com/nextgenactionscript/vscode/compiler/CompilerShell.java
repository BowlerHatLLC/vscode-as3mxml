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

import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.services.LanguageClient;

public class CompilerShell
{
	private LanguageClient languageClient;
	private Process process;
	private String compileID;

	public CompilerShell(LanguageClient languageClient)
	{
		this.languageClient = languageClient;
	}

	public boolean compile(ProjectOptions projectOptions, Path workspaceRoot, Path frameworkSDKHome)
	{
        Path fcshPath = frameworkSDKHome.resolve("lib/fcsh.jar");
        if (!fcshPath.toFile().exists())
        {
            languageClient.showMessage(new MessageParams(MessageType.Error, "Flex Compiler Shell (fcsh) not found in the workspace's SDK."));
            return false;
        }
        if (projectOptions == null)
        {
            languageClient.showMessage(new MessageParams(MessageType.Error, "Failed to start Flex Compiler Shell (fcsh) because project options are invalid."));
            return false;
        }

		boolean waitingForStart = false;
        if (process == null)
        {
            waitingForStart = true;
            Path javaExecutablePath = Paths.get(System.getProperty("java.home"), "bin", "java");
            ArrayList<String> options = new ArrayList<>();
            options.add(javaExecutablePath.toString());
            options.add("-Dapplication.home=" + frameworkSDKHome);
			options.add("-Djava.util.Arrays.useLegacyMergeSort=true");
			options.add("-jar");
            options.add(fcshPath.toAbsolutePath().toString());
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
                languageClient.showMessage(new MessageParams(MessageType.Error, "Failed to start Flex Compiler Shell (fcsh)."));
                return false;
            }
        }

        if(waitingForStart)
        {
            String currentInput = "";
            InputStream inputStream = process.getInputStream();
            do
            {
                try
                {
                    char next = (char) inputStream.read();
                    currentInput += next;
                }
                catch (IOException e)
                {
                    e.printStackTrace(System.err);
                    languageClient.showMessage(new MessageParams(MessageType.Error, "Error reading from Flex Compiler Shell (fcsh)."));
                    return false;
                }
            }
            while (!currentInput.endsWith("(fcsh) "));
            languageClient.logMessage(new MessageParams(MessageType.Info, currentInput));
            waitingForStart = false;
        }

		String command;
        if (compileID != null)
        {
            command = "compile " + compileID + "\n";
        }
        else
        {
			command = getCommand(projectOptions);
        }
        languageClient.logMessage(new MessageParams(MessageType.Info, command));

        OutputStream outputStream = process.getOutputStream();
        try
        {
            outputStream.write(command.getBytes());
            outputStream.flush();
        }
        catch(IOException e)
        {
            e.printStackTrace(System.err);
            languageClient.showMessage(new MessageParams(MessageType.Error, "Error writing to Flex Compiler Shell (fcsh)."));
            return false;
        }

        String currentError = "";
        String currentInput = "";
        InputStream inputStream = process.getInputStream();
        InputStream errorStream = process.getErrorStream();
        boolean waitingForInput = true;
        boolean waitingForError = false;
        try
        {
            waitingForError = errorStream.available() > 0;
        }
        catch (IOException e)
        {
            e.printStackTrace(System.err);
            languageClient.showMessage(new MessageParams(MessageType.Error, "Error reading from Flex Compiler Shell (fcsh)."));
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
                        languageClient.logMessage(new MessageParams(MessageType.Error, currentError));
                        currentError = "";
                    }
                }
                waitingForError = errorStream.available() > 0;
                if (waitingForInput)
                {
                    char next = (char) inputStream.read();
                    currentInput += next;
                    //fcsh: Assigned 1 as the compile target id
                    if (currentInput.startsWith("fcsh: Assigned ") && currentInput.endsWith(" as the compile target id"))
                    {
                        compileID = currentInput.substring("fcsh: Assigned ".length(), currentInput.length() - " as the compile target id".length());
                    }
                    if (next == '\n')
                    {
                        languageClient.logMessage(new MessageParams(MessageType.Info, currentInput));
                        currentInput = "";
                    }
                    if (currentInput.endsWith("(fcsh) "))
                    {
                        waitingForInput = false;
                    }
                }
            }
            catch (IOException e)
            {
                e.printStackTrace(System.err);
                languageClient.showMessage(new MessageParams(MessageType.Error, "Error reading from Flex Compiler Shell (fcsh)."));
                return false;
            }
        }
        while (waitingForInput || waitingForError);
        if (currentError.length() > 0)
        {
            languageClient.logMessage(new MessageParams(MessageType.Error, currentError));
        }
        if (currentInput.length() > 0)
        {
            languageClient.logMessage(new MessageParams(MessageType.Info, currentInput));
        }
        
        return true;
	}

	private String getCommand(ProjectOptions projectOptions)
	{
		StringBuilder commandBuilder = new StringBuilder();
		if (projectOptions.type.equals(ProjectType.APP))
		{
			commandBuilder.append("mxmlc");
		}
		else if (projectOptions.type.equals(ProjectType.LIB))
		{
			commandBuilder.append("compc");
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