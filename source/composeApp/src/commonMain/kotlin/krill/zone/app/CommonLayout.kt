package krill.zone.app

import androidx.compose.ui.unit.*

/**
 * Centralized layout constants for consistent spacing, padding, and sizing across the UI.
 * All constants are feature-agnostic and named for their general purpose.
 */
object CommonLayout {
    
    // ===== Spacing & Padding =====
    /** Extra small padding/spacing: 4dp - Used for tight spacing between related items */
    val PADDING_EXTRA_SMALL = 4.dp
    
    /** Small padding/spacing: 8dp - Standard small padding and spacing */
    val PADDING_SMALL = 8.dp
    
    /** Medium padding/spacing: 12dp - Medium padding for form elements */
    val PADDING_MEDIUM = 12.dp
    
    /** Large padding/spacing: 16dp - Standard large padding */
    val PADDING_LARGE = 16.dp
    
    /** Extra large padding/spacing: 24dp - Large padding for major sections */
    val PADDING_EXTRA_LARGE = 24.dp
    
    /** Mobile-specific top padding: 30dp */
    val PADDING_MOBILE_TOP = 30.dp
    
    /** Start padding for nested items: 25dp */
    val PADDING_START_NESTED = 25.dp
    
    // ===== Spacing Between Items =====
    /** Small spacing between items: 8dp */
    val SPACING_SMALL = 8.dp
    
    /** Medium spacing between items: 12dp */
    val SPACING_MEDIUM = 12.dp
    
    /** Large spacing between items: 16dp */
    val SPACING_LARGE = 16.dp
    
    // ===== Corner Radius =====
    /** Small corner radius: 4dp - For small UI elements like chips */
    val CORNER_RADIUS_SMALL = 4.dp
    
    /** Medium corner radius: 8dp - Standard corner radius for cards and buttons */
    val CORNER_RADIUS_MEDIUM = 8.dp
    
    /** Large corner radius: 12dp - For larger cards */
    val CORNER_RADIUS_LARGE = 12.dp
    
    // ===== Borders =====
    /** Thin border: 1dp */
    val BORDER_THIN = 1.dp
    
    /** Medium border: 2dp */
    val BORDER_MEDIUM = 2.dp
    
    /** Thick border: 3dp */
    val BORDER_THICK = 3.dp
    
    // ===== Component Heights =====
    /** Standard button height: 48dp */
    val BUTTON_HEIGHT_STANDARD = 48.dp
    
    /** Large button height: 56dp */
    val BUTTON_HEIGHT_LARGE = 56.dp
    
    /** Minimum height for text input fields: 80dp */
    val INPUT_MIN_HEIGHT = 80.dp
    
    /** Standard row height: 42dp */
    val ROW_HEIGHT_STANDARD = 42.dp
    
    // ===== Component Widths & Sizes =====
    /** Progress indicator size: 64dp */
    val PROGRESS_INDICATOR_SIZE = 64.dp
    
    /** Maximum width for compact elements: 200dp */
    val MAX_WIDTH_COMPACT = 200.dp
    
    /** Icon size standard: 32dp */
    val ICON_SIZE_STANDARD = 32.dp
    
    /** Icon size large: 62dp */
    val ICON_SIZE_LARGE = 62.dp
    
    // ===== Pin-Specific Sizes =====
    /** Large dot size for pins: 45dp */
    val PIN_DOT_SIZE_LARGE = 45.dp
    
    /** Medium dot size for pins: 20dp */
    val PIN_DOT_SIZE_MEDIUM = 20.dp
    
    /** Border width for pin dots: 3dp */
    val PIN_DOT_BORDER = 3.dp
    
    /** Small pin dot size: 12dp */
    val PIN_DOT_SIZE_SMALL = 12.dp
    
    // ===== Node-Specific Sizes =====
    /** Node size: 60dp */
    val NODE_SIZE = 60.dp
    
    /** Node image size: 60dp */
    val NODE_IMAGE_SIZE = 60.dp
    
    /** Node label Y offset: -40dp */
    val NODE_LABEL_Y_OFFSET = (-40).dp
    
    /** Node menu ring radius: 120dp */
    val NODE_MENU_RING_RADIUS = 120.dp
    
    /** Node menu first item spacer: 112dp */
    val NODE_MENU_FIRST_ITEM_SPACER = 112.dp
    
    // ===== Elevation & Shadow =====
    /** Small elevation: 2dp */
    val ELEVATION_SMALL = 2.dp
    
    /** Medium elevation: 4dp */
    val ELEVATION_MEDIUM = 4.dp
    
    /** Large elevation: 6dp */
    val ELEVATION_LARGE = 6.dp
    
    // ===== Special Values =====
    /** Width constraint for dialog inner padding: 6dp */
    val DIALOG_INNER_PADDING = 6.dp
    
    /** Vertical padding for chips: 2dp */
    val CHIP_VERTICAL_PADDING = 2.dp
    
    /** Horizontal padding for chips: 6dp */
    val CHIP_HORIZONTAL_PADDING = 6.dp
    
    /** Extra tight horizontal padding: 2dp - Used for very tight element spacing */
    val PADDING_HORIZONTAL_EXTRA_TIGHT = 2.dp
    
    /** Small vertical padding for chips and badges: 2dp */
    val PADDING_VERTICAL_CHIP = 2.dp
    
    /** Small horizontal padding for chips and badges: 4dp */
    val PADDING_HORIZONTAL_CHIP = 4.dp
    
    /** Dialog maximum width: 600dp */
    val DIALOG_MAX_WIDTH = 600.dp
    
    /** Pin header width: 430dp */
    val PIN_HEADER_WIDTH = 430.dp
    
    /** Pin header height: 565dp */
    val PIN_HEADER_HEIGHT = 565.dp
    
    /** Circle indicator size: 10dp */
    val CIRCLE_INDICATOR_SIZE = 10.dp
    
    /** Small icon size: 16dp */
    val ICON_SIZE_SMALL = 16.dp
    
    /** Medium icon size: 24dp */
    val ICON_SIZE_MEDIUM = 24.dp
    
    /** Gate icon size: 36dp */
    val GATE_ICON_SIZE = 36.dp
    
    /** Cron field width: 120dp */
    val CRON_FIELD_WIDTH = 120.dp
    
    /** Cron datetime field width: 140dp */
    val CRON_DATETIME_FIELD_WIDTH = 140.dp
    
    /** Code editor height: 280dp */
    val CODE_EDITOR_HEIGHT = 280.dp
    
    /** Thin stroke width: 2dp */
    val STROKE_WIDTH_THIN = 2.dp
    
    /** Top padding for specific elements: 4dp */
    val PADDING_TOP_TIGHT = 4.dp
    
    /** Server connection form field width: 100dp */
    val SERVER_FIELD_WIDTH = 100.dp
    
    /** Server connection form max width: 400dp */
    val SERVER_FORM_MAX_WIDTH = 400.dp
    
    /** Minimum height for executor fields: 60dp */
    val EXECUTOR_FIELD_MIN_HEIGHT = 60.dp
    
    /** Large corner radius for cards: 16dp */
    val CORNER_RADIUS_EXTRA_LARGE = 16.dp
    
    /** Spacing for server lists: 10dp */
    val SPACING_SERVER_LIST = 10.dp
    
    /** Offset for highlight effects: -3dp */
    val OFFSET_HIGHLIGHT = (-3).dp
    
    /** Triangle size for client avatars: 12dp */
    val TRIANGLE_SIZE = 12.dp
    
    /** Client node height: 24dp */
    val CLIENT_NODE_HEIGHT = 24.dp
    
    /** Padding from edge for client screen: 18dp */
    val CLIENT_PADDING_FROM_EDGE = 18.dp
    
    /** Client canvas width: 450dp */
    val CLIENT_CANVAS_WIDTH = 450.dp
    
    /** Client node item width: 75dp */
    val CLIENT_NODE_ITEM_WIDTH = 75.dp
    
    /** Small padding: 5dp - For tight layout elements */
    val PADDING_TINY = 5.dp
    
    /** Padding: 6dp - For small form elements */
    val PADDING_FORM_SMALL = 6.dp
    
    /** Padding: 10dp - For medium form elements */
    val PADDING_FORM_MEDIUM = 10.dp
    
    /** Padding: 20dp - For larger spacing */
    val PADDING_FORM_LARGE = 20.dp
    
    /** Small number entry form width: 180dp */
    val NUMBER_FORM_WIDTH = 180.dp
    
    /** Delete dialog height: 376dp */
    val DELETE_DIALOG_HEIGHT = 376.dp
}
