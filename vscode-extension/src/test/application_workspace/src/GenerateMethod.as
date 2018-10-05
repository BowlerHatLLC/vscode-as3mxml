package
{
	public class GenerateMethod
	{
		public function someFunction():void
		{
			myMethod();
			this.myMethod();
			myMethod(1, false, "hi");
			new MyMethod();
		}
	}
}

class FileInternalGenerateMethod
{
	public function someFunction():void
	{
		myMethod();
		this.myMethod();
		myMethod(1, false, "hi");
		new MyMethod();
	}
}