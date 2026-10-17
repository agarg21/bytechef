/*
 * Copyright 2025 ByteChef
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bytechef.automation.ai.a2a.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bytechef.automation.ai.a2a.domain.A2aProject;
import com.bytechef.automation.ai.a2a.domain.A2aServer;
import com.bytechef.automation.ai.a2a.repository.A2aProjectRepository;
import com.bytechef.automation.ai.a2a.repository.A2aServerRepository;
import com.bytechef.automation.configuration.domain.Project;
import com.bytechef.automation.configuration.service.ProjectService;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * @author Ivica Cardic
 */
class A2aProjectOwnershipResolverTest {

    private final A2aProjectRepository a2aProjectRepository = mock(A2aProjectRepository.class);
    private final A2aServerRepository a2aServerRepository = mock(A2aServerRepository.class);
    private final ProjectService projectService = mock(ProjectService.class);
    private final A2aProjectOwnershipResolver resolver = new A2aProjectOwnershipResolver(
        a2aProjectRepository, a2aServerRepository, projectService);

    @Test
    void testResourceType() {
        assertThat(resolver.resourceType()).isEqualTo("A2aProject");
    }

    @Test
    void testResolvesTheWorkspaceSharedByServerAndProject() {
        mockA2aProject(42L, 42L);

        assertThat(resolver.resolveOwner(1L)
            .workspaceId()).hasValue(42L);
    }

    @Test
    void testProjectOfAnotherWorkspaceThanTheServerIsUnknown() {
        mockA2aProject(1049L, 42L);

        assertThat(resolver.resolveOwner(1L)
            .workspaceId()).isEmpty();
    }

    @Test
    void testMissingProjectIsUnknown() {
        mockA2aProject(42L, 42L);

        when(projectService.fetchProject(3L)).thenReturn(Optional.empty());

        assertThat(resolver.resolveOwner(1L)
            .workspaceId()).isEmpty();
    }

    @Test
    void testUnknownIsUnknown() {
        when(a2aProjectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(resolver.resolveOwner(99L)
            .workspaceId()).isEmpty();
    }

    private void mockA2aProject(long a2aServerWorkspaceId, long projectWorkspaceId) {
        A2aProject a2aProject = mock(A2aProject.class);

        when(a2aProject.getA2aServerId()).thenReturn(7L);
        when(a2aProject.getProjectId()).thenReturn(3L);
        when(a2aProjectRepository.findById(1L)).thenReturn(Optional.of(a2aProject));

        A2aServer a2aServer = mock(A2aServer.class);

        when(a2aServer.getWorkspaceId()).thenReturn(a2aServerWorkspaceId);
        when(a2aServerRepository.findById(7L)).thenReturn(Optional.of(a2aServer));

        Project project = mock(Project.class);

        when(project.getWorkspaceId()).thenReturn(projectWorkspaceId);
        when(projectService.fetchProject(3L)).thenReturn(Optional.of(project));
    }
}
