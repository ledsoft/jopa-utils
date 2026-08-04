package com.github.ledsoft.jopa.util;

import cz.cvut.kbss.jopa.model.AttributeNode;
import cz.cvut.kbss.jopa.model.EntityGraphImpl;
import cz.cvut.kbss.jopa.model.Subgraph;
import cz.cvut.kbss.jopa.model.metamodel.Attribute;
import cz.cvut.kbss.jopa.model.metamodel.EntityType;
import cz.cvut.kbss.jopa.model.metamodel.IdentifiableType;
import cz.cvut.kbss.jopa.model.metamodel.Metamodel;
import cz.cvut.kbss.jopa.model.metamodel.PluralAttribute;
import cz.cvut.kbss.jopa.utils.IdentifierTransformer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Adds attributes to an entity graph and its subgraphs.
 *
 * @param <T> Root entity type
 */
class EntityGraphAttributeAdder<T> {

    private final Metamodel metamodel;

    private final EntityGraphImpl<T> entityGraph;

    private final Map<IdentifiableType<?>, Set<Subgraph<?>>> subgraphsByDeclaringType;

    EntityGraphAttributeAdder(Metamodel metamodel, EntityGraphImpl<T> entityGraph) {
        this.metamodel = metamodel;
        this.entityGraph = entityGraph;
        this.subgraphsByDeclaringType = collectSubgraphs();
    }

    private Map<IdentifiableType<?>, Set<Subgraph<?>>> collectSubgraphs() {
        final Map<IdentifiableType<?>, Set<Subgraph<?>>> result = new HashMap<>();
        final EntityType<?> rootType = metamodel.entity(entityGraph.getClassType());
        collectSubgraphs(rootType, entityGraph.getAttributeNodes(), result);
        return result;
    }

    private void collectSubgraphs(EntityType<?> parentType, List<AttributeNode<?>> attributeNodes,
                                  Map<IdentifiableType<?>, Set<Subgraph<?>>> result) {
        for (AttributeNode<?> node : attributeNodes) {
            final Attribute<?, ?> attribute = parentType.getAttribute(node.getAttributeName());
            if (!attribute.isAssociation()) {
                continue;
            }
            Class<?> valueType = getBindableType(attribute);
            if (IdentifierTransformer.isValidIdentifierType(valueType)) {
                continue;
            }
            final Set<IdentifiableType<?>> typeHierarchy =
                    (Set<IdentifiableType<?>>) mapEntityTypeHierarchy(metamodel.entity(valueType));
            for (Subgraph<?> subgraph : node.getSubgraphs().values()) {
                typeHierarchy.forEach(type -> result.computeIfAbsent(type, k -> new HashSet<>()).add(subgraph));
                final EntityType<?> subgraphType = metamodel.entity(subgraph.getClassType());
                collectSubgraphs(subgraphType, subgraph.getAttributeNodes(), result);
            }
        }
    }

    private <X> Set<IdentifiableType<? super X>> mapEntityTypeHierarchy(EntityType<X> sourceEt) {
        final Set<IdentifiableType<? super X>> hierarchy = new HashSet<>();
        mapEntityTypeHierarchy(sourceEt, hierarchy);
        return hierarchy;
    }

    private <X> void mapEntityTypeHierarchy(IdentifiableType<X> sourceEt, Set<IdentifiableType<? super X>> hierarchy) {
        hierarchy.add(sourceEt);
        sourceEt.getSupertypes()
                .forEach(st -> mapEntityTypeHierarchy((IdentifiableType<X>) st, hierarchy));
    }

    /**
     * Adds the specified attributes to the subject entity graph (and its subgraphs) to be loaded by JOPA.
     * <p>
     * This method handles also entity type hierarchies, and in both ways. I.e., if the attribute to add is declared in
     * a superclass of the entity type of the entity graph (or any subgraph), as well as if the attribute to add is
     * declared in a subclass of the entity type of the entity graph (or any subgraph).
     *
     * @param attributes Attributes to add
     * @throws IllegalArgumentException If an attribute's declaring entity is not reachable from the root entity of the
     *                                  entity graph
     */
    void addAttributes(List<Attribute<?, ?>> attributes) {
        final EntityType<T> rootType = metamodel.entity(entityGraph.getClassType());
        final Set<IdentifiableType<? super T>> rootHierarchy = mapEntityTypeHierarchy(rootType);

        for (Attribute<?, ?> attribute : attributes) {
            final EntityType<?> attDeclaringType = (EntityType<?>) attribute.getDeclaringType();
            final Set<IdentifiableType<?>> declaringHierarchy =
                    (Set<IdentifiableType<?>>) mapEntityTypeHierarchy(attDeclaringType);
            if (rootHierarchy.contains(attDeclaringType) || mapEntityTypeHierarchy(attDeclaringType).contains(
                    rootType)) {
                entityGraph.addAttributeNodes((Attribute<T, ?>) attribute);
                if (attribute.isAssociation()) {
                    final Subgraph<?> sg = entityGraph.addSubgraph((Attribute<T, ?>) attribute);
                    registerAddedAttributeSubgraph(attribute, sg);
                }
            } else {
                final Optional<Set<Subgraph<?>>> matchingSubgraphs =
                        declaringHierarchy.stream().filter(subgraphsByDeclaringType::containsKey).findFirst()
                                          .map(subgraphsByDeclaringType::get);
                if (matchingSubgraphs.isPresent()) {
                    for (Subgraph<?> subgraph : matchingSubgraphs.get()) {
                        addAttributeToSubgraph(subgraph, attribute);
                    }
                } else {
                    throw new IllegalArgumentException(
                            "Attribute " + attribute + " is not reachable from the entity graph root.");
                }
            }
        }
    }

    private void registerAddedAttributeSubgraph(Attribute<?, ?> attribute, Subgraph<?> sg) {
        subgraphsByDeclaringType.computeIfAbsent(metamodel.entity(getBindableType(attribute)),
                                                 (et) -> new HashSet<>()).add(sg);
    }

    private static Class<?> getBindableType(Attribute<?, ?> attribute) {
        return attribute.isCollection() ? ((PluralAttribute) attribute).getBindableJavaType() :
               attribute.getJavaType();
    }

    @SuppressWarnings("unchecked")
    private <X> void addAttributeToSubgraph(Subgraph<X> subgraph, Attribute<?, ?> attribute) {
        subgraph.addAttributeNodes((Attribute<X, ?>) attribute);
        if (attribute.isAssociation()) {
            final Subgraph<?> sg = subgraph.addSubgraph((Attribute<X, ?>) attribute);
            registerAddedAttributeSubgraph(attribute, sg);
        }
    }
}
