package com.example.esrtestexercise

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.maps.android.PolyUtil
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import org.json.JSONArray
import org.json.JSONObject


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, PermissionListener {

    private val esrApi: String = "https://tatooine.eatsleepride.com/api/v5/feed/nearby?lat=40.6338031&lng=14.6002813"
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        fusedLocationProviderClient = FusedLocationProviderClient(this)

    }

    /*
    ~~~~To Do - Make this function less... gross...~~~~
     */

    // function for network call
    fun getData() {
        // Instantiate the RequestQueue
        val queue = Volley.newRequestQueue(this)

        // Request a string response from the provided URL
        val stringReq = StringRequest(
            com.android.volley.Request.Method.GET, esrApi,
            com.android.volley.Response.Listener<String> { response ->
                var strResp = response.toString()
                val jsonObj = JSONObject(strResp)

                val jsonArray: JSONArray = jsonObj.getJSONObject("payload").getJSONArray("items")
                for (i in 0 until jsonArray.length()) {
                    val item: JSONObject = jsonArray.getJSONObject(i)
                    val location: JSONObject = item.getJSONObject("location")
                    val ctype: String = item.getString("ctype")

                    // to avoid case type issues when comparing strings
                    if(ctype.toUpperCase() == "poi".toUpperCase()) { //POI
                        val poiLocation = LatLng(location.getDouble("lat"), location.getDouble("lng"))
                        val title: String = item.getString("title")
                        addPoi(poiLocation, title, BitmapDescriptorFactory.HUE_YELLOW)
                    }
                    else if(ctype.toUpperCase() == "route".toUpperCase()) { //Route
                        val data: JSONObject = item.getJSONObject("data")

                        // Polyline
                        addPolyline(data.getString("polyline"))

                        // Origin marker
                        val originJsonObj: JSONObject =  data.getJSONObject("origin")
                        val origin: LatLng = LatLng(originJsonObj.getDouble("lat"), originJsonObj.getDouble("lng"))
                        addPoi(origin, "Origin", BitmapDescriptorFactory.HUE_GREEN)

                        // Destination Marker
                        val destJsonObj: JSONObject = data.getJSONObject("destination")
                        val destination: LatLng = LatLng(destJsonObj.getDouble("lat"), destJsonObj.getDouble("lng"))
                        addPoi(destination, "Destination", BitmapDescriptorFactory.HUE_RED)
                    }
                }
            },
            // Error handler
            com.android.volley.Response.ErrorListener { Toast.makeText(this@MapsActivity, "Error with request", Toast.LENGTH_LONG)})
        queue.add(stringReq)
    }

    // creates a POI
    private fun addPoi(location: LatLng, title: String, markerBitmap: Float){
        // Add a marker
        mMap.addMarker(MarkerOptions()
            .position(location)
            .title(title)
            .icon(BitmapDescriptorFactory
                .defaultMarker(markerBitmap)))
    }

    // creates polyline
    private fun addPolyline(polyline: String){
        // Decode Polyline into LatLng List
        val points: List<LatLng> = PolyUtil.decode(polyline)
        val options = PolylineOptions().width(5f).color(Color.BLUE).geodesic(true)

        // Adding polyline
        options.addAll(points)
        mMap.addPolyline(options)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (isLocationPermissionGranted()){
            googleMap.isMyLocationEnabled = true
            getCurrentLocation()
        } else {
            // Dexter makes requesting permissions easier
            // https://github.com/Karumi/Dexter
            Dexter.withActivity(this)
                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(this)
                .check()
        }

        //gets API data and places on mMap
        getData()
    }

    /*
    ~~~~To do - Break the below into a separate class~~~~
     */

    private fun isLocationPermissionGranted(): Boolean{
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun getCurrentLocation() {
        val locationRequest = LocationRequest()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.interval = (2000).toLong() //assuming you're riding, set the time to every two seconds for faster updates
        locationRequest.fastestInterval = 1000

        val locationSettingsRequest = LocationSettingsRequest
            .Builder()
            .addLocationRequest(locationRequest)
            .build()

        val settingsClient = LocationServices
            .getSettingsClient(this)
            .checkLocationSettings(locationSettingsRequest)

        settingsClient.addOnCompleteListener { task ->
            try {
                val response = task.getResult(ApiException::class.java)
                if (response!!.locationSettingsStates.isLocationPresent){
                    moveCameraToCurrentLocation()
                }
            } catch (exception: Exception) {
                Toast.makeText(this, "Getting location failed.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun moveCameraToCurrentLocation(){
        //move camera to current location
        fusedLocationProviderClient.lastLocation
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful && task.result != null) {
                    var location: Location? = task.result

                    val cameraPosition = CameraPosition.Builder()
                        .target(LatLng(location!!.latitude, location!!.longitude))
                        .zoom(6f)
                        .build()

                    mMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                } else {
                    Toast.makeText(this, "Current location not found", Toast.LENGTH_LONG).show()
                }
            }
    }

    override fun onPermissionGranted(response: PermissionGrantedResponse?) {
        getCurrentLocation()
        mMap.isMyLocationEnabled = true
    }

    override fun onPermissionRationaleShouldBeShown(permission: PermissionRequest?, token: PermissionToken?) {
        token!!.continuePermissionRequest()
    }

    override fun onPermissionDenied(response: PermissionDeniedResponse?) {
        Toast.makeText(this, "Permission required for showing location", Toast.LENGTH_LONG).show()
        finish()
    }
}
