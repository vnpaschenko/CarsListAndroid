package com.example.ipaschenko.carslist.data

import android.arch.persistence.room.*
import android.content.Context
import android.os.Parcel
import android.os.Parcelable

const val NUMBER_ATTRIBUTE_CUSTOM = 1.shl(0)

@Entity(tableName = "cars")
class CarInfo(): Parcelable {

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

    // Parcelable implementation
    constructor(parcel: Parcel) : this() {
        id = parcel.readInt()
        modelName = parcel.readString()
        color = parcel.readString()
        number = parcel.readString()
        owner = parcel.readString()
        phone = parcel.readString()
        numberPrefix = parcel.readString()
        numberRoot = parcel.readString()
        numberSuffix = parcel.readString()
        numberAttributes = parcel.readInt()
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeString(modelName)
        parcel.writeString(color)
        parcel.writeString(number)
        parcel.writeString(owner)
        parcel.writeString(phone)
        parcel.writeString(numberPrefix)
        parcel.writeString(numberRoot)
        parcel.writeString(numberSuffix)
        parcel.writeInt(numberAttributes)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<CarInfo> {
        override fun createFromParcel(parcel: Parcel): CarInfo {
            return CarInfo(parcel)
        }

        override fun newArray(size: Int): Array<CarInfo?> {
            return arrayOfNulls(size)
        }
    }
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
