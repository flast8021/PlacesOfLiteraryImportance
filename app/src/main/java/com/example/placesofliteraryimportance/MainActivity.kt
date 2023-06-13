package com.example.placesofliteraryimportance

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.service.controls.ControlsProviderService.TAG
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.placesofliteraryimportance.R
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private const val LOCATION_PERMISSION_REQUEST_CODE = 1

class MainActivity : AppCompatActivity(), GoogleMap.OnInfoWindowClickListener {

    private lateinit var googleMap: GoogleMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync { map ->
            googleMap = map
            loadPlacesAndDisplayMarkers()
            googleMap.setOnInfoWindowClickListener(this)
        }
    }
    override fun onInfoWindowClick(marker: Marker) {
        val placeId = marker.tag.toString()
        Log.i(TAG,"Info Window Clicked $placeId")
        showPlaceDetails(placeId)
    }

    private fun showPlaceDetails(placeId: String) {

    }

    private fun checkPermissions()
    {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) { } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                  //  initializeMap()
                    //loadPlacesAndDisplayMarkers()
                } else {
                    Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
    }

    private fun fetchJSONData(url: String, callback: (JSONArray?) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to fetch JSON data from $url", e)
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val jsonData = response.body?.string()
                    val jsonArray = JSONArray(jsonData)
                    callback(jsonArray)
                    Log.d(TAG, "JSON Data: $jsonData")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse JSON data from $url", e)
                    callback(null)
                }
            }
        })
    }

    private fun loadPlacesAndDisplayMarkers() {
        val placeUrl =
            "https://gist.githubusercontent.com/saravanabalagi/541a511eb71c366e0bf3eecbee2dab0a/raw/bb1529d2e5b71fd06760cb030d6e15d6d56c34b3/places.json"
        val placeTypesUrl =
            "https://gist.githubusercontent.com/saravanabalagi/541a511eb71c366e0bf3eecbee2dab0a/raw/bb1529d2e5b71fd06760cb030d6e15d6d56c34b3/place_types.json"

        fetchJSONData(placeTypesUrl) { placeTypesArray ->
            fetchJSONData(placeUrl) { placesArray ->
                placeTypesArray?.let { types ->
                    placesArray?.let { places ->
                        displayMarkers(types, places)
                    }
                }
            }
        }
    }

    private fun displayMarkers(placeTypes: JSONArray, places: JSONArray) {
        val colors = arrayOf(
            BitmapDescriptorFactory.HUE_RED,
            BitmapDescriptorFactory.HUE_ORANGE,
            BitmapDescriptorFactory.HUE_YELLOW,
            BitmapDescriptorFactory.HUE_GREEN,
            BitmapDescriptorFactory.HUE_CYAN,
            BitmapDescriptorFactory.HUE_BLUE,
            BitmapDescriptorFactory.HUE_MAGENTA,
            BitmapDescriptorFactory.HUE_ROSE,
            BitmapDescriptorFactory.HUE_VIOLET,
            BitmapDescriptorFactory.HUE_AZURE,
            BitmapDescriptorFactory.HUE_BLUE,
            BitmapDescriptorFactory.HUE_GREEN,
            BitmapDescriptorFactory.HUE_YELLOW,
            BitmapDescriptorFactory.HUE_ORANGE,
            BitmapDescriptorFactory.HUE_ROSE
        )
        runOnUiThread {
            for (i in 0 until places.length()) {
                val placeObject = places.getJSONObject(i)
                val placeName = placeObject.getString("name")
                val placeType = placeObject.getString("place_type_id")
                val gaelic_name = placeObject.getString("gaelic_name")
                val placeId = placeObject.getString("id")

                val latitude = placeObject.getDouble("latitude")
                val longitude = placeObject.getDouble("longitude")

                val location = LatLng(latitude, longitude)

                // Find the index of the place type in the placeTypes array
                val placeTypeIndex = findPlaceTypeIndex(placeTypes, placeType)

                // Set the marker color based on the place type index
                val markerColor = colors[placeTypeIndex % colors.size]

              //  initializeMap()
                val marker=
                googleMap.addMarker(
                    MarkerOptions()
                        .position(location)
                        .title(placeName)
                        .snippet(gaelic_name.toString())
                        .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
                )
                if (marker != null) {
                    marker.tag = placeId
                }
            }
        }
    }
    private fun findPlaceTypeIndex(placeTypes: JSONArray, type: String): Int {
        for (i in 0 until placeTypes.length()) {
            val placeTypeObject = placeTypes.getJSONObject(i)
            if (placeTypeObject.getString("id") == type) {
                return i
            }
        }
        return -1
    }
}