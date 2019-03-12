package com.jpinpoint.perf.pinpointrules.concurrent;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.NotThreadSafe;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;

import static org.springframework.context.annotation.ScopedProxyMode.INTERFACES;

/**
 * Created by BorgersJM on 4-7-2017.
 */
@NotThreadSafe
public class AvoidMutableStaticFieldsTest {
    private static final String stringOk1 = "Ok";
    private static String stringViolate1;
    static String stringViolate2;
    public static Object objViolate3;
    @GuardedBy("lock")
    public static Object objGuardedOk;
    private static final Date dateViolate4 = new Date(); // Date is known mutable type
    private static Map mapViolate5;
    private static final Map mapOk = Collections.emptyMap();
    private static final Map mapViolate6 = new HashMap(); // HashMap is known mutable type
    @GuardedBy("lock")
    private static final Date dateOk = new Date(); // Date is known mutable type, needs guarding
    private static final File[] NO_FILES_OK = {};
    private static final File[] FILES_Violate7 = {new File("/tmp/a")};
    private static final String[] strArrayViolate8 = new String[2]; // array is mutable, elements can be replaced
    private static final Object[] objArrayOk = new Object[0]; // 0 elements to replace
    private static volatile String stringVolatileOk1; // volatile helps for the reference
    private static volatile Map mapViolate9 = new HashMap(); // HashMap is known mutable type, volatile only helps for the reference
    private static final String[] QUALIFIERS_Violate = {"alpha", "beta", "milestone"};
    private static final List QUALIFIERS_Ok = Collections.unmodifiableList(Arrays.asList("alpha", "beta", "milestone"));
    // In Java 9 we can do: List.of("alpha", "beta", "milestone"); // Immutable, Nice!

    public static void main(String[] args) {
        System.out.println("" + QUALIFIERS_Violate + QUALIFIERS_Ok);
    }
}
    @Component
    @Scope(value = "request", proxyMode = INTERFACES)
            // Only request is safe
class Component3Ok2 {
    static private Date date0Violate = new Date(); // static is shared among all prototype instances
    private Date date1Ok = new Date(); //
    private String string1Ok = new String(); //
    private final String string3Ok = new String(); // OK!: final, immutable
}

@Component
@Scope(value = "prototype", proxyMode = INTERFACES)
        // prototypes are recreated every time and instance fields are therefore safe, they are not shared
class Component5_2 {
    static private Date date0Violate = new Date(); // static is shared among all prototype instances
    private Date date1Ok = new Date(); //
    private String string1Ok = new String(); //
    private final String string3Ok = new String(); //
}


class NoFilesNoArrays_2 {
    private static final File[] NO_FILES_OK = {};
    private static final File[] FILES_Violate = {new File("/tmp/a")};
    private static final File file_violate = new File("dummy");
    private static final String[] names_violate = {"aap", "noot", "mies"};
}