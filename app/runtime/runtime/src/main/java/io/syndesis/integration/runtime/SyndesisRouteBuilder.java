/*
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.syndesis.integration.runtime;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

import io.syndesis.integration.model.Flow;
import io.syndesis.integration.model.SyndesisModel;
import io.syndesis.integration.model.YamlHelpers;
import io.syndesis.integration.model.steps.FromStep;
import io.syndesis.integration.model.steps.Step;
import io.syndesis.integration.runtime.designer.SingleMessageRoutePolicyFactory;
import io.syndesis.integration.support.Strings;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.util.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Camel {@link RouteBuilder} which maps the SyndesisModel rules to Camel routes
 */
public class SyndesisRouteBuilder extends RouteBuilder {
    private static final transient Logger LOG = LoggerFactory.getLogger(SyndesisRouteBuilder.class);

    private final String configurationUri;

    public SyndesisRouteBuilder(String configurationUri) {
        this.configurationUri = configurationUri;
    }

    protected SyndesisModel loadModel() throws Exception {
        try (InputStream is = ResourceHelper.resolveResourceAsInputStream(getContext().getClassResolver(), configurationUri)) {
            return is == null
                ? new SyndesisModel()
                : YamlHelpers.load(is);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void configure() throws Exception {
        int flowIndex = 0;
        for (Flow flow : loadModel().getFlows()) {
            getContext().setStreamCaching(true);

            if (flow.isTraceEnabled()) {
                getContext().setTracing(true);
            }

            String name = flow.getName();
            if (Strings.isEmpty(name)) {
                name = "flow" + (++flowIndex);
                flow.setName(name);
            }

            List<Step> steps = flow.getSteps();

            if (steps == null || steps.isEmpty()) {
                throw new IllegalStateException("No valid steps! Invalid flow " + flow);
            }

            ProcessorDefinition route = null;

            for (int i = 0; i < steps.size(); i++) {
                Step step = steps.get(i);

                if (i == 0 && !FromStep.class.isInstance(step)) {
                    throw new IllegalStateException("No endpoint found as first step (found: " + step.getKind() + ")");
                } else if (FromStep.class.isInstance(step)) {
                    boolean setId = route == null;

                    route = findHandler(step).handle(step, route, this).orElse(route);
                    if (setId) {
                        route.id(name);
                    }
                } else {
                    route = addStep(route, step).orElse(route);
                }
            }

            if (flow.isLogResultEnabled()) {
                route.to("log:" + name + "?showStreams=true");
            }

            if (flow.isSingleMessageModeEnabled()) {
                LOG.info("Enabling single message mode so that only one message is consumed for Design Mode");
                getContext().addRoutePolicyFactory(new SingleMessageRoutePolicyFactory());
            }
        }
    }

    public Optional<ProcessorDefinition> addSteps(ProcessorDefinition route, Iterable<? extends Step> steps) {
        if (route != null && steps != null) {
            for (Step item : steps) {
                route = addStep(route, item).orElse(route);
            }
        }
        return Optional.of(route);
    }

    @SuppressWarnings("unchecked")
    public <T extends Step> Optional<ProcessorDefinition> addStep(ProcessorDefinition route, T item) {
        if (route == null) {
            throw new IllegalArgumentException("You cannot use a " + item.getKind() + " step before you have started a flow");
        }

        return findHandler(item).handle(item, route, this);
    }

    private <T extends Step> StepHandler<T> findHandler(T item) {
        for (StepHandler handler : ServiceLoader.load(StepHandler.class, getClass().getClassLoader())) {
            if (handler.canHandle(item)) {
                return handler;
            }
        }

        throw new IllegalStateException("Unknown step kind: " + item + " of class: " + item.getClass().getName());
    }
}
