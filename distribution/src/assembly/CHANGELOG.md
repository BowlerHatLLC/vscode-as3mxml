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