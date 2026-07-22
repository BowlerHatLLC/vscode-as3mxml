You use the \<fx:Style\> tag to define styles that apply to the current
document and its children. You define styles in the \<fx:Style\> tag
using CSS syntax and can define styles that apply to all instances of a
control or to individual controls.

You can also point to an external CSS file that should be included by
using the `source` property. If you reference a file by using the
`source` property, that file must reside on the server and not on the
client machine. The compiler reads the source value and compiles the
source into the application at compile-time; the `source` value is not
read at runtime.

The following example specifies an external CSS file with the `source`
property:

    <fx:Style source="../assets/styles/MyStyles.css"/>

You can use type and class selectors inside the Style tag's CSS block.

The following example uses a type selector to apply the same color to
all instances of a Button class:

    <fx:Style/>
        Button {
            color:red;
        }
    </fx:Style/>

The following example uses a class selector to apply the same color to
all components whose `styleName` property is set to myStyle:

    <fx:Style/>
        .myStyle {
            color:red;
        }
    </fx:Style/>

**MXML Syntax**

The \<fx:Style\> tag has the following syntax:

    <fx:Style [source="style_sheet"]>
      [selector_name {
        style_property: value;
        [...]
      }]
    </fx:Style>
