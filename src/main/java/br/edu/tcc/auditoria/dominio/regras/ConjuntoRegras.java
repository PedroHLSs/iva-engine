package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.excecao.ConjuntoRegrasInvalido;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * As regras que uma auditoria aplica, e a versão do conjunto que elas formam.
 *
 * <h2>Por que o conjunto tem versão própria</h2>
 *
 * <p>Um relatório precisa poder ser refeito e dar o mesmo resultado. Guardar
 * apenas a versão de cada regra não basta: acrescentar uma regra nova, remover
 * uma existente ou trocar a ordem muda o relatório sem mudar versão de regra
 * nenhuma. A versão do conjunto identifica a auditoria inteira, e é ela que vai
 * junto do relatório.</p>
 *
 * <p><strong>Mudar qualquer regra obriga a subir esta versão.</strong> Isso não
 * tem como ser garantido pelo compilador — a versão é texto —, e por isso há
 * teste que fixa o inventário do conjunto padrão: alterar a versão de uma regra,
 * acrescentar regra ou reordenar faz o teste falhar, o que obriga a passar pela
 * decisão de versionamento em vez de deixá-la para depois.</p>
 *
 * <h2>Ordem é parte do conjunto</h2>
 *
 * <p>A lista é ordenada e a ordem é preservada: o motor aplica as regras na
 * sequência em que estão aqui, e é dessa sequência que sai a ordem das
 * avaliações de um mesmo item no relatório.</p>
 *
 * @param versao identificação do conjunto, por exemplo {@code "2026.1"}
 * @param regras as regras, na ordem em que serão aplicadas
 */
public record ConjuntoRegras(String versao, List<RegraAuditoria> regras) {

    /** Versão do conjunto montado por {@link #padrao}. */
    public static final String VERSAO_PADRAO = "2026.1";

    public ConjuntoRegras {
        if (versao == null || versao.isBlank()) {
            throw new ConjuntoRegrasInvalido(
                    "O conjunto de regras precisa de versão: sem ela o relatório não é reproduzível.");
        }
        if (regras == null) {
            throw new ConjuntoRegrasInvalido("A lista de regras não pode ser nula.");
        }
        if (regras.isEmpty()) {
            throw new ConjuntoRegrasInvalido(
                    "Um conjunto sem regra nenhuma produziria relatório vazio com aparência de auditoria feita.");
        }
        if (regras.stream().anyMatch(Objects::isNull)) {
            throw new ConjuntoRegrasInvalido("A lista de regras não pode conter elemento nulo.");
        }

        Set<String> identificadores = new LinkedHashSet<>();
        for (RegraAuditoria regra : regras) {
            if (!identificadores.add(regra.id())) {
                throw new ConjuntoRegrasInvalido(
                        ("O conjunto tem mais de uma regra com o identificador \"%s\". O identificador é o "
                                + "que liga o achado à regra que o produziu, e precisa ser único.")
                                .formatted(regra.id()));
            }
        }

        regras = List.copyOf(regras);
    }

    /**
     * O conjunto das sete regras, na ordem de R01 a R07.
     *
     * <p>Os dois parâmetros são a parte que não pode ser decidida em código: a
     * cobertura declarada das tabelas carregadas e a tolerância adotada na
     * conferência de valor. Ver {@link CoberturaDoCatalogo} e
     * {@link ToleranciaDeValor}.</p>
     */
    public static ConjuntoRegras padrao(CoberturaDoCatalogo cobertura, ToleranciaDeValor tolerancia) {
        if (cobertura == null) {
            throw new ConjuntoRegrasInvalido(
                    "O conjunto padrão precisa da cobertura declarada do catálogo carregado.");
        }
        return new ConjuntoRegras(VERSAO_PADRAO, List.of(
                new RegraClassificacaoTributariaExiste(cobertura.classificacoesTributarias()),
                new RegraCstCompativelComClassificacao(),
                new RegraBeneficioExigeNcmEmAnexo(cobertura.itensDeAnexo()),
                new RegraTratamentoDeAnexoNaoAproveitado(cobertura.itensDeAnexo()),
                new RegraValorDeTributoConfere(tolerancia),
                new RegraNcmExiste(cobertura.ncm()),
                new RegraCamposObrigatoriosPreenchidos()));
    }

    /** Identificadores das regras, na ordem de aplicação. */
    public List<String> identificadores() {
        return regras.stream().map(RegraAuditoria::id).toList();
    }
}
