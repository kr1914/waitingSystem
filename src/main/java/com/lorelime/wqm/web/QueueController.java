package com.lorelime.wqm.web;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
        import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/queue")
public class QueueController {

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private static final String QUEUE_KEY = "queue:default:waiting";

    private static final String WAITING_KEY = "queue:default:waiting";
    private static final String ACTIVE_KEY = "queue:default:active";

    // 스프링이 이 생성자를 보고 빈을 주입해줍니다.
    public QueueController(ReactiveRedisTemplate<String, String> reactiveRedisTemplate) {
        this.reactiveRedisTemplate = reactiveRedisTemplate;
    }

    /**
     * 1. 대기열 등록 및 SSE 연결
     * 사용자가 접속하면 Redis ZSET에 등록하고 순번을 실시간으로 Push합니다.
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> subscribe(@RequestParam(name = "userId", required = false) String userId) {
        final String finalUserId = (userId == null || userId.isEmpty()) ? UUID.randomUUID().toString() : userId;
        long now = Instant.now().getEpochSecond();

        // 1. 유저를 대기열(ZSET)에 등록
        return reactiveRedisTemplate.opsForZSet()
                .add(WAITING_KEY, finalUserId, now)
                .thenMany(
                        // QueueController.java 의 subscribe 메서드 내부 Flux 수정
                        Flux.interval(Duration.ofSeconds(1))
                                .flatMap(tick ->
                                        // 1. 먼저 Active 상태인지 확인
                                        reactiveRedisTemplate.opsForValue().get(ACTIVE_KEY + ":" + finalUserId)
                                                .flatMap(status -> Mono.just("입장 가능!")) // Active 상태면 메시지 변경
                                                .switchIfEmpty(
                                                        // 2. Active가 아니면 현재 순번 조회
                                                        reactiveRedisTemplate.opsForZSet().rank(WAITING_KEY, finalUserId)
                                                                .map(rank -> "내 앞 대기자 수: " + rank + "명")
                                                )
                                )
                                .map(message -> ServerSentEvent.<String>builder()
                                        .id(finalUserId)
                                        .event("queue-status")
                                        .data(message)
                                        .build())
                );
    }

    /**
     * 2. 상태 갱신 (Heartbeat) API
     * 클라이언트가 별도로 생존 신고를 할 때 사용합니다.
     */
    @PostMapping("/touch")
    public Mono<String> touch(@RequestParam String userId) {
        return reactiveRedisTemplate.opsForZSet()
                .add(QUEUE_KEY, userId, Instant.now().getEpochSecond())
                .map(result -> "Updated");
    }
}