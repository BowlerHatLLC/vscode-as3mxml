export default function(): string
{
	if(process.platform === "win32")
	{
		return ";";
	}
	return ":";
}