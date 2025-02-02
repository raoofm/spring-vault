/*
 * Copyright 2016-2019 the original author or authors.
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
package org.springframework.vault.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.Assert;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.authentication.LifecycleAwareSessionManager;
import org.springframework.vault.authentication.SessionManager;
import org.springframework.vault.client.ClientHttpRequestFactoryFactory;
import org.springframework.vault.client.RestTemplateBuilder;
import org.springframework.vault.client.SimpleVaultEndpointProvider;
import org.springframework.vault.client.VaultClients;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.client.VaultEndpointProvider;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.support.ClientOptions;
import org.springframework.vault.support.SslConfiguration;
import org.springframework.web.client.RestOperations;

/**
 * Base class for Spring Vault configuration using JavaConfig.
 *
 * @author Spencer Gibb
 * @author Mark Paluch
 */
@Configuration
public abstract class AbstractVaultConfiguration implements ApplicationContextAware {

	private @Nullable ApplicationContext applicationContext;

	/**
	 * @return Vault endpoint coordinates for HTTP/HTTPS communication, must not be
	 * {@literal null}.
	 */
	public abstract VaultEndpoint vaultEndpoint();

	/**
	 * @return a {@link VaultEndpointProvider} returning the value of
	 * {@link #vaultEndpoint()}.
	 * @since 1.1
	 */
	public VaultEndpointProvider vaultEndpointProvider() {
		return SimpleVaultEndpointProvider.of(vaultEndpoint());
	}

	/**
	 * Annotate with {@link Bean} in case you want to expose a
	 * {@link ClientAuthentication} instance to the
	 * {@link org.springframework.context.ApplicationContext}.
	 *
	 * @return the {@link ClientAuthentication} to use. Must not be {@literal null}.
	 */
	public abstract ClientAuthentication clientAuthentication();

	/**
	 * Create a {@link RestTemplateBuilder} initialized with {@link VaultEndpointProvider}
	 * and {@link ClientHttpRequestFactory}. May be overridden by subclasses.
	 *
	 * @return the {@link RestTemplateBuilder}.
	 * @see #vaultEndpointProvider()
	 * @see #clientHttpRequestFactoryWrapper()
	 * @since 2.2
	 */
	protected RestTemplateBuilder restTemplateBuilder(
			VaultEndpointProvider endpointProvider,
			ClientHttpRequestFactory requestFactory) {
		return RestTemplateBuilder.builder().endpointProvider(endpointProvider)
				.requestFactory(requestFactory);
	}

	/**
	 * Create a {@link VaultTemplate}.
	 *
	 * @return the {@link VaultTemplate}.
	 * @see #vaultEndpointProvider()
	 * @see #clientHttpRequestFactoryWrapper()
	 * @see #sessionManager()
	 */
	@Bean
	public VaultTemplate vaultTemplate() {
		return new VaultTemplate(
				restTemplateBuilder(vaultEndpointProvider(),
						clientHttpRequestFactoryWrapper().getClientHttpRequestFactory()),
				sessionManager());
	}

	/**
	 * Construct a {@link LifecycleAwareSessionManager} using
	 * {@link #clientAuthentication()}. This {@link SessionManager} uses
	 * {@link #threadPoolTaskScheduler()}.
	 *
	 * @return the {@link SessionManager} for Vault session management.
	 * @see SessionManager
	 * @see LifecycleAwareSessionManager
	 * @see #restOperations()
	 * @see #clientAuthentication()
	 * @see #threadPoolTaskScheduler() ()
	 */
	@Bean
	public SessionManager sessionManager() {

		ClientAuthentication clientAuthentication = clientAuthentication();

		Assert.notNull(clientAuthentication, "ClientAuthentication must not be null");

		return new LifecycleAwareSessionManager(clientAuthentication,
				threadPoolTaskScheduler(), restOperations());
	}

	/**
	 * Construct a {@link SecretLeaseContainer} using {@link #vaultTemplate()} and
	 * {@link #threadPoolTaskScheduler()}.
	 *
	 * @return the {@link SecretLeaseContainer} to allocate, renew and rotate secrets and
	 * their leases.
	 * @see #vaultTemplate()
	 * @see #threadPoolTaskScheduler()
	 */
	@Bean
	public SecretLeaseContainer secretLeaseContainer() throws Exception {

		SecretLeaseContainer secretLeaseContainer = new SecretLeaseContainer(
				vaultTemplate(), threadPoolTaskScheduler());

		secretLeaseContainer.afterPropertiesSet();
		secretLeaseContainer.start();

		return secretLeaseContainer;
	}

	/**
	 * Create a {@link ThreadPoolTaskScheduler} used by
	 * {@link LifecycleAwareSessionManager} and
	 * {@link org.springframework.vault.core.lease.SecretLeaseContainer}. Annotate with
	 * {@link Bean} in case you want to expose a {@link ThreadPoolTaskScheduler} instance
	 * to the {@link org.springframework.context.ApplicationContext}. This might be useful
	 * to supply managed executor instances or {@link ThreadPoolTaskScheduler}s using a
	 * queue/pooled threads.
	 *
	 * @return the {@link ThreadPoolTaskScheduler} to use. Must not be {@literal null}.
	 */
	@Bean("vaultThreadPoolTaskScheduler")
	public ThreadPoolTaskScheduler threadPoolTaskScheduler() {

		ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();

		threadPoolTaskScheduler
				.setThreadNamePrefix("spring-vault-ThreadPoolTaskScheduler-");
		threadPoolTaskScheduler.setDaemon(true);

		return threadPoolTaskScheduler;
	}

	/**
	 * Construct a {@link RestOperations} object configured for Vault session management
	 * and authentication usage.
	 *
	 * @return the {@link RestOperations} to be used for Vault access.
	 * @see #vaultEndpointProvider()
	 * @see #clientHttpRequestFactoryWrapper()
	 */
	public RestOperations restOperations() {
		return VaultClients.createRestTemplate(vaultEndpointProvider(),
				clientHttpRequestFactoryWrapper().getClientHttpRequestFactory());
	}

	/**
	 * Create a {@link ClientFactoryWrapper} containing a {@link ClientHttpRequestFactory}
	 * . {@link ClientHttpRequestFactory} is not exposed as root bean because
	 * {@link ClientHttpRequestFactory} is configured with {@link ClientOptions} and
	 * {@link SslConfiguration} which are not necessarily applicable for the whole
	 * application.
	 *
	 * @return the {@link ClientFactoryWrapper} to wrap a {@link ClientHttpRequestFactory}
	 * instance.
	 * @see #clientOptions()
	 * @see #sslConfiguration()
	 */
	@Bean
	public ClientFactoryWrapper clientHttpRequestFactoryWrapper() {
		return new ClientFactoryWrapper(ClientHttpRequestFactoryFactory
				.create(clientOptions(), sslConfiguration()));
	}

	/**
	 * @return {@link ClientOptions} to configure communication parameters.
	 * @see ClientOptions
	 */
	public ClientOptions clientOptions() {
		return new ClientOptions();
	}

	/**
	 * @return SSL configuration options. Defaults to
	 * {@link SslConfiguration#unconfigured()}.
	 * @see SslConfiguration
	 * @see SslConfiguration#unconfigured()
	 */
	public SslConfiguration sslConfiguration() {
		return SslConfiguration.unconfigured();
	}

	/**
	 * Return the {@link Environment} to access property sources during Spring Vault
	 * bootstrapping. Requires {@link #setApplicationContext(ApplicationContext)
	 * ApplicationContext} to be set.
	 *
	 * @return the {@link Environment} to access property sources during Spring Vault
	 * bootstrapping.
	 */
	protected Environment getEnvironment() {

		Assert.state(applicationContext != null,
				"ApplicationContext must be set before accessing getEnvironment()");

		return applicationContext.getEnvironment();
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		this.applicationContext = applicationContext;
	}

	/**
	 * Wrapper for {@link ClientHttpRequestFactory} to not expose the bean globally.
	 */
	public static class ClientFactoryWrapper implements InitializingBean, DisposableBean {

		private final ClientHttpRequestFactory clientHttpRequestFactory;

		public ClientFactoryWrapper(ClientHttpRequestFactory clientHttpRequestFactory) {
			this.clientHttpRequestFactory = clientHttpRequestFactory;
		}

		@Override
		public void destroy() throws Exception {
			if (clientHttpRequestFactory instanceof DisposableBean) {
				((DisposableBean) clientHttpRequestFactory).destroy();
			}
		}

		@Override
		public void afterPropertiesSet() throws Exception {

			if (clientHttpRequestFactory instanceof InitializingBean) {
				((InitializingBean) clientHttpRequestFactory).afterPropertiesSet();
			}
		}

		public ClientHttpRequestFactory getClientHttpRequestFactory() {
			return clientHttpRequestFactory;
		}
	}
}
