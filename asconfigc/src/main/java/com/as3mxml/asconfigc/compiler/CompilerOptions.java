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
package com.as3mxml.asconfigc.compiler;

public class CompilerOptions {
	public static final String ACCESSIBLE = "accessible";
	public static final String ADVANCED_TELEMETRY = "advanced-telemetry";
	public static final String BENCHMARK = "benchmark";
	public static final String DEBUG = "debug";
	public static final String DEBUG_PASSWORD = "debug-password";
	public static final String DEFAULT_BACKGROUND_COLOR = "default-background-color";
	public static final String DEFAULT_FRAME_RATE = "default-frame-rate";
	public static final String DEFAULT_SIZE = "default-size";
	public static final String DEFAULTS_CSS_FILES = "defaults-css-files";
	public static final String DEFINE = "define";
	public static final String DIRECTORY = "directory";
	public static final String DUMP_CONFIG = "dump-config";
	public static final String EXTERNAL_LIBRARY_PATH = "external-library-path";
	public static final String INCLUDE_LIBRARIES = "include-libraries";
	public static final String KEEP_ALL_TYPE_SELECTORS = "keep-all-type-selectors";
	public static final String KEEP_AS3_METADATA = "keep-as3-metadata";
	public static final String KEEP_GENERATED_ACTIONSCRIPT = "keep-generated-actionscript";
	public static final String LIBRARY_PATH = "library-path";
	public static final String LINK_REPORT = "link-report";
	public static final String LOAD_CONFIG = "load-config";
	public static final String LOAD_EXTERNS = "load-externs";
	public static final String LOCALE = "locale";
	public static final String NAMESPACE = "namespace";
	public static final String OPTIMIZE = "optimize";
	public static final String OMIT_TRACE_STATEMENTS = "omit-trace-statements";
	public static final String OUTPUT = "output";
	public static final String PRELOADER = "preloader";
	public static final String SHOW_UNUSED_TYPE_SELECTOR_WARNINGS = "show-unused-type-selector-warnings";
	public static final String SIZE_REPORT = "size-report";
	public static final String SOURCE_PATH = "source-path";
	public static final String STATIC_LINK_RUNTIME_SHARED_LIBRARIES = "static-link-runtime-shared-libraries";
	public static final String STRICT = "strict";
	public static final String SWF_VERSION = "swf-version";
	public static final String TARGET_PLAYER = "target-player";
	public static final String THEME = "theme";
	public static final String TOOLS_LOCALE = "tools-locale";
	public static final String USE_DIRECT_BLIT = "use-direct-blit";
	public static final String USE_GPU = "use-gpu";
	public static final String USE_NETWORK = "use-network";
	public static final String USE_RESOURCE_BUNDLE_METADATA = "use-resource-bundle-metadata";
	public static final String VERBOSE_STACKTRACES = "verbose-stacktraces";
	public static final String WARNINGS = "warnings";

	//royale options
	public static final String HTML_OUTPUT_FILENAME = "html-output-filename";
	public static final String HTML_TEMPLATE = "html-template";
	public static final String JS_COMPILER_OPTION = "js-compiler-option";
	public static final String JS_DEFAULT_INITIALIZERS = "js-default-initializers";
	public static final String JS_DEFINE = "js-define";
	public static final String JS_EXTERNAL_LIBRARY_PATH = "js-external-library-path";
	public static final String JS_LIBRARY_PATH = "js-library-path";
	public static final String JS_LOAD_CONFIG = "js-load-config";
	public static final String JS_OUTPUT = "js-output";
	public static final String JS_OUTPUT_TYPE = "js-output-type";
	public static final String REMOVE_CIRCULARS = "remove-circulars";
	public static final String SOURCE_MAP = "source-map";
	public static final String SWF_EXTERNAL_LIBRARY_PATH = "swf-external-library-path";
	public static final String SWF_LIBRARY_PATH = "swf-library-path";
	public static final String TARGETS = "targets";
	public static final String WARN_PUBLIC_VARS = "warn-public-vars";

	//library options
	public static final String INCLUDE_CLASSES = "include-classes";
	public static final String INCLUDE_FILE = "include-file";
	public static final String INCLUDE_NAMESPACES = "include-namespaces";
	public static final String INCLUDE_SOURCES = "include-sources";

	//sub-values
	public static final String DEFAULT_SIZE__WIDTH = "width";
	public static final String DEFAULT_SIZE__HEIGHT = "height";
	public static final String DEFINE__NAME = "name";
	public static final String DEFINE__VALUE = "value";
	public static final String NAMESPACE__URI = "uri";
	public static final String NAMESPACE__MANIFEST = "manifest";
	public static final String INCLUDE_FILE__FILE = "file";
	public static final String INCLUDE_FILE__PATH = "path";
}