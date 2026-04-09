package com.adam.localfts.webserver.common.search;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FileType {
    /**
     * 1.文档
     */
    DOC("Microsoft Word 文档"), DOCX("Microsoft Word 文档"),
    PDF("便携式文档格式"),
    XLS("Microsoft Excel 表格"), XLSX("Microsoft Excel 表格"),
    PPT("Microsoft PowerPoint 演示文稿"), PPTX("Microsoft PowerPoint 演示文稿"),
    TXT("纯文本文件"), MD("Markdown标记语言文件"), CSV("逗号分隔表格文件"),
    XML("可扩展标记语言"), LOG("日志文件"), LRC("歌词文件"),
    /**
     * 2.图像
     */
    JPG("联合图像专家组格式"), JPEG("联合图像专家组格式"), PNG("可移植网络图形"), GIF("动态图像"),
    SVG("矢量图形格式"), BMP("位图格式"),
    /**
     * 3.音频
     */
    MP3("MPEG音频层3"), WAV("波形音频文件"), FLAC("无损音频格式"),
    M4A("MPEG-4音频"),
    /**
     * 4.视频
     */
    MP4("MPEG-4视频"), AVI("音频视频交错格式"), MKV("Matroska视频"),
    MOV("QuickTime封装格式"),RMVB("RealMedia可变比特率"),RM("RealMedia固定比特率"),
    FLV("Flash视频"),
    /**
     * 5.压缩文件
     */
    ZIP("ZIP压缩文件"), RAR("RAR压缩文件"), _7Z("7Z压缩包格式"),
    TAR("未压缩的归档文件"), TAR_GZ(".tar.gz", "由Gzip压缩的归档文件"),
    TAR_XZ(".tar.xz", "由XZ算法压缩的归档文件"), ISO("光盘镜像文件"),
    /**
     * 6.可执行文件
     */
    EXE("Windows可执行文件"), BAT("Windows批处理文件"), DLL("Windows动态链接库"),
    SH("Shell脚本文件"), JAR("Java归档文件"), CLASS("Java编译生成的字节码文件"),
    /**
     * 7.编程
     */
    HTML("超文本标记语言"),HTM("超文本标记语言"),CSS("层叠样式表"),
    JS("JavaScript源代码文件"), MIN_JS(".min.js", "压缩的JavaScript源代码文件"),
    PHP("PHP脚本文件"), JAVA("Java源代码文件"), PY("Python源代码文件"),
    SQL("结构化查询语言"), YML("YML标记语言"), JSON("JavaScript对象表示法文件"),
    ;

    private String suffix;
    private String desc;

    FileType(String desc) {
        this.desc = desc;
    }

    public String getSuffix() {
        if(suffix != null) {
            return suffix;
        }
        String name = name();
        if(name.startsWith("_")) {
            name = name.substring(1);
        }
        return "." + name.toLowerCase();
    }

    public static boolean containsEnum(String enumValue) {
        if(enumValue == null) {
            return false;
        }
        FileType[] allFileTypes = values();
        for(FileType fileType: allFileTypes) {
            if(fileType.name().equals(enumValue)) {
                return true;
            }
        }
        return false;
    }
}
