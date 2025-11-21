package lceye.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RedisService implements MessageListener {
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            System.out.println("[8081 서버] 요청 받음! ");

            System.out.println("[8081 서버] 응답 보냄: ");
        } catch (Exception e) {
            System.out.println("e = " + e);
        } // try-catch end
    } // func end
} // class end