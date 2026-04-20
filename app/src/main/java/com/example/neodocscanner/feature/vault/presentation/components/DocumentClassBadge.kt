package com.example.neodocscanner.feature.vault.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.neodocscanner.core.domain.model.DocumentClass

/** Maps a [DocumentClass] to a Material Icons vector — used across multiple UI panels. */
@Composable
fun documentClassVectorIcon(cls: DocumentClass): ImageVector = when (cls) {
    DocumentClass.AADHAAR         -> Icons.Default.PersonOutline
    DocumentClass.PAN             -> Icons.Default.CreditCard
    DocumentClass.VOTER_ID        -> Icons.Default.HowToVote
    DocumentClass.DRIVING_LICENCE -> Icons.Default.DirectionsCar
    DocumentClass.PASSPORT        -> Icons.Default.Language
    DocumentClass.OTHER           -> Icons.AutoMirrored.Filled.HelpOutline
}

/**
 * A small coloured pill showing the document classification.
 * Used in both the Checklist and Review tabs.
 *
 * iOS equivalent: the classification badge Text in DocumentCardView.swift.
 */
@Composable
fun DocumentClassBadge(
    documentClass: DocumentClass,
    modifier: Modifier = Modifier
) {
    val bg = documentClass.badgeColor
    val onBg = if (bg.luminance() > 0.5f) Color.Black else Color.White

    Text(
        text      = documentClass.displayName,
        style     = MaterialTheme.typography.labelSmall,
        color     = onBg,
        modifier  = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
