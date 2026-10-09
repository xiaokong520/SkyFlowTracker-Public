package com.example.skyflowtracker.controller;

import com.example.skyflowtracker.dto.*;
import com.example.skyflowtracker.service.inte.FlyZonesServiceInte;
import com.example.skyflowtracker.vo.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/flyZones")
public class FlyZonesController {

    private final FlyZonesServiceInte flyZonesService;

    @Autowired
    public FlyZonesController(FlyZonesServiceInte flyZonesService) {
        this.flyZonesService = flyZonesService;
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse> sync(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                            @Valid @RequestBody SyncFlyZonesDto syncFlyZonesDto) throws Exception {
        Map<String, Object> result = flyZonesService.syncFlyZones(token, syncFlyZonesDto);
        String message = (String) result.get("message");
        Object data = result.get("data");
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(message, data));
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse> create(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                              @Valid @RequestBody CreateFlyZoneDto createFlyZoneDto) throws Exception {
        Map<String, Object> result = flyZonesService.createFlyZone(token, createFlyZoneDto);
        String message = (String) result.get("message");
        Object data = result.get("data");
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(message, data));
    }

    @PutMapping("/update")
    public ResponseEntity<ApiResponse> update(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                              @Valid @RequestBody UpdateFlyZoneDto updateFlyZoneDto) throws Exception {
        Map<String, Object> result = flyZonesService.updateFlyZone(token, updateFlyZoneDto);
        String message = (String) result.get("message");
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(message));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<ApiResponse> delete(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                              @Valid @RequestBody DeleteFlyZonesDto deleteFlyZonesDto) throws Exception {
        String message = flyZonesService.deleteFlyZones(token, deleteFlyZonesDto);
        if (message.equals("删除成功")) {
            return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(message));
        } else {
            return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.error(message));
        }
    }

    @GetMapping("/getFlyZonesList")
    public ResponseEntity<ApiResponse> getFlyZonesList(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                       @RequestParam(defaultValue = "1") Integer page,
                                                       @RequestParam(defaultValue = "10") Integer pageSize,
                                                       @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Integer category) throws Exception {
        Map<String, Object> result = flyZonesService.getFlyZonesList(token, page, pageSize, keyword, category);
        String message = (String) result.get("message");
        Object data = result.get("data");
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(message, data));
    }

    @GetMapping("/getAllEnabled")
    public ResponseEntity<ApiResponse> getAllEnabled() {
        Map<String, Object> result = flyZonesService.getAllEnabledFlyZones();
        String message = (String) result.get("message");
        Object data = result.get("data");
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.success(message, data));
    }
}
