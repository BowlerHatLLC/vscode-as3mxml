package com.example.references
{
	public class SuperReferences
	{
		public static var superStaticVar:Boolean = false;

		public static function get superStaticProperty():Boolean
		{
			return false;
		}

		public static function set superStaticProperty(value:Boolean):void
		{
		}

		protected static function superStaticFunction():void
		{
		}

		public var superMemberVar:String;

		public function get superMemberProperty():Boolean
		{
			return false;
		}

		public function set superMemberProperty(value:Boolean):void
		{
		}

		protected function superMemberFunction():void
		{
		}

		public function SuperReferences()
		{
		}
	}
}