package blue.eaudouce.waybiker.util

import com.mapbox.geojson.Point
import com.mapbox.maps.CoordinateBounds
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.tan

object MapTiling {

    // Determines how big tiles are
    const val ZOOM_LEVEL = 16

    // From https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames
    fun longitudeToTileX(lon: Double): Int {
        return floor((lon + 180.0) / 360.0 * (1 shl ZOOM_LEVEL)).toInt();
    }

    fun latitudeToTileY(lat: Double): Int {
        val latRad = lat * PI / 180.0;
        return floor((1.0 - asinh(tan(latRad)) / PI) / 2.0 * (1 shl ZOOM_LEVEL)).toInt();
    }

    fun tileXToLongitude(x: Int): Double {
        return x / (1 shl ZOOM_LEVEL).toDouble() * 360.0 - 180;
    }

    fun tileYToLatitude(y: Int): Double {
        val n = PI - 2.0 * PI * y / (1 shl ZOOM_LEVEL).toDouble();
        return 180.0 / PI * atan(0.5 * (exp(n) - exp(-n)));
    }

    data class MapTile(
        var x: Int,
        var y: Int
    )

    data class TileBounds(
        var min: MapTile,
        var max: MapTile
    )

    fun TileBounds.forEachTile(function: (MapTile) -> Unit) {
        for (i in min.x until max.x) {
            for (j in min.y downTo max.y + 1) {
                function(MapTile(i, j))
            }
        }
    }

    fun pointToMapTile(point: Point): MapTile {
        return MapTile(
            longitudeToTileX(point.longitude()),
            latitudeToTileY(point.latitude())
        )
    }

    fun mapTileToPoint(mapTile: MapTile): Point {
        return Point.fromLngLat(
            tileXToLongitude(mapTile.x),
            tileYToLatitude(mapTile.y),
        )
    }

    fun coordinateBoundsToTileBounds(coordinateBounds: CoordinateBounds): TileBounds {
        val minBounds = coordinateBounds.southwest
        val maxBounds = coordinateBounds.northeast

        val tileBounds = TileBounds(
            pointToMapTile(minBounds),
            pointToMapTile(maxBounds)
        )

        tileBounds.max.x += 1
        tileBounds.max.y -= 1

        return tileBounds
    }

//    fun tileBoundsToCoordinateBounds(tileBounds: TileBounds): CoordinateBounds {
//        val southwest = mapTileToPoint(
//            TileBounds(
//                MapTile(tileBounds.min.x),
//                MapTile()
//            )
//        )
//        return CoordinateBounds(
//            mapTileToPoint(tileBounds.min),
//            mapTileToPoint(tileBounds.max),
//        )
//    }

    fun MapTile.toCoordinateBounds(): CoordinateBounds {
        val southwest = mapTileToPoint(MapTile(x, y + 1))
        val northeast = mapTileToPoint(MapTile(x + 1, y))

        return CoordinateBounds(southwest, northeast)
    }
}