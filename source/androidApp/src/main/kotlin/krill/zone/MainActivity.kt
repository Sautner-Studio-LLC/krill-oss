package krill.zone

import android.os.*
import androidx.activity.*
import androidx.activity.compose.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.*
import com.google.firebase.*
import com.google.firebase.analytics.*
import krill.zone.app.App


class MainActivity : ComponentActivity() {

    private lateinit var firebaseAnalytics: FirebaseAnalytics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        firebaseAnalytics = Firebase.analytics
        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                App()
            }
        }
    }

    override fun onResume() {
        super.onResume()

    }
    override fun onPause() {
        super.onPause()

    }
}





