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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * @author Ivica Cardic
 */
class A2aServerOwnershipResolverTest {

    private final A2aProjectRepository a2aProjectRepository = mock(A2aProjectRepository.class);
    private final A2aServerRepository a2aServerRepository = mock(A2aServerRepository.class);
    private final ProjectService projectService = mock(ProjectService.class);
    private final A2aServerOwnershipResolver resolver = new A2aServerOwnershipResolver(
        a2aProjectRepository, a2aServerRepository, projectService);

    @Test
    void testResourceType() {
        assertThat(resolver.resourceType()).isEqualTo("A2aServer");
    }

    @Test
    void testResolvesTheServerWorkspace() {
        mockA2aServer(42L);

        when(a2aProjectRepository.findAllByA2aServerId(1L)).thenReturn(List.of());

        assertThat(resolver.resolveOwner(1L)
            .workspaceId()).hasValue(42L);
    }

    @Test
    void testResolvesTheServerWorkspaceWhenEveryAttachedProjectIsInIt() {
        mockA2aServer(42L);
        mockAttachedProjects(42L, 42L);

        assertThat(resolver.resolveOwner(1L)
            .workspaceId()).hasValue(42L);
    }

    @Test
    void testServerWithAProjectOfAnotherWorkspaceIsUnknown() {
        mockA2aServer(1049L);
        mockAttachedProjects(1049L, 42L);

        assertThat(resolver.resolveOwner(1L)
            .workspaceId()).isEmpty();
    }

    @Test
    void testServerWithoutWorkspaceIsUnknown() {
        A2aServer a2aServer = mock(A2aServer.class);

        when(a2aServer.getWorkspaceId()).thenReturn(null);
        when(a2aServerRepository.findById(1L)).thenReturn(Optional.of(a2aServer));

        assertThat(resolver.resolveOwner(1L)
            .workspaceId()).isEmpty();
    }

    @Test
    void testUnknownIsUnknown() {
        when(a2aServerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(resolver.resolveOwner(99L)
            .workspaceId()).isEmpty();
    }

    private void mockA2aServer(long workspaceId) {
        A2aServer a2aServer = mock(A2aServer.class);

        when(a2aServer.getWorkspaceId()).thenReturn(workspaceId);
        when(a2aServerRepository.findById(1L)).thenReturn(Optional.of(a2aServer));
    }

    private void mockAttachedProjects(long firstProjectWorkspaceId, long secondProjectWorkspaceId) {
        A2aProject firstA2aProject = mock(A2aProject.class);

        when(firstA2aProject.getProjectId()).thenReturn(3L);

        A2aProject secondA2aProject = mock(A2aProject.class);

        when(secondA2aProject.getProjectId()).thenReturn(4L);
        when(a2aProjectRepository.findAllByA2aServerId(1L)).thenReturn(List.of(firstA2aProject, secondA2aProject));

        Project firstProject = mock(Project.class);

        when(firstProject.getWorkspaceId()).thenReturn(firstProjectWorkspaceId);

        Project secondProject = mock(Project.class);

        when(secondProject.getWorkspaceId()).thenReturn(secondProjectWorkspaceId);
        when(projectService.getProjects(List.of(3L, 4L))).thenReturn(List.of(firstProject, secondProject));
    }
}
