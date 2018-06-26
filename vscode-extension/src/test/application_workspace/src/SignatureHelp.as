package
{
	import com.example.SuperSignatureHelp;
	import com.example.packageFunction;

	public class SignatureHelp extends SuperSignatureHelp
	{
		private static function staticFunction(one:String, two:Number = 3, ...rest:Array):Boolean
		{
			return false;
		}

		public function SignatureHelp()
		{
			function localFunction(one:String, two:Number = 3, ...rest:Array):Boolean
			{
				return false;
			};

			localFunction();
			memberFunction();
			this.memberFunction();
			staticFunction();
			SignatureHelp.staticFunction();
			packageFunction();
			com.example.packageFunction();
			internalFunction();
			super();
			super.superMemberMethod();
			super.privateSuperMemberMethod();
			new AfterPackageClass();
		}

		private function memberFunction(one:String, two:Number = 3, ...rest:Array):Boolean
		{
			return false;
		}
	}
}

function internalFunction(one:String, two:Number = 3, ...rest:Array):Boolean
{
	return false;
}

class AfterPackageClass
{
	public function AfterPackageClass(one:String, two:Number = 3, ...rest:Array)
	{

	}
}