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

public class GerarNotas implements AcaoRotinaJava {
    @Override
    public void doAction(ContextoAcao contextoAcao) throws Exception {

        List<BigDecimal> notas = new ArrayList<>();
        Registro[] linhas = contextoAcao.getLinhas();

            for (Registro linha : linhas) {
                Object codImportacao = linha.getCampo("CODIMPORTACAO");

                Collection<DynamicVO> cabs = JapeHelper.getVOs("AD_IMPORTNOTASCAB", "CODIMPORTACAO = " + codImportacao);
                for (DynamicVO cab : cabs) {
                    BigDecimal codimpCab = cab.asBigDecimal("CODIMPCAB");

                    JapeHelper.CreateNewLine newCab = new JapeHelper.CreateNewLine("CabecalhoNota");
                    newCab.set("NUMNOTA", cab.asBigDecimal("NUMNOTA"));
                    newCab.set("SERIENOTA", cab.asString("SERIENOTA"));
                    newCab.set("CODEMP", cab.asBigDecimal("CODEMP"));
                    newCab.set("CODPARC", cab.asBigDecimal("CODPARC"));
                    newCab.set("CODTIPOPER", cab.asBigDecimal("CODTIPOPER"));
                    newCab.set("CODTIPVENDA", cab.asBigDecimal("CODTIPVENDA"));
                    newCab.set("TIPMOV", cab.asString("TIPMOV"));
                    newCab.set("DTNEG", cab.asTimestamp("DTNEG"));
                    newCab.set("VLRDESCTOT", cab.asBigDecimal("VLRDESCTOT"));
                    newCab.set("VLRNOTA", cab.asBigDecimal("VLRNOTA"));
                    newCab.set("CODCENCUS", cab.asBigDecimal("CODCENCUS"));
                    newCab.set("CODNAT", cab.asBigDecimal("CODNAT"));

                    DynamicVO cabVO = newCab.save();
                    BigDecimal nunota = cabVO.asBigDecimal("NUNOTA");
                    notas.add(nunota);

                    inserirNunotaImp("AD_IMPORTNOTASCAB", codImportacao, codimpCab, nunota);

                    Collection<DynamicVO> itens = JapeHelper.getVOs("AD_IMPORTNOTASITE", "CODIMPORTACAO = " + codImportacao + " AND AD_IDEXTERNO = " + cab.asBigDecimal("AD_IDEXTERNO"));
                    for (DynamicVO item : itens) {
                        BigDecimal codimpIte = item.asBigDecimalOrZero("CODIMPITE");
                        BigDecimal qtd = item.asBigDecimalOrZero("QTDNEG");
                        BigDecimal vlrUnit = item.asBigDecimalOrZero("VLRUNIT");
                        BigDecimal percentual = item.asBigDecimalOrZero("PERCDESC").divide(new BigDecimal(100));
                        BigDecimal vlrTotalItem = qtd.multiply(vlrUnit);
                        BigDecimal desconto = vlrTotalItem.multiply(percentual);
                        BigDecimal vlrTotal = vlrTotalItem.subtract(desconto);

                        JapeHelper.CreateNewLine newIte = new JapeHelper.CreateNewLine("ItemNota");
                        DynamicVO produto = JapeHelper.getVO("Produto", "CODPROD=" + item.asBigDecimalOrZero("CODPROD"));
                        newIte.set("NUNOTA", nunota);
                        newIte.set("CODPROD", item.asBigDecimalOrZero("CODPROD"));
                        newIte.set("QTDNEG", item.asBigDecimalOrZero("QTDNEG"));
                        newIte.set("VLRUNIT", item.asBigDecimalOrZero("VLRUNIT"));
                        newIte.set("PERCDESC", item.asBigDecimalOrZero("PERCDESC"));
                        newIte.set("VLRTOT", vlrTotal);
                        newIte.set("CODVOL", item.asString("CODVOL"));
                        newIte.set("CODLOCALORIG", item.asBigDecimalOrZero("CODLOCALORIG"));
                        newIte.set("USOPROD", produto.asString("USOPROD"));
                        newIte.set("ATUALESTOQUE", BigDecimal.ZERO);
                        newIte.save();

                        inserirNunotaImp("AD_IMPORTNOTASITE", codImportacao, codimpIte, nunota);
                    }

                    recalcularImpostos(nunota);
                }
            }

        contextoAcao.setMensagemRetorno("Notas criadas com sucesso! " + notas.stream().map(BigDecimal::toString).collect(Collectors.joining(",")));
    }

    private static void recalcularImpostos(BigDecimal nunota) throws Exception {
        ImpostosHelpper impostosHelper = new ImpostosHelpper();
        impostosHelper.setForcarRecalculo(true);
        impostosHelper.carregarNota(nunota);
        impostosHelper.calcularImpostos(nunota);
        impostosHelper.totalizarNota(nunota);
        impostosHelper.salvarNota();
    }

    private static void inserirNunotaImp(String intancia, Object codImportacao, BigDecimal codigo, BigDecimal nunota) throws MGEModelException {
        JapeSession.SessionHandle hnd = null;

        try {
        hnd = JapeSession.open();
        JapeFactory.dao(intancia).
                prepareToUpdateByPK(codImportacao, codigo)
                .set("NUNOTA", nunota)
                .update();
        } catch (Exception e) {
            MGEModelException.throwMe(e);
        } finally {
            JapeSession.close(hnd);
        }
    }

}
