package br.edu.tcc.auditoria.aplicacao.catalogo;

import br.edu.tcc.auditoria.aplicacao.catalogo.ServicoDeImportacaoDeCatalogo.ResumoDaImportacao;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

import java.util.List;
import java.util.Optional;

// Representa o que a importação fez: se foi completa ou parcial, o resumo da carga gravada, de qual carga as tabelas herdadas vieram, com o estado dela no momento da importação, quais vieram no envio e quais foram herdadas. Acrescentado em 04/10/2026 (D026).
public record ResultadoDaImportacao(
        TipoDaImportacao tipo,
        ResumoDaImportacao resumo,
        Optional<EstadoDaCarga> origem,
        Optional<String> origemInformadaNaoUsada,
        List<String> tabelasEnviadas,
        List<String> tabelasHerdadas,
        String aviso) {

    // Valida que a origem exista exatamente na importação parcial, e que só a parcial herde tabela.
    public ResultadoDaImportacao {
        if (tipo == null || resumo == null || origem == null || origemInformadaNaoUsada == null
                || tabelasEnviadas == null || tabelasHerdadas == null || aviso == null || aviso.isBlank()) {
            throw new CatalogoInvalido(
                    "O resultado da importação precisa do tipo, do resumo, das tabelas e do aviso.");
        }
        if ((tipo == TipoDaImportacao.PARCIAL) != origem.isPresent()) {
            throw new CatalogoInvalido("Só a importação parcial tem carga de origem.");
        }
        if (tipo == TipoDaImportacao.COMPLETA && !tabelasHerdadas.isEmpty()) {
            throw new CatalogoInvalido("A importação completa não herda tabela.");
        }
        if (tipo == TipoDaImportacao.PARCIAL && origemInformadaNaoUsada.isPresent()) {
            throw new CatalogoInvalido("Na importação parcial, a origem informada é a usada.");
        }
        tabelasEnviadas = List.copyOf(tabelasEnviadas);
        tabelasHerdadas = List.copyOf(tabelasHerdadas);
    }
}
