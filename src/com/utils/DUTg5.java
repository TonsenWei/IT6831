package com.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.TooManyListenersException;

import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

public class DUTg5 {

	private SerialUtils serialUtils;
	private String deviceSerialLogPathStr = System.getProperty("user.dir") + "\\DeviceSerialLog.txt";//log保存路径
	private boolean stringComeout = false; 
//	private String bootOkString = "init: Boot Animation exit";
	private String checkString = "default";
	
	public DUTg5() {
		this.serialUtils = new SerialUtils(115200, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
	}
	
	public DUTg5(int baudRate, int databits, int stopbits, int parity) {
		this.serialUtils = new SerialUtils(baudRate, databits, stopbits, parity);
	}
	
	/**
	 * 设置log保存路径
	 * @param dirStr 目录
	 * @param logNameStr 文件名（包含扩展名）
	 */
	public void setLogPath(String dirStr, String logNameStr) {
		//创建日志文件夹
		File serialDirFile = new File(dirStr);
		if (!serialDirFile.exists()) {//创建目录
			serialDirFile.mkdirs();
		}
		deviceSerialLogPathStr = dirStr + "\\" + logNameStr;
	}
	
	
	/**
	 * 初始化日志文件和对应串口
	 * @param comStr
	 */
	public void init(String comStr) {
		File deviceSerialLogFile = new File(deviceSerialLogPathStr);
		if (deviceSerialLogFile.exists()) {
			deviceSerialLogFile.delete();//删除旧日志文件
		}
		try {
			this.serialUtils.connectPort(comStr);
			this.serialUtils.getSerialPort().addEventListener(new SerialPortEventListener() {
				@Override
				public void serialEvent(SerialPortEvent ev) {
					if (ev.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
						byte[] receiveDataPackage = new byte[4096];
						try {
							FileWriter serialLogFw = new FileWriter(deviceSerialLogPathStr, true);
							BufferedWriter serialLogBw = new BufferedWriter(serialLogFw);
							
							while(serialUtils.getSerialPort().getInputStream().available() > 0)
							{
								serialUtils.getSerialPort().getInputStream().read(receiveDataPackage);
								String receiveStr = new String(receiveDataPackage).trim();
								serialLogBw.append(receiveStr + "\r\n");
								if (receiveStr.contains(getCheckString())) {
									setStringComeout(true);
									System.out.println("setStringComeout");
								}
							}
							serialLogBw.close();
		            		serialLogFw.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			});
			serialUtils.getSerialPort().notifyOnDataAvailable(true);
			
		} catch (NoSuchPortException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (PortInUseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedCommOperationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TooManyListenersException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	public void close() {
		if (this.serialUtils != null) {
			if (this.serialUtils.getSerialPort() != null) {
				this.serialUtils.getSerialPort().close();
			}
			this.serialUtils = null;
		}
	}
	
	/**
	 * 检查指定时间内是否出现某个字符串
	 * @param checkStr 检查的字符串
	 * @param timeout 超时时间
	 * @return
	 * @throws InterruptedException 
	 */
	public boolean checkString(String checkStr, long timeout) throws InterruptedException {
		setCheckString(checkStr);
		setStringComeout(false);
		long startMills = System.currentTimeMillis();
		long currentMills = 0;
		while(currentMills <= timeout){
			currentMills = System.currentTimeMillis() - startMills;
			if (currentMills < timeout) {
				Thread.sleep(50);
			}
			if (isStringComeout()) {
				currentMills = timeout + 1;
			}
		}
		return isStringComeout();
	}
	
	public String getCheckString() {
		return checkString;
	}

	public void setCheckString(String waitString) {
		this.checkString = waitString;
	}

	public boolean isStringComeout() {
		return stringComeout;
	}

	public void setStringComeout(boolean stringComeout) {
		this.stringComeout = stringComeout;
	}

	public String getDeviceSerialLogPathStr() {
		return deviceSerialLogPathStr;
	}

	public void setDeviceSerialLogPathStr(String deviceSerialLogPathStr) {
		this.deviceSerialLogPathStr = deviceSerialLogPathStr;
	}
	
}
