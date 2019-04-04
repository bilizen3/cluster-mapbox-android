package com.flores.cluster_mapbox_android

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.expressions.Expression
import com.mapbox.mapboxsdk.style.layers.CircleLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonOptions
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.utils.BitmapUtils
import java.net.MalformedURLException

class MainActivity : AppCompatActivity() {

    lateinit var mapboxMap: MapboxMap
    lateinit var mvCluster: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mapbox.getInstance(
            this,
            "pk.eyJ1IjoiYmlsaXplbjMiLCJhIjoiY2p1MW90MTZkMDN3MTN5bGtjb3FuaTllNSJ9.gltr3BTKf3zeqzxwPTKsrg"
        )
        setContentView(R.layout.activity_main)
        mvCluster = findViewById(R.id.mvCluster)
        mvCluster.onCreate(savedInstanceState)
        mvCluster.getMapAsync(
            OnMapReadyCallback { map ->
                mapboxMap = map

                map.setStyle(Style.LIGHT, Style.OnStyleLoaded { style ->
                    mapboxMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(
                                12.099, -79.045
                            ), 1.0
                        )
                    )

                    addClusteredGeoJsonSource(style)
                    style.addImage(
                        "cross-icon-id",
                        BitmapUtils.getBitmapFromDrawable(resources.getDrawable(R.drawable.ic_cross))!!,
                        true
                    )

                    Toast.makeText(
                        this, "Zoom in and out to see cluster numbers change",
                        Toast.LENGTH_SHORT
                    ).show()
                })
            }
        )
    }

    private fun addClusteredGeoJsonSource(loadedMapStyle: Style) {

        // Add a new source from the GeoJSON data and set the 'cluster' option to true.
        try {
            val feature =
                Feature.fromJson(" { \"type\": \"Feature\", \"properties\": { \"id\": \"ak16994521\", \"mag\": 2.3, \"time\": 1507425650893, \"felt\": null, \"tsunami\": 0 }, \"geometry\": { \"type\": \"Point\", \"coordinates\": [ -151.5129, 63.1016, 0.0 ] } }")
            val feature2 =
                Feature.fromJson("{ \"type\":\"Feature\", \"properties\":{ \"id\":\"ak16994519\", \"mag\":1.7, \"time\":1507425289659, \"felt\":null, \"tsunami\":0 }, \"geometry\":{ \"type\":\"Point\", \"coordinates\":[ -150.4048, 63.1224, 105.5 ] } }")

            val features = mutableListOf<Feature>()
            features.add(feature)
            features.add(feature2)

            val featuresJson =
                "{ \"type\": \"FeatureCollection\", \"features\": [ { \"type\": \"Feature\", \"properties\": { \"id\": \"ak16994521\", \"mag\": 2.3, \"time\": 1507425650893, \"felt\": null, \"tsunami\": 0 }, \"geometry\": { \"type\": \"Point\", \"coordinates\": [ -151.5129, 63.1016, 0.0 ] } }, { \"type\": \"Feature\", \"properties\": { \"id\": \"ak16994519\", \"mag\": 1.7, \"time\": 1507425289659, \"felt\": null, \"tsunami\": 0 }, \"geometry\": { \"type\": \"Point\", \"coordinates\": [ -150.4048, 63.1224, 105.5 ] } } ] }"
            val featureCollection = FeatureCollection.fromJson(featuresJson)
            val source = GeoJsonSource(
                "earthquakes", featureCollection, GeoJsonOptions()
                    .withCluster(true)
                    .withClusterMaxZoom(14)
                    .withClusterRadius(10)
            )
            loadedMapStyle.addSource(source)

        } catch (malformedUrlException: MalformedURLException) {
            Log.e("MalformedURLException", malformedUrlException.toString())
        }


        // Use the earthquakes GeoJSON source to create three layers: One layer for each cluster category.
        // Each point range gets a different fill color.
        val layers = arrayOf(
            intArrayOf(150, ContextCompat.getColor(this, R.color.colorAccent)),
            intArrayOf(20, ContextCompat.getColor(this, R.color.colorPrimary)),
            intArrayOf(0, ContextCompat.getColor(this, R.color.colorPrimaryDark))
        )

        //Creating a marker layer for single data points
        val unclustered = SymbolLayer("unclustered-points", "earthquakes")

        unclustered.setProperties(
            PropertyFactory.iconImage("cross-icon-id"),
            PropertyFactory.iconSize(
                Expression.division(
                    Expression.get("mag"), Expression.literal(4.0f)
                )
            ),
            PropertyFactory.iconColor(
                Expression.interpolate(
                    Expression.exponential(1), Expression.get("mag"),
                    Expression.stop(2.0, Expression.rgb(0, 255, 0)),
                    Expression.stop(4.5, Expression.rgb(0, 0, 255)),
                    Expression.stop(7.0, Expression.rgb(255, 0, 0))
                )
            )
        )
        loadedMapStyle.addLayer(unclustered)

        for (i in layers.indices) {
            //Add clusters' circles
            val circles = CircleLayer("cluster-$i", "earthquakes")
            circles.setProperties(
                PropertyFactory.circleColor(layers[i][1]),
                PropertyFactory.circleRadius(18f)
            )

            val pointCount = Expression.toNumber(Expression.get("point_count"))

            // Add a filter to the cluster layer that hides the circles based on "point_count"
            circles.setFilter(
                if (i == 0)
                    Expression.all(
                        Expression.has("point_count"),
                        Expression.gte(pointCount, Expression.literal(layers[i][0]))
                    )
                else
                    Expression.all(
                        Expression.has("point_count"),
                        Expression.gt(pointCount, Expression.literal(layers[i][0])),
                        Expression.lt(pointCount, Expression.literal(layers[i - 1][0]))
                    )
            )
            loadedMapStyle.addLayer(circles)
        }

        //Add the count labels
        val count = SymbolLayer("count", "earthquakes")
        count.setProperties(
            PropertyFactory.textField(Expression.toString(Expression.get("point_count"))),
            PropertyFactory.textSize(12f),
            PropertyFactory.textColor(Color.WHITE),
            PropertyFactory.textIgnorePlacement(true),
            PropertyFactory.textAllowOverlap(true)
        )
        loadedMapStyle.addLayer(count)

    }

    override fun onStart() {
        super.onStart()
        mvCluster.onStart()
    }

    override fun onResume() {
        super.onResume()
        mvCluster.onResume()
    }

    override fun onPause() {
        super.onPause()
        mvCluster.onPause()
    }

    override fun onStop() {
        super.onStop()
        mvCluster.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mvCluster.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mvCluster.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mvCluster.onSaveInstanceState(outState)
    }

}
