package com.nextgenactionscript.vscode.utils;

import java.nio.file.Path;

public class ActionScriptSDKUtils
{
	public static boolean isRoyaleSDK(Path path)
	{
		path = path.resolve("frameworks");
        return isRoyaleFramework(path);
	}

	public static boolean isRoyaleFramework(Path path)
	{
		path = path.resolve("../royale-sdk-description.xml");
		return path.toFile().exists();
	}
}