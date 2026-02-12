package com.lorelime.wqm.waitingline.api;


import com.lorelime.wqm.waitingline.api.dto.PoolingRsp;
import com.lorelime.wqm.waitingline.domain.repository.WaitingLineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.web.bind.annotation.*;
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
}
