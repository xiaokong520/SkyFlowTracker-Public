package com.example.skyflowtracker.config;

import lombok.extern.slf4j.Slf4j;
import com.example.skyflowtracker.interceptor.LogInfoInterceptor;
import com.example.skyflowtracker.interceptor.TokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Slf4j
@Configuration
@Component
public class WebConfig implements WebMvcConfigurer {
    private final TokenInterceptor tokenInterceptor;
    private final LogInfoInterceptor LogInfoInterceptor;
    // 当前跨域请求最大有效时长。这里默认1天
    private static final long MAX_AGE = 24 * 60 * 60;

    @Autowired
    public WebConfig(TokenInterceptor tokenInterceptor, LogInfoInterceptor LogInfoInterceptor) {
        this.tokenInterceptor = tokenInterceptor;
        this.LogInfoInterceptor = LogInfoInterceptor;
    }
    //拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry){
        System.out.println(tokenInterceptor);
        registry.addInterceptor(LogInfoInterceptor)//log记录
                .addPathPatterns("/**");
        registry.addInterceptor(tokenInterceptor) //token校验
                .addPathPatterns("/api/v1/users/**")
                .addPathPatterns("/api/v1/devices/**")
                .addPathPatterns("/api/v1/flights/**")
                .addPathPatterns("/api/v1/inference/**")
                .addPathPatterns("/api/v1/dashboard/**")
                .addPathPatterns("/api/v1/ai/**")
                .addPathPatterns("/api/v1/share/**")
                .addPathPatterns("/api/v1/missions/**")
                .addPathPatterns("/api/v1/flyZones/**")
                .excludePathPatterns("/api/v1/users/getVerificationCode","/api/v1/users/register","/api/v1/users/login"
                        ,"/api/v1/users/getImgCode","/api/v1/users/forgotPassword","/api/v1/users/forgotPassword")
                .excludePathPatterns("/api/v1/share/info/**")
                .excludePathPatterns("/api/v1/flyZones/getAllEnabled");
        WebMvcConfigurer.super.addInterceptors(registry);
    }
    //配置CORS规则（跨域）
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")  // 使用 allowedOriginPatterns 替代 allowedOrigins
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)  // 允许携带凭证
                .maxAge(MAX_AGE);
        WebMvcConfigurer.super.addCorsMappings(registry);
    }

}
