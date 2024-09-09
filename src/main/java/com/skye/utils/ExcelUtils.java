package com.skye.utils;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.ResourceUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Excel工具类
 */
@Slf4j
public class ExcelUtils {

    public static String excelToCSV(MultipartFile multipartFile) {
//        File file = null;
//        try {
//            file = ResourceUtils.getFile("classpath:网站数据.xlsx");
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
        List<Map<Integer, String>> list = null;
        try {
            list = EasyExcel.read(multipartFile.getInputStream())
                    .excelType(ExcelTypeEnum.XLSX)
                    .sheet()
                    .headRowNumber(0)
                    .doReadSync();
        } catch (IOException e) {
            log.error("表格处理错误",e);
        }

        // 如果数据为空
        if (CollUtil.isEmpty(list)) {
            return "";
        }

        //转换
        StringBuilder stringBuilder = new StringBuilder();
        //读取表头
        LinkedHashMap<Integer, String> headerMap = (LinkedHashMap<Integer, String>) list.get(0);
        List<String> headerList = headerMap
                .values()
                .stream()
                .filter(ObjectUtils::isNotEmpty)
                .collect(Collectors.toList());
//        System.out.println(StringUtils.join(headerList,","));
        stringBuilder.append(StringUtils.join(headerList,",")).append("\n");

        //读取数据(读取完表头之后，从第一行开始读取)
        for (int i = 1; i < list.size(); i++) {
            LinkedHashMap<Integer, String> dataMap = (LinkedHashMap<Integer, String>) list.get(i);
            List<String> datalist = dataMap
                    .values()
                    .stream()
                    .filter(ObjectUtils::isNotEmpty)
                    .collect(Collectors.toList());
//            System.out.println(StringUtils.join(datalist,","));
            stringBuilder.append(StringUtils.join(datalist,",")).append("\n");
        }


        return stringBuilder.toString();
    }

    public static void main(String[] args) {
        excelToCSV(null);
    }
}
