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
package com.nextgenactionscript.vscode.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.ITypeDefinition;
import org.apache.royale.compiler.projects.ICompilerProject;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IClassNode;
import org.apache.royale.compiler.tree.as.IFunctionCallNode;
import org.apache.royale.compiler.tree.as.IFunctionNode;
import org.apache.royale.compiler.tree.as.IIdentifierNode;
import org.apache.royale.compiler.tree.as.IImportNode;
import org.apache.royale.compiler.tree.as.IInterfaceNode;
import org.apache.royale.compiler.tree.as.IScopedNode;
import org.apache.royale.compiler.tree.as.ITransparentContainerNode;
import org.apache.royale.compiler.tree.as.IVariableNode;
import org.apache.royale.compiler.units.ICompilationUnit;

public class ASTUtils
{
    private static final String DOT_STAR = ".*";

	public static Set<String> findUnresolvedIdentifiersToImport(IASNode node, ICompilerProject project)
    {
        HashSet<String> importsToAdd = new HashSet<>();
        findUnresolvedIdentifiersToImport(node, project, importsToAdd);
        return importsToAdd;
	}
	
    public static Set<IImportNode> findImportNodesToRemove(IASNode node, ICompilerProject project)
    {
        HashSet<IImportNode> importsToRemove = new HashSet<>();
        findImportNodesToRemove(node, project, new HashSet<>(), importsToRemove);
        return importsToRemove;
	}
	
	public static List<IDefinition> findTypesThatMatchName(String nameToFind, Collection<ICompilationUnit> compilationUnits)
	{
		ArrayList<IDefinition> result = new ArrayList<>();
        for (ICompilationUnit unit : compilationUnits)
        {
            if (unit == null)
            {
                continue;
            }
            try
            {
                Collection<IDefinition> definitions = unit.getFileScopeRequest().get().getExternallyVisibleDefinitions();
                if (definitions == null)
                {
                    continue;
                }
                for (IDefinition definition : definitions)
                {
                    if (definition instanceof ITypeDefinition)
                    {
                        ITypeDefinition typeDefinition = (ITypeDefinition) definition;
                        String baseName = typeDefinition.getBaseName();
                        if (typeDefinition.getQualifiedName().equals(baseName))
                        {
                            //this definition is top-level. no import required.
                            continue;
                        }
                        if (baseName.equals(nameToFind))
                        {
                            result.add(typeDefinition);
                        }
                    }
                }
            }
            catch (Exception e)
            {
                //safe to ignore
            }
		}
		return result;
	}
    
    protected static void findUnresolvedIdentifiersToImport(IASNode node, ICompilerProject project, Set<String> importsToAdd)
    {
		IASNode gp = node.getParent();
        for (int i = 0, count = node.getChildCount(); i < count; i++)
        {
            IASNode child = node.getChild(i);
            if (child instanceof IImportNode)
            {
                continue;
            }
            if (child instanceof IIdentifierNode)
            {
                IIdentifierNode identifierNode = (IIdentifierNode) child;
                IDefinition definition = identifierNode.resolve(project);
                if (definition == null)
                {
                    if (node instanceof IFunctionCallNode)
                    {
                        IFunctionCallNode functionCallNode = (IFunctionCallNode) node;
                        if (functionCallNode.isNewExpression())
                        {
							importsToAdd.add(identifierNode.getName());
                        }
					}
                    else if (node instanceof IVariableNode)
                    {
                        IVariableNode variableNode = (IVariableNode) node;
                        if (variableNode.getVariableTypeNode().equals(identifierNode))
                        {
							importsToAdd.add(identifierNode.getName());
                        }
					}
					else if (node instanceof IFunctionNode)
					{
						IFunctionNode functionNode = (IFunctionNode) node;
						if (functionNode.getReturnTypeNode().equals(identifierNode))
						{
							importsToAdd.add(identifierNode.getName());
						}
					}
					else if (node instanceof IClassNode)
					{
						IClassNode classNode = (IClassNode) node;
						if (classNode.getBaseClassExpressionNode().equals(identifierNode))
						{
							importsToAdd.add(identifierNode.getName());
						}
					}
					else if (node instanceof ITransparentContainerNode
							&& gp instanceof IClassNode)
					{
						IClassNode classNode = (IClassNode) gp;
						if (Arrays.asList(classNode.getImplementedInterfaceNodes()).contains(identifierNode))
						{
							importsToAdd.add(identifierNode.getName());
						}
					}
					else if (node instanceof ITransparentContainerNode
							&& gp instanceof IInterfaceNode)
					{
						IInterfaceNode classNode = (IInterfaceNode) gp;
						if (Arrays.asList(classNode.getExtendedInterfaceNodes()).contains(identifierNode))
						{
							importsToAdd.add(identifierNode.getName());
						}
					}
                }
            }
            findUnresolvedIdentifiersToImport(child, project, importsToAdd);
        }
	}
    
    protected static void findImportNodesToRemove(IASNode node, ICompilerProject project, Set<String> referencedDefinitions, Set<IImportNode> importsToRemove)
    {
        Set<IImportNode> childImports = null;
        if (node instanceof IScopedNode)
        {
            childImports = new HashSet<>();
        }
        for (int i = 0, count = node.getChildCount(); i < count; i++)
        {
            IASNode child = node.getChild(i);
            if (childImports != null && child instanceof IImportNode)
            {
                IImportNode importNode = (IImportNode) child;
                childImports.add(importNode);
                continue;
            }
            if (child instanceof IIdentifierNode)
            {
                IIdentifierNode identifierNode = (IIdentifierNode) child;
                IDefinition definition = identifierNode.resolve(project);
                if (definition != null
                        && definition.getPackageName().length() > 0
                        && definition.getQualifiedName().startsWith(definition.getPackageName()))
                {
                    referencedDefinitions.add(definition.getQualifiedName());
                }
            }
            findImportNodesToRemove(child, project, referencedDefinitions, importsToRemove);
        }
        if (childImports != null)
        {
            childImports.removeIf(importNode ->
            {
                if (importNode.getAbsoluteStart() == -1)
                {
                    return true;
                }
                String importName = importNode.getImportName();
                if (importName.endsWith(DOT_STAR))
                {
                    String importPackage = importName.substring(0, importName.length() - 2);
                    for (String reference : referencedDefinitions)
                    {
                        if(reference.startsWith(importPackage)
                            && !reference.substring(importPackage.length() + 1).contains("."))
                        {
                            //an entire package is imported, so check if any
                            //references are in that package
                            return true;
                        }
                    }
                    return false;
                }
                return referencedDefinitions.contains(importName);
            });
            importsToRemove.addAll(childImports);
        }
    }
}