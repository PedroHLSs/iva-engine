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

// Classe que junta o que a análise leu, da tabela da V7, com o que cada item declarou, da tabela da V2, em duas consultas, e não uma por item.
@Component
class ConsultaDeItensDaExecucaoNoBanco implements ConsultaDeItensDaExecucao {

    private final ItemDaExecucaoJpa doAcervo;
    private final ItemDocumentoJpa itens;

    // Construtor que recebe os repositórios de itens da execução e de itens do documento.
    ConsultaDeItensDaExecucaoNoBanco(ItemDaExecucaoJpa doAcervo, ItemDocumentoJpa itens) {
        this.doAcervo = doAcervo;
        this.itens = itens;
    }

    // Busca os itens lidos pela execução, com o conteúdo declarado e a descrição de cada um.
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
                // O banco não deveria apontar para item que não existe; se aponta, foi alterado por fora. A chave não entra na mensagem, porque contém o CNPJ.
                throw new PersistenciaInconsistente(
                        ("O acervo da execução aponta para o item %d de um documento que não está "
                                + "gravado.").formatted(lido.numeroItem()));
            }
            encontrados.add(new DadosDoItem(
                    new ChaveAcesso(lido.chaveAcesso()),
                    MapeadorDeDocumento.paraDominio(item),
                    // A descrição vem da linha que ESTA análise leu, porque item_documento é sobrescrito a cada reprocessamento.
                    lido.descricao(),
                    new HashDoItem(lido.hashItem()),
                    new HashDoItem(item.hashItem())));
        }
        return List.copyOf(encontrados);
    }

    // Método auxiliar que monta o endereço do item: chave de acesso e número.
    private static String endereco(String chaveAcesso, int numeroItem) {
        return chaveAcesso + "#" + numeroItem;
    }
}
