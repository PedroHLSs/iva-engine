package br.edu.tcc.auditoria.infraestrutura.api;

// Representa o que a API mostra de dado sensível: chave de acesso, justificativa da tratativa e descrição do produto. Os três vêm desligados por padrão, porque podem trazer CNPJ, razão social ou nome de cliente, e quem liga é quem instala, por configuração, e não quem monta a URL.
public record PoliticaDeExposicao(
        boolean chaveDeAcesso, boolean justificativa, boolean descricaoDoProduto) {

    // Motivos escritos no lugar de cada campo omitido.
    static final String CHAVE_OMITIDA =
            "a chave de acesso não é exposta por esta instalação: seus dígitos intermediários "
                    + "carregam o CNPJ do emitente. Ligue \"auditoria.api.expor-chave-de-acesso\" "
                    + "para incluí-la.";

    static final String JUSTIFICATIVA_OMITIDA =
            "a justificativa não é exposta por esta instalação: é texto livre e pode conter CNPJ ou "
                    + "razão social. Ligue \"auditoria.api.expor-justificativa\" para incluí-la.";

    static final String DESCRICAO_OMITIDA =
            "a descrição do produto não é exposta por esta instalação: é texto livre digitado pelo "
                    + "emitente, em escala e sem revisão, e costuma trazer nome de cliente e "
                    + "referência de pedido. Ligue \"auditoria.api.expor-descricao-do-produto\" "
                    + "para incluí-la.";

    // Método estático que cria a política padrão: tudo desligado.
    public static PoliticaDeExposicao restritiva() {
        return new PoliticaDeExposicao(false, false, false);
    }

    // Devolve a chave se a instalação a expõe, ou null. É null, e não Optional, porque é o valor que vai para o JSON; o motivo sai em motivoDaChaveOmitida().
    public String chaveOuNulo(String chave) {
        return chaveDeAcesso ? chave : null;
    }

    // Devolve o motivo de a chave estar omitida, ou null quando ela é exposta.
    public String motivoDaChaveOmitida() {
        return chaveDeAcesso ? null : CHAVE_OMITIDA;
    }

    // Devolve a justificativa se a instalação a expõe, ou null.
    public String justificativaOuNulo(String justificativaRegistrada) {
        return justificativa ? justificativaRegistrada : null;
    }

    // Devolve o motivo de a justificativa estar omitida, ou null quando ela é exposta.
    public String motivoDaJustificativaOmitida() {
        return justificativa ? null : JUSTIFICATIVA_OMITIDA;
    }

    // Devolve a descrição do produto se a instalação a expõe, ou null.
    public String descricaoOuNulo(String descricaoGravada) {
        return descricaoDoProduto ? descricaoGravada : null;
    }

    // Devolve o motivo de a descrição estar omitida, ou null quando ela é exposta.
    public String motivoDaDescricaoOmitida() {
        return descricaoDoProduto ? null : DESCRICAO_OMITIDA;
    }
}
