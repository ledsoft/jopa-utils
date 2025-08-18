package com.github.ledsoft.jopa.plugin.environment;

import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.vocabulary.OWL;

import java.net.URI;

@OWLClass(iri = OWL.THING)
public class Thing {

    @Id(generated = true)
    private URI uri;
}
