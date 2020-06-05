# ActionScript & MXML extension for Visual Studio Code

This README file is intended for contributors to the extension. If you simply want to install the latest stable version of the extension, please visit the [Visual Studio Marketplace](https://marketplace.visualstudio.com/items?itemName=bowlerhatllc.vscode-nextgenas). For help using the extension, visit the [vscode-as3mxml wiki](https://github.com/BowlerHatLLC/vscode-as3mxml/wiki) for detailed instructions.

## Modules

This project is divided into several modules.

1. **language-server** provides ActionScript and MXML code intelligence for Visual Studio Code and other editors (like [Moonshine IDE](https://moonshine-ide.com/)) that support the [Language Server Protocol](https://microsoft.github.io/language-server-protocol/). Code intelligence is powered by the ActionScript compiler from [Apache Royale](https://royale.apache.org/), but it supports a variety of ActionScript SDKs. This module is written in Java.

1. **asconfigc** creates an executable JAR file that can parse the [_asconfig.json_](https://github.com/BowlerHatLLC/vscode-as3mxml/wiki/asconfig.json) file format and execute the compiler with the specified options. This module is written in Java.

1. **check-java-version** creates an executable JAR file that will verify that the current version of Java meets the minimum requirements for the language server.

1. **check-royale-version** creates an executable JAR file that will verify that the current version of the Apache Royale compiler meets the minimum requirements for the language server.

1. **vscode-extension** implements various features that are specific to Visual Studio Code, and initializes the language server Java process. This module is written in TypeScript.

1. **distribution** packages everything together to create the final extension that is compatible with Visual Studio Code.

## Build instructions

Requires [Apache Maven](https://maven.apache.org/) and [Node.js](https://nodejs.org/). Run the following command in the root directory to build the extension:

```
mvn clean package
```

The extension will be generated in _distribution/target/vscode-as3mxml/vscode-as3mxml_. This directory may be run inside Visual Studio Code's extension host. Additionally, a _.vsix_ file will be generated that may be manually installed in Visual Studio Code.

## Running tests

Tests are run in the Visual Studio Code extension host.

1. Open the root of this repository in Visual Studio Code.
1. Goto the **View** menu, and select **Debug**.
1. Choose the **Launch Tests** configuration.
1. Goto the **Debug** menu and select **Start Debugging**.

Results will appear in the **Output** view.

Note: If the extension cannot find Apache Royale on your system automatically, you may need to configure the `as3mxml.sdk.framework` or `as3mxml.sdk.editor` setting in _vscode-extension/src/test/application_workspace/.vscode/settings.json_.

## Support this project

The [ActionScript & MXML extension for Visual Studio Code](https://marketplace.visualstudio.com/items?itemName=bowlerhatllc.vscode-nextgenas) is developed by [Josh Tynjala](http://patreon.com/josht) with the support of community members like you.

[Support Josh Tynjala on Patreon](http://patreon.com/josht)

Special thanks to the following sponsors for their generous support:

- [Moonshine IDE](http://moonshine-ide.com/)
- [Dedoose](https://www.dedoose.com/)
