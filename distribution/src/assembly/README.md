# ActionScript & MXML language extension for Visual Studio Code

Provides code intelligence for the ActionScript & MXML programming languages. Supports projects built with a variety of SDKs and tools — including Adobe AIR, Adobe Animate, the classic Apache Flex and Adobe Flex SDKs, Apache Royale, and the Feathers SDK. Runs on Windows, macOS, and Linux.

Extension created and maintained by [Josh Tynjala](https://patreon.com/josht). By [becoming a patron](https://www.patreon.com/bePatron?c=203199), you can directly support the ongoing development of this project.

## Features

- **Syntax Highlighting** for ActionScript, MXML, and JSFL files.
- **IntelliSense** completion for classes and interfaces, imports, properties, methods, and more.
- **Signature Help** shows a list of a function's parameters when you call it.
- **Errors and Warnings** are updated in real time as you type (or on save only, if you prefer).
- **Hover** over a symbol to see more details such as types, namespaces, documentation, and more.
- **Go to Definition** with `Ctrl+Click` on any usage of a symbol to open the file where it is defined. Can be used to see the public API of classes from compiled _.swc_ libraries too.
- **Find All References** for any symbol in the project across all source files.
- **Rename Symbol** for classes, interfaces, methods, and variables.
- **Organize Imports** sorts imports alphabetically, removes unused imports, and adds missing imports.
- **Format** ActionScript and MXML code to match a particular style.
- **Lint** ActionScript and MXML code to find "code smells" and other common issues.
- **Quick Fixes** to add missing imports, generate missing variables or methods, or convert variables to getters/setters.
- **Outline view** lists all symbols from the current file in a hierarchical tree.
- **Go to Symbol in Workspace** with `Ctrl+T` and type the name of any symbol to search for it in the workspace.
- **Build Tasks** can compile a project or package an Adobe AIR app with `Ctrl+Shift+B`.
- **Adobe Animate** integration includes _Test Movie_, _Debug Movie_, and _Publish_.
- **Import Projects** from Adobe Flash Builder or FlashDevelop.

## Help and Support

- [Documentation](https://github.com/BowlerHatLLC/vscode-as3mxml/wiki)
- [Help & Support Forum](https://github.com/BowlerHatLLC/vscode-as3mxml/discussions)
- [Bug Reports and Feature Requests](https://github.com/BowlerHatLLC/vscode-as3mxml/issues)
- [Official Website](https://as3mxml.com/)

## Minimum Requirements

- Visual Studio Code 1.70
- Java 8 or newer

## asconfig.json

Add a file named [_asconfig.json_](https://github.com/BowlerHatLLC/vscode-as3mxml/wiki/asconfig.json) to the root of your project to enable the ActionScript & MXML extension.

A sample _asconfig.json_ file for an [Apache Royale project](https://github.com/BowlerHatLLC/vscode-as3mxml/wiki/Create-a-new-ActionScript-project-in-Visual-Studio-Code-that-targets-Apache-Royale) appears below:

```json
{
  "compilerOptions": {
    "targets": ["JSRoyale"],
    "source-path": ["src"],
    "source-map": true
  },
  "mainClass": "HelloRoyale"
}
```

Here's another sample _asconfig.json_ file for a pure ActionScript project targeting [Adobe AIR on mobile](https://github.com/BowlerHatLLC/vscode-as3mxml/wiki/Create-a-new-ActionScript-project-in-Visual-Studio-Code-that-targets-Adobe-AIR-for-mobile-platforms):

```json
{
  "config": "airmobile",
  "compilerOptions": {
    "source-path": ["src"],
    "output": "bin/HelloAIR.swf"
  },
  "mainClass": "HelloAIR",
  "application": "src/HelloAIR-app.xml",
  "airOptions": {
    "android": {
      "output": "bin/HelloAIR.apk",
      "signingOptions": {
        "storetype": "pkcs12",
        "keystore": "android_certificate.p12"
      }
    },
    "ios": {
      "output": "bin/HelloAIR.ipa",
      "signingOptions": {
        "storetype": "pkcs12",
        "keystore": "ios_certificate.p12",
        "provisioning-profile": "example.mobileprovision"
      }
    }
  }
}
```

## Debug and Run

Debug and run SWF projects in Adobe AIR or Flash Player by installing the separate [Debugger for SWF](https://marketplace.visualstudio.com/items?itemName=bowlerhatllc.vscode-swf-debug) extension.

Debug and run Apache Royale with Visual Studio Code's built-in support for debugging JavaScript (or install the appropriate extension for debugging in your chosen web browser). Enable the `source-map` compiler option in your _asconfig.json_ file so that you may add breakpoints and step through your original _.as_ or _.mxml_ class files.

## Nightly builds

Continuous integration produces builds on every push to the repository. Visit the [Actions page for vscode-as3mxml](https://github.com/BowlerHatLLC/vscode-as3mxml/actions?query=branch%3Amain+is%3Asuccess+event%3Apush) to find the most recent successful runs. Each run should have an artifact attached that is named **vscode-as3mxml**. Download this file, unzip it, and you'll get a _.vsix_ file that may be installed by Visual Studio Code.

## Support this project

The [ActionScript & MXML language extension for Visual Studio Code](https://marketplace.visualstudio.com/items?itemName=bowlerhatllc.vscode-as3mxml) is developed by [Josh Tynjala](http://patreon.com/josht) — thanks to the generous support of developers and small businesses in the community. Folks just like you! By [becoming a patron](https://www.patreon.com/bePatron?c=203199), you can join them in supporting the ongoing development of this project.

[Support Josh Tynjala on Patreon](http://patreon.com/josht)

Special thanks to the following sponsors for their generous support:

- [Moonshine IDE](https://moonshine-ide.com/)
- [Jackbox Games](https://jackboxgames.com)
