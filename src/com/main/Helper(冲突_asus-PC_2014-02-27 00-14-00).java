package com.main;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Helper {

	public final static String LineSep = System.getProperty("line.separator");

	/**
	 * 默认的日期和我们的习惯不太一样，这里只是做个纠正，并转换它的类型
	 * 
	 * 纠正后的字段范围是： minute: 0-59 hour: 0-23 day: 1-31 month: 1-12 week: 0-6 year: 实际年份
	 */
	public static Map<String, Integer> justifyCalendar(long timeInMillisecond) {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(timeInMillisecond);
		Map<String, Integer> result = new HashMap<String, Integer>();

		result.put("minute", cal.get(Calendar.MINUTE));
		result.put("hour", cal.get(Calendar.HOUR_OF_DAY));
		result.put("day", cal.get(Calendar.DAY_OF_MONTH));
		result.put("month", cal.get(Calendar.MONTH) + 1); // 一月是0，依次递加，所以要加 1
		result.put("week", cal.get(Calendar.DAY_OF_WEEK) - 1); // 星期天、一、二 ...
																// 星期六 => 1 - 7，
																// 所以要减 1
		result.put("year", cal.get(Calendar.YEAR));
		
		return result;
	}

	/**
	 * 将 justifyCalendar 返回的字段重新恢复成 millisecond 的形式 注意：可以不需要 week，如果没有设置
	 * year，则默认取当前的 year
	 * 
	 * @param mapTime
	 * @return
	 */
	public static long reverseJustifyCalendar(Map<String, Integer> mapTime) {
		Calendar cal = Calendar.getInstance();
		Integer year = mapTime.containsKey("year") ? mapTime.get("year") : cal
				.get(Calendar.YEAR);
		cal.set(year, mapTime.get("month") - 1, mapTime.get("day"),
				mapTime.get("hour"), mapTime.get("minute"), 0);
		return cal.getTimeInMillis();
	}

	/**
	 * 读取文件内容
	 * 
	 * @param path
	 * @return
	 * @throws FileNotFoundException
	 *             , IOException
	 */
	public static String readFile(String path) throws FileNotFoundException,
			IOException {
		String line;
		StringBuilder sb = new StringBuilder();

		FileReader fr = new FileReader(path);
		BufferedReader bf = new BufferedReader(fr);

		// 逐行读取任务文件
		// TODO 一个命令写成多行的形式，即行末尾加 "\" 符号
		while ((line = bf.readLine()) != null) {
			sb.append(line);
			sb.append(Helper.LineSep);
		}

		bf.close();
		fr.close();

		return sb.toString();
	}
	

	/**
	 * 写入内容文件
	 * 
	 * @param path
	 * @param content
	 * @param appendToFile
	 * @throws IOException
	 */
	public static void writeFile(String path, String content,
			Boolean appendToFile) throws Exception {
		File f = new File(path);
		
		if (!f.exists()) f.createNewFile();
		if (f.isFile() && f.canWrite()) {
			// 这里也会有 IOException 哦
			FileWriter fw = new FileWriter(new File(path), appendToFile);
			fw.write(content, 0, content.length());
			fw.flush();
			fw.close();
		} else {
			throw new Exception(path + " 不是文本文件或者文件不能写。");
		}

	}

	/**
	 * 从文件中读取可序列化的对象
	 * 
	 * @param path
	 * @return
	 * @throws ClassNotFoundException
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Serializable> T readObjectFile(String path)
			throws FileNotFoundException, IOException, ClassNotFoundException,
			Exception {

		FileInputStream fis = new FileInputStream(path);
		ObjectInputStream ois = null;

		T result;
		try {
			ois = new ObjectInputStream(fis);
			result = (T) ois.readObject();
		} catch (EOFException e) {
			// 序列化操作失败
			result = null;
		} finally {
			fis.close();
			if (ois != null) ois.close();
		}

		if (result == null) {
			throw new Exception(path + " 解序列化操作失败。");
		}

		return result;
	}

	/**
	 * 把可序列化的对象写入文件中
	 * 
	 * @param path
	 * @param object
	 * @throws Exception
	 */
	public static <T extends Serializable> void writeObjectFile(String path,
			T object) throws Exception {

		File f = new File(path);
		FileOutputStream fos = null;
		ObjectOutputStream oos = null;
		
		if (!f.exists()) f.createNewFile();
		if (f.isFile() && f.canWrite()) {
			// 这里也会有 IOException 哦
			fos = new FileOutputStream(f);
			oos = new ObjectOutputStream(fos);
			oos.writeObject(object);
			fos.close();
			oos.close();
		} else {
			throw new Exception(path + " 不是文本文件或者文件不能写。");
		}

	}

	/**
	 * MD5 加密
	 * 
	 * @param msg
	 * @return
	 */
	public static String md5(String msg) {
		String s = null;
		byte[] source = msg.getBytes();
		char hexDigits[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
				'a', 'b', 'c', 'd', 'e', 'f' };
		try {
			java.security.MessageDigest md = java.security.MessageDigest
					.getInstance("MD5");
			md.update(source);
			byte tmp[] = md.digest(); // MD5 的计算结果是一个 128 位的长整数，
			// 用字节表示就是 16 个字节
			char str[] = new char[16 * 2]; // 每个字节用 16 进制表示的话，使用两个字符，
			// 所以表示成 16 进制需要 32 个字符
			int k = 0; // 表示转换结果中对应的字符位置
			for (int i = 0; i < 16; i++) { // 从第一个字节开始，对 MD5 的每一个字节
				// 转换成 16 进制字符的转换
				byte byte0 = tmp[i]; // 取第 i 个字节
				str[k++] = hexDigits[byte0 >>> 4 & 0xf]; // 取字节中高 4 位的数字转换,
				// >>> 为逻辑右移，将符号位一起右移
				str[k++] = hexDigits[byte0 & 0xf]; // 取字节中低 4 位的数字转换
			}
			s = new String(str); // 换后的结果转换为字符串
		} catch (Exception e) {
			e.printStackTrace();
		}
		return s;
	}
	
	/**
	 * 简单的执行 cmd 命令的函数
	 * 
	 * @return 执行命令过程中输出的字符
	 */
	public static String execCMD(String cmd) throws Exception {
		Runtime run = Runtime.getRuntime();

		Process process = run.exec(cmd);// 启动另一个进程来执行命令

		BufferedInputStream bis = new BufferedInputStream(
				process.getInputStream());
		BufferedReader br = new BufferedReader(new InputStreamReader(bis));

		String line;
		StringBuilder sb = new StringBuilder();
		String lineSep = System.getProperty("line.separator");
		while ((line = br.readLine()) != null) {
			// 获得命令执行后在控制台的输出信息
			sb.append(line);
			sb.append(lineSep);
		}

		// 检查命令是否执行失败
		if (process.waitFor() != 0) {
			if (process.exitValue() != 0) // exitValue()==0表示正常结束，1：非正常结束
				throw new Exception("命令 [" + cmd + "] 退出状态值不为 0， 执行失败!");
		}
		br.close();
		bis.close();

		return sb.toString();
	}
	
	

	
	/**
	 * 生成一个隐藏的 cache 文件夹，返回文件夹地址
	 * @throws Exception 
	 */
	public static String getCacheDir(String path) throws Exception {
		File cacheDir = new File(path);
		if ( !cacheDir.exists() || cacheDir.isFile()) {
			
			if (cacheDir.exists()) cacheDir.delete();
			
			cacheDir.mkdir();
			// 通过CMD命令设置成隐藏
			Runtime.getRuntime().exec("attrib +H \"" + cacheDir.getAbsolutePath() + "\"");
		}
		
		if (!cacheDir.canWrite()) {
			throw new Exception("无法写缓存文件[ " + cacheDir.getAbsolutePath() + " ]");
		}
		
		return cacheDir.getAbsolutePath();
	}
	
	
}
