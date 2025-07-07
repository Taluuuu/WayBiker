package blue.eaudouce.waybiker.map

class StreetBit(
    val nodeIds: List<Long>,
) {
    fun getEnds(): Pair<Long, Long> {
        assert(nodeIds.isNotEmpty())
        return Pair(nodeIds.first(), nodeIds.last())
    }

    fun getOtherEnd(end: Long): Long {
        val (e1, e2) = getEnds()
        return if (end == e1) e2 else e1
    }

    fun connectsIntersection(nodeId: Long): Boolean {
        val (e1, e2) = getEnds()
        return e1 == nodeId || e2 == nodeId
    }
}