package com.financemanage.identity.infrastructure.email;

import com.financemanage.identity.application.EmailSender;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Dev/test only: keeps the latest emails in memory (and logs them) instead of sending them. */
public class DevMailbox implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(DevMailbox.class);
    private static final int CAPACITY = 200;

    private final Deque<EmailMessage> messages = new ArrayDeque<>();

    @Override
    public synchronized void send(EmailMessage message) {
        log.info("[dev mailbox] to {}: {}\n{}", message.to(), message.subject(), message.body());
        messages.addFirst(message);
        if (messages.size() > CAPACITY) {
            messages.removeLast();
        }
    }

    /** Newest first. */
    public synchronized List<EmailMessage> messagesTo(String email) {
        return messages.stream().filter(m -> m.to().equalsIgnoreCase(email)).toList();
    }
}
