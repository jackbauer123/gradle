/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.maven.internal.publication;

import org.gradle.api.Action;
import org.gradle.api.XmlProvider;
import org.gradle.api.internal.UserCodeAction;
import org.gradle.api.publish.maven.MavenDependency;
import org.gradle.api.publish.maven.MavenPomContributor;
import org.gradle.api.publish.maven.MavenPomContributorContainer;
import org.gradle.api.publish.maven.MavenPomDeveloper;
import org.gradle.api.publish.maven.MavenPomDeveloperContainer;
import org.gradle.api.publish.maven.MavenPomLicense;
import org.gradle.api.publish.maven.MavenPomLicenseContainer;
import org.gradle.api.publish.maven.MavenPomOrganization;
import org.gradle.api.publish.maven.MavenPomScm;
import org.gradle.api.publish.maven.internal.dependencies.MavenDependencyInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;
import org.gradle.internal.MutableActionSet;
import org.gradle.internal.reflect.Instantiator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DefaultMavenPom implements MavenPomInternal, MavenPomLicenseContainer, MavenPomDeveloperContainer, MavenPomContributorContainer {

    private final MutableActionSet<XmlProvider> xmlAction = new MutableActionSet<XmlProvider>();
    private final MavenPublicationInternal mavenPublication;
    private final Instantiator instantiator;
    private String packaging;
    private String description;
    private String url;
    private Integer inceptionYear;
    private final List<MavenPomLicense> licenses = new ArrayList<MavenPomLicense>();
    private MavenPomOrganization organization;
    private final List<MavenPomDeveloper> developers = new ArrayList<MavenPomDeveloper>();
    private final List<MavenPomContributor> contributors = new ArrayList<MavenPomContributor>();
    private MavenPomScm scm;

    public DefaultMavenPom(MavenPublicationInternal mavenPublication, Instantiator instantiator) {
        this.mavenPublication = mavenPublication;
        this.instantiator = instantiator;
    }

    public void withXml(Action<? super XmlProvider> action) {
        xmlAction.add(new UserCodeAction<XmlProvider>("Could not apply withXml() to generated POM", action));
    }

    public Action<XmlProvider> getXmlAction() {
        return xmlAction;
    }

    public String getPackaging() {
        if (packaging == null) {
            return mavenPublication.determinePackagingFromArtifacts();
        }
        return packaging;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public Integer getInceptionYear() {
        return inceptionYear;
    }

    @Override
    public void setInceptionYear(Integer inceptionYear) {
        this.inceptionYear = inceptionYear;
    }

    @Override
    public void licenses(Action<? super MavenPomLicenseContainer> action) {
        action.execute(this);
    }

    @Override
    public void license(Action<? super MavenPomLicense> action) {
        MavenPomLicense license = instantiator.newInstance(DefaultMavenPomLicense.class);
        action.execute(license);
        licenses.add(license);
    }

    @Override
    public List<MavenPomLicense> getLicenses() {
        return Collections.unmodifiableList(licenses);
    }

    @Override
    public void organization(Action<? super MavenPomOrganization> action) {
        if (organization == null) {
            organization = instantiator.newInstance(DefaultMavenPomOrganization.class);
        }
        action.execute(organization);
    }

    @Override
    public MavenPomOrganization getOrganization() {
        return organization;
    }

    @Override
    public void developers(Action<? super MavenPomDeveloperContainer> action) {
        action.execute(this);
    }

    @Override
    public void developer(Action<? super MavenPomDeveloper> action) {
        MavenPomDeveloper developer = instantiator.newInstance(DefaultMavenPomDeveloper.class);
        action.execute(developer);
        developers.add(developer);
    }

    @Override
    public List<MavenPomDeveloper> getDevelopers() {
        return Collections.unmodifiableList(developers);
    }

    @Override
    public void contributors(Action<? super MavenPomContributorContainer> action) {
        action.execute(this);
    }

    @Override
    public void contributor(Action<? super MavenPomContributor> action) {
        MavenPomContributor contributor = instantiator.newInstance(DefaultMavenPomDeveloper.class);
        action.execute(contributor);
        contributors.add(contributor);
    }

    @Override
    public List<MavenPomContributor> getContributors() {
        return Collections.unmodifiableList(contributors);
    }

    @Override
    public MavenPomScm getScm() {
        return scm;
    }

    @Override
    public void scm(Action<? super MavenPomScm> action) {
        if (scm == null) {
            scm = instantiator.newInstance(DefaultMavenPomScm.class);
        }
        action.execute(scm);
    }

    public MavenProjectIdentity getProjectIdentity() {
        return mavenPublication.getMavenProjectIdentity();
    }

    @Override
    public Set<MavenDependencyInternal> getApiDependencies() {
        return mavenPublication.getApiDependencies();
    }

    public Set<MavenDependencyInternal> getRuntimeDependencies() {
        return mavenPublication.getRuntimeDependencies();
    }

    @Override
    public Set<MavenDependency> getApiDependencyManagement() {
        return mavenPublication.getApiDependencyConstraints();
    }

    @Override
    public Set<MavenDependency> getRuntimeDependencyManagement() {
        return mavenPublication.getRuntimeDependencyConstraints();
    }
}
