package pinpointrules.concurrent;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.NotThreadSafe;

import java.io.File;
import java.util.*;

/**
 * Created by BorgersJM on 4-7-2017.
 */
@NotThreadSafe
public class AvoidMutableStaticFieldTest {
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