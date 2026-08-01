package com.github.ledsoft.jopa.util;

import cz.cvut.kbss.jopa.model.EntityGraph;
import cz.cvut.kbss.jopa.model.EntityGraphImpl;
import cz.cvut.kbss.jopa.model.Subgraph;
import cz.cvut.kbss.jopa.model.metamodel.Attribute;
import cz.cvut.kbss.jopa.model.metamodel.EntityType;
import cz.cvut.kbss.jopa.model.metamodel.Metamodel;
import cz.cvut.kbss.jopa.model.query.criteria.CriteriaQuery;
import cz.cvut.kbss.jopa.model.query.criteria.Expression;
import cz.cvut.kbss.jopa.query.criteria.PathImpl;
import cz.cvut.kbss.jopa.query.criteria.expressions.AbstractComparisonExpression;
import cz.cvut.kbss.jopa.query.criteria.expressions.AbstractFunctionExpression;

import java.util.ArrayList;
import java.util.List;

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
}
