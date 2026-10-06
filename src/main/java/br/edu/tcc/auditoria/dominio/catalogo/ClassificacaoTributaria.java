package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.CodigoClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.CodigoCst;
import br.edu.tcc.auditoria.dominio.excecao.RegistroNormativoInvalido;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

// Representa o que o catálogo diz sobre um cClassTrib numa vigência; todo o conteúdo chega por importação, e a lista de CSTs compatíveis pode vir vazia. Desde 30/09/2026 traz também tributacaoIntegral, que o catálogo declara ou não: vazio quer dizer "não declarado", nunca "não é integral". Desde 30/09/2026 (R03 1.1.0) traz também anexosAdmitidos: vazio quer dizer "não declarado", e conjunto vazio quer dizer NENHUM, ou seja, o código não exige anexo.
// Emenda de 03/10/2026 (D015, R07 1.1.0): camposObrigatoriosCondicionados era List, e lista vazia valia como "nenhum campo exigido" — a célula em branco do CSV virava conformidade. Agora é Optional: vazio quer dizer "não declarado", e lista vazia quer dizer NENHUM. Os construtores antigos, que recebem List, leem lista vazia como "não declarado".
// Emenda de 03/10/2026 (D017, R05 1.3.0): traz também reducaoIncideSobre, que o catálogo declara ou não — vazio quer dizer "não declarado", e a R05 lê a redução como de alíquota, como a decisão D1 do usuário definiu para a coluna de redução. Até essa data a R05 deduzia a redução de base de uma lista de CST escrita em código.
public record ClassificacaoTributaria(
        CodigoClassificacaoTributaria codigo,
        Set<CodigoCst> cstsCompativeis,
        String dispositivoLegal,
        boolean indicadorDeBeneficio,
        Optional<BigDecimal> percentualReducao,
        Optional<IncidenciaDaReducao> reducaoIncideSobre,
        Optional<Boolean> tributacaoIntegral,
        Optional<Set<IdentificadorAnexo>> anexosAdmitidos,
        Optional<List<String>> camposObrigatoriosCondicionados,
        ProcedenciaNormativa procedencia) implements RegistroNormativo {

    // Construtor com a aridade de 03/10/2026, anterior à R05 1.3.0: a incidência da redução fica não declarada (D017).
    public ClassificacaoTributaria(
            CodigoClassificacaoTributaria codigo,
            Set<CodigoCst> cstsCompativeis,
            String dispositivoLegal,
            boolean indicadorDeBeneficio,
            Optional<BigDecimal> percentualReducao,
            Optional<Boolean> tributacaoIntegral,
            Optional<Set<IdentificadorAnexo>> anexosAdmitidos,
            Optional<List<String>> camposObrigatoriosCondicionados,
            ProcedenciaNormativa procedencia) {
        this(codigo, cstsCompativeis, dispositivoLegal, indicadorDeBeneficio, percentualReducao,
                Optional.empty(), tributacaoIntegral, anexosAdmitidos, camposObrigatoriosCondicionados,
                procedencia);
    }

    // Construtor com a aridade de 01/10/2026, quando os campos exigidos eram List: lista vazia fica "não declarado" (D015).
    public ClassificacaoTributaria(
            CodigoClassificacaoTributaria codigo,
            Set<CodigoCst> cstsCompativeis,
            String dispositivoLegal,
            boolean indicadorDeBeneficio,
            Optional<BigDecimal> percentualReducao,
            Optional<Boolean> tributacaoIntegral,
            Optional<Set<IdentificadorAnexo>> anexosAdmitidos,
            List<String> camposObrigatoriosCondicionados,
            ProcedenciaNormativa procedencia) {
        this(codigo, cstsCompativeis, dispositivoLegal, indicadorDeBeneficio, percentualReducao,
                tributacaoIntegral, anexosAdmitidos, declaradosSeNaoVazios(camposObrigatoriosCondicionados),
                procedencia);
    }

    // Construtor com a aridade de 30/09/2026, anterior à R03 1.1.0: os anexos admitidos ficam não declarados.
    public ClassificacaoTributaria(
            CodigoClassificacaoTributaria codigo,
            Set<CodigoCst> cstsCompativeis,
            String dispositivoLegal,
            boolean indicadorDeBeneficio,
            Optional<BigDecimal> percentualReducao,
            Optional<Boolean> tributacaoIntegral,
            List<String> camposObrigatoriosCondicionados,
            ProcedenciaNormativa procedencia) {
        this(codigo, cstsCompativeis, dispositivoLegal, indicadorDeBeneficio, percentualReducao,
                tributacaoIntegral, Optional.empty(), declaradosSeNaoVazios(camposObrigatoriosCondicionados),
                procedencia);
    }

    // Construtor com a aridade anterior a 30/09/2026: a tributação integral fica não declarada.
    public ClassificacaoTributaria(
            CodigoClassificacaoTributaria codigo,
            Set<CodigoCst> cstsCompativeis,
            String dispositivoLegal,
            boolean indicadorDeBeneficio,
            Optional<BigDecimal> percentualReducao,
            List<String> camposObrigatoriosCondicionados,
            ProcedenciaNormativa procedencia) {
        this(codigo, cstsCompativeis, dispositivoLegal, indicadorDeBeneficio, percentualReducao,
                Optional.empty(), Optional.empty(), declaradosSeNaoVazios(camposObrigatoriosCondicionados),
                procedencia);
    }

    // Valida a classificação: exige código, procedência e dispositivo, e recusa redução nula, que é diferente de redução zero.
    public ClassificacaoTributaria {
        if (codigo == null) {
            throw new RegistroNormativoInvalido("A classificação tributária precisa de código.");
        }
        if (procedencia == null) {
            throw new RegistroNormativoInvalido(
                    "A classificação tributária \"%s\" precisa de vigência e fonte normativa."
                            .formatted(codigo.valor()));
        }
        if (dispositivoLegal == null || dispositivoLegal.isBlank()) {
            throw new RegistroNormativoInvalido(
                    "A classificação tributária \"%s\" precisa citar o dispositivo legal."
                            .formatted(codigo.valor()));
        }
        if (cstsCompativeis == null) {
            throw new RegistroNormativoInvalido(
                    "O conjunto de CSTs compatíveis de \"%s\" deve ser vazio quando não há nenhum, nunca nulo."
                            .formatted(codigo.valor()));
        }
        if (cstsCompativeis.stream().anyMatch(Objects::isNull)) {
            throw new RegistroNormativoInvalido(
                    "O conjunto de CSTs compatíveis de \"%s\" não pode conter elemento nulo."
                            .formatted(codigo.valor()));
        }
        if (percentualReducao == null) {
            throw new RegistroNormativoInvalido(
                    ("A redução de \"%s\" deve ser Optional.empty() quando o catálogo não traz nenhuma, "
                            + "nunca nula e nunca zero: não declarar redução e declarar redução de zero "
                            + "são coisas diferentes.").formatted(codigo.valor()));
        }
        if (reducaoIncideSobre == null) {
            throw new RegistroNormativoInvalido(
                    ("A incidência da redução de \"%s\" deve ser Optional.empty() quando o catálogo não a "
                            + "declara, nunca nula.").formatted(codigo.valor()));
        }
        if (tributacaoIntegral == null) {
            throw new RegistroNormativoInvalido(
                    ("A tributação integral de \"%s\" deve ser Optional.empty() quando o catálogo não a "
                            + "declara, nunca nula.").formatted(codigo.valor()));
        }
        if (anexosAdmitidos == null) {
            throw new RegistroNormativoInvalido(
                    ("Os anexos admitidos de \"%s\" devem ser Optional.empty() quando o catálogo não os "
                            + "declara, nunca nulos: não declarar e declarar NENHUM são coisas diferentes.")
                            .formatted(codigo.valor()));
        }
        if (anexosAdmitidos.isPresent() && anexosAdmitidos.get().stream().anyMatch(Objects::isNull)) {
            throw new RegistroNormativoInvalido(
                    "Os anexos admitidos de \"%s\" não podem conter elemento nulo.".formatted(codigo.valor()));
        }
        if (camposObrigatoriosCondicionados == null) {
            throw new RegistroNormativoInvalido(
                    ("Os campos obrigatórios condicionados de \"%s\" devem ser Optional.empty() quando o "
                            + "catálogo não os declara, nunca nulos: não declarar e declarar NENHUM são "
                            + "coisas diferentes.").formatted(codigo.valor()));
        }
        for (String campo : camposObrigatoriosCondicionados.orElse(List.of())) {
            if (campo == null || campo.isBlank()) {
                throw new RegistroNormativoInvalido(
                        "A lista de campos obrigatórios condicionados de \"%s\" tem entrada vazia."
                                .formatted(codigo.valor()));
            }
        }

        cstsCompativeis = Set.copyOf(new LinkedHashSet<>(cstsCompativeis));
        anexosAdmitidos = anexosAdmitidos.map(Set::copyOf);
        camposObrigatoriosCondicionados = camposObrigatoriosCondicionados.map(List::copyOf);
    }

    // Método auxiliar dos construtores antigos, que recebem List: lista vazia vira "não declarado", porque ali não há como saber se a fonte disse NENHUM (D015).
    private static Optional<List<String>> declaradosSeNaoVazios(List<String> campos) {
        if (campos == null) {
            return null;
        }
        return campos.isEmpty() ? Optional.empty() : Optional.of(campos);
    }

    // Retorna a chave da série de vigência: o próprio código.
    @Override
    public String chaveDeVigencia() {
        return codigo.valor();
    }

    // Indica se o catálogo admite este CST junto deste código, na vigência deste registro.
    public boolean admiteCst(CodigoCst cst) {
        return cstsCompativeis.contains(cst);
    }
}
