package
{
	import com.example.SuperDefinitions;
	import com.example.packageFunction;
	import com.example.packageVar;

	public class Completion extends SuperDefinitions
	{
		public static var staticVar:Number = 2;

		private static function staticFunction():void
		{
		}

		private var memberVar:Boolean;

		private function memberFunction():void
		{
		}

		public function get memberProperty():Boolean
		{
			return false;
		}

		public function set memberProperty(value:Boolean):void
		{
		}

		public static function get staticProperty():Boolean
		{
			return false;
		}

		public static function set staticProperty(value:Boolean):void
		{
		}

		public function Completion()
		{
			super();

			var localVar:String = "hello";
			function localFunction():void {}

			;
			this.;
			super.;
			Completion.;
			SuperDefinitions.;
			com.example.;
			var instance:InternalCompletion = new InternalCompletion();
			instance.;
			InternalCompletion.;
			var types:;
		}
	}
}

function internalFunction():void {}
var internalVar:Number = 2;

class InternalCompletion
{
	public static var internalStaticVar:Boolean = false;

	public static function get internalStaticProperty():Boolean
	{
		return false;
	}

	public static function set internalStaticProperty(value:Boolean):void
	{
	}

	public static function internalStaticFunction():void
	{
	}

	public var internalMemberVar:String;

	public function get internalMemberProperty():Boolean
	{
		return false;
	}

	public function set internalMemberProperty(value:Boolean):void
	{
	}

	public function internalMemberFunction():void
	{
	}

	public function InternalCompletion()
	{
	}
}