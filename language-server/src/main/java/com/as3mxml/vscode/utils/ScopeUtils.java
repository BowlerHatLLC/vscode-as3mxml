/*
Copyright 2016-2021 Bowler Hat LLC

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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IInterfaceDefinition;
import org.apache.royale.compiler.definitions.INamespaceDefinition;
import org.apache.royale.compiler.definitions.ITypeDefinition;
import org.apache.royale.compiler.internal.scopes.ASScope;
import org.apache.royale.compiler.internal.scopes.TypeScope;
import org.apache.royale.compiler.projects.ICompilerProject;
import org.apache.royale.compiler.scopes.IASScope;
import org.apache.royale.compiler.tree.as.IScopedNode;

public class ScopeUtils {
    public static Set<INamespaceDefinition> getNamespaceSetForScopes(TypeScope typeScope, ASScope otherScope,
            ICompilerProject project) {
        Set<INamespaceDefinition> namespaceSet = new HashSet<>(otherScope.getNamespaceSet(project));
        if (typeScope.getContainingDefinition() instanceof IInterfaceDefinition) {
            //interfaces have a special namespace that isn't actually the same
            //as public, but should be treated the same way
            IInterfaceDefinition interfaceDefinition = (IInterfaceDefinition) typeScope.getContainingDefinition();
            collectInterfaceNamespaces(interfaceDefinition, namespaceSet, project);
        }
        IClassDefinition otherContainingClass = otherScope.getContainingClass();
        if (otherContainingClass != null) {
            IClassDefinition classDefinition = typeScope.getContainingClass();
            if (classDefinition != null) {
                boolean isSuperClass = Arrays.asList(otherContainingClass.resolveAncestry(project))
                        .contains(classDefinition);
                if (isSuperClass) {
                    //if the containing class of the type scope is a superclass
                    //of the other scope, we need to add the protected
                    //namespaces from the super classes
                    do {
                        namespaceSet.add(classDefinition.getProtectedNamespaceReference());
                        classDefinition = classDefinition.resolveBaseClass(project);
                    } while (classDefinition instanceof IClassDefinition);
                }
            }
        }
        return namespaceSet;
    }

    public static ITypeDefinition getContainingTypeDefinitionForScope(IScopedNode scopedNode) {
        IScopedNode currentNode = scopedNode;
        while (currentNode != null) {
            IASScope currentScope = currentNode.getScope();
            if (currentScope instanceof TypeScope) {
                TypeScope typeScope = (TypeScope) currentScope;
                return (ITypeDefinition) typeScope.getContainingDefinition();
            }
            currentNode = currentNode.getContainingScope();
        }
        return null;
    }

    private static void collectInterfaceNamespaces(IInterfaceDefinition interfaceDefinition,
            Set<INamespaceDefinition> namespaceSet, ICompilerProject project) {
        TypeScope typeScope = (TypeScope) interfaceDefinition.getContainedScope();
        namespaceSet.addAll(typeScope.getNamespaceSet(project));
        IInterfaceDefinition[] interfaceDefinitions = interfaceDefinition.resolveExtendedInterfaces(project);
        for (IInterfaceDefinition extendedInterface : interfaceDefinitions) {
            collectInterfaceNamespaces(extendedInterface, namespaceSet, project);
        }
    }
}