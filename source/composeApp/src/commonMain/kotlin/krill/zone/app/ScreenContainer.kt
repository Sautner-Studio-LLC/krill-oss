package krill.zone.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import krill.zone.*
import krill.zone.app.IconManager.iconLargeImageModifier
import krill.zone.shared.*
import org.koin.compose.*

@Composable
fun ScreenContainer(type: KrillApp, content: @Composable () -> Unit) {
    val scrollState = rememberScrollState()
    val screenCore: ScreenCore = koinInject()


    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Fixed header section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = CommonLayout.PADDING_EXTRA_LARGE, vertical = CommonLayout.PADDING_LARGE)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(modifier = iconLargeImageModifier, onClick = {
                    screenCore.reset()
                }) {
                    type.node().icon()
                }
                Spacer(modifier = Modifier.width(CommonLayout.SPACING_SMALL))
                Text(
                    text = type.content().title,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.fillMaxWidth(1.0f))
            }
        }
        HorizontalDivider()
        // Scrollable content section
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = CommonLayout.PADDING_EXTRA_LARGE, vertical = CommonLayout.PADDING_LARGE)
        ) {
            content()
        }
    }
}



