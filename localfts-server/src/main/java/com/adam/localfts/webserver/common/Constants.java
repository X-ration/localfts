package com.adam.localfts.webserver.common;

import com.adam.localfts.webserver.util.Util;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class Constants {

    public static final String[] ALLOWED_LOG_LEVELS = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR"};
    public static final List<String> ALLOWED_LOG_LEVELS_LIST = Arrays.asList(ALLOWED_LOG_LEVELS);
    public static final Pattern PATTERN_PATH_WINDOWS_ABSOLUTE = Pattern.compile("[A-Z]:(\\\\[^\\\\/:*?\"<>|]+)*?");
    public static final Pattern PATTERN_PATH_LINUX_MACOS_ABSOLUTE = Pattern.compile("/|(/[^/]+)+?");
    public static final Pattern PATTERN_PATH_WINDOWS_RELATIVE = Pattern.compile("[^\\\\]+(\\\\[^\\\\/:*?\"<>|]+)*?");
    public static final Pattern PATTERN_PATH_LINUX_MACOS_RELATIVE = Pattern.compile("[^/]+(/[^/]+)*?");
    public static final Pattern PATTERN_PATH_WINDOWS_STANDARD_RELATIVE = Pattern.compile("/|(/[^\\\\/:*?\"<>|]+)+?");
    public static final Pattern PATTERN_PATH_LINUX_MACOS_STANDARD_RELATIVE = Pattern.compile("/|(/[^/]+)+?");
    public static final Pattern PATTERN_FILE_SUFFIX_WINDOWS = Pattern.compile("(\\.[^\\\\/:*?\"<>|]+)+");
    public static final Pattern PATTERN_FILE_SUFFIX_LINUX_MACOS = Pattern.compile("(\\.[^/]+)+");
    public static final String ROOT_PATH_DEFAULT_WINDOWS = "C:";
    public static final String ROOT_PATH_DEFAULT_LINUX_MACOS = "/home";
    public static final int RUNTIME_AVAILABLE_PROCESSORS = Util.getAvailableProcessors();
    public static final int PHYSICAL_AVAILABLE_PROCESSORS = Util.getPhysicalProcessors();
    public static final Pattern PATTERN_ACTIVE_TASK_THRESHOLD = Pattern.compile("(0|[1-9][0-9]*)(\\.[0-9]*[1-9]+p)?");

    public static final String CRLF = "\r\n";
    public static final String CR = "\r";
    public static final String LF = "\n";
    public static final Pattern PATTERN_HTTP_HEADER_RANGE_COMMON = Pattern.compile("(-?[0-9]+)-(-?[0-9]+)?");
    public static final Pattern PATTERN_HTTP_HEADER_RANGE_LAST_N = Pattern.compile("-[0-9]+");
    public static final String DATE_FORMAT_FILE_STANDARD = "yyyy-MM-dd HH:mm:ss";
    public static final String FILE_INVALID_CHARACTER_WINDOWS = "\\ / : * ? \" < > |";
    public static final String FILE_INVALID_CHARACTER_LINUX_MACOS = "/";

    public static final String FOLDER_DELETE_ON_EXIT_HINT_FILE_NAME = "【删除文件夹提示】此文件夹将在局域网文件传输服务器应用(pid-${pid})退出后删除，请及时保存重要文件！";
    public static final String FOLDER_DELETE_ON_EXIT_HINT_FILE_CONTENT = "【删除文件夹提示】此文件夹将在局域网文件传输服务器应用(pid-${pid})退出后删除，请及时保存重要文件！" + System.lineSeparator()
            + "如不希望退出应用时删除文件夹，可通过如下命令强制结束进程：" + System.lineSeparator()
            + "● Windows: taskkill /PID ${pid} /F" + System.lineSeparator()
            + "● MacOS/Linux: kill -9 ${pid}" + System.lineSeparator();

}
