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
package nl.talsmasoftware.context.mdc;

import nl.talsmasoftware.context.Context;
import nl.talsmasoftware.context.ContextManagers;
import nl.talsmasoftware.context.ContextSnapshot;
import nl.talsmasoftware.context.executors.ContextAwareExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasToString;
import static org.hamcrest.Matchers.is;

/**
 * Unit test for the {@link MdcManager}.
 *
 * @author Sjoerd Talsma
 */
public class MdcManagerTest {

    ExecutorService threadpool;

    @Before
    public void setupThreadpool() {
        threadpool = new ContextAwareExecutorService(Executors.newCachedThreadPool());
    }

    @After
    public void shutdownThreadpool() {
        threadpool.shutdown();
        threadpool = null;
    }

    @Before
    @After
    public void clearMDC() {
        MDC.clear();
    }

    private static final Callable<String> GET_MDC_ITEM = new Callable<String>() {
        public String call() {
            return MDC.get("mdc-item");
        }
    };

    @Test
    public void testMdcItemPropagation() throws ExecutionException, InterruptedException {
        MDC.put("mdc-item", "Item value");
        Future<String> itemValue = threadpool.submit(GET_MDC_ITEM);
        assertThat(itemValue.get(), is("Item value"));
    }

    @Test
    public void testMdcItemRestoration() throws Exception {
        MDC.put("mdc-item", "Value 1");

        ContextSnapshot snapshot = ContextManagers.createContextSnapshot();
        assertThat("New snapshot shouldn't manipulate MDC.", GET_MDC_ITEM.call(), is("Value 1"));

        MDC.put("mdc-item", "Value 2");
        assertThat("Sanity check: MDC changed?", GET_MDC_ITEM.call(), is("Value 2"));

        Context<Void> reactivation = snapshot.reactivate();
        assertThat("MDC changed by reactivation", GET_MDC_ITEM.call(), is("Value 1"));

        reactivation.close();
        assertThat("MDC restored?", GET_MDC_ITEM.call(), is("Value 2"));
    }

    @Test
    public void testMdcManagerToString() {
        assertThat(new MdcManager(), hasToString("MdcManager"));
    }

    @Test
    public void testMdcContextToString() {
        MdcManager mgr = new MdcManager();
        MDC.put("dummy", "value");
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        assertThat(mgr.getActiveContext(), hasToString("MdcContext{closed}"));
        Context<Map<String, String>> ctx = mgr.initializeNewContext(mdc);
        try {
            assertThat(ctx, hasToString("MdcContext" + mdc));
        } finally {
            ctx.close();
        }
    }

    @Test
    public void testClearActiveContexts() {
        MdcManager mgr = new MdcManager();
        MDC.put("dummy", "value");
        // Test no-op for MdcManager
        ContextManagers.clearActiveContexts();
        assertThat(MDC.get("dummy"), is("value"));
    }
}
