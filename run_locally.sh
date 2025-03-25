mvn clean install -Dmaven.test.skip=true &&
java -Xms16g -Xmx16g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication -jar target/backtester-0.0.1-SNAPSHOT.jar
