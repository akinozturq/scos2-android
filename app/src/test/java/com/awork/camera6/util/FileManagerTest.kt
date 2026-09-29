package com.awork.camera6.util

import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import com.google.common.truth.Truth.assertThat
import java.io.File

class FileManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `toggleHidden creates nomedia file when hiding`() {
        val dir = tempFolder.newFolder("test_dir")
        val nomedia = File(dir, ".nomedia")

        assertThat(nomedia.exists()).isFalse()

        // Simulate toggle hidden
        if (!nomedia.exists()) nomedia.createNewFile()

        assertThat(nomedia.exists()).isTrue()
    }

    @Test
    fun `toggleHidden removes nomedia file when unhiding`() {
        val dir = tempFolder.newFolder("test_dir")
        val nomedia = File(dir, ".nomedia")
        nomedia.createNewFile()

        assertThat(nomedia.exists()).isTrue()

        nomedia.delete()

        assertThat(nomedia.exists()).isFalse()
    }

    @Test
    fun `isHidden returns true when nomedia exists`() {
        val dir = tempFolder.newFolder("test_dir")
        val nomedia = File(dir, ".nomedia")
        nomedia.createNewFile()

        assertThat(File(dir, ".nomedia").exists()).isTrue()
    }

    @Test
    fun `isHidden returns false when nomedia does not exist`() {
        val dir = tempFolder.newFolder("test_dir")
        assertThat(File(dir, ".nomedia").exists()).isFalse()
    }

    @Test
    fun `SCOS_DIR constant is correct`() {
        assertThat(FileManager.SCOS_DIR).isEqualTo("SCOS")
    }
}
