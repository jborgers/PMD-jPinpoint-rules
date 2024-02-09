Evaluation of Java PMD rules for use as Kotlin rules
---
### Common

| #   | Who does | Rule name                                | useful | complexity | used by sponsors | importance  | already available | note / to investigate                                        |
|-----|---------|------------------------------------------|--------|------------|------------------|-------------|-------------------|--------------------------------------------------------------|
| 1.  |         | AvoidCDIReferenceLeak                    | Yes    | Medium     | No               | Low         | ?                 | Kotlin mostly not used with Java/JakartaEE                   |
| 2.  |       | AvoidConstantsInInterface                | ?      | Low?       | Yes              | Low         | ?                 | To investiate                                                |
| 3.  | PP-1 | AvoidDecimalAndChoiceFormatAsField       | Yes?   | Low        | Yes              | High        | ?                 | NumberFornat/DateFormat not included?                        |
| 4.  |      | AvoidDuplicateAssignmentsInCases         | Yes    | Medium     | Yes              | Low/Medium  | ?                 | Add example, doc, Questionable if occuring often             |
| 5.  |      | AvoidImplicitlyRecompilingRegex          | Yes    | High       | Yes              | High        | ?                 | Kotlin has own String/regex, also occurs here? support both? |
| 6.  | JB-1 | AvoidInMemoryStreamingDefaultConstructor | Yes    | Low        | Yes              | High        | ?                 | Kotlin types? -> No                                          |
| 7.  |      | AvoidMultipleConcatStatements            | Yes    | Medium     | Yes              | High        | ?                 | How concat in Kotlin? Seems like Java                        | 
| 8.  |      | AvoidRecompilingPatterns                 | Yes    | Low/Medium | Yes              | High        | ?                 | Kotlin version?                                              |
| 9.  |      | AvoidRecompilingXPathExpression          | Yes    | Low        | Yes              | Medium/High | ?                 | Good example ThreadLocal in Kotlin?                          |
| 10. |      | AvoidRecreatingDateTimeFormatter         | Yes    | Medium     | Yes              | High        | ?                 | -                                                            |
| 11. |      | AvoidReflectionInToStringAndHashCode     | Yes    | Low/Medium | Yes              | Low/Medium  | ?                 | -                                                            |
| 12. |      | AvoidSimpleDateFormat                    | Yes    | Low        | Yes              | Medium      | ?                 |                                                              |
| 13. |      | AvoidStringBuffer                        | Yes    | Low        | Yes              | Low/Medium  | ?                 |                                                              |
| 14. |      | AvoidUnconditionalBuiltLogStrings        | Yes    | High       | Yes              | Medium      | ?                 |                                                              | 
| 15. |      | AvoidWideScopeXPathExpression            | Yes    | Low        | Yes              | Medium      | ?                 |                                                              | 
| 16. |      | AvoidXPathAPIUsage                       | Yes    | Low        | Yes              | Medium      | ?                 | remove VTD reference?, seems old, better alternatives?       |
| 17. |      |                                          |        |            |                  |             |                   |                                                              |

