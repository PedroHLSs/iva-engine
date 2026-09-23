package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchadosDaExecucao;
import br.edu.tcc.auditoria.dominio.tratativa.ChaveDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// Classe que lê do banco os apontamentos que uma execução produziu, com a tratativa atual de cada um. Usa a tabela achado_da_execucao, e não ultima_execucao_id, para uma execução antiga não perder os apontamentos que uma rodada nova reencontrou.
@Component
class ConsultaDeAchadosDaExecucaoNoBanco implements ConsultaDeAchadosDaExecucao {

    private final AchadoDaExecucaoJpa vinculos;
    private final AchadoJpa achados;
    private final TratativaJpa tratativas;

    // Construtor que recebe os repositórios de vínculos, apontamentos e tratativas.
    ConsultaDeAchadosDaExecucaoNoBanco(
            AchadoDaExecucaoJpa vinculos, AchadoJpa achados, TratativaJpa tratativas) {
        this.vinculos = vinculos;
        this.achados = achados;
        this.tratativas = tratativas;
    }

    // Busca os apontamentos da execução, ordenados por gravidade, com a tratativa de cada um.
    @Override
    @Transactional(readOnly = true)
    public List<AchadoRegistrado> daExecucao(UUID execucaoId) {
        if (execucaoId == null) {
            return List.of();
        }
        List<UUID> identificadores = vinculos.findByExecucaoId(execucaoId).stream()
                .map(AchadoDaExecucaoEntidade::achadoId)
                .toList();
        if (identificadores.isEmpty()) {
            return List.of();
        }

        List<AchadoEntidade> encontrados = achados.findAllById(identificadores).stream()
                .sorted(porGravidadeEDepoisPorDocumento())
                .toList();

        Map<ChaveDeTratativa, Tratativa> aplicaveis = buscarTratativas(encontrados);
        return encontrados.stream()
                .map(entidade -> montar(entidade, aplicaveis))
                .toList();
    }

    // Método auxiliar que ordena da gravidade maior para a menor, pela ordem do enum e não pelo texto, e depois por documento, item e regra.
    private static Comparator<AchadoEntidade> porGravidadeEDepoisPorDocumento() {
        return Comparator
                .comparingInt((AchadoEntidade entidade) -> entidade.severidade().ordinal())
                .thenComparing(AchadoEntidade::chaveAcesso)
                .thenComparingInt(AchadoEntidade::numeroItem)
                .thenComparing(AchadoEntidade::regraId);
    }

    // Método auxiliar que busca de uma vez as tratativas dos itens encontrados.
    private Map<ChaveDeTratativa, Tratativa> buscarTratativas(List<AchadoEntidade> encontrados) {
        Set<String> hashes = encontrados.stream()
                .map(AchadoEntidade::hashItem)
                .collect(Collectors.toSet());

        Map<ChaveDeTratativa, Tratativa> porChave = new LinkedHashMap<>();
        for (TratativaEntidade entidade : tratativas.findByHashItemIn(hashes)) {
            Tratativa tratativa = MapeadorDeTratativa.paraDominio(entidade);
            porChave.put(tratativa.chave(), tratativa);
        }
        return porChave;
    }

    // Método auxiliar que junta o apontamento com a tratativa da mesma chave, se houver.
    private static AchadoRegistrado montar(
            AchadoEntidade entidade, Map<ChaveDeTratativa, Tratativa> tratativas) {

        HashDoItem hashDoItem = new HashDoItem(entidade.hashItem());
        ChaveDeTratativa chave =
                new ChaveDeTratativa(hashDoItem, entidade.regraId(), entidade.regraVersao());

        return new AchadoRegistrado(
                entidade.id(),
                MapeadorDeAchado.paraDominio(entidade),
                hashDoItem,
                Optional.ofNullable(tratativas.get(chave)),
                entidade.detectadoEm(),
                entidade.vistoEm());
    }
}
