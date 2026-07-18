package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.baidu.mapapi.map.BitmapDescriptor
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.Marker
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.model.LatLng
import com.example.data.TodoItem

@Composable
fun BaiduTodoMapView(
    todoItems: List<TodoItem>,
    selectedItemId: Int?,
    modifier: Modifier = Modifier,
    onMarkerClicked: (Int) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestMarkerClick = rememberUpdatedState(onMarkerClicked)
    var mapView: MapView? = null

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.let { view ->
                (view.tag as? TodoMapController)?.destroy()
                view.onDestroy()
            }
            mapView = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            MapView(context).also { view ->
                mapView = view
                view.showZoomControls(false)
                view.showScaleControl(true)
                view.setOnTouchListener { touchedView, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_MOVE,
                        MotionEvent.ACTION_POINTER_DOWN ->
                            touchedView.parent?.requestDisallowInterceptTouchEvent(true)

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL ->
                            touchedView.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }

                val controller = TodoMapController(view)
                controller.onMarkerClicked = { latestMarkerClick.value(it) }
                view.tag = controller
            }
        },
        update = { view ->
            (view.tag as TodoMapController).also { controller ->
                controller.onMarkerClicked = { latestMarkerClick.value(it) }
                controller.render(todoItems, selectedItemId)
            }
        }
    )
}

private class TodoMapController(private val mapView: MapView) {
    private val baiduMap = mapView.map
    private val markerIds = mutableMapOf<Marker, Int>()
    private val markerIcons = mutableListOf<BitmapDescriptor>()
    private var renderSignature = ""
    private var selectedId: Int? = null
    private var hasPositionedCamera = false

    var onMarkerClicked: (Int) -> Unit = {}

    init {
        baiduMap.uiSettings.setAllGesturesEnabled(true)
        baiduMap.uiSettings.setRotateGesturesEnabled(false)
        baiduMap.uiSettings.setOverlookingGesturesEnabled(false)
        baiduMap.setMaxAndMinZoomLevel(20f, 4f)
        baiduMap.setOnMarkerClickListener { marker ->
            markerIds[marker]?.let(onMarkerClicked)
            true
        }
    }

    fun render(items: List<TodoItem>, requestedSelectedId: Int?) {
        val validItems = items.filter { it.latitude != null && it.longitude != null }
        val effectiveSelected = requestedSelectedId?.takeIf { id -> validItems.any { it.id == id } }
            ?: validItems.firstOrNull()?.id
        val signature = buildString {
            append(effectiveSelected)
            validItems.forEach { item ->
                append('|').append(item.id)
                    .append(':').append(item.latitude)
                    .append(':').append(item.longitude)
                    .append(':').append(item.category)
                    .append(':').append(item.isCompleted)
            }
        }

        if (signature != renderSignature) {
            renderSignature = signature
            baiduMap.clear()
            markerIds.clear()
            markerIcons.forEach(BitmapDescriptor::recycle)
            markerIcons.clear()

            validItems.forEach { item ->
                val selected = item.id == effectiveSelected
                val color = CategoryConfig.getByName(item.category).color.toArgb()
                val icon = createTodoMarkerIcon(color, selected, item.isCompleted)
                markerIcons += icon
                val marker = baiduMap.addOverlay(
                    MarkerOptions()
                        .position(LatLng(item.latitude!!, item.longitude!!))
                        .icon(icon)
                        .zIndex(if (selected) 10 else 1)
                ) as Marker
                markerIds[marker] = item.id
            }
        }

        if (effectiveSelected != selectedId || !hasPositionedCamera) {
            selectedId = effectiveSelected
            val selected = validItems.firstOrNull { it.id == effectiveSelected } ?: return
            val point = LatLng(selected.latitude!!, selected.longitude!!)
            if (hasPositionedCamera) {
                baiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(point, 16f), 350)
            } else {
                baiduMap.setMapStatus(MapStatusUpdateFactory.newLatLngZoom(point, 16f))
                hasPositionedCamera = true
            }
        }
    }

    fun destroy() {
        baiduMap.setOnMarkerClickListener(null)
        baiduMap.clear()
        markerIds.clear()
        markerIcons.forEach(BitmapDescriptor::recycle)
        markerIcons.clear()
    }
}

private fun createTodoMarkerIcon(
    categoryColor: Int,
    selected: Boolean,
    completed: Boolean
): BitmapDescriptor {
    val width = if (selected) 92 else 76
    val height = if (selected) 108 else 90
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val centerX = width / 2f
    val radius = if (selected) 32f else 26f
    val centerY = radius + 8f

    paint.color = android.graphics.Color.argb(55, 0, 0, 0)
    canvas.drawCircle(centerX + 2f, centerY + 5f, radius + 5f, paint)

    paint.color = if (completed) {
        android.graphics.Color.argb(175, 120, 120, 120)
    } else {
        categoryColor
    }
    val tip = Path().apply {
        moveTo(centerX - radius * 0.55f, centerY + radius * 0.7f)
        lineTo(centerX, height - 4f)
        lineTo(centerX + radius * 0.55f, centerY + radius * 0.7f)
        close()
    }
    canvas.drawPath(tip, paint)
    canvas.drawCircle(centerX, centerY, radius, paint)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = if (selected) 7f else 5f
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(centerX, centerY, radius - paint.strokeWidth / 2f, paint)
    paint.style = Paint.Style.FILL
    canvas.drawCircle(centerX, centerY, if (selected) 11f else 8f, paint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
