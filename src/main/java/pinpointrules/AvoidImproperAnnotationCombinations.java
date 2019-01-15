package com.jpinpoint.perf.pinpointrules;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.*;

@Controller
@Repository
public class AvoidImproperAnnotationCombinations {

    // Spring: Don't combine any of: @Component, @Service, @Configuration, @Repository, @Controller
    @Component
    @Configuration
    class StringAnnotationsViolation1 {
    }

    @Component
    class StringAnnotationsOk1 {
    }

    @Configuration
    class StringAnnotationsOk2 {
    }

    @Component
    @Service
    @Repository
    class StringAnnotationsViolation2 {
    }

    // JPA with Spring: Don't combine @Entity with one of: @Component, @Service, @Configuration, @Repository, @Controller
    @Entity
    @Component
    class JPASpringAnnotationsViolation1 {
    }

    @Entity
    class JPASpringAnnotationsOk1 {
    }

    // Lombok: Don't combine 1. @Data with @Value;
    // 2. @Data or @Value with bare/marker annotation one of: @EqualsAndHashCode, @ToString, @Getter, @Setter and @RequiredArgsConstructor
    @Data
    @Value
    class LombokAnnotationsViolation1 {
    }

    @ToString(includeFieldNames=true) // only bare/marker annotations are a violation
    @Data
    class LombokAnnotationsOk1 {
    }

    @ToString
    @Data
    class LombokAnnotationsViolation2 {
    }

    @Getter
    @Value
    class LombokAnnotationsViolation3 {
    }

    @EqualsAndHashCode
    @Data
    @Value
    class LombokAnnotationsViolation4 {
    }

}