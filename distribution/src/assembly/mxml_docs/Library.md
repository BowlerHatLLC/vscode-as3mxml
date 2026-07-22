You use the \<fx:Library\> tag to define zero or more named graphic
\<fx:Definition\> children. The definition itself in a library is not an
instance of that graphic, but it lets you reference that definition any
number of times in the document as an instance.

**MXML Syntax**

The \<fx:Library\> tag has the following syntax:

    <fx:Library>
        <fx:Definition name="defName">
            <!-- Non-visual declarations. -->
        </fx:Definition>
        ...
        <fx:Definition name="anotherDefName">
            <!-- Non-visual declarations. -->
        </fx:Definition>
    </fx:Library>

The \<fx:Library\> tag must be the first child of the document's root
tag. You can only have one \<fx:Library\> tag per document.

The following example defines a single graphic in the \<fx:Library\>
tag, and then uses it three times in the application:

    <?xml version="1.0" encoding="utf-8"?>
    <s:Application xmlns:fx="http://ns.adobe.com/mxml/2009"
                    xmlns:mx="library://ns.adobe.com/flex/mx"
                    xmlns:s="library://ns.adobe.com/flex/spark">

        <fx:Library>
            <fx:Definition name="MyTextGraphic">
                <s:Group>
                    <s:Label width="75">
                        <s:text>Hello World!</s:text>
                    </s:Label>
                    <s:Rect width="100%" height="100%">
                        <s:stroke>
                            <s:SolidColorStroke color="red"/>
                        </s:stroke>
                    </s:Rect>
                </s:Group>
            </fx:Definition>
        </fx:Library>

        <s:VGroup left="20" top="20">
            <fx:MyTextGraphic/>
            <fx:MyTextGraphic/>
            <fx:MyTextGraphic/>
        </s:VGroup>

    </s:Application>
