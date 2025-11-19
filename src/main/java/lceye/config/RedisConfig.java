package lceye.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@Configuration
@EnableRedisHttpSession
public class RedisConfig {
    private RedisSerializer<Object> jsonSerializer() {
        // 1. Jackson(JSON 변환기) 설정
        ObjectMapper objectMapper = new ObjectMapper();

        // 2. Spring Security 전용 모듈 등록 (이게 있어야 권한/인증 객체 해석 가능)
        objectMapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
        // 3. 날짜 타입(LocalDateTime) 처리 모듈 등록
        objectMapper.registerModule(new JavaTimeModule());

        // 4. JSON 설정이 적용된 Serializer 반환
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }


    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory){
        // 1. Redis 템플릿 객체 생성 : Redis 형식을 Map 타입으로 사용하기위한 설정
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        // 2. 생성한 템플릿 객체를 팩토리(Redis 저장소)에 등록
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        // 3. 생성한 템플릿은 key값을 String 타입으로 직렬화한다.
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        // 4. 생성한 템플릿은 value값을 JSON/DTO 타입으로 직렬화한다,
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.setHashValueSerializer(jsonSerializer());
        // 직렬화 : Redis에 저장된 데이터를 자바 타입으로 변환 과정
        return redisTemplate;
    } // func end

    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer(){
        return jsonSerializer();
    } // func end
} // class end