# JOPA Utils

Utilities for projects using the [Java OWL Persistence API (JOPA)](https://github.com/kbss-cvut/jopa).

## Modules

The following modules are available in the project:

### Spring Boot Loader

Spring Boot loader for JOPA. It is useful when a Spring Boot project has entity classes declared in a separate module that is imported as a dependency. It provides a custom
classpath scanner (`BootAwareClasspathScanner`) which is able to detect entity classes in nested JAR files in a Spring Boot JAR.

To use it, register the custom classpath scanner in your JOPA persistence unit setup:

```java
import com.github.ledsoft.jopa.loader.BootAwareClasspathScanner;
import java.util.Map;

Map<String, String> props = new HashMap<>();
// Other configuration, such as scanned package
props.put(JOPAPersistenceProperties.CLASSPATH_SCANNER_CLASS, BootAwareClasspathScanner.class.getName());
```

Maven dependency:
```xml
<dependency>
    <groupId>com.github.ledsoft</groupId>
    <artifactId>jopa-spring-boot-loader</artifactId>
</dependency>
```

### Data Utilities

Utilities for working with JOPA data.

Example of getting incoming references of an entity:

```java
EntityManager em = // get EntityManager
DataUtilities dataUtilities = new DataUtilities(em);

List<Triple> triples = dataUtilities.getIncomingReferences(entityId);
```

Maven dependency:
```xml
<dependency>
    <groupId>com.github.ledsoft</groupId>
    <artifactId>jopa-data-utils</artifactId>
</dependency>
```

## Links

Useful links:

- [JOPA](https://github.com/kbss-cvut/jopa) - the JOPA library
- [JOPA examples](https://github.com/kbss-cvut/jopa-examples) - example projects using JOPA
- [JOPA Spring transactions](https://github.com/ledsoft/jopa-spring-transaction) - integration of JOPA with Spring declarative transaction management

## License

MIT
