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
package com.nextgenactionscript.asconfigc.utils;

public class PathUtils
{
	public static String escapePath(String pathToEscape)
	{
		return escapePath(pathToEscape, true);
	}

	public static String escapePath(String pathToEscape, boolean force)
	{
		if(!force && !pathToEscape.contains(" "))
		{
			return pathToEscape;
		}
		//we don't want spaces in paths or they will be interpreted as new
		//command line options
		if(System.getProperty("os.name").startsWith("Windows"))
		{
			//on windows, paths may be wrapped in quotes to include spaces
			pathToEscape = "\"" + pathToEscape + "\"";
		}
		else
		{
			//on other platforms, a backslash preceding a string will
			//include the space in the path
			pathToEscape = pathToEscape.replace(" ", "\\ ");
		}
		return pathToEscape;
	}
}