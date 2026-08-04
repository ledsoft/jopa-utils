package com.github.ledsoft.jopa.util;

import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;

import java.net.URI;
import java.util.Set;

@OWLClass(iri = "foaf:organization")
public class UserOrganization {

    @Id(generated = true)
    private URI uri;

    @OWLDataProperty(iri = "foaf:name")
    private String name;

    @OWLObjectProperty(iri = "foaf:homepage")
    private Set<URI> homepage;

    @OWLObjectProperty(iri = "foaf:member")
    private Set<User> member;

    @OWLObjectProperty(iri = "foaf:fundedBy")
    private Set<URI> fundedBy;

    @OWLObjectProperty(iri = "foaf:depiction")
    private Set<URI> depiction;
}
