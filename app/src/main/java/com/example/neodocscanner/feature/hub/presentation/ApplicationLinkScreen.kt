package com.example.neodocscanner.feature.hub.presentation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.neodocscanner.core.domain.model.QRPayload
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationLinkScreen(
    onNavigateBack: () -> Unit,
    initialPayload: String? = null,
    viewModel: ApplicationHubViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var rawPayload by remember(initialPayload) { mutableStateOf(initialPayload.orEmpty()) }
    var parseError by remember { mutableStateOf<String?>(null) }
    var parsedSummary by remember { mutableStateOf<String?>(null) }

    val scannerOptions = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    }
    val scanner = remember(context) { GmsBarcodeScanning.getClient(context, scannerOptions) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Link Application") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Paste QR JSON payload",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Use this when onboarding from server-generated application QR/link data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (activity == null) {
                            parseError = "QR scan unavailable in current context."
                            return@Button
                        }
                        scanner.startScan()
                            .addOnSuccessListener { barcode ->
                                val scanned = barcode.rawValue
                                if (!scanned.isNullOrBlank()) {
                                    rawPayload = scanned
                                    parseError = null
                                    parsedSummary = null
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("ApplicationLinkScreen", "QR scan failed", e)
                                parseError = "QR scan failed: ${e.localizedMessage}"
                            }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan QR Code")
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = rawPayload,
                onValueChange = {
                    rawPayload = it
                    parseError = null
                    parsedSummary = null
                },
                label = { Text("QR payload JSON") },
                minLines = 8,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    when (val result = QRPayload.parse(rawPayload)) {
                        is QRPayload.Companion.ParseResult.Success -> {
                            val payload = result.payload
                            viewModel.createFromQR(payload)
                            parsedSummary = "Linked: ${payload.suggestedName} (${payload.ref})"
                            parseError = null
                            onNavigateBack()
                        }
                        is QRPayload.Companion.ParseResult.Failure -> {
                            parseError = result.message
                            parsedSummary = null
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Link Application")
            }

            parseError?.let { message ->
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            parsedSummary?.let { summary ->
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = summary,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

