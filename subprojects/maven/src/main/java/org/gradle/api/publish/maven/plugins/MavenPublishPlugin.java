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

package org.gradle.api.publish.maven.plugins;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.NamedDomainObjectList;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.internal.artifact.MavenArtifactNotationParserFactory;
import org.gradle.api.publish.maven.internal.artifact.SingleOutputTaskMavenArtifact;
import org.gradle.api.publish.maven.internal.publication.DefaultMavenProjectIdentity;
import org.gradle.api.publish.maven.internal.publication.DefaultMavenPublication;
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenProjectIdentity;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal;
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository;
import org.gradle.api.publish.plugins.PublishingPlugin;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

import javax.inject.Inject;
import java.util.Set;

import static org.apache.commons.lang.StringUtils.capitalize;

/**
 * Adds the ability to publish in the Maven format to Maven repositories.
 *
 * @since 1.4
 */
@Incubating
public class MavenPublishPlugin implements Plugin<Project> {

    public static final String PUBLISH_LOCAL_LIFECYCLE_TASK_NAME = "publishToMavenLocal";

    private final Instantiator instantiator;
    private final DependencyMetaDataProvider dependencyMetaDataProvider;
    private final FileResolver fileResolver;
    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final FileCollectionFactory fileCollectionFactory;
    private final FeaturePreviews featurePreviews;
    private final ImmutableAttributesFactory immutableAttributesFactory;

    @Inject
    public MavenPublishPlugin(Instantiator instantiator, DependencyMetaDataProvider dependencyMetaDataProvider, FileResolver fileResolver,
                              ProjectDependencyPublicationResolver projectDependencyResolver, FileCollectionFactory fileCollectionFactory,
                              FeaturePreviews featurePreviews, ImmutableAttributesFactory immutableAttributesFactory) {
        this.instantiator = instantiator;
        this.dependencyMetaDataProvider = dependencyMetaDataProvider;
        this.fileResolver = fileResolver;
        this.projectDependencyResolver = projectDependencyResolver;
        this.fileCollectionFactory = fileCollectionFactory;
        this.featurePreviews = featurePreviews;
        this.immutableAttributesFactory = immutableAttributesFactory;
    }

    public void apply(final Project project) {
        project.getPluginManager().apply(PublishingPlugin.class);

        final TaskContainer tasks = project.getTasks();
        final Task publishLocalLifecycleTask = tasks.create(PUBLISH_LOCAL_LIFECYCLE_TASK_NAME);
        publishLocalLifecycleTask.setDescription("Publishes all Maven publications produced by this project to the local Maven cache.");
        publishLocalLifecycleTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);

        PublishingExtension extension = project.getExtensions().getByType(PublishingExtension.class);
        extension.getPublications().registerFactory(MavenPublication.class, new MavenPublicationFactory(dependencyMetaDataProvider, instantiator, fileResolver));
        realizePublishingTasksLater(project, extension);
    }

    private void realizePublishingTasksLater(final Project project, final PublishingExtension extension) {
        final NamedDomainObjectSet<MavenPublicationInternal> mavenPublications = extension.getPublications().withType(MavenPublicationInternal.class);
        final TaskContainer tasks = project.getTasks();
        final DirectoryProperty buildDirectory = project.getLayout().getBuildDirectory();

        final Task publishLifecycleTask = tasks.getByName(PublishingPlugin.PUBLISH_LIFECYCLE_TASK_NAME);
        final Task publishLocalLifecycleTask = tasks.getByName(PUBLISH_LOCAL_LIFECYCLE_TASK_NAME);

        mavenPublications.all(new Action<MavenPublicationInternal>() {
            @Override
            public void execute(MavenPublicationInternal publication) {
                createGenerateMetadataTask(tasks, publication, mavenPublications, buildDirectory);
                createGeneratePomTask(tasks, publication, buildDirectory);
                createLocalInstallTask(tasks, publishLocalLifecycleTask, publication);
                createPublishTasksForEachMavenRepo(tasks, extension, publishLifecycleTask, publication);
            }
        });
    }

    private void createPublishTasksForEachMavenRepo(final TaskContainer tasks, PublishingExtension extension, final Task publishLifecycleTask, final MavenPublicationInternal publication) {
        final String publicationName = publication.getName();
        NamedDomainObjectList<MavenArtifactRepository> repositories = extension.getRepositories().withType(MavenArtifactRepository.class);
        repositories.all(new Action<MavenArtifactRepository>() {
            @Override
            public void execute(final MavenArtifactRepository repository) {
                final String repositoryName = repository.getName();

                String publishTaskName = "publish" + capitalize(publicationName) + "PublicationTo" + capitalize(repositoryName) + "Repository";

                tasks.create(publishTaskName, PublishToMavenRepository.class, new Action<PublishToMavenRepository>() {
                    public void execute(PublishToMavenRepository publishTask) {
                        publishTask.setPublication(publication);
                        publishTask.setRepository(repository);
                        publishTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
                        publishTask.setDescription("Publishes Maven publication '" + publicationName + "' to Maven repository '" + repositoryName + "'.");

                    }
                });
                publishLifecycleTask.dependsOn(publishTaskName);
            }
        });
    }

    private void createLocalInstallTask(TaskContainer tasks, final Task publishLocalLifecycleTask, final MavenPublicationInternal publication) {
        final String publicationName = publication.getName();
        final String installTaskName = "publish" + capitalize(publicationName) + "PublicationToMavenLocal";

        tasks.create(installTaskName, PublishToMavenLocal.class, new Action<PublishToMavenLocal>() {
            public void execute(PublishToMavenLocal publishLocalTask) {
                publishLocalTask.setPublication(publication);
                publishLocalTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
                publishLocalTask.setDescription("Publishes Maven publication '" + publicationName + "' to the local Maven repository.");
            }
        });
        publishLocalLifecycleTask.dependsOn(installTaskName);
    }

    private void createGeneratePomTask(TaskContainer tasks, final MavenPublicationInternal publication, final DirectoryProperty buildDir) {
        final String publicationName = publication.getName();
        String descriptorTaskName = "generatePomFileFor" + capitalize(publicationName) + "Publication";
        GenerateMavenPom generatorTask = tasks.create(descriptorTaskName, GenerateMavenPom.class, new Action<GenerateMavenPom>() {
            public void execute(final GenerateMavenPom generatePomTask) {
                generatePomTask.setDescription("Generates the Maven POM file for publication '" + publicationName + "'.");
                generatePomTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
                generatePomTask.setPom(publication.getPom());
                generatePomTask.setDestination(buildDir.file("publications/" + publication.getName() + "/pom-default.xml"));
            }
        });
        publication.setPomArtifact(new SingleOutputTaskMavenArtifact(generatorTask, "pom", null));
    }

    private void createGenerateMetadataTask(final TaskContainer tasks, final MavenPublicationInternal publication, final Set<? extends MavenPublicationInternal> publications, final DirectoryProperty buildDir) {
        final String publicationName = publication.getName();
        String descriptorTaskName = "generateMetadataFileFor" + capitalize(publicationName) + "Publication";
        GenerateModuleMetadata generatorTask = tasks.create(descriptorTaskName, GenerateModuleMetadata.class, new Action<GenerateModuleMetadata>() {
            public void execute(final GenerateModuleMetadata generateTask) {
                generateTask.setDescription("Generates the Gradle metadata file for publication '" + publicationName + "'.");
                generateTask.setGroup(PublishingPlugin.PUBLISH_TASK_GROUP);
                generateTask.getPublication().set(publication);
                generateTask.getPublications().set(publications);
                generateTask.getOutputFile().set(buildDir.file("publications/" + publication.getName() + "/module.json"));
                generateTask.onlyIf(new Spec<Task>() {
                    @Override
                    public boolean isSatisfiedBy(Task element) {
                        return publication.canPublishModuleMetadata();
                    }
                });
            }
        });
        publication.setGradleModuleMetadataArtifact(new SingleOutputTaskMavenArtifact(generatorTask, "module", null));
    }

    private class MavenPublicationFactory implements NamedDomainObjectFactory<MavenPublication> {
        private final Instantiator instantiator;
        private final DependencyMetaDataProvider dependencyMetaDataProvider;
        private final FileResolver fileResolver;

        private MavenPublicationFactory(DependencyMetaDataProvider dependencyMetaDataProvider, Instantiator instantiator, FileResolver fileResolver) {
            this.dependencyMetaDataProvider = dependencyMetaDataProvider;
            this.instantiator = instantiator;
            this.fileResolver = fileResolver;
        }

        public MavenPublication create(final String name) {
            Module module = dependencyMetaDataProvider.getModule();
            MavenProjectIdentity projectIdentity = new DefaultMavenProjectIdentity(module);
            NotationParser<Object, MavenArtifact> artifactNotationParser = new MavenArtifactNotationParserFactory(instantiator, fileResolver).create();

            return instantiator.newInstance(
                DefaultMavenPublication.class,
                name, projectIdentity, artifactNotationParser, instantiator, projectDependencyResolver, fileCollectionFactory, featurePreviews, immutableAttributesFactory
            );
        }
    }
}
