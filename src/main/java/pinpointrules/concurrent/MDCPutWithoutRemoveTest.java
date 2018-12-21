package nl.rabobank.perf.pinpointrules.concurrent;

import org.slf4j.MDC;

public class MDCPutWithoutRemoveTest {
	   private static final String USER_TYPE_KEY1 = "userTypeKey1Field";
	   private static final String USER_TYPE_KEY2 = "userTypeKey2Field";

    public void doFilter(/* request, response, chain */) {

        try {
            if (true) {
                MDC.put("levelKey1", "levelName");
                MDC.put(USER_TYPE_KEY1, "userTypeNameField");
                MDC.put("levelKey2", "levelName2");
                MDC.put("userTypeKey2", "userTypeName2");
                MDC.put(USER_TYPE_KEY2, "userTypeNameField2");
                
                if (true) {
                    MDC.put("focusIdKey1", "focusIdValue");
                    MDC.put("focusIdKey2", "focusIdValue2");
                }
            }
            //chain.doFilter(request, response);
            MDC.remove("levelKey2"); // violation, not in finally

        } finally {
            MDC.remove("focusIdKey1"); 
            MDC.remove("levelKey1"); 
            MDC.remove(USER_TYPE_KEY1); 
            //MDC.remove("focusIdKey2");  - violation
            //MDC.remove("userTypeKey2"); - violation
            //MDC.remove(USER_TYPE_KEY2); -violation
        }
        MDC.remove("levelKey2"); // violation, not in finally
    }
}
