package
{
	import com.example.SuperDefinitions;
	import com.example.packageFunction;
	import com.example.packageVar;
	import com.example.IPackageInterface;

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
			var instance:FileInternalCompletion = new FileInternalCompletion();
			instance.;
			FileInternalCompletion.;
			var types:;
			ClassWithConstants.ONE;
			var instance2:IPackageInterface;
			instance2.;
		}
	}
}

function fileInternalFunction():void {}
var fileInternalVar:Number = 2;

class FileInternalCompletion
{
	public static var fileInternalStaticVar:Boolean = false;

	public static function get fileInternalStaticProperty():Boolean
	{
		return false;
	}

	public static function set fileInternalStaticProperty(value:Boolean):void
	{
	}

	public static function fileInternalStaticFunction():void
	{
	}

	public var fileInternalMemberVar:String;

	public function get fileInternalMemberProperty():Boolean
	{
		return false;
	}

	public function set fileInternalMemberProperty(value:Boolean):void
	{
	}

	public function fileInternalMemberFunction():void
	{
	}

	protected static var protectedStaticVar:Boolean = false;

	protected static function get protectedStaticProperty():Boolean
	{
		return false;
	}

	protected static function set protectedStaticProperty(value:Boolean):void
	{
	}

	protected static function protectedStaticFunction():void
	{
	}

	protected var protectedMemberVar:String;

	protected function get protectedMemberProperty():Boolean
	{
		return false;
	}

	protected function set protectedMemberProperty(value:Boolean):void
	{
	}

	protected function protectedMemberFunction():void
	{
	}

	public function FileInternalCompletion()
	{
	}
}