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
package io.syndesis.rest.v1.handler.connection;

import io.syndesis.connector.generator.ConnectorGenerator;
import io.syndesis.dao.icon.IconDataAccessObject;
import io.syndesis.dao.manager.DataManager;
import io.syndesis.model.action.ConnectorAction;
import io.syndesis.model.connection.ConfigurationProperty;
import io.syndesis.model.connection.Connector;
import io.syndesis.model.connection.ConnectorGroup;
import io.syndesis.model.connection.ConnectorSettings;
import io.syndesis.model.connection.ConnectorSummary;
import io.syndesis.model.connection.ConnectorTemplate;

import org.junit.Test;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CustomConnectorHandlerTest {

    private final ApplicationContext applicationContext = mock(ApplicationContext.class);

    private final DataManager dataManager = mock(DataManager.class);

    private final IconDataAccessObject iconDao = mock(IconDataAccessObject.class);

    @Test
    public void shouldCreateNewConnectorsBasedOnConnectorTemplates() {
        final Map<String, ConfigurationProperty> properties = new HashMap<>();
        properties.put("prop1", new ConfigurationProperty.Builder().build());

        final Map<String, ConfigurationProperty> connectorProperties = new HashMap<>();
        connectorProperties.put("prop2", new ConfigurationProperty.Builder().build());
        connectorProperties.put("prop3", new ConfigurationProperty.Builder().build());

        final ConnectorGroup group = new ConnectorGroup.Builder().name("connector template group").build();

        final ConnectorTemplate connectorTemplate = new ConnectorTemplate.Builder()//
            .id("connector-template-id")//
            .name("connector template")//
            .properties(properties).connectorProperties(connectorProperties)//
            .connectorGroup(group)//
            .build();

        final ConnectorAction action = new ConnectorAction.Builder().name("action").build();

        when(dataManager.fetch(ConnectorTemplate.class, "connector-template-id")).thenReturn(connectorTemplate);
        when(dataManager.create(any(Connector.class))).thenAnswer(invocation -> invocation.getArgumentAt(0, Connector.class));

        when(applicationContext.getBean("connector-template-id", ConnectorGenerator.class)).thenReturn(new ConnectorGenerator() {
            @Override
            public Connector generate(final ConnectorTemplate connectorTemplate, final ConnectorSettings connectorSettings) {
                return new Connector.Builder().createFrom(baseConnectorFrom(connectorTemplate, connectorSettings))
                    .putAllProperties(connectorProperties).putConfiguredProperty("prop1", "value1").addAction(action).build();
            }

            @Override
            public ConnectorSummary info(final ConnectorTemplate connectorTemplate, final ConnectorSettings connectorSettings) {
                return null;
            }
        });

        final Connector created = new CustomConnectorHandler(dataManager, applicationContext, iconDao).create(//
            new ConnectorSettings.Builder()//
                .connectorTemplateId("connector-template-id")//
                .name("new connector")//
                .description("new connector description")//
                .icon("new connector icon")//
                .putConfiguredProperty("prop1", "value1")//
                .putConfiguredProperty("unknown-prop", "unknown-value")//
                .build());

        final Connector expected = new Connector.Builder()//
            .id(created.getId())//
            .name("new connector")//
            .description("new connector description")//
            .icon("new connector icon")//
            .connectorGroup(group)//
            .properties(connectorProperties)//
            .putConfiguredProperty("prop1", "value1")//
            .addAction(action)//
            .build();

        assertThat(created).isEqualTo(expected);
    }

    @Test
    public void shouldProvideInfoAboutAppliedConnectorSettings() {
        final CustomConnectorHandler handler = new CustomConnectorHandler(dataManager, applicationContext, iconDao);
        final ConnectorGenerator connectorGenerator = mock(ConnectorGenerator.class);

        final ConnectorTemplate template = new ConnectorTemplate.Builder().build();
        final ConnectorSettings connectorSettings = new ConnectorSettings.Builder().connectorTemplateId("connector-template").build();
        final ConnectorSummary preparedSummary = new ConnectorSummary.Builder().build();

        when(dataManager.fetch(ConnectorTemplate.class, "connector-template")).thenReturn(template);
        when(applicationContext.getBean("connector-template", ConnectorGenerator.class)).thenReturn(connectorGenerator);
        when(connectorGenerator.info(same(template), same(connectorSettings))).thenReturn(preparedSummary);

        final ConnectorSummary info = handler.info(connectorSettings);

        assertThat(info).isSameAs(preparedSummary);
    }

    @Test
    public void shouldThrowEntityNotFoundIfNoConnectorTemplateExists() {
        assertThatThrownBy(() -> new CustomConnectorHandler(dataManager, applicationContext, iconDao).create(//
            new ConnectorSettings.Builder().connectorTemplateId("non-existent").build())//
        ).isInstanceOf(EntityNotFoundException.class).hasMessage("Connector template: non-existent");
    }

}
