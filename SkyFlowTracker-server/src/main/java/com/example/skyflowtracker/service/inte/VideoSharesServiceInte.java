package com.example.skyflowtracker.service.inte;

import com.example.skyflowtracker.dto.CreateShareDto;

import java.util.Map;

public interface VideoSharesServiceInte {
    Map<String, Object> createShare(String token, CreateShareDto createShareDto) throws Exception;

    Map<String, Object> getShareInfo(String shareCode);

    Map<String, Object> getMyShares(String token, Integer page, Integer pageSize) throws Exception;

    String deleteShare(String token, Long shareId) throws Exception;
}
