package com.example.data

import kotlinx.coroutines.flow.Flow

class DashboardRepository(private val dashboardDao: DashboardDao) {
    val allRfCodes: Flow<List<RfCode>> = dashboardDao.getAllRfCodes()
    val allLoraCodes: Flow<List<LoraCode>> = dashboardDao.getAllLoraCodes()
    val connectionLogs: Flow<List<ConnectionLog>> = dashboardDao.getConnectionLogs()

    fun searchRfCodes(query: String): Flow<List<RfCode>> {
        return dashboardDao.searchRfCodes("%$query%")
    }

    fun searchLoraCodes(query: String): Flow<List<LoraCode>> {
        return dashboardDao.searchLoraCodes("%$query%")
    }

    suspend fun insertRfCode(code: RfCode) = dashboardDao.insertRfCode(code)
    suspend fun updateRfCode(code: RfCode) = dashboardDao.updateRfCode(code)
    suspend fun deleteRfCode(code: RfCode) = dashboardDao.deleteRfCode(code)
    suspend fun deleteRfCodeById(id: Int) = dashboardDao.deleteRfCodeById(id)

    suspend fun insertLoraCode(code: LoraCode) = dashboardDao.insertLoraCode(code)
    suspend fun updateLoraCode(code: LoraCode) = dashboardDao.updateLoraCode(code)
    suspend fun deleteLoraCode(code: LoraCode) = dashboardDao.deleteLoraCode(code)
    suspend fun deleteLoraCodeById(id: Int) = dashboardDao.deleteLoraCodeById(id)

    suspend fun insertConnectionLog(log: ConnectionLog) = dashboardDao.insertConnectionLog(log)
    suspend fun clearConnectionLogs() = dashboardDao.clearConnectionLogs()
}
