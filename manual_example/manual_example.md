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

### Prompt

`manual_example/offline_prompt.txt` is a prompt template. It require replaces placeholder with previous outputs and pasting project content from `manual_example/content-of_3redronin-murp.txt` 

### Results

`manual_example/result.md`