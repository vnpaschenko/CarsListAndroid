package com.example.ipaschenko.carslist

import android.app.Application
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.persistence.room.Room
import android.os.AsyncTask
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

        EXECUTOR.execute {
            try {
                initDatabase()
            } catch (error: Throwable) {
                mDbStateData.postValue(DbStatus(false, ErrorInfo(error)))
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

        if (dao.size() == 0) {

            val stream = assets.open("CarsList.csv")
            val parser = CarsListCsvParser(stream)

            database.beginTransaction()
            try {
                for (car in parser) {
                    if (car.numberAttributes.and(NUMBER_ATTRIBUTE_CUSTOM) != 0) {
                        // Custom numbers are not currently supported
                        throw UnsupportedNumberException(car.number)

                    }
                    dao.insert(car)
                }
                database.setTransactionSuccessful()
            } catch (e: Throwable) {
                error = e
            } finally {
                database.endTransaction()
            }
        }

        val errorInfo = if (error != null) ErrorInfo(error) else null
        mDbStateData.postValue(DbStatus(error == null, errorInfo))
    }
}
