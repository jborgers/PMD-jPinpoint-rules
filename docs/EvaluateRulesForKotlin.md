Evaluation of Java PMD rules for use as Kotlin rules
---
### Common

| Do  | Rule name                 | useful | complexity | used by sponsors | importance  | already available | note / to investigate                                        |
|-----|---------------------------|--------|------------|------------------|-------------|-------------------|--------------------------------------------------------------|
|     | AvoidCDIReferenceLeak     | Yes    | Medium     | No               | Low         | ?                 | Kotlin mostly not used with Java/JakartaEE                   |
|     | AvoidConstantsInInterface | ?      | Low?       | Yes              | Low         | ?                 | To investiate                                                |
| PP1 | AvoidDecimalAndChoiceFormatAsField       | Yes?   | Low        | Yes              | High        | ?                 | NumberFornat/DateFormat not included?                        |
|     | AvoidDuplicateAssignmentsInCases         | Yes    | Medium     | Yes              | Low/Medium  | ?                 | Add example, doc, Questionable if occuring often             |
|     | AvoidImplicitlyRecompilingRegex          | Yes    | High       | Yes              | High        | ?                 | Kotlin has own String/regex, also occurs here? support both? |
| JB1 | AvoidInMemoryStreamingDefaultConstructor | Yes    | Low        | Yes              | High        | ?                 | Kotlin types? -> No                                          |
|     | AvoidMultipleConcatStatements            | Yes    | Medium     | Yes              | High        | ?                 | How concat in Kotlin? Seems like Java                        | 
|     | AvoidRecompilingPatterns                 | Yes    | Low/Medium | Yes              | High        | ?                 | Kotlin version?                                              |
|     | AvoidRecompilingXPathExpression          | Yes    | Low        | Yes              | Medium/High | ?                 | Good example ThreadLocal in Kotlin?                          |
|     | AvoidRecreatingDateTimeFormatter         | Yes    | Medium     | Yes              | High        | ?                 | -                                                            |