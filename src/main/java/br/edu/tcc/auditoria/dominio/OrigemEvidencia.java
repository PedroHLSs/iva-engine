package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.EvidenciaInvalida;

// Interface que diz de onde saiu o valor de uma evidência: da própria nota, de uma tabela importada ou de uma conta feita pela regra.
public sealed interface OrigemEvidencia {

    // Representa o valor lido da própria nota, com o lugar onde ele estava.
    record DoDocumento(String localizacao) implements OrigemEvidencia {

        // Valida que a localização esteja preenchida.
        public DoDocumento {
            exigirTexto(localizacao, "localizacao");
        }
    }

    // Representa o valor que veio de uma tabela importada, com o nome e a versão da tabela.
    record DeTabelaNormativa(String nomeTabela, String versaoTabela) implements OrigemEvidencia {

        // Valida que o nome e a versão da tabela estejam preenchidos.
        public DeTabelaNormativa {
            exigirTexto(nomeTabela, "nomeTabela");
            exigirTexto(versaoTabela, "versaoTabela");
        }
    }

    // Representa o valor calculado pela própria regra, com a explicação de como foi obtido.
    record DaRegra(String descricao) implements OrigemEvidencia {

        // Valida que a descrição esteja preenchida.
        public DaRegra {
            exigirTexto(descricao, "descricao");
        }
    }

    // Método auxiliar para verificar se um campo de texto obrigatório está vazio e lançar uma exceção.
    private static void exigirTexto(String valor, String nomeDoCampo) {
        if (valor == null || valor.isBlank()) {
            throw new EvidenciaInvalida(
                    "O campo \"%s\" da origem da evidência é obrigatório.".formatted(nomeDoCampo));
        }
    }
}
