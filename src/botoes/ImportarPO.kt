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
import utilitarios.*
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

class ImportarPO : AcaoRotinaJava {

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
                val file = File(ctx.tempFolder, "IMPPO" + System.currentTimeMillis())
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
                        val pedidoPO = json.pedido.trim()
                        val itemPO = json.itemPo.trim()
                        val emissaoPO = json.emissaoPO.trim()
                        val aprovacaoPO = json.aprovacaoPO.trim()
                        val prazo = json.prazo.trim()
                        val valorPedido = converterValorMonetario(json.valorPedido.trim())
                        val municipio = json.municipio.trim()

                        val buscarInfos = retornaVO("AD_TGESPROJ", "IDATIVIDADE = ${idAtividade.toBigDecimal()}")
                        val nunotaBOQ = buscarInfos?.asBigDecimal("NUNOTABOQ")
                        val nunotaPO = buscarInfos?.asBigDecimal("NUNOTAPO")
                        val pedido = buscarInfos?.asString("PEDIDO")

                        val tipoOperacao = contextoAcao.getParametroSistema("TOPPO")

                        // Inserir as informações da PO depois realizar o faturamento da BOQ

                        if (pedido == null) {
                            var hnd2: JapeSession.SessionHandle? = null
                            try {
                                hnd2 = JapeSession.open()
                                JapeFactory.dao("AD_TGESPROJ").prepareToUpdateByPK(idAtividade.toBigDecimal())
                                    .set("PEDIDO", pedidoPO)
                                    .set("ITEMPO", itemPO.toBigDecimal())
                                    .set("DTEMISSAOPO", stringToTimeStamp(emissaoPO))
                                    .set("DTAPROVPO", stringToTimeStamp(aprovacaoPO))
                                    .set("DTPRAZO", stringToTimeStamp(prazo))
                                    .set("VLRPEDIDO", valorPedido)
                                    .set("MUNICIPIOPO", municipio)
                                    .update()
                            } catch (e: Exception) {
                                MGEModelException.throwMe(e)
                            } finally {
                                JapeSession.close(hnd2)
                            }
                        }

                        if (nunotaPO == null) {
                            val jsonString = """{
                                         "serviceName":"SelecaoDocumentoSP.faturar",
                                         "requestBody":{
                                            "notas":{
                                               "codTipOper":$tipoOperacao,
                                               "dtFaturamento":"$aprovacaoPO",
                                               "tipoFaturamento":"FaturamentoNormal",
                                               "dataValidada":false,
                                               "notasComMoeda":{
                                                  
                                               },
                                               "nota":[
                                                  {
                                                     "${'$'}": $nunotaBOQ
                                                  }
                                               ],
                                               "faturarTodosItens":true
                                            }
                                         }
                                      }""".trimIndent()

                            val (postbody) = post(
                                "mgecom/service.sbr?serviceName=SelecaoDocumentoSP.faturar&outputType=json",
                                jsonString
                            )
                            val status = getPropFromJSON("status", postbody)

                            if (status == "1") {
                                val nunotaRetorno = getPropFromJSON("responseBody.notas.nota.${'$'}", postbody)

                                //Antes de confirmar a nota deve-se trocar a data de negociação para a mesma data do faturamento
                                //Enviar nunota da po para a gestão de projetos
                                var hnd3: JapeSession.SessionHandle? = null
                                try {
                                    hnd3 = JapeSession.open()
                                    JapeFactory.dao("AD_TGESPROJ").prepareToUpdateByPK(idAtividade.toBigDecimal())
                                        .set("NUNOTAPO", nunotaRetorno.toBigDecimal())
                                        .update()
                                } catch (e: Exception) {
                                    MGEModelException.throwMe(e)
                                } finally {
                                    JapeSession.close(hnd3)
                                }

                                //Confirmar pedido
                                val postbodyConfirmar = confirmarNotaAPI(nunotaRetorno.toBigDecimal())
                                val statusConfirmar = getPropFromJSON("status", postbodyConfirmar)
                                if (statusConfirmar == "0") {
                                    val statusMessage = getPropFromJSON("statusMessage", postbodyConfirmar)
                                    inserirErroLOG("ID Atividade nro $idAtividade - Erro: $statusMessage", "API Confirmar Nota - Status não confirmado.")
                                }

                            } else {
                                val statusMessage = getPropFromJSON("statusMessage", postbody)
                                inserirErroLOG("ID Atividade nro $idAtividade - Erro: $statusMessage", "API Criar Pedido - Erro ao criar Pedido de Venda.")
                            }
                        }

                        line = br.readLine()
                    }
                }

            }
        } catch (e: Exception) {
            //throw MGEModelException("$e $ultimaLinhaJson ")
            inserirErroLOG("$e $ultimaLinhaJson", "Importação - Erro na linha importada.")
        } finally {
            JapeSession.close(hnd)
        }

        contextoAcao.setMensagemRetorno("Lançamento(s) inserido(s) com sucesso! Verifique a tela de Gestão de Projetos.")
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
            cells[7]
        ) else
            null

        if (ret == null) {
            val erro = "Erro ao processar a linha: $linha"
            inserirErroLOG(erro, "Importação - Erro na linha importada.")
            //throw Exception("Erro ao processar a linha: $linha")
        }
        return ret!!

    }

    private fun inserirErroLOG(erro: String, origemErro: String) {
        val logErroLinha: FluidCreateVO = getFluidCreateVO("AD_LOGIMPGP")
        logErroLinha.set("CODIMPORTACAO", codImportador)
        logErroLinha.set("ERRO", erro)
        logErroLinha.set("DHERRO", getDhAtual())
        logErroLinha.set("ORIGEMERRO", origemErro)
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
            val formatter: DateFormat = SimpleDateFormat("dd/MM/yyyy")
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
        val pedido: String,
        val itemPo: String,
        val emissaoPO: String,
        val aprovacaoPO: String,
        val prazo: String,
        val valorPedido: String,
        val municipio: String
    )
}