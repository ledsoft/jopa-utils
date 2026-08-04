package com.github.ledsoft.jopa.util;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;

@OWLClass(iri = "ex:User")
public class User extends Person {

    @OWLDataProperty(iri = "ex:username")
    private String username;

    @OWLDataProperty(iri = "ex:password")
    private String password;
}
