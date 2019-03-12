package com.jpinpoint.perf.pinpointrules.concurrent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MakeAutoWiredConstructedFieldFinalTest {

    private FeatureTogglesMessageSource featuresViolate; // autowired constructor, yet non-final

    @Autowired
    public MakeAutoWiredConstructedFieldFinalTest(FeatureTogglesMessageSource featureTogglesMessageSource) {
        this.featuresViolate = featureTogglesMessageSource;
    }
}

class FeatureTogglesMessageSource {
}


