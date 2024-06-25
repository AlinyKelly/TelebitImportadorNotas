package botoes

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava
import br.com.sankhya.extensions.actionbutton.ContextoAcao
import br.com.sankhya.jape.core.JapeSession
import br.com.sankhya.jape.wrapper.JapeFactory
import br.com.sankhya.modelcore.MGEModelException
import com.sankhya.ce.jape.JapeHelper
import utilitarios.DiversosAdv.retornaVO
import utilitarios.getPropFromJSON
import utilitarios.post
import java.math.BigDecimal
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.*
import java.util.stream.Collectors
import kotlin.collections.ArrayList

class GerarPedido : AcaoRotinaJava {
    private lateinit var nunotaRetorno: String

    override fun doAction(contextoAcao: ContextoAcao?) {
        val notas: MutableList<BigDecimal?> = ArrayList()
        val nunotaPOcab: MutableList<BigDecimal> = ArrayList()
        val linhas = contextoAcao!!.linhas
        var nunotaPedido: BigDecimal? = null

        for (linha in linhas) {
            val codImportacao = linha.getCampo("CODIMPPO")

            val pos = JapeHelper.getVOs(
                "AD_IMPORTPOITE",
                "CODIMPPO = $codImportacao"
            )
            for (po in pos) {
                val codimpIte = po.asBigDecimalOrZero("CODPOITE")
                val nunota = po.asBigDecimalOrZero("NUNOTA")
                val tipoOperacao = contextoAcao.getParametroSistema("TOPPO")
                val dtFaturamento = converterDataFormato(po.asTimestamp("EMISSAOPO").toString())
                val sequencia = po.asBigDecimalOrZero("SEQUENCIA")
                val pedido = po.asString("PEDIDOPO")
                val itemPO = po.asBigDecimalOrZero("ITEMPO")

                if (!nunotaPOcab.contains(nunota)) {
                    val json = """  {
                                     "serviceName":"SelecaoDocumentoSP.faturar",
                                     "requestBody":{
                                        "notas":{
                                           "codTipOper":$tipoOperacao,
                                           "dtFaturamento":"$dtFaturamento",
                                           "tipoFaturamento":"FaturamentoNormal",
                                           "dataValidada":true,
                                           "notasComMoeda":{
                                              
                                           },
                                           "nota":[
                                              {
                                                 "${'$'}": $nunota
                                              }
                                           ],
                                           "codLocalDestino":"",
                                           "faturarTodosItens":true,
                                           "umaNotaParaCada":"false",
                                           "ehWizardFaturamento":true,
                                           "dtFixaVenc":"",
                                           "ehPedidoWeb":false,
                                           "nfeDevolucaoViaRecusa":false
                                        }
                                     }
                                  }""".trimIndent()

                    val (postbody) = post("mgecom/service.sbr?serviceName=SelecaoDocumentoSP.faturar&outputType=json", json)
                    val status = getPropFromJSON("status", postbody)

                    if (status == "1") {

                        nunotaRetorno = getPropFromJSON("responseBody.notas.nota.${'$'}", postbody)

                        atualizarCabPedido("CabecalhoNota", nunotaRetorno, pedido)

//                        inserirNunotaImp("AD_IMPORTPOITE", codImportacao, codimpIte, nunotaRetorno.toBigDecimal())

//                        inserirErroAPI("AD_IMPORTPOITE", codImportacao, codimpIte, "")

                        inserirNunotaOuErroAPI("AD_IMPORTPOITE", codImportacao, codimpIte, nunotaRetorno.toBigDecimal(), "")

                        nunotaPOcab.add(nunota)

                        contextoAcao!!.setMensagemRetorno(
                            "Pedidos criados com sucesso! " + notas.stream().map { obj: BigDecimal? -> obj.toString() }
                                .collect(Collectors.joining(",")))
                    } else {
                        val statusMessage = getPropFromJSON("statusMessage", postbody)

//                        inserirErroAPI("AD_IMPORTPOITE", codImportacao, codimpIte, statusMessage)

                        inserirNunotaOuErroAPI("AD_IMPORTPOITE", codImportacao, codimpIte, BigDecimal.ZERO, statusMessage)

                    }

                } else {
//                    inserirNunotaImp("AD_IMPORTPOITE", codImportacao, codimpIte, nunotaRetorno.toBigDecimal())
//
//                    inserirErroAPI("AD_IMPORTPOITE", codImportacao, codimpIte, "")

                    inserirNunotaOuErroAPI("AD_IMPORTPOITE", codImportacao, codimpIte, nunotaRetorno.toBigDecimal(), "")
                }

                atualizarItensPedido(sequencia, itemPO)

            }
        }

    }

    private fun atualizarItensPedido(sequencia: BigDecimal?, itemPO: BigDecimal?) {
        var hnd: JapeSession.SessionHandle? = null

        try {
            hnd = JapeSession.open()
            JapeFactory.dao("ItemNota").prepareToUpdateByPK(nunotaRetorno, sequencia)
                .set("AD_ITEMPO", itemPO)
                .update()
        } catch (e: Exception) {
            MGEModelException.throwMe(e)
        } finally {
            JapeSession.close(hnd)
        }
    }

    private fun atualizarCabPedido(
        intancia: String,
        pknunota: Any,
        pedido: String,
    ) {
        var hnd: JapeSession.SessionHandle? = null

        try {
            hnd = JapeSession.open()
            JapeFactory.dao(intancia).prepareToUpdateByPK(pknunota)
                .set("AD_NROPO", pedido)
                .update()
        } catch (e: Exception) {
            MGEModelException.throwMe(e)
        } finally {
            JapeSession.close(hnd)
        }
    }

    private fun inserirNunotaImp(
        intancia: String,
        codImportacao: Any,
        codigoIteImportacao: BigDecimal,
        nunotaPedidoFaturado: BigDecimal?
    ) {
        var hnd: JapeSession.SessionHandle? = null

        try {
            hnd = JapeSession.open()
            JapeFactory.dao(intancia).prepareToUpdateByPK(codImportacao, codigoIteImportacao)
                .set("NUNOTAPED", nunotaPedidoFaturado)
                .update()
        } catch (e: Exception) {
            MGEModelException.throwMe(e)
        } finally {
            JapeSession.close(hnd)
        }
    }

    private fun inserirErroAPI(
        intancia: String,
        codImportacao: Any,
        codigoIteImportacao: BigDecimal,
        mensagemErro: String?
    ) {
        var hnd: JapeSession.SessionHandle? = null

        try {
            hnd = JapeSession.open()
            JapeFactory.dao(intancia).prepareToUpdateByPK(codImportacao, codigoIteImportacao)
                .set("ERROR", mensagemErro)
                .update()
        } catch (e: Exception) {
            MGEModelException.throwMe(e)
        } finally {
            JapeSession.close(hnd)
        }
    }

    private fun inserirNunotaOuErroAPI(
        intancia: String,
        codImportacao: Any,
        codigoIteImportacao: BigDecimal,
        nunotaPedidoFaturado: BigDecimal?,
        mensagemErro: String?
    ) {
        var hnd: JapeSession.SessionHandle? = null

        try {
            hnd = JapeSession.open()
            JapeFactory.dao(intancia).prepareToUpdateByPK(codImportacao, codigoIteImportacao)
                .set("NUNOTAPED", nunotaPedidoFaturado)
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