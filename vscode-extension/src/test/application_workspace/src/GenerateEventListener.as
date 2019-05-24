package
{
	import com.example.generateEvent.GenerateEventDispatcher;
	import com.example.generateEvent.GenerateEventEvent;

	[Event(name="constantWithMetadataAndClass",type="com.example.generateEvent.GenerateEventEvent")]
	public class GenerateEventListener
	{
		public function addEventListener(type:String, listener:Function):void {}

		public function someFunction():void
		{
			var dispatcher:GenerateEventDispatcher = new GenerateEventDispatcher();
			dispatcher.addEventListener("literalWithoutMetadata", literalWithoutMetadataListener);
			dispatcher.addEventListener("literalWithMetadataButMissingMetadataClass", literalWithMetadataButMissingMetadataClassListener);
			dispatcher.addEventListener("literalWithMetadataAndClass", literalWithMetadataAndClassListener);
			dispatcher.addEventListener(GenerateEventEvent.CONSTANT_WITHOUT_METADATA, constantWithoutMetadataListener);
			dispatcher.addEventListener(GenerateEventEvent.CONSTANT_WITH_METADATA_BUT_MISSING_METADATA_CLASS, constantWithMetadataButMissingMetadataClassListener);
			dispatcher.addEventListener(GenerateEventEvent.CONSTANT_WITH_METADATA_AND_CLASS, constantWithMetadataAndClassListener);
			this.addEventListener(GenerateEventEvent.CONSTANT_WITH_METADATA_AND_CLASS, explicitThisListener);
			addEventListener(GenerateEventEvent.CONSTANT_WITH_METADATA_AND_CLASS, implicitThisListener);
			dispatcher.addEventListener(GenerateEventEvent.CONSTANT_WITH_METADATA_AND_CLASS, this.listenerWithMemberAccess);
			dispatcher.addEventListener(GenerateEventEvent.CONSTANT_WITH_METADATA_AND_CLASS, dispatcher.fakeListener);
			dispatcher.addEventListener(GenerateEventEvent.CONSTANT_WITH_METADATA_AND_CLASS, someFunction);
		}
	}
}

import com.example.generateEvent.GenerateEventDispatcher;
import com.example.generateEvent.GenerateEventEvent;

class FileInternalGenerateEventListener
{
	public function someFunction():void
	{
		var dispatcher:GenerateEventDispatcher = new GenerateEventDispatcher();
		dispatcher.addEventListener(GenerateEventEvent.CONSTANT_WITH_METADATA_AND_CLASS, constantWithMetadataAndClassListener);
	}
}