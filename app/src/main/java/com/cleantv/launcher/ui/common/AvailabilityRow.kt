package com.cleantv.launcher.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.cleantv.launcher.data.tmdb.Availability

/** "Watch on Netflix · Prime Video +2 more", or why it isn't watchable, for the given region. */
@Composable
fun AvailabilityRow(availability: Availability?, regionLabel: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            availability == null -> Text(" ", fontSize = 12.sp)
            availability.services.isEmpty() -> Text(
                if (availability.otherCount > 0) {
                    "Not on your services in $regionLabel · on ${availability.otherCount} other"
                } else {
                    "Not streaming in $regionLabel"
                },
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
            else -> {
                Text("Watch on", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                availability.services.forEach { s ->
                    Text(
                        s.label,
                        modifier = Modifier
                            .background(s.color.copy(alpha = 0.25f), CircleShape)
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (availability.otherCount > 0) {
                    Text("+${availability.otherCount} more", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
        }
    }
}
