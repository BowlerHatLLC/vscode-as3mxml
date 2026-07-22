The \<fx:XML\> tag is a compile-time tag that generates an XML object or
XMLNode object from a text model. The tag has the following features
that are not provided directly by the Flash classes:

- You can specify a file as the source of the XML text model.
- You can use MXML binding expressions in the XML text to extract node
  contents from variable data; for example, you could bind a node's name
  attribute to a text input value, as in the following line:
  `<child name="{textInput1.text}"/>`
- You can use the `format="xml"` attribute to generate a legacy XMLNode
  object instead of an E4X-format XML object.

**MXML Syntax**

You can place an \<fx:XML\> tag in a Flex application file or in an MXML
component file. The \<fx:XML\> tag must have an `id` attribute value to
be referenced by another component. The \<fx:XML\> tag does not need an
`id` attribute value if the tag is a direct child of an
\<mx:dataProvider\> tag. The tag body must have a single root node
containing all child nodes. The \<fx:XML\> tag cannot be the root tag of
an MXML component. You cannot specify Flash XML or XMLNode class
properties in the tag; you must specify these in ActionScript.

The \<fx:XML\> tag has the following syntax:

    <fx:XML
        id="modelID"
        format="e4x|xml">
            <root>
                child nodes
            </root>
    </fx:XML>

or:

    <fx:XML
        id="modelID"
        format="e4x|xml"
        source="fileName"
        />

The default `format` property value of `e4x` creates an XML object,
which implements the XML-handling standards defined in the ECMA-357
specification (known as "E4X"). For backward compatibility, you can set
the `format` property to `xml` to generate an object of the type
flash.xml.XMLNode.

The `source` property specifies an external source, such as a file, for
the data model. The external source can contain static data and data
binding expressions. The compiler reads the source value and compiles
the source into the application; the `source` value is not read at
runtime.

The following example uses the \<fx:XML\> tag to define a model for a
MenuBar control:

    <?xml version="1.0"?>
    <s:Application xmlns:fx="http://ns.adobe.com/mxml/2009"
        xmlns:mx="library://ns.adobe.com/flex/mx"
        xmlns:s="library://ns.adobe.com/flex/spark"
        backgroundColor="#FFFFFF">

        <fx:XML format="e4x" id="myMenuModel">
            <root label="Menu">
            <menuitem label="MenuItem A">
                <menuitem label="SubMenuItem 1-A"/>
                <menuitem label="SubMenuItem 2-A" />
            </menuitem>
            <menuitem label="MenuItem B"/>
            <menuitem label="MenuItem C" type="check"/>
            <menuitem type="separator"/>
            <menuitem label="MenuItem D">
                <menuitem label="SubMenuItem 1-D" type="radio" groupName="one"/>
                <menuitem label="SubMenuItem 2-D" type="radio" groupName="one"/>
                <menuitem label="SubMenuItem 3-D" type="radio" groupName="one"/>
            </menuitem>
            </root>
        </fx:XML>

        <mx:MenuBar id="myMenu" labelField="@label" showRoot="true">
            <mx:dataProvider>
                {myMenuModel}
            </mx:dataProvider>
        </mx:MenuBar>

    </s:Application>
