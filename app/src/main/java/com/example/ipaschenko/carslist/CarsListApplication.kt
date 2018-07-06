package com.example.ipaschenko.carslist

import android.app.Application

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.persistence.room.Room
import com.example.ipaschenko.carslist.data.*
import com.example.ipaschenko.carslist.utils.Cancellable
import com.example.ipaschenko.carslist.utils.canContinue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Application
 */
class CarsListApplication: Application() {
    private val mDbLock = CountDownLatch(1)
    private var mDatabase: CarsDatabase? = null
    private val mDbStateData = MutableLiveData<DbStatus>()

    companion object {
        @JvmStatic
        lateinit var application: CarsListApplication
            private set

        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }

    fun getCarsListDatabase(cancellable: Cancellable?): CarsDatabase? {
        var database: CarsDatabase? = null
        while (cancellable.canContinue()) {
            if (mDbLock.await(500, TimeUnit.MILLISECONDS)) {
                database = mDatabase
                break
            }
        }
        return database
    }

    val databaseStatus: LiveData<DbStatus>
        get() = mDbStateData

    override fun onCreate() {
        super.onCreate()
        application = this

        mDbStateData.value = DbStatus(DbProcessingState.INITIALIZING, 0, null)

        EXECUTOR.execute {
            try {
                initDatabase()
            } catch (error: Throwable) {
                mDbStateData.postValue(DbStatus(DbProcessingState.INITIALIZED,
                        0, ErrorInfo(error)))
            } finally {
                mDbLock.countDown()
            }
        }
    }

    private fun initDatabase() {
        val database = Room.databaseBuilder(this, CarsDatabase::class.java, "cars_list").build()
        val dao = database.carsDao()
        var error: Throwable? = null
        mDatabase = database

        var recordsCount = dao.size()

        if (recordsCount <= 0) {

            database.beginTransaction()
            try {
                val stream = assets.open("carlist.html")
                val parser = CarsListHtmlParser(stream)

                for (car in parser) {
                    if (car.numberAttributes.and(NUMBER_ATTRIBUTE_CUSTOM) != 0) {
                        // Custom numbers are not currently supported
                        throw UnsupportedNumberException(car.number)

                    }
                    dao.insert(car)
                    recordsCount ++
                }
                database.setTransactionSuccessful()
            } catch (e: Throwable) {
                error = e
                recordsCount = 0

            } finally {
                database.endTransaction()
            }
        }

        val errorInfo = if (error != null) ErrorInfo(error) else null
        mDbStateData.postValue(DbStatus(DbProcessingState.INITIALIZED, recordsCount,  errorInfo))
    }
}
