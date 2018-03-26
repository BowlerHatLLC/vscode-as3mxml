# ActionScript & MXML extension for Visual Studio Code by NextGen ActionScript

## Features

* **Syntax Highlighting** for ActionScript and MXML files.
* **IntelliSense** provides autocompletion for imports, types, and member access.
* **Signature Help** shows a list of parameters when calling functions.
* **Errors and Warnings** are updated in real time as you type.
* **Hover** over a symbol to see more details such as types, namespaces, and more.
* **Goto Definition** with a `Ctrl+Click` on any usage of a symbol.
* **Find All References** for any symbol in the project.
* **Goto Symbol** lists all symbols in the current file with `Ctrl+Shift+O`.
* **Open Symbol by Name** with `Ctrl+T` and type the name of any symbol in the project.
* **Rename Symbol** for classes, interfaces, methods, and both local an member variables.
* **Organize Imports** sorts imports alphabetically and removes unused imports.
* **Code Generation** supports adding getters/setters, methods, and both local and member variables.
* **Build** a project with `Ctrl+Shift+B`.
* **Debug** Apache Royale (formerly known as FlexJS) projects in web browsers and Node.js.
* **Debug** SWF projects in Adobe AIR and Flash Player.

## Help and Support

* [Help & Documentation](https://github.com/BowlerHatLLC/vscode-nextgenas/wiki)
* [Issue Tracker](https://github.com/BowlerHatLLC/vscode-nextgenas/issues)

## Minimum Requirements

* Visual Studio Code 1.20
* Java 1.8 Runtime

## asconfig.json

Add a file named [*asconfig.json*](https://github.com/BowlerHatLLC/vscode-nextgenas/wiki/asconfig.json) to the root of your project to enable the ActionScript & MXML extension.

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

* [YETi CGI](http://yeticgi.com/)

* [Moonshine IDE](http://moonshine-ide.com/)