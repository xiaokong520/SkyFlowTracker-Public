package com.example.skyflowtracker.controller;

import com.example.skyflowtracker.service.inte.DashboardServiceInte;
import com.example.skyflowtracker.vo.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardServiceInte dashboardService;

    @Autowired
    public DashboardController(DashboardServiceInte dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/getData")
    public ResponseEntity<ApiResponse> getData(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                               @RequestParam(value = "detectionRange", defaultValue = "30d") String detectionRange,
                                               @RequestParam(value = "flightRange", defaultValue = "30d") String flightRange) throws Exception {
        Map<String, Object> result = dashboardService.getDashboardData(token, detectionRange, flightRange);
        String message = result.get("message").toString();
        Object data = result.get("data");
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(message, data));
    }
}
