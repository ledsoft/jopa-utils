package com.github.ledsoft.jopa.util;

import cz.cvut.kbss.jopa.Persistence;
import cz.cvut.kbss.jopa.model.AttributeNode;
import cz.cvut.kbss.jopa.model.EntityGraph;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.EntityManagerFactory;
import cz.cvut.kbss.jopa.model.JOPAPersistenceProperties;
import cz.cvut.kbss.jopa.model.JOPAPersistenceProvider;
import cz.cvut.kbss.jopa.model.Subgraph;
import cz.cvut.kbss.jopa.model.query.criteria.CriteriaBuilder;
import cz.cvut.kbss.jopa.model.query.criteria.CriteriaQuery;
import cz.cvut.kbss.jopa.model.query.criteria.Root;
import cz.cvut.kbss.ontodriver.rdf4j.Rdf4jDataSource;
import cz.cvut.kbss.ontodriver.rdf4j.config.Rdf4jOntoDriverProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityGraphFactoryTest {

    private static EntityManagerFactory emf;

    private EntityManager em;

    private EntityGraphFactory sut;

    @BeforeAll
    static void setUpBeforeAll() {
        emf = Persistence.createEntityManagerFactory("test", Map.of(
                JOPAPersistenceProperties.JPA_PERSISTENCE_PROVIDER, JOPAPersistenceProvider.class.getName(),
                JOPAPersistenceProperties.SCAN_PACKAGE, "com.github.ledsoft.jopa.util",
                JOPAPersistenceProperties.DATA_SOURCE_CLASS, Rdf4jDataSource.class.getName(),
                JOPAPersistenceProperties.ONTOLOGY_PHYSICAL_URI_KEY, "mem:test",
                Rdf4jOntoDriverProperties.USE_VOLATILE_STORAGE, "true"
        ));
    }

    @AfterAll
    static void tearDownAfterAll() {
        emf.close();
    }

    @BeforeEach
    void setUp() {
        this.em = emf.createEntityManager();
        this.sut = new EntityGraphFactory(em.getMetamodel());
    }

    @AfterEach
    void tearDown() {
        em.close();
    }

    @Test
    void createEntityGraphWithRootAndSingleAttribute() {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Person> query = cb.createQuery(Person.class);
        Root<Person> root = query.from(Person.class);
        query.select(root).where(cb.greaterThan(root.getAttr("age"), 18).not());


        final EntityGraph<Person> result = sut.createEntityGraph(query);
        assertEquals(1, result.getAttributeNodes().size());
        assertEquals("age", result.getAttributeNodes().get(0).getAttributeName());
    }

    @Test
    void createEntityGraphWithRootAndSingularAttributeSubgraphTraversal() {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Person> query = cb.createQuery(Person.class);
        Root<Person> root = query.from(Person.class);
        query.select(root).distinct()
             .where(cb.equal(root.getAttr("account").getAttr("accountName"), ""));

        final EntityGraph<Person> result = sut.createEntityGraph(query);
        assertEquals(1, result.getAttributeNodes().size());
        final AttributeNode<OnlineAccount> an = (AttributeNode<OnlineAccount>) result.getAttributeNodes().get(0);
        assertEquals("account", an.getAttributeName());
        assertTrue(an.getSubgraphs().containsKey(OnlineAccount.class));
        final Subgraph<OnlineAccount> sg = an.getSubgraphs().get(OnlineAccount.class);
        assertEquals(1, sg.getAttributeNodes().size());
        assertEquals("accountName", sg.getAttributeNodes().get(0).getAttributeName());
    }
}