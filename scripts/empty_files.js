/**
 *	清空文本文件里的内容
 *	如果文件不存在，则创建一个空文件
 *
 *	示例: empty_file.js file1 file2 ...
 */

var fs = require("fs");

if (process.argv.length < 3) {
	console.log("At least input one file!")
	process.exit(0);
}

process.argv.slice(2).forEach(function(file) {
	fs.closeSync(fs.openSync(file, "w"));
});
