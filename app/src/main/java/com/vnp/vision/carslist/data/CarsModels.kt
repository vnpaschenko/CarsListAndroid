package com.vnp.vision.carslist.data

import android.arch.persistence.room.*
import java.util.concurrent.locks.ReadWriteLock

const val NUMBER_ATTRIBUTE_CUSTOM = 1.shl(0)

fun getMostProperCar(selectedCars: List<CarInfo>?, recognizedNumber: CarNumber): CarInfo? {
    if (selectedCars == null || selectedCars.isEmpty()) {
        return null
    }

    if (selectedCars.size == 1) {
        return selectedCars.first()
    } else {
        return selectedCars.maxBy {
            recognizedNumber.matchWithParts(it.numberPrefix, it.numberSuffix)
        }
    }
}


@Entity(tableName = "cars")
class CarInfo {

    @PrimaryKey(autoGenerate = true)
    var id: Int = 0

    @ColumnInfo(name = "model_name") var modelName: String = ""
    @ColumnInfo(name = "color") var color: String = ""
    @ColumnInfo(name= "number") var number: String = ""
    @ColumnInfo(name= "owner") var owner: String = ""
    @ColumnInfo(name = "phone") var phone: String = ""

    @ColumnInfo(name = "number_prefix") var numberPrefix: String? = null
    @ColumnInfo(name = "number_root") var numberRoot: String = ""
    @ColumnInfo(name = "number_suffix") var numberSuffix: String? = null
    @ColumnInfo(name = "number_attributes") var numberAttributes: Int = 0
}

@Dao
interface CarsDao {

    //Query with parameters. Kotlin renames params
    @Query("SELECT * FROM cars WHERE number_root = :numberRoot")
    fun loadByNumberRoot(numberRoot: String): List<CarInfo>

    @Query("SELECT Count(*) FROM cars")
    fun size(): Int

    @Insert
    fun insert(vararg cars: CarInfo)

    @Query("DELETE FROM cars")
    fun clear()
}


@Database(entities = arrayOf(CarInfo::class), version = 1, exportSchema = false)
abstract class CarsDatabase : RoomDatabase() {
    abstract fun carsDao(): CarsDao
}

class CarsDatabaseHolder(private val database: CarsDatabase, private val lock: ReadWriteLock) {
    fun lockRead(): CarsDatabase {
        lock.readLock().lock();
        return database;
    }

    fun unlockRead() = lock.readLock().unlock();

    inline fun read(block: (CarsDatabase) -> Unit) {
        val db = lockRead()
        try {
            block(db)
        } finally {
            unlockRead();
        }
    }

    fun lockWrite(): CarsDatabase {
        lock.writeLock().lock();
        return database;
    }

    fun unlockWrite() = lock.writeLock().unlock();

    inline fun write(block: (CarsDatabase) -> Unit) {
        val db = lockWrite()
        try {
            block(db)
        } finally {
            unlockWrite();
        }
    }

}
