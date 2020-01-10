# ActionScript & MXML language extension for Visual Studio Code

Provides code intelligence for the ActionScript & MXML programming languages. Supports projects built with a variety of SDKs and tools — including Adobe AIR, Adobe Animate, the classic Apache Flex and Adobe Flex SDKs, Apache Royale, and the Feathers SDK. Runs on Windows, macOS, and Linux.

Extension created and maintained by [Josh Tynjala](https://patreon.com/josht). By [becoming a patron](https://www.patreon.com/bePatron?c=203199), you can directly support the ongoing development of this project.

## Features

- **Syntax Highlighting** for ActionScript, MXML, and JSFL files.
- **IntelliSense** completion for classes and interfaces, imports, properties, methods, and more.
- **Signature Help** shows a list of parameters when calling functions.
- **Errors and Warnings** are updated in real time as you type (or on save only, if you prefer).
- **Hover** over a symbol to see more details such as types, namespaces, and more.
- **Go to Definition** with `Ctrl+Click` on any usage of a symbol to open the file where it is defined.
- **Find All References** for any symbol in the project.
- **Rename Symbol** for classes, interfaces, methods, and variables.
- **Organize Imports** sorts imports alphabetically and removes unused imports.
- **Quick Fixes** to add missing imports, generate missing variables or methods, or convert variables to getters/setters.
- **Outline view** lists all symbols in the current file.
- **Go to Symbol in Workspace** with `Ctrl+T` and type the name of any symbol in the workspace.
- **Build Tasks** can compile a project or package an Adobe AIR app with `Ctrl+Shift+B`.
- **Debug** SWF projects in Adobe AIR and Flash Player.
- **Debug** Apache Royale (formerly known as FlexJS) projects in web browsers and Node.js.
- **Adobe Animate** integration includes _Test Movie_, _Debug Movie_, and _Publish_.
- **Import Projects** from Adobe Flash Builder and FlashDevelop.

## Help and Support

- [Help & Documentation](https://github.com/BowlerHatLLC/vscode-as3mxml/wiki)
- [Issue Tracker](https://github.com/BowlerHatLLC/vscode-as3mxml/issues)
- [Official Website](https://as3mxml.com/)

## Minimum Requirements

- Visual Studio Code 1.37
- Java 1.8 Runtime

## asconfig.json

Add a file named [_asconfig.json_](https://github.com/BowlerHatLLC/vscode-as3mxml/wiki/asconfig.json) to the root of your project to enable the ActionScript & MXML extension.

A sample _asconfig.json_ file for an Apache Royale project appears below:

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

Here's another sample _asconfig.json_ file for a pure ActionScript project targeting Adobe AIR on mobile:

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

## Support this project

The [ActionScript & MXML language extension for Visual Studio Code](https://marketplace.visualstudio.com/items?itemName=bowlerhatllc.vscode-nextgenas) is developed by [Josh Tynjala](http://patreon.com/josht) — thanks to the generous support of developers and small businesses in the community. Folks just like you! By [becoming a patron](https://www.patreon.com/bePatron?c=203199), you can join them in supporting the ongoing development of this project.

[Support Josh Tynjala on Patreon](http://patreon.com/josht)

Special thanks to the following sponsors for their generous support:

- [Moonshine IDE](http://moonshine-ide.com/)
- [Dedoose](https://www.dedoose.com/)
