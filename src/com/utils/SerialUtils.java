package com.utils;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;


import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

public class SerialUtils {
	
	private SerialPort multiPort;
	private OutputStream outputStream;
	private CommPortIdentifier portIdentifier; 
	
	private int baudRate;//波特率
	private int databits;//数据位
	private int stopbits;//停止位
	private int parity;  //奇偶校验
	
	
    /**
     * 构造函数，串口默认配置
     * category波特率：115200
     * 数据位：SerialPort.DATABITS_8
     * 停止位：SerialPort.STOPBITS_1
     * 奇偶校验：SerialPort.PARITY_NONE
     */
    public SerialUtils()
    {
        super();
        this.baudRate = 115200;
    	this.databits = SerialPort.DATABITS_8;
    	this.stopbits = SerialPort.STOPBITS_1;
    	this.parity = SerialPort.PARITY_NONE;
    }
    
    /**
     * 构造函数，传入串口参数
     * @param baudRate 波特率
     * @param databits 数据位
     * @param stopbits 停止位
     * @param parity   奇偶校验
     */
    public SerialUtils(int baudRate, int databits, int stopbits, int parity)
    {
    	super();
    	this.baudRate = baudRate;
    	this.databits = databits;
    	this.stopbits = stopbits;
    	this.parity = parity;
    }

    /**
     * 连接串口
     * @param portName 串口名（COMXX）
     * @return SerialPort 串口
     * @throws NoSuchPortException 
     * @throws PortInUseException 
     * @throws UnsupportedCommOperationException 
     * @throws Exception 
     */
    public SerialPort connectPort ( String portName ) throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException {
    	
        portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
        if ( portIdentifier.isCurrentlyOwned() ) {
            System.out.println("Error: Port " + portName + " is currently in use");
        } else {
            CommPort commPort = portIdentifier.open(this.getClass().getName(),2000);
            if ( commPort instanceof SerialPort ) {
                SerialPort serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(baudRate, databits, stopbits, parity);
                multiPort = serialPort;
                return serialPort;
            } else {
                System.out.println("Error: Only serial ports are handled by this example.");
                return null;
            }
        }
		return null;     
    }
    
    /** 
     * @方法名称 :write
     * @功能描述 :向端口发送数据，请在调用此方法前 先选择端口，并确定SerialPort正常打开
     * @返回值类型 :void 
     * @param message 
     */  
    public void write(String message) {  
        try{  
            outputStream = new BufferedOutputStream(multiPort.getOutputStream());  
        }catch(IOException e){  
            throw new RuntimeException("获取端口的OutputStream出错：" + e.getMessage());  
        }  
          
        try{  
            outputStream.write(message.getBytes("US-ASCII"));  //US-ASCII/UTF-8
//            log("信息发送成功！");  
        }catch(IOException e){  
            throw new RuntimeException("向端口发送信息时出错："+e.getMessage());  
        }finally{  
            try{  
                outputStream.close();  
            }catch(Exception e){ 
            	
            }  
        }  
    }  
    
    /**
     * 获取该实例下的串口
     * @return 串口
     */
    public SerialPort getSerialPort() {
		return multiPort;
	}
    
    
    /**
     * 向串口发送10进制字符串
     * @param hexStr 字符串
     */
    public void writeHexStrToPort(String hexStr) { 
    	try {  
    		outputStream = new BufferedOutputStream(multiPort.getOutputStream());  
    	}catch(IOException e){  
    		throw new RuntimeException("获取端口的OutputStream出错："+e.getMessage());  
    	}  
    	
    	try {
    		byte[] byteFrame = DataEncode.hexStringToBytes(hexStr);
    		outputStream.write(byteFrame);  
    		outputStream.flush();
    	} catch (IOException e){  
    		throw new RuntimeException("向端口发送信息时出错："+e.getMessage());  
    	} finally {  
    	      //关闭输出流
    	      if(outputStream!=null)
    	      {
    	          try {
    	        	  outputStream.close();
    	        	  outputStream=null;
    	          } catch (IOException e) {
    	              e.printStackTrace();
    	          }   
    	      }
    	}           
    }
 
}
