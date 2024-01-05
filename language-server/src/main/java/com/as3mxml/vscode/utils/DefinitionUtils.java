/*
Copyright 2016-2024 Bowler Hat LLC

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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import com.as3mxml.vscode.project.ILspProject;

import org.apache.royale.abc.ABCParser;
import org.apache.royale.abc.Pool;
import org.apache.royale.abc.PoolingABCVisitor;
import org.apache.royale.compiler.constants.IASLanguageConstants;
import org.apache.royale.compiler.constants.IMetaAttributeConstants;
import org.apache.royale.compiler.definitions.IAccessorDefinition;
import org.apache.royale.compiler.definitions.IAppliedVectorDefinition;
import org.apache.royale.compiler.definitions.IClassDefinition;
import org.apache.royale.compiler.definitions.IClassDefinition.ClassClassification;
import org.apache.royale.compiler.definitions.IClassDefinition.IClassIterator;
import org.apache.royale.compiler.definitions.IDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition;
import org.apache.royale.compiler.definitions.IFunctionDefinition.FunctionClassification;
import org.apache.royale.compiler.definitions.IGetterDefinition;
import org.apache.royale.compiler.definitions.IInterfaceDefinition;
import org.apache.royale.compiler.definitions.IInterfaceDefinition.InterfaceClassification;
import org.apache.royale.compiler.definitions.INamespaceDefinition;
import org.apache.royale.compiler.definitions.ISetterDefinition;
import org.apache.royale.compiler.definitions.ITypeDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition;
import org.apache.royale.compiler.definitions.IVariableDefinition.VariableClassification;
import org.apache.royale.compiler.definitions.metadata.IMetaTag;
import org.apache.royale.compiler.internal.projects.CompilerProject;
import org.apache.royale.compiler.internal.scopes.ASProjectScope;
import org.apache.royale.compiler.internal.scopes.ASScope;
import org.apache.royale.compiler.internal.scopes.TypeScope;
import org.apache.royale.compiler.projects.ICompilerProject;
import org.apache.royale.compiler.tree.as.IASNode;
import org.apache.royale.compiler.tree.as.IBinaryOperatorNode;
import org.apache.royale.compiler.tree.as.IDynamicAccessNode;
import org.apache.royale.compiler.tree.as.IExpressionNode;
import org.apache.royale.compiler.tree.as.IIdentifierNode;
import org.apache.royale.compiler.tree.as.IMemberAccessExpressionNode;
import org.apache.royale.compiler.tree.as.IOperatorNode.OperatorType;
import org.apache.royale.compiler.units.ICompilationUnit;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

public class DefinitionUtils {
	private static final String PROPERTY_FRAMEWORK_LIB = "royalelib";
	private static final String SDK_FRAMEWORKS_PATH_SIGNATURE = "/frameworks/";
	private static final String SDK_SOURCE_PATH_SIGNATURE_UNIX = "/frameworks/projects/";
	private static final String SDK_SOURCE_PATH_SIGNATURE_WINDOWS = "\\frameworks\\projects\\";

	/**
	 * Returns the qualified name of the required type for child elements, or null.
	 */
	public static String getMXMLChildElementTypeForDefinition(IDefinition definition, ICompilerProject project) {
		return getMXMLChildElementTypeForDefinition(definition, project, true);
	}

	private static String getMXMLChildElementTypeForDefinition(IDefinition definition, ICompilerProject project,
			boolean resolvePaired) {
		IMetaTag arrayElementType = definition.getMetaTagByName(IMetaAttributeConstants.ATTRIBUTE_ARRAYELEMENTTYPE);
		if (arrayElementType != null) {
			return arrayElementType.getValue();
		}
		IMetaTag instanceType = definition.getMetaTagByName(IMetaAttributeConstants.ATTRIBUTE_INSTANCETYPE);
		if (instanceType != null) {
			return instanceType.getValue();
		}
		if (resolvePaired) {
			if (definition instanceof IGetterDefinition) {
				IGetterDefinition getterDefinition = (IGetterDefinition) definition;
				ISetterDefinition setterDefinition = getterDefinition.resolveSetter(project);
				if (setterDefinition != null) {
					String result = getMXMLChildElementTypeForDefinition(setterDefinition, project, false);
					if (result != null) {
						return result;
					}
				}
			} else if (definition instanceof ISetterDefinition) {
				ISetterDefinition setterDefinition = (ISetterDefinition) definition;
				IGetterDefinition getterDefinition = setterDefinition.resolveGetter(project);
				if (getterDefinition != null) {
					String result = getMXMLChildElementTypeForDefinition(getterDefinition, project, false);
					if (result != null) {
						return result;
					}
				}
			}
		}
		ITypeDefinition typeDefinition = definition.resolveType(project);
		if (typeDefinition != null) {
			String qualifiedName = typeDefinition.getQualifiedName();
			if (qualifiedName.equals(IASLanguageConstants.Array)) {
				// the wrapping array can be omitted, and since there's no
				// [ArrayElementType] metadata, default to Object
				return IASLanguageConstants.Object;
			}
			return qualifiedName;
		}
		return null;
	}

	public static String getDefinitionDebugSourceFilePath(IDefinition definition, ICompilerProject project) {
		ASProjectScope projectScope = (ASProjectScope) project.getScope();
		ICompilationUnit unit = projectScope.getCompilationUnitForDefinition(definition);
		if (unit == null) {
			return null;
		}
		try {
			byte[] abcBytes = unit.getABCBytesRequest().get().getABCBytes();
			ABCParser parser = new ABCParser(abcBytes);
			PoolingABCVisitor visitor = new PoolingABCVisitor();
			parser.parseABC(visitor);
			Pool<String> pooledStrings = visitor.getStringPool();
			for (String pooledString : pooledStrings.getValues()) {
				if (pooledString.contains(SDK_SOURCE_PATH_SIGNATURE_UNIX)
						|| pooledString.contains(SDK_SOURCE_PATH_SIGNATURE_WINDOWS)) {
					// just go with the first one that we find
					return transformDebugFilePath(pooledString);
				}
			}
		} catch (InterruptedException e) {
			// safe to ignore
		}
		return null;
	}

	public static boolean isImplementationOfInterface(IClassDefinition classDefinition,
			IInterfaceDefinition interfaceDefinition, ICompilerProject project) {
		Iterator<IInterfaceDefinition> interfaceIterator = classDefinition.interfaceIterator(project);
		while (interfaceIterator.hasNext()) {
			IInterfaceDefinition implementedInterfaceDefinition = interfaceIterator.next();
			if (implementedInterfaceDefinition.equals(interfaceDefinition)) {
				return true;
			}
		}
		return false;
	}

	public static boolean extendsOrImplements(ICompilerProject project, ITypeDefinition typeDefinition,
			String qualifiedNameToFind) {
		if (typeDefinition instanceof IClassDefinition) {
			IClassDefinition classDefinition = (IClassDefinition) typeDefinition;
			IClassIterator classIterator = classDefinition.classIterator(project, true);
			while (classIterator.hasNext()) {
				IClassDefinition baseClassDefinition = classIterator.next();
				if (baseClassDefinition.getQualifiedName().equals(qualifiedNameToFind)) {
					return true;
				}
			}
			Iterator<IInterfaceDefinition> interfaceIterator = classDefinition.interfaceIterator(project);
			if (interfaceIteratorContainsQualifiedName(interfaceIterator, qualifiedNameToFind)) {
				return true;
			}
		} else if (typeDefinition instanceof IInterfaceDefinition) {
			IInterfaceDefinition interfaceDefinition = (IInterfaceDefinition) typeDefinition;
			Iterator<IInterfaceDefinition> interfaceIterator = interfaceDefinition.interfaceIterator(project, true);
			if (interfaceIteratorContainsQualifiedName(interfaceIterator, qualifiedNameToFind)) {
				return true;
			}
		}
		return false;
	}

	public static IDefinition resolveWithExtras(IIdentifierNode identifierNode, ILspProject project) {
		return resolveWithExtras(identifierNode, project, null);
	}

	public static IDefinition resolveWithExtras(IIdentifierNode identifierNode, ILspProject project,
			Range sourceRange) {
		IDefinition definition = identifierNode.resolve(project);
		if (definition != null) {
			if (definition instanceof IAccessorDefinition) {
				boolean preferSetter = false;
				IASNode parentNode = identifierNode.getParent();
				while (parentNode != null) {
					if (parentNode instanceof IMemberAccessExpressionNode) {
						IMemberAccessExpressionNode memberAccess = (IMemberAccessExpressionNode) parentNode;
						IASNode rightOperandNode = memberAccess.getRightOperandNode();
						if (identifierNode.equals(rightOperandNode)
								|| ASTUtils.nodeContainsNode(rightOperandNode, identifierNode)) {
							parentNode = parentNode.getParent();
							continue;
						}
						break;
					} else if (parentNode instanceof IBinaryOperatorNode) {
						IBinaryOperatorNode binaryOperatorNode = (IBinaryOperatorNode) parentNode;
						if (OperatorType.ASSIGNMENT.equals(binaryOperatorNode.getOperator())) {
							IASNode leftOperandNode = binaryOperatorNode.getLeftOperandNode();
							preferSetter = identifierNode.equals(leftOperandNode)
									|| ASTUtils.nodeContainsNode(leftOperandNode, identifierNode);
						}
						break;
					}
					break;
				}

				if (preferSetter && definition instanceof IGetterDefinition) {
					IGetterDefinition getterDefinition = (IGetterDefinition) definition;
					ISetterDefinition setterDefinition = getterDefinition.resolveSetter(project);
					if (setterDefinition != null) {
						definition = setterDefinition;
					}
				} else if (!preferSetter && definition instanceof ISetterDefinition) {
					ISetterDefinition setterDefinition = (ISetterDefinition) definition;
					IGetterDefinition getterDefinition = setterDefinition.resolveGetter(project);
					if (getterDefinition != null) {
						definition = getterDefinition;
					}
				}
			}
			return definition;
		}

		IASNode parentNode = identifierNode.getParent();
		if (parentNode instanceof IMemberAccessExpressionNode) {
			IMemberAccessExpressionNode memberAccess = (IMemberAccessExpressionNode) parentNode;
			IExpressionNode leftOperand = memberAccess.getLeftOperandNode();
			if (leftOperand instanceof IDynamicAccessNode) {
				IDynamicAccessNode dynamicAccess = (IDynamicAccessNode) leftOperand;
				IExpressionNode dynamicLeftOperandNode = dynamicAccess.getLeftOperandNode();
				ITypeDefinition leftType = dynamicLeftOperandNode.resolveType(project);
				if (leftType instanceof IAppliedVectorDefinition) {
					IAppliedVectorDefinition vectorDef = (IAppliedVectorDefinition) leftType;
					ITypeDefinition elementType = vectorDef.resolveElementType(project);
					if (elementType != null) {
						TypeScope typeScope = (TypeScope) elementType.getContainedScope();
						ASScope otherScope = (ASScope) identifierNode.getContainingScope().getScope();
						Set<INamespaceDefinition> namespaceSet = ScopeUtils.getNamespaceSetForScopes(typeScope,
								otherScope, project);
						definition = typeScope.getPropertyByNameForMemberAccess((CompilerProject) project,
								identifierNode.getName(), namespaceSet);
					}
				}
			}
		}

		if (definition == null) {
			IASNode currentNode = parentNode;
			while (currentNode instanceof IMemberAccessExpressionNode) {
				IMemberAccessExpressionNode memberAccessNode = (IMemberAccessExpressionNode) currentNode;
				definition = memberAccessNode.resolve(project);
				if (definition != null) {
					if (sourceRange != null) {
						sourceRange.setStart(new Position(memberAccessNode.getLine(), memberAccessNode.getColumn()));
						sourceRange
								.setEnd(new Position(memberAccessNode.getEndLine(), memberAccessNode.getEndColumn()));
					}
					break;
				}
				currentNode = currentNode.getParent();
			}
		}

		return definition;
	}

	public static IDefinition resolveTypeWithExtras(IIdentifierNode identifierNode, ILspProject project) {
		ITypeDefinition definition = identifierNode.resolveType(project);
		if (definition != null) {
			return definition;
		}

		IASNode parentNode = identifierNode.getParent();
		if (parentNode instanceof IMemberAccessExpressionNode) {
			IMemberAccessExpressionNode memberAccess = (IMemberAccessExpressionNode) parentNode;
			IExpressionNode leftOperand = memberAccess.getLeftOperandNode();
			if (leftOperand instanceof IDynamicAccessNode) {
				IDynamicAccessNode dynamicAccess = (IDynamicAccessNode) leftOperand;
				IExpressionNode dynamicLeftOperandNode = dynamicAccess.getLeftOperandNode();
				ITypeDefinition leftType = dynamicLeftOperandNode.resolveType(project);
				if (leftType instanceof IAppliedVectorDefinition) {
					IAppliedVectorDefinition vectorDef = (IAppliedVectorDefinition) leftType;
					ITypeDefinition elementType = vectorDef.resolveElementType(project);
					if (elementType != null) {
						TypeScope typeScope = (TypeScope) elementType.getContainedScope();
						ASScope otherScope = (ASScope) identifierNode.getContainingScope().getScope();
						Set<INamespaceDefinition> namespaceSet = ScopeUtils.getNamespaceSetForScopes(typeScope,
								otherScope, project);
						IDefinition propertyDefinition = typeScope.getPropertyByNameForMemberAccess(
								(CompilerProject) project, identifierNode.getName(), namespaceSet);
						if (propertyDefinition != null) {
							definition = propertyDefinition.resolveType(project);
						}

					}
				}
			}
		}

		return definition;
	}

	public static IDefinition getDefinitionByName(String qualifiedName, Collection<ICompilationUnit> compilationUnits) {
		for (ICompilationUnit unit : compilationUnits) {
			if (unit == null) {
				continue;
			}
			try {
				Collection<IDefinition> definitions = unit.getFileScopeRequest().get()
						.getExternallyVisibleDefinitions();
				if (definitions == null) {
					continue;
				}
				for (IDefinition definition : definitions) {
					if (definition.isImplicit()) {
						continue;
					}
					if (definition instanceof IClassDefinition) {
						IClassDefinition classDefinition = (IClassDefinition) definition;
						if (!ClassClassification.PACKAGE_MEMBER
								.equals(classDefinition.getClassClassification())) {
							continue;
						}
					} else if (definition instanceof IInterfaceDefinition) {
						IInterfaceDefinition interfaceDefinition = (IInterfaceDefinition) definition;
						if (!InterfaceClassification.PACKAGE_MEMBER
								.equals(interfaceDefinition.getInterfaceClassification())) {
							continue;
						}
					} else if (definition instanceof IFunctionDefinition) {
						IFunctionDefinition functionDefinition = (IFunctionDefinition) definition;
						if (!FunctionClassification.PACKAGE_MEMBER
								.equals(functionDefinition.getFunctionClassification())) {
							continue;
						}
					} else if (definition instanceof IVariableDefinition) {
						IVariableDefinition variableDefinition = (IVariableDefinition) definition;
						if (!VariableClassification.PACKAGE_MEMBER
								.equals(variableDefinition.getVariableClassification())) {
							continue;
						}
					} else {
						// unknown definition type
						continue;
					}
					if (!qualifiedName.equals(definition.getQualifiedName())) {
						// this definition is top-level. no import required.
						continue;
					}
					return definition;
				}
			} catch (Exception e) {
				// safe to ignore
			}
		}
		return null;
	}

	private static String transformDebugFilePath(String sourceFilePath) {
		int index = -1;
		if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			// the debug file path divides directories with ; instead of slash in
			// a couple of places, but it's easy to fix
			sourceFilePath = sourceFilePath.replace(';', '\\');
			sourceFilePath = sourceFilePath.replace('/', '\\');
			index = sourceFilePath.indexOf(SDK_SOURCE_PATH_SIGNATURE_WINDOWS);
		} else {
			sourceFilePath = sourceFilePath.replace(';', '/');
			sourceFilePath = sourceFilePath.replace('\\', '/');
			index = sourceFilePath.indexOf(SDK_SOURCE_PATH_SIGNATURE_UNIX);
		}
		if (index == -1) {
			return sourceFilePath;
		}
		String newSourceFilePath = sourceFilePath.substring(index + SDK_FRAMEWORKS_PATH_SIGNATURE.length());
		Path frameworkPath = Paths.get(System.getProperty(PROPERTY_FRAMEWORK_LIB));
		Path transformedPath = frameworkPath.resolve(newSourceFilePath);
		if (transformedPath.toFile().exists()) {
			// only transform the path if the transformed file exists
			// if it doesn't exist, the original path may be valid
			return transformedPath.toFile().getAbsolutePath();
		}
		return sourceFilePath;
	}

	private static boolean interfaceIteratorContainsQualifiedName(Iterator<IInterfaceDefinition> interfaceIterator,
			String qualifiedName) {
		while (interfaceIterator.hasNext()) {
			IInterfaceDefinition interfaceDefinition = interfaceIterator.next();
			if (interfaceDefinition.getQualifiedName().equals(qualifiedName)) {
				return true;
			}
		}
		return false;
	}
}