package com.example.skyflowtracker.service.inte;

import com.example.skyflowtracker.dto.*;

import java.util.Map;

public interface FlyZonesServiceInte {
    Map<String, Object> syncFlyZones(String token, SyncFlyZonesDto dto) throws Exception;
    Map<String, Object> createFlyZone(String token, CreateFlyZoneDto dto) throws Exception;
    Map<String, Object> updateFlyZone(String token, UpdateFlyZoneDto dto) throws Exception;
    String deleteFlyZones(String token, DeleteFlyZonesDto dto) throws Exception;
    Map<String, Object> getFlyZonesList(String token, Integer page, Integer pageSize, String keyword, Integer category) throws Exception;
    Map<String, Object> getAllEnabledFlyZones();
}
