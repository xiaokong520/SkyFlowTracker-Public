package com.example.skyflowtracker.service.inte;

import com.example.skyflowtracker.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface UsersServiceInte {
    public String registerUser(RegisterDto registerDto);
    public Map<String,Object> login(LoginDto loginDto,HttpServletRequest request) throws Exception;
    public String logout(String token) throws Exception;
    public Map<String,Object> getUserInfo(String token) throws Exception;
    public String forgotPassword(ForgotPasswordDto forgotPasswordDto);
    String updatePassword(@Valid UpdatePasswordDto updatePasswordDto, String token) throws Exception;
    String updateAvatar(MultipartFile file, String userId, String token) throws Exception;
    String updateUserInfo(@Valid UpdateUserInfoDto updateUserInfoDto, String token) throws Exception;
    Map<String,Object> checkToken(String token) throws Exception;
    Map<String, Object> getUsersInfoList(String token,Integer page,Integer pageSize) throws Exception;
    String deleteUsers(String token, List<String> userIds) throws Exception;
}
