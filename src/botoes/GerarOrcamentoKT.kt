package botoes

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava
import br.com.sankhya.extensions.actionbutton.ContextoAcao
import br.com.sankhya.jape.core.JapeSession
import br.com.sankhya.jape.dao.JdbcWrapper
import br.com.sankhya.jape.sql.NativeSql
import br.com.sankhya.jape.vo.DynamicVO
import br.com.sankhya.modelcore.MGEModelException
import br.com.sankhya.modelcore.util.EntityFacadeFactory
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sankhya.ce.jape.JapeHelper
import utilitarios.getPropFromJSON
import utilitarios.post
import utilitarios.retornaVO
import java.math.BigDecimal
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class GerarOrcamentoKT : AcaoRotinaJava {
    private var codImportacao: BigDecimal? = null
    private var loteBOQcab: MutableList<BigDecimal> =
        ArrayList() //Armazena o nro da BOQ para verificar se o cabeçalho já foi criado.
    private val codImpIteToLoteBOQ = mutableListOf<Pair<BigDecimal, BigDecimal>>()

    override fun doAction(contextoAcao: ContextoAcao?) {
        val linhas = contextoAcao!!.linhas

        var jsonResult: JsonObject? = null
        var cabecalhoJson: JsonObject? = null
        var items: JsonArray? = null

        for (linha in linhas) {
            codImportacao = linha.getCampo("CODIMPORTACAO") as BigDecimal

            val boqs = JapeHelper.getVOs("AD_IMPORTNOTASITE", "CODIMPORTACAO = $codImportacao")

            for (boq in boqs) {
                val codImpIte = boq.asBigDecimalOrZero("CODIMPITE")
                val idAtividade = boq.asBigDecimalOrZero("IDATIVIDADE")
                val loteBOQ = boq.asBigDecimalOrZero("LOTEBOQ")
                val codprod = boq.asBigDecimalOrZero("CODPROD")
                val qtd = boq.asBigDecimalOrZero("QTDNEG")
                val vlrUnit = boq.asBigDecimalOrZero("VLRUNIT")
                val nunota = boq.asBigDecimalOrZero("NUNOTA")
                val codvol = boq.asString("CODVOL")

                if (nunota == null || nunota == BigDecimal.ZERO) {

                    if (!loteBOQcab.contains(loteBOQ)) {
                        if (jsonResult != null) {
                            // Enviar o JSON para a API
                            sendJsonToApi(jsonResult)
                        }

                        // Inicializar novo JSON
                        jsonResult = JsonObject().apply {
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

                        cabecalhoJson = jsonResult.getAsJsonObject("requestBody").getAsJsonObject("nota")
                            .getAsJsonObject("cabecalho")
                        items =
                            jsonResult.getAsJsonObject("requestBody").getAsJsonObject("nota").getAsJsonObject("itens")
                                .getAsJsonArray("item")

                        val cabecalho = getJsonCabecalho(boq, contextoAcao)
                        cabecalho.entrySet().forEach { entry ->
                            cabecalhoJson!!.add(entry.key, entry.value)
                        }
                        loteBOQcab.add(loteBOQ)
                    }

                    val item = JsonObject().apply {
                        add("NUNOTA", JsonObject())
                        add("CODPROD", JsonObject().apply { addProperty("\$", codprod.toString()) })
                        add("CODVOL", JsonObject().apply { addProperty("\$", codvol) })
                        add("QTDNEG", JsonObject().apply { addProperty("\$", qtd.toString()) })
                        add("VLRUNIT", JsonObject().apply { addProperty("\$", vlrUnit.toString()) })
                        add("CODLOCALORIG", JsonObject().apply { addProperty("\$", "0") })
                        add("PERCDESC", JsonObject().apply { addProperty("\$", "0") })
                        add("AD_NUMETAPA", JsonObject().apply { addProperty("\$", idAtividade.toString()) })
                    }
                    items!!.add(item)
                    codImpIteToLoteBOQ.add(Pair(codImpIte, loteBOQ))
                }
            }
        }

        //Enviar o último JSON para a API
        if (jsonResult != null) {
            sendJsonToApi(jsonResult)
        }

        contextoAcao.setMensagemRetorno("BOQs criadas com sucesso!")
    }

    private fun sendJsonToApi(jsonResult: JsonObject) {
        val gson = Gson()
        val jsonString = gson.toJson(jsonResult)

        val (postbody) = post("mgecom/service.sbr?serviceName=CACSP.incluirNota&outputType=json", jsonString)
        val status = getPropFromJSON("status", postbody)

        if (status == "1") {
            val nunotaRetorno = getPropFromJSON("responseBody.pk.NUNOTA.${'$'}", postbody).toBigDecimal()

            val buscarSeqItem = JapeHelper.getVOs("ItemNota", "NUNOTA = $nunotaRetorno")
            for (seq in buscarSeqItem) {
                val sequencia = seq.asBigDecimalOrZero("SEQUENCIA")

                var hnd: JapeSession.SessionHandle? = null
                var jdbc: JdbcWrapper? = null
                try {
                    hnd = JapeSession.open()
                    jdbc = EntityFacadeFactory.getDWFFacade().getJdbcWrapper()
                    jdbc.openSession()

                    val queryUpd = NativeSql(jdbc)
                    queryUpd.appendSql("UPDATE AD_IMPORTNOTASITE SET NUNOTA = :NUNOTA, SEQUENCIA = :SEQUENCIA WHERE CODIMPORTACAO = :CODIMPORTACAO AND CODIMPITE = :CODIMPITE AND LOTEBOQ = :LOTEBOQ")
                    queryUpd.setReuseStatements(true)
                    queryUpd.setBatchUpdateSize(500)

                    for ((codImpIte, loteBOQ) in codImpIteToLoteBOQ) {
                        queryUpd.setNamedParameter("CODIMPORTACAO", codImportacao)
                        queryUpd.setNamedParameter("CODIMPITE", codImpIte)
                        queryUpd.setNamedParameter("LOTEBOQ", loteBOQ)
                        queryUpd.setNamedParameter("NUNOTA", nunotaRetorno)
                        queryUpd.setNamedParameter("SEQUENCIA", sequencia)
                        queryUpd.addBatch()
                        queryUpd.cleanParameters()
                    }
                    queryUpd.flushBatchTail()
                    NativeSql.releaseResources(queryUpd)

                } catch (e: Exception) {
                    MGEModelException.throwMe(e)
                } finally {
                    JapeSession.close(hnd)
                }
            }

            confirmarNotaAPI(nunotaRetorno)

            codImpIteToLoteBOQ.clear() //Limpa o mapeamento para a próxima iteração

        } else {
            val statusMessage = getPropFromJSON("statusMessage", postbody)

            var hnd: JapeSession.SessionHandle? = null
            var jdbc: JdbcWrapper? = null

            try {
                hnd = JapeSession.open()
                jdbc = EntityFacadeFactory.getDWFFacade().getJdbcWrapper()
                jdbc.openSession()

                val queryUpd = NativeSql(jdbc)
                queryUpd.appendSql("UPDATE AD_IMPORTNOTASITE SET ERROR = :ERROR WHERE CODIMPORTACAO = :CODIMPORTACAO AND CODIMPITE = :CODIMPITE AND LOTEBOQ = :LOTEBOQ")
                queryUpd.setReuseStatements(true)
                queryUpd.setBatchUpdateSize(500)

                for ((codImpIte, loteBOQ) in codImpIteToLoteBOQ) {
                    queryUpd.setNamedParameter("CODIMPORTACAO", codImportacao)
                    queryUpd.setNamedParameter("CODIMPITE", codImpIte)
                    queryUpd.setNamedParameter("LOTEBOQ", loteBOQ)
                    queryUpd.setNamedParameter("ERROR", statusMessage)
                    queryUpd.addBatch()
                    queryUpd.cleanParameters()
                }
                queryUpd.flushBatchTail()
                NativeSql.releaseResources(queryUpd)

            } catch (e: Exception) {
                MGEModelException.throwMe(e)
            } finally {
                JapeSession.close(hnd)
            }

            codImpIteToLoteBOQ.clear()

        }
    }

    private fun confirmarNotaAPI(nunotaRetorno: BigDecimal) {
        val json = """{
                                "serviceName": "ServicosNfeSP.confirmarNota",
                                "requestBody": {
                                    "nota": {
                                        "compensarNotaAutomaticamente": "false",
                                        "NUNOTA": {
                                            "${'$'}": "{{$nunotaRetorno}}"
                                          }
                                    },
                                    "clientEventList": {
                                        "clientEvent": [
                                            {
                                                "${'$'}": "br.com.sankhya.actionbutton.clientconfirm"
                                            }
                                        ]
                                    }
                                }
                            }""".trimIndent()

        //Cofirmar a nota
        post("mgecom/service.sbr?serviceName=CACSP.confirmarNota&outputType=json", json)
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
