package botoes

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava
import br.com.sankhya.extensions.actionbutton.ContextoAcao
import br.com.sankhya.jape.core.JapeSession
import br.com.sankhya.jape.vo.DynamicVO
import br.com.sankhya.jape.wrapper.JapeFactory
import br.com.sankhya.modelcore.MGEModelException
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sankhya.ce.jape.JapeHelper
import utilitarios.getPropFromJSON
import utilitarios.mensagemErro
import utilitarios.post
import utilitarios.retornaVO
import java.math.BigDecimal
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList

class GerarOrcamentoKT : AcaoRotinaJava {
    private var codimpIte: BigDecimal? = null
    private var codImportacao: BigDecimal? = null

    override fun doAction(contextoAcao: ContextoAcao?) {
        val loteBOQcab: MutableList<BigDecimal> = ArrayList() //Armazena o nro da BOQ para verificar se o cabeçalho já foi criado.
        val linhas = contextoAcao!!.linhas

        val jsonResult = JsonObject().apply {
            addProperty("serviceName", "CACSP.incluirNota")
            add("requestBody", JsonObject().apply {
                add("nota", JsonObject().apply {
                    add("cabecalho", JsonObject())
                    add("itens", JsonObject().apply {
                        addProperty("INFORMARPRECO", "True")
                        add("item", JsonArray())
                    })
                })
            })
        }

        val cabecalhoJson = jsonResult.getAsJsonObject("requestBody").getAsJsonObject("nota").getAsJsonObject("cabecalho")
        val items = jsonResult.getAsJsonObject("requestBody").getAsJsonObject("nota").getAsJsonObject("itens").getAsJsonArray("item")

        for (linha in linhas) {
            codImportacao = linha.getCampo("CODIMPORTACAO") as BigDecimal?

            val boqs = JapeHelper.getVOs("AD_IMPORTNOTASITE", "CODIMPORTACAO = $codImportacao")

            for (boq in boqs) {
                codimpIte = boq.asBigDecimalOrZero("CODIMPITE")
                val loteBOQ = boq.asBigDecimalOrZero("LOTEBOQ")
                val codprod = boq.asBigDecimalOrZero("CODPROD")
                val qtd = boq.asBigDecimalOrZero("QTDNEG")
                val vlrUnit = boq.asBigDecimalOrZero("VLRUNIT")

                if (!loteBOQcab.contains(loteBOQ)) {
                    val cabecalho = getJsonCabecalho(boq, contextoAcao)
                    cabecalho.entrySet().forEach { entry ->
                        cabecalhoJson.add(entry.key, entry.value)
                    }
                    loteBOQcab.add(loteBOQ)

                    //Enviar o cabeçalho
                    //Salvar o nunota de retorno
                }

                val item = JsonObject().apply {
                    add("NUNOTA", JsonObject())
                    add("CODPROD", JsonObject().apply { addProperty("\$", codprod.toString()) })
                    add("CODVOL", JsonObject().apply { addProperty("\$", "SV") })
                    add("QTDNEG", JsonObject().apply { addProperty("\$", qtd.toString()) })
                    add("VLRUNIT", JsonObject().apply { addProperty("\$", vlrUnit.toString()) })
                    add("CODLOCALORIG", JsonObject().apply { addProperty("\$", "0") })
                    add("PERCDESC", JsonObject().apply { addProperty("\$", "0") })
                }
                items.add(item)

                //Enviar o item
            }
        }

        val gson = Gson()
        val jsonString = gson.toJson(jsonResult)

        // Enviar o JSON para a API
        // Código para envio da requisição aqui

        mensagemErro(jsonString)

        val (postbody) = post("mgecom/service.sbr?serviceName=CACSP.incluirNota&outputType=json", jsonString)
        val status = getPropFromJSON("status", postbody)

        if (status == "1") {
            val nunotaRetorno = getPropFromJSON("responseBody.pk.NUNOTA.${'$'}", postbody)

//            val boqsAtualizar = JapeHelper.getVOs("AD_IMPORTNOTASITE", "CODIMPORTACAO = $codImportacao")
//
//            for (boqA in boqsAtualizar) {
//                val codimpIteAtualizar = boqA.asBigDecimalOrZero("CODIMPITE")
//
//                var hnd: JapeSession.SessionHandle? = null
//
//                try {
//                    hnd = JapeSession.open()
//                    JapeFactory.dao("AD_IMPORTNOTASITE").prepareToUpdateByPK(codImportacao, codimpIteAtualizar)
//                        .set("NUNOTA", nunotaRetorno)
//                        .update()
//                } catch (e: Exception) {
//                    MGEModelException.throwMe(e)
//                } finally {
//                    JapeSession.close(hnd)
//                }
//            }

        } else {
            val statusMessage = getPropFromJSON("statusMessage", postbody)
            contextoAcao.setMensagemRetorno("Erro api: $statusMessage")
        }

        //contextoAcao.setMensagemRetorno("BOQs criadas com sucesso!")
    }


    private fun getJsonCabecalho(boq: DynamicVO, contextoAcao: ContextoAcao): JsonObject {
        val loteBOQ = boq.asBigDecimalOrZero("LOTEBOQ")
        val idAndamento = boq.asBigDecimalOrZero("IDANDAMENTO")
        val projetoTelebit = boq.asString("CODPROJ").toBigDecimal()
        val contratoTelebit = boq.asString("NUMCONTRATO").toBigDecimal()
        val codparc = boq.asBigDecimal("CODPARC")
        val tipmov = boq.asString("TIPMOV")
        val dtneg = formatDate(boq.asTimestamp("DTINCBOQ").toString())
        val tipoVenda = contextoAcao.getParametroSistema("TIPNEGBOQ")
        var tipoPedido = ""
        var top: BigDecimal? = null
        var contrato: BigDecimal? = null
        var projeto: BigDecimal? = null

        if (tipmov == "V") {
            tipoPedido = "P"
            top = contextoAcao.getParametroSistema("TOPBOQ") as BigDecimal
        } else {
            tipoPedido = "O"
            top = contextoAcao.getParametroSistema("TOPBOQCOMPRAS") as BigDecimal
        }

        //buscar contrato
        val buscarContrato = retornaVO("Contrato", "AD_CONTRATO = $contratoTelebit")
        if (buscarContrato != null) {
            contrato = buscarContrato.asBigDecimalOrZero("NUMCONTRATO")
            projeto = buscarContrato.asBigDecimalOrZero("CODPROJ")
        } else {
            contrato = BigDecimal.ZERO
            projeto = BigDecimal.ZERO
        }

        return JsonObject().apply {
            add("NUNOTA", JsonObject())
            add("TIPMOV", JsonObject().apply { addProperty("\$", tipoPedido) })
            add("DTNEG", JsonObject().apply { addProperty("\$", dtneg) })
            add("CODTIPVENDA", JsonObject().apply { addProperty("\$", "$tipoVenda") })
            add("CODPARC", JsonObject().apply { addProperty("\$", "$codparc") })
            add("CODTIPOPER", JsonObject().apply { addProperty("\$", "$top") })
            add("CODEMP", JsonObject().apply { addProperty("\$", "1") })
            add("CODPROJ", JsonObject().apply { addProperty("\$", "$projeto") })
            add("NUMCONTRATO", JsonObject().apply { addProperty("\$", "$contrato") })
            add("AD_LOTEBOQ", JsonObject().apply { addProperty("\$", "$loteBOQ") })
            add("AD_NUFAP", JsonObject().apply { addProperty("\$", "$idAndamento") })
        }
    }

    fun formatarDataString(originalDateStr: String): Timestamp? {
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

    fun formatDate(inputDate: String): String {
        // Define o formato de entrada da data
        val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        // Define o formato de saída da data
        val outputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        // Converte a data de entrada para LocalDate
        val date = LocalDate.parse(inputDate, inputFormatter)

        // Formata a data no formato de saída
        return date.format(outputFormatter)
    }

}
