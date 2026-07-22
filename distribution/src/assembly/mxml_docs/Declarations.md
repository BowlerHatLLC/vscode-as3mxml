You use the `<fx:Declarations>` tag to declare non-default, non-visual
properties of the current class. For example, you define effects,
validators, and formatters in the body of the `<fx:Declarations>` tag.

**MXML Syntax**

The `<fx:Declarations>` tag has the following syntax:

    <fx:Declarations>
        <!-- Non-visual declarations. -->
    </fx:Declarations>

For example, to apply an effect, you first define it in the
`<fx:Declarations>` tag, and then invoke the effect by calling the
`Effect.play()` method, as the following example shows:

    <?xml version="1.0"?>
    <s:Application xmlns:fx="http://ns.adobe.com/mxml/2009"
        xmlns:mx="library://ns.adobe.com/flex/mx"
        xmlns:s="library://ns.adobe.com/flex/spark">
        <s:layout>
            <s:VerticalLayout/>
        </s:layout>

        <fx:Declarations>
            <s:Resize id="myResizeEffect"
                target="{myImage}"
                widthBy="10" heightBy="10"/>
        </fx:Declarations>

        <mx:Image id="myImage"
            source="@Embed(source='assets/logo.jpg')"/>
        <s:Button label="Resize Me"
            click="myResizeEffect.end();myResizeEffect.play();"/>
    </s:Application>
