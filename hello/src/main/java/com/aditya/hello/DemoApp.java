package com.aditya.hello;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
public class DemoApp {
    
    @RequestMapping("/")
    public String home() {
        return "Welcome";
    }
    
}
