package com.notebaseapk

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.notebaseapk.databinding.ActivityLoginBinding

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private val db = FirebaseFirestore.getInstance()

    private val signInLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            val task =
                GoogleSignIn.getSignedInAccountFromIntent(
                    result.data
                )

            try {
                val account =
                    task.getResult(
                        ApiException::class.java
                    )!!

                firebaseAuthWithGoogle(
                    account.idToken!!
                )
            } catch (e: ApiException) {
                Toast.makeText(
                    this,
                    "Google sign in failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {

        /*
         * LoginActivity juga bisa dibuka langsung setelah logout,
         * tanpa melewati SplashActivity.
         */
        ThemeManager.applySavedTheme(this)

        super.onCreate(savedInstanceState)

        binding =
            ActivityLoginBinding.inflate(
                layoutInflater
            )

        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        val gso =
            GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN
            )
                .requestIdToken(
                    getString(
                        R.string.default_web_client_id
                    )
                )
                .requestEmail()
                .build()

        googleSignInClient =
            GoogleSignIn.getClient(
                this,
                gso
            )

        binding.btnGoogleSignIn.setOnClickListener {
            signInLauncher.launch(
                googleSignInClient.signInIntent
            )
        }

        binding.btnSkip.setOnClickListener {
            openMainAsNewRoot()
        }
    }

    private fun firebaseAuthWithGoogle(
        idToken: String
    ) {
        val credential =
            GoogleAuthProvider.getCredential(
                idToken,
                null
            )

        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->

                if (task.isSuccessful) {
                    checkUserProfile()
                } else {
                    Toast.makeText(
                        this,
                        "Authentication Failed.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun checkUserProfile() {
        val user =
            auth.currentUser ?: return

        db.collection("users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { document ->

                if (
                    document.exists() &&
                    document.contains("phone")
                ) {
                    openMainAsNewRoot()
                } else {
                    openCompleteProfileAsNewRoot()
                }
            }
            .addOnFailureListener {
                openCompleteProfileAsNewRoot()
            }
    }

    private fun openMainAsNewRoot() {
        val intent =
            Intent(
                this,
                MainActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

        startActivity(intent)
        finish()
    }

    private fun openCompleteProfileAsNewRoot() {
        val intent =
            Intent(
                this,
                CompleteProfileActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

        startActivity(intent)
        finish()
    }
}
