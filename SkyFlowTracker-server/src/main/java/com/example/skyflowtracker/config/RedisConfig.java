package com.example.skyflowtracker.config;

import com.example.skyflowtracker.exception.AppExceptionCodeMsg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig implements ApplicationRunner {
    private RedisConnectionFactory redisConnectionFactory;
    @Autowired
    public void RedisConfig(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }
    //redis无连接自动关闭服务端
    @Override
    public void run(ApplicationArguments args) throws Exception{
        try(RedisConnection connection = redisConnectionFactory.getConnection()){
            connection.ping();
        }catch (Exception e){
            throw new RedisConnectionFailureException(AppExceptionCodeMsg.REDIS_CONNECTION_FAILED.getMessage());
        }
    }

    //redis的序列化与反序列化
    @Bean
    public RedisTemplate<String,String> redisTemplate(RedisConnectionFactory factory){

        RedisTemplate<String,String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        // 使用StringRedisSerializer来序列化和反序列化redis的key值
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        // 设置hash key 和value序列化模式
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());

        template.afterPropertiesSet();

        return template;
    }
}
