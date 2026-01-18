package com.adam.localfts.webserver.common.search;

import com.adam.localfts.webserver.common.FunctionThrowsException;
import com.adam.localfts.webserver.common.compress.FolderCompressStatus;
import com.adam.localfts.webserver.util.Util;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Function;

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

    public void setLastModifiedLower(String lastModifiedLower) throws Exception{
        this.lastModifiedLower = safeConvertFromString(lastModifiedLower, simpleDateFormat::parse);
    }

    public String getLastModifiedLower() {
        return safeConvertToString(lastModifiedLower, simpleDateFormat::format);
    }

    public void setLastModifiedUpper(String lastModifiedUpper) throws Exception{
        this.lastModifiedUpper = safeConvertFromString(lastModifiedUpper, simpleDateFormat::parse);
    }

    public String getLastModifiedUpper() {
        return safeConvertToString(lastModifiedUpper, simpleDateFormat::format);
    }

    public void setCompressedFileLastModifiedLower(String compressedFileLastModifiedLower) throws Exception{
        this.compressedFileLastModifiedLower = safeConvertFromString(compressedFileLastModifiedLower, simpleDateFormat::parse);
    }

    public String getCompressedFileLastModifiedLower() {
        return safeConvertToString(compressedFileLastModifiedLower, simpleDateFormat::format);
    }

    public void setCompressedFileLastModifiedUpper(String compressedFileLastModifiedUpper) throws Exception {
        this.compressedFileLastModifiedUpper = safeConvertFromString(compressedFileLastModifiedUpper, simpleDateFormat::parse);
    }

    public String getCompressedFileLastModifiedUpper() {
        return safeConvertToString(compressedFileLastModifiedUpper, simpleDateFormat::format);
    }

    private <T> String safeConvertToString(T value, Function<T, String> function) {
        return value == null ? null : function.apply(value);
    }

    private <T> T safeConvertFromString(String str, FunctionThrowsException<String, T> function) throws Exception{
        return StringUtils.isEmpty(str) ? null : function.apply(str);
    }

    @Override
    public String toString() {
        return "AdvancedSearchCondition{" +
                "directory=" + directory +
                ", lastModifiedLower=" + getLastModifiedLower() +
                ", lastModifiedUpper=" + getLastModifiedUpper() +
                ", fileSizeLower=" + fileSizeLower +
                ", fileSizeUpper=" + fileSizeUpper +
                ", folderCompressStatus=" + folderCompressStatus +
                ", compressedFileSizeLower=" + compressedFileSizeLower +
                ", compressedFileSizeUpper=" + compressedFileSizeUpper +
                ", compressedFileLastModifiedLower=" + getCompressedFileLastModifiedLower() +
                ", compressedFileLastModifiedUpper=" + getCompressedFileLastModifiedUpper() +
                '}';
    }
}