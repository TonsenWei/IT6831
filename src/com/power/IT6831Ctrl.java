package com.power;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.TooManyListenersException;
import com.utils.DataEncode;
import com.utils.EmailUtils;
import com.utils.It6831;
import com.utils.MyUtils;
import com.utils.PropUtil;
import com.utils.SerialCommands;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;


/**
 * 用于自动化控制的脚本
 * @author uidq0460
 *
 */
public class IT6831Ctrl {
	public static final String LESS_CURRENT = "less";
	public static final String MORE_CURRENT = "more";
	
	//工作目录
	public static final String WORKSPACE_DIR = System.getProperty("user.dir");                //工作空间目录
	public static final String DEFAULT_RELAY_EXE_PATH = "\"" + WORKSPACE_DIR + "/USBRelay/DefaultUSBRelay.exe\"";
	
	//配置信息
	public static final String SETTING_PROP_FILE_PATH = WORKSPACE_DIR + "\\config.properties";//配置文件
	public static String powerCom = "COM5";
	public static String outputVoltage = "12.0";
	public static String currentCompare = "less";
	public static String targetOutputCurrent = "100";//单位毫安
	public static String timeout = "1";
	public static long timeoutL = 1000;     //超时时间的long型
	public static long targetCurrentL = 100;//单位毫安
	
	public static long realOutputCurrent = 0;
	
	public static boolean testPass = false;
	
	
	static It6831 power;
	static boolean keepGetStatus = true;	//是否保持检测电源状态
	public static String actualVoltage = "";
	public static String actualCurrent = "";
	
	//email相关
	public static String emailTitleSave = "Default Title";
	public static String projectName = "Default Project";
	public static String cksFileName = "DNL-5_firmware_usb.cks";
	public static String usbUpdateFileName = "DNL-5_firmware_usb.tar.gz";
	
	public static boolean isScpi = false; //是否是SCPI指令
	
	// 网络adb连接相关
	public static String ipPortStr = "192.168.0.2:5555";
	public static String delaySeconds = "3";
	public static String netAdbConnectTimeOutStr = "120";
	public static boolean netAdbConnected = false;// 网络adb是否已连接
	public static boolean netAdbOnline = false;   // adb devices命令检查到的是否在线（device状态，而非offline状态）
	public static String netAdbStatusStr = "";    // adb devices命令检查到的是否在线（device状态，而非offline状态）
	public static boolean isKeepWait = true;//是否继续等待命令执行结束
	private static boolean exceptionFail = false;
	private static boolean uiautomatorCaseFail = false;
	private static boolean assertionFailedError = false;
	private static boolean uiObjectNotFoundException = false;
	private static boolean uiautomatorCaseAborted = false;
	private static boolean uiautomatorCanNotConnect = false;

	public static void main(String[] args) {
		String scpiUsed = PropUtil.getValueOfProp("scpiUsed", SETTING_PROP_FILE_PATH);
		if (scpiUsed != null && !scpiUsed.equals("")) {//正常读取到数据，则判断值
			//是否使用SCPI
			if (scpiUsed.equalsIgnoreCase("true")) {
				isScpi = true;
			} else {
				isScpi = false;
			}
		} else {// 第一次打开程序或者读取异常
			PropUtil.setProperties(SETTING_PROP_FILE_PATH, "scpiUsed", "true", true);
			scpiUsed = "true";
			isScpi = true;
		}
		projectName = PropUtil.getValueOfProp("projectName", SETTING_PROP_FILE_PATH);
		if (projectName == null || projectName.equals("")) {
			projectName = "please set in config.properties,exam:projectName=IPU02";
		}
		emailTitleSave = PropUtil.getValueOfProp("EmailTitle", SETTING_PROP_FILE_PATH);
		if (emailTitleSave == null || emailTitleSave.equals("")) {
			emailTitleSave = "please set in config.properties,exam:EmailTitle=Title";
		}
		cksFileName = PropUtil.getValueOfProp("cksFileName", SETTING_PROP_FILE_PATH);
		if (cksFileName == null || cksFileName.equals("")) {
			cksFileName = "DNL-5_firmware_usb.cks";
		}
		usbUpdateFileName = PropUtil.getValueOfProp("usbUpdateFileName", SETTING_PROP_FILE_PATH);
		if (usbUpdateFileName == null || usbUpdateFileName.equals("")) {
			usbUpdateFileName = "DNL-5_firmware_usb.tar.gz";
		}
		MyUtils.printWithTimeMill("isScpi = " + isScpi);
//		String[] args = {"COM5", "more", "1200", "1"};

		//连接的ip和端口， 重连时间间隔，等待连接成功的超时时间
//		String[] args = {"192.168.0.2:5555", "3", "120"};
		for (int i = 0; i < args.length; i++) {
			MyUtils.printWithTimeMill("Arg" + i + ":" + args[i] + ",");
		}
		//email
		if (args.length == 1) {//只带一个参数，则以该参数为指令执行
			if (args[0].equalsIgnoreCase("email")) {
				sendReportEmail(getEmailContent("null"));
			} else {
				if (args[0].endsWith(".txt")) {
					serialExecCmdsFromFile(args[0]);
				}
			}
		} else if (args.length == 2) { // 带两个参数
			if (args[0] != null && args[0].equalsIgnoreCase("email")) {
				sendReportEmail(getEmailContent(args[1]));		
			} else if (args[0] != null && args[0].equalsIgnoreCase("UsbSwitchCopy")) {
				switchUSBAndCopyFile(args[1]);//目录
			} else {
				powerOutput(args[0], args[1]);
			}
		} else if (args.length == 4) {
			if (args[0].equalsIgnoreCase("email")) {
				List<String> attachments = new ArrayList<String>();
				for (int i = 2; i < args.length; i++) {
					attachments.add(args[i]);
				}
				sendReportEmail(getEmailContent(args[1]), attachments);
			} else if (args[0].startsWith("COM") && args[1].equalsIgnoreCase("wait")) {
				int exitCode = powerRebootAndWaitString(args[0], args[2], args[3]);// java -jar IT6800.jar COM5 wait "Startup complete" 30
				System.exit(exitCode);
			} else if (args[0].equalsIgnoreCase("serial") ) {
				int exitCode = sendSerialCmdAndWaitString(args[1], args[2], args[3]);//// java -jar IT6800.jar serial "ls" "diag_service" 30
				System.exit(exitCode);
			}else {
				powerCheckValue(args);
			}
		} else if (args.length == 3) {//String[] args = {"192.168.0.2:5555", "3", "120"};//连接的ip和端口， 重连时间间隔，等待连接成功的超时时间
			if (args[0] != null && args[0].equalsIgnoreCase("offline")) {
				waitNetAdbStatus(args[0], args[1], args[2]);
			} else if (args[0] != null && args[0].equalsIgnoreCase("device")) {
				waitNetAdbStatus(args[0], args[1], args[2]);
			}  else if (args[0] != null && args[0].equalsIgnoreCase("RebootUntilConnected")) { // 重启直到连接成功
				connectNetAdbPowerReboot(args[1], args[2]);//RebootUntilConnected， ip, 重启等待多长时间，（默认连接5次，adb connect 5次）
			} else if (args[0] != null && args[0].equalsIgnoreCase("email")) {
				List<String> attachments = new ArrayList<String>();
				attachments.add(args[2]);
				sendReportEmail(getEmailContent(args[1]), attachments);
			} else if (args[0] != null && args[0].equalsIgnoreCase("usbswitch")) {
				boolean isOk = switchUSB(args[1], args[2]);
				if (isOk == false) {
					System.exit(-1);
				}
			} else if (args[0] != null && args[0].equalsIgnoreCase("wait")) {
				int exitCode = sendSerialCmdAndWaitString(args[1], args[2]);// java -jar IT6800.jar wait "ls"  30
				System.exit(exitCode);
			} else {
				connectNetAdb(args[0], args[1], args[2]);
			}
		} else {
			if (args.length > 4) {
				if (args[0].equalsIgnoreCase("email")) {
					List<String> attachments = new ArrayList<String>();
					for (int i = 2; i < args.length; i++) {
						attachments.add(args[i]);
					}
					sendReportEmail(getEmailContent(args[1]), attachments);
				}
			} else {
				MyUtils.printWithTimeMill("test：" + args.length);
				connectNetAdbPowerReboot("192.168.0.2:5555", "30"); // RebootUntilConnected， ip, 重启等待多长时间，（默认连接5次，adb connect 5次）
			}
		}
	}
	
	public static void copyUpdateFile(String toFile) throws IOException {
		String cksFromFilePath = WORKSPACE_DIR + "\\" + cksFileName;
		String usbUpdateFromFilePath = WORKSPACE_DIR + "\\" + usbUpdateFileName;
		
		String cksToFilePath = toFile + "\\" + cksFileName;
		String usbUpdateToFilePath = toFile + "\\" + usbUpdateFileName;
		
		MyUtils.fileCopyByFileChannel(usbUpdateFromFilePath, usbUpdateToFilePath);
		MyUtils.fileCopyByFileChannel(cksFromFilePath, cksToFilePath);
	}
	
	/**
	 * releaseRelay = ToolsView.DEFAULT_RELAY_EXE_PATH + " close " + relaySelectStr;
	 * pressRelay = ToolsView.DEFAULT_RELAY_EXE_PATH + " open " + relaySelectStr;
	 * @throws Exception 
	 * */
	public static boolean switchUSB(String onOff, String fileDirPath) {
		boolean isOk = false;

		String pressRelay = DEFAULT_RELAY_EXE_PATH + " open 01";
		String releaseRelay = DEFAULT_RELAY_EXE_PATH + " close 01";
		try {
			File file = new File(fileDirPath);
			if (onOff.equalsIgnoreCase("on")) {
				if (file.exists()) {  // 本身已经是on，U盘已连接
					MyUtils.printWithTimeMill("USB have connected to PC");
					isOk = true;
				} else {  // 本身是off, 切换继电器,并检测usb连接上
					MyUtils.printWithTimeMill("USB have connected to PC");
					boolean exeRelayReault = MyUtils.excuteWinCmd(pressRelay);
					if (exeRelayReault == false) {
						try {
							throw new Exception("relay excute fail!");
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					Thread.sleep(100);
					exeRelayReault = MyUtils.excuteWinCmd(releaseRelay);
					if (exeRelayReault == false) {
						try {
							throw new Exception("relay excute fail!");
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					//wait String
					Thread.sleep(5000);
					MyUtils.printWithTimeMill("Waitting USB connect to PC...");
					boolean keepWait = true;
					long startWaitTime = System.currentTimeMillis();  	
					long currentMills = 0;
					while (keepWait == true && currentMills <= timeoutL) {
						currentMills = System.currentTimeMillis() - startWaitTime;
						if (file.exists()) {
							keepWait = false;
							currentMills = timeoutL + 1;
							isOk = true;
						} else {
							try {
								Thread.sleep(500);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
					}
				}
			} else if (onOff.equalsIgnoreCase("off")) {
				if (file.exists() == false) {  // 本身已经是off，U盘已连接
					MyUtils.printWithTimeMill("USB have disconnected to PC");
					isOk = true;
				} else {  // 本身是on, 切换继电器,并检测usb断开
					MyUtils.printWithTimeMill("USB have connected to PC");
					boolean exeRelayReault = MyUtils.excuteWinCmd(pressRelay);
					if (exeRelayReault == false) {
						try {
							throw new Exception("relay excute fail!");
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					Thread.sleep(100);
					exeRelayReault = MyUtils.excuteWinCmd(releaseRelay);
					if (exeRelayReault == false) {
						try {
							throw new Exception("relay excute fail!");
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					//wait String
					Thread.sleep(5000);
					MyUtils.printWithTimeMill("Waitting USB disconnect from PC...");
					boolean keepWait = true;
					long startWaitTime = System.currentTimeMillis();  	
					long currentMills = 0;
					while (keepWait == true && currentMills <= timeoutL) {
						currentMills = System.currentTimeMillis() - startWaitTime;
						if (file.exists()) {
							try {
								Thread.sleep(500);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						} else {
							keepWait = false;
							currentMills = timeoutL + 1;
							isOk = true;
							
						}
					}
				}
			} else {
				try {
					throw new Exception("input error: " + onOff);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (isOk == false) {
			System.out.println("error: switch USB fail");
		}
		return isOk;
	}
	
	/**
	 * releaseRelay = ToolsView.DEFAULT_RELAY_EXE_PATH + " close " + relaySelectStr;
	 * pressRelay = ToolsView.DEFAULT_RELAY_EXE_PATH + " open " + relaySelectStr;
	 * @throws Exception 
	 * */
	public static boolean switchUSBAndCopyFile(String fileDirPath) {
		boolean isOk = false;

		String pressRelay = DEFAULT_RELAY_EXE_PATH + " open 01";
		String releaseRelay = DEFAULT_RELAY_EXE_PATH + " close 01";
		try {
			File file = new File(fileDirPath);
			if (file.exists()) {  // 本身已经是on，U盘已连接
				MyUtils.printWithTimeMill("USB have connected to PC");
				isOk = true;
			} else {  // 本身是off, 切换继电器,并检测usb连接上
				MyUtils.printWithTimeMill("USB have connected to PC");
				boolean exeRelayReault = MyUtils.excuteWinCmd(pressRelay);
				if (exeRelayReault == false) {
					try {
						throw new Exception("relay excute fail!");
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				Thread.sleep(100);
				exeRelayReault = MyUtils.excuteWinCmd(releaseRelay);
				if (exeRelayReault == false) {
					try {
						throw new Exception("relay excute fail!");
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				//wait String
				MyUtils.printWithTimeMill("Waitting USB connect to PC...");
				boolean keepWait = true;
				long startWaitTime = System.currentTimeMillis();  	
				long currentMills = 0;
				while (keepWait == true && currentMills <= timeoutL) {
					currentMills = System.currentTimeMillis() - startWaitTime;
					if (file.exists()) {
						keepWait = false;
						currentMills = timeoutL + 1;
						isOk = true;
					} else {
						try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
			}
			Thread.sleep(3000);
			MyUtils.printWithTimeMill("Copy Update files to USB...");
			try {
				copyUpdateFile(fileDirPath);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Thread.sleep(3000);
			MyUtils.printWithTimeMill("Disconnect USB from PC...");
			// 拷贝完成，切换到
			MyUtils.excuteWinCmd(pressRelay);
			Thread.sleep(100);
			MyUtils.excuteWinCmd(releaseRelay);
			//wait String
			boolean keepWait = true;
			long startWaitTime = System.currentTimeMillis();
			long currentMills = 0;
			while (keepWait == true && currentMills <= timeoutL) {
				currentMills = System.currentTimeMillis() - startWaitTime;
				if (file.exists() == false) {
					keepWait = false;
					currentMills = timeoutL + 1;
					isOk = true;
				} else {
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}

			if (isOk == false) {
				System.out.println("error: switch fail, target file not exists!");
			} else {
				MyUtils.printWithTimeMill("USB have switch to qnx...");
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return isOk;
	}
	
	/**
	 * 等待字符串，不发送命令,串口端口从配置文件获取
	 * @param waitStr 等待的字符串
	 * @param timeoutStr 等待超时时间，单位秒
	 * @return
	 */
	public static int sendSerialCmdAndWaitString(String waitStr, String timeoutStr) {
		int isOk = -1;
		
		System.out.println("waitStr = " + waitStr + ", timeoutStr = " + timeoutStr);
		String deviceSerialCom = PropUtil.getValueOfProp("deviceSerialCom", SETTING_PROP_FILE_PATH);
		System.out.println("serialExecCmdsFromFile deviceSerialCom = " + deviceSerialCom);
		long timeoutL = 0;
		try {
			timeoutL = Long.parseLong(timeoutStr);
			timeoutL *= 1000;
		} catch (NumberFormatException e) {
			e.printStackTrace();
		}
		if (deviceSerialCom != null && !deviceSerialCom.equals("")) {//正常读取到数据，则判断值
			//是否使用SCPI
			SerialCommands serialCommands = new SerialCommands();
			try {
				serialCommands.initSerial(deviceSerialCom, "115200");
				serialCommands.setIsResultComeout(false);
				serialCommands.setWaitResultString(waitStr);
				
				//wait String
				boolean keepWait = true;
				long startWaitTime = System.currentTimeMillis();  	//用于计时每分钟输出一次提示
				long currentMills = 0;
				while (keepWait == true && currentMills <= timeoutL) {
					currentMills = System.currentTimeMillis() - startWaitTime;
					if (serialCommands.getIsResultComeout() == true) {
						keepWait = false;
						currentMills = timeoutL + 1;
						isOk = 0;
					} else {
						try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
				if (isOk != 0) {
					System.out.println("error: wait string timeout");
				}
			} catch (PortInUseException | UnsupportedCommOperationException e1) {
				System.out.println("error: " + e1.toString());
				e1.printStackTrace();
			} finally {
				SerialCommands.closeSerial();
			}
		} else {//第一次打开程序或者读取异常
			System.out.println("error: deviceSerialCom is null");
			isOk = -1;
		}
		
		return isOk;
	}
	
	/**
	 * 发送命令并等待字符串
	 * @param waitStr 等待的字符串
	 * @param timeoutStr 等待超时时间，单位秒
	 * @return
	 */
	public static int sendSerialCmdAndWaitString(String cmdStr, String waitStr, String timeoutStr) {
		int isOk = -1;
		
		System.out.println("cmdStr = " + cmdStr + ", waitStr = " + waitStr + ", timeoutStr = " + timeoutStr);
		String deviceSerialCom = PropUtil.getValueOfProp("deviceSerialCom", SETTING_PROP_FILE_PATH);
		System.out.println("serialExecCmdsFromFile deviceSerialCom = " + deviceSerialCom);
		long timeoutL = 0;
		try {
			timeoutL = Long.parseLong(timeoutStr);
			timeoutL *= 1000;
		} catch (NumberFormatException e) {
			e.printStackTrace();
		}
		if (deviceSerialCom != null && !deviceSerialCom.equals("")) {//正常读取到数据，则判断值
			//是否使用SCPI
			SerialCommands serialCommands = new SerialCommands();
			try {
				serialCommands.initSerial(deviceSerialCom, "115200");
				serialCommands.writeToSerialCom(cmdStr.trim() + "\r\n");
				try {
					Thread.sleep(500);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				serialCommands.setIsResultComeout(false);
				serialCommands.setWaitResultString(waitStr);
				
				serialCommands.writeToSerialCom(cmdStr.trim() + "\r\n");
				//wait String
				boolean keepWait = true;
				long startWaitTime = System.currentTimeMillis();  	//用于计时每分钟输出一次提示
				long currentMills = 0;
				while (keepWait == true && currentMills <= timeoutL) {
					currentMills = System.currentTimeMillis() - startWaitTime;
					if (serialCommands.getIsResultComeout() == true) {
						keepWait = false;
						currentMills = timeoutL + 1;
						isOk = 0;
					} else {
						try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
				if (isOk != 0) {
					System.out.println("error: wait string timeout");
				}
			} catch (PortInUseException | UnsupportedCommOperationException e1) {
				System.out.println("error: " + e1.toString());
				e1.printStackTrace();
			} finally {
				SerialCommands.closeSerial();
			}
		} else {//第一次打开程序或者读取异常
			System.out.println("error: deviceSerialCom is null");
			isOk = -1;
		}
		
		return isOk;
	}
	
	/**
	 * 等待字符串
	 * @param waitStr 等待的字符串
	 * @param timeoutStr 等待超时时间，单位秒
	 * @return
	 */
	public static int powerRebootAndWaitString(String comPower, String waitStr, String timeoutStr) {
		int isOk = -1;
		
		System.out.println("comPower = " + comPower + ", waitStr = " + waitStr + ", timeoutStr = " + timeoutStr);
		String deviceSerialCom = PropUtil.getValueOfProp("deviceSerialCom", SETTING_PROP_FILE_PATH);
		System.out.println("serialExecCmdsFromFile deviceSerialCom = " + deviceSerialCom);
		long timeoutL = 0;
		try {
			timeoutL = Long.parseLong(timeoutStr);
			timeoutL *= 1000;
		} catch (NumberFormatException e) {
			e.printStackTrace();
		}
		if (deviceSerialCom != null && !deviceSerialCom.equals("")) {//正常读取到数据，则判断值
			//是否使用SCPI
			SerialCommands serialCommands = new SerialCommands();
			try {
				serialCommands.initSerial(deviceSerialCom, "115200");
				serialCommands.setIsResultComeout(false);
				serialCommands.setWaitResultString(waitStr);
				//reboot power
				powerReboot(comPower);
				
				//wait String
				boolean keepWait = true;
				long startWaitTime = System.currentTimeMillis();  	//用于计时每分钟输出一次提示
				long currentMills = 0;
				while (keepWait == true && currentMills <= timeoutL) {
					currentMills = System.currentTimeMillis() - startWaitTime;
					if (serialCommands.getIsResultComeout() == true) {
						keepWait = false;
						currentMills = timeoutL + 1;
						isOk = 0;
					} else {
						try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
				if (isOk != 0) {
					System.out.println("error: wait string timeout");
				}
			} catch (PortInUseException | UnsupportedCommOperationException e1) {
				System.out.println("error: " + e1.toString());
				e1.printStackTrace();
			} finally {
				SerialCommands.closeSerial();
			}
		} else {//第一次打开程序或者读取异常
			System.out.println("error: deviceSerialCom is null");
			isOk = -1;
		}
		
		return isOk;
	}
	
	
	public static void powerCheckValue(String[] args) {
		powerCom = args[0];				//电源 com端口
		currentCompare = args[1];		//对比目标，less是如果实际值小于指定的值则正常，more是大于指定的值才正常，等于默认正常
		targetOutputCurrent = args[2];	//指定的值（单位A）,可以是小数
		timeout = args[3];				//超时时间，单位秒，可以是小数
		timeoutL = (long)(Float.parseFloat(timeout) * 1000);
		targetCurrentL = Long.parseLong(targetOutputCurrent);
		
		//初始化电源
		power = new It6831(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
		MyUtils.printWithTimeMill("Init " + powerCom);
		try {
			power.init(powerCom);
		} catch (NoSuchPortException | PortInUseException | UnsupportedCommOperationException
				| TooManyListenersException e) {
			e.printStackTrace();
		}
		checkValue(power);
	}
	

	public static void powerReboot(String com){
		powerCom = com;        // 电源端口
		
		power = new It6831(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
		MyUtils.printWithTimeMill("Init " + powerCom);
		try {
			power.init(powerCom);
		} catch (NoSuchPortException | PortInUseException | UnsupportedCommOperationException
				| TooManyListenersException e) {
			e.printStackTrace();
		}
		
		MyUtils.printWithTimeMill("Set to PC Ctrl");
		power.setCtrlMode(It6831.PC_MODE);
		power.selectChanel(1);//选择第一通道
		MyUtils.printWithTimeMill("Set power off");
		power.setOutput(It6831.POWER_OFF);
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		MyUtils.printWithTimeMill("Set power on");
		power.setOutput(It6831.POWER_ON);

		//测试结束关闭串口
		power.setCtrlMode(It6831.PANAL_MODE);
		power.close();
	}
	
	public static void powerOutput(String com, String volt){
		powerCom = com;        // 电源端口
		outputVoltage = volt;   // 输出电压
		
		power = new It6831(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
		MyUtils.printWithTimeMill("Init " + powerCom);
		try {
			power.init(powerCom);
		} catch (NoSuchPortException | PortInUseException | UnsupportedCommOperationException
				| TooManyListenersException e) {
			e.printStackTrace();
		}
		
		MyUtils.printWithTimeMill("Set to PC Ctrl");
		power.setCtrlMode(It6831.PC_MODE);
		power.selectChanel(1);//选择第一通道
		if (outputVoltage.equalsIgnoreCase("on")) {
			MyUtils.printWithTimeMill("Set power on");
			power.setOutput(It6831.POWER_ON);
		} else if (outputVoltage.equalsIgnoreCase("off")) {
			MyUtils.printWithTimeMill("Set power off");
			power.setOutput(It6831.POWER_OFF);
		} else {
			MyUtils.printWithTimeMill("Set Voltage:" + outputVoltage + "V");
			power.setOutputVoltage(outputVoltage);
		}
		//测试结束关闭串口
		power.setCtrlMode(It6831.PANAL_MODE);
		power.close();
	}
	
	
	public static int sendReportEmail(String emailBody, List<String> listAttachments) {
		int resultOk = 0;
		String emailSender = PropUtil.getValueOfProp("EmailSender", SETTING_PROP_FILE_PATH);
		String emailTo = PropUtil.getValueOfProp("EmailTo", SETTING_PROP_FILE_PATH);
		System.out.println("sendReportEmail emailSender = " + emailSender);
		System.out.println("sendReportEmail emailTo = " + emailTo);
		System.out.println("sendReportEmail emailTitle = " + emailTitleSave);
		if (emailSender != null && !emailSender.equals("") && emailTo != null && !emailTo.equals("")) {//正常读取到数据，读取要发送的邮件内容
	        String smtpHost = "mail.desay-svautomotive.com"; 	  	  // 邮件服务器
//	        String subject = "冒烟自动化测试报告";   // 主题 
	        if (emailTitleSave == null || emailTitleSave.equals("")) {
	        	emailTitleSave = "Email Title Not Set";
			}
	        EmailUtils email = EmailUtils.entity(smtpHost, emailSender, null, emailTo, null, emailTitleSave, emailBody, listAttachments);  
	        try {
				email.send();
			} catch (Exception e1) {
				e1.printStackTrace();
			}	// 发送！
		} else {// 第一次打开程序或者读取异常
			System.out.println("emailSender or emailTo is null");
			resultOk = -1;
		}
		return resultOk;
	}
	
	public static int sendReportEmail(String emailBody) {
		int resultOk = 0;
		String emailSender = PropUtil.getValueOfProp("EmailSender", SETTING_PROP_FILE_PATH);
		String emailTo = PropUtil.getValueOfProp("EmailTo", SETTING_PROP_FILE_PATH);
		System.out.println("sendReportEmail emailSender = " + emailSender);
		System.out.println("sendReportEmail emailTo = " + emailTo);
		System.out.println("sendReportEmail emailTitle = " + emailTitleSave);
		if (emailSender != null && !emailSender.equals("") && emailTo != null && !emailTo.equals("")) {//正常读取到数据，读取要发送的邮件内容
	        String smtpHost = "mail.desay-svautomotive.com"; 	  	  // 邮件服务器
	        if (emailTitleSave == null || emailTitleSave.equals("")) {
	        	emailTitleSave = "Email Title Not Set";
			}
	        EmailUtils email = EmailUtils.entity(smtpHost, emailSender, null, emailTo, null, emailTitleSave, emailBody, null);  
	        try {
				email.send();
			} catch (Exception e1) {
				e1.printStackTrace();
			}	// 发送！
		} else {// 第一次打开程序或者读取异常
			System.out.println("emailSender or emailTo is null");
			resultOk = -1;
		}
		return resultOk;
	}
	
	/**
	 * 拼接邮件内容
	 * 报告时间以执行发送邮件的时间为准
	 * @param reportPath 测试报告路径
	 * @return
	 */
	public static String getEmailContent(String reportPath) {
		StringBuilder builder = new StringBuilder();
		builder.append("<br />");
		//start table
		builder.append("<table border=\"1\">")
			.append("<tr>")
			.append("<td colspan=\"2\" bgcolor=\"DodgerBlue\" height=\"2\">")
			.append("<font size=\"4\" color=\"white\">")
			.append("<p align=\"center\">")
			.append(emailTitleSave)
			.append("<td>")
			.append("</tr>")
			//project name
			.append("<tr>")
			.append("<td>项目名称</td>")
			.append("<th><p align=\"left\">" + projectName + "</th>")
			.append("</tr>")
			//test time
			.append("<tr>")
			.append("<td>测试时间</td>")
			.append("<th><p align=\"left\">" + MyUtils.getNowTimeSpace() + "</th>")//邮件发送时间
			.append("</tr>")
			//report path
			.append("<tr>")
			.append("<td>报告路径</td>")
			.append("<td><A HREF=\" " + reportPath + " \">" + reportPath + "</A><br/></td>")
			.append("</tr>")
			// end table
			.append("</table>")
			;
		return builder.toString();
	}
	
	public static int serialExecCmdsFromFile(String filePath) {
		int resultOk = 0;
		String deviceSerialCom = PropUtil.getValueOfProp("deviceSerialCom", SETTING_PROP_FILE_PATH);
		System.out.println("serialExecCmdsFromFile deviceSerialCom = " + deviceSerialCom);
		if (deviceSerialCom != null && !deviceSerialCom.equals("")) {//正常读取到数据，则判断值
			//是否使用SCPI
			SerialCommands serialCommands = new SerialCommands();
			try {
				serialCommands.initSerial(deviceSerialCom, "115200");
				FileReader fr = null;
				BufferedReader br = null;
				try {
					fr = new FileReader(filePath);
					br = new BufferedReader(fr);
					String line;
					while ((line = br.readLine()) != null) { 
						System.out.println("serialExecCmdsFromFile< " + line);
						serialCommands.writeToSerialCom(line + "\r\n");
						Thread.sleep(2000);
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} catch (PortInUseException | UnsupportedCommOperationException e1) {
				e1.printStackTrace();
			} finally {
				SerialCommands.closeSerial();
			}
		} else {//第一次打开程序或者读取异常
			System.out.println("deviceSerialCom is null");
			resultOk = -1;
		}
		
		return resultOk;
	}
	/**
	 * 等待网络adb连接上（adb devices后的状态为device或offline）
	 * @param statusStr 状态，device,offline
	 * @param ipStr 待连接的ip，如192.168.0.2:5555
	 * @param timeoutStr 超时时间
	 */
	private static void excuteCmd(String cmdStr) {
		//指定时间
		uiautomatorCanNotConnect = false;
		uiautomatorCaseAborted = false;
		uiautomatorCaseFail = false;
		assertionFailedError = false;
		uiObjectNotFoundException = false;
		exceptionFail = false;
		try {
			MyUtils.printWithTimeMill("processCmd = " + cmdStr);
			Process processDeviceStatus = Runtime.getRuntime().exec(cmdStr);
			new Thread(new Runnable() {
				@Override
				public void run() {
					String line = "";
					try {
						BufferedReader statusInputStream = new BufferedReader(new InputStreamReader(processDeviceStatus.getInputStream(),"UTF-8"));
						try {
							while((line = statusInputStream.readLine()) != null) {
								String tempStr = line.trim();
								if (!tempStr.equals("")) {
									MyUtils.printWithTimeMill("normalStrem: " + line);
									if (tempStr.contains("INSTRUMENTATION_STATUS_CODE: -2")) {
										uiautomatorCaseFail = true;
									} else if (tempStr.contains("com.android.uiautomator.core.UiObjectNotFoundException:")) {
										uiObjectNotFoundException = true;
									} else if (tempStr.contains("INSTRUMENTATION_ABORTED:")) {
										uiautomatorCaseAborted = true;
									} else if (tempStr.contains("cannot connect to")) {
										uiautomatorCanNotConnect = true;
									} else if (tempStr.contains("junit.framework.AssertionFailedError")) {
										assertionFailedError = true;
									}
								}
							}
						} catch (IOException e) {
							exceptionFail = true;
							MyUtils.printWithTimeMill("IOException:" + e.toString());
						} finally {
							try {
								statusInputStream.close();
							} catch (IOException e) {
								exceptionFail = true;
								MyUtils.printWithTimeMill("IOException:" + e.toString());
							}
						}
					} catch (UnsupportedEncodingException e) {
						exceptionFail = true;
						MyUtils.printWithTimeMill("Exception:" + e.toString());
					}
				}
			}).start();
			
			new Thread(new Runnable() {
				@Override
				public void run() {
					String line = "";
					try {
						BufferedReader errorInputStream = new BufferedReader(new InputStreamReader(processDeviceStatus.getErrorStream(),"UTF-8"));
						try {
							while((line = errorInputStream.readLine()) != null) {
								if (!line.trim().equals("")) {
									MyUtils.printWithTimeMill("normalStrem: " + line);
									MyUtils.printWithTimeMill("errorStrem: " + line);
								}
							}
						} catch (IOException e) {
							exceptionFail = true;
							MyUtils.printWithTimeMill("IOException:" + e.toString());
						} finally {
							try {
								errorInputStream.close();
							} catch (IOException e) {
								exceptionFail = true;
								MyUtils.printWithTimeMill("IOException:" + e.toString());
							}
						}
					} catch (UnsupportedEncodingException e) {
						exceptionFail = true;
						MyUtils.printWithTimeMill("Exception:" + e.toString());
					}
				}
			}).start();
			
			
			int pStatusResult = processDeviceStatus.waitFor();
			MyUtils.printWithTimeMill("pResult deviceStatus = " + pStatusResult);
			if (pStatusResult == 0) {
				if (exceptionFail || uiautomatorCaseFail || uiObjectNotFoundException || uiautomatorCaseAborted || uiautomatorCanNotConnect || assertionFailedError) {
					System.exit(-1);
				}
			}
			System.exit(pStatusResult);
			
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 等待网络adb连接上（adb devices后的状态为device或offline）
	 * @param statusStr 状态，device,offline
	 * @param ipStr 待连接的ip，如192.168.0.2:5555
	 * @param timeoutStr 超时时间
	 */
	private static void waitNetAdbStatus(String statusStr, String ipStr, String timeoutStr) {
		String cmdDeviceStatusStr = "adb devices";
		
		long timeoutL = Long.parseLong(timeoutStr) * 1000;
		//指定时间
		long startWaitTime = System.currentTimeMillis();//发送命令后开始计时
		long currentMills = 0;
		while(currentMills <= timeoutL) {
			try {
				MyUtils.printWithTimeMill("processCmd = " + cmdDeviceStatusStr);
				Process processDeviceStatus = Runtime.getRuntime().exec(cmdDeviceStatusStr);
				new Thread(new Runnable() {
					@Override
					public void run() {
						String line = "";
						try {
							BufferedReader statusInputStream = new BufferedReader(new InputStreamReader(processDeviceStatus.getInputStream(),"UTF-8"));
							try {
								while((line = statusInputStream.readLine()) != null) {
									if (!line.trim().equals("")) {
										MyUtils.printWithTimeMill("normalStrem: " + line);
										if (line.contains(ipStr)) {//连接的ip,
											String[] tt = line.split("\\s+");//多个空格分隔
											netAdbStatusStr = tt[1].trim();
										} 
									}
								}
							} catch (IOException e) {
								MyUtils.printWithTimeMill("IOException:" + e.toString());
							} finally {
								try {
									statusInputStream.close();
								} catch (IOException e) {
									MyUtils.printWithTimeMill("IOException:" + e.toString());
								}
							}
						} catch (UnsupportedEncodingException e) {
							MyUtils.printWithTimeMill("Exception:" + e.toString());
						}
					}
				}).start();
				
				new Thread(new Runnable() {
					@Override
					public void run() {
						String line = "";
						try {
							BufferedReader errorInputStream = new BufferedReader(new InputStreamReader(processDeviceStatus.getErrorStream(),"UTF-8"));
							try {
								while((line = errorInputStream.readLine()) != null) {
									if (!line.trim().equals("")) {
										MyUtils.printWithTimeMill("errorStrem: " + line);
									}
								}
							} catch (IOException e) {
								MyUtils.printWithTimeMill("IOException:" + e.toString());
							} finally {
								try {
									errorInputStream.close();
								} catch (IOException e) {
									MyUtils.printWithTimeMill("IOException:" + e.toString());
								}
							}
						} catch (UnsupportedEncodingException e) {
							MyUtils.printWithTimeMill("Exception:" + e.toString());
						}
					}
				}).start();
				
				
				int pStatusResult = processDeviceStatus.waitFor();
				MyUtils.printWithTimeMill("pResult deviceStatus = " + pStatusResult);
				
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
			
			currentMills = System.currentTimeMillis() - startWaitTime;
			if (netAdbStatusStr.equalsIgnoreCase(statusStr)) {//已达到目标，退出测试
				currentMills = timeoutL + 1;
				break;
			} else {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					MyUtils.printWithTimeMill(e.toString());
					e.printStackTrace();
				}
			}
		}
		if (netAdbStatusStr.equalsIgnoreCase(statusStr)) {//已达到目标，退出测试
			MyUtils.printWithTimeMill("connectNetAdb PASS: " + statusStr);
		} else {
			MyUtils.printWithTimeMill("connectNetAdb fail: " + netAdbStatusStr + ", target:" + statusStr);
			System.exit(-1);
		}
	}
	
	/**
	 * 连接网络adb,
	 * @param ipStr IP地址，包括端口号，如：192.168.0.2:5555
	 * @param delaySecond 每次连接后等待下一次连接的时间间隔
	 * @param timeoutStr 等待连接成功的超时时间
	 */
	private static void connectNetAdb(String ipStr, String delaySecond, String timeoutStr) {
		String cmdStr = "adb connect " + ipStr;
		String cmdDeviceStatusStr = "adb devices";
		
		long delayTimeL = Long.parseLong(delaySecond);
		long waitTime = delayTimeL * 1000;//换算为毫秒
		
		long timeoutL = Long.parseLong(timeoutStr) * 1000;
		//指定时间
		long startWaitTime = System.currentTimeMillis();//发送命令后开始计时
		long currentMills = 0;
		while(currentMills <= timeoutL) {
			try {
				netAdbConnected = false;
				MyUtils.printWithTimeMill("processCmd = " + cmdStr);
				Process process = Runtime.getRuntime().exec(cmdStr);
				new Thread(new Runnable() {
					@Override
					public void run() {
						String line = "";
						try {
							BufferedReader adbCmdInputStream = new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"));
							try {
								while((line = adbCmdInputStream.readLine()) != null && isKeepWait == true) {
									if (!line.trim().equals("")) {
										MyUtils.printWithTimeMill("normalStrem: " + line);
										if (line.contains("cannot connect to")) {
											netAdbConnected = false;
										} else if (line.contains("already connected to " + ipStr)) {
											netAdbConnected = true;
											isKeepWait = false;
										} else if (line.contains("connected to " + ipStr)) {
											netAdbConnected = true;
											isKeepWait = false;
										}
									}
								}
							} catch (IOException e) {
								MyUtils.printWithTimeMill("IOException:" + e.toString());
							} finally {
								try {
									adbCmdInputStream.close();
								} catch (IOException e) {
									MyUtils.printWithTimeMill("IOException:" + e.toString());
								}
							}
						} catch (UnsupportedEncodingException e) {
							MyUtils.printWithTimeMill("Exception:" + e.toString());
						}
					}
				}).start();
				
				new Thread(new Runnable() {
					@Override
					public void run() {
						String line = "";
						try {
							BufferedReader errorInputStream = new BufferedReader(new InputStreamReader(process.getErrorStream(),"UTF-8"));
							try {
								while((line = errorInputStream.readLine()) != null && isKeepWait == true) {
									if (!line.trim().equals("")) {
										MyUtils.printWithTimeMill("errorStrem: " + line);
									}
								}
							} catch (IOException e) {
								MyUtils.printWithTimeMill("IOException:" + e.toString());
							} finally {
								try {
									errorInputStream.close();
								} catch (IOException e) {
									MyUtils.printWithTimeMill("IOException:" + e.toString());
								}
							}
						} catch (UnsupportedEncodingException e) {
							MyUtils.printWithTimeMill("Exception:" + e.toString());
						}
					}
				}).start();
				
				int pResult = process.waitFor();
				MyUtils.printWithTimeMill("pResult connectNetAdb = " + pResult);
				if (netAdbConnected == true) {//网络adb已连接上，检查状态
					netAdbOnline = false;
					isKeepWait = true;
					
					MyUtils.printWithTimeMill("processCmd = " + cmdDeviceStatusStr);
					Process processDeviceStatus = Runtime.getRuntime().exec(cmdDeviceStatusStr);
					new Thread(new Runnable() {
						@Override
						public void run() {
							String line = "";
							try {
								BufferedReader statusInputStream = new BufferedReader(new InputStreamReader(processDeviceStatus.getInputStream(),"UTF-8"));
								try {
									while((line = statusInputStream.readLine()) != null && isKeepWait == true) {
										if (line.trim().equals("")) {
											MyUtils.printWithTimeMill("normalStrem: " + line);
											if (line.contains(ipStr)) {//连接的ip,
												String[] tt = line.split("\\s+");//多个空格分隔
												if (tt[1].trim().equals("device")) {
													netAdbOnline = true;
													isKeepWait = false;//已经连接成功且在线，则退出
												} else {
													netAdbOnline = false;
												}
											} 
										}
									}
								} catch (IOException e) {
									MyUtils.printWithTimeMill("IOException:" + e.toString());
								} finally {
									try {
										statusInputStream.close();
									} catch (IOException e) {
										MyUtils.printWithTimeMill("IOException:" + e.toString());
									}
								}
							} catch (UnsupportedEncodingException e) {
								MyUtils.printWithTimeMill("Exception:" + e.toString());
							}
						}
					}).start();
					new Thread(new Runnable() {
						@Override
						public void run() {
							String line = "";
							try {
								BufferedReader errorInputStream = new BufferedReader(new InputStreamReader(processDeviceStatus.getErrorStream(),"UTF-8"));
								try {
									while((line = errorInputStream.readLine()) != null && isKeepWait == true) {
										if (!line.trim().equals("")) {
											MyUtils.printWithTimeMill("errorStrem: " + line);
										}
									}
								} catch (IOException e) {
									MyUtils.printWithTimeMill("IOException:" + e.toString());
								} finally {
									try {
										errorInputStream.close();
									} catch (IOException e) {
										MyUtils.printWithTimeMill("IOException:" + e.toString());
									}
								}
							} catch (UnsupportedEncodingException e) {
								MyUtils.printWithTimeMill("Exception:" + e.toString());
							}
						}
					}).start();
					
					int pStatusResult = process.waitFor();
					MyUtils.printWithTimeMill("pResult deviceStatus = " + pStatusResult);
				}
				
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
			try {
				MyUtils.printWithTimeMill("waitSecond = " + waitTime);
				Thread.sleep(waitTime);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			currentMills = System.currentTimeMillis() - startWaitTime;
			if (netAdbOnline == true) {//已达到目标，退出测试
				currentMills = timeoutL + 1;
				break;
			}
		}
		
		if (netAdbOnline != true) {
			System.exit(-1);
		} else {
			MyUtils.printWithTimeMill("connectNetAdb PASS");
		}
	}
	
	/**
	 * 连接网络adb,连接120秒后连接失败则重启再尝试连接，直到连接上或超时时间到
	 * @param ipStr IP地址，包括端口号，如：192.168.0.2:5555
	 * @param delaySecond 每次连接后等待下一次连接的时间间隔
	 * @param timeoutStr 等待连接成功的超时时间
	 */
	private static void connectNetAdbPowerReboot(String ipStr, String timeoutStr) {
		String cmdStr = "adb connect " + ipStr;
		String cmdDeviceStatusStr = "adb devices";
		
		long timeoutL = Long.parseLong(timeoutStr) * 1000;
		//指定时间
		long startWaitTime = System.currentTimeMillis();//发送命令后开始计时
		long currentMills = 0;
		int tryConnectCounter = 0;
		while(currentMills <= timeoutL && isKeepWait == true) {
			tryConnectCounter ++;
			if (tryConnectCounter < 5 && netAdbOnline == false) {
				try {
					netAdbConnected = false;
					MyUtils.printWithTimeMill("processCmd = " + cmdStr);
					Process process = Runtime.getRuntime().exec(cmdStr);
					new Thread(new Runnable() {
						@Override
						public void run() {
							String line = "";
							try {
								BufferedReader adbCmdInputStream = new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"));
								try {
									while((line = adbCmdInputStream.readLine()) != null && isKeepWait == true) {
										MyUtils.printWithTimeMill("normalStrem: " + line);
										if (!line.trim().equals("")) {
											if (line.contains("cannot connect to")) {
												netAdbConnected = false;
											} else if (line.contains("already connected to " + ipStr)) {
												netAdbConnected = true;
												isKeepWait = false;
											} else if (line.contains("connected to " + ipStr)) {
												netAdbConnected = true;
												isKeepWait = false;
											}
										}
									}
								} catch (IOException e) {
									MyUtils.printWithTimeMill("IOException:" + e.toString());
								} finally {
									try {
										adbCmdInputStream.close();
									} catch (IOException e) {
										MyUtils.printWithTimeMill("IOException:" + e.toString());
									}
								}
							} catch (UnsupportedEncodingException e) {
								MyUtils.printWithTimeMill("Exception:" + e.toString());
							}
						}
					}).start();
					
					new Thread(new Runnable() {
						@Override
						public void run() {
							String line = "";
							try {
								BufferedReader errorInputStream = new BufferedReader(new InputStreamReader(process.getErrorStream(),"UTF-8"));
								try {
									while((line = errorInputStream.readLine()) != null && isKeepWait == true) {
										if (!line.trim().equals("")) {
											MyUtils.printWithTimeMill("errorStrem: " + line);
										}
									}
								} catch (IOException e) {
									MyUtils.printWithTimeMill("IOException:" + e.toString());
								} finally {
									try {
										errorInputStream.close();
									} catch (IOException e) {
										MyUtils.printWithTimeMill("IOException:" + e.toString());
									}
								}
							} catch (UnsupportedEncodingException e) {
								MyUtils.printWithTimeMill("Exception:" + e.toString());
							}
						}
					}).start();
					
					int pResult = process.waitFor();
					MyUtils.printWithTimeMill("pResult connectNetAdb = " + pResult);
					if (netAdbConnected == true) {//网络adb已连接上，检查状态
						MyUtils.printWithTimeMill("device 192.168.0.2:5555 connected = " + netAdbConnected);
						netAdbOnline = false;
						isKeepWait = true;
						
						MyUtils.printWithTimeMill("processCmd = " + cmdDeviceStatusStr);
						Process processDeviceStatus = Runtime.getRuntime().exec(cmdDeviceStatusStr);
						String line = "";
						try {
							BufferedReader statusInputStream = new BufferedReader(new InputStreamReader(processDeviceStatus.getInputStream(),"UTF-8"));
							try {
								while((line = statusInputStream.readLine()) != null && isKeepWait == true) {
									MyUtils.printWithTimeMill("normalStrem: " + line);
									if (!line.trim().equals("")) {
										if (line.contains(ipStr)) {//连接的ip,
											String[] tt = line.split("\\s+");//多个空格分隔
											if (tt[1].trim().equals("device")) {
												netAdbOnline = true;
												isKeepWait = false;//已经连接成功且在线，则退出
												MyUtils.printWithTimeMill("devices is online(device):" + ipStr);
											} else {
												netAdbOnline = false;
											}
										} 
									}
								}
							} catch (IOException e) {
								MyUtils.printWithTimeMill("IOException:" + e.toString());
							} finally {
								try {
									statusInputStream.close();
								} catch (IOException e) {
									MyUtils.printWithTimeMill("IOException:" + e.toString());
								}
							}
						} catch (UnsupportedEncodingException e) {
							MyUtils.printWithTimeMill("Exception:" + e.toString());
						}
//						new Thread(new Runnable() {
//							@Override
//							public void run() {
//								
//							}
//						}).start();
						new Thread(new Runnable() {
							@Override
							public void run() {
								String line = "";
								try {
									BufferedReader errorInputStream = new BufferedReader(new InputStreamReader(processDeviceStatus.getErrorStream(),"UTF-8"));
									try {
										while((line = errorInputStream.readLine()) != null && isKeepWait == true) {
											if (!line.trim().equals("")) {
												MyUtils.printWithTimeMill("errorStrem: " + line);
											}
										}
									} catch (IOException e) {
										MyUtils.printWithTimeMill("IOException:" + e.toString());
									} finally {
										try {
											errorInputStream.close();
										} catch (IOException e) {
											MyUtils.printWithTimeMill("IOException:" + e.toString());
										}
									}
								} catch (UnsupportedEncodingException e) {
									MyUtils.printWithTimeMill("Exception:" + e.toString());
								}
							}
						}).start();
						
						int pStatusResult = process.waitFor();
						MyUtils.printWithTimeMill("pResult deviceStatus = " + pStatusResult);
					}
					
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				}
				
				currentMills = System.currentTimeMillis() - startWaitTime;
				if (netAdbOnline == true) {//已达到目标，退出测试
					currentMills = timeoutL + 1;
					break;
				}
				
			} else if (tryConnectCounter >= 5) {
				tryConnectCounter = 0;
				power = new It6831(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
				MyUtils.printWithTimeMill("Init " + powerCom);
				try {
					power.init(powerCom);
				} catch (NoSuchPortException | PortInUseException | UnsupportedCommOperationException
						| TooManyListenersException e) {
					e.printStackTrace();
				}
				
				MyUtils.printWithTimeMill("Set to PC Ctrl");
				power.setCtrlMode(It6831.PC_MODE);
				MyUtils.printWithTimeMill("Set power off");
				power.setOutput(It6831.POWER_OFF);
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				MyUtils.printWithTimeMill("Set power on");
				power.setOutput(It6831.POWER_ON);
				MyUtils.printWithTimeMill("Set power voltage=13.5");
				power.setOutputVoltage("13.5");
				
				//测试结束关闭串口
				power.setCtrlMode(It6831.PANAL_MODE);
				power.close();
				try {
					Thread.sleep(180000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		if (netAdbOnline != true) {
			System.exit(-1);
		} else {
			MyUtils.printWithTimeMill("connectNetAdb PASS");
		}
	}
	
	/**
	 * 获取电源状态
	 * @param it6831Power
	 */
	private static void checkValue(It6831 it6831Power) {
		String returnStr = "";
		String verifyStr = "";
		String andCodeStr = "";
		
		testPass = false;
		
		long startCheckTime = System.currentTimeMillis();
		long currentCountTime = System.currentTimeMillis();
		
		while(keepGetStatus && isScpi == false) {
			if (it6831Power != null) {
				returnStr = it6831Power.sentGetPowerStatusCmd();
			} else {
				returnStr = "";
			}
			if (!returnStr.equals("") && returnStr.length()>=52) {
				verifyStr = returnStr.substring(0, 50);
				andCodeStr = DataEncode.getVerifyCode(verifyStr);
				if (returnStr.substring(50, 52).equals(andCodeStr)) {
					//读取电流
					String currCmd = returnStr.substring(6, 10);
					String realCurrStr = DataEncode.switchCode(currCmd);
					long currLong = Long.parseLong(realCurrStr, 16);
					double curr = ((double)currLong)/1000;
					String currStr = String.format("%.2f", curr).toString();
					//读取电压
					String voltCmd = returnStr.substring(10, 14);
					String realVoltStr = DataEncode.switchCode(voltCmd);
					long voltLong = Long.parseLong(realVoltStr, 16);
					double volt = ((double)voltLong)/1000;
					String voltStr = String.format("%.2f", volt).toString();
					
					double powerW = volt*curr;
					String powerWStr = String.format("%.2f", powerW).toString();
					
					if (voltStr != null && currStr != null && !voltStr.equals("") && !currStr.equals("")) {
						actualCurrent = currStr;
						actualVoltage = voltStr;
						if (LESS_CURRENT.equalsIgnoreCase(currentCompare)) {//期望小于指定值
							if (currLong <= targetCurrentL) {//符合则测试通过
								MyUtils.printWithTimeMill("Voltage:" + voltStr + "V, Current:" + currStr + "A, Power:" + powerWStr + "W");
								keepGetStatus = false; //已收到电压电流信息，退出测试
								testPass = true;
								realOutputCurrent = currLong;
							}
						} else if (MORE_CURRENT.equalsIgnoreCase(currentCompare)) {//期望大于指定值
							if (currLong > targetCurrentL) {//符合则测试通过
								MyUtils.printWithTimeMill("Voltage:" + voltStr + "V, Current:" + currStr + "A, Power:" + powerWStr + "W");
								keepGetStatus = false; //已收到电压电流信息，退出测试
								testPass = true;
								realOutputCurrent = currLong;
							}
						} else {
							power.close();
							MyUtils.printWithTimeMill("ErrorArgs(Target)：" + currentCompare);
							System.exit(-1);
						}
					}
					
				}
			}
			currentCountTime = System.currentTimeMillis() - startCheckTime;
			if (currentCountTime >= timeoutL && testPass == false) {//超时了，报错
				MyUtils.printWithTimeMill("检测超时时间：" + timeout + "秒");
				power.close();
				System.exit(-1);
			} else if (currentCountTime < timeoutL && testPass == false) {//时间没到，也没有检测到结果，曾等待50秒后继续检测
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					MyUtils.printWithTimeMill("getPowerStatus sleep InterruptedException");
				}
			} else {//测试结果出现
				MyUtils.printWithTimeMill("CheckPass:" + realOutputCurrent + "mA, Target:" + targetOutputCurrent + "mA");
				keepGetStatus = false; //已收到电压电流信息，退出测试
			}
		}
		power.close();
	}

}
