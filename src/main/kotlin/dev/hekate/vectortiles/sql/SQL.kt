package dev.hekate.vectortiles.sql

fun getTileQuery(list: List<String>) = """
SELECT ST_AsMVT(q, '${list[3]}', 4096, 'geom')
FROM (
  SELECT id, name, type,
    ST_AsMvtGeom(
      geometry,
      TileBBox(${list[0]}, ${list[1]}, ${list[2]}, 3857),
      4096,
      256,
      true
    ) AS geom
  FROM import.osm_${list[3]}
  WHERE geometry && TileBBox(${list[0]}, ${list[1]}, ${list[2]}, 3857)
  AND ST_Intersects(geometry, TileBBox(${list[0]}, ${list[1]}, ${list[2]}, 3857))
) AS q;
          """.trimIndent()
