package com.example.remitexapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class FahrernummerEingabeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fahrernummereingabe)

        val button = findViewById<Button>(R.id.buttonbestaetigen)
        val editText = findViewById<EditText>(R.id.editTextFahrernummer)
       // val passwordInput = findViewById<EditText>(R.id.editTextPassword) // Für Code mit Passwort Abfrage
        val buttonTransferDaten = findViewById<Button>(R.id.buttonTransferDaten)

        button.setOnClickListener {
            val fahrerNummer = editText.text.toString()

            if (fahrerNummer.isNotEmpty()) {
                val intent = Intent(this, ContainerErfassungActivity::class.java).apply {
                    putExtra("fahrernummer", fahrerNummer)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Bitte eine Fahrernummer eingeben.", Toast.LENGTH_SHORT).show()
            }
        }

       /*
       // Code mit Passwort Abfrage
       button.setOnClickListener {
            val fahrerNummer = editText.text.toString()
            val password = passwordInput.text.toString()

            if (fahrerNummer.isNotEmpty() && password == "rem199") {
                val intent = Intent(this, ContainerErfassungActivity::class.java).apply {
                    putExtra("fahrernummer", fahrerNummer)
                }
                startActivity(intent)
            } else if (password != "rem199") {
                Toast.makeText(this, "Passwort falsch. Bitte erneut versuchen.", Toast.LENGTH_SHORT).show()
            }
        }
        */

        buttonTransferDaten.setOnClickListener {
            val intent = Intent(this, ExportActivity::class.java)
            startActivity(intent)
        }
    }
}
