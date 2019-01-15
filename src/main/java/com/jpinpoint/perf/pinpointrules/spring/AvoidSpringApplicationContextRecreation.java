package com.jpinpoint.perf.pinpointrules.spring;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class AvoidSpringApplicationContextRecreation {

    private static final ApplicationContext APPLICATION_CONTEXT2;
    private static final ApplicationContext APPLICATION_CONTEXT =
            new ClassPathXmlApplicationContext(new String[]{ "t-spring-context.xml" }); //OK

    static {
        APPLICATION_CONTEXT2 = new ClassPathXmlApplicationContext(new String[]{ "t-spring-context.xml" }); // OK
    }

    /** Hotspot in MB-signing, does jaxbcontext re-creation, classloading, unzipping.
     *
     * @return
     */
    private Object getRServiceViolate() {
        final ApplicationContext applicationContext =
                new ClassPathXmlApplicationContext(new String[]{"t-spring-context.xml"}); // violation

        final ApplicationContext applicationContext2 =
                new AnnotationConfigApplicationContext(new String[]{"nl.bank.perf"}); // violation

        return (Object) applicationContext.getBean("T_SERVICE");
    }


    private Object getRServiceOk() {
        return (Object) APPLICATION_CONTEXT.getBean("T_SERVICE");
    }
}
