package com.lorelime.wqm.waitingline.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller // HTML 뷰를 반환하기 위해 Controller 사용
public class ViewController {

    @GetMapping("/test")
    public String testPage() {
        // "test"를 반환하면 Thymeleaf가 templates/test.html을 찾습니다.
        return "test";
    }

    @GetMapping("/poolingTest")
    public String poolingTest() {
        // "test"를 반환하면 Thymeleaf가 templates/test.html을 찾습니다.
        return "pooling_test";
    }

    @GetMapping("/monitor")
    public String monitor() {
        // "test"를 반환하면 Thymeleaf가 templates/test.html을 찾습니다.
        return "monitor";
    }
}