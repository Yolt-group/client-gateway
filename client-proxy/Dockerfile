FROM 627987680837.dkr.ecr.eu-central-1.amazonaws.com/prd/yolt-openjdk-15:680149
ADD target/*.jar /app.jar
ENTRYPOINT ["sh", "-c", "java -XX:+UnlockExperimentalVMOptions -XX:MaxRAMPercentage=75.0 -XX:-OmitStackTraceInFastThrow ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom  -jar /app.jar"]
