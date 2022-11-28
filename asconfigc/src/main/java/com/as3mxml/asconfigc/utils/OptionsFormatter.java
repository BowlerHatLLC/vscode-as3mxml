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
package com.as3mxml.asconfigc.utils;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OptionsFormatter {
	private static String expandEnvironmentVariable(String value)
	{
		Map<String, String> envMap = System.getenv();
		String pattern = "(\\$\\{|%)([A-Za-z0-9]+)(\\}|%)";
		Pattern expr = Pattern.compile(pattern);
		Matcher matcher = expr.matcher(value);
		while (matcher.find()) {
			String envValue = envMap.get(matcher.group(2));
			if (envValue == null) {
				envValue = "";
			} else {
				envValue = envValue.replace("\\", "\\\\");
			}
			Pattern subexpr = Pattern.compile(Pattern.quote(matcher.group(0)));
			value = subexpr.matcher(value).replaceAll(envValue);
		}

		return value;
	}

	public static void setValue(String optionName, String value, List<String> result) {
		result.add("--" + optionName + "=" + expandEnvironmentVariable(value));
	}

	public static void setBoolean(String optionName, boolean value, List<String> result) {
		result.add("--" + optionName + "=" + Boolean.toString(value));
	}

	public static void setPathValue(String optionName, String value, List<String> result) {
		result.add("--" + optionName + "=" + expandEnvironmentVariable(value));
	}

	public static void setValues(String optionName, List<String> values, List<String> result) {
		int size = values.size();
		if (size == 0) {
			return;
		}
		String firstValue = values.get(0);
		result.add("--" + optionName + "=" + expandEnvironmentVariable(firstValue));
		appendValues(optionName, values.subList(1, size), result);
	}

	public static void setValuesWithCommas(String optionName, List<String> values, List<String> result) {
		StringBuilder joined = new StringBuilder();
		for (int i = 0, size = values.size(); i < size; i++) {
			if (i > 0) {
				joined.append(",");
			}
			joined.append(expandEnvironmentVariable(values.get(i)));
		}
		result.add("--" + optionName + "=" + joined.toString());
	}

	public static void appendValues(String optionName, List<String> values, List<String> result) {
		int size = values.size();
		if (size == 0) {
			return;
		}
		for (int i = 0; i < size; i++) {
			String currentValue = values.get(i);
			result.add("--" + optionName + "+=" + expandEnvironmentVariable(currentValue));
		}
	}

	public static void appendPaths(String optionName, List<String> paths, List<String> result) {
		int pathsCount = paths.size();
		for (int i = 0; i < pathsCount; i++) {
			String currentPath = paths.get(i);
			result.add("--" + optionName + "+=" + expandEnvironmentVariable(currentPath));
		}
	}

	public static void setThenAppendPaths(String optionName, List<String> paths, List<String> result) {
		int pathsCount = paths.size();
		for (int i = 0; i < pathsCount; i++) {
			String currentPath = paths.get(i);
			if (i == 0) {
				result.add("--" + optionName + "=" + expandEnvironmentVariable(currentPath));
			} else {
				result.add("--" + optionName + "+=" + expandEnvironmentVariable(currentPath));
			}
		}
	}
}
