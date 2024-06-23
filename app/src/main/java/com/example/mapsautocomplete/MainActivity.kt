package com.example.mapsautocomplete

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.maps.DirectionsApi
import com.google.maps.GeoApiContext
import com.google.maps.android.PolyUtil
import com.google.maps.model.DirectionsResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var placesClient: PlacesClient
    private lateinit var autoCompleteTextView: AutoCompleteTextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the SDK
        Places.initialize(applicationContext, "APIkey")
        placesClient = Places.createClient(this)

        // Set up the AutoCompleteTextView
        autoCompleteTextView = findViewById(R.id.autocomplete_text_view)
        autoCompleteTextView.threshold = 1 // Start suggesting after 1 character is typed

        // Set up the MapView
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapView) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Add a TextWatcher to the AutoCompleteTextView
        autoCompleteTextView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val token = AutocompleteSessionToken.newInstance()
                val request = FindAutocompletePredictionsRequest.builder()
                    .setSessionToken(token)
                    .setQuery(s.toString())
                    .build()

                placesClient.findAutocompletePredictions(request).addOnSuccessListener { response ->
                    val predictions = response.autocompletePredictions
                    val predictionList = predictions.map { it.getFullText(null).toString() }
                    val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, predictionList)
                    autoCompleteTextView.setAdapter(adapter)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Set up the item click listener for AutoCompleteTextView
        autoCompleteTextView.setOnItemClickListener { _, _, position, _ ->
            val prediction = autoCompleteTextView.adapter.getItem(position) as String
            searchPlaceAndDisplayRoute(prediction)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        // Move the camera to a default location
        val sydney = LatLng(-34.0, 151.0)
        mMap.addMarker(MarkerOptions().position(sydney).title("Marker in Sydney"))
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney))
    }

    private fun searchPlaceAndDisplayRoute(placeName: String) {
        val token = AutocompleteSessionToken.newInstance()
        val request = FindAutocompletePredictionsRequest.builder()
            .setSessionToken(token)
            .setQuery(placeName)
            .build()

        placesClient.findAutocompletePredictions(request).addOnSuccessListener { response ->
            val prediction = response.autocompletePredictions.firstOrNull() ?: return@addOnSuccessListener
            val placeId = prediction.placeId
            val placeRequest = FetchPlaceRequest.builder(placeId, listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)).build()

            placesClient.fetchPlace(placeRequest).addOnSuccessListener { placeResponse ->
                val place = placeResponse.place
                val latLng = place.latLng ?: return@addOnSuccessListener
                mMap.addMarker(MarkerOptions().position(latLng).title(place.name))
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                // Prompt for setting destination
                Toast.makeText(applicationContext, "Set this place as your destination?", Toast.LENGTH_LONG).show()

                // Start route planning
                CoroutineScope(Dispatchers.IO).launch {
                    val directionsResult = getDirections(LatLng(-34.0, 151.0), latLng) // Replace with your current location
                    withContext(Dispatchers.Main) {
                        if (directionsResult != null) {
                            // Display route on the map
                            for (route in directionsResult.routes) {
                                val polylineOptions = PolylineOptions().addAll(PolyUtil.decode(route.overviewPolyline.encodedPath))
                                mMap.addPolyline(polylineOptions)
                            }
                            // Prompt user to start navigation
                            Toast.makeText(applicationContext, "Route ready. Start navigation?", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    // Directions API call
    private suspend fun getDirections(origin: LatLng, destination: LatLng): DirectionsResult? {
        return try {
            val geoApiContext = GeoApiContext.Builder()
                .apiKey("YOUR_API_KEY")
                .build()
            DirectionsApi.newRequest(geoApiContext)
                .origin(com.google.maps.model.LatLng(origin.latitude, origin.longitude))
                .destination(com.google.maps.model.LatLng(destination.latitude, destination.longitude))
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
