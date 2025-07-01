package com.github.ledsoft.jopa.util;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.query.Query;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * Data utilities for JOPA-based applications.
 */
public class DataUtilities {

    private final EntityManager em;

    public DataUtilities(EntityManager em) {
        this.em = Objects.requireNonNull(em);
    }

    /**
     * Gets all triples that refer to the given object.
     * <p>
     * This includes inferred references.
     *
     * @param object Object of the references
     * @return List of triples
     */
    public List<Triple> getIncomingReferences(URI object) {
        Objects.requireNonNull(object);
        return getIncomingReferences(object, false);
    }

    private List<Triple> getIncomingReferences(URI object, boolean explicitOnly) {
        final Query query =
                em.createNativeQuery("SELECT ?subject ?predicate WHERE { ?subject ?predicate ?object . }")
                  .setParameter("object", object);
        if (explicitOnly) {
            query.setHint("cz.cvut.kbss.jopa.query.disableInference", true);
        }
        return query.getResultStream()
                    .map(row -> {
                        final Object[] resultRow = (Object[]) row;
                        return new Triple((URI) resultRow[0], (URI) resultRow[1], object);
                    })
                    .toList();
    }

    /**
     * Gets all triples that refer to the given object.
     * <p>
     * This includes only explicit references.
     *
     * @param object Object of the references
     * @return List of triples
     */
    public List<Triple> getExplicitIncomingReferences(URI object) {
        Objects.requireNonNull(object);
        return getIncomingReferences(object, true);
    }
}
