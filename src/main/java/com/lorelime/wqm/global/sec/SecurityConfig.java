package com.lorelime.wqm.global.sec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;


@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable) // 테스트를 위해 CSRF 비활성화
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/test").permitAll()
                        .pathMatchers("/poolingTest").permitAll()
                        .pathMatchers("/monitor").permitAll()
                        .pathMatchers("/api/*/queue/**", "/").permitAll() // 대기열 API 허용
                        .anyExchange().authenticated()
                )
                .build();
    }
}
