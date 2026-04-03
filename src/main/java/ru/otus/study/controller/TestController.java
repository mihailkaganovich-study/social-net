package ru.otus.study.controller;

import ru.otus.study.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @Autowired
    private TestService testService;

    @PostMapping("/save/{id}")
    public int saveAndGetCount(@PathVariable Long id) {
        return testService.saveAndGetCount(id);
    }
}
