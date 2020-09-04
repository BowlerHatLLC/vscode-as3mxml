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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ProjectUtilsTests {
	//--- findOutputDirectory

	@Test
	void testFindOutputDirectoryForSWF() {
		String path = ProjectUtils.findOutputDirectory("src/Test.as", "output/Test.swf", true);
		File file = new File(System.getProperty("user.dir"), "output");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findOutputDirectory() returned incorrect value.");
	}

	@Test
	void testFindOutputDirectoryForSWFWithoutOutputPath() {
		String path = ProjectUtils.findOutputDirectory("src/Test.as", null, true);
		File file = new File(System.getProperty("user.dir"), "src");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findOutputDirectory() returned incorrect value.");
	}

	@Test
	void testFindOutputDirectoryForSWFWithoutMainFileAndOutputPath() {
		String path = ProjectUtils.findOutputDirectory(null, null, true);
		Assertions.assertEquals(System.getProperty("user.dir"), path,
				"ProjectUtils.findOutputDirectory() returned incorrect value.");
	}

	@Test
	void testFindOutputDirectoryForSWFWithMainFileInRootAndWithoutOutputPath() {
		String path = ProjectUtils.findOutputDirectory("Test.as", null, true);
		Assertions.assertEquals(System.getProperty("user.dir"), path,
				"ProjectUtils.findOutputDirectory() returned incorrect value.");
	}

	@Test
	void testFindOutputDirectoryForSWFWithAbsoluteMainFileWithoutOutputPath() {
		String mainFile = "/path/to/Test.as";
		if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			mainFile = "C:\\path\\to\\Test.as";
		}
		String path = ProjectUtils.findOutputDirectory(mainFile, null, true);
		File file = new File(mainFile);
		Assertions.assertEquals(file.getParent(), path, "ProjectUtils.findOutputDirectory() returned incorrect value.");
	}

	@Test
	void testFindOutputDirectoryForJS() {
		String path = ProjectUtils.findOutputDirectory("src/Test.as", "output", false);
		File file = new File(System.getProperty("user.dir"), "output");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findOutputDirectory() returned incorrect value.");
	}

	@Test
	void testFindOutputDirectoryForJSWithMainFileWithoutOutputPath() {
		String path = ProjectUtils.findOutputDirectory("path/to/Test.as", null, false);
		File file = new File(System.getProperty("user.dir"), "path/to");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findOutputDirectory() returned incorrect value.");
	}

	@Test
	void testFindOutputDirectoryForJSWithMainFileInSrcWithoutOutputPath() {
		String path = ProjectUtils.findOutputDirectory("src/Test.as", null, false);
		Assertions.assertEquals(System.getProperty("user.dir"), path,
				"ProjectUtils.findOutputDirectory() returned incorrect value.");
	}

	@Test
	void testFindOutputDirectoryForJSWithMainFileInSrcMainFlexWithoutOutputPath() {
		String path = ProjectUtils.findOutputDirectory("src/main/flex/Test.as", null, false);
		Assertions.assertEquals(System.getProperty("user.dir"), path,
				"ProjectUtils.findOutputDirectory() returned incorrect value.");
	}

	@Test
	void testFindOutputDirectoryForJSWithMainFileInSrcMainRoyaleWithoutOutputPath() {
		String path = ProjectUtils.findOutputDirectory("src/main/royale/Test.as", null, false);
		Assertions.assertEquals(System.getProperty("user.dir"), path,
				"ProjectUtils.findOutputDirectory() returned incorrect value.");
	}

	@Test
	void testFindOutputDirectoryForJSWithMainFileInRootAndWithoutOutputPath() {
		String path = ProjectUtils.findOutputDirectory("Test.as", null, false);
		Assertions.assertEquals(System.getProperty("user.dir"), path,
				"ProjectUtils.findOutputDirectory() returned incorrect value.");
	}

	@Test
	void testFindOutputDirectoryForJSWithAbsoluteMainFileWithoutOutputPath() {
		String mainFile = "/path/to/Test.as";
		if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			mainFile = "C:\\path\\to\\Test.as";
		}
		String path = ProjectUtils.findOutputDirectory(mainFile, null, false);
		File file = new File(mainFile);
		Assertions.assertEquals(file.getParent(), path, "ProjectUtils.findOutputDirectory() returned incorrect value.");
	}

	@Test
	void testFindOutputDirectoryForJSWithAbsoluteMainFileInSrcWithoutOutputPath() {
		String mainFile = "/path/to/src/Test.as";
		if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			mainFile = "C:\\path\\to\\src\\Test.as";
		}
		String path = ProjectUtils.findOutputDirectory(mainFile, null, false);
		File file = new File(mainFile);
		Assertions.assertEquals(file.getParentFile().getParent(), path,
				"ProjectUtils.findOutputDirectory() returned incorrect value.");
	}

	@Test
	void testFindOutputDirectoryForJSWithAbsoluteMainFileInSrcMainRoyaleWithoutOutputPath() {
		String mainFile = "/path/to/src/main/royale/Test.as";
		if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			mainFile = "C:\\path\\to\\src\\main\\royale\\Test.as";
		}
		String path = ProjectUtils.findOutputDirectory(mainFile, null, false);
		File file = new File(mainFile);
		Assertions.assertEquals(file.getParentFile().getParentFile().getParentFile().getParent(), path,
				"ProjectUtils.findOutputDirectory() returned incorrect value.");
	}

	@Test
	void testFindOutputDirectoryForJSWithAbsoluteMainFileInSrcMainFlexWithoutOutputPath() {
		String mainFile = "/path/to/src/main/flex/Test.as";
		if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			mainFile = "C:\\path\\to\\src\\main\\flex\\Test.as";
		}
		String path = ProjectUtils.findOutputDirectory(mainFile, null, false);
		File file = new File(mainFile);
		Assertions.assertEquals(file.getParentFile().getParentFile().getParentFile().getParent(), path,
				"ProjectUtils.findOutputDirectory() returned incorrect value.");
	}

	//--- findAIRDescriptorOutputPath

	@Test
	void testFindAIRDescriptorOutputPathForSWF() {
		String path = ProjectUtils.findAIRDescriptorOutputPath("src/Test.as", "src/Test-app.xml", "bin/Test.swf", true,
				false);
		File file = new File(System.getProperty("user.dir"), "bin/Test-app.xml");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findAIRDescriptorOutputPath() returned incorrect value.");
	}

	@Test
	void testFindAIRDescriptorOutputPathForSWFWithoutOutputPath() {
		String path = ProjectUtils.findAIRDescriptorOutputPath("src/Test.as", "src/Test-app.xml", null, true, false);
		File file = new File(System.getProperty("user.dir"), "src/Test-app.xml");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findAIRDescriptorOutputPath() returned incorrect value.");
	}

	@Test
	void testFindAIRDescriptorOutputPathForSWFWithAbsoluteMainFileWithoutOutputPath() {
		String mainFile = "/path/to/Test.as";
		if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			mainFile = "C:\\path\\to\\Test.as";
		}
		String path = ProjectUtils.findAIRDescriptorOutputPath(mainFile, "src/Test-app.xml", null, true, false);
		File file = new File(mainFile);
		file = new File(file.getParent(), "Test-app.xml");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findAIRDescriptorOutputPath() returned incorrect value.");
	}

	@Test
	void testFindAIRDescriptorOutputPathForJSDebug() {
		String path = ProjectUtils.findAIRDescriptorOutputPath("src/Test.as", "src/Test-app.xml", "output", false,
				true);
		File file = new File(System.getProperty("user.dir"), "output/bin/js-debug/Test-app.xml");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findAIRDescriptorOutputPath() returned incorrect value.");
	}

	@Test
	void testFindAIRDescriptorOutputPathForJSDebugWithoutOutputPath() {
		String path = ProjectUtils.findAIRDescriptorOutputPath("path/to/Test.as", "path/to/Test-app.xml", null, false,
				true);
		File file = new File(System.getProperty("user.dir"), "path/to/bin/js-debug/Test-app.xml");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findAIRDescriptorOutputPath() returned incorrect value.");
	}

	@Test
	void testFindAIRDescriptorOutputPathForJSDebugInSrcWithoutOutputPath() {
		String path = ProjectUtils.findAIRDescriptorOutputPath("src/Test.as", "src/Test-app.xml", null, false, true);
		File file = new File(System.getProperty("user.dir"), "bin/js-debug/Test-app.xml");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findAIRDescriptorOutputPath() returned incorrect value.");
	}

	@Test
	void testFindAIRDescriptorOutputPathForJSDebugInSrcMainRoyaleWithoutOutputPath() {
		String path = ProjectUtils.findAIRDescriptorOutputPath("src/main/royale/Test.as",
				"src/main/royale/Test-app.xml", null, false, true);
		File file = new File(System.getProperty("user.dir"), "bin/js-debug/Test-app.xml");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findAIRDescriptorOutputPath() returned incorrect value.");
	}

	@Test
	void testFindAIRDescriptorOutputPathForJSDebugInSrcMainFlexWithoutOutputPath() {
		String path = ProjectUtils.findAIRDescriptorOutputPath("src/main/flex/Test.as", "src/main/flex/Test-app.xml",
				null, false, true);
		File file = new File(System.getProperty("user.dir"), "bin/js-debug/Test-app.xml");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findAIRDescriptorOutputPath() returned incorrect value.");
	}

	@Test
	void testFindAIRDescriptorOutputPathForJSDebugInRootWithoutOutputPath() {
		String path = ProjectUtils.findAIRDescriptorOutputPath("Test.as", "Test-app.xml", null, false, true);
		File file = new File(System.getProperty("user.dir"), "bin/js-debug/Test-app.xml");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findAIRDescriptorOutputPath() returned incorrect value.");
	}

	@Test
	void testFindAIRDescriptorOutputPathForJSDebugWithAbsoluteMainFileWithoutOutputPath() {
		String mainFile = "/path/to/Test.as";
		if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			mainFile = "C:\\path\\to\\Test.as";
		}
		String path = ProjectUtils.findAIRDescriptorOutputPath(mainFile, "src/Test-app.xml", null, false, true);
		File file = new File(mainFile);
		file = new File(file.getParent(), "bin/js-debug/Test-app.xml");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findAIRDescriptorOutputPath() returned incorrect value.");
	}

	@Test
	void testFindAIRDescriptorOutputPathForJSRelease() {
		String path = ProjectUtils.findAIRDescriptorOutputPath("src/Test.as", "src/Test-app.xml", "output", false,
				false);
		File file = new File(System.getProperty("user.dir"), "output/bin/js-release/Test-app.xml");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findAIRDescriptorOutputPath() returned incorrect value.");
	}

	@Test
	void testFindAIRDescriptorOutputPathForJSReleaseWithoutOutputPath() {
		String path = ProjectUtils.findAIRDescriptorOutputPath("path/to/src/Test.as", "path/to/src/Test-app.xml", null,
				false, false);
		File file = new File(System.getProperty("user.dir"), "path/to/bin/js-release/Test-app.xml");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findAIRDescriptorOutputPath() returned incorrect value.");
	}

	@Test
	void testFindAIRDescriptorOutputPathForJSReleaseInSrcWithoutOutputPath() {
		String path = ProjectUtils.findAIRDescriptorOutputPath("src/Test.as", "src/Test-app.xml", null, false, false);
		File file = new File(System.getProperty("user.dir"), "bin/js-release/Test-app.xml");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findAIRDescriptorOutputPath() returned incorrect value.");
	}

	@Test
	void testFindAIRDescriptorOutputPathForJSReleaseInSrcMainRoyaleWithoutOutputPath() {
		String path = ProjectUtils.findAIRDescriptorOutputPath("src/main/royale/Test.as",
				"src/main/royale/Test-app.xml", null, false, false);
		File file = new File(System.getProperty("user.dir"), "bin/js-release/Test-app.xml");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findAIRDescriptorOutputPath() returned incorrect value.");
	}

	@Test
	void testFindAIRDescriptorOutputPathForJSReleaseInSrcMainFlexWithoutOutputPath() {
		String path = ProjectUtils.findAIRDescriptorOutputPath("src/main/flex/Test.as", "src/main/flex/Test-app.xml",
				null, false, false);
		File file = new File(System.getProperty("user.dir"), "bin/js-release/Test-app.xml");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findAIRDescriptorOutputPath() returned incorrect value.");
	}

	@Test
	void testFindAIRDescriptorOutputPathForJSReleaseInRootWithoutOutputPath() {
		String path = ProjectUtils.findAIRDescriptorOutputPath("Test.as", "Test-app.xml", null, false, false);
		File file = new File(System.getProperty("user.dir"), "bin/js-release/Test-app.xml");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findAIRDescriptorOutputPath() returned incorrect value.");
	}

	@Test
	void testFindAIRDescriptorOutputPathForJSReleaseWithAbsoluteMainFileWithoutOutputPath() {
		String mainFile = "/path/to/Test.as";
		if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			mainFile = "C:\\path\\to\\Test.as";
		}
		String path = ProjectUtils.findAIRDescriptorOutputPath(mainFile, "src/Test-app.xml", null, false, false);
		File file = new File(mainFile);
		file = new File(file.getParent(), "bin/js-release/Test-app.xml");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findAIRDescriptorOutputPath() returned incorrect value.");
	}

	//--- findApplicationContent

	@Test
	void testFindApplicationContentForSWF() {
		String path = ProjectUtils.findApplicationContent("src/Test.as", "output/Test.swf", true);
		Assertions.assertEquals("Test.swf", path, "ProjectUtils.findApplicationContent() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentForSWFWithoutMainFile() {
		String path = ProjectUtils.findApplicationContent(null, "output/Test.swf", true);
		Assertions.assertEquals("Test.swf", path, "ProjectUtils.findApplicationContent() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentForSWFWithoutOutputPath() {
		String path = ProjectUtils.findApplicationContent("src/Test.as", null, true);
		Assertions.assertEquals("Test.swf", path, "ProjectUtils.findApplicationContent() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentForSWFWithoutMainFileAndOutputPath() {
		String path = ProjectUtils.findApplicationContent(null, null, true);
		Assertions.assertNull(path, "ProjectUtils.findApplicationContent() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentForJS() {
		String path = ProjectUtils.findApplicationContent("src/Test.as", "output", false);
		Assertions.assertEquals("index.html", path, "ProjectUtils.findApplicationContent() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentForJSWithoutMainFile() {
		String path = ProjectUtils.findApplicationContent(null, "output", false);
		Assertions.assertEquals("index.html", path, "ProjectUtils.findApplicationContent() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentForJSWithoutOutputPath() {
		String path = ProjectUtils.findApplicationContent("src/Test.as", null, false);
		Assertions.assertEquals("index.html", path, "ProjectUtils.findApplicationContent() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentForJSWithoutMainFileAndOutputPath() {
		String path = ProjectUtils.findApplicationContent(null, null, false);
		Assertions.assertEquals("index.html", path, "ProjectUtils.findApplicationContent() returned incorrect value.");
	}

	//--- findApplicationContentOutputPath

	@Test
	void testFindApplicationContentOutputPathForSWF() {
		String path = ProjectUtils.findApplicationContentOutputPath("src/Test.as", "output/Test.swf", true, false);
		File file = new File(System.getProperty("user.dir"), "output/Test.swf");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForSWFWithoutMainFile() {
		String path = ProjectUtils.findApplicationContentOutputPath(null, "output/Test.swf", true, false);
		File file = new File(System.getProperty("user.dir"), "output/Test.swf");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForSWFWithoutOutputPath() {
		String path = ProjectUtils.findApplicationContentOutputPath("src/Test.as", null, true, false);
		File file = new File(System.getProperty("user.dir"), "src/Test.swf");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForSWFWithoutMainFileOrOutputPath() {
		String path = ProjectUtils.findApplicationContentOutputPath(null, null, true, false);
		Assertions.assertNull(path, "ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForSWFWithAbsoluteMainFileWithoutOutputPath() {
		String mainFile = "/path/to/Test.as";
		if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			mainFile = "C:\\path\\to\\Test.as";
		}
		String path = ProjectUtils.findApplicationContentOutputPath(mainFile, null, true, false);
		File file = new File(mainFile);
		file = new File(file.getParent(), "Test.swf");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForJSRelease() {
		String path = ProjectUtils.findApplicationContentOutputPath("src/Test.as", "output", false, false);
		File file = new File(System.getProperty("user.dir"), "output/bin/js-release/index.html");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForJSReleaseWithoutMainFile() {
		String path = ProjectUtils.findApplicationContentOutputPath(null, "output", false, false);
		File file = new File(System.getProperty("user.dir"), "output/bin/js-release/index.html");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForJSReleaseWithoutOutputPath() {
		String path = ProjectUtils.findApplicationContentOutputPath("path/to/Test.as", null, false, false);
		File file = new File(System.getProperty("user.dir"), "path/to/bin/js-release/index.html");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForJSReleaseInSrcWithoutOutputPath() {
		String path = ProjectUtils.findApplicationContentOutputPath("src/Test.as", null, false, false);
		File file = new File(System.getProperty("user.dir"), "bin/js-release/index.html");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForJSReleaseInSrcMainRoyaleWithoutOutputPath() {
		String path = ProjectUtils.findApplicationContentOutputPath("src/main/royale/Test.as", null, false, false);
		File file = new File(System.getProperty("user.dir"), "bin/js-release/index.html");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForJSReleaseInSrcMainFlexWithoutOutputPath() {
		String path = ProjectUtils.findApplicationContentOutputPath("src/main/flex/Test.as", null, false, false);
		File file = new File(System.getProperty("user.dir"), "bin/js-release/index.html");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForJSReleaseInRootWithoutOutputPath() {
		String path = ProjectUtils.findApplicationContentOutputPath("Test.as", null, false, false);
		File file = new File(System.getProperty("user.dir"), "bin/js-release/index.html");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForJSReleaseWithoutMainFileAndOutputPath() {
		String path = ProjectUtils.findApplicationContentOutputPath(null, null, false, false);
		File file = new File(System.getProperty("user.dir"), "bin/js-release/index.html");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForJSReleaseWithAbsoluteMainFileWithoutOutputPath() {
		String mainFile = "/path/to/Test.as";
		if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			mainFile = "C:\\path\\to\\Test.as";
		}
		String path = ProjectUtils.findApplicationContentOutputPath(mainFile, null, false, false);
		File file = new File(mainFile);
		file = new File(file.getParent(), "bin/js-release/index.html");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForJSDebug() {
		String path = ProjectUtils.findApplicationContentOutputPath("src/Test.as", "output", false, true);
		File file = new File(System.getProperty("user.dir"), "output/bin/js-debug/index.html");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForJSDebugWithoutMainFile() {
		String path = ProjectUtils.findApplicationContentOutputPath(null, "output", false, true);
		File file = new File(System.getProperty("user.dir"), "output/bin/js-debug/index.html");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForJSDebugWithoutOutputPath() {
		String path = ProjectUtils.findApplicationContentOutputPath("path/to/Test.as", null, false, true);
		File file = new File(System.getProperty("user.dir"), "path/to/bin/js-debug/index.html");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForJSDebugInSrcWithoutOutputPath() {
		String path = ProjectUtils.findApplicationContentOutputPath("src/Test.as", null, false, true);
		File file = new File(System.getProperty("user.dir"), "bin/js-debug/index.html");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForJSDebugInSrcMainRoyaleWithoutOutputPath() {
		String path = ProjectUtils.findApplicationContentOutputPath("src/main/royale/Test.as", null, false, true);
		File file = new File(System.getProperty("user.dir"), "bin/js-debug/index.html");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForJSDebugInSrcMainFlexWithoutOutputPath() {
		String path = ProjectUtils.findApplicationContentOutputPath("src/main/flex/Test.as", null, false, true);
		File file = new File(System.getProperty("user.dir"), "bin/js-debug/index.html");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForJSDebugInRootWithoutOutputPath() {
		String path = ProjectUtils.findApplicationContentOutputPath("Test.as", null, false, true);
		File file = new File(System.getProperty("user.dir"), "bin/js-debug/index.html");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForJSDebugWithoutMainFileAndOutputPath() {
		String path = ProjectUtils.findApplicationContentOutputPath(null, null, false, true);
		File file = new File(System.getProperty("user.dir"), "bin/js-debug/index.html");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	@Test
	void testFindApplicationContentOutputPathForJSDebugWithAbsoluteMainFileWithoutOutputPath() {
		String mainFile = "/path/to/Test.as";
		if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			mainFile = "C:\\path\\to\\Test.as";
		}
		String path = ProjectUtils.findApplicationContentOutputPath(mainFile, null, false, true);
		File file = new File(mainFile);
		file = new File(file.getParent(), "bin/js-debug/index.html");
		Assertions.assertEquals(file.toPath().toString(), path,
				"ProjectUtils.findApplicationContentOutputPath() returned incorrect value.");
	}

	//--- assetPathToOutputPath

	@Test
	void testOutputPathForAssetAtRootOfImplicitSourcePathForMainFile() throws IOException {
		String result = ProjectUtils.assetPathToOutputPath("src/asset.txt", "src/Test.as", null, "bin");
		File file = new File(System.getProperty("user.dir"), "bin/asset.txt");
		Assertions.assertEquals(file.getAbsolutePath(), result,
				"ProjectUtils.assetPathToOutputPath() returned incorrect value.");
	}

	@Test
	void testOutputPathForAssetInSubDirectoryOfImplicitSourcePathForMainFile() throws IOException {
		String result = ProjectUtils.assetPathToOutputPath("src/sub-directory/asset.txt", "src/Test.as", null, "bin");
		File file = new File(System.getProperty("user.dir"), "bin/sub-directory/asset.txt");
		Assertions.assertEquals(file.getAbsolutePath(), result,
				"ProjectUtils.assetPathToOutputPath() returned incorrect value.");
	}

	@Test
	void testOutputPathForAssetAtRootOfExplicitSourcePath() throws IOException {
		List<String> sourcePaths = new ArrayList<>();
		sourcePaths.add("custom-src");
		String result = ProjectUtils.assetPathToOutputPath("custom-src/asset.txt", null, sourcePaths, "bin");
		File file = new File(System.getProperty("user.dir"), "bin/asset.txt");
		Assertions.assertEquals(file.getAbsolutePath(), result,
				"ProjectUtils.assetPathToOutputPath() returned incorrect value.");
	}

	@Test
	void testOutputPathForAssetInSubDirectoryOfExplicitSourcePath() throws IOException {
		List<String> sourcePaths = new ArrayList<>();
		sourcePaths.add("custom-src");
		String result = ProjectUtils.assetPathToOutputPath("custom-src/sub-directory/asset.txt", null, sourcePaths,
				"bin");
		File file = new File(System.getProperty("user.dir"), "bin/sub-directory/asset.txt");
		Assertions.assertEquals(file.getAbsolutePath(), result,
				"ProjectUtils.assetPathToOutputPath() returned incorrect value.");
	}

	//--- populateAdobeAIRDescriptorContent

	@Test
	void testPopulateAdobeAIRDescriptorContent() {
		String descriptorContent = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n"
				+ "<application xmlns=\"http://ns.adobe.com/air/application/24.0\">\n" + "\t<id>com.example.Main</id>\n"
				+ "\t<versionNumber>0.0.0</versionNumber>\n" + "\t<filename>Main</filename>\n" + "\t<name>Main</name>\n"
				+ "\t<initialWindow>\n" + "\t\t<content>[Path to content will be replaced by asconfigc]</content>\n"
				+ "\t\t<visible>true</visible>\n" + "\t</initialWindow>\n" + "</application>";
		String expected = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n"
				+ "<application xmlns=\"http://ns.adobe.com/air/application/24.0\">\n" + "\t<id>com.example.Main</id>\n"
				+ "\t<versionNumber>0.0.0</versionNumber>\n" + "\t<filename>Main</filename>\n" + "\t<name>Main</name>\n"
				+ "\t<initialWindow>\n" + "\t\t<content>Test.swf</content>\n" + "\t\t<visible>true</visible>\n"
				+ "\t</initialWindow>\n" + "</application>";
		String result = ProjectUtils.populateAdobeAIRDescriptorContent(descriptorContent, "Test.swf");
		Assertions.assertEquals(expected, result,
				"ProjectUtils.populateAdobeAIRDescriptorContent() returned incorrect value.");
	}

	@Test
	void testPopulateAdobeAIRDescriptorContentEmpty() {
		String descriptorContent = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n"
				+ "<application xmlns=\"http://ns.adobe.com/air/application/24.0\">\n" + "\t<id>com.example.Main</id>\n"
				+ "\t<versionNumber>0.0.0</versionNumber>\n" + "\t<filename>Main</filename>\n" + "\t<name>Main</name>\n"
				+ "\t<initialWindow>\n" + "\t\t<content></content>\n" + "\t\t<visible>true</visible>\n"
				+ "\t</initialWindow>\n" + "</application>";
		String expected = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n"
				+ "<application xmlns=\"http://ns.adobe.com/air/application/24.0\">\n" + "\t<id>com.example.Main</id>\n"
				+ "\t<versionNumber>0.0.0</versionNumber>\n" + "\t<filename>Main</filename>\n" + "\t<name>Main</name>\n"
				+ "\t<initialWindow>\n" + "\t\t<content>Test.swf</content>\n" + "\t\t<visible>true</visible>\n"
				+ "\t</initialWindow>\n" + "</application>";
		String result = ProjectUtils.populateAdobeAIRDescriptorContent(descriptorContent, "Test.swf");
		Assertions.assertEquals(expected, result,
				"ProjectUtils.populateAdobeAIRDescriptorContent() returned incorrect value.");
	}
}