package com.odom.seoulgeup

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getSystemService
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    var PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    val REQUEST_PERMISSION_CODE = 1
    val DEFAULT_ZOOM_LEVEL = 17f
    val CITY_HALL = LatLng(37.566648, 126.978449)

    var googleMap: GoogleMap? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapView.onCreate(savedInstanceState)

        if(hasPermissions()){
            initMap()
        }else{
            //권한 요청
            ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMISSION_CODE)
        }

        // 현재 위치 버튼 리스너
        myLocationButton.setOnClickListener { onMyLocationButtonClick() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // 맵 초기화
        initMap()
    }

    // 권한 있나 체크
    fun hasPermissions() : Boolean{
        for(permisison in PERMISSIONS)
            if(ActivityCompat.checkSelfPermission(this, permisison) != PackageManager.PERMISSION_GRANTED)
                return false

        return true
    }

    fun initMap(){
        // 맵뷰에서 구글 맵을 불러옴
        mapView.getMapAsync {
            googleMap = it
            it.uiSettings.isMyLocationButtonEnabled = false
            it.isMyLocationEnabled = true

            // 현재위치로 카메라 이동
            it.moveCamera(CameraUpdateFactory.newLatLngZoom(getMyLocation() , DEFAULT_ZOOM_LEVEL))
            Log.d("위치", getMyLocation().latitude.toString())
            Log.d("위치", getMyLocation().longitude.toString())
        }
    }

    @SuppressLint("MissingPermission")
    fun getMyLocation() :LatLng{
        val locationProvider : String = LocationManager.GPS_PROVIDER
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val lastKnownLocation : Location = locationManager.getLastKnownLocation(locationProvider) //as Location

        // 경도 위도 위치 반환
        return LatLng(lastKnownLocation.latitude, lastKnownLocation.longitude)
    }

    fun onMyLocationButtonClick(){
        when{
            hasPermissions() ->{
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(getMyLocation(), DEFAULT_ZOOM_LEVEL))
                Log.d("위치2", getMyLocation().latitude.toString())
                Log.d("위치2", getMyLocation().longitude.toString())
            }

            else -> Toast.makeText(applicationContext, "위치사용권한에 동의해주세요", Toast.LENGTH_SHORT).show()
        }
    }

    // 맵뷰의 라이프사이클 함수 호출
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }
    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}
