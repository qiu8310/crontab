package com.test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.main.Helper;

public class HelperTest {
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		File dir = new File(HelperTest.testDir);
		dir.mkdir();
		
		File readFile = new File(HelperTest.testReadOnlyFile);
		readFile.createNewFile();
		readFile.setReadOnly();
		
		File writeFile = new File(HelperTest.testWritableFile);
		writeFile.createNewFile();
		writeFile.setWritable(true);
		
		File readDir = new File(HelperTest.testReadOnlyDir);
		readDir.mkdir();
		readDir.setReadOnly();
	}
	
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		File readFile = new File(HelperTest.testReadOnlyFile), 
			 writeFile = new File(HelperTest.testWritableFile), 
			 readDir = new File(HelperTest.testReadOnlyDir), 
			 dir = new File(HelperTest.testDir);
		
		readFile.setWritable(true);
		readFile.delete();
		writeFile.delete();
		
		readDir.setWritable(true);
		readDir.delete();
		
		dir.delete();
	}
	
	@Before
	public void setUp() throws Exception {
		File writeFile = new File(HelperTest.testWritableFile);
		writeFile.delete();
		// 每次都重新创建一个空文件
		writeFile.createNewFile();
	}

	@After
	public void tearDown() throws Exception {
	}
	
	private final static String testDir = "testDir";
	private final static String testReadOnlyDir = "testDir/readonly";
	private final static String testReadOnlyFile = "testDir/readonly.txt";
	private final static String testWritableFile = "testDir/writable.txt";
	private final static String notExistFile = "testDir/notexist.txt";
	private final static String notExistFileInReadOnlyDir = "testDir/readonly/notexist.txt";
	

	
	
	@Test
	public void testLineSep() {
		String lineSep = System.getProperty("line.separator");
		assertEquals("LineSep", lineSep, Helper.LineSep);
	}

	@Test
	public void testJustifyCalendar() {
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());

		// 测试当前时间
		Map<String, Integer> timeMap = Helper.justifyCalendar(cal
				.getTimeInMillis());

		assertEquals(timeMap.keySet().size(), 6);

		assertEquals((long) timeMap.get("minute"), cal.get(Calendar.MINUTE));
		assertEquals((long) timeMap.get("month"), cal.get(Calendar.MONTH) + 1);

		// 手动设置时间
		cal.set(Calendar.MINUTE, 5);
		cal.set(Calendar.DAY_OF_WEEK, 1); // 设置成星期天
		cal.set(Calendar.MONTH, 2); // 设置成 3 月
		timeMap = Helper.justifyCalendar(cal.getTimeInMillis());

		assertEquals((long) timeMap.get("minute"), 5);
		assertEquals((long) timeMap.get("week"), 0); // 我们习惯星期天为 0， 而不是1
		assertEquals((long) timeMap.get("month"), 3); // 我们习惯 3 有为 3， 而不是2
	}

	@Test
	public void testReverseJustifyCalendar() {
		Calendar cal = Calendar.getInstance();
		cal.setTime(new Date());

		Map<String, Integer> mapTime = Helper.justifyCalendar(cal
				.getTimeInMillis());

		// 去掉 毫秒(1000) 和 秒(60) 的影响
		assertEquals(cal.getTimeInMillis() / 60000,
				(Helper.reverseJustifyCalendar(mapTime)) / 60000);

		mapTime.put("year", 2222);
		cal.set(Calendar.YEAR, 2222);
		assertEquals(cal.getTimeInMillis() / 60000,
				(Helper.reverseJustifyCalendar(mapTime)) / 60000);

		mapTime.put("day", 22);
		cal.set(Calendar.DAY_OF_MONTH, 22);
		assertEquals(cal.getTimeInMillis() / 60000,
				(Helper.reverseJustifyCalendar(mapTime)) / 60000);

		mapTime.put("month", 3);
		cal.set(Calendar.MONTH, 2);
		assertEquals(cal.getTimeInMillis() / 60000,
				(Helper.reverseJustifyCalendar(mapTime)) / 60000);
	}

	@Test
	public void testWriteFile() throws Exception {
		
		try {
			Helper.writeFile(HelperTest.testDir, "xxx", true);
		} catch (Exception e) {
			assertEquals(e.getMessage(), HelperTest.testDir + " 不是文本文件或者文件不能写。");
		}
		
		try {
			Helper.writeFile(HelperTest.testReadOnlyFile, "xxx", true);
		} catch (Exception e) {
			assertEquals(e.getMessage(), HelperTest.testReadOnlyFile + " 不是文本文件或者文件不能写。");
		}
		
		String content = "xxx" + Helper.LineSep;
		Helper.writeFile(HelperTest.testWritableFile, content, false);
		assertEquals(content, Helper.readFile(HelperTest.testWritableFile));
		
		Helper.writeFile(HelperTest.testWritableFile, content, true);
		assertEquals(content + content, Helper.readFile(HelperTest.testWritableFile));
		
	}

	@Test
	public void testReadFile() {
		try {
			Helper.readFile(HelperTest.notExistFile);
		} catch (FileNotFoundException e) {
			assertTrue(true);
		} catch (IOException e) {
			fail("这里一般不会发生");
		}
		
		// 上面其实已经测试了读取文件功能，这里就不测试了
	}
	
	@Test
	public void testWriteObjectFile() throws Exception {
		LinkedList<String> temp = new LinkedList<String>();
		try {
			Helper.writeObjectFile(HelperTest.testDir, temp);
		} catch (Exception e) {
			assertEquals(e.getMessage(), HelperTest.testDir + " 不是文本文件或者文件不能写。");
		}
		
		try {
			Helper.writeObjectFile(HelperTest.testReadOnlyFile, temp);
		} catch (Exception e) {
			assertEquals(e.getMessage(), HelperTest.testReadOnlyFile + " 不是文本文件或者文件不能写。");
		}
		
		LinkedList<String> l = new LinkedList<String>();
		List<String> ll;
		l.add("abc");
		l.add("123");
		
		Helper.writeObjectFile(HelperTest.testWritableFile, l);
		ll = Helper.readObjectFile(HelperTest.testWritableFile);
		assertArrayEquals(ll.toArray(), l.toArray());
		
		HashMap<String, String> mm, m = new HashMap<String, String>();
		Helper.writeObjectFile(HelperTest.testWritableFile, m);
		mm = Helper.readObjectFile(HelperTest.testWritableFile);
		assertArrayEquals(m.keySet().toArray(), mm.keySet().toArray());
		assertEquals(m.keySet().size(), 0);
		
		m.put("abc", "ABC");
		m.put("DEF", "def");
		Helper.writeObjectFile(HelperTest.testWritableFile, m);
		mm = Helper.readObjectFile(HelperTest.testWritableFile);
		assertArrayEquals(m.keySet().toArray(), mm.keySet().toArray());
		assertEquals(m.keySet().size(), 2);
	}
	
	@Test
	public void testReadObjectFile() throws Exception {

		try {
			Helper.readObjectFile(HelperTest.testWritableFile);
		} catch (Exception e) {
			assertEquals(HelperTest.testWritableFile + " 解序列化操作失败。", e.getMessage());
		}
		
		try {
			Helper.readObjectFile(HelperTest.notExistFile);
		} catch (Exception e) {
			assertEquals(FileNotFoundException.class, e.getClass());
		}
		
		// 上面其实已经测试了读取Object文件功能，这里就不测试了
	}
	
	@Test
	public void testMD5() {
		assertEquals(32, Helper.md5("123").length());
		assertEquals(32, Helper.md5("abc#$#").length());
		assertEquals(32, Helper.md5("中国").length());
		assertEquals(32, Helper.md5("").length());
		assertEquals(32, Helper.md5("0").length());
		assertNotSame(Helper.md5("0"), Helper.md5("00"));
	}
}
