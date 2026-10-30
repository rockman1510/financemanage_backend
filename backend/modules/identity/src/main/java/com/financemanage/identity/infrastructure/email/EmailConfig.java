package com.financemanage.identity.infrastructure.email;

import com.financemanage.identity.application.EmailSender;
import com.financemanage.identity.application.IdentityProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Picks the email adapter, in this order: the in-memory {@link DevMailbox} for the dev and test profiles, SMTP when
 * {@code spring.mail.host} is set, otherwise a logger that sends nothing.
 */
@Configuration(proxyBeanMethods = false)
class EmailConfig {

    @Bean
    @Profile({"dev", "test"})
    DevMailbox devMailbox() {
        return new DevMailbox();
    }

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    @ConditionalOnProperty("spring.mail.host")
    SmtpEmailSender smtpEmailSender(ObjectProvider<JavaMailSender> mailSender, IdentityProperties properties) {
        return new SmtpEmailSender(mailSender.getObject(), properties.mail().from());
    }

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    LoggingEmailSender loggingEmailSender() {
        return new LoggingEmailSender();
    }
}
