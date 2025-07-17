package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient() {
        // 1.创建配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.52.200:6379");
        // 2.根据配置创建RedissonClient实例
        return Redisson.create(config);
    }
}
