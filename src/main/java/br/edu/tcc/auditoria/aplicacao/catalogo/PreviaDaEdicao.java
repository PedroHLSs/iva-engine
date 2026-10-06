package br.edu.tcc.auditoria.aplicacao.catalogo;

import br.edu.tcc.auditoria.dominio.excecao.CatalogoInvalido;

import java.util.Optional;

// Representa o que salvar uma edição vai fazer, dito antes de a pessoa confirmar: se muda a própria carga ou cria uma nova, quantas análises usam a original e qual versão será criada.
public record PreviaDaEdicao(
        String versaoDeOrigem,
        EfeitoDaEdicao efeito,
        long analisesQueUsam,
        Optional<String> versaoQueSeraCriada,
        boolean passaraASerAMaisRecente,
        String aviso) {

    // Valida que a versão a criar exista exatamente quando o efeito é criar versão nova.
    public PreviaDaEdicao {
        if (versaoDeOrigem == null || efeito == null || versaoQueSeraCriada == null
                || aviso == null || aviso.isBlank()) {
            throw new CatalogoInvalido("A prévia da edição precisa da origem, do efeito e do aviso.");
        }
        if ((efeito == EfeitoDaEdicao.CRIAR_VERSAO_NOVA) != versaoQueSeraCriada.isPresent()) {
            throw new CatalogoInvalido(
                    "A prévia diz qual versão será criada se, e somente se, a edição cria versão nova.");
        }
    }
}
