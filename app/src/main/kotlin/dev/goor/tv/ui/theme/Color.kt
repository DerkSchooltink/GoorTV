package dev.goor.tv.ui.theme

import androidx.compose.ui.graphics.Color

// ---- GoorTV brand palette ---------------------------------------------------
// Dark, monochrome ink base with a single confident accent: Signal Amber.
// The amber does all the heavy lifting (FAB, focus rings, favorites, selection);
// everything else stays near-black so the brand reads as premium and intentional.

// Accent — Signal Amber
val GoorAmber          = Color(0xFFF2A93B) // primary accent
val GoorAmberBright    = Color(0xFFFFC061) // hover / pressed-bright
val GoorAmberDim       = Color(0xFF8A5E16) // muted amber for containers
val OnAmber            = Color(0xFF1A1206) // text/icons on amber fills
val AmberContainer     = Color(0xFF3D2A08) // low-emphasis amber surface
val OnAmberContainer   = Color(0xFFFAD9A0) // text on amber container

// Ink — stepped near-black surfaces (lowest → highest elevation)
val Ink                = Color(0xFF0D0D12) // app background / base surface
val InkLowest          = Color(0xFF08080C)
val InkLow             = Color(0xFF131319)
val InkContainer       = Color(0xFF17171F)
val InkHigh            = Color(0xFF20202A)
val InkHighest         = Color(0xFF26262F)
val InkVariant         = Color(0xFF2A2A33) // surfaceVariant (chips, dividers fill)

// Neutral foreground tones
val InkOn              = Color(0xFFECECEF) // primary text on ink
val InkOnVariant       = Color(0xFFC0C0C8) // secondary text
val Outline            = Color(0xFF55555E)
val OutlineVariant     = Color(0xFF2E2E36)

// Secondary / tertiary — kept as warm neutrals so amber stays the only hero
val WarmNeutral        = Color(0xFFD4C7B2)
val OnWarmNeutral      = Color(0xFF332B1C)
val WarmNeutralCont    = Color(0xFF2C281F)
val OnWarmNeutralCont  = Color(0xFFE9DCC6)

// Error — standard Material 3 dark error ramp
val ErrorRed           = Color(0xFFF2B8B5)
val OnErrorRed         = Color(0xFF601410)
val ErrorContainer     = Color(0xFF8C1D18)
val OnErrorContainer   = Color(0xFFF9DEDC)

val Scrim              = Color(0xFF000000)
