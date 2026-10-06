package br.edu.tcc.auditoria.aplicacao.identidade;

// Enum com os três perfis de usuário. Cada um diz o que pode fazer; quem de fato recusa o pedido é o servidor, e a tela só esconde o botão por conveniência.
public enum Perfil {

    // Gerencia usuários e cargas de catálogo, e faz tudo o que o fiscal faz.
    ADMINISTRADOR("Administrador"),

    // Envia notas para análise, consulta análises e registra tratativa.
    FISCAL("Fiscal"),

    // Só lê: não envia nota, não trata achado e não mexe no catálogo.
    CONSULTA("Consulta");

    private final String rotulo;

    // Construtor que recebe o nome do perfil como aparece na tela.
    Perfil(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }

    // Diz se o perfil pode enviar nota para análise.
    public boolean podeEnviarNota() {
        return this != CONSULTA;
    }

    // Diz se o perfil pode registrar tratativa sobre um apontamento.
    public boolean podeRegistrarTratativa() {
        return this != CONSULTA;
    }

    // Diz se o perfil pode criar, alterar e excluir usuários e cargas de catálogo.
    public boolean podeAdministrar() {
        return this == ADMINISTRADOR;
    }
}
