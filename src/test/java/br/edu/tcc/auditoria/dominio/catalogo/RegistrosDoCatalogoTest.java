package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.excecao.RegistroNormativoInvalido;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrosDoCatalogoTest {

    @Test
    void todoRegistroDeveExporVigenciaEFonteNormativa() {
        List<RegistroNormativo> registros = List.of(
                CatalogoFicticio.classificacao(CatalogoFicticio.CODIGO, procedenciaFechada()),
                CatalogoFicticio.registroNcm(CatalogoFicticio.NCM, procedenciaFechada()),
                CatalogoFicticio.itemAnexo(CatalogoFicticio.NCM, CatalogoFicticio.ANEXO, procedenciaFechada()),
                CatalogoFicticio.aliquota(
                        Tributo.CBS, CatalogoFicticio.ABRANGENCIA, "99.99", procedenciaFechada()));

        assertThat(registros).allSatisfy(registro -> {
            assertThat(registro.vigenciaInicio()).isEqualTo(CatalogoFicticio.INICIO);
            assertThat(registro.vigenciaFim()).contains(CatalogoFicticio.FIM);
            assertThat(registro.fonteNormativa()).isEqualTo(CatalogoFicticio.FONTE);
            assertThat(registro.chaveDeVigencia()).isNotBlank();
        });
    }

    @Test
    void todoRegistroDeveResponderSeEstaVigenteNaData() {
        RegistroNormativo registro =
                CatalogoFicticio.classificacao(CatalogoFicticio.CODIGO, procedenciaFechada());

        assertThat(registro.vigenteEm(CatalogoFicticio.INICIO)).isTrue();
        assertThat(registro.vigenteEm(CatalogoFicticio.FIM)).isTrue();
        assertThat(registro.vigenteEm(CatalogoFicticio.INICIO.minusDays(1))).isFalse();
        assertThat(registro.vigenteEm(CatalogoFicticio.FIM.plusDays(1))).isFalse();
    }

    @Test
    void deveRejeitarProcedenciaSemFonteNormativa() {
        assertThatThrownBy(() -> ProcedenciaNormativa.aPartirDe(CatalogoFicticio.INICIO, "  "))
                .isInstanceOf(RegistroNormativoInvalido.class)
                .hasMessageContaining("fonte normativa");
    }

    @Test
    void deveRejeitarProcedenciaSemVigencia() {
        assertThatThrownBy(() -> new ProcedenciaNormativa(null, CatalogoFicticio.FONTE))
                .isInstanceOf(RegistroNormativoInvalido.class)
                .hasMessageContaining("vigência");
    }

    @Test
    void deveRejeitarRegistroSemProcedencia() {
        assertThatThrownBy(() -> CatalogoFicticio.classificacao(CatalogoFicticio.CODIGO, null))
                .isInstanceOf(RegistroNormativoInvalido.class);
        assertThatThrownBy(() -> CatalogoFicticio.registroNcm(CatalogoFicticio.NCM, null))
                .isInstanceOf(RegistroNormativoInvalido.class);
        assertThatThrownBy(() ->
                CatalogoFicticio.itemAnexo(CatalogoFicticio.NCM, CatalogoFicticio.ANEXO, null))
                .isInstanceOf(RegistroNormativoInvalido.class);
        assertThatThrownBy(() ->
                CatalogoFicticio.aliquota(Tributo.CBS, CatalogoFicticio.ABRANGENCIA, "99.99", null))
                .isInstanceOf(RegistroNormativoInvalido.class);
    }

    @Test
    void classificacaoDeveAceitarConjuntoVazioDeCstsCompativeis() {
        // Exigir ao menos um CST seria afirmação sobre a norma. Cabe à regra decidir.
        ClassificacaoTributaria classificacao = new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(CatalogoFicticio.CODIGO),
                Set.of(),
                CatalogoFicticio.DISPOSITIVO,
                false,
                Optional.empty(),
                List.of(),
                procedenciaFechada());

        assertThat(classificacao.cstsCompativeis()).isEmpty();
        assertThat(classificacao.admiteCst(new CodigoCst(CatalogoFicticio.CST))).isFalse();
    }

    @Test
    void classificacaoDeveDistinguirReducaoAusenteDeReducaoZerada() {
        ClassificacaoTributaria semReducao = classificacaoComReducao(Optional.empty());
        ClassificacaoTributaria reducaoZero = classificacaoComReducao(Optional.of(new BigDecimal("0.00")));

        assertThat(semReducao.percentualReducao()).isEmpty();
        assertThat(reducaoZero.percentualReducao()).isPresent();
        assertThat(semReducao).isNotEqualTo(reducaoZero);
    }

    @Test
    void classificacaoDeveRejeitarReducaoNulaEmVezDeOptionalVazio() {
        assertThatThrownBy(() -> classificacaoComReducao(null))
                .isInstanceOf(RegistroNormativoInvalido.class)
                .hasMessageContaining("Optional.empty()");
    }

    @Test
    void classificacaoDeveCopiarAsColecoesRecebidas() {
        List<String> campos = new ArrayList<>(List.of("campoFicticioUm"));
        ClassificacaoTributaria classificacao = new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(CatalogoFicticio.CODIGO),
                Set.of(new CodigoCst(CatalogoFicticio.CST)),
                CatalogoFicticio.DISPOSITIVO,
                false,
                Optional.empty(),
                campos,
                procedenciaFechada());

        campos.add("campoFicticioDois");

        assertThat(classificacao.camposObrigatoriosCondicionados()).containsExactly("campoFicticioUm");
    }

    @Test
    void classificacaoDeveRejeitarDispositivoLegalVazio() {
        assertThatThrownBy(() -> new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(CatalogoFicticio.CODIGO),
                Set.of(),
                "   ",
                false,
                Optional.empty(),
                List.of(),
                procedenciaFechada()))
                .isInstanceOf(RegistroNormativoInvalido.class)
                .hasMessageContaining("dispositivo legal");
    }

    @Test
    void itemAnexoDeveTerChaveFormadaPeloParNcmEAnexo() {
        ItemAnexo noPrimeiroAnexo =
                CatalogoFicticio.itemAnexo(CatalogoFicticio.NCM, CatalogoFicticio.ANEXO, procedenciaFechada());
        ItemAnexo noSegundoAnexo = CatalogoFicticio.itemAnexo(
                CatalogoFicticio.NCM, CatalogoFicticio.ANEXO_ALTERNATIVO, procedenciaFechada());

        // Chaves distintas: o mesmo NCM em dois anexos não é conflito de vigência.
        assertThat(noPrimeiroAnexo.chaveDeVigencia()).isNotEqualTo(noSegundoAnexo.chaveDeVigencia());
        assertThat(noPrimeiroAnexo.chaveDeVigencia())
                .isEqualTo(ItemAnexo.chaveDe(
                        new Ncm(CatalogoFicticio.NCM), new IdentificadorAnexo(CatalogoFicticio.ANEXO)));
    }

    @Test
    void aliquotaDeveTerChaveFormadaPeloParTributoEAbrangencia() {
        AliquotaVigente estadual = CatalogoFicticio.aliquota(
                Tributo.IBS_UF, CatalogoFicticio.ABRANGENCIA, "99.99", procedenciaFechada());
        AliquotaVigente municipal = CatalogoFicticio.aliquota(
                Tributo.IBS_MUN, CatalogoFicticio.ABRANGENCIA, "99.99", procedenciaFechada());

        assertThat(estadual.chaveDeVigencia()).isNotEqualTo(municipal.chaveDeVigencia());
        assertThat(estadual.chaveDeVigencia())
                .isEqualTo(AliquotaVigente.chaveDe(
                        Tributo.IBS_UF, new Abrangencia(CatalogoFicticio.ABRANGENCIA)));
    }

    @Test
    void aliquotaDevePreservarAEscalaDeclarada() {
        AliquotaVigente aliquota = CatalogoFicticio.aliquota(
                Tributo.CBS, CatalogoFicticio.ABRANGENCIA, "99.9900", procedenciaFechada());

        assertThat(aliquota.percentual().scale()).isEqualTo(4);
    }

    @Test
    void abrangenciaDeveRejeitarTextoVazioOuComEspacoEmVolta() {
        assertThatThrownBy(() -> new Abrangencia("")).isInstanceOf(RegistroNormativoInvalido.class);
        assertThatThrownBy(() -> new Abrangencia(" ABRANGENCIA-XX"))
                .isInstanceOf(RegistroNormativoInvalido.class);
    }

    @Test
    void identificadorDeAnexoDeveRejeitarTextoVazio() {
        assertThatThrownBy(() -> new IdentificadorAnexo("  "))
                .isInstanceOf(RegistroNormativoInvalido.class);
    }

    private static ProcedenciaNormativa procedenciaFechada() {
        return CatalogoFicticio.procedencia(CatalogoFicticio.INICIO, CatalogoFicticio.FIM);
    }

    private static ClassificacaoTributaria classificacaoComReducao(Optional<BigDecimal> percentualReducao) {
        return new ClassificacaoTributaria(
                new CodigoClassificacaoTributaria(CatalogoFicticio.CODIGO),
                Set.of(new CodigoCst(CatalogoFicticio.CST)),
                CatalogoFicticio.DISPOSITIVO,
                false,
                percentualReducao,
                List.of(),
                procedenciaFechada());
    }
}
