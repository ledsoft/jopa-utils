package com.github.ledsoft.jopa.plugin;

import cz.cvut.kbss.jopa.Persistence;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.EntityManagerFactory;
import cz.cvut.kbss.jopa.model.JOPAPersistenceProperties;
import cz.cvut.kbss.jopa.model.JOPAPersistenceProvider;
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.jopa.vocabulary.OWL;
import cz.cvut.kbss.ontodriver.rdf4j.Rdf4jDataSource;
import cz.cvut.kbss.ontodriver.rdf4j.config.Rdf4jOntoDriverProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassHierarchyPluginTest {

    @Test
    void afterPersistenceUnitCreatedInsertsHierarchyStatementsIntoDefaultContext() {
        try (final EntityManagerFactory emf = Persistence.createEntityManagerFactory("testPU",
                                                                                     commonPersistenceProps())) {
            try (final EntityManager em = emf.createEntityManager()) {
                final TypedQuery<URI> query =
                        em.createNativeQuery("SELECT ?parent WHERE { ?x rdfs:subClassOf ?parent }", URI.class);
                List<URI> parents = query.setParameter("x", URI.create("http://example.com/jopa-plugin/subclass"))
                                         .getResultList();
                assertEquals(List.of(URI.create(OWL.THING)), parents);

                parents = query.setParameter("x", URI.create(OWL.THING))
                               .getResultList();
                assertTrue(parents.isEmpty());
            }
        }
    }

    private static Map<String, String> commonPersistenceProps() {
        return Map.of(
                JOPAPersistenceProperties.ONTOLOGY_PHYSICAL_URI_KEY, "mem:test",
                JOPAPersistenceProperties.SCAN_PACKAGE, "com.github.ledsoft.jopa.plugin.environment",
                JOPAPersistenceProperties.JPA_PERSISTENCE_PROVIDER, JOPAPersistenceProvider.class.getName(),
                JOPAPersistenceProperties.DATA_SOURCE_CLASS, Rdf4jDataSource.class.getName(),
                Rdf4jOntoDriverProperties.USE_VOLATILE_STORAGE, "true",
                JOPAPersistenceProperties.PERSISTENCE_UNIT_LIFECYCLE_PLUGINS, ClassHierarchyPlugin.class.getName()
        );
    }

    @Test
    void afterPersistenceUnitCreatedInsertsHierarchyStatementsIntoConfiguredContext() {
        final String context = "http://example.com/jopa-plugin/context";
        final Map<String, String> persistenceConfig = new HashMap<>(commonPersistenceProps());
        persistenceConfig.put(ClassHierarchyPlugin.TARGET_CONTEXT_CONFIG, context);
        try (final EntityManagerFactory emf = Persistence.createEntityManagerFactory("testPU", persistenceConfig)) {
            try (final EntityManager em = emf.createEntityManager()) {
                final TypedQuery<URI> query =
                        em.createNativeQuery("SELECT ?parent WHERE { GRAPH ?g { ?x rdfs:subClassOf ?parent } }",
                                             URI.class);
                List<URI> parents = query.setParameter("x", URI.create("http://example.com/jopa-plugin/subclass"))
                                         .setParameter("g", URI.create(context))
                                         .getResultList();
                assertEquals(List.of(URI.create(OWL.THING)), parents);

                parents = query.setParameter("x", URI.create(OWL.THING))
                               .setParameter("g", URI.create(context))
                               .getResultList();
                assertTrue(parents.isEmpty());
            }
        }
    }

    @Test
    void afterPersistenceUnitCreatedInsertsRdfsClassStatementsWhenConfiguredTo() {
        final Map<String, String> persistenceConfig = new HashMap<>(commonPersistenceProps());
        persistenceConfig.put(ClassHierarchyPlugin.GENERATE_RDFS_CLASS_CONFIG, "true");
        try (final EntityManagerFactory emf = Persistence.createEntityManagerFactory("testPU",
                                                                                     persistenceConfig)) {
            try (final EntityManager em = emf.createEntityManager()) {
                final TypedQuery<Boolean> query = em.createNativeQuery("ASK WHERE { ?x a rdfs:Class }", Boolean.class);
                assertTrue(query.setParameter("x", URI.create(OWL.THING)).getSingleResult());
                assertTrue(query.setParameter("x", URI.create("http://example.com/jopa-plugin/subclass"))
                                .getSingleResult());
            }
        }
    }

    @Test
    void afterPersistenceUnitCreatedInsertsOwlClassStatementsWhenConfiguredTo() {
        final Map<String, String> persistenceConfig = new HashMap<>(commonPersistenceProps());
        persistenceConfig.put(ClassHierarchyPlugin.GENERATE_OWL_CLASS_CONFIG, "true");
        try (final EntityManagerFactory emf = Persistence.createEntityManagerFactory("testPU",
                                                                                     persistenceConfig)) {
            try (final EntityManager em = emf.createEntityManager()) {
                final TypedQuery<Boolean> query = em.createNativeQuery("ASK WHERE { ?x a owl:Class }", Boolean.class);
                assertTrue(query.setParameter("x", URI.create(OWL.THING)).getSingleResult());
                assertTrue(query.setParameter("x", URI.create("http://example.com/jopa-plugin/subclass"))
                                .getSingleResult());
            }
        }
    }
}