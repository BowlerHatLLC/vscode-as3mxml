# ActionScript & MXML for Visual Studio Code Changelog

## 1.14.1

### Other Changes

- Upgraded some dependencies that were preventing a release from being published.

## 1.14.0

### New Features

- Build: Added `aab-debug`, `android-studio`, `android-studio-debug`, and `apk-emulator` as supported targets for Adobe AIR packaging.
- Hover: Display `@param`, `@return`, `@throws`, and `@default` in documentation.
- Hover: Documentation for AS3 constructors now also appends documentation for the class at the end.
- Settings: Added `as3mxml.quickCompile.enabled` property, which may be set to `false` to disable the experimental quick compile and run/debug commands.

### Fixed Issues

- Build: Prevent multiple simultaneous experimental quick compile and run/debug builds. Only one should be active at a time.
- Format: Fixed issue where extra new lines were sometimes incorrectly added at the end of a file.
- General: Thread safety fixes when cleaning up a project and recreating it.
- Hover: Fixed multiline descriptions of `@param` and other ASDoc tags.
- Rename: Fixed rename symbol and find references failing to detect MXML `id` attributes.
- Settings: Fixed incorrectly named `as3mxml.format.enable` setting which should have been `as3mxml.format.enabled`.
- Syntax: Added missing color for `with` keyword.

### Other Changes

- Settings: Removed old deprecated `nextgenas` settings that were replaced with `as3mxml` settings.

## 1.13.0

### New Features

- Completion: In ASDoc comments, tags such as `@param`, `@return`, and `@see` are now suggested.
- Definition: ASDoc `@see`, `@throws` and `@copy` tags referencing symbols (like classes, interfaces, properties, and methods) now work with Ctrl+Click to Go To Definition.
- Hover: ASDoc `@see`, `@throws` and `@copy` tags referencing symbols now display more details about the symbol on mouse hover.
- Settings: (Advanced) Added `actionscript.trace.server` setting to display (for debugging purposes) the messages passed between Visual Studio Code and the ActionScript & MXML language server.

### Fixed Issues

- General: Improved detection of SDK "short names" to display in the status bar. Some long SDK descriptions were not being stripped of less relevant information.
- Problems: Fixed issue where some configuration errors and warnings were not reported in the Problems view, and you could see them only when compiling the project.
- Problems: Fixed issue where non-fatal errors could sometimes block error checking in other files.

## 1.12.1

### Fixed Issues

- Build: Fixed issue where the wrong paths for the Adobe AIR application descriptor and the initial window contents were passed to ADT when packaging an Adobe AIR app.

## 1.12.0

### New Features

- Formatting: ActionScript and MXML code files may be formatted with Visual Studio Code's standard _Format Document_ command. Includes a variety of new settings to configure the code formatting style for these languages.

### Fixed Issues

- Build: Fixed issue where `as3mxml.asconfigc.verboseOutput` was incorrectly ignored when running ActionScript clean task.
- Build: Fixed issue where replacing values in the Adobe AIR application descriptor XML could fail if elements were duplicated and the first one was commented out.
- Build: Fixed issues where the `htmlTemplate` files and the Adobe AIR application descriptor file were incorrectly copied to the SWF output directory instead of the JavaScript output directory when targeting JavaScript with Apache Royale.
- Build: Fixed issue where cleaning a project incorrectly deleted files in the SWF output directory instead of the JavaScript output directories when targeting JavaScript with Apache Royale.
- Build: Added a final fallback of using the project directory name when the Adobe AIR application ID needs to be generated.
- Build: Copies asset files before compiling, instead of after, to create a better developer experience with Apache Royale compiler's new upcoming file watcher feature.
- Documentation: Fixed issue where @ character outside of ASDoc tag was sometimes incorrectly recognized as an ASDoc tag.
- Documentation: Fixed rendering of HTML entities inside text formatted as code.
- Documentation: Improved formatting of tables.
- Documentation: Improved formatting of code blocks on a single line.
- General: Fix Java icon appearing in macOS dock when launching language server.

### Other Changes

- Dependencies: Apache Royale compiler updated to v0.9.9.

## 1.11.1

### Fixed Issues

- Build: Fixed issue where multiple Quick Compile & Debug/Run commands could be run simultaneously. Now, you must wait for one to complete before starting a new one.
- Build: Fixed issue where Adobe AIR packaging tasks were incorrectly provided for _.swc_ library projects.
- Documentation: Fixed failure to remove uppercase HTML tags from rendered text.
- Documentation: Fixed line breaks missing between some block-level elements.
- Documentation: Fixed missing formatting for list items.
- Documentation: Fixed display of HTML entities in code blocks.
- General: Fixed wrong Apache Royale version number in error message about invalid `as3mxml.sdk.editor` values.
- General: The thread to watch source paths is a daemon to avoid it blocking the language server from exiting.

## 1.11.0

### New Features

- Build: Added a number of new compiler options for Apache Royale to the `compilerOptions` section of _asconfig.json_, meaning that many advanced options no longer need to be added to `additionalOptions`.
- Hover: Documentation is now detected in SDKs that store it in resource bundle _.swc_ files separately from the _.swc_ files containing compiled code. More documentation from the Adobe and Apache Flex SDKs should now be shown in hover, completion, and signature help.

### Fixed Issues

- Build: Fixed issue where module and worker _.swf_ files were not automatically included in Adobe AIR bundles.
- Build: Fixed issue where cleaning the project sometimes would not clean module _.swf_ files.
- General: Fixed an issue with detection of changes to _.swc_ library files that caused the workspace to continue using stale APIs.
- Settings: Fixed validation of relative framework SDK paths on Windows.
- Settings: Fixed issue where changing certain settings sometimes failed to restart the language server.
- Hover: Fixed missing asdoc documentation for members of interfaces.
- Hover: Fixed missing asdoc documentation for APIs in the public-like `AS3` namespace.
- Hover: Fixed missing asdoc documentation for `[Style]` and `[Event]` metadata in _.swc_ libraries.
- Hover: Fixed missing asdoc documentation for accessors when the asdoc comment was added to the setter instead of the getter.
- Imports: Fixed issue where a class outside of the package block had an import for the class inside the package block, and it was incorrectly removed when organizing imports.
- Views: Fixed exception in ActionScript Source Paths view when the project has no source paths yet.

## 1.10.0

### New Features

- General: SDK path is no longer required to be absolute. It may be specified relative to the workspace root folder, or relative to the _.code-workspace_ file, for multi-root workspaces.
- Imports: When completing an override of a function, types of parameters and return are now automatically imported, if possible.

### Fixed Issues

- Completion: Fixed intermittent string range exception when completing imports.
- General: Fixed watching of individual _.swc_ files in `library-path` or `external-library-path`, and not only directories of _.swc_ files.

## 1.9.0

### Fixed Issues

- General: Fixed issue that prevented the language server from running with a socket instead of standard input.
- General: The language server automatically exits as soon as the input stream from the parent process closes. This should help prevent zombie processes from staying open after closing Visual Studio Code.

### Other Changes

- Dependencies: Apache Royale compiler updated to v0.9.8.

## 1.8.0

### New Features

- Code Generation: Smarter detection of underscore (`_`) characters at the beginning of the original variable. The undercore is added to the backing variable, if missing. It is removed from the getter and setter, if present.
- Completion: Can now suggest methods to override after only the `override` keyword (previously, you needed to specify both the `override` and `function` keywords and the namespace to get suggestions). Also, lists getters and setters that may be overridden.
- Settings: New setting `as3mxml.sources.organizeImports.addMissingImports` determines whether missing imports are added when the organize imports command is run.
- Settings: New setting `as3mxml.sources.organizeImports.removeUnusedImports` determines whether unused imports are removed when the organize imports command is run.
- Settings: New setting `as3mxml.sources.organizeImports.insertNewLineBetweenTopLevelPackages` determines of an extra new line is added between top-level packages when the organize imports command is run.

### Fixed Issues

- General: Fixed issue where code intelligence could freeze if the same _asconfig.json_ file appeared in multiple workspace root folders.
- Hover: Fixed resolution of symbols when they are fully-qualified with the package name.
- Organize Imports: Fixed issue where imports where sometimes moved to the top of an _.mxml_ file instead of remaining at the top of the `<fx:Script>` section.
- Quick Compile & Run/Debug: Fixed issue where pressing Enter while Ctrl+F searching could fail after running Quick Compile & Run/Debug because the output channel would not release focus.

### Other Changes

- Rename and Find References: Optimized performance by skipping most files in the project when finding/renaming private symbols.
- Dependencies: eclipse/lsp4j language server updated to v0.12.0.

## 1.7.1

### Fixed Issues

- Build: Fixed missing Adobe AIR build tasks.

## 1.7.0

### New Features

- Code Actions: Added new `as3mxml.codeGeneration.getterSetter.forcePublicFunctions` and `as3mxml.codeGeneration.getterSetter.forcePrivateVariable` settings to customize how the generate getter and setter quick fixes behave.

### Fixed Issues

- Build: Fixed issue where captive runtime and native installer builds were debug instead of release.
- Code Actions: Fixed missing "Add import" code actions for classes in _.swc_ libraries.
- Completion: Fixed wrong namespace in Apache Flex projects when adding the root tag with the MX namespace to an _.mxml_ file.
- Hover: Fixed missing mouse hover text for custom user namespaces.
- Syntax: Missing syntax coloring for namespace declarations.

## 1.6.0

### New Features

- Build: Added new `workers` field in _asconfig.json_ to compile worker SWFs with an application project.
- Build: Added support for `resdir` in `airOptions` section of _asconfig.json_ to support HARMAN's latest SDK.
- Code Actions: Suggests imports for package-level variables or functions when a symbol is unrecognized (not just classes and interfaces).
- Import: Workers are now included when importing an Adobe Flash Builder project.

### Fixed Issues

- Build: No longer deletes root output folder when cleaning the project. This change is to more closely match the behavior of other IDEs.
- Import: Fixed issue where an Adobe Flash Builder project for Adobe AIR might be detected as targeting desktop instead of mobile, in some cases.
- Import: Fixed issue where an Adobe Flash Builder project could not be successfully imported if the folder also contained a FlashDevelop project.

### Other changes

- Dependencies: eclipse/lsp4j language server updated to v0.10.0.

## v1.5.0

### New Features

- Build: The `additionalOptions` field in _asconfig.json_ files may optionally be specified as an array of strings, instead of a single string.
- Settings: A new `as3mxml.languageServer.concurrentRequests` setting may be set to `false` to disable spawning a thread for each request to the ActionScript & MXML code intelligence. This may result in a more stable environment, in some cases.
- Views: The ActionScript Source Paths view now displays source paths for all open projects (not just the first).

### Fixed Issues

- General: Fixed issue where source files on case-insensitive file systems might not be associated with the correct project, resulting in limited code intelligence.
- Problems: Fixed issue where some errors were not displayed if certain compiler options were misconfigured.

## v1.4.0

### New Features

- Build: Support for compiling Apache Flex modules.
- Project Import: Modules in Adobe Flash Builder projects are now included.

### Fixed Issues

- Build: Further reduce command line options generated when adding folders to an Adobe AIR application. In some cases, it would make the command too long and the build would fail.
- Build: Add missing action to unpackage Adobe AIR native extensions for debugging when compiling with Adobe Animate.
- Completion: Fix location of added imports in MXML `<fx:Script>` tags when CDATA is missing.

## v1.3.0

### New Features

- Projects: Multiple projects in a single workspace folder. If a workspace folder contains additional _asconfig.json_ files in sub-folders, these will be detected as separate projects. Perfect for projects containing tests and for quickly opening a shared parent folder without manually adding each project as a separate workspace folder.

### Fixed Issues

- Build: Quick compile commands no longer print incorrect "pending" message before initialization when no _asconfig.json_ files are detected.
- General: Fix warning in output console that referenced a _vscode-userdata:_ URI.
- Hover: fix formatting of constructor names to look like `ClassName.com.example.ClassName`.
- Hover: fix missing ASDoc documentation when hovering over symbols in _.mxml_ files.

### Other changes

- General: The ActionScript SDK status bar item is visible only when the active editor contains an _.as_, _.mxml_ or _asconfig.json_ file. This better matches how the TypeScript version status bar item behaves.

## v1.2.2

### Fixed Issues

- Build: Hide Java icon from dock on macOS.
- Code Actions: Fix null exception for errors/warnings that don't have a code.
- Definition: Go to definition works for symbols from _.ane_ files (showing a decompiled stub, similar to _.swc_ files).
- Problems: Fix null exception on file change when real-time problems are disabled.
- Problems: Fix issue where problems might not be cleared for deleted files.
- Tasks: Fix exception when searching for Adobe Animate on Windows, and no Adobe software is installed.
- Tasks: Fix error _The task provider for "actionscript" tasks unexpectedly provided a task of type "animate"._

## v1.2.1

### Fixed Issues

- Settings: Fixed issue where `as3mxml.sdk.animate` setting was not properly handled when using the default value of `null`.

## v1.2.0

### New Features

- Editor: Fades out sections of code that are disabled by conditional compilation.
- Hover: Documentation for native Adobe AIR and Flash Player classes, like `flash.display.Sprite`, is now displayed.
- Hover: Documentation for _.swc_ files that embed the appropriate asdoc XML files is now displayed.
- Hover/Definition: Resolves `this` and `super` to the appropriate class so that some details may be displayed.
- Settings: Added new `as3mxml.languageServer.jvmargs` setting to pass additional arguments to the language server (the code intelligence engine) on startup.
  Example:
  ```json
  {
    "as3mxml.languageServer.jvmargs": "-Xms512m -Xmx1024m"
  }
  ```
- Settings: Added new `as3mxml.sdk.animate` setting to optionally configure a custom path for Adobe Animate if it is installed at a non-standard location.

### Fixed Issues

- Hover: Fixed null pointer exception when hovering over the `void` keyword.

### Other Changes

- Dependencies: Apache Royale compiler updated to v0.9.7.

## v1.1.1

### Fixed Issues

- Problems: Fixed issue where setting both `as3mxml.sdk.framework` and `as3mxml.sdk.editor` to the same SDK would be incorrectly detected as having no SDK configured.

## v1.1.0

### New Features

- Project: Added the `defaults-css-files` compiler option to _asconfig.json_.

### Fixed Issues

- Build: Fixed issue where Adobe AIR files copied into the debug folder did not have the same relative paths as they would when packaging the final application.
- Completion: Fixed issue where completing a property in MXML as a child tag with an existing prefix would incorrectly omit the same prefix from the closing tag.

### Other Changes

- Problems: When the _as3mxml.sdk.framework_ setting is empty, and no SDKs are automatically detected from the environment, the extension no longer displays a pop-up error notification. Instead, the message appears in the problems view when an _.as_ or _.mxml_ file is open, or if an _asconfig.json_ file is present at the root of the workspace.
- Tasks: The extension does not automatically activate when a full list of tasks is requested by VSCode. However, tasks will be provided if the extension is activated in another way, such as when the workspace contains _asconfig.json_ or when an _.as_ or _.mxml_ file is open in an editor.

## v1.0.0

### New Features

- Project: Import projects from FlashDevelop. If you open a folder containing a FlashDevelop project, Visual Studio Code will prompt to convert it to _asconfig.json_.

### Fixed Issues

- Build: Fixed issue where `as3mxml.asconfigc.verboseOutput` setting was ignored when building with Adobe Animate.
- General: Fixed issue where certain compiler options could be incorrectly interpreted as file names, resulting in strange errors.
- General: Fixed issue where an Apache Royale SDK that contains _royale-sdk-description.xml_, but not _flex-sdk-description.xml_, was not considered valid.
- Problems: Fixed issue where wrong path was displayed when `mainClass` was not found.
- Rename: Fixed issue where references were not found inside array `[]` and object `{}` literals.
- Snippets: Fixed issue where for-loop snippet was missing a semi-colon.

### Other Changes

- Build: The `theme` compiler option may now be either a string or an array of strings.
- Build: Added the `include-libraries` compiler option.
- Rename: Optimized renaming a local variable or function inside a method, which previously searched for references in multiple files.

## v0.25.0

### New Features

- Build: Added `aab` as a value for the Adobe AIR `arch` option for Android to support Harman's latest SDK.
- Build: Added `mainClass` field to _asconfig.json_ to optionally use instead of `files`.
  Example:

  ```json
  {
    "compilerOptions": {
      "source-path": ["src"]
    },
    "mainClass": "com.example.Main"
  }
  ```

- Build: Added `extends` field to _asconfig.json_ that may be used to reference another _asconfig_ file to use as a base template while overriding some values.
  For example, you could override the `mainClass` field for a custom build:

  ```json
  {
    "extends": "asconfig.base.json",
    "mainClass": "com.example.MainOverride"
  }
  ```

### Fixed Issues

- Build: Fixed issue where a file not found error could be reported when using `-arch` option when packaging an Adobe AIR application.
- General: Fixed null reference exception in _.mxml_ file when root tag is not found.
- General: Fixed incorrect code intelligence near beginning or end of _.as_ files included with `<fx:Script>` in MXML.
- Syntax: Fixed syntax highlighting issues in MXML for `<fx:Metadata>` and `<fx:Script>` tags that start and end on the same line.
- Tasks: When no target is specified for an Adobe AIR desktop app, tasks list now includes both _captive runtime_ and _shared runtime_ packaging tasks. Previously, it was necessary to specify the `bundle` target.

### Other Changes

- Language Server: Specifying the socket port to use for protocol communication requires the `-Das3mxml.server.port` command line option. Other editors besides Visual Studio Code may need to update their launch command. However, using stdio instead of sockets is recommended.

## v0.24.1

### Fixed Issues

- Dependencies: Fixed dependency on vscode-swf-debug extension that had a typo.

## v0.24.0

### New Features

- Build: Added `x86_64` as a value for the Adobe AIR `arch` option for Android to support Harman's latest SDK.

### Fixed Issues

- Completion: Fixed issue where completion could sometimes be incorrectly disabled because the current code offset was incorrectly detected as being inside a multi-line comment.

### Other Changes

- Debugging: Moved SWF debugger into separate extension, [vscode-swf-debug](https://marketplace.visualstudio.com/items?itemName=bowlerhatllc.vscode-swf-debug), and made that a dependency of this extension.

## v0.23.2

### Fixed Issues

- Dependencies: Fixed issue that language server was built with nightly version of the Apache Royale compiler instead of v0.9.6. The nightly version had a bug that caused code intelligence to freeze.

## v0.23.1

### Fixed Issues

- Problems: Fixed issue where an error containing a Java stack trace was sometimes displayed after a _.swc_ file was changed on the file system.
- Problems: Fixed issue where an error containing a Java stack trace was displayed when the `output` compiler option was missing in a library project.
- Problems: Fixed issue where no errors were displayed if certain required compiler options were missing.

## v0.23.0

### New Features

- Build: Added `armv8` as a value for the Adobe AIR `arch` option for Android to support Harman's latest SDK.
- Editor: Improved performance of real-time problem checking, reducing sluggishness in certain files.
- Editor: Fades out unused imports, and new code actions may be used to remove them.
- Editor: Strikes out references to deprecated APIs.
- Problems: Hides compiler errors and warnings for files that are not referenced by the project, unless they are opened in an editor.
  > This change makes Visual Studio Code behave more like other IDEs, such as Adobe Flash Builder, when displaying problems for ActionScript and MXML.

### Fixed Issues

- Build: Fixed issue where the "quick compile" commands could be triggered without an _asconfig.json_ file
- Completion: Fixed issue where functions and variables at a package-level were not included in import completion.
- Editor: Fixed issue where some _asconfig_ files were not validated. Now, if the file name contains a middle token, like _asconfig.production.json_, it will be validated.
- Problems: Fixed issue where warnings were reported without their associated numeric code.
- Syntax: Fixed syntax highlighting issues for function parameter types and return types.

### Other Changes

- Dependencies: Apache Royale compiler updated to v0.9.6.
- Dependencies: eclipse/lsp4j updated to v0.8.0.
- Snippets: Removed snippets for `return` statements, to match Microsoft's removal of similar snippets for JavaScript and TypeScript.

## v0.22.0

### New Features

- Build: The `application` field in _asconfig.json_ can now (optionally) accept an object that references multiple platforms so that each platform may use a different file.
  Example:
  ```json
  {
    "application": {
      "ios": "src/ExampleIOS-app.xml",
      "android": "src/ExampleAndroid-app.xml"
    }
  }
  ```
  You may continue to specify a single Adobe AIR application descriptor for all platforms — just like in previous versions:
  ```json
  {
    "application": "src/Example-app.xml"
  }
  ```
- Settings: Added `as3mxml.asconfigc.jvmargs` setting to pass additional arguments to the Java Virtual Machine when running the compiler.
  Example:
  ```json
  {
    "as3mxml.asconfigc.jvmargs": "-Xms512m -Xmx1024m"
  }
  ```
  This setting may be used to increase performance and fix out of memory errors when compiling, [similar to Adobe Flash Builder](https://helpx.adobe.com/flash-builder/kb/sluggish-performance-out-memory-errors.html).
- Settings: Added `as3mxml.asconfigc.verboseOutput` setting to display more detailed output when running a build task. Verbose output includes the full set of options passed to any tools that are run during the build (including the compiler and the Adobe AIR packager).

### Fixed Issues

- Build: Fixed issue where `-sampler`, `-embedBitcode` and `-hideAneLibSymbols` were incorrectly formatted when packaging an Adobe AIR application for iOS.
- Build: Simplified the ADT command when packaging folders into an Adobe AIR application.
- Build: Fixed issue where folders to be packaged in an Adobe AIR app could not be found when building an asconfig.json file that is not in the workspace root (such as when the file is in a sub-folder).
- Build: Fixed issue where the name of the generated Adobe AIR application descriptor was wrong when using a template from the SDK.
- Debug: Fixed issue where SWF workers were not allowed to start in the debugger.
- Tasks: Fixed issue where Adobe AIR packaging tasks were missing if the `airOptions` section were missing in _asconfig.json_, but other fields could be used to detect the Adobe AIR requirement.
- Code Actions: Fixed null reference exception when MXML cannot be parsed.
- Completion: Fixed issue where `//` in a URL namespace was incorrectly detected as a comment when appearing before an MXML attribute.
- Completion: Fixed issue where completing an MXML attribute does not automatically add `=""` if the next character is already `=`.

### Other Changes

- Documentation: Added a page that explains [how to enable ActionScript and MXML code intelligence in Sublime Text](https://github.com/BowlerHatLLC/vscode-as3mxml/wiki/How-to-use-the-ActionScript-and-MXML-language-server-with-Sublime-Text) by using the language server from this Visual Studio Code extension.

## v0.21.0

### New Features

- Editor: Enabled partial code intelligence for open files that come from outside of the workspace's source path. This includes SDK framework classes, and _.as_ and _.mxml_ files that are opened when no workspace folder is open in Visual Studio Code.
- Settings: Added `as3mxml.problems.showFileOutsideSourcePath` setting to disable the informational message that is displayed when an open _.as_ or _.mxml_ file is not in the workspace's source path.

### Fixed Issues

- Code Actions: Fixed issue where a null reference exception could be thrown if the line number and indent could not be discovered.
- Completion: Fixed issue where completing a method incorrectly added `()` when the next character in the file is already `(`.
- Debugger: Fixed issue where port forwarding for connected devices was not cleaned up if the connection to the debugger times out.
- Syntax: Fixed issue where comments inside function signature parameters were not colored correctly.
- Syntax: Fixed issue where parameter types containing numbers were not colored correctly.

### Other Changes

- Build: Activating a "quick compile" command before the language server initializes now queues it up for later, instead of displaying an error message. After initialization, the queued compile command will be re-attempted.
- Language Server: Consolidated the `TextDocumentService` and `WorkspaceService` implementations into a single `ActionScriptServices` class. Custom language servers that extend the old `ActionScriptTextDocumentService` or `ActionScriptWorkspaceService` will need to extend the new class instead.

## v0.20.0

### New Features

- Code Action: Added ability to generate an event listener from an `addEventListener()` call.
- Completion: When completing a method name, automatically adds parentheses, moves the cursor between them, and activates signature help.
- SWF Debugger: Supports breakpoints in _.hx_ files.
- SWF Debugger: Classes and other global objects may be added to the **Watch** panel to see static variables and constants.

### Fixed Issues

- Build: Fixed slowdown in v0.19 caused by fixes for `copySourcePathAssets` and read-only files.
- Build: Fixed issues parsing `additionalOptions` in _asconfig.json_ and consolidated parsing code so that it is consistent between builds and code intelligence.
- Build: Added missing `keep-all-type-selectors` and `show-unused-type-selectors` to `compilerOptions`.
- Editor: Fixed issue where contents of `<fx:Style>` and `<mx:Style>` were not treated as CSS for some actions, such as toggling comments.
- General: Fixed issue where code intelligence for items in a `Vector` was not available when accessed by index. For example, the `alpha` property in `vec[0].alpha` is now properly resolved by code intelligence.
- Rename: Fixed issue where rename symbol failed.
- Signature Help: Fixed issue where signature help was not available in MXML data binding and events.
- SWF Debugger: Fixed issue where static variables and constants were displayed on an instance when paused at a breakpoint. To see static variables now, add the class to the **Watch** panel.

### Other Changes

- The minimum supported version of Visual Studio Code is now 1.34.0.
- eclipse/lsp4j dependency updated to v0.7.1.
- Refactoring: Major refactor to make the AS3 & MXML language server easier to maintain and more approachable to new contributors. Extracted most features out into their own separate classes.
- Changed Visual Studio Code's language identifier for ActionScript from `"nextgenas"` to `"actionscript"`. Most people will not be affected by this change, but if your _settings.json_ contains a `"[nextgenas]"` section, you will need to change that to `"[actionscript]"` instead.

### Tips & Tricks

- To generate an event listener, call `addEventListener()` with the name of a method that doesn't exist yet:
  ```actionscript
  target.addEventListener(Event.CHANGE, changeHandler);
  ```
  Place the cursor inside the method name, and click the light bulb 💡 icon that appears (or use the `Ctrl+.` keyboard shortcut). Choose _Generate Event Listener_ from the drop down.
- In the SWF debugger, to see the static variables and constants defined on a class, you can add the fully-qualified class name to the **Watch** panel. If the class is in a package, be sure to separate the package and class with the `::` operator. For example, you might add `flash.events::Event` to see all of the static constants defined on this class.

## v0.19.0

### New Features

- Tasks: New tasks to compile and publish with Adobe Animate.
- Debug: If a _.fla_ file is defined in _asconfig.json_, the `Ctrl+Enter` or `Ctrl+Shift+Enter` keyboard shortcuts will launch Adobe Animate and run either **Test Movie** or **Debug Movie**.
- Code Actions: Added **Implement interface** code action to implement missing methods.
- Go to Definition: Opens external files referenced by `<fx:Script source="file.as"/>`.
- Go to Definition: Opens external files referenced by `<fx:Style source="file.css"/>`.
- Editor: Syntax in _.jsfl_ files is now highlighted as JavaScript.

### Fixed Issues

- Problems: Fixed issue where deleting _asconfig.json_ would not clear existing configuration errors.
- Editor: Fixed issue where Java exceptions appeared in the console when the file path for a `<fx:Script source="file.as">` tag could not be found.
- Project: Fixed issue where importing a Flash Builder project did not resolve tokens for custom linked resources in the Eclipse workspace.
- Build: Fixed issue where `copySourcePathAssets` could fail when a file is read-only.
- Build: Fixed issue where `copySourcePathAssets` sometimes copied the same file to the output folder more than once.
- Build: Fixed compiler failure in large projects by increasing the max heap size for the JVM.

### Other Changes

- Quick Compile & Debug: The `Ctrl+Enter` keyboard shortcut now starts without debugging, and `Ctrl+Shift+Enter` should be used instead to debug. This change is to make the behavior more consistent with the new Adobe Animate integration.

## v0.18.0

### New Features

- Project: Import projects from Adobe Flash Builder. If you open a folder containing a Flash Builder project, Visual Studio Code will prompt to convert it to _asconfig.json_.
- Go To Definition: When a definition is in a SWC file, the generated/decompiled text that is opened in an editor now includes more details, including metadata and some constant values. Additionally, members of classes are now sorted, and custom namespaces are formatted better.
- Hover: Constants are now shown with their values, if the value is primitive, like a `String`, `Boolean`, or `Number`.
- Quick Compile & Debug: All files are saved before compiling, matching the default behavior in Visual Studio Code when running a build task.
- Build: Added support for `directory`, `load-externs` and `include-file` compiler options to `compilerOptions` field in _asconfig.json_.
- General: Added `as3mxml.problems.realTime` setting. When set to `false`, problems will be reported on save only.
- Build: The `application` field in _asconfig.json_ is now optional. If the SDK contains a template for an Adobe AIR application descriptor, this file will be automatically copied to the output folder and populated with some simple defaults.

### Fixed Issues

- Build: Fixed issue where clean command resulted in a null reference exception if main file was in the root folder of the workspace.
- Completion: Fixed missing qualified name in package-level functions that made it difficult to differentiate between functions with the same name.
- SWF Debugger: Fixed issue where the call stack might link to a file that doesn't exist in the current SDK.
- General: Fixed some concurrency issues that would sometimes require a restart of the language server.
- General: Fixed some issues related to the ActionScript `include` statement and MXML `<fx:Script source="script.as"/>`.
- Hover: Fixed issue where hover pop-up for `void` and `Object` types incorrectly displayed the `extends` keyword with no superclass.
- Problems: Fixed null reference exception if a compiler problem had no offset position.
- Problems: Fixed issue where problems were not updated properly when an open file that wasn't originally on the `source-path` gets added to the `source-path` later.

### Other Changes

- The `$nextgenas_nomatch` problem matcher in _tasks.json_ is now considered deprecated. Use an empty array instead:
  ```
  "problemMatchers": []
  ```
- General: JavaScript code in the extension is now bundled/minified with Webpack to speed up start-up time and reduce download size.

## v0.17.2

### Fixed Issues

- Build: Fixed issue where multiple values in the `include-classes` compiler option were not passed to the compiler correctly.
- Build: Fixed issue where formatting perfectly valid options in `additionalOptions` in a certain way could cause compiles to fail.
- Completion: Fixed issue where ActionScript imports added in MXML were added to the beginning of the file when it contains no `<fx:Script>` element yet.
- General: Fixed issue where code intelligence might hang with if certain invalid compiler options are specified. On startup, the issue caused the "Initializing ActionScript & MXML language server..." message in the status bar to get stuck.

## v0.17.1

### Fixed Issues

- Completion: Fixed issue where completion _still_ did not work in an empty MXML file when using Flex as the workspace SDK.

## v0.17.0

### New Features

- Debug: A _launch.json_ file is no longer required to launch the SWF debugger. If _launch.json_ is missing, and you start a debugging session, you can choose _SWF_, and the extension will choose reasonable defaults based on your project's _asconfig.json_.
- Debug: ANEs are detected in _asconfig.json_ and automatically unpackaged for debugging in the Adobe AIR simulator. It is no longer necessary to unpackage ANEs manually.
- Debug: The `extdir` and `profile` fields for SWF debugging in _launch.json_ are populated automatically based on your project's _asconfig.json_.
- Completion: MXML completion now provides enumeration values for properties with `[Inspectable]` metadata and styles.

### Fixed Issues

- Build: Fixed the clean task when used in an Apache Royale project with the default output folder.
- Build: Fixed the formatting for the `namespace` compiler option because it was failing in some situations.
- Build: Fixed issue where some files could not be copied to the output folder when creating a debug build for the Adobe AIR simulator.
- Completion: Fixed issue where completion did not work in an empty MXML file. Now provides all components and will insert appropriate MXML namespaces.
- Completion: Fixed issue where the 2006 MXML namespace might be incorrectly chosen for certain components when the 2009 MXML namespace was already included in the file.
- Completion: Fixed issue where MXML completion would not provide types for children of Flex MX containers.
- General: Fixed issue where the `include-namespaces` compiler option was not validated correctly in _asconfig.json_.

### Other Changes

- Build: Added missing `js-load-config` and `js-define` compiler options.
- Debug: Migrated from Visual Studio Code's deprecated `adapterExecutableCommand` to `DebugAdapterDescriptorFactory`.
- Advanced: SDKs may include a file named _ide/vscode-as3mxml/vscode-as3mxml-config.xml_ to configure the extension with their own default compiler options.
- Commands: Added new `as3mxml.saveSessionPassword` command that may be used when packaging an Adobe AIR Application. See _Tips & Tricks_ below for an example.

### Tips & Tricks

- You can now save your Adobe AIR code signing password for the current session. A new `as3mxml.saveSessionPassword` command may be called from a custom task:
  ```json
  {
    "label": "package Adobe AIR app (Android)",
    "type": "shell",
    "command": "asconfigc",
    "args": [
      "--sdk=${config:as3mxml.sdk.framework}",
      "--debug=false",
      "--air=android",
      "--storepass=${command:as3mxml.saveSessionPassword}"
    ],
    "problemMatcher": []
  }
  ```
  You must install the command line version of [**asconfigc**](https://www.npmjs.com/package/asconfigc) to use it in a custom task.

## v0.16.0

### New Features

- Code Actions: New code action to generate `catch` block of try/catch, if it is missing.
- Debugger: Added support for **Watch expressions** in SWF projects.
- Snippets: Added MXML snippet for Jewel components in Apache Royale.
- Tasks: Added new **Clean** task that cleans the output folder.

### Fixed Issues

- Build: Updated `target-player` compiler option format in _asconfig.json_ to support both **major.minor** and **major.minor.revision**.
- Completion: Fixed issue where missing name values in `[Event]` or `[Style]` metadata caused null reference exception.
- Completion: Fixed issue where completion was not available inside MXML data binding curly braces.
- Completion: Added missing `localId` attribute in MXML for Apache Royale projects.
- Rename: Fixed issue where old deleted file would remain open after renaming a class or interface.
- Settings: Fixed issue where changes to `as3mxml.java.path` and `as3mxml.sdk.editor` settings were not always detected.
- Hover, Goto Definition: Fixed issue where class reference in `new` function call resolved to the clas instead of its constructor.

### Other Changes

- Code Actions: Commands to generate getters and setters now available in **Refactor...** context menu item. Also triggered by the **Ctrl+Shift+R** keyboard shortcut when cursor is on a member variable.
- Code Actions: Organize Imports now available in **Source Action...** context menu item. Also triggered by the **Shift+Alt+O** keyboard shortcut.
- eclipse/lsp4j updated to v0.5.0.
- Apache Royale compiler for code intelligence updated to v0.9.4.

### Tips & Tricks

- To organize imports every time that a file is saved, enable the following setting in Visual Studio Code:

  "editor.codeActionsOnSave": {
  "source.organizeImports": true
  }

## v0.15.0

### New Features

- Build: Supports new `htmlTemplate` field in _asconfig.json_ that will copy template to output folder and replace tokens, similar to Flash Builder.
- Debugging: SWF debugger automatically sets up port forwarding when debugging Adobe AIR apps on Android and iOS mobile devices connected over USB.
- Debugging: SWFs can be launched with **Start Without Debugging** to simply run without connecting to the debugger.

### Fixed Issues

- Code Actions: Fixed issue where **Generate Method** and **Generate Field Variable** code actions did not work for unrecognized symbols in MXML event attributes.
- Completion: Fixed issue where an import automatically added to an MXML script block was incorrectly added at top of file if no other imports exist yet.
- Debugging: Fixed issue where some breakpoints worked correctly, but appeared as unverified in the user interface.
- Debugging: Fixed issue where logpoints could not be added to multiple files at the same time.
- Organize Imports: Fixed issue where organize imports command removed imports for constants used in MXML binding after the `<fx:Script>` element.
- Quick Compile & Debug: Fixed issue where code intelligence was temporarily unavailable until compilation completed.

### Other Changes

- Build: The task identifiers for compiling ActionScript or packaging Adobe AIR apps have been simplified a bit to make them easier to use as a `preLaunchTask` in _launch.json_.
- Code Actions: The code action for generating a getter and setter for a variable is now named "Generate 'get' and 'set' accessors" to better match other languages.
- Views: The **ActionScript Source Path** view displays only when _.as_ or _.mxml_ files are open to ensure that the VSCode extension does not initialize for workspaces that use other languages.

## v0.14.1

### Fixed Issues

- Completion: fixed issue where Apache Flex MX components were given the `adobe` namespace in MXML intead of `mx`.
- Quick Compile & Debug: Fixed issue where debugger incorrectly launched when compiler reported errors.

## v0.14.0

### New Features

- Multi-Threading: Improved performance by handling requests from VSCode in multiple threads.
- Outline: Symbols in the Outline view are now displayed as a tree. For example, you'll now see properties and methods of a class as its children.
- Quick Compile & Debug: If another debug session is currently active, it is now stopped automatically before starting a new one.
- Settings: Replaced prefix `nextgenas` with `as3mxml`. For example, `nextgenas.sdk.framework` is now `as3mxml.sdk.framework`. You don't need to do anything to migrate. The extension will detect existing settings and automatically convert them when you open a workspace.
- Tasks: Provides additional build tasks for workspaces that contain multiple _asconfig_ JSON files. For example, you could now create the standard _asconfig.json_ for development, with a separate _asconfig.prod.json_ for production builds.

### Fixed Issues

- Build: Fixed issue where files that should be included when packaging an Adobe AIR app were not available when debugging in the simulator.
- Code Intelligence: Fixed issue where adding, removing, or changing _.swc_ files in the workspace would not update code intelligence without restaring VSCode.
- Completion: Fixed issue where the list of states in MXML didn't include state groups.
- Library projects: In addition to `source-path`, now also checks for `include-sources` to determine if a file should support code intelligence when opened in an editor.
- Multi-Root Workspaces: Fixed stability issues in workspaces with multiple root folders.
- Multi-Root Workspaces: Fixed issue where adding a new root folder to a workspace would not activate code intelligence until VSCode is restarted.
- Royale: Fixed issue where the order of items in the `targets` compiler option did not affect code intelligence anymore.
- Syntax Highlighting: Fixed a number of issues that should make ActionScript more consistent with other languages.

### Other Changes

- eclipse/lsp4j updated to v0.5.0.

## v0.13.0

### New Features

- Workspaces: Code intelligence is now supported in [workspaces with multiple root folders](https://code.visualstudio.com/docs/editor/multi-root-workspaces). Open multiple projects in the same window!
- Go to Type Definition: Navigate to the definition of a variable's type. Similar to Go to Definition, which jumps to the definition of the variable itself.
- Go to Implementation: See a list of all implementations of an ActionScript interface.
- Workspace Symbol: Added support for camel-case abbreviations in workspace symbol search. For example, searching for `DiObCo` will find `DisplayObjectContainer`.
- Workspace Symbol: Added classes and interfaces defined in _.swc_ files to search results.
- Extension API: added `framworkSDKPath` getter for third-party extensions to use.
- Extension API: added `isLanguageClientReady` getter for third-party extensions to use.

### Fixed Issues

- asconfig.json: Fixed issue where compiler tokens like `{locale}` in the source path were not properly parsed.
- Build: Fixed issue where the _Quick Compile & Debug_ command would continue to use the old SDK after switching to another.
- Build: If the output path of an Adobe AIR application is not defined, the application file name will be based on the SWF file name.
- Build: Fixed issue where an empty `content` field in an Adobe AIR application descriptor would not get automatically populated.
- Hover: Fixed issues where a type annotation could be displayed as `:` instead of `:*` when untyped.
- Problems: Fixed issue where compiler options that affected compiler errors and warnings were ignored. Now problem list better matches the compiler's console output.
- SWF Debugger: Fixed issue where Adobe AIR application descriptor could not be detected if the name did not end with _-app.xml_. Now simply needs an _.xml_ file extension.
- Syntax: Fixed issue where MXML script tags with attributes like `fb:purpose="styling"` would cause embedded ActionScript to not have syntax highlighting.
- Syntax: Fixed issue where some classes with numbers in name were not colored correctly.
- Syntax: Fixed issue where MXML elements after `<fx:Script source="path/to/file.as"/>` were not colored correctly.
- Syntax: Fixed issue where the contents of `<fx:Metadata>` was not colored correctly.
- Fixed issue where extension kept some files open after reading their contents, which could prevent them from being modified.
- Fixed issues handling changes made by other programs to files and folders in the current workspace.
- Fixed issue will a null pointer exception could be thrown when searching for a symbol's source file path.
- Fixed issue where a _Generate Method_ code action was incorrectly created for a constructor called with the `new` keyword.
- Fixed issue where the `-inline` compiler option caused errors in code intelligence. This compiler option is now used during builds only.
- Fixed issue where initializing message in status bar was not cleared if initialization of extension failed.

## v0.12.0

### New Features

- Completion: In MXML, xmlns prefix is displayed before component names to help differentiate between different libraries that might have components with the same name. For example, instead of seeing `Button` in the list twice when using Apache Flex, you'll now see `s:Button` and `mx:Button`.
- Completion: When completing types in MXML for child elements, types that are incompatible with the current property are filtered out.
- Completion: In MXML, when defining a property, style, or event listener as a child XML element, now automatically adds the closing tag and places the cursor in between the opening and closing tags.
- Completion: Improved the automatic xmlns prefix detection for MXML components so that it is now based on the namespace URI or the package name to avoid prefixes like `ns1`, `ns2`, etc. (except as a final fallback).
- Completion: Added `id`, `includeIn`, and `excludeFrom` keywords in MXML.
- SWF Debugger: Added support for [_logpoints_](https://code.visualstudio.com/docs/editor/debugging#_logpoints), a special kind of breakpoint that writes to the console and continues without breaking.
- SWF Debugger: Added ability to automatically install an Adobe AIR application on a device before connecting to the debugger. In _launch.json_, you may now specify a new, optional `platform` field on an `attach` request. Valid values for `platform` are **"ios"** and **"android"**.
- Quick Compile & Debug: Displays activity indicator in status bar during compilation.
- asconfig.json: Added `js-default-initializers`, `js-output`, `warn-public-vars`, and `theme` as valid compiler options.

### Fixed Issues

- asconfig.json: Stricter validation so that unknown compiler options are properly flagged as invalid.
- asconfig.json: Fixed issue where project was not immediately updated if the configuration file has errors, but then gets modified and becomes valid.
- Completion: Improved performance in larger files by moving error checking into a separate thread.
- Completion: Adding a package block to an empty file now places the cursor in between the curly braces.
- Completion: Fixed the classification of events so that they have the correct icon in the completion list.
- Completion: Fixed issue where styles defined in superclasses were incorrectly omitted in MXML.
- Hover: Fixed issue where hover details for a variable without a type annotation would incorrectly display `:` when it should be `:*` instead.
- Organize Imports: Fixed issue where the organize imports command would not work from the File Explorer, if the file were not already open in an editor.
- Problem Checking: Fixed issue where "duplicate definition" errors could be displayed when copying/creating files because the workspace got out of sync.
- Goto Definition: Fixed null reference exception when MXML file does not contain any xmlns references in the root tag.
- Goto Definition: Fixed null reference exception when attempting to goto an event definition in a SWC file.
- Language Server: Fixed issue where language server process did not properly exit on macOS when requested by Visual Studio Code.
- SWF Debugger: Fixed issue where source code for classes in the SDK could not be loaded from the call stack on Windows when stopped at a breakpoint during debugging.
- Usability: Improved informational message displayed when an _.as_ or _.mxml_ file is outside of the source path and code intelligence is disabled.

### Other Changes

- eclipse/lsp4j updated to v0.4.0.

## 0.11.1

### Fixed Issues

- asconfigc: Fixed issue where Apache Royale builds would have broken encoding so that UTF-8 characters would not render correctly.
- Code Generation: Fixed issue where _Generate Method_ and _Generate Field Variable_ code actions had no effect in MXML files.
- Completion: Fixed issue where triggering completion without the `<` character in MXML would omit the character.
- Language Server: Fixed issue where changing the framework SDK was not detected, and restarting the language server or reloading the window was required.
- Organize Imports: Fixed issue where unused imports were not removed in MXML files.
- Problems: Fixed issue where problem checking was disabled for a file when a `var` has `[Embed]` metadata (`const` did not have this issue).
- SWF Debugger: Fixed issue where `trace()` calls with multiple consecutive new line characters (`\n\n`) would only add a single new line to the debug console.

## 0.11.0

### New Features

- Build: New experimental **ActionScript: Quick Compile & Debug** command builds projects faster by keeping the compiler in memory. Available using the `Ctrl+Enter` keyboard shortcut. This command may be used with all supported SDKs.

### Fixed Issues

- asconfigc: Added `ios_simulator` as a new platform in `airOptions`.
- Completion: When using completion to add MXML language tags, like `<fx:Script>` or `<fx:Component>`, the cursor is now correctly placed between opening and closing tags instead of at the end.
- Completion: Fixed issue where triggering the completion list in MXML from an existing prefix would incorrectly omit components if the MXML namespace were defined from a package, like `xmlns:example="com.example.*"`.
- Completion: Fixed issue where an exception was displayed when triggering completion for aattributes on an `<fx:Component>` tag.
- Completion: Changed the "kind" for getters/setters and methods so that the proper icon is displayed in the completion list.
- Hover: Fixed issue where hovering over an MXML event attribute would throw a null reference exception if a preceeding tag the file is unclosed.
- Hover: Fixed issue where the definition for the final closing tag in an MXML file could not be found if a preceeding tag in the file is unclosed.
- Hover: Fixed issue where hover details were incorrectly formatted if documentation were also displayed on hover.
- Hover: Fixed issue where an exception was displayed when hovering over the `id` attribute of an `<fx:Component>` tag.
- Organize Imports: Fixed issue where some missing imports were not added.
- Organize Imports: Fixed issue where no changes would be made if all imports can be removed.
- Problems: Fixed issue where warnings were still displayed if the `warnings` compiler option were set to `false`.
- Royale: Fixed issue where an exception were thrown if the `targets` compiler option lists "SWF" before "JSRoyale".
- Tasks: Removed deprecated command "Tasks: Configure Task Runner (ActionScript - asconfig.json)". Use **Configure Default Build Task** in **Tasks** menu instead.
- Tasks: If the Adobe AIR desktop target is set to "native" in _asconfig.json_ the list of tasks will specifically mention which tasks will package a native installer.
- Workspace Symbols: Fixed issue where an exception was displayed when listing workspace symbols and a SWC file in the SDK contains resource bundles.

## 0.10.0

### New Features

- Build: _asconfig.json_ may optionally include `${flexlib}` token in paths specified in the compiler options. This token will be replaced with the path to the _frameworks_ directory of the current SDK. For Apache Royale projects, the `${royalelib}` token is available instead.
- Debugger: When a runtime error is thrown, displays the details inside the editor – directly below the line where the error occurred.
- Debugger: When a runtime error is thrown, the properties of the error object are displayed in the **Variables** view.
- Editor: Goto definition works for symbols in SWC files. Opens a temporary, read-only text file that displays the public API.
- Views: The ActionScript Source Paths view displays file icons.

### Fixed Issues

- Build: Fixed issue where `extdir` option for Adobe AIR packaging was missing from the _asconfig.json_ schema.
- Build: The _asconfig.json_ file format is validated as _JSON with Comments_ by Visual Studio Code.
- Build: Fixed issue where the the bundled version of **asconfigc** failed when paths contained spaces.
- Editor: Fixed issue where compiler configuration errors with no file path were not displayed.
- Editor: Fixed issue where setting styles in MXML in Flex projects could result in incorrect warnings.
- Editor: Fixed issue where files created, deleted, or modified in directories specified with the `source-path` compiler option were not reflected in the code intelligence.
- Editor: Fixed issue where deleting directory did not clear problems for _.as_ and _.mxml_ files in the directory.
- Settings: Fixed issue where a Maven-built distribution of Apache Royale could not be used with the `nextgenas.sdk.editor` setting.
- Syntax Highlighting: Classes and interfaces are now colored differently than keywords, making ActionScript syntax highlighting more consistent with other languages in Visual Studio Code. Additionally, the package name in fully qualified class names is now colored too.

## 0.9.1

### Fixed Issues

- Apache Royale: Updated to search for npm moddules with new names: _@apache-royale/royale-js_ and _@apache-royale/royale-js-swf_.
- Build: Fixed issue where copying files from source paths could fail from nested directories.
- Completion: Fixed issue where activating completion with `Ctrl+Space` after a partial MXML component name could omit some results due to incorrect case-sensitive comparison.

## 0.9.0

### New Features

- Apache Royale is now supported! The code intelligence features of the ActionScript & MXML extension are now powered by the latest Apache Royale compiler. SWF projects targeting Adobe AIR and Flash Player remain fully suported, but Apache FlexJS projects are considered deprecated, and updgrading to Royale is recommended.
- Build: The **asconfigc** utility is now bundled with the ActionScript & MXML extension, and you don't need to install it manually anymore.
- Editor: When overriding a method, the completion list will suggest method names from superclass that can be overridden.
- Settings: added `nextgenas.asconfigc.useBundled` setting to disable the bundled version of **asconfigc** and use the globally installed npm version, if you prefer.
- Workspaces: Tasks and SWF debugging are now supported in multi-root workspaces. Code intelligence features are supported only in the first root folder for now. Work on multi-root workspaces will be completed in a future update.

### Fixed Issues

- Editor: Fixed issue where completion list could sometimes contain duplicate entries for the same class because it was defined in multiple SWC files.
- Editor: Fixed issue where completion sometimes would not work in MXML event handlers if an MXML namespace containing `//` were defined beforehand on the same line.
- Editor: Fixed exception thrown when editing code in a conditional compilation block that is currently disabled.

## 0.8.0

### New Features

- Build: Package Adobe AIR applications using a new `airOptions` field in _asconfig.json_. Supports both desktop and mobile, including both debug and release packages.
- Editor: If an MXML tag is unclosed, a suggestion to close it will appear in the completion list.
- Editor: Now supports completion of state names in MXML.
- Editor: Fixed issue where the first import added to a `<fx:Script>` element in MXML would incorrectly be added at the beginning of the file.
- Command: Added "Restart ActionScript/MXML server" command to restart the language server without reloading the entire window.
- Debugging: If _launch.json_ does not exist, the generated initial debug configurations for SWF now includes both `launch` and `attach` requests to better match the behavior of other debug extensions.

### Fixed Issues

- Debugging: Fixed issue where `extdir` option for AIR debugger could not use a relative path.
- Fixed issue where starting with the SDK set to Apache FlexJS and later switching to another SDK could cause an invalid embedded fonts error to be displayed in the Problems view.
- Fixed issue where "Internal error in ABC generator subsystem" error could be displayed in Apache FlexJS projects using CSS.
- ActionScript Source Path view now displays parent directories, if necessary, to make it easier to differentiate between source paths that have the same directory name.

## 0.7.1

### New Features

- Workspace: ActionScript SDK picker will show an open folder dialog when adding new SDKs instead of opening the settings to edit manually.

### Fixed Issues

- Code generation: Fixed issue where generate getter/setter light bulb suggestions did not appear unless the entire variable definition was selected.
- Editor: Fixed issue where automatic MXML namespace insertion would fail sometimes.
- Editor: Values of `true`, `false`, and `null` are now displayed in the completion list with other keywords.
- Fixed issue where language server could automatically detect an incorrect `swf-version` value, if omitted.
- Fixed issue where closing a workspace would leave the language server running with high CPU and memory usage because it wasn't shutting down correctly.
- Fixed issue where changing the "editor" SDK would require a restart. The language server is now restarted automatically.
- Fixed issue where Output panel would display useless "Unsupported notification method: \$/setTraceNotification" warning.
- Fixed issue where Output panel would display useless warning about SLFJ library.
- Fixed issue where Output panel would display useless warning about invalid _asconfig.json_ file, when the editor also displays the validation errors in context.
- Fixed issue where creating the root MXML file would sometimes fail because the compilation unit could not be found.

## 0.7.0

### New Features

- Editor: New built-in snippets for ActionScript and MXML.
- Editor: Added support for folding arbitrary regions in the editor with `//#region` and `//#endregion` comments.
- Editor: ASDoc documentation is displayed with signature help and parameters, if available.
- Debugging: new "attach" request to connect to Flash Player or AIR that is launched outside of Visual Studio Code. Can be used for Wi-Fi or USB debugging on a mobile device.
- Debugging: Simplified _launch.json_ by making the "program" field optional. When omitted, the extension automatically detects the location of the SWF or AIR application descriptor, using the output location in _asconfig.json_ or compiler defaults.
- Debugging: Source location is shown on the right side of the debug console when a runtime error is thrown and displayed in the console.

### Fixed Issues

- Java Compatibility: Fixed issue where extension would not work correctly with Java 9 because the version string format changed.
- Completion: Fixed issue where opening completion list in a completely empty MXML file would result in an exception.
- Completion: Fixed issue where the first import added to a file could be incorrectly added before the package block.
- Debugging: Migrated away from deprecated method for providing inital configurations for SWF debugger.
- Debugging: If the "profile" field is missing in _launch.json_, but the "config" field in _asconfig.json_ is set to "airmobile", will correctly use the "mobileDevice" profile.
- Debugging: Fixed issue where _http:_ and _https:_ URLs could not be used in "program" field of _launch.json_.
- Debugging: Improved detection of executable in _Contents/MacOS_ when "runtimeExecutable" field in _launch.json_ ends at _.app_ file extension.
- Editor: Fixed issue where the Organize Imports command added imports above package block if new imports are added and there were no existing imports.
- Syntax: Fixed issue where an escaped forward slash in a regular expression would prematurely finish the expression.
- Tasks: If _asconfig.json_ is missing from workspace, the asconfigc task will be offered if an _.as_ or _.mxml_ is open in the active editor.

## 0.6.0

### New Features

- Code generation: If an unknown method is called, a new code action can generate a new method with that signature.
- Code generation: If an unknown variable is referenced, a new code action can generate a new variable.
- Code generation: New code action will convert a member variable into a getter and setter.
- Completion: now includes language keywords in completion list when in certain contexts.
- Debugging: Some fields in _launch.json_ for SWF now provide a helpful completion list.
- Documentation: If asdoc comments are defined in _.as_ and _.mxml_ source files, they will be displayed in UI. Supported in both completion list and in hover popup.
- Organize imports: In addition to sorting, will now also remove unused imports and add missing imports.
- Rename Symbol: Added support for renaming classes and interfaces.
- Syntax highlighting: CSS in `<mx:Style>` or `<fx:Style>` blocks is now colored.
- UI: On startup, displays initializing message in status bar until extension is ready.

### Fixed Issues

- Completion: Fixed issue where an import could be incorrectly added for a symbol in the same package as the current file.
- Debugging: Fixed issue where Step Over command did not behave correctly with SWF files.
- Error Checking: Fixed issue where `swf-version` and `target-player` compiler options were incorrectly ignored.
- Goto definition: Fixed issue where null pointer exception would be displayed if goto definition were used on an MXML tag that could not be resolved to a class.
- Goto definition: Fixed issue where goto definition did not work with `super()` calls.
- Rename Symbol: fixed issue where renaming a getter did not also rename a setter with the same name (and vice versa).
- Syntax highlighting: fixed issue where metadata like `[Bindable]` was not colored in interface files.
- Syntax highlighting: fixed issue where code in MXML `Script` blocks with `mx` prefix was not colored (it only worked with `fx` prefix).
- Workspace symbol: improved search for class names by making unqualified name more important than package name.

## 0.5.1

- Fixed that hover and signature help did not work inside event added to the root element of an MXML component.
- Fixed issue where asconfig.json validation failed to warn if `files` field is missing for application (this field is still optional for a library).
- Fixed issue where searching by workspace symbol would throw error because some files were not opened in the editor.
- Fixed syntax highlighting for `/**/` being incorrectly detected as an asdoc comment, which caused the coloring to extend beyond the `*/` to the following lines.
- Fixed syntax highlighting of single line comments that start at end of the same line as a class, interface, or function declaration.
- Fixed issue where building the vscode extension from source would fail if Adobe dependencies were not already installed with Maven from another project, and updated the build instructions to use settings-template.xml.

## 0.5.0

- SDK version is listed in status bar and you can click it to open a helpful new SDK picker.
- New `nextgenas.sdk.searchPaths` setting allows you to add more SDKs to the SDK picker.
- New "ActionScript Source Path" view lists all classes/interfaces/components available from the `source-path` compiler option.
- The `nextgenas.sdk.editor` setting is now considered advanced, and most users should not need to use it any longer (even when using SDKs other than Apache FlexJS). Simply set `nextgenas.sdk.framework` (or use the new SDK picker) to choose your current project's SDK.
- Changing the `nextgenas.sdk.framework` setting does not require restarting Visual Studio Code anymore.
- In a FlexJS project, if value of `targets` compiler option does not start with "SWF", completion will give precedence to JS APIs.
- Adding a new line inside an asdoc comment will automatically add a `*` on the next line.
- The "Tasks: Configure Task Runner (ActionScript - asconfig.json)" command is deprecated. Go to Visual Studio Code's new "Tasks" menu and choose "Configure Default Build Task" instead.
- Replaced "NextGen ActionScript" icon with a new "AS3" icon.
- SWF debugger: If a source file is not found when trying to add a breakpoint, tries again later when more scripts are loaded. If breakpoints start out unverified, it's possible that they will be verified later when the SWF goes to a new frame.
- SWF debugger: Fixed issue where breakpoints removed in editor were not actually removed in running SWF and would still stop the debugger.
- SWF debugger: Begins executing SWF earlier because breakpoints can now be verified after startup. This may allow preloaders to render properly now (they skipped immediately to the end before).
- Searching workspace symbols simplified to exclude local variables in methods.
- Fixed issue where automatically adding an import would fail if the ActionScript file contained a license header or other comments before the package keyword.
- Fixed issue where some errors caused by invalid compiler options might not be displayed in the problems view.
- Fixed issue where extension could crash when doing certain things without an open workspace. Will now display a warning if certain actions are attempted without an open workspace.

## 0.4.4

- MXML completion list includes `<fx:Binding>`, `<fx:Component>`, `<fx:Declarations>`, `<fx:Metdata>`, `<fx:Script>`, and `<fx:Style>` tags.
- Added new `args` field to _launch.json_ configuration for Adobe AIR debugging. May be used to pass arguments to invoked AIR application.
- Added new compiler options for Apache FlexJS to _asconfig.json_ schema, including `html-template`, `html-output-filename`, `js-compiler-option`, `js-external-library-path`, `js-library-path`, `swf-external-library-path`, `swf-library-path`, and `remove-circulars`.
- Fixed issue where the extension could not be used in multiple VSCode windows on Windows 10 because it did not properly detect an open port.
- Fixed issue where package completion on Windows would incorrectly contain backslashes.
- Fixed issue where package completion would not work when file is completely empty and editor is powered by Apache FlexJS 0.8.0 (not fixed with FlexJS 0.7.0, due to bugs in the compiler).
- Fixed issue where values surrounded in quotes in the `additionalOptions` field of _asconfig.json_ were not parsed correctly and would lead to misleading errors.
- Fixed issue where completion could be unexpectedly triggered inside a string or a regular expression literal.
- Fixed regression where code intelligence features might stop working (and require restarting VSCode to fix) when the current file contains unclosed MXML constructs, like CDATA or comments, and the editor is powered by Apache FlexJS 0.7.0.

## 0.4.3

- Added "Organize Imports" context menu items for ActionScript and MXML files, and "ActionScript: Organize Imports" appears in the command palette.
- Added support for the the new `targets` compiler option from Apache FlexJS 0.8 in _asconfig.json_.
- The `config` field in _asconfig.json_ is now optional. It will default to `"flex"`, the same as when you run the `mxmlc` compiler.
- Fixed issue where functions or variables defined in a package were not automatically imported like classes or interfaces.
- Fixed issue where CRLF line endings were not stored properly in memory, causing the code intelligence to get out of sync with the editor on Windows.
- Fixed issue where Visual Studio code would incorrectly recognize all XML files as MXML.
- Fixed issue where `__AS3__.vec.Vector` would be incorrectly imported when typing a variable as `Vector`.
- Fixed issue where the `Vector` type in MXML was not considered part of the `http://ns.adobe.com/mxml/2009` namespace.
- Fixed issue where triggering completion on the left side of member access failed to provide suggestions.
- Fixed issue where inherited members were not included in completion for an interface.
- Fixed issue where failure to parse _asconfig.json_ would result in null reference errors from the language server.
- In addition to support for completion from the previous update, ActionScript in MXML event attributes now includes support for signature help, hover, goto definition, find all references, and rename.

## 0.4.2

- When opening a workspace, immediately checks for errors instead of waiting to open an ActionScript or MXML file.
- ActionScript completion is now supported inside MXML event attributes.
- Added ability to specify `extdir` option in _launch.json_ to allow AIR applications to access a directory of unpacked native extensions.
- Search for symbols in workspace is now case insensitive and it may start from anywhere inside the fully qualified name of a symbol (not only from the beginning).
- Fixed issue where the generated _launch.json_ might reference the wrong file extension (was .as, but should have been .swf).
- Fixed issue where the completion list was incorrectly empty after activating with Ctrl+click on the left side of member access.
- Fixed issue where a goto definition link would not appear if the mouse was over the first character of an identifier.

## 0.4.1

- Fixed issue where super protected members were sometimes omitted
- Added missing completion for package name at beginning of file.
- Fixed issue where list of document or workspace symbols would sometimes jump to metadata instead of definition name.
- Fixed issue where `trace()` console output would not appear on new lines.
- Fixed issue where signature help was not provided for constructor functions.
- Fixed issue where goto definition did not work on framework classes if framework was built on Windows (caused by different slashes in path).
- Fixed issue where numeric or boolean conditional compilation constants were not parsed correctly.

## 0.4.0

- Debug SWF files in Adobe AIR or Flash Player.
- Goto definition now finds framework classes in the SDK, even if they are compiled into a SWC.
- Fixed issue where misleading errors were displayed for ActionScript and MXML files that are outside the workspace or a `source-path`.
- Fixed issue where a problem was reported for embedded fonts even if the framework SDK supports them.
- Fixed issue where some syntax inside a package block was not colored correctly.
- Fixed issue where checking for errors would fail for certain projects due to exceptions in the compiler.
- Fixed issue where the Adobe AIR SDK & Compiler could not be passed to the `nextgenas.sdk.framework` setting because the compiler set an invalid default for the `-theme` compiler option.
- Improved default launch configuration options for Node.js.

## 0.3.1

- Fixed issue where `protected` members were not omitted in scopes were they should not be accessible.
- Fixed issue where problems reported for compiler configuration options were not cleared after they were resolved.
- Fixed regression that caused the extension to fail when using a Maven distribution of Apache FlexJS.

## 0.3.0

- IntelliSense completion of classes in ActionScript and `<fx:Script>` blocks now automatically adds imports.
- IntelliSense completion of classes in MXML now automatically adds xmlns declarations.
- The `nextgenas.flexjssdk` setting is deprecated and has been renamed to `nextgenas.sdk.editor`.
- The `nextgenas.frameworksdk` setting is deprecated and has been renamed to `nextgenas.sdk.framework`.
- When running the _Tasks: Configure Task Runner (ActionScript - asconfig.json)_ command, _tasks.json_ is automatically populated with the value of `nextgenas.sdk.framework` or `nextgenas.sdk.editor`.
- The `nextgenas.sdk.editor` setting now supports an Apache FlexJS binary distribution built with Maven (instead of Ant).
- Fixed issue where nightly builds of Apache FlexJS 0.8 could not be used as the editor SDK.
- Fixed issue where problems with compiler options were not reported.
- Fixed issue where code after a package block would be incorrectly colored.
- Fixed issue where `[ExcludeClass]` metadata was incorrectly ignored, causing classes with strange names to show up in completion.
- Fixed issue where file-internal symbols (things after the package block) did not appear in completion.
- Fixed issue where some members from superclass were not included in completion.
- Fixed issue where extension could be built from source only on macOS. It can now be built on Linux and Windows too.

## 0.2.1

- Fixed issue where opening a second workspace with a NextGenAS project would result in an error.
- Fixed issue where completion would incorrectly show generated classes from `[Embed]` metadata.
- Fixed issue where completion of local scope would include duplicate entries for getters and setters.
- Fixed issue where using MXML namespace \* for the top-level package would not be recognized by completion.
- Fixed issue where extension would check for errors in XML files that don't contain MXML.
- Fixed issue where auto-closing pairs like [], {}, and () would not work inside MXML `<fx:Script>` elements.
- Fixed issue where toggle comment keyboard shortcuts worked incorrectly inside MXML `<fx:Script>` elements.

## 0.2.0

- Added support for MXML.
- Added `nextgenas.frameworksdk` setting to load a framework inside a different SDK from Apache FlexJS. For instance, this will allow you to use the Feathers SDK or the original Apache Flex SDK.
- Migrated build script to Apache Maven to make it easier for contributors to get started.

## 0.1.1

- Added support for `define` compiler option in _asconfig.json_.
- Added support for `additionalOptions` field in in _asconfig.json_ to add new compiler options that aren't yet defined in the `compilerOptions` field.
- Added `nextgenas.java` setting to optionally point directly to a Java executable to override the automatic detection.
- Fixed issue where extension would crash if an old version of Java were discovered before a supported version of Java.
- Fixed issue where a required, but missing, JAR file of Apache FlexJS SDK could result in a cryptic error message.
- Fixed issue where creating a _tasks.json_ file for **asconfigc** would fail if the NextGenAS extension weren't already activated.
- Fixed issue where the extension would crash if activated when a folder is not open in VSCode. Now suggests to open a folder to enable all features.
- Fixed issue where some compiler errors would not be displayed in VSCode Problems view.
- Fixed issue where completion could be triggered inside a comment.
- Fixed issue where code intelligence would not work in some method bodies.
- Fixed minor issues with ActionScript syntax highlighting.

## 0.1.0

- Initial release with support for ActionScript.
