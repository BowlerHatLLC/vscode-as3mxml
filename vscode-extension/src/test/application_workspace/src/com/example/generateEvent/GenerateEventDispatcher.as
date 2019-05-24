package com.example.generateEvent
{
	[Event(name="literalWithMetadataButMissingMetadataClass")]
	[Event(name="literalWithMetadataAndClass",type="com.example.generateEvent.GenerateEventEvent")]
	[Event(name="constantWithMetadataButMissingMetadataClass")]
	[Event(name="constantWithMetadataAndClass",type="com.example.generateEvent.GenerateEventEvent")]
	public class GenerateEventDispatcher
	{
		public function addEventListener(type:String, listener:Function):void {}
	}
}