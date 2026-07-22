You use the \<fx:Reparent\> tag to change the parent container of a
component as part of a change of view state.

**MXML Syntax**

You can place an \<fx:Reparent\> tag in a Flex application file, or in
an MXML component file. You can use the \<fx:Reparent\> tag in any
parent component that can hold a child component, and the child can use
the `includeIn` or `excludeFrom` keywords. The \<fx:Reparent\> tag has
the following syntax:

    <fx:Reparent target="targetComp" includeIn="stateName">

where `target` specifies the target component, and `includeIn` specifies
a view state. When the current view state is set to *stateName*, the
target component becomes a child of the parent component of the
\<fx:Reparent\> tag.

The following example uses the \<fx:Reparent\> tag to switch a Button
control between two VBox containers in an HDividedBox container:

    <?xml version="1.0" encoding="utf-8"?>
    <s:Application xmlns:fx="http://ns.adobe.com/mxml/2009"
        xmlns:mx="library://ns.adobe.com/flex/mx"
        xmlns:s="library://ns.adobe.com/flex/spark">
        <s:layout>
            <s:VerticalLayout/>
        </s:layout>
        
        <s:states>
            <s:State name="Parent1"/>
            <s:State name="Parent2"/>
        </s:states>
        
        <mx:HDividedBox height="25%" width="100%" borderStyle="inset">
            <mx:VBox id="VB1" height="100%" width="100%" borderStyle="inset">
                <mx:Label text="VB1"/>
                <mx:Button id="setCB" includeIn="Parent1"/>
            </VBox>
            <mx:VBox id="VB2" height="100%" width="100%" borderStyle="inset">
                <mx:Label text="VB2"/>
                <fx:Reparent target="setCB" includeIn="Parent2"/>
            </VBox>
        </mx:HDividedBox>
        
        <s:Group>
            <s:layout>
                <s:HorizontalLayout/>
            </s:layout>
            <s:Button label="Parent 1"
                click="currentState='Parent1'"
                enabled.Parent1="false"/>
            <s:Button label="Parent 2"
                click="currentState='Parent2'"
                enabled.Parent2="false"/>
        </s:Group>
    </s:Application>
