# We investigating repo:
https://github.com/3redronin/murp#


### Code hotspots
```bash
git log --since="3 year ago" --pretty=format:"" --name-only --no-merges | \
  grep -vE "${EXCLUDE_PATTERN_GREP:-^$}" | \
  grep '.' | \
  sort | \
  uniq -c | \
  sort -nr | \
  head -n 10 | \
  awk '{count=$1; $1=""; sub(/^[ \t]+/, ""); print $0 ": " count " changes"}' | cat
```

Output
```
pom.xml: 14 changes
src/test/java/io/muserver/murp/ReverseProxyTest.java: 13 changes
src/main/java/io/muserver/murp/ReverseProxy.java: 13 changes
src/main/java/io/muserver/murp/ReverseProxyBuilder.java: 3 changes
src/main/java/io/muserver/murp/ResponseInterceptor.java: 3 changes
src/main/java/io/muserver/murp/ProxyListener.java: 3 changes
.github/workflows/release.yaml: 3 changes
src/test/java/io/muserver/murp/TimeoutsTest.java: 2 changes
src/main/java/io/muserver/murp/RequestInterceptor.java: 2 changes
src/main/java/io/muserver/murp/HttpClientUtils.java: 2 changes  
```

### Directories hotspots

```bash
git log --since="3 year ago" --pretty=format:"" --name-only --no-merges | \
  grep -vE "${EXCLUDE_PATTERN_GREP:-^$}" | \
  grep '.' | \
  awk -F/ -v OFS=/ 'NF > 1 {$NF = ""; print $0 } NF <= 1 { print "." }' | \
  sed 's|/*$||' | \
  sed 's|^\\.$|project root|' | \
  sort | \
  uniq -c | \
  sort -nr | \
  head -n 10 | \
  awk '{count=$1; $1=""; sub(/^[ \t]+/, ""); print $0 ": " count " changes"}' | cat
```
Output
```
src/main/java/io/muserver/murp: 31 changes
src/test/java/io/muserver/murp: 17 changes
.: 15 changes
.github/workflows: 5 changes
src/test/java: 1 changes  
```

### Commit history

```bash
git log -n 100 --no-merges --date=short --pretty=format:"<commit date='%ad'>%s</commit>"
```

Output:
```
<commit date='2025-08-15'>Updated dependencies and changing maven publish</commit>
<commit date='2025-08-15'>Updated dependencies and changing maven publish</commit>
<commit date='2025-03-11'>adding error detect callback</commit>
<commit date='2025-03-11'>improve error handling for target server early drop</commit>
<commit date='2025-02-28'>stablize the test</commit>
<commit date='2025-02-28'>skip the early drop test before jdk 17</commit>
<commit date='2025-02-28'>update maven dependencies</commit>
<commit date='2025-02-28'>review and remove duplicate cancel/onError call</commit>
<commit date='2025-02-28'>improve the error handling for client early drop</commit>
<commit date='2025-02-24'>Updated plugins</commit>
<commit date='2025-02-24'>Fixing github actions</commit>
<commit date='2025-02-23'>update mvn dependencies</commit>
<commit date='2025-02-23'>add ProxyListener for tracing requests life cycle, also improve the error handling.</commit>
<commit date='2024-12-19'>When disabling TLS verification, also disable hostname verification as described by https://stackoverflow.com/a/70741993/131578</commit>
<commit date='2024-09-07'>clone byteBuffer when uploading request body</commit>
<commit date='2024-09-05'>adding test case to reproduce issue</commit>
<commit date='2024-05-09'>fix legacy forwarded header null pointer exception (#14)</commit>
<commit date='2024-03-25'>bug fix: response body bytes mis-ordering (#13)</commit>
<commit date='2023-12-26'>bug fixes: remove pseudo headers for http/1.1 client</commit>
<commit date='2023-12-10'>bug fixes: 1. can proxy multiple header 2. update response interceptor to avoid null pointer</commit>
<commit date='2023-11-06'>Plugin version updates</commit>
<commit date='2023-11-06'>Update the CI and SCM references</commit>
<commit date='2023-11-06'>fix build (#11)</commit>
<commit date='2023-11-05'>fix ci build (#10)</commit>
<commit date='2023-11-05'>No jetty (#9)</commit>
<commit date='2022-08-07'>Fixed the tests for http2</commit>
<commit date='2022-08-07'>Dependency upgrades</commit>
<commit date='2021-02-17'>Gateway error messages are printed using the async handle rather than the blocking one</commit>
<commit date='2020-10-19'>Added test for streamed request bodies</commit>
<commit date='2020-10-19'>Proxy body if request does not have Content-Length header</commit>
<commit date='2020-10-13'>Bump junit from 4.12 to 4.13.1</commit>
<commit date='2020-06-29'>Updated dependencies</commit>
<commit date='2020-06-28'>Changed back to oraclejdk8 and trusty</commit>
<commit date='2020-06-28'>Changed from oracle to openjdk8</commit>
<commit date='2020-06-28'>Downgrading to xenial to allow for java8</commit>
<commit date='2020-06-28'>Updated dependencies and updated failing test. Updated travisci build</commit>
<commit date='2020-06-28'>Updated dependencies to the latest versions to remediate issues raised by snyk.io</commit>
<commit date='2019-10-13'>Upgraded to latest mu version</commit>
<commit date='2019-10-07'>Removed unnecessary test dependency</commit>
<commit date='2019-10-07'>Added test to check that cookies sent across multiple headers (which can only happen in http2) are all forwarded. To ensure this happens, mu-server 0.41.2 or later must be used.</commit>
<commit date='2019-10-06'>Fixing travis build config</commit>
<commit date='2019-10-06'>Fixed test compile error</commit>
<commit date='2019-10-06'>Updated to latest mu version</commit>
<commit date='2019-07-08'>Mu version bump</commit>
<commit date='2019-07-07'>When receiving data in a request, the data is no longer copied in a buffer</commit>
<commit date='2019-05-16'>Updated to latest mu</commit>
<commit date='2019-05-16'>Stop proxying the 'expect' header so clients that use it don't hang. A better fix would be to actually proxy and handle the expect header better.</commit>
<commit date='2019-05-05'>Added large headers test</commit>
<commit date='2019-04-29'>The HTTP client's request header buffer size can be set; HTTP2 works under a toggle.</commit>
<commit date='2019-04-23'>Updated to latest mu where Headers is an interface rather than a class</commit>
<commit date='2019-04-16'>Changed the max request header size from 4k to 16k</commit>
<commit date='2019-04-06'>Removed hard coded path in config</commit>
<commit date='2019-04-06'>Updated release plugin version</commit>
<commit date='2019-04-06'>Added Slf4jResponseLogger</commit>
<commit date='2019-04-06'>Added request and response interceptors</commit>
<commit date='2019-04-05'>Added tests for idle and total timeouts</commit>
<commit date='2019-04-04'>Made test threadsafe</commit>
<commit date='2019-04-04'>Adding logging for test failure on travis</commit>
<commit date='2019-04-04'>Fixed the example reverse proxy</commit>
<commit date='2019-04-04'>Added option to not proxy the Host header as that will be rejected by HTTPS servers with SNI</commit>
<commit date='2019-03-31'>Updated readme and docs</commit>
<commit date='2019-03-31'>Added io.muserver.murp.Murp.pathAndQuery(Uri)</commit>
<commit date='2019-03-29'>Made the Via header a comma separated list (rather than multiple headers) and updated mu version</commit>
<commit date='2019-03-28'>Added listener for subscribing to proxy-complete events and exposed some useful things from the ReverseProxy</commit>
<commit date='2019-03-28'>Added Murp.artifactVersion()</commit>
<commit date='2019-03-28'>Updated to latest jetty version</commit>
<commit date='2019-03-28'>Added name to pom to allow releases to the OSS repo</commit>
<commit date='2019-03-28'>Added scm section</commit>
<commit date='2019-03-28'>Added ciManagement section</commit>
<commit date='2019-03-28'>Added initial implementation</commit>
<commit date='2019-03-27'>Initial commit</commit>
```

### Prompt

`manual_example/10x_
offline_prompt.txt` is a prompt template. It require replaces placeholder with previous outputs and pasting project content from `manual_example/content-of_3redronin-murp.txt` 
