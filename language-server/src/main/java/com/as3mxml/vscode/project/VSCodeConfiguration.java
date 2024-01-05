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
package com.as3mxml.vscode.project;

import org.apache.royale.compiler.config.Configuration;
import org.apache.royale.compiler.config.ConfigurationValue;
import org.apache.royale.compiler.exceptions.ConfigurationException;
import org.apache.royale.compiler.internal.config.annotations.Config;
import org.apache.royale.compiler.internal.config.annotations.Mapping;

public class VSCodeConfiguration extends Configuration {
	private final int UNSET_SWF_VERSION = -1;
	private final int MINIMUM_SMART_PLAYER_VERSION = 12;
	private final int MINIMUM_SMART_SWF_VERSION = 23;

	public VSCodeConfiguration() {
		super();
	}

	private int swfVersion = UNSET_SWF_VERSION;

	public int getSwfVersion() {
		if (swfVersion == UNSET_SWF_VERSION) {
			int targetPlayerMajorVersion = getTargetPlayerMajorVersion();
			if (targetPlayerMajorVersion >= MINIMUM_SMART_PLAYER_VERSION) {
				return MINIMUM_SMART_SWF_VERSION + targetPlayerMajorVersion - MINIMUM_SMART_PLAYER_VERSION;
			}
		}
		swfVersion = super.getSwfVersion();
		return swfVersion;
	}

	@Config
	@Mapping("swf-version")
	public void setSwfVersion(ConfigurationValue cv, int version) throws ConfigurationException {
		super.setSwfVersion(cv, version);
		swfVersion = version;
	}
}