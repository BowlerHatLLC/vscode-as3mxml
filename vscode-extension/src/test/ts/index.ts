/*
Copyright 2016-2020 Bowler Hat LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

	http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
import * as path from "path";
import * as glob from "glob";
import * as Mocha from "mocha";

export function run(): Promise<void>
{
	const mocha = new Mocha({ ui: "tdd", color: true });
	mocha.timeout(7500);

	const testsRoot = path.resolve(__dirname, "..");

	return new Promise((resolve, reject) =>
	{
		glob("**/**.test.js", { cwd: testsRoot }, (error, files) =>
		{
			if(error)
			{
				return reject(error);
			}

			// Add files to the test suite
			files.forEach(file => mocha.addFile(path.resolve(testsRoot, file)));

			try
			{
				// Run the mocha test
				mocha.run(failures =>
				{
					if(failures > 0)
					{
						reject(new Error(`${failures} tests failed.`));
					}
					else
					{
						resolve();
					}
				});
			}
			catch(err)
			{
				reject(err);
			}
		});
	});
}