package
{
	import com.example.typeDefinition.MemberVarTypeDefinition;
	import com.example.typeDefinition.StaticVarTypeDefinition;
	import com.example.typeDefinition.LocalVarTypeDefinition;
	import com.example.typeDefinition.ParameterTypeDefinition;

	public class TypeDefinitions
	{
		public static var staticVar:StaticVarTypeDefinition;
		public var memberVar:MemberVarTypeDefinition;

		public function memberFunction(param:ParameterTypeDefinition):void
		{
			var localVar:LocalVarTypeDefinition;
		}
	}
}