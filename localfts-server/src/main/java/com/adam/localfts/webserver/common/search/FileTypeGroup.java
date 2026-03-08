package com.adam.localfts.webserver.common.search;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
@AllArgsConstructor
public enum FileTypeGroup {
    DOCUMENT("文档", FileType.DOC, FileType.DOCX, FileType.PDF, FileType.XLS, FileType.XLSX, FileType.PPT, FileType.PPTX,
            FileType.TXT, FileType.MD, FileType.CSV, FileType.XML, FileType.LOG),
    PICTURE("图像", FileType.JPG, FileType.JPEG, FileType.PNG, FileType.SVG, FileType.BMP),
    AUDIO("音频", FileType.MP3, FileType.WAV, FileType.FLAC, FileType.M4A),
    VIDEO("视频", FileType.MP4, FileType.AVI, FileType.MKV, FileType.MOV, FileType.RMVB, FileType.RM, FileType.FLV),
    COMPRESSED_FILE("压缩文件", FileType.ZIP, FileType.RAR, FileType._7Z, FileType.TAR, FileType.TAR_GZ, FileType.TAR_XZ,
            FileType.ISO),
    EXECUTABLE_FILE("可执行文件", FileType.EXE, FileType.BAT, FileType.DLL, FileType.SH, FileType.JAR, FileType.CLASS),
    PROGRAMMING_FILE("编程文件", FileType.HTML, FileType.HTM, FileType.CSS, FileType.JS, FileType.MIN_JS, FileType.PHP,
            FileType.JAVA, FileType.PY, FileType.SQL, FileType.YML),
    ;

    private String desc;
    private List<FileType> fileTypeList;
    FileTypeGroup(String desc, FileType... fileTypes) {
        this.desc = desc;
        this.fileTypeList = new ArrayList<>();
        if(fileTypes != null) {
            this.fileTypeList.addAll(Arrays.asList(fileTypes));
        }
    }
}
