/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.publish.maven;

import java.util.Map;
import java.util.Set;

public interface MavenPomContributor {

    String getName();

    void setName(String name);

    String getEmail();

    void setEmail(String email);

    String getUrl();

    void setUrl(String url);

    String getOrganization();

    void setOrganization(String organization);

    String getOrganizationUrl();

    void setOrganizationUrl(String organizationUrl);

    Set<String> getRoles();

    void roles(String... roles);

    String getTimezone();

    void setTimezone(String timezone);

    Map<String, String> getProperties();

    void properties(Map<String, String> properties);

}
