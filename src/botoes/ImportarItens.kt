package botoes

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava
import br.com.sankhya.extensions.actionbutton.ContextoAcao
import br.com.sankhya.jape.core.JapeSession
import br.com.sankhya.jape.vo.DynamicVO
import br.com.sankhya.jape.wrapper.JapeFactory
import br.com.sankhya.modelcore.MGEModelException
import br.com.sankhya.ws.ServiceContext
import org.apache.commons.io.FileUtils
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

class ImportarItens : AcaoRotinaJava{
    @Throws(MGEModelException::class, IOException::class)
    override fun doAction(contextoAcao: ContextoAcao) {
        var hnd: JapeSession.SessionHandle? = null

        var ultimaLinhaJson: LinhaJson? = null

        val linhasSelecionadas = contextoAcao.linhas

        try {
            for (linha in linhasSelecionadas) {
                var count = 0

                val codimportacao = linha.getCampo("CODIMPORTACAO") as BigDecimal?

                val data = linha.getCampo("ARQUIVOITE") as ByteArray?
                val ctx = ServiceContext.getCurrent()
                val file = File(ctx.tempFolder, "ARQUIVOITE" + System.currentTimeMillis())
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

                        val idexterno = json.idExterno.trim()

                        val buscarImpCab = retornaVO("AD_IMPORTNOTASCAB", "CODIMPORTACAO = $codimportacao AND AD_IDEXTERNO = $idexterno")
                        val nunotaImp = buscarImpCab?.asBigDecimalOrZero("NUNOTA")

                        val novaLinhaIte = contextoAcao.novaLinha("AD_IMPORTNOTASITE")
                        novaLinhaIte.setCampo("CODIMPORTACAO", codimportacao)
                        novaLinhaIte.setCampo("AD_IDEXTERNO", idexterno)
                        novaLinhaIte.setCampo("CODPROD", json.codprod.trim())
                        novaLinhaIte.setCampo("CODVOL", json.codvol.trim())
                        novaLinhaIte.setCampo("PERCDESC", converterValorMonetario(json.percdesc.trim()))
                        novaLinhaIte.setCampo("QTDNEG", converterValorMonetario(json.qtdneg.trim()))
                        novaLinhaIte.setCampo("VLRUNIT", converterValorMonetario(json.vlrunit.trim()))
                        novaLinhaIte.setCampo("DTALTER", json.dtalter.trim())
                        novaLinhaIte.setCampo("CODLOCALORIG", json.codlocalorig.trim())
                        novaLinhaIte.setCampo("NUNOTA", nunotaImp)
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
        val ret = if (cells.isNotEmpty()) LinhaJson(cells[0], cells[1], cells[2], cells[3], cells[4], cells[5], cells[6], cells[7], cells[8]) else
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

    data class LinhaJson(
        val idExterno: String,
        val codprod: String,
        val codvol: String,
        val percdesc: String,
        val qtdneg: String,
        val vlrunit: String,
        val vlrtot: String,
        val dtalter: String,
        val codlocalorig: String
    )
}