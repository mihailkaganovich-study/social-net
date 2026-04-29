package ru.otus.study.repository.tarantool;

import io.tarantool.driver.api.TarantoolClient;
import io.tarantool.driver.api.TarantoolClientFactory;
import io.tarantool.driver.api.TarantoolResult;
import io.tarantool.driver.api.tuple.TarantoolTuple;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Slf4j
@Repository
public class TarantoolConnectionRepository {

    private final TarantoolClient<TarantoolTuple, TarantoolResult<TarantoolTuple>> client;

    public TarantoolConnectionRepository(
            @Value("${tarantool.host:localhost}") String host,
            @Value("${tarantool.port:3301}") int port,
            @Value("${tarantool.username:}") String username,
            @Value("${tarantool.password:}") String password
    ) {
        var builder = TarantoolClientFactory.createClient()
                .withAddress(host, port);

        if (username != null && !username.isBlank()
                && password != null && !password.isBlank()) {
            builder = builder.withCredentials(username, password);
        }

        this.client = builder.build();
        log.info("Connected Tarantool client to {}:{}", host, port);
    }

    public List<?> callUdf(String functionName, Object... args) {
        try {
            return client.call(functionName, args).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while calling Tarantool UDF: " + functionName, e);
        } catch (ExecutionException e) {
            throw new RuntimeException(
                    "Failed to call Tarantool UDF: " + functionName + ", error=" + e.getCause(),
                    e.getCause()
            );
        }
    }

    @PreDestroy
    public void close() {
        try {
            client.close();
        } catch (Exception e) {
            log.warn("Failed to close Tarantool client", e);
        }
    }
}

