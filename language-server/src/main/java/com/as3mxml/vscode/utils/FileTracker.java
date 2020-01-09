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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.royale.compiler.filespecs.IFileSpecification;
import org.apache.royale.compiler.workspaces.IWorkspace;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;

public class FileTracker
{
	private Map<Path,String> sourceByPath = new HashMap<>();
	private LanguageServerFileSpecGetter fileSpecGetter;
	
	public FileTracker(IWorkspace compilerWorkspace)
	{
        fileSpecGetter = new LanguageServerFileSpecGetter(compilerWorkspace, this);
	}

	public boolean isOpen(Path path)
	{
		return sourceByPath.containsKey(path);
	}

	public Set<Path> getOpenFiles()
	{
		return sourceByPath.keySet();
	}

	public void openFile(Path path, String text)
	{
		sourceByPath.put(path, text);
	}

	public String closeFile(Path path)
	{
		return sourceByPath.remove(path);
	}

	public void changeFile(Path path, List<TextDocumentContentChangeEvent> contentChanges)
	{
        for (TextDocumentContentChangeEvent change : contentChanges)
        {
            if (change.getRange() == null)
            {
                sourceByPath.put(path, change.getText());
            }
            else if(sourceByPath.containsKey(path))
            {
                String existingText = sourceByPath.get(path);
                String newText = patch(existingText, change);
                sourceByPath.put(path, newText);
            }
            else
            {
                System.err.println("Failed to apply changes to code intelligence from path: " + path);
            }
        }
	}

    public Reader getReader(Path path)
    {
        if(path == null)
        {
            return null;
        }
        Reader reader = null;
        if (sourceByPath.containsKey(path))
        {
            //if the file is open, use the edited code
            String code = sourceByPath.get(path);
            reader = new StringReader(code);
        }
        else
        {
            File file = new File(path.toAbsolutePath().toString());
            if (!file.exists())
            {
                return null;
            }
            //if the file is not open, read it from the file system
            try
            {
                reader = new FileReader(file);
            }
            catch (FileNotFoundException e)
            {
                //do nothing
            }
        }
        return reader;
    }

    public String getText(Path path)
    {
        if(sourceByPath.containsKey(path))
        {
            return sourceByPath.get(path);
        }
        Reader reader = getReader(path);
        if(reader == null)
        {
            return null;
        }
        try
        {
            return IOUtils.toString(reader);
        }
        catch (IOException e)
        {
            return null;
        }
        finally
        {
            try
            {
                reader.close();
            }
            catch(IOException e) {}
        }
    }

    public IFileSpecification getFileSpecification(String filePath)
    {
        return fileSpecGetter.getFileSpecification(filePath);
    }

    private String patch(String sourceText, TextDocumentContentChangeEvent change)
    {
        Range range = change.getRange();
        Position start = range.getStart();
        StringReader reader = new StringReader(sourceText);
        int offset = LanguageServerCompilerUtils.getOffsetFromPosition(reader, start);
        StringBuilder builder = new StringBuilder();
        builder.append(sourceText.substring(0, offset));
        builder.append(change.getText());
        builder.append(sourceText.substring(offset + change.getRangeLength()));
        return builder.toString();
    }
}