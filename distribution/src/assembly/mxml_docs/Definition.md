Use one or more \<fx:Definition\> tags inside a \<fx:Library\> tag to
define graphical children that you can then use in other parts of the
application. A \<fx:Library\> tag can have any number of
\<fx:Definition\> tags as children. An element in the \<fx:Definition\>
tag is not instantiated or added to the display list until it is added
as a tag outside of the \<fx:Library\> tag.

**MXML Syntax**

The \<fx:Definition\> tag has the following syntax.

     <fx:Library>
          <fx:Definition name="defName">
               <!-- Graphical children. -->
          </fx:Definition>
          ...
          <fx:Definition name="anotherDefName">
               <!-- Graphical children. -->
          </fx:Definition>
     </fx:Library>

The \<fx:Definition\> tag must define a `name` attribute. You use the
attribute as the tag name when instantiating the element.

The following example defines the MyCircle and MySquare graphics with
the Definition tags. It then instantiates several instances of these in
the application file:

     <?xml version="1.0" encoding="utf-8"?>
     <s:Application xmlns:fx="http://ns.adobe.com/mxml/2009"
          xmlns:mx="library://ns.adobe.com/flex/mx"
          xmlns:s="library://ns.adobe.com/flex/spark">
          <fx:Library>
               <fx:Definition name="MySquare">
                    <s:Group>
                         <s:Rect width="100%" height="100%">
                              <s:stroke>
                                   <s:SolidColorStroke color="red"/>
                              </s:stroke>
                         </s:Rect>
                    </s:Group>
               </fx:Definition>
               <fx:Definition name="MyCircle">
                    <s:Group>
                         <s:Ellipse width="100%" height="100%">
                              <s:stroke>
                                   <s:SolidColorStroke color="blue"/>
                              </s:stroke>
                         </s:Ellipse>
                    </s:Group>
               </fx:Definition>
          </fx:Library>
          <mx:Canvas>
               <fx:MySquare x="0" y="0" height="20" width="20"/>
               <fx:MySquare x="25" y="0" height="20" width="20"/>
               <fx:MyCircle x="50" y="0" height="20" width="20"/>
               <fx:MyCircle x="0" y="25" height="20" width="20"/>
               <fx:MySquare x="25" y="25" height="20" width="20"/>
               <fx:MySquare x="50" y="25" height="20" width="20"/>
               <fx:MyCircle x="0" y="50" height="20" width="20"/>
               <fx:MyCircle x="25" y="50" height="20" width="20"/>
               <fx:MySquare x="50" y="50" height="20" width="20"/>
          </mx:Canvas>
     </s:Application>

Each Definition in the \<fx:Library\> tag is compiled into a separate
ActionScript class. that is a subclass of the type represented by the
first node in the definition. In the previous example, the new class is
a subclass of mx.graphics.Group. This scope of this class is limited to
the document. It should be treated as a private ActionScript class.
