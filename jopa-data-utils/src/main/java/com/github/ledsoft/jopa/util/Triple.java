package com.github.ledsoft.jopa.util;

import java.net.URI;

/**
 * Simplified representation of an RDF triple.
 *
 * @param subject   Triple subject
 * @param predicate Triple predicate
 * @param object    Triple object. Could be literal or resource
 */
public record Triple(URI subject, URI predicate, Object object) {
}
