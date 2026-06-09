package com.charles.ollama.client.data.repository

import com.charles.ollama.client.data.database.dao.PromptPresetDao
import com.charles.ollama.client.data.database.entity.PromptPresetEntity
import com.charles.ollama.client.domain.prompt.BuiltInPrompts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FakePromptPresetDao : PromptPresetDao {
    var upsertedPreset: PromptPresetEntity? = null
    var deletedId: Long? = null
    var presetsFlow = MutableSharedFlow<List<PromptPresetEntity>>(replay = 1)

    override fun getAllFlow(): Flow<List<PromptPresetEntity>> = presetsFlow

    override suspend fun upsert(preset: PromptPresetEntity): Long {
        upsertedPreset = preset
        return preset.id
    }

    override suspend fun deleteById(id: Long) {
        deletedId = id
    }
}

class PromptRepositoryTest {

    private lateinit var fakeDao: FakePromptPresetDao
    private lateinit var repository: PromptRepository

    @Before
    fun setUp() {
        fakeDao = FakePromptPresetDao()
        repository = PromptRepository(fakeDao)
    }

    @Test
    fun `builtIns returns all built-in prompts`() {
        assertEquals(BuiltInPrompts.all, repository.builtIns)
    }

    @Test
    fun `customPresets maps entities to domain objects with custom prefix`() = runBlocking {
        val entity1 = PromptPresetEntity(id = 1, title = "T1", text = "Text 1")
        val entity2 = PromptPresetEntity(id = 2, title = "T2", text = "Text 2")
        fakeDao.presetsFlow.tryEmit(listOf(entity1, entity2))

        val presets = repository.customPresets.first()

        assertEquals(2, presets.size)
        assertEquals("custom_1", presets[0].id)
        assertEquals("T1", presets[0].title)
        assertEquals("Text 1", presets[0].text)
        assertEquals(false, presets[0].builtIn)

        assertEquals("custom_2", presets[1].id)
        assertEquals("T2", presets[1].title)
        assertEquals("Text 2", presets[1].text)
        assertEquals(false, presets[1].builtIn)
    }

    @Test
    fun `saveCustom with null existingId inserts new entity with default title if blank`() = runBlocking {
        repository.saveCustom("   ", "  My text  ", null)

        val captured = fakeDao.upsertedPreset!!
        assertEquals(0L, captured.id)
        assertEquals("Untitled", captured.title)
        assertEquals("My text", captured.text)
    }

    @Test
    fun `saveCustom trims title and text`() = runBlocking {
        repository.saveCustom(" My Title ", " My Text ", null)

        val captured = fakeDao.upsertedPreset!!
        assertEquals(0L, captured.id)
        assertEquals("My Title", captured.title)
        assertEquals("My Text", captured.text)
    }

    @Test
    fun `saveCustom with existingId updates existing entity`() = runBlocking {
        repository.saveCustom("New Title", "New Text", "custom_42")

        val captured = fakeDao.upsertedPreset!!
        assertEquals(42L, captured.id)
        assertEquals("New Title", captured.title)
        assertEquals("New Text", captured.text)
    }

    @Test
    fun `saveCustom with invalid existingId defaults to 0`() = runBlocking {
        repository.saveCustom("New Title", "New Text", "custom_abc")

        val captured = fakeDao.upsertedPreset!!
        assertEquals(0L, captured.id)
    }

    @Test
    fun `deleteCustom with valid id deletes by rowId`() = runBlocking {
        repository.deleteCustom("custom_42")

        assertEquals(42L, fakeDao.deletedId)
    }

    @Test
    fun `deleteCustom with invalid id does nothing`() = runBlocking {
        repository.deleteCustom("custom_abc")

        assertNull(fakeDao.deletedId)
    }

    @Test
    fun `deleteCustom without prefix does nothing if parsing fails`() = runBlocking {
        repository.deleteCustom("abc")

        assertNull(fakeDao.deletedId)
    }

    @Test
    fun `deleteCustom without prefix works if parsing succeeds`() = runBlocking {
        repository.deleteCustom("42")

        assertEquals(42L, fakeDao.deletedId)
    }
}
