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
package com.as3mxml.vscode.commands;

public interface ICommandConstants {
	public static final String ADD_IMPORT = "as3mxml.addImport";
	public static final String ADD_MXML_NAMESPACE = "as3mxml.addMXMLNamespace";
	public static final String ORGANIZE_IMPORTS_IN_URI = "as3mxml.organizeImportsInUri";
	public static final String ORGANIZE_IMPORTS_IN_DIRECTORY = "as3mxml.organizeImportsInDirectory";
	public static final String REMOVE_UNUSED_IMPORTS_IN_URI = "as3mxml.removeUnusedImportsInUri";
	public static final String ADD_MISSING_IMPORTS_IN_URI = "as3mxml.addMissingImportsInUri";
	public static final String SORT_IMPORTS_IN_URI = "as3mxml.sortImportsInUri";
	public static final String QUICK_COMPILE = "as3mxml.quickCompile";
	public static final String GET_ACTIVE_PROJECT_URIS = "as3mxml.getActiveProjectURIs";
	public static final String GET_LIBRARY_DEFINITION_TEXT = "as3mxml.getLibraryDefinitionText";
	public static final String SET_ROYALE_PREFERRED_TARGET = "as3mxml.setRoyalePreferredTarget";
}