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

package com.bytechef.automation.configuration.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.bytechef.automation.configuration.domain.WorkspaceApiKey;
import com.bytechef.automation.configuration.security.AutomationMethodSecurityExpressionHandler;
import com.bytechef.automation.configuration.security.AutomationPermissionEvaluator;
import com.bytechef.automation.configuration.service.PermissionService;
import com.bytechef.automation.configuration.service.WorkspaceApiKeyService;
import com.bytechef.platform.constant.PlatformType;
import com.bytechef.platform.security.domain.ApiKey;
import com.bytechef.platform.security.facade.ApiKeyFacade;
import com.bytechef.platform.security.service.ApiKeyService;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * @author Ivica Cardic
 */
class WorkspaceApiKeyFacadeTest {

    private static final long API_KEY_ID = 7L;
    private static final String BODY_REACHED = "body reached";
    private static final long ENVIRONMENT_ID = 2L;
    private static final long WORKSPACE_ID = 42L;

    @Nested
    class MethodSecurity {

        @BeforeEach
        void beforeEach() {
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();

            securityContext.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "member", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

            SecurityContextHolder.setContext(securityContext);
        }

        @AfterEach
        void afterEach() {
            SecurityContextHolder.clearContext();
        }

        @Test
        void testCreateDeniesWhenTheApiKeyCreateScopeIsRefusedInTheWorkspace() {
            assertGuard(
                facade -> facade.create(WORKSPACE_ID, new ApiKey()), WORKSPACE_ID, "Workspace", "API_KEY_CREATE",
                false);
        }

        @Test
        void testCreateAllowsWhenTheApiKeyCreateScopeIsGrantedInTheWorkspace() {
            assertGuard(
                facade -> facade.create(WORKSPACE_ID, new ApiKey()), WORKSPACE_ID, "Workspace", "API_KEY_CREATE",
                true);
        }

        @Test
        void testDeleteDeniesWhenTheApiKeyDeleteScopeIsRefusedOnTheApiKey() {
            assertGuard(facade -> facade.delete(API_KEY_ID), API_KEY_ID, "ApiKey", "API_KEY_DELETE", false);
        }

        @Test
        void testDeleteAllowsWhenTheApiKeyDeleteScopeIsGrantedOnTheApiKey() {
            assertGuard(facade -> facade.delete(API_KEY_ID), API_KEY_ID, "ApiKey", "API_KEY_DELETE", true);
        }

        @Test
        void testGetApiKeysDeniesWhenTheApiKeyViewScopeIsRefusedInTheWorkspace() {
            assertGuard(
                facade -> facade.getApiKeys(WORKSPACE_ID, ENVIRONMENT_ID), WORKSPACE_ID, "Workspace", "API_KEY_VIEW",
                false);
        }

        @Test
        void testGetApiKeysAllowsWhenTheApiKeyViewScopeIsGrantedInTheWorkspace() {
            assertGuard(
                facade -> facade.getApiKeys(WORKSPACE_ID, ENVIRONMENT_ID), WORKSPACE_ID, "Workspace", "API_KEY_VIEW",
                true);
        }

        private void assertGuard(
            Consumer<WorkspaceApiKeyFacade> invocation, long resourceId, String resourceType, String expectedScope,
            boolean granted) {

            PermissionService permissionService = mock(PermissionService.class);

            when(permissionService.hasResourceScope(resourceId, resourceType, expectedScope)).thenReturn(granted);

            WorkspaceApiKeyFacade workspaceApiKeyFacade = secure(
                new WorkspaceApiKeyFacadeImpl(
                    bodyReachedMock(ApiKeyFacade.class), bodyReachedMock(ApiKeyService.class),
                    bodyReachedMock(WorkspaceApiKeyService.class)),
                permissionService);

            if (granted) {
                assertThatThrownBy(() -> invocation.accept(workspaceApiKeyFacade))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(BODY_REACHED);
            } else {
                assertThatThrownBy(() -> invocation.accept(workspaceApiKeyFacade))
                    .isInstanceOf(AccessDeniedException.class);
            }

            verify(permissionService).hasResourceScope(resourceId, resourceType, expectedScope);
            verifyNoMoreInteractions(permissionService);
        }

        private static <T> T bodyReachedMock(Class<T> type) {
            return mock(type, invocation -> {
                throw new IllegalStateException(BODY_REACHED);
            });
        }

        @SuppressWarnings("unchecked")
        private static <T> T secure(T target, PermissionService permissionService) {
            AutomationMethodSecurityExpressionHandler expressionHandler =
                new AutomationMethodSecurityExpressionHandler(permissionService);

            expressionHandler.setPermissionEvaluator(new AutomationPermissionEvaluator(permissionService));

            PreAuthorizeAuthorizationManager preAuthorizeAuthorizationManager = new PreAuthorizeAuthorizationManager();

            preAuthorizeAuthorizationManager.setExpressionHandler(expressionHandler);

            ProxyFactory proxyFactory = new ProxyFactory(target);

            proxyFactory.addAdvice(
                AuthorizationManagerBeforeMethodInterceptor.preAuthorize(preAuthorizeAuthorizationManager));

            return (T) proxyFactory.getProxy();
        }
    }

    @Nested
    class Behavior {

        private final ApiKeyFacade apiKeyFacade = mock(ApiKeyFacade.class);
        private final ApiKeyService apiKeyService = mock(ApiKeyService.class);
        private final WorkspaceApiKeyService workspaceApiKeyService = mock(WorkspaceApiKeyService.class);
        private final WorkspaceApiKeyFacade workspaceApiKeyFacade = new WorkspaceApiKeyFacadeImpl(
            apiKeyFacade, apiKeyService, workspaceApiKeyService);

        @Test
        void testCreateMintsAnAutomationKeyOwnedByTheCallerAndLinksItToTheWorkspace() {
            ApiKey apiKey = new ApiKey();
            ApiKey createdApiKey = new ApiKey();
            String secretKey = String.valueOf(UUID.randomUUID());

            createdApiKey.setId(API_KEY_ID);
            createdApiKey.setSecretKey(secretKey);

            when(apiKeyFacade.createAutomationApiKey(apiKey)).thenReturn(createdApiKey);

            assertThat(workspaceApiKeyFacade.create(WORKSPACE_ID, apiKey)).isEqualTo(secretKey);

            verify(workspaceApiKeyService).create(API_KEY_ID, WORKSPACE_ID);
        }

        @Test
        void testGetApiKeysListsOnlyTheCallerVisibleKeysOfTheWorkspace() {
            when(workspaceApiKeyService.getWorkspaceApiKeys(WORKSPACE_ID)).thenReturn(
                List.of(workspaceApiKey(1L), workspaceApiKey(2L)));
            when(apiKeyFacade.getApiKeys(ENVIRONMENT_ID, PlatformType.AUTOMATION)).thenReturn(
                List.of(apiKey(1L), apiKey(3L)));

            assertThat(workspaceApiKeyFacade.getApiKeys(WORKSPACE_ID, ENVIRONMENT_ID))
                .extracting(ApiKey::getId)
                .containsExactly(1L);
        }

        @Test
        void testDeleteRefusesAnotherUserKeyBeforeTouchingIt() {
            when(apiKeyFacade.getApiKey(API_KEY_ID)).thenThrow(
                new IllegalArgumentException("Api key not found for id: " + API_KEY_ID));

            assertThatThrownBy(() -> workspaceApiKeyFacade.delete(API_KEY_ID))
                .isInstanceOf(IllegalArgumentException.class);

            verify(workspaceApiKeyService, never()).deleteWorkspaceApiKey(anyLong());
            verify(apiKeyService, never()).delete(anyLong());
        }

        @Test
        void testDeleteRemovesTheCallerOwnKey() {
            when(apiKeyFacade.getApiKey(API_KEY_ID)).thenReturn(apiKey(API_KEY_ID));

            workspaceApiKeyFacade.delete(API_KEY_ID);

            verify(workspaceApiKeyService).deleteWorkspaceApiKey(API_KEY_ID);
            verify(apiKeyService).delete(API_KEY_ID);
        }

        private static ApiKey apiKey(long id) {
            ApiKey apiKey = new ApiKey();

            apiKey.setId(id);

            return apiKey;
        }

        private static WorkspaceApiKey workspaceApiKey(long apiKeyId) {
            return new WorkspaceApiKey(apiKeyId, WORKSPACE_ID);
        }
    }
}
