package lceye.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(){
        // 일단 기본적은 WebClient 생성 및 반환
        return WebClient.builder()
                .baseUrl("http://localhost:8080/")
                .build();
    } // func end
} // class end