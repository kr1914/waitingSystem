package com.lorelime.wqm.waitingline.infrastructure.schedular;


import com.lorelime.wqm.waitingline.common.constant.WaitinglineEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class QueueScheduler {

    // 직접 로거를 선언합니다.
    private static final Logger log = LoggerFactory.getLogger(QueueScheduler.class);

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    public QueueScheduler(ReactiveRedisTemplate<String, String> reactiveRedisTemplate) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
    }

    @Scheduled(fixedDelay = 2000)
    public void processQueue() {
        // 1. 이탈자 제거 (Heartbeat/TTL 키가 없는 유저 삭제)
        cleanDeadUsers()
                .then(promoteUsers()) // 2. 여유 공간만큼 입장 처리
                .subscribe();
    }

    /**
     * TTL 키가 없는 이탈자를 대기열에서 제거합니다.
     */
    private Mono<Void> cleanDeadUsers() {
        String waitingKey = WaitinglineEnum.RedisKey.WAITING_KEY.getValue();
        String ttlPrefix = WaitinglineEnum.RedisKey.WAITING_TTL_KEY.getValue() + ":";

        return reactiveRedisTemplate.opsForZSet()
                .range(waitingKey, org.springframework.data.domain.Range.unbounded())
                .flatMap(userId -> {
                    // 유저별 TTL 키 존재 여부 확인
                    return reactiveRedisTemplate.hasKey(ttlPrefix + userId)
                            .flatMap(exists -> {
                                if (!exists) {
                                    log.info("이탈자 발견 및 삭제: {}", userId);
                                    return reactiveRedisTemplate.opsForZSet().remove(waitingKey, userId);
                                }
                                return Mono.empty();
                            });
                })
                .then();
    }

    /**
     * 여유 공간 확인 후 대기자를 Active 상태로 승격합니다.
     */
    private Mono<Void> promoteUsers() {
        String activePattern = WaitinglineEnum.RedisKey.ACTIVE_KEY.getValue() + ":*";
        int maxActive = 2;

        return reactiveRedisTemplate.keys(activePattern)
                .collectList()
                .flatMap(keys -> {
                    int currentActiveCount = keys.size();
                    int availableSlots = maxActive - currentActiveCount;

                    if (availableSlots <= 0) {
                        return Mono.empty();
                    }

                    return reactiveRedisTemplate.opsForZSet()
                            .popMin(WaitinglineEnum.RedisKey.WAITING_KEY.getValue(), availableSlots)
                            .flatMap(member -> {
                                String userId = member.getValue();
                                log.info("사용자 입장 처리: {}", userId);
                                return reactiveRedisTemplate.opsForValue()
                                        .set(WaitinglineEnum.RedisKey.ACTIVE_KEY.getValue() + ":" + userId,
                                                "allowed", java.time.Duration.ofMinutes(10));
                            })
                            .then();
                });
    }
}