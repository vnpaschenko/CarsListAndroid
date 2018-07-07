package com.example.ipaschenko.carslist.data

import android.os.Parcel
import android.os.Parcelable

/**
 * Car info display representation used for show it in car details activity
 */
data class CarDetails(
        val id: Int, val modelName: String, val color: String, val number: String,
        val owner: String, val phone: String): Parcelable {

    constructor(car: CarInfo):
            this(car.id, car.modelName, car.color, car.number, car.owner, car.phone)

    constructor(parcel: Parcel) : this(
            parcel.readInt(),
            parcel.readString(),
            parcel.readString(),
            parcel.readString(),
            parcel.readString(),
            parcel.readString())

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeString(modelName)
        parcel.writeString(color)
        parcel.writeString(number)
        parcel.writeString(owner)
        parcel.writeString(phone)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<CarDetails> {
        override fun createFromParcel(parcel: Parcel): CarDetails {
            return CarDetails(parcel)
        }

        override fun newArray(size: Int): Array<CarDetails?> {
            return arrayOfNulls(size)
        }
    }
}
