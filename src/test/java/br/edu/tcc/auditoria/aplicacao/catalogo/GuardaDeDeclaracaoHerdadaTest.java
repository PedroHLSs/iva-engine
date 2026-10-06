package br.edu.tcc.auditoria.aplicacao.catalogo;

import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.catalogo.AnexoDeclarado;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.catalogo.IdentificadorAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.ProcedenciaNormativa;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.catalogo.TipoDeCodigoDoAnexo;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;
import br.edu.tcc.auditoria.dominio.Ncm;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// D026 (04/10/2026), decisão D-a: trocar uma tabela e herdar a declaração que fala dela é recusado. Trocar classificações, NCM ou itens de anexo exige cobertura.csv junto; trocar itens de anexo exige anexos-declarados.csv junto quando a lista herdada declara algum anexo carregado. Valores fictícios.
class GuardaDeDeclaracaoHerdadaTest {

    private static final ProcedenciaNormativa PROCEDENCIA =
            ProcedenciaNormativa.aPartirDe(LocalDate.of(1900, 1, 1), "FONTE FICTICIA v0.0");
    private static final CoberturaDoCatalogo COBERTURA = new CoberturaDoCatalogo(PROCEDENCIA, PROCEDENCIA, PROCEDENCIA);
    private static final AnexoDeclarado CARREGADO = new AnexoDeclarado(new IdentificadorAnexo("ANEXO-XX"),
            TipoDeCodigoDoAnexo.NCM, Optional.of(PeriodoVigencia.aPartirDe(LocalDate.of(1900, 1, 1))),
            "FONTE FICTICIA v0.0");
    private static final AnexoDeclarado SO_IDENTIFICADOR = new AnexoDeclarado(new IdentificadorAnexo("ANEXO-YY"),
            TipoDeCodigoDoAnexo.NCM, Optional.empty(), "FONTE FICTICIA v0.0");

    @Test
    void trocarTabelaComCoberturaSemCoberturaJuntoDeveSerRecusado() {
        CargaDeCatalogo origem = origem(List.of());
        List<SubstituicaoDeTabelas> semCobertura = List.of(
                troca(Optional.of(classificacoes()), Optional.empty(), Optional.empty(), Optional.empty()),
                troca(Optional.empty(), Optional.of(ncms()), Optional.empty(), Optional.empty()),
                troca(Optional.empty(), Optional.empty(), Optional.of(itens()), Optional.empty()));
        List<String> arquivos = List.of("classificacao-tributaria.csv", "registro-ncm.csv", "item-anexo.csv");

        for (int i = 0; i < semCobertura.size(); i++) {
            SubstituicaoDeTabelas substituicao = semCobertura.get(i);
            assertThatThrownBy(() -> GuardaDeDeclaracaoHerdada.exigir(substituicao, origem))
                    .isInstanceOf(CatalogoInvalido.class)
                    .hasMessageContaining("Trocar " + arquivos.get(i) + " exige cobertura.csv junto");
        }
    }

    @Test
    void trocarSoAsAliquotasSemCoberturaDevePassar() {
        assertThatCode(() -> GuardaDeDeclaracaoHerdada.exigir(new SubstituicaoDeTabelas(Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of(new TabelaSubstituta<>(List.of(), Optional.empty())),
                Optional.empty()), origem(List.of(CARREGADO))))
                .doesNotThrowAnyException();
    }

    @Test
    void trocarItensDeAnexoSemAnexosDeclaradosDeveSerRecusadoQuandoHaAnexoCarregado() {
        assertThatThrownBy(() -> GuardaDeDeclaracaoHerdada.exigir(
                troca(Optional.empty(), Optional.empty(), Optional.of(itens()), Optional.of(COBERTURA)),
                origem(List.of(CARREGADO, SO_IDENTIFICADOR))))
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("Trocar item-anexo.csv exige anexos-declarados.csv junto")
                .hasMessageContaining("\"ANEXO-XX\"")
                .hasMessageNotContaining("ANEXO-YY");
    }

    @Test
    void trocarItensDeAnexoSemAnexosDeclaradosDevePassarQuandoNenhumAnexoEstaCarregado() {
        assertThatCode(() -> GuardaDeDeclaracaoHerdada.exigir(
                troca(Optional.empty(), Optional.empty(), Optional.of(itens()), Optional.of(COBERTURA)),
                origem(List.of(SO_IDENTIFICADOR))))
                .doesNotThrowAnyException();
    }

    @Test
    void trocarItensDeAnexoComAnexosDeclaradosJuntoDevePassar() {
        SubstituicaoDeTabelas comLista = new SubstituicaoDeTabelas(Optional.empty(), Optional.empty(),
                Optional.of(itens()), Optional.empty(), Optional.of(COBERTURA),
                Optional.of(new TabelaSubstituta<>(List.of(CARREGADO), Optional.of(Natureza.FICTICIO))),
                Optional.of(Natureza.FICTICIO));

        assertThatCode(() -> GuardaDeDeclaracaoHerdada.exigir(comLista, origem(List.of(CARREGADO))))
                .doesNotThrowAnyException();
    }

    @Test
    void osDoisProblemasDevemSairNumaMensagemSo() {
        assertThatThrownBy(() -> GuardaDeDeclaracaoHerdada.exigir(
                troca(Optional.empty(), Optional.empty(), Optional.of(itens()), Optional.empty()),
                origem(List.of(CARREGADO))))
                .isInstanceOf(CatalogoInvalido.class)
                .hasMessageContaining("exige cobertura.csv junto")
                .hasMessageContaining("exige anexos-declarados.csv junto")
                .hasMessageContaining("Nada foi gravado");
    }

    private static SubstituicaoDeTabelas troca(
            Optional<TabelaSubstituta<ClassificacaoTributaria>> classificacoes,
            Optional<TabelaSubstituta<RegistroNcm>> ncms,
            Optional<TabelaSubstituta<ItemAnexo>> itens,
            Optional<CoberturaDoCatalogo> cobertura) {
        return new SubstituicaoDeTabelas(classificacoes, ncms, itens, Optional.empty(), cobertura, Optional.empty(),
                cobertura.map(lida -> Natureza.FICTICIO));
    }

    private static CargaDeCatalogo origem(List<AnexoDeclarado> anexos) {
        return new CargaDeCatalogo("carga-a",
                new CoberturaDoCatalogo(PROCEDENCIA, PROCEDENCIA, PROCEDENCIA, anexos),
                new NaturezaDaCarga(Optional.of(Natureza.FICTICIO), Optional.of(Natureza.FICTICIO),
                        Optional.of(Natureza.FICTICIO), Optional.empty(),
                        anexos.isEmpty() ? Optional.empty() : Optional.of(Natureza.FICTICIO),
                        Optional.of(Natureza.FICTICIO)),
                classificacoes().registros(), ncms().registros(), itens().registros(), List.of());
    }

    private static TabelaSubstituta<ClassificacaoTributaria> classificacoes() {
        return new TabelaSubstituta<>(List.of(new ClassificacaoTributaria(new CodigoClassificacaoTributaria("999999"),
                Set.of(new CodigoCst("AAA")), "Dispositivo ficticio", false, Optional.empty(), List.of(),
                PROCEDENCIA)), Optional.of(Natureza.FICTICIO));
    }

    private static TabelaSubstituta<RegistroNcm> ncms() {
        return new TabelaSubstituta<>(List.of(new RegistroNcm(new Ncm("00000000"), "Descricao ficticia", PROCEDENCIA)),
                Optional.of(Natureza.FICTICIO));
    }

    private static TabelaSubstituta<ItemAnexo> itens() {
        return new TabelaSubstituta<>(List.of(new ItemAnexo(new Ncm("00000000"), new IdentificadorAnexo("ANEXO-XX"),
                "TRATAMENTO-XX", PROCEDENCIA)), Optional.of(Natureza.FICTICIO));
    }
}
