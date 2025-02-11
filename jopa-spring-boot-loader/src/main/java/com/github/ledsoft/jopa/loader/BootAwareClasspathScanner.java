package com.github.ledsoft.jopa.loader;

import cz.cvut.kbss.jopa.exceptions.OWLPersistenceException;
import cz.cvut.kbss.jopa.loaders.DefaultClasspathScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.loader.jar.NestedJarFile;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.jar.JarEntry;

/**
 * Classpath scanner supporting the Spring Boot JAR structure.
 * <p>
 * This scanner is able to detect entity classes in JAR files imported as dependencies into the application. I.e., it
 * supports nested JAR file processing.
 */
public class BootAwareClasspathScanner extends DefaultClasspathScanner {

    private static final Logger LOG = LoggerFactory.getLogger(BootAwareClasspathScanner.class);

    @Override
    protected void processJarFile(java.util.jar.JarFile jarFile) {
        LOG.trace("Scanning jar file {} for entity classes.", jarFile.getName());
        try{
            final Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                final String entryName = entry.getName();
                if (isJar(entryName)) {
                    final NestedJarFile nestedJar = new NestedJarFile(new File(jarFile.getName()), entryName);
                    processNestedJarEntries(nestedJar);
                } else {
                    processEntry(entryName);
                }
            }
        } catch (IOException e) {
            throw new OWLPersistenceException("Unexpected IOException reading JAR File " + jarFile, e);
        }
    }

    private void processEntry(String entryName) {
        if (entryName.endsWith(CLASS_FILE_SUFFIX) && entryName.contains(pathPattern)) {
            String className = entryName.substring(entryName.indexOf(pathPattern));
            className = className.replace('/', '.').replace('\\', '.');
            className = className.substring(0, className.length() - CLASS_FILE_SUFFIX.length());
            processClass(className);
        }
    }

    private void processNestedJarEntries(java.util.jar.JarFile jarFile) {
        LOG.trace("Scanning nested JAR file {} for entity classes.", jarFile.getName());
        final Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            final String entryName = entries.nextElement().getName();
            processEntry(entryName);
        }
    }
}
