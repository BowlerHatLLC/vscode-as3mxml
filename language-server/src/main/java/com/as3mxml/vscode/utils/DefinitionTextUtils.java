/*
Copyright 2016-2025 Bowler Hat LLC

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.apache.royale.abc.ABCConstants;
import org.apache.royale.compiler.constants.IASKeywordConstants;
import org.apache.royale.compiler.constants.IASLanguageConstants;
import org.apache.royale.compiler.constants.IMetaAttributeConstants;
import org.apache.royale.compiler.definitions.IAccessorDefinition;
import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IConstantDefinition;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IDocumentableDefinition;
import org.apache.royale.compiler.definitions.IEventDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IGetterDefinition;
import org.apache.royale.compiler.definitions.IInterfaceDefinition;
import org.apache.royale.compiler.definitions.INamespaceDefinition;
import org.apache.royale.compiler.definitions.INamespaceDefinition.IInterfaceNamespaceDefinition;
import org.apache.royale.compiler.definitions.IPackageDefinition;
import org.apache.royale.compiler.definitions.IParameterDefinition;
import org.apache.royale.compiler.definitions.ISetterDefinition;
import org.apache.royale.compiler.definitions.IStyleDefinition;
import org.apache.royale.compiler.definitions.ITypeDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition;
import org.apache.royale.compiler.definitions.metadata.IMetaTag;
import org.apache.royale.compiler.definitions.metadata.IMetaTagAttribute;
import org.apache.royale.compiler.projects.ICompilerProject;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

public class DefinitionTextUtils {
    private static final String UNDERSCORE_UNDERSCORE_AS3_PACKAGE = "__AS3__.";
    private static final String NAMESPACE_ID_AS3 = "AS3";
    private static final String NAMESPACE_URI_AS3 = "http://adobe.com/AS3/2006/builtin";
    private static final String NAMESPACE_ID_FLASH_PROXY = "flash_proxy";
    private static final String NAMESPACE_URI_FLASH_PROXY = "http://www.adobe.com/2006/actionscript/flash/proxy";
    private static final String NAMESPACE_ID_MX_INTERNAL = "mx_internal";
    private static final String NAMESPACE_MX_INTERNAL = "http://www.adobe.com/2006/flex/mx/internal";
    private static final String NEW_LINE = "\n";
    private static final String INDENT = "\t";
    private static final String ASDOC_START = "/**";
    private static final String ASDOC_LINE_START = " * ";
    private static final String ASDOC_END = " */";
    public static final Comparator<IDefinition> DEFINITION_COMPARATOR = (IDefinition def1, IDefinition def2) -> {
        // static first
        boolean static1 = def1.isStatic();
        boolean static2 = def2.isStatic();
        if (static1 && !static2) {
            return -1;
        }
        if (!static1 && static2) {
            return 1;
        }

        boolean func1 = def1 instanceof IFunctionDefinition;
        boolean func2 = def2 instanceof IFunctionDefinition;
        boolean constructor1 = func1 && ((IFunctionDefinition) def1).isConstructor();
        boolean constructor2 = func2 && ((IFunctionDefinition) def2).isConstructor();
        if (constructor1 && !constructor2) {
            return -1;
        }
        if (!constructor1 && constructor2) {
            return 1;
        }

        boolean const1 = def1 instanceof IConstantDefinition;
        boolean const2 = def2 instanceof IConstantDefinition;
        if (const1 && !const2) {
            return -1;
        }
        if (!const1 && const2) {
            return 1;
        }

        boolean var1 = def1 instanceof IVariableDefinition;
        boolean var2 = def2 instanceof IVariableDefinition;
        if (var1 && !var2) {
            return -1;
        }
        if (!var1 && var2) {
            return 1;
        }

        // accessors second to last
        boolean acc1 = def1 instanceof IAccessorDefinition;
        boolean acc2 = def2 instanceof IAccessorDefinition;
        if (acc1 && !acc2) {
            return -1;
        }
        if (!acc1 && acc2) {
            return 1;
        }

        if (func1 && !func2) {
            return -1;
        }
        if (!func1 && func2) {
            return 1;
        }

        return 0;
    };

    public static class DefinitionAsText {
        public int startLine = 0;
        public int startColumn = 0;
        public int endLine = 0;
        public int endColumn = 0;
        public String text;
        public String uri;

        public Range toRange() {
            Position start = new Position();
            start.setLine(startLine);
            start.setCharacter(startColumn);
            Position end = new Position();
            end.setLine(endLine);
            end.setCharacter(endColumn);
            Range range = new Range();
            range.setStart(start);
            range.setEnd(end);
            return range;
        }

        public Location toLocation() {
            if (uri == null) {
                return null;
            }
            Location location = new Location();
            location.setUri(uri);
            location.setRange(toRange());
            return location;
        }
    }

    public static Location definitionToLocation(IDefinition definition, ICompilerProject currentProject,
            boolean includeASDoc) {
        DefinitionAsText definitionText = definitionToTextDocument(definition, currentProject, includeASDoc);
        if (definitionText != null) {
            return definitionText.toLocation();
        }
        return null;
    }

    public static Range definitionToRange(IDefinition definition, ICompilerProject currentProject,
            boolean includeASDoc) {
        DefinitionAsText definitionText = definitionToTextDocument(definition, currentProject, includeASDoc);
        if (definitionText != null) {
            return definitionText.toRange();
        }
        return null;
    }

    public static DefinitionAsText definitionToTextDocument(IDefinition definition, ICompilerProject currentProject,
            boolean includeASDoc) {
        if (definition instanceof IClassDefinition) {
            IClassDefinition classDefinition = (IClassDefinition) definition;
            return classDefinitionToTextDocument(classDefinition, currentProject, definition, includeASDoc);
        }
        if (definition instanceof IInterfaceDefinition) {
            IInterfaceDefinition interfaceDefinition = (IInterfaceDefinition) definition;
            return interfaceDefinitionToTextDocument(interfaceDefinition, currentProject, definition, includeASDoc);
        }
        if (definition instanceof INamespaceDefinition) {
            INamespaceDefinition namespaceDefinition = (INamespaceDefinition) definition;
            return namespaceDefinitionToTextDocument(namespaceDefinition, currentProject, definition, includeASDoc);
        }
        if (definition instanceof IFunctionDefinition) {
            IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
            IDefinition parentDefinition = functionDefinition.getParent();
            if (parentDefinition instanceof ITypeDefinition) {
                if (parentDefinition instanceof IClassDefinition) {
                    IClassDefinition classDefinition = (IClassDefinition) parentDefinition;
                    return classDefinitionToTextDocument(classDefinition, currentProject, definition, includeASDoc);
                } else if (parentDefinition instanceof IInterfaceDefinition) {
                    IInterfaceDefinition interfaceDefinition = (IInterfaceDefinition) parentDefinition;
                    return interfaceDefinitionToTextDocument(interfaceDefinition, currentProject, definition,
                            includeASDoc);
                }
            } else {
                return functionDefinitionToTextDocument(functionDefinition, currentProject, definition, includeASDoc);
            }
        }
        if (definition instanceof IVariableDefinition) {
            IVariableDefinition variableDefinition = (IVariableDefinition) definition;
            IDefinition parentDefinition = variableDefinition.getParent();
            if (parentDefinition instanceof ITypeDefinition) {
                if (parentDefinition instanceof IClassDefinition) {
                    IClassDefinition classDefinition = (IClassDefinition) parentDefinition;
                    return classDefinitionToTextDocument(classDefinition, currentProject, definition, includeASDoc);
                } else if (parentDefinition instanceof IInterfaceDefinition) {
                    IInterfaceDefinition interfaceDefinition = (IInterfaceDefinition) parentDefinition;
                    return interfaceDefinitionToTextDocument(interfaceDefinition, currentProject, definition,
                            includeASDoc);
                }
            } else {
                return variableDefinitionToTextDocument(variableDefinition, currentProject, definition, includeASDoc);
            }
        }
        return null;
    }

    private static DefinitionAsText classDefinitionToTextDocument(IClassDefinition classDefinition,
            ICompilerProject currentProject, IDefinition definitionToFind, boolean includeASDoc) {
        DefinitionAsText result = new DefinitionAsText();
        String indent = "";
        StringBuilder textDocumentBuilder = new StringBuilder();
        insertHeaderCommentIntoTextDocument(classDefinition, textDocumentBuilder);
        textDocumentBuilder.append(IASKeywordConstants.PACKAGE);
        String packageName = classDefinition.getPackageName();
        if (packageName != null && packageName.length() > 0) {
            textDocumentBuilder.append(" ");
            textDocumentBuilder.append(packageName);
        }
        textDocumentBuilder.append(NEW_LINE);
        textDocumentBuilder.append("{");
        textDocumentBuilder.append(NEW_LINE);
        indent = increaseIndent(indent);
        insertClassDefinitionIntoTextDocument(classDefinition, textDocumentBuilder, indent, currentProject, result,
                definitionToFind, includeASDoc);
        indent = decreaseIndent(indent);
        textDocumentBuilder.append("}");
        result.text = textDocumentBuilder.toString();
        return result;
    }

    private static DefinitionAsText interfaceDefinitionToTextDocument(IInterfaceDefinition interfaceDefinition,
            ICompilerProject currentProject, IDefinition definitionToFind, boolean includeASDoc) {
        DefinitionAsText result = new DefinitionAsText();
        String indent = "";
        StringBuilder textDocumentBuilder = new StringBuilder();
        insertHeaderCommentIntoTextDocument(interfaceDefinition, textDocumentBuilder);
        textDocumentBuilder.append(IASKeywordConstants.PACKAGE);
        String packageName = interfaceDefinition.getPackageName();
        if (packageName != null && packageName.length() > 0) {
            textDocumentBuilder.append(" ");
            textDocumentBuilder.append(packageName);
        }
        textDocumentBuilder.append(NEW_LINE);
        textDocumentBuilder.append("{");
        textDocumentBuilder.append(NEW_LINE);
        indent = increaseIndent(indent);
        insertInterfaceDefinitionIntoTextDocument(interfaceDefinition, textDocumentBuilder, indent, currentProject,
                result, definitionToFind, includeASDoc);
        indent = decreaseIndent(indent);
        textDocumentBuilder.append("}");
        result.text = textDocumentBuilder.toString();
        return result;
    }

    private static DefinitionAsText namespaceDefinitionToTextDocument(INamespaceDefinition namespaceDefinition,
            ICompilerProject currentProject, IDefinition definitionToFind, boolean includeASDoc) {
        DefinitionAsText result = new DefinitionAsText();
        String indent = "";
        StringBuilder textDocumentBuilder = new StringBuilder();
        insertHeaderCommentIntoTextDocument(namespaceDefinition, textDocumentBuilder);
        textDocumentBuilder.append(IASKeywordConstants.PACKAGE);
        String packageName = namespaceDefinition.getPackageName();
        if (packageName != null && packageName.length() > 0) {
            textDocumentBuilder.append(" ");
            textDocumentBuilder.append(packageName);
        }
        textDocumentBuilder.append(NEW_LINE);
        textDocumentBuilder.append("{");
        textDocumentBuilder.append(NEW_LINE);
        indent = increaseIndent(indent);
        insertNamespaceDefinitionIntoTextDocument(namespaceDefinition, textDocumentBuilder, indent, currentProject,
                result, definitionToFind, includeASDoc);
        indent = decreaseIndent(indent);
        textDocumentBuilder.append("}");
        result.text = textDocumentBuilder.toString();
        return result;
    }

    private static DefinitionAsText functionDefinitionToTextDocument(IFunctionDefinition functionDefinition,
            ICompilerProject currentProject, IDefinition definitionToFind, boolean includeASDoc) {
        DefinitionAsText result = new DefinitionAsText();
        String indent = "";
        StringBuilder textDocumentBuilder = new StringBuilder();
        insertHeaderCommentIntoTextDocument(functionDefinition, textDocumentBuilder);
        textDocumentBuilder.append(IASKeywordConstants.PACKAGE);
        String packageName = functionDefinition.getPackageName();
        if (packageName != null && packageName.length() > 0) {
            textDocumentBuilder.append(" ");
            textDocumentBuilder.append(packageName);
        }
        textDocumentBuilder.append(NEW_LINE);
        textDocumentBuilder.append("{");
        textDocumentBuilder.append(NEW_LINE);
        indent = increaseIndent(indent);
        insertFunctionDefinitionIntoTextDocument(functionDefinition, textDocumentBuilder, indent, currentProject,
                result, definitionToFind, includeASDoc);
        indent = decreaseIndent(indent);
        textDocumentBuilder.append("}");
        result.text = textDocumentBuilder.toString();
        return result;
    }

    private static DefinitionAsText variableDefinitionToTextDocument(IVariableDefinition variableDefinition,
            ICompilerProject currentProject, IDefinition definitionToFind, boolean includeASDoc) {
        DefinitionAsText result = new DefinitionAsText();
        String indent = "";
        StringBuilder textDocumentBuilder = new StringBuilder();
        insertHeaderCommentIntoTextDocument(variableDefinition, textDocumentBuilder);
        textDocumentBuilder.append(IASKeywordConstants.PACKAGE);
        String packageName = variableDefinition.getPackageName();
        if (packageName != null && packageName.length() > 0) {
            textDocumentBuilder.append(" ");
            textDocumentBuilder.append(packageName);
        }
        textDocumentBuilder.append(NEW_LINE);
        textDocumentBuilder.append("{");
        textDocumentBuilder.append(NEW_LINE);
        indent = increaseIndent(indent);
        insertVariableDefinitionIntoTextDocument(variableDefinition, textDocumentBuilder, indent, currentProject,
                result, definitionToFind, includeASDoc);
        indent = decreaseIndent(indent);
        textDocumentBuilder.append("}");
        result.text = textDocumentBuilder.toString();
        return result;
    }

    private static void insertClassDefinitionIntoTextDocument(IClassDefinition classDefinition,
            StringBuilder textDocumentBuilder, String indent, ICompilerProject currentProject, DefinitionAsText result,
            IDefinition definitionToFind, boolean includeASDoc) {

        insertMetaTagsIntoTextDocument(classDefinition, textDocumentBuilder, indent, currentProject, result,
                definitionToFind);

        if (includeASDoc) {
            insertASDocIntoTextDocument(classDefinition, textDocumentBuilder, currentProject, indent);
        }

        textDocumentBuilder.append(indent);
        if (classDefinition.isPublic()) {
            textDocumentBuilder.append(IASKeywordConstants.PUBLIC);
            textDocumentBuilder.append(" ");
        } else if (classDefinition.isInternal()) {
            textDocumentBuilder.append(IASKeywordConstants.INTERNAL);
            textDocumentBuilder.append(" ");
        }
        if (classDefinition.isFinal()) {
            textDocumentBuilder.append(IASKeywordConstants.FINAL);
            textDocumentBuilder.append(" ");
        }
        if (classDefinition.isDynamic()) {
            textDocumentBuilder.append(IASKeywordConstants.DYNAMIC);
            textDocumentBuilder.append(" ");
        }
        textDocumentBuilder.append(IASKeywordConstants.CLASS);
        textDocumentBuilder.append(" ");
        appendDefinitionName(classDefinition, textDocumentBuilder, definitionToFind, includeASDoc, result);
        String baseClassName = classDefinition.getBaseClassAsDisplayString();
        if (baseClassName != null && baseClassName.length() > 0 && !baseClassName.equals(IASLanguageConstants.Object)) {
            textDocumentBuilder.append(" ");
            textDocumentBuilder.append(IASKeywordConstants.EXTENDS);
            textDocumentBuilder.append(" ");
            textDocumentBuilder.append(baseClassName);
        }
        String[] interfaceNames = classDefinition.getImplementedInterfacesAsDisplayStrings();
        if (interfaceNames.length > 0) {
            textDocumentBuilder.append(" ");
            textDocumentBuilder.append(IASKeywordConstants.IMPLEMENTS);
            textDocumentBuilder.append(" ");
            appendInterfaceNamesToDetail(textDocumentBuilder, interfaceNames);
        }
        textDocumentBuilder.append(NEW_LINE);
        textDocumentBuilder.append(indent);
        textDocumentBuilder.append("{");
        textDocumentBuilder.append(NEW_LINE);
        indent = increaseIndent(indent);
        final String childIndent = indent;
        Collection<IDefinition> definitionSet = classDefinition.getContainedScope().getAllLocalDefinitions();
        definitionSet.stream().filter(childDefinition -> {
            if (childDefinition.isOverride() || childDefinition.isPrivate()) {
                // skip overrides and private
                return false;
            }
            return childDefinition instanceof IAccessorDefinition || childDefinition instanceof IFunctionDefinition
                    || childDefinition instanceof IVariableDefinition;
        }).sorted(DEFINITION_COMPARATOR).forEach(childDefinition -> {
            if (childDefinition instanceof IAccessorDefinition) {
                IAccessorDefinition functionDefinition = (IAccessorDefinition) childDefinition;
                insertFunctionDefinitionIntoTextDocument(functionDefinition, textDocumentBuilder, childIndent,
                        currentProject, result, definitionToFind, includeASDoc);
            } else if (childDefinition instanceof IFunctionDefinition) {
                IFunctionDefinition functionDefinition = (IFunctionDefinition) childDefinition;
                insertFunctionDefinitionIntoTextDocument(functionDefinition, textDocumentBuilder, childIndent,
                        currentProject, result, definitionToFind, includeASDoc);
            } else if (childDefinition instanceof IVariableDefinition) {
                IVariableDefinition variableDefinition = (IVariableDefinition) childDefinition;
                insertVariableDefinitionIntoTextDocument(variableDefinition, textDocumentBuilder, childIndent,
                        currentProject, result, definitionToFind, includeASDoc);
            }
        });
        indent = decreaseIndent(indent);
        textDocumentBuilder.append(indent);
        textDocumentBuilder.append("}");
        textDocumentBuilder.append(NEW_LINE);
    }

    private static void insertInterfaceDefinitionIntoTextDocument(IInterfaceDefinition interfaceDefinition,
            StringBuilder textDocumentBuilder, String indent, ICompilerProject currentProject, DefinitionAsText result,
            IDefinition definitionToFind, boolean includeASDoc) {

        insertMetaTagsIntoTextDocument(interfaceDefinition, textDocumentBuilder, indent, currentProject, result,
                definitionToFind);

        if (includeASDoc) {
            insertASDocIntoTextDocument(interfaceDefinition, textDocumentBuilder, currentProject, indent);
        }

        textDocumentBuilder.append(indent);
        if (interfaceDefinition.isPublic()) {
            textDocumentBuilder.append(IASKeywordConstants.PUBLIC);
            textDocumentBuilder.append(" ");
        } else if (interfaceDefinition.isInternal()) {
            textDocumentBuilder.append(IASKeywordConstants.INTERNAL);
            textDocumentBuilder.append(" ");
        }
        textDocumentBuilder.append(IASKeywordConstants.INTERFACE);
        textDocumentBuilder.append(" ");
        appendDefinitionName(interfaceDefinition, textDocumentBuilder, definitionToFind, includeASDoc, result);
        String[] interfaceNames = interfaceDefinition.getExtendedInterfacesAsDisplayStrings();
        if (interfaceNames.length > 0) {
            textDocumentBuilder.append(" ");
            textDocumentBuilder.append(IASKeywordConstants.EXTENDS);
            textDocumentBuilder.append(" ");
            appendInterfaceNamesToDetail(textDocumentBuilder, interfaceNames);
        }
        textDocumentBuilder.append(NEW_LINE);
        textDocumentBuilder.append(indent);
        textDocumentBuilder.append("{");
        textDocumentBuilder.append(NEW_LINE);
        indent = increaseIndent(indent);
        final String childIndent = indent;
        Collection<IDefinition> definitionSet = interfaceDefinition.getContainedScope().getAllLocalDefinitions();
        definitionSet.stream().filter(childDefinition -> {
            if (childDefinition.isOverride() || childDefinition.isPrivate() || childDefinition.isInternal()) {
                // skip overrides, private, and internal
                return false;
            }
            return childDefinition instanceof IAccessorDefinition || childDefinition instanceof IFunctionDefinition;
        }).sorted(DEFINITION_COMPARATOR).forEach(childDefinition -> {
            if (childDefinition instanceof IAccessorDefinition) {
                IAccessorDefinition functionDefinition = (IAccessorDefinition) childDefinition;
                insertFunctionDefinitionIntoTextDocument(functionDefinition, textDocumentBuilder, childIndent,
                        currentProject, result, definitionToFind, includeASDoc);
            } else if (childDefinition instanceof IFunctionDefinition) {
                IFunctionDefinition functionDefinition = (IFunctionDefinition) childDefinition;
                insertFunctionDefinitionIntoTextDocument(functionDefinition, textDocumentBuilder, childIndent,
                        currentProject, result, definitionToFind, includeASDoc);
            }
        });
        indent = decreaseIndent(indent);
        textDocumentBuilder.append(indent);
        textDocumentBuilder.append("}");
        textDocumentBuilder.append(NEW_LINE);
    }

    private static void insertNamespaceDefinitionIntoTextDocument(INamespaceDefinition namespaceDefinition,
            StringBuilder textDocumentBuilder, String indent, ICompilerProject currentProject, DefinitionAsText result,
            IDefinition definitionToFind, boolean includeASDoc) {
        insertMetaTagsIntoTextDocument(namespaceDefinition, textDocumentBuilder, indent, currentProject, result,
                definitionToFind);

        if (includeASDoc) {
            insertASDocIntoTextDocument(namespaceDefinition, textDocumentBuilder, currentProject, indent);
        }

        textDocumentBuilder.append(indent);
        if (namespaceDefinition.isPublic()) {
            textDocumentBuilder.append(IASKeywordConstants.PUBLIC);
            textDocumentBuilder.append(" ");
        } else if (namespaceDefinition.isInternal()) {
            textDocumentBuilder.append(IASKeywordConstants.INTERNAL);
            textDocumentBuilder.append(" ");
        }
        textDocumentBuilder.append(IASKeywordConstants.NAMESPACE);
        textDocumentBuilder.append(" ");
        appendDefinitionName(namespaceDefinition, textDocumentBuilder, definitionToFind, includeASDoc, result);
        textDocumentBuilder.append(" ");
        textDocumentBuilder.append("=");
        textDocumentBuilder.append(" ");
        textDocumentBuilder.append("\"");
        textDocumentBuilder.append(namespaceDefinition.getURI());
        textDocumentBuilder.append("\"");
        textDocumentBuilder.append(";");
        textDocumentBuilder.append(NEW_LINE);
    }

    private static void insertFunctionDefinitionIntoTextDocument(IFunctionDefinition functionDefinition,
            StringBuilder textDocumentBuilder, String indent, ICompilerProject currentProject, DefinitionAsText result,
            IDefinition definitionToFind, boolean includeASDoc) {
        insertMetaTagsIntoTextDocument(functionDefinition, textDocumentBuilder, indent, currentProject, result,
                definitionToFind);

        if (includeASDoc) {
            insertASDocIntoTextDocument(functionDefinition, textDocumentBuilder, currentProject, indent);
        }

        textDocumentBuilder.append(indent);
        if (functionDefinition.isOverride()) {
            textDocumentBuilder.append(IASKeywordConstants.OVERRIDE);
            textDocumentBuilder.append(" ");
        }
        if (functionDefinition.isPublic() || functionDefinition.isConstructor()) {
            textDocumentBuilder.append(IASKeywordConstants.PUBLIC);
            textDocumentBuilder.append(" ");
        } else if (functionDefinition.isInternal()) {
            textDocumentBuilder.append(IASKeywordConstants.INTERNAL);
            textDocumentBuilder.append(" ");
        } else if (functionDefinition.isPrivate()) {
            textDocumentBuilder.append(IASKeywordConstants.PRIVATE);
            textDocumentBuilder.append(" ");
        } else if (functionDefinition.isProtected()) {
            textDocumentBuilder.append(IASKeywordConstants.PROTECTED);
            textDocumentBuilder.append(" ");
        } else {
            INamespaceDefinition ns = functionDefinition.resolveNamespace(currentProject);
            appendNamespace(ns, textDocumentBuilder);
        }
        if (functionDefinition.isStatic()) {
            textDocumentBuilder.append(IASKeywordConstants.STATIC);
            textDocumentBuilder.append(" ");
        }
        if (functionDefinition.isFinal()) {
            textDocumentBuilder.append(IASKeywordConstants.FINAL);
            textDocumentBuilder.append(" ");
        }
        if (!(functionDefinition.getParent() instanceof IInterfaceDefinition)) {
            textDocumentBuilder.append(IASKeywordConstants.NATIVE);
            textDocumentBuilder.append(" ");
        }
        textDocumentBuilder.append(IASKeywordConstants.FUNCTION);
        textDocumentBuilder.append(" ");
        if (functionDefinition instanceof IGetterDefinition) {
            textDocumentBuilder.append(IASKeywordConstants.GET);
            textDocumentBuilder.append(" ");
        } else if (functionDefinition instanceof ISetterDefinition) {
            textDocumentBuilder.append(IASKeywordConstants.SET);
            textDocumentBuilder.append(" ");
        }
        appendDefinitionName(functionDefinition, textDocumentBuilder, definitionToFind, includeASDoc, result);
        textDocumentBuilder.append(functionDefinitionToParametersAndReturnValue(functionDefinition, currentProject));
        textDocumentBuilder.append(";");
        textDocumentBuilder.append(NEW_LINE);
    }

    private static void insertVariableDefinitionIntoTextDocument(IVariableDefinition variableDefinition,
            StringBuilder textDocumentBuilder, String indent, ICompilerProject currentProject, DefinitionAsText result,
            IDefinition definitionToFind, boolean includeASDoc) {

        insertMetaTagsIntoTextDocument(variableDefinition, textDocumentBuilder, indent, currentProject, result,
                definitionToFind);

        if (includeASDoc) {
            insertASDocIntoTextDocument(variableDefinition, textDocumentBuilder, currentProject, indent);
        }

        textDocumentBuilder.append(indent);
        if (variableDefinition.isPublic()) {
            textDocumentBuilder.append(IASKeywordConstants.PUBLIC);
            textDocumentBuilder.append(" ");
        } else if (variableDefinition.isInternal()) {
            textDocumentBuilder.append(IASKeywordConstants.INTERNAL);
            textDocumentBuilder.append(" ");
        } else if (variableDefinition.isPrivate()) {
            textDocumentBuilder.append(IASKeywordConstants.PRIVATE);
            textDocumentBuilder.append(" ");
        } else if (variableDefinition.isProtected()) {
            textDocumentBuilder.append(IASKeywordConstants.PROTECTED);
            textDocumentBuilder.append(" ");
        } else {
            INamespaceDefinition ns = variableDefinition.resolveNamespace(currentProject);
            if (ns != null) {
                appendNamespace(ns, textDocumentBuilder);
            }
        }
        if (variableDefinition.isStatic()) {
            textDocumentBuilder.append(IASKeywordConstants.STATIC);
            textDocumentBuilder.append(" ");
        }
        if (variableDefinition instanceof IConstantDefinition) {
            textDocumentBuilder.append(IASKeywordConstants.CONST);
        } else {
            textDocumentBuilder.append(IASKeywordConstants.VAR);
        }
        textDocumentBuilder.append(" ");
        appendDefinitionName(variableDefinition, textDocumentBuilder, definitionToFind, includeASDoc, result);
        textDocumentBuilder.append(":");
        textDocumentBuilder.append(getTypeAsDisplayString(variableDefinition));
        if (variableDefinition instanceof IConstantDefinition) {
            IConstantDefinition constantDefinition = (IConstantDefinition) variableDefinition;
            Object initialValue = constantDefinition.resolveInitialValue(currentProject);
            String initialValueAsString = valueToString(initialValue);
            if (initialValueAsString != null) {
                textDocumentBuilder.append(" = ");
                textDocumentBuilder.append(initialValueAsString);
            }
        }
        textDocumentBuilder.append(";");
        textDocumentBuilder.append(NEW_LINE);
    }

    private static void insertMetaTagsIntoTextDocument(IDefinition definition, StringBuilder textDocumentBuilder,
            String indent, ICompilerProject currentProject, DefinitionAsText result, IDefinition definitionToFind) {
        IMetaTag[] metaTags = definition.getAllMetaTags();
        if (metaTags.length > 0) {
            for (int i = 0; i < metaTags.length; i++) {
                IMetaTag metaTag = metaTags[i];
                String tagName = metaTag.getTagName();
                if (IMetaAttributeConstants.ATTRIBUTE_GOTODEFINITIONHELP.equals(tagName)
                        || IMetaAttributeConstants.ATTRIBUTE_GOTODEFINITION_CTOR_HELP.equals(tagName)) {
                    // skip these because they add way too much noise and aren't
                    // meant to be seen
                    continue;
                }
                textDocumentBuilder.append(indent);
                insertMetaTagIntoTextDocument(metaTag, textDocumentBuilder, currentProject, result, definitionToFind);
                textDocumentBuilder.append(NEW_LINE);
            }
        }
    }

    private static void insertMetaTagIntoTextDocument(IMetaTag metaTag, StringBuilder textDocumentBuilder,
            ICompilerProject currentProject, DefinitionAsText result, IDefinition definitionToFind) {
        textDocumentBuilder.append("[");
        textDocumentBuilder.append(metaTag.getTagName());
        IMetaTagAttribute[] attributes = metaTag.getAllAttributes();
        if (attributes.length > 0) {
            textDocumentBuilder.append("(");
            for (int i = 0; i < attributes.length; i++) {
                if (i > 0) {
                    textDocumentBuilder.append(",");
                }
                IMetaTagAttribute attribute = attributes[i];
                String key = attribute.getKey();
                if (key != null) {
                    textDocumentBuilder.append(attribute.getKey());
                    textDocumentBuilder.append("=");
                }
                textDocumentBuilder.append("\"");
                textDocumentBuilder.append(attribute.getValue());
                textDocumentBuilder.append("\"");
            }
            textDocumentBuilder.append(")");
        }
        textDocumentBuilder.append("]");
    }

    private static void appendInterfaceNamesToDetail(StringBuilder detailBuilder, String[] interfaceNames) {
        for (int i = 0, count = interfaceNames.length; i < count; i++) {
            if (i > 0) {
                detailBuilder.append(", ");
            }
            detailBuilder.append(interfaceNames[i]);
        }
    }

    public static String valueToString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            String stringValue = (String) value;
            // handle rendering of certain whitespace and other escape sequences
            // https://help.adobe.com/en_US/as3/dev/WS5b3ccc516d4fbf351e63e3d118a9b90204-7ef8.html
            stringValue = stringValue
                    // backslash (must be first because the rest add more backslashes)
                    .replace("\\", "\\\\")
                    // backspace
                    .replace("\b", "\\b")
                    // form feed
                    .replace("\f", "\\f")
                    // new line
                    .replace("\n", "\\n")
                    // carriage return
                    .replace("\r", "\\r")
                    // horizontal tab
                    .replace("\t", "\\t")
                    // double quote
                    .replace("\"", "\\\"");
            return "\"" + stringValue + "\"";
        } else if (value == ABCConstants.UNDEFINED_VALUE) {
            return IASLanguageConstants.UNDEFINED;
        } else if (value == ABCConstants.NULL_VALUE) {
            return IASLanguageConstants.NULL;
        }
        return value.toString();
    }

    public static String definitionToDetail(IDefinition definition, ICompilerProject currentProject) {
        StringBuilder detailBuilder = new StringBuilder();
        if (definition instanceof IClassDefinition) {
            IClassDefinition classDefinition = (IClassDefinition) definition;
            if (classDefinition.isDynamic()) {
                detailBuilder.append(IASKeywordConstants.DYNAMIC);
                detailBuilder.append(" ");
            }
            detailBuilder.append(IASKeywordConstants.CLASS);
            detailBuilder.append(" ");
            if (classDefinition.getPackageName().startsWith(UNDERSCORE_UNDERSCORE_AS3_PACKAGE)) {
                // classes like __AS3__.vec.Vector should not include the
                // package name
                detailBuilder.append(classDefinition.getBaseName());
            } else {
                detailBuilder.append(classDefinition.getQualifiedName());
            }
            String baseClassName = classDefinition.getBaseClassAsDisplayString();
            if (baseClassName != null && baseClassName.length() > 0
                    && !baseClassName.equals(IASLanguageConstants.Object)) {
                detailBuilder.append(" ");
                detailBuilder.append(IASKeywordConstants.EXTENDS);
                detailBuilder.append(" ");
                detailBuilder.append(baseClassName);
            }
            String[] interfaceNames = classDefinition.getImplementedInterfacesAsDisplayStrings();
            if (interfaceNames.length > 0) {
                detailBuilder.append(" ");
                detailBuilder.append(IASKeywordConstants.IMPLEMENTS);
                detailBuilder.append(" ");
                appendInterfaceNamesToDetail(detailBuilder, interfaceNames);
            }
        } else if (definition instanceof IInterfaceDefinition) {
            IInterfaceDefinition interfaceDefinition = (IInterfaceDefinition) definition;
            detailBuilder.append(IASKeywordConstants.INTERFACE);
            detailBuilder.append(" ");
            detailBuilder.append(interfaceDefinition.getQualifiedName());
            String[] interfaceNames = interfaceDefinition.getExtendedInterfacesAsDisplayStrings();
            if (interfaceNames.length > 0) {
                detailBuilder.append(" ");
                detailBuilder.append(IASKeywordConstants.EXTENDS);
                detailBuilder.append(" ");
                DefinitionTextUtils.appendInterfaceNamesToDetail(detailBuilder, interfaceNames);
            }
        } else if (definition instanceof IVariableDefinition) {
            IVariableDefinition variableDefinition = (IVariableDefinition) definition;
            IDefinition parentDefinition = variableDefinition.getParent();
            if (parentDefinition instanceof ITypeDefinition) {
                // an IAccessorDefinition actually extends both
                // IVariableDefinition and IFunctionDefinition
                if (variableDefinition instanceof IAccessorDefinition) {
                    detailBuilder.append("(property) ");
                } else if (variableDefinition instanceof IConstantDefinition) {
                    detailBuilder.append("(const) ");
                } else {
                    detailBuilder.append("(variable) ");
                }
                detailBuilder.append(parentDefinition.getQualifiedName());
                detailBuilder.append(".");
            } else if (parentDefinition instanceof IFunctionDefinition) {
                if (variableDefinition instanceof IParameterDefinition) {
                    detailBuilder.append("(parameter) ");
                } else {
                    detailBuilder.append("(local ");
                    if (variableDefinition instanceof IConstantDefinition) {
                        detailBuilder.append("const) ");
                    } else {
                        detailBuilder.append("var) ");
                    }
                }
            } else {
                if (variableDefinition instanceof IConstantDefinition) {
                    detailBuilder.append(IASKeywordConstants.CONST);
                } else {
                    detailBuilder.append(IASKeywordConstants.VAR);
                }
                detailBuilder.append(" ");
            }
            detailBuilder.append(variableDefinition.getQualifiedName());
            detailBuilder.append(":");
            detailBuilder.append(getTypeAsDisplayString(variableDefinition));
            if (variableDefinition instanceof IConstantDefinition) {
                IConstantDefinition constantDefinition = (IConstantDefinition) variableDefinition;
                Object initialValue = constantDefinition.resolveInitialValue(currentProject);
                String initialValueAsString = valueToString(initialValue);
                if (initialValueAsString != null) {
                    detailBuilder.append(" = ");
                    detailBuilder.append(initialValueAsString);
                }
            }
        } else if (definition instanceof IFunctionDefinition) {
            IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
            IDefinition parentDefinition = functionDefinition.getParent();
            if (parentDefinition instanceof ITypeDefinition) {
                if (functionDefinition.isConstructor()) {
                    detailBuilder.append("(constructor) ");
                    // don't append the parent definition before the constructor,
                    // like we do with methods, because the constructor name
                    // already includes the full package
                } else {
                    detailBuilder.append("(method) ");
                    detailBuilder.append(parentDefinition.getBaseName());
                    detailBuilder.append(".");
                }
            } else if (parentDefinition instanceof IFunctionDefinition) {
                detailBuilder.append("(local function) ");
            } else {
                detailBuilder.append(IASKeywordConstants.FUNCTION);
                detailBuilder.append(" ");
            }
            detailBuilder.append(functionDefinitionToSignature(functionDefinition, currentProject));
        } else if (definition instanceof IEventDefinition) {
            IEventDefinition eventDefinition = (IEventDefinition) definition;
            detailBuilder.append("(event) ");
            detailBuilder.append("[");
            detailBuilder.append(IMetaAttributeConstants.ATTRIBUTE_EVENT);
            detailBuilder.append("(");
            detailBuilder.append(IMetaAttributeConstants.NAME_EVENT_NAME);
            detailBuilder.append("=");
            detailBuilder.append("\"");
            detailBuilder.append(eventDefinition.getBaseName());
            detailBuilder.append("\"");
            detailBuilder.append(",");
            detailBuilder.append(IMetaAttributeConstants.NAME_EVENT_TYPE);
            detailBuilder.append("=");
            detailBuilder.append("\"");
            detailBuilder.append(getTypeAsDisplayString(eventDefinition));
            detailBuilder.append("\"");
            detailBuilder.append(")");
            detailBuilder.append("]");
        } else if (definition instanceof IStyleDefinition) {
            IStyleDefinition styleDefinition = (IStyleDefinition) definition;
            detailBuilder.append("(style) ");
            detailBuilder.append("[");
            detailBuilder.append(IMetaAttributeConstants.ATTRIBUTE_STYLE);
            detailBuilder.append("(");
            detailBuilder.append(IMetaAttributeConstants.NAME_STYLE_NAME);
            detailBuilder.append("=");
            detailBuilder.append("\"");
            detailBuilder.append(styleDefinition.getBaseName());
            detailBuilder.append("\"");
            detailBuilder.append(",");
            detailBuilder.append(IMetaAttributeConstants.NAME_STYLE_TYPE);
            detailBuilder.append("=");
            detailBuilder.append("\"");
            detailBuilder.append(getTypeAsDisplayString(styleDefinition));
            detailBuilder.append("\"");
            detailBuilder.append(")");
            detailBuilder.append("]");
        } else if (definition instanceof INamespaceDefinition) {
            INamespaceDefinition namespaceDefinition = (INamespaceDefinition) definition;
            detailBuilder.append(IASKeywordConstants.NAMESPACE);
            detailBuilder.append(" ");
            detailBuilder.append(namespaceDefinition.getQualifiedName());
            detailBuilder.append(" ");
            detailBuilder.append("=");
            detailBuilder.append(" ");
            detailBuilder.append("\"");
            detailBuilder.append(namespaceDefinition.getURI());
            detailBuilder.append("\"");
        }
        return detailBuilder.toString();
    }

    public static String functionDefinitionToSignature(IFunctionDefinition functionDefinition,
            ICompilerProject currentProject) {
        StringBuilder labelBuilder = new StringBuilder();
        labelBuilder.append(functionDefinition.getQualifiedName());
        String parametersAndReturnValue = functionDefinitionToParametersAndReturnValue(functionDefinition,
                currentProject);
        labelBuilder.append(parametersAndReturnValue);
        return labelBuilder.toString();
    }

    private static String functionDefinitionToParametersAndReturnValue(IFunctionDefinition functionDefinition,
            ICompilerProject currentProject) {
        StringBuilder labelBuilder = new StringBuilder();
        labelBuilder.append("(");
        IParameterDefinition[] parameters = functionDefinition.getParameters();
        for (int i = 0, count = parameters.length; i < count; i++) {
            if (i > 0) {
                labelBuilder.append(", ");
            }
            IParameterDefinition parameterDefinition = parameters[i];
            if (parameterDefinition.isRest()) {
                labelBuilder.append(IASLanguageConstants.REST);
            }
            String baseName = parameterDefinition.getBaseName();
            if (baseName == null || baseName.length() == 0) {
                if (parameterDefinition.isRest()) {
                    labelBuilder.append(IASLanguageConstants.REST_IDENTIFIER);
                } else {
                    labelBuilder.append("arg");
                    labelBuilder.append(i);
                }
            } else {
                labelBuilder.append(baseName);
            }
            labelBuilder.append(":");
            labelBuilder.append(getTypeAsDisplayString(parameterDefinition));
            if (parameterDefinition.hasDefaultValue()) {
                labelBuilder.append(" = ");
                Object defaultValue = parameterDefinition.resolveDefaultValue(currentProject);
                String defaultValueAsString = valueToString(defaultValue);
                if (defaultValueAsString != null) {
                    labelBuilder.append(defaultValueAsString);
                } else {
                    // I don't know how this might happen, but this is probably
                    // a safe fallback value
                    labelBuilder.append(IASLanguageConstants.NULL);
                }
            }
        }
        labelBuilder.append(")");
        if (!functionDefinition.isConstructor()) {
            String returnTypeString = functionDefinition.getReturnTypeAsDisplayString();
            if (returnTypeString.length() == 0) {
                // replace empty string with *
                returnTypeString = IASLanguageConstants.ANY_TYPE;
            }
            labelBuilder.append(":");
            labelBuilder.append(returnTypeString);
        }
        return labelBuilder.toString();
    }

    private static void insertHeaderCommentIntoTextDocument(IDefinition definition, StringBuilder builder) {
        builder.append("// Generated from: ");
        builder.append(definition.getContainingFilePath());
        builder.append("\n");
    }

    private static void appendDefinitionName(IDefinition definition, StringBuilder textDocumentBuilder,
            IDefinition definitionToFind, boolean includeASDoc, DefinitionAsText result) {
        String name = definition.getBaseName();
        if (definition.equals(definitionToFind)) {
            DefinitionURI defUri = new DefinitionURI();
            List<String> symbols = new ArrayList<>();
            IDefinition currentDefinition = definition;
            while (currentDefinition != null) {
                if (currentDefinition instanceof IPackageDefinition) {
                    break;
                }
                String qualifiedName = currentDefinition.getQualifiedName();
                symbols.add(0, qualifiedName);
                defUri.swcFilePath = definition.getContainingFilePath();
                defUri.rootDefinition = currentDefinition;
                currentDefinition = currentDefinition.getParent();
            }
            defUri.symbols = symbols;
            defUri.definition = definition;
            defUri.includeASDoc = includeASDoc;
            result.uri = defUri.encode();
            String[] lines = textDocumentBuilder.toString().split(NEW_LINE);
            result.startLine = lines.length - 1;
            result.startColumn = lines[lines.length - 1].length();
            result.endLine = result.startLine;
            result.endColumn = result.startColumn + name.length();
        }
        textDocumentBuilder.append(name);
    }

    private static void appendNamespace(INamespaceDefinition ns, StringBuilder textDocumentBuilder) {
        if (ns == null) {
            return;
        }
        if (ns instanceof IInterfaceNamespaceDefinition) {
            return;
        }
        switch (ns.getURI()) {
            case NAMESPACE_URI_AS3: {
                textDocumentBuilder.append(NAMESPACE_ID_AS3);
                textDocumentBuilder.append(" ");
                break;
            }
            case NAMESPACE_URI_FLASH_PROXY: {
                textDocumentBuilder.append(NAMESPACE_ID_FLASH_PROXY);
                textDocumentBuilder.append(" ");
                break;
            }
            case NAMESPACE_MX_INTERNAL: {
                textDocumentBuilder.append(NAMESPACE_ID_MX_INTERNAL);
                textDocumentBuilder.append(" ");
                break;
            }
            default: {
                // not ideal, but I can't figure out how to find the name
                textDocumentBuilder.append("/* ");
                textDocumentBuilder.append(ns.getURI());
                textDocumentBuilder.append(" */ ");
            }
        }
    }

    private static String getTypeAsDisplayString(IDefinition definition) {
        String typeAsDisplayString = definition.getTypeAsDisplayString();
        if (typeAsDisplayString.length() == 0) {
            // replace empty string with *
            return IASLanguageConstants.ANY_TYPE;
        }
        return typeAsDisplayString;
    }

    private static String increaseIndent(String indent) {
        return indent + INDENT;
    }

    private static String decreaseIndent(String indent) {
        if (indent.length() == 0) {
            return indent;
        }
        return indent.substring(1);
    }

    private static void insertASDocIntoTextDocument(IDocumentableDefinition def, StringBuilder textDocumentBuilder,
            ICompilerProject currentProject, String indent) {
        String comment = DefinitionDocumentationUtils.getDocumentationForDefinition(def, false,
                currentProject, true);
        if (comment == null) {
            return;
        }
        textDocumentBuilder.append(indent);
        textDocumentBuilder.append(ASDOC_START);
        // assume tab is 4 characters wide
        final int baseWidth = indent.replace("\t", "    ").length() + ASDOC_LINE_START.length();
        for (String line : comment.split(NEW_LINE)) {
            int currentWidth = 80;
            for (String word : line.trim().split(" ", -1)) {
                // 1 extra for space
                if (currentWidth + 1 + word.length() < 80) {
                    textDocumentBuilder.append(" ");
                    textDocumentBuilder.append(word);
                    currentWidth += 1 + word.length();
                } else {
                    textDocumentBuilder.append(NEW_LINE);
                    textDocumentBuilder.append(indent);
                    textDocumentBuilder.append(ASDOC_LINE_START);
                    textDocumentBuilder.append(word);
                    currentWidth = baseWidth + word.length();
                }
            }
        }
        textDocumentBuilder.append(NEW_LINE);
        textDocumentBuilder.append(indent);
        textDocumentBuilder.append(ASDOC_END);
        textDocumentBuilder.append(NEW_LINE);
    }
}
