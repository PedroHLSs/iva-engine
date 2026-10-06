package br.edu.tcc.auditoria.aplicacao.catalogo;

import br.edu.tcc.auditoria.dominio.catalogo.AliquotaVigente;
import br.edu.tcc.auditoria.dominio.catalogo.AnexoDeclarado;
import br.edu.tcc.auditoria.dominio.catalogo.ClassificacaoTributaria;
import br.edu.tcc.auditoria.dominio.catalogo.ItemAnexo;
import br.edu.tcc.auditoria.dominio.catalogo.RegistroNcm;
import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;
import br.edu.tcc.auditoria.dominio.regras.CoberturaDoCatalogo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Representa uma edição de carga: as tabelas que vieram em CSV novo, e só elas. Tabela que não veio é copiada da carga de origem como está, com a natureza que já tinha. Não há edição de linha por formulário: conteúdo normativo só entra por CSV (seção 5 do CLAUDE.md). Desde 01/10/2026 inclui a lista de anexos declarados: se não vier, é herdada da carga de origem, mesmo quando o cobertura.csv vem.
public record SubstituicaoDeTabelas(
        Optional<TabelaSubstituta<ClassificacaoTributaria>> classificacoesTributarias,
        Optional<TabelaSubstituta<RegistroNcm>> registrosDeNcm,
        Optional<TabelaSubstituta<ItemAnexo>> itensDeAnexo,
        Optional<TabelaSubstituta<AliquotaVigente>> aliquotas,
        Optional<CoberturaDoCatalogo> cobertura,
        Optional<TabelaSubstituta<AnexoDeclarado>> anexosDeclarados,
        Optional<Natureza> naturezaDaCobertura) {

    // Emenda de 04/10/2026 (D021): o cobertura.csv novo traz a natureza dele, e ela substitui a da origem junto com a cobertura.

    // Construtor com a aridade anterior a 04/10/2026: sem a natureza da cobertura, que só pode vir com a cobertura.
    public SubstituicaoDeTabelas(
            Optional<TabelaSubstituta<ClassificacaoTributaria>> classificacoesTributarias,
            Optional<TabelaSubstituta<RegistroNcm>> registrosDeNcm,
            Optional<TabelaSubstituta<ItemAnexo>> itensDeAnexo,
            Optional<TabelaSubstituta<AliquotaVigente>> aliquotas,
            Optional<CoberturaDoCatalogo> cobertura,
            Optional<TabelaSubstituta<AnexoDeclarado>> anexosDeclarados) {
        this(classificacoesTributarias, registrosDeNcm, itensDeAnexo, aliquotas, cobertura, anexosDeclarados,
                Optional.empty());
    }

    // Construtor com a aridade anterior a 01/10/2026: a lista de anexos declarados não é substituída.
    public SubstituicaoDeTabelas(
            Optional<TabelaSubstituta<ClassificacaoTributaria>> classificacoesTributarias,
            Optional<TabelaSubstituta<RegistroNcm>> registrosDeNcm,
            Optional<TabelaSubstituta<ItemAnexo>> itensDeAnexo,
            Optional<TabelaSubstituta<AliquotaVigente>> aliquotas,
            Optional<CoberturaDoCatalogo> cobertura) {
        this(classificacoesTributarias, registrosDeNcm, itensDeAnexo, aliquotas, cobertura, Optional.empty(),
                Optional.empty());
    }

    // Valida que nenhum campo venha nulo e que ao menos um arquivo tenha vindo.
    public SubstituicaoDeTabelas {
        if (classificacoesTributarias == null || registrosDeNcm == null || itensDeAnexo == null
                || aliquotas == null || cobertura == null || anexosDeclarados == null
                || naturezaDaCobertura == null) {
            throw new CatalogoInvalido(
                    "Tabela não substituída se representa com Optional.empty(), nunca com nulo.");
        }
        if (classificacoesTributarias.isEmpty() && registrosDeNcm.isEmpty() && itensDeAnexo.isEmpty()
                && aliquotas.isEmpty() && cobertura.isEmpty() && anexosDeclarados.isEmpty()) {
            throw new CatalogoInvalido(
                    "Nenhum arquivo foi enviado. A edição substitui uma ou mais tabelas por CSV novo; "
                            + "sem arquivo, não há o que mudar.");
        }
        if (naturezaDaCobertura.isPresent() && cobertura.isEmpty()) {
            throw new CatalogoInvalido("A natureza da cobertura só vem junto com a cobertura.");
        }
    }

    // Monta a carga nova: as tabelas substituídas vêm do CSV, as outras vêm da origem, e a validação é a mesma de uma importação.
    public CargaDeCatalogo aplicarSobre(CargaDeCatalogo origem, String versaoResultante) {
        NaturezaDaCarga naturezaDeOrigem = origem.natureza();
        CoberturaDoCatalogo base = cobertura.orElse(origem.cobertura());
        return new CargaDeCatalogo(
                versaoResultante,
                new CoberturaDoCatalogo(
                        base.classificacoesTributarias(),
                        base.ncm(),
                        base.itensDeAnexo(),
                        anexosDeclarados.map(TabelaSubstituta::registros)
                                .orElse(origem.cobertura().anexosDeclarados())),
                new NaturezaDaCarga(
                        classificacoesTributarias.map(TabelaSubstituta::natureza)
                                .orElse(naturezaDeOrigem.classificacoesTributarias()),
                        registrosDeNcm.map(TabelaSubstituta::natureza)
                                .orElse(naturezaDeOrigem.registrosDeNcm()),
                        itensDeAnexo.map(TabelaSubstituta::natureza)
                                .orElse(naturezaDeOrigem.itensDeAnexo()),
                        aliquotas.map(TabelaSubstituta::natureza)
                                .orElse(naturezaDeOrigem.aliquotas()),
                        anexosDeclarados.map(TabelaSubstituta::natureza)
                                .orElse(naturezaDeOrigem.anexosDeclarados()),
                        // Cobertura nova traz a natureza dela; sem natureza, fica não declarada, e não herda a da origem.
                        cobertura.isPresent() ? naturezaDaCobertura : naturezaDeOrigem.cobertura()),
                classificacoesTributarias.map(TabelaSubstituta::registros)
                        .orElse(origem.classificacoesTributarias()),
                registrosDeNcm.map(TabelaSubstituta::registros).orElse(origem.registrosDeNcm()),
                itensDeAnexo.map(TabelaSubstituta::registros).orElse(origem.itensDeAnexo()),
                aliquotas.map(TabelaSubstituta::registros).orElse(origem.aliquotas()));
    }

    // Devolve os nomes das tabelas que esta edição substitui, para a resposta dizer o que mudou.
    public List<String> tabelasSubstituidas() {
        List<String> nomes = new ArrayList<>();
        classificacoesTributarias.ifPresent(tabela -> nomes.add(NaturezaDaCarga.CLASSIFICACOES_TRIBUTARIAS));
        registrosDeNcm.ifPresent(tabela -> nomes.add(NaturezaDaCarga.REGISTROS_DE_NCM));
        itensDeAnexo.ifPresent(tabela -> nomes.add(NaturezaDaCarga.ITENS_DE_ANEXO));
        aliquotas.ifPresent(tabela -> nomes.add(NaturezaDaCarga.ALIQUOTAS));
        cobertura.ifPresent(tabela -> nomes.add(NaturezaDaCarga.COBERTURA));
        anexosDeclarados.ifPresent(tabela -> nomes.add(NaturezaDaCarga.ANEXOS_DECLARADOS));
        return List.copyOf(nomes);
    }
}
