package com.example.qltv;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void testPasswordHashing() {
        // "123456" hash SHA-256 standard
        String expectedHash123456 = "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92";
        String actualHash123456 = HashUtils.hashPassword("123456");
        assertEquals(expectedHash123456, actualHash123456);

        // "admin" hash SHA-256 standard
        String expectedHashAdmin = "8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918";
        String actualHashAdmin = HashUtils.hashPassword("admin");
        assertEquals(expectedHashAdmin, actualHashAdmin);
    }
}