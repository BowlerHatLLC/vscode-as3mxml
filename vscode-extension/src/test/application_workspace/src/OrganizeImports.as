package
{
	import com.example.organizeImports.ImportToKeepInterface;
	import com.example.organizeImports.ImportToRemove;
	import com.example.organizeImports.ImportToKeepClass;
	import com.example.organizeImports.wildcards.*;

	public class OrganizeImports
	{
		public function OrganizeImports()
		{
			ImportToKeepClass;
			ImportToKeepInterface;
			ImportToAdd;
			var x:*;
			if(x is ImportToAddFromIsOperator)
			{

			}
			var y:* = x as ImportToAddFromAsOperator;
			function test():ImportToAddFromReturnType {return null;}
			y = ImportToAddFromCast(x);
			x = new ImportToAddFromNew();
			ImportFromWildcard;
		}
	}
}