package com.lorelime.wqm.waitingline.infrastructure.persistence;

import com.lorelime.wqm.waitingline.common.constant.WaitinglineEnum;
import com.lorelime.wqm.waitingline.domain.repository.WaitingLineRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.time.Duration;

@Repository
@Slf4j
public class RedisWaitingLineRepository implements WaitingLineRepository {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    public RedisWaitingLineRepository(ReactiveRedisTemplate<String, String> reactiveRedisTemplate) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
    }

    @Override
    public Mono<Boolean> setUserWaiting(String userId, double score) {
        String key = WaitinglineEnum.RedisKey.WAITING_TTL_KEY.getValue() + ":" + userId;
        log.debug("setUserWaiting (TTL Key)=> {}", key);

        // 1. TTL 키 설정 Mono
        Mono<Boolean> setTtlMono = reactiveRedisTemplate.opsForValue()
                .set(key, "ok", Duration.ofSeconds(10));

        // 2. ZSet 추가 Mono
        Mono<Boolean> addZSetMono = reactiveRedisTemplate.opsForZSet()
                .add(WaitinglineEnum.RedisKey.WAITING_KEY.getValue(), userId, score);

        // 3. 두 작업을 순차적으로 연결 (TTL 설정 후 ZSet 추가)
        return setTtlMono.then(addZSetMono);
    }

    @Override
    public Mono<Long> getUserRank(String userId) {
        return reactiveRedisTemplate.opsForZSet()
                .rank(WaitinglineEnum.RedisKey.WAITING_KEY.getValue(), userId);
    }

    @Override
    public Mono<Boolean> isUserActive(String userId) {
        String key = WaitinglineEnum.RedisKey.ACTIVE_KEY.getValue() + ":" + userId;
        return reactiveRedisTemplate.hasKey(key);
    }

    @Override
    public Mono<Long> removeUser(String userId) {
        return reactiveRedisTemplate.opsForZSet()
                .remove(WaitinglineEnum.RedisKey.WAITING_KEY.getValue(), userId);
    }

    @Override
    public Flux<String> getWaitingUsers(long start, long end) {
        // ZSet에서 score(시간) 순으로 정렬된 유저 목록 조회
        return reactiveRedisTemplate.opsForZSet()
                .range(WaitinglineEnum.RedisKey.WAITING_KEY.getValue(), Range.closed(start, end));
    }

    @Override
    public Flux<String> getActiveUsers() {
        // "ACTIVE:*" 패턴의 키들을 찾아서 userId 부분만 추출
        String pattern = WaitinglineEnum.RedisKey.ACTIVE_KEY.getValue() + ":*";
        return reactiveRedisTemplate.keys(pattern)
                .map(key -> key.replace(WaitinglineEnum.RedisKey.ACTIVE_KEY.getValue() + ":", ""));
    }

    // RedisWaitingLineRepository.java 내에 추가
    @Override
    public Mono<Boolean> removeActiveUser(String userId) {
        String key = WaitinglineEnum.RedisKey.ACTIVE_KEY.getValue() + ":" + userId;
        return reactiveRedisTemplate.delete(key).map(count -> count > 0);
    }

    // RedisWaitingLineRepository 구현체에 추가
    @Override
    public Mono<Boolean> refreshWaitTime(String userId) {
        // 대기열 전체(ZSet)의 TTL을 관리하기보다,
        // 개별 사용자의 '최근 활동 시간'을 score로 업데이트하여 순서를 유지하며 갱신하거나
        // 별도의 TTL 전용 키를 운영하는 방식이 일반적입니다.
        // 여기서는 ZSet의 score를 현재 시간으로 업데이트하여 순서를 뒤로 밀지 않고
        // 단순히 '생존'을 확인하는 용도의 별도 키를 예로 듭니다.
        String key = WaitinglineEnum.RedisKey.WAITING_TTL_KEY.getValue()+ ":" + userId;
        log.debug("refreshWaitTime=> {}", key);
        // 키가 없으면 생성(SET)하고, 있으면 덮어쓰면서 TTL을 10초로 갱신합니다.
        // 값(Value)은 단순히 생존 확인용이므로 userId나 "ok" 등을 넣으면 됩니다.
        return reactiveRedisTemplate.opsForValue()
                .set(key, "ok", Duration.ofSeconds(10));
    }

    @Override
    public Mono<Boolean> refreshActiveTime(String userId) {
        String key = WaitinglineEnum.RedisKey.ACTIVE_KEY.getValue() + ":" + userId;
        return reactiveRedisTemplate.expire(key, Duration.ofSeconds(30)); // 30초 연장
    }
}