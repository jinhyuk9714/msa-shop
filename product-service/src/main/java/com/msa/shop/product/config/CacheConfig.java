package com.msa.shop.product.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@EnableCaching
public class CacheConfig {

    /** Redis 제외 시(profile local) 인메모리 캐시 사용. */
    @Bean
    @Profile("local")
    @Primary
    public CacheManager localCacheManager() {
        return new ConcurrentMapCacheManager("products", "product");
    }
}
