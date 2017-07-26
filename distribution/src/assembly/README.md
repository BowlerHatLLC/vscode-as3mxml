# NextGen ActionScript extension for Visual Studio Code

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
* **Rename Symbol** for class members and local variables.
* **Build** a project with `Ctrl+Shift+B`.
* **Debug** Apache FlexJS projects in web browsers and Node.js and SWF projects in Adobe AIR and Flash Player.

## Help and Support

* [Documentation](https://github.com/BowlerHatLLC/vscode-nextgenas/wiki)
* [Issue Tracker](https://github.com/BowlerHatLLC/vscode-nextgenas/issues)

## Minimum Requirements

* Visual Studio Code 1.14
* Java 8 Runtime

## asconfig.json

Add a file named [`asconfig.json`](https://github.com/BowlerHatLLC/vscode-nextgenas/wiki/asconfig.json) to the root of your project to enable the ActionScript and MXML extension.

A sample `asconfig.json` file for an Apache FlexJS project appears below:

	{
		"compilerOptions": {
			"targets": [
				"JSFlex"
			]
		},
		"files":
		[
			"src/HelloFlexJS.mxml"
		]
	}

Here's another sample `asconfig.json` file that is for a pure ActionScript project targeting Adobe AIR on mobile:

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

## Support this project

Want to see more ActionScript and MXML tools and utilities like this Visual Studio Code extension? Please [become a patron](http://patreon.com/josht) and support the next generation of ActionScript development on the web -- without a plugin!

[NextGen ActionScript by Josh Tynjala on Patreon](http://patreon.com/josht)

Special thanks to the following sponsors for their generous support:

* [YETi CGI](http://yeticgi.com/)

* [Moonshine IDE](http://moonshine-ide.com/)
