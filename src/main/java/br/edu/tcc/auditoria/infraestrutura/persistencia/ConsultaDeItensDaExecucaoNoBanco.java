package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeItensDaExecucao;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaInvalida;
import br.edu.tcc.auditoria.aplicacao.consulta.DadosDoItem;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Junta o que a análise leu (V7) com o conteúdo declarado de cada item (V2).
 *
 * <p>Duas consultas, não uma por item: um lote de quatrocentas notas tem
 * milhares de itens, e uma ida ao banco por item para desenhar uma tabela seria
 * trabalho real e visível.</p>
 */
@Component
class ConsultaDeItensDaExecucaoNoBanco implements ConsultaDeItensDaExecucao {

    private final ItemDaExecucaoJpa doAcervo;
    private final ItemDocumentoJpa itens;

    ConsultaDeItensDaExecucaoNoBanco(ItemDaExecucaoJpa doAcervo, ItemDocumentoJpa itens) {
        this.doAcervo = doAcervo;
        this.itens = itens;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DadosDoItem> daExecucao(UUID execucaoId) {
        if (execucaoId == null) {
            throw new ConsultaInvalida("Não há execução cujos itens consultar.");
        }

        List<ItemDaExecucaoEntidade> lidos =
                doAcervo.findByExecucaoIdOrderByChaveAcessoAscNumeroItemAsc(execucaoId);
        if (lidos.isEmpty()) {
            return List.of();
        }

        Set<String> chaves = new LinkedHashSet<>();
        lidos.forEach(linha -> chaves.add(linha.chaveAcesso()));

        Map<String, ItemDocumentoEntidade> porEndereco = new LinkedHashMap<>();
        for (ItemDocumentoEntidade item : itens.findByChaveAcessoIn(chaves)) {
            porEndereco.put(endereco(item.chaveAcesso(), item.numeroItem()), item);
        }

        List<DadosDoItem> encontrados = new ArrayList<>();
        for (ItemDaExecucaoEntidade lido : lidos) {
            ItemDocumentoEntidade item =
                    porEndereco.get(endereco(lido.chaveAcesso(), lido.numeroItem()));
            if (item == null) {
                // A chave estrangeira da V7 cascateia, então o acervo não deveria
                // apontar para item inexistente. Se aponta, o banco foi alterado
                // por fora — e a chave não entra na mensagem, porque carrega o
                // CNPJ do emitente.
                throw new PersistenciaInconsistente(
                        ("O acervo da execução aponta para o item %d de um documento que não está "
                                + "gravado.").formatted(lido.numeroItem()));
            }
            encontrados.add(new DadosDoItem(
                    new ChaveAcesso(lido.chaveAcesso()),
                    MapeadorDeDocumento.paraDominio(item),
                    // A descrição vem da linha do acervo, que é o que ESTA análise
                    // leu. item_documento não a guarda, e não deve: ele é
                    // sobrescrito a cada reprocessamento.
                    lido.descricao(),
                    new HashDoItem(lido.hashItem()),
                    new HashDoItem(item.hashItem())));
        }
        return List.copyOf(encontrados);
    }

    private static String endereco(String chaveAcesso, int numeroItem) {
        return chaveAcesso + "#" + numeroItem;
    }
}
