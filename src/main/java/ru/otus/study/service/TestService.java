package ru.otus.study.service;


import org.springframework.transaction.annotation.Transactional;
import ru.otus.study.repository.TestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TestService {

    @Autowired
    private TestRepository testRepository;

    @Transactional
    public int saveAndGetCount(Long id) {
        return testRepository.saveAndGetCount(id);
    }
}
