You use the `<fx:Metadata>` tag to insert metadata tags in an MXML file.
Metadata tags provide information to the Flex compiler that describes
how your MXML components are used in a Flex application. Metadata tags
do not get compiled into executable code, but provide information to
control how portions of your code get compiled.

Note that you can only insert metadata tags in the `<fx:Metadata>`
block; you cannot insert MXML or ActionScript code.

For example, you may create an MXML component that defines a new event.
To make that event known to the Flex compiler so that you can reference
it in MXML, you insert the \[Event\] metadata tag into your component,
as the following example shows:

    <fx:Metadata>
        [Event("darken")]
    </fx:Metadata>

In this example, you use metadata to make the `darken` event available
to the MXML compiler. Metadata tags include \[Event\], \[Effect\],
\[Style\], \[Inspectable\], and others. For more information, see the
*Using Metadata Tags* chapter in the *Creating and Extending Flex
Components* book.

When using metadata tags in ActionScrip class files, you insert the
metadata tag directly into the class definition; you do not use the
`<fx:Metadata>` tag.

In an MXML file, you insert the metadata tags either in an `<fx:Script>`
block along with your ActionScript code, or in an `<fx:Metadata>` block,
as the following example shows:

    <?xml version="1.0"?>
    <mx:TextArea xmlns:mx="http://www.adobe.com/2006/mxml">

        <fx:Metadata>
          [Event("enableChange")]
        </fx:Metadata>

        <fx:Script>
            <![CDATA[
                
                // Import Event class.
                import flash.events.Event;

                // Define class properties/methods
                private var _enableTA:Boolean;

                // Add the [Inspectable] metadata tag before the individual property.
                [Inspectable(defaultValue="false")]
                public function set enableTA(val:Boolean):void {
                    _enableTA = val;
                    this.enabled = val;
                    
                    // Define event object, initialize it, then dispatch it.
                    var eventObj:Event = new Event("enableChange");
                    dispatchEvent(eventObj);
                }
            ]]>
        </fx:Script>
    </mx:TextArea>

**MXML Syntax**

The \<fx:Metadata\> tag has the following syntax:

    <fx:Metadata>
        <!-- Metadata tags go here. -->
    </fx:Metadata>
