package com.attunetv.launcher

import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey

class ChatSecurityTest {
    @Test fun rejectsWrongIssuerAudienceNonceExpiryAndAccount() {
        fun valid(issuer:String="https://auth.openai.com", audience:List<String> = listOf("issued-client"), nonce:String="fresh",
                  expiry:Long=200,subject:String="user-a") = ChatSecurity.validClaims(issuer,subject,audience,"issued-client",nonce,"fresh",expiry,100,"user-a")
        assertTrue(valid())
        assertFalse(valid(issuer="https://untrusted.example"))
        assertFalse(valid(audience=listOf("different-client")))
        assertFalse(valid(nonce="old"))
        assertFalse(valid(expiry=100))
        assertFalse(valid(subject="user-b"))
    }
    @Test fun rejectsTamperedSignature() {
        val pair=KeyPairGenerator.getInstance("RSA").apply {initialize(2048)}.generateKeyPair()
        val key=pair.public as RSAPublicKey
        val data="header.payload".toByteArray()
        val signed=Signature.getInstance("SHA256withRSA").run {initSign(pair.private);update(data);sign()}
        assertTrue(ChatSecurity.verifySignature(data,signed,key.modulus.toByteArray(),key.publicExponent.toByteArray()))
        assertFalse(ChatSecurity.verifySignature("header.other".toByteArray(),signed,key.modulus.toByteArray(),key.publicExponent.toByteArray()))
    }
    @Test fun multipleAudiencesRequireMatchingAuthorizedParty() {
        assertFalse(ChatSecurity.validClaims("https://auth.openai.com","user",listOf("client","other"),"client","nonce","nonce",200,100))
        assertTrue(ChatSecurity.validClaims("https://auth.openai.com","user",listOf("client","other"),"client","nonce","nonce",200,100,authorizedParty="client"))
        assertFalse(ChatSecurity.validClaims("https://auth.openai.com","user",listOf("client"),"client","nonce","nonce",200,100,authorizedParty="other"))
    }
    @Test fun genericProviderNamesAreNotMovieTitles() {
        for(name in listOf("PrimeVideo","Prime Video","Netflix","Apple TV","Disney+","Hulu","HBO Max","Peacock","Paramount+","null",""))
            assertNull(ChatSecurity.usableTitle(name))
        assertEquals("A real title",ChatSecurity.usableTitle(" A real title "))
    }
    @Test fun nativeSupportDoesNotTrustCandidateOrPrototypeLists() {
        assertTrue("com.amazon.firebat" in StreamingSupport.candidates)
        assertFalse(StreamingSupport.supported("com.amazon.firebat"))
        assertFalse(StreamingSupport.supported("com.amazon.firebat.fake"))
        assertFalse(StreamingSupport.supported(null))
    }
}
