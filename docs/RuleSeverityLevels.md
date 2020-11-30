# Severity levels of the rules
1. Blocker
2. Critical
3. Major
4. Minor
5. Informative

## Criteria for a level
1. **Blocker**: high risk, the finding can cause much trouble, costly consequences. Like customer data mix-up, crashes, difficult to reproduce errors or malfunction. 
2. **Critical**: serious risk, it can cause severe performance degradation with bad user experience. Or malfunction like 1 but we are not sure or chances are small.
3. **Major**: it increases the chances, the risk of above 2 consequences.
4. **Minor**: it is generally a bad practice, it may increase the above risks.
5. **Informative**: for your information, it might increase the above risks.

| Rule name | original level 2020-11| proposed level | level after review 1 (if different)| decided level after review 2 (if different) - apply from 2020-11-30 
| --------- | ------------- | -------------- | ------- | ---
|AvoidApacheCommonsFileItemNonStreaming| 2| 2|
|AvoidCDIReferenceLeak| 2| 1|
|AvoidCalendar| 2| 3|
|AvoidCalendarDateCreation| 2| 2|
|AvoidCompletionServiceTake| 2| 2|
|AvoidConcatInAppend| 2| 2|
|AvoidConcatInLoop| 2| 2|
|AvoidConstantsInInterface| 3| 3-4?| 3
|AvoidDecimalAndChoiceFormatAsField|2|1? |1
|AvoidDeprecatedHttpConnectors|3 |1? |2 (maybe later to 1)
|AvoidDuplicateAssignmentsInCases|2 |2 |
|AvoidExpressionsInCacheable|2|2|
|AvoidFutureGetWithoutTimeout| 2| 2| |1
|AvoidHugeQueryFetchSize|2|2|
|AvoidImplicitlyRecompilingRegex| 2| 2|
|AvoidImproperAnnotationCombinations|2|3?|3
|AvoidInMemoryStreamingDefaultConstructor|2 | 2|
|AvoidIncrementOrDecrementForVolatileField| 2| 1?|1
|AvoidJAXBUtil| 2| 2|
|AvoidModelMapAsRenderParameter|2|2|
|AvoidMultipleConcatStatements|2 | 2|
|AvoidMultipleRoundtripsForQuery|2|2|
|AvoidMutableLists|2 |3 |
|AvoidMutableStaticFields| 2| 3?| 3
|AvoidRecompilingPatterns| 2|2 |
|AvoidRecompilingXPathExpression| 2| 2|
|AvoidRecreatingDateTimeFormatter| 2| 2|
|AvoidReflectionInToStringAndHashCode| 2| 2|
|AvoidSimpleCaches|2|3| |2
|AvoidSimpleDateFormat| 2| 2|
|AvoidSpringApplicationContextRecreation|2|2|
|AvoidSpringMVCMemoryLeaks|2|2| |1
|AvoidSqlInExpression|2|2|
|AvoidStaticXmlFactories| 1| 1|
|AvoidStringBuffer| 3| 3|
|AvoidThreadUnsafeJaxbUsage| 1| 1|
|AvoidTimeUnitConfusion| 2| 3?|3
|AvoidUnconditionalBuiltLogStrings| 2| 2|
|AvoidUnguardedAssignmentToNonFinalFieldsInObjectsUsingSynchronized| 2| 1?|1
|AvoidUnguardedAssignmentToNonFinalFieldsInSharedObjects| 2| 1?|1
|AvoidUnguardedMutableFieldsInObjectsUsingSynchronized| 3| 3?|3
|AvoidUnguardedMutableFieldsInSharedObjects| 2| 2?|1
|AvoidUnguardedMutableInheritedFieldsInSharedObjects| 2| 2?|1
|AvoidVolatileInPrototypeScope|2|2|
|AvoidWideScopeXPathExpression| 2| 2|
|AvoidXMLGregorianCalendar| 2| 2|
|AvoidXPathAPIUsage| 2| 3?|3
|AvoidXPathUsage| 3| 3?|3
|DefineConcurrencyForJavaEESingleton|2|3|
|HttpClientBuilderWithoutDisableConnectionState| 2| 2|
|ImplementEqualsHashCodeOnValueObjects| 3| 3|
|InconsistentEqualsAndHashCode| 2| 1?| 1 (false positives fixed)
|JAXBContextCreatedForEachMethodCall| 2| 2|
|MDCPutWithoutRemove| 2| 2|
|MakeAutoWiredConstructedFieldFinal|4|3?|3
|MinimizeActionModelMapInSession| 2| 2|
|MinimizeAttributesInSession|2|2|
|MissingFieldInEquals| 2| 3?| 3 (maybe later 2)
|NotProperlySynchronizingOnFieldWhileUsingGuardedBy|3|3| |2
|NotProperlySynchronizingOnThisWhileUsingGuardedBy|3|3| |2
|ObjectMapperCreatedForEachMethodCall| 2| 2|
|SynchronizeForKeyInCacheable|2|2|
|UnconditionalConcatInLogArgument| 2| 2|
|UnconditionalOperationOnLogArgument| 2| 2|
|UsingSuppressWarnings| 4| 5?| 4 (maybe later 5)|5
|UsingSuppressWarningsHighRisk| 4| 4?| 4
