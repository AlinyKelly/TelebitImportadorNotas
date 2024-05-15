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

class ImportarCabecalho : AcaoRotinaJava{
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

                        val novaLinhaCab = contextoAcao.novaLinha("AD_IMPORTNOTASCAB")
                        novaLinhaCab.setCampo("CODIMPORTACAO", codimportacao)
                        novaLinhaCab.setCampo("AD_IDEXTERNO", json.idExterno.trim())
                        novaLinhaCab.setCampo("NUMNOTA", json.numnota.trim())
                        novaLinhaCab.setCampo("SERIENOTA", json.serienota.trim())
                        novaLinhaCab.setCampo("CODEMP", json.codemp.trim())
                        novaLinhaCab.setCampo("CODPARC", json.codparc.trim())
                        novaLinhaCab.setCampo("CODTIPOPER", json.codtipoper.trim())
                        novaLinhaCab.setCampo("CODTIPVENDA", json.codtipvenda.trim())
                        novaLinhaCab.setCampo("TIPMOV", json.tipmov.trim())
                        novaLinhaCab.setCampo("DHTIPOPER", json.dhtipoper)
                        novaLinhaCab.setCampo("DTALTER", json.dtalter)
                        novaLinhaCab.setCampo("DTNEG", json.dtneg)
                        novaLinhaCab.setCampo("VLRDESCTOT", converterValorMonetario(json.vlrdesctot))
                        novaLinhaCab.setCampo("VLRNOTA", converterValorMonetario(json.vlrnota))
                        novaLinhaCab.setCampo("CODCENCUS", json.codcencus.trim())
                        novaLinhaCab.setCampo("CODNAT", json.codnat.trim())
                        novaLinhaCab.save()

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
        val ret = if (cells.isNotEmpty()) LinhaJson(cells[0], cells[1], cells[2], cells[3], cells[4], cells[5], cells[6], cells[7], cells[8], cells[9], cells[10], cells[11], cells[12], cells[13], cells[14]) else
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
        val numnota: String,
        val serienota: String,
        val codemp: String,
        val codparc: String,
        val codtipoper: String,
        val codtipvenda: String,
        val tipmov: String,
        val dhtipoper: String,
        val dtalter: String,
        val dtneg: String,
        val vlrdesctot: String,
        val vlrnota: String,
        val codcencus: String,
        val codnat: String
    )

}