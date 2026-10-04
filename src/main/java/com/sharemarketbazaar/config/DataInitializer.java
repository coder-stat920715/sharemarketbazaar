package com.sharemarketbazaar.config;

import com.sharemarketbazaar.model.Account;
import com.sharemarketbazaar.repository.AccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/** Seeds a demo trading account so the order API can be exercised immediately. */
@Slf4j
@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedDemoAccount(AccountRepository accounts) {
        return args -> {
            if (accounts.findByUserId("demo-user").isEmpty()) {
                accounts.save(Account.builder()
                        .userId("demo-user")
                        .balance(new BigDecimal("1000000.00"))
                        .build());
                log.info("Seeded demo account 'demo-user' with balance 1,000,000.00");
            }
        };
    }
}
