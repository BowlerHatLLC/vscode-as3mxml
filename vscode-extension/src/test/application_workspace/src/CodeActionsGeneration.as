package
{
	public class CodeActionsGeneration
	{
		public function CodeActionsGeneration()
		{
			variableWithoutThis = 2;
			this.variableWithThis = "hi";

			methodWithoutThis("hi", 2);
			this.methodWithThis(3);
		}

		protected var getterAndSetter:String = "getAndSet";
		private static var staticGetterAndSetter:Number;
	}
}