package com.charles.ollama.client.data.repository

import com.charles.ollama.client.data.database.dao.ServerConfigDao
import com.charles.ollama.client.data.database.entity.ServerConfigEntity
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import com.google.firebase.FirebaseApp
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace

class ServerRepositoryTest {

    private lateinit var serverConfigDao: ServerConfigDao
    private lateinit var serverRepository: ServerRepository

    private lateinit var firebaseAppMock: MockedStatic<FirebaseApp>
    private lateinit var firebasePerfMock: MockedStatic<FirebasePerformance>

    @Before
    fun setUp() {
        firebaseAppMock = mockStatic(FirebaseApp::class.java)
        firebaseAppMock.`when`<Any> { FirebaseApp.getInstance() }.thenReturn(null)

        firebasePerfMock = mockStatic(FirebasePerformance::class.java)
        val mockPerf = mock(FirebasePerformance::class.java)
        val mockTrace = mock(Trace::class.java)
        `when`(mockPerf.newTrace(org.mockito.ArgumentMatchers.anyString())).thenReturn(mockTrace)
        firebasePerfMock.`when`<Any> { FirebasePerformance.getInstance() }.thenReturn(mockPerf)

        serverConfigDao = mock(ServerConfigDao::class.java)
        serverRepository = ServerRepository(serverConfigDao)
    }

    @After
    fun tearDown() {
        firebaseAppMock.close()
        firebasePerfMock.close()
    }

    @Test
    fun insertServer_whenDefault_clearsPreviousDefaults() {
        runBlocking {
            val server = ServerConfigEntity(name = "Test", baseUrl = "http://localhost:11434", isDefault = true)
            `when`(serverConfigDao.insertServer(server)).thenReturn(1L)

            val id = serverRepository.insertServer(server)

            assertEquals(1L, id)
            verify(serverConfigDao).clearDefaultServers()
            verify(serverConfigDao).insertServer(server)
        }
    }

    @Test
    fun insertServer_whenNotDefault_doesNotClearDefaults() {
        runBlocking {
            val server = ServerConfigEntity(name = "Test", baseUrl = "http://localhost:11434", isDefault = false)
            `when`(serverConfigDao.insertServer(server)).thenReturn(1L)

            val id = serverRepository.insertServer(server)

            assertEquals(1L, id)
            verify(serverConfigDao, org.mockito.Mockito.never()).clearDefaultServers()
            verify(serverConfigDao).insertServer(server)
        }
    }

    @Test
    fun updateServer_whenDefault_clearsPreviousDefaults() {
        runBlocking {
            val server = ServerConfigEntity(name = "Test", baseUrl = "http://localhost:11434", isDefault = true)

            serverRepository.updateServer(server)

            verify(serverConfigDao).clearDefaultServers()
            verify(serverConfigDao).updateServer(server)
        }
    }

    @Test
    fun updateServer_whenNotDefault_doesNotClearDefaults() {
        runBlocking {
            val server = ServerConfigEntity(name = "Test", baseUrl = "http://localhost:11434", isDefault = false)

            serverRepository.updateServer(server)

            verify(serverConfigDao, org.mockito.Mockito.never()).clearDefaultServers()
            verify(serverConfigDao).updateServer(server)
        }
    }

    @Test
    fun setDefaultServer_clearsPreviousDefaultsAndSetsNew() {
        runBlocking {
            val serverId = 1L

            serverRepository.setDefaultServer(serverId)

            verify(serverConfigDao).clearDefaultServers()
            verify(serverConfigDao).setDefaultServer(serverId)
        }
    }

    @Test
    fun deleteServer_callsDao() {
        runBlocking {
            val server = ServerConfigEntity(name = "Test", baseUrl = "http://localhost:11434")

            serverRepository.deleteServer(server)

            verify(serverConfigDao).deleteServer(server)
        }
    }

    @Test
    fun getAllServers_callsDao() {
        runBlocking {
            val servers = listOf(ServerConfigEntity(name = "Test", baseUrl = "http://localhost:11434"))
            `when`(serverConfigDao.getAllServers()).thenReturn(flowOf(servers))

            val result = serverRepository.getAllServers()

            verify(serverConfigDao).getAllServers()
        }
    }

    @Test
    fun getServerById_callsDao() {
        runBlocking {
            val server = ServerConfigEntity(id = 1L, name = "Test", baseUrl = "http://localhost:11434")
            `when`(serverConfigDao.getServerById(1L)).thenReturn(server)

            val result = serverRepository.getServerById(1L)

            assertEquals(server, result)
            verify(serverConfigDao).getServerById(1L)
        }
    }

    @Test
    fun getDefaultServer_callsDao() {
        runBlocking {
            val server = ServerConfigEntity(name = "Test", baseUrl = "http://localhost:11434", isDefault = true)
            `when`(serverConfigDao.getDefaultServerFlow()).thenReturn(flowOf(server))

            val result = serverRepository.getDefaultServer()

            verify(serverConfigDao).getDefaultServerFlow()
        }
    }

    @Test
    fun getDefaultServerSync_callsDao() {
        runBlocking {
            val server = ServerConfigEntity(name = "Test", baseUrl = "http://localhost:11434", isDefault = true)
            `when`(serverConfigDao.getDefaultServer()).thenReturn(server)

            val result = serverRepository.getDefaultServerSync()

            assertEquals(server, result)
            verify(serverConfigDao).getDefaultServer()
        }
    }
}
