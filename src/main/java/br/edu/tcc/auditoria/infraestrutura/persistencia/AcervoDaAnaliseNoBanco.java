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
import java.util.Optional;
import java.util.UUID;

// Repositório que grava e lê as duas tabelas da V7: os itens que a análise leu e os arquivos que ela não conseguiu ler. É uma classe só porque as duas nascem juntas, mas a escrita e a leitura continuam em portas separadas.
// Emenda de 04/10/2026 (D018): grava também a marca da V20, na mesma transação, dizendo que a lista de ilegíveis foi registrada e com quantas linhas. Sem ela, a leitura devolve vazio em vez de lista vazia: tabela sem linha não prova que nada falhou.
@Repository
class AcervoDaAnaliseNoBanco implements RegistroDoAcervoDaAnalise, ConsultaDoAcervoDaAnalise {

    private final ItemDaExecucaoJpa itens;
    private final FalhaDeLeituraDaExecucaoJpa falhas;
    private final LeituraDaExecucaoJpa leituras;

    // Construtor que recebe os repositórios de itens, de falhas e da marca de leitura da execução.
    AcervoDaAnaliseNoBanco(
            ItemDaExecucaoJpa itens, FalhaDeLeituraDaExecucaoJpa falhas, LeituraDaExecucaoJpa leituras) {
        this.itens = itens;
        this.falhas = falhas;
        this.leituras = leituras;
    }

    // Grava os itens lidos e os arquivos ilegíveis, na ordem em que falharam.
    @Override
    @Transactional
    public void registrar(
            UUID execucaoId, List<ItemDaAnalise> lidos, List<ArquivoIlegivel> ilegiveis,
            int documentosRepetidosDescartados) {

        if (execucaoId == null) {
            throw new AnaliseInvalida("Não há execução a que vincular o acervo da análise.");
        }
        exigirSemNulo(lidos, "itens lidos");
        exigirSemNulo(ilegiveis, "arquivos ilegíveis");
        if (documentosRepetidosDescartados < 0) {
            throw new AnaliseInvalida("A contagem de documentos repetidos descartados não pode ser negativa.");
        }

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
        leituras.save(new LeituraDaExecucaoEntidade(execucaoId, ilegiveis.size(), documentosRepetidosDescartados));
    }

    // Busca os itens que a execução leu.
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

    // Busca os arquivos que a execução não conseguiu ler, na ordem em que falharam; vazio quando a leitura não foi registrada.
    @Override
    @Transactional(readOnly = true)
    public Optional<List<ArquivoIlegivel>> arquivosIlegiveis(UUID execucaoId) {
        exigirExecucao(execucaoId);
        List<ArquivoIlegivel> gravados = falhas.findByExecucaoIdOrderByOrdemAsc(execucaoId).stream()
                .map(linha -> new ArquivoIlegivel(
                        linha.origem(), linha.tipoDeErro(), linha.motivo()))
                .toList();

        Optional<LeituraDaExecucaoEntidade> marca = leituras.findById(execucaoId);
        if (marca.isPresent()) {
            if (marca.get().arquivosIlegiveis() != gravados.size()) {
                throw new AnaliseInvalida(
                        ("A marca de leitura da execução diz %d arquivo(s) ilegível(is) e há %d gravado(s). "
                                + "O banco foi alterado por fora, e nenhum dos dois números pode ser "
                                + "afirmado.").formatted(marca.get().arquivosIlegiveis(), gravados.size()));
            }
            return Optional.of(gravados);
        }
        // Análise da interface anterior à V20: o registro gravava itens e falhas juntos, numa transação só,
        // e qualquer linha de uma das duas prova que a lista inteira foi gravada.
        if (!gravados.isEmpty() || itens.existsByExecucaoId(execucaoId)) {
            return Optional.of(gravados);
        }
        return Optional.empty();
    }

    // Busca quantos documentos repetidos a execução descartou; vazio sem a marca, ou com a marca anterior à V21.
    @Override
    @Transactional(readOnly = true)
    public Optional<Integer> documentosRepetidosDescartados(UUID execucaoId) {
        exigirExecucao(execucaoId);
        return leituras.findById(execucaoId).map(LeituraDaExecucaoEntidade::documentosDuplicados);
    }

    // Método auxiliar que exige o identificador da execução.
    private static void exigirExecucao(UUID execucaoId) {
        if (execucaoId == null) {
            throw new AnaliseInvalida("Não há execução cujo acervo consultar.");
        }
    }

    // Método auxiliar que exige lista não nula e sem elemento nulo.
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
