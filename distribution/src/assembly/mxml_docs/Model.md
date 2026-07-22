You use the \<fx:Model\> tag to declare a data model in MXML. An
\<fx:Model\> tag is compiled into a tree of ActionScript objects; the
leaves of the tree are scalar values.

**MXML Syntax**

You can place an \<fx:Model\> tag in a Flex application file, or in an
MXML component file. The tag must have an id value. It cannot be the
root tag of an MXML component. The \<fx:Model\> tag has the following
syntax:

    <fx:Model id="modelID">
      model declaration
    </fx:Model>

or:

    <fx:Model id="modelID" source="fileName" />

where `source` specifies an external source, such as a file, for the
data model. The external source can contain static data and data binding
expressions. The file referenced in a `source` property resides on the
server and not on the client machine. The compiler reads the source
value and compiles the source into the application; the `source` value
is not read at runtime.

The model declaration, either in-line in the tag or in the source file,
must have a single root node that contains all other nodes. You can use
MXML binding expressions, such as `{myForm.lastName.text}` in the model
declaration. This way you can bind the contents of form fields to a
structured data representation.

In the following example, the myEmployee model is placed in an MXML
application file:

    <?xml version="1.0"?>
    <s:Application xmlns:fx="http://ns.adobe.com/mxml/2009"
        xmlns:mx="library://ns.adobe.com/flex/mx"
        xmlns:s="library://ns.adobe.com/flex/spark">
    ...
      <fx:Model id="MyEmployee">
        <root>
            <name>
                <first>Will</first>
                <last>Tuckerman</last>
            </name>
            <department>Accounting</department>
            <email>wtuckerman@wilsoncompany.com</email>
        </root>
      </fx:Model>
    ...
    </s:Application>
