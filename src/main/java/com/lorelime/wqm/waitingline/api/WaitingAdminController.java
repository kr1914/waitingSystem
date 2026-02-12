package com.lorelime.wqm.waitingline.api;


import com.lorelime.wqm.waitingline.domain.repository.WaitingLineRepository;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;


@RestController
@RequestMapping("/api/v2/queue/admin")
public class WaitingAdminController {

    private final WaitingLineRepository waitingLineRepository;

    public WaitingAdminController(WaitingLineRepository waitingLineRepository) {
        this.waitingLineRepository = waitingLineRepository;
    }

    /**
     * Flux를 List로 모아서(collectList) 하나의 JSON 배열로 반환합니다.
     */
    @GetMapping("/waiting-list")
    public Mono<List<String>> getWaitingList() {
        return waitingLineRepository.getWaitingUsers(0, 49)
                .collectList(); // Flux<String> -> Mono<List<String>>
    }

    @GetMapping("/active-list")
    public Mono<List<String>> getActiveList() {
        return waitingLineRepository.getActiveUsers()
                .collectList(); // Flux<String> -> Mono<List<String>>
    }

    /**
     * 특정 사용자를 대기열 또는 활성 상태에서 강제 삭제
     */
    @DeleteMapping("/remove")
    public Mono<Void> removeUser(@RequestParam String userId, @RequestParam String type) {
        if ("WAITING".equals(type)) {
            return waitingLineRepository.removeUser(userId).then();
        } else {
            // 활성 사용자는 Repository의 isUserActive 로직에서 사용하는 키 구조와 맞춰야 합니다.
            // 여기서는 단순히 삭제 처리를 수행합니다.
            return waitingLineRepository.removeActiveUser(userId).then();
        }
    }
}