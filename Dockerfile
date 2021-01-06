FROM openjdk:8-jre

ENTRYPOINT ["/usr/local/openjdk-8/bin/java", "-jar", "/usr/share/rdns-lookup/rdns-lookup.jar"]

ADD target/lib /usr/share/rdns-lookup/lib
ARG JAR_FILE
ADD target/${JAR_FILE} /usr/share/rdns-lookup/rdns-lookup.jar