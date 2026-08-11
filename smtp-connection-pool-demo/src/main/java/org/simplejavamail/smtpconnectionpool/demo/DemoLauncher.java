package org.simplejavamail.smtpconnectionpool.demo;

/** Runs every currently supported executable demonstration against its own dummy SMTP server. */
public final class DemoLauncher {
    private DemoLauncher() {
    }

    /** Runs path 1 first, followed by the standalone batch path and the path 3 framework variants. */
    public static void main(final String[] arguments) throws Exception {
        System.out.println(DirectPoolDemo.runReuse());
        System.out.println(DirectPoolDemo.runInvalidation());
        System.out.println(SimpleJavaMailDemo.run());
        System.out.println(BatchModuleDemo.run());
        System.out.println(JakartaMailDemo.run());
        System.out.println(SpringDemo.run());
        System.out.println(CamelDemo.run());
    }
}
