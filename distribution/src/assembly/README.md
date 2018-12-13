# ActionScript & MXML extension for Visual Studio Code

## Features

* **Syntax Highlighting** for ActionScript and MXML files.
* **IntelliSense** provides autocompletion for imports, types, and member access.
* **Signature Help** shows a list of parameters when calling functions.
* **Errors and Warnings** are updated in real time as you type.
* **Hover** over a symbol to see more details such as types, namespaces, and more.
* **Go to Definition** with a `Ctrl+Click` on any usage of a symbol.
* **Find All References** for any symbol in the project.
* **Go to Symbol in File** lists all symbols in the current file with `Ctrl+Shift+O`.
* **Go to Symbol in Workspace** with `Ctrl+T` and type the name of any symbol in the workspace.
* **Rename Symbol** for classes, interfaces, methods, and both local and member variables.
* **Organize Imports** sorts imports alphabetically and removes unused imports.
* **Code Generation** supports adding getters/setters, methods, and both local and member variables.
* **Build** a project with `Ctrl+Shift+B` or **Quick Compile & Debug** with `Ctrl+Enter`.
* **Debug** SWF projects in Adobe AIR and Flash Player.
* **Debug** Apache Royale (formerly known as FlexJS) projects in web browsers and Node.js.

## Help and Support

* [Help & Documentation](https://github.com/BowlerHatLLC/vscode-as3mxml/wiki)
* [Issue Tracker](https://github.com/BowlerHatLLC/vscode-as3mxml/issues)

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
		]
	},
	"files":
	[
		"src/HelloRoyale.mxml"
	]
}
```

Here's another sample *asconfig.json* file that is for a pure ActionScript project targeting Adobe AIR on mobile:

``` json
{
	"config": "airmobile",
	"compilerOptions": {
		"output": "bin/HelloAIR.swf"
	},
	"application": "src/HelloAIR-app.xml",
	"files":
	[
		"src/HelloAIR.as"
	]
}
```

## Support this project

The [ActionScript & MXML extension for Visual Studio Code](https://marketplace.visualstudio.com/items?itemName=bowlerhatllc.vscode-nextgenas) is developed by [Josh Tynjala](http://patreon.com/josht) with the support of community members like you.

[Support Josh Tynjala on Patreon](http://patreon.com/josht)

Special thanks to the following sponsors for their generous support:

* [Moonshine IDE](http://moonshine-ide.com/)

* [Dedoose](https://www.dedoose.com/)