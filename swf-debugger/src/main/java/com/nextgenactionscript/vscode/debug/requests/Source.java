/*
Copyright 2016 Bowler Hat LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.nextgenactionscript.vscode.debug.requests;

public class Source
{
    /**
     * The short name of the source. Every source returned from the debug adapter has a name. When sending a source to the debug adapter this name is optional.
     */
    public String name;

    /**
     * The path of the source to be shown in the UI. It is only used to locate and load the content of the source if no sourceReference is specified (or its value is 0).
     */
    public String path;

    /**
     * If sourceReference > 0 the contents of the source must be retrieved through the SourceRequest (even if a path is specified). A sourceReference is only valid for a session, so it must not be used to persist a source.
     */
    public double sourceReference;

    /**
     * An optional hint for how to present the source in the UI. A value of 'deemphasize' can be used to indicate that the source is not available or that it is skipped on stepping.
     */
    public String presentationHint = null;

    /**
     * The (optional) origin of this source: possible values 'internal module', 'inlined content from source map', etc.
     */
    public String origin = null;

    /**
     * Optional data that a debug adapter might want to loop through the client. The client should leave the data intact and persist it across sessions. The client should not interpret the data.
     */
    public Object adapterData = null;

    /**
     * The checksums associated with this file.
     */
    public Checksum[] checksums;
}
