package com.example.skyflowtracker.controller;



import com.example.skyflowtracker.dto.*;
import com.example.skyflowtracker.exception.AppException;
import com.example.skyflowtracker.exception.AppExceptionCodeMsg;
import com.example.skyflowtracker.service.impl.GetCode;
import com.example.skyflowtracker.service.inte.UsersServiceInte;
import com.example.skyflowtracker.vo.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/v1/users",produces = "application/json;charset=UTF-8")
public class UsersController {
    private final UsersServiceInte usersService;
    private GetCode getCode;

    @Autowired
    public UsersController(UsersServiceInte usersService, GetCode getCode) {
        this.usersService = usersService;
        this.getCode = getCode;
    }

    @PostMapping("/getVerificationCode")
    public ResponseEntity<ApiResponse> getVerificationCode(@Valid @RequestBody GetVerificationCodeDto getVerificationCodeDto) throws IOException {
        String email = getVerificationCodeDto.getEmail();
        String message = getCode.getVerificationCode(email);
        if (message.equals("验证码发送成功，请注意查收！")){
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(@Valid @RequestBody RegisterDto registerDto){
        String message = usersService.registerUser(registerDto);
        if (message.equals("注册成功")){
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@Valid @RequestBody LoginDto loginDto,
                                             HttpServletRequest request) throws Exception {
        Map<String,Object> result = usersService.login(loginDto,request);
        String message = result.get("message").toString();
        if (message.equals("登录成功")){
            Map<String,Object> data = (Map<String, Object>) result.get("data");
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message,data));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String token) throws Exception {
        String message = usersService.logout(token);
        if (message.equals("退出登录成功")){
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @GetMapping("/getUserInfo")
    public ResponseEntity<ApiResponse> getUserInfo(@RequestHeader(HttpHeaders.AUTHORIZATION) String token) throws Exception {
        Map<String,Object> map = usersService.getUserInfo(token);
        String message = map.get("message").toString();
        Map<String,Object> data = (Map<String, Object>) map.get("data");
        if (message.equals("获取成功")){
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message,data));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @PutMapping("/forgotPassword")
    public ResponseEntity<ApiResponse> forgotPassword(@Valid @RequestBody ForgotPasswordDto forgotPasswordDto){
        String message = usersService.forgotPassword(forgotPasswordDto);
        if (message.equals("修改成功")){
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message));
        }else {
            return  ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @PutMapping("/updatePassword")
    public ResponseEntity<ApiResponse> updatePassword(@Valid @RequestBody UpdatePasswordDto updatePasswordDto,
                                                      @RequestHeader(HttpHeaders.AUTHORIZATION) String token) throws Exception {
        String message = usersService.updatePassword(updatePasswordDto,token);
        if (message.equals("修改成功")){
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @PutMapping("/updateAvatar")
    public ResponseEntity<ApiResponse> updateAvatar(@RequestParam("file") MultipartFile file,
                                                    @RequestParam(value = "userId",required = false) String userId,
                                                    @RequestHeader(HttpHeaders.AUTHORIZATION) String token) throws Exception {
        //判断参数是否为空
        if (file.isEmpty()){
            throw new AppException(AppExceptionCodeMsg.IMAGE_NOT_EXIST);
        }
        System.out.println(userId);
        String message = usersService.updateAvatar(file,userId,token);
        if (message.equals("修改成功")){
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @PutMapping("/updateUserInfo")
    public ResponseEntity<ApiResponse> updateUserInfo(@Valid @RequestBody UpdateUserInfoDto updateUserInfoDto,
                                                      @RequestHeader(HttpHeaders.AUTHORIZATION) String token) throws Exception {
        String message = usersService.updateUserInfo(updateUserInfoDto,token);
        if (message.equals("修改成功")){
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @PostMapping("/checkToken")
    public ResponseEntity<ApiResponse> checkToken(@RequestHeader(HttpHeaders.AUTHORIZATION) String token) throws Exception{
        Map<String,Object> result = usersService.checkToken(token);
        String messageStr = result.get("message").toString();
        String UserId = result.get("userId").toString();
        Integer role = Integer.parseInt(result.get("role").toString());
        Map<String,Object> data = new HashMap<>();
        data.put("userId", UserId);
        data.put("role",role);
        if (messageStr.equals("验证通过")){
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(messageStr,data));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(messageStr));
        }
    }

    @GetMapping("/getUsersInfoList")
    public ResponseEntity<ApiResponse> getUsersInfoList(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                        @RequestParam(defaultValue = "1") Integer page,
                                                        @RequestParam(defaultValue = "10") Integer pageSize) throws Exception{
        Map<String,Object> map = usersService.getUsersInfoList(token,page,pageSize);
        String message = map.get("message").toString();
        if (message.equals("获取成功")){
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message,map.get("data")));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @DeleteMapping("/deleteUsers")
    public ResponseEntity<ApiResponse> deleteUsers(@RequestHeader(HttpHeaders.AUTHORIZATION) String token,
                                                   @RequestBody DeleteUsersIdsDto deleteUsersIdsDto) throws Exception{
        String message = usersService.deleteUsers(token,deleteUsersIdsDto.getUserIds());
        if (message.equals("删除成功")){
            return  ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.success(message));
        }else {
            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(ApiResponse.error(message));
        }
    }

    @GetMapping("/getImgCode")
    public ResponseEntity<ApiResponse> getImgCode(){
        Map<String,String> data = getCode.getImagCode();
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("获取成功",data));
    }

}
