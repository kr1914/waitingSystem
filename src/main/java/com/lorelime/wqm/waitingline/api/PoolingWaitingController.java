package com.lorelime.wqm.waitingline.api;


import com.lorelime.wqm.waitingline.api.dto.PoolingRsp;
import com.lorelime.wqm.waitingline.domain.repository.WaitingLineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v2/queue")
public class PoolingWaitingController {

    private static final Logger log = LoggerFactory.getLogger(PoolingWaitingController.class);

    private final WaitingLineRepository waitingLineRepository;


    // 스프링이 이 생성자를 보고 빈을 주입해줍니다.
    public PoolingWaitingController(WaitingLineRepository waitingLineRepository) {
        this.waitingLineRepository = waitingLineRepository;
    }

    @PostMapping(value = "/pooling")
    public Mono<PoolingRsp> subscribe(@RequestParam(name = "userId") String userId) {
        return waitingLineRepository.isUserActive(userId)
                .flatMap(isActive -> {
                    if (isActive) {
                        // 1. 활성 상태라면 TTL 갱신 후 응답
                        return waitingLineRepository.refreshActiveTime(userId)
                                .then(Mono.just(new PoolingRsp(0L, "COMPLETED")));
                    }

                    // 2. 활성 상태가 아니라면 대기열 순번 조회 및 갱신
                    return waitingLineRepository.getUserRank(userId)
                            .flatMap(rank -> {
                                // 대기열에 존재한다면 TTL 성격의 추가 로직 수행 (예: 별도 세션 키 갱신)
                                return waitingLineRepository.refreshWaitTime(userId)
                                        .then(Mono.just(new PoolingRsp(rank, "WAITING")));
                            })
                            .switchIfEmpty(
                                    // 3. 신규 진입 시
                                    waitingLineRepository.setUserWaiting(userId, System.currentTimeMillis())
                                            .then(waitingLineRepository.getUserRank(userId))
                                            .map(rank -> new PoolingRsp(rank, "WAITING"))
                            );
                })
                .onErrorResume(e -> {
                    log.error("Pooling error for user {}: {}", userId, e.getMessage());
                    return Mono.just(new PoolingRsp(-1L, "ERROR"));
                });
    }

    @PostMapping(value = "/poolingSessinon")
    public Mono<PoolingRsp> subscribe(WebSession session) {

        session.start();
        // 1. 세션에서 식별자 추출 (세션 ID 자체를 사용하거나, 로그인된 정보가 있다면 그 값을 사용)
        String sessionId = session.getId();

        return waitingLineRepository.isUserActive(sessionId)
                .flatMap(isActive -> {
                    if (isActive) {
                        // 2. 활성 상태라면 TTL(만료시간) 갱신 후 응답
                        return waitingLineRepository.refreshActiveTime(sessionId)
                                .then(Mono.just(new PoolingRsp(0L, "COMPLETED")));
                    }

                    // 3. 활성 상태가 아니라면 대기열 순번 조회 및 갱신
                    return waitingLineRepository.getUserRank(sessionId)
                            .flatMap(rank -> {
                                // 대기열 TTL 성격의 추가 로직 수행
                                return waitingLineRepository.refreshWaitTime(sessionId)
                                        .then(Mono.just(new PoolingRsp(rank, "WAITING")));
                            })
                            .switchIfEmpty(
                                    // 4. 대기열에 없는 신규 진입 시 (처음 세션이 생성되어 접근한 경우)
                                    waitingLineRepository.setUserWaiting(sessionId, System.currentTimeMillis())
                                            .then(waitingLineRepository.getUserRank(sessionId))
                                            .map(rank -> new PoolingRsp(rank, "WAITING"))
                            );
                })
                .onErrorResume(e -> {
                    log.error("Pooling error for session {}: {}", sessionId, e.getMessage());
                    return Mono.just(new PoolingRsp(-1L, "ERROR"));
                });
    }
}
