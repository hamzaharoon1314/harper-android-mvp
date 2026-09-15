package com.example.harperandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    FixtureScreen()
                }
            }
        }
    }
}

@Composable
fun FixtureScreen() {
    Column(
        modifier =
            Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Text("Accessibility Fixture", style = MaterialTheme.typography.headlineMedium)

        var standardText by remember { mutableStateOf("") }
        OutlinedTextField(
            value = standardText,
            onValueChange = { standardText = it },
            label = { Text("Standard Editable Field") },
            modifier = Modifier.padding(vertical = 8.dp),
        )

        var multilineText by remember { mutableStateOf("") }
        OutlinedTextField(
            value = multilineText,
            onValueChange = { multilineText = it },
            label = { Text("Multiline Editable Field") },
            singleLine = false,
            maxLines = 5,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        var passwordText by remember { mutableStateOf("") }
        OutlinedTextField(
            value = passwordText,
            onValueChange = { passwordText = it },
            label = { Text("Password Field") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.padding(vertical = 8.dp),
        )

        var pinText by remember { mutableStateOf("") }
        OutlinedTextField(
            value = pinText,
            onValueChange = { pinText = it },
            label = { Text("OTP/PIN Field") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.padding(vertical = 8.dp),
        )

        var largeText by remember { mutableStateOf("This is a large text fixture. ".repeat(100)) }
        OutlinedTextField(
            value = largeText,
            onValueChange = { largeText = it },
            label = { Text("Large Text Fixture") },
            modifier = Modifier.padding(vertical = 8.dp),
        )

        var rapidText by remember { mutableStateOf("") }
        OutlinedTextField(
            value = rapidText,
            onValueChange = { rapidText = it },
            label = { Text("Rapid Text Change Fixture") },
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Text("Privacy Policy", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        Text(
            text =
                "• Text is analyzed locally on-device.\n" +
                    "• Raw typed text is not persisted by default.\n" +
                    "• The service only inspects accessible text fields in allowed applications.\n" +
                    "• Password and protected fields are automatically excluded.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
