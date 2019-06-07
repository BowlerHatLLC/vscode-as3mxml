package com.example.references
{
	public class References extends SuperReferences
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

		public function References()
		{
			super();

			var localVar:String = "hello";
			function localFunction():void {}

			memberFunction();
			this.memberFunction();

			staticFunction();
			References.staticFunction();

			staticVar = 4;
			References.staticVar = 4;

			memberVar = true;
			this.memberVar = true;

			memberProperty = false;
			this.memberProperty = false;

			staticProperty = true;
			References.staticProperty = true;

			superMemberFunction();
			this.superMemberFunction();
			super.superMemberFunction();

			superStaticFunction();
			SuperReferences.superStaticFunction();

			superMemberVar = "hi";
			this.superMemberVar = "hi";
			super.superMemberVar = "hi";

			superStaticVar = true;
			SuperReferences.superStaticVar = true;

			superMemberProperty = false;
			this.superMemberProperty = false;
			super.superMemberProperty = false;

			superStaticProperty = true;
			SuperReferences.superStaticProperty = true;

			referencesPackageFunction();
			com.example.references.referencesPackageFunction();

			referencesPackageVar = 3;
			com.example.references.referencesPackageVar = 3;

			localVar = "hi";

			localFunction();

			fileInternalVar = 3;
			fileInternalFunction();

			var internalReferencesInstance:FileInternalReferences = new FileInternalReferences();

			internalReferencesInstance.fileInternalMemberFunction();
			internalReferencesInstance.fileInternalMemberVar = "hi";
			internalReferencesInstance.fileInternalMemberProperty = false;

			FileInternalReferences.fileInternalStaticFunction();
			FileInternalReferences.fileInternalStaticVar = 4;
			FileInternalReferences.fileInternalStaticProperty = true;

			new SuperReferences();
		}
	}
}

function fileInternalFunction():void {}
var fileInternalVar:Number = 2;

class FileInternalReferences
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

	public function FileInternalReferences()
	{
	}
}