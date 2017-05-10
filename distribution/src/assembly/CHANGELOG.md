## 0.4.3

* Added "Organize Imports" context menu items for ActionScript and MXML files, and "ActionScript: Organize Imports" appears in the command palette.
* Added support for the the new `targets` compiler option from Apache FlexJS 0.8 in `asconfig.json`.
* The `config` field in `asconfig.json` is now optional. It will default to `"flex"`, the same as when you run the `mxmlc` compiler.
* Fixed issue where functions or variables defined in a package were not automatically imported like classes or interfaces.
* Fixed issue where CRLF line endings were not stored properly in memory, causing the code intelligence to get out of sync with the editor on Windows.
* Fixed issue where Visual Studio code would incorrectly recognize all XML files as MXML.
* Fixed issue where `__AS3__.vec.Vector` would be incorrectly imported when typing a variable as `Vector`.
* Fixed issue where the `Vector` type in MXML was not considered part of the `http://ns.adobe.com/mxml/2009` namespace.
* Fixed issue where triggering completion on the left side of member access failed to provide suggestions.
* Fixed issue where inherited members were not included in completion for an interface.
* Fixed issue where failure to parse `asconfig.json` would result in null reference errors from the language server.

## 0.4.2

* When opening a workspace, immediately checks for errors instead of waiting to open an ActionScript or MXML file.
* ActionScript completion is now supported inside MXML event attributes.
* Added ability to specify `extdir` option in `launch.json` to allow AIR applications to access a directory of unpacked native extensions.
* Search for symbols in workspace is now case insensitive and it may start from anywhere inside the fully qualified name of a symbol (not only from the beginning).
* Fixed issue where the generated `launch.json` might reference the wrong file extension (was .as, but should have been .swf).
* Fixed issue where the completion list was incorrectly empty after activating with Ctrl+click on the left side of member access.
* Fixed issue where a goto definition link would not appear if the mouse was over the first character of an identifier.

## 0.4.1

* Fixed issue where super protected members were sometimes omitted
* Added missing completion for package name at beginning of file.
* Fixed issue where list of document or workspace symbols would sometimes jump to metadata instead of definition name.
* Fixed issue where `trace()` console output would not appear on new lines.
* Fixed issue where signature help was not provided for constructor functions.
* Fixed issue where goto definition did not work on framework classes if framework was built on Windows (caused by different slashes in path).
* Fixed issue where numeric or boolean conditional compilation constants were not parsed correctly.

## 0.4.0

* Debug SWF files in Adobe AIR or Flash Player.
* Goto definition now finds framework classes in the SDK, even if they are compiled into a SWC.
* Fixed issue where misleading errors were displayed for ActionScript and MXML files that are outside the workspace or a `source-path`.
* Fixed issue where a problem was reported for embedded fonts even if the framework SDK supports them.
* Fixed issue where some syntax inside a package block was not colored correctly.
* Fixed issue where checking for errors would fail for certain projects due to exceptions in the compiler.
* Fixed issue where the Adobe AIR SDK & Compiler could not be passed to the `nextgenas.sdk.framework` setting because the compiler set an invalid default for the `-theme` compiler option.
* Improved default launch configuration options for Node.js.

## 0.3.1

* Fixed issue where `protected` members were not omitted in scopes were they should not be accessible.
* Fixed issue where problems reported for compiler configuration options were not cleared after they were resolved.
* Fixed regression that caused the extension to fail when using a Maven distribution of Apache FlexJS.

## 0.3.0

* IntelliSense completion of classes in ActionScript and `<fx:Script>` blocks now automatically adds imports.
* IntelliSense completion of classes in MXML now automatically adds xmlns declarations.
* The `nextgenas.flexjssdk` setting is deprecated and has been renamed to `nextgenas.sdk.editor`.
* The `nextgenas.frameworksdk` setting is deprecated and has been renamed to `nextgenas.sdk.framework`.
* When running the *Tasks: Configure Task Runner (ActionScript - asconfig.json)* command, `tasks.json` is automatically populated with the value of `nextgenas.sdk.framework` or `nextgenas.sdk.editor`.
* The `nextgenas.sdk.editor` setting now supports an Apache FlexJS binary distribution built with Maven (instead of Ant).
* Fixed issue where nightly builds of Apache FlexJS 0.8 could not be used as the editor SDK.
* Fixed issue where problems with compiler options were not reported.
* Fixed issue where code after a package block would be incorrectly colored.
* Fixed issue where `[ExcludeClass]` metadata was incorrectly ignored, causing classes with strange names to show up in completion.
* Fixed issue where file-internal symbols (things after the package block) did not appear in completion.
* Fixed issue where some members from superclass were not included in completion.
* Fixed issue where extension could be built from source only on macOS. It can now be built on Linux and Windows too.

## 0.2.1

* Fixed issue where opening a second workspace with a NextGenAS project would result in an error.
* Fixed issue where completion would incorrectly show generated classes from `[Embed]` metadata.
* Fixed issue where completion of local scope would include duplicate entries for getters and setters.
* Fixed issue where using MXML namespace * for the top-level package would not be recognized by completion.
* Fixed issue where extension would check for errors in XML files that don't contain MXML.
* Fixed issue where auto-closing pairs like [], {}, and () would not work inside MXML `<fx:Script>` elements.
* Fixed issue where toggle comment keyboard shortcuts worked incorrectly inside MXML `<fx:Script>` elements.

## 0.2.0

* Added support for MXML.
* Added `nextgenas.frameworksdk` setting to load a framework inside a different SDK from Apache FlexJS. For instance, this will allow you to use the Feathers SDK or the original Apache Flex SDK.
* Migrated build script to Apache Maven to make it easier for contributors to get started.

## 0.1.1

* Added support for `define` compiler option in `asconfig.json`.
* Added support for `additionalOptions` field in in `asconfig.json` to add new compiler options that aren't yet defined in the `compilerOptions` field.
* Added `nextgenas.java` setting to optionally point directly to a Java executable to override the automatic detection.
* Fixed issue where extension would crash if an old version of Java were discovered before a supported version of Java.
* Fixed issue where a required, but missing, JAR file of Apache FlexJS SDK could result in a cryptic error message.
* Fixed issue where creating a `tasks.json` file for `asconfigc` would fail if the NextGenAS extension weren't already activated.
* Fixed issue where the extension would crash if activated when a folder is not open in VSCode. Now suggests to open a folder to enable all features.
* Fixed issue where some compiler errors would not be displayed in VSCode Problems view.
* Fixed issue where completion could be triggered inside a comment.
* Fixed issue where code intelligence would not work in some method bodies. 
* Fixed minor issues with ActionScript syntax highlighting.

## 0.1.0

* Initial release with support for ActionScript.