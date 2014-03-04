/**
 *	经常换电脑，电脑里一些有用的文件就容易丢失，我写这个脚本就是为了把一些重要的文件同步到金山快盘中
 * 	金山快盘会定时自动将文件同步，所以这个脚本需要系统同时安装有金山快盘或其它类似的网盘
 *
 *	@TODO 利用一些网盘的API，同时要文件全自动化的同步到线上，而不要依赖于网盘的同步
 *
 *	@author qiuzhonglei
 *	@time 2014/2/20
 */

var fs = require("fs"),
	path = require("path"),
	CONFIG_FILE = "conf/backup_config_files.config.local";

process.chdir("D:/cloud/kuaipan/code/bin/java/crontab/tasks");

var configData = JSON.parse(
		fs.readFileSync(CONFIG_FILE, {flag: "r", encoding: "utf8"})
	);


// 遍历所有要同步的文件
var dest, srcs,				// 目标文件， 源文件集
	//syncCollection, 		// 所有同步的文件集合（不包括文件夹，文件夹在遍历的时候就创建好了）
	cwd = process.cwd();	// 当前目录

for (dest in configData) {
	srcs = configData[dest];

	// 获取绝对目标文件路径
	dest = path.resolve(cwd, dest);

	// 确保目标文件所在的文件夹存在
	mkdir_r(path.dirname(dest));

	// 链接文件不处理，所以下面用 lstat
	switch (Object.prototype.toString.call(srcs)) {
		// 源文件就是一个文件，目标文件是文件夹还是纯文件和源文件应该保持一致
		case "[object String]":
			file_to_file(srcs, dest);
			break;

		// 源文件是一批文件，那么目标文件肯定是一个文件夹
		case "[object Array]":
			mkdir_r(dest);
			srcs.forEach(function(src) {
				file_to_file(src, dest);
			});
			break;

		// 源文件是一批文件，同时还可能有些源文件需要被排除，目标文件也一定需要是一个文件夹
		case "[object Object]":
			var stat, ex = srcs.exclude;
			srcs.files.forEach(function(src) {
				exclude(src, dest, typeof ex === "string" ? [ex] : (ex || []));
			});
			break;
	}
}

/**
 *	排除某些文件
 */
function exclude(src, dest, rules) {
	var stat, destDir,
		basename = path.basename(src);

	stat = fs.lstatSync(src);
	if (stat.isDirectory()) {

		destDir = path.join(dest, basename);
		mkdir_r(destDir);

		fs.readdirSync(src).forEach(function(filename) {
			var file = path.join(src, filename);
			if (!file_match(file, rules)) {
				// 如果子文件是目录继续排除
				if (fs.lstatSync(file).isDirectory()) {
					exclude(file, destDir, rules);
				} else {
					file_copy(file, path.join(destDir, filename));
				}
			}
		});
	} else if (stat.isFile()) {
		if (!file_match(src, rules)) {
			file_copy(src, path.join(dest, basename));
		}
	}
}

/**
 *	判断文件是否和指定的规则集匹配
 */
function file_match(src, rules) {
	var i, rule, name = path.basename(src), start, notMatch = true;

	if (rules.length === 0) return notMatch;

	for (i=rules.length; i>0; --i) {
		rule = rules[i-1];
		if (rule === name) {
			notMatch = false;
			break;
		}

		if (rule.indexOf("*") >= 0) {
			start = 0;
			notMatch = false;
			rule.split("*").forEach(function(part) {
				if (part === "") return ;
				start = name.indexOf(part, start);
				if (start < 0) {
					notMatch = true;
				}
			});
			if (notMatch === false) break;;
		}
	}

	return !notMatch;
}


/**
 *	一个文件复制到另一个文件的策略
 * 	源文件肯定存在，但不确定它的文件类型
 * 	目标文件可能存在，也可能不存在，存在时它又可能是文本，又可能是文件夹
 * 	目标文件上一级的目录一定存在，这是确认的
 */
function file_to_file(src, dest) {
	if (fs.existsSync(src)) {
		var stat = fs.lstatSync(src);
		if (stat.isDirectory()) {
			mkdir_r(dest);
			dir_copy(src, dest);
		} else if (stat.isFile()) {
			try {
				stat = fs.lstatSync(dest);
				if (stat.isDirectory()) {
					file_copy(src, path.join(dest, path.basename(src)));
					return ;
				}
			} catch (e) {}

			file_copy(src, dest);
		}
	}
}

/**
 *	文件夹复制
 * 	将 src 文件夹里的内容全部复制到 dest 文件夹，并保持目录结构一致
 */
function dir_copy(src, dest) {
	var files = fs.readdirSync(src), stat;
	files.forEach(function(file) {

		srcFile = path.join(src, file);
		destFile = path.join(dest, file);

		stat = fs.lstatSync(srcFile);
		if (stat.isDirectory()) {
			mkdir_r(destFile);
			dir_copy(srcFile, destFile);
		} else if (stat.isFile()) {
			file_copy(srcFile, destFile);
		}
	});
}

/**
 *	文本文件的复制
 */
function file_copy(src, dest) {
	// 源文件需要比目标文件新才复制，否则没必要复制
	if (fs.existsSync(dest) && fs.lstatSync(src).mtime <= fs.lstatSync(dest).mtime) return ;

	var BUF_LENGTH = 64*1024, buff, fdr, fdw, bytesRead = 1, pos = 0;
	buff = new Buffer(BUF_LENGTH);
	fdr = fs.openSync(src, "r");
	fdw = fs.openSync(dest, "w");

	while (bytesRead > 0) {
		bytesRead = fs.readSync(fdr, buff, 0, BUF_LENGTH, pos);
		fs.writeSync(fdw, buff, 0, bytesRead);
		pos += bytesRead;
	}

	fs.closeSync(fdr);
	fs.closeSync(fdw);
}

/**
 *	创建目录，相当于 Linux 的 mkdir -r
 */
function mkdir_r(dir) {
	var stat, err;
	try {
		stat = fs.lstatSync(dir);
	} catch (e) {
		err = e;
	}
	if (err || !stat.isDirectory()) {
		mkdir_r(path.dirname(dir));
		fs.mkdirSync(dir);
	}
}
