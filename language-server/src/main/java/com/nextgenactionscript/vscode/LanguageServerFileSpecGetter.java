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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.flex.compiler.common.IFileSpecificationGetter;
import org.apache.flex.compiler.constants.IMXMLCoreConstants;
import org.apache.flex.compiler.filespecs.FileSpecification;
import org.apache.flex.compiler.filespecs.IFileSpecification;
import org.apache.flex.compiler.internal.filespecs.StringFileSpecification;
import org.apache.flex.compiler.workspaces.IWorkspace;

/**
 * Returns instances of IFileSpecification to be used by the Apache FlexJS
 * compiler to get the contents of files. If a file is open, and it is being
 * edited (possibly with changes not saved to the file system), returns a
 * StringFileSpecification. StringFileSpecification stores the code in a String.
 * If a file is not open, and it's simply coming from the file system, returns a
 * FileSpecification. FileSpecification reads the actual file.
 */
public class LanguageServerFileSpecGetter implements IFileSpecificationGetter
{
    private static final String SCRIPT_START = "<fx:Script>";
    private static final String SCRIPT_END = "</fx:Script>";
    private static final String MXML_FILE_EXTENSION = ".mxml";

    public LanguageServerFileSpecGetter(IWorkspace workspace, Map<Path, String> sourceByPath)
    {
        this.workspace = workspace;
        this.sourceByPath = sourceByPath;
    }

    private Map<Path, String> sourceByPath;
    private IWorkspace workspace;

    public IWorkspace getWorkspace()
    {
        return workspace;
    }

    public void setWorkspace(IWorkspace value)
    {
        workspace = value;
    }

    public IFileSpecification getFileSpecification(String filePath)
    {
        Path path = Paths.get(filePath);
        if (sourceByPath.containsKey(path))
        {
            String code = sourceByPath.get(path);
            if (path.endsWith(MXML_FILE_EXTENSION))
            {
                code = fixUnclosedXMLComment(code);
                code = fixUnclosedScriptCDATA(code);
            }
            return new StringFileSpecification(filePath, code);
        }
        return new FileSpecification(filePath);
    }

    /**
     * If an XML comment is not closed, the compiler will get into an infinite
     * loop!
     */
    private String fixUnclosedXMLComment(String code)
    {
        int startComment = code.lastIndexOf(IMXMLCoreConstants.commentStart);
        if (startComment == -1)
        {
            return code;
        }
        int endComment = code.indexOf(IMXMLCoreConstants.commentEnd, startComment);
        if (endComment == -1)
        {
            return code + IMXMLCoreConstants.commentEnd;
        }
        return code;
    }

    /**
     * If a <![CDATA[ inside <fx:Script></fx:Script> is not closed, the compiler
     * will get into an infinite loop!
     */
    private String fixUnclosedScriptCDATA(String code)
    {
        int startIndex = 0;
        do
        {
            int startScript = code.indexOf(SCRIPT_START, startIndex);
            if (startScript == -1)
            {
                return code;
            }
            int endScript = code.indexOf(SCRIPT_END, startScript);
            if (endScript == -1)
            {
                endScript = code.length();
            }
            int startCDATA = code.indexOf(IMXMLCoreConstants.cDataStart, startScript);
            if (startCDATA != -1)
            {
                int endCDATA = code.lastIndexOf(IMXMLCoreConstants.cDataEnd, endScript);
                System.out.print("startCDATA: " + startCDATA + "endCDATA: " + endCDATA);
                if (endCDATA < startCDATA)
                {
                    code = code.substring(0, endScript) + IMXMLCoreConstants.cDataEnd + code.substring(endScript);
                    endScript += IMXMLCoreConstants.cDataEnd.length();
                    System.out.println("========");
                    System.out.println(code);
                }
            }
            startIndex = endScript;
        }
        while (true);
    }
}
