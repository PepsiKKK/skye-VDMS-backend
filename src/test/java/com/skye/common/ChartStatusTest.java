package com.skye.common;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ChartStatusTest {


    @Test
    void test(){
        String status = ChartStatus.RUNNING.getStatus();
//        String status = ChartStatus.RUNNING.toString();
        System.out.println(status);
    }
}