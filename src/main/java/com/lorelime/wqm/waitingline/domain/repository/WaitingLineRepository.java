package com.lorelime.wqm.waitingline.domain.repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface WaitingLineRepository {

    /**
     * 사용자를 대기열(ZSet)에 추가합니다.
     * + TTL 정보도 별도로 추가 저장
     */
    Mono<Boolean> setUserWaiting(String userId, double score);

    /**
     * 사용자의 현재 대기 순번을 조회합니다.
     * -> 화면에 몇번째 남았는지 표시용도
     */
    Mono<Long> getUserRank(String userId);

    /**
     * 사용자가 활성(Active) 상태인지 확인합니다.
     * 대기열시스템에서는 다음 프로세스로 넘어가도 되는지 판단
     * 본 서비스에서는 인가된 사용자인지 판단
     */
    Mono<Boolean> isUserActive(String userId);

    /**
     * 사용자를 대기열에서 삭제합니다.
     * 대기열 TTL이 만료될 동안 갱신요청이 오지 않았다면 삭제처리
     */
    Mono<Long> removeUser(String userId);

    /**
     * 대기열 갱신부분
     * 화면에서 대기열 조회 요청이 올때마다 남은 TTL을 갱신
     */
    Mono<Boolean> refreshWaitTime(String userId);

    /**
     * 활성사용자 종료를 명시적으로 하지않고 TTL로 처리하고자 한다면, (예 : 30분동안만 사용가능)
     * active사용자도 시간갱신이 필요
     */
    Mono<Boolean> refreshActiveTime(String userId);

    /**
     * 대기열 상위 N명의 사용자를 조회합니다.
     */
    Flux<String> getWaitingUsers(long start, long end);

    /**
     * 활성화된 모든 사용자의 ID를 조회합니다.
     */
    Flux<String> getActiveUsers();

    /**
     * 명시적으로 활성화된 사용자를 이탈시키는 용도 ( 예 : 송금, 예매 완료 화면에서 삭제 처리 )
     */
    public Mono<Boolean> removeActiveUser(String userId);
}
