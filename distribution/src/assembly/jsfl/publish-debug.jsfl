/* CONFIG */

var buildProject = true;
var tempPath = getTempPath();
var isAS3 = detectVersion();

/* RUN */

cleanup();
setDebug(1);
build();
setDebug(0);
postBuild();


/* OPERATIONS */

function build()
{
	var version = parseInt(fl.version.split(" ")[1]);
	var proj = (buildProject && version < 10 && fl.getProject) ? fl.getProject() : null; 
	if (proj != null && proj.canTestProject()) 
	{ 
	   proj.publishProject();
	} 
	else 
	{
		var doc = fl.getDocumentDOM();
		if (doc == null) fl.trace("No documents open");
		else doc.publish();
	}
}

function postBuild()
{
	// log errors
	if (fl.compilerErrors)
	{
		fl.compilerErrors.save(tempPath + "AnimateErrors.log");		
	}
	var doc = fl.getDocumentDOM();
	if (doc)
	{
		// log path to FLA to indicate that the build is done
		FLfile.write(tempPath + "AnimateDocument.log", doc.path);
	}
}


/* TOOLS */

function cleanup()
{
	if (FLfile.exists(tempPath + "AnimateErrors.log"))
	{
		FLfile.remove(tempPath + "AnimateErrors.log");
	}
	if (FLfile.exists(tempPath + "AnimateDocument.log"))
	{
		FLfile.remove(tempPath + "AnimateDocument.log");
	}
}

function getTempPath()
{
	var file = fl.configURI;
	file = file.split("Adobe")[0] + "Adobe/vscode-as3mxml/";
	//ensure that the folder exists, or any attempt to create files will fail
	FLfile.createFolder(file);
	return file;
}

function setDebug(value)
{
	if (!isAS3) return;
	var doc = fl.getDocumentDOM();
	if (!doc || !doc.exportPublishProfileString) return;
	
	var config = XML(doc.exportPublishProfileString());
	config.PublishFlashProperties.DebuggingPermitted = value ? 1 : 0;
	doc.importPublishProfileString(config);
}

function detectVersion()
{
	var doc = fl.getDocumentDOM();
	var valid = (doc && doc.asVersion >= 3);
	if (!valid) runInFlashDevelop = false;
	return valid;
}

