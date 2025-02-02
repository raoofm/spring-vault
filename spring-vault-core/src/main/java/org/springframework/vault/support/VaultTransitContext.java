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
package org.springframework.vault.support;

import java.util.Arrays;

import org.springframework.util.Assert;

/**
 * Transit backend encryption/decryption/rewrapping context.
 *
 * @author Mark Paluch
 */
public class VaultTransitContext {

	/**
	 * Empty (default) {@link VaultTransitContext} without a {@literal context} and
	 * {@literal nonce}.
	 */
	private static final VaultTransitContext EMPTY = new VaultTransitContext(new byte[0],
			new byte[0]);

	private final byte[] context;

	private final byte[] nonce;

	VaultTransitContext(byte[] context, byte[] nonce) {
		this.context = context;
		this.nonce = nonce;
	}

	/**
	 * @return a new {@link VaultTransitRequestBuilder}.
	 */
	public static VaultTransitRequestBuilder builder() {
		return new VaultTransitRequestBuilder();
	}

	/**
	 * @return an empty {@link VaultTransitContext}.
	 */
	public static VaultTransitContext empty() {
		return EMPTY;
	}

	/**
	 * Create a {@link VaultTransitContext} given {@code context} bytes.
	 *
	 * @param context context bytes, must not be {@literal null}.
	 * @return a {@link VaultTransitContext} for {@code context}.
	 * @since 2.0
	 */
	public static VaultTransitContext fromContext(byte[] context) {
		return builder().context(context).build();
	}

	/**
	 * Create a {@link VaultTransitContext} given {@code nonce} bytes.
	 *
	 * @param nonce nonce bytes, must not be {@literal null}.
	 * @return a {@link VaultTransitContext} for {@code nonce}.
	 * @since 2.0
	 */
	public static VaultTransitContext fromNonce(byte[] nonce) {
		return builder().nonce(nonce).build();
	}

	/**
	 * @return the key derivation context.
	 */
	public byte[] getContext() {
		return context;
	}

	/**
	 * @return the
	 */
	public byte[] getNonce() {
		return nonce;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof VaultTransitContext))
			return false;
		VaultTransitContext that = (VaultTransitContext) o;
		return Arrays.equals(context, that.context) && Arrays.equals(nonce, that.nonce);
	}

	@Override
	public int hashCode() {
		int result = Arrays.hashCode(context);
		result = 31 * result + Arrays.hashCode(nonce);
		return result;
	}

	/**
	 * Builder for {@link VaultTransitContext}.
	 */
	public static class VaultTransitRequestBuilder {

		private byte[] context = new byte[0];
		private byte[] nonce = new byte[0];

		VaultTransitRequestBuilder() {
		}

		/**
		 * Configure a key derivation context for the {@code transit} operation.
		 *
		 * @param context key derivation context, provided as a binary data. Must be
		 *     provided if derivation is enabled.
		 * @return {@code this} {@link VaultTransitRequestBuilder}.
		 */
		public VaultTransitRequestBuilder context(byte[] context) {

			Assert.notNull(context, "Context must not be null");

			this.context = context;
			return this;
		}

		/**
		 * Configure the nonce value for a {@code transit} operation. Must be provided if
		 * convergent encryption is enabled for this key and the key was generated with
		 * Vault 0.6.1. Not required for keys created in 0.6.2+.
		 *
		 * @param nonce value must be exactly 96 bits (12 bytes) long and the user must
		 *     ensure that for any given context (and thus, any given encryption key) this
		 *     nonce value is never reused
		 * @return {@code this} {@link VaultTransitRequestBuilder}.
		 */
		public VaultTransitRequestBuilder nonce(byte[] nonce) {

			Assert.notNull(nonce, "Nonce must not be null");

			this.nonce = nonce;
			return this;
		}

		/**
		 * Build a new {@link VaultTransitContext} instance.
		 *
		 * @return a new {@link VaultTransitContext}.
		 */
		public VaultTransitContext build() {
			return new VaultTransitContext(context, nonce);
		}
	}
}
