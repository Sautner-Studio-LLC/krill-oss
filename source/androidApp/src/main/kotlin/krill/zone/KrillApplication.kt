package krill.zone

import android.app.*
import krill.zone.app.di.*
import krill.zone.shared.di.*
import org.koin.android.ext.koin.*
import org.koin.core.context.*

class KrillApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        

        
        startKoin {
            androidContext(this@KrillApplication)
            modules(
                sharedModule, appModule, composeModule,
                platformModule, clientProcessModule, clientNodeManagerModule
            )

        }


    }
}