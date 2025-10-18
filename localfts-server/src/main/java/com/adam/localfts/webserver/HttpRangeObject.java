package com.adam.localfts.webserver;

import lombok.*;

import java.util.LinkedList;
import java.util.List;

@Getter
@Setter
@ToString
public class HttpRangeObject {

    private List<Range> rangeList;
    private String originalString;
    private long fileLength;

    public HttpRangeObject(String originalString, long fileLength) {
        this.rangeList = new LinkedList<>();
        this.originalString = originalString;
        this.fileLength = fileLength;
    }

    public void addRangeCommon(String originalString, Long lower, Long upper) {
        Range range = new Range(originalString, false, lower, upper, null);
        Assert.isTrue(range.isValidRange(fileLength), "Invalid common range:" + range + ",fileLength:" + fileLength, InvalidRangeException.class);
        range.calcActualBounds(fileLength);
        rangeList.add(range);
    }

    public void addRangeLastN(String originalString, Long lastN) {
        Range range = new Range(originalString, true, null, null, lastN);
        Assert.isTrue(range.isValidRange(fileLength), "Invalid common range:" + range + ",fileLength:" + fileLength, InvalidRangeException.class);
        range.calcActualBounds(fileLength);
        rangeList.add(range);
    }

    public int size() {
        return this.rangeList.size();
    }

    public boolean isMultipleRange() {
        return size() > 1;
    }

    public Range get(int i) {
        return rangeList.get(i);
    }

    public long totalRangeLength() {
        long total = 0;
        for(Range range: rangeList) {
            total += (range.actualUpper - range.actualLower + 1);
        }
        return total;
    }

    public String toSimplifiedString() {
        StringBuilder stringBuilder = new StringBuilder();
        for(Range range: rangeList) {
            stringBuilder.append(range.actualLower).append("-").append(range.actualUpper).append(",");
        }
        if(size() > 0) {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }
        return stringBuilder.toString();
    }

    @NoArgsConstructor
    @Getter
    @Setter
    @ToString
    public class Range{
        private String originalString;
        private boolean isLastN;
        private Long lower, upper;
        private Long lastN;
        private long actualLower, actualUpper;

        public Range(String originalString, boolean isLastN, Long lower, Long upper, Long lastN) {
            this.originalString = originalString;
            this.isLastN = isLastN;
            this.lower = lower;
            this.upper = upper;
            this.lastN = lastN;
        }

        public void calcActualBounds(long fileLength) {
            if(!isLastN) {
                actualLower = lower;
                actualUpper = upper == null ? fileLength - 1 : upper;
            } else {
                actualLower = fileLength - (-1 * lastN);
                actualUpper = fileLength - 1;
            }
        }

        public boolean isValidRange(long fileLength) {
            if(!isLastN()) {
                return getLower() != null && getLower() >= 0 && getLower() < fileLength && (getUpper() == null || (getUpper() >= getLower() && getUpper() < fileLength));
            } else {
                Long lastN = getLastN();
                return lastN != null && (-1 * lastN) <= fileLength;
            }
        }
    }

}
