package com.example.util

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.local.entity.StatutVente
import com.example.data.local.entity.VenteDetail
import com.example.domain.model.StoreSettings

object ReceiptPrinter {

    fun printReceipt(
        context: Context,
        detail: VenteDetail,
        settings: StoreSettings,
        onError: (String) -> Unit
    ) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                onError("Service d'impression indisponible sur cet appareil. La vente reste conservée et réimprimable.")
                return
            }

            val html = buildReceiptHtml(detail, settings)
            val webView = WebView(context)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    try {
                        val jobName = "Ticket_${detail.vente.numero}"
                        val adapter = webView.createPrintDocumentAdapter(jobName)
                        printManager.print(
                            jobName,
                            adapter,
                            PrintAttributes.Builder()
                                .setMediaSize(PrintAttributes.MediaSize.ISO_A5)
                                .build()
                        )
                    } catch (ex: Exception) {
                        onError("Impossible de lancer l'impression : ${ex.message ?: "erreur"}")
                    }
                }
            }
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        } catch (ex: Exception) {
            onError("Échec d'aperçu d'impression : ${ex.message ?: "erreur"}. La vente reste conservée.")
        }
    }

    private fun escapeHtml(input: String): String =
        input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private fun buildReceiptHtml(detail: VenteDetail, settings: StoreSettings): String {
        val v = detail.vente
        val dateStr = Formatters.formatDateTimeUtc(v.dateHeureUtc, settings.fuseauHoraire)
        val rows = detail.lignes.joinToString("\n") { line ->
            """
            <tr>
              <td>
                <strong>${escapeHtml(line.nomSnapshot)}</strong><br/>
                <span class="muted">${escapeHtml(line.referenceSnapshot)}</span>
              </td>
              <td class="num">${line.quantite}</td>
              <td class="num">${escapeHtml(Formatters.formatFcfa(line.prixUnitaire))}</td>
              <td class="num"><strong>${escapeHtml(Formatters.formatFcfa(line.sousTotal))}</strong></td>
            </tr>
            """.trimIndent()
        }
        val cancelledBanner = if (v.statut == StatutVente.ANNULEE) {
            val reason = detail.annulation?.motif ?: ""
            """
            <div class="cancelled">
              VENTE ANNULÉE ${if (reason.isNotBlank()) "— ${escapeHtml(reason)}" else ""}
            </div>
            """.trimIndent()
        } else ""

        return """
        <!DOCTYPE html>
        <html lang="fr">
        <head>
          <meta charset="UTF-8"/>
          <style>
            body { font-family: sans-serif; color: #1F2937; margin: 18px; font-size: 13px; }
            .header { text-align: center; border-bottom: 2px solid #1F2937; padding-bottom: 10px; margin-bottom: 12px; }
            .store { font-size: 18px; font-weight: bold; }
            .muted { color: #6B7280; font-size: 11px; }
            .meta { margin-bottom: 12px; line-height: 1.5; }
            table { width: 100%; border-collapse: collapse; margin: 10px 0; }
            th { text-align: left; border-bottom: 1px solid #9CA3AF; padding: 6px 4px; font-size: 12px; }
            td { padding: 6px 4px; border-bottom: 1px dashed #E5E7EB; vertical-align: top; }
            .num { text-align: right; white-space: nowrap; }
            .totals { margin-top: 12px; border-top: 2px solid #1F2937; padding-top: 8px; }
            .totals-row { display: flex; justify-content: space-between; margin: 4px 0; }
            .grand { font-size: 16px; font-weight: bold; }
            .cancelled { background: #FEE2E2; color: #B91C1C; padding: 8px; text-align: center; font-weight: bold; margin-bottom: 10px; border: 1px solid #FCA5A5; }
            .footer { margin-top: 18px; text-align: center; font-size: 11px; color: #6B7280; }
          </style>
        </head>
        <body>
          <div class="header">
            <div class="store">${escapeHtml(settings.nomMagasin)}</div>
            ${if (settings.coordonneesMagasin.isNotBlank()) "<div class='muted'>${escapeHtml(settings.coordonneesMagasin)}</div>" else ""}
          </div>
          $cancelledBanner
          <div class="meta">
            <div><strong>Ticket N° :</strong> ${escapeHtml(v.numero)}</div>
            <div><strong>Date :</strong> $dateStr</div>
            <div><strong>Vendeur :</strong> ${escapeHtml(v.vendeurNom)}</div>
            <div><strong>Paiement :</strong> ${escapeHtml(v.modePaiement.libelle)}</div>
          </div>
          <table>
            <thead>
              <tr>
                <th>Article</th>
                <th class="num">Qté</th>
                <th class="num">P.U.</th>
                <th class="num">Sous-total</th>
              </tr>
            </thead>
            <tbody>
              $rows
            </tbody>
          </table>
          <div class="totals">
            <div class="totals-row grand"><span>TOTAL</span><span>${escapeHtml(Formatters.formatFcfa(v.total))}</span></div>
            <div class="totals-row"><span>Montant reçu</span><span>${escapeHtml(Formatters.formatFcfa(v.montantRecu))}</span></div>
            <div class="totals-row"><span>Monnaie rendue</span><span>${escapeHtml(Formatters.formatFcfa(v.monnaieRendue))}</span></div>
          </div>
          <div class="footer">Merci de votre visite — ${escapeHtml(settings.nomMagasin)}</div>
        </body>
        </html>
        """.trimIndent()
    }
}
