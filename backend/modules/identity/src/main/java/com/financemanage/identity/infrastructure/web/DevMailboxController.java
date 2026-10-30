package com.financemanage.identity.infrastructure.web;

import com.financemanage.identity.application.EmailSender.EmailMessage;
import com.financemanage.identity.infrastructure.email.DevMailbox;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev profile only: the emails "sent" to an address (verification and password reset codes), newest first, so the
 * flows can be tried without an SMTP server. This controller does not exist in any other profile.
 */
@RestController
@Profile("dev")
class DevMailboxController {

    private final DevMailbox mailbox;

    DevMailboxController(DevMailbox mailbox) {
        this.mailbox = mailbox;
    }

    @GetMapping("/api/v1/dev/mailbox")
    List<EmailMessage> mailbox(@RequestParam String to) {
        return mailbox.messagesTo(to);
    }
}
