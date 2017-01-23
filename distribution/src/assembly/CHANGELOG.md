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