/*
Copyright 2016-2017 Bowler Hat LLC

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
package com.nextgenactionscript.asconfigc.compiler;

import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.nextgenactionscript.asconfigc.utils.OptionsFormatter;
import com.nextgenactionscript.asconfigc.utils.PathUtils;
import com.nextgenactionscript.asconfigc.utils.JsonUtils;

public class CompilerOptionsParser
{
	public CompilerOptionsParser()
	{
	}

	private boolean checkPaths = true;
	
	public boolean getCheckPaths()
	{
		return checkPaths;
	}

	public void setCheckPaths(boolean value)
	{
		checkPaths = value;
	}

	public void parse(JsonNode options, Boolean debugBuild, List<String> result) throws FileNotFoundException
	{
		Iterator<String> iterator = options.fieldNames();
		while(iterator.hasNext())
		{
			String key = iterator.next();
			switch(key)
			{
				case CompilerOptions.ACCESSIBLE:
				{
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.ADVANCED_TELEMETRY:
				{
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.BENCHMARK:
				{
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.DEBUG:
				{
					if(debugBuild == null)
					{
						//don't set -debug if it's been overridden
						OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					}
					break;
				}
				case CompilerOptions.DEBUG_PASSWORD:
				{
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.DEFAULT_FRAME_RATE:
				{
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.DEFAULT_SIZE:
				{
					setDefaultSize(options.get(key), result);
					break;
				}
				case CompilerOptions.DEFINE:
				{
					setDefine(options.get(key), result);
					break;
				}
				case CompilerOptions.DUMP_CONFIG:
				{
					OptionsFormatter.setPathValue(key, options.get(key).asText(), false, result);
					break;
				}
				case CompilerOptions.EXTERNAL_LIBRARY_PATH:
				{
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, checkPaths, result);
					break;
				}
				case CompilerOptions.HTML_OUTPUT_FILENAME:
				{
					OptionsFormatter.setPathValue(key, options.get(key).asText(), false, result);
					break;
				}
				case CompilerOptions.HTML_TEMPLATE:
				{
					OptionsFormatter.setPathValue(key, options.get(key).asText(), checkPaths, result);
					break;
				}
				case CompilerOptions.INCLUDE_CLASSES:
				{
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendValues(key, values, result);
					break;
				}
				case CompilerOptions.INCLUDE_NAMESPACES:
				{
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendValues(key, values, result);
					break;
				}
				case CompilerOptions.INCLUDE_SOURCES:
				{
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, checkPaths, result);
					break;
				}
				case CompilerOptions.JS_COMPILER_OPTION:
				{
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					appendJSCompilerOptions(key, values, result);
					break;
				}
				case CompilerOptions.JS_EXTERNAL_LIBRARY_PATH:
				{
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, checkPaths, result);
					break;
				}
				case CompilerOptions.JS_LIBRARY_PATH:
				{
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, checkPaths, result);
					break;
				}
				case CompilerOptions.JS_OUTPUT_TYPE:
				{
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.KEEP_AS3_METADATA:
				{
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendValues(key, values, result);
					break;
				}
				case CompilerOptions.KEEP_GENERATED_ACTIONSCRIPT:
				{
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.LIBRARY_PATH:
				{
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, checkPaths, result);
					break;
				}
				case CompilerOptions.LINK_REPORT:
				{
					OptionsFormatter.setPathValue(key, options.get(key).asText(), false, result);
					break;
				}
				case CompilerOptions.LOAD_CONFIG:
				{
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, checkPaths, result);
					break;
				}
				case CompilerOptions.LOCALE:
				{
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.setValues(key, values, result);
					break;
				}
				case CompilerOptions.NAMESPACE:
				{
					setNamespace(options.get(key), result);
					break;
				}
				case CompilerOptions.OPTIMIZE:
				{
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.OMIT_TRACE_STATEMENTS:
				{
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.OUTPUT:
				{
					OptionsFormatter.setPathValue(key, options.get(key).asText(), false, result);
					break;
				}
				case CompilerOptions.PRELOADER:
				{
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.REMOVE_CIRCULARS:
				{
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.SIZE_REPORT:
				{
					OptionsFormatter.setPathValue(key, options.get(key).asText(), false, result);
					break;
				}
				case CompilerOptions.SOURCE_MAP:
				{
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.SOURCE_PATH:
				{
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, checkPaths, result);
					break;
				}
				case CompilerOptions.STATIC_LINK_RUNTIME_SHARED_LIBRARIES:
				{
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.STRICT:
				{
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.SWF_EXTERNAL_LIBRARY_PATH:
				{
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, checkPaths, result);
					break;
				}
				case CompilerOptions.SWF_LIBRARY_PATH:
				{
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.appendPaths(key, values, checkPaths, result);
					break;
				}
				case CompilerOptions.SWF_VERSION:
				{
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.TARGET_PLAYER:
				{
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.TARGETS:
				{
					List<String> values = JsonUtils.jsonNodeToListOfStrings(options.get(key));
					OptionsFormatter.setValuesWithCommas(key, values, result);
					break;
				}
				case CompilerOptions.TOOLS_LOCALE:
				{
					OptionsFormatter.setValue(key, options.get(key).asText(), result);
					break;
				}
				case CompilerOptions.USE_DIRECT_BLIT:
				{
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.USE_GPU:
				{
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.USE_NETWORK:
				{
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.USE_RESOURCE_BUNDLE_METADATA:
				{
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.VERBOSE_STACKTRACES:
				{
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				case CompilerOptions.WARNINGS:
				{
					OptionsFormatter.setBoolean(key, options.get(key).asBoolean(), result);
					break;
				}
				default:
				{
					throw new Error("Unknown compiler option: " + key);
				}
			}
		}
	}
	
	private void setNamespace(JsonNode values, List<String> result)
	{
		int size = values.size();
		if(size == 0)
		{
			return;
		}
		for(int i = 0; i < size; i++)
		{
			JsonNode currentValue = values.get(i);
			String uri = currentValue.get(CompilerOptions.NAMESPACE__URI).asText();
			String manifest = currentValue.get(CompilerOptions.NAMESPACE__MANIFEST).asText();
			result.add("--" + CompilerOptions.NAMESPACE);
			result.add(uri);
			result.add(PathUtils.escapePath(manifest, false));
		}
	}

	private void appendJSCompilerOptions(String optionName, List<String> values, List<String> result)
	{
		int size = values.size();
		if(size == 0)
		{
			return;
		}
		for(int i = 0; i < size; i++)
		{
			String currentValue = values.get(i);
			result.add("--" + optionName + "+=\"" + currentValue.toString() + "\"");
		}
	}

	private void setDefaultSize(JsonNode sizePair, List<String> result)
	{
		int width = sizePair.get(CompilerOptions.DEFAULT_SIZE__WIDTH).asInt();
		int height = sizePair.get(CompilerOptions.DEFAULT_SIZE__HEIGHT).asInt();
		result.add("--" + CompilerOptions.DEFAULT_SIZE);
		result.add(Integer.toString(width));
		result.add(Integer.toString(height));
	}

	private void setDefine(JsonNode values, List<String> result)
	{
		int size = values.size();
		if(size == 0)
		{
			return;
		}
		for(int i = 0; i < size; i++)
		{
			JsonNode currentValue = values.get(i);
			String defineName = currentValue.get(CompilerOptions.DEFINE__NAME).asText();
			JsonNode defineValue = currentValue.get(CompilerOptions.DEFINE__VALUE);
			String defineValueAsString = defineValue.asText();
			if(defineValue.isTextual())
			{
				defineValueAsString = defineValueAsString.replace("\"", "\\\"");
				defineValueAsString = "\"" + defineValueAsString + "\"";
			}
			result.add("--" + CompilerOptions.DEFINE + "+=" +
				defineName + "," + defineValueAsString);
		}
	}
}