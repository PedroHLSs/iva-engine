package br.edu.tcc.auditoria.infraestrutura.persistencia;

import br.edu.tcc.auditoria.aplicacao.consulta.AchadoRegistrado;
import br.edu.tcc.auditoria.aplicacao.consulta.ConsultaDeAchados;
import br.edu.tcc.auditoria.aplicacao.consulta.FiltroDeAchados;
import br.edu.tcc.auditoria.dominio.ChaveAcesso;
import br.edu.tcc.auditoria.dominio.Severidade;
import br.edu.tcc.auditoria.dominio.tratativa.ChaveDeTratativa;
import br.edu.tcc.auditoria.dominio.tratativa.HashDoItem;
import br.edu.tcc.auditoria.dominio.tratativa.Tratativa;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Lista apontamentos gravados já acompanhados da tratativa que houver.
 *
 * <p>A tratativa vem junto, e não numa consulta separada feita depois por quem
 * exibe o relatório: apontamento listado sem dizer que já foi tratado faz a
 * mesma pessoa reexaminar a mesma coisa a cada rodada.</p>
 *
 * <p>A busca das tratativas é feita em bloco, pelos resumos dos apontamentos da
 * página, e o pareamento final é por chave completa — resumo do item, regra e
 * versão da regra. Uma tratativa gravada para outra versão da mesma regra não
 * pareia, e o apontamento aparece aberto. Isso é o comportamento pretendido, não
 * um efeito colateral.</p>
 */
@Component
class ConsultaDeAchadosNoBanco implements ConsultaDeAchados {

    private final AchadoJpa achados;
    private final TratativaJpa tratativas;

    ConsultaDeAchadosNoBanco(AchadoJpa achados, TratativaJpa tratativas) {
        this.achados = achados;
        this.tratativas = tratativas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AchadoRegistrado> listar(FiltroDeAchados filtro) {
        List<AchadoEntidade> encontrados = achados.filtrar(
                filtro.severidade().orElse(null),
                filtro.regraId().orElse(null),
                filtro.chaveAcesso().map(ChaveAcesso::valor).orElse(null),
                filtro.apenasAbertos(),
                Severidade.CRITICA,
                Severidade.GRAVE,
                Severidade.MODERADA,
                PageRequest.of(0, filtro.limite()));

        Map<ChaveDeTratativa, Tratativa> tratativasAplicaveis = buscarTratativas(encontrados);
        return encontrados.stream()
                .map(entidade -> montar(entidade, tratativasAplicaveis))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AchadoRegistrado> porId(UUID id) {
        return achados.findById(id)
                .map(entidade -> montar(entidade, buscarTratativas(List.of(entidade))));
    }

    @Override
    @Transactional(readOnly = true)
    public long contar(FiltroDeAchados filtro) {
        return achados.contar(
                filtro.severidade().orElse(null),
                filtro.regraId().orElse(null),
                filtro.chaveAcesso().map(ChaveAcesso::valor).orElse(null),
                filtro.apenasAbertos());
    }

    private Map<ChaveDeTratativa, Tratativa> buscarTratativas(List<AchadoEntidade> encontrados) {
        if (encontrados.isEmpty()) {
            return Map.of();
        }
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
