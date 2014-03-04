# Windows 版 Crontab [JAVA]

>关于文件编码，由于需要结合 windows 系统的命令行，所以源文件和配置文件都是GBK编码的，请注意。

Crontab 是 Linux 的一个计划任务管理工具，你可以在那上面添加一些任务，在指定的时间让它在后台运行，经常用来定时清除系统或程序的缓存，可以定时执行任意的脚本等等，这些功能在 Linux 上都是非常有用的，尤其是当 Linux 做为服务器来用时。

##为什么 Windows 上没有一个呢

已经有人做了，像python版的 [pycron](http://www.kalab.com/freeware/pycron/pycron.htm) 和 Perl 版的 [cronw](http://cronw.sourceforge.net/)，它们和 Linux 系统上的 Crontab 功能基本一致，它们会在 Windows 注册一个系统服务来定时运行计划任务。它们忽略了两个 Windows 用户的重要特性：

1. Windows 不是服务器，经常需要关机，关机后计划任务就无效了，那我定制它还有什么用
2. Windows 上不像 Linux 那样有很多强大的命令程序，想写个计划任务，确没有好的工具；自己写个工具，又不好管理

*关于 Windows 上没有强大的命令程序：用户可以去安装一些工具，它会自带有很多 Linux 的命令程序，你只要将这些命令程序的目录写入你的环境变量 Path 中就行了，我所知道的带有 Linux 命令程序的软件有 [Git](http://git-scm.com/)、[Ruby](http://rubyinstaller.org/) 和 [unxutils](http://unxutils.sourceforge.net/)*

**本程序很巧妙的解决了上面两个问题  详情请继续往下看**

##功能简介

* 完全兼容 Linux 版的计划任务，格式 `分 时 日 月 周  命令`
* 支持多行写法，如果命令太长，需要用多行完成，可以在换行的时候未尾加上 "\"，
* 记录任务运行日志、错误日志，日志文件可以自由配置
* 有一个专门管理自己脚本的文件夹，里面的脚本可以直接使用在计划任务的 `命令` 中，可以不带路径参数
* **任务运行失败除了记录日志外，还可自动弹窗提醒用户，以防错过一些重要的命令**
* **如果关机了，任务本来应该运行但没得到运行，可以在开机的自动运行这些错过的任务（需要对任务进行配置，默认是不会的）**
* **自动记录你每天的开机时间，关机时间，空闲时间，形成图表显示，方便您了解自己的电脑使用习惯【正在开发中】**
* 更多功能还在开发中，敬请期待...


##安装


1. 安装 [JDK](http://docs.oracle.com/javase/7/docs/webnotes/install/)
2. 下载[nircmd](http://www.nirsoft.net/utils/nircmd.html)（它主要用来隐藏运行的命令界面），把它的可执行文件目录写入你的环境变量PATH中
3. 下载本程序代码【待公开】；
2. 将下载后的文件解压后放到你希望的一个目录下，比如我把它放在 C:\mylike 文件夹上
3. 修改 C:\mylike\Crontab-New\bin 下的三个 bat 文件，主要是修改里面的bin目录成你当前的bin目录
4. 直接双击运行 crontab\_install.bat（主要是在Windows上添加了一个[计划任务](http://www.microsoft.com/resources/documentation/windows/xp/all/proddocs/en-us/schtasks.mspx)），计划即启动完成。需要卸载的运行 crontab\_uninstall.bat就行， crontab.bat 是一个用户脚本，可以用来查看自己的计划任务，查看它的下次运行时间，同时还可以强制运行一个指定的任务


##使用

###配置schedule.conf

格式：

	[tags] * * * * * command

示例：

	# 每天晚上 11 点左右定时清空 crontab 的运行日志文件
	# $1 表示本项目的根目录
	# 注意： * */23 * * * => 每隔23个小时，这样 0 点会运行一次， 23 点又会运行一次
	30 22 * * * empty_files.js $1\log\crontab.log $1\log\crontab.err

	# 每 2 分钟访问一下某个链接
	# A 代表 Alert，30表示30秒，表示此任务如果运行失败会弹出一个弹窗，如果你不手动关闭，30秒后会自动关闭
	[A30]*/2 * * * * curl -s http://localhost/api.php?action=cleanup


	# 每 3 小时同步一次系统上的配置文件
	# P 代表 Poweroff，说明如果这个任务在关机期间本来应该运行，在下次开机后不管有没有到计划时间都会自动运行
	[P]* */3 * * * sync_files.js


	# 每隔两小时提醒自己去喝水
	[S]* */2 * * * alert.js 喝口水吧，顺便休息下眼睛

	# 父亲节（6月的第三个星期日）前两天提醒下自己
	# 多个 Tag 可以同时使用
	[A30P]* * 1-7 6 5 alert.js 父亲节要到了，准备下礼物吧
