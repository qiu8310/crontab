package com.main;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class Task implements Serializable {
	private static final long serialVersionUID = 7165811152451154376L;

	// 在计划任务前加上 S 字符，就可以让该计划任务开机时一定运行一次
	public Boolean isStartUpTask = false;

	// 在计划任务前加上 P 字符时，如果该任务在关机时间段内本来需要执行，则在开机后一段时间内会自动运行一次
	public Boolean isPowerOffTask = false;
	
	// 计划任务前加了 A 字符，后面可以接一串数字，表示计划任务在创建或执行过程中遇到任务错误，都会弹窗提醒用户
	// 后面接数字的意义， n： 弹出n秒后就弹窗自动消失； 如果不设置数字，或设置成0，则弹出窗口后就不消失
	public Integer alert = -1;

	// 时间计划，一个是原生的，一个是解析成单个字段对应的
	public String rawSchedule;
	public Map<String, String> schedule;

	// 命令
	public String[] cmds;
	public String line;

	// 匹配时间字段写法是否正确，最复杂的时间字段是 3-4,6,7,10-14/2,3
	private final static Pattern timeFieldPattern = Pattern
			.compile("(^\\*|^\\d+([,\\-]\\d+)*)(/\\d+([,\\-]\\d+)*)?$");

	// 每个时间字段对应的最大值
	@SuppressWarnings("serial")
	private final static Map<String, Integer> fieldMaxNumber = new HashMap<String, Integer>() {
		{
			put("minute", 59);
			put("hour", 23);
			put("day", 31);
			put("month", 12);
			put("week", 6);
		}
	};

	// 每个时间字段对应的最小值
	@SuppressWarnings("serial")
	private final static Map<String, Integer> fieldMinNumber = new HashMap<String, Integer>() {
		{
			put("minute", 0);
			put("hour", 0);
			put("day", 1);
			put("month", 1);
			put("week", 0);
		}
	};

	// 任务下次运行时间，只有 powerOffTask 才需要这个字段
	private long nextRunTime;

	// 通过 schedule 得到的每个字段下面所允许的所有时间值
	private Map<String, List<Integer>> allowedFieldNumbers;

	@SuppressWarnings("serial")
	public Task(String line) throws Exception {
		this.line = line;
		
		// 设置计划任务的类型 A: Alert, P: Poweroff, S: Startup
		if (line.charAt(0) == '[') {
			Integer last = line.indexOf("]");
			if (last == -1) throw new Exception("计划任务前面的标志位设置有误 " + line);
			for (Integer i = 1; i < last ; i++) {
				switch (line.charAt(i)) {
				case 'A':
				case 'a':
					this.alert = 0;
					Integer idx = i+1;
					String timeout;
					char next = line.charAt(i+1);
					while (next >= '0' && next <= '9') {
						i++;
						next = line.charAt(i+1);
					}
					timeout = line.substring(idx, i+1);
					if (timeout.length() > 0) this.alert = Integer.parseInt(timeout);
					break;
				case 'P':
				case 'p':
					this.isPowerOffTask = true;
					break;
				case 'S':
				case 's':
					this.isStartUpTask = true;
					break;
				}
			}
			this.isPowerOffTask = true;
			
			
			line = line.substring(last+1).trim();
		}
		
		final String[] lineParts = line.split("\\s+", 6);
		if (lineParts.length != 6) {
			throw new Exception("定时任务格式不正确，缺少字段 " + line);
		}

		for (Integer i = 0; i < 5; i++) {
			if (!Task.timeFieldPattern.matcher(lineParts[i]).matches()) {
				throw new Exception("定时格式任务不正确，时间字段错误 " + lineParts[i]
						+ " \tAt Line: " + line);
			}
		}

		this.rawSchedule = lineParts[0] + " " + lineParts[1] + " "
				+ lineParts[2] + " " + lineParts[3] + " " + lineParts[4];

		final String minute = this.rawSchedule.equals("* * * * *") ? "*/1"
				: lineParts[0];
		this.schedule = new HashMap<String, String>() {
			{
				put("minute", minute);
				put("hour", lineParts[1]);
				put("day", lineParts[2]);
				put("month", lineParts[3]);
				put("week", lineParts[4]);
			}
		};

		this.cmds = lineParts[5].split("\\s+&&\\s+");

		// 获取每个时间字段允许的所有值
		this.allowedFieldNumbers = this.getAllowedFieldNumbers();

		
		// 检查通过这个允许的值是否能够得到一个下次运行时间，如果不能，抛出异常
		this.checkFieldNumbers();
		
		
	}

	/**
	 * 得到当前计划任务的下次运行时间
	 * 
	 * @return
	 * @throws Exception
	 */
	public long getNextRunTime() throws Exception {

		if (this.nextRunTime == 0) {
			Calendar cal = Calendar.getInstance();
			cal.setTime(new Date());
			this.nextRunTime = this.figureOutNextRunTimeByAnyTime(cal.getTimeInMillis());
		}

		return this.nextRunTime;
	}

	/**
	 * 当前计划任务相对于指定时间的下一次运行时间
	 * 	和上面的不同之处是这个不会缓存计算到的 nextRunTime 的， 
	 * 	nextRunTime 在运行的时候会自动重新计算
	 * 
	 * @param timeInMillisecond
	 * @return
	 * @throws Exception
	 */
	public long getNextRunTime(long timeInMillisecond) throws Exception {

		return this.figureOutNextRunTimeByAnyTime(timeInMillisecond);
	}

	/**
	 * 时分不变，通过改变 日、月、年，来得到一个符合的周，从而得到一个合适的下次运行时间
	 * 
	 * 前提： 
	 * 1、保证此字段的 分、时 不变 2、当前的 日、月 是在允许的值中的，年没有所谓的是否允许，只要从小向上一步步加1就行
	 * 
	 * 算法： 
	 * 1、保持 年 不变，一步步增加 日、月，看当前的 年、月、日、时、分 组合起来得到的 周 是否符合，符合则得到了结果 
	 * 2、如果第 1步的结果不符合，同时 日、月 的值已经增大到了最大，则 年 + 1，日、月 置为最小值，再跳到第 1 步
	 * 
	 * 
	 * @param timeMap
	 * @return
	 * @throws Exception
	 *             找不到合适的下次运行时间 （年份加到溢出了）
	 */
	private long figureOutNextRunTimeByDMY(Map<String, Integer> timeMap)
			throws Exception {

		Calendar cal = Calendar.getInstance();
		String[] keys = { "day", "month" }; // 循环用的关键字

		// while (timeMap)

		Boolean got; // 是否找到了一个合适的值（没有计算周是否符合）

		Integer currTime; // 当前指定的 日/月
		List<Integer> allowTimes; // 所有允许的 日/月 值
		Integer allowSize, // 所有允许的 日/月 值的总个数
		index; // 指定的 日/月 在允许的 日/月 中的索引

		// 最大的允许的年份
		long maxYear = cal.getActualMaximum(Calendar.YEAR) - 10;

		while (true) {
			got = false;
			for (String key : keys) {
				currTime = timeMap.get(key);
				allowTimes = allowedFieldNumbers.get(key);
				
				// 是月的话，如果上次日期是 29、30、31，就需要判断下当前月是否支持设置这些日期
				if (key.equals("month") && timeMap.get("day") > 28) {
					allowTimes = this.reviseAllowMonths(allowTimes, timeMap.get("year"), timeMap.get("day"));
					if (allowTimes.size() == 0) break;
				}

				// 上一个时间字段比指定的大，下面的字段只要保持和指定的相等，就可以保证是最临近的下次运行时间
				if (got) {
					timeMap.put(key, currTime);
					continue;
				}

				index = allowTimes.indexOf(currTime);
				allowSize = allowTimes.size();

				// 指定的时间是最大值
				if (index + 1 == allowSize) {
					timeMap.put(key, allowTimes.get(0)); // 没有比当前时间大的，只能取最小的值了
				} else {
					got = true;
					timeMap.put(key, allowTimes.get(index + 1));
				}
			}

			if (!got) {
				// 年 + 1， 其它值设置成最小的
				timeMap.put("year", timeMap.get("year") + 1);
				timeMap.put("day", allowedFieldNumbers.get("day").get(0));
				timeMap.put("month", allowedFieldNumbers.get("month").get(0));

				if (maxYear < timeMap.get("year")) {
					throw new Exception("找不到合适的下次运行时间（年份已经加到了最大值了）。");
				}
			}

			cal.setTimeInMillis(Helper.reverseJustifyCalendar(timeMap));

			// 周在计划时间中，跳出循环， 我之前直接手动算周，结果忘记了周是我经过修正的
			if (this.canRunAt(cal.getTimeInMillis()))
				break;
		}
		return cal.getTimeInMillis();
	}

	/**
	 * 根据当前运行时间计算出此计划任务下次运行的时间（运行时间不考虑秒和毫秒，不管它们是不是0，都把它当作0）
	 * 
	 * 思路： 计划任务中有周，没有年，关系比较复杂。但我们可以分开来考虑，分、时的变化不影响周。要算周时，可以动态高度年去得到一个合适的周
	 * 
	 * 算法： 1、因为当前时间是可运行时间，所以保持日、月、周不变，只改变分、时，看能否得出一个比当前时间大的值，能的话这个就是下次运行时间
	 * 2、第1步没取到，则分别取分、时的最小值，并保持不变，改变日、月、年（不是周），来计算出下次运行时间 => 这一步的具体算法放在
	 * figureOutNextRunTimeByDMY 函数中
	 * 
	 * @return
	 * @throws Exception
	 */
	private long figureOutNextRunTimeByRunTime(long timeInMillisecond)
			throws Exception {
		// 当前指定的时间的组合
		Map<String, Integer> timeMap = Helper
				.justifyCalendar(timeInMillisecond);


		Boolean got = false; // 是否找到了一个合适的值
		String[] keys = { "minute", "hour" }; // 循环用的关键字

		Integer currTime; // 当前指定的时/分
		List<Integer> allowTimes; // 所有允许的时/分值
		Integer allowSize, // 所有允许的时/分值的总个数
		index; // 指定的 时/分 在允许的 时/分 中的索引

		for (String key : keys) {

			currTime = timeMap.get(key);
			allowTimes = allowedFieldNumbers.get(key);

			// 上一个时间字段比指定的大，下面的字段只要保持和指定的相等，就可以保证是最临近的下次运行时间
			if (got) {
				timeMap.put(key, currTime);
				continue;
			}

			index = allowTimes.indexOf(currTime);
			allowSize = allowTimes.size();

			// 指定的时间是最大值
			if (index + 1 == allowSize) {
				timeMap.put(key, allowTimes.get(0)); // 没有比当前时间大的，只能取最小的值了
			} else {
				got = true;
				timeMap.put(key, allowTimes.get(index + 1));
			}

		}
		
		if (got) {
			return Helper.reverseJustifyCalendar(timeMap);
		} else {
			return this.figureOutNextRunTimeByDMY(timeMap);
		}
	}
	
	/**
	 * 根据年份日期修改所有允许的 month 值，去掉那些不可能的
	 */
	private List<Integer> reviseAllowMonths (List<Integer> allows, Integer year, Integer day) {
		List<Integer> result = new ArrayList<Integer>(allows);
		
		if (day == 29) {
			Calendar cal = Calendar.getInstance();
			cal.set(Calendar.YEAR, year);
			cal.set(Calendar.MONTH, Calendar.FEBRUARY);
			Integer maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
			if (maxDay < 29) {
				result.remove(2);
			}
		}
		if (day == 30) {
			result.remove(2);
		}
		if (day == 31) {
			result.remove(2);
			result.remove(4);
			result.remove(6);
			result.remove(9);
			result.remove(11);
		}
		
		return result;
	}
	/**
	 * 根据年份月份修改所有允许的 day 值，去掉那些不可能的
	 */
	private List<Integer> reviseAllowDays (List<Integer> allows, Map<String, Integer> timeMap) {
		
		List<Integer> result = new ArrayList<Integer>();
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(Helper.reverseJustifyCalendar(timeMap));
		
		Integer maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

		// 不能直接删除 allows 中的数据，它是引用的，删除后对之后的遍历会有影响
		for (Integer i : allows) {
			if (i <= maxDay) {
				result.add(i);
			}
		}
		
		return result;
	}

	/**
	 * 根据任意时间来获取计划任务的下次运行的时间
	 * 
	 * 思路： 得到一个最小的 月、日、时、分 组合，使得它可以匹配计划任务的 月、日、时、分，
	 * 再判断下它的周符不符合条件，如果符合则返回结果；如果不符合， 则将时、分设置成最小值，再扔给 figureOutNextRunTimeByDMY
	 * 处理。
	 * 
	 * 所以此处算法关键是如何根据任意一个时间得到这个最小的月、日、时、分组合
	 * 
	 * 算法：
	 * 设置 got = false（表示：是否找到了一个比指定时间大的值，如果为true预示着之后的所有字段都取最小值）
	 * 设置 back = false (表示：按当前遍历的字段是否是从下一个字段回退过来的，回退过来同时表示之前保存的值需要加1码)
	 * 	1、按 month、day、hour、minute 的顺序遍历
	 *  2、修正：当前遍历的是否是 day，是day的话需要根据当前的年份和月份，算出最大允许的day，和计划的所有允许的day取个交集
	 *  	如果这个交集是个空集（每月的最大天数会变化，所以对于那些日数设置成31或30的偶尔会出现空集）， 回退 back = true 
	 *  3、
	 *  	如果 got = true
	 *  		取当前允许的最小值
	 *  	如果 back = true
	 *  		取出之前此字段保存的值，看还能否加一码
	 *  			如果能加，此字段设置成加一码后的值，got = true
	 *  			如果不能加 => 当前字段是否无法再后退了 ? 退出循环 : 继续后退 back = true
	 *  	got = false && back = false
	 *  		如果能取到和指定值相等的值，则保存此值，继续遍历
	 *  		如果所有值都小于指定值， back = true
	 *  		剩下的情况，取一个正好比指定值大的值，保存下来， got = true
	 *  
	 *  4、退出遍历后
	 *  	这样得到了一个最小的 month、day、hour、minute组合，可以符合正好比指定时间大，同时满足当前计划任务的对这四个值的要求
	 *  	再结合指定的 year，看下这个组合得到的 week 是否符合计划任务要求
	 *  		符合的话就返回这个值
	 *  		不符合就取 hour、minute的最小值，同时交给之前写的另一个算法：处理 year month day 和 week 之前的关系
	 *  
	 * 
	 * @param timeInMillisecond
	 * @return
	 * @throws Exception
	 */
	private long figureOutNextRunTimeByAnyTime(long timeInMillisecond)
			throws Exception {
		// 可以用上面的算法就用上面的
		if (this.canRunAt(timeInMillisecond)) {
			return this.figureOutNextRunTimeByRunTime(timeInMillisecond);
		}
		
		Map<String, Integer> timeMap = Helper
				.justifyCalendar(timeInMillisecond);
		
		String[] keys = {"month", "day", "hour", "minute"};
		String key;
		Integer keyIdx;
		
		List<Integer> allows; // 所有允许的时间值
		Integer allowSize, allowMin, allowMax, currVal, currIdx;
		Boolean got = false, 
				back = false;
		for (keyIdx = 0; keyIdx < keys.length; keyIdx++) {
			key = keys[keyIdx];
			
			allows = this.allowedFieldNumbers.get(key);
			
			// 根据年份月份修改 days
			if (key.equals("day") && allows.get(allows.size() - 1) > 28) {
				allows = this.reviseAllowDays(allows, timeMap);
				if (allows.size() == 0) {
					back = true;
					keyIdx = keyIdx - 2; // 当前 keyIdx = 1，所以肯定可以后退，减2后再循环的时候会加1，所以不用担心keyIdx = -1
					continue;
				}
			}
		
			
			allowSize = allows.size();
			allowMin = allows.get(0);
			allowMax = allows.get(allowSize - 1);
			currVal  = timeMap.get(key);
			currIdx  = allows.indexOf(currVal);
;
			if (got == true) {
				timeMap.put(key, allowMin);
			} else if (back == true) {
				if (currVal < allowMax) {
					// 取出一个比当前大的值放到 timeMap 中
					for (int i : allows) {
						if (i > currVal) {
							timeMap.put(key, i);
							break;
						}
					}
					got = true;
					back = false;
					continue;
					
				} else {
					//System.out.println(keyIdx);
					// 回退到了最顶层，退出循环
					if (keyIdx == 0) {
						break;
					// 继续回退
					} else {
						back = true;
						keyIdx = keyIdx - 2;
						continue;
					}
				}
			} else {
				//如果能取到和指定值相等的值，则保存此值，继续遍历 back = false
				//如果所有值都小于指定值， back = true
				//剩下的情况，取一个正好比指定值大的值，保存下来， got = true
				if (currIdx != -1) {
					timeMap.put(key, currVal);
					back = false;
					continue;
				} else if (allowMax < currVal) {
					back = true;
					keyIdx = keyIdx - 2;
					continue;
				} else {
					for (int j : allows) {
						if (j > currVal) {
							timeMap.put(key, j);
							break;
						}
					}
					got = true;
				}
			}
		}

		// 这样得到了一个最小的 month、day、hour、minute组合，可以符合正好比指定时间大，同时满足当前计划任务的对这四个值的要求
		// 再结合指定的 year，看下这个组合得到的 week 是否符合计划任务要求
		// 符合的话就返回这个值
		// 不符合就取 hour、minute的最小值，同时交给之前写的另一个算法：处理 year month day 和 week 之前的关系
		if (got == true) {
			long milliSeconds = Helper.reverseJustifyCalendar(timeMap);
			if (this.canRunAt(milliSeconds)) {
				return milliSeconds;
			}
		}
		timeMap.put("minute", this.allowedFieldNumbers.get("minute").get(0));
		timeMap.put("hour", this.allowedFieldNumbers.get("hour").get(0));
		//System.out.println(timeMap);
		return this.figureOutNextRunTimeByDMY(timeMap);
	}

	/**
	 * 计划任务在指定的时间是否能运行
	 * 
	 * @param timeInMillisecond
	 * @return
	 */
	public Boolean canRunAt(long timeInMillisecond) {

		List<Integer> numbers;
		Map<String, Integer> time;

		time = Helper.justifyCalendar(timeInMillisecond);

		for (String key : this.allowedFieldNumbers.keySet()) {
			numbers = this.allowedFieldNumbers.get(key);
			// System.out.println(field + ": " + numbers.toString());
			if (!numbers.contains(time.get(key)))
				return false;
		}
		return true;
	}

	/**
	 * 运行任务 （要保证提供的时间是可以运行的时间，否则会报错
	 * 
	 * @throws Exception
	 * @return 返回命令执行时输出的内容
	 */
	public String run(long timeInMillisecond) throws Exception {
		if (!this.canRunAt(timeInMillisecond)) {
			throw new Exception("命令的计划时间不符合当前提供的时间，所以无法执行");
		}
		
		StringBuilder sb = new StringBuilder();
		for (String c : this.cmds) {
			sb.append(Helper.execCMD(c));
		}

		// 获取下次运行时间
		this.nextRunTime = this.figureOutNextRunTimeByRunTime(timeInMillisecond);

		return sb.toString();
	}



	/**
	 * 内部函数，根据 schedule 来获取每时间字段允许的所有值
	 * 
	 * @return
	 */
	private Map<String, List<Integer>> getAllowedFieldNumbers() {

		// 返回值
		Map<String, List<Integer>> result = new HashMap<String, List<Integer>>();

		// TASK_FILE 中前5个字段表示的含意
		String[] fields = { "minute", "hour", "day", "month", "week" };

		// * 表示字段只能是最小值
		// crontab 有这样一个规则：第一个非 * 的字段，其之前的所有 * 字段只能表示为 最小值 ， 其之后的所有 * 字段可以表示为任意值
		Boolean starRepresentMin = true;

		// 用来记录单个 field 中所能允许的所有值
		List<Integer> numbers;

		// 单个 field 中 "/" 分隔的两段所包含的所有值
		Set<Integer> preHalfNumbers, postHalfNumbers;

		// TASK_FILE 中单个字段对应的值
		String fieldRawValue;

		Integer rangeStart, rangeEnd, rangeGo,
		// 单个字段允许的最小值和最大值，是个常量
		min, max;

		// 遍历每个字段，分别计算出它能包含的所有值
		for (final String field : fields) {

			fieldRawValue = this.schedule.get(field);
			max = Task.fieldMaxNumber.get(field);
			min = Task.fieldMinNumber.get(field);

			// 再对 fieldRawValue 进行解析
			// 最简单情况，当前的 fieldRawValue 是 *
			if (fieldRawValue.equals("*")) {
				numbers = new ArrayList<Integer>();
				if (starRepresentMin) {
					numbers.add(min);
				} else {
					for (rangeGo = min; rangeGo <= max; rangeGo++) {
						numbers.add(rangeGo);
					}
				}
				// 复杂情况，可以将 fieldRawValue 拆成两半(也有可能只有一半)，一半是 / 之前的，一半是 /
				// 之后的，每半的解析方法是一致的，都可以包含这些值： 4,6-8,10-2
			} else {
				starRepresentMin = false;

				preHalfNumbers = new HashSet<Integer>();
				postHalfNumbers = new HashSet<Integer>();

				Set<Integer> refHalfNumbers;

				Boolean isFirstHalf = true;
				for (final String half : fieldRawValue.split("/", 2)) {
					// 获取对应数据集的引用
					refHalfNumbers = isFirstHalf ? preHalfNumbers
							: postHalfNumbers;

					for (final String perHalf : half.split(",")) {
						if (perHalf.indexOf("-") >= 0) {
							String[] parts = perHalf.split("-");
							rangeStart = new Integer(parts[0]);
							rangeEnd = new Integer(parts[1]);

							if (rangeStart <= rangeEnd) {
								rangeStart = Math.max(rangeStart, min);
								rangeEnd = Math.min(rangeEnd, max);
								for (rangeGo = rangeStart; rangeGo <= rangeEnd; rangeGo++) {
									refHalfNumbers.add(rangeGo);
								}
							} else {
								for (rangeGo = rangeStart; rangeGo <= max; rangeGo++) {
									refHalfNumbers.add(rangeGo);
								}
								for (rangeGo = rangeEnd; rangeGo >= min; rangeGo--) {
									refHalfNumbers.add(rangeGo);
								}
							}
						} else {
							if (perHalf.equals("*")) {
								for (rangeGo = min; rangeGo <= max; rangeGo++) {
									refHalfNumbers.add(rangeGo);
								}
							} else {
								rangeGo = new Integer(perHalf);
								if (rangeGo >= min && rangeGo <= max)
									refHalfNumbers.add(rangeGo);
							}
						}
					}
					isFirstHalf = false;
				}

				// 再来分析数据 postHalfNumbers 代表的是每 （可能为空）， preHalfNumbers 代表的是所有的数
				if (postHalfNumbers.isEmpty()) {
					numbers = new ArrayList<Integer>(preHalfNumbers);
				} else {
					numbers = new ArrayList<Integer>();

					for (Integer num : preHalfNumbers) {
						if (numbers.contains(num))
							continue;
						for (Integer per : postHalfNumbers) {
							if (num % per == 0) {
								numbers.add(num);
								break;
							}
						}
					}
				}
			}

			// 保证了数据按从小到大排序，并且数据在指定的范围内
			Collections.sort(numbers);
			result.put(field, numbers);
		}

		return result;
	}

	
	/**
	 * 根据所有允许的值，判断下能否得到一个有效的下次运行时间
	 * @throws Exception 
	 * 
	 */
	private void checkFieldNumbers () throws Exception {
		// 检查下在此时间规则下是否可以得到一个下次运行时间，不能的话抛出异常
		for (String field : this.allowedFieldNumbers.keySet()) {
			if (this.allowedFieldNumbers.get(field).size() == 0) {
				throw new Exception("定时计划不可能运行，请修改 " + this.rawSchedule);
			}
		}

		// 每个月份的最大天数不同，这里需要做特殊处理
		// 1、day的最小值是31的话，month不能是2、4、6、9、11这些月的组合
		// 2、day的最小值是30的话，month不能只是2月
		Integer minDay = this.allowedFieldNumbers.get("day").get(
				this.allowedFieldNumbers.get("day").size() - 1);
		List<Integer> months = this.allowedFieldNumbers.get("month");
		
		if (minDay == 31) {
			Boolean allow = false;
			@SuppressWarnings("serial")
			List<Integer> allowMonths = new ArrayList<Integer>(){{
				add(1);add(3);add(5);add(7);add(8);add(10);add(12);
			}};
			for (Integer month : months) {
				if (allowMonths.indexOf(month) != -1) {
					allow = true;
					break;
				}
			}
			if (!allow) throw new Exception("定时计划的月日组合无法运行 " + this.rawSchedule);
		} else if (minDay == 30) {
			if (months.size() == 1 && months.get(0) == 2) {
				throw new Exception("定时计划的月日组合无法运行 " + this.rawSchedule);
			}
		}
	}
	
	@Override
	public String toString() {
		return "Task: " + this.line;

	}


}
