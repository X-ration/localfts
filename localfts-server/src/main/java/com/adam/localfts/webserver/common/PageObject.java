package com.adam.localfts.webserver.common;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.util.CollectionUtils;

import java.util.LinkedList;
import java.util.List;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class PageObject<T> {
    protected int pageSize;
    protected int currentPage;
    protected int currentSize;
    protected int totalPage;
    protected int totalSize;
    protected List<T> data;

    public PageObject(int pageNo, int pageSize, int totalSize, List<T> data) {
        this.currentPage = pageNo;
        this.pageSize = pageSize;
        this.totalSize = totalSize;
        this.totalPage = totalSize / pageSize + (totalSize % pageSize > 0 ? 1 : 0);
        if(pageNo > totalPage) {
            this.currentSize = 0;
            this.data = null;
            return;
        }
        int actualPageSize = pageNo == totalPage ? (totalSize - pageSize * (pageNo - 1)) : pageSize;
        this.currentSize = actualPageSize;
        this.data = data;
    }

    public PageObject(int pageNo, int pageSize, List<T> allData) {
        this.currentPage = pageNo;
        this.pageSize = pageSize;
        if(CollectionUtils.isEmpty(allData)) {
            this.currentSize = 0;
            this.totalPage = 0;
            this.totalSize = 0;
            this.data = null;
        } else {
            int totalSize = allData.size(), totalPage = totalSize / pageSize + (totalSize % pageSize > 0 ? 1 : 0);
            this.totalPage = totalPage;
            this.totalSize = totalSize;
            if(pageNo > totalPage) {
                this.currentSize = 0;
                this.data = null;
                return;
            }
            int actualPageSize = pageNo == totalPage ? (totalSize - pageSize * (pageNo - 1)) : pageSize;
            this.currentSize = actualPageSize;
            //左开右闭区间[lIndex,rIndex)
            int lIndex = pageSize * (pageNo - 1), rIndex = lIndex + actualPageSize;
            List<T> subList = new LinkedList<>();
            for(int i=0;i<allData.size();i++) {
                if(i >= lIndex && i < rIndex) {
                    T t = allData.get(i);
                    subList.add(t);
                }
            }
            this.data = subList;
        }
    }
}
