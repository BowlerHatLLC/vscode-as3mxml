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
package com.as3mxml.vscode.utils;

import org.apache.royale.compiler.common.PrefixMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MXMLNamespaceUtilsTests {
	// --- getNamespaceFromURI

	@Test
	void testGetNamespaceFromURIWithRoyaleLibrary() {
		String uri = "library://ns.apache.org/royale/example";
		MXMLNamespace result = MXMLNamespaceUtils.getNamespaceFromURI(uri, new PrefixMap());
		Assertions.assertNotNull(result);
		Assertions.assertEquals(uri, result.uri, "MXMLNamespaceUtils.getNamespaceFromURI() returned incorrect uri.");
		Assertions.assertEquals("example", result.prefix,
				"MXMLNamespaceUtils.getNamespaceFromURI() returned incorrect prefix.");
	}

	@Test
	void testGetNamespaceFromURIWithFlexLibrary() {
		String uri = "http://flex.apache.org/example/ns";
		MXMLNamespace result = MXMLNamespaceUtils.getNamespaceFromURI(uri, new PrefixMap());
		Assertions.assertNotNull(result);
		Assertions.assertEquals(uri, result.uri, "MXMLNamespaceUtils.getNamespaceFromURI() returned incorrect uri.");
		Assertions.assertEquals("example", result.prefix,
				"MXMLNamespaceUtils.getNamespaceFromURI() returned incorrect prefix.");
	}

	@Test
	void testGetNamespaceFromURIWithHTTPAndWWW() {
		String uri = "http://www.example.com/2006/mxml";
		MXMLNamespace result = MXMLNamespaceUtils.getNamespaceFromURI(uri, new PrefixMap());
		Assertions.assertNotNull(result);
		Assertions.assertEquals(uri, result.uri, "MXMLNamespaceUtils.getNamespaceFromURI() returned incorrect uri.");
		Assertions.assertEquals("example", result.prefix,
				"MXMLNamespaceUtils.getNamespaceFromURI() returned incorrect prefix.");
	}

	@Test
	void testGetNamespaceFromURIWithHTTPAndNoSubdomain() {
		String uri = "http://example.com/2006/mxml";
		MXMLNamespace result = MXMLNamespaceUtils.getNamespaceFromURI(uri, new PrefixMap());
		Assertions.assertNotNull(result);
		Assertions.assertEquals(uri, result.uri, "MXMLNamespaceUtils.getNamespaceFromURI() returned incorrect uri.");
		Assertions.assertEquals("example", result.prefix,
				"MXMLNamespaceUtils.getNamespaceFromURI() returned incorrect prefix.");
	}

	@Test
	void testGetNamespaceFromURIWithLibraryProtocolAndWWW() {
		String uri = "library://www.example.com/2006/mxml";
		MXMLNamespace result = MXMLNamespaceUtils.getNamespaceFromURI(uri, new PrefixMap());
		Assertions.assertNotNull(result);
		Assertions.assertEquals(uri, result.uri, "MXMLNamespaceUtils.getNamespaceFromURI() returned incorrect uri.");
		Assertions.assertEquals("example", result.prefix,
				"MXMLNamespaceUtils.getNamespaceFromURI() returned incorrect prefix.");
	}

	@Test
	void testGetNamespaceFromURIWithLibraryProtocolAndNoSubdomain() {
		String uri = "library://example.com/2006/mxml";
		MXMLNamespace result = MXMLNamespaceUtils.getNamespaceFromURI(uri, new PrefixMap());
		Assertions.assertNotNull(result);
		Assertions.assertEquals(uri, result.uri, "MXMLNamespaceUtils.getNamespaceFromURI() returned incorrect uri.");
		Assertions.assertEquals("example", result.prefix,
				"MXMLNamespaceUtils.getNamespaceFromURI() returned incorrect prefix.");
	}
}