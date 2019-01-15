package pinpointrules.concurrent;

import com.google.common.annotations.VisibleForTesting;
import lombok.*;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.NotThreadSafe;
import org.slf4j.Logger;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.*;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static lombok.AccessLevel.PRIVATE;
import static org.springframework.context.annotation.ScopedProxyMode.INTERFACES;
import static org.springframework.context.annotation.ScopedProxyMode.TARGET_CLASS;
import static org.springframework.web.context.WebApplicationContext.SCOPE_APPLICATION;

/**
 * Created by BorgersJM on 2-3-2017.
 */
public class AvoidUnguardedMutableFieldsInSharedObjectsTest {

}

@Component
class Component1 {
    static private Date date0Violate = new Date(); // violation: non-final, mutable, unguarded ; static or not doesn't make difference
    private Date date1Violate = new Date(); // violation: non-final, mutable, unguarded
    private final Date date2Violate = new Date(); // violation: mutable, unguarded
    private final List list1Violate = new ArrayList(); // violation: mutable, unguarded
    private final List list2Ok; // possibly OK
    private final List list3Ok = Collections.unmodifiableList(Arrays.asList("1", "2")); // OK
    private Map<String, String> map1Violate = new HashMap<>(); // violation: mutable, unguarded
    private String string1Violate = new String(); // violation: non-final, unguarded
    private String string2Violate; // violation: non-final, unguarded
    private final String string3OK = new String(); // OK!: final, immutable
    private volatile String string4OK; // OK: reference is guarded by volatile, immutable
    private volatile Date date3Violate; // violation: mutable
    private volatile Map<String, String> map2Violate = new HashMap<>(); // violation: mutable, unguarded value

    @Autowired
    private String string5OK; // immutable, should only be assigned by wiring, no assignments to it, then OK

    @Value("${maxElementsInMemory}")
    private String valueOK;

    Component1() {
        list2Ok = Collections.unmodifiableList(Arrays.asList("1", "2"));
    }
}

// the same for @Service
@Service
class Service1 {
    static private Date date0Violate = new Date(); // violation: non-final, mutable, unguarded ; static or not doesn't make difference
    private Date date1Violate = new Date(); // violation: non-final, mutable, unguarded
    private String string1Violate = new String(); // violation: non-final, unguarded
    private final String string3OK = new String(); // OK!: final, immutable

    @Value("${batchService.http.url}")
    private String url; // additional unguarded accessor method, next line

    public void setUrlViolate(final String url) {
        this.url = url;
    } // violation: unguarded accessor method
}

// the same for @Singleton, yet only in case ConcurrencyManagementType.BEAN
@Singleton
class Singleton1 {
    static private Date date0Ok = new Date(); // dealt with by @Singleton default: @ConcurrencyManagementType.CONTAINER
    private Date date1Ok = new Date(); // same
    private String stringOk = new String(); // same
    private final String string3Ok = new String(); // OK!: final, immutable
}

@Component
@Scope(value = SCOPE_APPLICATION, proxyMode = TARGET_CLASS)
class Component2 {
    static private Date date0Violate = new Date(); // violation: non-final, mutable, unguarded ; static or not doesn't make difference
    private Date date1Violate = new Date(); // violation: non-final, mutable, unguarded
    private String string1Violate = new String(); // violation: non-final, unguarded
    private final String string3Ok = new String(); // OK!: final, immutable
}

@Component
@Scope(value = "request", proxyMode = INTERFACES)
        // Only request is safe
class Component3Ok {
    static private Date date0Violate = new Date(); // static is shared among all prototype instances
    private Date date1Ok = new Date(); //
    private String string1Ok = new String(); //
    private final String string3Ok = new String(); // OK!: final, immutable
}

@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = INTERFACES)
        // sessions need to be thread safe, too; they are shared among threads
class Component4 {
    static private Date date0Violate = new Date(); // violation: non-final, mutable, unguarded ; static or not doesn't make difference
    private Date date1Violate = new Date(); // violation: non-final, mutable, unguarded
    private String string1Violate = new String(); // violation: non-final, unguarded
    private final String string3Ok = new String(); // OK!: final, immutable
}

@Component
@Scope(value = "prototype", proxyMode = INTERFACES)
        // prototypes are recreated every time and instance fields are therefore safe, they are not shared
class Component5 {
    static private Date date0Violate = new Date(); // static is shared among all prototype instances
    private Date date1Ok = new Date(); //
    private String string1Ok = new String(); //
    private final String string3Ok = new String(); //
}

@Component
class ComponentMoValidation {
    private String string1Violate = new String(); // violation: non-final, unguarded
    private final String string3Ok = new String(); // OK!: final, immutable
    private RestTemplate restTemplateOk; // OK, autowired setter

    @Autowired
    public void setRestTemplate(@Qualifier("beheerBatchRestTemplate") final RestTemplate restTemplate) {
        this.restTemplateOk = restTemplate;
    }

    private RestTemplate restTemplate; // also assigned outside autowired setter

    @Autowired
    public void setRestTemplateViolate(@Qualifier("beheerBatchRestTemplate") final RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void doRestTemplateViolate() {
        restTemplate = null;
    }

    @Data
    private static class InnerDataOk { // static, so not accessible from outer instance, so OK
        private Long Id;
    }

    @Data
    private class InnerDataViolate { // not static, so accessible from outer instance,
        private Long Id; // violation: non-final, unguarded
        private String string1Violate = new String(); // violation: non-final, unguarded
        private final String string3Ok = new String(); // OK!: final, immutable
    }

    private FeatureTogglesMessageSource featuresViolate; // autowired constructor, yet non-final --> can be lower severity since not a bug, only non-defensive

    @Autowired
    public ComponentMoValidation(FeatureTogglesMessageSource featureTogglesMessageSource) {
        this.featuresViolate = featureTogglesMessageSource;
    }

    class FeatureTogglesMessageSource {
    }

    private static final int KILO = 1024;

    @Value("${fileStorageService.http.output.buffer.size}")
    private int outputBufferSizeInBytes;

    private int outputBufferSizeOk;

    @PostConstruct
    public void afterPropertiesSet() {
        outputBufferSizeOk = outputBufferSizeInBytes * KILO;
    }

    @Value("${cm.getordersummary.url}")
    private String getOrderSummaryUrlOK;

    @VisibleForTesting
    void setGetOrderSummaryUrl(String getOrderSummaryUrl) {
        this.getOrderSummaryUrlOK = getOrderSummaryUrl;
    }

    @PersistenceContext
    private EntityManager entityManagerOk;

    private StepExecution stepExecutionOk;

    @BeforeStep
    public void setStepExecution(final StepExecution stepExecution) {
        this.stepExecutionOk = stepExecution;
    }
}

// the same for @Singleton, yet only in case ConcurrencyManagementType.BEAN
@Singleton
@ConcurrencyManagement(value = ConcurrencyManagementType.BEAN)
class SingletonConcurrencyBean {
    static private Date date0Violate = new Date(); // violation: non-final, mutable, unguarded ; static or not doesn't make difference
    private Date date1Violate = new Date(); // violation: non-final, mutable, unguarded
    private final Object Lock = new Object();
    @GuardedBy("LOCK")
    private String string1Ok = new String(); // OK!: guarded
    @GuardedBy("LOCK")
    private final List list1OK = new ArrayList(); // OK, guarded
    @GuardedBy("LOCK")
    private Map<String, String> map1Ok = new HashMap<>(); // OK, guarded

    private final String string3Ok = new String(); // OK!: final, immutable
}

// From RM
@Singleton
@Startup
@ConcurrencyManagement(value = ConcurrencyManagementType.BEAN)
class ABbEventSender {

    @EJB
    private Object bbEventServiceOk;

    @Resource
    private SessionContext sessionContextOk;

    @Inject
    private Object bbEventProducerOk;

    @Inject
    private Logger loggerOk;

    // Boolean to indicate if the scheduled process is currently running.
    private AtomicBoolean processingBbEventViolation = new AtomicBoolean(false); // needs to be final

    private int databaseQueryMaxResultsViolation = 1000; // needs to be final
}

@Singleton
//@Startup
@LocalBean
@ConcurrencyManagement(value = ConcurrencyManagementType.BEAN)
class ToggleRequestOutSender {
        private int databaseQueryMaxResultsOk;
        private int databaseQueryMaxResultsViolation;

        @PostConstruct
        private void postConstruct() {
            try {
                databaseQueryMaxResultsOk = Integer.parseInt("1000");
            } catch (NullPointerException | NumberFormatException e) {
                databaseQueryMaxResultsOk = 5000;
            }
        }
        private void setMax() {
            try {
                databaseQueryMaxResultsViolation = 5000;
            } catch (RuntimeException e) {}
        }
}

@Service("InitializeRCDCTaskletViolate")
abstract class InitializeTaskletViolate implements Tasklet, InitializingBean {
    // Work directory for this instance only.
    protected String workDirectoryViolate;
    public void setWorkDirectory(final String workDirectory) {
        this.workDirectoryViolate = workDirectory;
    }

}

@Service("InitializeRCDCTaskletOk")
@NotThreadSafe
abstract class InitializeTaskletOk implements Tasklet, InitializingBean {
    // Work directory for this instance only.
    protected String workDirectoryOk;
    public void setWorkDirectory(final String workDirectory) {
        this.workDirectoryOk = workDirectory;
    }
}

@Component
@Getter
@Setter //violation
@Builder
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@ConfigurationProperties(prefix = "fx.co.taskExecutor")
//@NotThreadSafe // if ommitted, it is a violation
final class TaskExecutorPropertiesViolate {
    private int corePoolSize;
    private int maxPoolSize;
    private int queueCapacity;
}

@Component
class NoFiles {
    private static final File[] NO_FILES_OK = {};
    private static final File[] FILES_Violate = {new File("/tmp/a")};
}

@Component
@Getter // No @Setter, so OK
@EqualsAndHashCode
@NoArgsConstructor
@ConfigurationProperties(prefix = "fp.co.togglz")
@SuppressWarnings({ "PMD.UnusedPrivateField", "PMD.SingularField" })
final class TogglePropertiesOk   {
    private boolean misInFocusEnabled = true;
    private boolean basisPeanutFocusEnabled = true;

    public boolean isExterneJellyEnabled() {
        return false;
    }
}

@Component
class SparkleproductImportListener {
    private volatile long startTime;

    @Autowired
    public SparkleproductImportListener(final Object sparkleproductRepository) {
    }

    public void beforeJob(final Object jobExecution) {
        startTime = System.currentTimeMillis();
    }
}

@Service
class CakeSpaceService {
    private int verschilIngangsdatumOk;

    @Value("${cake.situatie.aanvang.sprinkleperiode.verschil}")
    public void setVerschilIngangsdatum(final int verschilIngangsdatum) {
        this.verschilIngangsdatumOk = verschilIngangsdatum;
    }
}

// @Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
// @Scope(value = "session", proxyMode = ScopedProxyMode.INTERFACES)
//@Scope(value = "globalSession", proxyMode = ScopedProxyMode.INTERFACES)

/*
    String SCOPE_REQUEST = "request";
    String SCOPE_SESSION = "session";
    String SCOPE_GLOBAL_SESSION = "globalSession";
    String SCOPE_APPLICATION = "application";
    String SERVLET_CONTEXT_BEAN_NAME = "servletContext";
    String CONTEXT_PARAMETERS_BEAN_NAME = "contextParameters";
    String CONTEXT_ATTRIBUTES_BEAN_NAME = "contextAttributes";
*/