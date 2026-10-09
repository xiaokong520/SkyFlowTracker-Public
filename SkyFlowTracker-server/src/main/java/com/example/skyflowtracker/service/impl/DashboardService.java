package com.example.skyflowtracker.service.impl;

import com.example.skyflowtracker.exception.AppException;
import com.example.skyflowtracker.exception.AppExceptionCodeMsg;
import com.example.skyflowtracker.mapper.DashboardMapper;
import com.example.skyflowtracker.mapper.UsersMapper;
import com.example.skyflowtracker.pojo.InferenceTasks;
import com.example.skyflowtracker.pojo.Users;
import com.example.skyflowtracker.service.inte.DashboardServiceInte;
import com.example.skyflowtracker.utils.TokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DashboardService implements DashboardServiceInte {

    private final TokenUtil tokenUtil;
    private final UsersMapper usersMapper;
    private final DashboardMapper dashboardMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public DashboardService(TokenUtil tokenUtil, UsersMapper usersMapper, DashboardMapper dashboardMapper) {
        this.tokenUtil = tokenUtil;
        this.usersMapper = usersMapper;
        this.dashboardMapper = dashboardMapper;
    }

    @Override
    public Map<String, Object> getDashboardData(String token, String detectionRange, String flightRange) throws Exception {
        //获取userId和角色
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String currentUserId = checkToken.get("userId").toString();
        Users user = usersMapper.selectUserByUserId(currentUserId);
        if (user == null) {
            throw new AppException(AppExceptionCodeMsg.USER_NOT_EXIST);
        }
        Integer userStatus = user.getStatus();
        if (userStatus == 0) {
            throw new AppException(AppExceptionCodeMsg.USER_BLOCK);
        }
        if (userStatus == 2) {
            throw new AppException(AppExceptionCodeMsg.USER_NOT_APPROVED);
        }
        //管理员查全部，普通用户只查自己的
        Integer userRole = user.getRole();
        String userId = (userRole == 1) ? null : currentUserId;
        boolean isAdmin = (userRole == 1);

        Map<String, Object> data = new HashMap<>();
        data.put("isAdmin", isAdmin);
        //概览卡片数据
        data.put("totalFlights", dashboardMapper.countTotalFlights(userId));
        data.put("totalDetections", dashboardMapper.sumTotalDetections(userId));
        data.put("totalDuration", dashboardMapper.sumFlightDuration(userId));
        data.put("totalDevices", dashboardMapper.countTotalDevices(userId));
        //检测趋势
        data.put("detectionTrend", dashboardMapper.selectDetectionTrend(userId, detectionRange));
        //飞行活动
        data.put("flightActivity", dashboardMapper.selectFlightActivity(userId, flightRange));
        //车辆类型分布（从result_data解析）
        data.put("classDistribution", aggregateClassCounts(userId));
        //设备使用排行
        data.put("deviceRanking", dashboardMapper.selectDeviceRanking(userId));
        //飞行位置
        data.put("flightLocations", dashboardMapper.selectFlightLocations(userId));
        //管理员额外数据
        if (isAdmin) {
            data.put("totalUsers", dashboardMapper.countTotalUsers());
            data.put("userActivityRanking", dashboardMapper.selectUserActivityRanking());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("message", "获取成功");
        result.put("data", data);
        return result;
    }

    /**
     * 从已完成推理任务的 result_data JSON 中聚合 class_counts
     */
    private Map<String, Integer> aggregateClassCounts(String userId) {
        Map<String, Integer> totalCounts = new HashMap<>();
        List<InferenceTasks> tasks = dashboardMapper.selectCompletedTasksResultData(userId);
        for (InferenceTasks task : tasks) {
            try {
                String resultData = task.getResultData();
                if (resultData == null || resultData.isEmpty()) continue;
                Map<String, Object> parsed = objectMapper.readValue(resultData, Map.class);
                Object classCounts = parsed.get("class_counts");
                if (classCounts instanceof Map) {
                    Map<String, Object> counts = (Map<String, Object>) classCounts;
                    for (Map.Entry<String, Object> entry : counts.entrySet()) {
                        String cls = entry.getKey();
                        int count = ((Number) entry.getValue()).intValue();
                        totalCounts.merge(cls, count, Integer::sum);
                    }
                }
            } catch (Exception e) {
                log.warn("解析 result_data 失败, taskId={}: {}", task.getId(), e.getMessage());
            }
        }
        return totalCounts;
    }
}
