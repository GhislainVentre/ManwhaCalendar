package com.ghislainventre.manhwacalendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ghislainventre.manhwacalendar.ui.ManhwaCalendarScreen
import com.ghislainventre.manhwacalendar.ui.ManhwaCalendarTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ManhwaCalendarTheme {
                ManhwaCalendarScreen()
            }
        }
    }
}
