# ActionScript & MXML language extension for Visual Studio Code

Provides ActionScript & MXML language support for Visual Studio Code — including a debugger for Adobe AIR and Flash Player. Supports projects using a variety of SDKs (including Adobe AIR, Apache Flex, Feathers SDK, and Apache Royale). Runs on Windows, macOS, and Linux. Open source and developed with community support.

## Features

* **Syntax Highlighting** for ActionScript and MXML files.
* **IntelliSense** completion for classes and interfaces, imports, properties, methods, and more.
* **Signature Help** shows a list of parameters when calling functions.
* **Errors and Warnings** are updated in real time as you type.
* **Hover** over a symbol to see more details such as types, namespaces, and more.
* **Go to Definition** with a `Ctrl+Click` on any usage of a symbol.
* **Find All References** for any symbol in the project.
* **Rename Symbol** for classes, interfaces, methods, and both local and member variables.
* **Organize Imports** sorts imports alphabetically and removes unused imports.
* **Quick Fixes** to add missing imports, add missing variables or methods, or generate getters/setters.
* **Outline view** lists all symbols in the current file.
* **Go to Symbol in Workspace** with `Ctrl+T` and type the name of any symbol in the workspace.
* **Build** a project with `Ctrl+Shift+B` or **Quick Compile & Debug** with `Ctrl+Enter`.
* **Debug** SWF projects in Adobe AIR and Flash Player.
* **Debug** Apache Royale (formerly known as FlexJS) projects in web browsers and Node.js.

## Help and Support

* [Help & Documentation](https://github.com/BowlerHatLLC/vscode-as3mxml/wiki)
* [Issue Tracker](https://github.com/BowlerHatLLC/vscode-as3mxml/issues)
* [Official Website](https://as3mxml.com/)

## Minimum Requirements

* Visual Studio Code 1.30
* Java 1.8 Runtime

## asconfig.json

Add a file named [*asconfig.json*](https://github.com/BowlerHatLLC/vscode-as3mxml/wiki/asconfig.json) to the root of your project to enable the ActionScript & MXML extension.

A sample *asconfig.json* file for an Apache Royale project appears below:

``` json
{
	"compilerOptions": {
		"targets": [
			"JSRoyale"
		],
		"source-map": true
	},
	"files":
	[
		"src/HelloRoyale.mxml"
	]
}
```

Here's another sample *asconfig.json* file for a pure ActionScript project targeting Adobe AIR on mobile:

``` json
{
	"config": "airmobile",
	"compilerOptions": {
		"output": "bin/HelloAIR.swf"
	},
	"application": "src/HelloAIR-app.xml",
	"files": [
		"src/HelloAIR.as"
	],
	"airOptions": {
		"android": {
			"output": "bin/HelloAIR.apk",
			"signingOptions": {
				"storetype": "pkcs12",
				"keystore": "ios_certificate.p12"
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

The [ActionScript & MXML language extension for Visual Studio Code](https://marketplace.visualstudio.com/items?itemName=bowlerhatllc.vscode-nextgenas) is developed by [Josh Tynjala](http://patreon.com/josht) — thanks to the generous support of developers and small businesses in the community. Folks just like you! Please consider [becoming a patron](https://www.patreon.com/bePatron?c=203199).

[Support Josh Tynjala on Patreon](http://patreon.com/josht)

Special thanks to the following sponsors for their generous support:

* [Moonshine IDE](http://moonshine-ide.com/)

* [Dedoose](https://www.dedoose.com/)