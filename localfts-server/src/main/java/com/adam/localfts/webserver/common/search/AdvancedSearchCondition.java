package com.adam.localfts.webserver.common.search;

import com.adam.localfts.webserver.common.compress.FolderCompressStatus;
import com.adam.localfts.webserver.util.Util;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
public class AdvancedSearchCondition {

    private Boolean directory;
    @Getter(AccessLevel.NONE)
    private Date lastModifiedLower, lastModifiedUpper;
    /**
     * 只对文件生效
     */
    private Long fileSizeLower, fileSizeUpper;
    /**
     * 只对文件夹生效
     */
    private FolderCompressStatus folderCompressStatus;
    private Long compressedFileSizeLower, compressedFileSizeUpper;
    @Getter(AccessLevel.NONE)
    private Date compressedFileLastModifiedLower,compressedFileLastModifiedUpper;

    @Getter(AccessLevel.NONE)
    private final SimpleDateFormat simpleDateFormat = Util.getSimpleDateFormat();

    public boolean isEmpty() {
        return directory == null && fileSizeLower == null && fileSizeUpper == null
                && lastModifiedLower == null && lastModifiedUpper == null && folderCompressStatus == null
                && compressedFileSizeLower == null && compressedFileSizeUpper == null
                && compressedFileLastModifiedLower == null && compressedFileLastModifiedUpper == null;
    }

    public void setLastModifiedLower(String lastModifiedLower) throws ParseException {
        this.lastModifiedLower = StringUtils.isEmpty(lastModifiedLower) ? null : simpleDateFormat.parse(lastModifiedLower);
    }

    public String getLastModifiedLower() {
        return lastModifiedLower == null ? null : simpleDateFormat.format(lastModifiedLower);
    }

    public void setLastModifiedUpper(String lastModifiedUpper) throws ParseException{
        this.lastModifiedUpper = StringUtils.isEmpty(lastModifiedUpper) ? null : simpleDateFormat.parse(lastModifiedUpper);
    }

    public String getLastModifiedUpper() {
        return lastModifiedUpper == null ? null : simpleDateFormat.format(lastModifiedUpper);
    }

    public void setCompressedFileLastModifiedLower(String compressedFileLastModifiedLower) throws ParseException{
        this.compressedFileLastModifiedLower = StringUtils.isEmpty(compressedFileLastModifiedLower) ? null : simpleDateFormat.parse(compressedFileLastModifiedLower);
    }

    public String getCompressedFileLastModifiedLower() {
        return compressedFileLastModifiedLower == null ? null : simpleDateFormat.format(compressedFileLastModifiedLower);
    }

    public void setCompressedFileLastModifiedUpper(String compressedFileLastModifiedUpper) throws ParseException{
        this.compressedFileLastModifiedUpper = StringUtils.isEmpty(compressedFileLastModifiedUpper) ? null : simpleDateFormat.parse(compressedFileLastModifiedUpper);
    }

    public String getCompressedFileLastModifiedUpper() {
        return compressedFileLastModifiedUpper == null ? null : simpleDateFormat.format(compressedFileLastModifiedUpper);
    }

    private String formatDate(Date date) {
        return date == null ? null : simpleDateFormat.format(date);
    }

    @Override
    public String toString() {
        return "AdvancedSearchCondition{" +
                "directory=" + directory +
                ", lastModifiedLower=" + formatDate(lastModifiedLower) +
                ", lastModifiedUpper=" + formatDate(lastModifiedUpper) +
                ", fileSizeLower=" + fileSizeLower +
                ", fileSizeUpper=" + fileSizeUpper +
                ", folderCompressStatus=" + folderCompressStatus +
                ", compressedFileSizeLower=" + compressedFileSizeLower +
                ", compressedFileSizeUpper=" + compressedFileSizeUpper +
                ", compressedFileLastModifiedLower=" + formatDate(compressedFileLastModifiedLower) +
                ", compressedFileLastModifiedUpper=" + formatDate(compressedFileLastModifiedUpper) +
                '}';
    }
}