# JOPA Plugins

This project contains utility plugins using the JOPA Plugin API.

## ClassHierarchyPlugin

Executed after the persistence unit is created, this plugin scans the available entity classes to discover hierarchies.
It then inserts `rdfs:subClassOf` statements representing the discovered hierarchies into the underlying repository.

### Configuration

`com.github.ledsoft.jopa.plugin.classHierarchy.targetContext` can be used to specify the repository context (RDF named
graph) into which the hierarchy statements should be inserted. If not set, they are added to the default context.