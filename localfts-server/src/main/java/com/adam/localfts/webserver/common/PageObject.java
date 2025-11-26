package com.adam.localfts.webserver.common;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
public class PageObject<T> {
    protected int pageSize;
    protected int currentPage;
    protected int currentSize;
    protected int totalPage;
    protected int totalSize;
    protected List<T> data;
}
