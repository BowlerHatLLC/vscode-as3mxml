package com.as3mxml.asconfigc.htmlTemplate;

import java.util.Arrays;
import java.util.List;

public class HTMLTemplateOptions
{
	public static final String TITLE = "title";
	public static final String BGCOLOR = "bgcolor";
	public static final String USE_BROWSER_HISTORY = "useBrowserHistory";
	public static final String VERSION_MAJOR = "version_major";
	public static final String VERSION_MINOR = "version_minor";
	public static final String VERSION_REVISION = "version_revision";
	public static final String EXPRESS_INSTALL_SWF = "expressInstallSwf";
	public static final String APPLICATION = "application";
	public static final String SWF = "swf";
	public static final String WIDTH = "width";
	public static final String HEIGHT = "height";
	//don't forget to add new options to the ALL_OPTIONS list

	public static final List<String> ALL_OPTIONS = Arrays.asList(
		TITLE,
		BGCOLOR,
		USE_BROWSER_HISTORY,
		VERSION_MAJOR,
		VERSION_MINOR,
		VERSION_REVISION,
		EXPRESS_INSTALL_SWF,
		APPLICATION,
		SWF,
		WIDTH,
		HEIGHT
	);
}