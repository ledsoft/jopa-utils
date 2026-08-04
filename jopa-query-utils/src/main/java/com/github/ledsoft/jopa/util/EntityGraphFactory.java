package com.github.ledsoft.jopa.util;

import cz.cvut.kbss.jopa.model.AttributeNode;
import cz.cvut.kbss.jopa.model.EntityGraph;
import cz.cvut.kbss.jopa.model.EntityGraphImpl;
import cz.cvut.kbss.jopa.model.Subgraph;
import cz.cvut.kbss.jopa.model.metamodel.Attribute;
import cz.cvut.kbss.jopa.model.metamodel.EntityType;
import cz.cvut.kbss.jopa.model.metamodel.IdentifiableType;
import cz.cvut.kbss.jopa.model.metamodel.Metamodel;
import cz.cvut.kbss.jopa.model.metamodel.PluralAttribute;
import cz.cvut.kbss.jopa.model.query.criteria.CriteriaQuery;
import cz.cvut.kbss.jopa.model.query.criteria.Expression;
import cz.cvut.kbss.jopa.query.criteria.PathImpl;
import cz.cvut.kbss.jopa.query.criteria.expressions.AbstractComparisonExpression;
import cz.cvut.kbss.jopa.query.criteria.expressions.AbstractFunctionExpression;
import cz.cvut.kbss.jopa.utils.IdentifierTransformer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Utility class for creating entity graphs.
 */
public class EntityGraphFactory {

    private final Metamodel metamodel;

    public EntityGraphFactory(Metamodel metamodel) {
        this.metamodel = metamodel;
    }

    /**
     * Creates an entity graph based on the given criteria query.
     * <p>
     * It adds subgraphs corresponding to path expressions traversing relationships to other entities from the root
     * (recursively).
     *
     * @param query Query based on which to create the entity graph
     * @param <T>   Selected entity type
     * @return Entity graph based on the given query
     */
    public <T> EntityGraph<T> createEntityGraph(CriteriaQuery<T> query) {
        Objects.requireNonNull(query);
        assert query.getRoots().size() == 1;
        final EntityGraph<T> result = new EntityGraphImpl<>((EntityType<T>) query.getRoots().iterator().next()
                                                                                 .getModel(), metamodel::entity);
        for (Expression<Boolean> expr : query.getRestriction().getExpressions()) {
            recursivelyProcessExpression(expr, result);
        }
        return result;
    }

    private <T> void recursivelyProcessExpression(Expression<?> expr, EntityGraph<T> graph) {
        if (expr instanceof AbstractComparisonExpression comparisonExpr) {
            recursivelyProcessExpression(comparisonExpr.getLeft(), graph);
            recursivelyProcessExpression(comparisonExpr.getRight(), graph);
        } else if (expr instanceof AbstractFunctionExpression<?> functionExpr) {
            functionExpr.getArguments().forEach(ape -> recursivelyProcessExpression(ape, graph));
        } else if (expr instanceof PathImpl<?> pathExpr) {
            assert pathExpr.getAttribute() instanceof Attribute;
            final List<PathImpl<?>> path = new ArrayList<>();
            PathImpl<?> currentPath = pathExpr;
            while (currentPath != null) {
                path.add(0, currentPath);
                currentPath = currentPath.getParentPath() instanceof PathImpl<?> parent ? parent : null;
            }
            Subgraph currentGraph = null;
            for (int i = 0; i < path.size(); i++) {
                final Attribute attribute = (Attribute) path.get(i).getAttribute();
                if (i == path.size() - 1) {
                    if (currentGraph == null) {
                        graph.addAttributeNodes(attribute);
                    } else {
                        currentGraph.addAttributeNodes(attribute);
                    }
                } else if (currentGraph == null) {
                    currentGraph = graph.addSubgraph(attribute);
                } else {
                    currentGraph = currentGraph.addSubgraph(attribute);
                }
            }
        }
    }

    /**
     * Adds the specified attributes to the specified entity graph (and its subgraphs) to be loaded by JOPA.
     * <p>
     * This method handles also entity type hierarchies, and in both ways. I.e., if the attribute to add is declared in
     * a superclass of the entity type of the entity graph (or any subgraph), as well as if the attribute to add is
     * declared in a subclass of the entity type of the entity graph (or any subgraph).
     *
     * @param entityGraph Entity graph to enhance
     * @param attributes  Attributes to add
     * @param <T>         Root entity type
     * @throws IllegalArgumentException If an attribute's declaring entity is not reachable from the root entity of the
     *                                  entity graph
     */
    public <T> void addAttributesToLoad(EntityGraph<T> entityGraph, List<Attribute<?, ?>> attributes) {
        Objects.requireNonNull(entityGraph);
        Objects.requireNonNull(attributes);
        final EntityGraphImpl<T> eg = (EntityGraphImpl<T>) entityGraph;
        final Map<IdentifiableType<?>, Set<Subgraph<?>>> subgraphsByDeclaringType = collectSubgraphs(eg);
        final EntityType<T> rootType = metamodel.entity(eg.getClassType());
        final Set<IdentifiableType<? super T>> rootHierarchy = mapEntityTypeHierarchy(rootType);

        // TODO This will not support attributes of entities that are not already declared in the entity graph even though they are reachable from the root
        for (Attribute<?, ?> attribute : attributes) {
            final EntityType<?> attDeclaringType = (EntityType<?>) attribute.getDeclaringType();
            final Set<IdentifiableType<?>> declaringHierarchy =
                    (Set<IdentifiableType<?>>) mapEntityTypeHierarchy(attDeclaringType);
            if (rootHierarchy.contains(attDeclaringType) || mapEntityTypeHierarchy(attDeclaringType).contains(
                    rootType)) {
                eg.addAttributeNodes((Attribute<T, ?>) attribute);
            } else {
                final Optional<Set<Subgraph<?>>> matchingType =
                        declaringHierarchy.stream().filter(subgraphsByDeclaringType::containsKey).findFirst()
                                          .map(subgraphsByDeclaringType::get);
                if (matchingType.isPresent()) {
                    for (Subgraph<?> subgraph : matchingType.get()) {
                        addAttributeToSubgraph(subgraph, attribute);
                    }
                } else {
                    throw new IllegalArgumentException(
                            "Attribute " + attribute + " is not reachable from the entity graph root.");
                }
            }
        }
    }

    private <T> Set<IdentifiableType<? super T>> mapEntityTypeHierarchy(EntityType<T> sourceEt) {
        final Set<IdentifiableType<? super T>> hierarchy = new HashSet<>();
        mapEntityTypeHierarchy(sourceEt, hierarchy);
        return hierarchy;
    }

    private <T> void mapEntityTypeHierarchy(IdentifiableType<T> sourceEt, Set<IdentifiableType<? super T>> hierarchy) {
        hierarchy.add(sourceEt);
        sourceEt.getSupertypes()
                .forEach(st -> mapEntityTypeHierarchy((IdentifiableType<T>) st, hierarchy));
    }

    private Map<IdentifiableType<?>, Set<Subgraph<?>>> collectSubgraphs(EntityGraphImpl<?> graph) {
        final Map<IdentifiableType<?>, Set<Subgraph<?>>> result = new HashMap<>();
        final EntityType<?> rootType = metamodel.entity(graph.getClassType());
        collectSubgraphs(rootType, graph.getAttributeNodes(), result);
        return result;
    }

    private void collectSubgraphs(EntityType<?> parentType, List<AttributeNode<?>> attributeNodes,
                                  Map<IdentifiableType<?>, Set<Subgraph<?>>> result) {
        for (AttributeNode<?> node : attributeNodes) {
            final Attribute<?, ?> attribute = parentType.getAttribute(node.getAttributeName());
            if (!attribute.isAssociation()) {
                continue;
            }
            Class<?> valueType = attribute.isCollection() ? ((PluralAttribute) attribute).getBindableJavaType() :
                                 attribute.getJavaType();
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

    @SuppressWarnings("unchecked")
    private static <X> void addAttributeToSubgraph(Subgraph<X> subgraph, Attribute<?, ?> attribute) {
        subgraph.addAttributeNodes((Attribute<X, ?>) attribute);
    }
}
