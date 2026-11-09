package com.attunetv.launcher

import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.RSAPublicKeySpec

/** Pure validation rules, also exercised on the JVM. */
object ChatSecurity {
    fun validClaims(issuer: String, subject: String, audience: List<String>, client: String,
                    nonce: String, expectedNonce: String, expiry: Long, now: Long,
                    expectedSubject: String? = null, authorizedParty: String? = null): Boolean =
        issuer == "https://auth.openai.com" && subject.isNotBlank() && client in audience &&
            nonce == expectedNonce && nonce.isNotEmpty() && expiry > now &&
            (expectedSubject == null || expectedSubject == subject) &&
            (audience.size <= 1 || authorizedParty == client) &&
            (authorizedParty == null || authorizedParty == client)

    fun verifySignature(input: ByteArray, signature: ByteArray, modulus: ByteArray, exponent: ByteArray): Boolean {
        val key = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(BigInteger(1, modulus), BigInteger(1, exponent)))
        return Signature.getInstance("SHA256withRSA").run { initVerify(key); update(input); verify(signature) }
    }

    fun usableTitle(title: String?): String? = title?.trim()?.takeIf {
        it.isNotEmpty() && !Regex("(?i)null|unknown|prime\\s*video|netflix|apple\\s*tv|disney\\+?|hulu|hbo\\s*max|max|peacock|paramount\\+?").matches(it)
    }?.take(240)
}
