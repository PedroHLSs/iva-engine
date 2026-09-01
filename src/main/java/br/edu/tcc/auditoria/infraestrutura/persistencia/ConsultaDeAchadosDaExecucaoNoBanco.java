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

/**
 * Lê os apontamentos que uma execução produziu, com a tratativa atual de cada um.
 *
 * <p>O recorte sai da tabela de vínculo {@code achado_da_execucao}, e não de
 * {@code ultima_execucao_id}: aquele campo guarda apenas quem viu o apontamento
 * por último, de modo que uma execução antiga passaria a "não ter" os
 * apontamentos que uma rodada posterior reencontrou.</p>
 */
@Component
class ConsultaDeAchadosDaExecucaoNoBanco implements ConsultaDeAchadosDaExecucao {

    private final AchadoDaExecucaoJpa vinculos;
    private final AchadoJpa achados;
    private final TratativaJpa tratativas;

    ConsultaDeAchadosDaExecucaoNoBanco(
            AchadoDaExecucaoJpa vinculos, AchadoJpa achados, TratativaJpa tratativas) {
        this.vinculos = vinculos;
        this.achados = achados;
        this.tratativas = tratativas;
    }

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

    /**
     * Do mais grave para o menos grave, e dentro da severidade pela ordem do
     * documento, do item e da regra.
     *
     * <p>A ordem não pode sair do texto da severidade: em ordem alfabética
     * "INFORMATIVA" viria antes de "MODERADA", e a planilha abriria pela
     * observação em vez da incoerência. {@code Severidade} declara as constantes
     * da mais grave para a menos grave, e é essa ordem que vale.</p>
     */
    private static Comparator<AchadoEntidade> porGravidadeEDepoisPorDocumento() {
        return Comparator
                .comparingInt((AchadoEntidade entidade) -> entidade.severidade().ordinal())
                .thenComparing(AchadoEntidade::chaveAcesso)
                .thenComparingInt(AchadoEntidade::numeroItem)
                .thenComparing(AchadoEntidade::regraId);
    }

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
