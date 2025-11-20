package lceye.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

import lceye.model.dto.MemberDto;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RedisService implements MessageListener {
    private final ObjectMapper objectMapper;
    private MemberDto result = null;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody());
        try {
            result = objectMapper.readValue(body, MemberDto.class);
            System.out.println("result = " + result);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } // try-catch end
    } // func end

    public MemberDto getMemberDto(){
        return result;
    } // func end
} // class end