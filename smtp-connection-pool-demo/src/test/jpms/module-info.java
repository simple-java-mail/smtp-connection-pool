module org.simplejavamail.smtpconnectionpool.jpms.consumer {
	requires jakarta.mail;
	requires org.bbottema.genericobjectpool;
	requires org.bbottema.clusteredobjectpool;
	requires org.simplejavamail.smtpconnectionpool;
	requires org.simplejavamail.smtpconnectionpool.jakarta;
	requires org.simplejavamail.smtpconnectionpool.camel;
	requires org.simplejavamail.batch;
}
