# API Gateway

##Config-server
> **⚠ client-proxy does not use config-server!**

This project contains 2 sub-projects, client-proxy and client-users. 
The first project, client-proxy, does not use the config server because of the risk of overriding the proxy configuration.
The client-users project still uses config server.

## Client Proxy

To set up the client-proxy for Gatling tests, run the application with the following Spring profiles:
- local
- gatling

In IntelliJ you can change "Active Profiles" in the run configuration to "gatling,local"


To run Gatling tests, set the following environment variables:
- TARGET_URL (or targetUrl JVM argument) (the base URL of the proxy, e.g. http://localhost:8090/client-proxy)
- LOCAL_ADDRESS (optional) (the host's IP which will be used to set the source IP in the request, no default provided)
- CLIENT_ID (or clientId JVM argument) (The client ID to use to create the request token)
- JWS_KEY_NAME (or jwsKeyName JVM argument) (The name of the private key that will be used for the JWS when creating the request token)
- USERS_PER_SECOND (optional) (the amount of users per second for the Gatling tests)
- DURATION_SECONDS (optional) (the duration in seconds for the Gatling tests)
- SLOW_BACKEND_SLEEP (optional) (the time in milliseconds the stub service needs to wait in the "slow backend" simulation, default is 750)
- RESPONSE_SIZE_BYTES (optional) (the size of the response in bytes that the stub service will return)
- gatling.http.ssl.keyStore.password JVM system variable (must contain the password for the keyStore, the JWS private key is expected to have the same password)


Client ID on ACC: 5aeafb70-6e5e-4cf5-a13e-3023846fc928
JWS key name for ACC = yolt-acc-client-jws-key


Some information concerning LOCAL_ADDRESS:
- The LOCAL_ADDRESS environment variable must be set if the tests are failing about 50% of the time due to network-related errors.
- Then Gatling is probably doing something wrong when setting the source IP (mixing IPv6 and IPv4 or something).
- When running locally, setting this to 127.0.0.1 should resolve that.


Then run the "gatlingRun" Gradle task

This task runs the Gatling simulations sequentially. The simulations that need to be run are configured in build.gradle in the gatling section.  
You can run a single simulation by executing the task gatlingRun-<fully qualified class name>, e.g. gatlingRun-FastRespondingBackendSimulation.


This project includes a keyStore containing the client certificate necessary to connect to acceptance. You need to provide the password
for this keyStore when running the Gatling tests against https://acc-api.yolt.com by setting the gatling.http.ssl.keyStore.password property.  
Example: `gradlew gatlingRun -DtargetUrl=https://acc-api.yolt.com/client-proxy -Dgatling.http.ssl.keyStore.password=my-password`

## Client users
Used for managing client-users. This project has the access to the database wher the client-users are stored.

## Context diagram
![context diagram](https://git.yolt.io/pages/backend-tools/yolt-architecture-diagram/downloaded-architecture-diagrams/client-gateway.puml.svg?job=build)  
[source](https://git.yolt.io/backend-tools/yolt-architecture-diagram/)
