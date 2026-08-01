package com.github.ledsoft.jopa.util;

import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;

import java.net.URI;

@OWLClass(iri = "foaf:OnlineAccount")
public class OnlineAccount {

    @Id(generated = true)
    private URI uri;

    @OWLDataProperty(iri = "foaf:accountName")
    private String accountName;

    @OWLObjectProperty(iri = "foaf:accountServiceHomepage")
    private URI accountServiceHomepage;
}
