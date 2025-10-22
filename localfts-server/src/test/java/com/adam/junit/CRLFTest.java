package com.adam.junit;

import org.junit.Assert;
import org.junit.Test;

public class CRLFTest {

    @Test
    public void testCRLF() {
        Assert.assertEquals('\r', 13);
        Assert.assertEquals('\n', 10);
    }

}
