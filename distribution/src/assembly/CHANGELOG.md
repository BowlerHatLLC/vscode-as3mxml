## 0.11.1

### Fixed Issues

* asconfigc: Fixed issue where Apache Royale builds would have broken encoding so that UTF-8 characters would not render correctly.
* Code Generation: Fixed issue where *Generate Method* and *Generate Field Variable* code actions had no effect in MXML files.
* Completion: Fixed issue where triggering completion without the `<` character in MXML would omit the character.
* Language Server: Fixed issue where changing the framework SDK was not detected, and restarting the language server or reloading the window was required.
* Organize Imports: Fixed issue where unused imports were not removed in MXML files.
* Problems: Fixed issue where problem checking was disabled for a file when a `var` has `[Embed]` metadata (`const` did not have this issue).
* SWF Debugger: Fixed issue where `trace()` calls with multiple consecutive new line characters (`\n\n`) would only add a single new line to the debug console.

## 0.11.0

### New Features

* Build: New experimental **ActionScript: Quick Compile & Debug** command builds projects faster by keeping the compiler in memory. Available using the `Ctrl+Enter` keyboard shortcut. This command may be used with all supported SDKs.

### Fixed Issues

* asconfigc: Added `ios_simulator` as a new platform in `airOptions`.
* Completion: When using completion to add MXML language tags, like `<fx:Script>` or `<fx:Component>`, the cursor is now correctly placed between opening and closing tags instead of at the end.
* Completion: Fixed issue where triggering the completion list in MXML from an existing prefix would incorrectly omit components if the MXML namespace were defined from a package, like `xmlns:example="com.example.*"`.
* Completion: Fixed issue where an exception was displayed when triggering completion for aattributes on an `<fx:Component>` tag.
* Completion: Changed the "kind" for getters/setters and methods so that the proper icon is displayed in the completion list.
* Hover: Fixed issue where hovering over an MXML event attribute would throw a null reference exception if a preceeding tag the file is unclosed.
* Hover: Fixed issue where the definition for the final closing tag in an MXML file could not be found if a preceeding tag in the file is unclosed.
* Hover: Fixed issue where hover details were incorrectly formatted if documentation were also displayed on hover.
* Hover: Fixed issue where an exception was displayed when hovering over the `id` attribute of an `<fx:Component>` tag.
* Organize Imports: Fixed issue where some missing imports were not added.
* Organize Imports: Fixed issue where no changes would be made if all imports can be removed.
* Problems: Fixed issue where warnings were still displayed if the `warnings` compiler option were set to `false`.
* Royale: Fixed issue where an exception were thrown if the `targets` compiler option lists "SWF" before "JSRoyale".
* Tasks: Removed deprecated command "Tasks: Configure Task Runner (ActionScript - asconfig.json)". Use **Configure Default Build Task** in **Tasks** menu instead.
* Tasks: If the Adobe AIR desktop target is set to "native" in *asconfig.json* the list of tasks will specifically mention which tasks will package a native installer.
* Workspace Symbols: Fixed issue where an exception was displayed when listing workspace symbols and a SWC file in the SDK contains resource bundles.

## 0.10.0

### New Features

* Build: *asconfig.json* may optionally include `${flexlib}` token in paths specified in the compiler options. This token will be replaced with the path to the *frameworks* directory of the current SDK. For Apache Royale projects, the `${royalelib}` token is available instead.
* Debugger: When a runtime error is thrown, displays the details inside the editor – directly below the line where the error occurred.
* Debugger: When a runtime error is thrown, the properties of the error object are displayed in the **Variables** view.
* Editor: Goto definition works for symbols in SWC files. Opens a temporary, read-only text file that displays the public API.
* Views: The ActionScript Source Paths view displays file icons.

### Fixed Issues

* Build: Fixed issue where `extdir` option for Adobe AIR packaging was missing from the *asconfig.json* schema.
* Build: The *asconfig.json* file format is validated as *JSON with Comments* by Visual Studio Code.
* Build: Fixed issue where the the bundled version of **asconfigc** failed when paths contained spaces.
* Editor: Fixed issue where compiler configuration errors with no file path were not displayed.
* Editor: Fixed issue where setting styles in MXML in Flex projects could result in incorrect warnings.
* Editor: Fixed issue where files created, deleted, or modified in directories specified with the `source-path` compiler option were not reflected in the code intelligence.
* Editor: Fixed issue where deleting directory did not clear problems for *.as* and *.mxml* files in the directory.
* Settings: Fixed issue where a Maven-built distribution of Apache Royale could not be used with the `nextgenas.sdk.editor` setting.
* Syntax Highlighting: Classes and interfaces are now colored differently than keywords, making ActionScript syntax highlighting more consistent with other languages in Visual Studio Code. Additionally, the package name in fully qualified class names is now colored too.

## 0.9.1

### Fixed Issues

* Apache Royale: Updated to search for npm moddules with new names: *@apache-royale/royale-js* and *@apache-royale/royale-js-swf*.
* Build: Fixed issue where copying files from source paths could fail from nested directories.
* Completion: Fixed issue where activating completion with `Ctrl+Space` after a partial MXML component name could omit some results due to incorrect case-sensitive comparison.

## 0.9.0

### New Features

* Apache Royale is now supported! The code intelligence features of the ActionScript & MXML extension are now powered by the latest Apache Royale compiler. SWF projects targeting Adobe AIR and Flash Player remain fully suported, but Apache FlexJS projects are considered deprecated, and updgrading to Royale is recommended.
* Build: The **asconfigc** utility is now bundled with the ActionScript & MXML extension, and you don't need to install it manually anymore.
* Editor: When overriding a method, the completion list will suggest method names from superclass that can be overridden.
* Settings: added `nextgenas.asconfigc.useBundled` setting to disable the bundled version of **asconfigc** and use the globally installed npm version, if you prefer.
* Workspaces: Tasks and SWF debugging are now supported in multi-root workspaces. Code intelligence features are supported only in the first root folder for now. Work on multi-root workspaces will be completed in a future update.

### Fixed Issues

* Editor: Fixed issue where completion list could sometimes contain duplicate entries for the same class because it was defined in multiple SWC files.
* Editor: Fixed issue where completion sometimes would not work in MXML event handlers if an MXML namespace containing `//` were defined beforehand on the same line.
* Editor: Fixed exception thrown when editing code in a conditional compilation block that is currently disabled.

## 0.8.0

### New Features

* Build: Package Adobe AIR applications using a new `airOptions` field in *asconfig.json*. Supports both desktop and mobile, including both debug and release packages.
* Editor: If an MXML tag is unclosed, a suggestion to close it will appear in the completion list.
* Editor: Now supports completion of state names in MXML.
* Editor: Fixed issue where the first import added to a `<fx:Script>` element in MXML would incorrectly be added at the beginning of the file.
* Command: Added "Restart ActionScript/MXML server" command to restart the language server without reloading the entire window.
* Debugging: If *launch.json* does not exist, the generated initial debug configurations for SWF now includes both `launch` and `attach` requests to better match the behavior of other debug extensions.

### Fixed Issues

* Debugging: Fixed issue where `extdir` option for AIR debugger could not use a relative path.
* Fixed issue where starting with the SDK set to Apache FlexJS and later switching to another SDK could cause an invalid embedded fonts error to be displayed in the Problems view.
* Fixed issue where "Internal error in ABC generator subsystem" error could be displayed in Apache FlexJS projects using CSS.
* ActionScript Source Path view now displays parent directories, if necessary, to make it easier to differentiate between source paths that have the same directory name.

## 0.7.1

### New Features

* Workspace: ActionScript SDK picker will show an open folder dialog when adding new SDKs instead of opening the settings to edit manually.

### Fixed Issues

* Code generation: Fixed issue where generate getter/setter light bulb suggestions did not appear unless the entire variable definition was selected.
* Editor: Fixed issue where automatic MXML namespace insertion would fail sometimes.
* Editor: Values of `true`, `false`, and `null` are now displayed in the completion list with other keywords.
* Fixed issue where language server could automatically detect an incorrect `swf-version` value, if omitted.
* Fixed issue where closing a workspace would leave the language server running with high CPU and memory usage because it wasn't shutting down correctly.
* Fixed issue where changing the "editor" SDK would require a restart. The language server is now restarted automatically.
* Fixed issue where Output panel would display useless "Unsupported notification method: $/setTraceNotification" warning.
* Fixed issue where Output panel would display useless warning about SLFJ library.
* Fixed issue where Output panel would display useless warning about invalid *asconfig.json* file, when the editor also displays the validation errors in context.
* Fixed issue where creating the root MXML file would sometimes fail because the compilation unit could not be found.

## 0.7.0

### New Features

* Editor: New built-in snippets for ActionScript and MXML.
* Editor: Added support for folding arbitrary regions in the editor with `//#region` and `//#endregion` comments.
* Editor: ASDoc documentation is displayed with signature help and parameters, if available.
* Debugging: new "attach" request to connect to Flash Player or AIR that is launched outside of Visual Studio Code. Can be used for Wi-Fi or USB debugging on a mobile device.
* Debugging: Simplified *launch.json* by making the "program" field optional. When omitted, the extension automatically detects the location of the SWF or AIR application descriptor, using the output location in *asconfig.json* or compiler defaults.
* Debugging: Source location is shown on the right side of the debug console when a runtime error is thrown and displayed in the console.

### Fixed Issues

* Java Compatibility: Fixed issue where extension would not work correctly with Java 9 because the version string format changed.
* Completion: Fixed issue where opening completion list in a completely empty MXML file would result in an exception.
* Completion: Fixed issue where the first import added to a file could be incorrectly added before the package block.
* Debugging: Migrated away from deprecated method for providing inital configurations for SWF debugger.
* Debugging: If the "profile" field is missing in *launch.json*, but the "config" field in *asconfig.json* is set to "airmobile", will correctly use the "mobileDevice" profile.
* Debugging: Fixed issue where *http:* and *https:* URLs could not be used in "program" field of *launch.json*.
* Debugging: Improved detection of executable in *Contents/MacOS* when "runtimeExecutable" field in *launch.json* ends at *.app* file extension.
* Editor: Fixed issue where the Organize Imports command added imports above package block if new imports are added and there were no existing imports.
* Syntax: Fixed issue where an escaped forward slash in a regular expression would prematurely finish the expression.
* Tasks: If *asconfig.json* is missing from workspace, the asconfigc task will be offered if an *.as* or *.mxml* is open in the active editor.

## 0.6.0

### New Features

* Code generation: If an unknown method is called, a new code action can generate a new method with that signature.
* Code generation: If an unknown variable is referenced, a new code action can generate a new variable.
* Code generation: New code action will convert a member variable into a getter and setter.
* Completion: now includes language keywords in completion list when in certain contexts.
* Debugging: Some fields in *launch.json* for SWF now provide a helpful completion list.
* Documentation: If asdoc comments are defined in *.as* and *.mxml* source files, they will be displayed in UI. Supported in both completion list and in hover popup.
* Organize imports: In addition to sorting, will now also remove unused imports and add missing imports.
* Rename Symbol: Added support for renaming classes and interfaces.
* Syntax highlighting: CSS in `<mx:Style>` or `<fx:Style>` blocks is now colored.
* UI: On startup, displays initializing message in status bar until extension is ready.

### Fixed Issues

* Completion: Fixed issue where an import could be incorrectly added for a symbol in the same package as the current file.
* Debugging: Fixed issue where Step Over command did not behave correctly with SWF files.
* Error Checking: Fixed issue where `swf-version` and `target-player` compiler options were incorrectly ignored.
* Goto definition: Fixed issue where null pointer exception would be displayed if goto definition were used on an MXML tag that could not be resolved to a class.
* Goto definition: Fixed issue where goto definition did not work with `super()` calls.
* Rename Symbol: fixed issue where renaming a getter did not also rename a setter with the same name (and vice versa).
* Syntax highlighting: fixed issue where metadata like `[Bindable]` was not colored in interface files.
* Syntax highlighting: fixed issue where code in MXML `Script` blocks with `mx` prefix was not colored (it only worked with `fx` prefix).
* Workspace symbol: improved search for class names by making unqualified name more important than package name.

## 0.5.1

* Fixed that hover and signature help did not work inside event added to the root element of an MXML component.
* Fixed issue where asconfig.json validation failed to warn if `files` field is missing for application (this field is still optional for a library).
* Fixed issue where searching by workspace symbol would throw error because some files were not opened in the editor.
* Fixed syntax highlighting for `/**/` being incorrectly detected as an asdoc comment, which caused the coloring to extend beyond the `*/` to the following lines.
* Fixed syntax highlighting of single line comments that start at end of the same line as a class, interface, or function declaration.
* Fixed issue where building the vscode-nextgenas extension from source would fail if Adobe dependencies were not already installed with Maven from another project, and updated the build instructions to use settings-template.xml.

## 0.5.0

* SDK version is listed in status bar and you can click it to open a helpful new SDK picker.
* New `nextgenas.sdk.searchPaths` setting allows you to add more SDKs to the SDK picker.
* New "ActionScript Source Path" view lists all classes/interfaces/components available from the `source-path` compiler option.
* The `nextgenas.sdk.editor` setting is now considered advanced, and most users should not need to use it any longer (even when using SDKs other than Apache FlexJS). Simply set `nextgenas.sdk.framework` (or use the new SDK picker) to choose your current project's SDK.
* Changing the `nextgenas.sdk.framework` setting does not require restarting Visual Studio Code anymore.
* In a FlexJS project, if value of `targets` compiler option does not start with "SWF", completion will give precedence to JS APIs.
* Adding a new line inside an asdoc comment will automatically add a `*` on the next line.
* The "Tasks: Configure Task Runner (ActionScript - asconfig.json)" command is deprecated. Go to Visual Studio Code's new "Tasks" menu and choose "Configure Default Build Task" instead.
* Replaced "NextGen ActionScript" icon with a new "AS3" icon.
* SWF debugger: If a source file is not found when trying to add a breakpoint, tries again later when more scripts are loaded. If breakpoints start out unverified, it's possible that they will be verified later when the SWF goes to a new frame.
* SWF debugger: Fixed issue where breakpoints removed in editor were not actually removed in running SWF and would still stop the debugger.
* SWF debugger: Begins executing SWF earlier because breakpoints can now be verified after startup. This may allow preloaders to render properly now (they skipped immediately to the end before).
* Searching workspace symbols simplified to exclude local variables in methods.
* Fixed issue where automatically adding an import would fail if the ActionScript file contained a license header or other comments before the package keyword.
* Fixed issue where some errors caused by invalid compiler options might not be displayed in the problems view.
* Fixed issue where extension could crash when doing certain things without an open workspace. Will now display a warning if certain actions are attempted without an open workspace.

## 0.4.4

* MXML completion list includes `<fx:Binding>`, `<fx:Component>`, `<fx:Declarations>`, `<fx:Metdata>`, `<fx:Script>`, and `<fx:Style>` tags.
* Added new `args` field to `launch.json` configuration for Adobe AIR debugging. May be used to pass arguments to invoked AIR application.
* Added new compiler options for Apache FlexJS to `asconfig.json` schema, including `html-template`, `html-output-filename`, `js-compiler-option`, `js-external-library-path`, `js-library-path`, `swf-external-library-path`, `swf-library-path`, and `remove-circulars`.
* Fixed issue where the extension could not be used in multiple VSCode windows on Windows 10 because it did not properly detect an open port.
* Fixed issue where package completion on Windows would incorrectly contain backslashes.
* Fixed issue where package completion would not work when file is completely empty and editor is powered by Apache FlexJS 0.8.0 (not fixed with FlexJS 0.7.0, due to bugs in the compiler).
* Fixed issue where values surrounded in quotes in the `additionalOptions` field of `asconfig.json` were not parsed correctly and would lead to misleading errors.
* Fixed issue where completion could be unexpectedly triggered inside a string or a regular expression literal.
* Fixed regression where code intelligence features might stop working (and require restarting VSCode to fix) when the current file contains unclosed MXML constructs, like CDATA or comments, and the editor is powered by Apache FlexJS 0.7.0.

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
* In addition to support for completion from the previous update, ActionScript in MXML event attributes now includes support for signature help, hover, goto definition, find all references, and rename.

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