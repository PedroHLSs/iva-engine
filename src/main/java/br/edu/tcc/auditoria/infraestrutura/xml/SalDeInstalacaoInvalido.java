package br.edu.tcc.auditoria.infraestrutura.xml;

/**
 * O sal de pseudonimização não foi configurado, ou foi configurado com valor
 * inaceitável.
 *
 * <p>É falha de configuração da instalação, não de um documento: interrompe o
 * processamento em vez de virar {@link FalhaDeLeitura}. Continuar sem sal
 * exigiria adotar um valor padrão, e um sal conhecido torna o pseudônimo
 * reversível — o oposto do que ele existe para fazer.</p>
 *
 * <p>A mensagem nunca reproduz o valor recusado.</p>
 */
public class SalDeInstalacaoInvalido extends RuntimeException {

    public SalDeInstalacaoInvalido(String mensagem) {
        super(mensagem);
    }
}
