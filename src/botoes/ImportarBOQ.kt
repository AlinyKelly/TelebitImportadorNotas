package botoes

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava
import br.com.sankhya.extensions.actionbutton.ContextoAcao
import br.com.sankhya.jape.core.JapeSession
import br.com.sankhya.jape.vo.DynamicVO
import br.com.sankhya.jape.wrapper.JapeFactory
import br.com.sankhya.modelcore.MGEModelException
import br.com.sankhya.ws.ServiceContext
import org.apache.commons.io.FileUtils
import utilitarios.convertIso88591ToUtf8
import utilitarios.converterUTF8ISO88591
import utilitarios.mensagemErro
import utilitarios.removerCaracteresEspeciais
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
    @Throws(MGEModelException::class, IOException::class)
    override fun doAction(contextoAcao: ContextoAcao) {
        var hnd: JapeSession.SessionHandle? = null

        var ultimaLinhaJson: LinhaJson? = null

        val linhasSelecionadas = contextoAcao.linhas

        try {
            for (linha in linhasSelecionadas) {
                var count = 0

                val codimportacao = linha.getCampo("CODIMPORTACAO") as BigDecimal?

                val data = linha.getCampo("ARQUIVO") as ByteArray?
                val ctx = ServiceContext.getCurrent()
                val file = File(ctx.tempFolder, "IMPCABECALHO" + System.currentTimeMillis())
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

                        val tipoBOQ = removerCaracteresEspeciais(converterUTF8ISO88591(json.tipoBOQ.trim()))
                        var tipo = ""
                        tipo = if (tipoBOQ == "Servico") {
                            "S"
                        } else if (tipoBOQ == "Adiciona") {
                            "A"
                        } else if (tipoBOQ == "Material") {
                            "M"
                        } else {
                            "N"
                        }

                        val statusBOQ = removerCaracteresEspeciais(converterUTF8ISO88591(json.statusBOQ.trim()))
                        var status = ""
                        status = if ("Criado" == statusBOQ) {
                            "C"
                        } else if ("Pedido de PO" == statusBOQ) {
                            "P"
                        } else if ("PO Emitido" == statusBOQ) {
                            "O"
                        } else if ("Reprovado" == statusBOQ) {
                            "R"
                        } else if ("Revisado" == statusBOQ) {
                            "E"
                        } else if ("Revisao" == statusBOQ) {
                            "V"
                        } else if ("RP" == statusBOQ) {
                            "S"
                        } else {
                            "N"
                        }

                        val novaLinhaIte = contextoAcao.novaLinha("AD_IMPORTNOTASITE")
                        novaLinhaIte.setCampo("CODIMPORTACAO", codimportacao)
                        novaLinhaIte.setCampo("IDANDAMENTO", json.idandamento.trim())
                        novaLinhaIte.setCampo("IDATIVIDADE", json.idatividade.trim())
                        novaLinhaIte.setCampo("CODPARC", json.codparc.trim())
                        novaLinhaIte.setCampo("CODPROD", json.itemBOQ.trim())
                        novaLinhaIte.setCampo("DTINCBOQ", formatarDataString(json.dataInclusaoBOQ.trim()))
                        novaLinhaIte.setCampo("QTDNEG", converterValorMonetario(json.qtdBOQ.trim()))
                        novaLinhaIte.setCampo("LOTEBOQ", json.loteBOQ.trim())
                        novaLinhaIte.setCampo("TIPOBOQ", tipo)
                        novaLinhaIte.setCampo("SITEID", json.siteID.trim())
                        novaLinhaIte.setCampo("ENDERECOID", json.enderecoID.trim())
                        novaLinhaIte.setCampo("MUNBOQ", json.municipioBOQ.trim())
                        novaLinhaIte.setCampo("UFBOQ", json.ufBOQ.trim())
                        novaLinhaIte.setCampo("REGIONAL", json.regionalBOQ.trim())
                        novaLinhaIte.setCampo("GRUPOUSU", json.grupoUsuarios.trim())
                        novaLinhaIte.setCampo("ORGCHAVE", json.orgChave.trim())
                        novaLinhaIte.setCampo("OCBOQ", json.ocBOQ.trim())
                        novaLinhaIte.setCampo("VLRITELPU", converterValorMonetario(json.vlrItemLPU.trim()))
                        novaLinhaIte.setCampo("VLRUNIT", converterValorMonetario(json.vltUnitItem.trim()))
                        novaLinhaIte.setCampo("VLRTOT", converterValorMonetario(json.vlrItem.trim()))
                        novaLinhaIte.setCampo("STATUSBOQ", status)
                        novaLinhaIte.save()

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
            cells[18],
            cells[19],
            cells[20]
        ) else
            null

        if (ret == null) {
            throw Exception("Erro ao processar a linha: $linha")
        }
        return ret

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
        val idandamento: String,
        val idatividade: String,
        val codparc: String,
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