package pinpointrules.concurrent;

import com.google.common.annotations.VisibleForTesting;
import lombok.Data;
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
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

//import javax.inject.Inject;
import static org.springframework.context.annotation.ScopedProxyMode.INTERFACES;
import static org.springframework.context.annotation.ScopedProxyMode.TARGET_CLASS;
import static org.springframework.web.context.WebApplicationContext.SCOPE_APPLICATION;


/**
 * Created by BorgersJM on 25-4-2017.
 */
public class AvoidUnguardedAssignmentToNonFinalFieldsInSharedObjectsTest {

}

// the same for @Service
@Service
class AService1 {
    static private Date date0OK = new Date(); // rule not applicable
    private Date date1OK = new Date();
    private String string1OK = new String();
    private final String string3OK = new String();

    @Value("${batchService.http.url}")
    private String url; // violation on next line, of additional unguarded accessor method

    public void setUrlViolate(final String url) {
        this.url = url;
    } // violation: unguarded accessor method
}

// the same for @Singleton, yet only in case ConcurrencyManagementType.BEAN
@Singleton
class ASingletonContainer {
    static private Date date0Ok = new Date(); // rule not applicable
    private Date date1Ok = new Date(); // same
    private String stringOk = new String(); // same
    private final String string3Ok = new String(); // same

    private String url; //

    public void setUrlOK(final String url) {
        this.url = url;
    } // OK: container manages thread-safety

}

// the same for @Singleton, yet only in case ConcurrencyManagementType.BEAN
@Singleton
@ConcurrencyManagement(value = ConcurrencyManagementType.BEAN)
class ASingletonBean {
    static private Date date0Ok = new Date(); // rule not applicable
    private Date date1Ok = new Date(); // same
    private String stringOk = new String(); // same
    private final String string3Ok = new String(); //

    private String url;

    public void setUrlViolate(final String url) {
        this.url = url;
    } // violation: unguarded accessor method
}

@Component
@Scope(value = SCOPE_APPLICATION, proxyMode = TARGET_CLASS)
class AComponent2 {
    private String _url;
    public void setUrlViolate(final String url) {
        _url = url;
    } // violation: unguarded accessor method
}

@Component
@Scope(value = "request", proxyMode = INTERFACES)
        // Only request is safe
class AComponent3Ok {
    private String url; //
    public void setUrlOk(final String url) {
        this.url = url;
    } // no violation: request scope
}
@Component
class AnotherComponent {
    private String url;
    private volatile String volatileOk;
    private String wiredOk;

    public void setUrlViolate(String url) {
        url += "/";
        this.url = url;
    } // violation: unguarded accessor method

    public void setVolatileOk(String vol) {
        volatileOk = vol;
    }

    @Autowired
    public void setAutowiredOk(String wire) {
        wiredOk = wire;
    }
}

@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION, proxyMode = INTERFACES)
        // sessions need to be thread safe, too; they are shared among threads
class ASessionScopedComponent {
    private String _url;
    public void setUrlViolate(final String url) {
        _url = url;
    } // violation: unguarded accessor method
}

@Component
@Scope(value = "prototype", proxyMode = INTERFACES)
        // prototypes are recreated every time and instance fields are therefore safe, they are not shared
class AComponent5 {
    private String url; //
    public void setUrlOk(final String url) {
        this.url = url;
    } // no violation: prototype scope
}

// the same for @Singleton, yet only in case ConcurrencyManagementType.BEAN
@Singleton
@ConcurrencyManagement(value = ConcurrencyManagementType.BEAN)
class ASyncedSingletonConcurrencyBean {
    private final Object LOCK = new Object();
    @GuardedBy("LOCK")
    private String string1Ok = new String(); // OK!: guarded
    @GuardedBy("LOCK")
    private final List list1OK = new ArrayList(); // OK, guarded
    @GuardedBy("LOCK")
    private Map<String, String> map1Ok = new HashMap<>(); // OK, guarded
    @Autowired
    private String url;

    public void setString1Ok(String string1) {
        synchronized(LOCK) { // Findbugs should be able to check
            string1Ok = string1;
        }
    }

    @VisibleForTesting
    void setUrlForTestingOnlyOk(String urlForTestingOnly) {
        this.url = urlForTestingOnly;
    }

    @VisibleForTesting
    public void setUrlForTestingOnlyViolate(String urlForTestingOnly) {
        this.url = urlForTestingOnly;
    }
}



@Component
class AComponentMaoValidation {
    private String string1Violate = new String(); // violation: non-final, unguarded
    private final String string3Ok = new String(); // OK!: final, immutable
    private RestTemplate restTemplateOk; // OK, autowired setter

    @Autowired
    public void setRestTemplate(@Qualifier("workLongRestTemplate") final RestTemplate restTemplate) {
        this.restTemplateOk = restTemplate;
    }

    private RestTemplate restTemplateViolate; // Violation because also assigned outside autowired setter

    @Autowired
    public void setRestTemplateViolate(@Qualifier("workLongRestTemplate") final RestTemplate restTemplate) {
        this.restTemplateViolate = restTemplate;
    }

    public void doRestTemplateViolate() {
        restTemplateViolate = null;
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

    private FeatureTogglesMessageSource featuresViolate; // autowired constructor, yet non-final

    @Autowired
    public AComponentMaoValidation(FeatureTogglesMessageSource featureTogglesMessageSource) {
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

    @Value("${rmc.getfiddlesummary.url}")
    private String getFiddleSummaryUrlOK;

    @VisibleForTesting
    void setGetFiddleSummaryUrl(String getFiddleSummaryUrl) {
        this.getFiddleSummaryUrlOK = getFiddleSummaryUrl;
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
class ASingletonConcurrencyBean {
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

// From RO
@Singleton
@Startup
@ConcurrencyManagement(value = ConcurrencyManagementType.BEAN)
class BbEventSender {

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
class APayRequestOutSender {
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
abstract class AInitializeTaskletViolate implements Tasklet, InitializingBean {
    // Work directory for this instance only.
    protected String workDirectoryViolate;
    public void setWorkDirectory(final String workDirectory) {
        this.workDirectoryViolate = workDirectory;
    }

}

@Service("InitializeRCDCTaskletOk")
@NotThreadSafe
abstract class AInitializeTaskletOk implements Tasklet, InitializingBean {
    // Work directory for this instance only.
    protected String workDirectoryOk;
    public void setWorkDirectory(final String workDirectory) {
        this.workDirectoryOk = workDirectory;
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

/* -WIP-XPATH-Rule-definition

Experimental/to test:

Pieces:
//TypeDeclaration/Annotation//Name[(@Image='Component' or @Image='Service' or @Image='Controller' or @Image='Repository' or
(@Image='Singleton' and ancestor::TypeDeclaration/Annotation/NormalAnnotation/MemberValuePairs//Name[@Image='ConcurrencyManagementType.BEAN']))
and not (ancestor::TypeDeclaration/Annotation/NormalAnnotation/Name[@Image='Scope']/..//Literal[@Image='"request"' or @Image='"prototype"'])
and not (ancestor::TypeDeclaration/Annotation/MarkerAnnotation/Name[@Image='NotThreadSafe'])]

/../../..//ClassOrInterfaceDeclaration[@Static='false']/ClassOrInterfaceBody/ClassOrInterfaceBodyDeclaration/MethodDeclaration/Block/BlockStatement/Statement//StatementExpression/AssignmentOperator/../PrimaryExpression//@Image=
./VariableDeclarator/VariableDeclaratorId/@Image)

FieldDeclaration[@Final='false' and @Volatile='false'
and not (../../ClassOrInterfaceBodyDeclaration/Annotation//Name[@Image='Autowired' or @Image='PostConstruct' or @Image='BeforeStep']

/../../../
or (../../ClassOrInterfaceBodyDeclaration[count (./Annotation//Name[@Image='Autowired' or @Image='PostConstruct' or @Image='VisibleForTesting' or @Image='BeforeStep'])=0]
/MethodDeclaration/Block/BlockStatement/Statement//StatementExpression/AssignmentOperator/../PrimaryExpression//@Image=
./VariableDeclarator/VariableDeclaratorId/@Image)
]

Working for first cases:
//TypeDeclaration/Annotation//Name[(@Image='Component' or @Image='Service' or @Image='Controller' or @Image='Repository' or
(@Image='Singleton' and ancestor::TypeDeclaration/Annotation/NormalAnnotation/MemberValuePairs//Name[@Image='ConcurrencyManagementType.BEAN']))
and not (ancestor::TypeDeclaration/Annotation/NormalAnnotation/Name[@Image='Scope']/..//Literal[@Image='"request"' or @Image='"prototype"'])
and not (ancestor::TypeDeclaration/Annotation/MarkerAnnotation/Name[@Image='NotThreadSafe'])]

/../../..//ClassOrInterfaceDeclaration[@Static='false']/ClassOrInterfaceBody/ClassOrInterfaceBodyDeclaration/MethodDeclaration/Block/BlockStatement/Statement//StatementExpression/AssignmentOperator/../PrimaryExpression//*[@Image=
ancestor::ClassOrInterfaceBody/ClassOrInterfaceBodyDeclaration/FieldDeclaration[@Final='false' and @Volatile='false']/VariableDeclarator/VariableDeclaratorId/@Image]


Thought: what about getter access? Also wrong. Yet, if not assigned-to, no problem? Maybe for non-primitives, non-atomics, how to check? return a List or HashMap? Known mutable field types?

*/