package lceye.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {
    private final RedissonClient redissonClient;
    private final AopTransaction aopTransaction;

    @Around("@annotation(lceye.aop.DistributedLock)")
    public Object distributedLock(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 메소드 정보와 어노테이션 가져오기
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

        // 2. Lock Key 생성
        String key = ParameterParser.getDynamicValue(
                signature.getParameterNames(),
                joinPoint.getArgs(),
                distributedLock.lockKey()
        ).toString();
        String lockKey = "lock:project:aop:" + key;
        RLock rLock = redissonClient.getLock(lockKey);
        try {
            if (rLock.isLocked()){
                log.info("[대기 진입] 이미 락이 점유된 상태입니다. 락 획득을 대기합니다.");
            } // if end

            long waitStartTime = System.currentTimeMillis();
            // 3. 락 획득 시도
            boolean available = rLock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );
            long waitTime = System.currentTimeMillis() - waitStartTime;

            // 4. 락 획득에 실패했다면, 메소드 종료
            if (!available){
                log.warn("락 획득 실패 - 키: {}", lockKey);
                return false;
            } // if end

            if (waitTime > 100) { // 예: 100ms 이상 걸렸다면 대기한 것으로 간주
                log.info("[대기 후 획득] {}ms 대기 후 락 획득 성공 - 키: {}", waitTime, lockKey);
            } else {
                log.info("[즉시 획득] 락 획득 성공 - 키: {}", lockKey);
            } // if end

            // 5. 락 획득에 성공했다면, 비지니스 로직 실행
            return aopTransaction.proceed(joinPoint);
        } catch (InterruptedException e) {
            throw new InterruptedException();
        } finally {
            try {
                if (rLock.isLocked() && rLock.isHeldByCurrentThread()) {
                    rLock.unlock();
                    log.info("락 해제 완료 - 키: {}", lockKey);
                } // if end
            } catch (IllegalMonitorStateException e) {
                log.error("락 해제 중 오류 발생 - 이미 해제되었거나 타임아웃됨");
            } // try-catch end
        } // try-catch-finally end
    } // func end
} // class end