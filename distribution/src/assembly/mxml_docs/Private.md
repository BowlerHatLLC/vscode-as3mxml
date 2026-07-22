You use the \<fx:Private\> tag to provide meta information about the
MXML or FXG document. The compiler ignores all content of the
\<fx:Private\> tag, although it must be valid XML. The XML can be empty,
contain arbitrary tags, or contain a string of characters.

**MXML Syntax**

The \<fx:Private\> tag has the following syntax:

     <fx:Private>
          <!-- Private declarations. -->
     </fx:Private>

The \<fx:Private\> tag must be a child of the root document tag, and it
must be the last tag in the file.

The following example adds private information about the author and date
to the MXML file:

     <?xml version="1.0" encoding="utf-8"?>
     <s:Application xmlns:fx="http://ns.adobe.com/mxml/2009"
          xmlns:mx="library://ns.adobe.com/flex/mx"
          xmlns:s="library://ns.adobe.com/flex/spark">
          <mx:Canvas top="0" bottom="0" left="0" right="0">
               <s:Graphic>
                    <s:TextGraphic x="0" y="0">
                         <content>Hello World!</content>
                    </s:TextGraphic>
               </s:Graphic>
          </mx:Canvas>
          <fx:Private>
               <Date>10/22/2008</Date>
               <Author>Nick Danger</Author>
          </fx:Private>
     </s:Application>
