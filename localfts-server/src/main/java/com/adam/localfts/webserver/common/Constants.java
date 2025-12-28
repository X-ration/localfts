package com.adam.localfts.webserver.common;

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
    public static final String ROOT_PATH_DEFAULT_WINDOWS = "C:";
    public static final String ROOT_PATH_DEFAULT_LINUX_MACOS = "/home";

    public static final String CRLF = "\r\n";
    public static final String CR = "\r";
    public static final String LF = "\n";
    public static final Pattern PATTERN_HTTP_HEADER_RANGE_COMMON = Pattern.compile("(-?[0-9]+)-(-?[0-9]+)?");
    public static final Pattern PATTERN_HTTP_HEADER_RANGE_LAST_N = Pattern.compile("-[0-9]+");
    public static final String DATE_FORMAT_FILE_STANDARD = "yyyy-MM-dd HH:mm:ss";

    public static final String FOLDER_DELETE_ON_EXIT_HINT = "【删除文件夹提示】此文件夹将在局域网文件传输服务器应用(pid-${pid})退出后删除，请及时保存重要文件！";

}
