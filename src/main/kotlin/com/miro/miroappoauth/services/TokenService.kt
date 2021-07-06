package com.miro.miroappoauth.services

import com.miro.miroappoauth.client.MiroClient
import com.miro.miroappoauth.dto.AccessTokenDto
import com.miro.miroappoauth.dto.UserDto
import com.miro.miroappoauth.model.Token
import com.miro.miroappoauth.model.TokenState
import com.miro.miroappoauth.model.TokenState.INVALID
import com.miro.miroappoauth.model.TokenState.NEW
import com.miro.miroappoauth.model.TokenState.VALID
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException.Unauthorized
import java.time.Instant

@Service
class TokenService(
    private val tokenStore: TokenStore,
    private val miroClient: MiroClient,
) {

    fun getAccessToken(code: String, redirectUri: String): AccessTokenDto {
        val accessToken = miroClient.getAccessToken(code, redirectUri)
        storeToken(accessToken)
        return accessToken
    }

    fun revokeToken(accessToken: String) {
        try {
            miroClient.revokeToken(accessToken)
            updateToken(accessToken, INVALID)
        } catch (e: Unauthorized) {
            updateToken(accessToken, INVALID)
            throw e
        }
    }

    fun refreshToken(accessTokenValue: String): AccessTokenDto {
        val token = tokenStore.get(accessTokenValue)
            ?: throw IllegalStateException("Missing accessToken $accessTokenValue")
        val accessToken = token.accessToken
        try {
            if (accessToken.refreshToken == null) {
                throw IllegalStateException("refresh_token is null for $accessToken")
            }
            val refreshedToken = miroClient.refreshToken(accessToken.refreshToken)
            storeToken(refreshedToken)
            updateToken(accessToken.accessToken, INVALID)
            return refreshedToken
        } catch (e: Unauthorized) {
            updateToken(accessToken.accessToken, INVALID)
            throw e
        }
    }

    fun getSelfUser(accessToken: String): UserDto {
        try {
            val self = miroClient.getSelfUser(accessToken)
            updateToken(accessToken, VALID)
            return self
        } catch (e: Unauthorized) {
            updateToken(accessToken, INVALID)
            throw e
        }
    }

    fun getToken(accessToken: String): Token? {
        return tokenStore.get(accessToken)
    }

    private fun storeToken(accessToken: AccessTokenDto) {
        val token = Token(accessToken, NEW, Instant.now(), null)
        try {
            tokenStore.insert(token)
        } catch (e: DuplicateKeyException) {
            tokenStore.update(token)
        }
    }

    private fun updateToken(accessToken: String, state: TokenState) {
        val token = tokenStore.get(accessToken) ?: throw IllegalStateException("Missing token $accessToken")
        token.state = state
        token.lastAccessedTime = Instant.now()
        tokenStore.update(token)
    }
}