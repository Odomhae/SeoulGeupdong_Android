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
import android.net.NetworkCapabilities
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.maps.android.clustering.ClusterManager
import android.widget.TextView
import com.odom.seoulgeup.databinding.ActivityMainBinding
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    var PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.INTERNET
        )

    val REQUEST_PERMISSION_CODE = 1
    val DEFAULT_ZOOM_LEVEL = 18f
    val CITY_HALL = LatLng(37.566648, 126.978449)
    var googleMap: GoogleMap? = null

    lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootView = this.findViewById<View>(android.R.id.content)
        if (rootView != null) {

            ViewCompat.setOnApplyWindowInsetsListener(rootView, OnApplyWindowInsetsListener { v, insets ->
                val systemBarSpacing = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(
                    systemBarSpacing.left,
                    systemBarSpacing.top,
                    systemBarSpacing.right,
                    systemBarSpacing.bottom
                )

                insets
            })
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val compat = WindowInsetsControllerCompat(this.window, this.window.decorView)
            compat.isAppearanceLightStatusBars = true
        }

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // 인터넷 연결 안되어있으면
        // 알림 후 종료
        if(!checkInternetConnection()){
            val builder = AlertDialog.Builder(this@MainActivity)
            builder.setTitle(R.string.check_internet)
                .setPositiveButton(R.string.check) { _, _ ->
                    finish()
                    exitProcess(0)
                }

            val alertDialog = builder.create()
            alertDialog.show()
        }

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.mapView.onCreate(savedInstanceState)
       // MapsInitializer.initialize(applicationContext)

        //권한 요청
        ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMISSION_CODE)

        // 현재 위치 버튼 리스너
        binding.myLocationButton.setOnClickListener { onMyLocationButtonClick() }

        // 광고 초기화
        initializeAds()
    }

    // 인터넷 연결 확인
    fun checkInternetConnection(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun initializeAds() {

        var mInterstitialAd: InterstitialAd? = null

        // AdMob 초기화
        MobileAds.initialize(this) {
            Log.d("test", "Ad loaded")
        }

        // 전면 광고 로드
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            this,
            getString(R.string.REAL_fullscreen_ad_unit_id),
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    mInterstitialAd = interstitialAd
                    mInterstitialAd?.show(this@MainActivity)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    mInterstitialAd = null
                }
            }
        )
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
        binding.mapView.getMapAsync {

            // cluster 객체 초기화
            clusterManager = ClusterManager(this, it)
            clusterRenderer = ClusterRenderer(this, it, clusterManager)

            clusterManager?.setOnClusterItemClickListener { item ->
                reverseItemMap[item]?.let { showToiletDetail(it) }
                true
            }

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

                        val myLoc = LatLng(location.latitude, location.longitude)
                        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(myLoc, DEFAULT_ZOOM_LEVEL))
                    }
                }

        }

        // 안드로이드 10 버전에선 이게 되고
        else{
            val myLoc = LatLng(lastKnownLocation!!.latitude, lastKnownLocation!!.longitude)
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(myLoc, DEFAULT_ZOOM_LEVEL))
        }

        // 경도, 위도 위치 반환
        if(lastKnownLocation == null){
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
                    builder.setTitle(R.string.check_gps)
                        .setPositiveButton(R.string.check) { _, _ ->
                            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            intent.addCategory(Intent.CATEGORY_DEFAULT)
                            startActivity(intent)
                        }
                        .setNegativeButton(R.string.cancel) {_, _ ->
                        }

                    val alertDialog = builder.create()
                    alertDialog.show()
                }
            }

            else -> {
                val builder = AlertDialog.Builder(this@MainActivity)
                builder.setTitle(R.string.check_permission)
                    .setPositiveButton(R.string.check) { _, _ ->
                        //권한 요청
                        ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMISSION_CODE)
                    }
                    .setNegativeButton(R.string.cancel) {_, _ ->
                        Toast.makeText(applicationContext, R.string.alert_location_permission, Toast.LENGTH_SHORT).show()
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
        binding.mapView.onResume()
    }
    override fun onPause() {
        binding.mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapView.onDestroy()
        fetchJob?.cancel()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }

    // 서울 열린 데이터 광장 발급 키
    val API_KEY = "6e795662766a6968353444666d6453" // 4a4f64704a6a69683531797672504b

    var fetchJob: Job? = null
    // 화장실 정보 저장할 배열
    var toilets = JSONArray()
    // JSONobject를 키로 MyItem 객체를 저장할 맵
    val itemMap = mutableMapOf<JSONObject, MyItem>()
    val reverseItemMap = mutableMapOf<MyItem, JSONObject>()

    // 화장실 이미지
    val bitmap by lazy {
        val drawable = ContextCompat.getDrawable(this, R.drawable.toilet_sign) as BitmapDrawable
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
                "http://openapi.seoul.go.kr:8088" + "/" +
                        "${API_KEY}/json/mgisToiletPoi/${startIndex}/${lastIndex}"
            )
        val connection = url.openConnection()

        val data = connection.getInputStream().readBytes().toString(charset("UTF-8"))
        return JSONObject(data)
    }

    private fun fetchToilets() {
        fetchJob?.cancel()
        fetchJob = lifecycleScope.launch {
            binding.progressIndicator.visibility = View.VISIBLE
            googleMap?.clear()
            toilets = JSONArray()
            itemMap.clear()
            reverseItemMap.clear()

            val step = 1000
            var startIndex = 1
            var lastIndex = step
            var totalCnt = 0

            try {
                do {
                    if (!isActive) break

                    if (totalCnt != 0) {
                        startIndex += step
                        lastIndex += step
                    }

                    val jsonObject = withContext(Dispatchers.IO) {
                        readData(startIndex, lastIndex)
                    }

                    val mgisObj = jsonObject.getJSONObject("mgisToiletPoi")
                    totalCnt = mgisObj.getInt("list_total_count")
                    val rows = mgisObj.getJSONArray("row")
                    toilets.merge(rows)

                    for (i in 0 until rows.length()) {
                        addMarkers(rows.getJSONObject(i))
                    }
                    clusterManager?.cluster()

                } while (lastIndex < totalCnt)

                setupSearchAdapter()
            } finally {
                binding.progressIndicator.visibility = View.GONE
            }
        }
    }

    private fun setupSearchAdapter() {
        val textList = mutableListOf<String>()
        for (i in 0 until toilets.length()) {
            textList.add(toilets.getJSONObject(i).getString("CONTS_NAME"))
        }
        val adapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            textList
        )
        binding.searchBar.autoCompleteTextView.threshold = 1
        binding.searchBar.autoCompleteTextView.setAdapter(adapter)
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
        if (checkInternetConnection()) {
            fetchToilets()
        }
        binding.searchBar.imageView.setOnClickListener {
            val word = binding.searchBar.autoCompleteTextView.text.toString()
            if (TextUtils.isEmpty(word)) return@setOnClickListener

            toilets.findByChildProperty("CONTS_NAME", word)?.let {
                val myItem = itemMap[it]
                val marker = clusterRenderer?.getMarker(myItem)
                marker?.showInfoWindow()
                googleMap?.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(it.getDouble("COORD_Y"), it.getDouble("COORD_X")),
                        DEFAULT_ZOOM_LEVEL
                    )
                )
                clusterManager?.cluster()
            }
            binding.searchBar.autoCompleteTextView.setText("")
        }
    }

    // 앱이 비활성화될때마다 백그라운드 작업취소
    override fun onStop() {
        super.onStop()
        fetchJob?.cancel()
    }

    // 마커 추가
    fun addMarkers(toilet: JSONObject) {
        val item = MyItem(
            LatLng(toilet.getDouble("COORD_Y"), toilet.getDouble("COORD_X")),
            toilet.getString("CONTS_NAME"),
            toilet.getString("CONTS_NAME"),
            BitmapDescriptorFactory.fromBitmap(bitmap)
        )
        clusterManager?.addItem(item)
        itemMap[toilet] = item
        reverseItemMap[item] = toilet
    }

    fun showToiletDetail(json: JSONObject) {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_toilet_detail, null)

        sheetView.findViewById<TextView>(R.id.tvName).text = json.optString("CONTS_NAME")

        val address = json.optString("RDNMADR").ifEmpty { json.optString("LNMADR") }
        if (address.isNotEmpty()) {
            sheetView.findViewById<TextView>(R.id.tvAddress).text = address
        } else {
            sheetView.findViewById<View>(R.id.rowAddress).visibility = View.GONE
        }

        val openTime = json.optString("OPNTIME")
        val closeTime = json.optString("CLSTIME")
        if (openTime.isNotEmpty() || closeTime.isNotEmpty()) {
            sheetView.findViewById<TextView>(R.id.tvHours).text = "$openTime ~ $closeTime"
        } else {
            sheetView.findViewById<View>(R.id.rowHours).visibility = View.GONE
        }

        val disabledYn = json.optString("DSBLED_TOILET_YN")
        if (disabledYn.isNotEmpty()) {
            sheetView.findViewById<TextView>(R.id.tvDisabled).text =
                if (disabledYn == "Y") "있음" else "없음"
        } else {
            sheetView.findViewById<View>(R.id.rowDisabled).visibility = View.GONE
        }

        val babyYn = json.optString("BABY_POTY_YN")
        if (babyYn.isNotEmpty()) {
            sheetView.findViewById<TextView>(R.id.tvBaby).text =
                if (babyYn == "Y") "있음" else "없음"
        } else {
            sheetView.findViewById<View>(R.id.rowBaby).visibility = View.GONE
        }

        dialog.setContentView(sheetView)
        dialog.show()
    }
}