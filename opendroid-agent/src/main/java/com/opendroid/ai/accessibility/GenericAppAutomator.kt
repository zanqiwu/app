package com.opendroid.ai.accessibility

import android.accessibilityservice.AccessibilityService

object GenericAppAutomator {

    fun clickText(text: String): Boolean {
        val service = OpenDroidAccessibilityService.getInstance() ?: return false
        return service.findAndClick(text)
    }

    fun clickId(viewId: String): Boolean {
        val service = OpenDroidAccessibilityService.getInstance() ?: return false
        return service.findAndClickById(viewId)
    }

    fun typeText(searchText: String, content: String): Boolean {
        val service = OpenDroidAccessibilityService.getInstance() ?: return false
        return service.findAndType(searchText, content)
    }

    fun typeId(viewId: String, content: String): Boolean {
        val service = OpenDroidAccessibilityService.getInstance() ?: return false
        return service.findAndTypeById(viewId, content)
    }

    fun scrapeScreen(): String {
        val service = OpenDroidAccessibilityService.getInstance() ?: return ""
        return service.getScreenText()
    }

    fun pressBack(): Boolean {
        val service = OpenDroidAccessibilityService.getInstance() ?: return false
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    fun pressHome(): Boolean {
        val service = OpenDroidAccessibilityService.getInstance() ?: return false
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    fun scroll(forward: Boolean): Boolean {
        val service = OpenDroidAccessibilityService.getInstance() ?: return false
        return service.performScroll(forward)
    }

    fun clickCoordinates(x: Float, y: Float): Boolean {
        val service = OpenDroidAccessibilityService.getInstance() ?: return false
        return service.clickCoordinates(x, y)
    }
}
