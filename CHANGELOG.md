Changelog
=========

# 0.47.0 / 2022-06-24

* [FEATURE] Add support for ZGC Cycles and ZGC pauses beans [#393][]
* [OTHER] Update jcommander dependency [#392][]

# 0.46.0 / 2022-03-14

### Changes

* [FEATURE] Make StatsD client queue size and telemetry configurable [#390][]

# 0.45.3 / 2021-12-22

### Changes

* [OTHER] remove all dependencies on log4j and use `java.util.logging` instead [#383][]

# 0.44.6 / 2021-12-21

### Changes

* [OTHER] remove all dependencies on log4j and use `java.util.logging` instead [#383][]

# 0.45.2 / 2021-12-15 

### Changes

* [BUGFIX] bumping log4j to v2.12.2 [#380][] 

# 0.44.5 / 2021-12-14 

### Changes

* [BUGFIX] bumping log4j to v2.12.2 [#380][]

# 0.45.1 / 2021-12-13 

### Changes

* [BUGFIX] enable format no NoLookups for log4j

# 0.44.4 / 2021-12-10

### Changes

* [BUGFIX] enable format no NoLookups for log4j

# 0.45.0 / 2021-12-09 

### Changes
* [FEATURE] Support adding multiple service tags to submitted metrics [#375][]
* [IMPROVEMENT] run() no longer static - call on App object [#376][]

# 0.44.3 / 2021-08-24

### Changes
* [BUGFIX] Remove messages for non-error can_connect service checks [#369][]

# 0.44.2 / 2021-07-08

### Changes
* [BUGFIX] Sleep after over-long iteration [#365][]

# 0.44.1 / 2021-05-31

### Changes
* [OTHER] Bump `jackson-jr-objects` dependency to version `2.12.3` [#364][]

# 0.44.0 / 2021-05-03

### Changes
* [IMPROVEMENT] Display more information when the error `Could not initialize instance` happens [#362][]

# 0.43.1 / 2021-04-27

### Changes
* [BUGFIX] Service tag should not be excluded if specified [#360][]

# 0.43.0 / 2021-04-16

### Changes
* [FEATURE] Add `jvm.gc.old_gen_size` as an alias for `Tenured Gen` [#358][]
* [BUGFIX] Prevent double signing of release artifacts [#357][]

# 0.42.1 / 2021-03-30

### Changes
* [IMPROVEMENT] Move publishing pipeline from Bintray to Sonatype. See
  [here](https://jfrog.com/blog/into-the-sunset-bintray-jcenter-gocenter-and-chartcenter/)
  for more info.

# 0.42.0 / 2021-01-25

### Changes
* [FEATURE] Adds a configurable period for the initial bean refresh [#349][]
* [BUGFIX] Fix empty include in configuration [#348][]

# 0.41.0 / 2020-12-14

### Changes
* [FEATURE] Make collection of default JVM metrics optional [#345][]

# 0.40.3 / 2020-11-19

### Changes
* [BUGFIX] Bump java-dogstatsd-client to version 2.10.5: fixes thread leak [java-dogstatsd-client-issue-128][]. [#344][]

# 0.40.2 / 2020-11-13

### Changes
* [BUGFIX] Bump java-dogstatsd-client to version 2.10.4: fixes thread leak [java-dogstatsd-client-issue-126][]. [#340][]

# 0.40.1 / 2020-10-30

### Changes
* [IMPROVEMENT] Status tracks java-dogstatsd-client errors [#336][]

# 0.40.0 / 2020-10-28

### Changes
* [IMPROVEMENT] Statsd reporter to require host name and optional port to support UDS [#335][]
* [IMPROVEMENT] Added runtime info to stats [#330][]

# 0.39.2 / 2020-09-22

### Changes
* [BUGFIX] JSON: serialization of status and reporter should include null fields. [#331][]

# 0.39.1 / 2020-09-18

### Changes
* [BUGFIX] Correct collection loop period [#323][]
* [IMPROVEMENT] Issue debug message for invalid types [#314][]

# 0.39.0 / 2020-09-03

### Changes
* [IMPROVEMENT] Remove 3rd-party deps: guava, commons-io, commons-lang, jackson-databind (replaced with jackson-jr-objects). [#319][], [#320][], [#321][], [#322][]
* [IMPROVEMENT] Make default reconnection timeout consistent with other default timeouts. [#317][]

# 0.38.2 / 2020-07-17

### Changes
* [BUGFIX] Re-introduce support of Java 7, broken in `0.38.1` because of a dependency upgrade. [#311][]

# 0.38.1 / 2020-07-07

### Changes
* [BUGFIX] Apply the '--log_format_rfc3339' option to the console logger. [#308][]

# 0.38.0 / 2020-06-25

### Changes
* [IMPROVEMENT] Collect Shenandoah GC collection count and time. [#306][]
* [IMPROVEMENT] Collect ZGC collection count and time. [#303][]
* [BUGFIX] Fix the '--log_format_rfc3339' option. [#302][]

# 0.37.0 / 2020-06-16

### Changes
* [IMPROVEMENT] Update logs format to match the Datadog Agent format. [#300][]
* [IMPROVEMENT] Add the option '--log_format_rfc3339' to use  RFC3339 for dates in the logs. [#300][]
* [IMPROVEMENT] Better handling of timeouts for both SSL and non-SSL RMI/JMX connections. [#298][]

# 0.36.2 / 2020-05-15

### Changes
* [SECURITY] Bump snakeyaml dependency to 1.26. [#294][]
* [IMPROVEMENT] Only log about broken instances when they exist. [#280][]

# 0.36.1 / 2020-04-02

### Changes
* [IMPROVEMENT] Fix json service check status [#287][]
* [BUGFIX] Revert pull request [#285][] [#288][] and re-implement the ability to configure the RMI connection timeout [#289][]

# 0.36.0 / 2020-03-31

### Changes
* [FEATURE] Add class name and class regex filters [#277][]
* [FEATURE] Add check prefix config [#284][]
* [IMPROVEMENT] Fix service checks in the JSON reporter [#278][]
* [IMPROVEMENT] Fix RMI socket connection timeout [#285][]

# 0.35.0 / 2020-02-24

### Changes
* [FEATURE] Add embedded mode. [#262][]
* [FEATURE] Add none EntityId by default to UDS metrics. [#262][]
* [FEATURE] Add new command to test rate metrics. See [#267][]
* [FEATURE] Add support for a `service` tag. See [#269][]
* [IMPROVEMENT] Fix NPE handling. [#260][]
* [IMPROVEMENT] Fix ThreadPool creation: accurate thread naming. See [#261][]
* [IMPROVEMENT] Fix getTagsMap generic parameter handling. See [#265][]
* [IMPROVEMENT] Replace LinkedHashMap with HashMap or Map. See [#266][]
* [IMPROVEMENT] Replace LinkedList with ArrayList or List. See [#268][]
* [IMPROVEMENT] Replace HashMap concrete type with Map interface. See [#270][]
* [IMPROVEMENT] Replace metric Map with a Metric class. See [#271][]
* [IMPROVEMENT] Cache and reuse metric instances. See [#273][]
* [IMPROVEMENT] Display service checks with JsonReporter. See [#272][]
* [IMPROVEMENT] Bump java-dogstatsd-client to version 0.29.0. See [#275][]

# 0.34.0 / 2020-01-13

### Changes
* [IMPROVEMENT] Collect direct memory buffers by default. [#257][]
* [SECURITY] Migrate to log4j2. [#258][]

# 0.33.1 / 2019-11-29

### Changes
* [IMPROVEMENT] Identify an instance with its configured name if available. [#255][]

# 0.33.0 / 2019-10-10

### Changes
* [IMPROVEMENT] Add the ability to list metrics in JSON. See [#241][]

# 0.32.1 / 2019-09-27

### Changes
* [SECURITY] Bump jackson dependency to 2.10.0. See [#250][]

# 0.32.0 / 2019-08-26

### Changes
* [IMPROVEMENT] Bump java-dogstatsd-client to 2.8. See [#245][]

# 0.31.0 / 2019-08-21

### Changes
* [IMPROVEMENT] Handle attributes that were being ignored on jasperserver. See [#243][]

# 0.30.1 / 2019-08-09

### Changes
* [SECURITY] Bump jackson-databind dependency to 2.9.9.3. See [#240][]

# 0.30.0 / 2019-07-08

### Changes
* [IMPROVEMENT] Provided a configuration option to execute jmxfetch tasks as daemons. See [#237][]
* [BUGFIX] Fix JMX metric name for G1GC old generation statistics. See [#231][]
* [IMPROVEMENT] Bump guava dependency to '27.1-android' (compatible with Java7). See [#228][]

# 0.29.1 / 2019-06-04

### Changes
* [SECURITY] Bump jackson dependency to 2.9.9. See [#227][]
* [IMPROVEMENT] Log new check names instead of their complete configuration. See [#229][]

# 0.29.0 / 2019-05-22

### Changes

* [IMPROVEMENT] Log via slf4j instead of directly to log4j. See [#223][]
* [IMPROVEMENT] Use Lombok builder for AppConfig, formalize jvm_direct connection. See [#225][]

# 0.28.0 / 2019-04-29

### Changes

* [IMPROVEMENT] Added --version command. See [#218][]
* [BUGFIX] Don't print a stacktrace when failing to get a metric. See [#219][]

# 0.27.1 / 2019-06-04

### Changes
* [SECURITY] Bump jackson dependency to 2.9.9. See [#227][]
* [IMPROVEMENT] Log new check names instead of their complete configuration. See [#229][]

# 0.27.0 / 2019-03-01

### Changes

* [IMPROVEMENT] `java-dogstatsd-client` has been upgraded to version 2.7. See [#207][] (Thanks [@mattdrees][])
* [IMPROVEMENT] `java-dogstatsd-client` connections errors are now logged. See [#208][] (Thanks [@mattdrees][])
* [IMPROVEMENT] Lower log level of log entry dumping all configs from AutoConfig. See [#212][]
* [BUGFIX] GC metrics `jvm.gc.eden_size` and `jvm.gc.survivor_size` were not collected for
`Eden Space` and `Survivor Space` memory pools. See [#214][]

# 0.26.2 / 2019-06-04

### Changes
* [SECURITY] Bump jackson dependency to 2.9.9. See [#227][]
* [IMPROVEMENT] Log new check names instead of their complete configuration. See [#229][]

# 0.26.1 / 2019-02-26

### Changes

* [BUGFIX] fix task processor readiness log + remove unused code. See [#210][]
* [IMPROVEMENT] better log entries. See [#210][]

# 0.26.0 / 2019-02-13

### Changes

* [FEATURE] Adding monotonic count support. See [#203][]
* [FEATURE] Concurrent instance metric collection. See [#199][]
* [IMPROVEMENT] `Checkstyle` + Google Java Style. See [#98][]

# 0.25.0 / 2019-01-23

### Changes

* [FEATURE] Allow `metrics.yaml` configs to be loaded via resources. See [#205][]
* [FEATURE] Allow replacing `$value` by the JMX value in a metric name. See [#204][]

# 0.24.1 / 2019-01-09

### Changes

* [SECURITY] Bump FasterXML to 2.9.8. See [#206][] and [CVE-2018-1000873](https://nvd.nist.gov/vuln/detail/CVE-2018-1000873).

# 0.24.0 / 2018-12-10

### Changes

* [FEATURE] Add support for direct local jmx connections. See [#201][]

# 0.23.0 / 2018-11-30

### Changes

* [FEATURE] Support the empty_default_hostname instance field. See [#184][]
* [FEATURE] Default JVM metrics for GC pools, class load count, and descriptors. See [#198][]
* [BUGFIX] Hide new GC metrics behind a flag. See [#197][]

# 0.22.0 / 2018-10-19

### Changes

* [FEATURE] Provide a way to pass extra tags when jmxfetch is used as a library. See [#191][].

# 0.21.0 / 2018-10-10

### Changes

* [FEATURE] Adds support for rmi registry connection over SSL and client authentication. See [#185][].
* [IMPROVEMENT] jmxfetch can now be used as a library. See [#180][].

# 0.20.2 / 2018-09-03

### Changes

* [SECURITY] Bump FasterXML to 2.9.6. See [#178][], [CVE-2018-7489](https://nvd.nist.gov/vuln/detail/CVE-2018-7489) and [CVE-2018-5968](https://nvd.nist.gov/vuln/detail/CVE-2018-5968).

# 0.20.1 / 2018-06-26

### Changes

* [BUGFIX] Use provided check name in the JSON as the instance check name. See [#174][].

# 0.20.0 / 2018-04-30

### Changes

* [FEATURE] Configs can now be given to jmxfetch using the https endpoint when running list_* troubleshooting commands. See [#171][].
* [IMPROVEMENT] Parameter `rmi_client_timeout` can now be given as an integer. See [#170][].

# 0.19.0 / 2018-03-19

### Changes

* [FEATURE] Ability to specify tags on metrics based on regex groupings of bean names. See [#167][].
* [IMPROVEMENT] Set TCP response timeout to ensure JMXFetch doesn't hang on broken beans. See [#168][].

# 0.18.2 / 2018-02-13

#### Changes

* [IMPROVEMENT] Logs are now output to stdout if no log file is configured. See [#164][].

# 0.18.1 / 2017-12-05

#### Changes

* [BUGFIX] confd is now an optional parameter. See [#161][]

# 0.18.0 / 2017-10-11

#### Changes

* [FEATURE] Collect instance configurations via API. See [#156][]

# 0.17.0 / 2017-09-20

#### Changes

* [FEATURE] Add support for submission of JMX statuses to REST API. See [#155][]
* [IMPROVEMENT] Rates: add canonical_rate + feature flag for the feature. See [#154][] (Thanks [@arrawatia][])

# 0.16.0 / 2017-08-21

#### Changes

* [BUGFIX] Increase maximum length of instance configs pulled from Auto-Discovery pipe. See [#147][]
* [IMPROVEMENT] Touch JMXFetch launch file on boot-up. See [#143][]

# 0.15.0 / 2017-07-10

#### Changes

* [FEATURE] Transition to auto-discovery nomenclature, support legacy SD. See [#142][]
* [IMPROVEMENT] Auto_discovery: process templates larger than the page buffer size. See [#145][]

# 0.14.0 / 2017-05-31

#### Changes
* [FEATURE] Add support for `min_collection_interval`. See [#135][] and [#140][]

# 0.13.1 / 2017-04-18

#### Changes
* [BUGFIX] Service_discovery: fix race condition preventing SD initialization. See [#135][]

# 0.13.0 / 2017-03-22

#### Changes
* [BUGFIX] Allow specifying no alias on detailed attribute. See [#133][]
* [BUGFIX] Fix connectivity loss when multiple instances are assigned to a same JVM. See [#124][]
* [BUGFIX] Parse string-defined ports to integers in user configurations. See [#121][]
* [BUGFIX] Support `java.util.Map` attribute types. See [#130][]
* [BUGFIX] Support list-defined user tags at instance level. See [#132][]
* [FEATURE] Add `histogram` metric type. See [#115][]
* [FEATURE] Add `list_jvms` command to list available JVMs when using the Attach API. See [#100][], [#112][] (Thanks [@cslee00][])
* [FEATURE] Add tag blacklisting. See [#116][]
* [FEATURE] Add user tags definition for MBeans. See [#117][].
* [FEATURE] Enable service discovery via a named pipe. See [#113][]
* [FEATURE] Support `javax.management.openmbean.TabularData` attribute types. See [#111][], [#128][] (Thanks [@brothhaar][])
* [FEATURE] Support user tag value substitution by attribute name. See [#117][].
* [IMPROVEMENT] Print exception messages on Attach API connection failures. See [#122][] (Thanks [@aoking][])

# 0.12.0 / 2016-09-27

#### Changes
* [BUGFIX] Fix `list_not_matching_attributes` action to return all "not matching" attributes. See [#102][] (Thanks [@nwillems][])

# 0.11.0 / 2016-05-23

#### Changes
* [BUGFIX] Report properly beans with ':' in the name. See [#90][], [#91][], [#95][] (Thanks [@bluestix][])
* [BUGFIX] Sanitize metric names and tags, i.e. remove illegal characters. See [#89][]
* [BUGFIX] Support `javax.management.Attribute` attribute types. See [#92][] (Thanks [@nwillems][])
* [FEATURE] Add user tags to service checks. See [#96][]
* [FEATURE] Allow group name substitutions in attribute/alias parameters. See [#94][], [#97][] (Thanks [@alz][])

# 0.10.0 / 2016-03-23

#### Changes
* [FEATURE] Allow configuration of StatsD host. See [#85][]
* [IMPROVEMENT] Re-throw IOException caught at the instance-level to handle them properly. See [#83][]

# 0.9.0 / 2015-11-05

#### Changes
* [BUGFIX] Fix bean name matching logic: `OR`→`AND`. See [#81][]
* [FEATURE] Support `float` and `java.lang.Float` attribute types as simple JMX attributes. See [#76][]
* [FEATURE] Support Cassandra > 2.2 metric name structure (CASSANDRA-4009). See [#79][]
* [FEATURE] Support custom JMX Service URL to connect to, on a per-instance basis. See [#80][]
* [IMPROVEMENT] Assign generic alias if not defined. See [#78][]

# 0.8.0 / 2015-09-17

#### Changes
* [BUGFIX] Do not send service check warnings on metric limit violation. See [#73][]
* [BUGFIX] Log exception stack traces instead of printing them. See [#67][]
* [BUGFIX] Use `jmx_server` tag instead of `host` to tag JMX host's service checks. See [#66][]
* [FEATURE] Wildcard support on domains and bean names. See [#57][]
* [IMPROVEMENT] Memory saving by limiting MBeans queries to certain scopes. See [#63][]
* [IMPROVEMENT] Memory saving by query bean names instead of full bean objects. See [#71][]

# 0.7.0 / 2015-06-04

#### Changes
* [BUGFIX] Rename 'host' bean parameter to 'bean_host' in tags to avoid conflicts. See [#59][]
* [ENHANCEMENT] Add option to exit JMXFetch when a specified file is created. See [#58][]

# 0.6.0 / 2015-05-20

#### Changes
* [ENHANCEMENT] Format service check names prefix names to strip non alphabetic characters. See [#53][]
* [FEATURE] Write the number of service check sent to status file. See [#54][]

# 0.5.2 / 2015-04-08

#### Changes
* [ENHANCEMENT] Only send instance name in service check tags. See [#51][]

# 0.5.1 / 2015-04-02

#### Changes
* [BUGFIX] Fix bad regression with `Status` type. See [#49][]

# 0.5.0 / 2015-03-16

#### Changes
* [FEATURE] Send service checks for JMX integrations
* [FEATURE] Support list of filters instead of simple filters: See [#20][]

# 0.4.1 / 2014-02-11

#### Changes
* [BUGFIX] Memory leak fix when fetching attributes value is failing. See [#30][]
* [FEATURE/BUGFIX] Fetch more JVM (Non)Heap variables by default. See [bd8915c2f1eec794f406414b678ce6110407dce1](https://github.com/DataDog/jmxfetch/commit/bd8915c2f1eec794f406414b678ce6110407dce1)

# 0.3.0 / 2014-03-25

#### Changes
* [BUGFIX] Refresh JMX tree: See [4374b92cbf1b93d88fa5bd9d7339907e16a2da4a](https://github.com/DataDog/jmxfetch/commit/4374b92cbf1b93d88fa5bd9d7339907e16a2da4a)
* [BUGFIX] Reset statsd connection: See [#19][]
* [BUGFIX] Support WARN log level, See [#14][]
* [FEATURE] Support custom instance tags: See [#28][] (Thanks [@coupacooke][])
* [FEATURE] Support new types: Boolean, String, java.lang.Number, AtomicInteger, AtomicLong: See [#25][] [#26][] (Thanks [@coupacooke][])


[java-dogstatsd-client-issue-128]: https://github.com/DataDog/java-dogstatsd-client/pull/128
[java-dogstatsd-client-issue-126]: https://github.com/DataDog/java-dogstatsd-client/pull/126

<!--- The following link definition list is generated by PimpMyChangelog --->
[#14]: https://github.com/DataDog/jmxfetch/issues/14
[#19]: https://github.com/DataDog/jmxfetch/issues/19
[#20]: https://github.com/DataDog/jmxfetch/issues/20
[#25]: https://github.com/DataDog/jmxfetch/issues/25
[#26]: https://github.com/DataDog/jmxfetch/issues/26
[#28]: https://github.com/DataDog/jmxfetch/issues/28
[#30]: https://github.com/DataDog/jmxfetch/issues/30
[#49]: https://github.com/DataDog/jmxfetch/issues/49
[#50]: https://github.com/DataDog/jmxfetch/issues/50
[#51]: https://github.com/DataDog/jmxfetch/issues/51
[#53]: https://github.com/DataDog/jmxfetch/issues/53
[#54]: https://github.com/DataDog/jmxfetch/issues/54
[#57]: https://github.com/DataDog/jmxfetch/issues/57
[#58]: https://github.com/DataDog/jmxfetch/issues/58
[#59]: https://github.com/DataDog/jmxfetch/issues/59
[#63]: https://github.com/DataDog/jmxfetch/issues/63
[#66]: https://github.com/DataDog/jmxfetch/issues/66
[#67]: https://github.com/DataDog/jmxfetch/issues/67
[#71]: https://github.com/DataDog/jmxfetch/issues/71
[#73]: https://github.com/DataDog/jmxfetch/issues/73
[#76]: https://github.com/DataDog/jmxfetch/issues/76
[#78]: https://github.com/DataDog/jmxfetch/issues/78
[#79]: https://github.com/DataDog/jmxfetch/issues/79
[#80]: https://github.com/DataDog/jmxfetch/issues/80
[#81]: https://github.com/DataDog/jmxfetch/issues/81
[#83]: https://github.com/DataDog/jmxfetch/issues/83
[#85]: https://github.com/DataDog/jmxfetch/issues/85
[#89]: https://github.com/DataDog/jmxfetch/issues/89
[#90]: https://github.com/DataDog/jmxfetch/issues/90
[#91]: https://github.com/DataDog/jmxfetch/issues/91
[#92]: https://github.com/DataDog/jmxfetch/issues/92
[#94]: https://github.com/DataDog/jmxfetch/issues/94
[#95]: https://github.com/DataDog/jmxfetch/issues/95
[#96]: https://github.com/DataDog/jmxfetch/issues/96
[#97]: https://github.com/DataDog/jmxfetch/issues/97
[#98]: https://github.com/DataDog/jmxfetch/issues/98
[#100]: https://github.com/DataDog/jmxfetch/issues/100
[#102]: https://github.com/DataDog/jmxfetch/issues/102
[#111]: https://github.com/DataDog/jmxfetch/issues/111
[#112]: https://github.com/DataDog/jmxfetch/issues/112
[#113]: https://github.com/DataDog/jmxfetch/issues/113
[#115]: https://github.com/DataDog/jmxfetch/issues/115
[#116]: https://github.com/DataDog/jmxfetch/issues/116
[#117]: https://github.com/DataDog/jmxfetch/issues/117
[#121]: https://github.com/DataDog/jmxfetch/issues/121
[#122]: https://github.com/DataDog/jmxfetch/issues/122
[#124]: https://github.com/DataDog/jmxfetch/issues/124
[#128]: https://github.com/DataDog/jmxfetch/issues/128
[#130]: https://github.com/DataDog/jmxfetch/issues/130
[#132]: https://github.com/DataDog/jmxfetch/issues/132
[#133]: https://github.com/DataDog/jmxfetch/issues/133
[#135]: https://github.com/DataDog/jmxfetch/issues/135
[#140]: https://github.com/DataDog/jmxfetch/issues/140
[#142]: https://github.com/DataDog/jmxfetch/issues/142
[#143]: https://github.com/DataDog/jmxfetch/issues/143
[#145]: https://github.com/DataDog/jmxfetch/issues/145
[#147]: https://github.com/DataDog/jmxfetch/issues/147
[#154]: https://github.com/DataDog/jmxfetch/issues/154
[#155]: https://github.com/DataDog/jmxfetch/issues/155
[#156]: https://github.com/DataDog/jmxfetch/issues/156
[#161]: https://github.com/DataDog/jmxfetch/issues/161
[#164]: https://github.com/DataDog/jmxfetch/issues/164
[#167]: https://github.com/DataDog/jmxfetch/issues/167
[#168]: https://github.com/DataDog/jmxfetch/issues/168
[#170]: https://github.com/DataDog/jmxfetch/issues/170
[#171]: https://github.com/DataDog/jmxfetch/issues/171
[#174]: https://github.com/DataDog/jmxfetch/issues/174
[#178]: https://github.com/DataDog/jmxfetch/issues/178
[#180]: https://github.com/DataDog/jmxfetch/issues/180
[#184]: https://github.com/DataDog/jmxfetch/issues/184
[#185]: https://github.com/DataDog/jmxfetch/issues/185
[#191]: https://github.com/DataDog/jmxfetch/issues/191
[#197]: https://github.com/DataDog/jmxfetch/issues/197
[#198]: https://github.com/DataDog/jmxfetch/issues/198
[#199]: https://github.com/DataDog/jmxfetch/issues/199
[#201]: https://github.com/DataDog/jmxfetch/issues/201
[#203]: https://github.com/DataDog/jmxfetch/issues/203
[#204]: https://github.com/DataDog/jmxfetch/issues/204
[#205]: https://github.com/DataDog/jmxfetch/issues/205
[#206]: https://github.com/DataDog/jmxfetch/issues/206
[#207]: https://github.com/DataDog/jmxfetch/issues/207
[#208]: https://github.com/DataDog/jmxfetch/issues/208
[#210]: https://github.com/DataDog/jmxfetch/issues/210
[#212]: https://github.com/DataDog/jmxfetch/issues/212
[#214]: https://github.com/DataDog/jmxfetch/issues/214
[#218]: https://github.com/DataDog/jmxfetch/issues/218
[#219]: https://github.com/DataDog/jmxfetch/issues/219
[#223]: https://github.com/DataDog/jmxfetch/issues/223
[#225]: https://github.com/DataDog/jmxfetch/issues/225
[#227]: https://github.com/DataDog/jmxfetch/issues/227
[#228]: https://github.com/DataDog/jmxfetch/issues/228
[#229]: https://github.com/DataDog/jmxfetch/issues/229
[#231]: https://github.com/DataDog/jmxfetch/issues/231
[#237]: https://github.com/DataDog/jmxfetch/issues/237
[#240]: https://github.com/DataDog/jmxfetch/issues/240
[#241]: https://github.com/DataDog/jmxfetch/issues/241
[#243]: https://github.com/DataDog/jmxfetch/issues/243
[#245]: https://github.com/DataDog/jmxfetch/issues/245
[#250]: https://github.com/DataDog/jmxfetch/issues/250
[#255]: https://github.com/DataDog/jmxfetch/issues/255
[#257]: https://github.com/DataDog/jmxfetch/issues/257
[#258]: https://github.com/DataDog/jmxfetch/issues/258
[#260]: https://github.com/DataDog/jmxfetch/issues/260
[#261]: https://github.com/DataDog/jmxfetch/issues/261
[#262]: https://github.com/DataDog/jmxfetch/issues/262
[#265]: https://github.com/DataDog/jmxfetch/issues/265
[#266]: https://github.com/DataDog/jmxfetch/issues/266
[#267]: https://github.com/DataDog/jmxfetch/issues/267
[#268]: https://github.com/DataDog/jmxfetch/issues/268
[#269]: https://github.com/DataDog/jmxfetch/issues/269
[#270]: https://github.com/DataDog/jmxfetch/issues/270
[#271]: https://github.com/DataDog/jmxfetch/issues/271
[#272]: https://github.com/DataDog/jmxfetch/issues/272
[#273]: https://github.com/DataDog/jmxfetch/issues/273
[#275]: https://github.com/DataDog/jmxfetch/issues/275
[#277]: https://github.com/DataDog/jmxfetch/issues/277
[#278]: https://github.com/DataDog/jmxfetch/issues/278
[#280]: https://github.com/DataDog/jmxfetch/issues/280
[#284]: https://github.com/DataDog/jmxfetch/issues/284
[#285]: https://github.com/DataDog/jmxfetch/issues/285
[#287]: https://github.com/DataDog/jmxfetch/issues/287
[#288]: https://github.com/DataDog/jmxfetch/issues/288
[#289]: https://github.com/DataDog/jmxfetch/issues/289
[#294]: https://github.com/DataDog/jmxfetch/issues/294
[#298]: https://github.com/DataDog/jmxfetch/issues/298
[#300]: https://github.com/DataDog/jmxfetch/issues/300
[#302]: https://github.com/DataDog/jmxfetch/issues/302
[#303]: https://github.com/DataDog/jmxfetch/issues/303
[#306]: https://github.com/DataDog/jmxfetch/issues/306
[#308]: https://github.com/DataDog/jmxfetch/issues/308
[#311]: https://github.com/DataDog/jmxfetch/issues/311
[#314]: https://github.com/DataDog/jmxfetch/issues/314
[#317]: https://github.com/DataDog/jmxfetch/issues/317
[#319]: https://github.com/DataDog/jmxfetch/issues/319
[#320]: https://github.com/DataDog/jmxfetch/issues/320
[#321]: https://github.com/DataDog/jmxfetch/issues/321
[#322]: https://github.com/DataDog/jmxfetch/issues/322
[#323]: https://github.com/DataDog/jmxfetch/issues/323
[#330]: https://github.com/DataDog/jmxfetch/issues/330
[#331]: https://github.com/DataDog/jmxfetch/issues/331
[#335]: https://github.com/DataDog/jmxfetch/issues/335
[#336]: https://github.com/DataDog/jmxfetch/issues/336
[#340]: https://github.com/DataDog/jmxfetch/issues/340
[#344]: https://github.com/DataDog/jmxfetch/issues/344
[#345]: https://github.com/DataDog/jmxfetch/issues/345
[#348]: https://github.com/DataDog/jmxfetch/issues/348
[#349]: https://github.com/DataDog/jmxfetch/issues/349
[#357]: https://github.com/DataDog/jmxfetch/issues/357
[#358]: https://github.com/DataDog/jmxfetch/issues/358
[#360]: https://github.com/DataDog/jmxfetch/issues/360
[#362]: https://github.com/DataDog/jmxfetch/issues/362
[#364]: https://github.com/DataDog/jmxfetch/issues/364
[#365]: https://github.com/DataDog/jmxfetch/issues/365
[#369]: https://github.com/DataDog/jmxfetch/issues/369
[#375]: https://github.com/DataDog/jmxfetch/issues/375
[#376]: https://github.com/DataDog/jmxfetch/issues/376
[#380]: https://github.com/DataDog/jmxfetch/issues/380
[#383]: https://github.com/DataDog/jmxfetch/issues/383
[#390]: https://github.com/DataDog/jmxfetch/issues/390
[#392]: https://github.com/DataDog/jmxfetch/issues/393
[#393]: https://github.com/DataDog/jmxfetch/issues/393
[@alz]: https://github.com/alz
[@aoking]: https://github.com/aoking
[@arrawatia]: https://github.com/arrawatia
[@bluestix]: https://github.com/bluestix
[@brothhaar]: https://github.com/brothhaar
[@coupacooke]: https://github.com/coupacooke
[@cslee00]: https://github.com/cslee00
[@mattdrees]: https://github.com/mattdrees
[@nwillems]: https://github.com/nwillems