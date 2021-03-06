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

package org.gradle.tooling.internal.provider.runner

import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.BuildCancelledException
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.StartParameterInternal
import org.gradle.initialization.BuildEventConsumer
import org.gradle.internal.invocation.BuildController
import org.gradle.internal.service.ServiceRegistry
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException
import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException
import org.gradle.tooling.internal.protocol.InternalPhasedAction
import org.gradle.tooling.internal.protocol.PhasedActionResult
import org.gradle.tooling.internal.provider.BuildActionResult
import org.gradle.tooling.internal.provider.BuildClientSubscriptions
import org.gradle.tooling.internal.provider.ClientProvidedPhasedAction
import org.gradle.tooling.internal.provider.PhasedBuildActionResult
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer
import org.gradle.tooling.internal.provider.serialization.SerializedPayload
import spock.lang.Specification

class ClientProvidedPhasedActionRunnerTest extends Specification {

    def startParameter = Stub(StartParameterInternal)
    def serializedAction = Stub(SerializedPayload)
    def clientSubscriptions = Stub(BuildClientSubscriptions)
    def clientProvidedPhasedAction = new ClientProvidedPhasedAction(startParameter, serializedAction, clientSubscriptions)

    def projectsLoadedAction = Mock(InternalBuildActionVersion2)
    def projectsEvaluatedAction = Mock(InternalBuildActionVersion2)
    def buildFinishedAction = Mock(InternalBuildActionVersion2)
    def phasedAction = Mock(InternalPhasedAction) {
        getProjectsLoadedAction() >> projectsLoadedAction
        getProjectsEvaluatedAction() >> projectsEvaluatedAction
        getBuildFinishedAction() >> buildFinishedAction
    }

    def nullSerialized = Stub(SerializedPayload)
    def buildEventConsumer = Mock(BuildEventConsumer)
    def payloadSerializer = Mock(PayloadSerializer) {
        deserialize(serializedAction) >> phasedAction
        serialize(null) >> nullSerialized
    }
    BuildListener listener
    def gradle = Stub(GradleInternal) {
        addBuildListener(_) >> { BuildListener listener ->
            this.listener = listener
        }
        getServices() >> Stub(ServiceRegistry) {
            get(PayloadSerializer) >> payloadSerializer
            get(BuildEventConsumer) >> buildEventConsumer
        }
    }
    def buildResult = Mock(BuildResult)
    def buildController = Mock(BuildController) {
        run() >> {
            listener.projectsLoaded(gradle)
            listener.projectsEvaluated(gradle)
            listener.buildFinished(buildResult)
        }
        setResult(_) >> {
            buildController.hasResult() >> true
        }
        hasResult() >> false
        getGradle() >> gradle
    }
    def runner = new ClientProvidedPhasedActionRunner()

    def "can run actions and results are sent to event consumer"() {
        def result1 = 'result1'
        def serializedResult1 = Mock(SerializedPayload)
        def result2 = 'result2'
        def serializedResult2 = Mock(SerializedPayload)
        def result3 = 'result3'
        def serializedResult3 = Mock(SerializedPayload)

        given:
        payloadSerializer.serialize(result1) >> serializedResult1
        payloadSerializer.serialize(result2) >> serializedResult2
        payloadSerializer.serialize(result3) >> serializedResult3

        when:
        runner.run(clientProvidedPhasedAction, buildController)

        then:
        1 * projectsLoadedAction.execute(_) >> result1
        1 * projectsEvaluatedAction.execute(_) >> result2
        1 * buildFinishedAction.execute(_) >> result3
        1 * buildController.setResult({
            it instanceof BuildActionResult &&
                it.failure == null &&
                it.result == nullSerialized
        })
        1 * buildEventConsumer.dispatch({
            it instanceof PhasedBuildActionResult &&
                it.phase == PhasedActionResult.Phase.PROJECTS_LOADED &&
                it.result == serializedResult1
        })
        1 * buildEventConsumer.dispatch({
            it instanceof PhasedBuildActionResult &&
                it.phase == PhasedActionResult.Phase.PROJECTS_EVALUATED &&
                it.result == serializedResult2
        })
        1 * buildEventConsumer.dispatch({
            it instanceof PhasedBuildActionResult &&
                it.phase == PhasedActionResult.Phase.BUILD_FINISHED &&
                it.result == serializedResult3
        })
    }

    def "do not run later build action when fails"() {
        def serializedFailure = Mock(SerializedPayload)

        given:
        payloadSerializer.serialize({ it instanceof RuntimeException }) >> serializedFailure

        when:
        runner.run(clientProvidedPhasedAction, buildController)

        then:
        1 * projectsLoadedAction.execute(_) >> { throw new RuntimeException() }
        0 * projectsEvaluatedAction.execute(_)
        0 * buildFinishedAction.execute(_)
        1 * buildController.setResult(_) >> { args ->
            def it = args[0]
            assert it instanceof BuildActionResult
            assert it.failure == serializedFailure
            assert it.result == null
            buildController.hasResult() >> true
        }
        0 * buildEventConsumer.dispatch({
            it instanceof PhasedBuildActionResult &&
                it.phase == PhasedActionResult.Phase.PROJECTS_LOADED
        })
        0 * buildEventConsumer.dispatch({
            it instanceof PhasedBuildActionResult &&
                it.phase == PhasedActionResult.Phase.PROJECTS_EVALUATED
        })
        0 * buildEventConsumer.dispatch({
            it instanceof PhasedBuildActionResult &&
                it.phase == PhasedActionResult.Phase.BUILD_FINISHED
        })
    }

    def "exceptions are wrapped"() {
        when:
        runner.run(clientProvidedPhasedAction, buildController)

        then:
        1 * projectsLoadedAction.execute(_) >> { throw new RuntimeException() }
        1 * payloadSerializer.serialize({ it instanceof InternalBuildActionFailureException })

        when:
        runner.run(clientProvidedPhasedAction, buildController)

        then:
        1 * projectsLoadedAction.execute(_) >> { throw new BuildCancelledException() }
        1 * payloadSerializer.serialize({ it instanceof InternalBuildCancelledException })
    }

    def "action not run if null"() {
        when:
        runner.run(clientProvidedPhasedAction, buildController)

        then:
        noExceptionThrown()
        1 * phasedAction.getProjectsLoadedAction() >> null
        1 * phasedAction.getProjectsEvaluatedAction() >> null
        1 * phasedAction.getBuildFinishedAction() >> null
        1 * buildController.setResult({
            it instanceof BuildActionResult &&
                it.failure == null &&
                it.result == nullSerialized
        })
        0 * buildEventConsumer.dispatch(_)
    }
}
