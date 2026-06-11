package com.time.set

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.time.set.ui.screens.MainContainer
import com.time.set.ui.theme.时区语言一键切换Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            时区语言一键切换Theme {
                MainContainer()
            }
        }
    }
}
