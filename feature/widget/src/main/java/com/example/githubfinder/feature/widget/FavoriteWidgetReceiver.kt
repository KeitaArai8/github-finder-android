/*
 * Abstract:
 * FavoriteWidget を OS に登録するための AppWidgetProvider（Glance 経由）。
 * AndroidManifest.xml の receiver 宣言から参照される。
 */

package com.example.githubfinder.feature.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class FavoriteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FavoriteWidget()
}
