package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.analise.AnaliseInvalida;
import br.edu.tcc.auditoria.aplicacao.analise.ArquivoIlegivel;
import br.edu.tcc.auditoria.aplicacao.analise.ConsultaDoAcervoDaAnalise;
import br.edu.tcc.auditoria.aplicacao.analise.ItemDaAnalise;
import br.edu.tcc.auditoria.aplicacao.analise.RegistroDoAcervoDaAnalise;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * As duas tabelas da V7, do lado de quem grava e do lado de quem lê.
 *
 * <p>Uma classe só porque é um assunto só — o que a análise leu —, e porque as
 * duas tabelas nascem juntas na mesma gravação. As portas continuam separadas,
 * escrita de um lado e leitura do outro, como no resto do projeto.</p>
 */
@Repository
class AcervoDaAnaliseNoBanco implements RegistroDoAcervoDaAnalise, ConsultaDoAcervoDaAnalise {

    private final ItemDaExecucaoJpa itens;
    private final FalhaDeLeituraDaExecucaoJpa falhas;

    AcervoDaAnaliseNoBanco(ItemDaExecucaoJpa itens, FalhaDeLeituraDaExecucaoJpa falhas) {
        this.itens = itens;
        this.falhas = falhas;
    }

    @Override
    @Transactional
    public void registrar(
            UUID execucaoId, List<ItemDaAnalise> lidos, List<ArquivoIlegivel> ilegiveis) {

        if (execucaoId == null) {
            throw new AnaliseInvalida("Não há execução a que vincular o acervo da análise.");
        }
        exigirSemNulo(lidos, "itens lidos");
        exigirSemNulo(ilegiveis, "arquivos ilegíveis");

        List<ItemDaExecucaoEntidade> linhasDeItem = new ArrayList<>();
        for (ItemDaAnalise item : lidos) {
            linhasDeItem.add(new ItemDaExecucaoEntidade(
                    UUID.randomUUID(),
                    execucaoId,
                    item.chaveAcesso().valor(),
                    item.numeroItem(),
                    item.hashDoItem().valor(),
                    item.descricao()));
        }
        itens.saveAll(linhasDeItem);

        List<FalhaDeLeituraDaExecucaoEntidade> linhasDeFalha = new ArrayList<>();
        for (int ordem = 0; ordem < ilegiveis.size(); ordem++) {
            ArquivoIlegivel ilegivel = ilegiveis.get(ordem);
            linhasDeFalha.add(new FalhaDeLeituraDaExecucaoEntidade(
                    UUID.randomUUID(),
                    execucaoId,
                    ordem,
                    ilegivel.origem(),
                    ilegivel.tipoDeErro(),
                    ilegivel.motivo()));
        }
        falhas.saveAll(linhasDeFalha);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemDaAnalise> itensDaExecucao(UUID execucaoId) {
        exigirExecucao(execucaoId);
        return itens.findByExecucaoIdOrderByChaveAcessoAscNumeroItemAsc(execucaoId).stream()
                .map(linha -> new ItemDaAnalise(
                        new ChaveAcesso(linha.chaveAcesso()),
                        linha.numeroItem(),
                        new HashDoItem(linha.hashItem()),
                        linha.descricao()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArquivoIlegivel> arquivosIlegiveis(UUID execucaoId) {
        exigirExecucao(execucaoId);
        return falhas.findByExecucaoIdOrderByOrdemAsc(execucaoId).stream()
                .map(linha -> new ArquivoIlegivel(
                        linha.origem(), linha.tipoDeErro(), linha.motivo()))
                .toList();
    }

    private static void exigirExecucao(UUID execucaoId) {
        if (execucaoId == null) {
            throw new AnaliseInvalida("Não há execução cujo acervo consultar.");
        }
    }

    private static void exigirSemNulo(List<?> lista, String oQueE) {
        if (lista == null) {
            throw new AnaliseInvalida(
                    "A lista de %s deve ser vazia quando não há nenhum, nunca nula.".formatted(oQueE));
        }
        if (lista.stream().anyMatch(Objects::isNull)) {
            throw new AnaliseInvalida(
                    "A lista de %s não pode conter elemento nulo.".formatted(oQueE));
        }
    }
}
