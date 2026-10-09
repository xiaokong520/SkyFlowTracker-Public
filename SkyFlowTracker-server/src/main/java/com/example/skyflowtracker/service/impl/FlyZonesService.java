package com.example.skyflowtracker.service.impl;

import com.example.skyflowtracker.dto.*;
import com.example.skyflowtracker.exception.AppException;
import com.example.skyflowtracker.exception.AppExceptionCodeMsg;
import com.example.skyflowtracker.mapper.FlyZonesMapper;
import com.example.skyflowtracker.pojo.FlyZones;
import com.example.skyflowtracker.service.inte.FlyZonesServiceInte;
import com.example.skyflowtracker.utils.TokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class FlyZonesService implements FlyZonesServiceInte {

    private static final String REDIS_KEY_ALL_ENABLED = "flyZones:allEnabled";
    private static final long CACHE_TTL_HOURS = 24;

    private final TokenUtil tokenUtil;
    private final FlyZonesMapper flyZonesMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public FlyZonesService(TokenUtil tokenUtil, FlyZonesMapper flyZonesMapper, RedisTemplate<String, String> redisTemplate) {
        this.tokenUtil = tokenUtil;
        this.flyZonesMapper = flyZonesMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Map<String, Object> syncFlyZones(String token, SyncFlyZonesDto dto) throws Exception {
        // 验证token
        tokenUtil.checkToken(token);

        int insertedCount = 0;
        int skippedCount = 0;
        LocalDateTime now = LocalDateTime.now();

        for (SyncFlyZonesDto.FlyZoneItem item : dto.getZones()) {
            // 检查是否已存在（按 dji_fly_zone_id 去重）
            if (item.getDjiFlyZoneId() != null) {
                FlyZones existing = flyZonesMapper.selectByDjiFlyZoneId(item.getDjiFlyZoneId());
                if (existing != null) {
                    skippedCount++;
                    continue;
                }
            }

            FlyZones flyZone = new FlyZones();
            flyZone.setDjiFlyZoneId(item.getDjiFlyZoneId());
            flyZone.setName(item.getName());
            flyZone.setCategory(item.getCategory());
            flyZone.setShape(item.getShape());
            flyZone.setCenterLat(item.getCenterLat());
            flyZone.setCenterLng(item.getCenterLng());
            flyZone.setRadius(item.getRadius());
            if (item.getPolygonPoints() != null && !item.getPolygonPoints().isEmpty()) {
                flyZone.setPolygonPoints(objectMapper.writeValueAsString(item.getPolygonPoints()));
            }
            flyZone.setMaxAltitude(item.getMaxAltitude());
            flyZone.setSource(0); // DJI同步
            flyZone.setEnabled(1);
            flyZone.setCreateTime(now);
            flyZone.setModificationTime(now);

            flyZonesMapper.insertFlyZone(flyZone);
            insertedCount++;
        }

        // 清除Redis缓存
        clearCache();

        Map<String, Object> result = new HashMap<>();
        result.put("message", "同步成功");
        Map<String, Object> data = new HashMap<>();
        data.put("inserted", insertedCount);
        data.put("skipped", skippedCount);
        result.put("data", data);
        return result;
    }

    @Override
    public Map<String, Object> createFlyZone(String token, CreateFlyZoneDto dto) throws Exception {
        // 管理员权限校验
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        Integer role = Integer.parseInt(checkToken.get("role").toString());
        if (role != 1) {
            throw new AppException(AppExceptionCodeMsg.PERMISSION_DENIED);
        }

        LocalDateTime now = LocalDateTime.now();
        FlyZones flyZone = new FlyZones();
        flyZone.setName(dto.getName());
        flyZone.setCategory(dto.getCategory());
        flyZone.setShape(dto.getShape());
        flyZone.setCenterLat(dto.getCenterLat());
        flyZone.setCenterLng(dto.getCenterLng());
        flyZone.setRadius(dto.getRadius());
        flyZone.setPolygonPoints(dto.getPolygonPoints());
        flyZone.setMaxAltitude(dto.getMaxAltitude());
        flyZone.setDescription(dto.getDescription());
        flyZone.setSource(1); // 管理员手动创建
        flyZone.setCreatorId(userId);
        flyZone.setEnabled(1);
        flyZone.setCreateTime(now);
        flyZone.setModificationTime(now);

        flyZonesMapper.insertFlyZone(flyZone);
        clearCache();

        Map<String, Object> result = new HashMap<>();
        result.put("message", "创建成功");
        Map<String, Object> data = new HashMap<>();
        data.put("id", flyZone.getId());
        result.put("data", data);
        return result;
    }

    @Override
    public Map<String, Object> updateFlyZone(String token, UpdateFlyZoneDto dto) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        Integer role = Integer.parseInt(checkToken.get("role").toString());
        if (role != 1) {
            throw new AppException(AppExceptionCodeMsg.PERMISSION_DENIED);
        }

        FlyZones existing = flyZonesMapper.selectFlyZoneById(dto.getId());
        if (existing == null) {
            throw new AppException(AppExceptionCodeMsg.FLY_ZONE_NOT_EXIST);
        }

        FlyZones flyZone = new FlyZones();
        flyZone.setId(dto.getId());
        flyZone.setName(dto.getName());
        flyZone.setCategory(dto.getCategory());
        flyZone.setShape(dto.getShape());
        flyZone.setCenterLat(dto.getCenterLat());
        flyZone.setCenterLng(dto.getCenterLng());
        flyZone.setRadius(dto.getRadius());
        flyZone.setPolygonPoints(dto.getPolygonPoints());
        flyZone.setMaxAltitude(dto.getMaxAltitude());
        flyZone.setDescription(dto.getDescription());
        flyZone.setEnabled(dto.getEnabled());
        flyZone.setModificationTime(LocalDateTime.now());

        flyZonesMapper.updateFlyZone(flyZone);
        clearCache();

        Map<String, Object> result = new HashMap<>();
        result.put("message", "更新成功");
        return result;
    }

    @Override
    public String deleteFlyZones(String token, DeleteFlyZonesDto dto) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        Integer role = Integer.parseInt(checkToken.get("role").toString());
        if (role != 1) {
            throw new AppException(AppExceptionCodeMsg.PERMISSION_DENIED);
        }

        int result = flyZonesMapper.deleteFlyZones(dto.getIds());
        clearCache();

        if (result > 0) {
            return "删除成功";
        } else {
            return "删除失败";
        }
    }

    @Override
    public Map<String, Object> getFlyZonesList(String token, Integer page, Integer pageSize, String keyword, Integer category) throws Exception {
        Map<String, Object> checkToken = tokenUtil.checkToken(token);
        Integer role = Integer.parseInt(checkToken.get("role").toString());
        if (role != 1) {
            throw new AppException(AppExceptionCodeMsg.PERMISSION_DENIED);
        }

        int offset = (page - 1) * pageSize;
        List<Map<String, Object>> flyZonesList = flyZonesMapper.selectFlyZonesList(keyword, category, offset, pageSize);
        int total = flyZonesMapper.countFlyZones(keyword, category);

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", page);
        pagination.put("size", pageSize);
        pagination.put("total", total);
        pagination.put("totalPage", (int) Math.ceil((double) total / pageSize));

        Map<String, Object> result = new HashMap<>();
        result.put("message", "获取成功");
        Map<String, Object> data = new HashMap<>();
        data.put("flyZonesList", flyZonesList);
        data.put("pagination", pagination);
        result.put("data", data);
        return result;
    }

    @Override
    public Map<String, Object> getAllEnabledFlyZones() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "获取成功");

        // 先从Redis缓存读取
        try {
            String cached = redisTemplate.opsForValue().get(REDIS_KEY_ALL_ENABLED);
            if (cached != null) {
                List<?> cachedList = objectMapper.readValue(cached, List.class);
                result.put("data", cachedList);
                return result;
            }
        } catch (Exception e) {
            log.warn("Redis缓存读取失败，从数据库获取: {}", e.getMessage());
        }

        // 缓存未命中，查数据库
        List<FlyZones> flyZonesList = flyZonesMapper.selectAllEnabledFlyZones();
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (FlyZones fz : flyZonesList) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", fz.getId());
            item.put("djiFlyZoneId", fz.getDjiFlyZoneId());
            item.put("name", fz.getName());
            item.put("category", fz.getCategory());
            item.put("shape", fz.getShape());
            item.put("centerLat", fz.getCenterLat());
            item.put("centerLng", fz.getCenterLng());
            item.put("radius", fz.getRadius());
            // 解析 polygonPoints JSON
            if (fz.getPolygonPoints() != null && !fz.getPolygonPoints().isEmpty()) {
                try {
                    item.put("polygonPoints", objectMapper.readValue(fz.getPolygonPoints(), List.class));
                } catch (Exception e) {
                    item.put("polygonPoints", new ArrayList<>());
                }
            }
            item.put("maxAltitude", fz.getMaxAltitude());
            item.put("description", fz.getDescription());
            item.put("source", fz.getSource());
            dataList.add(item);
        }

        // 写入Redis缓存
        try {
            String json = objectMapper.writeValueAsString(dataList);
            redisTemplate.opsForValue().set(REDIS_KEY_ALL_ENABLED, json, CACHE_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis缓存写入失败: {}", e.getMessage());
        }

        result.put("data", dataList);
        return result;
    }

    private void clearCache() {
        try {
            redisTemplate.delete(REDIS_KEY_ALL_ENABLED);
        } catch (Exception e) {
            log.warn("Redis缓存清除失败: {}", e.getMessage());
        }
    }
}
