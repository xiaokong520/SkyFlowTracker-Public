package com.example.skyflowtracker.service.impl;

import com.example.skyflowtracker.dto.*;
import com.example.skyflowtracker.exception.AppException;
import com.example.skyflowtracker.exception.AppExceptionCodeMsg;
import com.example.skyflowtracker.mapper.UsersMapper;
import com.example.skyflowtracker.pojo.Users;
import com.example.skyflowtracker.service.inte.UsersServiceInte;
import com.example.skyflowtracker.utils.PasswordUtil;
import com.example.skyflowtracker.utils.TokenUtil;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UsersService implements UsersServiceInte {
    private UsersMapper usersMapper;
    private EmailService emailService;
    private RedisTemplate<String,String> redisTemplate;
    private PasswordUtil passwordUtil;
    private TokenUtil tokenUtil;

    @Value("${nginx.baseUrl}")
    private String nginxBaseUrl;
    @Value("${nginx.basePath}")
    private String nginxBasePath;

    @Autowired
    public UsersService(UsersMapper usersMapper, EmailService emailService, RedisTemplate<String, String> redisTemplate,
                        PasswordUtil passwordUtil, TokenUtil tokenUtil) {
        this.usersMapper = usersMapper;
        this.emailService = emailService;
        this.redisTemplate = redisTemplate;
        this.passwordUtil = passwordUtil;
        this.tokenUtil = tokenUtil;
    }

    @Override
    public String registerUser(RegisterDto registerDto) {
        //判断该用户是否存在
        Users users = usersMapper.selectUserByUserName(registerDto.getUserName());
        if (users != null){
            throw new AppException(AppExceptionCodeMsg.USER_EXIST);
        }
        Users users1 = usersMapper.selectUserByEmail(registerDto.getEmail());
        if (users1 != null){
            throw new AppException(AppExceptionCodeMsg.EMAIL_EXIST);
        }
        //判断验证码是否正确
        String email = registerDto.getEmail();
        String code = registerDto.getCode().toLowerCase();
        String verificationCode = redisTemplate.opsForValue().get("SkyFlowTracker:code:" + email);
        if (verificationCode == null || !code.equals(verificationCode.toLowerCase())){
            throw new AppException(AppExceptionCodeMsg.INVALID_CODE);
        }
        //加密密码
        String encodePassword = passwordUtil.encode(registerDto.getPassword());
        //设置注册数据
        LocalDateTime now = LocalDateTime.now();
        registerDto.setUserId(UUID.randomUUID().toString());
        registerDto.setPassword(encodePassword);
        registerDto.setCreateTime(now);
        registerDto.setModificationTime(now);
        registerDto.setStatus(2); //注册完成之后需要管理员审核
        registerDto.setRole(0);  //默认为普通用户，管理员用户需要管理员手动提权，无法直接注册
        //存储数据
        int result = usersMapper.addUser(registerDto);
        if (result > 0){
            //删除redis中存储的验证码
            redisTemplate.delete("SkyFlowTracker:code:" + email);
            //发送注册成功邮件
            try {
                String htmlContent = emailService.readHtmlFile("static/email/registEmail.html");
                htmlContent = htmlContent.replace("${name}",registerDto.getUserName());
                String subject = "SkyFlowTracker注册";
                emailService.sendHtmlEmail(email, subject, htmlContent);
            } catch (MessagingException | IOException e) {
                log.error("发送邮件失败，" + e.getMessage());
            }
            return "注册成功";
        }else {
            return "注册失败";
        }
    }

    @Override
    public Map<String,Object> login(LoginDto loginDto, HttpServletRequest request) throws Exception {
        //判断图片验证码是否正确
        String imgCode = loginDto.getImgCode().toLowerCase();
        String imgId = loginDto.getImgId();
        String code = redisTemplate.opsForValue().get("SkyFlowTracker:imgCode:" + imgId);
        if (code == null || !imgCode.equals(code.toLowerCase())){
            throw new AppException(AppExceptionCodeMsg.INVALID_CODE);
        }
        //判断传入的是userName还是email
        String userName = loginDto.getUserName();
        String email = loginDto.getEmail();
        // 两者都为空，抛出异常
        if ((userName == null || userName.isEmpty()) && (email == null || email.isEmpty())) {
            throw new AppException(AppExceptionCodeMsg.REQUEST_BODY_NOT_FOUND);
        }
        if (userName == null || email == null){
            //验证邮箱格式
            String emailReg = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
            if (email != null && !email.matches(emailReg)){
                throw new AppException(AppExceptionCodeMsg.INVALID_EMAIL);
            }
        }
        //判断账号是否存在
        Users users = usersMapper.login(loginDto);
        if (users == null){
            throw new AppException(AppExceptionCodeMsg.AUTH_ERROR);
        }
        //判断密码是否正确
        Boolean isPassword = passwordUtil.matches(loginDto.getPassword(),users.getPassword());
        if (!isPassword){
            throw new AppException(AppExceptionCodeMsg.AUTH_ERROR);
        }
        //判断账号状态
        if (users.getStatus() == 0){
            throw new AppException(AppExceptionCodeMsg.USER_BLOCK);
        }
        if (users.getStatus() == 2){
            throw new AppException(AppExceptionCodeMsg.USER_NOT_APPROVED);
        }
        //生成token
        String token = tokenUtil.creatToken(users.getUserId(),users.getRole());
        //判断是否异地登录
        String oldIp = users.getIp();
        String newIp = getClientReaIp(request);
        if (oldIp != null && !oldIp.equals(newIp)){
            try {
                //发送异地登登录邮件提醒
                String htmlContent = emailService.readHtmlFile("static/email/loginEmail.html");
                htmlContent = htmlContent.replace("${ip}",users.getIp()).replace("${name}",users.getUserName());
                String subject = "SkyFlowTracker异地登录提醒";
                emailService.sendHtmlEmail(users.getEmail(), subject, htmlContent);
            }catch (MessagingException | IOException e){
                log.error("发送邮件失败," + e.getMessage());
            }
        }
        //修改IP地址与登录时间
        LocalDateTime now = LocalDateTime.now();
        users.setIp(newIp);
        users.setModificationTime(now);
        users.setLastLoginTime(now);
        int result = usersMapper.updateIp(users);
        if (result > 0){
            log.info("修改用户数据成功");
        }else {
            log.error("修改用户数据失败");
        }
        //删除redis中存储的验证码
        redisTemplate.delete("SkyFlowTracker:imgCode:" + imgId);
        //构建返回消息
        Map<String,Object> map = new HashMap<>();
        Map<String,Object> data = new HashMap<>();
        map.put("message","登录成功");
        data.put("token",token);
        data.put("role",users.getRole());
        map.put("data",data);
        return map;
    }

    @Override
    public String logout(String token) throws Exception {
        //将token存储到redis中
        Long result = redisTemplate.opsForSet().add("SkyFlowTracker:blacklist",token);
        redisTemplate.expire("SkyFlowTacker:blacklist",7,TimeUnit.DAYS); //设置7天自动清理
        if (result== 1){
            return "退出登录成功";
        }else {
            return "退出登录失败";
        }
    }

    @Override
    public Map<String, Object> getUserInfo(String token) throws Exception {
        //获取userId
        Map<String,Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        //通过userId查询用户信息
        Users users = usersMapper.selectUserByUserId(userId);
        if (users == null){
            throw new AppException(AppExceptionCodeMsg.USER_NOT_EXIST);
        }
        //构造返回数据
        String ip = maskIp(users.getIp());
        //判断数据库中是否存储该用户头像地址，如果没有存储地址那么再判断是否是QQ邮箱，是QQ邮箱则默认使用QQ头像，如果不是QQ邮箱则使用默认头像
        String email = users.getEmail();
        String avatar = users.getAvatar();
        if (avatar == null){
            String suffix = email.split("@")[1];
            if (suffix.equals("qq.com")){
                String qq = email.split("@")[0];
                avatar = "https://q1.qlogo.cn/g?b=qq&nk=" + qq + "&s=640";
            }else {
                avatar = nginxBaseUrl + "images/avatar/DefaultAvatar.png";
            }
        }else {
            //拼接头像地址
            avatar = nginxBaseUrl + avatar;
        }
        Map<String,Object> map = new HashMap<>();
        map.put("message","获取成功");
        Map<String,Object> data = new HashMap<>();
        data.put("userId", users.getUserId());
        data.put("userName", users.getUserName());
        data.put("email", users.getEmail());
        data.put("ip",ip);
        data.put("role",users.getRole());
        data.put("avatar",avatar);
        data.put("lastLoginTime",users.getLastLoginTime());
        data.put("createTime",users.getCreateTime());
        data.put("status",users.getStatus());
        data.put("nickName",users.getNickName());
        map.put("data",data);
        return map;
    }

    @Override
    public String forgotPassword(ForgotPasswordDto forgotPasswordDto) {
        //判断验证码是否正确
        String email = forgotPasswordDto.getEmail();
        String code = forgotPasswordDto.getCode().toLowerCase();
        String verificationCode = redisTemplate.opsForValue().get("SkyFlowTracker:code:" + email);
        if (verificationCode == null || !code.equals(verificationCode.toLowerCase())){
            throw new AppException(AppExceptionCodeMsg.INVALID_CODE);
        }
        //查询该用户是否存在
        Users user = usersMapper.selectUserByEmail(email);
        if (user == null){
            throw new AppException(AppExceptionCodeMsg.USER_NOT_EXIST);
        }
        //查询该账号是否被封禁
        if (user.getStatus() == 0){
            throw new AppException(AppExceptionCodeMsg.USER_BLOCK);
        }
        if (user.getStatus() == 2){
            throw new AppException(AppExceptionCodeMsg.USER_NOT_APPROVED);
        }
        //加密密码
        String password = passwordUtil.encode(forgotPasswordDto.getPassword());
        user.setPassword(password);
        //更新数据库
        user.setModificationTime(LocalDateTime.now());
        int result = usersMapper.updatePassword(user);
        if (result > 0){
            //删除redis中储存的验证码
            redisTemplate.delete("SkyFlowTracker:code:" + email);
            return "修改成功";
        }else {
            return "修改失败";
        }
    }

    @Override
    public String updatePassword(UpdatePasswordDto updatePasswordDto, String token) throws Exception {
        //检查token
        Map<String,Object> checkToken = tokenUtil.checkToken(token);
        //获取userId以及role
        String userId = checkToken.get("userId").toString();
        Integer role = (Integer) checkToken.get("role");
        //判断updatePasswordDto中userId是否存在，不存在则判断是否是管理员
        //只有管理员才能修改所有人的密码，普通用户只能修改自己的密码
        String updateUserId = updatePasswordDto.getUserId();
        if (updateUserId != null){
            if (role != 1){
                throw new AppException(AppExceptionCodeMsg.PERMISSION_DENIED);
            }else {
                userId = updateUserId;
            }
        }
        //判断该用户是否存在且是否封禁
        Users users = usersMapper.selectUserByUserId(userId);
        System.out.println(userId);
        if (users == null || users.getStatus() == 0){
            throw new AppException(AppExceptionCodeMsg.USER_BLOCK);
        }
        if (users.getStatus() == 2 && role != 1){
            throw new AppException(AppExceptionCodeMsg.USER_NOT_APPROVED);
        }
        //修改密码
        users.setPassword(passwordUtil.encode(updatePasswordDto.getPassword()));
        users.setModificationTime(LocalDateTime.now());
        int result = usersMapper.updatePassword(users);
        if (result > 0){
            //将原token加入黑名单中
            redisTemplate.expire("SkyFlowTacker:blacklist",7,TimeUnit.DAYS);
            return "修改成功";
        }else {
            return "修改失败";
        }
    }

    @Override
    public String updateAvatar(MultipartFile file, String userId, String token) throws Exception {
        //检查token
        Map<String,Object> checkToken = tokenUtil.checkToken(token);
        //获取userId以及role
        String checkUserId = checkToken.get("userId").toString();
        Integer role = (Integer) checkToken.get("role");
        //判断userId是否存在，不存在则判断是否是管理员
        //只有管理员才能修改所有人的头像，普通用户只能修改自己的头像
        if (userId != null){
            if (role != 1){
                throw new AppException(AppExceptionCodeMsg.PERMISSION_DENIED);
            }else {
                checkUserId = userId;
            }
        }
        //判断该用户是否存在且是否封禁
        Users users = usersMapper.selectUserByUserId(checkUserId);
        if (users == null || users.getStatus() == 0){
            throw new AppException(AppExceptionCodeMsg.USER_BLOCK);
        }
        if (users.getStatus() == 2 && role != 1){
            throw new AppException(AppExceptionCodeMsg.USER_NOT_APPROVED);
        }
        //构建保存目录
        String saveDir = nginxBasePath + "images/avatar";
        File f = new File(saveDir);
        if (!f.exists()) f.mkdirs();
        //获取原始文件名
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isEmpty()){
            throw new AppException(AppExceptionCodeMsg.IMAGE_NOT_EXIST);
        }
        //提取文件扩展名
        String extension = "";
        int doIndex = originalName.lastIndexOf(".");
        if (doIndex > 0){
            extension = originalName.substring(doIndex);
        }
        //使用userId作为文件名，防止文件名重复
        String fileName = checkUserId + extension;
        System.out.println(fileName);
        String saveFile = saveDir + File.separator + fileName;
        //保存文件
        File dest = new File(saveFile);
        try(FileOutputStream fos = new FileOutputStream(saveFile)) {
            fos.write(file.getBytes());
        }catch (Exception e){
            log.error("保存文件时出错", e.getMessage());
            throw new AppException(AppExceptionCodeMsg.FILE_SAVE_ERROR);
        }
        //拼接Url并保存到数据库中
        users.setAvatar("images/avatar/" + fileName);
        users.setModificationTime(LocalDateTime.now());
        int result = usersMapper.updateAvatar(users);
        if (result > 0){
            return "修改成功";
        }else {
            return "修改失败";
        }
    }

    @Override
    public String updateUserInfo(UpdateUserInfoDto updateUserInfoDto, String token) throws Exception {
        //检查token
        Map<String,Object> checkToken = tokenUtil.checkToken(token);
        //获取userId以及role
        String userId = checkToken.get("userId").toString();
        Integer role = (Integer) checkToken.get("role");
        //判断updateUserInfoDto中userId是否存在，不存在则判断是否是管理员
        //只有管理员才能修改所有人的用户信息，普通用户只能修改自己的昵称
        String updateUserId = updateUserInfoDto.getUserId();
        Integer status = updateUserInfoDto.getStatus();
        Integer role2 = updateUserInfoDto.getRole();
        if (updateUserId != null){
            System.out.println(role2);
            if (role != 1){
                if (status != null || role2 != null){
                    throw new AppException(AppExceptionCodeMsg.PERMISSION_DENIED);
                }
            }else {
                userId = updateUserId;
            }
        }
        //判断该用户是否存在
        Users users = usersMapper.selectUserByUserId(userId);
        if (users == null){
            throw new AppException(AppExceptionCodeMsg.USER_NOT_EXIST);
        }
        if (users.getStatus() == 2 && role != 1){
            throw new AppException(AppExceptionCodeMsg.USER_NOT_APPROVED);
        }
        Integer oldStatus = users.getStatus();
        //修改数据库
        updateUserInfoDto.setUserId(userId);
        updateUserInfoDto.setModificationTime(LocalDateTime.now());
        int result = usersMapper.updateUserInfo(updateUserInfoDto);
        if (result > 0){
            //如果旧状态是待审核(2)修改的状态是1，即审核通过，则发送审核通过邮件提醒
            if (oldStatus == 2 && status != null &&  status == 1){
                try {
                    String html = emailService.readHtmlFile("static/email/accountReview.html");
                    String content = html.replace("${name}", users.getUserName());
                    String subject = "SkyFlowTracker审核通过";
                    emailService.sendHtmlEmail(users.getEmail(), subject, content);
                }catch (MessagingException | IOException e){
                    log.error("发送邮件失败，" + e.getMessage());
                }
            }
            return "修改成功";
        }else {
            return "修改失败";
        }
    }

    @Override
    public Map<String,Object> checkToken(String token) throws Exception {
        Map<String,Object> checkToken = tokenUtil.checkToken(token);
        return checkToken;
    }

    @Override
    public Map<String, Object> getUsersInfoList(String token,Integer page,Integer pageSize) throws Exception {
        //检查token获取role
        Map<String,Object> checkToken = tokenUtil.checkToken(token);
        Integer role = (Integer) checkToken.get("role");
        if (role != 1){
            throw new AppException(AppExceptionCodeMsg.PERMISSION_DENIED);
        }
        //计算分页参数
        int offset = (page - 1) * pageSize;
        //查询用户列表和总数
        List<Users> usersList = usersMapper.selectUsersList(offset,pageSize);
        int total = usersMapper.countUsers();
        //构建返回data数据
        List<Map<String,Object>> dataList = new ArrayList<>();
        for (Users user : usersList){
            String email = user.getEmail();
            Map<String,Object> userData = new HashMap<>();
            userData.put("userId", user.getUserId());
            userData.put("userName", user.getUserName());
            userData.put("email", email);
            userData.put("nickName", user.getNickName());
            userData.put("status", user.getStatus());
            userData.put("role", user.getRole());
            userData.put("ip",maskIp(user.getIp()));
            userData.put("createTime", user.getCreateTime());
            userData.put("lastLoginTime", user.getLastLoginTime());
            //构建头像url
            String avatar = user.getAvatar();
            if (avatar == null){
                String suffix = email.split("@")[1];
                if (suffix.equals("qq.com")){
                    String qq = email.split("@")[0];
                    avatar = "https://q1.qlogo.cn/g?b=qq&nk=" + qq + "&s=640";
                }else {
                    avatar = nginxBaseUrl + "images/avatar/DefaultAvatar.png";
                }
            }else {
                //拼接头像地址
                avatar = nginxBaseUrl + avatar;
            }
            userData.put("avatar", avatar);
            dataList.add(userData);
        }
        //构建分页信息
        Map<String,Object> pagination = new HashMap<>();
        pagination.put("page",page);
        pagination.put("pageSize",pageSize);
        pagination.put("total",total);
        pagination.put("totalPages", (int) Math.ceil((double) total / pageSize));
        //构建返回数据
        Map<String,Object> result = new HashMap<>();
        result.put("message","获取成功");
        Map<String,Object> data = new HashMap<>();
        data.put("userInfoList",dataList);
        data.put("pagination",pagination);
        result.put("data",data);
        return result;
    }

    @Override
    public String deleteUsers(String token, List<String> userIds) throws Exception {
        //检查token并判断是否是管理员
        Map<String,Object> checkToken = tokenUtil.checkToken(token);
        String userId = checkToken.get("userId").toString();
        Integer role = (Integer) checkToken.get("role");
        //只有管理员才能删除账号
        if (role != 1){
            throw new AppException(AppExceptionCodeMsg.PERMISSION_DENIED);
        }
        //防止删除自己
        if (userIds.contains(userId)){
            throw new AppException(AppExceptionCodeMsg.CANNOT_DELETE_SELF);
        }
        //批量删除用户
        int result = usersMapper.deleteUsers(userIds);
        if (result > 0){
            log.info("管理员{}删除了{}个用户", userId, result);
            return "删除成功";
        }else {
            return "删除失败";
        }
    }

    private String getClientReaIp(HttpServletRequest request){
        String ip = request.getHeader("X-Forwarded-For");
        if(ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)){
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)){
            ip = request.getRemoteAddr();
        }
        return ip.contains(",") ? ip.split(",")[0].trim() : ip;
    }

    //IP脱敏
    public static String maskIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return "";
        }
        // IPv4 脱敏：192.168.1.1 -> 192.168.*.*
        if (ip.contains(".")) {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".*.*";
            }
        }
        // IPv6 脱敏：2001:0db8:85a3:0000:0000:8a2e:0370:7334 -> 2001:0db8:****:****:****:****:****:****
        if (ip.contains(":")) {
            String[] parts = ip.split(":");
            if (parts.length >= 2) {
                return parts[0] + ":" + parts[1] + ":****:****:****:****:****:****";
            }
        }
        return ip;
    }



}
