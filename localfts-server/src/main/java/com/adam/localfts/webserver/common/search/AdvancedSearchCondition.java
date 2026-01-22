package com.adam.localfts.webserver.common.search;

import com.adam.localfts.webserver.common.FunctionThrowsException;
import com.adam.localfts.webserver.common.compress.FolderCompressStatus;
import com.adam.localfts.webserver.util.Util;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

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
    @Getter(AccessLevel.NONE)
    private Long fileSizeLower, fileSizeUpper;
    private String fileSizeLowerStr, fileSizeUpperStr;
    /**
     * 只对文件夹生效
     */
    private FolderCompressStatus folderCompressStatus;
    /**
     * 只对文件夹生效
     */
    @Getter(AccessLevel.NONE)
    private Long compressedFileSizeLower, compressedFileSizeUpper;
    private String compressedFileSizeLowerStr, compressedFileSizeUpperStr;
    /**
     * 只对文件夹生效
     */
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

    public void setFileSizeLower(String fileSizeLower) {
        this.fileSizeLowerStr = safeConvertToStringNoEX(fileSizeLower, String::toString);
        this.fileSizeLower = safeConvertFromStringNoEX(fileSizeLower, this::parseDataSizeStrToBytes);
    }

    public void setFileSizeUpper(String fileSizeUpper) {
        this.fileSizeUpperStr = safeConvertToStringNoEX(fileSizeUpper, String::toString);
        this.fileSizeUpper = safeConvertFromStringNoEX(fileSizeUpper, this::parseDataSizeStrToBytes);
    }

    public void setCompressedFileSizeLower(String compressedFileSizeLower) {
        this.compressedFileSizeLowerStr = safeConvertToStringNoEX(compressedFileSizeLower, String::toString);
        this.compressedFileSizeLower = safeConvertFromStringNoEX(compressedFileSizeLower, this::parseDataSizeStrToBytes);
    }

    public void setCompressedFileSizeUpper(String compressedFileSizeUpper) {
        this.compressedFileSizeUpperStr = safeConvertToStringNoEX(compressedFileSizeUpper, String::toString);
        this.compressedFileSizeUpper = safeConvertFromStringNoEX(compressedFileSizeUpper, this::parseDataSizeStrToBytes);
    }

    public void setLastModifiedLower(String lastModifiedLower) throws Exception{
        this.lastModifiedLower = safeConvertFromStringEX(lastModifiedLower, simpleDateFormat::parse);
    }

    public String getLastModifiedLower() {
        return safeConvertToStringNoEX(lastModifiedLower, simpleDateFormat::format);
    }

    public void setLastModifiedUpper(String lastModifiedUpper) throws Exception{
        this.lastModifiedUpper = safeConvertFromStringEX(lastModifiedUpper, simpleDateFormat::parse);
    }

    public String getLastModifiedUpper() {
        return safeConvertToStringNoEX(lastModifiedUpper, simpleDateFormat::format);
    }

    public void setCompressedFileLastModifiedLower(String compressedFileLastModifiedLower) throws Exception{
        this.compressedFileLastModifiedLower = safeConvertFromStringEX(compressedFileLastModifiedLower, simpleDateFormat::parse);
    }

    public void setCompressedFileLastModifiedLower(Date compressedFileLastModifiedLower) {
        this.compressedFileLastModifiedLower = compressedFileLastModifiedLower;
    }

    public String getCompressedFileLastModifiedLower() {
        return safeConvertToStringNoEX(compressedFileLastModifiedLower, simpleDateFormat::format);
    }

    public void setCompressedFileLastModifiedUpper(String compressedFileLastModifiedUpper) throws Exception {
        this.compressedFileLastModifiedUpper = safeConvertFromStringEX(compressedFileLastModifiedUpper, simpleDateFormat::parse);
    }

    public void setCompressedFileLastModifiedUpper(Date compressedFileLastModifiedUpper) {
        this.compressedFileLastModifiedUpper = compressedFileLastModifiedUpper;
    }

    public String getCompressedFileLastModifiedUpper() {
        return safeConvertToStringNoEX(compressedFileLastModifiedUpper, simpleDateFormat::format);
    }

    private <T> String safeConvertToStringNoEX(T value, Function<T, String> function) {
        return value == null ? null : function.apply(value);
    }

    private <T> T safeConvertFromStringEX(String str, FunctionThrowsException<String, T> function) throws Exception{
        return StringUtils.isEmpty(str) ? null : function.apply(str);
    }

    private <T> T safeConvertFromStringNoEX(String str, Function<String, T> function) {
        return StringUtils.isEmpty(str) ? null : function.apply(str);
    }

    private Long parseDataSizeStrToBytes(String str) {
        return DataSize.parse(str).toBytes();
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        if(directory != null) {
            stringBuilder.append(", directory=").append(directory);
        }
        if(lastModifiedLower != null) {
            stringBuilder.append(", lastModifiedLower=").append(getLastModifiedLower());
        }
        if(lastModifiedUpper != null) {
            stringBuilder.append(", lastModifiedUpper=").append(getLastModifiedUpper());
        }
        if(fileSizeLower != null) {
            stringBuilder.append(", fileSizeLower=").append(fileSizeLowerStr);
        }
        if(fileSizeUpper != null) {
            stringBuilder.append(", fileSizeUpper=").append(fileSizeUpperStr);
        }
        if(folderCompressStatus != null) {
            stringBuilder.append(", folderCompressStatus=").append(folderCompressStatus);
        }
        if(compressedFileSizeLower != null) {
            stringBuilder.append(", compressedFileSizeLower=").append(compressedFileSizeLowerStr);
        }
        if(compressedFileSizeUpper != null) {
            stringBuilder.append(", compressedFileSizeUpper=").append(compressedFileSizeUpperStr);
        }
        if(compressedFileLastModifiedLower != null) {
            stringBuilder.append(", compressedFileLastModifiedLower=").append(getCompressedFileLastModifiedLower());
        }
        if(compressedFileLastModifiedUpper != null) {
            stringBuilder.append(", compressedFileLastModifiedUpper=").append(getCompressedFileLastModifiedUpper());
        }
        if(stringBuilder.length() > 0) {
            stringBuilder.delete(0, 2);
        }
        stringBuilder.append("}");
        return "AdvancedSearchCondition{" + stringBuilder;
    }
}