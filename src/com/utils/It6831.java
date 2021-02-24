/**
 * @Author：Tonsen
 * @Email ：Dongcheng.Wei@desay-svautomotive.com
 * @Date  ：2017-05-29
 */
package com.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.TooManyListenersException;
import com.power.IT6831Ctrl;
import com.utils.DataEncode;
import com.utils.MyUtils;
import com.utils.SerialUtils;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;


/** 
* @author 作者 E-mail: Dongcheng.Wei@desay-svautomotive.com
* @version 创建时间：2017年5月29日 下午4:45:30 
* 类说明 :
*/
/**
 * @author uidq0460
 *
 */
public class It6831 {
	
	//串口相关
	private SerialUtils powerSerial;
//	private boolean isPowerIdle = true;    //电源是否空闲状态
	private String totalStr = "";		   //总字符串
	private String returnPackage = "";     //应答的字符串
	private String returnStatusStr = "";   //应答的字符串

	private String START_CODE = "AA";      //第一个字节
	private String POWER_ADD = "00";       //第二个字节，电源地址
	private String POWER_CMD = "00";       //第三个字节，命令字
	private String CODE_FRAME = "AA00000000000000000000000000000000000000000000000000";//pc
	public static final boolean POWER_ON = true;
	public static final  boolean POWER_OFF = false;
	public static final  boolean PC_MODE = true;
	public static final  boolean PANAL_MODE = false;
	
	//测试
	private long startSendCmdTime = System.currentTimeMillis();
	private static boolean returnStatus = false;
	private static boolean returnVerify = false;
	private static final long SEND_TIMEOUT = 280; 
	

	private int OUTPUT_VOLTAGE = 23;      //输出电压命令
	private int OUTPUT_CURRENT = 24;      //输出电压命令
	
	private boolean isBusy = false;
	private final long WAIT_TIME_OUT = 3000;//三秒
	
	public It6831() {
		this.powerSerial = new SerialUtils(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
	}
	
	public It6831(int baudRate, int databits, int stopbits, int parity) {
		this.powerSerial = new SerialUtils(baudRate, databits, stopbits, parity);
	}
	
	public SerialUtils It6831HaveInstants() {
		return this.powerSerial;
	}
	
	public boolean init(String comStr) throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException, TooManyListenersException {
			boolean isOk = false;
			this.powerSerial.connectPort(comStr);
			this.powerSerial.getSerialPort().addEventListener(new SerialPortEventListener() {
				@Override
				public void serialEvent(SerialPortEvent ev) {
					if (ev.getEventType() == SerialPortEvent.DATA_AVAILABLE) {
						try {
							if (IT6831Ctrl.isScpi) {
								BufferedReader bufferedReaderReceive = new BufferedReader(new InputStreamReader(powerSerial.getSerialPort().getInputStream(), "UTF-8"));//UTF-8格式读取
								try {
									String receiveStr = "";
									while (bufferedReaderReceive != null && (receiveStr = bufferedReaderReceive.readLine()) != null) {
										if (!receiveStr.equals("")) {
											returnStatusStr = receiveStr;
										}
									}
									bufferedReaderReceive.close();
								} catch (Exception e) {
//									System.out.println("serialEvent(SerialPortEvent ev):" + e.getMessage());
								} finally {
									bufferedReaderReceive.close();
								}
							} else {
								byte[] receiveDataPackage = new byte[1024];
								int bufferLength = powerSerial.getSerialPort().getInputStream().available();
								
								while(bufferLength > 0)
								{
									receiveDataPackage = new byte[bufferLength];
									powerSerial.getSerialPort().getInputStream().read(receiveDataPackage);
									bufferLength = powerSerial.getSerialPort().getInputStream().available();
									String hexStr = DataEncode.bytesToHexString(receiveDataPackage);
									
									totalStr += hexStr.trim();
									if (totalStr.length() >= 52) {
										String[] strings = totalStr.split("aa");
										String resultStr = "";
										for (String string : strings) {
											if (!string.equals("")) {
												returnPackage = "aa" + string;
												if (returnPackage.length() >= 52) {
													String codeStatusStr = returnPackage.substring(4, 6);
													String codeStr = returnPackage.substring(6, 8);
													synchronized (this) {
														if (codeStatusStr.equals("26")) {
															returnStatus = true;
															returnStatusStr = returnPackage;
														} else if (codeStr.equals("80")) {
															returnVerify = true;
														} 
													}
												}
												resultStr = string;//拼接到下一个
											}
										}
										if (resultStr.length() >= 50) {//最后一组刚好也是一组命令时
											totalStr = "";
										} else {
											totalStr = ("aa" + resultStr);
										}
									}
								}
							}
						} catch (IOException e) {
							System.out.println(e.toString());
						} 
					}
				}
			});
			powerSerial.getSerialPort().notifyOnDataAvailable(true);
			isOk = true;
			return isOk;
	}
	
    /**
     * 把16进制字符串写到串口
     * @param hexStr
     */
    public synchronized boolean writeHexStr(String hexStr) { 
    	boolean isOk = false;
    	
    	returnStatus = false;
    	returnVerify = false;
    	this.powerSerial.writeHexStrToPort(hexStr);
		//指定时间
    	startSendCmdTime = System.currentTimeMillis();//发送命令后开始计时
		long currentMills = 0;
		while(currentMills <= SEND_TIMEOUT) {
			
			currentMills = System.currentTimeMillis() - startSendCmdTime;
			if(SEND_TIMEOUT > 0) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					System.out.println("writeHexStr:" + e.getMessage());
				}
			}
			if (returnStatus == true || returnVerify == true) {
				if (returnStatus == true) {
					isOk = true;
					returnStatus = false;
				}
				if (returnVerify == true) {
					isOk = true;
					returnVerify = false;
				}
				currentMills = SEND_TIMEOUT + 1;
			}
		}
		return isOk;
    }
	
	public void close() {
		if (this.powerSerial != null) {
			//串口添加了监听，关闭串口最好先关闭这个监听，否则容易出现关闭失败，从而退出程序后下次打开程序提示串口端口被占用
			try {
				this.powerSerial.getSerialPort().removeEventListener();
				if (this.powerSerial.getSerialPort() != null) {
					this.powerSerial.getSerialPort().close();
				}
			} catch (NullPointerException e) {
				System.out.println("close():" + e.getMessage());
				// TODO: handle exception
			}
			this.powerSerial = null;
		} 
	}

	/**
	 * 发送查询电源状态的指令
	 * @throws InterruptedException 
	 * @Date 2017-06-01
	 */
	public String sentGetPowerStatusCmd() {
		String isOk = "";
					   //AA00000000000000000000000000000000000000000000000000
					   //AA002600000000000000000000000000000000000000000000D00A//多加0A表示结束
		if (IT6831Ctrl.isScpi) {
			//指定时间
	    	startSendCmdTime = System.currentTimeMillis();//发送命令后开始计时
			long currentMills = 0;
			while(currentMills <= WAIT_TIME_OUT) {
				
				currentMills = System.currentTimeMillis() - startSendCmdTime;
				if(WAIT_TIME_OUT > 0) {
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						System.out.println("writeHexStr:" + e.getMessage());
					}
				}
				if (isBusy() == false) {//
					setBusy(true);
					currentMills = WAIT_TIME_OUT + 1;
					
					this.powerSerial.write("MEAS:VOLT?;CURR?;POW?" + System.lineSeparator());//发送查询
					//指定时间
			    	long startWaitTime = System.currentTimeMillis();//发送命令后开始计时
					long nowMills = 0;
					long waitTimeOut = 3000;
					while(nowMills <= waitTimeOut) {
						
						nowMills = System.currentTimeMillis() - startWaitTime;
						if(waitTimeOut > 0) {
							try {
								Thread.sleep(10);
							} catch (InterruptedException e) {
								System.out.println("timeout:" + e.getMessage());
							}
						}
						if (!returnStatusStr.equals("")) {
							nowMills = waitTimeOut + 1;
							isOk = returnStatusStr;
						}
					}
					setBusy(false);
				}
			}
		} else {
			String cmdStr = "aa002600000000000000000000000000000000000000000000d0";
			if (writeHexStr(cmdStr)) {
				isOk = returnStatusStr;
			} else {
				System.out.println("sentGetPowerStatusCmd失败");
			}
		}
		return isOk;
	}
	
	/**
	 * 解析电源返回码
	 * @param returnStr 返回码，通过这个返回码解析当前电源输出电压电流等信息
	 * @return 返回电源状态
	 * @Date 2017-06-01
	 */
//	public static PowerStatus getPowerStatus(String returnStr) {
//		String startCmd = returnStr.substring(0, 2);
//		String addCmd = returnStr.substring(2, 4);
//		String cmdCmd = returnStr.substring(4, 6);
//		PowerStatus powerStatus = new PowerStatus();
//		if (startCmd.equals("AA") && addCmd.equals(POWER_ADD) && cmdCmd.equals("26")) {
//			String currCmd = returnStr.substring(6, 10);
//			String realCurrStr = DataEncode.switchCode(currCmd);
//			long currLong = Long.parseLong(realCurrStr, 16);
//			double curr = ((double)currLong)/1000;
//			String currStr = String.format("%.3f", curr).toString();
//			powerStatus.setCurrNowStr(currStr);
//			System.out.println("currLong = " + currLong + ", double=" + currStr);
//			
//			String voltCmd = returnStr.substring(10, 14);
//			String realVoltStr = DataEncode.switchCode(voltCmd);
//			long voltLong = Long.parseLong(realVoltStr, 16);
//			double volt = ((double)voltLong)/1000;
//			String voltStr = String.format("%.3f", volt).toString();
//			powerStatus.setVoltNowStr(voltStr);
//			System.out.println("voltLong = " + voltLong + ", doubleVolt=" + voltStr);
//		}
//		return powerStatus;
//	}
	
	/**
	 * 根据返回的字符串判断电源输出开关是否为打开状态
	 * @param statusStr 发送查询命令后返回的状态信息
	 * @return 为打开状态则返回true否则为false
	 */
	public boolean isOutputOn(String statusStr) {
		String remoteCode = statusStr.substring(18, 20);
		byte[] bytes = DataEncode.hexStringToBytes(remoteCode);
		byte bbb = bytes[0];
		if ((bbb & 0x01) == 0x01) {
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * 根据返回的字符串判断电源是否正常
	 * @param statusStr 发送查询命令后返回的状态信息
	 * @return pc控制状态返回true，面板控制状态返回false
	 */
	public boolean isPowerNormal(String statusStr) {
		String remoteCode = statusStr.substring(18, 20);
		byte[] bytes = DataEncode.hexStringToBytes(remoteCode);
		byte bbb = bytes[0];
		if (((bbb & (0x04)) == 0x04) && ((bbb & (0x02)) != 0x02)) {
			return true;
		} else {
			return false;
		}
	}
	

	/**
	 * 根据返回的字符串判断电源输出模式是否为PC控制状态
	 * @param statusStr 发送查询命令后返回的状态信息
	 * @return pc控制状态返回true，面板控制状态返回false
	 */
	public boolean isPcCtrlMode(String statusStr) {
		String remoteCode = statusStr.substring(18, 20);
		byte[] bytes = DataEncode.hexStringToBytes(remoteCode);
		byte bbb = bytes[0];
		if ((bbb & (0x01 << 7)) == 0x00) {
			return false;
		} else {
			return true;
		}
	}
	
	
	/**
	 * 设置电源输出电压,待增加数值限制
	 * 占两个字节，第4和第5个字节
	 * @param voltStr 电压如12.000
	 * @throws InterruptedException 
	 * @Date 2017-05-29
	 */
	public boolean setOutputVoltage(String voltStr) {//12V
		boolean isOk = false;
		System.out.println(MyUtils.getNowTimeMills() + "< Set output voltage:" + voltStr + "V");
		if (IT6831Ctrl.isScpi) {
			//指定时间
	    	startSendCmdTime = System.currentTimeMillis();//发送命令后开始计时
			long currentMills = 0;
			while(currentMills <= WAIT_TIME_OUT) {
				
				currentMills = System.currentTimeMillis() - startSendCmdTime;
				if(WAIT_TIME_OUT > 0) {
					try {
						Thread.sleep(20);
					} catch (InterruptedException e) {
						System.out.println("writeHexStr:" + e.getMessage());
					}
				}
				if (isBusy() == false) {//
					setBusy(true);
					currentMills = WAIT_TIME_OUT + 1;
					
					this.powerSerial.write("VOLT " + voltStr + "V" + System.lineSeparator());
					System.out.println("VOLT " + voltStr + "V");
					isOk = true;
					
					setBusy(false);
				}
			}
		} else {
			isOk = setCmd(OUTPUT_VOLTAGE, voltStr);
		}
		return isOk;//12V
	}
	
	/**
	 * 设置电源输出电流大小
	 * @param currStr
	 * @throws InterruptedException 
	 * @Date 2017-05-31
	 */
	public boolean setOutputCurrent(String currStr) {
		boolean isOk = false;
		System.out.println(MyUtils.getNowTimeMills() + "< Set output current:" + currStr + "A");
		if (IT6831Ctrl.isScpi) {
			//指定时间
	    	startSendCmdTime = System.currentTimeMillis();//发送命令后开始计时
			long currentMills = 0;
			while(currentMills <= WAIT_TIME_OUT) {
				
				currentMills = System.currentTimeMillis() - startSendCmdTime;
				if(WAIT_TIME_OUT > 0) {
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						System.out.println("writeHexStr:" + e.getMessage());
					}
				}
				if (isBusy() == false) {//
					setBusy(true);
					currentMills = WAIT_TIME_OUT + 1;
					
					this.powerSerial.write("CURR " + currStr + "A"+ System.lineSeparator());
					System.out.println("CURR " + currStr + "A");
					isOk = true;
					
					setBusy(false);
				}
			}
		} else {
			isOk = setCmd(OUTPUT_CURRENT, currStr);
		}
		return isOk;
	}
	
	/**
	 * 设置电压和电流等的命令
	 * @param cmd 命令，电压，电流等
	 * @param dataStr 命令数据，比如电压值、电流值的字符串
	 * @throws InterruptedException 
	 * @Date 2017-05-31
	 */
	public boolean setCmd(int cmd, String dataStr) {
		boolean isOk = false;
		POWER_CMD = "" + cmd;//电压的命令字
		String dataCmd = "0000";
		double testDou = Double.parseDouble(dataStr);//12.5
		int dataInt = (int) (testDou*1000);//int     12500 9000
		
		String dataHexStr = Integer.toHexString(dataInt);
		int hexIntLength = dataHexStr.length();			//实际电压字符串长度
		int dataIntLength = dataCmd.length();  			//规定电压字符串长度
		int addCounter = dataIntLength - hexIntLength;	//需要补的长度
		String addStr = "";								//待补字符串
		if (hexIntLength < dataIntLength) {
			for (int i = 0; i < addCounter; i++) {
				addStr += "0";
			}
			dataHexStr = addStr + dataHexStr;
		}
		
		String switchCodeStr = DataEncode.switchCode(dataHexStr);
		String cmdStr = START_CODE + POWER_ADD + POWER_CMD + switchCodeStr + CODE_FRAME.substring(10, 50);//
		cmdStr += (DataEncode.getVerifyCode(cmdStr) + "0A");
		if (writeHexStr(cmdStr)) {//"0A"
			isOk = true;
		} else {
			System.out.println("setCmd " + cmd + " " + dataStr + " returnVerify 失败");
		}
		return isOk;
	}
	
	/**
	 * 控制电源输出
	 * @param onOrOff true为打开电源输出
	 * @throws InterruptedException 
	 * @Date 2017-05-29
	 */
	public boolean setOutput(boolean onOrOff) {
		boolean isOk = false;
		if (IT6831Ctrl.isScpi) {
			//指定时间
	    	startSendCmdTime = System.currentTimeMillis();//发送命令后开始计时
			long currentMills = 0;
			while(currentMills <= WAIT_TIME_OUT) {
				
				currentMills = System.currentTimeMillis() - startSendCmdTime;
				if(WAIT_TIME_OUT > 0) {
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						System.out.println("writeHexStr:" + e.getMessage());
					}
				}
				if (isBusy() == false) {//
					setBusy(true);
					currentMills = WAIT_TIME_OUT + 1;
					
					if (onOrOff) {
						this.powerSerial.write("OUTPut ON" + System.lineSeparator());
						System.out.println("OUTPut ON");
					} else {
						this.powerSerial.write("OUTPut OFF" + System.lineSeparator());
						System.out.println("OUTPut OFF");
					}
					
					setBusy(false);
					isOk = true;
				}
			}
		} else {
			POWER_CMD = "21";//电源命令字
			String codeStr = "00";
			if (onOrOff == POWER_ON) {
				codeStr = "01";
			}
			String cmdVolt = START_CODE + POWER_ADD + POWER_CMD + codeStr + CODE_FRAME.substring(8, 50);
			cmdVolt += DataEncode.getVerifyCode(cmdVolt);
			if (writeHexStr(cmdVolt)) {
				isOk = true;
			} else {
				System.out.println("setOutput " + onOrOff + " 失败");
			}
		}
		return isOk;
	}
	
	/**
	 * 选择通道，仅使用与多通道电源，且仅支持scpi
	 * @param 2为第二通道，3为第三通道，其他值默认为第一通道
	 * @throws InterruptedException 
	 * @Date 2017-05-29
	 */
	public boolean selectChanel(int ch) {
		boolean isOk = false;
		if (IT6831Ctrl.isScpi) {
			//指定时间
	    	startSendCmdTime = System.currentTimeMillis();//发送命令后开始计时
			long currentMills = 0;
			while(currentMills <= WAIT_TIME_OUT) {
				
				currentMills = System.currentTimeMillis() - startSendCmdTime;
				if(WAIT_TIME_OUT > 0) {
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						System.out.println(" selectChanel:" + e.getMessage());
					}
				}
				if (isBusy() == false) {//
					setBusy(true);
					currentMills = WAIT_TIME_OUT + 1;
					
					if (ch == 2) {
						this.powerSerial.write("INST SECO" + System.lineSeparator());
//						MyUtils.printWithTimePrefix(appName + " OUTPut ON");
					} else if (ch == 3) {
						this.powerSerial.write("INST THI" + System.lineSeparator());
//						MyUtils.printWithTimePrefix(appName + " OUTPut OFF");
					} else {
						this.powerSerial.write("INST FIR" + System.lineSeparator());
					}
					setBusy(false);
					isOk = true;
				}
			}
		}
		return isOk;
	}
	
	
	/**
	 * 电源控制模式
	 * @param mode true为PC端(远程)，false为面板控制
	 * @throws InterruptedException 
	 * @Date 2017-05-29
	 */
	public boolean setCtrlMode(boolean mode){
		boolean isOk = false;
		
		if (IT6831Ctrl.isScpi) {
			//指定时间
	    	startSendCmdTime = System.currentTimeMillis();//发送命令后开始计时
			long currentMills = 0;
			while(currentMills <= WAIT_TIME_OUT) {
				
				currentMills = System.currentTimeMillis() - startSendCmdTime;
				if(WAIT_TIME_OUT > 0) {
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						System.out.println("writeHexStr:" + e.getMessage());
					}
				}
				if (isBusy() == false) {//
					setBusy(true);
					currentMills = WAIT_TIME_OUT + 1;
					
					if (mode) {
						this.powerSerial.write("SYST:REM" + System.lineSeparator());
						System.out.println("SYST:REM");
					} else {
						this.powerSerial.write("SYST:LOC" + System.lineSeparator());
						System.out.println("SYST:LOC");
					}
					
					setBusy(false);
					isOk = true;
				}
			}
		} else {
			POWER_CMD = "20";//电源命令字
			String codeStr = "00";
			if (mode == PC_MODE) {
				codeStr = "01";
			}
			String cmdVolt = START_CODE + POWER_ADD + POWER_CMD + codeStr + CODE_FRAME.substring(8, 50);
			cmdVolt += DataEncode.getVerifyCode(cmdVolt);
			if (writeHexStr(cmdVolt)) {
				isOk = true;
			} else {
				System.out.println("setCtrlMode " + mode + " 失败");
			}
		}
		return isOk;
	}
	
	public void writeString(String message) {
		this.powerSerial.write(message);
	}

	public boolean isBusy() {
		return isBusy;
	}

	public void setBusy(boolean isBusy) {
		this.isBusy = isBusy;
	}
	
}
