package br.edu.tcc.auditoria.dominio.catalogo;

import br.edu.tcc.auditoria.dominio.PeriodoVigencia;
import br.edu.tcc.auditoria.dominio.excecao.RegistroNormativoInvalido;

import java.time.LocalDate;
import java.util.Optional;

// Representa um anexo que a carga declara existir, e o período em que os itens dele estão carregados. Sem período, o anexo existe (serve para validar identificadores) mas não está carregado em data nenhuma. Acrescentado em 01/10/2026 (decisões D7 e D9 do usuário).
public record AnexoDeclarado(
        IdentificadorAnexo identificador,
        TipoDeCodigoDoAnexo tipoDeCodigo,
        Optional<PeriodoVigencia> carregamento,
        String fonteNormativa) {

    // Valida que haja identificador, tipo, fonte e que o período venha como Optional, nunca nulo.
    public AnexoDeclarado {
        if (identificador == null) {
            throw new RegistroNormativoInvalido("O anexo declarado precisa de identificador.");
        }
        if (tipoDeCodigo == null) {
            throw new RegistroNormativoInvalido(
                    "O anexo declarado \"%s\" precisa dizer o tipo de código.".formatted(identificador.valor()));
        }
        if (carregamento == null) {
            throw new RegistroNormativoInvalido(
                    ("O período de carregamento do anexo \"%s\" deve ser Optional.empty() quando não há, "
                            + "nunca nulo.").formatted(identificador.valor()));
        }
        if (fonteNormativa == null || fonteNormativa.isBlank()) {
            throw new RegistroNormativoInvalido(
                    "O anexo declarado \"%s\" precisa de fonte normativa.".formatted(identificador.valor()));
        }
    }

    // Diz se os itens do anexo estão carregados na data: só quando há período e a data cai nele.
    public boolean carregadoEm(LocalDate data) {
        return carregamento.map(periodo -> periodo.contem(data)).orElse(false);
    }
}
