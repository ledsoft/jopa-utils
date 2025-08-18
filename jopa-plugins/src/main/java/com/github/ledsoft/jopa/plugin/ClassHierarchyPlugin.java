package com.github.ledsoft.jopa.plugin;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.metamodel.EntityType;
import cz.cvut.kbss.jopa.model.metamodel.Type;
import cz.cvut.kbss.jopa.plugin.PersistenceUnitLifecyclePlugin;
import cz.cvut.kbss.jopa.utils.IdentifierTransformer;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Propagates class hierarchies discovered in JOPA object model into the underlying repository as {@literal subClassOf}
 * statements.
 */
public class ClassHierarchyPlugin implements PersistenceUnitLifecyclePlugin {

    private static final Logger LOG = LoggerFactory.getLogger(ClassHierarchyPlugin.class);

    /**
     * Repository context (named graph) IRI configuration.
     * <p>
     * IRI of the repository context into which the class hierarchies should be inserted.
     */
    public static final String TARGET_CONTEXT_CONFIG = "com.github.ledsoft.jopa.plugin.classHierarchy.targetContext";

    @Override
    public void afterPersistenceUnitCreated(EntityManager em) {
        Objects.requireNonNull(em);

        LOG.debug("{} executing.", ClassHierarchyPlugin.class.getSimpleName());
        final Map<String, Set<String>> hierarchies = resolveClassHierarchies(em);
        if (hierarchies.isEmpty()) {
            return;
        }
        final String insertBody = constructSubClassOfStatements(hierarchies);
        insertHierarchyStatements(insertBody, em);
        LOG.debug("{} finished.", ClassHierarchyPlugin.class.getSimpleName());
    }

    private static Map<String, Set<String>> resolveClassHierarchies(EntityManager em) {
        LOG.trace("Resolving entity class hierarchies from metamodel.");
        final Map<String, Set<String>> hierarchies = new HashMap<>();
        for (EntityType<?> et : em.getMetamodel().getEntities()) {
            if (et.getSupertypes().isEmpty()) {
                LOG.trace("{} ({}): 0 declared supertypes", et.getName(), IdentifierTransformer.stringifyIri(et.getIRI()));
                continue;
            }
            final Set<String> supertypes = hierarchies.computeIfAbsent(et.getIRI().toString(), k -> new HashSet<>());
            et.getSupertypes().stream().filter(st -> st.getPersistenceType() == Type.PersistenceType.ENTITY)
              .forEach(st -> {
                  final EntityType<?> entityType = (EntityType<?>) st;
                  supertypes.add(entityType.getIRI().toString());
              });
            LOG.trace("{} ({}): {} declared supertype(s) - {}", et.getName(), IdentifierTransformer.stringifyIri(et.getIRI()), supertypes.size(), supertypes);
        }
        return hierarchies;
    }

    private static String constructSubClassOfStatements(Map<String, Set<String>> hierarchies) {
        final StringBuilder sb = new StringBuilder();
        hierarchies.forEach((subject, parents) -> {
            final String subjectIri = IdentifierTransformer.stringifyIri(subject);
            parents.forEach(p -> sb.append('\n').append(subjectIri).append(" rdfs:subClassOf ")
                                   .append(IdentifierTransformer.stringifyIri(p)).append(" . "));
        });
        return sb.toString();
    }

    private void insertHierarchyStatements(String insertBody, EntityManager em) {
        final String targetContext = em.getProperties().getOrDefault(TARGET_CONTEXT_CONFIG, "").toString();
        String queryString = "PREFIX rdfs: " + IdentifierTransformer.stringifyIri(RDFS.NAMESPACE) + "\n\n";
        if (!targetContext.isBlank()) {
            LOG.trace("Inserting hierarchy statements into context {}.", IdentifierTransformer.stringifyIri(targetContext));
            queryString += "INSERT DATA {\n GRAPH " + IdentifierTransformer.stringifyIri(targetContext) + " {" + insertBody + "\n}\n}";
        } else {
            LOG.trace("Inserting hierarchy statements into the default context.");
            queryString += "INSERT DATA {" + insertBody + "\n}";
        }
        em.getTransaction().begin();
        em.createNativeQuery(queryString).executeUpdate();
        em.getTransaction().commit();
    }
}
