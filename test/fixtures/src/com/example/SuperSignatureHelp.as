package com.example
{
	public class SuperSignatureHelp
	{
		public function SuperSignatureHelp(one:String, two:Number = 3)
		{
		}

		protected function superMemberMethod(one:String, two:Number = 3, ...rest:Array):Boolean
		{
			return false;
		}

		private function privateSuperMemberMethod(one:String, two:Number = 3, ...rest:Array):Boolean
		{
			return false;
		}
	}
}