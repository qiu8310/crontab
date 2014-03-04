@echo off

schtasks /delete /tn "MyCrontabTask" /f

IF errorlevel 1 GOTO ERROR
IF errorlevel 0 GOTO SUCCESS

:ERROR
echo 您还没有安装，无须卸载！
GOTO END

:SUCCESS
echo 您已成功卸载！
GOTO END


:END
pause