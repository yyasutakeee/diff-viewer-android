package com.example.diffviewer.core.data

import com.example.diffviewer.core.domain.DiffLineKind
import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubPatchParserTest {
    @Test
    fun parsesLineKindsAndLineNumbers() {
        val patch = """@@ -10,2 +10,3 @@ class Example
 context
-old value
+new value
+second value
""".trimIndent()

        val diffHunkItems = parseGitHubPatch(patch)

        assertEquals(1, diffHunkItems.size)
        assertEquals("@@ -10,2 +10,3 @@ class Example", diffHunkItems.single().header)
        assertEquals(
            listOf(
                DiffLineKind.CONTEXT,
                DiffLineKind.DELETION,
                DiffLineKind.ADDITION,
                DiffLineKind.ADDITION,
            ),
            diffHunkItems.single().lineItems.map { diffLine -> diffLine.kind },
        )
        assertEquals(listOf(10, 11, null, null), diffHunkItems.single().lineItems.map { it.oldLine })
        assertEquals(listOf(10, null, 11, 12), diffHunkItems.single().lineItems.map { it.newLine })
    }
}
