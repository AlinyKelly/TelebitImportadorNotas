package botoes

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava
import br.com.sankhya.extensions.actionbutton.ContextoAcao
import br.com.sankhya.jape.core.JapeSession
import br.com.sankhya.jape.wrapper.JapeFactory
import br.com.sankhya.modelcore.MGEModelException
import com.sankhya.ce.jape.JapeHelper
import utilitarios.getPropFromJSON
import utilitarios.post
import java.math.BigDecimal
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.*
import java.util.stream.Collectors
import kotlin.collections.ArrayList

class GerarNotaFiscal : AcaoRotinaJava {
    private var nunotaRetorno: String = ""

    override fun doAction(contextoAcao: ContextoAcao?) {
        val notas: MutableList<BigDecimal?> = ArrayList()
        val nunotaPOcab: MutableList<BigDecimal> = ArrayList()
        val linhas = contextoAcao!!.linhas

        for (linha in linhas) {
            val codImportacao = linha.getCampo("CODFS") as BigDecimal

            val pos = JapeHelper.getVOs(
                "AD_IMPORTADORITEFS",
                "CODFS = $codImportacao"
            )
            for (po in pos) {
                val codimpIte = po.asBigDecimalOrZero("CODITEFS")
                val nunota = po.asBigDecimalOrZero("NUNOTA")
                val tipoOperacao = contextoAcao.getParametroSistema("TOPFS")
                var serieTipoOperacao = contextoAcao.getParametroSistema("SERIETOPFS")
                if (serieTipoOperacao == null) {
                    serieTipoOperacao = ""
                } else {
                    serieTipoOperacao = contextoAcao.getParametroSistema("SERIETOPFS")
                }

                val dtFaturamento = converterDataFormato(po.asTimestamp("EMISSAOFS").toString())
                val sequencia = po.asBigDecimalOrZero("SEQUENCIA")
                val nroFS = po.asBigDecimalOrZero("NROFS")
                val qtdFS = po.asBigDecimalOrZero("QTDFS")

                val jsonString = """{
                              "serviceName": "SelecaoDocumentoSP.faturar",
                              "requestBody": {
                                "notas": {
                                  "codTipOper": $tipoOperacao,
                                  "dtFaturamento": "$dtFaturamento",
                                  "serie": "$serieTipoOperacao",
                                  "dtSaida": "",
                                  "hrSaida": "",
                                  "tipoFaturamento": "FaturamentoNormal",
                                  "dataValidada": true,
                                  "notasComMoeda": {},
                                  "nota": [
                                    { "NUNOTA": "$nunota", "itens": { "item": [{ "QTDFAT": $qtdFS, "$": $sequencia }] } }
                                  ],
                                  "codLocalDestino": "",
                                  "conta2": 0,
                                  "faturarTodosItens": false,
                                  "umaNotaParaCada": "false",
                                  "ehWizardFaturamento": true,
                                  "dtFixaVenc": "",
                                  "ehPedidoWeb": false,
                                  "nfeDevolucaoViaRecusa": false,
                                  "isFaturamentoDanfeSeguranca": false
                                },
                                "clientEventList": {
                                  "clientEvent": [
                                    { "$": "br.com.sankhya.actionbutton.clientconfirm" },
                                    { "$": "br.com.sankhya.mgecom.enviar.recebimento.wms.sncm" },
                                    { "$": "comercial.status.nfe.situacao.diferente" },
                                    { "$": "br.com.sankhya.mgecom.compra.SolicitacaoComprador" },
                                    { "$": "br.com.sankhya.mgecom.expedicao.SolicitarUsuarioConferente" },
                                    { "$": "br.com.sankhya.mgecom.nota.adicional.SolicitarUsuarioGerente" },
                                    { "$": "br.com.sankhya.mgecom.cancelamento.nfeAcimaTolerancia" },
                                    { "$": "br.com.sankhya.mgecom.cancelamento.processo.wms.andamento" },
                                    { "$": "br.com.sankhya.mgecom.msg.nao.possui.itens.pendentes" },
                                    { "$": "br.com.sankhya.mgecomercial.event.baixaPortal" },
                                    { "$": "br.com.sankhya.mgecom.valida.ChaveNFeCompraTerceiros" },
                                    { "$": "br.com.sankhya.mgewms.expedicao.validarPedidos" },
                                    { "$": "br.com.sankhya.mgecom.gera.lote.xmlRejeitado" },
                                    { "$": "br.com.sankhya.comercial.solicitaContingencia" },
                                    { "$": "br.com.sankhya.mgecom.cancelamento.notas.remessa" },
                                    { "$": "br.com.sankhya.mgecomercial.event.compensacao.credito.debito" },
                                    {
                                      "$": "br.com.sankhya.modelcore.comercial.cancela.nota.devolucao.wms"
                                    },
                                    { "$": "br.com.sankhya.mgewms.expedicao.selecaoDocas" },
                                    { "$": "br.com.sankhya.mgewms.expedicao.cortePedidos" },
                                    {
                                      "$": "br.com.sankhya.modelcore.comercial.cancela.nfce.baixa.caixa.fechado"
                                    },
                                    { "$": "br.com.utiliza.dtneg.servidor" },
                                    {
                                      "$": "br.com.sankhya.mgecomercial.event.estoque.insuficiente.produto"
                                    }
                                  ]
                                }
                              }
                            }""".trimIndent()

                val (postbody) = post("mgecom/service.sbr?serviceName=SelecaoDocumentoSP.faturar&outputType=json", jsonString)
                val status = getPropFromJSON("status", postbody)

                if (status == "1") {

                    nunotaRetorno = getPropFromJSON("responseBody.notas.nota.${'$'}", postbody)

                    inserirNunotaImp(codImportacao, codimpIte, nunotaRetorno.toBigDecimal())

                    atualizarItensPedido(nunotaRetorno.toBigDecimal(), sequencia, nroFS)

                    inserirErroAPI(codImportacao, codimpIte, "")

                    nunotaPOcab.add(nunota)

                    contextoAcao!!.setMensagemRetorno(
                        "Pedidos faturados com sucesso! " + notas.stream().map { obj: BigDecimal? -> obj.toString() }
                            .collect(Collectors.joining(",")))
                } else {
                    val statusMessage = getPropFromJSON("statusMessage", postbody)

                    inserirErroAPI(codImportacao, codimpIte, statusMessage)
                }
            }
        }
    }

    private fun atualizarItensPedido(nunota: BigDecimal?, sequencia: BigDecimal?, nroFolha: BigDecimal?) {
        var hnd: JapeSession.SessionHandle? = null

        try {
            hnd = JapeSession.open()
            JapeFactory.dao("ItemNota").prepareToUpdateByPK(nunota, sequencia)
                .set("AD_NROFS", nroFolha)
                .update()
        } catch (e: Exception) {
            MGEModelException.throwMe(e)
        } finally {
            JapeSession.close(hnd)
        }
    }

    private fun inserirNunotaImp(
        codImportacao: BigDecimal,
        codigoIteImportacao: BigDecimal,
        nunotaPedidoFaturado: BigDecimal?
    ) {
        var hnd: JapeSession.SessionHandle? = null

        try {
            hnd = JapeSession.open()
            JapeFactory.dao("AD_IMPORTADORITEFS").prepareToUpdateByPK(codImportacao, codigoIteImportacao)
                .set("NUNOTAFS", nunotaPedidoFaturado)
                .update()
        } catch (e: Exception) {
            MGEModelException.throwMe(e)
        } finally {
            JapeSession.close(hnd)
        }
    }

    private fun inserirErroAPI(
        codImportacao: BigDecimal,
        codigoIteImportacao: BigDecimal,
        mensagemErro: String?
    ) {
        var hnd: JapeSession.SessionHandle? = null

        try {
            hnd = JapeSession.open()
            JapeFactory.dao("AD_IMPORTADORITEFS").prepareToUpdateByPK(codImportacao, codigoIteImportacao)
                .set("ERROR", mensagemErro)
                .update()
        } catch (e: Exception) {
            MGEModelException.throwMe(e)
        } finally {
            JapeSession.close(hnd)
        }
    }

    fun formatarDataString(originalDateStr: String): Timestamp? {
        // Formato original da data
        val originalFormat = SimpleDateFormat("M/dd/yyyy", Locale.US)
        // Novo formato desejado
        val newFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

        // Parse da data original
        val date: Date? = originalFormat.parse(originalDateStr)

        return if (date != null) {
            // Formatação da data para o novo formato (string)
            val formattedDateStr = newFormat.format(date)
            // Parse da string formatada para um Date
            val formattedDate: Date? = newFormat.parse(formattedDateStr)
            // Conversão do Date para Timestamp
            formattedDate?.let { Timestamp(it.time) }
        } else {
            null // Retorna null se a data original for inválida
        }

    }

    fun formatarDataString2(originalDateStr: String): Timestamp? {
        // Formato original da data
        val originalFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        // Novo formato desejado
        val newFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

        // Parse da data original
        val date: Date? = originalFormat.parse(originalDateStr)

        return if (date != null) {
            // Formatação da data para o novo formato (string)
            val formattedDateStr = newFormat.format(date)
            // Parse da string formatada para um Date
            val formattedDate: Date? = newFormat.parse(formattedDateStr)
            // Conversão do Date para Timestamp
            formattedDate?.let { Timestamp(it.time) }
        } else {
            null // Retorna null se a data original for inválida
        }

    }

    fun converterDataFormato(input: String): String {
        // Define o formato de entrada (yyyy-MM-dd)
        val formatoEntrada = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        // Define o formato de saída (dd/MM/yyyy)
        val formatoSaida = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        // Converte a string de entrada para um objeto Date
        val data = formatoEntrada.parse(input)

        // Retorna a string formatada no novo formato
        return formatoSaida.format(data)
    }
}