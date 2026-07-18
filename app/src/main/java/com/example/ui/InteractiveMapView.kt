package com.example.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InteractiveMapView(
    modifier: Modifier = Modifier,
    initialLat: Double = 30.25,
    initialLng: Double = 120.15,
    initialZoom: Int = 12,
    mode: MapMode = MapMode.VIEW,
    todoItems: List<com.example.data.TodoItem> = emptyList(),
    onLocationPicked: ((Double, Double) -> Unit)? = null,
    onMarkerClicked: ((Int) -> Unit)? = null
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isMapLoaded by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current

    // Convert category colors to hex for JS
    val categoryColorsJs = remember {
        CategoryConfig.categories.joinToString(prefix = "{", postfix = "}") {
            val colorHex = String.format("#%06X", (it.color.value.toLong() and 0xFFFFFFL))
            "\"${it.name}\": \"$colorHex\""
        }
    }

    val htmlContent = remember(initialLat, initialLng, initialZoom, categoryColorsJs) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="leaflet.css" />
            <style>
                html, body { margin: 0; padding: 0; height: 100%; width: 100%; background: #fafafa; }
                #map { position: absolute; top: 0; bottom: 0; left: 0; right: 0; width: 100%; height: 100%; }
                .leaflet-control-attribution { display: none !important; }
                @keyframes bounce {
                    0% { transform: translateY(-20px) rotate(-45deg); }
                    50% { transform: translateY(5px) rotate(-45deg); }
                    100% { transform: translateY(0) rotate(-45deg); }
                }
            </style>
            <script src="leaflet.js"></script>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var categoryColors = $categoryColorsJs;
                var map = null;
                var markers = {};
                var selectionMarker = null;
                var fallbackTilesAdded = false;

                // Robust initialization helper to handle load timing securely
                function tryInit() {
                    console.log("tryInit triggered. typeof L: " + typeof L);
                    if (typeof L === 'undefined') {
                        setTimeout(tryInit, 200);
                        return;
                    }
                    if (map) return;
                    initializeMap();
                }

                function initializeMap() {
                    console.log("Initializing map centered at: [" + $initialLat + ", " + $initialLng + "], zoom: " + $initialZoom);
                    try {
                        map = L.map('map', { zoomControl: false }).setView([$initialLat, $initialLng], $initialZoom);
                        
                        // China-friendly Gaode/AutoNavi Map Tile Layer
                        L.tileLayer('https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}', {
                            subdomains: ["1", "2", "3", "4"],
                            maxZoom: 20,
                            crossOrigin: true
                        }).on('tileerror', function(e) {
                            console.error("Map tile load failed: " + (e.tile && e.tile.src ? e.tile.src : "unknown"));
                            if (!fallbackTilesAdded) {
                                fallbackTilesAdded = true;
                                L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                                    maxZoom: 19,
                                    crossOrigin: true
                                }).addTo(map);
                                refreshMapSize();
                            }
                        }).addTo(map);

                        console.log("Map initialized successfully!");
                        refreshMapSize();
                        setTimeout(refreshMapSize, 100);
                        setTimeout(refreshMapSize, 350);
                        setTimeout(refreshMapSize, 800);
                        if (window.AndroidInterface && window.AndroidInterface.onMapInitialized) {
                            window.AndroidInterface.onMapInitialized();
                        }
                    } catch (e) {
                        console.error("Map init error:", e);
                        if (window.AndroidInterface && window.AndroidInterface.onMapLoadError) {
                            window.AndroidInterface.onMapLoadError("地图加载失败: " + e.message);
                        }
                    }
                }

                function refreshMapSize() {
                    if (!map) return;
                    map.invalidateSize(true);
                }

                window.addEventListener('resize', refreshMapSize);

                // Attach tryInit to all load triggers to ensure instantaneous loading
                tryInit();
                window.onload = tryInit;
                document.addEventListener("DOMContentLoaded", tryInit);
                document.addEventListener("readystatechange", function() {
                    if (document.readyState === "complete" || document.readyState === "interactive") {
                        tryInit();
                    }
                });

                function addMarker(id, lat, lng, title, category, isCompleted) {
                    if (!map) return;
                    if (markers[id]) {
                        map.removeLayer(markers[id]);
                    }
                    
                    var color = categoryColors[category] || "#6750A4";
                    var opacity = isCompleted ? "0.5" : "1.0";
                    var scale = isCompleted ? "0.8" : "1.0";
                    
                    var iconHtml = '<div style="' +
                        'background-color: ' + color + ';' +
                        'width: 24px;' +
                        'height: 24px;' +
                        'border-radius: 50% 50% 50% 0;' +
                        'transform: rotate(-45deg) scale(' + scale + ');' +
                        'display: flex;' +
                        'align-items: center;' +
                        'justify-content: center;' +
                        'border: 2px solid white;' +
                        'opacity: ' + opacity + ';' +
                        'box-shadow: 0 2px 5px rgba(0,0,0,0.3);' +
                        'margin-left: -12px;' +
                        'margin-top: -24px;' +
                        '">';
                    
                    if (isCompleted) {
                        iconHtml += '<div style="color: white; font-size: 10px; font-weight: bold; transform: rotate(45deg); margin-bottom: 2px;">✓</div>';
                    } else {
                        iconHtml += '<div style="background: white; width: 6px; height: 6px; border-radius: 50%;"></div>';
                    }
                    iconHtml += '</div>';

                    var customIcon = L.divIcon({
                        html: iconHtml,
                        className: 'custom-pin',
                        iconSize: [24, 24],
                        iconAnchor: [12, 24]
                    });

                    var marker = L.marker([lat, lng], { icon: customIcon }).addTo(map);
                    marker.bindPopup("<div style='font-family: sans-serif; font-size: 13px; line-height:1.4;'><b style='color:#1C1B1F;'>" + title + "</b><br/><span style='font-size:11px; color:#555;'>类别: " + category + "</span></div>");
                    
                    marker.on('click', function() {
                        if (window.AndroidInterface) {
                            window.AndroidInterface.onMarkerClicked(id);
                        }
                    });

                    markers[id] = marker;
                }

                function clearMarkers() {
                    if (!map) return;
                    for (var id in markers) {
                        map.removeLayer(markers[id]);
                    }
                    markers = {};
                }

                function setPickerLocation(lat, lng) {
                    if (!map) return;
                    if (selectionMarker) {
                        selectionMarker.setLatLng([lat, lng]);
                    } else {
                        var pinHtml = '<div style="' +
                            'background-color: #6750A4;' +
                            'width: 30px;' +
                            'height: 30px;' +
                            'border-radius: 50% 50% 50% 0;' +
                            'transform: rotate(-45deg);' +
                            'display: flex;' +
                            'align-items: center;' +
                            'justify-content: center;' +
                            'border: 2.5px solid white;' +
                            'box-shadow: 0 3px 8px rgba(0,0,0,0.4);' +
                            'margin-left: -15px;' +
                            'margin-top: -30px;' +
                            'animation: bounce 0.3s ease;' +
                            '"><div style="background: white; width: 10px; height: 10px; border-radius: 50%;"></div></div>';

                        var customIcon = L.divIcon({
                            html: pinHtml,
                            className: 'custom-picker-pin',
                            iconSize: [30, 30],
                            iconAnchor: [15, 30]
                        });

                        selectionMarker = L.marker([lat, lng], { icon: customIcon }).addTo(map);
                    }
                    map.panTo([lat, lng]);
                    
                    if (window.AndroidInterface) {
                        window.AndroidInterface.onLocationPicked(lat, lng);
                    }
                }

                function enableLocationPicker(initLat, initLng) {
                    if (!map) {
                        setTimeout(function() { enableLocationPicker(initLat, initLng); }, 200);
                        return;
                    }
                    if (initLat && initLng) {
                        setPickerLocation(initLat, initLng);
                    }
                    map.on('click', function(e) {
                        setPickerLocation(e.latlng.lat, e.latlng.lng);
                    });
                }

                function panTo(lat, lng) {
                    if (!map) return;
                    map.panTo([lat, lng]);
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    // Update map markers when todoItems change or map becomes loaded
    LaunchedEffect(todoItems, isMapLoaded, webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (!isMapLoaded) return@LaunchedEffect

        if (mode == MapMode.VIEW) {
            webView.evaluateJavascript("clearMarkers();", null)
            todoItems.forEach { item ->
                if (item.latitude != null && item.longitude != null) {
                    val safeTitle = item.title.replace("'", "\\'")
                    val safeCategory = item.category.replace("'", "\\'")
                    val jsCommand = "addMarker(${item.id}, ${item.latitude}, ${item.longitude}, '$safeTitle', '$safeCategory', ${item.isCompleted});"
                    webView.evaluateJavascript(jsCommand, null)
                }
            }
        }
    }

    // Handle center pan updates when list items are interacted with
    LaunchedEffect(initialLat, initialLng, isMapLoaded, webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        if (isMapLoaded) {
            webView.evaluateJavascript("panTo($initialLat, $initialLng);", null)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewRef = this
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                        val msg = consoleMessage?.message() ?: ""
                        val level = consoleMessage?.messageLevel()?.name ?: ""
                        val line = consoleMessage?.lineNumber() ?: 0
                        android.util.Log.d("InteractiveMapViewJS", "[$level] $msg (行:$line)")
                        
                        // If it's an error message, toast it immediately so any JS compilation or map asset issues are clearly visible!
                        if (level == "ERROR" || level == "WARNING") {
                            post {
                                android.widget.Toast.makeText(
                                    context,
                                    "【网页 $level】$msg (行:$line)",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        return true
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    allowFileAccess = true
                    allowContentAccess = true
                    // Enable cross-origin resource requests from local assets so online map tiles load perfectly
                    allowFileAccessFromFileURLs = true
                    allowUniversalAccessFromFileURLs = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onMapInitialized() {
                        post {
                            isMapLoaded = true
                            
                            // If we are in PICKER mode, enable click-to-pick
                            if (mode == MapMode.PICKER) {
                                evaluateJavascript("enableLocationPicker($initialLat, $initialLng);", null)
                            } else {
                                // Populate initial pins
                                todoItems.forEach { item ->
                                    if (item.latitude != null && item.longitude != null) {
                                        val safeTitle = item.title.replace("'", "\\'")
                                        val safeCategory = item.category.replace("'", "\\'")
                                        val jsCommand = "addMarker(${item.id}, ${item.latitude}, ${item.longitude}, '$safeTitle', '$safeCategory', ${item.isCompleted});"
                                        evaluateJavascript(jsCommand, null)
                                    }
                                }
                            }
                        }
                    }

                    @JavascriptInterface
                    fun onMapLoadError(message: String) {
                        post {
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }

                    @JavascriptInterface
                    fun onLocationPicked(lat: Double, lng: Double) {
                        post {
                            onLocationPicked?.invoke(lat, lng)
                        }
                    }

                    @JavascriptInterface
                    fun onMarkerClicked(id: Int) {
                        post {
                            onMarkerClicked?.invoke(id)
                        }
                    }
                }, "AndroidInterface")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        android.util.Log.d("InteractiveMapView", "onPageFinished: $url")
                        view?.evaluateJavascript("if (typeof refreshMapSize === 'function') { setTimeout(refreshMapSize, 100); setTimeout(refreshMapSize, 500); }", null)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onReceivedError(
                        view: WebView?,
                        errorCode: Int,
                        description: String?,
                        failingUrl: String?
                    ) {
                        val errMsg = "网页加载错误: $description (URL: $failingUrl)"
                        android.util.Log.e("InteractiveMapView", errMsg)
                        android.widget.Toast.makeText(context, errMsg, android.widget.Toast.LENGTH_LONG).show()
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        val errMsg = "网页加载错误: ${error?.description} (URL: ${request?.url})"
                        android.util.Log.e("InteractiveMapView", errMsg)
                        // Ignore minor subresource loading errors to prevent spamming
                        if (request?.isForMainFrame == true) {
                            android.widget.Toast.makeText(context, errMsg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }

                    @SuppressLint("WebViewClientOnReceivedSslError")
                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: android.webkit.SslErrorHandler?,
                        error: android.net.http.SslError?
                    ) {
                        android.util.Log.w("InteractiveMapView", "SSL Error: ${error?.toString()}")
                        handler?.proceed()
                    }
                }
                loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "utf-8", null)
            }
        },
        update = {
            it.evaluateJavascript("if (typeof refreshMapSize === 'function') { setTimeout(refreshMapSize, 100); }", null)
        }
    )
}

enum class MapMode {
    VIEW, PICKER
}
