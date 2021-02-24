/**
 * @Author：Tonsen
 * @Email ：Dongcheng.Wei@desay-svautomotive.com
 * @Date  ：2017-05-28
 */
package com.utils;

/** 
* @author 作者 E-mail: Dongcheng.Wei@desay-svautomotive.com
* @version 创建时间：2017年5月28日 下午1:29:01 
* 类说明 :
*/
/**
 * @author uidq0460
 *
 */
public class DataEncode {
	/**
	 * 高低字节对调，如ABCD ->  CDAB
	 * @param codeStr
	 * @return
	 * @Date 2017-05-29
	 */
	public static String switchCode(String codeStr) {
		String codeSwitchStr = "";
		for (int i = codeStr.length()-2; i > -1; i-=2) {
			codeSwitchStr += codeStr.substring(i, i+2);
		}
		
		return codeSwitchStr;
	}
	/**
	 * 计算校验码并转成字符串
	 * @param codeStr
	 * @return
	 * @Date 2017-05-29  
	 */
	public static String getVerifyCode(String codeStr) {
		long verifyCode = 0;
		for (int i = 0; i < codeStr.length(); i+=2) {
			String codeNum = codeStr.substring(i, i+2);
			verifyCode += Long.parseLong(codeNum, 16);
		}
		String realVerifyCode = Long.toHexString(verifyCode);
		return realVerifyCode.substring(realVerifyCode.length()-2, realVerifyCode.length());
	}
	/**
	 * Convert byte[] to hex string.这里我们可以将byte转换成int，然后利用Integer.toHexString(int)来转换成16进制字符串。
	 * @param src byte[] data   
	 * @return hex string   
	 */
	public static String bytesToHexString(byte[] src){   
	    StringBuilder stringBuilder = new StringBuilder("");   
	    if (src == null || src.length <= 0) {   
	        return null;   
	    }   
	    for (int i = 0; i < src.length; i++) {   
	        int v = src[i] & 0xFF;   
	        String hv = Integer.toHexString(v);   
	        if (hv.length() < 2) {   
	            stringBuilder.append(0);   
	        }   
	        stringBuilder.append(hv);   
	    }   
	    return stringBuilder.toString();   
	}
	/** 
	 * 转换10进制字符串为byte 
	 * Convert hex string to byte[]  
	 * @param hexString the hex string  
	 * @return byte[]  
	 */  
	public static byte[] hexStringToBytes(String hexString) {   
	    if (hexString == null || hexString.equals("")) {   
	        return null;   
	    }   
	    hexString = hexString.toUpperCase();   
	    int length = hexString.length() / 2;   
	    char[] hexChars = hexString.toCharArray();   
	    byte[] d = new byte[length];   
	    for (int i = 0; i < length; i++) {   
	        int pos = i * 2;   
	        d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));   
	    }   
	    return d;   
	}
		
	/**
	 * char转byte
	 * @param c
	 * @return
	 * @Date 2017-05-28
	 */
	public static byte charToByte(char c) {   
		    return (byte) "0123456789ABCDEF".indexOf(c);
    }
	
    //将指定byte数组以16进制的形式打印到控制台   
    public static void printHexString( byte[] b) {     
       for (int i = 0; i < b.length; i++) {    
         String hex = Integer.toHexString(b[i] & 0xFF);    
         if (hex.length() == 1) {    
           hex = '0' + hex;    
         }    
         System.out.print(hex.toUpperCase() );    
       }    
      
    }  
	
}
