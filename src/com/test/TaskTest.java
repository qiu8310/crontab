package com.test;

import static org.junit.Assert.*;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.main.Helper;
import com.main.Task;

public class TaskTest {
	public static Task everyMinuteTask;
	public static Task powerOffEveryMinuteTask;
	public static Task startUpEveryMinuteTask;
	public static Task everyMinuteTask2;
	public static Task everyMinuteTask3;
	
	public static Task powerOffFiveClockTask;
	public static Task FiveClockTask;
	public static Task FiveClockTask3;
	public static Task startUpFiveClockTask;
	
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		TaskTest.everyMinuteTask = new Task("* * * * * ls");
		TaskTest.powerOffEveryMinuteTask = new Task("[p]* * * * * ls");
		TaskTest.startUpEveryMinuteTask = new Task("[s]* * * * * ls");
		TaskTest.everyMinuteTask2 = new Task("*/1 * * * * ls");
		TaskTest.everyMinuteTask3 = new Task("*/1 * * * * ls -l");
		
		TaskTest.powerOffFiveClockTask = new Task("[p]* 5 * * * ls");
		TaskTest.FiveClockTask = new Task("* 5 * * * ls");
		TaskTest.FiveClockTask3 = new Task("* 5 * * * ls -l");
		TaskTest.startUpFiveClockTask = new Task("[s]* 5 * * * ls");
		
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		TaskTest.everyMinuteTask = null;
		TaskTest.powerOffEveryMinuteTask = null;
		TaskTest.startUpEveryMinuteTask = null;
		TaskTest.everyMinuteTask2 = null;
		TaskTest.everyMinuteTask3 = null;
		
		TaskTest.powerOffFiveClockTask = null;
		TaskTest.FiveClockTask = null;
		TaskTest.FiveClockTask3 = null;
		TaskTest.startUpFiveClockTask = null;
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testTask() throws Exception {
		// 比较任务类型的不同
		assertNotSame(TaskTest.FiveClockTask, TaskTest.powerOffFiveClockTask);
		assertNotSame(TaskTest.startUpFiveClockTask, TaskTest.powerOffFiveClockTask);
		assertNotSame(TaskTest.startUpFiveClockTask, TaskTest.FiveClockTask);
		
		// 比较任务计划时间的不同
		assertNotSame(TaskTest.startUpFiveClockTask, TaskTest.everyMinuteTask);
		assertNotSame(TaskTest.startUpFiveClockTask, TaskTest.startUpEveryMinuteTask);
		assertNotSame(TaskTest.powerOffFiveClockTask, TaskTest.everyMinuteTask);
		assertNotSame(TaskTest.powerOffFiveClockTask, TaskTest.powerOffEveryMinuteTask);
		assertNotSame(TaskTest.FiveClockTask, TaskTest.everyMinuteTask);
	
		// 比较任务计划的CMD的不同
		assertNotSame(TaskTest.everyMinuteTask2, TaskTest.everyMinuteTask3);
		assertNotSame(TaskTest.everyMinuteTask, TaskTest.everyMinuteTask3);
		assertNotSame(TaskTest.FiveClockTask, TaskTest.FiveClockTask3);
		
		Task t = null;
		String line = null;
		// Exception
		try {
			t = new Task("* * * * ls");
			fail("期待异常，这里执行不到。");
		} catch (Exception e) {
			assertEquals("定时任务格式不正确，缺少字段 * * * * ls", e.getMessage());
		}
		try {
			line = "4 1-7,9 2-5,7,8/2,4,20-1 1,2,3,9-1,10,12/2,4,6-9 1,2/3,a,4 ls";
			t = new Task(line);
			fail("期待异常，这里执行不到。");
		} catch (Exception e) {
			assertEquals("定时格式任务不正确，时间字段错误 1,2/3,a,4 \tAt Line: " + line, e.getMessage());
		}
		
		try {
			new Task("* * 30 2 * ls"); // 2月份没有30号
			fail("期待异常，这里执行不到。");
		} catch (Exception e) {
			assertEquals("定时计划的月日组合无法运行 * * 30 2 *", e.getMessage());
		}
		
		try {
			new Task("* * 31 2,4,6 * ls"); // 2/4/6月份没有31号
			fail("期待异常，这里执行不到。");
		} catch (Exception e) {
			assertEquals("定时计划的月日组合无法运行 * * 31 2,4,6 *", e.getMessage());
		}
		
		
		// 测试创建
		t = new Task("* * * * * ls");
		Map<String, String> m= new HashMap<String, String>();
		m.put("minute", "*/1");
		m.put("hour", "*");
		m.put("day", "*");
		m.put("month", "*");
		m.put("week", "*");
		
		assertEquals(t.isPowerOffTask, false);
		assertEquals(t.isStartUpTask, false);
		assertEquals(t.rawSchedule, "* * * * *");
		assertEquals(t.schedule, m);
		assertEquals(t.cmds[0], "ls");
		
		assertTrue(new Task("[p]* * * * * ls").isPowerOffTask);
		assertTrue(new Task("[s]* * * * * ls").isStartUpTask);
	}

	@Test(timeout=1000)
	public void testGetNextRunTime() throws Exception {
		Calendar cal = Calendar.getInstance();
		
		Task task = TaskTest.powerOffEveryMinuteTask;
		task.run(cal.getTimeInMillis());
		
		// 当前运行时间，得到下一次运行时间
		nextRunTimeEqual(task, "2013 12 31 4 4", "2013 12 31 4 5");
		

		task = new Task("* * 5 1 * ls"); // 1月5日 00：00分执行一次
		nextRunTimeEqual(task, "2013 1 5 0 0", "2014 1 5 0 0");
		nextRunTimeEqual(task, "2014 1 5 0 0", "2015 1 5 0 0");
		
		task = new Task("* * 1-7 1 1 ls"); // 1月 1-7日中的第一个星期一
		nextRunTimeEqual(task, "2014 1 6 0 0", "2015 1 5 0 0");
	
		task = new Task("* * 1-7/2 2 * ls"); // 2月 2、4、6日 00:00 运行
		nextRunTimeEqual(task, "2014 2 2 0 0", "2014 2 4 0 0");
		nextRunTimeEqual(task, "2014 2 4 0 0", "2014 2 6 0 0");
		nextRunTimeEqual(task, "2014 2 6 0 0", "2015 2 2 0 0");
		
		task = new Task("* * 1,28,30 * * ls");
		nextRunTimeEqual(task, "2014 2 28 0 0", "2014 3 1 0 0");
		
		// 随机的当前时间，得到下一次运行时间
		nextRunTimeEqual2(task, "2014 2 27 0 0", "2014 2 28 0 0");
		nextRunTimeEqual2(task, "2014 2 28 0 1", "2014 3 1 0 0");
		nextRunTimeEqual2(task, "2014 2 28 5 1", "2014 3 1 0 0");
		
		task = new Task("* * 1-7 */1 1 ls"); // 每月第一个周一
		nextRunTimeEqual2(task, "2014 2 28 0 1", "2014 3 3 0 0");
		nextRunTimeEqual2(task, "2014 3 28 0 1", "2014 4 7 0 0");
		
		task = new Task("* * 31 */1 * ls"); // 每月31号
	
		nextRunTimeEqual2(task, "2014 2 28 0 1", "2014 3 31 0 0");
		nextRunTimeEqual2(task, "2014 4 28 0 1", "2014 5 31 0 0");

		
		
		
		
	}
	
	private long getMillissecondFromString (String time) {
		Map<String, Integer> m = new HashMap<String, Integer>();
		String[] parts = time.split(" ");
		m.put("year", Integer.parseInt(parts[0]));
		m.put("month", Integer.parseInt(parts[1]));
		m.put("day", Integer.parseInt(parts[2]));
		m.put("hour", Integer.parseInt(parts[3]));
		m.put("minute", Integer.parseInt(parts[4]));
		return Helper.reverseJustifyCalendar(m);
		
	}
	
	private void nextRunTimeEqual2 (Task task, String currTime, String expectNextTime) throws Exception {
		long cur = getMillissecondFromString(currTime);
		long expNext = getMillissecondFromString(expectNextTime);
		
		assertEquals(Helper.justifyCalendar(task.getNextRunTime(cur)),
				Helper.justifyCalendar(expNext));
	}
	private void nextRunTimeEqual (Task task, String currTime, String expectNextTime) throws Exception {
		long cur = getMillissecondFromString(currTime);
		long expNext = getMillissecondFromString(expectNextTime);
		
		task.run(cur);

		assertEquals(Helper.justifyCalendar(task.getNextRunTime()), Helper.justifyCalendar(expNext));
	}
	

	@Test
	public void testCanRunAt() throws Exception {
		// 每分钟的任务可以在任意时间运行
		assertTrue(TaskTest.everyMinuteTask.canRunAt(0));
		assertTrue(TaskTest.everyMinuteTask.canRunAt(1434323));
		assertTrue(TaskTest.everyMinuteTask.canRunAt(123));
		
		Calendar cal = Calendar.getInstance();
		
		cal.set(Calendar.HOUR_OF_DAY, 5);
		cal.set(Calendar.MINUTE, 0);
		
		assertTrue(TaskTest.FiveClockTask.canRunAt(cal.getTimeInMillis()));
		
		cal.set(Calendar.MINUTE, 1);
		assertFalse(TaskTest.FiveClockTask.canRunAt(cal.getTimeInMillis()));
		
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.HOUR_OF_DAY, 4);
		assertFalse(TaskTest.FiveClockTask.canRunAt(cal.getTimeInMillis()));
		
		Task task;
		
		// 测试一些复杂任务
		task = new Task("3,4,6 * * * * ls");
		taskCanRunAtMinute(task, 3, true);
		taskCanRunAtMinute(task, 4, true);
		taskCanRunAtMinute(task, 5, false);
		taskCanRunAtMinute(task, 6, true);
		
		
		task = new Task("3-5 * * * * ls");
		taskCanRunAtMinute(task, 1, false);
		taskCanRunAtMinute(task, 2, false);
		taskCanRunAtMinute(task, 3, true);
		taskCanRunAtMinute(task, 4, true);
		taskCanRunAtMinute(task, 5, true);
		taskCanRunAtMinute(task, 6, false);
		
		task = new Task("1,3,5,6-8 * * * * ls");
		taskCanRunAtMinute(task, 1, true);
		taskCanRunAtMinute(task, 2, false);
		taskCanRunAtMinute(task, 3, true);
		taskCanRunAtMinute(task, 4, false);
		taskCanRunAtMinute(task, 5, true);
		taskCanRunAtMinute(task, 6, true);
		
		task = new Task("2,3,4,6-9/2 * * * * ls");
		taskCanRunAtMinute(task, 1, false);
		taskCanRunAtMinute(task, 2, true);
		taskCanRunAtMinute(task, 3, false);
		taskCanRunAtMinute(task, 4, true);
		taskCanRunAtMinute(task, 5, false);
		taskCanRunAtMinute(task, 6, true);
		taskCanRunAtMinute(task, 7, false);
		
		
		task = new Task("2,3,4,6-9/2,3 * * * * ls");
		taskCanRunAtMinute(task, 1, false);
		taskCanRunAtMinute(task, 2, true);
		taskCanRunAtMinute(task, 3, true);
		taskCanRunAtMinute(task, 4, true);
		taskCanRunAtMinute(task, 5, false);
		taskCanRunAtMinute(task, 6, true);
		taskCanRunAtMinute(task, 7, false);
		
		task = new Task("2,3,4,6-9/2,3,4-9 * * * * ls");
		taskCanRunAtMinute(task, 1, false);
		taskCanRunAtMinute(task, 2, true);
		taskCanRunAtMinute(task, 3, true);
		taskCanRunAtMinute(task, 4, true);
		taskCanRunAtMinute(task, 5, false);
		taskCanRunAtMinute(task, 6, true);
		taskCanRunAtMinute(task, 7, true);
		
		// 如果数字超过了实际允许的会怎么样
		task = new Task("1-100 * * * * ls");
		taskCanRunAtMinute(task, 1, true);
		taskCanRunAtMinute(task, 2, true);
		taskCanRunAtMinute(task, 3, true);
		taskCanRunAtMinute(task, 4, true);
		taskCanRunAtMinute(task, 5, true);
		taskCanRunAtMinute(task, 6, true);
		
		// 测试一些多字段的
		task = new Task("2 6 8 7 1 ls"); // 7月8日 6:2 星期一
		taskCanRunAtMutilField(true, task, 2, 6, 8, 7, 1);
		taskCanRunAtMutilField(false, task, 2, 6, 8, 7, 2);
		taskCanRunAtMutilField(false, task, 3, 6, 8, 7, 1);
		
		task = new Task("* * 1-7 1 1 ls"); // 一月份第一个星期一的 00:00
		taskCanRunAtMutilField(true, task, 0, 0, 2, 1, 1);
		taskCanRunAtMutilField(true, task, 0, 0, 3, 1, 1);
		taskCanRunAtMutilField(false, task, 0, 0, 3, 1, 2);
		
		task = new Task("1-8/2 22-6 * * * ls"); // 22点到6点，2、4、6、8分的时候
		taskCanRunAtMutilField(true, task, 2, 22);
		taskCanRunAtMutilField(true, task, 2, 23);
		taskCanRunAtMutilField(true, task, 2, 6);
		taskCanRunAtMutilField(true, task, 4, 0);
		taskCanRunAtMutilField(false, task, 5, 4);
		taskCanRunAtMutilField(false, task, 4, 7);
	}
	
	private void taskCanRunAtMutilField(Boolean canRun, Task task, Integer... fields) {
		Calendar cal = Calendar.getInstance();
		for (int i = 0; i < fields.length; i++) {
			switch (i) {
			case 0: cal.set(Calendar.MINUTE, fields[i]); break;
			case 1: cal.set(Calendar.HOUR_OF_DAY, fields[i]); break;
			case 2: cal.set(Calendar.DAY_OF_MONTH, fields[i]); break;
			case 3: cal.set(Calendar.MONTH, fields[i] - 1); break;
			case 4: cal.set(Calendar.DAY_OF_WEEK, fields[i] + 1); break;
			}
		}
	}
	
	private void taskCanRunAtMinute(Task task, Integer minute, Boolean canRun) {
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.MINUTE, minute);
		if (canRun) {
			assertTrue(task.canRunAt(cal.getTimeInMillis()));
		} else {
			assertFalse(task.canRunAt(cal.getTimeInMillis()));
		}
	}

	@Test (timeout = 2000 )
	public void testRun() {
	}

	@Test
	public void testExecCMD() {
	}

	@Test
	public void testToString() {
		
	}

}
