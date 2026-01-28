package com.lorelime.wqm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling // 스케줄러 기능 활성화
@SpringBootApplication
public class WqmApplication {

    public static void main(String[] args) {
        SpringApplication.run(WqmApplication.class, args);
    }

}
