/*
Copyright 2016-2019 Bowler Hat LLC

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
package com.as3mxml.vscode;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.services.WorkspaceService;

public class ActionScriptWorkspaceService implements WorkspaceService
{
    private static final String ROYALE_ASJS_RELATIVE_PATH_CHILD = "./royale-asjs";
    private static final String FRAMEWORKS_RELATIVE_PATH_CHILD = "./frameworks";
    private static final String PROPERTY_FRAMEWORK_LIB = "royalelib";

	public ActionScriptTextDocumentService textDocumentService;

	public ActionScriptWorkspaceService()
	{
	}

	@Override
	public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params)
	{
		//delegate to the ActionScriptTextDocumentService, since that's
		//where the compiler is running, and the compiler is needed to
		//find workspace symbols
		return textDocumentService.workspaceSymbol(params);
	}

	@Override
	public void didChangeConfiguration(DidChangeConfigurationParams params)
	{
		if(!(params.getSettings() instanceof JsonObject))
		{
			return;
		}
		JsonObject settings = (JsonObject) params.getSettings();
		this.updateSDK(settings);
		this.updateRealTimeProblems(settings);
	}

	@Override
	public void didChangeWatchedFiles(DidChangeWatchedFilesParams params)
	{
		//delegate to the ActionScriptTextDocumentService, since that's
		//where the compiler is running, and the compiler may need to
		//know about file changes
		textDocumentService.didChangeWatchedFiles(params);
	}

	@Override
	public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params)
	{
		for(WorkspaceFolder folder : params.getEvent().getRemoved())
		{
			textDocumentService.removeWorkspaceFolder(folder);
		}
		for(WorkspaceFolder folder : params.getEvent().getAdded())
		{
			textDocumentService.addWorkspaceFolder(folder);
		}
	}
     
	@JsonNotification("$/setTraceNotification")
	public void setTraceNotification(Object params)
	{
		//this may be ignored. see: eclipse/lsp4j#22
	}

	@Override
	public CompletableFuture<Object> executeCommand(ExecuteCommandParams params)
	{
		return textDocumentService.executeCommand(params);
	}

	private void updateSDK(JsonObject settings)
	{
		if (!settings.has("as3mxml"))
		{
			return;
		}
		JsonObject as3mxml = settings.get("as3mxml").getAsJsonObject();
		if (!as3mxml.has("sdk"))
		{
			return;
		}
		JsonObject sdk = as3mxml.get("sdk").getAsJsonObject();
		String frameworkSDK = null;
		if (sdk.has("framework"))
		{
			JsonElement frameworkValue = sdk.get("framework");
			if (!frameworkValue.isJsonNull())
			{
				frameworkSDK = frameworkValue.getAsString();
			}
		}
		if (frameworkSDK == null && sdk.has("editor"))
		{
			//for legacy reasons, we fall back to the editor SDK
			JsonElement editorValue = sdk.get("editor");
			if (!editorValue.isJsonNull())
			{
				frameworkSDK = editorValue.getAsString();
			}
		}
		if (frameworkSDK == null)
		{
			//keep using the existing framework for now
			return;
		}
		String frameworkLib = null;
		Path frameworkLibPath = Paths.get(frameworkSDK).resolve(FRAMEWORKS_RELATIVE_PATH_CHILD).toAbsolutePath().normalize();
		if (frameworkLibPath.toFile().exists())
		{
			//if the frameworks directory exists, use it!
			frameworkLib = frameworkLibPath.toString();
		}
		else 
		{
			//if the frameworks directory doesn't exist, we also
			//need to check for Apache Royale's unique layout
			//with the royale-asjs directory
			Path royalePath = Paths.get(frameworkSDK).resolve(ROYALE_ASJS_RELATIVE_PATH_CHILD).resolve(FRAMEWORKS_RELATIVE_PATH_CHILD).toAbsolutePath().normalize();
			if(royalePath.toFile().exists())
			{
				frameworkLib = royalePath.toString();
			}
		}
		if (frameworkLib == null)
		{
			//keep using the existing framework for now
			return;
		}
		String oldFrameworkLib = System.getProperty(PROPERTY_FRAMEWORK_LIB);
		if (oldFrameworkLib.equals(frameworkLib))
		{
			//frameworks library has not changed
			return;
		}
		System.setProperty(PROPERTY_FRAMEWORK_LIB, frameworkLib);
		textDocumentService.checkForProblemsNow();
	}

	private void updateRealTimeProblems(JsonObject settings)
	{
		if (!settings.has("as3mxml"))
		{
			return;
		}
		JsonObject as3mxml = settings.get("as3mxml").getAsJsonObject();
		if (!as3mxml.has("problems"))
		{
			return;
		}
		JsonObject problems = as3mxml.get("problems").getAsJsonObject();
		if (!problems.has("realTime"))
		{
			return;
		}
		boolean realTime = problems.get("realTime").getAsBoolean();
		textDocumentService.setRealTimeProblems(realTime);
	}
}