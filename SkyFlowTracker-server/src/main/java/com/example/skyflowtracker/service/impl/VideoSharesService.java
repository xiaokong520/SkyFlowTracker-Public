package com.example.skyflowtracker.service.impl;

import com.example.skyflowtracker.dto.CreateShareDto;
import com.example.skyflowtracker.exception.AppException;
import com.example.skyflowtracker.exception.AppExceptionCodeMsg;
import com.example.skyflowtracker.mapper.FlightsMapper;
import com.example.skyflowtracker.mapper.InferenceTasksMapper;
import com.example.skyflowtracker.mapper.UsersMapper;
import com.example.skyflowtracker.mapper.VideoSharesMapper;
import com.example.skyflowtracker.pojo.Flights;
import com.example.skyflowtracker.pojo.InferenceTasks;
import com.example.skyflowtracker.pojo.Users;
import com.example.skyflowtracker.pojo.VideoShares;
import com.example.skyflowtracker.service.inte.VideoSharesServiceInte;
import com.example.skyflowtracker.utils.TokenUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class VideoSharesService implements VideoSharesServiceInte {

    private final TokenUtil tokenUtil;
    private final UsersMapper usersMapper;
    private final InferenceTasksMapper inferenceTasksMapper;
    private final FlightsMapper flightsMapper;
    private final VideoSharesMapper videoSharesMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${nginx.baseUrl}")
    private String nginxBaseUrl;

    private static final String SHARE_REDIS_PREFIX = "SkyFlowTracker:share:";

    @Autowired
    public VideoSharesService(TokenUtil tokenUtil, UsersMapper usersMapper,
                              InferenceTasksMapper inferenceTasksMapper,
                              FlightsMapper flightsMapper,
                              VideoSharesMapper videoSharesMapper,
                              RedisTemplate<String, String> redisTemplate) {
        this.tokenUtil = tokenUtil;
        this.usersMapper = usersMapper;
        this.inferenceTasksMapper = inferenceTasksMapper;
        this.flightsMapper = flightsMapper;
        this.videoSharesMapper = videoSharesMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public Map<String, Object> createShare(String token, CreateShareDto dto) throws Exception {
        // 1. 验证token，获取userId和角色
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        Integer userRole = Integer.parseInt(checkToken.get("role").toString());

        // 2. 验证用户状态
        Users user = usersMapper.selectUserByUserId(userId);
        if (user == null) throw new AppException(AppExceptionCodeMsg.USER_NOT_EXIST);
        if (user.getStatus() == 0) throw new AppException(AppExceptionCodeMsg.USER_BLOCK);
        if (user.getStatus() == 2) throw new AppException(AppExceptionCodeMsg.USER_NOT_APPROVED);

        // 3. 验证推理任务存在且已完成
        InferenceTasks task = inferenceTasksMapper.selectTasksById(dto.getTaskId());
        if (task == null) throw new AppException(AppExceptionCodeMsg.TASK_NOT_EXIST);
        if (task.getStatus() != 1) throw new AppException(AppExceptionCodeMsg.TASK_NOT_COMPLETED);

        // 4. 非管理员验证任务归属（直接通过推理任务的 userId 判断）
        if (userRole != 1) {
            if (!userId.equals(task.getUserId())) {
                throw new AppException(AppExceptionCodeMsg.TASK_NOT_EXIST);
            }
        }

        // 5. 生成 shareCode
        String shareCode = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // 6. 计算过期时间
        LocalDateTime expireTime = LocalDateTime.now().plusHours(dto.getExpireHours());

        // 7. 写入 MySQL
        VideoShares share = new VideoShares();
        share.setShareCode(shareCode);
        share.setTaskId(dto.getTaskId());
        share.setUserId(userId);
        share.setExpireTime(expireTime);
        share.setViewCount(0);
        share.setCreateTime(LocalDateTime.now());
        videoSharesMapper.insertShare(share);

        // 8. 写入 Redis
        String redisKey = SHARE_REDIS_PREFIX + shareCode;
        Map<String, Object> cacheData = buildShareCacheData(task, share.getId());
        redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(cacheData),
                dto.getExpireHours(), TimeUnit.HOURS);

        // 9. 返回
        Map<String, Object> result = new HashMap<>();
        result.put("message", "创建成功");
        Map<String, Object> data = new HashMap<>();
        data.put("shareCode", shareCode);
        data.put("shareId", share.getId());
        data.put("expireTime", expireTime.toString());
        result.put("data", data);
        return result;
    }

    @Override
    public Map<String, Object> getShareInfo(String shareCode) {
        Map<String, Object> result = new HashMap<>();

        // 1. 先查 Redis
        String redisKey = SHARE_REDIS_PREFIX + shareCode;
        String cached = redisTemplate.opsForValue().get(redisKey);

        if (cached != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> cacheData = objectMapper.readValue(cached, Map.class);
                // 更新访问次数
                Object shareIdObj = cacheData.get("shareId");
                if (shareIdObj != null) {
                    videoSharesMapper.incrementViewCount(((Number) shareIdObj).longValue());
                }
                result.put("message", "获取成功");
                result.put("data", cacheData);
                return result;
            } catch (JsonProcessingException e) {
                log.warn("Redis缓存解析失败: {}", e.getMessage());
            }
        }

        // 2. 查 MySQL
        VideoShares share = videoSharesMapper.selectByShareCode(shareCode);
        if (share == null) {
            throw new AppException(AppExceptionCodeMsg.SHARE_NOT_EXIST);
        }

        // 3. 检查过期
        if (share.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new AppException(AppExceptionCodeMsg.SHARE_EXPIRED);
        }

        // 4. 查推理任务
        InferenceTasks task = inferenceTasksMapper.selectTasksById(share.getTaskId());
        if (task == null) {
            throw new AppException(AppExceptionCodeMsg.TASK_NOT_EXIST);
        }

        // 5. 构建返回数据
        Map<String, Object> data = buildShareCacheData(task, share.getId());

        // 6. 回填 Redis
        long remainSeconds = Duration.between(LocalDateTime.now(), share.getExpireTime()).getSeconds();
        if (remainSeconds > 0) {
            try {
                redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(data),
                        remainSeconds, TimeUnit.SECONDS);
            } catch (JsonProcessingException e) {
                log.warn("Redis回填序列化失败: {}", e.getMessage());
            }
        }

        // 7. 更新访问次数
        videoSharesMapper.incrementViewCount(share.getId());

        result.put("message", "获取成功");
        result.put("data", data);
        return result;
    }

    @Override
    public Map<String, Object> getMyShares(String token, Integer page, Integer pageSize) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        Integer userRole = Integer.parseInt(checkToken.get("role").toString());

        Users user = usersMapper.selectUserByUserId(userId);
        if (user == null) throw new AppException(AppExceptionCodeMsg.USER_NOT_EXIST);
        if (user.getStatus() == 0) throw new AppException(AppExceptionCodeMsg.USER_BLOCK);

        int offset = (page - 1) * pageSize;
        List<Map<String, Object>> list;
        int total;

        if (userRole == 1) {
            // 管理员查看所有分享
            list = videoSharesMapper.selectAllShareList(offset, pageSize);
            total = videoSharesMapper.countAllShares();
        } else {
            // 普通用户只查看自己的
            list = videoSharesMapper.selectShareListByUserId(userId, offset, pageSize);
            total = videoSharesMapper.countSharesByUserId(userId);
        }

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", page);
        pagination.put("size", pageSize);
        pagination.put("total", total);
        pagination.put("totalPage", (int) Math.ceil((double) total / pageSize));

        Map<String, Object> result = new HashMap<>();
        result.put("message", "获取成功");
        Map<String, Object> data = new HashMap<>();
        data.put("sharesList", list);
        data.put("pagination", pagination);
        result.put("data", data);
        return result;
    }

    @Override
    public String deleteShare(String token, Long shareId) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        Integer userRole = Integer.parseInt(checkToken.get("role").toString());

        // 先查出 shareCode 用于删除 Redis
        String shareCode = videoSharesMapper.selectShareCodeById(shareId);

        int rows;
        if (userRole == 1) {
            // 管理员可删除任意分享
            rows = videoSharesMapper.deleteShareById(shareId);
        } else {
            // 普通用户只能删除自己的
            rows = videoSharesMapper.deleteShareByIdAndUserId(shareId, userId);
        }
        if (rows > 0) {
            // 删除 Redis 缓存
            if (shareCode != null) {
                redisTemplate.delete(SHARE_REDIS_PREFIX + shareCode);
            }
            return "删除成功";
        }
        return "分享不存在或无权删除";
    }

    /**
     * 构建分享缓存数据
     */
    private Map<String, Object> buildShareCacheData(InferenceTasks task, Long shareId) {
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", task.getId());
        String videoPath = task.getVideoPath();
        if (videoPath != null && !videoPath.isEmpty() && !videoPath.startsWith("http")) {
            videoPath = nginxBaseUrl + videoPath;
        }
        data.put("videoPath", videoPath);
        data.put("modelName", task.getModelName());
        data.put("totalDetections", task.getTotalDetections());
        // 解析 resultData
        if (task.getResultData() != null && !task.getResultData().isEmpty()) {
            try {
                data.put("resultData", objectMapper.readValue(task.getResultData(), Map.class));
            } catch (JsonProcessingException e) {
                data.put("resultData", new HashMap<>());
            }
        } else {
            data.put("resultData", new HashMap<>());
        }
        if (task.getStartTime() != null) data.put("startTime", task.getStartTime().toString());
        if (task.getEndTime() != null) data.put("endTime", task.getEndTime().toString());
        data.put("shareId", shareId);
        return data;
    }
}
