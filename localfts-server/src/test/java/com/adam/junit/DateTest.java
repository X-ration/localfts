package com.adam.junit;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateTest {

    @Test
    public void testDate() {
        Date date = new Date();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z");
        System.out.println(simpleDateFormat.format(date));
    }

    @Test
    public void testDate2() {
        Date date = new Date();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        System.out.println(simpleDateFormat.getTimeZone().getID());
        System.out.println(simpleDateFormat.format(date));
    }

}
