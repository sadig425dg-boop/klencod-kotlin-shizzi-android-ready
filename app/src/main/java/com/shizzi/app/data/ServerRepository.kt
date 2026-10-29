package com.shizzi.app.data

import com.shizzi.app.core.db.ServerDao
import com.shizzi.app.core.model.ServerConfig
import kotlinx.coroutines.flow.Flow

class ServerRepository(private val dao: ServerDao) {

    fun observeServers(): Flow<List<ServerConfig>> = dao.observeAll()

    suspend fun getServers(): List<ServerConfig> = dao.getAll()

    suspend fun getSelected(): ServerConfig? = dao.getSelected()

    suspend fun saveAll(servers: List<ServerConfig>): List<Long> = dao.insertAll(servers)

    suspend fun save(server: ServerConfig): Long = dao.insert(server)

    suspend fun delete(server: ServerConfig) = dao.delete(server)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun select(id: Long) {
        dao.clearSelection()
        dao.select(id)
    }

    suspend fun updatePing(id: Long, pingMs: Int?) = dao.updatePing(id, pingMs)

    suspend fun pickFastestSelected(): ServerConfig? {
        val fastest = dao.getAll().filter { it.pingMs != null }.minByOrNull { it.pingMs!! }
        fastest?.let { select(it.id) }
        return fastest
    }
}
