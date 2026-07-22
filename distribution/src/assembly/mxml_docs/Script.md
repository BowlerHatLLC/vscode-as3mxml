You use the \<fx:Script\> tag to define blocks of ActionScript code.
ActionScript blocks can contain variable and function definitions. You
can place blocks of ActionScript code in the body of the tag, or you can
include an external file by specifying the source with the `source`
property of the tag, as shown below:

    <fx:Script source="file_name.as" />

The script within an \<fx:Script\> tag is accessible from any component
in the MXML file. You can define multiple script blocks in your MXML
files, but you should try to keep them in one location to improve
readability.

**MXML Syntax**

When using a script block in the body of the tag, you must wrap the
contents in a CDATA construct. This prevents the compiler from
interpreting the contents of the script block as XML, and allows
ActionScript to be properly generated. As a result, it is a good
practice to write all your \<fx:Script\> and \</fx:Script\> tags as
follows:

    <fx:Script>
        <![CDATA[
            //ActionScript statements
        ]]>
    </fx:Script>
