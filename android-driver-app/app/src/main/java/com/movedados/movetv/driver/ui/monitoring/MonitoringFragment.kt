package com.movedados.movetv.driver.ui.monitoring

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.movedados.movetv.driver.R

class MonitoringFragment : Fragment() {

    // Número oficial de atendimento MoveDados, no formato internacional exigido pelo WhatsApp
    private val whatsappNumber = "5534998380144"
    private val referralEmail = "Operacao@movedados.com.br"

    private val ufList = listOf(
        "Selecione", "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO",
        "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN",
        "RS", "RO", "RR", "SC", "SP", "SE", "TO"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_monitoring, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialButton>(R.id.btnWhatsapp).setOnClickListener { openWhatsapp() }
        view.findViewById<MaterialButton>(R.id.btnIndicar).setOnClickListener { showReferralDialog() }
    }

    private fun openWhatsapp() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$whatsappNumber"))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Não foi possível abrir o WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== INDICAR UM MOTORISTA ====================

    private fun showReferralDialog() {
        val ctx = requireContext()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }

        val etNome = fieldWithLabel(root, "Nome do Motorista Indicado")
        val etTelefone = fieldWithLabel(root, "Telefone (XX) XXXXX-XXXX")
        etTelefone.addTextChangedListener(PhoneMaskWatcher(etTelefone))

        val spEstado = spinnerWithLabel(root, "Estado", ufList)
        val etCidade = fieldWithLabel(root, "Cidade")

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("Indique um Motorista")
            .setView(root)
            .setPositiveButton("Enviar", null) // sobrescrito abaixo para não fechar em caso de erro
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val nome = etNome.text.toString().trim()
                val telefone = etTelefone.text.toString().trim()
                val estado = (spEstado.selectedItem as? String).takeIf { it != "Selecione" }
                val cidade = etCidade.text.toString().trim()

                if (nome.isBlank() || telefone.isBlank() || estado == null || cidade.isBlank()) {
                    Toast.makeText(ctx, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                sendReferralEmail(nome, telefone, estado, cidade)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun fieldWithLabel(container: LinearLayout, label: String): EditText {
        container.addView(TextView(requireContext()).apply {
            text = label
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textSize = 12f
            setPadding(0, dp(10), 0, dp(2))
        })
        val editText = EditText(requireContext())
        container.addView(editText)
        return editText
    }

    private fun spinnerWithLabel(container: LinearLayout, label: String, items: List<String>): Spinner {
        container.addView(TextView(requireContext()).apply {
            text = label
            setTextColor(resources.getColor(R.color.text_secondary, null))
            textSize = 12f
            setPadding(0, dp(10), 0, dp(2))
        })
        val spinner = Spinner(requireContext())
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        container.addView(spinner)
        return spinner
    }

    /** Abre o app de email do motorista já com destinatário, assunto e corpo preenchidos.
     *  O app não envia o email sozinho (nenhum app deveria guardar senha de email) — o
     *  motorista só precisa tocar em "Enviar" no próprio aplicativo de email dele. */
    private fun sendReferralEmail(nome: String, telefone: String, estado: String, cidade: String) {
        val body = """
            Nova indicação de motorista:

            Nome: $nome
            Telefone: $telefone
            Estado: $estado
            Cidade: $cidade
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(referralEmail))
            putExtra(Intent.EXTRA_SUBJECT, "Indicação de Motorista - $nome")
            putExtra(Intent.EXTRA_TEXT, body)
        }

        try {
            startActivity(Intent.createChooser(intent, "Enviar indicação por email"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Nenhum aplicativo de email encontrado", Toast.LENGTH_LONG).show()
        }
    }

    /** Máscara (XX) XXXXX-XXXX reaproveitada aqui. */
    private class PhoneMaskWatcher(private val editText: EditText) : TextWatcher {
        private var isUpdating = false
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (isUpdating || s == null) return
            isUpdating = true
            val digits = s.toString().filter { it.isDigit() }
            val mask = "(##) #####-####"
            val formatted = StringBuilder()
            var i = 0
            for (m in mask) {
                if (i >= digits.length) break
                if (m == '#') { formatted.append(digits[i]); i++ } else formatted.append(m)
            }
            editText.setText(formatted.toString())
            editText.setSelection(formatted.length)
            isUpdating = false
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
