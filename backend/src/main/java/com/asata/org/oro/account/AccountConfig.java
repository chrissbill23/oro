package com.asata.org.oro.account;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties(AccountProperties.class)
public class AccountConfig {

    /**
     * the passwords of Asata accounts, as Umoja and Mokili read them ({bcrypt}...: DelegatingPasswordEncoder): the
     * same account signs in everywhere with the same password
     */
    @Bean
    PasswordEncoder accountPasswordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
