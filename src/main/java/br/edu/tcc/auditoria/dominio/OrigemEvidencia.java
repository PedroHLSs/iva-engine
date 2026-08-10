package br.edu.tcc.auditoria.dominio;

import br.edu.tcc.auditoria.dominio.excecao.EvidenciaInvalida;

/**
 * De onde saiu o valor registrado em uma {@link Evidencia}.
 *
 * <p>Existe para que o relatório consiga responder "por que você diz que o
 * esperado era isso?". Um valor esperado que veio de tabela importada carrega o
 * nome e a versão da tabela; um valor que a própria regra derivou diz como
 * derivou; um valor lido do documento diz onde no documento estava.</p>
 *
 * <p>Nenhuma das variantes contém conteúdo normativo: são rótulos preenchidos
 * em tempo de execução por quem carregou a tabela ou escreveu a regra.</p>
 */
public sealed interface OrigemEvidencia {

    /**
     * O valor foi lido do próprio documento auditado.
     *
     * @param localizacao onde no documento o valor estava, em texto livre
     */
    record DoDocumento(String localizacao) implements OrigemEvidencia {

        public DoDocumento {
            exigirTexto(localizacao, "localizacao");
        }
    }

    /**
     * O valor veio de uma tabela normativa importada.
     *
     * @param nomeTabela   identificação da tabela carregada
     * @param versaoTabela versão ou data da carga usada na avaliação
     */
    record DeTabelaNormativa(String nomeTabela, String versaoTabela) implements OrigemEvidencia {

        public DeTabelaNormativa {
            exigirTexto(nomeTabela, "nomeTabela");
            exigirTexto(versaoTabela, "versaoTabela");
        }
    }

    /**
     * O valor foi derivado pela própria regra, por exemplo uma soma que deveria
     * fechar com outro campo do documento.
     *
     * @param descricao como o valor foi obtido, em texto livre
     */
    record DaRegra(String descricao) implements OrigemEvidencia {

        public DaRegra {
            exigirTexto(descricao, "descricao");
        }
    }

    private static void exigirTexto(String valor, String nomeDoCampo) {
        if (valor == null || valor.isBlank()) {
            throw new EvidenciaInvalida(
                    "O campo \"%s\" da origem da evidência é obrigatório.".formatted(nomeDoCampo));
        }
    }
}
