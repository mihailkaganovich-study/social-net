package ru.otus.study.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import ru.otus.study.datasource.ReplicationRoutingDataSource;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
public class ReplicationDataSourceConfig {

    @Value("${spring.datasource.url:jdbc:postgresql://pg-master:5432/study_db}")
    private String masterUrl;

    @Value("${spring.datasource.username:study_user}")
    private String masterUsername;

    @Value("${spring.datasource.password:study_password}")
    private String masterPassword;

    @Value("${spring.datasource.secondary.url:jdbc:postgresql://pg-slave1:5432,pg-slave2:5432/study_db?loadBalanceHosts=true&hostRecheckSeconds=10}")
    private String slaveUrl;

    @Value("${spring.datasource.secondary.username:study_user}")
    private String slaveUsername;

    @Value("${spring.datasource.secondary.password:study_password}")
    private String slavePassword;

    @Primary
    @Bean(name = "dataSource")
    public DataSource dataSource() {
        // Мастер для записи
        HikariConfig masterConfig = new HikariConfig();
        masterConfig.setJdbcUrl(masterUrl);
        masterConfig.setUsername(masterUsername);
        masterConfig.setPassword(masterPassword);
        masterConfig.setMaximumPoolSize(10);
        masterConfig.setPoolName("MasterPool");
        masterConfig.setConnectionTimeout(30000);
        masterConfig.setIdleTimeout(600000);
        masterConfig.setMaxLifetime(1800000);

        // Слейвы для чтения с балансировкой
        HikariConfig slaveConfig = new HikariConfig();
        slaveConfig.setJdbcUrl(slaveUrl);
        slaveConfig.setUsername(slaveUsername);
        slaveConfig.setPassword(slavePassword);
        slaveConfig.setMaximumPoolSize(20);
        slaveConfig.setPoolName("SlavePool");
        slaveConfig.setReadOnly(true);
        slaveConfig.setConnectionTimeout(30000);
        slaveConfig.setIdleTimeout(600000);
        slaveConfig.setMaxLifetime(1800000);
        slaveConfig.addDataSourceProperty("loadBalanceHosts", "true");
        slaveConfig.addDataSourceProperty("hostRecheckSeconds", "10");

        // Создаем routing datasource
        return new ReplicationRoutingDataSource(
                new HikariDataSource(masterConfig),
                new HikariDataSource(slaveConfig)
        );
    }

    @Bean(name = "jdbcTemplate")
    public JdbcTemplate jdbcTemplate(@Qualifier("dataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Primary
    @Bean(name = "transactionManager")
    public PlatformTransactionManager transactionManager(@Qualifier("dataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}