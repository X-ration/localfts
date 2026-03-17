package com.adam.localfts.webserver.common.search;

import com.adam.localfts.webserver.common.Constants;
import com.adam.localfts.webserver.common.FunctionThrowsException;
import com.adam.localfts.webserver.common.compress.FolderCompressStatus;
import com.adam.localfts.webserver.util.Util;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
public class AdvancedSearchCondition implements Cloneable{

    /**
     * 指定搜索的目标路径，可以有多个
     */
    @Getter(AccessLevel.NONE)
    private List<String> searchPathList;
    @Getter(AccessLevel.NONE)
    private List<String> fileTypeList;
    private Boolean filterFileType;
    private SearchType searchType;
    /**
     * 是否区分大小写和简繁体
     */
    private Boolean caseAndSTCSensitive;
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
    @Getter(AccessLevel.NONE)
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    @Getter(AccessLevel.NONE)
    private boolean emptyResult;

    public boolean isEmpty() {
        return caseAndSTCSensitive == null && searchType == null
                && (filterFileType == null || CollectionUtils.isEmpty(fileTypeList))
                && CollectionUtils.isEmpty(searchPathList) && directory == null && fileSizeLower == null && fileSizeUpper == null
                && lastModifiedLower == null && lastModifiedUpper == null && folderCompressStatus == null
                && compressedFileSizeLower == null && compressedFileSizeUpper == null
                && compressedFileLastModifiedLower == null && compressedFileLastModifiedUpper == null;
    }

    public AdvancedSearchCondition copy() throws CloneNotSupportedException {
        return (AdvancedSearchCondition) clone();
    }

    public void clean() {
        if(this.directory != null) {
            if(this.directory) {
                //限制文件夹
                setFilterFileType(null);
                setFileTypeList(null);
                setFileSizeLower(null);
                setFileSizeLowerStr(null);
                setFileSizeUpper(null);
                setFileSizeUpperStr(null);
            } else {
                //限制文件
                setFolderCompressStatus(null);
                setCompressedFileSizeLower(null);
                setCompressedFileSizeLowerStr(null);
                setCompressedFileSizeUpper(null);
                setCompressedFileSizeUpperStr(null);
                setCompressedFileLastModifiedLower((Date) null);
                setCompressedFileLastModifiedUpper((Date) null);
            }
        }
    }

    public boolean emptyResult() {
        return this.emptyResult;
    }

    public void setSearchPaths(String[] searchPaths) {
        if(searchPaths != null) {
            this.searchPathList = Arrays.stream(searchPaths)
                    .filter(str -> !StringUtils.isEmpty(str))
                    .filter(str -> {
                        Matcher matcher;
                        if(Util.isSystemWindows()) {
                            matcher = Constants.PATTERN_PATH_WINDOWS_STANDARD_RELATIVE.matcher(str);
                        } else {
                            matcher = Constants.PATTERN_PATH_LINUX_MACOS_RELATIVE.matcher(str);
                        }
                        Assert.isTrue(matcher.matches(), "Search path '" + str + "' does not match rules!");
                        return true;
                    })
                    .collect(Collectors.toList());
            List<String> recordList = new LinkedList<>();
            for(String str: this.searchPathList) {
                Assert.isTrue(!recordList.contains(str), "Search path '" + str + "' duplicate!");
                recordList.add(str);
            }
        }
    }

    public void setFileTypes(String[] fileTypes) {
        if(fileTypes != null && fileTypes.length > 0) {
            this.fileTypeList = Arrays.stream(fileTypes)
                    .filter(Objects::nonNull)
                    .filter(Util::isValidFileSuffix)
//                    .filter(FileType::containsEnum)
                    .distinct()
                    .collect(Collectors.toList());
        }
    }

    public List<String> getSearchPaths() {
        return searchPathList;
    }

    public List<String> getFileTypes() {
        return fileTypeList;
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

    public Date lastModifiedLower() {
        return lastModifiedLower;
    }

    public void setLastModifiedUpper(String lastModifiedUpper) throws Exception{
        this.lastModifiedUpper = safeConvertFromStringEX(lastModifiedUpper, simpleDateFormat::parse);
    }

    public String getLastModifiedUpper() {
        return safeConvertToStringNoEX(lastModifiedUpper, simpleDateFormat::format);
    }

    public Date lastModifiedUpper() {
        return lastModifiedUpper;
    }

    public Long fileSizeLower() {
        return fileSizeLower;
    }

    public Long fileSizeUpper() {
        return fileSizeUpper;
    }

    public Long compressedFileSizeLower() {
        return compressedFileSizeLower;
    }

    public Long compressedFileSizeUpper() {
        return compressedFileSizeUpper;
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

    public Date compressedFileLastModifiedLower() {
        return compressedFileLastModifiedLower;
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

    public Date compressedFileLastModifiedUpper() {
        return compressedFileLastModifiedUpper;
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
        if(str.contains("i")) {
            str = str.replace("i", "");
        }
        return DataSize.parse(str).toBytes();
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        if(emptyResult) {
//            stringBuilder.append(", emptyResult=true");
            return "AdvancedSearchCondition{emptyResult=true}";
        }
        if(!CollectionUtils.isEmpty(searchPathList)) {
            stringBuilder.append(", searchPathList=").append(searchPathList);
        }
        if(!CollectionUtils.isEmpty(fileTypeList)) {
            stringBuilder.append(", fileTypeList=").append(fileTypeList);
        }
        if(filterFileType != null) {
            stringBuilder.append(", filterFileType=").append(filterFileType);
        }
        if(searchType != null) {
            stringBuilder.append(", searchType=").append(searchType);
        }
        if(caseAndSTCSensitive != null) {
            stringBuilder.append(", caseAndSTCSensitive=").append(caseAndSTCSensitive);
        }
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