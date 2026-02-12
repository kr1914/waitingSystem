package com.lorelime.wqm.waitingline.domain.repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface WaitingLineRepository {

    /**
     * 사용자를 대기열(ZSet)에 추가합니다.
     */
    Mono<Boolean> setUserWaiting(String userId, double score);

    /**
     * 사용자의 현재 대기 순번을 조회합니다.
     */
    Mono<Long> getUserRank(String userId);

    /**
     * 사용자가 활성(Active) 상태인지 확인합니다.
     */
    Mono<Boolean> isUserActive(String userId);

    /**
     * 사용자를 대기열에서 삭제합니다.
     */
    Mono<Long> removeUser(String userId);

    /**
     * 대기열 갱신부분
     * @param userId
     * @return
     */
    Mono<Boolean> refreshWaitTime(String userId);
    Mono<Boolean> refreshActiveTime(String userId);

    /**
     * 대기열 상위 N명의 사용자를 조회합니다.
     */
    Flux<String> getWaitingUsers(long start, long end);

    /**
     * 활성화된 모든 사용자의 ID를 조회합니다.
     */
    Flux<String> getActiveUsers();


    public Mono<Boolean> removeActiveUser(String userId);
}
