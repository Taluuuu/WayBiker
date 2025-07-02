package blue.eaudouce.waybiker.map

class Intersection(
    val nodeId: Long
) {
    val connectingStreets = ArrayList<StreetBit>()
}