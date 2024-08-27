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
import utilitarios.confirmarNotaAPI
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

class ImportarFS : AcaoRotinaJava {

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
                val file = File(ctx.tempFolder, "IMPFS" + System.currentTimeMillis())
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
                        val folhaSevico = json.fs.trim()
                        val qtdFS = converterValorMonetario(json.qtdFs.trim())
                        val emissaoFS = json.emissaoFS.trim()
                        val statusFS = json.statusFS.trim()
//                        val codigoServico = json.codigoservico.trim()
//                        val aliqISS = converterValorMonetario(json.aliqISS.trim())
//                        val aliqPIS = converterValorMonetario(json.aliqPIS.trim())
//                        val aliqCOFINS = converterValorMonetario(json.aliqCOFINS.trim())
//                        val aliqCSSL = converterValorMonetario(json.aliqCSSL.trim())
//                        val aliqIRRF = converterValorMonetario(json.aliqIRRF.trim())
//                        val aliqINSS = converterValorMonetario(json.aliqINSS.trim())

                        val buscarInfos = retornaVO("AD_TGESPROJ", "IDATIVIDADE = ${idAtividade.toBigDecimal()}")
                        val nunotaPO = buscarInfos?.asBigDecimal("NUNOTAPO")
                        val tipoOperacao = contextoAcao.getParametroSistema("TOPFS")
                        val serieTipoOperacao = contextoAcao.getParametroSistema("SERIETOPFS")
                        val nunotaFS = buscarInfos?.asBigDecimalOrZero("NUNOTAFS")
                        val nroFS = buscarInfos?.asString("NROFS")

                        if (nroFS == null) {
                            var hnd2: JapeSession.SessionHandle? = null
                            try {
                                hnd2 = JapeSession.open()
                                JapeFactory.dao("AD_TGESPROJ").prepareToUpdateByPK(idAtividade.toBigDecimal())
                                    .set("NROFS", folhaSevico)
                                    .set("QTDFS", qtdFS)
                                    .set("DTEMISSAOFS", stringToTimeStamp(emissaoFS))
                                    .set("STATUSFS", statusFS)
                                    .update()
                            } catch (e: Exception) {
                                MGEModelException.throwMe(e)
                            } finally {
                                JapeSession.close(hnd2)
                            }
                        }

                        if (nunotaFS == null) {
                            val jsonString = """{
                              "serviceName": "SelecaoDocumentoSP.faturar",
                              "requestBody": {
                                "notas": {
                                  "codTipOper": $tipoOperacao,
                                  "dtFaturamento": "$emissaoFS",
                                  "serie": "$serieTipoOperacao",
                                  "dtSaida": "",
                                  "hrSaida": "",
                                  "tipoFaturamento": "FaturamentoNormal",
                                  "dataValidada": true,
                                  "notasComMoeda": {},
                                  "nota": [
                                    { "NUNOTA": "$nunotaPO", "itens": { "item": [{ "QTDFAT": $qtdFS, "$":1 }] } }
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

                            val (postbody) = post(
                                "mgecom/service.sbr?serviceName=SelecaoDocumentoSP.faturar&outputType=json",
                                jsonString
                            )
                            val status = getPropFromJSON("status", postbody)

                            if (status == "1") {
                                val nunotaRetorno = getPropFromJSON("responseBody.notas.nota.${'$'}", postbody)

                                var hnd4: JapeSession.SessionHandle? = null
                                try {
                                    hnd4 = JapeSession.open()
                                    JapeFactory.dao("AD_TGESPROJ").prepareToUpdateByPK(idAtividade.toBigDecimal())
                                        .set("NUNOTAFS", nunotaRetorno.toBigDecimal())
                                        .update()
                                } catch (e: Exception) {
                                    MGEModelException.throwMe(e)
                                } finally {
                                    JapeSession.close(hnd4)
                                }

                            } else {
                                val statusMessage = getPropFromJSON("statusMessage", postbody)
                                inserirErroLOG("ID Atividade nro $idAtividade - Erro: $statusMessage", "API - Erro ao criar Folha de Serviço.")
                            }
                        }

                        line = br.readLine()
                    }

                }

            }
        } catch (e: Exception) {
            //throw MGEModelException("$e $ultimaLinhaJson")
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
            cells[7],
            cells[8],
            cells[9],
            cells[10],
            cells[11]
        ) else
            null

        if (ret == null) {
//            throw Exception("Erro ao processar a linha: $linha")
            val erro = "Erro ao processar a linha: $linha"
            inserirErroLOG(erro, "Importação - Erro na linha importada.")
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
        val fs: String,
        val qtdFs: String,
        val emissaoFS: String,
        val statusFS: String,
        val codigoservico: String,
        val aliqISS: String,
        val aliqPIS: String,
        val aliqCOFINS: String,
        val aliqCSSL: String,
        val aliqIRRF: String,
        val aliqINSS: String
    )
}