/*
 * Copyright (C) 2016 Red Hat, Inc.
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

package io.syndesis.inspector;

import com.google.common.io.Resources;
import io.fabric8.mockwebserver.DefaultMockServer;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.util.List;

public class DataMapperClassInspectorTest {

    @Rule
    public InfinispanCache infinispan = new InfinispanCache();
    private DefaultMockServer mockServer = new DefaultMockServer();
    private ClassInspectorConfigurationProperties config = new ClassInspectorConfigurationProperties(mockServer.getHostName(), mockServer.getPort(), false);



    @Test
    public void shouldHandleNesting() throws Exception {
        DataMapperClassInspector dataMapperClassInspector = new DataMapperClassInspector(infinispan.getCaches(), new RestTemplate(), config);

        mockServer.expect().get().withPath("/v2/atlas/java/class?className=twitter4j.StatusJSONImpl").andReturn(200, Resources.toString(getClass().getResource("/twitter4j.StatusJSONImpl.json"), Charset.defaultCharset())).always();
        mockServer.expect().get().withPath("/v2/atlas/java/class?className=twitter4j.Logger").andReturn(200, Resources.toString(getClass().getResource("/twitter4j.Logger.json"), Charset.defaultCharset())).always();
        mockServer.expect().get().withPath("/v2/atlas/java/class?className=twitter4j.LoggerFactory").andReturn(200, Resources.toString(getClass().getResource("/twitter4j.LoggerFactory.json"), Charset.defaultCharset())).always();
        List<String> paths = dataMapperClassInspector.getPaths("twitter4j.StatusJSONImpl");

        Assert.assertNotNull(paths);
        Assert.assertTrue(paths.contains("StatusJSONImpl.id"));
        Assert.assertTrue(paths.contains("StatusJSONImpl.logger.infoEnabled"));
    }

    @Test
    public void shouldHandleInterfaces() throws Exception {
        DataMapperClassInspector dataMapperClassInspector = new DataMapperClassInspector(infinispan.getCaches(), new RestTemplate(), config);
        mockServer.expect().get().withPath("/v2/atlas/java/class?className=twitter4j.Status").andReturn(200, Resources.toString(getClass().getResource("/twitter4j.Status.json"), Charset.defaultCharset())).always();

        List<String> paths = dataMapperClassInspector.getPaths("twitter4j.Status");

        Assert.assertNotNull(paths);
        Assert.assertTrue(paths.contains("Status.id"));
    }

    @Test
    public void shouldExtractClassName() throws Exception {
        DataMapperClassInspector dataMapperClassInspector = new DataMapperClassInspector(infinispan.getCaches(), new RestTemplate(), config);
        Assert.assertEquals("Status", dataMapperClassInspector.getClassName("Status"));
        Assert.assertEquals("Status", dataMapperClassInspector.getClassName("twitter4j.Status"));
        Assert.assertEquals("Status", dataMapperClassInspector.getClassName("more.twitter4j.Status"));
    }
}
