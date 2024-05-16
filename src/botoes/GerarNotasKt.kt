package botoes

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava
import br.com.sankhya.extensions.actionbutton.ContextoAcao
import br.com.sankhya.jape.vo.DynamicVO
import br.com.sankhya.modelcore.MGEModelException
import com.sankhya.ce.jape.JapeHelper
import com.sankhya.ce.jape.JapeHelper.CreateNewLine
import java.math.BigDecimal


class GerarNotasKt : AcaoRotinaJava {
    override fun doAction(contextoAcao: ContextoAcao?) {
        val notas: List<BigDecimal> = ArrayList()
        val linhas = contextoAcao!!.linhas

        for (linha in linhas) {
            val codImportacao = linha.getCampo("CODIMPORTACAO")

            val vo: DynamicVO = JapeHelper.getVO("AD_IMPORTNOTASCAB", "CODIMPORTACAO = '$codImportacao'")
                ?: throw MGEModelException("Cabeçalhos da Importação não encontrados.")

            val newCab: JapeHelper.CreateNewLine = JapeHelper.CreateNewLine("CabecalhoNota")
            newCab.set("NUMNOTA", vo.asBigDecimal("NUMNOTA"))
            newCab.set("SERIENOTA", vo.asString("SERIENOTA"))
            newCab.set("CODEMP", vo.asBigDecimal("CODEMP"))
            newCab.set("CODPARC", vo.asBigDecimal("CODPARC"))
            newCab.set("CODTIPOPER", vo.asBigDecimal("CODTIPOPER"))
            newCab.set("CODTIPVENDA", vo.asBigDecimal("CODTIPVENDA"))
            newCab.set("TIPMOV", vo.asString("TIPMOV"))
            newCab.set("DHTIPOPER", vo.asTimestamp("DHTIPOPER"))
            newCab.set("DTALTER", vo.asTimestamp("DTALTER"))
            newCab.set("DTNEG", vo.asTimestamp("DTNEG"))
            newCab.set("VLRDESCTOT", vo.asBigDecimal("VLRDESCTOT"))
            newCab.set("VLRNOTA", vo.asBigDecimal("VLRNOTA"))
            newCab.set("CODCENCUS", vo.asBigDecimal("CODCENCUS"))
            newCab.set("CODNAT", vo.asBigDecimal("CODNAT"))

            val cabVO = newCab.save()
            val nunota = cabVO.asBigDecimal("NUNOTA")
//            notas.add(nunota)

            val itens = JapeHelper.getVOs("AD_IMPORTNOTASITE", "CODIMPORTACAO = $codImportacao")
            for (item in itens) {
                val newIte = CreateNewLine("ItemNota")
                val codprod = item.asBigDecimal("CODPROD")
                val produto = JapeHelper.getVO("Produto", "CODPROD=$codprod")
                    ?: throw MGEModelException("Produto não encontrado:$codprod")
                newIte["NUNOTA"] = nunota
                newIte["CODPROD"] = codprod
                newIte["QTDNEG"] = item.asBigDecimal("QTDNEG")
                newIte["CODVOL"] = produto.asString("CODVOL")
                newIte.save()
            }

        }

    }
}