package com.wtz.ffmpeg.utils;

public class DateTimeUtil {

    /**
     * 把剩余毫秒数转化成“时:分:秒”字符串
     *
     * @param timeMilli
     * @return
     */
    public static String changeRemainTimeToHms(long timeMilli) {
        if (timeMilli == 0) {
            return "00:00:00";
        }
        int totalSeconds = Math.round((float) timeMilli / 1000);// 毫秒数转秒数，毫秒部分四舍五入
        int second = totalSeconds % 60;// 秒数除60得分钟数再取余得秒数
        int minute = totalSeconds / 60 % 60;// 秒数除两个60得小时再取余得分钟数
        int hour = totalSeconds / 60 / 60;// 秒数除两个60得小时数
        String hourString = formatTime(String.valueOf(hour));
        String minuteString = formatTime(String.valueOf(minute));
        String secondString = formatTime(String.valueOf(second));
        return hourString + ":" + minuteString + ":" + secondString;
    }

    private static String formatTime(String original) {
        if (original != null && original.length() < 2) {
            original = "0" + original;
        }
        return original;
    }

}
