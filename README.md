# NextGen ActionScript extension for Visual Studio Code

## Features

* **Syntax Highlighting** for ActionScript files.
* **IntelliSense** provides autocompletion for imports, types, and member access.
* **Signature Help** shows a list of parameters when calling functions.
* **Errors and Warnings** are updated in real time as you type.
* **Hover** over a symbol to see more details such as types, namespaces, and more.
* **Goto Definition** with a `Ctrl+Click` on any usage of a symbol.
* **Find All References** for any symbol in the project.
* **Goto Symbol** lists all symbols in the current file with `Ctrl+Shift+O`.
* **Open Symbol by Name** with `Ctrl+T` and type the name of any symbol in the project.
* **Rename Symbol** for class members and local variables.
* **Debug** ActionScript transpiled to JavaScript in Node.js or Google Chrome (browser debugging requires [Debugger for Chrome](https://marketplace.visualstudio.com/items?itemName=msjsdiag.debugger-for-chrome) extension).

## Installation

This extension is still in development. A preview build will be available soon.

## Minimum Requirements

* Visual Studio Code 1.4
* Java 8 Runtime
* Apache FlexJS 0.7 Nightly

## asconfig.json

Add a file named `asconfig.json` to the root of your project to enable the NextGenAS extension.

More detailed documentation will be available soon. Here is a sample file:

	{
		"config": "js",
		"compilerOptions": {
			"debug": true,
			"library-path": [
				"libs"
			],
		},
		"files":
		[
			"src/Main.as"
		]
	}

## Support this project

Want to see more ActionScript tools and utilities like this Visual Studio Code extension? Please [become a patron](http://patreon.com/josht) and support the next generation of ActionScript development on the web -- without a plugin!

[NextGen ActionScript by Josh Tynjala on Patreon](http://patreon.com/josht)

Special thanks to the following sponsors for their generous support:

* [YETi CGI](http://yeticgi.com/)