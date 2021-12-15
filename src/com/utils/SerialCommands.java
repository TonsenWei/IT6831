package com.utils;


import com.utils.DataEncode;
import com.utils.MyUtils;
import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.TooManyListenersException;

/**   
* @Title: SerialComWindow.java 
* @Package com.main.widget 
* @Description: 串口控制面板
* @author 韦冬成    E-mail: 470029260@qq.com
* @date 2020年04月07日 下午9:26:18 
* @version V1.0  
* @user uidq0460 
* @update 
* 2020年04月07日 下午9:26:18
* appName = comStr;
* String boundrateStr = comboBoundrate.getText();
* initSerialDeviceInThread(comStr, boundrateStr);
* 
* closeSerial();
*/
public class SerialCommands implements Runnable, SerialPortEventListener {
	public static final String WORKSPACE_DIR = System.getProperty("user.dir");                //工作空间目录
	
	//串口相关变量
    private static String appName = "COMX";  
    private static int timeout = 2000;				//open 端口时的等待时间
    private int threadTime = 0;  
    private static CommPortIdentifier commPort;  
    private static SerialPort serialPort;  
    private static InputStream inputStream;  
    private static BufferedReader bufferedReaderReceive;
    private static OutputStream outputStream;  
    private static boolean isComInitOk = false;
    private static String receiveLineStr = "";
    long startComTimeMillis = System.currentTimeMillis();//断开打开开始时刻
    private String portName = "";
    private static BufferedWriter traceTempFile;//用来保存日志的
    private boolean isResultComeout = false;
    private String waitResultString = "TonsenAndroid";
	private String saveLogPath = WORKSPACE_DIR + "\\串口日志" + appName + ".txt";
	
	private long receiveNullTime = System.currentTimeMillis();
	
	
	//控件相关
	private static String filterKeyworksStr = "";
	private int index;
	private String boundrateString;
	private String serialPortName;
	
	private List<String> cmdList = Collections.synchronizedList(new ArrayList<String>());//adb命令输入列表
	private int cmdCount = 0;//记录输入的adb指令历史记录的个数

    private static boolean needFlushSerialFile = false;
    private static Thread watchThread; 
	private static int markCounter = 0;
	
	
	public SerialCommands ()
	{
		
	}
	/**
	 * 向绑定的串口端口写入字符串
	 * @param message 写入的字符串
	 */
	public void writeToSerialCom(String message) {
		if (isComInitOk) {
			write(message);
		}
	}
	
	/**
	 * 向绑定的串口端口写入十六进制字符串
	 * @param message 写入的字符串
	 */
	public void writeToHexToCom(String message) {
		if (isComInitOk) {
			writeHexStrToPort(message);
		}
	}


    /** 
     * @方法名称 :listPort 
     * @功能描述 :列出所有可用的串口 
     * @返回值类型 :void 
     */  
    @SuppressWarnings("rawtypes")  
    public static ArrayList<String> listPort(){  
        CommPortIdentifier cpid;  
        Enumeration en = CommPortIdentifier.getPortIdentifiers();  
        
        ArrayList<String> portNameList=new ArrayList<String>();  
          
        while(en.hasMoreElements()){  
            cpid = (CommPortIdentifier)en.nextElement();  
            if(cpid.getPortType() == CommPortIdentifier.PORT_SERIAL){
            	String comNameStr = cpid.getName();
            	portNameList.add(comNameStr); 
                System.out.println(cpid.getName() + ", " + cpid.getCurrentOwner());  
            }  
        }
        return portNameList;
    }  
      
    /** 
     * @方法名称 :selectPort 
     * @功能描述 :选择一个端口，比如：COM1 
     * @返回值类型 :void 
     *  @param portName 
     * @throws UnsupportedCommOperationException 
     * @throws PortInUseException 
     */  
    @SuppressWarnings("rawtypes")
	public boolean selectPort(String portNameStr, int baudRate) throws PortInUseException, UnsupportedCommOperationException {  
        this.portName = portNameStr;
        commPort = null;  
        CommPortIdentifier cpid;  
        Enumeration en = CommPortIdentifier.getPortIdentifiers();  
          
        while(en.hasMoreElements()){  
            cpid = (CommPortIdentifier)en.nextElement();  
            if(cpid.getPortType() == CommPortIdentifier.PORT_SERIAL  
                    && cpid.getName().equals(portNameStr)){  
                commPort = cpid;  
                break;  
            }  
        }  
        //打开选择的端口
        return openPort(baudRate);  
    }  
      
    /** 
     * @throws PortInUseException 
     * @throws UnsupportedCommOperationException 
     * @方法名称 :openPort 
     * @功能描述 :打开SerialPort 
     * @返回值类型 :void 
     */  
    private boolean openPort(int baudRate) throws PortInUseException, UnsupportedCommOperationException {  
    	boolean isOk = false;
        if(commPort == null) { 
        	isOk = false;
        	System.out.println("commPort == null");
        } else{  
            log("SelectPortOk,CurrentPort:" + commPort.getName() + ",now Initial SerialPort:");  
            serialPort = (SerialPort)commPort.open(appName, timeout);
            if (serialPort != null) {
            	//设置com口
            	serialPort.setDTR(false);
            	serialPort.setRTS(false);
        		serialPort.setSerialPortParams(
        				baudRate,
        				SerialPort.DATABITS_8, 
        				SerialPort.STOPBITS_1, 
        				SerialPort.PARITY_NONE);
        		isOk = true;
            	log("Initial SerialPort OK!");  
			} else {
				isOk = false;
			}
        }
        return isOk;
    }  
      
    /** 
     * @方法名称 :checkPort 
     * @功能描述 :检查端口是否正确连接
     * @返回值类型 :void 
     */  
    public boolean checkPort(){  
    	isComInitOk = true;
    	
        if(commPort == null)  {
        	isComInitOk = false;
        }
          
        if(serialPort == null){  
        	isComInitOk = false;
        }  
        
        return isComInitOk;
    }  
     
    
    /**
     * 向串口发送16进制字符串
     * @param hexStr 字符串
     */
    public void writeHexStrToPort(String hexStr) { 
    	try {
    		outputStream = new BufferedOutputStream(serialPort.getOutputStream());  
    	}catch(IOException e){  
    		throw new RuntimeException("获取端口的OutputStream出错：" + e.getMessage());  
    	}  
    	
    	try {
    		byte[] byteFrame = DataEncode.hexStringToBytes(hexStr);
    		outputStream.write(byteFrame);  
    		outputStream.flush();
    	} catch (IOException e){  
    		throw new RuntimeException("向端口发送信息时出错：" + e.getMessage());  
    	} finally {  
    	      //关闭输出流
    	      if(outputStream!=null)
    	      {
    	          try {
    	        	  outputStream.close();
    	        	  outputStream=null;
    	          } catch (IOException e) {
    	        	  System.out.println("SerialWindow.java.writeHexStrToPort():" + e.toString());
    	          }   
    	      }
    	}           
    }
    
    /** 
     * @方法名称 :write 
     * @功能描述 :向端口发送数据，请在调用此方法前 先选择端口，并确定SerialPort正常打开！ 
     * @返回值类型 :void 
     *  @param message 
     */  
    public void write(String message) {  
    	if (checkPort()) {
    		try{  
    			outputStream = new BufferedOutputStream(serialPort.getOutputStream());  
    		}catch(IOException e){  
    			throw new RuntimeException("Error:获取端口的OutputStream出错："+e.getMessage());  
    		}  
    		
    		try{  
//    			outputStream.write(message.getBytes("US-ASCII"));  
    			outputStream.write(message.getBytes("UTF-8"));  
//            log("信息发送成功！");  
    		} catch (IOException e){  
    			throw new RuntimeException("Error:向端口发送信息时出错："+e.getMessage());  
    		} finally {  
    			try{  
    				outputStream.close();  
    			} catch (Exception e){  
    				System.out.println("SerialWindow.java.write(String message):" + e.toString());
    			}  
    		}  
		}
          
    }  
      
    /** 
     * @方法名称 :startRead 
     * @功能描述 :开始监听从端口中接收的数据 
     * @返回值类型 :void 
     *  @param time  监听程序的存活时间，单位为秒，0 则是一直监听
     */  
    public void startRead(int time){  
    	if (checkPort()) {
    		try{  
    			inputStream = new BufferedInputStream(serialPort.getInputStream());
    			bufferedReaderReceive = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));//UTF-8格式读取, "UTF-8"
    		}catch(IOException e){  
    			throw new RuntimeException("Error:获取端口的InputStream出错："+e.getMessage());  
    		}  
    		
    		try{  
    			serialPort.addEventListener(this);  
    		}catch(TooManyListenersException e){  
    			throw new RuntimeException(e.getMessage());  
    		}  
    		
    		serialPort.notifyOnDataAvailable(true);  
    		
    		log(String.format("Start lisent data from '%1$s'--------------", commPort.getName()));  
    		if(time > 0){  
    			this.threadTime = time*1000;  
    			Thread t = new Thread(this);  
    			t.start();  
    			log(String.format("监听程序将在%1$d秒后关闭。。。。", threadTime));  
    		}  
    	}
    }  
      
      
    /** 
     * @方法名称 : close 
     * @功能描述 : 关闭 SerialPort
     * @返回值类型 : void 
     */  
    public static void closeSerial() { 
    	if (serialPort != null) {
    		System.out.println("start closeSerial()");
    		serialPort.close();  
    		System.out.println("closeSerial() OK");
    		serialPort = null;  
		}
    	if (commPort != null) {
    		commPort = null;  
		}
    	if (traceTempFile != null) {
    		try {
    			traceTempFile.flush();
				traceTempFile.close();
				traceTempFile = null;
			} catch (IOException e) {
				System.out.println(appName + "日志关闭失败...");
			}
		}
    }  
     
    public String getPortName() {
    	return this.portName;
    }
      
    private static void log(String msg){  
        System.out.println(appName+" --> "+msg);  
    }  
  
    /** 
     * 数据接收的监听处理函数
     */  
    @Override  
    public void serialEvent(SerialPortEvent arg0) { 
    	
        switch(arg0.getEventType()){  
        case SerialPortEvent.BI:/*Break interrupt,通讯中断*/
        	System.out.println("SerialComposite.java case SerialPortEvent.BI:通讯中断");
        case SerialPortEvent.OE:/*Overrun error，溢位错误*/
        	System.out.println("SerialComposite.java case SerialPortEvent.OE:溢位错误");
        case SerialPortEvent.FE:/*Framing error，传帧错误*/ 
        	System.out.println("SerialComposite.java case SerialPortEvent.FE:传帧错误");
        case SerialPortEvent.PE:/*Parity error，校验错误*/  
        	System.out.println("SerialComposite.java case SerialPortEvent.PE:校验错误");
        case SerialPortEvent.CD:/*Carrier detect，载波检测*/  
        	System.out.println("SerialComposite.java case SerialPortEvent.CD:载波检测");
        case SerialPortEvent.CTS:/*Clear to send，清除发送*/ 
        	System.out.println("SerialComposite.java case SerialPortEvent.CTS:清除发送");
        case SerialPortEvent.DSR:/*Data set ready，数据设备就绪*/   
        	System.out.println("SerialComposite.java case SerialPortEvent.DSR:数据设备就绪");
        case SerialPortEvent.RI:/*Ring indicator，响铃指示*/ 
        	System.out.println("SerialComposite.java case SerialPortEvent.RI:响铃指示");
        case SerialPortEvent.OUTPUT_BUFFER_EMPTY:/*Output buffer is empty，输出缓冲区清空*/
        	System.out.println("SerialComposite.java case SerialPortEvent.OUTPUT_BUFFER_EMPTY:输出缓冲区清空");
            break;  
        case SerialPortEvent.DATA_AVAILABLE:/*Data available at the serial port，端口有可用数据。读到缓冲数组，输出到终端*/
            try {  
				if (serialPort != null) {
					while(inputStream.available() > 0)
					{
						String receiveStr = "";
						try {
							while ((receiveStr = bufferedReaderReceive.readLine()) != null) {
								setReceiveNullTime(System.currentTimeMillis());
								if (receiveStr.contains(waitResultString)) {
									isResultComeout = true;
									try {//接收的字符串写入文件
										receiveLineStr = MyUtils.getNowTimeMills() + "> " + receiveStr + System.lineSeparator();
										System.out.println(receiveStr);
										needFlushSerialFile = true;
										traceTempFile.append(receiveLineStr);
										traceTempFile.flush();
									} catch (Exception e) {
									}
									System.out.print("waitString Appear,receiveStr = " + receiveStr + System.lineSeparator());
								} else if (!receiveStr.equals("")) {
									try {//接收的字符串写入文件
										receiveLineStr = MyUtils.getNowTimeMills() + "> " + receiveStr + System.lineSeparator();
										System.out.println(receiveStr);
										needFlushSerialFile = true;
										traceTempFile.append(receiveLineStr);
										traceTempFile.flush();
									} catch (Exception e) {
									}
								}
							}
						} catch (IOException e) {//遇到返回0byte时的异常时不处理
						}
					}
				}
            } catch (IOException e) {  
//            	* V7.16-20200723 串口断开检测
//            	*   增加串口断开判断，如果拔掉串口或串口接触不良，会关闭串口，界面上显示串口为未连接状态。在考虑是否增加重连功能。
            	System.out.println("SerialComposite.java_case SerialPortEvent.DATA_AVAILABLE:" + e.toString());
            	closeSerial();
            }  
        }  
    }   
  
  
    @Override  
    public void run() {  
        try{  
            Thread.sleep(threadTime);  
//          close();  
//          log(String.format("端口''监听关闭了！", commPort.getName()));  
        }catch(Exception e){  
            e.printStackTrace();  
        }  
    }
    
    /**
     * 设置结果
     * @return
     */
    public void setIsResultComeout(boolean comeoutB) {
    	this.isResultComeout = comeoutB;
    }
    
    /**
     * 获取串口检测结果，获取结果后需要该标志位为未出现
     * @return
     */
    public boolean getIsResultComeout() {
    	boolean result = this.isResultComeout;
		return result;
	}
    /**
     * 设置等待的字符串，设置完毕需要把字符串出现的结果置为false，这样下次字符串出现才会置为true
     * @param str
     * @return
     */
    public void setWaitResultString(String str) {
    	this.waitResultString = str;
    	this.isResultComeout = false;
	}
    
    /**
     * 获取当前检测的字符串
     * @return
     */
    public String getWaitResultString() {
		return this.waitResultString;
	}
    
    /**
     * 开一个线程初始化串口,初始化结果不弹窗
     * @param comStr 串口端口号
     * @param boundrateStr 串口波特率
     * @throws UnsupportedCommOperationException 
     * @throws PortInUseException 
     */
    public void initSerial(String comStr, String boundrateStr) throws PortInUseException, UnsupportedCommOperationException {
    	int baudRateInt = Integer.parseInt(boundrateStr);
		System.out.println("comStr = " + comStr + ", baudRateInt = " + baudRateInt);
		if (selectPort(comStr, baudRateInt)) {
			setLogPath(comStr);
			delLogFile();
			try {
				traceTempFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(saveLogPath, true), "utf-8"));
				traceTempFile.append("start");
			} catch (IOException e1) {
				System.out.println("initSerialWithoutUi(" + comStr + "):" + e1.toString());
			}
			startRead(0);//监听端口
		} else {
			System.out.println("initSerial:选择端口失败");
		}
    }
    
    /**
     * 开一个线程初始化串口
     * @param comStr 串口端口号
     * @param boundrateStr 串口波特率
     */
    private void initSerialDeviceInThread(String comStr, String boundrateStr) {
    	new Thread(new Runnable() {
			@Override
			public void run() {
				int baudRateInt = Integer.parseInt(boundrateStr);
				try {
					if (selectPort(comStr, baudRateInt)) {
						setLogPath(comStr);
						delLogFile();
						try {
							traceTempFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(saveLogPath, true), "utf-8"));
						} catch (IOException e1) {
							System.out.println("initDeviceCom(" + comStr + "):" + e1.toString());
						}
						startRead(0);//监听端口
					} else {
						System.out.println("initDeviceCom:选择端口失败");
					}
				} catch (PortInUseException e) {
					System.out.println("initDeviceCom:端口正在使用");
				} catch (UnsupportedCommOperationException e) {
					System.out.println("initDeviceCom:端口正在使用");
				}
			}
		}).start();
    }
    
    private void setLogPath(String comStr) {
    	this.saveLogPath = WORKSPACE_DIR + "\\串口日志" + comStr + ".txt";
    }
    
	
    /**
     * 清除日志和窗口，不判断焦点是否是当前窗口是否
     */
    public void clearAllLogs() {
    	markCounter = 0;
		if (traceTempFile != null) {
			try {
				traceTempFile.close();
				delLogFile();
			} catch (IOException e) {
				System.out.println("SerialComposite.java.clearLog()" + e.toString());
			}
		}
		try {
			traceTempFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(saveLogPath, true), "utf-8"));
		} catch (IOException e1) {
			System.out.println("clearLog(" + portName + "):" + e1.toString());
		}
    }
    
    public void delLogFile() {
		File logFile = new File(saveLogPath);
		if (logFile.exists()) {
			logFile.delete();
		}
	}
	
    public long getReceiveNullTime() {
		return receiveNullTime;
	}
	public void setReceiveNullTime(long receiveNullTime) {
		this.receiveNullTime = receiveNullTime;
	}
	
}
