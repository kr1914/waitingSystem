package com.lorelime.wqm.waitingline.api;

import com.lorelime.wqm.waitingline.common.constant.WaitinglineEnum;
import com.lorelime.wqm.waitingline.domain.repository.WaitingLineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class SseWaitingController {

    private static final Logger log = LoggerFactory.getLogger(SseWaitingController.class);

    private final WaitingLineRepository waitingLineRepository;

    public SseWaitingController(WaitingLineRepository waitingLineRepository) {
        this.waitingLineRepository = waitingLineRepository;
    }

    /**
     * 1. 대기열 등록 및 SSE 연결
     * 사용자가 접속하면 Redis ZSET에 등록하고 순번을 실시간으로 Push합니다.
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> subscribe(@RequestParam(name = "userId", required = false) String userId) {
        final String finalUserId = userId;
        double now = Instant.now().getNano();

        // 1. Mono<Boolean>으로 유저 등록을 먼저 완료함
        return waitingLineRepository.setUserWaiting(finalUserId, now)
                .thenMany( // 2. 등록(Mono)이 성공하면 아래 Flux 스트림을 시작함
                        Flux.interval(Duration.ofSeconds(1))
                                .flatMap(tick ->
                                        // 3. 내부 로직도 Mono를 조합하여 결과를 도출
                                        waitingLineRepository.isUserActive(finalUserId)
                                                .flatMap(active -> {
                                                    if (active) return Mono.just("입장 가능!");
                                                    // Active가 아니면 순번 조회 (Mono를 반환해야 flatMap이 동작함)
                                                    return waitingLineRepository.getUserRank(finalUserId)
                                                            .map(rank -> "내 앞 대기자 수: " + rank + "명")
                                                            .defaultIfEmpty("순번 계산 중...");
                                                })
                                )
                                .map(message -> ServerSentEvent.<String>builder()
                                        .id(finalUserId)
                                        .event("queue-status")
                                        .data(message)
                                        .build())
                )
                .doFinally(signalType -> {
                    log.info("SSE disconnected for user: {}", finalUserId);
                    // 비동기로 유저 삭제 실행
                    waitingLineRepository.removeUser(finalUserId).subscribe();
                });
    }

    /**
     * 2. 상태 갱신 (Heartbeat) API
     * 클라이언트가 주기적으로 호출하여 대기열에서의 순번을 유지(Score 갱신)합니다.
     */
    @PostMapping("/touch")
    public Mono<String> touch(@RequestParam String userId) {
        // 현재 시간을 Score로 사용하여 순서를 최신화합니다.
        double now = Instant.now().getEpochSecond();

        return waitingLineRepository.setUserWaiting(userId, now)
                .map(success -> "Updated")
                .defaultIfEmpty("Failed to Update");
    }

    /**
     * 3. 대기열 이탈 처리
     * 사용자가 명시적으로 대기열을 나갈 때 호출합니다.
     */
    @PostMapping("/out")
    public Mono<String> out(@RequestParam String userId) {
        log.info("Request to leave queue: {}", userId);

        return waitingLineRepository.removeUser(userId)
                .map(removedCount -> {
                    if (removedCount > 0) {
                        log.info("User {} successfully removed from queue.", userId);
                        return "Removed";
                    }
                    return "User not found in queue";
                })
                .onErrorResume(e -> {
                    log.error("Error occurred while removing user from Redis: ", e);
                    return Mono.just("Error: " + e.getMessage());
                });
    }
}