package br.edu.tcc.auditoria.infraestrutura.configuracao;

/**
 * Configuração obrigatória ausente ou malformada.
 *
 * <p>É lançada durante a subida do contexto, de propósito: valor de configuração
 * que muda o que o relatório mostra não pode ser descoberto pela metade do
 * processamento, e muito menos ser substituído por um padrão silencioso.</p>
 */
public class ConfiguracaoInvalida extends RuntimeException {

    public ConfiguracaoInvalida(String mensagem) {
        super(mensagem);
    }

    public ConfiguracaoInvalida(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
