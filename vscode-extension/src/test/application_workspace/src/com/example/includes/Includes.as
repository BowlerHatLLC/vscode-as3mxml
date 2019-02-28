package com.example.includes
{
	public class Includes
	{
		public function beforeIncludes():void {}

		include "scripts/include1.as";

		public function betweenIncludes():void {}

		include "scripts/include2.as";

		public function afterIncludes():void
		{
			firstInclude();
		}
	}
}