package com.skye.manager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

class AiManagerTest {

    AiManager aiManager = new AiManager();

    @Test
    void doChat() {
        String answer = aiManager.doChat(1651468516836098050L, "邓紫棋");
        System.out.println(answer);
    }

    @Test
    void doChat01() {
        String message = "分析需求：\n" +
                "分析网站用户的增长情况\n" +
                "原始数据：\n" +
                "日期，用户数：\n" +
                "1号，10\n" +
                "2号，20\n" +
                "3号，30";
        String answer = aiManager.doChat(1832673039155191809L, message);
        System.out.println(answer);
    }


}