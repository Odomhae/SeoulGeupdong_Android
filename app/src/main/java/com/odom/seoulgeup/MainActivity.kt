    package com.odom.seoulgeup

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.location.LocationManager
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.getSystemService
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

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

                else -> {
                    Toast.makeText(applicationContext, "위치사용권한에 동의해주세요", Toast.LENGTH_SHORT).show()
                    googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(CITY_HALL, DEFAULT_ZOOM_LEVEL))
                }
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

        // 서울 열린 데이터 광장 발급 키
        val API_KEY = "4a4f64704a6a69683531797672504b"

        var task: ToiletReadTask ?= null
        // 화장실 정보 저장할 배열
        var toilets = JSONArray()
        // 화장실 이미지
        val bitmap by lazy {
            val drawable = resources.getDrawable(R.drawable.restroom_sign) as BitmapDrawable
            Bitmap.createScaledBitmap(drawable.bitmap, 64, 64, false)
        }

        // JsonArrray 병합
        fun JSONArray.merge(anotherArray:JSONArray){
            for(i in 0 until anotherArray.length())
                this.put(anotherArray.get(i))
        }

        // 화장실 정보를 읽어와서 JSONobject로 변환
        fun readData(startIndex:Int, lastIndex:Int) : JSONObject{
            val url =
                URL("http://openAPI.seoul.go.kr:8088"+"/" +
                        "${API_KEY}/json/SearchPublicToiletPOIService/${startIndex}/${lastIndex}")
            val connection = url.openConnection()

            val data = connection.getInputStream().readBytes().toString(charset("UTF-8"))
            return JSONObject(data)
        }

        // 화장실 데이터를 읽어오는 AsyncTask
        inner class ToiletReadTask : AsyncTask<Void, JSONArray, String>() {

            // 기존 데이터 초기화
            override fun onPreExecute() {
                // 구글맵 마커 초기화
                googleMap?.clear()
                // 화장실 정보 초기화
                toilets = JSONArray()
            }

            override fun doInBackground(vararg params: Void?): String {
                // 서울시 데이터는 최대 1000개씩 가져올 수 있으므로
                // 1000개씩 끊는다.
                val step = 1000
                var startIndex = 1
                var lastIndex = step
                var totalCnt = 0

                do {
                    // 백그라운드 작업이 취소되었을 때는 루프 종료
                    if (isCancelled)
                        break

                    if (totalCnt != 0) {
                        startIndex += step // 1000
                        lastIndex += step // 1000
                    }

                    val jsonObject = readData(startIndex, lastIndex)

                    totalCnt = jsonObject.getJSONObject("SearchPublicToiletPOIService")
                        .getInt("list_total_count")
                    //
                    val rows =
                        jsonObject.getJSONObject("SearchPublicToiletPOIService").getJSONArray("row")
                    // 기존에 읽었던 데이터와 병합
                    toilets.merge(rows)
                    //
                    publishProgress(rows)

                } while (lastIndex < totalCnt)

                return "complete"
            }

            // 데이터를 읽어올때마다 실행
            override fun onProgressUpdate(vararg values: JSONArray?) {
                // 0번째의 데이터 사용
                val array = values[0]
                array?.let {
                    for (i in 0 until array.length()) {
                        // 마커 추가
                        addMarkers(array.getJSONObject(i))

                    }
                }
            }
        }

        // 앱이 활성화될때마다 데이터를 읽어옴
        override fun onStart() {
            super.onStart()
            task?.cancel(true)
            task = ToiletReadTask()
            task?.execute()
        }

        // 앱이 비활성화될때마다 백그라운드 작업취소
        override fun onStop() {
            super.onStop()
            task?.cancel(true)
            task = null
        }

        // 마커 추가
        fun addMarkers(toilets : JSONObject){
            googleMap?.addMarker(
                MarkerOptions()
                    .position(LatLng(toilets.getDouble("Y_WGS84"), toilets.getDouble("X_WGS84")))
                    .title(toilets.getString("FNAME"))
                    .snippet(toilets.getString("ANAME"))
                    .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
            )

        }
}