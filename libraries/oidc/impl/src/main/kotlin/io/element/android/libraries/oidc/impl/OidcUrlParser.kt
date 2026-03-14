/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.oidc.impl

import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.matrix.api.auth.OidcRedirectUrlProvider
import io.element.android.libraries.oidc.api.OidcAction

fun interface OidcUrlParser {
    fun parse(url: String): OidcAction?
}

/**
 * Simple parser for oidc url interception.
 * TODO Find documentation about the format.
 */
@ContributesBinding(AppScope::class)
class DefaultOidcUrlParser(
    private val oidcRedirectUrlProvider: OidcRedirectUrlProvider,
) : OidcUrlParser {
    /**
     * Return a OidcAction, or null if the url is not a OidcUrl.
     * Note:
     * When user press button "Cancel", we get the url:
     * `io.element.android:/?error=access_denied&state=IFF1UETGye2ZA8pO`
     * On success, we get:
     * `io.element.android:/?state=IFF1UETGye2ZA8pO&code=y6X1GZeqA3xxOWcTeShgv8nkgFJXyzWB`
     */
    override fun parse(url: String): OidcAction? {
        val parsedUrl = Uri.parse(url)
        val hasAuthResponse = parsedUrl.getQueryParameter("code") != null || parsedUrl.getQueryParameter("error") != null
        if (hasAuthResponse.not()) return null

        val configuredRedirect = oidcRedirectUrlProvider.provide()
        val configuredUri = Uri.parse(configuredRedirect)

        val exactMatch = url.startsWith(configuredRedirect)
        val acceptedHttpsHost = when (configuredUri.host) {
            null -> false
            "chat.avivaeu.org" -> parsedUrl.host == "chat.avivaeu.org" || parsedUrl.host == "web.chat.avivaeu.org"
            else -> parsedUrl.host == configuredUri.host
        }
        val acceptedHttpsPath = configuredUri.path?.let { path ->
            parsedUrl.path == path || parsedUrl.path.orEmpty().startsWith(path)
        } ?: true

        val compatibleHttpsRedirect = configuredUri.scheme == "https" &&
            parsedUrl.scheme == "https" &&
            acceptedHttpsHost &&
            acceptedHttpsPath

        if (exactMatch.not() && compatibleHttpsRedirect.not()) return null

        if (parsedUrl.getQueryParameter("error") == "access_denied") return OidcAction.GoBack()
        if (parsedUrl.getQueryParameter("code") != null) return OidcAction.Success(url)

        // Other case not supported, let's crash the app for now
        error("Not supported: $url")
    }
}
