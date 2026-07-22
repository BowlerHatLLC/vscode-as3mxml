You use the \<fx:Binding\> tag to tie the data in one object to another
object. When you use the \<fx:Binding\> tag, you provide a source
property and a destination property. You can use the \<fx:Binding\> tag
to completely separate the view, or user interface, from the model. the
\<fx:Binding\> tag also lets you bind different source properties to the
same destination property.

**MXML Syntax**

The \<fx:Binding\> tag has the following syntax:

    <fx:Binding
        source="No default."
        destination="No default"
    />

For example, you might bind the `text` property of the name field of one
form to the `text` property of the name field of another form, as
follows:

    <fx:Binding
        source="billForm.name.text"
        destination="shipform.name.text"
    />

Bidirectional, or two-way, data binding occurs when two objects act as
the source and the destination for each other. When you modify the
source property of either object, the destination property of the other
object is updated.

Define a bidirectional data binding using one of the following methods:

1\. Define two objects that specify as the source a property of the
other object. In the following example, input1 specifies input2.text as
the source property, and input2 specifies input1.text as the source
property. Any change to input1.text updates input2.text, and any change
to input2.text updates input1.text:

    <!-- Specify data binding for both controls. -->
    <s:TextInput id="input1" text="{input2.text}"/>
    <s:TextInput id="input2" text="{input1.text}"/>

2\. Use the @{bindable_property} syntax for one source property, as the
following example shows:

    <!-- Specify data binding for both controls. -->
    <s:TextInput id="input1" text="@{input2.text}"/>
    <s:TextInput id="input2"/>

**Note:** The property definition that includes the @{bindable_property}
syntax i s called the primary property. If the primary property has not
had a value assigned to it, the binding to it occurs first, before a
binding to the other property.

3\. Use the `twoWay` property of the \<fx:Binding\> tag, as the
following example shows:

    <fx:Binding source="input1.text" destination="input2.text" twoWay="true/>
