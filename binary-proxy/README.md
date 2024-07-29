# jms-api-gateway
Rest API gateway for microservices with Kafka

Advantages:

1. No custom code for JMS layer. Only official well known kafka libraries.
   
2. Fully reactive, non-blocking streams with exceptional performance.
   Even with testcontainers kafka on local machine gateway is able to survive
   load up to 150 requests per minute.
   
3. The "module" package contains special java classes that actually present isolated microservices
   (something similar to OSGI concept):
   - every module can be build/dockerized/deployed separately by means of dedicated gradle plugin;
   - every module consumes messages from one topic;
   - every module is an extension of PayloadProcessor module that encapsulate all boiler plated code,
     developer actually is responsible for implementation of business operations only, 
     knowledge about Kafka/Webflux is not required for implementation of new features.
    
4. All source code in one place, no need in shared libraries, no dependency update hell. 
   If some core functionality was changed you should just run pipeline that automatically 
   rebuild/redeploy all microservices.
   
5. Consistent versioning for all microservices X.Y.Z:
    X - major version, stored in root pom.xml, the same for all microservices, 
        updated if core implementation was changed only;
    Y - minor version, stored in module pom.xml, different modules can have different versions;
    Z - cross repository build number.

6. Command Query Responsibility Segregation(CQRS) pattern is implemented 
   that can help solve storage performance issues.
   
7. Classic message bus for all microservices is implemented: 
   - Event message can travel from microservice to microservice and be validated/enriched/stored on every step,
     one command can cover several requests, several microservices can be a result contributor;
   - Event message contains declarative route description and payload typification.
   - Can be a basis for SAGA pattern or two phases commit implementation.
    
8. True event sourcing architecture. All events are grouped around domain entities, so we have a simple way to add
   event sourcing DB for monitoring and statistic to replace Mixpanel.
   
9. A proper error handling is implemented. With isolated microservices much easier 
   to find/fix a root cause of any issue.
   
10. All vendor specific code is isolated, so Kafka can be easily replaced with any pub/sub system(Redis, ArtemisMQ).

11. Gateway contains only two rest endpoint for all command/query, so we can easily replace http 
    with any protocol to have more efficient backend for some frontend.
    
12. Because of all the code is placed in one repository we can create end-to-end integration tests 
    with Testcontainers and completely eliminate manual testing necessity.

Disadvantages:

1. Payload size limitation for queries (100kb without Kafka performance penalty), so pagination is must have 
