package
{
	public class Main
	{
		private static var staticVar:Object; 
		public static const STATIC_CONST:int = 4;

		protected static function staticFunction():void
		{
		}

		public static function get staticProperty():Object
		{
			return null;
		}

		public function Main()
		{
			var localVar:Number = 2;
		}

		public var memberVar:String;

		private function memberFunction():void
		{
		}

		public function get memberProperty():Object
		{
			return null;
		}
	}
}

class MainInternalClass
{
	private var internalClassMemberVar:Number;
}