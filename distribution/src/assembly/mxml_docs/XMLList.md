The \<fx:XMLList\> tag is a compile-time tag that generates an XMLList
object from a text model that consists of valid XML nodes.

Unlike the XMLList class in ActionScript, this tag lets you use MXML
binding expressions in the XML text to extract node contents from
variable data. For example, you can bind a node's name attribute to a
text input value, as in the following line:

    <child name="{textInput1.text}"/>

**MXML Syntax**

You can place an \<fx:XMLList\> tag in a Flex application file or in an
MXML component file. The \<fx:XMLList\> tag must have an `id` attribute
value to be referenced by another component. The \<fx:XMLList\> tag does
not need an `id` attribute value if the tag is a direct child of an
\<mx:dataProvider\> tag. The \<fx:XMLList\> tag cannot be the root tag
of an MXML component.

The \<fx:XMLList\> tag has the following syntax:

    <fx:XMLList
        id="list ID">
            model declaration
    </fx:XMLList>

The following example uses the \<fx:XMLList\> tag to define a model for
MenuBar control:

    <?xml version="1.0"?>
    <s:Application xmlns:fx="http://ns.adobe.com/mxml/2009"
        xmlns:mx="library://ns.adobe.com/flex/mx"
        xmlns:s="library://ns.adobe.com/flex/spark"
        backgroundColor="#FFFFFF">

        <fx:XMLList id="myMenuModel">
            <menuitem label="MenuItem A" >
                <menuitem label="SubMenuItem 1-A" />
                <menuitem label="SubMenuItem 2-A" />
            </menuitem>
            <menuitem label="MenuItem B" />
            <menuitem label="MenuItem C" type="check" />
            <menuitem type="separator" />
            <menuitem label="MenuItem D" >
                <menuitem label="SubMenuItem 1-D" type="radio" groupName="one" />
                <menuitem label="SubMenuItem 2-D" type="radio" groupName="one" />
                <menuitem label="SubMenuItem 3-D" type="radio" groupName="one" />
            </menuitem>
        </fx:XMLList>

        <mx:MenuBar id="myMenu" labelField="@label" showRoot="true">
            <mx:dataProvider>
                {myMenuModel}
            </mx:dataProvider>
        </mx:MenuBar>

    </s:Application>
