package com.lasso.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PageController {

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public org.springframework.core.io.Resource index() {
        return new org.springframework.core.io.ClassPathResource("static/index.html");
    }
}
