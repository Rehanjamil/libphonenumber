import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalResourceApi::class)
@Composable
@Preview
fun App() {
    val scope = rememberCoroutineScope()
    val phoneNumberUtil: PhoneNumberUtil = PhoneNumberUtil.createInstance(scope)

    var text by remember { mutableStateOf("Hello, World!") }
    var examplePhoneNumberToFormat by remember { mutableStateOf("8005551212") }
    var examplePhoneNumberFormatted by remember { mutableStateOf(false) }
    var asYouTypeFormatterText by remember { mutableStateOf("") }
    val region = remember {
        try {
            "PK"
        } catch (e: Exception) {
            // as of compose 1.4.3, js fails to get the region so default to US
            "US"
        }
    }
    MaterialTheme {
        Column(Modifier.fillMaxWidth()) {
            Button(onClick = {
                text = "Hello, ${getPlatform().name}!"
            }) {
                Text(text)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    scope.launch {
                        val phoneNumber = phoneNumberUtil.parse(examplePhoneNumberToFormat, region)
                        examplePhoneNumberToFormat =
                            phoneNumberUtil.format(
                                phoneNumber,
                                PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL
                            )
                        examplePhoneNumberFormatted = true
                    }
                }) {
                    Text(if (!examplePhoneNumberFormatted) "Click to format" else "Formatted")
                }
                Text("Phone number: $examplePhoneNumberToFormat")
            }
            Row {
                OutlinedTextField(
                    value = asYouTypeFormatterText,
                    onValueChange = { text: String ->
                        asYouTypeFormatterText = text
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("AsYouTypeFormatter Input") },
                    singleLine = true,
                    visualTransformation = PhoneNumberVisualTransformation(
                        phoneNumberUtil,
                        region,
                        coroutineScope = scope
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
            }
        }
    }
}