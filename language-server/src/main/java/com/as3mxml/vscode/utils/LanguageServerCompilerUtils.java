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

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;

import com.as3mxml.vscode.compiler.problems.DisabledConfigConditionBlockProblem;
import com.as3mxml.vscode.compiler.problems.SyntaxFallbackProblem;
import com.as3mxml.vscode.compiler.problems.UnusedImportProblem;
import com.as3mxml.vscode.utils.CompilationUnitUtils.IncludeFileData;

import org.apache.royale.compiler.clients.problems.CompilerProblemCategorizer;
import org.apache.royale.compiler.common.ISourceLocation;
import org.apache.royale.compiler.definitions.IAccessorDefinition;
import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IConstantDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IEventDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IInterfaceDefinition;
import org.apache.royale.compiler.definitions.IPackageDefinition;
import org.apache.royale.compiler.definitions.IStyleDefinition;
import org.apache.royale.compiler.definitions.ITypeDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition.VariableClassification;
import org.apache.royale.compiler.internal.parsing.as.OffsetCue;
import org.apache.royale.compiler.problems.CompilerProblemSeverity;
import org.apache.royale.compiler.problems.DeprecatedAPIProblem;
import org.apache.royale.compiler.problems.ICompilerProblem;
import org.apache.royale.compiler.projects.ICompilerProject;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DiagnosticTag;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;

/**
 * Utility functions for converting between language server types and Flex
 * compiler types.
 */
public class LanguageServerCompilerUtils
{
    private static final String FILE_EXTENSION_MXML = ".mxml";
    private static final String FILE_EXTENSION_AS = ".as";
    private static final String SDK_LIBRARY_PATH_SIGNATURE_UNIX = "/frameworks/libs/";
    private static final String SDK_LIBRARY_PATH_SIGNATURE_WINDOWS = "\\frameworks\\libs\\";

    /**
     * Converts an URI from the language server protocol to a Path.
     */
    public static Path getPathFromLanguageServerURI(String apiURI)
    {
        if (apiURI == null)
        {
            return null;
        }
        URI uri = URI.create(apiURI);
        Optional<Path> optionalPath = getFilePath(uri);
        if (!optionalPath.isPresent())
        {
            System.err.println("Could not find URI " + uri);
            return null;
        }
        return optionalPath.get();
    }

    /**
     * Converts a compiler problem to a language server severity.
     */
    public static DiagnosticSeverity getDiagnosticSeverityFromCompilerProblem(ICompilerProblem problem)
    {
        if (problem instanceof SyntaxFallbackProblem)
        {
            return DiagnosticSeverity.Information;
        }
        if (problem instanceof UnusedImportProblem || problem instanceof DisabledConfigConditionBlockProblem)
        {
            return DiagnosticSeverity.Hint;
        }

        CompilerProblemCategorizer categorizer = new CompilerProblemCategorizer(null);
        CompilerProblemSeverity severity = categorizer.getProblemSeverity(problem);
        switch (severity)
        {
            case ERROR:
            {
                return DiagnosticSeverity.Error;
            }
            case WARNING:
            {
                return DiagnosticSeverity.Warning;
            }
            default:
            {
                return DiagnosticSeverity.Information;
            }
        }
    }

    /**
     * Converts a compiler source location to a language server location. May
     * return null if the line or column of the source location is -1.
     */
    public static Location getLocationFromSourceLocation(ISourceLocation sourceLocation)
    {
        Path sourceLocationPath = Paths.get(sourceLocation.getSourcePath());
        Location location = new Location();
        location.setUri(sourceLocationPath.toUri().toString());

        Range range = getRangeFromSourceLocation(sourceLocation);
        if (range == null)
        {
            //this is probably generated by the compiler somehow
            return null;
        }
        location.setRange(range);

        return location;
    }

    /**
     * Converts a compiler source location to a language server range. May
     * return null if the line or column of the source location is -1.
     */
    public static Range getRangeFromSourceLocation(ISourceLocation sourceLocation)
    {
        int line = sourceLocation.getLine();
        int column = sourceLocation.getColumn();
        if (line == -1 || column == -1)
        {
            //this is probably generated by the compiler somehow
            return null;
        }
        Position start = new Position();
        start.setLine(line);
        start.setCharacter(column);

        int endLine = sourceLocation.getEndLine();
        int endColumn = sourceLocation.getEndColumn();
        if (endLine == -1 || endColumn == -1)
        {
            endLine = line;
            endColumn = column;
        }
        Position end = new Position();
        end.setLine(endLine);
        end.setCharacter(endColumn);

        Range range = new Range();
        range.setStart(start);
        range.setEnd(end);

        return range;
    }

    public static int getOffsetFromPosition(Reader reader, Position position, IncludeFileData includeFileData)
    {
        int offset = 0;
        try
        {
            offset = getOffsetFromPosition(reader, position);
        }
        finally
        {
            try
            {
                reader.close();
            }
            catch(IOException e) {}
        }
 
        if(includeFileData != null)
        {
            int originalOffset = offset;
            //we're actually going to use the offset from the file that includes
            //this one
            for(OffsetCue offsetCue : includeFileData.getOffsetCues())
            {
                if(originalOffset >= offsetCue.local)
                {
                    offset = originalOffset + offsetCue.adjustment;
                }
            }
        }
        return offset;
    }
    
    /**
     * Converts the absolute character offset to a language server position.
     */
    public static Position getPositionFromOffset(Reader in, int targetOffset)
    {
        return getPositionFromOffset(in, targetOffset, new Position());
    }

    public static Position getPositionFromOffset(Reader in, int targetOffset, Position result)
    {
        try
        {
            int offset = 0;
            int line = 0;
            int character = 0;

            while (offset < targetOffset)
            {
                int next = in.read();

                if (next < 0)
                {
                    result.setLine(line);
                    result.setCharacter(line);
                    return result;
                }
                else
                {
                    offset++;
                    character++;

                    if (next == '\n')
                    {
                        line++;
                        character = 0;
                    }
                }
            }

            result.setLine(line);
            result.setCharacter(character);
        }
        catch (IOException e)
        {
            result.setLine(-1);
            result.setCharacter(-1);
        }
        return result;
    }
    
    /**
     * Converts a language server position to the absolute character offset.
     */
    public static int getOffsetFromPosition(Reader in, Position position)
    {
        int targetLine = position.getLine();
        int targetCharacter = position.getCharacter();
        try
        {
            int offset = 0;
            int line = 0;
            int character = 0;

            while (line < targetLine)
            {
                int next = in.read();

                if (next < 0)
                {
                    return offset;
                }
                else
                {
                    //don't skip \r here if line endings are \r\n in the file
                    //there may be cases where the file line endings don't match
                    //what the editor ends up rendering. skipping \r will help
                    //that, but it will break other cases.
                    offset++;

                    if (next == '\n')
                    {
                        line++;
                    }
                }
            }

            while (character < targetCharacter)
            {
                int next = in.read();

                if (next < 0)
                {
                    return offset;
                }
                else
                {
                    offset++;
                    character++;
                }
            }

            return offset;
        }
        catch (IOException e)
        {
            return -1;
        }
    }

    private static Optional<Path> getFilePath(URI uri)
    {
        if (!uri.getScheme().equals("file"))
        {
            return Optional.empty();
        }
        else
        {
            return Optional.of(Paths.get(uri));
        }
    }

    public static CompletionItemKind getCompletionItemKindFromDefinition(IDefinition definition)
    {
        if (definition instanceof IClassDefinition)
        {
            return CompletionItemKind.Class;
        }
        else if (definition instanceof IInterfaceDefinition)
        {
            return CompletionItemKind.Interface;
        }
        else if (definition instanceof IAccessorDefinition)
        {
            return CompletionItemKind.Property;
        }
        else if (definition instanceof IFunctionDefinition)
        {
            IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
            if (functionDefinition.isConstructor())
            {
                return CompletionItemKind.Constructor;
            }
            IDefinition parentDefinition = functionDefinition.getParent();
            if (parentDefinition != null && parentDefinition instanceof ITypeDefinition)
            {
                return CompletionItemKind.Method;
            }
            return CompletionItemKind.Function;
        }
        else if (definition instanceof IConstantDefinition)
        {
            return CompletionItemKind.Constant;
        }
        else if (definition instanceof IVariableDefinition)
        {
            IVariableDefinition variableDefinition = (IVariableDefinition) definition;
            VariableClassification variableClassification = variableDefinition.getVariableClassification();
            if (variableClassification != null)
            {
                switch(variableClassification)
                {
                    case INTERFACE_MEMBER:
                    case CLASS_MEMBER:
                    {
                        return CompletionItemKind.Field;
                    }
                    default:
                    {
                        return CompletionItemKind.Variable;
                    }
                }
            }
        }
        else if (definition instanceof IEventDefinition)
        {
            return CompletionItemKind.Event;
        }
        else if (definition instanceof IStyleDefinition)
        {
            return CompletionItemKind.Field;
        }
        return CompletionItemKind.Value;
    }

    public static SymbolKind getSymbolKindFromDefinition(IDefinition definition)
    {
        if (definition instanceof IPackageDefinition)
        {
            return SymbolKind.Package;
        }
        else if (definition instanceof IClassDefinition)
        {
            return SymbolKind.Class;
        }
        else if (definition instanceof IInterfaceDefinition)
        {
            return SymbolKind.Interface;
        }
        else if (definition instanceof IAccessorDefinition)
        {
            return SymbolKind.Property;
        }
        else if (definition instanceof IFunctionDefinition)
        {
            IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
            if (functionDefinition.isConstructor())
            {
                return SymbolKind.Constructor;
            }
            IDefinition parentDefinition = functionDefinition.getParent();
            if (parentDefinition != null && parentDefinition instanceof ITypeDefinition)
            {
                return SymbolKind.Method;
            }
            return SymbolKind.Function;
        }
        else if (definition instanceof IConstantDefinition)
        {
            return SymbolKind.Constant;
        }
        else if (definition instanceof IVariableDefinition)
        {
            IVariableDefinition variableDefinition = (IVariableDefinition) definition;
            VariableClassification variableClassification = variableDefinition.getVariableClassification();
            if (variableClassification != null)
            {
                switch(variableClassification)
                {
                    case INTERFACE_MEMBER:
                    case CLASS_MEMBER:
                    {
                        return SymbolKind.Field;
                    }
                    default:
                    {
                        return SymbolKind.Variable;
                    }
                }
            }
        }
        else if (definition instanceof IEventDefinition)
        {
            return SymbolKind.Event;
        }
        else if (definition instanceof IStyleDefinition)
        {
            return SymbolKind.Field;
        }
        return SymbolKind.Object;
    }

    public static Diagnostic getDiagnosticFromCompilerProblem(ICompilerProblem problem)
    {
        Diagnostic diagnostic = new Diagnostic();

        DiagnosticSeverity severity = LanguageServerCompilerUtils.getDiagnosticSeverityFromCompilerProblem(problem);
        diagnostic.setSeverity(severity);

        if (problem instanceof DisabledConfigConditionBlockProblem)
        {
            diagnostic.setTags(Collections.singletonList(DiagnosticTag.Unnecessary));
        }
        else if (problem instanceof DeprecatedAPIProblem)
        {
            diagnostic.setTags(Collections.singletonList(DiagnosticTag.Deprecated));
        }

        Range range = LanguageServerCompilerUtils.getRangeFromSourceLocation(problem);
        if (range == null)
        {
            //fall back to an empty range
            range = new Range(new Position(), new Position());
        }
        diagnostic.setRange(range);

        diagnostic.setMessage(problem.toString());

        if(diagnostic.getCode() == null)
        {
            try
            {
                Field field = problem.getClass().getDeclaredField("errorCode");
                int errorCode = (int) field.get(problem);
                diagnostic.setCode(Integer.toString(errorCode));
            }
            catch (Exception e)
            {
                //skip it
            }
        }

        if(diagnostic.getCode() == null)
        {
            try
            {
                Field field = problem.getClass().getDeclaredField("warningCode");
                int errorCode = (int) field.get(problem);
                diagnostic.setCode(Integer.toString(errorCode));
            }
            catch (Exception e)
            {
                //skip it
            }
        }

        if(diagnostic.getCode() == null)
        {
            try
            {
                Field field = problem.getClass().getDeclaredField("DIAGNOSTIC_CODE");
                String diagnosticCode = (String) field.get(problem);
                diagnostic.setCode(diagnosticCode);
            }
            catch (Exception e)
            {
                //skip it
            }
        }

        if(diagnostic.getCode() != null)
        {
            String code = diagnostic.getCode().isLeft() ? diagnostic.getCode().getLeft() : diagnostic.getCode().getRight().toString();
            switch(code)
            {
                case "as3mxml-unused-import":
                case "as3mxml-disabled-config-condition-block":
                {
                    diagnostic.setTags(Collections.singletonList(DiagnosticTag.Unnecessary));
                    break;
                }
                case "3602":
                case "3604":
                case "3606":
                case "3608":
                case "3610":
                {
                    diagnostic.setTags(Collections.singletonList(DiagnosticTag.Deprecated));
                    break;
                }
            }
        }
        
        return diagnostic;
    }

    public static String getSourcePathFromDefinition(IDefinition definition, ICompilerProject project)
    {
        String sourcePath = definition.getSourcePath();
        if (sourcePath == null)
        {
            //I'm not sure why getSourcePath() can sometimes return null, but
            //getContainingFilePath() seems to work as a fallback -JT
            sourcePath = definition.getContainingFilePath();
        }
        if (sourcePath == null)
        {
            return null;
        }
        if (!sourcePath.endsWith(FILE_EXTENSION_AS)
                && !sourcePath.endsWith(FILE_EXTENSION_MXML)
                && (sourcePath.contains(SDK_LIBRARY_PATH_SIGNATURE_UNIX)
                || sourcePath.contains(SDK_LIBRARY_PATH_SIGNATURE_WINDOWS)))
        {
            //if it's a framework SWC, we're going to attempt to resolve
            //the real source file 
            String debugPath = DefinitionUtils.getDefinitionDebugSourceFilePath(definition, project);
            if (debugPath != null)
            {
                //if we can't find the debug source file, keep the SWC extension
                sourcePath = debugPath;
            }
        }
        return sourcePath;
    }
}
