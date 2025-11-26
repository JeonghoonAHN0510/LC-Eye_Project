package lceye.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lceye.model.dto.MemberDto;
import lceye.model.dto.RedisRequestDto;
import lceye.model.dto.RedisResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisService implements MessageListener {
    private final RedisTemplate<String, String> redisStringTemplate;
    private final ObjectMapper objectMapper;
    private final ChannelTopic memberTopic;

    // 동기처리를 위한 저장소
    private final Map<String, CompletableFuture<MemberDto>> memberFutures = new ConcurrentHashMap<>();

    public MemberDto getMemberEntityByMno(int mno){
        // 1. 고유한 번호표 생성
        String requestId = UUID.randomUUID().toString();
        // 2. 요청 객체 생성
        RedisRequestDto redisRequestDto = RedisRequestDto.builder()
                .requestId(requestId)
                .mno(mno)
                .build();
        // 3. 요청을 받을 빈 Future를 만들어 대기열에 등록
        CompletableFuture<MemberDto> future = new CompletableFuture<>();
        memberFutures.put(requestId, future);
        try {
            // 4. 8080 서버로 요청 전송
            String jsonRequest = objectMapper.writeValueAsString(redisRequestDto);
            redisStringTemplate.convertAndSend(memberTopic.getTopic(), jsonRequest);
            System.out.println("[8081] 요청 전송 (ID: " + requestId + ", MNO: " + mno + ")");
            // 5. 응답이 올때까지 5초 대기
            return future.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // 6. 타임아웃 시 Map에서 제거하고 null 처리 혹은 예외 던지기
            memberFutures.remove(requestId);
            log.error(e.getMessage());
            return null;
        } catch (Exception e) {
            memberFutures.remove(requestId);
            log.error(e.getMessage());
            throw new RuntimeException("Redis 통신 중 오류 발생");
        } // try-catch end
    } // func end

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // 1. 받은 메시지를 JSON 문자열로 변환
            String body = new String(message.getBody());
            RedisResponseDto responseDto = objectMapper.readValue(body, RedisResponseDto.class);
            System.out.println("[8081 서버] 요청 받음 " + responseDto);
            // 2. 대기열에서 해당 요청번호를 가진 Future 찾기
            String requestId = responseDto.getResponseId();
            CompletableFuture<MemberDto> future = memberFutures.remove(requestId);
            // 3. 기다리전 Future에 값 넣어주기
            if (future != null){
                future.complete(responseDto.getResponseMember());
            } // if end
        } catch (Exception e) {
            log.error(e.getMessage());
        } // try-catch end
    } // func end
} // class end