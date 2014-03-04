package com.main;
/**
 * 无法获取任务下次运行时间
 * @author qiuzhonglei
 *
 */
public class TimeUngetableException extends RuntimeException {
	public TimeUngetableException(String message) {
        super(message);
    }
}
