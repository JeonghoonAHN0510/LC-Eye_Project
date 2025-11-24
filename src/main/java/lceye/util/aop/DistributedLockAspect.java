package lceye.util.aop;

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

    @Around("@annotation(lceye.util.aop.DistributedLock)")
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
            // 3. 락 획득 시도
            boolean available = rLock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );
            // 4. 락 획득에 실패했다면, 메소드 종료
            if (!available){
                log.warn("락 획득 실패 - 키: {}", lockKey);
                return false;
            } // if end
            // 5. 락 획득에 성공했다면, 비지니스 로직 실행
            // 트랜잭션을 보장하기 위해서 클래스 분리
            log.info("락 획득 성공 - 키: {}", lockKey);
            return aopTransaction.proceed(joinPoint);
        } catch (InterruptedException e) {
            throw new InterruptedException();
        } finally {
            try {
                // 6. 락 해제 (중요: 내가 잡은 락인지, 아직 잠겨있는지 확인)
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