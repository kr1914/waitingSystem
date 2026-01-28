package com.lorelime.wqm.schedular;

import lombok.RequiredArgsConstructor;
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

    private static final String WAITING_KEY = "queue:default:waiting";
    private static final String ACTIVE_KEY = "queue:default:active";

    public QueueScheduler(ReactiveRedisTemplate<String, String> reactiveRedisTemplate) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
    }

    @Scheduled(fixedDelay = 5000) // 좀 더 빠른 회전율을 위해 5초로 변경 가능
    public void processQueue() {
        String activePattern = ACTIVE_KEY + ":*";
        int maxActive = 2; // 최대 허용 유저 수

        // 1. 현재 Active 상태인 유저 키가 몇 개인지 확인
        reactiveRedisTemplate.keys(activePattern)
                .collectList()
                .flatMap(keys -> {
                    int currentActiveCount = keys.size();
                    int availableSlots = maxActive - currentActiveCount;

                    if (availableSlots <= 0) {
                        log.info("현재 이용 중인 유저가 꽉 찼습니다. (Active: {})", currentActiveCount);
                        return Mono.empty();
                    }

                    log.info("여유 공간 발견: {}명 추가 입장 가능", availableSlots);

                    // 2. 여유 공간(availableSlots)만큼만 대기열에서 뽑기
                    return reactiveRedisTemplate.opsForZSet()
                            .popMin(WAITING_KEY, availableSlots)
                            .flatMap(member -> {
                                String userId = member.getValue();
                                log.info("사용자 입장 처리: {}", userId);
                                return reactiveRedisTemplate.opsForValue()
                                        .set(ACTIVE_KEY + ":" + userId, "allowed", java.time.Duration.ofMinutes(30));
                            })
                            .then();
                })
                .subscribe();
    }
}