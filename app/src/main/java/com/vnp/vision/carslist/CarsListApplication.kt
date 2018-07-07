package com.vnp.vision.carslist

import android.app.Application

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.persistence.room.Room
import android.net.Uri
import com.vnp.vision.carslist.data.*
import com.vnp.vision.carslist.utils.Cancellable
import com.vnp.vision.carslist.utils.canContinue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * Application
 */
class CarsListApplication: Application() {
    private val mDbLock = CountDownLatch(1)
    private val mDbAccessLock = ReentrantReadWriteLock()
    private var mDatabaseHolder: CarsDatabaseHolder? = null
    private val mDbStateData = MutableLiveData<DbStatus>()


    companion object {
        @JvmStatic
        lateinit var application: CarsListApplication
            private set

        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }

    fun getCarsListDatabase(cancellable: Cancellable?): CarsDatabaseHolder? {
        var databaseHolder: CarsDatabaseHolder? = null
        while (cancellable.canContinue()) {
            if (mDbLock.await(500, TimeUnit.MILLISECONDS)) {
                databaseHolder = mDatabaseHolder
                break
            }
        }
        return databaseHolder
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

    fun updateDatabase(fileUri: Uri) {

        val initialRecords = mDbStateData.value?.availableRecordsCount ?: 0
        mDbStateData.value = DbStatus(DbProcessingState.UPDATING,
                initialRecords, null)

        EXECUTOR.execute {
            try {
                loadDbUpdate(fileUri, initialRecords)
            } catch (error: Throwable) {
                mDbStateData.postValue(DbStatus(DbProcessingState.UPDATED,
                        initialRecords, ErrorInfo(error)))
            }
        }
    }

    fun loadDbUpdate(fileUri: Uri, initialRecords: Int) {
        val stream = contentResolver.openInputStream(fileUri)
        val parser = CarsListHtmlParser(stream)

        getCarsListDatabase(null)!!.write {
            it.beginTransaction()
            var recordsCount = 0
            try {
                val dao = it.carsDao()
                dao.clear()

                for (car in parser) {
                    if (car.numberAttributes.and(NUMBER_ATTRIBUTE_CUSTOM) != 0) {
                        // Custom numbers are not currently supported
                        throw UnsupportedNumberException(car.number)

                    }
                    dao.insert(car)
                    recordsCount ++
                }

                it.setTransactionSuccessful()

            } finally {
                it.endTransaction()
            }

            mDbStateData.postValue(DbStatus(DbProcessingState.INITIALIZED, recordsCount, null))
        }
    }

    private fun initDatabase() {
        val database = Room.databaseBuilder(this, CarsDatabase::class.java, "cars_list").build()
        val dao = database.carsDao()
        var error: Throwable? = null
        mDatabaseHolder = CarsDatabaseHolder(database, mDbAccessLock)

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
