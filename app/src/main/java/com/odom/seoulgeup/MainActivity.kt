package com.odom.seoulgeup

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.AsyncTask
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterManager
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.search_bar.view.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import kotlin.math.pow
import kotlin.system.exitProcess


class MainActivity : AppCompatActivity() {

    var PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.INTERNET
        )

    val REQUEST_PERMISSION_CODE = 1
    val DEFAULT_ZOOM_LEVEL = 16f
    val CITY_HALL = LatLng(37.566648, 126.978449)
    var googleMap: GoogleMap? = null

    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // 인터넷 연결 안되어있으면
        // 알림 후 종료
        if(!checkInternetConnection()){
            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setTitle("인터넷 연결을 확인해주세요 ")
                .setPositiveButton("확인") { _, _ ->
                    finish()
                    exitProcess(0)
                }

            val alertDialog = builder.create()
            alertDialog.show()
        }

        setContentView(R.layout.activity_main)
        mapView.onCreate(savedInstanceState)
        MapsInitializer.initialize(applicationContext)

        //권한 요청
        ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMISSION_CODE)

        // 현재 위치 버튼 리스너
        myLocationButton.setOnClickListener { onMyLocationButtonClick() }
    }

    // 인터넷 연결 확인
    fun checkInternetConnection() : Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = cm.activeNetworkInfo

        if (activeNetwork != null)
            return true

        return false
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

    // clusterManager 변수
    var clusterManager : ClusterManager<MyItem>? = null
    // clusterRenderer 변수
    var clusterRenderer : ClusterRenderer? = null

    @SuppressLint("MissingPermission")
    fun initMap(){
        // 맵뷰에서 구글 맵을 불러옴
        mapView.getMapAsync {

            // cluster 객체 초기화
            clusterManager = ClusterManager(this, it)
            clusterRenderer = ClusterRenderer(this, it, clusterManager)

            //
            it.setOnCameraIdleListener(clusterManager)
            it.setOnMarkerClickListener(clusterManager)

            googleMap = it
            it.uiSettings.isMyLocationButtonEnabled = false

            when{
                hasPermissions() ->{
                    it.isMyLocationEnabled = true
                    it.moveCamera(CameraUpdateFactory.newLatLngZoom(getMyLocation(), DEFAULT_ZOOM_LEVEL))
                }
                else ->{
                    it.moveCamera(CameraUpdateFactory.newLatLngZoom(CITY_HALL, DEFAULT_ZOOM_LEVEL))
                }
            }
        }
    }

    @SuppressLint("MissingPermission") // MissingPermission 문제의 Lint 검사 중지
    fun getMyLocation() : LatLng{
        val locationProvider : String = LocationManager.GPS_PROVIDER
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var lastKnownLocation : Location? = locationManager.getLastKnownLocation(locationProvider)

        // 내 폰에선 이게 되고
        if(lastKnownLocation == null){
            fusedLocationProviderClient.lastLocation
                .addOnSuccessListener { location ->
                    if(location == null) {
                        Log.d("TAG", "location get fail")
                    } else {
                        lastKnownLocation = location
                        Log.d("hhh", "${location.latitude} , ${location.longitude}")

                        val myLoc = LatLng(location.latitude, location.longitude)
                        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(myLoc, DEFAULT_ZOOM_LEVEL))
                    }
                }

        }
        // 안드로이드 10 버전에선 이게 되고
        else{
            Log.d("zzz", "${lastKnownLocation!!.latitude} , ${lastKnownLocation!!.longitude}")

            val myLoc = LatLng(lastKnownLocation!!.latitude, lastKnownLocation!!.longitude)
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(myLoc, DEFAULT_ZOOM_LEVEL))
        }

        // 경도, 위도 위치 반환
        if(lastKnownLocation == null){
            //Toast.makeText(applicationContext, "위치를 가져오지 못 했습니다.", Toast.LENGTH_SHORT).show()
            Log.d("TAG", "위치 확인불가")
            return LatLng(CITY_HALL.latitude, CITY_HALL.longitude)
        }

        return LatLng(lastKnownLocation!!.latitude, lastKnownLocation!!.longitude)
    }

    fun onMyLocationButtonClick(){
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        when{
            hasPermissions() ->{
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(getMyLocation(), DEFAULT_ZOOM_LEVEL))
                Log.d("TAG", "권한있음"+" 위치 :"+getMyLocation().toString())

                // 권한은 있는데 GPS 꺼져있으면 켜는 화면으로 이동
                if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                    val builder = AlertDialog.Builder(this@MainActivity)
                    builder.setTitle("GPS가 꺼져있습니다.")
                        .setPositiveButton("확인") { _, _ ->
                            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            intent.addCategory(Intent.CATEGORY_DEFAULT)
                            startActivity(intent)
                        }
                        .setNegativeButton("취소") {_, _ ->
                        }

                    val alertDialog = builder.create()
                    alertDialog.show()
                }
            }

            else -> {
                val builder = AlertDialog.Builder(this@MainActivity)
                builder.setTitle("위치 사용권한에 동의해주세요.")
                    .setPositiveButton("동의하기") { dialog, which ->
                        //권한 요청
                        ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMISSION_CODE)
                    }
                    .setNegativeButton("거절") {_, _ ->
                        Toast.makeText(applicationContext, "위치 사용권한에 동의하지 않았습니다", Toast.LENGTH_SHORT).show()
                        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(CITY_HALL, DEFAULT_ZOOM_LEVEL))
                    }

                val alertDialog = builder.create()
                alertDialog.show()
            }
        }
    }

    // 맵뷰의 라이프사이클 함수 호출
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        //  앱 AsyncTask 중지되었으면
        if(ToiletReadTask().status == AsyncTask.Status.FINISHED)
            ToiletReadTask().execute()
    }
    override fun onPause() {
        mapView.onPause()
        super.onPause()
        // 앱 AsyncTask도 pause
        if(ToiletReadTask().status == AsyncTask.Status.RUNNING)
            ToiletReadTask().cancel(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        // 앱 종료시 AsyncTask도 종료
        if(ToiletReadTask().status == AsyncTask.Status.RUNNING)
            ToiletReadTask().cancel(true)
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
    // JSONobject를 키로 MyItem 객체를 저장할 맵
    val itemMap = mutableMapOf<JSONObject, MyItem>()

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
    fun readData(startIndex:Int, lastIndex:Int) : JSONObject {
        val url =
            URL(
                "http://openAPI.seoul.go.kr:8088" + "/" +
                        "${API_KEY}/json/SearchPublicToiletPOIService/${startIndex}/${lastIndex}"
            )
        val connection = url.openConnection()

        val data = connection.getInputStream().readBytes().toString(charset("UTF-8"))
        return JSONObject(data)
    }

    // 화장실 데이터를 읽어오는 AsyncTask
    @SuppressLint("StaticFieldLeak")
    inner class ToiletReadTask : AsyncTask<Void, JSONArray, String>() {

        // 기존 데이터 초기화
        override fun onPreExecute() {
            // 구글맵 마커 초기화
            googleMap?.clear()
            // 화장실 정보 초기화
            toilets = JSONArray()
            // itemMap 변수 초기화
            itemMap.clear()
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

            // clusterManager의 클러스터링 실행
            clusterManager?.cluster()
        }

        // 백그라운드 작업이 끝난 후 실행
        override fun onPostExecute(result: String?) {
            // 자동완성 텍스트뷰에서 사용할 텍스트 리스트
            val textList = mutableListOf<String>()

            // 모든 화장실의 이름을 리스트에 추가
            for(i in 0 until toilets.length()){
                val toilet = toilets.getJSONObject(i)
                textList.add(toilet.getString("FNAME"))
            }

            // 자동완성 텍스트뷰의 어댑터 추가
            val adapter = ArrayAdapter<String>(
                this@MainActivity,
                android.R.layout.simple_dropdown_item_1line, textList
            )

            // 자동완성이 시작되는 글자수
            searchBar.autoCompleteTextView.threshold = 1
            // 자동완성 텍스트뷰의 어댑터 설정
            searchBar.autoCompleteTextView.setAdapter(adapter)
        }
    }

    // JSONArray에서 원소의 속성으로 검색
    fun JSONArray.findByChildProperty(propertyName : String, value : String) : JSONObject?{
        for(i in 0 until length()){
            val obj = getJSONObject(i)
            if(value == obj.getString(propertyName))
                return obj
        }
        return null
    }

    // 앱이 활성화될때마다 데이터를 읽어옴
    override fun onStart() {
        super.onStart()
        task?.cancel(true)
        task = ToiletReadTask()

        // 인터넷 연결이 있을시에만 AsyncTask 실행★
        if(checkInternetConnection()){
            Log.d("TAG", "task EXECUTE")
            task?.execute()
        }


        // searchbar 검색 리스너 설정
        searchBar.imageView.setOnClickListener {
            val word = searchBar.autoCompleteTextView.text.toString()
            // 값이 없으면 그대로 리턴
            if(TextUtils.isEmpty(word))
                return@setOnClickListener

            // 검색 키워드에 해당하는 jsonobject 검색
            toilets.findByChildProperty("FNAME", word)?.let{
                val myItem = itemMap[it]

                // clusterRenderer에서 myItem을 기반으로 마커 검색
                val marker = clusterRenderer?.getMarker(myItem)
                marker?.showInfoWindow()

                // 마커 위치로 카메라 이동
                googleMap?.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(it.getDouble("Y_WGS84"), it.getDouble("X_WGS84")), DEFAULT_ZOOM_LEVEL
                    )
                )
                clusterManager?.cluster()
            }

            // 검색 텍스트 초기화
            searchBar.autoCompleteTextView.setText("")
        }
    }

    // 좌표로 거리구하기
    fun getDistance( lat1: Double, lng1:Double, lat2:Double, lng2:Double) : Float{

        val myLoc = Location(LocationManager.NETWORK_PROVIDER)
        val targetLoc = Location(LocationManager.NETWORK_PROVIDER)
        myLoc.latitude= lat1
        myLoc.longitude = lng1

        targetLoc.latitude= lat2
        targetLoc.longitude = lng2

        return myLoc.distanceTo(targetLoc)
    }

    // 앱이 비활성화될때마다 백그라운드 작업취소
    override fun onStop() {
        super.onStop()
        task?.cancel(true)
        task = null
    }

    // 마커 추가
    fun addMarkers(toilets : JSONObject){
        val item = MyItem(
            LatLng(toilets.getDouble("Y_WGS84"), toilets.getDouble("X_WGS84")),
            toilets.getString("FNAME"),
            toilets.getString("ANAME"),
            BitmapDescriptorFactory.fromBitmap(bitmap)
        )

        // clusterManager를 이용해 마커 추가
        // 내 위치에서 500m내
        if(getDistance(getMyLocation().latitude, getMyLocation().longitude,
                toilets.getDouble("Y_WGS84"), toilets.getDouble("X_WGS84")) < 500 ){

            Log.d("거리" , getDistance(CITY_HALL.latitude, CITY_HALL.longitude,
                toilets.getDouble("Y_WGS84"), toilets.getDouble("X_WGS84")).toString())

            clusterManager?.addItem(
                MyItem(
                    LatLng(toilets.getDouble("Y_WGS84"), toilets.getDouble("X_WGS84")),
                    toilets.getString("FNAME"),
                    toilets.getString("ANAME"),
                    BitmapDescriptorFactory.fromBitmap(bitmap)
                )
            )

            //
            itemMap.put(toilets, item)
        }

    }
}