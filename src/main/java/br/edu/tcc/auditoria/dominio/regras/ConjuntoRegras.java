package br.edu.tcc.auditoria.dominio.regras;

import br.edu.tcc.auditoria.dominio.excecao.ConjuntoRegrasInvalido;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// Guarda as regras que a auditoria aplica, na ordem em que rodam, e a versão desse conjunto. Se alguma regra mudar, a versão do conjunto também tem de mudar; um teste confere isso.
public record ConjuntoRegras(String versao, List<RegraAuditoria> regras) {

    // Versão do conjunto criado pelo método padrao. 2026.4 desde 03/10/2026: a R07 passou a 1.1.0 (D015). 2026.5 no mesmo dia: a R05 passou a 1.2.0 e recebe a cobertura das classificações (D016). 2026.6: a R05 passou a 1.3.0 e lê no catálogo se a redução incide sobre a base (D017).
    public static final String VERSAO_PADRAO = "2026.6";

    // Confere se o conjunto tem versão, pelo menos uma regra, nenhuma regra nula e nenhum código de regra repetido.
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

    // Cria o conjunto com as sete regras, de R01 a R07. Recebe de fora o período coberto pelo catálogo e a tolerância de valor, porque isso não pode ficar fixo no código.
    public static ConjuntoRegras padrao(CoberturaDoCatalogo cobertura, ToleranciaDeValor tolerancia) {
        if (cobertura == null) {
            throw new ConjuntoRegrasInvalido(
                    "O conjunto padrão precisa da cobertura declarada do catálogo carregado.");
        }
        return new ConjuntoRegras(VERSAO_PADRAO, List.of(
                new RegraClassificacaoTributariaExiste(cobertura.classificacoesTributarias()),
                new RegraCstCompativelComClassificacao(),
                new RegraBeneficioExigeNcmEmAnexo(cobertura.itensDeAnexo(), cobertura.anexosDeclarados()),
                new RegraTratamentoDeAnexoNaoAproveitado(cobertura.itensDeAnexo()),
                new RegraValorDeTributoConfere(tolerancia, cobertura.classificacoesTributarias()),
                new RegraNcmExiste(cobertura.ncm()),
                new RegraCamposObrigatoriosPreenchidos()));
    }

    // Devolve os códigos das regras, na ordem em que rodam.
    public List<String> identificadores() {
        return regras.stream().map(RegraAuditoria::id).toList();
    }
}
