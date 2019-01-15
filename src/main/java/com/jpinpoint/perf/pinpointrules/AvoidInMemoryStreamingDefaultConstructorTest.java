package com.jpinpoint.perf.pinpointrules;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;

/**
 * Created by BorgersJM on 14-4-2015.
 */
public class AvoidInMemoryStreamingDefaultConstructorTest {
    public static void testViolation1()  {
        ByteArrayOutputStream baos = null;
        baos = new ByteArrayOutputStream();
    }
    public static void testViolation2()  {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
    }
    public static void testViolation3()  {
        StringWriter sw = new StringWriter();
    }
    public static void testNoViolation1()  {
        ByteArrayOutputStream baos = null;
        baos = new ByteArrayOutputStream(8*1024);
    }
    public static void testNoViolation2()  {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
    }
    public static void testNoViolation3()  {
        StringWriter sw = new StringWriter(2048);
    }
}
