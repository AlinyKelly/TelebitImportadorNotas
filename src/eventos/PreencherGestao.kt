package eventos

import br.com.sankhya.extensions.eventoprogramavel.EventoProgramavelJava
import br.com.sankhya.jape.core.JapeSession
import br.com.sankhya.jape.event.PersistenceEvent
import br.com.sankhya.jape.event.TransactionContext
import br.com.sankhya.jape.vo.DynamicVO
import br.com.sankhya.jape.wrapper.JapeFactory
import br.com.sankhya.jape.wrapper.fluid.FluidCreateVO
import br.com.sankhya.modelcore.MGEModelException
import br.com.sankhya.modelcore.util.DynamicEntityNames
import utilitarios.getFluidCreateVO
import utilitarios.retornaVO


/*
* Ao inserir uma etapa na tabela TCSFET (Etapa do Projeto)
* devemos inserir a mesma na tela AD_TGESPROJ (Gestão de Projetos)
* Realizar todas as etapas para Insert, Update e Delete.
*/

class PreencherGestao : EventoProgramavelJava {
    override fun beforeInsert(persistenceEvent: PersistenceEvent?) {
        
    }

    override fun beforeUpdate(persistenceEvent: PersistenceEvent?) {
        val etapaVO = persistenceEvent!!.vo as DynamicVO
        val nuFap = etapaVO.asBigDecimalOrZero("NUFAP")
        val numEtapa = etapaVO.asBigDecimalOrZero("NUMETAPA")
        val servico = etapaVO.asBigDecimalOrZero("CODPROD")
        val concluida = etapaVO.asString("CONCLUIDA")
        val descricao = etapaVO.asString("DESCRICAO")
        val observacao = etapaVO.asString("OBSERVACAO")
        val dtInicio = etapaVO.asTimestamp("DTCEDOINIPREV")
        val dtFim = etapaVO.asTimestamp("DTCEDOFIMPREV")

        val fields = listOf("CONCLUIDA","DESCRICAO","OBSERVACAO", "DTCEDOINIPREV", "DTCEDOFIMPREV")
        val isModifing = fields.any {
            persistenceEvent.modifingFields.isModifing(it)
        }

        if (isModifing) {
            val atualizarGP = retornaVO("AD_TGESPROJ", "NUFAP = $nuFap AND NUMETAPA = $numEtapa")

            if (atualizarGP != null) {
                var hnd: JapeSession.SessionHandle? = null

                try {
                    hnd = JapeSession.open()
                    JapeFactory.dao("AD_TGESPROJ").prepareToUpdate(atualizarGP)
                        .set("CONCLUIDA", concluida)
                        .set("CODPROD", servico)
                        .set("DESCRICAO", descricao)
                        .set("OBSERVACAO", observacao)
                        .set("DTCEDOINIPREV", dtInicio)
                        .set("DTCEDOFIMPREV", dtFim)
                        .update()
                } catch (e: Exception) {
                    MGEModelException.throwMe(e)
                } finally {
                    JapeSession.close(hnd)
                }
            }

        }

    }

    override fun beforeDelete(persistenceEvent: PersistenceEvent?) {
        val etapaVO = persistenceEvent!!.vo as DynamicVO
        val nuFap = etapaVO.asBigDecimalOrZero("NUFAP")
        val numEtapa = etapaVO.asBigDecimalOrZero("NUMETAPA")

        val gestaoVO = retornaVO("AD_TGESPROJ", "NUFAP = $nuFap AND NUMETAPA = $numEtapa")

        if (gestaoVO != null) {
            //realizar o delete
            val idAtividade = gestaoVO.asBigDecimal("IDATIVIDADE")

            var hnd: JapeSession.SessionHandle? = null

            try {
                hnd = JapeSession.open()
                val gestaoDAO = JapeFactory.dao("AD_TGESPROJ")
                gestaoDAO.delete(idAtividade)
            } catch (e: Exception) {
                MGEModelException.throwMe(e)
            } finally {
                JapeSession.close(hnd)
            }

        }
    }

    override fun afterInsert(persistenceEvent: PersistenceEvent?) {
        val etapaVO = persistenceEvent!!.vo as DynamicVO
        val nuFap = etapaVO.asBigDecimalOrZero("NUFAP")
        val numEtapa = etapaVO.asBigDecimalOrZero("NUMETAPA")
        val concluida = etapaVO.asString("CONCLUIDA")
        val descricao = etapaVO.asString("DESCRICAO")
        val observacao = etapaVO.asString("OBSERVACAO")
        val servico = etapaVO.asBigDecimalOrZero("CODPROD")
        val dtInicio = etapaVO.asTimestamp("DTCEDOINIPREV")
        val dtFim = etapaVO.asTimestamp("DTCEDOFIMPREV")

        // Buscar informações do Projeto Servico na tabela TCSFAP
        val buscarProjServ = retornaVO("ProjetoServico", "NUFAP = $nuFap")
        val projeto = buscarProjServ?.asBigDecimalOrZero("AD_CODPROJ")
        val contrato = buscarProjServ?.asBigDecimalOrZero("AD_NUMCONTRATO")
        val coordenador = buscarProjServ?.asBigDecimalOrZero("CODCOORD")
        val parceiro = buscarProjServ?.asBigDecimalOrZero("CODPARC")

        // Buscar outras informação do Projeto na tabela TCSPRJ
        val buscarProjeto = retornaVO("Projeto", "CODPROJ = $projeto")
        val descrProjeto = buscarProjeto?.asString("IDENTIFICACAO")

        // Buscar o gerente de projeto na Equipe, tabela TCSFEQ
        val buscarEquipe = retornaVO("EquipeProjeto", "NUFAP = $nuFap AND CODTIPFUNCAO = 1")
        val gerente = buscarEquipe?.asBigDecimalOrZero("CODUSU")

        // Buscar informações da LPU na tabela de Produtos/Serviços do Contrato TCSPSC
        val buscarItemLPU = retornaVO("ProdutoServicoContrato", "NUMCONTRATO = $contrato AND CODPROD = $servico")
        val quantidade = buscarItemLPU?.asBigDecimalOrZero("NUMUSUARIOS")
        val regional = buscarItemLPU?.asString("AD_REGIONAL")
        val descrDetalhada = buscarItemLPU?.asString("AD_DESCRDET")

        // Buscar valores do item da LPU na tabela Preco do Contrato TCSPRE
        val buscarPrecoLPU = retornaVO("PrecoContrato", "NUMCONTRATO = $contrato AND CODPROD = $servico")
        val valorUnit = buscarPrecoLPU?.asBigDecimalOrZero("VALOR")

//        val buscarServProd = retornaVO("Servico", "CODPROD = $servico") ?: retornaVO("Produto", "CODPROD = $servico")
//        val descricaoProd = buscarServProd?.asString("DESCRPROD")

        val gestaoProjFCVo: FluidCreateVO = getFluidCreateVO("AD_TGESPROJ")
        gestaoProjFCVo.set("NUFAP", nuFap)
        gestaoProjFCVo.set("NUMETAPA", numEtapa)
        gestaoProjFCVo.set("CONCLUIDA", concluida)
        gestaoProjFCVo.set("CODPROJ", projeto)
        gestaoProjFCVo.set("IDENTIFICACAO", descrProjeto)
        gestaoProjFCVo.set("CODUSU", gerente)
        gestaoProjFCVo.set("CODCOORD", coordenador)
        gestaoProjFCVo.set("NUMCONTRATO", contrato)
        gestaoProjFCVo.set("CODPROD", servico)
        gestaoProjFCVo.set("QTDNEG", quantidade)
        gestaoProjFCVo.set("REGIONAL", regional)
        gestaoProjFCVo.set("DTCEDOINIPREV", dtInicio)
        gestaoProjFCVo.set("DTCEDOFIMPREV", dtFim)
        gestaoProjFCVo.set("DESCRICAO", descricao) //Descrição da Etapa
        gestaoProjFCVo.set("DESCRDET", descrDetalhada)
        gestaoProjFCVo.set("VLRUNIT", valorUnit)
        gestaoProjFCVo.set("VLRATV", valorUnit)
        gestaoProjFCVo.set("OBSERVACAO", observacao)
        gestaoProjFCVo.set("CODPARC", parceiro)
        gestaoProjFCVo.save()
    }

    override fun afterUpdate(persistenceEvent: PersistenceEvent?) {

    }

    override fun afterDelete(persistenceEvent: PersistenceEvent?) {
        
    }

    override fun beforeCommit(persistenceEvent: TransactionContext?) {
        
    }
}