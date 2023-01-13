package org.simplejavamail.smtpconnectionpool;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@AllArgsConstructor
@Getter
public class SessionTransport {
    @NotNull private final jakarta.mail.Session session;
    @NotNull private final jakarta.mail.Transport transport;
}