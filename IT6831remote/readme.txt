
一、环境安装
依赖java环境，公司电脑已默认安装。
把rxtxParallel.dll和rxtxSerial.dll拷贝到java安装目录下jdk和jre的bin目录下。

二、命令说明
D:\IT6831.jar为IT6831.jar放置的路径，需改为使用时实际存放的路径。
COM4为控制电源对应的串口端口，使用时要改为实际的端口号。



设置电源输出指定电压（单位V）：
java -jar D:\IT6831.jar COM4 13.5

设置电源断电
java -jar D:\IT6831.jar COM4 off

设置电源上电
java -jar D:\IT6831.jar COM4 on

检测电流小于100毫安，超时时间1秒
java -jar D:\IT6831.jar COM4 less 100 1


检测电流大于1200毫安，超时时间10秒
java -jar D:\IT6831.jar COM4 more 1200 10
