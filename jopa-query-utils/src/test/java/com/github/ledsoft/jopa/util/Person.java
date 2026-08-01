package com.github.ledsoft.jopa.util;

import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;

import java.net.URI;
import java.util.Set;

@OWLClass(iri = "foaf:Person")
public class Person {

    @Id(generated = true)
    private URI uri;

    @OWLDataProperty(iri = "foaf:name")
    private String name;

    @OWLDataProperty(iri = "foaf:givenName")
    private String givenName;

    @OWLDataProperty(iri = "foaf:familyName")
    private String familyName;

    @OWLDataProperty(iri = "foaf:nick")
    private Set<String> nick;

    @OWLDataProperty(iri = "foaf:title")
    private String title;

    @OWLObjectProperty(iri = "foaf:mbox")
    private Set<URI> mbox;

    @OWLDataProperty(iri = "foaf:age")
    private Integer age;

    @OWLObjectProperty(iri = "foaf:homepage")
    private Set<URI> homepage;

    @OWLObjectProperty(iri = "foaf:depiction")
    private Set<URI> depiction;

    @OWLObjectProperty(iri = "foaf:knows")
    private Set<Person> knows;

    @OWLObjectProperty(iri = "foaf:account")
    private OnlineAccount account;
}
