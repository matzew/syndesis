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
package io.syndesis.credential.salesforce;

import io.syndesis.credential.CredentialProvider;
import io.syndesis.credential.CredentialProviderFactory;
import io.syndesis.credential.OAuth2CredentialProvider;
import io.syndesis.credential.salesforce.SalesforceConfiguration.SalesforceApplicator;

import org.springframework.boot.autoconfigure.social.SocialProperties;
import org.springframework.social.oauth2.OAuth2Template;
import org.springframework.social.salesforce.connect.SalesforceConnectionFactory;

public final class SalesforceCredentialProviderFactory implements CredentialProviderFactory {

    @Override
    public CredentialProvider create(final SocialProperties properties) {
        return createCredentialProvider(properties);
    }

    @Override
    public String id() {
        return "salesforce";
    }

    /* default */ static SalesforceConnectionFactory
        createConnectionFactory(final SocialProperties salesforceProperties) {
        final SalesforceConnectionFactory salesforce = new SalesforceConnectionFactory(salesforceProperties.getAppId(),
            salesforceProperties.getAppSecret());

        final OAuth2Template oAuthOperations = (OAuth2Template) salesforce.getOAuthOperations();

        // Salesforce requires OAuth client id and secret on the OAuth request
        oAuthOperations.setUseParametersForClientAuthentication(true);

        return salesforce;
    }

    /* default */ static CredentialProvider createCredentialProvider(final SocialProperties properties) {
        final SalesforceConnectionFactory connectionFactory = createConnectionFactory(properties);

        return new OAuth2CredentialProvider<>("salesforce", connectionFactory,
            new SalesforceApplicator(connectionFactory, properties));
    }

}
