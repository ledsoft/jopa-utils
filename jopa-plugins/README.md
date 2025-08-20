# JOPA Plugins

This project contains utility plugins using the JOPA Plugin API.

## ClassHierarchyPlugin

Executed after the persistence unit is created, this plugin scans the available entity classes to discover hierarchies.
It then inserts `rdfs:subClassOf` statements representing the discovered hierarchies into the underlying repository.

### Configuration

| Property                                                          | Description                                                                                                                            |
|:------------------------------------------------------------------|:---------------------------------------------------------------------------------------------------------------------------------------|
| `com.github.ledsoft.jopa.plugin.classHierarchy.targetContext`     | Repository context (RDF named graph) int which the model statements should be inserted. If not specified, the default context is used. |
| `com.github.ledsoft.jopa.plugin.classHierarchy.generateOwlClass`  | Whether to also generate OWL class statements (`?x a owl:Class`). Takes precedence over RDFS class statements config (see below).      |
| `com.github.ledsoft.jopa.plugin.classHierarchy.generateRdfsClass` | Whether to also generate RDFS class statements (`?x a rdfs:Class`).                                                                    |                                                                     |

All the configuration properties are declared as public static fields on the `CLassHierarchyPlugin` class.
