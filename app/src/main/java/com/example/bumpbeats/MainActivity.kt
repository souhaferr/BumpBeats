package com.example.bumpbeats
import android.os.Bundle
import android.util.Patterns
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar
import android.widget.Toast
import android.app.DatePickerDialog
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.activity.ComponentActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.bumpbeats.ui.theme.BumpBeatsTheme
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.Divider
import android.util.Log
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

/////////////////////////////////////////////////////////////////////////////

private const val TAG = "GoogleSignIn"

class MainActivity : ComponentActivity() {
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    // Use Firebase instances from the Application class
    private val firebaseAuth by lazy { (application as MyApp).firebaseAuth }
    private val firestore by lazy { (application as MyApp).firestore }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Google Sign-In
        val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // Your Web Client ID from Firebase
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, googleSignInOptions)

        // Set up ActivityResultLauncher
        googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            if (task.isSuccessful) {
                val account = task.result
                account?.let {
                    firebaseAuthWithGoogle(it)
                }
            } else {
                Log.e(TAG, "Google Sign-In failed: ${task.exception}")
                showToast("Google Sign-In failed. Please try again.")
            }
        }
    }

    private fun startGoogleSignIn() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser
                    user?.let {
                        // Check if email is verified
                        if (!user.isEmailVerified) {
                            // If not verified, send a verification email
                            user.sendEmailVerification()
                                .addOnCompleteListener { verificationTask ->
                                    if (verificationTask.isSuccessful) {
                                        Log.d(TAG, "Verification email sent.")
                                        showToast("Verification email sent. Please check your inbox.")
                                    } else {
                                        Log.e(TAG, "Failed to send verification email.")
                                        showToast("Failed to send verification email.")
                                    }
                                }
                        }

                        // Store Google user data in Firestore
                        val userDetails = hashMapOf(
                            "firstName" to account.givenName,
                            "lastName" to account.familyName,
                            "email" to account.email,
                            "uid" to user.uid
                        )

                        firestore.collection("users")
                            .document(user.uid)
                            .set(userDetails)
                            .addOnSuccessListener {
                                Log.d(TAG, "User data stored successfully in Firestore")
                                navigateToHomePage() // Proceed to home page after storing user data
                            }
                            .addOnFailureListener { exception ->
                                // Handle Firestore failure (optional cleanup)
                                firebaseAuth.currentUser?.delete()
                                    ?.addOnCompleteListener {
                                        Log.e(TAG, "Failed to store user data in Firestore: ${exception.localizedMessage}")
                                        showToast("Failed to store user data. Please try again.")
                                    }
                            }
                    }
                } else {
                    Log.e(TAG, "Sign-in failed: ${task.exception?.message}")
                    showToast("Sign-in failed. Please try again.")
                }
            }
    }
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun navigateToScreen(content: @Composable () -> Unit) {
        setContent {
            BumpBeatsTheme {
                content()
            }
        }
    }

    private fun navigateToSignInScreen() {
        navigateToScreen {
            SignInScreen(
                onSignInSuccess = { navigateToHomePage() },
                onNavigateToSignUp = { navigateToSignUpScreen() },
                onGoogleSignIn = { startGoogleSignIn() }
            )
        }
    }

    private fun navigateToSignUpScreen() {
        navigateToScreen {
            SignUpScreen(
                onSignUpSuccess = { navigateToSignInScreen() },
                onNavigateToSignIn = { navigateToSignInScreen() }
            )
        }
    }

    private fun navigateToHomePage() {
        val userId = firebaseAuth.currentUser?.uid ?: return
        navigateToScreen {
            HomePageScreen(
                userId = userId,
                onNavigate = { destination ->
                    when (destination) {
                        "HomePage" -> navigateToHomePage()
                        "AppointmentsPage" -> navigateToScreen { AppointmentsPage() }
                        "HealthTrackerPage" -> navigateToScreen { HealthTrackerPage() }
                        "CalendarPage" -> navigateToScreen { CalendarPage() }
                        "SettingsPage" -> navigateToScreen { SettingsPage() }
                    }
                }
            )
        }
    }

    private fun navigateToChatPage() {
        navigateToScreen {
            ChatPage(onBack = { navigateToHomePage() })
        }
    }
}

@Composable
fun SignUpScreen(
    onSignUpSuccess: () -> Unit, // Callback for successful sign-up
    onNavigateToSignIn: () -> Unit // Callback to navigate to Sign-In screen
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordErrorMessage by remember { mutableStateOf<String?>(null) }
    var birthday by remember { mutableStateOf("") }
    var pregnancyWeeks by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var termsAccepted by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val firstNameFocusRequester = FocusRequester()
    val lastNameFocusRequester = FocusRequester()
    val emailFocusRequester = FocusRequester()
    val passwordFocusRequester = FocusRequester()
    val pregnancyWeeksFocusRequester = FocusRequester()

    Scaffold(
        content = { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Background image
                Image(
                    painter = painterResource(id = R.drawable.home),
                    contentDescription = "Sign Up Background",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                )
            }

            // Sign Up Form
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "App Logo",
                    modifier = Modifier.size(225.dp)
                )

                // First Name
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("First Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = "First Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(firstNameFocusRequester),
                    singleLine = true,
                    isError = errorMessage?.contains("first name") == true
                )
                if (errorMessage?.contains("first name") == true) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Last Name
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Last Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(lastNameFocusRequester),
                    singleLine = true,
                    isError = errorMessage?.contains("last name") == true
                )
                if (errorMessage?.contains("last name") == true) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Email
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(emailFocusRequester),
                    singleLine = true,
                    isError = errorMessage?.contains("email") == true
                )
                if (errorMessage?.contains("email") == true) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Password
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it
                        passwordErrorMessage = validatePassword(password)},
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocusRequester),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = passwordErrorMessage != null
                )
                if (passwordErrorMessage != null) {
                    Text(
                        text = passwordErrorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Birthday with DatePickerDialog
                val calendar = Calendar.getInstance()
                val datePickerDialog = DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        // Update the birthday state with the selected date
                        birthday = "$year-${month + 1}-$dayOfMonth"
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                )

                // Birthday
                OutlinedTextField(
                    value = birthday,
                    onValueChange = {},
                    label = { Text("Birthday (YYYY-MM-DD)") },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.calendar), // Your vector asset resource
                            contentDescription = "Select Birthday",
                            modifier = Modifier.clickable {
                                datePickerDialog.show() // Show the DatePickerDialog when clicked
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            datePickerDialog.show() // Show DatePickerDialog when the field itself is clicked
                        },
                    singleLine = true,
                    readOnly = true // Make the field read-only to prevent manual typing
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Pregnancy Weeks
                OutlinedTextField(
                    value = pregnancyWeeks,
                    onValueChange = { pregnancyWeeks = it },
                    label = { Text("Weeks of Pregnancy") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = "Weeks of Pregnancy"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(pregnancyWeeksFocusRequester),
                    singleLine = true,
                    isError = errorMessage?.contains("weeks") == true
                )
                if (errorMessage?.contains("weeks") == true) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Privacy Policy & Terms
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = termsAccepted,
                        onCheckedChange = { termsAccepted = it }
                    )
                    Text(
                        text = "I agree to the Privacy Policy and Terms of Service",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { /* Open terms link */ }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Sign-Up Button
                Button(
                    onClick = {
                        if (!termsAccepted) {
                            errorMessage = "You must accept the terms to continue."
                            return@Button
                        }
                        isLoading = true
                        handleSignUp(
                            firstName = firstName,
                            lastName = lastName,
                            email = email,
                            password = password,
                            birthday = birthday,
                            pregnancyWeeks = pregnancyWeeks,
                            onSuccess = {
                                isLoading = false
                                onSignUpSuccess()
                            },
                            onError = {
                                isLoading = false
                                errorMessage = it
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB373D1),
                        contentColor = Color.White
                    ),
                    enabled = !isLoading && termsAccepted,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text("Sign Up", style = MaterialTheme.typography.bodyLarge)
                    }

                }
            }

        }
    )
}

fun validatePassword(password: String): String? {
    return when {
        password.length < 8 -> "Password must be at least 8 characters long."
        !password.any { it.isUpperCase() } -> "Password must include at least one uppercase letter."
        !password.any { it.isLowerCase() } -> "Password must include at least one lowercase letter."
        !password.any { it.isDigit() } -> "Password must include at least one number."
        !password.any { "!@#$%^&*()-_=+[]{}|;:'\",.<>?/`~".contains(it) } -> "Password must include at least one special character."
        else -> null // Password is valid
    }
}

fun handleSignUp(
    firstName: String,
    lastName: String,
    email: String,
    password: String,
    birthday: String,
    pregnancyWeeks: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    // Input validation
    if (firstName.isBlank() || lastName.isBlank()) {
        onError("First and last names are required.")
        return
    }
    if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
        onError("Invalid email format.")
        return
    }
    if (password.isBlank()) {
        onError("Please enter a password.")
        return
    }
    validatePassword(password)?.let { error ->
        onError(error)
        return
    }
    if (pregnancyWeeks.toIntOrNull() == null || pregnancyWeeks.toInt() <= 0) {
        onError("Enter a valid number for weeks of pregnancy.")
        return
    }

    // Firebase Authentication for Sign-Up
    FirebaseAuth.getInstance()
        .createUserWithEmailAndPassword(email, password)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = task.result?.user
                if (user != null) {
                    // Store user data in Fire store
                    val userDetails = hashMapOf(
                        "firstName" to firstName,
                        "lastName" to lastName,
                        "email" to email,
                        "birthday" to birthday,
                        "pregnancyWeeks" to pregnancyWeeks
                    )

                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(user.uid)
                        .set(userDetails)
                        .addOnSuccessListener {
                            onSuccess() // Data successfully saved, navigate or show success
                        }
                        .addOnFailureListener { exception ->
                            // If saving to Fire store fails, delete the user
                            FirebaseAuth.getInstance().currentUser?.delete()
                                ?.addOnCompleteListener {
                                    onError("Failed to save user details: ${exception.localizedMessage}")
                                }
                        }
                } else {
                    onError("Failed to get user information.")
                }
            } else {
                onError(task.exception?.localizedMessage ?: "Sign-Up failed.")
            }
        }
}

@Composable
fun SignInScreen(
    onSignInSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onGoogleSignIn: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Scaffold(
        content = { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Background Image
                Image(
                    painter = painterResource(id = R.drawable.home),
                    contentDescription = "Sign In Background",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Logo
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "App Logo",
                        modifier = Modifier.size(250.dp)
                    )

                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email Icon") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = errorMessage?.contains("email") == true
                    )
                    if (errorMessage?.contains("email") == true) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password Icon") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        isError = errorMessage?.contains("password") == true
                    )
                    if (errorMessage?.contains("password") == true) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Remember Me Checkbox
                    var isRememberMeChecked by remember { mutableStateOf(false) }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isRememberMeChecked,
                            onCheckedChange = { isRememberMeChecked = it }
                        )
                        Text(text = "Remember me?")
                    }
                    Spacer(modifier = Modifier.height(16.dp))


                    // Forgot Password
                    Text(
                        text = "Forgot Password?",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.End)
                            .clickable {
                                // Navigate to forgot password logic here
                                Toast.makeText(context, "Forgot Password Clicked", Toast.LENGTH_SHORT).show()
                            },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    // Sign In Button
                    Button(
                        onClick = {
                            isLoading = true
                            handleSignIn(
                                email = email,
                                password = password,
                                onSuccess = {
                                    isLoading = false
                                    onSignInSuccess
                                },
                                onError = { error ->
                                    isLoading = false
                                    errorMessage = error
                                }
                            )
                        },
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFB373D1),
                            contentColor = Color.White
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text("Sign In", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // OR Divider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Divider(modifier = Modifier.weight(1f))
                        Text(
                            text = "OR",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Divider(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // Google Sign-In Button
                    Button(
                        onClick = { onGoogleSignIn() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA4335))
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google), // Replace with your Google icon
                            contentDescription = "Google Sign-In",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign In with Google", color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // Sign Up Link
                    Text(
                        text = "Don't have an account? Sign up here",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onNavigateToSignUp() }
                    )
                }
            }
        }
    )
}

fun handleSignIn(
    email: String,
    password: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
        onError("Please enter a valid email address.")
        return
    }
    if (password.isBlank()) {
        onError("Please enter your password.")
        return
    }

    FirebaseAuth.getInstance()
        .signInWithEmailAndPassword(email, password)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val user = FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    // Check if the email is verified
                    if (!user.isEmailVerified) {
                        // Send a verification email if not verified
                        user.sendEmailVerification()
                            .addOnCompleteListener { verificationTask ->
                                if (verificationTask.isSuccessful) {
                                    onError("A verification email has been sent. Please check your inbox.")
                                } else {
                                    onError("Failed to send verification email.")
                                }
                            }
                    } else {
                        // Fetch user data from Fire store
                        val userId = user.uid
                        FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(userId)
                            .get()
                            .addOnSuccessListener { document ->
                                if (document.exists()) {
                                    // User data exists; proceed with success callback
                                    onSuccess()
                                } else {
                                    // Handle case where user data doesn't exist
                                    onError("User data not found.")
                                }
                            }
                            .addOnFailureListener { exception ->
                                onError("Failed to fetch user data: ${exception.localizedMessage}")
                            }
                    }
                } else {
                    onError("User ID is null.")
                }
            } else {
                onError(task.exception?.localizedMessage ?: "Sign-In failed.")
            }
        }
}

@Composable
fun HomePageScreen(userId: String, onNavigate: (String) -> Unit) {
    var pregnancyWeeks by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedItem by remember { mutableIntStateOf(0) }
    var firstName by remember { mutableStateOf<String?>(null) }

    // Fetch the user's first name from Firebase
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            FirebaseFirestore.getInstance().collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener { document ->
                    firstName = document.getString("firstName") ?: "User"
                }
                .addOnFailureListener { exception ->
                    errorMessage = "Error fetching user data: ${exception.localizedMessage}"
                }
        }
    }

    Scaffold(
        content = { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Background Image
                Image(
                    painter = painterResource(id = R.drawable.home),
                    contentDescription = "Home Background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Main content area
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    // Welcome message
                    Text(
                        text = "Hello ${firstName ?: "User"}, Welcome To BumpBeats!",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Fetch pregnancy weeks from Firebase
                    LaunchedEffect(userId) {
                        if (userId.isNotEmpty()) {
                            getPregnancyWeek(userId, onSuccess = { weeks ->
                                pregnancyWeeks = weeks
                            }, onError = { error ->
                                errorMessage = error
                            })
                        } else {
                            errorMessage = "User ID is invalid."
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = "Error: $errorMessage",
                            color = Color.Red,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        // Baby Growth Tracker
                        BabyGrowthTracker(pregnancyWeeks)

                        Spacer(modifier = Modifier.height(10.dp))

                        // Due Date Card
                        DueDateCard(pregnancyWeeks)

                        Spacer(modifier = Modifier.height(10.dp))

                        //AI Assistant
                        AIAssistantCard(onNavigate = onNavigate)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 20.dp
            ) {
                NavigationBarItem(
                    icon = {
                        Image(
                            painter = painterResource(id = R.drawable.home_icon),
                            contentDescription = "Home",
                            modifier = Modifier
                                .size(30.dp) // Adjust image size
                                .clickable {
                                    selectedItem = 0
                                    onNavigate("HomePage")
                                }
                        )
                    },
                    label = { Text("Home", fontSize = 10.sp) },
                    selected = selectedItem == 0,
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent // Disable default background
                    ),
                    onClick = { /* Empty because image is already clickable */ }
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.appointment),
                            contentDescription = "Appointments",
                            modifier = Modifier.size(30.dp) // Smaller icon size
                        )
                    },
                    label = { Text("Appointment", fontSize = 10.sp) },
                    selected = selectedItem == 1,
                    onClick = {
                        selectedItem = 1
                        onNavigate("AppointmentsPage")
                    }
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.health),
                            contentDescription = "Health",
                            modifier = Modifier.size(30.dp)
                        )
                    },
                    label = { Text("Health", fontSize = 10.sp) },
                    selected = selectedItem == 2,
                    onClick = {
                        selectedItem = 2
                        onNavigate("HealthTrackerPage")
                    }
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.calendar_home),
                            contentDescription = "Calendar",
                            modifier = Modifier.size(30.dp)
                        )
                    },
                    label = { Text("Calendar", fontSize = 10.sp) },
                    selected = selectedItem == 3,
                    onClick = {
                        selectedItem = 3
                        onNavigate("CalendarPage")
                    }
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.profile),
                            contentDescription = "Profile",
                            modifier = Modifier.size(30.dp)
                        )
                    },
                    label = { Text("Profile", fontSize = 10.sp) },
                    selected = selectedItem == 4,
                    onClick = {
                        selectedItem = 4
                        onNavigate("SettingsPage")
                    }
                )
            }
        }
    )
}

@Composable
fun BabyGrowthTracker(pregnancyWeeks: Int) {
    // List of fruits with their corresponding weeks and images
    val fruitSizes = listOf(
        FruitSize(4, "A Poppy Seed", R.drawable.poppy_seed),
        FruitSize(5, "A Sesame Seed", R.drawable.sesame_seed),
        FruitSize(6, "A Single Pea", R.drawable.single_pea),
        FruitSize(7, "A Blueberry", R.drawable.blueberry),
        FruitSize(8, "A Raspberry", R.drawable.raspberry),
        FruitSize(9, "A Grape", R.drawable.grape),
        FruitSize(10, "A Kumquat", R.drawable.kumquat),
        FruitSize(11, "A Fig", R.drawable.fig),
        FruitSize(12, "A Lime", R.drawable.lime),
        FruitSize(13, "A Lemon", R.drawable.lemon),
        FruitSize(14, "A Peach", R.drawable.peach),
        FruitSize(15, "An Apple", R.drawable.apple),
        FruitSize(16, "An Avocado", R.drawable.avocado),
        FruitSize(17, "A Pear", R.drawable.pear),
        FruitSize(18, "A Sweet Potato", R.drawable.sweet_potato),
        FruitSize(19, "A Mango", R.drawable.mango),
        FruitSize(20, "A Banana", R.drawable.banana),
        FruitSize(21, "A Carrot", R.drawable.carrot),
        FruitSize(22, "A Papaya", R.drawable.papaya),
        FruitSize(23, "A Grapefruit", R.drawable.grapefruit),
        FruitSize(24, "An Corn", R.drawable.corn),
        FruitSize(25, "An Acorn Squash", R.drawable.acorn_squash),
        FruitSize(26, "A Zucchini", R.drawable.zucchini),
        FruitSize(27, "A Cauliflower", R.drawable.cauliflower),
        FruitSize(28, "An Eggplant", R.drawable.eggplant),
        FruitSize(29, "A Butternut Squash", R.drawable.butternut_squash),
        FruitSize(30, "A Cabbage", R.drawable.cabbage),
        FruitSize(31, "A Coconut", R.drawable.coconut),
        FruitSize(32, "A Jicama", R.drawable.jicama),
        FruitSize(33, "A Pineapple", R.drawable.pineapple),
        FruitSize(34, "A Cantaloupe", R.drawable.cantaloupe),
        FruitSize(35, "A Honeydew", R.drawable.honeydew),
        FruitSize(36, "A Lettuce", R.drawable.lettuce),
        FruitSize(37, "A Swiss Chard", R.drawable.swiss_chard),
        FruitSize(38, "A Rhubarb", R.drawable.rhubarb),
        FruitSize(39, "A Pumpkin", R.drawable.pumpkin),
        FruitSize(40, "A Watermelon", R.drawable.watermelon)
    )

    val fruit = fruitSizes.find { it.week == pregnancyWeeks } ?: FruitSize(1, "An Apple Seed", R.drawable.apple_seed)

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFA)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Baby Growth Tracker",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6A1B9A)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Week $pregnancyWeeks - You're the size of ${fruit.fruitName}!",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.DarkGray,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Fruit Image & Baby Image
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = fruit.fruitImage),
                    contentDescription = fruit.fruitName,
                    modifier = Modifier.size(100.dp)
                )
                Image(
                    painter = painterResource(id = R.drawable.baby),
                    contentDescription = "Baby icon",
                    modifier = Modifier.size(100.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // See More Button
            Button(
                onClick = { /* Navigate to detailed growth tracker */ },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF002A4D)), // Purple button
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("See More", color = Color.White)
            }
        }
    }
}

// Helper function to fetch pregnancy weeks from Firebase
fun getPregnancyWeek(userId: String, onSuccess: (Int) -> Unit, onError: (String) -> Unit) {
    FirebaseFirestore.getInstance()
        .collection("users")
        .document(userId)
        .get()
        .addOnSuccessListener { document ->
            if (document.exists()) {
                val pregnancyWeeks = document.getString("pregnancyWeeks")?.toIntOrNull()
                pregnancyWeeks?.let {
                    onSuccess(it)
                } ?: onError("Invalid pregnancy weeks")
            } else {
                onError("User data not found")
            }
        }
        .addOnFailureListener { exception ->
            onError("Error fetching pregnancy week: ${exception.localizedMessage}")
        }
}

// Data class to hold fruit size information
data class FruitSize(val week: Int, val fruitName: String, val fruitImage: Int)

@Composable
fun DueDateCard(pregnancyWeeks: Int) {
    val dueDate = calculateDueDate(pregnancyWeeks)

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFA)), // Light purple background
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.due_date),
                contentDescription = "Expected Due Date",
                modifier = Modifier.size(60.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "Expected Due Date",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6A1B9A) // Dark purple
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dueDate,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.DarkGray
                )
            }
        }
    }
}

// Function to calculate the due date based on current pregnancy weeks
fun calculateDueDate(currentWeeks: Int): String {
    val remainingWeeks = 40 - currentWeeks
    val calendar = Calendar.getInstance().apply {
        add(Calendar.WEEK_OF_YEAR, remainingWeeks)
    }
    val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return formatter.format(calendar.time)
}

@Composable
fun AIAssistantCard(onNavigate: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFA)), // Light blue background
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "AI Pregnancy Assistant",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6A1B9A)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Logo of the pregnancy assistant
            Image(
                painter = painterResource(id = R.drawable.pregnancy_assistant), // Replace with your logo
                contentDescription = "Pregnancy Assistant Logo",
                modifier = Modifier.size(80.dp) // Adjust size as needed
            )

            Text(
                text = "Chat with me for advice, tips, and emotional support!",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.DarkGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Start Chat Button
            Button(
                onClick = { onNavigate("ChatPage") },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF002A4D)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Start Chat", color = Color.White)
            }
        }
    }
}

@Composable
fun ChatPage(onBack: () -> Unit) {
    var userInput by remember { mutableStateOf("") }
    var chatMessages by remember { mutableStateOf<List<String>>(emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Chat Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "AI Pregnancy Assistant",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Chat Messages
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(chatMessages) { message ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)), // Light blue background
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black
                    )
                }
            }
        }

        // Input Field
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = userInput,
                onValueChange = { userInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type your message...") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (userInput.isNotEmpty()) {
                        // Add user message to chat
                        chatMessages = chatMessages + listOf("You: $userInput")
                        // Get AI response (call your AI API here)
                        val aiResponse = getAIResponse(userInput)
                        chatMessages = chatMessages + listOf("AI: $aiResponse")
                        userInput = ""
                    }
                }
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}

// Simulate AI response (replace with actual API call)
fun getAIResponse(userInput: String): String {
    return when {
        userInput.contains("sad") -> "It's okay to feel sad sometimes. Try taking deep breaths or talking to someone you trust."
        userInput.contains("diet") -> "Focus on a balanced diet with fruits, vegetables, and proteins. Avoid processed foods."
        else -> "I'm here to help! Let me know how I can assist you."
    }
}
@Composable
fun AppointmentsPage() {
    Text("Appointments Page")
}

@Composable
fun HealthTrackerPage() {
    Text("Health Tracker Page")
}

@Composable
fun CalendarPage() {
    Text("Calendar Page")
}

@Composable
fun SettingsPage() {
    Text("Settings Page")
}


@Preview(showBackground = true)
@Composable
fun SignInScreenPreview() {
    BumpBeatsTheme {
        SignInScreen(
            onSignInSuccess = {},
            onNavigateToSignUp = {},
            onGoogleSignIn = {} // Provide a placeholder function for the preview
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SignUpScreenPreview() {
    BumpBeatsTheme {
        SignUpScreen(
            onSignUpSuccess = {},
            onNavigateToSignIn = {}
        )
    }
}


@Preview(showBackground = true)
@Composable
fun PreviewHomePageScreen() {
    BumpBeatsTheme {
        HomePageScreen(
            userId = "testUser",
            onNavigate = {} // Dummy lambda for preview
        )
    }
}
