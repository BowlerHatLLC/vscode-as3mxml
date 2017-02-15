/*
Copyright 2016 Bowler Hat LLC

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
package com.nextgenactionscript.vscode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import flash.tools.debugger.ILauncher;

/**
 * If the "launch" command includes runtimeExecutable and (optionally)
 * runtimeArgs fields, we need to launch the SWF runtime manually instead of
 * letting the debugger do it automatically.
 */
public class CustomRuntimeLauncher implements ILauncher
{
    private static final String EXTENSION_APP = ".app";
    private String runtimeExecutable;
    private String[] runtimeArgs;

    public CustomRuntimeLauncher(String runtimeExecutablePath)
    {
        this(runtimeExecutablePath, null);
    }

    public CustomRuntimeLauncher(String runtimeExecutable, String[] runtimeArgs)
    {
        if (runtimeExecutable.endsWith(EXTENSION_APP))
        {
            //for convenience, we'll automatically dig into .app packages on
            //macOS to find the real executable. easier than documenting the
            //whole "Show Package Contents" thing in Finder.
            Path directoryPath = Paths.get(runtimeExecutable).resolve("./Contents/MacOS");
            File directory = directoryPath.toFile();
            if (directory.exists() && directory.isDirectory())
            {
                File[] files = directory.listFiles();
                if (files.length > 0)
                {
                    runtimeExecutable = files[0].getAbsolutePath();
                }
            }
        }
        this.runtimeExecutable = runtimeExecutable;
        this.runtimeArgs = runtimeArgs;
    }

    public Process launch(String[] cmd) throws IOException
    {
        int count = 1 + cmd.length;
        if (runtimeArgs != null)
        {
            count += runtimeArgs.length;
        }
        String[] finalArgs = new String[count];
        finalArgs[0] = runtimeExecutable;
        int offset = 1;
        if (runtimeArgs != null)
        {
            System.arraycopy(runtimeArgs, 0, finalArgs, offset, runtimeArgs.length);
            offset += runtimeArgs.length;
        }
        System.arraycopy(cmd, 0, finalArgs, offset, cmd.length);
        return Runtime.getRuntime().exec(finalArgs);
    }

    public void terminate(Process process) throws IOException
    {
        process.destroy();
    }
}
