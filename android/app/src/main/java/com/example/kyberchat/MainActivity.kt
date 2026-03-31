package com.example.kyberchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.kyberchat.ui.ChatScreen
import com.example.kyberchat.ui.HomeScreen
import com.example.kyberchat.viewmodel.ChatViewModel

class MainActivity : ComponentActivity() {
    
    // Use factory for AndroidViewModel
    private val viewModel: ChatViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNavigation(viewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: ChatViewModel) {
    val navController = rememberNavController()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    
    // Auto-navigate if logged in (handled by initialDestination logic)
    val startDest = if (isLoggedIn) "home" else "login"

    NavHost(navController = navController, startDestination = startDest) {
        
        composable("login") {
            RegistrationScreen(
                onRegister = { id -> 
                    viewModel.register(id)
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        
        composable("home") {
            val contacts by viewModel.contacts.collectAsState()
            val myId by viewModel.clientId.collectAsState()
            val isConnected by viewModel.isConnected.collectAsState()
            HomeScreen(
                myClientId = myId,
                contacts = contacts,
                isConnected = isConnected,
                onChatClick = { contactId ->
                    navController.navigate("chat/$contactId")
                },
                onLogout = {
                    viewModel.logout()
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
        
        composable("chat/{contactId}") { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId") ?: return@composable
            val myId by viewModel.clientId.collectAsState()
            val messages by viewModel.getMessages(contactId).collectAsState(initial = emptyList())
            
            ChatScreen(
                myClientId = myId,
                recipientId = contactId,
                messages = messages,
                onSend = { content -> viewModel.sendMessage(contactId, content) },
                onDelete = {
                    viewModel.deleteChat(contactId)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
    
    // Call UI Overlay
    // Call UI Overlay - Removed by user request
    // if (callState != ... ) block removed
}

// Simple Registration Screen
@Composable
fun RegistrationScreen(onRegister: (String) -> Unit) {
    var clientId by remember { mutableStateOf("") }
    
    Column(
         modifier = Modifier.fillMaxSize().padding(16.dp),
         verticalArrangement = Arrangement.Center,
         horizontalAlignment = Alignment.CenterHorizontally
    ) {
         Text("Kyber Chat Login", style = MaterialTheme.typography.headlineMedium)
         Spacer(modifier = Modifier.height(32.dp))
         
         OutlinedTextField(
             value = clientId,
             onValueChange = { clientId = it },
             label = { Text("Enter Client ID/Name") }
         )
         Spacer(modifier = Modifier.height(16.dp))
         
         Button(onClick = { if (clientId.isNotBlank()) onRegister(clientId) }) {
             Text("Register & Enter")
         }
    }
}
