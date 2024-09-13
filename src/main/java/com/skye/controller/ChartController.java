package com.skye.controller;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.gson.Gson;
import com.skye.annotation.AuthCheck;
import com.skye.common.*;
import com.skye.constant.CommonConstant;
import com.skye.constant.UserConstant;
import com.skye.exception.BusinessException;
import com.skye.exception.ThrowUtils;
import com.skye.manager.AiManager;
import com.skye.manager.RedisLimiterManager;
import com.skye.model.dto.chart.*;
import com.skye.model.entity.Chart;
import com.skye.model.entity.User;
import com.skye.model.vo.AiVO;
import com.skye.service.ChartService;
import com.skye.service.UserService;
import com.skye.utils.ExcelUtils;
import com.skye.utils.SqlUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 帖子接口
 *
 *  @author skye
 *  
 */
@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;

    @Resource
    private UserService userService;

    @Resource
    private AiManager aiManager;

    //redisson限流
    @Resource
    private RedisLimiterManager redisLimiterManager;

    //线程池
    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    private final static Gson GSON = new Gson();
    long VDMS_MODEL_ID = 1832673039155191809L;

    // region 增删改查

    /**
     * 创建
     *
     * @param chartAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);
        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());
        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = chart.getId();
        return ResultUtils.success(newChartId);
    }

    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param chartUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);
        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get")
    public BaseResponse<Chart> getChartById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }

    /**
     * 分页获取列表（仅管理员）
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest, HttpServletRequest request) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        //限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }


    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param chartQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<Chart>> listMyChartVOByPage(@RequestBody ChartQueryRequest chartQueryRequest,
            HttpServletRequest request) {
        if (chartQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        chartQueryRequest.setUserId(loginUser.getId());
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Chart> chartPage = chartService.page(new Page<>(current, size),
                getQueryWrapper(chartQueryRequest));
        return ResultUtils.success(chartPage);
    }

    // endregion

    /**
     * 编辑（用户）
     *
     * @param chartEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editChart(@RequestBody ChartEditRequest chartEditRequest, HttpServletRequest request) {
        if (chartEditRequest == null || chartEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartEditRequest, chart);
        User loginUser = userService.getLoginUser(request);
        long id = chartEditRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldChart.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

    /**
     * 智能分析（同步）
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen")
    public BaseResponse<AiVO> genChartByAi(@RequestPart("file") MultipartFile multipartFile,
                                           GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {

        // 获取表格信息
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();

        // 校验表格信息
        // 如果分析目标为空，就抛出请求参数错误异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "分析目标不能为空");
        // 如果名称不为空，长度大于100，抛出异常，给出提示
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称长度不能超过100");

        /**
         * 校验文件
         */
        // 获取文件大小
        long fileSize = multipartFile.getSize();
        // 获取文件名
        String originalFilename = multipartFile.getOriginalFilename();

        // 校验文件大小
        // 定义1MB的常量
        final long ONE_MB = 1024 * 1024L;
        // 如果文件大小,大于一兆,就抛出异常,并提示文件超过1M
        ThrowUtils.throwIf(fileSize > ONE_MB, ErrorCode.PARAMS_ERROR, "文件大小不得大于1M");

        // 校验文件名 使用FileUtil工具类
        String suffix = FileUtil.getSuffix(originalFilename);
        //定义合法的文件后缀
        final List<String> ALLOW_SUFFIX = Arrays.asList("xlsx", "xls");
        // 如果suffix的后缀不在List的范围内,抛出异常,并提示'文件后缀非法'
        ThrowUtils.throwIf(!ALLOW_SUFFIX.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");


        // 用户登录，登录后才可以使用
        User loginUser = userService.getLoginUser(request);

        // redisson限流
        redisLimiterManager.doRateLimit("genCharByAi_"+loginUser.getId());
        /*
        * 用户输入实例：
        *   分析需求：
            分析网站用户的增长情况
            原始数据：
            日期，用户数：
            1号，10
            2号，20
            3号，30";
        * */
        //构建用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析目标：").append("\n");
        //拼接分析目标
        String userGoal = goal;
        //如果图表内容不为空
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += "，请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");

        String cvsData = ExcelUtils.excelToCSV(multipartFile);
        userInput.append(cvsData).append("\n");

        //获取结果
        String result = aiManager.doChat(VDMS_MODEL_ID, userInput.toString());
        String[] splits = result.split("【【【【【");
        //拆分后校验
        if (splits.length < 3) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Ai生成错误");
        }

        String genChart = splits[1].trim();
        // 遍历genChart，将其中的单引号改为双引号
        genChart = genChart.replaceAll("'", "\"");
        String genResult = splits[2].trim();
        // 插入到数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(cvsData);
        chart.setChartType(chartType);
        chart.setGenChart(genChart);
        chart.setGenResult(genResult);
        chart.setUserId(loginUser.getId());
        chart.setStatus(ChartStatus.SUCCEED.getStatus());
        boolean saveResult = chartService.save(chart);
        if (!saveResult) {
            handleChartUpdateError(chart.getId(), "图表保存失败");
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
        AiVO aiVO = new AiVO();
        aiVO.setGenChart(genChart);
        aiVO.setGenResult(genResult);
        aiVO.setChartId(chart.getId());
        return ResultUtils.success(aiVO);
    }
    /**
     * 智能分析（异步）
     *
     * @param multipartFile
     * @param genChartByAiRequest
     * @param request
     * @return
     */
    @PostMapping("/gen/async")
    public BaseResponse<AiVO> genChartByAiAsync(@RequestPart("file") MultipartFile multipartFile,
                                           GenChartByAiRequest genChartByAiRequest, HttpServletRequest request) {

        // 获取表格信息
        String name = genChartByAiRequest.getName();
        String goal = genChartByAiRequest.getGoal();
        String chartType = genChartByAiRequest.getChartType();

        // 校验表格信息
        // 如果分析目标为空，就抛出请求参数错误异常，并给出提示
        ThrowUtils.throwIf(StringUtils.isBlank(goal), ErrorCode.PARAMS_ERROR, "分析目标不能为空");
        // 如果名称不为空，长度大于100，抛出异常，给出提示
        ThrowUtils.throwIf(StringUtils.isNotBlank(name) && name.length() > 100, ErrorCode.PARAMS_ERROR, "名称长度不能超过100");

        /**
         * 校验文件
         */
        // 获取文件大小
        long fileSize = multipartFile.getSize();
        // 获取文件名
        String originalFilename = multipartFile.getOriginalFilename();

        // 校验文件大小
        // 定义1MB的常量
        final long ONE_MB = 1024 * 1024L;
        // 如果文件大小,大于一兆,就抛出异常,并提示文件超过1M
        ThrowUtils.throwIf(fileSize > ONE_MB, ErrorCode.PARAMS_ERROR, "文件大小不得大于1M");

        // 校验文件名 使用FileUtil工具类
        String suffix = FileUtil.getSuffix(originalFilename);
        //定义合法的文件后缀
        final List<String> ALLOW_SUFFIX = Arrays.asList("xlsx", "xls");
        // 如果suffix的后缀不在List的范围内,抛出异常,并提示'文件后缀非法'
        ThrowUtils.throwIf(!ALLOW_SUFFIX.contains(suffix), ErrorCode.PARAMS_ERROR, "文件后缀非法");


        // 用户登录，登录后才可以使用
        User loginUser = userService.getLoginUser(request);

        // redisson限流
        redisLimiterManager.doRateLimit("genCharByAi_"+loginUser.getId());


        /*
        * 用户输入实例：
        *   分析需求：
            分析网站用户的增长情况
            原始数据：
            日期，用户数：
            1号，10
            2号，20
            3号，30";
        * */
        //构建用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析目标：").append("\n");
        //拼接分析目标
        String userGoal = goal;
        //如果图表内容不为空
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += "，请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");

        String cvsData = ExcelUtils.excelToCSV(multipartFile);
        userInput.append(cvsData).append("\n");

        /*
        * 先把图表保存到数据库中
        * */
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(cvsData);
        chart.setChartType(chartType);
        // 此时图表和结论还没生成，将任务设置为派对中
//        chart.setGenChart(genChart);
//        chart.setGenResult(genResult);
        chart.setStatus(ChartStatus.WAIT.getStatus());
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");


        //获取结果，获取前提交一个任务
        // TODO: 2024/9/12 在任务队列满了的情况下，前端抛出异常
        CompletableFuture.runAsync(() -> {
            log.info("异步请求执行中");
            // 修改图表任务状态为“执行中”
            Chart updateChart = new Chart();
            updateChart.setId(chart.getId());
            updateChart.setStatus(ChartStatus.RUNNING.getStatus());
            boolean b = chartService.updateById(updateChart);
            if (!b) {
                handleChartUpdateError(chart.getId(), "更新图表执行中状态失败");
                return;
            }

            //调用Ai
            String result = aiManager.doChat(VDMS_MODEL_ID, userInput.toString());
            String[] splits = result.split("【【【【【");
            //拆分后校验
            if (splits.length < 3) {
                handleChartUpdateError(chart.getId(), "AI生成错误");
                return;
            }
            String genChart = splits[1].trim();
            // 遍历genChart，将其中的单引号改为双引号
            genChart = genChart.replaceAll("'", "\"");
            String genResult = splits[2].trim();

            // 调用获取结果后，再更新一次信息
            Chart updateChartResult = new Chart();
            updateChartResult.setId(chart.getId());
            updateChartResult.setGenChart(genChart);
            updateChartResult.setGenResult(genResult);
            updateChartResult.setStatus("succeed");
            boolean updateResult = chartService.updateById(updateChartResult);
            if (!updateResult) {
                handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
            }
            log.info("异步请求执行完毕");
        }, threadPoolExecutor);

        AiVO aiVO = new AiVO();
//        aiVO.setGenChart(genChart);
//        aiVO.setGenResult(genResult);
        aiVO.setChartId(chart.getId());

        return ResultUtils.success(aiVO);
    }

    // 上面的接口很多用到异常,直接定义一个工具类
    private void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus(ChartStatus.FAILED.getStatus());
        updateChartResult.setExecMessage(execMessage);
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }
    }



    /**
     * 获取查询包装类
     *
     * @param chartQueryRequest
     * @return
     */
    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String name = chartQueryRequest.getName();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();

        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.eq(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

}
