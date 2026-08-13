package com.github.ledsoft.jopa.util;

import cz.cvut.kbss.jopa.Persistence;
import cz.cvut.kbss.jopa.model.AttributeNode;
import cz.cvut.kbss.jopa.model.EntityGraph;
import cz.cvut.kbss.jopa.model.EntityGraphImpl;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.EntityManagerFactory;
import cz.cvut.kbss.jopa.model.JOPAPersistenceProperties;
import cz.cvut.kbss.jopa.model.JOPAPersistenceProvider;
import cz.cvut.kbss.jopa.model.Subgraph;
import cz.cvut.kbss.jopa.model.metamodel.EntityType;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    @Test
    void addAttributesToLoadAddsSpecifiedRootEntityAttributes() {
        final EntityType<Person> et = em.getMetamodel().entity(Person.class);
        final EntityGraph<Person> eg =
                new EntityGraphImpl<>(et, em.getMetamodel()::entity);
        // Note that the attributes would more likely be from a static metamodel, but we are not generating it here
        sut.addAttributesToLoad(eg, List.of(et.getDeclaredAttribute("givenName"),
                                            et.getDeclaredAttribute("familyName")));

        assertEquals(2, eg.getAttributeNodes().size());
        assertTrue(eg.getAttributeNodes().stream().anyMatch(an -> an.getAttributeName().equals("givenName")));
        assertTrue(eg.getAttributeNodes().stream().anyMatch(an -> an.getAttributeName().equals("familyName")));
    }

    @Test
    void addAttributesToLoadAddsSpecifiedSingularAttributesFromReferencedEntity() {
        final EntityType<Person> et = em.getMetamodel().entity(Person.class);
        final EntityType<OnlineAccount> etAccount = em.getMetamodel().entity(OnlineAccount.class);
        final EntityGraph<Person> eg = new EntityGraphImpl<>(et, em.getMetamodel()::entity);
        final Subgraph<?> accountSubgraph = eg.addSubgraph(et.getDeclaredAttribute("account"));
        sut.addAttributesToLoad(eg, List.of(etAccount.getDeclaredAttribute("accountName")));

        assertTrue(accountSubgraph.getAttributeNodes().stream()
                                  .anyMatch(an -> an.getAttributeName().equals("accountName")));
    }

    @Test
    void addAttributesToLoadAddsSpecifiedAttributesFromEntityReferencedViaCollection() {
        final EntityType<Organization> etOrg = em.getMetamodel().entity(Organization.class);
        final EntityType<Person> etPerson = em.getMetamodel().entity(Person.class);
        final EntityGraph<Organization> eg = new EntityGraphImpl<>(etOrg, em.getMetamodel()::entity);
        final Subgraph<?> personSubgraph = eg.addSubgraph(etOrg.getDeclaredAttribute("member"));
        sut.addAttributesToLoad(eg, List.of(etPerson.getDeclaredAttribute("givenName"),
                                            etPerson.getDeclaredAttribute("familyName"),
                                            etOrg.getDeclaredAttribute("name")));

        assertTrue(personSubgraph.getAttributeNodes().stream()
                                 .anyMatch(an -> an.getAttributeName().equals("givenName")));
        assertTrue(personSubgraph.getAttributeNodes().stream()
                                 .anyMatch(an -> an.getAttributeName().equals("familyName")));
        assertTrue(eg.getAttributeNodes().stream()
                     .anyMatch(an -> an.getAttributeName().equals("name")));
    }

    @Test
    void addAttributesToLoadAddsAttributesFromSuperclass() {
        final EntityType<User> et = em.getMetamodel().entity(User.class);
        final EntityGraph<User> eg = new EntityGraphImpl<>(et, em.getMetamodel()::entity);
        sut.addAttributesToLoad(eg, List.of(et.getDeclaredAttribute("username"),
                                            et.getAttribute("name")));

        assertEquals(2, eg.getAttributeNodes().size());
        assertTrue(eg.getAttributeNodes().stream().anyMatch(an -> an.getAttributeName().equals("username")));
        assertTrue(eg.getAttributeNodes().stream().anyMatch(an -> an.getAttributeName().equals("name")));
    }

    @Test
    void addAttributesToLoadAddsAttributesFromSubclass() {
        final EntityType<Person> et = em.getMetamodel().entity(Person.class);
        final EntityType<User> etUser = em.getMetamodel().entity(User.class);
        final EntityGraph<Person> eg = new EntityGraphImpl<>(et, em.getMetamodel()::entity);
        sut.addAttributesToLoad(eg, List.of(et.getDeclaredAttribute("name"),
                                            etUser.getDeclaredAttribute("username")));

        assertEquals(2, eg.getAttributeNodes().size());
        assertTrue(eg.getAttributeNodes().stream().anyMatch(an -> an.getAttributeName().equals("name")));
        assertTrue(eg.getAttributeNodes().stream().anyMatch(an -> an.getAttributeName().equals("username")));
    }

    @Test
    void addAttributesToLoadAddsAttributesFromSuperclassInSubgraph() {
        final EntityType<UserOrganization> etOrg = em.getMetamodel().entity(UserOrganization.class);
        final EntityType<User> etUser = em.getMetamodel().entity(User.class);
        final EntityGraph<UserOrganization> eg = new EntityGraphImpl<>(etOrg, em.getMetamodel()::entity);
        final Subgraph<?> userSubgraph = eg.addSubgraph(etOrg.getDeclaredAttribute("member"));
        sut.addAttributesToLoad(eg, List.of(etOrg.getDeclaredAttribute("name"), etUser.getAttribute("name"),
                                            etUser.getAttribute("username")));

        assertTrue(userSubgraph.getAttributeNodes().stream()
                               .anyMatch(an -> an.getAttributeName().equals("name")));
        assertTrue(userSubgraph.getAttributeNodes().stream()
                               .anyMatch(an -> an.getAttributeName().equals("username")));
        assertTrue(eg.getAttributeNodes().stream()
                     .anyMatch(an -> an.getAttributeName().equals("name")));
    }

    @Test
    void addAttributesToLoadAddsAttributesFromSubclassInSubgraph() {
        final EntityType<Organization> etOrg = em.getMetamodel().entity(Organization.class);
        final EntityType<Person> etPerson = em.getMetamodel().entity(Person.class);
        final EntityType<User> etUser = em.getMetamodel().entity(User.class);
        final EntityGraph<Organization> eg = new EntityGraphImpl<>(etOrg, em.getMetamodel()::entity);
        final Subgraph<?> userSubgraph = eg.addSubgraph(etOrg.getDeclaredAttribute("member"));
        sut.addAttributesToLoad(eg, List.of(etOrg.getDeclaredAttribute("name"),
                                            etPerson.getAttribute("name"),
                                            etUser.getAttribute("username")));

        assertTrue(userSubgraph.getAttributeNodes().stream()
                               .anyMatch(an -> an.getAttributeName().equals("name")));
        assertTrue(userSubgraph.getAttributeNodes().stream()
                               .anyMatch(an -> an.getAttributeName().equals("username")));
        assertTrue(eg.getAttributeNodes().stream()
                     .anyMatch(an -> an.getAttributeName().equals("name")));
    }

    @Test
    void addAttributesToLoadAddsSubgraphToEntityGraph() {
        final EntityType<Organization> etOrg = em.getMetamodel().entity(Organization.class);
        final EntityType<Person> etPerson = em.getMetamodel().entity(Person.class);
        final EntityGraph<Organization> eg = new EntityGraphImpl<>(etOrg, em.getMetamodel()::entity);
        sut.addAttributesToLoad(eg, List.of(etOrg.getDeclaredAttribute("member"),
                                            etPerson.getDeclaredAttribute("givenName"),
                                            etPerson.getDeclaredAttribute("familyName"),
                                            etOrg.getDeclaredAttribute("name")));

        final Optional<AttributeNode<?>> memberNode = eg.getAttributeNodes().stream()
                                                         .filter(an -> an.getAttributeName().equals("member")).
                                                         findFirst();
        assertTrue(memberNode.isPresent());
        assertEquals(1, memberNode.get().getSubgraphs().size());
        final Subgraph<?> userSubgraph = memberNode.get().getSubgraphs().get(Person.class);
        assertTrue(userSubgraph.getAttributeNodes().stream()
                               .anyMatch(an -> an.getAttributeName().equals("givenName")));
        assertTrue(userSubgraph.getAttributeNodes().stream()
                               .anyMatch(an -> an.getAttributeName().equals("familyName")));
    }

    @Test
    void addAttributesToLoadAddsSpecifiedSingularAttributesFromReferencedEntity_StaticMetamodel() {
        final EntityType<Person> et = em.getMetamodel().entity(Person.class);
        final EntityGraph<Person> eg = new EntityGraphImpl<>(et, em.getMetamodel()::entity);
        final Subgraph<?> accountSubgraph = eg.addSubgraph(Person_.account);
        sut.addAttributesToLoad(eg, List.of(OnlineAccount_.accountName));

        assertTrue(accountSubgraph.getAttributeNodes().stream()
                                  .anyMatch(an -> an.getAttributeName().equals("accountName")));
    }
}