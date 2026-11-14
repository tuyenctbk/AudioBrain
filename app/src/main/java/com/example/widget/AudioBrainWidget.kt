package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.ACTION_QUICK_RECORD
import com.example.EXTRA_OPEN_NOTE_ID
import com.example.MainActivity
import com.example.R
import com.example.data.model.NoteCategory
import com.example.ui.common.labelRes

class AudioBrainWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AudioBrainWidget()
}

class AudioBrainWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(RESPONSIVE_BUCKETS)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetSnapshotStore.loadSnapshot(context)
        provideContent {
            GlanceTheme {
                AudioBrainWidgetContent(context = context, snapshot = snapshot)
            }
        }
    }

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable
    ) {
        val fallback = RemoteViews(context.packageName, R.layout.widget_audiobrain_initial)
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, fallback)
    }

    @Composable
    private fun AudioBrainWidgetContent(
        context: Context,
        snapshot: WidgetSnapshot
    ) {
        val size = LocalSize.current
        val isCompact = size.width < 220.dp || size.height < 130.dp

        val recordIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_QUICK_RECORD
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (snapshot.latestNoteId.isNotBlank()) {
                putExtra(EXTRA_OPEN_NOTE_ID, snapshot.latestNoteId)
            }
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(14.dp),
            verticalAlignment = Alignment.Vertical.Top
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(openAppIntent)),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.app_name),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Box(
                    modifier = GlanceModifier
                        .background(GlanceTheme.colors.secondaryContainer)
                        .cornerRadius(10.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (snapshot.openTasksCount == 1) {
                            context.getString(R.string.widget_one_task)
                        } else {
                            context.getString(R.string.widget_n_tasks, snapshot.openTasksCount)
                        },
                        style = TextStyle(
                            color = GlanceTheme.colors.onSecondaryContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            if (!isCompact) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                        .background(GlanceTheme.colors.surfaceVariant)
                        .cornerRadius(12.dp)
                        .padding(10.dp)
                        .clickable(actionStartActivity(openAppIntent))
                ) {
                    Text(
                        text = if (snapshot.latestNoteTitle.isNotBlank()) {
                            snapshot.latestNoteTitle
                        } else if (snapshot.isSignedIn) {
                            context.getString(R.string.widget_tap_record_first)
                        } else {
                            context.getString(R.string.widget_tap_open_journal)
                        },
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 2
                    )
                    if (snapshot.latestNoteCategory.isNotBlank()) {
                        val matchedCategory = NoteCategory.entries.firstOrNull {
                            it.label.equals(snapshot.latestNoteCategory, ignoreCase = true)
                        }
                        val categoryLabel = if (matchedCategory != null) {
                            context.getString(matchedCategory.labelRes())
                        } else {
                            snapshot.latestNoteCategory
                        }
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = context.getString(
                                R.string.widget_category_notes_count,
                                categoryLabel,
                                snapshot.totalNotesCount
                            ),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 11.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.height(10.dp))
            } else {
                Spacer(modifier = GlanceModifier.defaultWeight())
            }

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Button(
                    text = context.getString(R.string.widget_record_cta),
                    onClick = actionStartActivity(recordIntent),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = GlanceTheme.colors.primary,
                        contentColor = GlanceTheme.colors.onPrimary
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                if (!isCompact && snapshot.latestNoteId.isNotBlank()) {
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Button(
                        text = context.getString(R.string.action_open),
                        onClick = actionStartActivity(openAppIntent),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = GlanceTheme.colors.primaryContainer,
                            contentColor = GlanceTheme.colors.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }

    companion object {
        private val RESPONSIVE_BUCKETS = setOf(
            DpSize(160.dp, 100.dp),
            DpSize(250.dp, 120.dp),
            DpSize(280.dp, 190.dp)
        )
    }
}
