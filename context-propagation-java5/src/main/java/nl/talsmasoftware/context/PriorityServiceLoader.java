/*
 * Copyright 2016-2018 Talsma ICT
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.talsmasoftware.context;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Collections.*;

/**
 * Loader class to delegate to JDK 6 {@code ServiceLoader} or fallback to the old (imageio.spi) {@code ServiceRegistry}.
 *
 * @param <SVC> The type of service to load.
 * @author Sjoerd Talsma
 */
final class PriorityServiceLoader<SVC> implements Iterable<SVC> {
    private static final Logger LOGGER = Logger.getLogger(PriorityServiceLoader.class.getName());

    private final Class<SVC> serviceType;
    private volatile Iterable<SVC> delegate;

    PriorityServiceLoader(Class<SVC> serviceType) {
        this.serviceType = serviceType;
    }

    void reload() {
        delegate = null;
    }

    private synchronized Iterable<SVC> delegate() {
        Iterable<SVC> iterable = delegate;
        if (iterable == null) {
            final ArrayList<SVC> services = new ArrayList<SVC>();
            for (Iterator<SVC> iterator = loadServices(serviceType); iterator.hasNext(); ) {
                try {
                    SVC service = iterator.next();
                    if (service != null) services.add(service);
                } catch (Error error) {
                    if (isServiceConfigurationError(error)) {
                        LOGGER.log(Level.SEVERE, "Service configuration error iterating service "
                                + serviceType + ": " + error.getMessage(), error);
                    } else throw error;
                } catch (RuntimeException rte) {
                    LOGGER.log(Level.SEVERE, "Exception iterating service "
                            + serviceType + ": " + rte.getMessage(), rte);
                }
            }

            if (PriorityComparator.PRIORITY_AVAILABLE) sort(services, PriorityComparator.INSTANCE);
            if (services.isEmpty()) {
                iterable = emptySet();
            } else {
                services.trimToSize();
                iterable = delegate = unmodifiableList(services);
            }
        }
        return iterable;
    }

    @SuppressWarnings("Since15")
    private static boolean isServiceConfigurationError(Error error) {
        try {
            return java.util.ServiceConfigurationError.class.isInstance(error);
        } catch (LinkageError le) {
            LOGGER.log(Level.FINEST, "No ServiceConfigurationError available, probably running on Java 1.5.", le);
            return false;
        }
    }

    @SuppressWarnings("Since15")
    private static <SVC> Iterator<SVC> loadServices(Class<SVC> serviceType) {
        try {
            return java.util.ServiceLoader.load(serviceType).iterator();
        } catch (LinkageError le) {
            LOGGER.log(Level.FINEST, "No ServiceLoader available, probably running on Java 1.5.", le);
            return loadJava5Services(serviceType);
        } catch (RuntimeException loadingException) {
            LOGGER.log(Level.WARNING, "Unexpected error loading services of " + serviceType, loadingException);
            return Collections.<SVC>emptySet().iterator();
        }
    }

    @SuppressWarnings("unchecked")
    private static <SVC> Iterator<SVC> loadJava5Services(Class<SVC> serviceType) {
        try {
            return (Iterator<SVC>) Class.forName("javax.imageio.spi.ServiceRegistry")
                    .getDeclaredMethod("lookupProviders", Class.class)
                    .invoke(null, serviceType);
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.FINEST, "No java 5 (imageio.spi) ServiceRegistry found.", e);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("No \"lookupProviders\" method found in ServiceRegistry: " + e.getMessage(), e);
        } catch (IllegalAccessException e) {
            LOGGER.log(Level.FINEST, "Not allowed to call ServiceRegistry.lookupProviders: " + e.getMessage(), e);
        } catch (InvocationTargetException e) {
            throw e.getCause() instanceof RuntimeException ? (RuntimeException) e.getCause()
                    : new UnsupportedOperationException("Exception looking up " + serviceType + " service: "
                    + e.getCause().getMessage(), e.getCause());
        }
        return Collections.<SVC>emptySet().iterator();
    }

    public Iterator<SVC> iterator() {
        return delegate().iterator();
    }

}
