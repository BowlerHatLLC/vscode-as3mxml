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
package com.as3mxml.asconfigc.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OptionsUtils {
	private static final Pattern ADDITIONAL_OPTIONS_PATTERN = Pattern.compile(
			"([\\-+]([^\\s]+\\+?=)(('([^'])*')|(\"([^\"])*\")|([^\\s\"',]+))(,(('([^'])*')|(\"([^\"])*\")|([^\\s\"']+)))*)|('([^'])*')|(\"([^\"])*\")|([^\\s\"']+)");

	public static List<String> parseAdditionalOptions(String combined) {
		List<String> result = new ArrayList<>();
		Matcher matcher = ADDITIONAL_OPTIONS_PATTERN.matcher(combined);
		while (matcher.find()) {
			String option = matcher.group();
			result.add(option);
		}
		return result;
	}
}