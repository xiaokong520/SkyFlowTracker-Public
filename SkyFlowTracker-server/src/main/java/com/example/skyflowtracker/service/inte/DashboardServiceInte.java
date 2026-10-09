package com.example.skyflowtracker.service.inte;

import java.util.Map;

public interface DashboardServiceInte {
    Map<String, Object> getDashboardData(String token, String detectionRange, String flightRange) throws Exception;
}
