package br.edu.tcc.auditoria.aplicacao.conferencia;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia.NAO_FOI_POSSIVEL_CONCLUIR;
import static br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia.POSSIVEL_DIVERGENCIA;
import static br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia.REQUER_CONFERENCIA;
import static br.edu.tcc.auditoria.aplicacao.conferencia.EstadoDeConferencia.SEM_DIVERGENCIA_IDENTIFICADA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Os quatro números, sempre os quatro. */
class ContagemDeEstadosTest {

    @Test
    void deveTrazerOsQuatroEstadosInclusiveOsQueNaoOcorreram() {
        ContagemDeEstados contagem = ContagemDeEstados.de(List.of(POSSIVEL_DIVERGENCIA));

        assertThat(contagem.porEstado()).hasSize(4);
        assertThat(contagem.quantidadeDe(POSSIVEL_DIVERGENCIA)).isEqualTo(1);
        assertThat(contagem.quantidadeDe(REQUER_CONFERENCIA)).isZero();
        assertThat(contagem.quantidadeDe(NAO_FOI_POSSIVEL_CONCLUIR)).isZero();
        assertThat(contagem.quantidadeDe(SEM_DIVERGENCIA_IDENTIFICADA)).isZero();
    }

    @Test
    void deveIterarNaOrdemDePrecedencia() {
        assertThat(ContagemDeEstados.nenhum().porEstado().keySet())
                .containsExactly(
                        POSSIVEL_DIVERGENCIA,
                        REQUER_CONFERENCIA,
                        NAO_FOI_POSSIVEL_CONCLUIR,
                        SEM_DIVERGENCIA_IDENTIFICADA);
    }

    @Test
    void deveRecusarContagemAQueFalteAlgumDosEstados() {
        Map<EstadoDeConferencia, Integer> incompleta = new EnumMap<>(EstadoDeConferencia.class);
        incompleta.put(POSSIVEL_DIVERGENCIA, 1);
        incompleta.put(REQUER_CONFERENCIA, 0);
        incompleta.put(NAO_FOI_POSSIVEL_CONCLUIR, 0);

        assertThatThrownBy(() -> new ContagemDeEstados(incompleta))
                .isInstanceOf(ConferenciaInvalida.class)
                .hasMessageContaining(SEM_DIVERGENCIA_IDENTIFICADA.rotulo());
    }

    @Test
    void deveRecusarContagemNegativa() {
        Map<EstadoDeConferencia, Integer> negativa = new EnumMap<>(EstadoDeConferencia.class);
        for (EstadoDeConferencia estado : EstadoDeConferencia.values()) {
            negativa.put(estado, 0);
        }
        negativa.put(NAO_FOI_POSSIVEL_CONCLUIR, -1);

        assertThatThrownBy(() -> new ContagemDeEstados(negativa))
                .isInstanceOf(ConferenciaInvalida.class)
                .hasMessageContaining("negativa");
    }

    @Test
    void deveRecusarColecaoNulaEElementoNulo() {
        assertThatThrownBy(() -> ContagemDeEstados.de(null))
                .isInstanceOf(ConferenciaInvalida.class);

        List<EstadoDeConferencia> comNulo = new java.util.ArrayList<>();
        comNulo.add(POSSIVEL_DIVERGENCIA);
        comNulo.add(null);
        assertThatThrownBy(() -> ContagemDeEstados.de(comNulo))
                .isInstanceOf(ConferenciaInvalida.class)
                .hasMessageContaining("nulo");
    }

    @Test
    void deveSomarEstadoAEstado() {
        ContagemDeEstados uma = ContagemDeEstados.de(List.of(
                POSSIVEL_DIVERGENCIA, SEM_DIVERGENCIA_IDENTIFICADA));
        ContagemDeEstados outra = ContagemDeEstados.de(List.of(
                NAO_FOI_POSSIVEL_CONCLUIR, SEM_DIVERGENCIA_IDENTIFICADA));

        ContagemDeEstados soma = uma.mais(outra);

        assertThat(soma.quantidadeDe(POSSIVEL_DIVERGENCIA)).isEqualTo(1);
        assertThat(soma.quantidadeDe(REQUER_CONFERENCIA)).isZero();
        assertThat(soma.quantidadeDe(NAO_FOI_POSSIVEL_CONCLUIR)).isEqualTo(1);
        assertThat(soma.quantidadeDe(SEM_DIVERGENCIA_IDENTIFICADA)).isEqualTo(2);
        assertThat(soma.total()).isEqualTo(4);
    }

    @Test
    void deveContarNadaComOsQuatroEmZero() {
        assertThat(ContagemDeEstados.nenhum().total()).isZero();
        assertThat(ContagemDeEstados.nenhum().porEstado()).hasSize(4);
        assertThat(ContagemDeEstados.de(List.of()).porEstado().values()).allMatch(zero -> zero == 0);
    }

    @Test
    void naoDevePermitirAlteracaoDoMapaDevolvido() {
        Map<EstadoDeConferencia, Integer> porEstado = ContagemDeEstados.nenhum().porEstado();
        assertThatThrownBy(() -> porEstado.put(POSSIVEL_DIVERGENCIA, 99))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
