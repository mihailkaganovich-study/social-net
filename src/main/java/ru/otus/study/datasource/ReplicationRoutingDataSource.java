package ru.otus.study.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

public class ReplicationRoutingDataSource extends AbstractRoutingDataSource {

    public ReplicationRoutingDataSource(DataSource masterDataSource, DataSource slaveDataSource) {

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("master", masterDataSource);
        targetDataSources.put("slave", slaveDataSource);

        super.setTargetDataSources(targetDataSources);
        super.setDefaultTargetDataSource(masterDataSource);
        super.afterPropertiesSet();
    }

    @Override
    protected Object determineCurrentLookupKey() {
        // Для readOnly транзакций используем слейвы с балансировкой
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return "slave";
        }
        return "master";
    }
}