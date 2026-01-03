package com.example.homeinventorymanager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 日期工具类：计算有效期间隔、判断是否过期/即将过期
 */
public class DateUtil {
    // 统一日期格式（和我们选择的日期格式一致：yyyy-MM-dd）
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);

    /**
     * 计算目标日期（有效期）与当前日期的间隔天数
     * @param targetDateStr 目标日期字符串（yyyy-MM-dd）
     * @return 间隔天数：正数=未来日期（未过期），负数=已过期，0=当天，-1=日期格式错误
     */
    public static int getDayInterval(String targetDateStr) {
        // 加强空值判断：避免传入 null 导致后续报错
        if (targetDateStr == null || targetDateStr.trim().isEmpty() || targetDateStr.equals("未设置")) {
            return -1;
        }

        try {
            // 解析目标日期（有效期）
            Date targetDate = DATE_FORMAT.parse(targetDateStr);
            // 获取当前日期（忽略时分秒，只比较年月日）
            Date currentDate = new Date();
            String currentDateStr = DATE_FORMAT.format(currentDate);
            currentDate = DATE_FORMAT.parse(currentDateStr);

            // 计算毫秒数差值，转换为天数
            long timeDiff = targetDate.getTime() - currentDate.getTime();
            return (int) (timeDiff / (1000 * 60 * 60 * 24));
        } catch (ParseException e) {
            e.printStackTrace();
            return -1; // 日期格式错误返回-1，不抛出异常
        }
    }

    /**
     * 判断是否为已过期物品
     * @param validDateStr 有效期字符串（yyyy-MM-dd）
     * @return true=已过期，false=未过期/无有效期
     */
    public static boolean isExpired(String validDateStr) {
        int dayInterval = getDayInterval(validDateStr);
        return dayInterval != -1 && dayInterval < 0;
    }

    /**
     * 判断是否为7天内即将过期物品
     * @param validDateStr 有效期字符串（yyyy-MM-dd）
     * @return true=7天内即将过期，false=非即将过期/无有效期
     */
    public static boolean isWillExpireIn7Days(String validDateStr) {
        int dayInterval = getDayInterval(validDateStr);
        return dayInterval != -1 && dayInterval >= 0 && dayInterval <= 7;
    }
}