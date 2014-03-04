@ECHO OFF
schtasks /create /tn "MyCrontabTask" /tr "nircmd.exe exec hide java -cp D:\cloud\kuaipan\code\bin\java\Crontab-New\bin com.main.Crontab -r" /sc MINUTE /mo 1

pause