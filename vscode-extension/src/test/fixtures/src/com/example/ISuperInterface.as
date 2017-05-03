package com.example
{
	public interface ISuperInterface extends ISuperSuperInterface
	{
		function get superMemberProperty():Boolean;
		function set superMemberProperty(value:Boolean):void;
		function superMemberFunction():void;
	}
}