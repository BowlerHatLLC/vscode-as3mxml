# ActionScript and MXML extension for Visual Studio Code

This README file is intended for contributors to the extension. If you simply want to install the latest stable version of the extension, please visit the [Visual Studio Marketplace](https://marketplace.visualstudio.com/items?itemName=bowlerhatllc.vscode-nextgenas). For help using the extension, visit the [wiki](https://github.com/BowlerHatLLC/vscode-nextgenas/wiki) for detailed instructions.

## Modules

This project is divided into several modules.

1. **language-server** provides ActionScript and MXML code intelligence for Visual Studio Code or another editor that supports the Language Server Protocol. It integrates with the compiler from Apache Royale and is written in Java.

1. **swf-debugger** provides SWF debugging for Visual Studio Code or another editor that supports the VSCode Debugging Protocol. It integrates with the debugger from Apache Royale and is written in Java.

1. **asconfigc** parses *asconfig.json* and executes the compiler with the specified options.

1. **check-java-version** creates an executable JAR file that will verify that the current version of Java meets the minimum requirements for the language server.

1. **check-royale-version** creates an executable JAR file that will verify that the current version of Apache Royale meets the minimum requirements for the language server.

1. **vscode-extension** initializes the Java language-server process from inside Visual Studio Code and handles a few features that the Language Server Protocol does not support.

1. **distribution** packages everything together to create the final extension that is compatible with Visual Studio Code.

## Build instructions

Requires [Apache Maven](https://maven.apache.org/). Run the following command in the root directory to build the extension:

```
mvn clean package -s settings-template.xml
```

The extension will be generated in *distribution/target/vscode-nextgenas/vscode-nextgenas*. This directory may be run inside Visual Studio Code's extension host. Additionally, a *.vsix* file will be generated that may be installed in Visual Studio Code.

## Running tests

Tests are run in the Visual Studio Code extension host.

1. Open the root of this repository in Visual Studio Code.
1. Goto the **View** menu, and select **Debug**.
1. Choose the **Launch Tests** configuration.
1. Goto the **Debug** menu and select **Start Debugging**.

Results will appear in the **Output** view.

Note: If the extension cannot find Apache Royale on your system automatically, you may need to configure the `nextgenas.sdk.framework` or `nextgenas.sdk.editor` setting in *vscode-extension/src/test/fixtures/.vscode/settings.json*.

## Support this project

The [ActionScript and MXML extension for Visual Studio Code](https://marketplace.visualstudio.com/items?itemName=bowlerhatllc.vscode-nextgenas) is developed by [Josh Tynjala](http://patreon.com/josht) with the support of community members like you.

[Support Josh Tynjala on Patreon](http://patreon.com/josht)

Special thanks to the following sponsors for their generous support:

* [YETi CGI](http://yeticgi.com/)

* [Moonshine IDE](http://moonshine-ide.com/)