package com.example.diffviewer.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubAuthorizationTest {
    @Test
    fun returnsNoHeaderForBlankToken() {
        assertNull(githubAuthorizationHeaderValue("   "))
    }

    @Test
    fun returnsBearerHeaderForToken() {
        assertEquals(
            "Bearer test-token-value",
            githubAuthorizationHeaderValue(" test-token-value "),
        )
    }
}
