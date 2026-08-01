package com.github.ledsoft.jopa.util;

import cz.cvut.kbss.jopa.model.AttributeNode;
import cz.cvut.kbss.jopa.model.EntityGraph;
import cz.cvut.kbss.jopa.model.EntityGraphImpl;
import cz.cvut.kbss.jopa.model.Subgraph;
import cz.cvut.kbss.jopa.model.metamodel.Attribute;
import cz.cvut.kbss.jopa.model.metamodel.EntityType;
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
        final Map<EntityType<?>, Set<Subgraph<?>>> subgraphsByDeclaringType = collectSubgraphs(eg);
        final EntityType<T> rootType = metamodel.entity(eg.getClassType());

        for (Attribute<?, ?> attribute : attributes) {
            // TODO What about entity class hierarchies (since we are using getDeclaringType)
            // TODO This will not support attributes of entities that are not already declared in the entity graph even though they are reachable from the root
            final EntityType<?> declaringType = (EntityType<?>) attribute.getDeclaringType();
            if (declaringType.getJavaType().equals(rootType.getJavaType())) {
                eg.addAttributeNodes((Attribute<T, ?>) attribute);
            } else if (subgraphsByDeclaringType.containsKey(declaringType)) {
                for (Subgraph<?> subgraph : subgraphsByDeclaringType.get(declaringType)) {
                    addAttributeToSubgraph(subgraph, attribute);
                }
            } else {
                throw new IllegalArgumentException(
                        "Attribute " + attribute + " is not reachable from the entity graph root.");
            }
        }
    }

    private Map<EntityType<?>, Set<Subgraph<?>>> collectSubgraphs(EntityGraphImpl<?> graph) {
        final Map<EntityType<?>, Set<Subgraph<?>>> result = new HashMap<>();
        final EntityType<?> rootType = metamodel.entity(graph.getClassType());
        collectSubgraphs(rootType, graph.getAttributeNodes(), result);
        return result;
    }

    private void collectSubgraphs(EntityType<?> parentType, List<AttributeNode<?>> attributeNodes,
                                  Map<EntityType<?>, Set<Subgraph<?>>> result) {
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
            final EntityType<?> declaringType = metamodel.entity(valueType);
            for (Subgraph<?> subgraph : node.getSubgraphs().values()) {
                result.computeIfAbsent(declaringType, k -> new HashSet<>()).add(subgraph);
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
