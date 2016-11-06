package
{
	import com.example.SuperDefinitions;
	import com.example.packageFunction;
	import com.example.packageVar;

	public class Definitions extends SuperDefinitions
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

		public function Definitions()
		{
			super();

			var localVar:String = "hello";
			function localFunction():void {}

			memberFunction();
			this.memberFunction();

			staticFunction();
			Definitions.staticFunction();

			staticVar = 4;
			Definitions.staticVar = 4;

			memberVar = true;
			this.memberVar = true;

			memberProperty = false;
			this.memberProperty = false;

			staticProperty = true;
			Definitions.staticProperty = true;

			superMemberFunction();
			this.superMemberFunction();
			super.superMemberFunction();

			superStaticFunction();
			SuperDefinitions.superStaticFunction();

			superMemberVar = "hi";
			this.superMemberVar = "hi";
			super.superMemberVar = "hi";

			superStaticVar = true;
			SuperDefinitions.superStaticVar = true;

			superMemberProperty = false;
			this.superMemberProperty = false;
			super.superMemberProperty = false;

			superStaticProperty = true;
			SuperDefinitions.superStaticProperty = true;

			packageFunction();
			com.example.packageFunction();

			packageVar = 3;
			com.example.packageVar = 3;

			localVar = "hi";

			localFunction();

			internalVar = 3;
			internalFunction();

			var internalDefinitionsInstance:InternalDefinitions = new InternalDefinitions();

			internalDefinitionsInstance.internalMemberFunction();
			internalDefinitionsInstance.internalMemberVar = true;
			internalDefinitionsInstance.internalMemberProperty = false;

			InternalDefinitions.internalStaticFunction();
			InternalDefinitions.internalStaticVar = 4;
			InternalDefinitions.internalStaticProperty = true;
		}
	}
}

function internalFunction():void {}
var internalVar:Number = 2;

class InternalDefinitions
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

	public function InternalDefinitions()
	{
	}
}