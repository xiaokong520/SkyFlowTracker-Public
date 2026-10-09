package dji.v5.ux.mapkit.amap.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 文件日志工具类
 * 用于将日志同时输出到 Logcat 和文件
 */
public class FileLogger {

    private static final String TAG = "FileLogger";
    private static final String LOG_DIR = "SkyFlowTracker";
    private static final String LOG_FILE_NAME = "location_log.txt";
    private static final int MAX_LOG_SIZE = 5 * 1024 * 1024; // 5MB

    private static File logFile = null;
    private static boolean isInitialized = false;

    /**
     * 初始化日志文件
     */
    public static void init(Context context) {
        if (isInitialized) {
            return;
        }

        try {
            // 获取应用的外部存储目录（不需要额外权限）
            File logDir = new File(context.getExternalFilesDir(null), LOG_DIR);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            logFile = new File(logDir, LOG_FILE_NAME);

            // 如果文件过大，清空重新开始
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                logFile.delete();
                logFile.createNewFile();
                writeToFile("=== 日志文件已重置（超过5MB） ===\n");
            } else if (!logFile.exists()) {
                logFile.createNewFile();
            }

            isInitialized = true;

            // 写入启动标记
            writeToFile("\n\n=== 应用启动 " + getCurrentTime() + " ===\n");
            Log.i(TAG, "日志文件初始化成功: " + logFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "初始化日志文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 写入 DEBUG 级别日志
     */
    public static void d(String tag, String message) {
        Log.d(tag, message);
        writeLog("D", tag, message);
    }

    /**
     * 写入 INFO 级别日志
     */
    public static void i(String tag, String message) {
        Log.i(tag, message);
        writeLog("I", tag, message);
    }

    /**
     * 写入 WARNING 级别日志
     */
    public static void w(String tag, String message) {
        Log.w(tag, message);
        writeLog("W", tag, message);
    }

    /**
     * 写入 ERROR 级别日志
     */
    public static void e(String tag, String message) {
        Log.e(tag, message);
        writeLog("E", tag, message);
    }

    /**
     * 写入日志到文件
     */
    private static void writeLog(String level, String tag, String message) {
        if (!isInitialized || logFile == null) {
            return;
        }

        String logMessage = String.format("[%s] %s/%s: %s\n",
                getCurrentTime(), level, tag, message);
        writeToFile(logMessage);
    }

    /**
     * 写入内容到文件
     */
    private static void writeToFile(String content) {
        try {
            FileWriter writer = new FileWriter(logFile, true);
            writer.write(content);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            Log.e(TAG, "写入日志文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前时间字符串
     */
    private static String getCurrentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
        return sdf.format(new Date());
    }

    /**
     * 获取日志文件路径
     */
    public static String getLogFilePath() {
        return logFile != null ? logFile.getAbsolutePath() : "未初始化";
    }

    /**
     * 清空日志文件
     */
    public static void clearLog() {
        if (logFile != null && logFile.exists()) {
            try {
                FileWriter writer = new FileWriter(logFile, false);
                writer.write("=== 日志已清空 " + getCurrentTime() + " ===\n");
                writer.flush();
                writer.close();
                Log.i(TAG, "日志文件已清空");
            } catch (IOException e) {
                Log.e(TAG, "清空日志文件失败: " + e.getMessage());
            }
        }
    }

    /**
     * 获取日志文件大小（字节）
     */
    public static long getLogFileSize() {
        return logFile != null && logFile.exists() ? logFile.length() : 0;
    }

    /**
     * 获取日志文件大小（可读格式）
     */
    public static String getLogFileSizeReadable() {
        long size = getLogFileSize();
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.2f KB", size / 1024.0);
        } else {
            return String.format(Locale.getDefault(), "%.2f MB", size / (1024.0 * 1024.0));
        }
    }
}
