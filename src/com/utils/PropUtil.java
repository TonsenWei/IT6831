package com.utils;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class PropUtil {

	/**
	 * 获取键值
	 * @param keyStr 键
	 * @param pathStr 配置文件路径
	 * @return
	 * @Date 2017-05-31
	 */
	public static String getValueOfProp(String keyStr, String pathStr) {
		String valueStr = "";
		Map<String, String> mapTest = getProperties(pathStr);
		for (Map.Entry<String, String> entry : mapTest.entrySet()) {
			if (entry.getKey().equals(keyStr)) {//获取键值
				valueStr = entry.getValue();
			}
		}
		return valueStr;
	}
	
	/*
	 * 获取配置信息
	 * 返回map
	 * */
	//"a.properties"
	@SuppressWarnings("unchecked")
	public static Map<String, String> getProperties(String pathStr) {
		Properties prop = new Properties();
		try {
          //读取属性文件a.properties
         InputStream in = new BufferedInputStream (new FileInputStream(pathStr));
         prop.load(in);     //加载属性列表
         @SuppressWarnings("rawtypes")
         Map<String, String> mapProperties = new HashMap<String, String>((Map) prop);
         in.close();
         return mapProperties;
		} catch (Exception e) {
			System.out.println("GetProperties Exception:" + e.toString());
			return null;
		}
	}
	
	/**
	 * 设置配置文件
	 * @param pathStr 配置文件路径
	 * @param keyStr 键
	 * @param valueStr 值
	 * @param isAdd 是否以追加的方式,如果不追加则只记录最后一次设置的参数。
	 * 				比如需要记录姓名和年龄，不追加则只记录了年龄。
	 * @Date 2017-05-31
	 */
	public static void setProperties(String pathStr, String keyStr, String valueStr, boolean isAdd) {
		Properties prop = new Properties();
		try {
          ///保存属性到b.properties文件
          FileOutputStream oFile = new FileOutputStream(pathStr, isAdd);//true表示追加打开
          prop.setProperty(keyStr, valueStr);
          prop.store(oFile, "The New properties file");
          oFile.close();
		} catch (Exception e) {
			System.out.println(e);
		}
	}
}
