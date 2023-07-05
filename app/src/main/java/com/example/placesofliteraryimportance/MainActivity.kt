package com.example.placesofliteraryimportance

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.service.controls.ControlsProviderService.TAG
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONArray
import java.io.IOException

data class Place(
    val id: String,
    val name: String,
    val gaelicName: String,
    val type: String,
    val latitude: Double,
    val longitude: Double
)

private const val LOCATION_PERMISSION_REQUEST_CODE = 1

class MainActivity : AppCompatActivity(), GoogleMap.OnInfoWindowClickListener,
    GoogleMap.OnMapLongClickListener {

    //private variables
    private lateinit var googleMap: GoogleMap
    private var circle: Circle? = null
    private var marker: Marker? = null
    private var markerCreated: Boolean = false
    private var newProgress: Int = 10
    private var radiusSeekBar: SeekBar? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        checkPermissions()

        val mapFragment =
            supportFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
        mapFragment.getMapAsync { map ->
            googleMap = map
            loadPlacesAndDisplayMarkers()

            markerCreated = false


            googleMap.setOnInfoWindowClickListener(this)
            googleMap.setOnMapLongClickListener(this)

            val items = resources.getStringArray(R.array.place_types)
            val adapter = CustomArrayAdapter(this, items)

            val spinner = findViewById<Spinner>(R.id.spinner_place_type)
            spinner.adapter = adapter

            radiusSeekBar = findViewById(R.id.seekbar_radius)
            radiusSeekBar?.progress = 10 // Set default radius to 10 km

            radiusSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {

                    if (markerCreated) {
                        updateCircleRadius(progress)
                    }
                    val radiusText = "Radius Size: $progress km"
                    newProgress = progress

                    text_radius.text = radiusText
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {

                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    // Do nothing

                }
            })
        }
    }

    // Back Button
    override fun onBackPressed() {
        finish()
    }

    //Long Click
    override fun onMapLongClick(latLng: LatLng) {

        //custom marker
        val customMarkerDrawable = ContextCompat.getDrawable(this, R.drawable.custom_marker)
        val bitmap = customMarkerDrawable?.toBitmap()
        val color = ContextCompat.getColor(this, R.color.azure)
        val modifiedBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, true)

        modifiedBitmap?.let {
            val canvas = Canvas(it)
            val paint = Paint()
            paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(it, 0f, 0f, paint)
        }

        val customMarker = modifiedBitmap?.let { BitmapDescriptorFactory.fromBitmap(it) }
        //removing marker and circle
        circle?.remove()
        marker?.remove()

        //Adding Marker
        marker = googleMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .icon(customMarker)
                .draggable(true)
        )

        markerCreated = true

        val defaultRadius = newProgress

        //Adding Circle
        val circleOptions =
            CircleOptions()
                .visible(true)
                .center(latLng)
                .radius(defaultRadius.toDouble() * 1000)
                .strokeWidth(2f)
                .strokeColor(Color.BLUE)
                .fillColor(Color.argb(100, 0, 0, 255))
        circle = googleMap.addCircle(circleOptions)

        //setting drag listener
        googleMap.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
            override fun onMarkerDrag(marker: Marker) {

            }

            override fun onMarkerDragStart(marker: Marker) {
                //Removing existing circle
                circle?.remove()
            }

            override fun onMarkerDragEnd(marker: Marker) {

                val radius = radiusSeekBar?.progress ?: 10
                updateCircleRadius(radius)
            }
        })
    }

    private fun updateCircleRadius(radius: Int) {

        // Remove existing circle if it exists
        circle?.remove()

        // Getting the marker position
        val markerPosition = marker?.position

        // Create new circle with updated radius
        markerPosition?.let { position ->
            val circleOptions = CircleOptions()
                .visible(true)
                .center(position)
                .radius(radius.toDouble() * 1000) // Convert km to meters
                .strokeWidth(2f)
                .strokeColor(Color.BLUE)
                .fillColor(Color.argb(100, 0, 0, 255))
            circle = googleMap.addCircle(circleOptions)
        }
    }

    //method to show places details
    private fun showPlaceDetails(placeId: String) {
        GlobalScope.launch(Dispatchers.Main) {
            val placeDetails = withContext(Dispatchers.IO) {
                getPlaceDetails(placeId)
            }

            val placeDetailsLayout = layoutInflater.inflate(R.layout.place_details_layout, null)

            val textViewId = placeDetailsLayout.findViewById<TextView>(R.id.text_view_id)
            val textViewName = placeDetailsLayout.findViewById<TextView>(R.id.text_view_name)
            val textViewGaelicName =
                placeDetailsLayout.findViewById<TextView>(R.id.text_view_gaelic_name)
            val textViewType = placeDetailsLayout.findViewById<TextView>(R.id.text_view_type)
            val textViewCoordinates =
                placeDetailsLayout.findViewById<TextView>(R.id.text_view_coordinates)
            val backButton =
                placeDetailsLayout.findViewById<ImageButton>(R.id.backButton) // Change variable name to 'backButton'

            textViewId.text = "ID: ${placeDetails.id}"
            textViewName.text = "Name: ${placeDetails.name}"
            textViewGaelicName.text = "Gaelic Name: ${placeDetails.gaelicName}"
            textViewType.text = "Type: ${placeDetails.type}"
            textViewCoordinates.text =
                "Coordinates: ${placeDetails.latitude}, ${placeDetails.longitude}"

            val popupWindow = PopupWindow(
                placeDetailsLayout,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                true
            )
            popupWindow.showAtLocation(placeDetailsLayout, Gravity.CENTER, 0, 0)

            backButton.setOnClickListener {
                popupWindow.dismiss()
            }

            // Loading image from google maps using Picasso
            val placeImageView =
                placeDetailsLayout.findViewById<ImageView>(R.id.placeImageView) // Update ImageView ID
            val lat = placeDetails.latitude
            val long = placeDetails.longitude

            val apiKey = "AIzaSyAGhhMntnEe2hse94KEDzI7jrwpBEKDfXk"
            val imageUrl = "https://maps.googleapis.com/maps/api/staticmap" +
                    "?center=$lat,$long" +
                    "&zoom=12" +
                    "&size=400x300" +
                    "&key=$apiKey"
            Picasso.get().load(imageUrl).into(placeImageView)
        }
    }

    // fetching the JSON data from the provided URL
    private fun fetchJSONDataa(url: String): String {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        return response.body?.string() ?: ""
    }

    private fun getPlaceDetails(placeId: String): Place {
        // Loading the JSON data from the provided URL
        val url =
            "https://gist.githubusercontent.com/saravanabalagi/541a511eb71c366e0bf3eecbee2dab0a/raw/bb1529d2e5b71fd06760cb030d6e15d6d56c34b3/places.json"
        val jsonString = fetchJSONDataa(url)

        // Parseing the JSON string to retrieve the place details
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val placeObject = jsonArray.getJSONObject(i)
            if (placeObject.getString("id") == placeId) {

                // Extracting the details from the JSON object
                val id = placeObject.getString("id")
                val name = placeObject.getString("name")
                val gaelicName = placeObject.getString("gaelic_name")
                val type = placeObject.getString("place_type_id")
                val latitude = placeObject.getDouble("latitude")
                val longitude = placeObject.getDouble("longitude")

                // Creating a Place object with the retrieved details
                return Place(id, name, gaelicName, type, latitude, longitude)
            }
        }

        // Returning a default Place object if the place details are not found
        return Place("", "", "", "", 0.0, 0.0)
    }

    //Marker info window click
    override fun onInfoWindowClick(marker: Marker) {
        val placeId = marker.tag.toString()
        Log.i(TAG, "Info Window Clicked $placeId")
        showPlaceDetails(placeId)
    }

    //permissions
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private fun retrieveLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    // Using the obtained location coordinates
                    val latitude = location.latitude
                    val longitude = location.longitude

                    val currentLocation = LatLng(latitude, longitude)

                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 8f))

                } else {
                }
            }
            .addOnFailureListener { exception: Exception ->
                //if Failed to retrieve location,gonna handle accordingly
            }
    }

    //permissions check method
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            retrieveLocation()
        } else {
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
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Location permission granted, proceeding with location retrieval
            retrieveLocation()
        } else {
            // Location permission denied
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //  initializeMap()
                    //loadPlacesAndDisplayMarkers()
                } else {
                    Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
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
// URLs for fetching place types and places JSON data
        val placeUrl =
            "https://gist.githubusercontent.com/saravanabalagi/541a511eb71c366e0bf3eecbee2dab0a/raw/bb1529d2e5b71fd06760cb030d6e15d6d56c34b3/places.json"
        val placeTypesUrl =
            "https://gist.githubusercontent.com/saravanabalagi/541a511eb71c366e0bf3eecbee2dab0a/raw/bb1529d2e5b71fd06760cb030d6e15d6d56c34b3/place_types.json"


        fetchJSONData(placeTypesUrl) { placeTypesArray ->
            fetchJSONData(placeUrl) { placesArray ->
                placeTypesArray?.let { types ->
                    placesArray?.let { places ->
                        // Display markers based on place types and places
                        displayMarkers(types, places)
                        spinner_place_type.onItemSelectedListener =
                            object : AdapterView.OnItemSelectedListener {
                                override fun onItemSelected(
                                    parent: AdapterView<*>,
                                    view: View?,
                                    position: Int,
                                    id: Long
                                ) {
                                    val arrayPlacee = resources.getStringArray(R.array.place_types)
                                    val selectedPlaceType = parent.getItemAtPosition(position)
                                    val positionOfSelectedPlace =
                                        arrayPlacee.indexOf(selectedPlaceType)
                                    if (selectedPlaceType == "All") {
                                        // Clearing and then Displaying markers for all places
                                        googleMap.clear()
                                        displayMarkers(placeTypesArray, placesArray)

                                    } else {
                                        // Clearing markers, Filtering places based on selected place type and displaying markers
                                        googleMap.clear()

                                        val filteredPlaces = filterPlacesByType(
                                            positionOfSelectedPlace.toString(),
                                            placesArray
                                        )

                                        containsPlaceType(placesArray, selectedPlaceType.toString())
                                        displayMarkers(placeTypesArray, filteredPlaces)

                                    }
                                }

                                override fun onNothingSelected(parent: AdapterView<*>) {
                                    // Do nothing
                                }
                            }
                    }
                }
            }
        }
    }

    private fun filterPlacesByType(placeType: String, places: JSONArray): JSONArray {
        val filteredPlaces = JSONArray()
        for (i in 0 until places.length()) {
            val placeObject = places.getJSONObject(i)
            val placeTypeId = placeObject.getString("place_type_id")

            if (placeTypeId == placeType) {
                //  placesFiltered = true
                filteredPlaces.put(placeObject)

            }
        }
        val count = filteredPlaces.length()
        Toast.makeText(this, "Total $count places found.", Toast.LENGTH_SHORT).show()
        return filteredPlaces
    }

    private fun containsPlaceType(placeTypeIds: JSONArray, placeType: String): Boolean {
        for (i in 0 until placeTypeIds.length()) {
            if (placeTypeIds.getString(i) == placeType) {
                return true
            }
        }
        return false
    }

    private fun displayMarkers(placeTypes: JSONArray, places: JSONArray) {
        val red = ContextCompat.getColor(this, R.color.red)
        val orange = ContextCompat.getColor(this, R.color.orange)
        val yellow = ContextCompat.getColor(this, R.color.yellow)
        val green = ContextCompat.getColor(this, R.color.green)
        val cyan = ContextCompat.getColor(this, R.color.cyan)
        val blue = ContextCompat.getColor(this, R.color.blue)
        val magenta = ContextCompat.getColor(this, R.color.magenta)
        val rose = ContextCompat.getColor(this, R.color.rose)
        val violet = ContextCompat.getColor(this, R.color.violet)
        val azure = ContextCompat.getColor(this, R.color.azure)
        val olive = ContextCompat.getColor(this, R.color.olive)
        val gray = ContextCompat.getColor(this, R.color.gray)
        val maroon = ContextCompat.getColor(this, R.color.maroon)
        val teal = ContextCompat.getColor(this, R.color.teal)
        val navy = ContextCompat.getColor(this, R.color.navy)

        val colors = arrayOf(
            red,
            orange,
            yellow,
            green,
            cyan,
            blue,
            magenta,
            rose,
            violet,
            azure,
            olive,
            gray,
            maroon,
            teal,
            navy
        )
        val colorCount = colors.size

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

                val customMarkerDrawable = ContextCompat.getDrawable(this, R.drawable.custom_marker)
                val bitmap = customMarkerDrawable?.toBitmap()
                val modifiedBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, true)

                // Finding the index of the place type in the placeTypes array
                val placeTypeIndex = findPlaceTypeIndex(placeTypes, placeType)

                modifiedBitmap?.let {
                    val canvas = Canvas(it)
                    val paint = Paint()
                    paint.colorFilter =
                        PorterDuffColorFilter(
                            colors[placeTypeIndex % colorCount],
                            PorterDuff.Mode.SRC_IN
                        )
                    canvas.drawBitmap(it, 0f, 0f, paint)
                }

                val customMarker = modifiedBitmap?.let { BitmapDescriptorFactory.fromBitmap(it) }
                googleMap.addMarker(
                    MarkerOptions()
                        .position(location)
                        .title(placeName)
                        .snippet(gaelic_name.toString())
                        .icon(customMarker)
                )?.apply {
                    tag = placeId
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

class CustomArrayAdapter(context: Context, items: Array<String>) :
    ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textView = view.findViewById<TextView>(android.R.id.text1)

        // Customizing the text color of the selected item
        textView.setTextColor(ContextCompat.getColor(context, R.color.white))
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)

        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent)
        val textView = view.findViewById<TextView>(android.R.id.text1)

        // Customizing the text color and background of each item in the dropdown
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        textView.setPadding(0, 15, 0, 15)
        textView.gravity = Gravity.CENTER

        return view
    }
}

