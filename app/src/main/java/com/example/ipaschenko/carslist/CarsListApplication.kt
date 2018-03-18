package com.example.ipaschenko.carslist

import android.app.Application
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.persistence.room.Room
import android.os.AsyncTask
import com.example.ipaschenko.carslist.data.CarsDatabase
import com.example.ipaschenko.carslist.data.CarsListCsvParser
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


data class DbState(val status: Int, val error: Throwable?) {
    companion object {
        const val INITIALIZED_STATE_MASK = 1
        const val UPDATING_STATE_MASK: Int = 1.shl(1)
        const val UPDATED_STATE_MASK: Int = 1.shl(2)
    }
}

val DbState?.isInitialized
    get() = this != null && this.status.and(DbState.INITIALIZED_STATE_MASK) != 0


class CarsListApplication: Application() {
    private val mDbLock = CountDownLatch(1)
    private var mDatabase: CarsDatabase? = null
    private val mDbStateData = MutableLiveData<DbState>()

    companion object {
        @JvmStatic
        lateinit var application: CarsListApplication
            private set
    }

    fun getCarsListDatabase(timeoutMills: Long): CarsDatabase? =
        if (mDbLock.await(timeoutMills, TimeUnit.MILLISECONDS)) mDatabase else null

    val databaseState: LiveData<DbState>
        get() = mDbStateData

    override fun onCreate() {
        super.onCreate()
        application = this

        AsyncTask.THREAD_POOL_EXECUTOR.execute {
            try {
                initDatabase()
            } finally {
                mDbLock.countDown()
            }
        }
    }

    private fun initDatabase() {
        val database = Room.databaseBuilder(this, CarsDatabase::class.java, "cars_list").build()
        val dao = database.carsDao()
        var error: Throwable? = null

        if (dao.size() == 0) {

            val stream = assets.open("CarsList.csv")
            val parser = CarsListCsvParser(stream)

            database.beginTransaction()
            try {
                for (car in parser) {
                    dao.insert(car)
                }
                database.setTransactionSuccessful()
            } catch (e: Throwable) {
                error = e
            } finally {
                database.endTransaction()
            }
        }

        mDatabase = database

        mDbStateData.postValue(DbState(if (error == null) DbState.INITIALIZED_STATE_MASK else 0,
                error))
    }
}
