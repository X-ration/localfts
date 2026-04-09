package com.adam.junit;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import org.junit.Test;

public class ChineseSTConvertTest {
    @Test
    public void testConvertToSimple() {
        String text = "三國志X 太史慈 年表\n" +
                "開始劇本";
        text = ZhConverterUtil.toSimple(text);
        System.out.println(text);
    }
}
