package botoes;

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava;
import br.com.sankhya.extensions.actionbutton.ContextoAcao;
import br.com.sankhya.extensions.actionbutton.Registro;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.modelcore.MGEModelException;
import br.com.sankhya.modelcore.comercial.impostos.ImpostosHelpper;
import com.sankhya.ce.jape.JapeHelper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Realiza a geração do orçamento
 */
public class GerarOrcamento implements AcaoRotinaJava {
    @Override
    public void doAction(ContextoAcao contextoAcao) throws Exception {

        List<BigDecimal> notas = new ArrayList<>();
        List<BigDecimal> loteBOQcab = new ArrayList<>();
        Registro[] linhas = contextoAcao.getLinhas();
        BigDecimal nunota = null;

        for (Registro linha : linhas) {
            Object codImportacao = linha.getCampo("CODIMPORTACAO");

            Collection<DynamicVO> boqs = JapeHelper.getVOs("AD_IMPORTNOTASITE", "CODIMPORTACAO = " + codImportacao);
            for (DynamicVO boq : boqs) {
                BigDecimal codimpIte = boq.asBigDecimalOrZero("CODIMPITE");
                BigDecimal loteBOQ = boq.asBigDecimalOrZero("LOTEBOQ");
                BigDecimal codprod = boq.asBigDecimalOrZero("CODPROD");
                BigDecimal qtd = boq.asBigDecimalOrZero("QTDNEG");
                BigDecimal vlrUnit = boq.asBigDecimalOrZero("VLRUNIT");
//                BigDecimal percentual = boq.asBigDecimalOrZero("PERCDESC").divide(new BigDecimal(100));
                BigDecimal vlrTotalItem = qtd.multiply(vlrUnit);
//                BigDecimal desconto = vlrTotalItem.multiply(percentual);
//                BigDecimal vlrTotal = vlrTotalItem.subtract(desconto);
                BigDecimal vlrTotal = vlrTotalItem.multiply(qtd);

                if (!loteBOQcab.contains(loteBOQ)) {

                    JapeHelper.CreateNewLine newCab = new JapeHelper.CreateNewLine("CabecalhoNota");
                    newCab.set("NUMNOTA", BigDecimal.ZERO);
                    newCab.set("CODEMP", BigDecimal.ONE);
                    newCab.set("CODPARC", boq.asBigDecimal("CODPARC"));
                    newCab.set("CODTIPOPER", contextoAcao.getParametroSistema("TOPBOQ"));
                    newCab.set("CODTIPVENDA", contextoAcao.getParametroSistema("TIPNEGBOQ"));
                    newCab.set("TIPMOV", "P");
                    newCab.set("DTNEG", boq.asTimestamp("DTINCBOQ"));
                    newCab.set("VLRDESCTOT", BigDecimal.ZERO);
                    newCab.set("CODCENCUS", contextoAcao.getParametroSistema("CODCENCUSBOQ"));
                    newCab.set("CODNAT", contextoAcao.getParametroSistema("CODNATBOQ"));
                    DynamicVO cabVO = newCab.save();
                    nunota = cabVO.asBigDecimal("NUNOTA");
                    notas.add(nunota);

                    loteBOQcab.add(loteBOQ);
                }


                JapeHelper.CreateNewLine newIte = new JapeHelper.CreateNewLine("ItemNota");
                DynamicVO produto = JapeHelper.getVO("Produto", "CODPROD=" + boq.asBigDecimalOrZero("CODPROD"));
                newIte.set("NUNOTA", nunota);
                newIte.set("CODPROD", boq.asBigDecimalOrZero("CODPROD"));
                newIte.set("QTDNEG", boq.asBigDecimalOrZero("QTDNEG"));
                newIte.set("VLRUNIT", boq.asBigDecimalOrZero("VLRUNIT"));
                newIte.set("VLRTOT", vlrTotal);
                newIte.set("CODVOL", "UN");
                newIte.set("CODLOCALORIG", BigDecimal.ZERO);
                newIte.set("USOPROD", produto.asString("USOPROD"));
                newIte.set("ATUALESTOQUE", BigDecimal.ZERO);
                DynamicVO iteVO = newIte.save();
                BigDecimal sequencia = iteVO.asBigDecimalOrZero("SEQUENCIA");

                inserirNunotaImp("AD_IMPORTNOTASITE", codImportacao, codimpIte, nunota, sequencia);


                recalcularImpostos(nunota);
            }
        }

        contextoAcao.setMensagemRetorno("BOQs criadas com sucesso! " + notas.stream().map(BigDecimal::toString).collect(Collectors.joining(",")));
    }

    private static void recalcularImpostos(BigDecimal nunota) throws Exception {
        ImpostosHelpper impostosHelper = new ImpostosHelpper();
        impostosHelper.setForcarRecalculo(true);
        impostosHelper.carregarNota(nunota);
        impostosHelper.calcularImpostos(nunota);
        impostosHelper.totalizarNota(nunota);
        impostosHelper.salvarNota();
    }

    private static void inserirNunotaImp(String intancia, Object codImportacao, BigDecimal codigo, BigDecimal nunota, BigDecimal sequencia) throws MGEModelException {
        JapeSession.SessionHandle hnd = null;

        try {
            hnd = JapeSession.open();
            JapeFactory.dao(intancia).
                    prepareToUpdateByPK(codImportacao, codigo)
                    .set("NUNOTA", nunota)
                    .set("SEQUENCIA", sequencia)
                    .update();
        } catch (Exception e) {
            MGEModelException.throwMe(e);
        } finally {
            JapeSession.close(hnd);
        }
    }

}
