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
package com.nextgenactionscript.vscode.project;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.io.FileUtils;
import org.apache.flex.compiler.internal.mxml.MXMLNamespaceMapping;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Configures a project using an asconfig.json file.
 */
public class ASConfigProjectConfigStrategy implements IProjectConfigStrategy
{
    private Path asconfigPath;
    private boolean changed = true;

    public ASConfigProjectConfigStrategy()
    {

    }

    public Path getASConfigPath()
    {
        return asconfigPath;
    }

    public void setASConfigPath(Path value)
    {
        asconfigPath = value;
    }

    public boolean getChanged()
    {
        return changed;
    }

    public void setChanged(boolean value)
    {
        changed = value;
    }

    public ProjectOptions getOptions()
    {
        changed = false;
        if (asconfigPath == null)
        {
            return null;
        }
        File asconfigFile = asconfigPath.toFile();
        if (!asconfigFile.exists())
        {
            return null;
        }
        Path projectRoot = asconfigPath.getParent();
        ProjectType type = ProjectType.APP;
        String config = null;
        String[] files = null;
        String additionalOptions = null;
        CompilerOptions compilerOptions = new CompilerOptions();
        try (InputStream schemaInputStream = getClass().getResourceAsStream("/schemas/asconfig.schema.json"))
        {
            JSONObject rawJsonSchema = new JSONObject(new JSONTokener(schemaInputStream));
            Schema schema = SchemaLoader.load(rawJsonSchema);
            String contents = FileUtils.readFileToString(asconfigFile);
            JSONObject json = new JSONObject(contents);
            schema.validate(json);
            if (json.has(ProjectOptions.TYPE)) //optional, defaults to "app"
            {
                String typeString = json.getString(ProjectOptions.TYPE);
                type = ProjectType.fromToken(typeString);
            }
            config = json.getString(ProjectOptions.CONFIG);
            if (json.has(ProjectOptions.FILES)) //optional
            {
                JSONArray jsonFiles = json.getJSONArray(ProjectOptions.FILES);
                int fileCount = jsonFiles.length();
                files = new String[fileCount];
                for (int i = 0; i < fileCount; i++)
                {
                    String pathString = jsonFiles.getString(i);
                    Path filePath = projectRoot.resolve(pathString);
                    files[i] = filePath.toString();
                }
            }
            if (json.has(ProjectOptions.COMPILER_OPTIONS)) //optional
            {
                JSONObject jsonCompilerOptions = json.getJSONObject(ProjectOptions.COMPILER_OPTIONS);
                if (jsonCompilerOptions.has(CompilerOptions.DEBUG))
                {
                    compilerOptions.debug = jsonCompilerOptions.getBoolean(CompilerOptions.DEBUG);
                }
                if (jsonCompilerOptions.has(CompilerOptions.DEFINE))
                {
                    HashMap<String, String> defines = new HashMap<>();
                    JSONArray jsonDefine = jsonCompilerOptions.getJSONArray(CompilerOptions.DEFINE);
                    for (int i = 0, count = jsonDefine.length(); i < count; i++)
                    {
                        JSONObject jsonNamespace = jsonDefine.getJSONObject(i);
                        String name = jsonNamespace.getString(CompilerOptions.DEFINE_NAME);
                        Object value = jsonNamespace.get(CompilerOptions.DEFINE_VALUE);
                        if (value instanceof String)
                        {
                            value = "\"" + value + "\"";
                        }
                        defines.put(name, value.toString());
                    }
                    compilerOptions.defines = defines;
                }
                if (jsonCompilerOptions.has(CompilerOptions.EXTERNAL_LIBRARY_PATH))
                {
                    JSONArray jsonExternalLibraryPath = jsonCompilerOptions.getJSONArray(CompilerOptions.EXTERNAL_LIBRARY_PATH);
                    ArrayList<File> externalLibraryPath = new ArrayList<>();
                    for (int i = 0, count = jsonExternalLibraryPath.length(); i < count; i++)
                    {
                        String pathString = jsonExternalLibraryPath.getString(i);
                        Path filePath = projectRoot.resolve(pathString);
                        externalLibraryPath.add(filePath.toFile());
                    }
                    compilerOptions.externalLibraryPath = externalLibraryPath;
                }
                if (jsonCompilerOptions.has(CompilerOptions.INCLUDE_CLASSES))
                {
                    JSONArray jsonIncludeClasses = jsonCompilerOptions.getJSONArray(CompilerOptions.INCLUDE_CLASSES);
                    ArrayList<String> includeClasses = new ArrayList<>();
                    for (int i = 0, count = jsonIncludeClasses.length(); i < count; i++)
                    {
                        String qualifiedName = jsonIncludeClasses.getString(i);
                        includeClasses.add(qualifiedName);
                    }
                    compilerOptions.includeClasses = includeClasses;
                }
                if (jsonCompilerOptions.has(CompilerOptions.INCLUDE_NAMESPACES))
                {
                    JSONArray jsonIncludeNamespaces = jsonCompilerOptions.getJSONArray(CompilerOptions.INCLUDE_NAMESPACES);
                    ArrayList<String> includeNamespaces = new ArrayList<>();
                    for (int i = 0, count = jsonIncludeNamespaces.length(); i < count; i++)
                    {
                        String namespaceURI = jsonIncludeNamespaces.getString(i);
                        includeNamespaces.add(namespaceURI);
                    }
                    compilerOptions.includeNamespaces = includeNamespaces;
                }
                if (jsonCompilerOptions.has(CompilerOptions.INCLUDE_SOURCES))
                {
                    JSONArray jsonIncludeSources = jsonCompilerOptions.getJSONArray(CompilerOptions.INCLUDE_SOURCES);
                    ArrayList<File> includeSources = new ArrayList<>();
                    for (int i = 0, count = jsonIncludeSources.length(); i < count; i++)
                    {
                        String pathString = jsonIncludeSources.getString(i);
                        Path filePath = projectRoot.resolve(pathString);
                        includeSources.add(filePath.toFile());
                    }
                    compilerOptions.includeSources = includeSources;
                }
                if (jsonCompilerOptions.has(CompilerOptions.NAMESPACE))
                {
                    JSONArray jsonLibraryPath = jsonCompilerOptions.getJSONArray(CompilerOptions.NAMESPACE);
                    ArrayList<MXMLNamespaceMapping> namespaceMappings = new ArrayList<>();
                    for (int i = 0, count = jsonLibraryPath.length(); i < count; i++)
                    {
                        JSONObject jsonNamespace = jsonLibraryPath.getJSONObject(i);
                        String uri = jsonNamespace.getString(CompilerOptions.NAMESPACE_URI);
                        String manifest = jsonNamespace.getString(CompilerOptions.NAMESPACE_MANIFEST);
                        MXMLNamespaceMapping mapping = new MXMLNamespaceMapping(uri, manifest);
                        namespaceMappings.add(mapping);
                    }
                    compilerOptions.namespaceMappings = namespaceMappings;
                }
                if (jsonCompilerOptions.has(CompilerOptions.LIBRARY_PATH))
                {
                    JSONArray jsonLibraryPath = jsonCompilerOptions.getJSONArray(CompilerOptions.LIBRARY_PATH);
                    ArrayList<File> libraryPath = new ArrayList<>();
                    for (int i = 0, count = jsonLibraryPath.length(); i < count; i++)
                    {
                        String pathString = jsonLibraryPath.getString(i);
                        Path filePath = projectRoot.resolve(pathString);
                        libraryPath.add(filePath.toFile());
                    }
                    compilerOptions.libraryPath = libraryPath;
                }
                if (jsonCompilerOptions.has(CompilerOptions.SOURCE_PATH))
                {
                    JSONArray jsonSourcePath = jsonCompilerOptions.getJSONArray(CompilerOptions.SOURCE_PATH);
                    ArrayList<File> sourcePath = new ArrayList<>();
                    for (int i = 0, count = jsonSourcePath.length(); i < count; i++)
                    {
                        String pathString = jsonSourcePath.getString(i);
                        Path filePath = projectRoot.resolve(pathString);
                        sourcePath.add(filePath.toFile());
                    }
                    compilerOptions.sourcePath = sourcePath;
                }
                if (jsonCompilerOptions.has(CompilerOptions.WARNINGS))
                {
                    compilerOptions.warnings = jsonCompilerOptions.getBoolean(CompilerOptions.WARNINGS);
                }
            }
            //these options are formatted as if sent in through the command line
            if (json.has(ProjectOptions.ADDITIONAL_OPTIONS)) //optional
            {
                additionalOptions = json.getString(ProjectOptions.ADDITIONAL_OPTIONS);
            }
        }
        catch (ValidationException e)
        {
            System.err.println("Failed to validate asconfig.json: " + e);
            return null;
        }
        catch (Exception e)
        {
            System.err.println("Failed to parse asconfig.json: " + e);
            e.printStackTrace();
            return null;
        }
        //in a library project, the files field will be treated the same as the
        //include-sources compiler option
        if (type == ProjectType.LIB && files != null)
        {
            if (compilerOptions.includeSources == null)
            {
                compilerOptions.includeSources = new ArrayList<>();
            }
            for (int i = 0, count = files.length; i < count; i++)
            {
                String filePath = files[i];
                compilerOptions.includeSources.add(new File(filePath));
            }
            files = null;
        }
        ProjectOptions options = new ProjectOptions();
        options.type = type;
        options.config = config;
        options.files = files;
        options.compilerOptions = compilerOptions;
        options.additionalOptions = additionalOptions;
        return options;
    }
}
