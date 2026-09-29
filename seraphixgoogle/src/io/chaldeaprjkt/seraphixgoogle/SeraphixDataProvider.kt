/*
 * Copyright (C) 2021 Chaldeaprjkt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.chaldeaprjkt.seraphixgoogle

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import io.chaldeaprjkt.seraphixgoogle.SeraphixCompanion.isPackageEnabled

class SeraphixDataProvider(
    private val context: Context,
    private val hostId: Int,
    hostWidgetId: Int = -1
) {
    private val widgetManager by lazy { AppWidgetManager.getInstance(context) }
    private val widgetHost by lazy { EphemeralWidgetHostGoogle(context, hostId) }
    private lateinit var widgetHostView: EphemeralWidgetHostViewGoogle
    private val smartspaceProviderComponent = ComponentName(QSB_PACKAGE, SMARTSPACE_PROVIDER)
    private var widgetId = hostWidgetId
    private var isWidgetBound = false
    private var isListening = false

    fun setOnDataUpdated(listener: DataProviderListener? = null): SeraphixDataProvider {
        widgetHost.setOnDataUpdated(listener)
        return this
    }

    fun pauseListening() {
        if (isListening) {
            widgetHost.stopListening()
            isListening = false
        }
    }

    fun resumeListening() {
        if (isWidgetBound && !isListening) {
            widgetHost.startListening()
            isListening = true
        }
    }

    /** Returns binding success, not whether the widget has already delivered weather data. */
    fun bind(onBounded: DataProviderBinder? = null): Boolean {
        if (!context.isPackageEnabled(QSB_PACKAGE)) {
            unbind()
            return false
        }
        // Do not cache absence: the Google app/provider can become available later.
        val providerInfo = widgetManager.installedProviders
                .firstOrNull { it.provider == smartspaceProviderComponent }
        if (providerInfo == null) {
            unbind()
            return false
        }

        try {
            val wInfo = if (widgetId > -1) widgetManager.getAppWidgetInfo(widgetId) else null
            if (isWidgetBound && wInfo?.provider == smartspaceProviderComponent) {
                resumeListening()
                return true
            }
            if (wInfo?.provider != smartspaceProviderComponent) {
                unbind()
                widgetId = widgetHost.allocateAppWidgetId()
                if (!widgetManager.bindAppWidgetIdIfAllowed(widgetId, smartspaceProviderComponent)) {
                    unbind()
                    return false
                }
            }

            widgetHostView = widgetHost.createView(context, widgetId, providerInfo)
                    as EphemeralWidgetHostViewGoogle
            isWidgetBound = true
            resumeListening()
            onBounded?.onBound(widgetId)
            return true
        } catch (e: RuntimeException) {
            unbind()
            throw e
        }
    }

    fun unbind() {
        // A denied/failed bind may still own an allocated ID. Release that ID too,
        // without deleting other widgets belonging to the same host.
        try {
            pauseListening()
            if (::widgetHostView.isInitialized) {
                widgetHostView.setOnUpdateAppWidget(null)
            }
        } finally {
            val id = widgetId
            widgetId = -1
            isWidgetBound = false
            isListening = false
            if (id > -1) widgetHost.deleteAppWidgetId(id)
        }
    }

    companion object {
        const val TAG = "SeraphixDataProvider"
        const val SMARTSPACE_PROVIDER =
            "com.google.android.apps.gsa.staticplugins.smartspace.widget.SmartspaceWidgetProvider"
        const val QSB_PACKAGE = "com.google.android.googlequicksearchbox"
    }
}
