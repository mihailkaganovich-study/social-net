package ru.otus.study.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;
import ru.otus.study.web.RequestIdConstants;
import ru.otus.study.web.RequestIdPropagation;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class DialogsClientConfig {

    @Bean("dialogsRestTemplate")
    public RestTemplate dialogsRestTemplate(@Value("${dialogs.service.base-url}") String baseUrl) {
        RestTemplate restTemplate = new RestTemplateBuilder()
                .rootUri(baseUrl)
                .build();

        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>(restTemplate.getInterceptors());
        interceptors.add(requestIdInterceptor());
        restTemplate.setInterceptors(interceptors);
        return restTemplate;
    }

    private static ClientHttpRequestInterceptor requestIdInterceptor() {
        return (request, body, execution) -> {
            String requestId = RequestIdPropagation.current();
            if (requestId != null) {
                request.getHeaders().set(RequestIdConstants.HEADER, requestId);
            }
            return execution.execute(request, body);
        };
    }
}
