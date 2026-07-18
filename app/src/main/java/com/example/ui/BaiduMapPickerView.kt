package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.BitmapDescriptor
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MapPoi
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.geocode.GeoCodeOption
import com.baidu.mapapi.search.geocode.GeoCodeResult
import com.baidu.mapapi.search.geocode.GeoCoder
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult
import com.baidu.mapapi.search.poi.OnGetPoiSearchResultListener
import com.baidu.mapapi.search.poi.PoiCitySearchOption
import com.baidu.mapapi.search.poi.PoiDetailResult
import com.baidu.mapapi.search.poi.PoiDetailSearchResult
import com.baidu.mapapi.search.poi.PoiIndoorResult
import com.baidu.mapapi.search.poi.PoiResult
import com.baidu.mapapi.search.poi.PoiSearch
import com.baidu.mapapi.search.sug.OnGetSuggestionResultListener
import com.baidu.mapapi.search.sug.SuggestionResult
import com.baidu.mapapi.search.sug.SuggestionSearch
import com.baidu.mapapi.search.sug.SuggestionSearchOption

data class BaiduPoiSuggestion(
    val name: String,
    val address: String,
    val city: String,
    val latitude: Double,
    val longitude: Double
) {
    val displayAddress: String
        get() = listOf(city, address)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")

    val displayName: String
        get() = if (displayAddress.isBlank()) name else "$name · $displayAddress"
}

@Composable
fun BaiduMapPickerView(
    modifier: Modifier = Modifier,
    initialLat: Double = 30.25,
    initialLng: Double = 120.15,
    initialZoom: Float = 15f,
    searchKeyword: String = "",
    searchTrigger: Int = 0,
    selectedSuggestion: BaiduPoiSuggestion? = null,
    selectedSuggestionTrigger: Int = 0,
    onLocationPicked: (latitude: Double, longitude: Double, address: String?) -> Unit,
    onSearchStateChanged: (isSearching: Boolean, message: String?) -> Unit = { _, _ -> },
    onSearchResultsChanged: (List<BaiduPoiSuggestion>) -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var geoCoderRef by remember { mutableStateOf<GeoCoder?>(null) }
    var poiSearchRef by remember { mutableStateOf<PoiSearch?>(null) }
    var suggestionSearchRef by remember { mutableStateOf<SuggestionSearch?>(null) }
    var markerIconRef by remember { mutableStateOf<BitmapDescriptor?>(null) }
    var searchHandler by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var focusHandler by remember { mutableStateOf<((BaiduPoiSuggestion) -> Unit)?>(null) }

    LaunchedEffect(searchTrigger, searchHandler) {
        val keyword = searchKeyword.trim()
        if (searchTrigger > 0 && keyword.isNotBlank()) {
            searchHandler?.invoke(keyword)
        }
    }

    LaunchedEffect(selectedSuggestionTrigger, selectedSuggestion, focusHandler) {
        selectedSuggestion?.let { focusHandler?.invoke(it) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewRef?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewRef?.onPause()
                Lifecycle.Event.ON_DESTROY -> mapViewRef?.onDestroy()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            geoCoderRef?.destroy()
            poiSearchRef?.destroy()
            suggestionSearchRef?.destroy()
            markerIconRef?.recycle()
            mapViewRef?.onDestroy()
            geoCoderRef = null
            poiSearchRef = null
            suggestionSearchRef = null
            markerIconRef = null
            mapViewRef = null
            searchHandler = null
            focusHandler = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val mapView = MapView(context).also { mapViewRef = it }
            val baiduMap = mapView.map
            val markerIcon = createPickerMarkerIcon().also { markerIconRef = it }
            val geoCoder = GeoCoder.newInstance().also { geoCoderRef = it }
            val poiSearch = PoiSearch.newInstance().also { poiSearchRef = it }
            val suggestionSearch = SuggestionSearch.newInstance().also { suggestionSearchRef = it }
            var marker: Marker? = null
            var latestPoint: LatLng? = null
            var currentCity = "全国"
            var lastSearchKeyword = ""

            mapView.showZoomControls(false)
            mapView.showScaleControl(true)
            mapView.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_MOVE,
                    MotionEvent.ACTION_POINTER_DOWN -> view.parent?.requestDisallowInterceptTouchEvent(true)

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }

            baiduMap.uiSettings.setAllGesturesEnabled(true)
            baiduMap.uiSettings.setFlingEnable(false)
            baiduMap.uiSettings.setInertialAnimation(false)
            baiduMap.uiSettings.setRotateGesturesEnabled(false)
            baiduMap.uiSettings.setOverlookingGesturesEnabled(false)
            baiduMap.setMaxAndMinZoomLevel(20f, 4f)

            fun setMarker(point: LatLng) {
                latestPoint = point
                if (marker == null) {
                    marker = baiduMap.addOverlay(
                        MarkerOptions()
                            .position(point)
                            .icon(markerIcon)
                            .draggable(false)
                    ) as Marker
                } else {
                    marker?.position = point
                }
            }

            fun moveCamera(point: LatLng, zoom: Float = 16f) {
                baiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(point, zoom), 350)
            }

            fun pick(point: LatLng, address: String? = null, reverseGeocode: Boolean = true) {
                setMarker(point)
                moveCamera(point)
                onLocationPicked(point.latitude, point.longitude, address)
                if (reverseGeocode) {
                    geoCoder.reverseGeoCode(ReverseGeoCodeOption().location(point).radius(500))
                }
            }

            fun toSuggestion(
                name: String?,
                address: String?,
                city: String?,
                point: LatLng?
            ): BaiduPoiSuggestion? {
                if (point == null) return null
                val safeName = name?.takeIf { it.isNotBlank() } ?: address?.takeIf { it.isNotBlank() } ?: "搜索结果"
                return BaiduPoiSuggestion(
                    name = safeName,
                    address = address.orEmpty(),
                    city = city.orEmpty(),
                    latitude = point.latitude,
                    longitude = point.longitude
                )
            }

            fun focusSuggestion(suggestion: BaiduPoiSuggestion) {
                val point = LatLng(suggestion.latitude, suggestion.longitude)
                pick(point, suggestion.displayName, reverseGeocode = true)
            }

            fun runGeocode(keyword: String) {
                val cityForSearch = currentCity.takeIf { it.isNotBlank() } ?: "全国"
                val started = geoCoder.geocode(
                    GeoCodeOption()
                        .city(cityForSearch)
                        .address(keyword)
                )
                if (!started) {
                    onSearchStateChanged(false, "搜索启动失败，请检查百度 SDK AK 和网络")
                }
            }

            fun runPoiSearch(keyword: String) {
                val cityForSearch = currentCity.takeIf { it.isNotBlank() } ?: "全国"
                val started = poiSearch.searchInCity(
                    PoiCitySearchOption()
                        .city(cityForSearch)
                        .keyword(keyword)
                        .pageNum(0)
                        .pageCapacity(8)
                        .scope(2)
                        .cityLimit(false)
                        .isReturnAddr(true)
                )
                if (!started) {
                    runGeocode(keyword)
                }
            }

            fun search(keyword: String) {
                lastSearchKeyword = keyword
                onSearchResultsChanged(emptyList())
                onSearchStateChanged(true, "正在搜索：$keyword")
                val center = baiduMap.mapStatus?.target ?: latestPoint ?: LatLng(initialLat, initialLng)
                val cityForSearch = currentCity.takeIf { it.isNotBlank() } ?: "全国"
                val started = suggestionSearch.requestSuggestion(
                    SuggestionSearchOption()
                        .city(cityForSearch)
                        .keyword(keyword)
                        .location(center)
                        .citylimit(false)
                )
                if (!started) {
                    runPoiSearch(keyword)
                }
            }

            geoCoder.setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
                override fun onGetGeoCodeResult(result: GeoCodeResult?) {
                    if (result?.error == SearchResult.ERRORNO.NO_ERROR && result.location != null) {
                        val suggestion = toSuggestion(
                            name = lastSearchKeyword,
                            address = result.address,
                            city = currentCity,
                            point = result.location
                        )
                        if (suggestion != null) {
                            onSearchResultsChanged(listOf(suggestion))
                            focusSuggestion(suggestion)
                            onSearchStateChanged(false, "已定位到：${suggestion.name}")
                        } else {
                            onSearchStateChanged(false, "没有找到可用坐标")
                        }
                    } else {
                        onSearchStateChanged(false, "未找到“$lastSearchKeyword”，请补充城市或更完整地址")
                    }
                }

                override fun onGetReverseGeoCodeResult(result: ReverseGeoCodeResult?) {
                    if (result?.error == SearchResult.ERRORNO.NO_ERROR) {
                        val point = result.location ?: latestPoint
                        val address = result.address?.takeIf { it.isNotBlank() }
                        val city = result.addressDetail?.city?.takeIf { it.isNotBlank() }
                        if (!city.isNullOrBlank()) currentCity = city
                        if (point != null && address != null) {
                            onLocationPicked(point.latitude, point.longitude, address)
                        }
                    }
                }
            })

            suggestionSearch.setOnGetSuggestionResultListener(object : OnGetSuggestionResultListener {
                override fun onGetSuggestionResult(result: SuggestionResult?) {
                    val suggestions = if (result?.error == SearchResult.ERRORNO.NO_ERROR) {
                        result.allSuggestions
                            ?.mapNotNull { info ->
                                toSuggestion(
                                    name = info.key,
                                    address = info.address,
                                    city = info.city,
                                    point = info.pt
                                )
                            }
                            ?.distinctBy { "${it.name}_${it.latitude}_${it.longitude}" }
                            .orEmpty()
                    } else {
                        emptyList()
                    }

                    if (suggestions.isNotEmpty()) {
                        onSearchResultsChanged(suggestions)
                        focusSuggestion(suggestions.first())
                        onSearchStateChanged(false, "已找到 ${suggestions.size} 个地点")
                    } else {
                        runPoiSearch(lastSearchKeyword)
                    }
                }
            })

            poiSearch.setOnGetPoiSearchResultListener(object : OnGetPoiSearchResultListener {
                override fun onGetPoiResult(result: PoiResult?) {
                    val suggestions = if (result?.error == SearchResult.ERRORNO.NO_ERROR) {
                        result.allPoi
                            ?.mapNotNull { poi ->
                                toSuggestion(
                                    name = poi.name,
                                    address = poi.address,
                                    city = poi.city,
                                    point = poi.location
                                )
                            }
                            ?.distinctBy { "${it.name}_${it.latitude}_${it.longitude}" }
                            .orEmpty()
                    } else {
                        emptyList()
                    }

                    if (suggestions.isNotEmpty()) {
                        onSearchResultsChanged(suggestions)
                        focusSuggestion(suggestions.first())
                        onSearchStateChanged(false, "已找到 ${suggestions.size} 个地点")
                    } else {
                        runGeocode(lastSearchKeyword)
                    }
                }

                override fun onGetPoiDetailResult(result: PoiDetailResult?) = Unit

                override fun onGetPoiDetailResult(result: PoiDetailSearchResult?) = Unit

                override fun onGetPoiIndoorResult(result: PoiIndoorResult?) = Unit
            })

            baiduMap.setOnMapClickListener(object : BaiduMap.OnMapClickListener {
                override fun onMapClick(point: LatLng) {
                    pick(point)
                    onSearchStateChanged(false, "已选择地图位置")
                }

                override fun onMapPoiClick(poi: MapPoi) {
                    pick(poi.position, poi.name)
                    onSearchStateChanged(false, "已选择：${poi.name}")
                }
            })

            val initialPoint = LatLng(initialLat, initialLng)
            setMarker(initialPoint)
            baiduMap.setMapStatus(MapStatusUpdateFactory.newLatLngZoom(initialPoint, initialZoom))
            searchHandler = ::search
            focusHandler = ::focusSuggestion
            mapView
        }
    )
}

private fun createPickerMarkerIcon(): BitmapDescriptor {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val center = size / 2f

    paint.color = android.graphics.Color.argb(70, 0, 0, 0)
    canvas.drawCircle(center, center + 14f, 26f, paint)

    paint.style = Paint.Style.FILL
    paint.color = android.graphics.Color.rgb(0x29, 0x64, 0xFF)
    canvas.drawCircle(center, center, 28f, paint)

    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(center, center, 12f, paint)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 4f
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(center, center, 28f, paint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
