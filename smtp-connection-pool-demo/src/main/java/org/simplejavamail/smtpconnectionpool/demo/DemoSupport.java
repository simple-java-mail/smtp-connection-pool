package org.simplejavamail.smtpconnectionpool.demo;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.email.Recipient;
import org.simplejavamail.email.EmailBuilder;

import java.util.Properties;

final class DemoSupport {
    static final int MESSAGE_COUNT = 3;
    static final String SENDER = "sender@example.test";
    static final String RECIPIENT = "recipient@example.test";

    private DemoSupport() {
    }

    static Properties smtpProperties(final int port) {
        final Properties properties = new Properties();
        properties.setProperty("mail.transport.protocol", "smtp");
        properties.setProperty("mail.smtp.host", CountingSmtpServer.HOST);
        properties.setProperty("mail.smtp.port", Integer.toString(port));
        properties.setProperty("mail.smtp.auth", "false");
        properties.setProperty("mail.smtp.connectiontimeout", "5000");
        properties.setProperty("mail.smtp.timeout", "5000");
        properties.setProperty("mail.smtp.writetimeout", "5000");
        properties.setProperty("mail.smtp.quitwait", "false");
        return properties;
    }

    static Session newSession(final int port) {
        return Session.getInstance(smtpProperties(port));
    }

    static MimeMessage newMessage(final Session session, final String scenario, final int number) throws Exception {
        final MimeMessage message = new MimeMessage(session);
        populateMessage(message, scenario, number);
        return message;
    }

    static void populateMessage(final MimeMessage message, final String scenario, final int number) throws Exception {
        message.setFrom(new InternetAddress(SENDER, "Demo sender"));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(RECIPIENT, "Demo recipient"));
        message.setSubject(scenario + " message " + number);
        message.setText("Connection pooling demo message " + number);
    }

    static Email newSimpleJavaMailEmail(final int number) {
        return EmailBuilder.startingBlank()
                .from("Demo sender", SENDER)
                .withRecipients(new Recipient("Demo recipient", RECIPIENT, Message.RecipientType.TO, null))
                .withSubject("Simple Java Mail message " + number)
                .withPlainText("Connection pooling demo message " + number)
                .buildEmail();
    }
}
