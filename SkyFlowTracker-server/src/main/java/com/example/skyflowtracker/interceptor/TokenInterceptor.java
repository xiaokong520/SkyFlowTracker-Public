package com.example.skyflowtracker.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.example.skyflowtracker.utils.TokenUtil;
import com.example.skyflowtracker.vo.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Map;

//自定义拦截规则
@Component
public class TokenInterceptor implements HandlerInterceptor {
    private  TokenUtil tokenUtil;
    private RedisTemplate<String,String> redisTemplate;

    @Autowired
    public TokenInterceptor(TokenUtil tokenUtil, RedisTemplate<String,String> redisTemplate) {
        this.tokenUtil = tokenUtil;
        this.redisTemplate = redisTemplate;
    }

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
       response.setContentType("application/json;charset=utf-8");
        //获取请求头中的token
        String token = request.getHeader("Authorization");
        //判断token是否为空
        if(token == null || token.isEmpty()){
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            ApiResponse<Object> apiResponse = new ApiResponse<>(0,"token不能为空！");
            sendJson(response,apiResponse);
            return false;
        }
        //判断token是否在黑名单中
        Boolean tempToken = redisTemplate.opsForSet().isMember("SkyFlowTracker:blacklist",token);
        if(Boolean.TRUE.equals(tempToken)){
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            ApiResponse<Object> apiResponse = new ApiResponse<>(0,"token已失效！");
            sendJson(response,apiResponse);
            return false;
        }

        //验证token，验证失败返回false，成功返回true
        Map<String,Object> result = tokenUtil.checkToken(token); //验证成功返回body
        String message = result.get("message").toString();
        if (!message.equals("验证通过")){
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            ApiResponse<Object> apiResponse = new ApiResponse<>(0,"用户ID不正确");
            sendJson(response,apiResponse);
            return false;
        }
        return true;
    }

    //发送json数据
    public void sendJson(HttpServletResponse response,ApiResponse<?> apiResponse)
            throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonResponse = objectMapper.writeValueAsString(apiResponse);
        response.getWriter().write(jsonResponse);
    }
}
