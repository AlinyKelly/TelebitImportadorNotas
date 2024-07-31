package botoes

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava
import br.com.sankhya.extensions.actionbutton.ContextoAcao
import br.com.sankhya.jape.core.JapeSession
import br.com.sankhya.jape.vo.DynamicVO
import br.com.sankhya.jape.wrapper.JapeFactory
import br.com.sankhya.jape.wrapper.fluid.FluidCreateVO
import br.com.sankhya.modelcore.MGEModelException
import br.com.sankhya.ws.ServiceContext
import org.apache.commons.io.FileUtils
import utilitarios.getFluidCreateVO
import utilitarios.getPropFromJSON
import utilitarios.post
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.math.BigDecimal
import java.sql.Timestamp
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern

/*
* Botão para importar as informações da BOQ
* */
class ImportarBOQ : AcaoRotinaJava {

    private var codImportador: BigDecimal? = null

    @Throws(MGEModelException::class, IOException::class)
    override fun doAction(contextoAcao: ContextoAcao) {
        var hnd: JapeSession.SessionHandle? = null

        var ultimaLinhaJson: LinhaJson? = null

        val linhasSelecionadas = contextoAcao.linhas

        try {
            for (linha in linhasSelecionadas) {
                var count = 0

                codImportador = linha.getCampo("CODIMPORTACAO") as BigDecimal?
                val data = linha.getCampo("ARQUIVO") as ByteArray?
                val ctx = ServiceContext.getCurrent()
                val file = File(ctx.tempFolder, "IMPBOQ" + System.currentTimeMillis())
                FileUtils.writeByteArrayToFile(file, data)

                hnd = JapeSession.open()

                BufferedReader(FileReader(file)).use { br ->
                    var line = br.readLine()

                    while (line != null) {
                        if (count == 0) {
                            count++
                            line = br.readLine()
                            continue
                        }
                        count++

                        if (line.contains("__end_fileinformation__")) {
                            // The substituted value will be contained in the result variable
                            line = getReplaceFileInfo(line);
                        }

                        val json = trataLinha(line)
                        ultimaLinhaJson = json

                        val idAtividade = json.idatividade.trim()
                        val loteBOQ = json.loteBOQ.trim()
                        val tipoBOQ = json.tipoBOQ.trim()
                        val dataInc = json.dataInclusaoBOQ.trim()
                        val grupoUsuarios = json.grupoUsuarios.trim()
                        val orgChave = json.orgChave.trim()
                        val oc = json.ocBOQ.trim()
                        val siteID = json.siteID.trim()
                        val enderecoID = json.enderecoID.trim()
                        val municipio = json.municipioBOQ.trim()
                        val ufBOQ = json.ufBOQ.trim()
                        val statusBOQ = json.statusBOQ.trim()
                        val statusFin: String

                        when (statusBOQ) {
                            "BOQ Solicitada" -> {
                                statusFin = "1"
                            }

                            "Criado" -> {
                                statusFin = "2"
                            }

                            "Pedido de PO" -> {
                                statusFin = "3"
                            }

                            "PO Emitido" -> {
                                statusFin = "4"
                            }

                            "Reprovado" -> {
                                statusFin = "5"
                            }

                            "Revisado" -> {
                                statusFin = "6"
                            }

                            "Revisão" -> {
                                statusFin = "7"
                            }

                            "RP" -> {
                                statusFin = "8"
                            }

                            "Ag. Faturamento" -> {
                                statusFin = "9"
                            }

                            "Faturado" -> {
                                statusFin = "10"
                            }

                            else -> {
                                statusFin = "N"
                            }
                        }

                        //GERAR O LANÇAMENTO DA BOQ AQUI
                        // Criar o JSON com as informações necessárias para criar o lançamento
                        // Enviar o json e receber o NUNOTA enviado no retorno.
                        // Atualizar o campo NUNOTABOQ na tela de Gestão
                        // Se gerar algum erro no processo salvar o erro no campo de erro ou em  alguma tela de LOG
                        val buscarInfos = retornaVO("AD_TGESPROJ", "IDATIVIDADE = ${idAtividade.toBigDecimal()}")
                        val nufap = buscarInfos?.asBigDecimal("NUFAP")
                        val etapa = buscarInfos?.asBigDecimal("NUMETAPA")
                        val projeto = buscarInfos?.asBigDecimal("CODPROJ")
                        val contrato = buscarInfos?.asBigDecimal("NUMCONTRATO")
                        val parceiro = buscarInfos?.asBigDecimal("CODPARC")
                        val empresa = buscarInfos?.asBigDecimal("CODEMP")
                        val codvol = buscarInfos?.asString("CODVOL")
                        val codProd = buscarInfos?.asBigDecimal("CODPROD")
                        val top = contextoAcao.getParametroSistema("TOPBOQ") as BigDecimal
                        val tipoVenda = contextoAcao.getParametroSistema("TIPNEGBOQ") as BigDecimal
                        val qtdNeg = json.qtdBOQ.trim().toBigDecimal()
                        val vlrUnit = json.vltUnitItem.trim().toBigDecimal()

                        val jsonString = """{
                                               "serviceName":"CACSP.incluirNota",
                                               "requestBody":{
                                                  "nota":{
                                                     "cabecalho":{
                                                        "NUNOTA":{
                                                        },
                                                        "CODPARC":{
                                                           "${'$'}":"$parceiro"
                                                        },
                                                        "DTNEG":{
                                                           "${'$'}":"$dataInc"
                                                        },
                                                        "CODTIPOPER":{
                                                           "${'$'}":"$top"
                                                        },
                                                        "CODTIPVENDA":{
                                                           "${'$'}":"$tipoVenda"
                                                        },
                                                        "CODVEND":{
                                                           "${'$'}":"0"
                                                        },
                                                        "CODEMP":{
                                                           "${'$'}":"$empresa"
                                                        },
                                                        "TIPMOV":{
                                                           "${'$'}":"P"
                                                        },
                                                        "NUMCONTRATO":{
                                                           "${'$'}":"$contrato"
                                                        },
                                                        "CODPROJ":{
                                                           "${'$'}":"$projeto"
                                                        },
                                                        "AD_LOTEBOQ":{
                                                           "${'$'}":"$loteBOQ"
                                                        },
                                                        "AD_NUFAP":{
                                                           "${'$'}":"$nufap"
                                                        }
                                                     },
                                                     "itens":{
                                                        "INFORMARPRECO":"True",
                                                        "item":[
                                                              {
                                                               "NUNOTA":{
                                                              },
                                                              "CODPROD":{
                                                                 "${'$'}":"$codProd"
                                                              },
                                                              "QTDNEG":{
                                                                 "${'$'}":"$qtdNeg"
                                                              },
                                                              "CODLOCALORIG":{
                                                                 "${'$'}":"0"
                                                              },
                                                              "CODVOL":{
                                                                 "${'$'}":"'$codvol'"
                                                              },
                                                              "PERCDESC": {
                                                                "${'$'}": "0"
                                                              },
                                                             "VLRUNIT": {
                                                                "${'$'}": "$vlrUnit"
                                                              },
                                                             "AD_NUMETAPA": {
                                                                "${'$'}": "$etapa"
                                                              }
                                                           }               
                                                        ]
                                                     }
                                                  }
                                               }
                                            }""".trimIndent()

                        val (postbody) = post("mgecom/service.sbr?serviceName=CACSP.incluirNota&outputType=json", jsonString)
                        val status = getPropFromJSON("status", postbody)

                        if (status == "1") {
                            val nunotaRetorno = getPropFromJSON("responseBody.pk.NUNOTA.${'$'}", postbody).toBigDecimal()
                            var hnd2: JapeSession.SessionHandle? = null
                            try {
                                hnd2 = JapeSession.open()
                                JapeFactory.dao("AD_TGESPROJ").prepareToUpdateByPK(idAtividade.toBigDecimal())
                                    .set("NUNOTABOQ", nunotaRetorno)
                                    .set("LOTEBOQ", loteBOQ)
                                    .set("TIPOBOQ", tipoBOQ)
                                    .set("DATAINC", dataInc)
                                    .set("GRUPOUSU", grupoUsuarios)
                                    .set("ORGCHAVE", orgChave)
                                    .set("OCBOQ", oc)
                                    .set("SITEID", siteID)
                                    .set("ENDERECOID", enderecoID)
                                    .set("MUNBOQ", municipio)
                                    .set("UFBOQ", ufBOQ)
                                    .set("STATUSFIN", statusFin) //Status da BOQ
                                    .update()

                            } catch (e: Exception) {
                                MGEModelException.throwMe(e)
                            } finally {
                                JapeSession.close(hnd2)
                            }

                        } else {
                            val statusMessage = getPropFromJSON("statusMessage", postbody)
                            inserirErroLOG(statusMessage)

                        }

                        line = br.readLine()

                    }

                }



            }
        } catch (e: Exception) {
            throw MGEModelException("$e $ultimaLinhaJson ")
        } finally {
            JapeSession.close(hnd)
        }
    }

    private fun getReplaceFileInfo(line: String): String {
        val regex = "__start_fileinformation__.*__end_fileinformation__"
        val subst = ""

        val pattern: Pattern = Pattern.compile(regex, Pattern.MULTILINE)
        val matcher: Matcher = pattern.matcher(line)

        return matcher.replaceAll(subst)
    }

    private fun trataLinha(linha: String): LinhaJson {
        var cells = if (linha.contains(";")) linha.split(";(?=([^\"]*\"[^\"]*\")*[^\"]*\$)".toRegex()).toTypedArray()
        else linha.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*\$)".toRegex()).toTypedArray()

        cells = cells.filter { predicate ->
            if (predicate.isEmpty())
                return@filter false
            return@filter true
        }.toTypedArray() // Remove linhas vazias

        val ret = if (cells.isNotEmpty()) LinhaJson(
            cells[0],
            cells[1],
            cells[2],
            cells[3],
            cells[4],
            cells[5],
            cells[6],
            cells[7],
            cells[8],
            cells[9],
            cells[10],
            cells[11],
            cells[12],
            cells[13],
            cells[14],
            cells[15],
            cells[16],
            cells[17],
            cells[18]
        ) else
            null

        if (ret == null) {
            //throw Exception("Erro ao processar a linha: $linha")
            // Salvar o erro de processamento da linha na tela detalhe de log.
            val erro = "Erro ao processar a linha: $linha"

            inserirErroLOG(erro)
        }

        return ret!!
    }

    private fun inserirErroLOG(erro: String) {
        val logErroLinha: FluidCreateVO = getFluidCreateVO("AD_LOGIMPGP")
        logErroLinha.set("CODIMPORTACAO", codImportador)
        logErroLinha.set("ERRO", erro)
        logErroLinha.save()
    }

    @Throws(MGEModelException::class)
    fun retornaVO(instancia: String?, where: String?): DynamicVO? {
        var dynamicVo: DynamicVO? = null
        var hnd: JapeSession.SessionHandle? = null
        try {
            hnd = JapeSession.open()
            val instanciaDAO = JapeFactory.dao(instancia)
            dynamicVo = instanciaDAO.findOne(where)
        } catch (e: java.lang.Exception) {
            MGEModelException.throwMe(e)
        } finally {
            JapeSession.close(hnd)
        }
        return dynamicVo
    }

    /**
     * Converte um valor (String) com separador de milhares para [BigDecimal]
     * @author Aliny Sousa
     * @param str  Texto a ser convertido
     * @return [BigDecimal]
     */
    fun converterValorMonetario(valorMonetario: String): BigDecimal {
        val valorNumerico = valorMonetario.replace("\"", "").replace(".", "").replace(",", ".")
        return BigDecimal(valorNumerico)
    }

    /**
     * Converte uma data dd/mm/yyyy ou dd-mm-yyyy em timestampb
     * @author Luis Ricardo Alves Santos
     * @param strDate  Texto a ser convertido
     * @return [Timestamp]
     */
    fun stringToTimeStamp(strDate: String): Timestamp? {
        try {
            val formatter: DateFormat = SimpleDateFormat("MM/dd/yyyy")
            val date: Date = formatter.parse(strDate)
            return Timestamp(date.time)
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Retorna a data atual
     */
    fun getDhAtual(): Timestamp {
        return Timestamp(System.currentTimeMillis())
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

    data class LinhaJson(
        val idatividade: String,
        val loteBOQ: String,
        val tipoBOQ: String,
        val itemBOQ: String,
        val dataInclusaoBOQ: String,
        val descricaoItem: String,
        val qtdBOQ: String,
        val siteID: String,
        val enderecoID: String,
        val municipioBOQ: String,
        val ufBOQ: String,
        val regionalBOQ: String,
        val grupoUsuarios: String,
        val orgChave: String,
        val ocBOQ: String,
        val vlrItemLPU: String,
        val vltUnitItem: String,
        val vlrItem: String,
        val statusBOQ: String
    )

}