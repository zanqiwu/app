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

    // Convert category colors to hex for JS
    val categoryColorsJs = remember {
        CategoryConfig.categories.joinToString(prefix = "{", postfix = "}") {
            val colorHex = String.format("#%06X", (it.color.value.toLong() and 0xFFFFFFL))
            "\"${it.name}\": \"$colorHex\""
        }
    }

    val htmlContent = remember(initialLat, initialLng, initialZoom) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                body { margin: 0; padding: 0; background: #f0f0f0; }
                #map { height: 100vh; width: 100vw; }
                .leaflet-control-attribution { display: none !important; }
                @keyframes bounce {
                    0% { transform: translateY(-20px) rotate(-45deg); }
                    50% { transform: translateY(5px) rotate(-45deg); }
                    100% { transform: translateY(0) rotate(-45deg); }
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var categoryColors = $categoryColorsJs;
                var map = L.map('map', { zoomControl: false }).setView([$initialLat, $initialLng], $initialZoom);
                
                // Beautiful clean vector maps provider
                L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
                    maxZoom: 20
                }).addTo(map);

                var markers = {};

                function addMarker(id, lat, lng, title, category, isCompleted) {
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
                        // Checkmark inside completed pin
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
                    for (var id in markers) {
                        map.removeLayer(markers[id]);
                    }
                    markers = {};
                }

                // Selection marker for PICKER mode
                var selectionMarker = null;
                
                function setPickerLocation(lat, lng) {
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
                    if (initLat && initLng) {
                        setPickerLocation(initLat, initLng);
                    }
                    map.on('click', function(e) {
                        setPickerLocation(e.latlng.lat, e.latlng.lng);
                    });
                }

                function panTo(lat, lng) {
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
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onLocationPicked(lat: Double, lng: Double) {
                        onLocationPicked?.invoke(lat, lng)
                    }

                    @JavascriptInterface
                    fun onMarkerClicked(id: Int) {
                        onMarkerClicked?.invoke(id)
                    }
                }, "AndroidInterface")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isMapLoaded = true
                        
                        // If we are in PICKER mode, enable click-to-pick
                        if (mode == MapMode.PICKER) {
                            view?.evaluateJavascript("enableLocationPicker($initialLat, $initialLng);", null)
                        } else {
                            // Populate initial pins
                            todoItems.forEach { item ->
                                if (item.latitude != null && item.longitude != null) {
                                    val safeTitle = item.title.replace("'", "\\'")
                                    val safeCategory = item.category.replace("'", "\\'")
                                    val jsCommand = "addMarker(${item.id}, ${item.latitude}, ${item.longitude}, '$safeTitle', '$safeCategory', ${item.isCompleted});"
                                    view?.evaluateJavascript(jsCommand, null)
                                }
                            }
                        }
                    }
                }
                loadDataWithBaseURL("https://localhost", htmlContent, "text/html", "utf-8", null)
            }
        },
        update = {
            // Can handle dynamic updates if needed
        }
    )
}

enum class MapMode {
    VIEW, PICKER
}
