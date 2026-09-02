package com.yungmoolah.converter.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Tighter corners than Material's defaults. Large pill-shaped cards read as soft;
 * a small, consistent radius reads as precise, which suits a screen that is mostly
 * numbers.
 */
object MoolahShapes {
    /** Currency rows and the add tile. */
    val Card = RoundedCornerShape(12.dp)
    /** The status chip and other small affordances. */
    val Chip = RoundedCornerShape(8.dp)
    /** The search field in the picker. */
    val Field = RoundedCornerShape(10.dp)
    /** The picker sheet's top corners. */
    val Sheet = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
}

val MoolahShapeScheme = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(18.dp),
)
